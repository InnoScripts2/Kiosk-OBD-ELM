package com.selfservice.kiosk.reports

import com.selfservice.platform.data.diagnostics.DiagnosticsReportRecord
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.json.JSONObject

class DiagnosticsReportSupabaseSchemaTest {

    @Test
    fun toRowEncodesPdfAndMergesAdditionalFields() {
        val record = DiagnosticsReportRecord(
            sessionId = "session-99",
            generatedAtMillis = 42L,
            html = "<html>report</html>",
            pdfBytes = byteArrayOf(0x01, 0x02, 0x7F),
            metadata = mapOf<String, Any?>(
                "vehicle" to mapOf<String, Any?>("make" to "Hyundai", "model" to "Solaris"),
                "customer" to mapOf<String, Any?>("phone" to "+70000000000")
            )
        )

        val row = DiagnosticsReportSupabaseSchema.toRow(record, additionalFields = mapOf("kiosk_id" to "k1"))

        assertEquals("k1", row["kiosk_id"])
        assertEquals("session-99", row[DiagnosticsReportSupabaseSchema.Columns.SESSION_ID])
        val base64 = row[DiagnosticsReportSupabaseSchema.Columns.REPORT_PDF_BASE64] as String
        assertEquals(Base64.getEncoder().encodeToString(record.pdfBytes), base64)
        val metadata = row[DiagnosticsReportSupabaseSchema.Columns.METADATA] as JSONObject
        val vehicle = metadata.getJSONObject("vehicle")
        assertEquals("Hyundai", vehicle.getString("make"))
        assertEquals("Solaris", vehicle.getString("model"))
    }

    @Test
    fun stableIdDependsOnMetadata() {
        val record = DiagnosticsReportRecord(
            sessionId = "session-1",
            generatedAtMillis = 100L,
            html = "<html></html>",
            pdfBytes = byteArrayOf(0x01),
            metadata = mapOf<String, Any?>(
                "summary" to mapOf<String, Any?>("metrics_total" to 5)
            )
        )
        val same = DiagnosticsReportRecord(
            sessionId = "session-1",
            generatedAtMillis = 100L,
            html = "<html></html>",
            pdfBytes = byteArrayOf(0x01),
            metadata = mapOf<String, Any?>(
                "summary" to mapOf<String, Any?>("metrics_total" to 5)
            )
        )
        val different = DiagnosticsReportRecord(
            sessionId = "session-1",
            generatedAtMillis = 100L,
            html = "<html></html>",
            pdfBytes = byteArrayOf(0x01),
            metadata = mapOf<String, Any?>(
                "summary" to mapOf<String, Any?>("metrics_total" to 7)
            )
        )

        val id = DiagnosticsReportSupabaseSchema.stableId(record)
        val sameId = DiagnosticsReportSupabaseSchema.stableId(same)
        val differentId = DiagnosticsReportSupabaseSchema.stableId(different)

        assertEquals(id, sameId)
        assertNotEquals(id, differentId)
    }
}
