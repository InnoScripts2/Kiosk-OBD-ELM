package com.selfservice.obd.core.passthru

import com.selfservice.obd.core.connection.BleDevice
import com.selfservice.obd.core.recovery.BleReconnectCoordinator
import com.selfservice.obd.core.session.ConnectedAdapter
import com.selfservice.obd.core.session.ObdSessionConfig
import com.selfservice.obd.core.session.ObdSessionController
import com.selfservice.obd.core.session.ObdSessionState
import com.selfservice.obd.core.transport.ObdTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class TestRecordingObdSessionController(
        private val defaultDeviceProvider: () -> BleDevice = {
            BleDevice(address = "00:11:22:33:44:55", name = "Passthru", rssi = null)
        }
) : ObdSessionController {

    private val backingState = MutableStateFlow<ObdSessionState>(ObdSessionState.Idle)
    var startedWith: Pair<ObdSessionConfig, ObdTransport>? = null
    var nextStartState: ObdSessionState? = null
    var failCount: Int = 0
    var connectionDecision: BleReconnectCoordinator.RecoveryDecision =
            BleReconnectCoordinator.RecoveryDecision.Retry(
                    attempt = 1,
                    maxAttempts = 1,
                    delayMillis = 0L,
                    jitterMillis = 0L
            )

    override val state: StateFlow<ObdSessionState> = backingState

    override suspend fun start(config: ObdSessionConfig, transport: ObdTransport): ObdSessionState {
        startedWith = config to transport
        val resolvedState =
                nextStartState
                        ?: ObdSessionState.Ready(
                                config.initialAdapter?.device ?: defaultDeviceProvider()
                        )
        backingState.value = resolvedState
        return resolvedState
    }

    override suspend fun onTransportReady(adapter: ConnectedAdapter) {}

    override suspend fun onHandshakeCompleted() {}

    override suspend fun beginDiagnostics() {}

    override suspend fun recordDiagnosticsHeartbeat() {}

    override suspend fun completeSuccessfully() {
        backingState.value = ObdSessionState.Completed
    }

    override suspend fun fail(cause: Throwable) {
        failCount += 1
        backingState.value = ObdSessionState.Failed(cause)
    }

    override suspend fun onConnectionIssue(
            issue: BleReconnectCoordinator.ConnectionIssue,
            cause: Throwable?,
            adapter: ConnectedAdapter?
    ): BleReconnectCoordinator.RecoveryDecision = connectionDecision

    override suspend fun cancel() {}
}
