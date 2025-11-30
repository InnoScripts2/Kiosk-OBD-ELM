package com.selfservice.kiosk.reports

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.selfservice.core.DefaultDispatchersProvider
import com.selfservice.core.DispatchersProvider
import com.selfservice.kiosk.reports.DiagnosticsReportDeliverySupabaseSchema
import com.selfservice.kiosk.supabase.SupabaseOutboxWriter
import com.selfservice.platform.data.diagnostics.DiagnosticsReportRecord
import com.selfservice.platform.data.diagnostics.DiagnosticsReportStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Экспортирует локально сохранённые диагностические отчёты в Supabase outbox.
 */
open class DiagnosticsReportOutboxBridge(
    private val store: DiagnosticsReportStore,
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

    open fun notifyRecordInserted() {
        triggerExport()
    }

    fun triggerExport() {
        val enqueue = enqueueBlock ?: return
        if (!hasPendingExport.compareAndSet(false, true)) {
            return
        }
        scope.launch(dispatchers.io) {
            try {
                exportPending(enqueue)
            } finally {
                hasPendingExport.set(false)
            }
        }
    }

    private suspend fun exportPending(
        enqueue: (String, Map<String, Any?>) -> Unit
    ) {
        exportMutex.withLock {
            val records = runCatching { store.pending() }
                .getOrElse { error ->
                    Log.w(TAG, "Не удалось получить очередь отчётов", error)
                    return
                }
            if (records.isEmpty()) {
                return
            }
            val additionalFields = additionalFieldsProvider?.invoke() ?: emptyMap()
            var lastExported: Long? = null
            var totalReportsEnqueued = 0
            var totalDeliveriesEnqueued = 0
            records.forEach { record ->
                val reportEnqueued = enqueueReport(enqueue, record, additionalFields)
                if (!reportEnqueued) {
                    return
                }
                totalReportsEnqueued += 1
                val deliveriesEnqueued = enqueueDeliveries(enqueue, record, additionalFields) ?: return
                totalDeliveriesEnqueued += deliveriesEnqueued
                val timestamp = record.generatedAtMillis
                lastExported = when (val previous = lastExported) {
                    null -> timestamp
                    else -> maxOf(previous, timestamp)
                }
            }
            lastExported?.let { threshold ->
                runCatching { store.markExportedUpTo(threshold) }
                    .onFailure { error ->
                        Log.w(TAG, "Не удалось отметить отчёты как экспортированные", error)
                    }
                    .onSuccess {
                        Log.i(
                                TAG,
                                "Экспортировано отчётов в Supabase: reports=$totalReportsEnqueued deliveries=$totalDeliveriesEnqueued exportedUpTo=$threshold"
                        )
                        onExported?.invoke()
                    }
            }
        }
    }

    private fun enqueueReport(
        enqueue: (String, Map<String, Any?>) -> Unit,
        record: DiagnosticsReportRecord,
        additionalFields: Map<String, Any?>
    ): Boolean {
        val payload = DiagnosticsReportSupabaseSchema.toRow(record, additionalFields)
        return runCatching {
            enqueue(DiagnosticsReportSupabaseSchema.TABLE_NAME, payload)
        }.onFailure { error ->
            Log.w(TAG, "Не удалось добавить отчёт в очередь Supabase", error)
        }.isSuccess
    }

    private fun enqueueDeliveries(
        enqueue: (String, Map<String, Any?>) -> Unit,
        record: DiagnosticsReportRecord,
        additionalFields: Map<String, Any?>
    ): Int? {
        val rows = DiagnosticsReportDeliverySupabaseSchema.toRows(record, additionalFields)
        if (rows.isEmpty()) {
            return 0
        }
        var enqueued = 0
        rows.forEach { row ->
            val success = runCatching {
                enqueue(DiagnosticsReportDeliverySupabaseSchema.TABLE_NAME, row)
            }.onFailure { error ->
                Log.w(
                    TAG,
                    "Не удалось добавить запрос на доставку диагностического отчёта в очередь Supabase",
                    error
                )
            }.isSuccess
            if (!success) {
                return null
            }
            enqueued += 1
        }
        return enqueued
    }

    companion object {
        private const val TAG = "DiagReportOutbox"
    }
}
