package com.selfservice.platform.data.diagnostics

import com.selfservice.platform.data.diagnostics.room.DiagnosticsReportAggregate
import com.selfservice.platform.data.diagnostics.room.DiagnosticsReportDao
import com.selfservice.platform.data.diagnostics.room.DiagnosticsReportEntity
import java.util.concurrent.atomic.AtomicLong

class DiagnosticsReportLocalStore(
    private val dao: DiagnosticsReportDao,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : DiagnosticsReportStore {

    private val lastExportedTimestamp = AtomicLong(Long.MIN_VALUE)

    override suspend fun record(record: DiagnosticsReportRecord) {
        val metadataJson = MetadataJsonCodec.encode(record.metadata)
        dao.insert(
            DiagnosticsReportEntity(
                sessionId = record.sessionId,
                generatedAtMillis = record.generatedAtMillis,
                reportHtml = record.html,
                reportPdf = record.pdfBytes,
                metadataJson = metadataJson
            )
        )
        val exportedThreshold = lastExportedThreshold()
        if (exportedThreshold != null && record.generatedAtMillis <= exportedThreshold) {
            dao.markExportedUpTo(exportedThreshold, clock())
        }
    }

    override suspend fun pending(): List<DiagnosticsReportRecord> {
        val entities = dao.pending()
        if (entities.isEmpty()) {
            return emptyList()
        }
        return entities.map { entity ->
            DiagnosticsReportRecord(
                sessionId = entity.sessionId,
                generatedAtMillis = entity.generatedAtMillis,
                html = entity.reportHtml,
                pdfBytes = entity.reportPdf,
                metadata = MetadataJsonCodec.decode(entity.metadataJson)
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

    override suspend fun summary(): DiagnosticsReportSummary {
        val aggregate: DiagnosticsReportAggregate? = dao.aggregate()
        return if (aggregate == null) {
            DiagnosticsReportSummary.empty()
        } else {
            DiagnosticsReportSummary(
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
}