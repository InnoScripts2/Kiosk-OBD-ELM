package com.selfservice.kiosk.diagnostics

import android.content.Context
import com.selfservice.core.DefaultDispatchersProvider
import com.selfservice.core.DispatchersProvider
import com.selfservice.kiosk.dictionary.LocalDictionaryUpdateProvider
import com.selfservice.obd.core.connection.BleAdapterManager
import com.selfservice.obd.core.connection.ObdConnectionManager
import com.selfservice.obd.core.dictionary.DictionarySyncDefaults
import com.selfservice.obd.core.dictionary.DictionarySyncPolicy
import com.selfservice.obd.core.dictionary.DictionarySyncSchedule
import com.selfservice.obd.core.dictionary.DictionaryUpdateProvider
import com.selfservice.obd.core.diagnostics.ObdDiagnosticsProcessor
import com.selfservice.obd.core.passthru.PassThruDiagnosticsCoordinator
import com.selfservice.obd.core.passthru.PassThruNativeBridge
import com.selfservice.obd.core.passthru.PassThruNativeBridgeRegistry
import com.selfservice.obd.core.passthru.PassThruSessionManager
import com.selfservice.obd.core.session.ObdSessionController
import com.selfservice.obd.core.session.telemetry.ObdSessionTelemetry
import com.selfservice.obd.core.connection.TransportFactory
import com.selfservice.obd.core.transport.ble.BleGattTransportConfig
import java.util.concurrent.atomic.AtomicReference

/** Factory wrapper that allows instrumentation tests to override diagnostics runtime wiring. */
object PassThruDiagnosticsRuntimeProvider {

    data class Config(
            val context: Context,
            val dispatchers: DispatchersProvider = DefaultDispatchersProvider(),
            val dictionaryUpdateProvider: DictionaryUpdateProvider =
                    LocalDictionaryUpdateProvider(context),
            val dictionarySchedule: DictionarySyncSchedule? = DictionarySyncDefaults.schedule,
            val dictionaryPolicy: DictionarySyncPolicy = DictionarySyncDefaults.policy,
            val diagnosticsExecutorFactory: () -> PassThruDiagnosticsCoordinator.DiagnosticsExecutor = {
                val processor = ObdDiagnosticsProcessor()
                PassThruDiagnosticsCoordinator.DiagnosticsExecutor { transport, request ->
                    processor.run(transport, request)
                }
            },
            val bridgeProvider: () -> PassThruNativeBridge = PassThruNativeBridgeRegistry::resolve,
            val telemetry: ObdSessionTelemetry = ObdSessionTelemetry.NoOp,
        val controllerBuilder: (() -> ObdSessionController)? = null,
        val adapterManagerFactory: (() -> BleAdapterManager)? = null,
        val connectionManagerFactory: ((PassThruSessionManager, BleAdapterManager) -> ObdConnectionManager)? = null,
        val adapterDiscoveryFactory: ((BleAdapterManager) -> ObdConnectionManager.AdapterDiscovery)? = null,
            val timeProvider: () -> Long = { System.currentTimeMillis() },
            val transportFactoryOverride: TransportFactory? = null,
            val bleTransportConfig: BleGattTransportConfig = BleGattTransportConfig.Default
    )

    private val overrideFactory = AtomicReference<(Config) -> PassThruDiagnosticsRuntime?>(null)

    fun override(factory: (Config) -> PassThruDiagnosticsRuntime?) {
        overrideFactory.set(factory)
    }

    fun resetOverride() {
        overrideFactory.set(null)
    }

    fun create(config: Config): PassThruDiagnosticsRuntime {
        overrideFactory.get()?.let { factory ->
            factory.invoke(config)?.let { runtime -> return runtime }
        }
        return PassThruDiagnosticsRuntime(
                context = config.context,
                dispatchers = config.dispatchers,
                dictionaryUpdateProvider = config.dictionaryUpdateProvider,
                dictionarySchedule = config.dictionarySchedule,
                dictionaryPolicy = config.dictionaryPolicy,
                diagnosticsExecutorFactory = config.diagnosticsExecutorFactory,
                bridgeProvider = config.bridgeProvider,
                telemetry = config.telemetry,
                controllerBuilder = config.controllerBuilder,
                adapterManagerFactory = config.adapterManagerFactory,
                connectionManagerFactory = config.connectionManagerFactory,
                adapterDiscoveryFactory = config.adapterDiscoveryFactory,
                timeProvider = config.timeProvider,
                transportFactoryOverride = config.transportFactoryOverride,
                bleTransportConfig = config.bleTransportConfig
        )
    }
}
