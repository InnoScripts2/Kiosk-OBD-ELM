package com.selfservice.platform.data.thickness

/**
 * Представляет отчёт толщиномера, сохранённый локально.
 */
data class ThicknessReportRecord(
    val sessionId: String,
    val generatedAtMillis: Long,
    val html: String,
    val pdfBytes: ByteArray,
    val metadata: Map<String, Any?> = emptyMap()
)

data class ThicknessReportSummary(
    val totalCount: Int,
    val pendingCount: Int,
    val oldestPendingAtMillis: Long?
) {
    companion object {
        fun empty(): ThicknessReportSummary = ThicknessReportSummary(0, 0, null)
    }
}

interface ThicknessReportStore {
    suspend fun record(record: ThicknessReportRecord)

    suspend fun pending(): List<ThicknessReportRecord>

    suspend fun markExportedUpTo(timestampMillis: Long)

    suspend fun deleteOlderThan(thresholdMillis: Long)

    suspend fun summary(): ThicknessReportSummary
}
