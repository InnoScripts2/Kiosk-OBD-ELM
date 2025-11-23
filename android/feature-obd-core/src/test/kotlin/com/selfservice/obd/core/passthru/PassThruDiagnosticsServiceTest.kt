package com.selfservice.obd.core.passthru

import com.selfservice.core.DispatchersProvider
import com.selfservice.obd.core.connection.BleDevice
import com.selfservice.obd.core.diagnostics.ObdDiagnosticsRequest
import com.selfservice.obd.core.diagnostics.ObdDiagnosticsResult
import com.selfservice.obd.core.session.ConnectedAdapter
import com.selfservice.obd.core.session.ObdSessionConfig
import com.selfservice.obd.core.session.ObdSessionController
import com.selfservice.obd.core.session.ObdSessionState
import com.selfservice.obd.core.transport.ObdTransport
import kotlin.test.Test
import kotlin.test.assertSame
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

private class ServiceDispatchers(private val dispatcher: CoroutineDispatcher) :
        DispatchersProvider {
    override val io: CoroutineDispatcher = dispatcher
    override val computation: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}

private class RecordingServiceController : ObdSessionController {
    private val backingState = MutableStateFlow<ObdSessionState>(ObdSessionState.Idle)
    var startedWith: Pair<ObdSessionConfig, ObdTransport>? = null

    override val state: StateFlow<ObdSessionState> = backingState

    override suspend fun start(config: ObdSessionConfig, transport: ObdTransport): ObdSessionState {
        startedWith = config to transport
        backingState.value = ObdSessionState.Ready(config.initialAdapter?.device ?: device())
        return backingState.value
    }

    override suspend fun onTransportReady(adapter: ConnectedAdapter) {}

    override suspend fun onHandshakeCompleted() {}

    override suspend fun beginDiagnostics() {}

    override suspend fun recordDiagnosticsHeartbeat() {}

    override suspend fun completeSuccessfully() {
        backingState.value = ObdSessionState.Completed
    }

    override suspend fun fail(cause: Throwable) {
        backingState.value = ObdSessionState.Failed(cause)
    }

    override suspend fun onConnectionIssue(
            issue: com.selfservice.obd.core.recovery.BleReconnectCoordinator.ConnectionIssue,
            cause: Throwable?,
            adapter: ConnectedAdapter?
    ): com.selfservice.obd.core.recovery.BleReconnectCoordinator.RecoveryDecision {
        return com.selfservice.obd.core.recovery.BleReconnectCoordinator.RecoveryDecision.Abort
    }

    override suspend fun cancel() {}

    private fun device(): BleDevice =
            BleDevice(address = "AA:BB:CC", name = "Passthru", rssi = null)
}

class PassThruDiagnosticsServiceTest {

    @Test
    fun runDiagnosticsDelegatesExecutor() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = ServiceDispatchers(dispatcher)
        val bridge = FakePassThruBridge()
        val controller = RecordingServiceController()
        val environment =
                PassThruSessionEnvironment(
                        dispatchers = dispatchers,
                        bridgeProvider = { bridge },
                        controllerBuilder = { controller }
                )
        val diagnostics =
                ObdDiagnosticsResult(
                        pidSamples = emptyList(),
                        troubleCodes = null,
                        clearPerformed = null,
                        failures = emptyList(),
                        durationMillis = 0L
                )
        var capturedTransport: ObdTransport? = null
        val service =
                PassThruDiagnosticsService(
                        environmentProvider = { environment },
                        executorProvider = {
                            PassThruDiagnosticsCoordinator.DiagnosticsExecutor { transport, _ ->
                                capturedTransport = transport
                                diagnostics
                            }
                        }
                )
        val adapter =
                ConnectedAdapter(
                        device = BleDevice("00:11:22", "Passthru", null),
                        protocol = "J2534"
                )

        val result = service.runDiagnostics(adapter, ObdDiagnosticsRequest())

        assertSame(diagnostics, result.diagnostics)
        assertSame(controller.startedWith?.second, capturedTransport)
    }
}
