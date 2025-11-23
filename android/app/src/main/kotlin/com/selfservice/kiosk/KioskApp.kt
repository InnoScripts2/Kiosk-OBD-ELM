package com.selfservice.kiosk

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.selfservice.core.DefaultDispatchersProvider
import com.selfservice.core.DispatchersProvider
import com.selfservice.core.logging.DiagnosticsLogFileSink
import com.selfservice.feature.payments.FilePaymentAuditSink
import com.selfservice.feature.payments.MutablePaymentAuditSink
import com.selfservice.feature.payments.PaymentEnvironment
import com.selfservice.feature.payments.PaymentIntentStoreOptions
import com.selfservice.feature.payments.PaymentIntentStorePrunePolicy
import com.selfservice.feature.payments.PaymentLogger
import com.selfservice.feature.payments.PaymentModule
import com.selfservice.feature.payments.PaymentModuleOptions
import com.selfservice.feature.payments.combinePaymentAuditSinks
import com.selfservice.feature.reports.DiagnosticsReportCustomer
import com.selfservice.feature.reports.DiagnosticsReportGenerator
import com.selfservice.feature.reports.DiagnosticsReportVehicle
import com.selfservice.kiosk.diagnostics.PassThruDiagnosticsRuntime
import com.selfservice.kiosk.diagnostics.PassThruDiagnosticsRuntimeProvider
import com.selfservice.kiosk.diagnostics.ObdConnectionController
import com.selfservice.kiosk.diagnostics.DiagnosticsReportWorkflow
import com.selfservice.kiosk.dictionary.LocalDictionaryUpdateProvider
import com.selfservice.kiosk.dictionary.LocalDictionaryUpdateWatcher
import com.selfservice.kiosk.dictionary.ManufacturerDictionaryInstaller
import com.selfservice.kiosk.mdm.DeviceCommandSupabaseSchema
import com.selfservice.kiosk.mdm.DeviceEventSupabaseSchema
import com.selfservice.kiosk.mdm.DeviceIdentity
import com.selfservice.kiosk.mdm.DeviceStatusReporter
import com.selfservice.kiosk.mdm.DeviceStatusSnapshot
import com.selfservice.kiosk.mdm.DeviceStatusSupabaseSchema
import com.selfservice.kiosk.mdm.MdmCommand
import com.selfservice.kiosk.mdm.MdmCommandManager
import com.selfservice.kiosk.mdm.MdmCommandQueue
import com.selfservice.kiosk.mdm.DefaultMdmCommandHandler
import com.selfservice.platform.logging.DiagnosticsLogRetentionCoordinator
import com.selfservice.platform.logging.DiagnosticsLogService
import com.selfservice.platform.logging.DiagnosticsLogSupabaseSchema
import com.selfservice.kiosk.payments.PaymentGatewayResolver
import com.selfservice.kiosk.payments.PaymentsAuditSupabaseSchema
import com.selfservice.kiosk.reports.DiagnosticsReportRetentionCoordinator
import com.selfservice.kiosk.reports.DiagnosticsReportDeliverySupabaseSchema
import com.selfservice.kiosk.reports.DiagnosticsReportOutboxBridge
import com.selfservice.kiosk.reports.DiagnosticsReportSupabaseSchema
import com.selfservice.kiosk.selftest.DevMockPassThruBridge
import com.selfservice.kiosk.selftest.PassThruSelfTestRuntime
import com.selfservice.kiosk.selftest.PassThruSelfTestRuntimeProvider
import com.selfservice.kiosk.payments.SupabasePaymentAuditSink
import com.selfservice.kiosk.supabase.SupabaseCredentialsProvider
import com.selfservice.kiosk.supabase.SupabaseOutboxMonitor
import com.selfservice.kiosk.supabase.SupabaseOutboxStatusUiMapper
import com.selfservice.kiosk.supabase.SupabaseOutboxSync
import com.selfservice.kiosk.supabase.SupabaseOutboxWriter
import com.selfservice.kiosk.supabase.SupabaseRestClient
import com.selfservice.kiosk.telemetry.DiagnosticsTelemetryOutboxBridge
import com.selfservice.kiosk.telemetry.DiagnosticsTelemetrySupabaseSchema
import com.selfservice.obd.core.connection.ObdConnectionSnapshot
import com.selfservice.obd.core.dictionary.DictionarySyncState
import com.selfservice.obd.core.diagnostics.ObdDiagnosticsRequest
import com.selfservice.obd.core.passthru.PassThruNativeBridge
import com.selfservice.obd.core.passthru.PassThruNativeBridgeInstaller
import com.selfservice.obd.core.passthru.PassThruNativeBridgeRegistry
import com.selfservice.obd.core.session.ConnectedAdapter
import com.selfservice.obd.core.session.ObdSessionState
import com.selfservice.platform.data.diagnostics.DiagnosticsLogLocalStore
import com.selfservice.platform.data.diagnostics.DiagnosticsTelemetrySummary
import com.selfservice.platform.data.diagnostics.DiagnosticsLogSummary
import com.selfservice.platform.data.diagnostics.DiagnosticsLogSummaryObserver
import com.selfservice.platform.data.diagnostics.DiagnosticsReportLocalStore
import com.selfservice.platform.data.diagnostics.DiagnosticsReportRecord
import com.selfservice.platform.data.diagnostics.DiagnosticsReportStore
import com.selfservice.platform.data.diagnostics.DiagnosticsReportSummary
import com.selfservice.platform.data.diagnostics.DiagnosticsReportSummaryObserver
import com.selfservice.platform.data.diagnostics.DiagnosticsTelemetryLocalStore
import com.selfservice.platform.data.diagnostics.DiagnosticsTelemetryStore
import com.selfservice.platform.data.diagnostics.DiagnosticsTelemetrySummaryObserver
import com.selfservice.platform.data.diagnostics.room.DiagnosticsLogDatabase
import com.selfservice.platform.data.diagnostics.room.DiagnosticsLogDatabaseFactory
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricDefinition
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricDefinitionAssetSource
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricProfileRepository
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricSample
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricTrend
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricTrendRecorder
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricStatus
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsProfileSnapshot
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendation
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendationEngine
import com.selfservice.obd.core.session.telemetry.ObdSessionTelemetry
import com.selfservice.kiosk.telemetry.RoomObdSessionTelemetry
import com.selfservice.kiosk.telemetry.DiagnosticsTelemetryRetentionCoordinator
import com.selfservice.obd.core.protocol.ObdPidSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

open class KioskApp : Application() {

    lateinit var passThruSelfTestRuntime: PassThruSelfTestRuntime
        private set
    lateinit var passThruDiagnosticsRuntime: PassThruDiagnosticsRuntime
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var supabaseOutboxSync: SupabaseOutboxSync? = null
    private var supabaseOutboxMonitor: SupabaseOutboxMonitor? = null
    private var supabaseOutboxWriter: SupabaseOutboxWriter? = null
    private var supabaseOutboxObserver: (() -> Unit)? = null
    private var supabaseOutboxStatusLogger: Job? = null
    private var deviceStatusReporter: DeviceStatusReporter? = null
    private var deviceStatusJob: Job? = null
    private var mdmCommandManager: MdmCommandManager? = null
    private val pendingMdmCommands = MdmCommandQueue(MAX_PENDING_MDM_COMMANDS)
    private var mdmCommandStateJob: Job? = null
    @Volatile private var lastCompletedMdmCommand: MdmCommandManager.CommandState? = null
    private var dictionaryUpdateWatcher: LocalDictionaryUpdateWatcher? = null
    private var manufacturerOverrideWatcher: LocalDictionaryUpdateWatcher? = null
    private var diagnosticsLogDatabase: DiagnosticsLogDatabase? = null
    private var diagnosticsLogSummaryObserver: DiagnosticsLogSummaryObserver? = null
    private var diagnosticsLogRetentionCoordinator: DiagnosticsLogRetentionCoordinator? = null
    private var diagnosticsTelemetryOutbox: DiagnosticsTelemetryOutboxBridge? = null
    private var diagnosticsTelemetrySummaryObserver: DiagnosticsTelemetrySummaryObserver? = null
    private var diagnosticsTelemetryRetentionCoordinator: DiagnosticsTelemetryRetentionCoordinator? = null
    private var diagnosticsTelemetryStore: DiagnosticsTelemetryStore? = null
    private var diagnosticsReportStore: DiagnosticsReportStore? = null
    private var diagnosticsReportOutbox: DiagnosticsReportOutboxBridge? = null
    private var diagnosticsReportSummaryObserver: DiagnosticsReportSummaryObserver? = null
    private var diagnosticsReportSummaryJob: Job? = null
    private var diagnosticsReportRetentionCoordinator: DiagnosticsReportRetentionCoordinator? = null
    private var obdSessionTelemetry: ObdSessionTelemetry = ObdSessionTelemetry.NoOp
    private var obdConnectionController: ObdConnectionController? = null
    private lateinit var diagnosticsLogService: DiagnosticsLogService
    private var nativeBridgeProvider: (() -> PassThruNativeBridge)? = null
    private var diagnosticsMetricRepository: DiagnosticsMetricProfileRepository? = null
    private var diagnosticsRecommendationEngine: DiagnosticsRecommendationEngine? = null
    private val diagnosticsMetricSnapshot = MutableStateFlow<DiagnosticsProfileSnapshot?>(null)
    private val diagnosticsRecommendations = MutableStateFlow<List<DiagnosticsRecommendation>>(emptyList())
    private val diagnosticsMetricTrendRecorder = DiagnosticsMetricTrendRecorder()
    private val diagnosticsMetricTrends = MutableStateFlow<Map<String, DiagnosticsMetricTrend>>(emptyMap())
    private val diagnosticsReportResult = MutableStateFlow<DiagnosticsReportWorkflow.Result?>(null)
    private val diagnosticsReportSummaryState = MutableStateFlow(DiagnosticsReportSummary.empty())
    private val diagnosticsReportGenerator by lazy { DiagnosticsReportGenerator.create() }
    private val paymentModule: PaymentModule by lazy { buildPaymentModule() }
    private val paymentAuditSink = MutablePaymentAuditSink()
    private val diagnosticsClock: () -> Long = { System.currentTimeMillis() }
    private val diagnosticsSessionIdProvider: () -> String = { UUID.randomUUID().toString() }
    private var diagnosticsReportWorkflow: DiagnosticsReportWorkflow? = null

    data class DiagnosticsMetricEvaluation(
        val snapshot: DiagnosticsProfileSnapshot,
        val recommendations: List<DiagnosticsRecommendation>,
        val trends: Map<String, DiagnosticsMetricTrend> = emptyMap()
    )

    data class DiagnosticsConnectionOptions(
        val forceReconnect: Boolean = false,
        val adapter: ConnectedAdapter? = null
    )

    override fun onCreate() {
        super.onCreate()
        instance = this
        val diagnosticsDir = File(filesDir, DIAGNOSTICS_DIR)
        val sink = DiagnosticsLogFileSink(directory = diagnosticsDir)
        val database = DiagnosticsLogDatabaseFactory.create(this)
        diagnosticsLogDatabase = database
        val store = DiagnosticsLogLocalStore(database.diagnosticsLogDao())
        val telemetryStore = DiagnosticsTelemetryLocalStore(database.diagnosticsTelemetryDao())
        diagnosticsTelemetryStore = telemetryStore
    val telemetrySummaryObserver = DiagnosticsTelemetrySummaryObserver(
        store = telemetryStore,
        scope = applicationScope
    )
    diagnosticsTelemetrySummaryObserver = telemetrySummaryObserver
        val telemetryOutbox = DiagnosticsTelemetryOutboxBridge(
                store = telemetryStore,
        scope = applicationScope,
        onExported = telemetrySummaryObserver::requestRefresh
        )
        diagnosticsTelemetryOutbox = telemetryOutbox
        obdSessionTelemetry = RoomObdSessionTelemetry(
                store = telemetryStore,
        onRecord = {
            telemetryOutbox.notifyRecordInserted()
            telemetrySummaryObserver.requestRefresh()
        }
        )
        val summaryObserver = DiagnosticsLogSummaryObserver(
                store = store,
                scope = applicationScope
        )
        diagnosticsLogService =
                DiagnosticsLogService(
                        sink = sink,
                        scope = applicationScope,
                        store = store,
                        storeUpdateCallback = summaryObserver::requestRefresh
                )
        val retentionCoordinator = DiagnosticsLogRetentionCoordinator(
                summaryState = summaryObserver.state,
                requestSummaryRefresh = summaryObserver::requestRefresh,
                scope = applicationScope,
                pruneAction = diagnosticsLogService::pruneOlderThan,
                onPruneError = { error ->
                    Log.w(TAG, "Diagnostics log retention prune failed", error)
                }
        )
    val telemetryRetentionCoordinator = DiagnosticsTelemetryRetentionCoordinator(
        summaryState = telemetrySummaryObserver.state,
        requestSummaryRefresh = telemetrySummaryObserver::requestRefresh,
        scope = applicationScope,
        pruneAction = telemetryStore::deleteOlderThan,
        onPruneError = { error ->
            Log.w(TAG, "Diagnostics telemetry retention prune failed", error)
        }
    )
        summaryObserver.requestRefresh()
    telemetrySummaryObserver.requestRefresh()
        diagnosticsLogSummaryObserver = summaryObserver
        diagnosticsLogRetentionCoordinator = retentionCoordinator
    diagnosticsTelemetryRetentionCoordinator = telemetryRetentionCoordinator
    initializeDiagnosticsReportPersistence(database)
    initializeManufacturerDictionaries()
    initializePassThruSelfTests()
    initializePassThruDiagnostics()
    loadDiagnosticsMetricCatalog()
    startDictionaryWatchers()
    refreshPaymentAuditSink()
    }

    override fun onTerminate() {
        supabaseOutboxObserver?.let { observer ->
            if (::passThruSelfTestRuntime.isInitialized) {
                passThruSelfTestRuntime.unregisterOutboxObserver(observer)
            }
        }
        supabaseOutboxObserver = null
        supabaseOutboxStatusLogger?.cancel()
        supabaseOutboxStatusLogger = null
        supabaseOutboxMonitor?.close()
        supabaseOutboxMonitor = null
        supabaseOutboxSync?.stop()
        supabaseOutboxSync = null
        stopDeviceStatusReporter()
        deviceStatusReporter = null
        stopMdmCommandManager()
        diagnosticsLogService.detachOutbox()
        diagnosticsTelemetryOutbox?.detachSupabaseOutbox()
        diagnosticsTelemetryOutbox = null
        setDiagnosticsReportComponents(store = null, outbox = null, summaryObserver = null)
        dictionaryUpdateWatcher?.close()
        dictionaryUpdateWatcher = null
    manufacturerOverrideWatcher?.close()
    manufacturerOverrideWatcher = null
        diagnosticsTelemetrySummaryObserver?.close()
        diagnosticsTelemetrySummaryObserver = null
        diagnosticsTelemetryRetentionCoordinator?.close()
        diagnosticsTelemetryRetentionCoordinator = null
        diagnosticsTelemetryStore = null
        diagnosticsLogSummaryObserver?.close()
        diagnosticsLogSummaryObserver = null
        diagnosticsLogRetentionCoordinator?.close()
        diagnosticsLogRetentionCoordinator = null
        diagnosticsLogDatabase?.close()
        diagnosticsLogDatabase = null
        obdSessionTelemetry = ObdSessionTelemetry.NoOp
    diagnosticsMetricRepository = null
    diagnosticsRecommendationEngine = null
    diagnosticsMetricSnapshot.value = null
    diagnosticsRecommendations.value = emptyList()
    diagnosticsReportResult.value = null
    diagnosticsReportWorkflow = null
        if (::passThruSelfTestRuntime.isInitialized) {
            passThruSelfTestRuntime.shutdown()
        }
        if (::passThruDiagnosticsRuntime.isInitialized) {
            passThruDiagnosticsRuntime.shutdown()
        }
    obdConnectionController = null
        nativeBridgeProvider = null
        applicationScope.cancel()
        instance = null
        super.onTerminate()
    }

    fun diagnosticsTelemetryStore(): DiagnosticsTelemetryStore? = diagnosticsTelemetryStore

    private fun initializeManufacturerDictionaries() {
        ManufacturerDictionaryInstaller.ensureInstalled(this, MANUFACTURER_DTC_ASSET)
    }

    private fun initializePassThruSelfTests() {
        val bridgeProvider = resolvePassThruBridgeProvider()
        val runtimeConfig =
                PassThruSelfTestRuntimeProvider.Config(
                        context = this,
                        dictionaryUpdateProvider = LocalDictionaryUpdateProvider(this),
                        bridgeProvider = bridgeProvider,
                        telemetry = obdSessionTelemetry
                )
        passThruSelfTestRuntime = PassThruSelfTestRuntimeProvider.create(runtimeConfig)
        configureSupabaseOutbox(passThruSelfTestRuntime)
    }

    private fun initializePassThruDiagnostics() {
    val bridgeProvider = resolvePassThruBridgeProvider()
    val dispatchers = DefaultDispatchersProvider()
    val runtimeConfig =
        PassThruDiagnosticsRuntimeProvider.Config(
            context = this,
            dispatchers = dispatchers,
            dictionaryUpdateProvider = LocalDictionaryUpdateProvider(this),
            bridgeProvider = bridgeProvider,
            telemetry = obdSessionTelemetry
        )
    val runtime = PassThruDiagnosticsRuntimeProvider.create(runtimeConfig)
    installPassThruDiagnosticsRuntime(runtime, dispatchers)
    }

    private fun initializeDiagnosticsReportPersistence(database: DiagnosticsLogDatabase) {
        val store = DiagnosticsReportLocalStore(database.diagnosticsReportDao())
        val summaryObserver = DiagnosticsReportSummaryObserver(
            store = store,
            scope = applicationScope
        )
        val outbox = DiagnosticsReportOutboxBridge(
            store = store,
            scope = applicationScope,
            onExported = summaryObserver::requestRefresh
        )
        setDiagnosticsReportComponents(store, outbox, summaryObserver)
    }

    private fun setDiagnosticsReportComponents(
        store: DiagnosticsReportStore?,
        outbox: DiagnosticsReportOutboxBridge?,
        summaryObserver: DiagnosticsReportSummaryObserver?
    ) {
        diagnosticsReportOutbox?.detachSupabaseOutbox()
        diagnosticsReportOutbox = null
        diagnosticsReportSummaryJob?.cancel()
        diagnosticsReportSummaryJob = null
        diagnosticsReportSummaryObserver?.close()
        diagnosticsReportSummaryObserver = null
        diagnosticsReportRetentionCoordinator?.close()
        diagnosticsReportRetentionCoordinator = null
        diagnosticsReportStore = null

        diagnosticsReportStore = store
        diagnosticsReportOutbox = outbox
        diagnosticsReportSummaryObserver = summaryObserver

        if (summaryObserver != null) {
            diagnosticsReportSummaryJob = applicationScope.launch {
                summaryObserver.state.collect { summary ->
                    diagnosticsReportSummaryState.value = summary
                }
            }
            summaryObserver.requestRefresh()
            val reportStore = store
            if (reportStore != null) {
                diagnosticsReportRetentionCoordinator = DiagnosticsReportRetentionCoordinator(
                    summaryState = summaryObserver.state,
                    requestSummaryRefresh = summaryObserver::requestRefresh,
                    scope = applicationScope,
                    pruneAction = reportStore::deleteOlderThan,
                    onPruneError = { error ->
                        Log.w(TAG, "Diagnostics report retention prune failed", error)
                    }
                )
            }
        } else {
            diagnosticsReportSummaryState.value = DiagnosticsReportSummary.empty()
        }
    }

    private fun installPassThruDiagnosticsRuntime(
        runtime: PassThruDiagnosticsRuntime,
        dispatchers: DispatchersProvider
    ) {
    passThruDiagnosticsRuntime = runtime
    obdConnectionController = ObdConnectionController(
        manager = runtime.connectionManager(),
        scope = applicationScope,
        dispatchers = dispatchers
    )
    diagnosticsReportResult.value = null
    diagnosticsReportWorkflow = DiagnosticsReportWorkflow(
        diagnosticsRunner = runtime::runDiagnostics,
        metricEvaluator = ::evaluateDiagnosticsMetrics,
        reportProducer = DiagnosticsReportWorkflow.ReportProducer { input ->
            diagnosticsReportGenerator.generate(input)
        },
        timeProvider = diagnosticsClock,
        sessionIdProvider = diagnosticsSessionIdProvider
    )
    }

    @VisibleForTesting
    fun overridePassThruDiagnosticsRuntime(
            runtime: PassThruDiagnosticsRuntime,
            dispatchers: DispatchersProvider = DefaultDispatchersProvider()
    ) {
        if (::passThruDiagnosticsRuntime.isInitialized) {
            runCatching { passThruDiagnosticsRuntime.shutdown() }
        }
        obdConnectionController?.disconnectAsync()
        obdConnectionController = null
        installPassThruDiagnosticsRuntime(runtime, dispatchers)
        notifyDictionaryRuntimes(force = false)
    }

    @VisibleForTesting
    fun overrideObdConnectionController(controller: ObdConnectionController?) {
        if (controller === obdConnectionController) {
            return
        }
        obdConnectionController?.disconnectAsync()
        obdConnectionController = controller
    }

    @VisibleForTesting
    fun overrideDiagnosticsReportWorkflow(workflow: DiagnosticsReportWorkflow?) {
        diagnosticsReportResult.value = null
        diagnosticsReportWorkflow = workflow
    }

    @VisibleForTesting
    fun overrideDiagnosticsReportComponents(
        store: DiagnosticsReportStore?,
        outbox: DiagnosticsReportOutboxBridge?,
        summaryObserver: DiagnosticsReportSummaryObserver?
    ) {
        setDiagnosticsReportComponents(store, outbox, summaryObserver)
    }

    private fun resolvePassThruBridgeProvider(): () -> PassThruNativeBridge =
            if (isDebuggable()) {
                { DevMockPassThruBridge.create() }
            } else {
                nativeBridgeProvider ?: installPassThruNativeBridge()
            }

    fun diagnosticsMetricDefinitions(): List<DiagnosticsMetricDefinition> =
            diagnosticsMetricRepository?.definitions() ?: emptyList()

    fun diagnosticsMetricSnapshotState(): StateFlow<DiagnosticsProfileSnapshot?> =
            diagnosticsMetricSnapshot.asStateFlow()

    fun diagnosticsRecommendationsState(): StateFlow<List<DiagnosticsRecommendation>> =
            diagnosticsRecommendations.asStateFlow()

    fun diagnosticsMetricTrendsState(): StateFlow<Map<String, DiagnosticsMetricTrend>> =
        diagnosticsMetricTrends.asStateFlow()

    fun diagnosticsReportState(): StateFlow<DiagnosticsReportWorkflow.Result?> =
        diagnosticsReportResult.asStateFlow()

    fun diagnosticsReportSummaryState(): StateFlow<DiagnosticsReportSummary> =
        diagnosticsReportSummaryState.asStateFlow()

    fun payments(): PaymentModule = paymentModule

    fun evaluateDiagnosticsMetrics(samples: Collection<ObdPidSample>): DiagnosticsMetricEvaluation? {
        val repository = diagnosticsMetricRepository ?: run {
            diagnosticsRecommendations.value = emptyList()
            diagnosticsMetricTrendRecorder.reset()
            diagnosticsMetricTrends.value = emptyMap()
            return null
        }
        val mappedSamples = samples.map { sample ->
            DiagnosticsMetricSample(
                mode = sample.definition.mode,
                pid = sample.definition.pid,
                value = sample.value,
                unit = sample.unit ?: sample.definition.unit,
                timestampMillis = sample.timestampMillis
            )
        }
        val snapshot = repository.evaluate(mappedSamples)
        diagnosticsMetricSnapshot.value = snapshot
        val trends = diagnosticsMetricTrendRecorder.record(snapshot)
        diagnosticsMetricTrends.value = trends
        val engine = diagnosticsRecommendationEngine
        val recommendations = engine?.recommendations(snapshot) ?: emptyList()
        diagnosticsRecommendations.value = recommendations
        return DiagnosticsMetricEvaluation(
            snapshot = snapshot,
            recommendations = recommendations,
            trends = trends
        )
    }

    fun resetDiagnosticsMetrics() {
        diagnosticsMetricSnapshot.value = null
        diagnosticsRecommendations.value = emptyList()
        diagnosticsMetricTrendRecorder.reset()
        diagnosticsMetricTrends.value = emptyMap()
    }

    suspend fun runDiagnosticsReport(
        request: ObdDiagnosticsRequest,
        metadata: DiagnosticsReportWorkflow.Metadata = DiagnosticsReportWorkflow.Metadata(),
        connectionOptions: DiagnosticsConnectionOptions = DiagnosticsConnectionOptions()
    ): DiagnosticsReportWorkflow.Result {
        val workflow = diagnosticsReportWorkflow ?: error("Diagnostics report workflow is not initialized")
        diagnosticsReportResult.value = null
        resetDiagnosticsMetrics()
        val adapter = connectionOptions.adapter ?: run {
            val controller = obdConnectionController ?: error("OBD connection controller is not initialized")
            val snapshot = controller.connect(force = connectionOptions.forceReconnect)
            snapshot.adapter ?: error("OBD adapter is not connected")
        }
        val result = workflow.run(adapter, request, metadata)
        diagnosticsReportResult.value = result
        persistDiagnosticsReport(result)
        return result
    }

    private fun loadDiagnosticsMetricCatalog() {
        val definitions = DiagnosticsMetricDefinitionAssetSource(assets).load()
        if (definitions.isEmpty()) {
            diagnosticsMetricRepository = null
            diagnosticsRecommendationEngine = null
            diagnosticsMetricSnapshot.value = null
            diagnosticsRecommendations.value = emptyList()
            diagnosticsMetricTrendRecorder.reset()
            diagnosticsMetricTrends.value = emptyMap()
            diagnosticsReportResult.value = null
            Log.w(TAG, "Diagnostics metric catalog is empty; insights disabled")
            return
        }
        diagnosticsMetricRepository = DiagnosticsMetricProfileRepository(definitions)
        diagnosticsRecommendationEngine = DiagnosticsRecommendationEngine()
        diagnosticsMetricSnapshot.value = null
        diagnosticsRecommendations.value = emptyList()
        diagnosticsMetricTrendRecorder.reset()
        diagnosticsMetricTrends.value = emptyMap()
        diagnosticsReportResult.value = null
        Log.i(TAG, "Diagnostics metric catalog loaded: ${definitions.size} metrics")
    }

    private suspend fun persistDiagnosticsReport(result: DiagnosticsReportWorkflow.Result) {
        val store = diagnosticsReportStore ?: return
        val metadata = buildDiagnosticsReportMetadata(result)
        val record = DiagnosticsReportRecord(
            sessionId = result.reportInput.sessionId,
            generatedAtMillis = result.reportInput.generatedAtMillis,
            html = result.report.html,
            pdfBytes = result.report.pdfBytes,
            metadata = metadata
        )
        val summaryObserver = diagnosticsReportSummaryObserver
        val outbox = diagnosticsReportOutbox
        withContext(Dispatchers.IO) {
            runCatching { store.record(record) }
                .onFailure { error ->
                    Log.w(TAG, "Failed to persist diagnostics report", error)
                }
                .onSuccess {
                    summaryObserver?.requestRefresh()
                    outbox?.notifyRecordInserted()
                }
        }
    }

    private fun buildDiagnosticsReportMetadata(
        result: DiagnosticsReportWorkflow.Result
    ): Map<String, Any?> {
        val input = result.reportInput
        val metadata = LinkedHashMap<String, Any?>()
        input.vehicle?.let { vehicle ->
            vehicleMetadata(vehicle)?.let { metadata["vehicle"] = it }
        }
        input.customer?.let { customer ->
            customerMetadata(customer)?.let { metadata["customer"] = it }
        }
        metadata["summary"] = buildReportSummaryMetadata(
            snapshot = input.snapshot,
            recommendationCount = input.recommendations.size
        )
        buildDiagnosticsMetadata(result)?.let { diagnostics ->
            metadata["diagnostics"] = diagnostics
        }
        return metadata
    }

    private fun vehicleMetadata(vehicle: DiagnosticsReportVehicle): Map<String, Any?>? {
        val map = LinkedHashMap<String, Any?>()
        if (vehicle.make.isNotBlank()) {
            map["make"] = vehicle.make.trim()
        }
        vehicle.model?.takeIf { it.isNotBlank() }?.let { map["model"] = it.trim() }
        vehicle.year?.takeIf { it > 0 }?.let { map["year"] = it }
        vehicle.vin?.takeIf { it.isNotBlank() }?.let { map["vin"] = it.trim() }
        return map.takeIf { it.isNotEmpty() }
    }

    private fun customerMetadata(customer: DiagnosticsReportCustomer): Map<String, Any?>? {
        val map = LinkedHashMap<String, Any?>()
        customer.phone?.takeIf { it.isNotBlank() }?.let { map["phone"] = it.trim() }
        customer.email?.takeIf { it.isNotBlank() }?.let { map["email"] = it.trim() }
        return map.takeIf { it.isNotEmpty() }
    }

    private fun buildReportSummaryMetadata(
        snapshot: DiagnosticsProfileSnapshot,
        recommendationCount: Int
    ): Map<String, Any?> {
        var normal = 0
        var warning = 0
        var critical = 0
        var noData = 0
        snapshot.metrics.forEach { insight ->
            when (insight.status) {
                DiagnosticsMetricStatus.OK -> normal += 1
                DiagnosticsMetricStatus.WARNING_HIGH,
                DiagnosticsMetricStatus.WARNING_LOW -> warning += 1
                DiagnosticsMetricStatus.CRITICAL_HIGH,
                DiagnosticsMetricStatus.CRITICAL_LOW -> critical += 1
                DiagnosticsMetricStatus.NO_DATA -> noData += 1
            }
        }
        return mapOf(
            "metrics_total" to snapshot.metrics.size,
            "metrics_normal" to normal,
            "metrics_warning" to warning,
            "metrics_critical" to critical,
            "metrics_no_data" to noData,
            "recommendations_total" to recommendationCount
        )
    }

    private fun buildDiagnosticsMetadata(
        result: DiagnosticsReportWorkflow.Result
    ): Map<String, Any?>? {
        val diagnostics = result.diagnosticsRun.diagnostics
        val dtcBatch = diagnostics.troubleCodes
        val dtcEntries = dtcBatch?.entries?.map { entry ->
            val definition = entry.definition
            val map = LinkedHashMap<String, Any?>(3)
            map["code"] = entry.code
            if (definition != null) {
                map["label"] = definition.label
                map["system"] = definition.system.value
                definition.notes?.takeIf { it.isNotBlank() }?.let { map["notes"] = it }
            }
            map
        }?.takeIf { it.isNotEmpty() }

        val failures = diagnostics.failures.map { failure ->
            mapOf(
                "stage" to failure.stage.name,
                "detail" to failure.detail
            )
        }.takeIf { it.isNotEmpty() }

        val sessionStateName = result.diagnosticsRun.sessionState::class.simpleName
            ?: result.diagnosticsRun.sessionState::class.java.simpleName

        val metadata = LinkedHashMap<String, Any?>(6)
        metadata["session_state"] = sessionStateName
        metadata["duration_ms"] = diagnostics.durationMillis
        metadata["pid_sample_count"] = diagnostics.pidSamples.size
        diagnostics.clearPerformed?.let { metadata["clear_performed"] = it }
        dtcBatch?.let {
            metadata["dtc_count"] = it.entries.size
            metadata["dtc_timestamp_ms"] = it.timestampMillis
        }
        dtcEntries?.let { metadata["dtc_entries"] = it }
        failures?.let { metadata["failures"] = it }
        return metadata.takeIf { it.isNotEmpty() }
    }

    private fun installPassThruNativeBridge(): () -> PassThruNativeBridge {
        val provider = try {
            PassThruNativeBridgeInstaller.install()
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to install PassThru native bridge", error)
            throw error
        }
        nativeBridgeProvider = provider
        return provider
    }

    private fun configureSupabaseOutbox(runtime: PassThruSelfTestRuntime) {
        supabaseOutboxSync?.stop()
        supabaseOutboxSync = null
        supabaseOutboxMonitor?.close()
        supabaseOutboxMonitor = null
        supabaseOutboxStatusLogger?.cancel()
        supabaseOutboxStatusLogger = null
        supabaseOutboxObserver?.let(runtime::unregisterOutboxObserver)
        supabaseOutboxObserver = null
        val credentials = SupabaseCredentialsProvider.fromBuildConfig()
        if (credentials == null) {
            Log.i(TAG, "Supabase credentials not configured; outbox sync disabled")
            diagnosticsLogService.detachOutbox()
            diagnosticsTelemetryOutbox?.detachSupabaseOutbox()
            diagnosticsReportOutbox?.detachSupabaseOutbox()
            supabaseOutboxWriter = null
            refreshPaymentAuditSink()
            refreshDeviceStatusReporter()
            stopMdmCommandManager()
            return
        }
        val outboxWriter = runtime.outboxWriter()
        supabaseOutboxWriter = outboxWriter
        refreshPaymentAuditSink()
        refreshDeviceStatusReporter()
        refreshMdmCommandManager()
        diagnosticsLogService.attachOutbox(outboxWriter) {
            KioskRuntimeConfig.diagnosticsLogFields()
        }
        diagnosticsTelemetryOutbox?.attachSupabaseOutbox(outboxWriter) {
            KioskRuntimeConfig.diagnosticsTelemetryFields()
        }
        diagnosticsReportOutbox?.attachSupabaseOutbox(outboxWriter) {
            KioskRuntimeConfig.diagnosticsReportFields()
        }
        val uploader = runtime.createOutboxUploader(SupabaseRestClient(credentials))
        val sync = SupabaseOutboxSync({ limit -> uploader.flush(limit) }, applicationScope)
        sync.start()
        supabaseOutboxSync = sync
        val monitor = SupabaseOutboxMonitor(
                directory = runtime.outboxDirectory(),
                syncState = sync.state,
                parentScope = applicationScope
        )
        supabaseOutboxMonitor = monitor
        val observer: () -> Unit = { monitor.requestRefresh() }
        runtime.registerOutboxObserver(observer)
        supabaseOutboxObserver = observer

        var lastReportedTotal = -1
        var lastReportedLogPending = -1
        var lastReportedLogOldest: Long? = null
        var lastReportedTelemetryPending = -1
        var lastReportedTelemetryOldest: Long? = null
        var lastReportedReportPending = -1
        var lastReportedReportOldest: Long? = null
        var lastReportedPaymentsPending = -1
        var lastReportedPaymentsOldest: Long? = null
        var lastReportedDeviceStatusPending = -1
        var lastReportedDeviceStatusOldest: Long? = null
    var lastReportedReportDeliveryPending = -1
    var lastReportedReportDeliveryOldest: Long? = null
        var lastReportedDeviceCommandsPending = -1
        var lastReportedDeviceCommandsOldest: Long? = null
        var lastReportedDeviceEventsPending = -1
        var lastReportedDeviceEventsOldest: Long? = null
        var lastReportedLogStorePending: Int? = null
        var lastReportedLogStoreTotal: Int? = null
        var lastReportedTelemetryStorePending: Int? = null
        var lastReportedTelemetryStoreTotal: Int? = null
        var lastReportedReportStorePending: Int? = null
        var lastReportedReportStoreTotal: Int? = null
        var lastReportedStale = false
        var lastReportedStaleAgeMillis: Long? = null

        val loggerJob = applicationScope.launch {
            fun ageMillis(timestampMillis: Long?): Long? {
                if (timestampMillis == null || timestampMillis <= 0) {
                    return null
                }
                val now = System.currentTimeMillis()
                val age = now - timestampMillis
                return if (age >= 0) age else null
            }

            monitor.status.collect { status ->
                val totalPending = status.pendingCount
                val logPending = status.pendingCountsByTable[DiagnosticsLogSupabaseSchema.TABLE_NAME] ?: 0
                val logOldest = status.oldestPendingAtByTable[DiagnosticsLogSupabaseSchema.TABLE_NAME]
                val telemetryPending = status.pendingCountsByTable[DiagnosticsTelemetrySupabaseSchema.TABLE_NAME] ?: 0
                val telemetryOldest = status.oldestPendingAtByTable[DiagnosticsTelemetrySupabaseSchema.TABLE_NAME]
                val reportPending = status.pendingCountsByTable[DiagnosticsReportSupabaseSchema.TABLE_NAME] ?: 0
                val reportOldest = status.oldestPendingAtByTable[DiagnosticsReportSupabaseSchema.TABLE_NAME]
                val paymentsPending = status.pendingCountsByTable[PaymentsAuditSupabaseSchema.TABLE_NAME] ?: 0
                val paymentsOldest = status.oldestPendingAtByTable[PaymentsAuditSupabaseSchema.TABLE_NAME]
                val deviceStatusPending = status.pendingCountsByTable[DeviceStatusSupabaseSchema.TABLE_NAME] ?: 0
                val deviceStatusOldest = status.oldestPendingAtByTable[DeviceStatusSupabaseSchema.TABLE_NAME]
                val deviceCommandsPending = status.pendingCountsByTable[DeviceCommandSupabaseSchema.TABLE_NAME] ?: 0
                val deviceCommandsOldest = status.oldestPendingAtByTable[DeviceCommandSupabaseSchema.TABLE_NAME]
                val deviceEventsPending = status.pendingCountsByTable[DeviceEventSupabaseSchema.TABLE_NAME] ?: 0
                val deviceEventsOldest = status.oldestPendingAtByTable[DeviceEventSupabaseSchema.TABLE_NAME]
                val reportDeliveryPending = status.pendingCountsByTable[DiagnosticsReportDeliverySupabaseSchema.TABLE_NAME] ?: 0
                val reportDeliveryOldest = status.oldestPendingAtByTable[DiagnosticsReportDeliverySupabaseSchema.TABLE_NAME]
        val logSummaryState = diagnosticsLogSummaryObserver?.state?.value
        val telemetrySummaryState = diagnosticsTelemetrySummaryObserver?.state?.value
        val reportSummaryState = diagnosticsReportSummaryObserver?.state?.value
        val logStorePending = logSummaryState?.pendingCount
        val logStoreTotal = logSummaryState?.totalCount
        val telemetryStorePending = telemetrySummaryState?.pendingCount
        val telemetryStoreTotal = telemetrySummaryState?.totalCount
        val reportStorePending = reportSummaryState?.pendingCount
        val reportStoreTotal = reportSummaryState?.totalCount

        val shouldReport = totalPending != lastReportedTotal ||
            logPending != lastReportedLogPending ||
            logOldest != lastReportedLogOldest ||
            telemetryPending != lastReportedTelemetryPending ||
            telemetryOldest != lastReportedTelemetryOldest ||
            reportPending != lastReportedReportPending ||
            reportOldest != lastReportedReportOldest ||
            paymentsPending != lastReportedPaymentsPending ||
            paymentsOldest != lastReportedPaymentsOldest ||
            deviceStatusPending != lastReportedDeviceStatusPending ||
            deviceStatusOldest != lastReportedDeviceStatusOldest ||
            deviceCommandsPending != lastReportedDeviceCommandsPending ||
            deviceCommandsOldest != lastReportedDeviceCommandsOldest ||
            deviceEventsPending != lastReportedDeviceEventsPending ||
            deviceEventsOldest != lastReportedDeviceEventsOldest ||
            reportDeliveryPending != lastReportedReportDeliveryPending ||
            reportDeliveryOldest != lastReportedReportDeliveryOldest ||
            logStorePending != lastReportedLogStorePending ||
            logStoreTotal != lastReportedLogStoreTotal ||
            telemetryStorePending != lastReportedTelemetryStorePending ||
            telemetryStoreTotal != lastReportedTelemetryStoreTotal ||
            reportStorePending != lastReportedReportStorePending ||
            reportStoreTotal != lastReportedReportStoreTotal

                if (!shouldReport) {
                    return@collect
                }

                val localParts = mutableListOf<String>()
                if (logStorePending != null) {
                    val descriptor = if (logStoreTotal != null) {
                        "logLocalPending=$logStorePending logLocalTotal=$logStoreTotal"
                    } else {
                        "logLocalPending=$logStorePending"
                    }
                    localParts += descriptor
                }
                if (telemetryStorePending != null) {
                    val descriptor = if (telemetryStoreTotal != null) {
                        "telemetryLocalPending=$telemetryStorePending telemetryLocalTotal=$telemetryStoreTotal"
                    } else {
                        "telemetryLocalPending=$telemetryStorePending"
                    }
                    localParts += descriptor
                }
                if (reportStorePending != null) {
                    val descriptor = if (reportStoreTotal != null) {
                        "reportsLocalPending=$reportStorePending reportsLocalTotal=$reportStoreTotal"
                    } else {
                        "reportsLocalPending=$reportStorePending"
                    }
                    localParts += descriptor
                }
                val localSummary = localParts.joinToString(separator = " ")
                val storePart = if (localSummary.isEmpty()) "" else " " + localSummary
                val logAge = ageMillis(logOldest)
                val telemetryAge = ageMillis(telemetryOldest)
                val reportAge = ageMillis(reportOldest)
                val paymentsAge = ageMillis(paymentsOldest)
                val deviceStatusAge = ageMillis(deviceStatusOldest)
                val deviceCommandsAge = ageMillis(deviceCommandsOldest)
                val deviceEventsAge = ageMillis(deviceEventsOldest)
                val reportDeliveryAge = ageMillis(reportDeliveryOldest)
                val staleAge = ageMillis(status.lastRefreshAtMillis)
                val previousStaleAge = lastReportedStaleAgeMillis
                val isStale = staleAge != null &&
                    staleAge >= SupabaseOutboxStatusUiMapper.STALE_REFRESH_THRESHOLD_MILLIS

                if (isStale) {
                    val shouldReportStale = !lastReportedStale ||
                        staleAge != null && (
                        previousStaleAge == null ||
                            staleAge - previousStaleAge >= STALE_LOG_INTERVAL_MILLIS)
                    if (shouldReportStale) {
                        val message = buildString {
                            append("Supabase monitor stale: no refresh for ")
                            append(staleAge)
                            append(" ms")
                            if (totalPending > 0) {
                                append(", pending=")
                                append(totalPending)
                            }
                        }
                        Log.w(TAG, message)
                        logSupabaseOutboxEvent(
                                message = message,
                                metadata = LinkedHashMap<String, Any?>().apply {
                                    put("pendingCount", totalPending)
                                    put("isRunning", status.isRunning)
                                    staleAge?.let { put("staleAgeMs", it) }
                                    status.lastRefreshAtMillis?.let { put("lastRefreshAt", it) }
                                    if (localSummary.isNotEmpty()) {
                                        put("localSummary", localSummary)
                                    }
                                }
                        )
                        lastReportedStaleAgeMillis = staleAge
                    }
                } else if (lastReportedStale) {
                    val message = buildString {
                        append("Supabase monitor refresh recovered after stale window")
                        previousStaleAge?.let { age ->
                            append(" (lastAgeMs=")
                            append(age)
                            append(')')
                        }
                    }
                    Log.i(TAG, message)
                    logSupabaseOutboxEvent(
                            message = message,
                            metadata = LinkedHashMap<String, Any?>().apply {
                                put("pendingCount", totalPending)
                                previousStaleAge?.let { put("lastStaleAgeMs", it) }
                                status.lastRefreshAtMillis?.let { put("lastRefreshAt", it) }
                            }
                    )
                    lastReportedStaleAgeMillis = null
                }

                val diagMessage = when {
                    totalPending > 0 -> {
                    val logPart = if (logPending > 0) {
                        val agePart = logAge?.let { " logsOldestAgeMs=$it" } ?: ""
                        " logsPending=$logPending$agePart"
                    } else {
                        ""
                    }
                    val telemetryPart = if (telemetryPending > 0) {
                        val agePart = telemetryAge?.let { " telemetryOldestAgeMs=$it" } ?: ""
                        " telemetryPending=$telemetryPending$agePart"
                    } else {
                        ""
                    }
                    val reportPart = if (reportPending > 0) {
                        val agePart = reportAge?.let { " reportsOldestAgeMs=$it" } ?: ""
                        " reportsPending=$reportPending$agePart"
                    } else {
                        ""
                    }
                    val paymentsPart = if (paymentsPending > 0) {
                        val agePart = paymentsAge?.let { " paymentsOldestAgeMs=$it" } ?: ""
                        " paymentsAuditPending=$paymentsPending$agePart"
                    } else {
                        ""
                    }
                    val deviceStatusPart = if (deviceStatusPending > 0) {
                        val agePart = deviceStatusAge?.let { " deviceStatusOldestAgeMs=$it" } ?: ""
                        " deviceStatusPending=$deviceStatusPending$agePart"
                    } else {
                        ""
                    }
                    val deviceCommandsPart = if (deviceCommandsPending > 0) {
                        val agePart = deviceCommandsAge?.let { " deviceCommandsOldestAgeMs=$it" } ?: ""
                        " deviceCommandsPending=$deviceCommandsPending$agePart"
                    } else {
                        ""
                    }
                    val deviceEventsPart = if (deviceEventsPending > 0) {
                        val agePart = deviceEventsAge?.let { " deviceEventsOldestAgeMs=$it" } ?: ""
                        " deviceEventsPending=$deviceEventsPending$agePart"
                    } else {
                        ""
                    }
                    val reportDeliveryPart = if (reportDeliveryPending > 0) {
                        val agePart = reportDeliveryAge?.let { " reportDeliveriesOldestAgeMs=$it" } ?: ""
                        " reportDeliveriesPending=$reportDeliveryPending$agePart"
                    } else {
                        ""
                    }
                        val parts = listOf(storePart, logPart, telemetryPart, reportPart, paymentsPart, deviceStatusPart, deviceCommandsPart, deviceEventsPart, reportDeliveryPart).filter { it.isNotEmpty() }
                        val suffix = parts.joinToString(separator = "")
                        val message = "Supabase outbox backlog total=$totalPending$suffix"
                        Log.w(TAG, message)
                        message
                    }
                    lastReportedTotal > 0 -> {
                        val message = "Supabase outbox backlog cleared$storePart"
                        Log.i(TAG, message)
                        message
                    }
                    localSummary.isNotEmpty() -> "Supabase local buffer updated $localSummary"
                    else -> "Supabase outbox metrics updated"
                }

                if (logPending == 0 && lastReportedLogPending > 0) {
                    Log.i(TAG, "Supabase diagnostics backlog cleared$storePart")
                }

                if (telemetryPending == 0 && lastReportedTelemetryPending > 0) {
                    Log.i(TAG, "Supabase telemetry backlog cleared$storePart")
                }

                if (reportPending == 0 && lastReportedReportPending > 0) {
                    Log.i(TAG, "Supabase reports backlog cleared$storePart")
                }

                if (paymentsPending == 0 && lastReportedPaymentsPending > 0) {
                    Log.i(TAG, "Supabase payments audit backlog cleared$storePart")
                }

                if (deviceStatusPending == 0 && lastReportedDeviceStatusPending > 0) {
                    Log.i(TAG, "Supabase device status backlog cleared$storePart")
                }

                if (deviceCommandsPending == 0 && lastReportedDeviceCommandsPending > 0) {
                    Log.i(TAG, "Supabase device commands backlog cleared$storePart")
                }

                if (deviceEventsPending == 0 && lastReportedDeviceEventsPending > 0) {
                    Log.i(TAG, "Supabase device events backlog cleared$storePart")
                }

                if (reportDeliveryPending == 0 && lastReportedReportDeliveryPending > 0) {
                    Log.i(TAG, "Supabase report deliveries backlog cleared$storePart")
                }

                recordSupabaseOutboxSnapshot(
                    message = diagMessage,
                    totalPending = totalPending,
                    diagnosticsPending = logPending,
                    diagnosticsOldestAtMillis = logOldest,
                    diagnosticsOldestAgeMillis = logAge,
                    telemetryPending = telemetryPending,
                    telemetryOldestAtMillis = telemetryOldest,
                    telemetryOldestAgeMillis = telemetryAge,
                    reportsPending = reportPending,
                    reportsOldestAtMillis = reportOldest,
                    reportsOldestAgeMillis = reportAge,
                    paymentsPending = paymentsPending,
                    paymentsOldestAtMillis = paymentsOldest,
                    paymentsOldestAgeMillis = paymentsAge,
                    deviceStatusPending = deviceStatusPending,
                    deviceStatusOldestAtMillis = deviceStatusOldest,
                    deviceStatusOldestAgeMillis = deviceStatusAge,
                    deviceCommandsPending = deviceCommandsPending,
                    deviceCommandsOldestAtMillis = deviceCommandsOldest,
                    deviceCommandsOldestAgeMillis = deviceCommandsAge,
                    deviceEventsPending = deviceEventsPending,
                    deviceEventsOldestAtMillis = deviceEventsOldest,
                    deviceEventsOldestAgeMillis = deviceEventsAge,
                    reportDeliveriesPending = reportDeliveryPending,
                    reportDeliveriesOldestAtMillis = reportDeliveryOldest,
                    reportDeliveriesOldestAgeMillis = reportDeliveryAge,
                    diagnosticsLocalPending = logStorePending,
                    diagnosticsLocalTotal = logStoreTotal,
                    telemetryLocalPending = telemetryStorePending,
                    telemetryLocalTotal = telemetryStoreTotal,
                    reportsLocalPending = reportStorePending,
                    reportsLocalTotal = reportStoreTotal,
                    localSummary = localSummary
                )

                lastReportedTotal = totalPending
                lastReportedLogPending = logPending
                lastReportedLogOldest = logOldest
                lastReportedTelemetryPending = telemetryPending
                lastReportedTelemetryOldest = telemetryOldest
                lastReportedReportPending = reportPending
                lastReportedReportOldest = reportOldest
                lastReportedPaymentsPending = paymentsPending
                lastReportedPaymentsOldest = paymentsOldest
                lastReportedDeviceStatusPending = deviceStatusPending
                lastReportedDeviceStatusOldest = deviceStatusOldest
                lastReportedDeviceCommandsPending = deviceCommandsPending
                lastReportedDeviceCommandsOldest = deviceCommandsOldest
                lastReportedDeviceEventsPending = deviceEventsPending
                lastReportedDeviceEventsOldest = deviceEventsOldest
                lastReportedReportDeliveryPending = reportDeliveryPending
                lastReportedReportDeliveryOldest = reportDeliveryOldest
                lastReportedLogStorePending = logStorePending
                lastReportedLogStoreTotal = logStoreTotal
                lastReportedTelemetryStorePending = telemetryStorePending
                lastReportedTelemetryStoreTotal = telemetryStoreTotal
                lastReportedReportStorePending = reportStorePending
                lastReportedReportStoreTotal = reportStoreTotal
                lastReportedStale = isStale
            }
        }
        supabaseOutboxStatusLogger = loggerJob
        applicationScope.launch {
            runCatching { sync.flushOnce() }
                    .onFailure { error -> Log.w(TAG, "Initial Supabase outbox flush failed", error) }
        }
    }

    private fun startDictionaryWatchers() {
        dictionaryUpdateWatcher?.close()
        manufacturerOverrideWatcher?.close()
        val directoryResolver = { File(filesDir, "dictionaries") }

    val updateWatcher =
        LocalDictionaryUpdateWatcher(
            scope = applicationScope,
            directoryResolver = directoryResolver,
            onUpdateAvailable = {
                ManufacturerDictionaryInstaller.refresh(this@KioskApp)
                notifyDictionaryRuntimes(force = true)
            }
        )
        updateWatcher.start()
        dictionaryUpdateWatcher = updateWatcher

        val overridesWatcher =
                LocalDictionaryUpdateWatcher(
                        scope = applicationScope,
                        directoryResolver = directoryResolver,
                        fileName = "manufacturer_dtc_overrides.json",
                        onUpdateAvailable = {
                            val refreshed = ManufacturerDictionaryInstaller.refresh(this@KioskApp)
                            if (refreshed) {
                                notifyDictionaryRuntimes(force = true)
                            }
                        }
                )
        overridesWatcher.start()
        manufacturerOverrideWatcher = overridesWatcher

        notifyDictionaryRuntimes(force = false)
    }

    private fun notifyDictionaryRuntimes(force: Boolean) {
        if (::passThruSelfTestRuntime.isInitialized) {
            passThruSelfTestRuntime.requestDictionarySync(force)
        }
        if (::passThruDiagnosticsRuntime.isInitialized) {
            passThruDiagnosticsRuntime.requestDictionarySync(force)
        }
    }

    fun supabaseOutboxState(): StateFlow<SupabaseOutboxSync.State>? = supabaseOutboxSync?.state

    fun supabaseOutboxStatus(): StateFlow<SupabaseOutboxMonitor.Status>? =
            supabaseOutboxMonitor?.status

    fun requestSupabaseOutboxRefresh(): Boolean {
        val monitor = supabaseOutboxMonitor ?: return false
        monitor.requestRefresh()
        return true
    }

    fun requestDeviceStatusSnapshotNow(): Boolean {
        if (deviceStatusReporter == null) {
            return false
        }
        recordDeviceStatusSnapshot()
        return true
    }

    fun obdConnectionSnapshot(): StateFlow<ObdConnectionSnapshot>? =
            obdConnectionController?.snapshot

    fun obdSessionState(): StateFlow<ObdSessionState>? =
            obdConnectionController?.sessionState

    fun requestObdConnect(force: Boolean = false): Boolean {
        val controller = obdConnectionController ?: return false
        controller.connectAsync(force = force)
        return true
    }

    fun requestObdDisconnect(): Boolean {
        val controller = obdConnectionController ?: return false
        controller.disconnectAsync()
        return true
    }

    fun dictionarySyncState(): StateFlow<DictionarySyncState>? =
            when {
                ::passThruSelfTestRuntime.isInitialized ->
                        passThruSelfTestRuntime.dictionarySyncState()
                ::passThruDiagnosticsRuntime.isInitialized ->
                        passThruDiagnosticsRuntime.dictionarySyncState()
                else -> null
            }

    fun requestDictionarySync(force: Boolean = false): Boolean {
        var triggered = false
        if (::passThruSelfTestRuntime.isInitialized) {
            passThruSelfTestRuntime.requestDictionarySync(force)
            triggered = true
        }
        if (::passThruDiagnosticsRuntime.isInitialized) {
            passThruDiagnosticsRuntime.requestDictionarySync(force)
            triggered = true
        }
        return triggered
    }

    fun diagnosticsLogs(): DiagnosticsLogService = diagnosticsLogService

    fun diagnosticsLogSummary(): StateFlow<DiagnosticsLogSummary>? =
            diagnosticsLogSummaryObserver?.state

    fun dispatchManagedMdmCommand(command: MdmCommand): Boolean {
        val manager = mdmCommandManager
        if (manager == null) {
            val queued = pendingMdmCommands.enqueue(command)
            if (queued) {
                Log.i(TAG, "Queued MDM command ${command.type} until manager is ready")
            } else {
                Log.w(TAG, "MDM command manager not ready and queue is full; dropping ${command.type}")
            }
            return queued
        }
        manager.submit(command)
        return true
    }

        fun diagnosticsTelemetrySummary(): StateFlow<DiagnosticsTelemetrySummary>? =
            diagnosticsTelemetrySummaryObserver?.state

    fun diagnosticsReportWorkflow(): DiagnosticsReportWorkflow? = diagnosticsReportWorkflow

    private fun isDebuggable(): Boolean =
            (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    companion object {
        private const val TAG = "KioskApp"
        private const val STALE_LOG_INTERVAL_MILLIS = 60_000L
        private const val DEVICE_STATUS_REPORT_INTERVAL_MS = 5L * 60L * 1000L
        private const val DIAGNOSTICS_DIR = "diagnostics"
        private const val MANUFACTURER_DTC_ASSET = "dtc_database.json"
        private const val MAX_PENDING_MDM_COMMANDS = 32
        private const val PENDING_MDM_COMMAND_PREVIEW_LIMIT = 5
        @Volatile private var instance: KioskApp? = null

        fun from(context: Context): KioskApp {
            val app = context.applicationContext
            return app as? KioskApp ?: error("Application context is not KioskApp")
        }

        fun passThruSelfTests(context: Context): PassThruSelfTestRuntime =
                from(context).passThruSelfTestRuntime

    fun passThruDiagnostics(context: Context): PassThruDiagnosticsRuntime =
        from(context).passThruDiagnosticsRuntime

    fun obdConnectionSnapshot(context: Context): StateFlow<ObdConnectionSnapshot>? =
        from(context).obdConnectionSnapshot()

    fun obdSessionState(context: Context): StateFlow<ObdSessionState>? =
        from(context).obdSessionState()

    fun requestObdConnect(context: Context, force: Boolean = false): Boolean =
        from(context).requestObdConnect(force)

    fun requestObdDisconnect(context: Context): Boolean =
        from(context).requestObdDisconnect()

        fun diagnosticsLogs(context: Context): DiagnosticsLogService =
                from(context).diagnosticsLogService

        fun diagnosticsTelemetrySummary(context: Context): StateFlow<DiagnosticsTelemetrySummary>? =
            from(context).diagnosticsTelemetrySummary()

    fun diagnosticsMetricDefinitions(context: Context): List<DiagnosticsMetricDefinition> =
        from(context).diagnosticsMetricDefinitions()

    fun diagnosticsMetricSnapshot(context: Context): StateFlow<DiagnosticsProfileSnapshot?> =
        from(context).diagnosticsMetricSnapshotState()

    fun diagnosticsReport(context: Context): StateFlow<DiagnosticsReportWorkflow.Result?> =
        from(context).diagnosticsReportState()

    fun diagnosticsReportWorkflow(context: Context): DiagnosticsReportWorkflow? =
        from(context).diagnosticsReportWorkflow()

    fun payments(context: Context): PaymentModule =
        from(context).payments()

    fun evaluateDiagnosticsMetrics(
        context: Context,
        samples: Collection<ObdPidSample>
    ): DiagnosticsMetricEvaluation? =
        from(context).evaluateDiagnosticsMetrics(samples)

    fun resetDiagnosticsMetrics(context: Context) {
        from(context).resetDiagnosticsMetrics()
    }
    }

    private fun recordSupabaseOutboxSnapshot(
        message: String,
        totalPending: Int,
        diagnosticsPending: Int,
        diagnosticsOldestAtMillis: Long?,
        diagnosticsOldestAgeMillis: Long?,
        telemetryPending: Int,
        telemetryOldestAtMillis: Long?,
        telemetryOldestAgeMillis: Long?,
        reportsPending: Int,
        reportsOldestAtMillis: Long?,
        reportsOldestAgeMillis: Long?,
        paymentsPending: Int,
        paymentsOldestAtMillis: Long?,
        paymentsOldestAgeMillis: Long?,
        deviceStatusPending: Int,
        deviceStatusOldestAtMillis: Long?,
        deviceStatusOldestAgeMillis: Long?,
        deviceCommandsPending: Int,
        deviceCommandsOldestAtMillis: Long?,
        deviceCommandsOldestAgeMillis: Long?,
        deviceEventsPending: Int,
        deviceEventsOldestAtMillis: Long?,
        deviceEventsOldestAgeMillis: Long?,
        reportDeliveriesPending: Int,
        reportDeliveriesOldestAtMillis: Long?,
        reportDeliveriesOldestAgeMillis: Long?,
        diagnosticsLocalPending: Int?,
        diagnosticsLocalTotal: Int?,
        telemetryLocalPending: Int?,
        telemetryLocalTotal: Int?,
        reportsLocalPending: Int?,
        reportsLocalTotal: Int?,
        localSummary: String
    ) {
        val metadata = LinkedHashMap<String, Any?>().apply {
            put("totalPending", totalPending)
            put("diagnosticsPending", diagnosticsPending)
            diagnosticsOldestAtMillis?.let { put("diagnosticsOldestAt", it) }
            diagnosticsOldestAgeMillis?.let { put("diagnosticsOldestAgeMs", it) }
            put("telemetryPending", telemetryPending)
            telemetryOldestAtMillis?.let { put("telemetryOldestAt", it) }
            telemetryOldestAgeMillis?.let { put("telemetryOldestAgeMs", it) }
            put("reportsPending", reportsPending)
            reportsOldestAtMillis?.let { put("reportsOldestAt", it) }
            reportsOldestAgeMillis?.let { put("reportsOldestAgeMs", it) }
            put("paymentsPending", paymentsPending)
            paymentsOldestAtMillis?.let { put("paymentsOldestAt", it) }
            paymentsOldestAgeMillis?.let { put("paymentsOldestAgeMs", it) }
            put("deviceStatusPending", deviceStatusPending)
            deviceStatusOldestAtMillis?.let { put("deviceStatusOldestAt", it) }
            deviceStatusOldestAgeMillis?.let { put("deviceStatusOldestAgeMs", it) }
            put("deviceCommandsPending", deviceCommandsPending)
            deviceCommandsOldestAtMillis?.let { put("deviceCommandsOldestAt", it) }
            deviceCommandsOldestAgeMillis?.let { put("deviceCommandsOldestAgeMs", it) }
            put("deviceEventsPending", deviceEventsPending)
            deviceEventsOldestAtMillis?.let { put("deviceEventsOldestAt", it) }
            deviceEventsOldestAgeMillis?.let { put("deviceEventsOldestAgeMs", it) }
            put("reportDeliveriesPending", reportDeliveriesPending)
            reportDeliveriesOldestAtMillis?.let { put("reportDeliveriesOldestAt", it) }
            reportDeliveriesOldestAgeMillis?.let { put("reportDeliveriesOldestAgeMs", it) }
            diagnosticsLocalPending?.let { put("diagnosticsLocalPending", it) }
            diagnosticsLocalTotal?.let { put("diagnosticsLocalTotal", it) }
            telemetryLocalPending?.let { put("telemetryLocalPending", it) }
            telemetryLocalTotal?.let { put("telemetryLocalTotal", it) }
            reportsLocalPending?.let { put("reportsLocalPending", it) }
            reportsLocalTotal?.let { put("reportsLocalTotal", it) }
            if (localSummary.isNotEmpty()) {
                put("localSummary", localSummary)
            }
        }
        logSupabaseOutboxEvent(message, metadata)
    }

    private fun logSupabaseOutboxEvent(message: String, metadata: Map<String, Any?>) {
        runCatching {
            diagnosticsLogService.log(
                category = "supabase.outbox",
                message = message,
                metadata = metadata
            )
        }.onFailure { error ->
            Log.w(TAG, "Failed to log Supabase outbox event", error)
        }
    }

    private fun buildPaymentModule(): PaymentModule {
        val environment = resolvePaymentEnvironment()
        val encryptionKey = BuildConfig.PAYMENTS_ENCRYPTION_KEY.takeIf { it.isNotBlank() }
        val storeFile = paymentStoreFile()
        val storeOptions = PaymentIntentStoreOptions(
            environment = environment,
            file = storeFile,
            encryptionKey = encryptionKey
        )
        return PaymentModule(
            PaymentModuleOptions(
                environment = environment,
                logger = AndroidPaymentLogger(),
                storeOptions = storeOptions,
                auditSink = paymentAuditSink,
                storePrunePolicy = PaymentIntentStorePrunePolicy(),
                gatewayFactory = { options ->
                    PaymentGatewayResolver.create(
                        environment = options.environment,
                        logger = options.logger,
                    )
                },
            )
        )
    }

    private fun resolvePaymentEnvironment(): PaymentEnvironment {
        val label = KioskRuntimeConfig.environment().trim().uppercase(Locale.ROOT)
        return when (label) {
            "PROD" -> PaymentEnvironment.PROD
            "QA", "STAGE", "STAGING" -> PaymentEnvironment.QA
            else -> PaymentEnvironment.DEV
        }
    }

    private fun paymentStoreFile(): File = File(paymentsDirectory(), "intents.json")

    private fun paymentAuditFile(): File = File(paymentsDirectory(), "audit.ndjson")

    private fun paymentsDirectory(): File {
        val dir = File(filesDir, ".selfservice/payments")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun refreshPaymentAuditSink() {
        val fileSink = FilePaymentAuditSink(paymentAuditFile()) { error ->
            Log.w(TAG, "Payment audit file sink failure", error)
        }
        val supabaseSink = supabaseOutboxWriter?.let {
            SupabasePaymentAuditSink(it) { KioskRuntimeConfig.paymentsAuditFields() }
        }
        val delegate = combinePaymentAuditSinks(fileSink, supabaseSink)
        paymentAuditSink.setDelegate(delegate)
    }

    private fun refreshDeviceStatusReporter() {
        stopDeviceStatusReporter()
        val writer = supabaseOutboxWriter ?: run {
            deviceStatusReporter = null
            return
        }
        val reporter = DeviceStatusReporter(writer)
        deviceStatusReporter = reporter
        recordDeviceStatusSnapshot()
        deviceStatusJob = applicationScope.launch {
            while (isActive) {
                delay(DEVICE_STATUS_REPORT_INTERVAL_MS)
                recordDeviceStatusSnapshot()
            }
        }
    }

    private fun stopDeviceStatusReporter() {
        deviceStatusJob?.cancel()
        deviceStatusJob = null
    }

    private fun refreshMdmCommandManager() {
        stopMdmCommandManager()
        val writer = supabaseOutboxWriter ?: return
        val manager = MdmCommandManager(
            context = this,
            scope = applicationScope,
            writer = writer,
            identityProvider = ::resolveDeviceIdentity,
            handler = DefaultMdmCommandHandler(
                supabaseRefresher = ::requestSupabaseOutboxRefresh,
                dictionarySyncRequester = { requestDictionarySync(force = true) },
                heartbeatRequester = ::requestDeviceStatusSnapshotNow,
                obdConnector = { force -> requestObdConnect(force = force) },
                obdDisconnector = ::requestObdDisconnect
            ),
            enableDebugReceiver = BuildConfig.DEBUG
        )
        mdmCommandManager = manager
        flushPendingMdmCommands(manager)
        mdmCommandStateJob = applicationScope.launch {
            manager.lastCommandState.collect { state ->
                lastCompletedMdmCommand = state
            }
        }
    }

    private fun stopMdmCommandManager() {
        mdmCommandStateJob?.cancel()
        mdmCommandStateJob = null
        mdmCommandManager?.shutdown()
        mdmCommandManager = null
    }

    private fun flushPendingMdmCommands(manager: MdmCommandManager) {
        val drained = pendingMdmCommands.drain { pending ->
            manager.submit(pending)
        }
        if (drained > 0) {
            Log.i(TAG, "Dispatched $drained queued MDM commands after manager initialization")
        }
    }

    private fun resolveDeviceIdentity(): DeviceIdentity = DeviceIdentity(
        kioskId = KioskRuntimeConfig.kioskId(),
        environment = KioskRuntimeConfig.environment(),
        mdmDeviceId = BuildConfig.MDM_DEVICE_ID.takeIf { it.isNotBlank() }
    )

    private fun recordDeviceStatusSnapshot() {
        val reporter = deviceStatusReporter ?: return
        val snapshot = runCatching { buildDeviceStatusSnapshot() }
            .onFailure { error -> Log.w(TAG, "Failed to capture device status snapshot", error) }
            .getOrNull() ?: return
        runCatching { reporter.record(snapshot) }
            .onFailure { error -> Log.w(TAG, "Failed to enqueue device status snapshot", error) }
    }

    private fun buildDeviceStatusSnapshot(nowMillis: Long = System.currentTimeMillis()): DeviceStatusSnapshot {
        val kioskId = KioskRuntimeConfig.kioskId()
        val environment = KioskRuntimeConfig.environment()
        val mdmDeviceId = BuildConfig.MDM_DEVICE_ID.takeIf { it.isNotBlank() }
        val policyVersion = BuildConfig.MDM_POLICY_VERSION.takeIf { it.isNotBlank() }
        val battery = readBatteryStatus()
        val network = resolveNetworkStatus()
        val uptimeSeconds = TimeUnit.MILLISECONDS.toSeconds(SystemClock.elapsedRealtime())
        val annotations = mutableMapOf<String, Any?>(
            "heartbeat_interval_ms" to DEVICE_STATUS_REPORT_INTERVAL_MS,
            "captured_at_ms" to nowMillis
        )

        val pendingCommandCount = pendingMdmCommands.size()
        if (pendingCommandCount > 0) {
            annotations["pending_mdm_command_count"] = pendingCommandCount
            val pendingCommands = pendingMdmCommands.snapshot(maxEntries = MAX_PENDING_MDM_COMMANDS)
            val typeCounts = pendingCommands.groupingBy { it.type }.eachCount()
            if (typeCounts.isNotEmpty()) {
                annotations["pending_mdm_command_types"] = typeCounts
            }
            val preview = pendingCommands
                .take(PENDING_MDM_COMMAND_PREVIEW_LIMIT)
                .map { command ->
                    mapOf(
                        "id" to command.id,
                        "type" to command.type,
                        "age_ms" to maxOf(0L, nowMillis - command.issuedAtMillis),
                        "source" to command.source
                    )
                }
            if (preview.isNotEmpty()) {
                annotations["pending_mdm_command_preview"] = preview
            }
            val oldestIssuedAt = pendingCommands.firstOrNull()?.issuedAtMillis
            if (oldestIssuedAt != null) {
                annotations["pending_mdm_command_oldest_age_ms"] = maxOf(0L, nowMillis - oldestIssuedAt)
            }
        }
        val lastCommand = lastCompletedMdmCommand
        return DeviceStatusSnapshot(
            kioskId = kioskId,
            environment = environment,
            mdmDeviceId = mdmDeviceId,
            serialNumber = resolveDeviceSerial(),
            hardwareModel = Build.MODEL,
            hardwareManufacturer = Build.MANUFACTURER,
            osVersion = Build.VERSION.RELEASE,
            osApiLevel = Build.VERSION.SDK_INT,
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            batteryPercent = battery?.levelPercent,
            isCharging = battery?.isCharging,
            networkType = network?.typeLabel,
            vpnActive = network?.vpnActive,
            policyVersion = policyVersion,
            uptimeSeconds = uptimeSeconds,
            status = DeviceStatusSnapshot.DeviceState.ACTIVE,
            lastCommandId = lastCommand?.id,
            lastCommandStatus = lastCommand?.status?.wireValue,
            annotations = annotations
        )
    }

    private fun readBatteryStatus(): BatteryStatus? {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) {
            ((level / scale.toFloat()) * 100).roundToInt().coerceIn(0, 100)
        } else {
            null
        }
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return BatteryStatus(percent, isCharging)
    }

    private fun resolveNetworkStatus(): NetworkStatus? {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val network = connectivityManager.activeNetwork ?: return NetworkStatus("offline", vpnActive = false)
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkStatus("unknown", vpnActive = false)
        val vpnActive = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        val label = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            vpnActive -> "vpn"
            else -> "unknown"
        }
        return NetworkStatus(label, vpnActive)
    }

    @SuppressLint("HardwareIds")
    private fun resolveDeviceSerial(): String? {
        val serial = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Build.getSerial()
            } else {
                @Suppress("DEPRECATION")
                Build.SERIAL
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        if (!serial.isNullOrBlank()) {
            return serial
        }
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)?.takeIf { it.isNotBlank() }
    }

    private data class BatteryStatus(val levelPercent: Int?, val isCharging: Boolean)

    private data class NetworkStatus(val typeLabel: String?, val vpnActive: Boolean)

    private class AndroidPaymentLogger : PaymentLogger {
        override fun debug(message: String, context: Map<String, Any?>?) {
            Log.d(TAG, format(message, context))
        }

        override fun info(message: String, context: Map<String, Any?>?) {
            Log.i(TAG, format(message, context))
        }

        override fun warn(message: String, context: Map<String, Any?>?) {
            Log.w(TAG, format(message, context))
        }

        override fun error(message: String, context: Map<String, Any?>?) {
            Log.e(TAG, format(message, context))
        }

        private fun format(message: String, context: Map<String, Any?>?): String {
            if (context.isNullOrEmpty()) {
                return message
            }
            val suffix = context.entries.joinToString(prefix = " [", postfix = "]") { (key, value) ->
                "$key=${value ?: "null"}"
            }
            return message + suffix
        }
    }
}
