package com.selfservice.platform.data.diagnostics

import com.selfservice.core.logging.DiagnosticsLogEntry
import com.selfservice.platform.data.diagnostics.room.DiagnosticsLogDao
import com.selfservice.platform.data.diagnostics.room.DiagnosticsLogEntity
import java.util.concurrent.atomic.AtomicLong

class DiagnosticsLogLocalStore(
    private val dao: DiagnosticsLogDao,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : DiagnosticsLogStore {

    private val lastExportedTimestamp = AtomicLong(Long.MIN_VALUE)

    override suspend fun record(entry: DiagnosticsLogEntry) {
        val metadataJson = MetadataJsonCodec.encode(entry.metadata)
        dao.insert(
            DiagnosticsLogEntity(
                timestampMillis = entry.timestampMillis,
                category = entry.category,
                message = entry.message,
                metadataJson = metadataJson
            )
        )
        val exportedThreshold = lastExportedThreshold()
        if (exportedThreshold != null && entry.timestampMillis <= exportedThreshold) {
            dao.markExportedUpTo(exportedThreshold, clock())
        }
    }

    override suspend fun markExportedUpTo(timestampMillis: Long) {
        lastExportedTimestamp.set(timestampMillis)
        dao.markExportedUpTo(timestampMillis, clock())
    }

    override suspend fun deleteOlderThan(thresholdMillis: Long) {
        dao.deleteOlderThan(thresholdMillis)
    }

    override suspend fun summary(): DiagnosticsLogSummary {
        val aggregate = dao.aggregate()
        return if (aggregate == null) {
            DiagnosticsLogSummary.empty()
        } else {
            DiagnosticsLogSummary(
                totalCount = aggregate.totalCount,
                pendingCount = aggregate.pendingCount,
                oldestPendingAtMillis = aggregate.oldestPendingAt
            )
        }
    }

    private fun lastExportedThreshold(): Long? {
        val value = lastExportedTimestamp.get()
        return if (value == Long.MIN_VALUE) null else value
    }

    companion object {
        fun decodeMetadata(json: String): Map<String, Any?> {
            return MetadataJsonCodec.decode(json)
        }
    }
}
