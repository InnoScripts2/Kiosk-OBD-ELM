package com.selfservice.kiosk.reports

import com.selfservice.core.DispatchersProvider
import com.selfservice.kiosk.reports.DiagnosticsReportDeliverySupabaseSchema
import com.selfservice.platform.data.diagnostics.DiagnosticsReportRecord
import com.selfservice.platform.data.diagnostics.DiagnosticsReportStore
import com.selfservice.platform.data.diagnostics.DiagnosticsReportSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsReportOutboxBridgeTest {

    @Test
    fun `exports pending reports into outbox and marks them exported`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatchersProvider {
            override val io: CoroutineDispatcher = dispatcher
            override val computation: CoroutineDispatcher = dispatcher
            override val main: CoroutineDispatcher = dispatcher
        }
        val store = RecordingReportStore(
            pendingRecords = mutableListOf(
                DiagnosticsReportRecord(
                    sessionId = "s1",
                    generatedAtMillis = 100L,
                    html = "<html>a</html>",
                    pdfBytes = byteArrayOf(0x01, 0x02),
                    metadata = mutableMapOf<String, Any?>().apply {
                        this["summary"] = mutableMapOf<String, Any?>().apply {
                            this["metrics_total"] = 3
                        }
                    }
                ),
                DiagnosticsReportRecord(
                    sessionId = "s2",
                    generatedAtMillis = 150L,
                    html = "<html>b</html>",
                    pdfBytes = byteArrayOf(0x03),
                    metadata = emptyMap()
                )
            )
        )
        val enqueuedOperations = mutableListOf<Pair<String, Map<String, Any?>>>()
        var exportNotifications = 0

        val bridge = DiagnosticsReportOutboxBridge(
            store = store,
            scope = this,
            dispatchers = dispatchers,
            onExported = { exportNotifications += 1 }
        )

        bridge.attachSupabaseOutbox(
            enqueue = { table, payload -> enqueuedOperations += table to payload },
            additionalFieldsProvider = { mapOf<String, Any?>("kiosk_id" to "k-01") }
        )

        advanceUntilIdle()

        assertEquals(listOf(150L), store.markedThresholds)
        assertEquals(1, exportNotifications)
        assertTrue(store.pendingRecords.isEmpty())
        val reportRows = enqueuedOperations.filter { it.first == DiagnosticsReportSupabaseSchema.TABLE_NAME }
        assertEquals(2, reportRows.size, "Expected one Supabase row per report")
        val firstPayload = reportRows.first().second
        val metadata = firstPayload[DiagnosticsReportSupabaseSchema.Columns.METADATA] as JSONObject
        val summary = metadata.getJSONObject("summary")
        assertEquals(3, summary.getInt("metrics_total"))
        val pdfBase64 = firstPayload[DiagnosticsReportSupabaseSchema.Columns.REPORT_PDF_BASE64] as String
        assertTrue(pdfBase64.isNotBlank())
    }

    @Test
    fun `enqueues delivery requests for available channels`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatchersProvider {
            override val io: CoroutineDispatcher = dispatcher
            override val computation: CoroutineDispatcher = dispatcher
            override val main: CoroutineDispatcher = dispatcher
        }
        val store = RecordingReportStore(
            pendingRecords = mutableListOf(
                DiagnosticsReportRecord(
                    sessionId = "s-delivery",
                    generatedAtMillis = 250L,
                    html = "<html>delivery</html>",
                    pdfBytes = byteArrayOf(0x01),
                    metadata = mutableMapOf<String, Any?>().apply {
                        this["summary"] = mutableMapOf<String, Any?>().apply {
                            this["metrics_total"] = 5
                        }
                        this["vehicle"] = mutableMapOf<String, Any?>().apply {
                            this["vin"] = "VIN"
                        }
                        this["customer"] = mutableMapOf<String, Any?>().apply {
                            this["email"] = "client@example.com"
                            this["phone"] = "+1 (415) 555-1234"
                        }
                    }
                )
            )
        )
        val enqueuedOperations = mutableListOf<Pair<String, Map<String, Any?>>>()

        val bridge = DiagnosticsReportOutboxBridge(
            store = store,
            scope = this,
            dispatchers = dispatchers
        )

        bridge.attachSupabaseOutbox(
            enqueue = { table, payload -> enqueuedOperations += table to payload },
            additionalFieldsProvider = {
                mapOf<String, Any?>("kiosk_id" to "k-02", "environment" to "qa")
            }
        )

        advanceUntilIdle()

        val reportRow = enqueuedOperations.firstOrNull { it.first == DiagnosticsReportSupabaseSchema.TABLE_NAME }
        requireNotNull(reportRow)
        val reportPayload = reportRow.second
        val reportId = reportPayload[DiagnosticsReportSupabaseSchema.Columns.REPORT_ID] as String

        val deliveryRows = enqueuedOperations.filter { it.first == DiagnosticsReportDeliverySupabaseSchema.TABLE_NAME }
        assertEquals(2, deliveryRows.size)

        deliveryRows.forEach { (table, payload) ->
            assertEquals(DiagnosticsReportDeliverySupabaseSchema.TABLE_NAME, table)
            assertEquals(reportId, payload[DiagnosticsReportDeliverySupabaseSchema.Columns.REPORT_ID])
            assertEquals(250L, payload[DiagnosticsReportDeliverySupabaseSchema.Columns.GENERATED_AT_MS])
            assertEquals(
                DiagnosticsReportDeliverySupabaseSchema.STATUS_QUEUED,
                payload[DiagnosticsReportDeliverySupabaseSchema.Columns.STATUS]
            )
            assertEquals("k-02", payload[DiagnosticsReportDeliverySupabaseSchema.Columns.KIOSK_ID])
            assertEquals("qa", payload[DiagnosticsReportDeliverySupabaseSchema.Columns.ENVIRONMENT])
            val metadataJson = payload[DiagnosticsReportDeliverySupabaseSchema.Columns.METADATA] as JSONObject
            assertTrue(metadataJson.has("summary"))
            assertTrue(metadataJson.has("vehicle"))
        }

        val emailDelivery = deliveryRows.first { (table, payload) ->
            table == DiagnosticsReportDeliverySupabaseSchema.TABLE_NAME &&
                payload[DiagnosticsReportDeliverySupabaseSchema.Columns.CHANNEL] == DiagnosticsReportDeliverySupabaseSchema.Channel.Email.wireValue
        }.second
        assertEquals("client@example.com", emailDelivery[DiagnosticsReportDeliverySupabaseSchema.Columns.RECIPIENT])

        val smsDelivery = deliveryRows.first { (table, payload) ->
            table == DiagnosticsReportDeliverySupabaseSchema.TABLE_NAME &&
                payload[DiagnosticsReportDeliverySupabaseSchema.Columns.CHANNEL] == DiagnosticsReportDeliverySupabaseSchema.Channel.Sms.wireValue
        }.second
        assertEquals("+14155551234", smsDelivery[DiagnosticsReportDeliverySupabaseSchema.Columns.RECIPIENT])
    }

    private class RecordingReportStore(
        val pendingRecords: MutableList<DiagnosticsReportRecord>
    ) : DiagnosticsReportStore {

        val markedThresholds = mutableListOf<Long>()

        override suspend fun record(record: DiagnosticsReportRecord) {
            pendingRecords += record
        }

        override suspend fun pending(): List<DiagnosticsReportRecord> = pendingRecords.toList().also { pendingRecords.clear() }

        override suspend fun markExportedUpTo(timestampMillis: Long) {
            markedThresholds += timestampMillis
        }

        override suspend fun deleteOlderThan(thresholdMillis: Long) {
            // no-op for test
        }

        override suspend fun summary() = DiagnosticsReportSummary.empty()
    }
}
