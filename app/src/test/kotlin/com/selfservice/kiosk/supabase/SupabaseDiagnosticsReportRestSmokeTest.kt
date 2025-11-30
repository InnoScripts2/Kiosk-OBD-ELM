package com.selfservice.kiosk.supabase

import com.selfservice.kiosk.reports.DiagnosticsReportDeliverySupabaseSchema
import com.selfservice.kiosk.reports.DiagnosticsReportDeliverySupabaseSchema.Columns as DeliveryColumns
import com.selfservice.kiosk.reports.DiagnosticsReportSupabaseSchema
import com.selfservice.kiosk.reports.DiagnosticsReportSupabaseSchema.Columns
import com.selfservice.platform.data.diagnostics.DiagnosticsReportRecord
import java.io.File
import java.nio.file.Files
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseDiagnosticsReportRestSmokeTest {

    @Test
    fun `upsert and fetch diagnostics report row`() {
        val endpoint = FakeSupabaseEndpoint()
        val outboxDir = Files.createTempDirectory("supabase-outbox-reports").toFile()

        try {
            performUpsert(endpoint, outboxDir)

            val requests = endpoint.requests
            assertEquals("Expected report plus two delivery requests", 3, requests.size)

            val reportRequest = requests.first()
            assertEquals(DiagnosticsReportSupabaseSchema.TABLE_NAME, reportRequest.table)
            assertInsertedRow(reportRequest.payload)

            val fetched = endpoint.fetchFirst()
            assertEquals(reportRequest.payload.getString(Columns.REPORT_ID), fetched.getString(Columns.REPORT_ID))
            assertEquals("kiosk-smoke", fetched.getString(Columns.KIOSK_ID))
            assertEquals("DEV", fetched.getString(Columns.ENVIRONMENT))

            val deliveryRequests = requests.drop(1)
            val reportId = reportRequest.payload.getString(Columns.REPORT_ID)
            deliveryRequests.forEach { delivery ->
                assertEquals(DiagnosticsReportDeliverySupabaseSchema.TABLE_NAME, delivery.table)
                assertEquals(reportId, delivery.payload.getString(DeliveryColumns.REPORT_ID))
                assertEquals("session-report", delivery.payload.getString(DeliveryColumns.SESSION_ID))
                assertEquals(1_731_801_700_000L, delivery.payload.getLong(DeliveryColumns.GENERATED_AT_MS))
                assertEquals(
                    DiagnosticsReportDeliverySupabaseSchema.STATUS_QUEUED,
                    delivery.payload.getString(DeliveryColumns.STATUS)
                )
                assertEquals("kiosk-smoke", delivery.payload.getString(DeliveryColumns.KIOSK_ID))
                assertEquals("DEV", delivery.payload.getString(DeliveryColumns.ENVIRONMENT))
                val deliveryMetadata = delivery.payload.getJSONObject(DeliveryColumns.METADATA)
                val vehicle = deliveryMetadata.getJSONObject("vehicle")
                assertEquals("VIN123456789", vehicle.getString("vin"))
                val summary = deliveryMetadata.getJSONObject("summary")
                assertEquals(0, summary.getInt("errors"))
                assertEquals(1, summary.getInt("warnings"))
                val customer = deliveryMetadata.getJSONObject("customer")
                assertEquals("client@example.com", customer.getString("email"))
                assertEquals("+14155551234", customer.getString("phone"))
            }

            val emailDelivery = deliveryRequests.first { request ->
                request.payload.getString(DeliveryColumns.CHANNEL) == DiagnosticsReportDeliverySupabaseSchema.Channel.Email.wireValue
            }
            assertEquals("client@example.com", emailDelivery.payload.getString(DeliveryColumns.RECIPIENT))

            val smsDelivery = deliveryRequests.first { request ->
                request.payload.getString(DeliveryColumns.CHANNEL) == DiagnosticsReportDeliverySupabaseSchema.Channel.Sms.wireValue
            }
            assertEquals("+14155551234", smsDelivery.payload.getString(DeliveryColumns.RECIPIENT))

            val connection = endpoint.lastConnection
            requireNotNull(connection)
            assertEquals("POST", connection.requestMethod)
            assertEquals("application/json", connection.headers["Content-Type"])
            assertEquals("resolution=merge-duplicates,return=minimal", connection.headers["Prefer"])
        } finally {
            outboxDir.deleteRecursively()
        }
    }

    private fun performUpsert(endpoint: FakeSupabaseEndpoint, outboxDir: File) {
        val writer = SupabaseOutboxWriter(outboxDir)
        val record = DiagnosticsReportRecord(
            sessionId = "session-report",
            generatedAtMillis = 1_731_801_700_000L,
            html = "<html><body>Diagnostics</body></html>",
            pdfBytes = PDF_BYTES,
            metadata = mapOf(
                "vehicle" to mapOf("vin" to "VIN123456789", "brand" to "Toyota"),
                "summary" to mapOf("errors" to 0, "warnings" to 1),
                "customer" to mapOf("email" to "client@example.com", "phone" to "+14155551234")
            )
        )
        val additionalFields = mapOf(
            Columns.KIOSK_ID to "kiosk-smoke",
            Columns.ENVIRONMENT to "DEV"
        )
        val row = DiagnosticsReportSupabaseSchema.toRow(record, additionalFields = additionalFields)
        writer.enqueue(DiagnosticsReportSupabaseSchema.TABLE_NAME, row)
        val deliveryRows = DiagnosticsReportDeliverySupabaseSchema.toRows(record, additionalFields)
        deliveryRows.forEach { deliveryRow ->
            writer.enqueue(DiagnosticsReportDeliverySupabaseSchema.TABLE_NAME, deliveryRow)
        }

        val credentials = SupabaseCredentials(
            restUrl = "http://example.com",
            serviceKey = "service-key"
        )
        val client = SupabaseRestClient(
            credentials = credentials,
            connectionFactory = { url -> endpoint.createConnection(url) },
            logger = {}
        )
        val uploader = SupabaseOutboxUploader(directory = outboxDir, client = client)

        val result = runBlocking { uploader.flush() }
            assertTrue("Uploader flush failed: ${'$'}{result.failure}", result.isSuccess)
            assertEquals(3, result.processed)
            assertEquals(0, result.remaining)
    }

    private fun assertInsertedRow(row: JSONObject) {
        val reportId = row.getString(Columns.REPORT_ID)
        assertTrue("report_id must be 64 hex chars", reportId.matches(HEX_64_PATTERN))
        assertEquals("session-report", row.getString(Columns.SESSION_ID))
        assertEquals(1_731_801_700_000L, row.getLong(Columns.GENERATED_AT_MS))
        assertEquals("<html><body>Diagnostics</body></html>", row.getString(Columns.REPORT_HTML))

        val pdfBase64 = row.getString(Columns.REPORT_PDF_BASE64)
        val decodedPdf = Base64.getDecoder().decode(pdfBase64)
        assertArrayEquals(PDF_BYTES, decodedPdf)

        val metadata = row.getJSONObject(Columns.METADATA)
        val vehicle = metadata.getJSONObject("vehicle")
        assertEquals("VIN123456789", vehicle.getString("vin"))
        assertEquals("Toyota", vehicle.getString("brand"))
        val summary = metadata.getJSONObject("summary")
        assertEquals(0, summary.getInt("errors"))
        assertEquals(1, summary.getInt("warnings"))
        assertEquals("kiosk-smoke", row.getString(Columns.KIOSK_ID))
        assertEquals("DEV", row.getString(Columns.ENVIRONMENT))
    }

    companion object {
        private val PDF_BYTES = byteArrayOf(0x25, 0x50, 0x44, 0x46)
    }
}