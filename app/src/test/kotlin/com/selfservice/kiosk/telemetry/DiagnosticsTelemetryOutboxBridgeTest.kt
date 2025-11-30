package com.selfservice.kiosk.telemetry

import com.selfservice.core.DispatchersProvider
import com.selfservice.platform.data.diagnostics.DiagnosticsTelemetryRecord
import com.selfservice.platform.data.diagnostics.DiagnosticsTelemetryStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsTelemetryOutboxBridgeTest {

    @Test
    fun `marks records exported and notifies listener`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatchersProvider {
            override val io: CoroutineDispatcher = dispatcher
            override val computation: CoroutineDispatcher = dispatcher
            override val main: CoroutineDispatcher = dispatcher
        }
        val store = RecordingTelemetryStore(
            pendingRecords = mutableListOf(
                DiagnosticsTelemetryRecord(timestampMillis = 100L, eventType = "SessionStarted", sessionId = "s1"),
                DiagnosticsTelemetryRecord(timestampMillis = 250L, eventType = "SessionCompleted", sessionId = "s1")
            )
        )
        val exportedNotifications = mutableListOf<Unit>()
        val enqueuedPayloads = mutableListOf<Map<String, Any?>>()

        val bridge = DiagnosticsTelemetryOutboxBridge(
            store = store,
            scope = this,
            dispatchers = dispatchers,
            onExported = { exportedNotifications += Unit }
        )

        bridge.attachSupabaseOutbox(
            enqueue = { _, payload -> enqueuedPayloads += payload },
            additionalFieldsProvider = { emptyMap() }
        )

        advanceUntilIdle()

        assertEquals(listOf(250L), store.markedThresholds)
        assertEquals(1, exportedNotifications.size)
        assertEquals(2, enqueuedPayloads.size)
        assertTrue(store.pendingRecords.isEmpty())
    }

    private class RecordingTelemetryStore(
        val pendingRecords: MutableList<DiagnosticsTelemetryRecord>
    ) : DiagnosticsTelemetryStore {

        val markedThresholds = mutableListOf<Long>()

        override suspend fun record(record: DiagnosticsTelemetryRecord) {
            pendingRecords += record
        }

        override suspend fun pending(): List<DiagnosticsTelemetryRecord> = pendingRecords.toList().also { pendingRecords.clear() }

        override suspend fun markExportedUpTo(timestampMillis: Long) {
            markedThresholds += timestampMillis
        }

        override suspend fun deleteOlderThan(thresholdMillis: Long) {
            // no-op for test
        }

        override suspend fun summary() = com.selfservice.platform.data.diagnostics.DiagnosticsTelemetrySummary.empty()
    }
}