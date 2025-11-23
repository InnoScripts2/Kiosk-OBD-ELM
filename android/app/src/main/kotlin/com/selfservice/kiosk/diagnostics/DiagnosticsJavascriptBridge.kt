package com.selfservice.kiosk.diagnostics

import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.VisibleForTesting
import com.autoservice.diagnostics.obd.DiagnosticsPreviewRuntime
import com.autoservice.diagnostics.obd.ObdProtocol
import com.autoservice.diagnostics.obd.ObdSample
import com.autoservice.diagnostics.obd.PidThresholds
import com.autoservice.diagnostics.obd.StandardPids
import com.selfservice.kiosk.KioskApp
import com.selfservice.kiosk.KioskApp.DiagnosticsConnectionOptions
import com.selfservice.obd.core.connection.BleDevice
import com.selfservice.obd.core.dictionary.ObdDictionaryManager
import com.selfservice.obd.core.diagnostics.DiagnosticsFailure
import com.selfservice.obd.core.diagnostics.ObdDiagnosticsRequest
import com.selfservice.obd.core.diagnostics.ObdDiagnosticsResult
import com.selfservice.obd.core.diagnostics.ObdPidRequest
import com.selfservice.obd.core.dtc.DtcDictionaryRevision
import com.selfservice.obd.core.dtc.ObdDtcDefinition
import com.selfservice.obd.core.pid.ObdPidDefinition
import com.selfservice.obd.core.pid.PidDictionaryRevision
import com.selfservice.obd.core.protocol.ObdDtcEntry
import com.selfservice.obd.core.protocol.ObdPidSample
import com.selfservice.obd.core.passthru.PassThruDiagnosticsCoordinator
import com.selfservice.obd.core.platform.BluetoothPrerequisiteResult
import com.selfservice.obd.core.session.ConnectedAdapter
import com.selfservice.obd.core.session.ObdSessionState
import com.selfservice.feature.reports.DiagnosticsReportCustomer
import com.selfservice.feature.reports.DiagnosticsReportVehicle
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricInsight
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricTrend
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricTrendPoint
import com.selfservice.kiosk.diagnostics.summary.DiagnosticsMetricSummary
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsProfileSnapshot
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendation
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendationThreshold
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendationThresholdType
import com.selfservice.kiosk.diagnostics.DiagnosticsPreviewController
import com.selfservice.platform.data.diagnostics.DiagnosticsTelemetryRecord
import com.selfservice.platform.data.diagnostics.DiagnosticsTelemetryStore
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * WebView bridge exposing diagnostics capabilities to JavaScript clients.
 * Messages follow the `{ requestId, action, payload }` contract similar to the payments bridge.
 */
class DiagnosticsJavascriptBridge @VisibleForTesting internal constructor(
    private val dispatcher: EventDispatcher,
    private val scope: CoroutineScope,
    private val runner: DiagnosticsReportRunner,
    private val latestReportProvider: () -> DiagnosticsReportWorkflow.Result?,
    private val ioDispatcher: CoroutineDispatcher,
    private val previewRunner: DiagnosticsPreviewRunner,
    private val dictionary: ObdDictionaryManager = ObdDictionaryManager.shared,
    private val telemetryStore: DiagnosticsTelemetryStore? = null,
) {

    constructor(
        webView: WebView,
        app: KioskApp,
        scope: CoroutineScope,
    ) : this(
        dispatcher = WebViewEventDispatcher(webView),
        scope = scope,
        runner = DiagnosticsReportRunner { request, metadata, options ->
            app.runDiagnosticsReport(request, metadata, options)
        },
        latestReportProvider = { app.diagnosticsReportState().value },
        ioDispatcher = Dispatchers.IO,
        previewRunner = DiagnosticsPreviewRunner(
            DiagnosticsPreviewController()::capture
        ),
        telemetryStore = app.diagnosticsTelemetryStore(),
    )

    @Volatile
    private var disposed: Boolean = false
    private val active = AtomicBoolean(false)
    @Volatile
    private var currentJob: Job? = null

    @JavascriptInterface
    fun postMessage(raw: String?) {
        if (disposed) return
        val message = runCatching { parseMessage(raw) }
            .onFailure { error ->
                Log.w(TAG, "Failed to parse diagnostics JS message", error)
                emitError(
                    requestId = null,
                    action = null,
                    code = "invalid_request",
                    message = error.message ?: error.javaClass.simpleName,
                )
            }
            .getOrNull() ?: return
        when (message.action.lowercase(Locale.ROOT)) {
            ACTION_RUN_REPORT -> handleRunReport(message)
            ACTION_GET_LAST_REPORT -> handleGetLastReport(message)
            ACTION_CANCEL_REPORT -> handleCancelReport(message)
            ACTION_PREVIEW_SNAPSHOT -> handlePreviewSnapshot(message)
            ACTION_DICTIONARY_LOOKUP -> handleDictionaryLookup(message)
            ACTION_DICTIONARY_SUGGEST -> handleDictionarySuggest(message)
            else -> emitError(
                requestId = message.requestId,
                action = message.action,
                code = "unknown_action",
                message = "Unsupported action ${message.action}",
            )
        }
    }

    fun dispose() {
        disposed = true
        currentJob?.cancel()
    }

    private fun handleRunReport(message: BridgeMessage) {
        if (!active.compareAndSet(false, true)) {
            emitError(
                requestId = message.requestId,
                action = message.action,
                code = "busy",
                message = "Diagnostics session is already running",
            )
            return
        }
        val job = scope.launch(ioDispatcher) {
            try {
                val payload = message.payload ?: error("payload is required")
                val vehicle = payload.optJSONObject("vehicle").toVehicle()
                val customer = payload.optJSONObject("customer").toCustomer()
                val metadata = DiagnosticsReportWorkflow.Metadata(vehicle = vehicle, customer = customer)
                val request = payload.optJSONObject("request").toDiagnosticsRequest()
                val connection = payload.optJSONObject("connection").toConnectionOptions()
                val result = runner.run(request, metadata, connection)
                emitSuccess(message.requestId, message.action, result.toJson())
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                Log.w(TAG, "run_report failed", error)
                emitError(
                    requestId = message.requestId,
                    action = message.action,
                    code = error.codeFor(),
                    message = error.message ?: error.javaClass.simpleName,
                )
            } finally {
                currentJob = null
                active.set(false)
            }
        }
        currentJob = job
    }

    private fun handleGetLastReport(message: BridgeMessage) {
        scope.launch(ioDispatcher) {
            val result = latestReportProvider()
            if (result == null) {
                emitError(
                    requestId = message.requestId,
                    action = message.action,
                    code = "not_ready",
                    message = "Diagnostics report is not available",
                )
            } else {
                emitSuccess(message.requestId, message.action, result.toJson())
            }
        }
    }

    private fun handleCancelReport(message: BridgeMessage) {
        val job = currentJob
        if (job == null || !job.isActive) {
            emitError(
                requestId = message.requestId,
                action = message.action,
                code = "not_running",
                message = "Diagnostics session is not running",
            )
            return
        }
        job.cancel(CancellationException("Cancelled via diagnostics bridge"))
        emitSuccess(
            requestId = message.requestId,
            action = message.action,
            data = JSONObject().apply { put("cancelled", true) },
        )
    }

    private fun handlePreviewSnapshot(message: BridgeMessage) {
        scope.launch(ioDispatcher) {
            try {
                val request = message.payload.toPreviewRequest()
                val snapshot = previewRunner.capture(request)
                val payload = JSONObject().apply {
                    put("snapshot", snapshot.toJson())
                }
                emitSuccess(
                    requestId = message.requestId,
                    action = message.action,
                    data = payload,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                Log.w(TAG, "preview_snapshot failed", error)
                emitError(
                    requestId = message.requestId,
                    action = message.action,
                    code = error.codeFor(),
                    message = error.message ?: error.javaClass.simpleName,
                )
            }
        }
    }

    private fun handleDictionaryLookup(message: BridgeMessage) {
        scope.launch(ioDispatcher) {
            try {
                val request = message.payload.toDictionaryLookupRequest()
                val payload = buildDictionaryLookupPayload(request)
                recordDictionaryLookupTelemetry(request, payload)
                emitSuccess(
                    requestId = message.requestId,
                    action = message.action,
                    data = payload,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                Log.w(TAG, "dictionary_lookup failed", error)
                emitError(
                    requestId = message.requestId,
                    action = message.action,
                    code = error.codeFor(),
                    message = error.message ?: error.javaClass.simpleName,
                )
            }
        }
    }

    private fun handleDictionarySuggest(message: BridgeMessage) {
        scope.launch(ioDispatcher) {
            try {
                val request = message.payload.toDictionarySuggestRequest()
                val payload = buildDictionarySuggestPayload(request)
                recordDictionarySuggestTelemetry(request, payload)
                emitSuccess(
                    requestId = message.requestId,
                    action = message.action,
                    data = payload,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                Log.w(TAG, "dictionary_suggest failed", error)
                emitError(
                    requestId = message.requestId,
                    action = message.action,
                    code = error.codeFor(),
                    message = error.message ?: error.javaClass.simpleName,
                )
            }
        }
    }

    private suspend fun recordDictionarySuggestTelemetry(
        request: DictionarySuggestRequest,
        response: JSONObject,
    ) {
        val store = telemetryStore ?: return
        val metadata = mutableMapOf<String, Any?>()
        metadata["query"] = request.query
        metadata["normalizedQuery"] = request.normalizedQuery
        metadata["compactQuery"] = request.compactQuery.ifEmpty { null }
        metadata["limit"] = request.limit
        if (request.types.isNotEmpty()) {
            metadata["types"] = request.types.map { it.value }.sorted()
        }
        request.manufacturer?.let { metadata["manufacturer"] = it }
        request.pidKeyHint?.let { hint ->
            metadata["pidKeyHint"] = canonicalPidKey(hint.mode, hint.pid)
        }
        response.optJSONObject("meta")?.let { meta ->
            meta.optIntOrNull("pidCount")?.let { metadata["pidCount"] = it }
            meta.optIntOrNull("dtcCount")?.let { metadata["dtcCount"] = it }
        }
        response.optJSONArray("pids")?.let { array ->
            metadata["pidResults"] = array.length()
            val pidKeys = mutableListOf<String>()
            val matchReasons = mutableListOf<String>()
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                entry.optString("pidKey").takeIf { it.isNotBlank() }?.let { pidKeys += it }
                entry.optJSONObject("match")
                    ?.optString("reason")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { matchReasons += it }
            }
            if (pidKeys.isNotEmpty()) {
                metadata["pidKeys"] = pidKeys
            }
            if (matchReasons.isNotEmpty()) {
                metadata["pidMatchReasons"] = matchReasons
            }
        }
        response.optJSONArray("dtcs")?.let { array ->
            metadata["dtcResults"] = array.length()
            val dtcCodes = mutableListOf<String>()
            val matchReasons = mutableListOf<String>()
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                entry.optString("code").takeIf { it.isNotBlank() }?.let { dtcCodes += it }
                entry.optJSONObject("match")
                    ?.optString("reason")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { matchReasons += it }
            }
            if (dtcCodes.isNotEmpty()) {
                metadata["dtcCodes"] = dtcCodes
            }
            if (matchReasons.isNotEmpty()) {
                metadata["dtcMatchReasons"] = matchReasons
            }
        }
        runCatching {
            store.record(
                DiagnosticsTelemetryRecord(
                    timestampMillis = System.currentTimeMillis(),
                    eventType = "DictionarySuggest",
                    sessionId = null,
                    metadata = metadata.filterValues { value ->
                        when (value) {
                            null -> false
                            is Collection<*> -> value.isNotEmpty()
                            else -> true
                        }
                    },
                )
            )
        }.onFailure { error ->
            Log.w(TAG, "Failed to record dictionary suggest telemetry", error)
        }
    }

    private fun buildDictionarySuggestPayload(request: DictionarySuggestRequest): JSONObject {
        val pidSuggestions = if (DictionarySuggestType.PID in request.types) {
            findPidSuggestions(request)
        } else {
            emptyList()
        }
        val dtcSuggestions = if (DictionarySuggestType.DTC in request.types) {
            findDtcSuggestions(request)
        } else {
            emptyList()
        }
        val meta = JSONObject().apply {
            put("query", request.query)
            put("limit", request.limit)
            val typesArray = JSONArray()
            request.types.forEach { typesArray.put(it.value) }
            put("types", typesArray)
            put("pidCount", pidSuggestions.size)
            put("dtcCount", dtcSuggestions.size)
            request.manufacturer?.let { put("manufacturer", it) }
        }
        return JSONObject().apply {
            if (pidSuggestions.isNotEmpty()) {
                put("pids", JSONArray().apply {
                    pidSuggestions.forEach { put(it) }
                })
            }
            if (dtcSuggestions.isNotEmpty()) {
                put("dtcs", JSONArray().apply {
                    dtcSuggestions.forEach { put(it) }
                })
            }
            put("meta", meta)
        }
    }

    private fun findPidSuggestions(request: DictionarySuggestRequest): List<JSONObject> {
        val candidates = mutableListOf<DictionarySuggestionCandidate>()
        dictionary.pidSnapshot().forEach { definition ->
            val match = definition.match(request) ?: return@forEach
            val suggestion = definition.toSuggestionJson(match)
            val pidKey = canonicalPidKey(definition.mode, definition.pid)
            candidates += DictionarySuggestionCandidate(
                identifier = pidKey,
                priority = match.priority,
                sourcePriority = 0,
                tieBreaker = definition.label.lowercase(Locale.ROOT),
                json = suggestion,
            )
        }
        return selectSuggestions(candidates, request.limit)
    }

    private fun findDtcSuggestions(request: DictionarySuggestRequest): List<JSONObject> {
        val candidates = mutableListOf<DictionarySuggestionCandidate>()
        dictionary.dtcSnapshot().forEach { definition ->
            val match = definition.match(request) ?: return@forEach
            val suggestion = definition.toSuggestionJson("standard", match)
            candidates += DictionarySuggestionCandidate(
                identifier = definition.code,
                priority = match.priority,
                sourcePriority = 1,
                tieBreaker = definition.code,
                json = suggestion,
            )
        }
        request.manufacturer?.let { manufacturer ->
            dictionary.manufacturerCatalog(manufacturer).forEach { definition ->
                val match = definition.match(request) ?: return@forEach
                val suggestion = definition.toSuggestionJson(manufacturer, match)
                candidates += DictionarySuggestionCandidate(
                    identifier = definition.code,
                    priority = match.priority,
                    sourcePriority = 0,
                    tieBreaker = definition.code,
                    json = suggestion,
                )
            }
        }
        return selectSuggestions(candidates, request.limit)
    }

    private fun selectSuggestions(
        candidates: List<DictionarySuggestionCandidate>,
        limit: Int,
    ): List<JSONObject> {
        if (candidates.isEmpty()) return emptyList()
        val bestById = linkedMapOf<String, DictionarySuggestionCandidate>()
        candidates.forEach { candidate ->
            val existing = bestById[candidate.identifier]
            if (existing == null || candidate.isBetterThan(existing)) {
                bestById[candidate.identifier] = candidate
            }
        }
        return bestById.values
            .sortedWith(compareBy<DictionarySuggestionCandidate> { it.priority }
                .thenBy { it.sourcePriority }
                .thenBy { it.tieBreaker })
            .take(limit)
            .map { it.json }
    }

    private fun buildDictionaryLookupPayload(request: DictionaryLookupRequest): JSONObject {
        return JSONObject().apply {
            if (request.pidSelectors.isNotEmpty()) {
                put("pids", JSONArray().apply {
                    request.pidSelectors.forEach { selector ->
                        add(selector.toJson())
                    }
                })
            }
            if (request.dtcCodes.isNotEmpty()) {
                put("dtcs", JSONArray().apply {
                    request.dtcCodes.forEach { code ->
                        add(code.toJson(request.manufacturer))
                    }
                })
            }
            put("meta", buildDictionaryMeta(request))
        }
    }

    private suspend fun recordDictionaryLookupTelemetry(
        request: DictionaryLookupRequest,
        response: JSONObject,
    ) {
        val store = telemetryStore ?: return
        val metadata = mutableMapOf<String, Any?>()
        metadata["pidCount"] = request.pidSelectors.size
        metadata["dtcCount"] = request.dtcCodes.size
        metadata["includeManufacturers"] = request.includeManufacturers
        request.manufacturer?.let { metadata["manufacturer"] = it }
        if (request.pidSelectors.isNotEmpty()) {
            metadata["pidKeys"] = request.pidSelectors.map { selector ->
                canonicalPidKey(selector.mode, selector.pid)
            }
        }
        if (request.dtcCodes.isNotEmpty()) {
            metadata["dtcCodes"] = request.dtcCodes
        }
        response.optJSONArray("pids")?.countFoundEntries()?.let { metadata["foundPidCount"] = it }
        response.optJSONArray("dtcs")?.countFoundEntries()?.let { metadata["foundDtcCount"] = it }
        response.optJSONObject("meta")?.let { meta ->
            if (meta.has("manufacturerCount")) {
                metadata["manufacturerCount"] = meta.optInt("manufacturerCount")
            }
            meta.optJSONArray("manufacturers")?.let { manufacturers ->
                metadata["manufacturersListed"] = manufacturers.length()
            }
        }
        runCatching {
            store.record(
                DiagnosticsTelemetryRecord(
                    timestampMillis = System.currentTimeMillis(),
                    eventType = "DictionaryLookup",
                    sessionId = null,
                    metadata = metadata.filterValues { value ->
                        when (value) {
                            null -> false
                            is Collection<*> -> value.isNotEmpty()
                            else -> true
                        }
                    },
                )
            )
        }.onFailure { error ->
            Log.w(TAG, "Failed to record dictionary telemetry", error)
        }
    }

    private fun DictionaryPidSelector.toJson(): JSONObject {
        val definition = dictionary.lookupPid(mode, pid)
        return JSONObject().apply {
            put("mode", mode)
            put("pid", pid)
            put("pidKey", canonicalPidKey(mode, pid))
            put("found", definition != null)
            definition?.let { def ->
                put("label", def.label)
                def.unit?.let { put("unit", it) }
                def.min?.let { put("min", it) }
                def.max?.let { put("max", it) }
                def.notes?.let { put("notes", it) }
                def.formula?.let { put("formula", it) }
                def.conversion?.let { put("conversion", it) }
                def.pollIntervalMs?.let { put("pollIntervalMs", it) }
            }
        }
    }

    private fun ObdPidDefinition.toSuggestionJson(match: SuggestionMatch): JSONObject = JSONObject().apply {
        put("pidKey", canonicalPidKey(mode, pid))
        put("mode", mode)
        put("pid", pid)
        put("label", label)
        min?.let { put("min", it) }
        max?.let { put("max", it) }
        unit?.let { put("unit", it) }
        conversion?.let { put("conversion", it) }
        formula?.let { put("formula", it) }
        pollIntervalMs?.let { put("pollIntervalMs", it) }
        notes?.let { put("notes", it) }
        put("match", JSONObject().apply {
            put("reason", match.reason)
            match.field?.let { put("field", it) }
            put("priority", match.priority)
        })
    }

    private fun String.toJson(manufacturer: String?): JSONObject {
        val standard = dictionary.lookupDtc(this)
        val (definition, source) = if (standard != null) {
            standard to "standard"
        } else {
            val manufacturerDefinition = lookupManufacturerDtc(manufacturer, this)
            manufacturerDefinition?.let { it to "manufacturer" } ?: (null to null)
        }
        return JSONObject().apply {
            put("code", this@toJson)
            if (definition != null) {
                put("found", true)
                put("label", definition.label)
                put("system", definition.system.value)
                definition.notes?.let { put("notes", it) }
                source?.let { put("source", it) }
            } else {
                put("found", false)
            }
        }
    }

    private fun ObdDtcDefinition.toSuggestionJson(source: String, match: SuggestionMatch): JSONObject = JSONObject().apply {
        put("code", code)
        put("label", label)
        put("system", system.value)
        notes?.let { put("notes", it) }
        put("source", source)
        put("match", JSONObject().apply {
            put("reason", match.reason)
            match.field?.let { put("field", it) }
            put("priority", match.priority)
        })
    }

    private fun lookupManufacturerDtc(manufacturer: String?, code: String): ObdDtcDefinition? {
        if (manufacturer.isNullOrBlank()) {
            return null
        }
        val trimmed = manufacturer.trim()
        val candidates = linkedSetOf(trimmed)
        candidates.add(trimmed.uppercase(Locale.ROOT))
        candidates.add(trimmed.lowercase(Locale.ROOT))
        for (candidate in candidates) {
            val definition = dictionary.lookupManufacturerDtc(candidate, code)
            if (definition != null) {
                return definition
            }
        }
        return null
    }

    private fun buildDictionaryMeta(request: DictionaryLookupRequest): JSONObject {
        val meta = JSONObject().apply {
            put("pidRevision", dictionary.pidRevision().toJson())
            put("dtcRevision", dictionary.dtcRevision().toJson())
            put("manufacturerCount", dictionary.manufacturerCount())
            request.manufacturer?.let { put("manufacturer", it) }
        }
        if (request.includeManufacturers) {
            val manufacturers = JSONArray()
            dictionary.manufacturerList().forEach { manufacturers.put(it) }
            meta.put("manufacturers", manufacturers)
        }
        return meta
    }

    private fun parseMessage(raw: String?): BridgeMessage {
        if (raw.isNullOrBlank()) {
            error("empty payload")
        }
        val json = JSONObject(raw)
        val action = json.optString("action").takeIf { it.isNotBlank() } ?: error("action is required")
        val requestId = json.optString("requestId").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val payload = json.optJSONObject("payload")
        return BridgeMessage(requestId = requestId, action = action, payload = payload)
    }

    private fun emitSuccess(requestId: String?, action: String, data: JSONObject) {
        if (disposed) return
        val detail = JSONObject().apply {
            put("type", RESPONSE_TYPE)
            put("ok", true)
            put("action", action)
            requestId?.let { put("requestId", it) }
            put("data", data)
        }
        dispatcher.dispatch(detail)
    }

    private fun emitError(requestId: String?, action: String?, code: String, message: String) {
        if (disposed) return
        val detail = JSONObject().apply {
            put("type", RESPONSE_TYPE)
            put("ok", false)
            action?.let { put("action", it) }
            requestId?.let { put("requestId", it) }
            put("error", JSONObject().apply {
                put("code", code)
                put("message", message)
            })
        }
        dispatcher.dispatch(detail)
    }

    private fun Throwable.codeFor(): String = when (this) {
        is IllegalArgumentException -> "invalid_argument"
        is IllegalStateException -> "failed_precondition"
        else -> "internal_error"
    }

    private fun JSONObject?.toPreviewRequest(): DiagnosticsPreviewController.Request {
        if (this == null) return DiagnosticsPreviewController.Request.Empty
        val protocolToken = optString("protocol").takeIf { it.isNotBlank() }
        val protocol = protocolToken?.let { token ->
            ObdProtocol.values().firstOrNull { it.name.equals(token, ignoreCase = true) }
                ?: ObdProtocol.values().firstOrNull { it.command.equals(token, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown protocol: $token")
        }
        val selectorsArray = optJSONArray("pidSelectors") ?: optJSONArray("pids")
        val selectors = selectorsArray?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val entry = array.optJSONObject(index) ?: continue
                    val mode = entry.optString("mode").takeIf { it.isNotBlank() } ?: continue
                    val pid = entry.optString("pid").takeIf { it.isNotBlank() } ?: continue
                    add(DiagnosticsPreviewController.Request.PidSelector(mode = mode, pid = pid))
                }
            }
        }
        return DiagnosticsPreviewController.Request(
            protocol = protocol,
            pidSelectors = selectors?.takeIf { it.isNotEmpty() }
        )
    }

    private fun JSONObject?.toDictionaryLookupRequest(): DictionaryLookupRequest {
        val payload = this
        val manufacturer = payload?.optString("manufacturer")?.takeIf { it.isNotBlank() }?.trim()
        val includeManufacturers = payload?.optBoolean("includeManufacturers", false) ?: false
        val pidSelectors = payload.optArray("pids", "pidSelectors")?.toPidSelectorList().orEmpty()
        val dtcCodes = payload.optArray("dtcs", "codes")?.toDtcList().orEmpty()
        if (pidSelectors.isEmpty() && dtcCodes.isEmpty() && !includeManufacturers) {
            throw IllegalArgumentException("At least one PID, DTC or includeManufacturers flag must be provided")
        }
        return DictionaryLookupRequest(
            pidSelectors = pidSelectors,
            dtcCodes = dtcCodes,
            manufacturer = manufacturer,
            includeManufacturers = includeManufacturers,
        )
    }

    private fun JSONArray.toPidSelectorList(): List<DictionaryPidSelector> = buildList {
        for (index in 0 until length()) {
            val entry = opt(index)
            val selector = when (entry) {
                is JSONObject -> entry.toPidSelector()
                is String -> entry.toPidSelector()
                else -> throw IllegalArgumentException("Unsupported PID selector at index $index")
            }
            add(selector)
        }
    }

    private fun JSONArray.toDtcList(): List<String> = buildList {
        for (index in 0 until length()) {
            val entry = opt(index)
            val rawCode = when (entry) {
                is JSONObject -> entry.optString("code")
                is String -> entry
                else -> throw IllegalArgumentException("Unsupported DTC entry at index $index")
            }.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("DTC entry at index $index is missing code")
            add(normalizeDtcCode(rawCode))
        }
    }

    private fun JSONObject.toPidSelector(): DictionaryPidSelector {
        val key = optString("key").takeIf { it.isNotBlank() }
        if (key != null) {
            return key.toPidSelector()
        }
        val mode = optString("mode").takeIf { it.isNotBlank() }
            ?: error("PID selector missing mode")
        val pid = optString("pid").takeIf { it.isNotBlank() }
            ?: error("PID selector missing pid")
        return DictionaryPidSelector(
            mode = normalizePidComponent(mode, "mode"),
            pid = normalizePidComponent(pid, "pid"),
        )
    }

    private fun String.toPidSelector(): DictionaryPidSelector {
        val parts = split(':')
        require(parts.size == 2) { "PID selector '$this' must use 'mode:pid' format" }
        return DictionaryPidSelector(
            mode = normalizePidComponent(parts[0], "mode"),
            pid = normalizePidComponent(parts[1], "pid"),
        )
    }

    private fun normalizePidComponent(raw: String, field: String): String {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "$field is required" }
        val withoutPrefix = if (trimmed.startsWith("0x", ignoreCase = true)) trimmed.substring(2) else trimmed
        val upper = withoutPrefix.uppercase(Locale.ROOT)
        require(upper.length <= 2) { "$field value $raw is invalid" }
        return "0x${upper.padStart(2, '0')}"
    }

    private fun normalizeDtcCode(raw: String): String {
        var token = raw.trim()
        if (token.startsWith("0x", ignoreCase = true)) {
            token = token.substring(2)
        }
        val normalized = token.uppercase(Locale.ROOT)
        require(normalized.length == 5) { "Invalid DTC code $raw" }
        return normalized
    }

    private fun JSONObject?.toDictionarySuggestRequest(): DictionarySuggestRequest {
        val payload = this ?: throw IllegalArgumentException("payload is required")
        val rawQuery = payload.optString("query").takeIf { it.isNotBlank() }?.trim()
            ?: throw IllegalArgumentException("query is required")
        val normalizedQuery = rawQuery.lowercase(Locale.ROOT)
        val compactQuery = normalizedQuery.filter { it.isLetterOrDigit() }
        val hexQuery = compactQuery.takeIf { it.isNotEmpty() && it.all { ch -> ch.isHexDigit() } }
        val tokenizedQuery = tokenizeQuery(normalizedQuery)
        val types = payload.optArray("types")?.toSuggestionTypeSet()
            ?: DictionarySuggestType.values().toSet()
        val limit = (payload.optNumber("limit")?.toInt() ?: 8).coerceIn(1, 50)
        val manufacturer = payload.optString("manufacturer").takeIf { it.isNotBlank() }?.trim()
        val pidKeyHint = parsePidSelectorFromQuery(rawQuery)
        return DictionarySuggestRequest(
            query = rawQuery,
            normalizedQuery = normalizedQuery,
            compactQuery = compactQuery,
            hexQuery = hexQuery,
            tokenizedQuery = tokenizedQuery,
            types = types,
            limit = limit,
            manufacturer = manufacturer,
            pidKeyHint = pidKeyHint,
        )
    }

    private fun tokenizeQuery(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        return QUERY_TOKEN_REGEX.split(query)
            .map { it.trim() }
            .filter { it.length >= 2 }
    }

    private fun parsePidSelectorFromQuery(raw: String): DictionaryPidSelector? {
        val token = raw.trim()
        if (token.isEmpty()) return null
        val normalized = token.replace(" ", "")
        val selector = runCatching { normalized.toPidSelector() }.getOrNull()
        if (selector != null) {
            return selector
        }
        val digitsOnly = normalized
            .replace("0x", "", ignoreCase = true)
            .replace(":", "")
        if (digitsOnly.length == 4 && digitsOnly.all { it.isHexDigit() }) {
            val mode = "0x${digitsOnly.substring(0, 2).uppercase(Locale.ROOT)}"
            val pid = "0x${digitsOnly.substring(2).uppercase(Locale.ROOT)}"
            return DictionaryPidSelector(mode = mode, pid = pid)
        }
        return null
    }

    private fun JSONArray.toSuggestionTypeSet(): Set<DictionarySuggestType> {
        val types = mutableSetOf<DictionarySuggestType>()
        for (index in 0 until length()) {
            val token = optString(index).takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Suggestion type at index $index is invalid")
            val type = DictionarySuggestType.fromToken(token)
                ?: throw IllegalArgumentException("Unknown suggestion type $token")
            types += type
        }
        if (types.isEmpty()) {
            throw IllegalArgumentException("At least one suggestion type must be provided")
        }
        return types
    }

    private fun ObdPidDefinition.match(request: DictionarySuggestRequest): SuggestionMatch? {
        if (request.normalizedQuery.isBlank()) return null
        val key = canonicalPidKey(mode, pid)
        val keyLower = key.lowercase(Locale.ROOT)
        val keyCompact = keyLower.replace(":", "")
        val modeLower = mode.lowercase(Locale.ROOT)
        val pidLower = pid.lowercase(Locale.ROOT)
        val modeHex = modeLower.withoutHexPrefix()
        val pidHex = pidLower.withoutHexPrefix()
        val combinedHex = modeHex + pidHex

        request.pidKeyHint?.let { hint ->
            val hintKey = canonicalPidKey(hint.mode, hint.pid).lowercase(Locale.ROOT)
            if (hintKey == keyLower) {
                return SuggestionMatch(priority = 0, reason = "pid_key_exact", field = "pid")
            }
        }

        val normalizedQuery = request.normalizedQuery
        if (normalizedQuery == keyLower || normalizedQuery == keyCompact || normalizedQuery == modeLower || normalizedQuery == pidLower) {
            return SuggestionMatch(priority = 0, reason = "pid_key_exact", field = "pid")
        }

        val hexQuery = request.hexQuery
        if (hexQuery != null) {
            if (keyCompact == hexQuery || combinedHex == hexQuery) {
                return SuggestionMatch(priority = 0, reason = "pid_key_exact", field = "pid")
            }
            if (keyCompact.startsWith(hexQuery) || combinedHex.startsWith(hexQuery)) {
                return SuggestionMatch(priority = 1, reason = "pid_key_prefix", field = "pid")
            }
            if (pidHex.startsWith(hexQuery)) {
                return SuggestionMatch(priority = 2, reason = "pid_component_prefix", field = "pid")
            }
        }

        if (keyLower.startsWith(normalizedQuery)) {
            return SuggestionMatch(priority = 1, reason = "pid_key_prefix", field = "pid")
        }

        val labelLower = label.lowercase(Locale.ROOT)
        if (labelLower.startsWith(normalizedQuery)) {
            return SuggestionMatch(priority = 2, reason = "label_prefix", field = "label")
        }
        if (labelLower.contains(normalizedQuery)) {
            return SuggestionMatch(priority = 3, reason = "label_contains", field = "label")
        }

        val unitLower = unit?.lowercase(Locale.ROOT)
        if (unitLower?.contains(normalizedQuery) == true) {
            return SuggestionMatch(priority = 4, reason = "unit_contains", field = "unit")
        }

        val notesLower = notes?.lowercase(Locale.ROOT)
        if (notesLower?.contains(normalizedQuery) == true) {
            return SuggestionMatch(priority = 5, reason = "notes_contains", field = "notes")
        }

        if (request.tokenizedQuery.isNotEmpty()) {
            val haystack = buildList {
                add(labelLower)
                unitLower?.let { add(it) }
                notesLower?.let { add(it) }
                add(keyLower)
            }
            val matchesTokens = request.tokenizedQuery.all { token ->
                haystack.any { entry -> entry.contains(token) }
            }
            if (matchesTokens) {
                return SuggestionMatch(priority = 6, reason = "token_match", field = "label")
            }
        }
        return null
    }

    private fun ObdDtcDefinition.match(request: DictionarySuggestRequest): SuggestionMatch? {
        val normalizedQuery = request.normalizedQuery
        if (normalizedQuery.isBlank()) return null
        val codeLower = code.lowercase(Locale.ROOT)
        val codeCompact = codeLower.filter { it.isLetterOrDigit() }
        if (codeLower == normalizedQuery || codeCompact == request.compactQuery) {
            return SuggestionMatch(priority = 0, reason = "code_exact", field = "code")
        }
        if (codeLower.startsWith(normalizedQuery) || (request.compactQuery.isNotEmpty() && codeCompact.startsWith(request.compactQuery))) {
            return SuggestionMatch(priority = 1, reason = "code_prefix", field = "code")
        }
        val labelLower = label.lowercase(Locale.ROOT)
        if (labelLower.startsWith(normalizedQuery)) {
            return SuggestionMatch(priority = 2, reason = "label_prefix", field = "label")
        }
        if (labelLower.contains(normalizedQuery)) {
            return SuggestionMatch(priority = 3, reason = "label_contains", field = "label")
        }
        val notesLower = notes?.lowercase(Locale.ROOT)
        if (notesLower?.contains(normalizedQuery) == true) {
            return SuggestionMatch(priority = 4, reason = "notes_contains", field = "notes")
        }
        if (request.tokenizedQuery.isNotEmpty()) {
            val haystack = buildList {
                add(labelLower)
                notesLower?.let { add(it) }
            }
            val matchesTokens = request.tokenizedQuery.all { token ->
                haystack.any { entry -> entry.contains(token) }
            }
            if (matchesTokens) {
                return SuggestionMatch(priority = 5, reason = "token_match", field = "label")
            }
        }
        return null
    }

    private fun DiagnosticsPreviewRuntime.Snapshot.toJson(): JSONObject = JSONObject().apply {
        put("generatedAtMillis", generatedAtMillis)
        put("protocol", protocol.name.lowercase(Locale.ROOT))
        put("samples", JSONArray().apply {
            samples.forEach { add(it.toJson()) }
        })
    }

    private fun ObdSample.toJson(): JSONObject = JSONObject().apply {
        put("mode", pid.mode.code)
        put("pid", pid.pid)
        put("pidKey", pid.key)
        put("description", pid.description)
        pid.unit?.let { put("expectedUnit", it) }
        unit?.let { put("unit", it) }
        if (value.isFinite()) {
            put("value", value)
        } else {
            put("value", JSONObject.NULL)
        }
        put("valid", value.isFinite())
        put("rawResponse", rawResponse)
        put("timestampMillis", timestampMillis)
        pid.thresholds?.takeUnless { it.isEmpty }?.let { thresholds ->
            put("thresholds", thresholds.toJson())
        }
    }

    private fun ObdPidSample.toJson(): JSONObject {
        val standardPid = StandardPids.lookup(definition.mode, definition.pid)
        val pidKey = standardPid?.key ?: canonicalPidKey(definition.mode, definition.pid)
        val thresholds = standardPid?.thresholds?.takeUnless { it.isEmpty }
        val expectedUnit = standardPid?.unit ?: definition.unit
        val description = standardPid?.description ?: definition.label
        val valueIsFinite = value?.isFinite() == true
        return JSONObject().apply {
            put("mode", definition.mode)
            put("pid", definition.pid)
            put("pidKey", pidKey)
            put("description", description)
            expectedUnit?.let { put("expectedUnit", it) }
            unit?.let { put("unit", it) }
            if (valueIsFinite) {
                put("value", value)
            } else {
                put("value", JSONObject.NULL)
            }
            put("valid", valueIsFinite)
            put("rawResponse", rawHex)
            put("timestampMillis", timestampMillis)
            thresholds?.let { put("thresholds", it.toJson()) }
        }
    }

    private fun PidThresholds.toJson(): JSONObject = JSONObject().apply {
        warningLow?.let { put("warningLow", it) }
        criticalLow?.let { put("criticalLow", it) }
        warningHigh?.let { put("warningHigh", it) }
        criticalHigh?.let { put("criticalHigh", it) }
    }

    private fun canonicalPidKey(mode: String, pid: String): String {
        fun normalize(token: String): String {
            val trimmed = token.trim()
            val withoutPrefix = if (trimmed.startsWith("0x", ignoreCase = true)) {
                trimmed.substring(2)
            } else {
                trimmed
            }
            return withoutPrefix.uppercase(Locale.ROOT).padStart(2, '0')
        }
        return "${normalize(mode)}:${normalize(pid)}"
    }

    private fun JSONObject?.toVehicle(): DiagnosticsReportVehicle? {
        if (this == null) return null
        val make = optString("make").trim()
        val model = optString("model").takeIf { it.isNotBlank() }?.trim()
        val year = optNumber("year")?.toInt()
        val vin = optString("vin").takeIf { it.isNotBlank() }?.trim()
        if (make.isEmpty() && model == null && year == null && vin == null) {
            return null
        }
        return DiagnosticsReportVehicle(make = make, model = model, year = year, vin = vin)
    }

    private fun JSONObject?.toCustomer(): DiagnosticsReportCustomer? {
        if (this == null) return null
        val phone = optString("phone").takeIf { it.isNotBlank() }?.trim()
        val email = optString("email").takeIf { it.isNotBlank() }?.trim()
        if (phone == null && email == null) {
            return null
        }
        return DiagnosticsReportCustomer(phone = phone, email = email)
    }

    private fun JSONObject?.toDiagnosticsRequest(): ObdDiagnosticsRequest {
        if (this == null) {
            return ObdDiagnosticsRequest()
        }
        val readTroubleCodes = optBoolean("readTroubleCodes", true)
        val clearTroubleCodes = optBoolean("clearTroubleCodes", false)
        val responseTimeoutMs = optNumber("responseTimeoutMs")?.toLong() ?: 1_500L
        val vehicleManufacturer = optString("vehicleManufacturer").takeIf { it.isNotBlank() }
        val pidRequests = optJSONArray("pidRequests")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val mode = item.optString("mode").takeIf { it.isNotBlank() } ?: continue
                    val pid = item.optString("pid").takeIf { it.isNotBlank() } ?: continue
                    val timeout = item.optNumber("timeoutMs")?.toLong()
                    add(ObdPidRequest(mode = mode, pid = pid, timeoutMillis = timeout))
                }
            }
        } ?: emptyList()
        return ObdDiagnosticsRequest(
            pidRequests = pidRequests,
            readTroubleCodes = readTroubleCodes,
            clearTroubleCodes = clearTroubleCodes,
            responseTimeoutMillis = responseTimeoutMs,
            vehicleManufacturer = vehicleManufacturer,
        )
    }

    private fun JSONObject?.toConnectionOptions(): DiagnosticsConnectionOptions {
        if (this == null) return DiagnosticsConnectionOptions()
        val forceReconnect = optBoolean("forceReconnect", false)
        val adapter = optJSONObject("adapter").toConnectedAdapter()
        return DiagnosticsConnectionOptions(forceReconnect = forceReconnect, adapter = adapter)
    }

    private fun JSONObject?.toConnectedAdapter(): ConnectedAdapter? {
        if (this == null) return null
        val address = optString("address").takeIf { it.isNotBlank() } ?: return null
        val name = optString("name").takeIf { it.isNotBlank() }
        val rssi = optNumber("rssi")?.toInt()
        val protocol = optString("protocol").takeIf { it.isNotBlank() }
        val device = BleDevice(address = address, name = name, rssi = rssi)
        return ConnectedAdapter(device = device, protocol = protocol)
    }

    private fun DiagnosticsReportWorkflow.Result.toJson(): JSONObject = JSONObject().apply {
        put("sessionId", sessionId)
        put("generatedAtMillis", reportInput.generatedAtMillis)
        put("report", JSONObject().apply {
            put("html", report.html)
            put("pdfBase64", Base64.encodeToString(report.pdfBytes, Base64.NO_WRAP))
            put("pdfBytesLength", report.pdfBytes.size)
        })
        reportInput.vehicle?.let { put("vehicle", it.toJson()) }
        reportInput.customer?.let { put("customer", it.toJson()) }
        put("snapshot", reportInput.snapshot.toJson(evaluation?.trends))
        put("recommendations", JSONArray().apply {
            reportInput.recommendations.forEach { add(it.toJson()) }
        })
        put("diagnostics", diagnosticsRun.toJson())
    }

    private fun DiagnosticsReportVehicle.toJson(): JSONObject = JSONObject().apply {
        put("make", make)
        model?.let { put("model", it) }
        year?.let { put("year", it) }
        vin?.let { put("vin", it) }
    }

    private fun DiagnosticsReportCustomer.toJson(): JSONObject = JSONObject().apply {
        phone?.let { put("phone", it) }
        email?.let { put("email", it) }
    }

    private fun DiagnosticsProfileSnapshot.toJson(
        trends: Map<String, DiagnosticsMetricTrend>? = null
    ): JSONObject = JSONObject().apply {
        put("timestampMillis", timestampMillis)
        put("metrics", JSONArray().apply {
            metrics.forEach { insight ->
                add(insight.toJson(trends?.get(insight.definition.id)))
            }
        })
        put("summary", summarizeMetrics(metrics))
    }

    private fun summarizeMetrics(metrics: List<DiagnosticsMetricInsight>): JSONObject {
        val summary = DiagnosticsMetricSummary.fromInsights(metrics)
        return JSONObject().apply {
            put("total", summary.totalCount)
            put("ok", summary.normalCount)
            put("warning", summary.warningCount)
            put("critical", summary.criticalCount)
            put("noData", summary.unknownCount)
        }
    }

    private fun DiagnosticsMetricInsight.toJson(
        trend: DiagnosticsMetricTrend?
    ): JSONObject = JSONObject().apply {
        put("id", definition.id)
        put("pidKey", definition.normalizedKey)
        put("label", definition.label)
        put("status", status.name.lowercase(Locale.ROOT))
        value?.let { put("value", it) }
        val unitValue = unit ?: definition.unit
        unitValue?.let { put("unit", it) }
        put("category", definition.category.name.lowercase(Locale.ROOT))
        put("advice", advice)
        sourceTimestampMillis?.let { put("sourceTimestampMillis", it) }
        definition.thresholds.criticalHigh?.let { put("criticalHigh", it) }
        definition.thresholds.criticalLow?.let { put("criticalLow", it) }
        definition.thresholds.warningHigh?.let { put("warningHigh", it) }
        definition.thresholds.warningLow?.let { put("warningLow", it) }
        trend?.takeUnless { it.points.isEmpty() }?.let {
            put("trend", it.toJson())
        }
    }

    private fun DiagnosticsMetricTrend.toJson(): JSONObject = JSONObject().apply {
        put("metricId", metricId)
        put("points", JSONArray().apply {
            points.forEach { add(it.toJson()) }
        })
    }

    private fun DiagnosticsMetricTrendPoint.toJson(): JSONObject = JSONObject().apply {
        put("timestampMillis", timestampMillis)
        put("value", value)
    }

    private fun DiagnosticsRecommendation.toJson(): JSONObject = JSONObject().apply {
        put("metricId", metricId)
        put("pidKey", pidKey)
        put("title", title)
        put("message", message)
        put("severity", severity.name.lowercase(Locale.ROOT))
        put("priority", priority.name.lowercase(Locale.ROOT))
        put("status", status.name.lowercase(Locale.ROOT))
        threshold?.let { put("threshold", it.toJson()) }
    }

    private fun DiagnosticsRecommendationThreshold.toJson(): JSONObject = JSONObject().apply {
        put("type", type.name.lowercase(Locale.ROOT))
        put("value", value)
        unit?.let { put("unit", it) }
    }

    private fun PidDictionaryRevision.toJson(): JSONObject = JSONObject().apply {
        put("source", source)
        versionLabel?.let { put("versionLabel", it) }
        put("refreshedAtMillis", refreshedAtMillis)
        put("entryCount", entryCount)
    }

    private fun DtcDictionaryRevision.toJson(): JSONObject = JSONObject().apply {
        put("source", source)
        versionLabel?.let { put("versionLabel", it) }
        put("refreshedAtMillis", refreshedAtMillis)
        put("entryCount", entryCount)
    }

    private fun PassThruDiagnosticsCoordinator.DiagnosticsRun.toJson(): JSONObject {
        val diagnosticsJson = diagnostics.toJson()
        return JSONObject().apply {
            put("sessionState", sessionState.toJson())
            val keys = diagnosticsJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, diagnosticsJson.get(key))
            }
        }
    }

    private fun ObdSessionState.toJson(): JSONObject {
        val state = this
        return JSONObject().apply {
            when (state) {
                ObdSessionState.Idle -> put("state", "idle")
                is ObdSessionState.Scanning -> {
                    put("state", "scanning")
                    put("attempts", state.attempts)
                }
                is ObdSessionState.Connecting -> {
                    put("state", "connecting")
                    put("device", state.device.toJson())
                }
                is ObdSessionState.Ready -> {
                    put("state", "ready")
                    put("device", state.device.toJson())
                }
                is ObdSessionState.Diagnostics -> {
                    put("state", "diagnostics")
                    put("device", state.device.toJson())
                }
                is ObdSessionState.Failed -> {
                    put("state", "failed")
                    put("message", state.reason.message ?: state.reason.javaClass.simpleName)
                }
                ObdSessionState.Completed -> put("state", "completed")
                is ObdSessionState.PreconditionsMissing -> {
                    put("state", "preconditions_missing")
                    put("prerequisites", state.result.toJson())
                }
            }
        }
    }

    private fun BluetoothPrerequisiteResult.toJson(): JSONObject = JSONObject().apply {
        put("status", status::class.java.simpleName)
        put("action", action::class.java.simpleName)
        put("timestampMillis", timestampMillis)
    }

    private fun BleDevice.toJson(): JSONObject = JSONObject().apply {
        put("address", address)
        name?.let { put("name", it) }
        rssi?.let { put("rssi", it) }
    }

    private fun ObdDiagnosticsResult.toJson(): JSONObject = JSONObject().apply {
        put("durationMs", durationMillis)
        clearPerformed?.let { put("clearPerformed", it) }
        put("pidSampleCount", pidSamples.size)
        put("pidSamples", JSONArray().apply {
            pidSamples.forEach { add(it.toJson()) }
        })
        put("troubleCodes", JSONArray().apply {
            troubleCodes?.entries?.forEach { add(it.toJson()) }
        })
        put("failures", JSONArray().apply {
            failures.forEach { add(it.toJson()) }
        })
    }

    private fun ObdDtcEntry.toJson(): JSONObject = JSONObject().apply {
        put("code", code)
        definition?.let { def ->
            put("label", def.label)
            put("system", def.system.value)
            def.notes?.let { put("notes", it) }
        }
    }

    private fun DiagnosticsFailure.toJson(): JSONObject = JSONObject().apply {
        put("stage", stage.name.lowercase(Locale.ROOT))
        put("detail", detail)
    }

    private fun JSONArray.add(obj: JSONObject) {
        put(length(), obj)
    }

    private fun JSONArray.countFoundEntries(): Int {
        var count = 0
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            if (item.optBoolean("found", false)) {
                count += 1
            }
        }
        return count
    }

    private fun JSONObject?.optArray(vararg keys: String): JSONArray? {
        if (this == null) return null
        for (key in keys) {
            val array = optJSONArray(key)
            if (array != null) {
                return array
            }
        }
        return null
    }

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    private fun JSONObject.optNumber(key: String): Number? {
        if (!has(key)) return null
        val value = opt(key)
        return when (value) {
            is Number -> value
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? = when (val value = optNumber(key)) {
        null -> null
        else -> value.toInt()
    }

    private fun String.withoutHexPrefix(): String {
        return if (startsWith("0x", ignoreCase = true)) {
            substring(2)
        } else {
            this
        }
    }

    private fun Char.isHexDigit(): Boolean {
        return (this in '0'..'9') || (this in 'a'..'f') || (this in 'A'..'F')
    }

    private data class BridgeMessage(
        val requestId: String,
        val action: String,
        val payload: JSONObject?,
    )

    private data class DictionaryLookupRequest(
        val pidSelectors: List<DictionaryPidSelector>,
        val dtcCodes: List<String>,
        val manufacturer: String?,
        val includeManufacturers: Boolean,
    )

    private data class DictionarySuggestRequest(
        val query: String,
        val normalizedQuery: String,
        val compactQuery: String,
        val hexQuery: String?,
        val tokenizedQuery: List<String>,
        val types: Set<DictionarySuggestType>,
        val limit: Int,
        val manufacturer: String?,
        val pidKeyHint: DictionaryPidSelector?,
    )

    private enum class DictionarySuggestType(val value: String) {
        PID("pid"),
        DTC("dtc");

        companion object {
            fun fromToken(token: String): DictionarySuggestType? {
                val normalized = token.trim().lowercase(Locale.ROOT)
                return values().firstOrNull { it.value == normalized }
            }
        }
    }

    private data class DictionaryPidSelector(
        val mode: String,
        val pid: String,
    )

    private data class DictionarySuggestionCandidate(
        val identifier: String,
        val priority: Int,
        val sourcePriority: Int,
        val tieBreaker: String,
        val json: JSONObject,
    ) {
        fun isBetterThan(other: DictionarySuggestionCandidate): Boolean {
            if (priority != other.priority) return priority < other.priority
            if (sourcePriority != other.sourcePriority) return sourcePriority < other.sourcePriority
            return tieBreaker < other.tieBreaker
        }
    }

    private data class SuggestionMatch(
        val priority: Int,
        val reason: String,
        val field: String?,
    )

    fun interface DiagnosticsReportRunner {
        suspend fun run(
            request: ObdDiagnosticsRequest,
            metadata: DiagnosticsReportWorkflow.Metadata,
            options: DiagnosticsConnectionOptions,
        ): DiagnosticsReportWorkflow.Result
    }

    fun interface DiagnosticsPreviewRunner {
        suspend fun capture(request: DiagnosticsPreviewController.Request): DiagnosticsPreviewRuntime.Snapshot
    }

    internal interface EventDispatcher {
        fun dispatch(detail: JSONObject)
    }

    private class WebViewEventDispatcher(
        private val webView: WebView,
    ) : EventDispatcher {
        override fun dispatch(detail: JSONObject) {
            val script = "window.dispatchEvent(new CustomEvent(\"$EVENT_NAME\", { detail: ${detail.toString()} }));"
            webView.post {
                try {
                    webView.evaluateJavascript(script, null)
                } catch (_: Throwable) {
                    // ignore WebView disposal errors
                }
            }
        }
    }

    companion object {
        const val INTERFACE_NAME: String = "KioskDiagnostics"
        private const val EVENT_NAME: String = "kiosk-diagnostics"
        private const val RESPONSE_TYPE: String = "diagnostics_response"
        private const val ACTION_RUN_REPORT = "run_report"
        private const val ACTION_GET_LAST_REPORT = "get_last_report"
        private const val ACTION_CANCEL_REPORT = "cancel_report"
        private const val ACTION_PREVIEW_SNAPSHOT = "preview_snapshot"
        private const val ACTION_DICTIONARY_LOOKUP = "dictionary_lookup"
        private const val ACTION_DICTIONARY_SUGGEST = "dictionary_suggest"
        private val QUERY_TOKEN_REGEX = Regex("[^a-z0-9]+")
        private const val TAG = "DiagnosticsJsBridge"
    }
}
