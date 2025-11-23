package com.selfservice.platform.data.diagnostics

import com.selfservice.core.logging.DiagnosticsLogEntry
import com.selfservice.platform.data.diagnostics.room.DiagnosticsLogAggregate
import com.selfservice.platform.data.diagnostics.room.DiagnosticsLogDao
import com.selfservice.platform.data.diagnostics.room.DiagnosticsLogEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsLogLocalStoreTest {

    private val dispatcher = StandardTestDispatcher()

    @Test
    fun recordPersistsMetadata() = runTest(dispatcher) {
        val dao = RecordingDiagnosticsLogDao()
        val store = DiagnosticsLogLocalStore(dao)
        val entry = DiagnosticsLogEntry(
            category = "session",
            message = "start",
            timestampMillis = 100L,
            metadata = mapOf("step" to 1, "tags" to listOf("a", "b"))
        )

        store.record(entry)

        val inserted = dao.inserted.single()
        assertEquals("session", inserted.category)
        assertEquals("start", inserted.message)
        assertEquals(100L, inserted.timestampMillis)
        val metadata = DiagnosticsLogLocalStore.decodeMetadata(inserted.metadataJson)
        assertEquals(1, metadata["step"])
        assertEquals(listOf("a", "b"), metadata["tags"])
        assertTrue(!inserted.exported)
        assertNull(inserted.exportedAtMillis)
    }

    @Test
    fun markExportedUpToDelegatesToDao() = runTest(dispatcher) {
        val dao = RecordingDiagnosticsLogDao()
        val store = DiagnosticsLogLocalStore(dao) { 200L }

        store.markExportedUpTo(150L)

        assertEquals(listOf(150L), dao.markedThresholds)
        assertEquals(listOf(200L), dao.markedExportedAt)
    }

    @Test
    fun deleteOlderThanDelegatesToDao() = runTest(dispatcher) {
        val dao = RecordingDiagnosticsLogDao()
        val store = DiagnosticsLogLocalStore(dao)

        store.deleteOlderThan(50L)

        assertEquals(listOf(50L), dao.deletedThresholds)
    }

    @Test
    fun summaryReturnsEmptyWhenDaoHasNoRows() = runTest(dispatcher) {
        val dao = RecordingDiagnosticsLogDao()
        val store = DiagnosticsLogLocalStore(dao)

        val summary = store.summary()

        assertEquals(0, summary.totalCount)
        assertEquals(0, summary.pendingCount)
        assertNull(summary.oldestPendingAtMillis)
    }

    @Test
    fun summaryReflectsAggregate() = runTest(dispatcher) {
        val dao = RecordingDiagnosticsLogDao().apply {
            aggregateResult = DiagnosticsLogAggregate(
                totalCount = 10,
                pendingCount = 3,
                oldestPendingAt = 42L
            )
        }
        val store = DiagnosticsLogLocalStore(dao)

        val summary = store.summary()

        assertEquals(10, summary.totalCount)
        assertEquals(3, summary.pendingCount)
        assertEquals(42L, summary.oldestPendingAtMillis)
    }

    private class RecordingDiagnosticsLogDao : DiagnosticsLogDao {
        val inserted = mutableListOf<DiagnosticsLogEntity>()
        val markedThresholds = mutableListOf<Long>()
        val markedExportedAt = mutableListOf<Long>()
        val deletedThresholds = mutableListOf<Long>()
        var aggregateResult: DiagnosticsLogAggregate? = null

        override suspend fun insert(entity: DiagnosticsLogEntity): Long {
            inserted += entity
            return entity.id
        }

        override suspend fun markExportedUpTo(timestampMillis: Long, exportedAtMillis: Long) {
            markedThresholds += timestampMillis
            markedExportedAt += exportedAtMillis
        }

        override suspend fun deleteOlderThan(thresholdMillis: Long) {
            deletedThresholds += thresholdMillis
        }

        override suspend fun aggregate(): DiagnosticsLogAggregate? = aggregateResult
    }
}
