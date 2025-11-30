package com.selfservice.kiosk.reports

import com.selfservice.platform.data.thickness.ThicknessReportRecord
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.json.JSONObject

class ThicknessReportSupabaseSchemaTest {

    @Test
    fun toRowEncodesPdfAndMergesAdditionalFields() {
        val record = ThicknessReportRecord(
            sessionId = "session-55",
            generatedAtMillis = 73L,
            html = "<html>thickness</html>",
            pdfBytes = byteArrayOf(0x01, 0x02, 0x7F),
            metadata = mapOf(
                "vehicle_type" to "SUV",
                "stats" to mapOf("average" to 125.5, "deviations" to 2),
                "customer" to mapOf("phone" to "+79000000000")
            )
        )

        val row = ThicknessReportSupabaseSchema.toRow(record, additionalFields = mapOf("kiosk_id" to "k-9"))

        assertEquals("k-9", row[ThicknessReportSupabaseSchema.Columns.KIOSK_ID])
        assertEquals("session-55", row[ThicknessReportSupabaseSchema.Columns.SESSION_ID])
        val base64 = row[ThicknessReportSupabaseSchema.Columns.REPORT_PDF_BASE64] as String
        assertEquals(Base64.getEncoder().encodeToString(record.pdfBytes), base64)
        val metadata = row[ThicknessReportSupabaseSchema.Columns.METADATA] as JSONObject
        assertEquals("SUV", metadata.getString("vehicle_type"))
        val stats = metadata.getJSONObject("stats")
        assertEquals(125.5, stats.getDouble("average"))
        assertEquals(2, stats.getInt("deviations"))
    }

    @Test
    fun stableIdDependsOnMetadata() {
        val record = ThicknessReportRecord(
            sessionId = "session-1",
            generatedAtMillis = 100L,
            html = "<html></html>",
            pdfBytes = byteArrayOf(0x01),
            metadata = mapOf(
                "analysis" to mapOf("status" to "ok")
            )
        )
        val same = record.copy()
        val different = record.copy(
            metadata = mapOf(
                "analysis" to mapOf("status" to "warning")
            )
        )

        val id = ThicknessReportSupabaseSchema.stableId(record)
        val sameId = ThicknessReportSupabaseSchema.stableId(same)
        val differentId = ThicknessReportSupabaseSchema.stableId(different)

        assertEquals(id, sameId)
        assertNotEquals(id, differentId)
    }
}
