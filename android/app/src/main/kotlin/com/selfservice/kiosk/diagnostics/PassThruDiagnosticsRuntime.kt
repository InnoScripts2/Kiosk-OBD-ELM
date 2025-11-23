package com.selfservice.kiosk.diagnostics

import android.content.Context
import android.util.Log
import com.selfservice.core.DefaultDispatchersProvider
import com.selfservice.core.DispatchersProvider
import com.selfservice.kiosk.dictionary.ManufacturerDictionaryInstaller
import com.selfservice.obd.core.connection.AndroidBleScanner
import com.selfservice.obd.core.connection.BleAdapterManager
import com.selfservice.obd.core.connection.BleScannerConfig
import com.selfservice.obd.core.connection.ObdConnectionManager
import com.selfservice.obd.core.connection.TransportFactory
import com.selfservice.obd.core.dictionary.DictionarySyncDefaults
import com.selfservice.obd.core.dictionary.DictionarySyncPolicy
import com.selfservice.obd.core.dictionary.DictionarySyncSchedule
import com.selfservice.obd.core.dictionary.DictionarySyncState
import com.selfservice.obd.core.dictionary.DictionarySyncSupervisor
import com.selfservice.obd.core.dictionary.DictionaryUpdateProvider
import com.selfservice.obd.core.dictionary.ObdDictionaryManager
import com.selfservice.obd.core.diagnostics.ObdDiagnosticsProcessor
import com.selfservice.obd.core.diagnostics.ObdDiagnosticsRequest
import com.selfservice.obd.core.passthru.PassThruDiagnosticsCoordinator
import com.selfservice.obd.core.passthru.PassThruDiagnosticsService
import com.selfservice.obd.core.passthru.PassThruNativeBridge
import com.selfservice.obd.core.passthru.PassThruNativeBridgeRegistry
import com.selfservice.obd.core.passthru.PassThruSessionEnvironment
import com.selfservice.obd.core.passthru.PassThruSessionManager
import com.selfservice.obd.core.session.ConnectedAdapter
import com.selfservice.obd.core.session.ObdSessionController
import com.selfservice.obd.core.session.telemetry.ObdSessionTelemetry
import com.selfservice.obd.core.transport.ble.AndroidBleGattClientFactory
import com.selfservice.obd.core.transport.ble.BleGattTransportConfig
import com.selfservice.obd.core.transport.ble.BleGattTransportFactory
import com.selfservice.obd.core.dictionary.DictionarySyncResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.plus

/**
 * Provides a production-facing runtime for running PassThru diagnostics sessions. The runtime
 * wires dictionary supervision, JNI bridge resolution and session orchestration so UI flows and
 * service layers can request diagnostics without duplicating setup logic.
 */
class PassThruDiagnosticsRuntime(
    context: Context,
        private val dispatchers: DispatchersProvider = DefaultDispatchersProvider(),
        private val dictionaryUpdateProvider: DictionaryUpdateProvider,
        private val dictionarySchedule: DictionarySyncSchedule? = DictionarySyncDefaults.schedule,
        private val dictionaryPolicy: DictionarySyncPolicy = DictionarySyncDefaults.policy,
        private val diagnosticsExecutorFactory: () -> PassThruDiagnosticsCoordinator.DiagnosticsExecutor = {
            val processor = ObdDiagnosticsProcessor()
            PassThruDiagnosticsCoordinator.DiagnosticsExecutor { transport, request ->
                processor.run(transport, request)
            }
        },
        private val bridgeProvider: () -> PassThruNativeBridge = PassThruNativeBridgeRegistry::resolve,
        private val telemetry: ObdSessionTelemetry = ObdSessionTelemetry.NoOp,
    private val controllerBuilder: (() -> ObdSessionController)? = null,
    private val adapterManagerFactory: (() -> BleAdapterManager)? = null,
    private val connectionManagerFactory: ((PassThruSessionManager, BleAdapterManager) -> ObdConnectionManager)? = null,
    private val adapterDiscoveryFactory: ((BleAdapterManager) -> ObdConnectionManager.AdapterDiscovery)? = null,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val transportFactoryOverride: TransportFactory? = null,
    private val bleTransportConfig: BleGattTransportConfig = BleGattTransportConfig.Default
) {

    private val appContext = context.applicationContext

    private val runtimeScope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val supervisor =
            DictionarySyncSupervisor(
                    dispatchers = dispatchers,
                    scope = runtimeScope,
                    updateProvider = dictionaryUpdateProvider,
                    schedule = dictionarySchedule,
                    policy = dictionaryPolicy,
                    manager = ObdDictionaryManager.shared
            )
    private val activeTransportFactory: TransportFactory by lazy {
        transportFactoryOverride
                ?: BleGattTransportFactory(
                        dispatchers = dispatchers,
                        clientFactory = AndroidBleGattClientFactory(appContext, dispatchers) { message, error ->
                            if (error != null) {
                                Log.w(TAG, message, error)
                            } else {
                                Log.d(TAG, message)
                            }
                        },
                        config = bleTransportConfig
                )
    }
    private val adapterManager: BleAdapterManager by lazy {
        adapterManagerFactory?.invoke()
                ?: BleAdapterManager(
                        scanner = AndroidBleScanner(appContext, dispatchers),
                        dispatchers = dispatchers
                )
    }
    private val adapterDiscovery: ObdConnectionManager.AdapterDiscovery by lazy {
        adapterDiscoveryFactory?.invoke(adapterManager)
                ?: ObdConnectionManager.adapterDiscovery(adapterManager, dispatchers)
    }
    private val environmentLazy = lazy {
        val coordinator = supervisor.coordinator
        if (controllerBuilder != null) {
            PassThruSessionEnvironment(
                    dispatchers = dispatchers,
                    bridgeProvider = bridgeProvider,
                    adapterSelector = adapterManager,
                    telemetry = telemetry,
                    dictionarySyncCoordinator = coordinator,
                    dictionarySyncSchedule = null,
                    dictionarySyncCoordinatorOwned = false,
                    controllerBuilder = controllerBuilder,
                    transportFactoryOverride = activeTransportFactory
            )
        } else {
            PassThruSessionEnvironment(
                    dispatchers = dispatchers,
                    bridgeProvider = bridgeProvider,
                    adapterSelector = adapterManager,
                    telemetry = telemetry,
                    dictionarySyncCoordinator = coordinator,
                    dictionarySyncSchedule = null,
                    dictionarySyncCoordinatorOwned = false,
                    transportFactoryOverride = activeTransportFactory
            )
        }
    }
    private val environment: PassThruSessionEnvironment by environmentLazy
    private val diagnosticsService: PassThruDiagnosticsService by lazy {
        PassThruDiagnosticsService(environmentProvider = { environment }, executorProvider = diagnosticsExecutorFactory)
    }
    private val connectionManagerLazy = lazy {
        val env = environment
        connectionManagerFactory?.invoke(env.manager, adapterManager)
                ?: ObdConnectionManager(
                        sessionGateway = ObdConnectionManager.sessionGateway(env.manager),
                        adapterDiscovery = adapterDiscovery,
                        dispatchers = dispatchers,
                        defaultScannerConfig = BleScannerConfig.Default,
                        timeProvider = timeProvider
                )
    }

    init {
        ManufacturerDictionaryInstaller.ensureInstalled(appContext)
    }

    fun diagnosticsService(): PassThruDiagnosticsService = diagnosticsService

    fun diagnosticsManager(): PassThruSessionManager = environment.manager

    fun connectionManager(): ObdConnectionManager = connectionManagerLazy.value

    suspend fun runDiagnostics(
            adapter: ConnectedAdapter,
            request: ObdDiagnosticsRequest
    ): PassThruDiagnosticsCoordinator.DiagnosticsRun {
        return diagnosticsService.runDiagnostics(adapter, request)
    }

    fun dictionarySyncState(): StateFlow<DictionarySyncState> = supervisor.state()

    fun requestDictionarySync(force: Boolean = false) {
        supervisor.requestSync(force)
    }

    fun lastDictionarySync(): DictionarySyncResult? = supervisor.lastResult

    suspend fun createTransport(adapter: ConnectedAdapter) =
            environment.manager.createTransport(adapter)

    fun transportFactory(): TransportFactory = activeTransportFactory

    fun sessionEnvironment(): PassThruSessionEnvironment = environment

    fun shutdown() {
        if (connectionManagerLazy.isInitialized()) {
            connectionManagerLazy.value.shutdown()
        }
        if (environmentLazy.isInitialized()) {
            environment.shutdown()
        }
        supervisor.close()
        runtimeScope.cancel()
    }

    companion object {
        private const val TAG = "PassThruDiagnosticsRuntime"
    }
}
