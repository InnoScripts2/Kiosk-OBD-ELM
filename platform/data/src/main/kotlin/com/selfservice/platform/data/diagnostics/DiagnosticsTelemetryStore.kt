package com.selfservice.platform.data.diagnostics

/** Summary information about locally persisted diagnostics telemetry events. */
data class DiagnosticsTelemetrySummary(
    val totalCount: Int,
    val pendingCount: Int,
    val oldestPendingAtMillis: Long?
) {
    companion object {
        fun empty(): DiagnosticsTelemetrySummary = DiagnosticsTelemetrySummary(0, 0, null)
    }
}

interface DiagnosticsTelemetryStore {
    suspend fun record(record: DiagnosticsTelemetryRecord)

    suspend fun pending(): List<DiagnosticsTelemetryRecord>

    suspend fun markExportedUpTo(timestampMillis: Long)

    suspend fun deleteOlderThan(thresholdMillis: Long)

    suspend fun summary(): DiagnosticsTelemetrySummary
}
