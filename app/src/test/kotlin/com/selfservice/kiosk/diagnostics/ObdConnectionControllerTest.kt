package com.selfservice.kiosk.diagnostics

import com.selfservice.core.DispatchersProvider
import com.selfservice.obd.core.connection.BleDevice
import com.selfservice.obd.core.connection.BleScannerConfig
import com.selfservice.obd.core.connection.ObdConnectionManager
import com.selfservice.obd.core.session.ConnectedAdapter
import com.selfservice.obd.core.session.ObdSessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ObdConnectionControllerTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatchersProvider {
        override val io: CoroutineDispatcher = dispatcher
        override val computation: CoroutineDispatcher = dispatcher
        override val main: CoroutineDispatcher = dispatcher
    }
    private val adapter = ConnectedAdapter(
        device = BleDevice(address = "00:11:22:33:44:55", name = "Ediag", rssi = -45),
        protocol = "BLE"
    )

    @Test
    fun connectAsyncDelegatesToManager() = runTest(dispatcher) {
        val gateway = FakeSessionGateway()
        val discovery = FakeAdapterDiscovery()
        discovery.latestAdapter = adapter
        gateway.nextResult = ObdSessionState.Ready(adapter.device)
        val manager = ObdConnectionManager(
            sessionGateway = gateway,
            adapterDiscovery = discovery,
            dispatchers = dispatchers,
            timeProvider = { 1L }
        )
        val controller = ObdConnectionController(
            manager = manager,
            scope = this,
            dispatchers = dispatchers,
            logTag = "TestController"
        )

        controller.connectAsync()
        advanceUntilIdle()

        assertTrue(manager.snapshot.value.sessionState is ObdSessionState.Ready)
        assertEquals(1, gateway.startCalls)
        assertEquals(1, discovery.startCalls)

        manager.shutdown()
    }

    @Test
    fun disconnectAsyncStopsSession() = runTest(dispatcher) {
        val gateway = FakeSessionGateway()
        val discovery = FakeAdapterDiscovery()
        discovery.latestAdapter = adapter
        gateway.nextResult = ObdSessionState.Diagnostics(adapter.device)
        val manager = ObdConnectionManager(
            sessionGateway = gateway,
            adapterDiscovery = discovery,
            dispatchers = dispatchers,
            timeProvider = { 2L }
        )
        val controller = ObdConnectionController(
            manager = manager,
            scope = this,
            dispatchers = dispatchers,
            logTag = "TestController"
        )

        controller.connectAsync()
        advanceUntilIdle()

        controller.disconnectAsync()
        advanceUntilIdle()

        assertEquals(ObdSessionState.Idle, manager.snapshot.value.sessionState)
        assertEquals(1, gateway.cancelCalls)
        assertEquals(1, discovery.stopCalls)

        manager.shutdown()
    }

    private class FakeSessionGateway(
        initialState: ObdSessionState = ObdSessionState.Idle
    ) : ObdConnectionManager.SessionGateway {

        private val backingState = MutableStateFlow(initialState)
        var nextResult: ObdSessionState = initialState
        var startCalls: Int = 0
        var cancelCalls: Int = 0

        override val state: StateFlow<ObdSessionState> = backingState

        override suspend fun startWithAdapter(
            adapter: ConnectedAdapter,
            scannerConfig: BleScannerConfig
        ): ObdSessionState {
            startCalls += 1
            backingState.value = nextResult
            return nextResult
        }

        override suspend fun cancel() {
            cancelCalls += 1
            backingState.value = ObdSessionState.Idle
        }
    }

    private class FakeAdapterDiscovery : ObdConnectionManager.AdapterDiscovery {
        var latestAdapter: ConnectedAdapter? = null
        var startCalls: Int = 0
        var stopCalls: Int = 0

        override suspend fun start(config: BleScannerConfig) {
            startCalls += 1
        }

        override fun latest(config: BleScannerConfig): ConnectedAdapter? = latestAdapter

        override suspend fun await(
            config: BleScannerConfig,
            timeoutMs: Long
        ): ConnectedAdapter? = latestAdapter

        override suspend fun stop() {
            stopCalls += 1
        }

        override fun close() {
            stopCalls += 1
        }
    }
}
