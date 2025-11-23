package com.selfservice.platform.data.diagnostics

import com.selfservice.platform.data.diagnostics.room.DiagnosticsTelemetryAggregate
import com.selfservice.platform.data.diagnostics.room.DiagnosticsTelemetryDao
import com.selfservice.platform.data.diagnostics.room.DiagnosticsTelemetryEntity
import java.util.concurrent.atomic.AtomicLong

class DiagnosticsTelemetryLocalStore(
    private val dao: DiagnosticsTelemetryDao,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : DiagnosticsTelemetryStore {

    private val lastExportedTimestamp = AtomicLong(Long.MIN_VALUE)

    override suspend fun record(record: DiagnosticsTelemetryRecord) {
        val metadataJson = MetadataJsonCodec.encode(record.metadata)
        dao.insert(
            DiagnosticsTelemetryEntity(
                timestampMillis = record.timestampMillis,
                eventType = record.eventType,
                sessionId = record.sessionId,
                metadataJson = metadataJson
            )
        )
        val exportedThreshold = lastExportedThreshold()
        if (exportedThreshold != null && record.timestampMillis <= exportedThreshold) {
            dao.markExportedUpTo(exportedThreshold, clock())
        }
    }

    override suspend fun pending(): List<DiagnosticsTelemetryRecord> {
        val entities = dao.pending()
        if (entities.isEmpty()) {
            return emptyList()
        }
        return entities.map { entity ->
            DiagnosticsTelemetryRecord(
                timestampMillis = entity.timestampMillis,
                eventType = entity.eventType,
                sessionId = entity.sessionId,
                metadata = decodeMetadata(entity.metadataJson)
            )
        }
    }

    override suspend fun markExportedUpTo(timestampMillis: Long) {
        lastExportedTimestamp.set(timestampMillis)
        dao.markExportedUpTo(timestampMillis, clock())
    }

    override suspend fun deleteOlderThan(thresholdMillis: Long) {
        dao.deleteOlderThan(thresholdMillis)
    }

    override suspend fun summary(): DiagnosticsTelemetrySummary {
        val aggregate: DiagnosticsTelemetryAggregate? = dao.aggregate()
        return if (aggregate == null) {
            DiagnosticsTelemetrySummary.empty()
        } else {
            DiagnosticsTelemetrySummary(
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
        fun decodeMetadata(json: String): Map<String, Any?> = MetadataJsonCodec.decode(json)
    }
}
