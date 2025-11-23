package com.selfservice.obd.core.passthru

import com.selfservice.core.DispatchersProvider
import com.selfservice.obd.core.connection.BleDevice
import com.selfservice.obd.core.diagnostics.ObdDiagnosticsRequest
import com.selfservice.obd.core.diagnostics.ObdDiagnosticsResult
import com.selfservice.obd.core.session.ConnectedAdapter
import com.selfservice.obd.core.session.ObdSessionState
import com.selfservice.obd.core.transport.ObdTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

private class CoordinatorDispatchers(private val dispatcher: CoroutineDispatcher) :
        DispatchersProvider {
    override val io: CoroutineDispatcher = dispatcher
    override val computation: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}

class PassThruDiagnosticsCoordinatorTest {

    @Test
    fun runDiagnosticsBootstrapsSessionAndDelegatesToExecutor() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = CoordinatorDispatchers(dispatcher)
        val bridge = FakePassThruBridge()
        val controller =
                TestRecordingObdSessionController().apply {
                    nextStartState = ObdSessionState.Ready(device())
                }
        val environment =
                PassThruSessionEnvironment(
                        dispatchers = dispatchers,
                        bridgeProvider = { bridge },
                        controllerBuilder = { controller }
                )
        val captured = mutableListOf<ObdTransport>()
        val diagnosticsResult =
                ObdDiagnosticsResult(
                        pidSamples = emptyList(),
                        troubleCodes = null,
                        clearPerformed = null,
                        failures = emptyList(),
                        durationMillis = 0L
                )
        val coordinator =
                PassThruDiagnosticsCoordinator(
                        environment,
                        PassThruDiagnosticsCoordinator.DiagnosticsExecutor { transport, _ ->
                            captured += transport
                            diagnosticsResult
                        }
                )
        val adapter = ConnectedAdapter(device = device(), protocol = "J2534")

        val result = coordinator.runDiagnostics(adapter, ObdDiagnosticsRequest())

        assertSame(diagnosticsResult, result.diagnostics)
        assertEquals(controller.nextStartState, result.sessionState)
        val started = controller.startedWith
        assertSame(started?.second, captured.single())
    }

    private fun device(): BleDevice =
            BleDevice(address = "AA:BB:CC:DD:EE:FF", name = "Passthru", rssi = null)
}
