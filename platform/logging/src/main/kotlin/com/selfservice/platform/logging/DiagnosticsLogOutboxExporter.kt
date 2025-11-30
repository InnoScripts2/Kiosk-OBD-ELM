package com.selfservice.platform.logging

import com.selfservice.core.logging.DiagnosticsLogEntry
import com.selfservice.core.logging.DiagnosticsLogFileSink
import kotlin.math.max

/**
 * Moves diagnostics log entries from the local file sink into the outbox queue provided by the
 * host application so that uploads can resume when connectivity is restored.
 */
class DiagnosticsLogOutboxExporter(
    private val sink: DiagnosticsLogFileSink,
    private val writer: DiagnosticsLogOutboxWriter,
    private val additionalFieldsProvider: () -> Map<String, Any?> = { emptyMap() }
) {

    private val lock = Any()

    fun exportPending(): ExportResult {
        synchronized(lock) {
            val entries = sink.snapshot().sortedBy { it.timestampMillis }
            if (entries.isEmpty()) {
                return ExportResult(exported = 0, lastTimestampMillis = null)
            }
            val additionalFields = additionalFieldsProvider()
            var exported = 0
            var lastTimestamp = Long.MIN_VALUE
            entries.forEach { entry ->
                val payload = DiagnosticsLogSupabaseSchema.toRow(entry, additionalFields)
                try {
                    writer.enqueue(
                        table = DiagnosticsLogSupabaseSchema.TABLE_NAME,
                        payload = payload,
                        operation = DiagnosticsLogOutboxWriter.Operation.UPSERT
                    )
                    exported += 1
                    lastTimestamp = max(lastTimestamp, entry.timestampMillis)
                } catch (error: Throwable) {
                    return ExportResult(
                        exported = exported,
                        lastTimestampMillis = lastTimestamp.takeIf { exported > 0 },
                        failure = error
                    )
                }
            }
            if (lastTimestamp != Long.MIN_VALUE) {
                sink.clearOlderThan(lastTimestamp + 1)
            }
            return ExportResult(exported = exported, lastTimestampMillis = lastTimestamp)
        }
    }

    data class ExportResult(
        val exported: Int,
        val lastTimestampMillis: Long?,
        val failure: Throwable? = null
    ) {
        val isSuccess: Boolean
            get() = failure == null
    }
}
