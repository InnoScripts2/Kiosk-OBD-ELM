package com.selfservice.platform.data.diagnostics

/**
 * Представляет диагностический отчёт, сохранённый локально.
 */
data class DiagnosticsReportRecord(
    val sessionId: String,
    val generatedAtMillis: Long,
    val html: String,
    val pdfBytes: ByteArray,
    val metadata: Map<String, Any?> = emptyMap()
)

data class DiagnosticsReportSummary(
    val totalCount: Int,
    val pendingCount: Int,
    val oldestPendingAtMillis: Long?
) {
    companion object {
        fun empty(): DiagnosticsReportSummary = DiagnosticsReportSummary(0, 0, null)
    }
}

interface DiagnosticsReportStore {
    suspend fun record(record: DiagnosticsReportRecord)

    suspend fun pending(): List<DiagnosticsReportRecord>

    suspend fun markExportedUpTo(timestampMillis: Long)

    suspend fun deleteOlderThan(thresholdMillis: Long)

    suspend fun summary(): DiagnosticsReportSummary
}