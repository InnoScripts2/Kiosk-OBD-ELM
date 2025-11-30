package com.selfservice.kiosk

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.webkit.WebViewAssetLoader
import com.selfservice.core.permissions.BluetoothEnvironmentStatus
import com.selfservice.core.permissions.BluetoothPrerequisiteAction
import com.selfservice.core.permissions.BluetoothPrerequisiteCommand
import com.selfservice.kiosk.databinding.ActivityMainBinding
import com.selfservice.kiosk.dictionary.DictionarySyncStatusUiMapper
import com.selfservice.kiosk.diagnostics.DiagnosticsJavascriptBridge
import com.selfservice.kiosk.diagnostics.ui.DiagnosticsMetricCategorySummaryView
import com.selfservice.kiosk.diagnostics.ui.DiagnosticsMetricSummaryView
import com.selfservice.kiosk.diagnostics.ui.DiagnosticsMetricSelection
import com.selfservice.kiosk.diagnostics.ui.DiagnosticsMetricUiFormatter
import com.selfservice.kiosk.diagnostics.ui.DiagnosticsMetricsPanelView
import com.selfservice.kiosk.diagnostics.ui.DiagnosticsRecommendationUiFormatter
import com.selfservice.kiosk.diagnostics.ui.DiagnosticsRecommendationsPanelView
import com.selfservice.kiosk.supabase.SupabaseOutboxMonitor
import com.selfservice.kiosk.supabase.SupabaseOutboxStatusUiMapper
import com.selfservice.kiosk.supabase.SupabaseOutboxStatusUiModel
import com.selfservice.obd.core.dictionary.DictionarySyncState
import com.selfservice.obd.ui.prerequisites.BluetoothPrerequisitePresenter
import com.selfservice.obd.ui.prerequisites.BluetoothPrerequisiteUiState
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsProfileSnapshot
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricTrend
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendation
import com.selfservice.kiosk.payments.PaymentsJavascriptBridge
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val webView: WebView get() = binding.webView

    private var touchStartTime: Long = 0
    private var touchPointerCount: Int = 0
    private lateinit var prerequisiteStatusContainerView: View
    private lateinit var prerequisiteStatusTextView: TextView
    private lateinit var prerequisiteStatusHintView: TextView
    private lateinit var dictionaryStatusContainerView: View
    private lateinit var dictionaryStatusTextView: TextView
    private lateinit var dictionaryStatusHintView: TextView
    private lateinit var dictionaryStatusProgressView: View
    private lateinit var outboxStatusContainerView: View
    private lateinit var outboxStatusTextView: TextView
    private lateinit var outboxStatusHintView: TextView
    private lateinit var outboxStatusProgressView: View
    private lateinit var diagnosticsPanelsContainerView: View
    private lateinit var diagnosticsMetricsSummaryView: DiagnosticsMetricSummaryView
    private lateinit var diagnosticsCategorySummaryView: DiagnosticsMetricCategorySummaryView
    private lateinit var diagnosticsMetricsPanelView: DiagnosticsMetricsPanelView
    private lateinit var diagnosticsRecommendationsPanelView: DiagnosticsRecommendationsPanelView
    private lateinit var diagnosticsMetricFormatter: DiagnosticsMetricUiFormatter
    private lateinit var diagnosticsRecommendationFormatter: DiagnosticsRecommendationUiFormatter
    private lateinit var prerequisitesPresenter: BluetoothPrerequisitePresenter
    private var prerequisitesJob: Job? = null
    private var supabaseStatusJob: Job? = null
    private var dictionaryStatusJob: Job? = null
    private var diagnosticsMetricsJob: Job? = null
    private var diagnosticsMetricTrendsJob: Job? = null
    private var diagnosticsRecommendationsJob: Job? = null
    private var diagnosticsLogSummaryJob: Job? = null
    private var diagnosticsTelemetrySummaryJob: Job? = null
    private var diagnosticsReportSummaryJob: Job? = null
    private var hasDiagnosticsMetrics: Boolean = false
    private var hasDiagnosticsRecommendations: Boolean = false
    private var selectedDiagnosticsSelection: DiagnosticsMetricSelection? = null
    private var latestDiagnosticsSnapshot: DiagnosticsProfileSnapshot? = null
    private var latestMetricTrends: Map<String, DiagnosticsMetricTrend> = emptyMap()
    private var lastPrerequisiteSignature: Pair<BluetoothEnvironmentStatus, BluetoothPrerequisiteAction>? = null
    private var lastExecutedCommand: BluetoothPrerequisiteCommand = BluetoothPrerequisiteCommand.None
    private var lastAccessibilityAnnouncement: String? = null
    private var lastOutboxAnnouncement: String? = null
    private var lastDictionaryAnnouncement: String? = null
    private var lastOutboxStatusModel: SupabaseOutboxStatusUiModel = SupabaseOutboxStatusUiModel.Hidden
    private var lastSupabaseOutboxStatus: SupabaseOutboxMonitor.Status = SupabaseOutboxMonitor.Status()
    private var paymentsBridge: PaymentsJavascriptBridge? = null
    private var paymentsClientScript: String? = null
    private var diagnosticsBridge: DiagnosticsJavascriptBridge? = null
    private var diagnosticsClientScript: String? = null
    private lateinit var kioskUrlResolution: KioskUrlResolution
    private var kioskFallbackIndex: Int = 0
    private val kioskAssetLoader by lazy {
        WebViewAssetLoader.Builder()
            .setDomain(ASSET_LOADER_DOMAIN)
            .addPathHandler(ASSET_LOADER_PATH, WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // State monitor will re-emit; command deduplication prevents loops.
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Monitor refreshes prerequisites on subsequent polling iteration.
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prerequisiteStatusContainerView = binding.root.findViewById(R.id.prerequisiteStatusContainer)
        prerequisiteStatusTextView = binding.root.findViewById(R.id.prerequisiteStatusText)
        prerequisiteStatusHintView = binding.root.findViewById(R.id.prerequisiteStatusHint)
        dictionaryStatusContainerView = binding.root.findViewById(R.id.dictionaryStatusContainer)
        dictionaryStatusTextView = binding.root.findViewById(R.id.dictionaryStatusText)
        dictionaryStatusHintView = binding.root.findViewById(R.id.dictionaryStatusHint)
        dictionaryStatusProgressView = binding.root.findViewById(R.id.dictionaryStatusProgress)
        outboxStatusContainerView = binding.root.findViewById(R.id.outboxStatusContainer)
        outboxStatusTextView = binding.root.findViewById(R.id.outboxStatusText)
        outboxStatusHintView = binding.root.findViewById(R.id.outboxStatusHint)
        outboxStatusProgressView = binding.root.findViewById(R.id.outboxStatusProgress)
        diagnosticsPanelsContainerView = binding.diagnosticsPanelsContainer
        diagnosticsMetricsSummaryView = binding.diagnosticsMetricsSummary
        diagnosticsCategorySummaryView = binding.diagnosticsCategorySummary
        diagnosticsMetricsPanelView = binding.diagnosticsMetricsPanel
        diagnosticsRecommendationsPanelView = binding.diagnosticsRecommendationsPanel
        diagnosticsMetricFormatter = DiagnosticsMetricUiFormatter(resources)
        diagnosticsRecommendationFormatter = DiagnosticsRecommendationUiFormatter(resources)
    diagnosticsMetricsPanelView.setOnTileClickListener(::handleMetricTileClick)
    diagnosticsRecommendationsPanelView.setOnRecommendationSelectedListener(::handleRecommendationTileClick)

        // Allow operator to tap the overlay to force a manual re-check of prerequisites.
        prerequisiteStatusContainerView.setOnClickListener { performManualPrerequisiteEvaluation() }
        ViewCompat.replaceAccessibilityAction(
            prerequisiteStatusContainerView,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
            getString(R.string.bluetooth_prereq_retry_accessibility_action)
        ) { _, _ -> performManualPrerequisiteEvaluation() }

        dictionaryStatusContainerView.setOnClickListener { performManualDictionaryRefresh() }
        ViewCompat.replaceAccessibilityAction(
            dictionaryStatusContainerView,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
            getString(R.string.dictionary_sync_retry_accessibility_action)
        ) { _, _ -> performManualDictionaryRefresh() }

        // Supabase outbox overlay refresh is operator-driven when queue stalls.
        outboxStatusContainerView.setOnClickListener { performManualOutboxRefresh() }
        ViewCompat.replaceAccessibilityAction(
            outboxStatusContainerView,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
            getString(R.string.supabase_outbox_retry_accessibility_action)
        ) { _, _ -> performManualOutboxRefresh() }
        outboxStatusContainerView.setOnLongClickListener { showOutboxStatusDetails() }
        ViewCompat.replaceAccessibilityAction(
            outboxStatusContainerView,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
            getString(R.string.supabase_outbox_status_details_accessibility_action)
        ) { _, _ -> showOutboxStatusDetails() }

        webView.settings.configure()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                injectPaymentsClient(view)
                injectDiagnosticsClient(view)
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                if (request.isForMainFrame && errorResponse.statusCode >= HTTP_ERROR_THRESHOLD) {
                    attemptFallbackLoad(view, request.url?.toString(),
                        "http_${errorResponse.statusCode}")
                }
                super.onReceivedHttpError(view, request, errorResponse)
            }

            @Suppress("OverridingDeprecatedMember", "DEPRECATION")
            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    attemptFallbackLoad(view, request.url?.toString(), "code_${error.errorCode}")
                }
                super.onReceivedError(view, request, error)
            }

            @Deprecated("Deprecated WebView callback")
            @Suppress("OverridingDeprecatedMember", "DEPRECATION")
            override fun onReceivedError(
                view: WebView,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                attemptFallbackLoad(view, failingUrl, "legacy_${errorCode}")
                super.onReceivedError(view, errorCode, description, failingUrl)
            }

            @Suppress("OverridingDeprecatedMember", "DEPRECATION")
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val assetResponse = kioskAssetLoader.shouldInterceptRequest(request.url)
                return assetResponse ?: super.shouldInterceptRequest(view, request)
            }

            @Deprecated("Deprecated WebView callback", level = DeprecationLevel.HIDDEN)
            @Suppress("OverridingDeprecatedMember", "DEPRECATION")
            override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
                val uri = runCatching { Uri.parse(url) }.getOrNull()
                val assetResponse = uri?.let { kioskAssetLoader.shouldInterceptRequest(it) }
                return assetResponse ?: super.shouldInterceptRequest(view, url)
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.setOnTouchListener(::handleTouch)

        val kioskApp = KioskApp.from(this)
        val paymentModule = KioskApp.payments(this)
        val bridge = PaymentsJavascriptBridge(
            webView = webView,
            payments = paymentModule,
            scope = lifecycleScope,
        )
        paymentsBridge = bridge
        webView.addJavascriptInterface(bridge, PaymentsJavascriptBridge.INTERFACE_NAME)

        val diagnosticsBridge = DiagnosticsJavascriptBridge(
            webView = webView,
            app = kioskApp,
            scope = lifecycleScope,
        )
        this.diagnosticsBridge = diagnosticsBridge
        webView.addJavascriptInterface(diagnosticsBridge, DiagnosticsJavascriptBridge.INTERFACE_NAME)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        finish()
                    }
                }
            }
        )

        initializeKioskUrl()

        setupBluetoothPrerequisites()
        setupDictionarySyncStatus()
        setupSupabaseOutboxStatus()
        setupDiagnosticsMetricTrends()
        setupDiagnosticsMetricsPanel()
        setupDiagnosticsRecommendationsPanel()
    }

    private fun WebSettings.configure() {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        cacheMode = WebSettings.LOAD_DEFAULT
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        allowFileAccess = true
        allowContentAccess = true
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
    }

    private fun injectPaymentsClient(target: WebView) {
        val script = loadPaymentsClientScript()
        if (script.isNullOrBlank()) {
            return
        }
        target.evaluateJavascript(script, null)
    }

    private fun loadPaymentsClientScript(): String? {
        paymentsClientScript?.let { return it }
        val script = runCatching {
            assets.open(PAYMENTS_CLIENT_ASSET).bufferedReader().use { it.readText() }
        }.onFailure { error ->
            Log.w(TAG, "Failed to load payments client script", error)
        }.getOrNull()
        paymentsClientScript = script
        return script
    }

    private fun injectDiagnosticsClient(target: WebView) {
        val script = loadDiagnosticsClientScript()
        if (script.isNullOrBlank()) {
            return
        }
        target.evaluateJavascript(script, null)
    }

    private fun loadDiagnosticsClientScript(): String? {
        diagnosticsClientScript?.let { return it }
        val script = runCatching {
            assets.open(DIAGNOSTICS_CLIENT_ASSET).bufferedReader().use { it.readText() }
        }.onFailure { error ->
            Log.w(TAG, "Failed to load diagnostics client script", error)
        }.getOrNull()
        diagnosticsClientScript = script
        return script
    }

    private fun handleTouch(@Suppress("UNUSED_PARAMETER") view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.pointerCount == 3) {
                    touchStartTime = System.currentTimeMillis()
                    touchPointerCount = 3
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                touchPointerCount = event.pointerCount
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (touchPointerCount == 3 &&
                    System.currentTimeMillis() - touchStartTime >= GESTURE_HOLD_MS
                ) {
                    showUrlDialog()
                }
                touchPointerCount = 0
            }
        }
        return false
    }

    private fun showUrlDialog() {
        val input = EditText(this).apply { setText(webView.url) }

        AlertDialog.Builder(this)
            .setTitle(R.string.kiosk_url_dialog_title)
            .setMessage(R.string.kiosk_url_dialog_message)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newUrl = input.text.toString()
                if (newUrl.isNotEmpty()) {
                    webView.loadKioskUrl(newUrl)
                    kioskFallbackIndex = kioskUrlResolution.fallbackChain.size
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun initializeKioskUrl() {
        kioskUrlResolution = KioskUrlResolver.resolve(
            appMode = BuildConfig.APP_MODE,
            options = KioskUrlOptions(
                primary = getString(R.string.kiosk_url),
                remote = getString(R.string.kiosk_url_remote),
                dev = getString(R.string.kiosk_url_dev),
                fallback = getString(R.string.kiosk_url_fallback)
            )
        )
        kioskFallbackIndex = 0
        webView.loadKioskUrl(kioskUrlResolution.initialUrl)
    }

    private fun attemptFallbackLoad(target: WebView, failingUrl: String?, reason: String) {
        if (!::kioskUrlResolution.isInitialized) {
            return
        }
        val fallback = kioskUrlResolution.fallbackChain.getOrNull(kioskFallbackIndex) ?: return
        kioskFallbackIndex += 1
        val normalizedFallback = normalizeKioskUrl(fallback)
        if (normalizedFallback.equals(failingUrl, ignoreCase = true)) {
            attemptFallbackLoad(target, failingUrl, reason)
            return
        }
        Log.w(
            TAG,
            "Switching kiosk UI URL to $normalizedFallback due to load failure ($reason) for $failingUrl"
        )
        target.post { target.loadKioskUrl(fallback) }
    }

    private fun setupBluetoothPrerequisites() {
        prerequisitesPresenter = BluetoothPrerequisitesEntryPoint.create(applicationContext)

        handlePrerequisiteState(prerequisitesPresenter.evaluate())

        prerequisitesJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                prerequisitesPresenter.observe().collect { state ->
                    handlePrerequisiteState(state)
                }
            }
        }
    }

    private fun setupSupabaseOutboxStatus() {
        val app = KioskApp.from(this)
        val statusFlow = app.supabaseOutboxStatus()
        if (statusFlow == null) {
            hideOutboxOverlay()
            clearLocalBufferObservers()
            lastSupabaseOutboxStatus = SupabaseOutboxMonitor.Status()
            return
        }
        lastSupabaseOutboxStatus = statusFlow.value
        supabaseStatusJob?.cancel()
        supabaseStatusJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                statusFlow.collect { status ->
                    lastSupabaseOutboxStatus = status
                    updateOutboxOverlay(status)
                }
            }
        }
        setupLocalBufferObservers(app)
        refreshOutboxOverlayWithLastStatus()
    }

    private fun setupLocalBufferObservers(app: KioskApp) {
        clearLocalBufferObservers()
        app.diagnosticsLogSummary()?.let { flow ->
            diagnosticsLogSummaryJob = lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    flow.collect {
                        refreshOutboxOverlayWithLastStatus()
                    }
                }
            }
        }
        app.diagnosticsTelemetrySummary()?.let { flow ->
            diagnosticsTelemetrySummaryJob = lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    flow.collect {
                        refreshOutboxOverlayWithLastStatus()
                    }
                }
            }
        }
        val reportSummaryFlow = app.diagnosticsReportSummaryState()
        diagnosticsReportSummaryJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                reportSummaryFlow.collect {
                    refreshOutboxOverlayWithLastStatus()
                }
            }
        }
    }

    private fun clearLocalBufferObservers() {
        diagnosticsLogSummaryJob?.cancel()
        diagnosticsLogSummaryJob = null
        diagnosticsTelemetrySummaryJob?.cancel()
        diagnosticsTelemetrySummaryJob = null
        diagnosticsReportSummaryJob?.cancel()
        diagnosticsReportSummaryJob = null
    }

    private fun refreshOutboxOverlayWithLastStatus() {
        updateOutboxOverlay(lastSupabaseOutboxStatus)
    }

    private fun setupDictionarySyncStatus() {
        val app = KioskApp.from(this)
        val stateFlow = app.dictionarySyncState()
        if (stateFlow == null) {
            hideDictionaryOverlay()
            return
        }
        dictionaryStatusJob?.cancel()
        dictionaryStatusJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                stateFlow.collect { state ->
                    updateDictionaryOverlay(state)
                }
            }
        }
    }

    private fun setupDiagnosticsMetricsPanel() {
        val app = KioskApp.from(this)
        diagnosticsMetricsJob?.cancel()
        diagnosticsMetricsJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.diagnosticsMetricSnapshotState().collect { snapshot ->
                    renderDiagnosticsMetrics(snapshot)
                }
            }
        }
    }

    private fun setupDiagnosticsMetricTrends() {
        val app = KioskApp.from(this)
        diagnosticsMetricTrendsJob?.cancel()
        diagnosticsMetricTrendsJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.diagnosticsMetricTrendsState().collect { trends ->
                    latestMetricTrends = trends
                    latestDiagnosticsSnapshot?.let { renderDiagnosticsMetrics(it) }
                }
            }
        }
    }

    private fun renderDiagnosticsMetrics(snapshot: DiagnosticsProfileSnapshot?) {
        latestDiagnosticsSnapshot = snapshot
        val models = snapshot?.metrics?.map { insight ->
            diagnosticsMetricFormatter.map(
                insight = insight,
                trend = latestMetricTrends[insight.definition.id]
            )
        } ?: emptyList()
        diagnosticsMetricsSummaryView.render(models)
        diagnosticsCategorySummaryView.render(models)
        diagnosticsMetricsPanelView.render(models)
        hasDiagnosticsMetrics = models.isNotEmpty()
        val selection = selectedDiagnosticsSelection
        if (selection != null && models.none { selection.matches(it.id, it.pidKey) }) {
            selectedDiagnosticsSelection = null
        }
        applySelectedDiagnosticsMetric()
        updateDiagnosticsPanelsVisibility()
    }

    private fun setupDiagnosticsRecommendationsPanel() {
        val app = KioskApp.from(this)
        diagnosticsRecommendationsJob?.cancel()
        diagnosticsRecommendationsJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.diagnosticsRecommendationsState().collect { recommendations ->
                    renderDiagnosticsRecommendations(recommendations)
                }
            }
        }
    }

    private fun renderDiagnosticsRecommendations(recommendations: List<DiagnosticsRecommendation>) {
        val models = recommendations.map(diagnosticsRecommendationFormatter::map)
        diagnosticsRecommendationsPanelView.render(models)
        hasDiagnosticsRecommendations = models.isNotEmpty()
        val selection = selectedDiagnosticsSelection
        if (selection != null && models.none { selection.matches(it.metricId, it.pidKey) }) {
            selectedDiagnosticsSelection = null
        }
        applySelectedDiagnosticsMetric()
        updateDiagnosticsPanelsVisibility()
    }

    private fun updateDiagnosticsPanelsVisibility() {
        diagnosticsPanelsContainerView.isVisible = hasDiagnosticsMetrics || hasDiagnosticsRecommendations
    }

    private fun handleMetricTileClick(selection: DiagnosticsMetricSelection) {
        val newSelection = if (selectedDiagnosticsSelection == selection) null else selection
        updateSelectedDiagnosticsMetric(newSelection)
    }

    private fun handleRecommendationTileClick(selection: DiagnosticsMetricSelection) {
        val newSelection = if (selectedDiagnosticsSelection == selection) null else selection
        updateSelectedDiagnosticsMetric(newSelection)
    }

    private fun updateSelectedDiagnosticsMetric(selection: DiagnosticsMetricSelection?) {
        if (selectedDiagnosticsSelection == selection) {
            return
        }
        selectedDiagnosticsSelection = selection
        applySelectedDiagnosticsMetric()
    }

    private fun applySelectedDiagnosticsMetric() {
        diagnosticsMetricsPanelView.highlightMetric(selectedDiagnosticsSelection)
        diagnosticsRecommendationsPanelView.highlightMetric(selectedDiagnosticsSelection)
    }

    private fun handlePrerequisiteState(state: BluetoothPrerequisiteUiState) {
        val signature = state.status to state.action
        if (signature == lastPrerequisiteSignature) {
            return
        }
        lastPrerequisiteSignature = signature

        updatePrerequisiteOverlay(state)

        if (state.isReady) {
            KioskApp.requestObdConnect(applicationContext)
            lastExecutedCommand = BluetoothPrerequisiteCommand.None
            return
        }
        KioskApp.requestObdDisconnect(applicationContext)
        executePrerequisiteCommand(state.command)
    }

    private fun executePrerequisiteCommand(command: BluetoothPrerequisiteCommand) {
        if (command == BluetoothPrerequisiteCommand.None) {
            lastExecutedCommand = BluetoothPrerequisiteCommand.None
            return
        }
        if (command == lastExecutedCommand) {
            return
        }
        lastExecutedCommand = command
        when (command) {
            is BluetoothPrerequisiteCommand.RequestPermissions -> {
                if (command.permissions.isNotEmpty()) {
                    requestPermissionsLauncher.launch(command.permissions)
                }
            }
            is BluetoothPrerequisiteCommand.StartActivityForResult -> {
                enableBluetoothLauncher.launch(command.intent)
            }
            is BluetoothPrerequisiteCommand.StartActivity -> {
                startActivity(command.intent)
            }
            BluetoothPrerequisiteCommand.None -> Unit
        }
    }

    private fun updatePrerequisiteOverlay(state: BluetoothPrerequisiteUiState) {
        if (state.isReady) {
            prerequisiteStatusContainerView.isVisible = false
            prerequisiteStatusContainerView.isClickable = false
            prerequisiteStatusContainerView.isFocusable = false
            prerequisiteStatusHintView.isVisible = false
            prerequisiteStatusContainerView.contentDescription = null
            lastAccessibilityAnnouncement = null
            return
        }
        val message = statusMessage(state)
        val hint = getString(R.string.bluetooth_prereq_retry_hint)
        prerequisiteStatusTextView.text = message
        prerequisiteStatusHintView.text = hint
        prerequisiteStatusHintView.isVisible = true
        prerequisiteStatusContainerView.isVisible = true
        prerequisiteStatusContainerView.isClickable = true
        prerequisiteStatusContainerView.isFocusable = true
        val combinedDescription = "$message $hint"
        prerequisiteStatusContainerView.contentDescription = combinedDescription
        announcePrerequisiteState(combinedDescription)
    }

    private fun statusMessage(state: BluetoothPrerequisiteUiState): String {
        return when (state.status) {
            BluetoothEnvironmentStatus.Ready -> getString(R.string.bluetooth_prereq_ready)
            is BluetoothEnvironmentStatus.MissingPermissions -> getString(R.string.bluetooth_prereq_missing_permissions)
            BluetoothEnvironmentStatus.BluetoothDisabled -> getString(R.string.bluetooth_prereq_enable_bluetooth)
            BluetoothEnvironmentStatus.LocationDisabled -> getString(R.string.bluetooth_prereq_enable_location)
        }
    }

    private fun performManualPrerequisiteEvaluation(): Boolean {
        if (!::prerequisitesPresenter.isInitialized) {
            return false
        }
        val state = prerequisitesPresenter.evaluate()
        handlePrerequisiteState(state)
        return true
    }

    private fun performManualOutboxRefresh(): Boolean {
        return KioskApp.from(this).requestSupabaseOutboxRefresh()
    }

    private fun performManualDictionaryRefresh(): Boolean {
        return KioskApp.from(this).requestDictionarySync(force = true)
    }

    private fun updateOutboxOverlay(status: SupabaseOutboxMonitor.Status) {
        val model = SupabaseOutboxStatusUiMapper.map(status, System.currentTimeMillis())
        lastOutboxStatusModel = model
        val localBufferEntries = collectLocalBufferEntries()
        val localOverlayMessage = formatLocalBufferOverlayMessage(localBufferEntries)
        val shouldShowLocalOnly = !model.isVisible && localOverlayMessage != null
        if (!model.isVisible && !shouldShowLocalOnly) {
            hideOutboxOverlay()
            return
        }

        val baseMessage = if (model.isVisible) {
            when (model.severity) {
                SupabaseOutboxStatusUiModel.Severity.Pending ->
                    formatPendingOutboxMessage(
                        pendingCount = model.pendingCount,
                        oldestAgeMillis = model.oldestPendingAgeMillis,
                        diagnosticsPendingCount = model.diagnosticsPendingCount,
                        diagnosticsOldestAgeMillis = model.diagnosticsOldestPendingAgeMillis,
                        telemetryPendingCount = model.telemetryPendingCount,
                        telemetryOldestAgeMillis = model.telemetryOldestPendingAgeMillis,
                        reportsPendingCount = model.reportsPendingCount,
                        reportsOldestAgeMillis = model.reportsOldestPendingAgeMillis,
                        paymentsPendingCount = model.paymentsAuditPendingCount,
                        paymentsOldestAgeMillis = model.paymentsAuditOldestPendingAgeMillis,
                        deviceStatusPendingCount = model.deviceStatusPendingCount,
                        deviceStatusOldestAgeMillis = model.deviceStatusOldestPendingAgeMillis,
                        deviceCommandsPendingCount = model.deviceCommandsPendingCount,
                        deviceCommandsOldestAgeMillis = model.deviceCommandsOldestPendingAgeMillis,
                        deviceEventsPendingCount = model.deviceEventsPendingCount,
                        deviceEventsOldestAgeMillis = model.deviceEventsOldestPendingAgeMillis,
                        reportDeliveriesPendingCount = model.reportDeliveriesPendingCount,
                        reportDeliveriesOldestAgeMillis = model.reportDeliveriesOldestPendingAgeMillis
                    )
                SupabaseOutboxStatusUiModel.Severity.Error -> formatOutboxErrorMessage(model.errorDescription)
                SupabaseOutboxStatusUiModel.Severity.Stale -> formatStaleOutboxMessage(model.staleAgeMillis)
            }
        } else {
            null
        }

        val message = when {
            baseMessage != null && localOverlayMessage != null ->
                getString(
                    R.string.supabase_outbox_overlay_with_local_suffix,
                    baseMessage,
                    localOverlayMessage
                )
            baseMessage != null -> baseMessage
            else -> localOverlayMessage ?: ""
        }
        val hint = getString(R.string.supabase_outbox_touch_hint)

        outboxStatusTextView.text = message
        outboxStatusHintView.text = hint
        outboxStatusHintView.isVisible = true
        outboxStatusProgressView.isVisible = model.isVisible && model.isSyncActive
        outboxStatusContainerView.isVisible = true
        outboxStatusContainerView.isClickable = true
        outboxStatusContainerView.isFocusable = true

        val contentDescription = "$message $hint"
        outboxStatusContainerView.contentDescription = contentDescription
        announceOutboxState(contentDescription)
    }

    private fun showOutboxStatusDetails(): Boolean {
        val message = buildOutboxDetailsMessage(lastOutboxStatusModel)
        AlertDialog.Builder(this)
            .setTitle(R.string.supabase_outbox_status_dialog_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        return true
    }

    private fun buildOutboxDetailsMessage(model: SupabaseOutboxStatusUiModel): String {
        val lines = mutableListOf<String>()
        buildOutboxStaleLine(model)?.let { lines += it }
        formatOutboxSummaryLine(model.pendingCount, model.oldestPendingAgeMillis)?.let { lines += it }
        val categoryLines = listOfNotNull(
            buildOutboxCategoryMessage(
                count = model.diagnosticsPendingCount,
                oldestAgeMillis = model.diagnosticsOldestPendingAgeMillis,
                pluralResId = R.plurals.supabase_outbox_status_pending_diagnostics
            ),
            buildOutboxCategoryMessage(
                count = model.telemetryPendingCount,
                oldestAgeMillis = model.telemetryOldestPendingAgeMillis,
                pluralResId = R.plurals.supabase_outbox_status_pending_telemetry
            ),
            buildOutboxCategoryMessage(
                count = model.reportsPendingCount,
                oldestAgeMillis = model.reportsOldestPendingAgeMillis,
                pluralResId = R.plurals.supabase_outbox_status_pending_reports
            ),
            buildOutboxCategoryMessage(
                count = model.paymentsAuditPendingCount,
                oldestAgeMillis = model.paymentsAuditOldestPendingAgeMillis,
                pluralResId = R.plurals.supabase_outbox_status_pending_payments
            ),
            buildOutboxCategoryMessage(
                count = model.deviceStatusPendingCount,
                oldestAgeMillis = model.deviceStatusOldestPendingAgeMillis,
                pluralResId = R.plurals.supabase_outbox_status_pending_device_status
            ),
            buildOutboxCategoryMessage(
                count = model.deviceCommandsPendingCount,
                oldestAgeMillis = model.deviceCommandsOldestPendingAgeMillis,
                pluralResId = R.plurals.supabase_outbox_status_pending_device_commands
            ),
            buildOutboxCategoryMessage(
                count = model.deviceEventsPendingCount,
                oldestAgeMillis = model.deviceEventsOldestPendingAgeMillis,
                pluralResId = R.plurals.supabase_outbox_status_pending_device_events
            ),
            buildOutboxCategoryMessage(
                count = model.reportDeliveriesPendingCount,
                oldestAgeMillis = model.reportDeliveriesOldestPendingAgeMillis,
                pluralResId = R.plurals.supabase_outbox_status_pending_report_deliveries
            )
        ).map { detail -> getString(R.string.supabase_outbox_status_dialog_entry_prefix, detail) }
        if (categoryLines.isNotEmpty()) {
            lines.addAll(categoryLines)
        }
        model.errorDescription?.takeIf { it.isNotBlank() }?.let { error ->
            lines += getString(R.string.supabase_outbox_status_dialog_error, error)
        }
        val localSection = buildLocalBufferSectionLines()
        if (localSection.isNotEmpty()) {
            if (lines.isNotEmpty()) {
                lines += ""
            }
            lines.addAll(localSection)
        }
        if (lines.isEmpty()) {
            return getString(R.string.supabase_outbox_status_dialog_empty)
        }
        return lines.joinToString(separator = "\n")
    }

    private fun buildOutboxStaleLine(model: SupabaseOutboxStatusUiModel): String? {
        if (!model.isStale) {
            return null
        }
        val formattedAge = model.staleAgeMillis?.takeIf { it > 0L }?.let { formatElapsedDuration(it) }
        return formattedAge?.let { age ->
            getString(R.string.supabase_outbox_status_dialog_stale_with_age, age)
        } ?: getString(R.string.supabase_outbox_status_dialog_stale_generic)
    }

    private fun buildLocalBufferSectionLines(): List<String> {
        val entries = collectLocalBufferEntries()
        if (entries.isEmpty()) {
            return emptyList()
        }
        val lines = mutableListOf<String>()
        lines += getString(R.string.supabase_outbox_status_dialog_local_heading)
        entries.mapTo(lines) { entry ->
            val payload = formatLocalBufferDialogEntry(entry)
            getString(R.string.supabase_outbox_status_dialog_entry_prefix, payload)
        }
        return lines
    }

    private fun collectLocalBufferEntries(nowMillis: Long = System.currentTimeMillis()): List<LocalBufferEntry> {
        val app = KioskApp.from(this)
        val logSummary = app.diagnosticsLogSummary()?.value
        val telemetrySummary = app.diagnosticsTelemetrySummary()?.value
        val reportSummary = app.diagnosticsReportSummaryState().value
        return listOfNotNull(
            logSummary?.let { summary ->
                createLocalBufferEntry(
                    label = getString(R.string.supabase_outbox_status_dialog_local_logs_label),
                    pendingCount = summary.pendingCount,
                    totalCount = summary.totalCount,
                    oldestTimestampMillis = summary.oldestPendingAtMillis,
                    nowMillis = nowMillis
                )
            },
            telemetrySummary?.let { summary ->
                createLocalBufferEntry(
                    label = getString(R.string.supabase_outbox_status_dialog_local_telemetry_label),
                    pendingCount = summary.pendingCount,
                    totalCount = summary.totalCount,
                    oldestTimestampMillis = summary.oldestPendingAtMillis,
                    nowMillis = nowMillis
                )
            },
            reportSummary.let { summary ->
                createLocalBufferEntry(
                    label = getString(R.string.supabase_outbox_status_dialog_local_reports_label),
                    pendingCount = summary.pendingCount,
                    totalCount = summary.totalCount,
                    oldestTimestampMillis = summary.oldestPendingAtMillis,
                    nowMillis = nowMillis
                )
            }
        )
    }

    private fun createLocalBufferEntry(
        label: String,
        pendingCount: Int,
        totalCount: Int,
        oldestTimestampMillis: Long?,
        nowMillis: Long
    ): LocalBufferEntry? {
        if (pendingCount <= 0 && totalCount <= 0) {
            return null
        }
        val ageMillis = calculateAgeMillis(nowMillis, oldestTimestampMillis)
        return LocalBufferEntry(
            label = label,
            pendingCount = pendingCount,
            totalCount = totalCount,
            oldestAgeMillis = ageMillis
        )
    }

    private fun formatLocalBufferDialogEntry(entry: LocalBufferEntry): String {
        val counts = getString(
            R.string.supabase_outbox_status_dialog_local_counts,
            entry.pendingCount,
            entry.totalCount
        )
        val ageSuffix = formatAgeSuffix(entry.oldestAgeMillis)
        val payload = if (ageSuffix.isNullOrEmpty()) counts else "$counts $ageSuffix"
        return "${entry.label}: $payload"
    }

    private fun formatLocalBufferOverlayMessage(entries: List<LocalBufferEntry>): String? {
        if (entries.isEmpty()) {
            return null
        }
        val summary = entries.map { entry ->
            val base = getString(
                R.string.supabase_outbox_local_overlay_entry,
                entry.label,
                entry.pendingCount
            )
            val ageSuffix = formatAgeSuffix(entry.oldestAgeMillis)
            if (ageSuffix.isNullOrEmpty()) base else "$base $ageSuffix"
        }.joinToString(separator = ". ")
        return getString(R.string.supabase_outbox_local_overlay_summary, summary)
    }

    private data class LocalBufferEntry(
        val label: String,
        val pendingCount: Int,
        val totalCount: Int,
        val oldestAgeMillis: Long?
    )

    private fun calculateAgeMillis(nowMillis: Long, timestampMillis: Long?): Long? {
        if (timestampMillis == null || timestampMillis <= 0L) {
            return null
        }
        val age = nowMillis - timestampMillis
        return age.takeIf { it >= 0L }
    }

    private fun formatOutboxSummaryLine(
        pendingCount: Int,
        oldestAgeMillis: Long?
    ): String? {
        if (pendingCount <= 0) {
            return null
        }
        val ageSuffix = formatAgeSuffix(oldestAgeMillis)
        val base = resources.getQuantityString(
            R.plurals.supabase_outbox_status_pending,
            pendingCount,
            pendingCount
        )
        return if (ageSuffix.isNullOrEmpty()) base else "$base $ageSuffix"
    }

    private fun updateDictionaryOverlay(state: DictionarySyncState) {
        val model = DictionarySyncStatusUiMapper.map(state)
        if (!model.isVisible) {
            hideDictionaryOverlay()
            return
        }

        val message = if (model.isRunning) {
            getString(R.string.dictionary_sync_running)
        } else {
            formatDictionaryErrorMessage(model.errorDescription)
        }
        val hint = getString(R.string.dictionary_sync_touch_hint)

        dictionaryStatusTextView.text = message
        dictionaryStatusHintView.text = hint
        dictionaryStatusHintView.isVisible = true
        dictionaryStatusProgressView.isVisible = model.isRunning
        dictionaryStatusContainerView.isVisible = true
        dictionaryStatusContainerView.isClickable = true
        dictionaryStatusContainerView.isFocusable = true

        val contentDescription = "$message $hint"
        dictionaryStatusContainerView.contentDescription = contentDescription
        announceDictionaryState(contentDescription)
    }

    private fun hideOutboxOverlay() {
        outboxStatusContainerView.isVisible = false
        outboxStatusContainerView.isClickable = false
        outboxStatusContainerView.isFocusable = false
        outboxStatusHintView.isVisible = false
        outboxStatusProgressView.isVisible = false
        outboxStatusContainerView.contentDescription = null
        lastOutboxAnnouncement = null
    }

    private fun hideDictionaryOverlay() {
        dictionaryStatusContainerView.isVisible = false
        dictionaryStatusContainerView.isClickable = false
        dictionaryStatusContainerView.isFocusable = false
        dictionaryStatusHintView.isVisible = false
        dictionaryStatusProgressView.isVisible = false
        dictionaryStatusContainerView.contentDescription = null
        lastDictionaryAnnouncement = null
    }

    private fun formatAgeSuffix(ageMillis: Long?): String? {
        if (ageMillis == null) {
            return null
        }
        val formatted = formatElapsedDuration(ageMillis)
        return formatted.takeIf { it.isNotEmpty() }?.let {
            getString(R.string.supabase_outbox_status_pending_age_suffix, it)
        }
    }

    private fun formatPendingOutboxMessage(
        pendingCount: Int,
        oldestAgeMillis: Long?,
        diagnosticsPendingCount: Int,
        diagnosticsOldestAgeMillis: Long?,
        telemetryPendingCount: Int,
        telemetryOldestAgeMillis: Long?,
        reportsPendingCount: Int,
        reportsOldestAgeMillis: Long?,
        paymentsPendingCount: Int,
        paymentsOldestAgeMillis: Long?,
        deviceStatusPendingCount: Int,
        deviceStatusOldestAgeMillis: Long?,
        deviceCommandsPendingCount: Int,
        deviceCommandsOldestAgeMillis: Long?,
        deviceEventsPendingCount: Int,
        deviceEventsOldestAgeMillis: Long?,
        reportDeliveriesPendingCount: Int,
        reportDeliveriesOldestAgeMillis: Long?
    ): String {
        val base = resources.getQuantityString(
            R.plurals.supabase_outbox_status_pending,
            pendingCount,
            pendingCount
        )
        val ageSuffix = formatAgeSuffix(oldestAgeMillis)
        val message = if (ageSuffix.isNullOrEmpty()) {
            base
        } else {
            "$base $ageSuffix"
        }

        val categoryMessages = listOfNotNull(
            buildOutboxCategoryMessage(
                count = diagnosticsPendingCount,
                oldestAgeMillis = diagnosticsOldestAgeMillis,
                pluralResId = R.plurals.supabase_outbox_status_pending_diagnostics
            ),
            buildOutboxCategoryMessage(
                count = telemetryPendingCount,
                oldestAgeMillis = telemetryOldestAgeMillis,
                pluralResId = R.plurals.supabase_outbox_status_pending_telemetry
            ),
            buildOutboxCategoryMessage(
                count = reportsPendingCount,
                oldestAgeMillis = reportsOldestAgeMillis,
                pluralResId = R.plurals.supabase_outbox_status_pending_reports
            ),
            buildOutboxCategoryMessage(
                count = paymentsPendingCount,
                oldestAgeMillis = paymentsOldestAgeMillis,
                pluralResId = R.plurals.supabase_outbox_status_pending_payments
            ),
            buildOutboxCategoryMessage(
                count = deviceStatusPendingCount,
                oldestAgeMillis = deviceStatusOldestAgeMillis,
                pluralResId = R.plurals.supabase_outbox_status_pending_device_status
            ),
            buildOutboxCategoryMessage(
                count = deviceCommandsPendingCount,
                oldestAgeMillis = deviceCommandsOldestAgeMillis,
                pluralResId = R.plurals.supabase_outbox_status_pending_device_commands
            ),
            buildOutboxCategoryMessage(
                count = deviceEventsPendingCount,
                oldestAgeMillis = deviceEventsOldestAgeMillis,
                pluralResId = R.plurals.supabase_outbox_status_pending_device_events
            ),
            buildOutboxCategoryMessage(
                count = reportDeliveriesPendingCount,
                oldestAgeMillis = reportDeliveriesOldestAgeMillis,
                pluralResId = R.plurals.supabase_outbox_status_pending_report_deliveries
            )
        )

        return if (categoryMessages.isEmpty()) {
            message
        } else {
            (listOf(message) + categoryMessages).joinToString(separator = ". ")
        }
    }

    private fun formatStaleOutboxMessage(staleAgeMillis: Long?): String {
        val formattedAge = staleAgeMillis?.takeIf { it > 0L }?.let { formatElapsedDuration(it) }
        return formattedAge?.let { age ->
            getString(R.string.supabase_outbox_status_stale_with_age, age)
        } ?: getString(R.string.supabase_outbox_status_stale_generic)
    }

    private fun buildOutboxCategoryMessage(
            count: Int,
            oldestAgeMillis: Long?,
            pluralResId: Int
    ): String? {
        if (count <= 0) {
            return null
        }
        val base = resources.getQuantityString(pluralResId, count, count)
        val ageSuffix = formatAgeSuffix(oldestAgeMillis)
        return ageSuffix?.let { "$base $it" } ?: base
    }

    private fun formatOutboxErrorMessage(rawMessage: String?): String {
        val message = rawMessage?.takeIf { it.isNotBlank() } ?: getString(R.string.supabase_outbox_status_error_generic)
        return getString(R.string.supabase_outbox_status_error, message)
    }

    private fun formatElapsedDuration(ageMillis: Long): String {
        if (ageMillis <= 0L) {
            return getString(R.string.supabase_outbox_duration_under_minute)
        }
        val days = TimeUnit.MILLISECONDS.toDays(ageMillis)
        if (days > 0L) {
            return getString(R.string.supabase_outbox_duration_days, days)
        }
        val hours = TimeUnit.MILLISECONDS.toHours(ageMillis)
        if (hours > 0L) {
            return getString(R.string.supabase_outbox_duration_hours, hours)
        }
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ageMillis)
        if (minutes > 0L) {
            return getString(R.string.supabase_outbox_duration_minutes, minutes)
        }
        return getString(R.string.supabase_outbox_duration_under_minute)
    }

    private fun announceOutboxState(description: String) {
        if (description == lastOutboxAnnouncement) {
            return
        }
        if (outboxStatusContainerView.isVisible) {
            outboxStatusContainerView.announceForAccessibility(description)
            lastOutboxAnnouncement = description
        }
    }

    private fun announceDictionaryState(description: String) {
        if (description == lastDictionaryAnnouncement) {
            return
        }
        if (dictionaryStatusContainerView.isVisible) {
            dictionaryStatusContainerView.announceForAccessibility(description)
            lastDictionaryAnnouncement = description
        }
    }

    private fun announcePrerequisiteState(description: String) {
        if (description == lastAccessibilityAnnouncement) {
            return
        }
        if (prerequisiteStatusContainerView.isVisible) {
            prerequisiteStatusContainerView.announceForAccessibility(description)
            lastAccessibilityAnnouncement = description
        }
    }

    private fun formatDictionaryErrorMessage(rawMessage: String?): String {
        val message = rawMessage?.takeIf { it.isNotBlank() } ?: getString(R.string.dictionary_sync_error_generic)
        return getString(R.string.dictionary_sync_failed, message)
    }

    override fun onDestroy() {
        prerequisitesJob?.cancel()
        prerequisitesJob = null
        supabaseStatusJob?.cancel()
        supabaseStatusJob = null
        dictionaryStatusJob?.cancel()
        dictionaryStatusJob = null
        diagnosticsMetricsJob?.cancel()
        diagnosticsMetricsJob = null
        diagnosticsRecommendationsJob?.cancel()
        diagnosticsRecommendationsJob = null
        clearLocalBufferObservers()
        paymentsBridge?.let {
            try {
                webView.removeJavascriptInterface(PaymentsJavascriptBridge.INTERFACE_NAME)
            } catch (_: Throwable) {
                // WebView might already be destroyed; ignore.
            }
            it.dispose()
            paymentsBridge = null
        }
        diagnosticsBridge?.let {
            try {
                webView.removeJavascriptInterface(DiagnosticsJavascriptBridge.INTERFACE_NAME)
            } catch (_: Throwable) {
                // WebView might already be destroyed; ignore.
            }
            it.dispose()
            diagnosticsBridge = null
        }
        super.onDestroy()
    }

    private fun WebView.loadKioskUrl(rawUrl: String) {
        loadUrl(normalizeKioskUrl(rawUrl))
    }

    private fun normalizeKioskUrl(rawUrl: String): String {
        if (rawUrl.startsWith(ASSET_FILE_PREFIX, ignoreCase = true)) {
            val relativePath = rawUrl.removePrefix(ASSET_FILE_PREFIX)
            return ASSET_LOADER_BASE_URL + relativePath
        }
        return rawUrl
    }

    companion object {
        private const val GESTURE_HOLD_MS = 5_000L
        private const val PAYMENTS_CLIENT_ASSET = "js/kiosk-payments.js"
        private const val DIAGNOSTICS_CLIENT_ASSET = "js/kiosk-diagnostics.js"
        private const val HTTP_ERROR_THRESHOLD = 400
        private const val TAG = "MainActivity"
        private const val ASSET_FILE_PREFIX = "file:///android_asset/"
        private const val ASSET_LOADER_DOMAIN = "appassets.androidplatform.net"
        private const val ASSET_LOADER_PATH = "/assets/"
        private const val ASSET_LOADER_BASE_URL = "https://" + ASSET_LOADER_DOMAIN + "/assets/"
    }
}
