package com.selfservice.kiosk.telemetry

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.selfservice.core.DefaultDispatchersProvider
import com.selfservice.core.DispatchersProvider
import com.selfservice.kiosk.supabase.SupabaseOutboxWriter
import com.selfservice.platform.data.diagnostics.DiagnosticsTelemetryRecord
import com.selfservice.platform.data.diagnostics.DiagnosticsTelemetryStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bridges locally persisted diagnostics telemetry into the Supabase outbox queue.
 * Exports pending records when a Supabase writer is attached or when new events arrive.
 */
class DiagnosticsTelemetryOutboxBridge(
    private val store: DiagnosticsTelemetryStore,
    private val scope: CoroutineScope,
    private val dispatchers: DispatchersProvider = DefaultDispatchersProvider(),
    private val onExported: (() -> Unit)? = null
) {

    private val exportMutex = Mutex()
    private val hasPendingExport = AtomicBoolean(false)
    private var enqueueBlock: ((String, Map<String, Any?>) -> Unit)? = null
    private var additionalFieldsProvider: (() -> Map<String, Any?>)? = null

    fun attachSupabaseOutbox(
        writer: SupabaseOutboxWriter,
        additionalFieldsProvider: () -> Map<String, Any?> = { emptyMap() }
    ) {
        attachSupabaseOutboxInternal(
            enqueue = { table, payload ->
                writer.enqueue(
                    table = table,
                    payload = payload,
                    operation = SupabaseOutboxWriter.OPERATION_UPSERT
                )
            },
            additionalFieldsProvider = additionalFieldsProvider
        )
    }

    @VisibleForTesting
    internal fun attachSupabaseOutbox(
        enqueue: (String, Map<String, Any?>) -> Unit,
        additionalFieldsProvider: () -> Map<String, Any?> = { emptyMap() }
    ) {
        attachSupabaseOutboxInternal(enqueue, additionalFieldsProvider)
    }

    private fun attachSupabaseOutboxInternal(
        enqueue: (String, Map<String, Any?>) -> Unit,
        additionalFieldsProvider: () -> Map<String, Any?>
    ) {
        this.enqueueBlock = enqueue
        this.additionalFieldsProvider = additionalFieldsProvider
        triggerExport()
    }

    fun detachSupabaseOutbox() {
        enqueueBlock = null
        additionalFieldsProvider = null
    }

    /** Signals that new telemetry was written and should be exported. */
    fun notifyRecordInserted() {
        triggerExport()
    }

    fun triggerExport() {
        if (enqueueBlock == null) {
            return
        }
        if (!hasPendingExport.compareAndSet(false, true)) {
            return
        }
        scope.launch(dispatchers.io) {
            try {
                exportPending()
            } finally {
                hasPendingExport.set(false)
            }
        }
    }

    private suspend fun exportPending() {
        val enqueue = enqueueBlock ?: return
        exportMutex.withLock {
            val records = runCatching { store.pending() }
                .getOrElse { error ->
                    Log.w(TAG, "Failed to load pending telemetry records", error)
                    return
                }
            if (records.isEmpty()) {
                return
            }
            val additionalFields = additionalFieldsProvider?.invoke() ?: emptyMap()
            var lastExportedTimestamp: Long? = null
            records.forEach { record ->
                if (!enqueueRecord(enqueue, record, additionalFields)) {
                    return
                }
                val currentTimestamp = record.timestampMillis
                lastExportedTimestamp = when (val previous = lastExportedTimestamp) {
                    null -> currentTimestamp
                    else -> maxOf(previous, currentTimestamp)
                }
            }
            lastExportedTimestamp?.let { threshold ->
                runCatching { store.markExportedUpTo(threshold) }
                    .onFailure { error ->
                        Log.w(TAG, "Failed to mark telemetry exported", error)
                    }
                    .onSuccess {
                        onExported?.invoke()
                    }
            }
        }
    }

    private fun enqueueRecord(
        enqueue: (String, Map<String, Any?>) -> Unit,
        record: DiagnosticsTelemetryRecord,
        additionalFields: Map<String, Any?>
    ): Boolean {
        val payload = DiagnosticsTelemetrySupabaseSchema.toRow(record, additionalFields)
        return runCatching {
            enqueue(DiagnosticsTelemetrySupabaseSchema.TABLE_NAME, payload)
        }.onFailure { error ->
            Log.w(TAG, "Failed to enqueue telemetry for Supabase", error)
        }.isSuccess
    }

    companion object {
        private const val TAG = "DiagTelemetryBridge"
    }
}
