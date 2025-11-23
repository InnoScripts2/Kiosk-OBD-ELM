package com.selfservice.platform.logging

import com.selfservice.core.DefaultDispatchersProvider
import com.selfservice.core.DispatchersProvider
import com.selfservice.core.logging.DiagnosticsLogEntry
import com.selfservice.core.logging.DiagnosticsLogFileSink
import com.selfservice.core.logging.DiagnosticsLogger
import com.selfservice.platform.data.diagnostics.DiagnosticsLogStore
import com.selfservice.platform.data.diagnostics.DiagnosticsLogSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Centralized manager for diagnostics logs. Provides in-process recording backed by disk and
 * optional bridging into the Supabase outbox queue when credentials become available.
 */
class DiagnosticsLogService(
    private val sink: DiagnosticsLogFileSink,
    private val scope: CoroutineScope,
    private val dispatchers: DispatchersProvider = DefaultDispatchersProvider(),
    private val store: DiagnosticsLogStore? = null,
    private val storeUpdateCallback: (() -> Unit)? = null
) {

    private val logger = DiagnosticsLogger(sink)
    private val exportMutex = Mutex()
    @Volatile
    private var exporter: DiagnosticsLogOutboxExporter? = null

    fun log(category: String, message: String, metadata: Map<String, Any?> = emptyMap()) {
        val entry = logger.record(category = category, message = message, metadata = metadata)
        store?.let { currentStore ->
            scope.launch(dispatchers.io) {
                currentStore.record(entry)
                storeUpdateCallback?.invoke()
            }
        }
        triggerExport()
    }

    fun attachOutbox(
        writer: DiagnosticsLogOutboxWriter,
        additionalFieldsProvider: () -> Map<String, Any?> = { emptyMap() }
    ) {
        exporter = DiagnosticsLogOutboxExporter(sink, writer, additionalFieldsProvider)
        triggerExport()
    }

    fun detachOutbox() {
        exporter = null
    }

    fun pruneOlderThan(thresholdMillis: Long) {
        sink.clearOlderThan(thresholdMillis)
        store?.let { currentStore ->
            scope.launch(dispatchers.io) {
                currentStore.deleteOlderThan(thresholdMillis)
                storeUpdateCallback?.invoke()
            }
        }
    }

    fun entriesSnapshot(): List<DiagnosticsLogEntry> = sink.snapshot()

    suspend fun summary(): DiagnosticsLogSummary? = store?.summary()

    private fun triggerExport() {
        val currentExporter = exporter ?: return
        scope.launch(dispatchers.io) {
            exportMutex.withLock {
                val result = currentExporter.exportPending()
                if (result.isSuccess) {
                    val lastTimestamp = result.lastTimestampMillis
                    if (lastTimestamp != null) {
                        store?.markExportedUpTo(lastTimestamp)
                        storeUpdateCallback?.invoke()
                    }
                }
            }
        }
    }
}
