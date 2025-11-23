package com.selfservice.platform.data.diagnostics

import com.selfservice.platform.data.diagnostics.MetadataJsonCodec
import com.selfservice.platform.data.diagnostics.room.DiagnosticsTelemetryAggregate
import com.selfservice.platform.data.diagnostics.room.DiagnosticsTelemetryDao
import com.selfservice.platform.data.diagnostics.room.DiagnosticsTelemetryEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsTelemetryLocalStoreTest {

    private val dispatcher = StandardTestDispatcher()

    @Test
    fun recordPersistsMetadataAndSessionId() = runTest(dispatcher) {
        val dao = RecordingDiagnosticsTelemetryDao()
        val store = DiagnosticsTelemetryLocalStore(dao)
        val record = DiagnosticsTelemetryRecord(
            timestampMillis = 123L,
            eventType = "SessionStarted",
            sessionId = "session-1",
            metadata = mapOf("attempt" to 1, "adapter" to mapOf("address" to "AA:BB"))
        )

        store.record(record)

        val inserted = dao.inserted.single()
        assertEquals("SessionStarted", inserted.eventType)
        assertEquals("session-1", inserted.sessionId)
        assertEquals(123L, inserted.timestampMillis)
        val metadata = DiagnosticsTelemetryLocalStore.decodeMetadata(inserted.metadataJson)
        assertEquals(1, metadata["attempt"])
        @Suppress("UNCHECKED_CAST")
        val adapter = metadata["adapter"] as Map<String, Any?>
        assertEquals("AA:BB", adapter["address"])
        assertTrue(!inserted.exported)
        assertNull(inserted.exportedAtMillis)
    }

    @Test
    fun markExportedDelegatesToDao() = runTest(dispatcher) {
        val dao = RecordingDiagnosticsTelemetryDao()
        val store = DiagnosticsTelemetryLocalStore(dao) { 250L }

        store.markExportedUpTo(200L)

        assertEquals(listOf(200L), dao.markedThresholds)
        assertEquals(listOf(250L), dao.markedExportedAt)
    }

    @Test
    fun deleteOlderThanDelegatesToDao() = runTest(dispatcher) {
        val dao = RecordingDiagnosticsTelemetryDao()
        val store = DiagnosticsTelemetryLocalStore(dao)

        store.deleteOlderThan(50L)

        assertEquals(listOf(50L), dao.deletedThresholds)
    }

    @Test
    fun pendingDecodesStoredEntities() = runTest(dispatcher) {
        val dao = RecordingDiagnosticsTelemetryDao().apply {
            pendingEntities = listOf(
                DiagnosticsTelemetryEntity(
                    id = 11L,
                    timestampMillis = 400L,
                    eventType = "SessionCompleted",
                    sessionId = "session-42",
                    metadataJson = MetadataJsonCodec.encode(mapOf("durationMillis" to 1234L))
                )
            )
        }
        val store = DiagnosticsTelemetryLocalStore(dao)

        val pending = store.pending()

        assertEquals(1, pending.size)
        val record = pending.single()
        assertEquals(400L, record.timestampMillis)
        assertEquals("SessionCompleted", record.eventType)
        assertEquals("session-42", record.sessionId)
    val duration = record.metadata["durationMillis"] as Number
    assertEquals(1234L, duration.toLong())
    }

    @Test
    fun pendingReturnsEmptyWhenDaoHasNoRows() = runTest(dispatcher) {
        val dao = RecordingDiagnosticsTelemetryDao()
        val store = DiagnosticsTelemetryLocalStore(dao)

        val pending = store.pending()

        assertTrue(pending.isEmpty())
    }

    @Test
    fun summaryReturnsEmptyWhenNoRows() = runTest(dispatcher) {
        val dao = RecordingDiagnosticsTelemetryDao()
        val store = DiagnosticsTelemetryLocalStore(dao)

        val summary = store.summary()

        assertEquals(0, summary.totalCount)
        assertEquals(0, summary.pendingCount)
        assertNull(summary.oldestPendingAtMillis)
    }

    @Test
    fun summaryReflectsAggregate() = runTest(dispatcher) {
        val dao = RecordingDiagnosticsTelemetryDao().apply {
            aggregateResult = DiagnosticsTelemetryAggregate(
                totalCount = 7,
                pendingCount = 2,
                oldestPendingAt = 42L
            )
        }
        val store = DiagnosticsTelemetryLocalStore(dao)

        val summary = store.summary()

        assertEquals(7, summary.totalCount)
        assertEquals(2, summary.pendingCount)
        assertEquals(42L, summary.oldestPendingAtMillis)
    }

    @Test
    fun recordMarksRowsOlderThanLastExported() = runTest(dispatcher) {
        val dao = RecordingDiagnosticsTelemetryDao()
        val store = DiagnosticsTelemetryLocalStore(dao) { 777L }

        store.markExportedUpTo(500L)
        dao.markedThresholds.clear()
        dao.markedExportedAt.clear()

        val record = DiagnosticsTelemetryRecord(
            timestampMillis = 400L,
            eventType = "LegacySession",
            sessionId = "session-legacy",
            metadata = emptyMap()
        )

        store.record(record)

        assertEquals(listOf(500L), dao.markedThresholds)
        assertEquals(listOf(777L), dao.markedExportedAt)
        assertEquals(1, dao.inserted.size)
        val inserted = dao.inserted.single()
        assertEquals("LegacySession", inserted.eventType)
        assertEquals("session-legacy", inserted.sessionId)
    }

    @Test
    fun decodeMetadataGracefullyHandlesInvalidJson() {
        val decoded = DiagnosticsTelemetryLocalStore.decodeMetadata("{invalid}")

        assertTrue(decoded.isEmpty())
    }

    private class RecordingDiagnosticsTelemetryDao : DiagnosticsTelemetryDao {
        val inserted = mutableListOf<DiagnosticsTelemetryEntity>()
        val markedThresholds = mutableListOf<Long>()
        val markedExportedAt = mutableListOf<Long>()
        val deletedThresholds = mutableListOf<Long>()
        var aggregateResult: DiagnosticsTelemetryAggregate? = null
        var pendingEntities: List<DiagnosticsTelemetryEntity> = emptyList()

        override suspend fun insert(entity: DiagnosticsTelemetryEntity): Long {
            inserted += entity
            return entity.id
        }

        override suspend fun pending(): List<DiagnosticsTelemetryEntity> = pendingEntities

        override suspend fun markExportedUpTo(timestampMillis: Long, exportedAtMillis: Long) {
            markedThresholds += timestampMillis
            markedExportedAt += exportedAtMillis
        }

        override suspend fun deleteOlderThan(thresholdMillis: Long) {
            deletedThresholds += thresholdMillis
        }

        override suspend fun aggregate(): DiagnosticsTelemetryAggregate? = aggregateResult
    }
}
