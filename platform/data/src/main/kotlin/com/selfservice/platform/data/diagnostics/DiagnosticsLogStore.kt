package com.selfservice.platform.data.diagnostics

import com.selfservice.core.logging.DiagnosticsLogEntry

/** Summary information about locally persisted diagnostics logs. */
data class DiagnosticsLogSummary(
    val totalCount: Int,
    val pendingCount: Int,
    val oldestPendingAtMillis: Long?
) {
    companion object {
        fun empty(): DiagnosticsLogSummary = DiagnosticsLogSummary(0, 0, null)
    }
}

interface DiagnosticsLogStore {
    suspend fun record(entry: DiagnosticsLogEntry)

    suspend fun markExportedUpTo(timestampMillis: Long)

    suspend fun deleteOlderThan(thresholdMillis: Long)

    suspend fun summary(): DiagnosticsLogSummary
}
