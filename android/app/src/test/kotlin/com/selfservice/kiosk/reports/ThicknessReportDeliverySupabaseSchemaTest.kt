package com.selfservice.kiosk.reports

import com.selfservice.platform.data.thickness.ThicknessReportRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.json.JSONObject

class ThicknessReportDeliverySupabaseSchemaTest {

    @Test
    fun `builds delivery rows for email and sms`() {
        val record = ThicknessReportRecord(
            sessionId = "session-777",
            generatedAtMillis = 900L,
            html = "<html></html>",
            pdfBytes = byteArrayOf(0x01),
            metadata = mapOf(
                "vehicle_type" to "Sedan",
                "stats" to mapOf("average" to 110.0, "deviations" to 1),
                "analysis" to mapOf("overall_status" to "GOOD"),
                "customer" to mapOf(
                    "email" to " CLIENT@EXAMPLE.COM ",
                    "phone" to " +7 (900) 111-22-33 "
                )
            )
        )

        val rows = ThicknessReportDeliverySupabaseSchema.toRows(
            record,
            additionalFields = mapOf(
                ThicknessReportDeliverySupabaseSchema.Columns.KIOSK_ID to "kiosk-2",
                ThicknessReportDeliverySupabaseSchema.Columns.ENVIRONMENT to "prod"
            )
        )

        assertEquals(2, rows.size)
        val channels = rows.map { it[ThicknessReportDeliverySupabaseSchema.Columns.CHANNEL] }.toSet()
        assertTrue(channels.contains(ThicknessReportDeliverySupabaseSchema.Channel.Email.wireValue))
        assertTrue(channels.contains(ThicknessReportDeliverySupabaseSchema.Channel.Sms.wireValue))

        val emailRow = rows.first { it[ThicknessReportDeliverySupabaseSchema.Columns.CHANNEL] == ThicknessReportDeliverySupabaseSchema.Channel.Email.wireValue }
        val smsRow = rows.first { it[ThicknessReportDeliverySupabaseSchema.Columns.CHANNEL] == ThicknessReportDeliverySupabaseSchema.Channel.Sms.wireValue }

        assertEquals("client@example.com", emailRow[ThicknessReportDeliverySupabaseSchema.Columns.RECIPIENT])
        assertEquals("+79001112233", smsRow[ThicknessReportDeliverySupabaseSchema.Columns.RECIPIENT])
        assertEquals(
            ThicknessReportDeliverySupabaseSchema.STATUS_QUEUED,
            emailRow[ThicknessReportDeliverySupabaseSchema.Columns.STATUS]
        )
        assertEquals(
            ThicknessReportDeliverySupabaseSchema.STATUS_QUEUED,
            smsRow[ThicknessReportDeliverySupabaseSchema.Columns.STATUS]
        )
        assertEquals("kiosk-2", emailRow[ThicknessReportDeliverySupabaseSchema.Columns.KIOSK_ID])
        assertEquals("prod", smsRow[ThicknessReportDeliverySupabaseSchema.Columns.ENVIRONMENT])

        val metadata = emailRow[ThicknessReportDeliverySupabaseSchema.Columns.METADATA] as JSONObject
        assertTrue(metadata.has("vehicle_type"))
        assertTrue(metadata.has("stats"))
        assertTrue(metadata.has("analysis"))
        assertTrue(metadata.has("customer"))
    }

    @Test
    fun `returns empty list when no recipients available`() {
        val record = ThicknessReportRecord(
            sessionId = "session-888",
            generatedAtMillis = 1200L,
            html = "<html></html>",
            pdfBytes = byteArrayOf(0x02),
            metadata = mapOf("customer" to emptyMap<String, Any?>())
        )

        val rows = ThicknessReportDeliverySupabaseSchema.toRows(record)

        assertTrue(rows.isEmpty())
    }
}
