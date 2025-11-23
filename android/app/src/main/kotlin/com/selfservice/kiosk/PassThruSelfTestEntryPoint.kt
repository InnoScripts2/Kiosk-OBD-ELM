package com.selfservice.kiosk

import android.content.Context
import com.selfservice.core.DefaultDispatchersProvider
import com.selfservice.core.DispatchersProvider
import com.selfservice.kiosk.dictionary.ManufacturerDictionaryInstaller
import com.selfservice.obd.core.dictionary.DictionarySyncCoordinator
import com.selfservice.obd.core.dictionary.DictionarySyncDefaults
import com.selfservice.obd.core.dictionary.DictionarySyncPolicy
import com.selfservice.obd.core.dictionary.DictionarySyncSchedule
import com.selfservice.obd.core.dictionary.DictionaryUpdateProvider
import com.selfservice.obd.core.dictionary.ObdDictionaryManager
import com.selfservice.obd.core.dictionary.ObdDictionarySynchronizer
import com.selfservice.obd.core.passthru.PassThruNativeBridge
import com.selfservice.obd.core.passthru.PassThruNativeBridgeRegistry
import com.selfservice.obd.core.passthru.PassThruSelfTestExecutor
import com.selfservice.obd.core.passthru.PassThruSelfTestMetadataProvider
import com.selfservice.obd.core.passthru.PassThruSelfTestService
import com.selfservice.obd.core.passthru.PassThruSessionEnvironment
import com.selfservice.obd.core.selftest.AdapterSelfTestUploader
import com.selfservice.obd.core.selftest.FileSelfTestResultDao
import com.selfservice.obd.core.selftest.SelfTestResultDao
import com.selfservice.obd.core.session.ConnectedAdapter
import com.selfservice.obd.core.session.ObdSessionController
import com.selfservice.obd.core.session.telemetry.ObdSessionTelemetry
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Constructs PassThru self-test infrastructure for the kiosk runtime. Designed to be overrideable
 * in tests similarly to [BluetoothPrerequisitesEntryPoint].
 */
object PassThruSelfTestEntryPoint {

    data class Config(
            val storageDirectory: File,
            val adapterProvider: () -> ConnectedAdapter,
            val stepHandler: PassThruSelfTestExecutor.StepHandler,
            val bridgeProvider: () -> PassThruNativeBridge = PassThruNativeBridgeRegistry::resolve,
            val dispatchers: DispatchersProvider = DefaultDispatchersProvider(),
            val dictionaryUpdateProvider: DictionaryUpdateProvider = DictionaryUpdateProvider { null },
            val metadataProvider: PassThruSelfTestService.MetadataProvider = PassThruSelfTestMetadataProvider(),
            val dictionarySchedule: DictionarySyncSchedule? = DictionarySyncDefaults.schedule,
            val dictionaryPolicy: DictionarySyncPolicy = DictionarySyncDefaults.policy,
            val dictionarySyncCoordinator: DictionarySyncCoordinator? = null,
            val resultDaoFactory: (File) -> SelfTestResultDao = { dir -> FileSelfTestResultDao(dir) },
            val controllerBuilder: (() -> ObdSessionController)? = null,
            val telemetry: ObdSessionTelemetry = ObdSessionTelemetry.NoOp
    )

    private val overrideFactory: AtomicReference<(Config) -> PassThruSelfTestHandle?> =
            AtomicReference(null)

    fun override(factory: (Config) -> PassThruSelfTestHandle?) {
        overrideFactory.set(factory)
    }

    fun resetOverride() {
        overrideFactory.set(null)
    }

    fun create(
            context: Context,
            adapterProvider: () -> ConnectedAdapter,
            stepHandler: PassThruSelfTestExecutor.StepHandler
    ): PassThruSelfTestHandle {
        val directory = File(context.filesDir, DEFAULT_STORAGE_SUBDIR)
    ManufacturerDictionaryInstaller.ensureInstalled(context)
        val config = Config(
                storageDirectory = directory,
                adapterProvider = adapterProvider,
                stepHandler = stepHandler
        )
        return create(config)
    }

    fun create(config: Config): PassThruSelfTestHandle {
        overrideFactory.get()?.let { factory ->
            val overridden = factory.invoke(config)
            if (overridden != null) {
                return overridden
            }
        }

        val (coordinator, ownsCoordinator) =
                config.dictionarySyncCoordinator?.let { existing ->
                    existing to false
                } ?: run {
                    val manager = ObdDictionaryManager.shared
                    val synchronizer =
                            ObdDictionarySynchronizer(
                                    manager = manager,
                                    provider = config.dictionaryUpdateProvider,
                                    policy = config.dictionaryPolicy
                            )
                    DictionarySyncCoordinator(synchronizer, config.dispatchers) to true
                }
        val environment =
                if (config.controllerBuilder != null) {
                    PassThruSessionEnvironment(
                            dispatchers = config.dispatchers,
                            bridgeProvider = config.bridgeProvider,
                            telemetry = config.telemetry,
                            dictionarySyncCoordinator = coordinator,
                            dictionarySyncSchedule = config.dictionarySchedule,
                            dictionarySyncCoordinatorOwned = ownsCoordinator,
                            controllerBuilder = config.controllerBuilder
                    )
                } else {
                    PassThruSessionEnvironment(
                            dispatchers = config.dispatchers,
                            bridgeProvider = config.bridgeProvider,
                            telemetry = config.telemetry,
                            dictionarySyncCoordinator = coordinator,
                            dictionarySyncSchedule = config.dictionarySchedule,
                            dictionarySyncCoordinatorOwned = ownsCoordinator
                    )
                }
        val resultDao = config.resultDaoFactory(config.storageDirectory)
        val uploader = AdapterSelfTestUploader(resultDao)
        val service =
                PassThruSelfTestService(
                        environmentProvider = { environment },
                        adapterProvider = config.adapterProvider,
                        stepHandler = config.stepHandler,
                        metadataProvider = config.metadataProvider,
                        uploader = uploader
                )
        return PassThruSelfTestHandle(
                service = service,
                environment = environment,
                dictionarySyncCoordinator = coordinator,
                resultStorageDirectory = config.storageDirectory
        )
    }

    private const val DEFAULT_STORAGE_SUBDIR = "selftests"
}

class PassThruSelfTestHandle
internal constructor(
        val service: PassThruSelfTestService,
        private val environment: PassThruSessionEnvironment,
        val dictionarySyncCoordinator: DictionarySyncCoordinator,
        val resultStorageDirectory: File
) : Closeable {

    override fun close() {
        shutdown()
    }

    fun shutdown() {
        environment.shutdown()
    }
}
