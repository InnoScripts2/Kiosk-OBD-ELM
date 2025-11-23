package com.selfservice.kiosk.reports

import com.selfservice.platform.data.diagnostics.DiagnosticsReportRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.json.JSONObject

class DiagnosticsReportDeliverySupabaseSchemaTest {

    @Test
    fun `builds delivery rows for email and sms`() {
        val record = DiagnosticsReportRecord(
            sessionId = "session-1",
            generatedAtMillis = 420L,
            html = "<html></html>",
            pdfBytes = byteArrayOf(0x01),
            metadata = mapOf(
                "summary" to mapOf("metrics_total" to 2),
                "vehicle" to mapOf("vin" to "VIN123"),
                "customer" to mapOf(
                    "email" to "CLIENT@EXAMPLE.COM ",
                    "phone" to " +7 999 111 22 33 "
                )
            )
        )

        val rows = DiagnosticsReportDeliverySupabaseSchema.toRows(
            record,
            additionalFields = mapOf(
                DiagnosticsReportDeliverySupabaseSchema.Columns.KIOSK_ID to "kiosk-1",
                DiagnosticsReportDeliverySupabaseSchema.Columns.ENVIRONMENT to "qa"
            )
        )

        assertEquals(2, rows.size)
        val channels = rows.map { it[DiagnosticsReportDeliverySupabaseSchema.Columns.CHANNEL] }.toSet()
        assertTrue(channels.contains(DiagnosticsReportDeliverySupabaseSchema.Channel.Email.wireValue))
        assertTrue(channels.contains(DiagnosticsReportDeliverySupabaseSchema.Channel.Sms.wireValue))

        val emailRow = rows.first { it[DiagnosticsReportDeliverySupabaseSchema.Columns.CHANNEL] == DiagnosticsReportDeliverySupabaseSchema.Channel.Email.wireValue }
        val smsRow = rows.first { it[DiagnosticsReportDeliverySupabaseSchema.Columns.CHANNEL] == DiagnosticsReportDeliverySupabaseSchema.Channel.Sms.wireValue }

        assertEquals("client@example.com", emailRow[DiagnosticsReportDeliverySupabaseSchema.Columns.RECIPIENT])
        assertEquals("+79991112233", smsRow[DiagnosticsReportDeliverySupabaseSchema.Columns.RECIPIENT])
        assertEquals(
            DiagnosticsReportDeliverySupabaseSchema.STATUS_QUEUED,
            emailRow[DiagnosticsReportDeliverySupabaseSchema.Columns.STATUS]
        )
        assertEquals(
            DiagnosticsReportDeliverySupabaseSchema.STATUS_QUEUED,
            smsRow[DiagnosticsReportDeliverySupabaseSchema.Columns.STATUS]
        )
        assertEquals("kiosk-1", emailRow[DiagnosticsReportDeliverySupabaseSchema.Columns.KIOSK_ID])
        assertEquals("qa", smsRow[DiagnosticsReportDeliverySupabaseSchema.Columns.ENVIRONMENT])

        val metadata = emailRow[DiagnosticsReportDeliverySupabaseSchema.Columns.METADATA] as JSONObject
        assertTrue(metadata.has("summary"))
        assertTrue(metadata.has("vehicle"))
        assertTrue(metadata.has("customer"))
    }

    @Test
    fun `returns empty list when no recipients available`() {
        val record = DiagnosticsReportRecord(
            sessionId = "session-2",
            generatedAtMillis = 500L,
            html = "<html></html>",
            pdfBytes = byteArrayOf(0x02),
            metadata = mapOf("customer" to emptyMap<String, Any?>())
        )

        val rows = DiagnosticsReportDeliverySupabaseSchema.toRows(record)

        assertTrue(rows.isEmpty())
    }
}
