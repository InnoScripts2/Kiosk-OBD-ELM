package com.selfservice.obd.core.connection

import com.selfservice.core.DispatchersProvider
import com.selfservice.obd.core.session.ConnectedAdapter
import com.selfservice.obd.core.session.ObdSessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ObdConnectionManagerTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatchersProvider {
        override val io: CoroutineDispatcher = dispatcher
        override val computation: CoroutineDispatcher = dispatcher
        override val main: CoroutineDispatcher = dispatcher
    }
    private val time = TestTime()
    private val adapter = ConnectedAdapter(
        device = BleDevice(address = "00:11:22:33:44:55", name = "Ediag", rssi = -50),
        protocol = "BLE"
    )

    @Test
    fun connectWithProvidedAdapterUpdatesSnapshot() = runTest(dispatcher) {
        val gateway = FakeSessionGateway()
        gateway.nextResult = ObdSessionState.Ready(adapter.device)
        val discovery = FakeAdapterDiscovery()
        discovery.latestAdapter = adapter
        val manager = ObdConnectionManager(
            sessionGateway = gateway,
            adapterDiscovery = discovery,
            dispatchers = dispatchers,
            timeProvider = time::now
        )
    time.value = 1_234L

        val snapshot = manager.connect()
        advanceUntilIdle()

        assertEquals(ObdSessionState.Ready(adapter.device), snapshot.sessionState)
        assertSame(adapter, snapshot.adapter)
        assertEquals(1_234L, snapshot.lastConnectedAtMillis)
        assertEquals(0, snapshot.reconnectAttempts)
        assertEquals(1, discovery.startCalls)
        assertEquals(1, gateway.startCalls)

        manager.shutdown()
    }

    @Test
    fun connectReturnsExistingSnapshotWhenBusyAndNotForced() = runTest(dispatcher) {
        val gateway = FakeSessionGateway()
        gateway.nextResult = ObdSessionState.Diagnostics(adapter.device)
        val discovery = FakeAdapterDiscovery()
        val manager = ObdConnectionManager(
            sessionGateway = gateway,
            adapterDiscovery = discovery,
            dispatchers = dispatchers,
            timeProvider = time::now
        )

        manager.connect(adapter = adapter)
        advanceUntilIdle()

    val busyState = ObdSessionState.Diagnostics(adapter.device)
    gateway.emit(busyState)
        advanceUntilIdle()

        val snapshot = manager.connect(force = false, adapter = adapter)
        advanceUntilIdle()

        assertEquals(busyState, snapshot.sessionState)
        assertEquals(1, gateway.startCalls)
        assertEquals(1, discovery.startCalls)

        manager.shutdown()
    }

    @Test
    fun connectThrowsWhenAdapterUnavailable() = runTest(dispatcher) {
        val gateway = FakeSessionGateway()
        val discovery = FakeAdapterDiscovery()
        discovery.latestAdapter = null
        discovery.awaitedAdapter = null
        val manager = ObdConnectionManager(
            sessionGateway = gateway,
            adapterDiscovery = discovery,
            dispatchers = dispatchers,
            timeProvider = time::now
        )
    time.value = 9_000L

        assertFailsWith<AdapterUnavailableException> {
            manager.connect(adapter = null)
        }
        advanceUntilIdle()

        val snapshot = manager.snapshot.value
        assertEquals(ObdSessionState.Idle, snapshot.sessionState)
        assertNull(snapshot.adapter)
        assertTrue(snapshot.lastError is AdapterUnavailableException)
        assertEquals(9_000L, snapshot.lastFailureAtMillis)
        assertEquals(1, snapshot.reconnectAttempts)
        assertEquals(1, discovery.startCalls)
        assertEquals(1, discovery.stopCalls)

        manager.shutdown()
    }

    @Test
    fun disconnectCancelsSessionAndStopsDiscovery() = runTest(dispatcher) {
        val gateway = FakeSessionGateway()
        gateway.nextResult = ObdSessionState.Ready(adapter.device)
        val discovery = FakeAdapterDiscovery()
        val manager = ObdConnectionManager(
            sessionGateway = gateway,
            adapterDiscovery = discovery,
            dispatchers = dispatchers,
            timeProvider = time::now
        )

        manager.connect(adapter = adapter)
        advanceUntilIdle()

        val snapshot = manager.disconnect()
        advanceUntilIdle()

        assertEquals(ObdSessionState.Idle, snapshot.sessionState)
        assertNull(snapshot.adapter)
        assertEquals(1, discovery.stopCalls)
        assertEquals(1, gateway.cancelCalls)

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

        fun emit(state: ObdSessionState) {
            backingState.value = state
        }
    }

    private class FakeAdapterDiscovery : ObdConnectionManager.AdapterDiscovery {
        var latestAdapter: ConnectedAdapter? = null
        var awaitedAdapter: ConnectedAdapter? = null
        var startCalls: Int = 0
        var stopCalls: Int = 0

        override suspend fun start(config: BleScannerConfig) {
            startCalls += 1
        }

        override fun latest(config: BleScannerConfig): ConnectedAdapter? = latestAdapter

        override suspend fun await(config: BleScannerConfig, timeoutMs: Long): ConnectedAdapter? = awaitedAdapter

        override suspend fun stop() {
            stopCalls += 1
        }

        override fun close() {
            stopCalls += 1
        }
    }

    private class TestTime {
        var value: Long = 0L
        fun now(): Long = value
    }
}
