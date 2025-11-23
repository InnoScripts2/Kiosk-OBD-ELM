package com.selfservice.obd.core.passthru

import com.selfservice.core.DispatchersProvider
import com.selfservice.obd.core.connection.BleAdapterSelector
import com.selfservice.obd.core.connection.BleScannerConfig
import com.selfservice.obd.core.connection.TransportFactory
import com.selfservice.obd.core.dictionary.DictionarySyncCoordinator
import com.selfservice.obd.core.dictionary.DictionarySyncSchedule
import com.selfservice.obd.core.platform.BluetoothPrerequisitesUseCase
import com.selfservice.obd.core.recovery.BleReconnectCoordinator
import com.selfservice.obd.core.session.BleSessionStateMachine
import com.selfservice.obd.core.session.DefaultObdSessionController
import com.selfservice.obd.core.session.ObdSessionController
import com.selfservice.obd.core.session.telemetry.ObdSessionTelemetry
import kotlinx.coroutines.Job

/** Bundles PassThru session dependencies and exposes a ready-to-use manager/controller pair. */
class PassThruSessionEnvironment(
        dispatchers: DispatchersProvider,
        bridgeProvider: () -> PassThruNativeBridge = PassThruNativeBridgeRegistry::resolve,
        transportConfig: PassThruTransportFactoryConfig = PassThruTransportFactoryConfig(),
        defaultScanner: BleScannerConfig = BleScannerConfig.Default,
        retryCount: Int = 3,
        reconnectDelayMs: Long = 2_000L,
        sessionTimeouts: BleSessionStateMachine.SessionTimeouts =
                BleSessionStateMachine.SessionTimeouts(),
        reconnectCoordinator: BleReconnectCoordinator = BleReconnectCoordinator(),
        adapterSelector: BleAdapterSelector = BleAdapterSelector.Empty,
        telemetry: ObdSessionTelemetry = ObdSessionTelemetry.NoOp,
        prerequisitesUseCase: BluetoothPrerequisitesUseCase? = null,
        timeProvider: () -> Long = { System.currentTimeMillis() },
        dictionarySyncCoordinator: DictionarySyncCoordinator? = null,
        dictionarySyncSchedule: DictionarySyncSchedule? = null,
        dictionarySyncScheduler: ((DictionarySyncCoordinator, DictionarySyncSchedule) -> Job)? = null,
        dictionarySyncCoordinatorOwned: Boolean = true,
        controllerBuilder: () -> ObdSessionController = {
            DefaultObdSessionController(
                    dispatchers = dispatchers,
                    timeouts = sessionTimeouts,
                    reconnectCoordinator = reconnectCoordinator,
                    timeProvider = timeProvider,
                    adapterSelector = adapterSelector,
                    telemetry = telemetry,
                    prerequisitesUseCase = prerequisitesUseCase
            )
        },
        transportFactoryOverride: TransportFactory? = null
) {

    val dispatchers: DispatchersProvider = dispatchers

    val controller: ObdSessionController = controllerBuilder()

    val configFactory: PassThruSessionConfigFactory =
            PassThruSessionConfigFactory(
                    dispatchers = dispatchers,
                    bridgeProvider = bridgeProvider,
                    transportConfig = transportConfig,
                    defaultScanner = defaultScanner,
                    retryCount = retryCount,
                    reconnectDelayMs = reconnectDelayMs,
                    transportFactoryOverride = transportFactoryOverride
            )

    private val syncCoordinator: DictionarySyncCoordinator? = dictionarySyncCoordinator
    private val ownsDictionarySyncCoordinator: Boolean = dictionarySyncCoordinatorOwned

    private val dictionarySyncJob =
                        if (syncCoordinator != null && dictionarySyncSchedule != null) {
                val scheduler =
                        dictionarySyncScheduler
                                ?: { coordinator: DictionarySyncCoordinator,
                                    schedule: DictionarySyncSchedule ->
                                    coordinator.schedulePeriodicSync(
                                            intervalMillis = schedule.intervalMillis,
                                            initialDelayMillis = schedule.initialDelayMillis,
                                            force = schedule.forceOnSchedule
                                    )
                                }
                                scheduler.invoke(syncCoordinator, dictionarySyncSchedule)
            } else {
                null
            }

    val manager: PassThruSessionManager =
            PassThruSessionManager(
                    controller = controller,
                    configFactory = configFactory,
                                        dictionarySyncCoordinator = syncCoordinator
            )

    fun shutdown() {
        dictionarySyncJob?.cancel()
                if (ownsDictionarySyncCoordinator) {
                        syncCoordinator?.shutdown()
                }
    }
}
