package com.selfservice.platform.data.diagnostics

import com.selfservice.platform.data.diagnostics.room.DiagnosticsReportAggregate
import com.selfservice.platform.data.diagnostics.room.DiagnosticsReportDao
import com.selfservice.platform.data.diagnostics.room.DiagnosticsReportEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsReportLocalStoreTest {

    private val dispatcher = StandardTestDispatcher()

    @Test
    fun recordPersistsMetadataAndSession() = runTest(dispatcher) {
        val dao = RecordingDiagnosticsReportDao()
        val store = DiagnosticsReportLocalStore(dao)
        val record = DiagnosticsReportRecord(
            sessionId = "session-42",
            generatedAtMillis = 123L,
            html = "<html></html>",
            pdfBytes = byteArrayOf(0x01, 0x02),
            metadata = mapOf(
                "vehicle" to mapOf("make" to "Toyota", "vin" to "VIN123"),
                "customer" to mapOf("email" to "user@example.com"),
                "summary" to mapOf("metrics_total" to 5)
            )
        )

        store.record(record)

        val inserted = dao.insertedEntities.single()
        assertEquals("session-42", inserted.sessionId)
        assertEquals(123L, inserted.generatedAtMillis)
        assertEquals("<html></html>", inserted.reportHtml)
        assertTrue(inserted.reportPdf.contentEquals(byteArrayOf(0x01, 0x02)))
        val decodedMetadata = MetadataJsonCodec.decode(inserted.metadataJson)
        @Suppress("UNCHECKED_CAST")
        val vehicle = decodedMetadata["vehicle"] as Map<String, Any?>
        assertEquals("Toyota", vehicle["make"])
        assertEquals("VIN123", vehicle["vin"])
    }

    @Test
    fun recordMarksRowsOlderThanLastExported() = runTest(dispatcher) {
        val dao = RecordingDiagnosticsReportDao()
        val store = DiagnosticsReportLocalStore(dao) { 777L }

        store.markExportedUpTo(500L)
        dao.markedThresholds.clear()
        dao.markedExportedAt.clear()

        val record = DiagnosticsReportRecord(
            sessionId = "legacy",
            generatedAtMillis = 400L,
            html = "<html></html>",
            pdfBytes = byteArrayOf(0x05),
            metadata = emptyMap()
        )

        store.record(record)

        assertEquals(listOf(500L), dao.markedThresholds)
        assertEquals(listOf(777L), dao.markedExportedAt)
    }

    @Test
    fun pendingDecodesStoredEntities() = runTest(dispatcher) {
        val dao = RecordingDiagnosticsReportDao().apply {
            pendingEntities = listOf(
                DiagnosticsReportEntity(
                    id = 1L,
                    sessionId = "session-pending",
                    generatedAtMillis = 900L,
                    reportHtml = "<html>pending</html>",
                    reportPdf = byteArrayOf(0x10, 0x20),
                    metadataJson = MetadataJsonCodec.encode(mapOf("summary" to mapOf("metrics_total" to 3)))
                )
            )
        }
        val store = DiagnosticsReportLocalStore(dao)

        val pending = store.pending()

        assertEquals(1, pending.size)
        val record = pending.single()
        assertEquals("session-pending", record.sessionId)
        assertEquals(900L, record.generatedAtMillis)
        assertEquals("<html>pending</html>", record.html)
        @Suppress("UNCHECKED_CAST")
        val summary = record.metadata["summary"] as Map<String, Any?>
        val total = summary["metrics_total"] as Number
        assertEquals(3L, total.toLong())
    }

    @Test
    fun pendingReturnsEmptyWhenNoEntities() = runTest(dispatcher) {
        val dao = RecordingDiagnosticsReportDao()
        val store = DiagnosticsReportLocalStore(dao)

        assertTrue(store.pending().isEmpty())
    }

    @Test
    fun markExportedDelegatesToDao() = runTest(dispatcher) {
        val dao = RecordingDiagnosticsReportDao()
        val store = DiagnosticsReportLocalStore(dao) { 1000L }

        store.markExportedUpTo(800L)

        assertEquals(listOf(800L), dao.markedThresholds)
        assertEquals(listOf(1000L), dao.markedExportedAt)
    }

    @Test
    fun deleteOlderThanDelegatesToDao() = runTest(dispatcher) {
        val dao = RecordingDiagnosticsReportDao()
        val store = DiagnosticsReportLocalStore(dao)

        store.deleteOlderThan(200L)

        assertEquals(listOf(200L), dao.deletedThresholds)
    }

    @Test
    fun summaryReturnsEmptyWhenNoAggregate() = runTest(dispatcher) {
        val dao = RecordingDiagnosticsReportDao()
        val store = DiagnosticsReportLocalStore(dao)

        val summary = store.summary()

        assertEquals(0, summary.totalCount)
        assertEquals(0, summary.pendingCount)
        assertNull(summary.oldestPendingAtMillis)
    }

    @Test
    fun summaryReflectsAggregateValues() = runTest(dispatcher) {
        val dao = RecordingDiagnosticsReportDao().apply {
            aggregateResult = DiagnosticsReportAggregate(
                totalCount = 8,
                pendingCount = 2,
                oldestPendingAt = 64L
            )
        }
        val store = DiagnosticsReportLocalStore(dao)

        val summary = store.summary()

        assertEquals(8, summary.totalCount)
        assertEquals(2, summary.pendingCount)
        assertEquals(64L, summary.oldestPendingAtMillis)
    }

    private class RecordingDiagnosticsReportDao : DiagnosticsReportDao {
        val insertedEntities = mutableListOf<DiagnosticsReportEntity>()
        val markedThresholds = mutableListOf<Long>()
        val markedExportedAt = mutableListOf<Long>()
        val deletedThresholds = mutableListOf<Long>()
        var aggregateResult: DiagnosticsReportAggregate? = null
        var pendingEntities: List<DiagnosticsReportEntity> = emptyList()

        override suspend fun insert(entity: DiagnosticsReportEntity): Long {
            insertedEntities += entity
            return entity.id
        }

        override suspend fun pending(): List<DiagnosticsReportEntity> = pendingEntities

        override suspend fun markExportedUpTo(timestampMillis: Long, exportedAtMillis: Long) {
            markedThresholds += timestampMillis
            markedExportedAt += exportedAtMillis
        }

        override suspend fun deleteOlderThan(thresholdMillis: Long) {
            deletedThresholds += thresholdMillis
        }

        override suspend fun aggregate(): DiagnosticsReportAggregate? = aggregateResult
    }
}
