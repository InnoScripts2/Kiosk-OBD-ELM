package com.selfservice.kiosk.supabase

import com.selfservice.kiosk.telemetry.DiagnosticsTelemetrySupabaseSchema
import com.selfservice.kiosk.telemetry.DiagnosticsTelemetrySupabaseSchema.Columns
import com.selfservice.platform.data.diagnostics.DiagnosticsTelemetryRecord
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseTelemetryRestSmokeTest {

    @Test
    fun `upsert and fetch diagnostics telemetry row`() {
        val endpoint = FakeSupabaseEndpoint()
        val outboxDir = Files.createTempDirectory("supabase-outbox").toFile()

        try {
            performUpsert(endpoint, outboxDir)

            val insertedRows = endpoint.rows
            assertEquals("Expected single upsert", 1, insertedRows.size)
            val inserted = insertedRows.single()
            assertInsertedRow(inserted)

            val fetched = endpoint.fetchFirst()
            assertEquals(inserted.getString(Columns.EVENT_ID), fetched.getString(Columns.EVENT_ID))
            assertEquals("kiosk-smoke", fetched.getString(Columns.KIOSK_ID))
            assertEquals("DEV", fetched.getString(Columns.ENVIRONMENT))

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
        val record = DiagnosticsTelemetryRecord(
            timestampMillis = 1_731_801_600_000L,
            eventType = "SessionCompleted",
            sessionId = "session-telemetry",
            metadata = mapOf("pid" to 12, "status" to "complete")
        )
        val row = DiagnosticsTelemetrySupabaseSchema.toRow(
            record,
            additionalFields = mapOf(
                Columns.KIOSK_ID to "kiosk-smoke",
                Columns.ENVIRONMENT to "DEV"
            )
        )
        writer.enqueue(DiagnosticsTelemetrySupabaseSchema.TABLE_NAME, row)

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
        assertTrue("Uploader flush failed: ${result.failure}", result.isSuccess)
        assertEquals(1, result.processed)
        assertEquals(0, result.remaining)
    }

    private fun assertInsertedRow(row: JSONObject) {
        assertEquals("SessionCompleted", row.getString(Columns.EVENT_TYPE))
        assertEquals("session-telemetry", row.optString(Columns.SESSION_ID))
        assertEquals(1_731_801_600_000L, row.getLong(Columns.DEVICE_TIMESTAMP_MS))
        assertEquals("kiosk-smoke", row.getString(Columns.KIOSK_ID))
        assertEquals("DEV", row.getString(Columns.ENVIRONMENT))
        val metadata = row.getJSONObject(Columns.METADATA)
        assertEquals(12, metadata.getInt("pid"))
        assertEquals("complete", metadata.getString("status"))
        val eventId = row.getString(Columns.EVENT_ID)
        assertTrue("event_id must be 64 hex chars", eventId.matches(HEX_64_PATTERN))
    }
}
