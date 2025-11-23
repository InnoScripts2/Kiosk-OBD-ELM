package com.selfservice.kiosk.selftest

import android.content.Context
import com.selfservice.core.DefaultDispatchersProvider
import com.selfservice.core.DispatchersProvider
import com.selfservice.kiosk.PassThruSelfTestEntryPoint
import com.selfservice.kiosk.PassThruSelfTestHandle
import com.selfservice.kiosk.dictionary.ManufacturerDictionaryInstaller
import com.selfservice.kiosk.supabase.SupabaseOutboxSelfTestResultDao
import com.selfservice.kiosk.supabase.SupabaseOutboxUploader
import com.selfservice.kiosk.supabase.SupabaseOutboxWriter
import com.selfservice.obd.core.dictionary.DictionarySyncDefaults
import com.selfservice.obd.core.dictionary.DictionarySyncPolicy
import com.selfservice.obd.core.dictionary.DictionarySyncSchedule
import com.selfservice.obd.core.dictionary.DictionarySyncState
import com.selfservice.obd.core.dictionary.DictionarySyncSupervisor
import com.selfservice.obd.core.dictionary.DictionaryUpdateProvider
import com.selfservice.obd.core.dictionary.ObdDictionaryManager
import com.selfservice.obd.core.passthru.PassThruNativeBridge
import com.selfservice.obd.core.passthru.PassThruNativeBridgeRegistry
import com.selfservice.obd.core.passthru.PassThruSelfTestExecutor
import com.selfservice.obd.core.passthru.PassThruSelfTestMetadataProvider
import com.selfservice.obd.core.passthru.PassThruSelfTestService
import com.selfservice.obd.core.passthru.PassThruSmokeOrchestrator
import com.selfservice.obd.core.passthru.PassThruSmokePlan
import com.selfservice.obd.core.passthru.PassThruSmokeRunner
import com.selfservice.obd.core.passthru.PassThruSmokeService
import com.selfservice.obd.core.selftest.SelfTestResultDao
import com.selfservice.obd.core.session.ConnectedAdapter
import com.selfservice.obd.core.session.ObdSessionController
import com.selfservice.obd.core.session.telemetry.ObdSessionTelemetry
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

/**
 * Configures and exposes PassThru self-test infrastructure for the kiosk application. Keeps
 * storage, dictionary policy and bridge wiring in a single place so UI flows can request handles
 * without duplicating setup code.
 */
class PassThruSelfTestRuntime(
    context: Context,
    private val dispatchers: DispatchersProvider = DefaultDispatchersProvider(),
    private val dictionaryUpdateProvider: DictionaryUpdateProvider = DictionaryUpdateProvider { null },
    private val metadataProvider: PassThruSelfTestService.MetadataProvider = PassThruSelfTestMetadataProvider(),
    private val dictionarySchedule: DictionarySyncSchedule? = DictionarySyncDefaults.schedule,
    private val dictionaryPolicy: DictionarySyncPolicy = DictionarySyncDefaults.policy,
    private val resultDaoFactory: ((File, () -> Unit) -> SelfTestResultDao)? = null,
    private val bridgeProvider: () -> PassThruNativeBridge = PassThruNativeBridgeRegistry::resolve,
    private val controllerBuilder: (() -> ObdSessionController)? = null,
    private val telemetry: ObdSessionTelemetry = ObdSessionTelemetry.NoOp,
    private val stepHandlerFactory: () -> PassThruSelfTestExecutor.StepHandler =
        { PassThruSelfTestStepLibrary().createStepHandler() }
) {

    private val storageDirectory = File(context.filesDir, STORAGE_SUBDIR)
    private val outboxObservers: MutableList<() -> Unit> = CopyOnWriteArrayList()
    private val sharedOutboxWriter: SupabaseOutboxWriter by lazy {
        SupabaseOutboxWriter(directory = outboxDirectory(), onEnqueued = { notifyOutboxObservers() })
    }
    private val resolvedResultDaoFactory: (File, () -> Unit) -> SelfTestResultDao =
            resultDaoFactory ?: { _, _ -> SupabaseOutboxSelfTestResultDao(sharedOutboxWriter) }
    private val runtimeScope = CoroutineScope(SupervisorJob() + dispatchers.io)
    init {
        ManufacturerDictionaryInstaller.ensureInstalled(context)
    }
    private val dictionarySupervisor =
        DictionarySyncSupervisor(
            dispatchers = dispatchers,
            scope = runtimeScope,
            updateProvider = dictionaryUpdateProvider,
            schedule = dictionarySchedule,
            policy = dictionaryPolicy,
            manager = ObdDictionaryManager.shared
        )

    fun createHandle(adapterProvider: () -> ConnectedAdapter): PassThruSelfTestHandle =
            createHandle(adapterProvider = adapterProvider, stepHandler = stepHandlerFactory())

    fun createHandle(
            adapterProvider: () -> ConnectedAdapter,
            stepHandler: PassThruSelfTestExecutor.StepHandler
    ): PassThruSelfTestHandle {
        val notifier = { notifyOutboxObservers() }
        val config =
                PassThruSelfTestEntryPoint.Config(
                        storageDirectory = storageDirectory,
                        adapterProvider = adapterProvider,
                        stepHandler = stepHandler,
                        bridgeProvider = bridgeProvider,
                        dispatchers = dispatchers,
                        dictionaryUpdateProvider = dictionaryUpdateProvider,
                        metadataProvider = metadataProvider,
                        dictionarySchedule = null,
                        dictionaryPolicy = dictionaryPolicy,
                        dictionarySyncCoordinator = dictionarySupervisor.coordinator,
                        resultDaoFactory = { root ->
                            val directory = File(root, OUTBOX_SUBDIR).apply { mkdirs() }
                            resolvedResultDaoFactory(directory, notifier)
                        },
                        controllerBuilder = controllerBuilder,
                        telemetry = telemetry
                )
        return PassThruSelfTestEntryPoint.create(config)
    }

    fun createOutboxUploader(client: SupabaseOutboxUploader.Client): SupabaseOutboxUploader {
        val outboxDirectory = File(storageDirectory, OUTBOX_SUBDIR).apply { mkdirs() }
        return SupabaseOutboxUploader(directory = outboxDirectory, client = client)
    }

    fun outboxDirectory(): File = File(storageDirectory, OUTBOX_SUBDIR).apply { mkdirs() }

    fun outboxWriter(): SupabaseOutboxWriter = sharedOutboxWriter

    fun registerOutboxObserver(observer: () -> Unit) {
        outboxObservers += observer
    }

    fun unregisterOutboxObserver(observer: () -> Unit) {
        outboxObservers -= observer
    }

    fun dictionarySyncState(): StateFlow<DictionarySyncState> = dictionarySupervisor.state()

    fun requestDictionarySync(force: Boolean = false) {
        dictionarySupervisor.requestSync(force)
    }

    fun runSmokeTest(
            plan: PassThruSmokePlan = PassThruSmokePlan.default(),
            payloadSupplier: () -> ByteArray = { ByteArray(0) },
            timeoutMillis: Long = DEFAULT_SMOKE_TIMEOUT_MS
    ): PassThruSmokeRunner.Result {
        val service = PassThruSmokeService(bridgeProvider)
        val orchestrator = PassThruSmokeOrchestrator(dispatchers, service)
        return runBlocking(dispatchers.io) {
            orchestrator.run(plan, payloadSupplier, timeoutMillis)
        }
    }

    fun shutdown() {
        dictionarySupervisor.close()
        runtimeScope.cancel()
        outboxObservers.clear()
    }

    private fun notifyOutboxObservers() {
        outboxObservers.forEach { observer ->
            try {
                observer.invoke()
            } catch (_: Throwable) {
                // Observers should not throw; ignore to avoid impacting self-test flow
            }
        }
    }

    companion object {
        private const val STORAGE_SUBDIR = "selftests"
        private const val OUTBOX_SUBDIR = "outbox"
        private const val DEFAULT_SMOKE_TIMEOUT_MS = 30_000L
    }
}
