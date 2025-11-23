package com.selfservice.platform.logging

import com.selfservice.core.DispatchersProvider
import com.selfservice.core.logging.DiagnosticsLogEntry
import com.selfservice.core.logging.DiagnosticsLogFileSink
import com.selfservice.platform.data.diagnostics.DiagnosticsLogStore
import com.selfservice.platform.data.diagnostics.DiagnosticsLogSummary
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsLogServiceTest {

    private val tempRoot: File = Files.createTempDirectory("diagnostics-service-test").toFile()
    private val logsDir = File(tempRoot, "logs")

    @AfterTest
    fun tearDown() {
        tempRoot.deleteRecursively()
    }

    @Test
    fun `log enqueues entry and triggers export when outbox attached`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = dispatcherProvider(dispatcher)
        val sink = DiagnosticsLogFileSink(directory = logsDir)
        val store = RecordingStore()
        val storeUpdates = mutableListOf<Unit>()
        val service = DiagnosticsLogService(
            sink = sink,
            scope = this,
            dispatchers = dispatchers,
            store = store,
            storeUpdateCallback = { storeUpdates += Unit }
        )
        val writer = RecordingWriter()

        service.attachOutbox(writer) {
            mapOf(
                DiagnosticsLogSupabaseSchema.Columns.KIOSK_ID to "kiosk-test",
                DiagnosticsLogSupabaseSchema.Columns.ENVIRONMENT to "dev"
            )
        }
        advanceUntilIdle()

        service.log("session", "start", mapOf("step" to 1))
        advanceUntilIdle()

        assertEquals(1, store.recordedEntries.size)
        assertEquals(2, storeUpdates.size)
        val storedEntry = store.recordedEntries.first()
        assertEquals("session", storedEntry.category)
        assertEquals("start", storedEntry.message)

        assertTrue(writer.operations.isNotEmpty())
        val payload = writer.operations.first().payload
        assertEquals("session", payload[DiagnosticsLogSupabaseSchema.Columns.CATEGORY])
        assertEquals("kiosk-test", payload[DiagnosticsLogSupabaseSchema.Columns.KIOSK_ID])
        assertTrue(sink.snapshot().isEmpty())
    }

    @Test
    fun `markExportedUpTo invoked after successful export`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = dispatcherProvider(dispatcher)
        val sink = DiagnosticsLogFileSink(directory = logsDir)
        val store = RecordingStore()
        val service = DiagnosticsLogService(
            sink = sink,
            scope = this,
            dispatchers = dispatchers,
            store = store,
            storeUpdateCallback = {}
        )
        val writer = RecordingWriter()

        service.attachOutbox(writer)
        advanceUntilIdle()

        service.log("session", "end", mapOf("result" to "ok"))
        advanceUntilIdle()

        assertTrue(store.markedExported.isNotEmpty())
        store.summaryValue = DiagnosticsLogSummary(totalCount = 3, pendingCount = 1, oldestPendingAtMillis = 42L)
        val summary = service.summary()
        assertEquals(store.summaryValue, summary)
    }

    @Test
    fun `prune older than clears store`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = dispatcherProvider(dispatcher)
        val sink = DiagnosticsLogFileSink(directory = logsDir)
        val store = RecordingStore()
        val storeUpdates = mutableListOf<Unit>()
        val service = DiagnosticsLogService(
            sink = sink,
            scope = this,
            dispatchers = dispatchers,
            store = store,
            storeUpdateCallback = { storeUpdates += Unit }
        )

        service.pruneOlderThan(1234L)
        advanceUntilIdle()

        assertEquals(listOf(1234L), store.deletedOlderThan)
        assertEquals(1, storeUpdates.size)
    }

    private fun dispatcherProvider(dispatcher: CoroutineDispatcher) = object : DispatchersProvider {
        override val io: CoroutineDispatcher = dispatcher
        override val computation: CoroutineDispatcher = dispatcher
        override val main: CoroutineDispatcher = dispatcher
    }

    private class RecordingWriter : DiagnosticsLogOutboxWriter {
        data class Operation(
            val table: String,
            val payload: Map<String, Any?>,
            val operation: String
        )

        val operations = mutableListOf<Operation>()

        override fun enqueue(table: String, payload: Map<String, Any?>, operation: String) {
            operations += Operation(table, payload, operation)
        }
    }

    private class RecordingStore : DiagnosticsLogStore {
        val recordedEntries = mutableListOf<DiagnosticsLogEntry>()
        val markedExported = mutableListOf<Long>()
        val deletedOlderThan = mutableListOf<Long>()
        var summaryValue: DiagnosticsLogSummary = DiagnosticsLogSummary.empty()

        override suspend fun record(entry: DiagnosticsLogEntry) {
            recordedEntries += entry
        }

        override suspend fun markExportedUpTo(timestampMillis: Long) {
            markedExported += timestampMillis
        }

        override suspend fun deleteOlderThan(thresholdMillis: Long) {
            deletedOlderThan += thresholdMillis
        }

        override suspend fun summary(): DiagnosticsLogSummary = summaryValue
    }
}
