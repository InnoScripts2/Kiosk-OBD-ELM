package com.selfservice.kiosk.payments

import com.selfservice.feature.payments.PaymentAuditEvent
import com.selfservice.feature.payments.PaymentEnvironment
import com.selfservice.feature.payments.PaymentStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.json.JSONObject

class PaymentsAuditSupabaseSchemaTest {

    @Test
    fun `toRow maps audit event into supabase payload`() {
        val event = PaymentAuditEvent(
            type = "intent_created",
            timestampIso = "2025-11-21T12:00:00Z",
            intentId = "pi_123",
            sessionId = "session-1",
            serviceType = "diagnostics",
            status = PaymentStatus.PENDING,
            amount = 1500,
            currency = "RUB",
            environment = PaymentEnvironment.QA,
            gateway = "dev-gateway",
            operatorId = "agent-7",
            requestId = "req-9",
            details = buildJsonObject { put("reason", JsonPrimitive("testing")) }
        )

        val row = PaymentsAuditSupabaseSchema.toRow(
            event = event,
            additionalFields = mapOf(PaymentsAuditSupabaseSchema.Columns.KIOSK_ID to "kiosk-1")
        )

        assertEquals("intent_created", row[PaymentsAuditSupabaseSchema.Columns.EVENT_TYPE])
        assertEquals("2025-11-21T12:00:00Z", row[PaymentsAuditSupabaseSchema.Columns.RECORDED_AT])
        assertEquals("pi_123", row[PaymentsAuditSupabaseSchema.Columns.INTENT_ID])
        assertEquals("session-1", row[PaymentsAuditSupabaseSchema.Columns.SESSION_ID])
        assertEquals("diagnostics", row[PaymentsAuditSupabaseSchema.Columns.SERVICE_TYPE])
        assertEquals("pending", row[PaymentsAuditSupabaseSchema.Columns.STATUS])
        assertEquals(1500L, row[PaymentsAuditSupabaseSchema.Columns.AMOUNT])
        assertEquals("RUB", row[PaymentsAuditSupabaseSchema.Columns.CURRENCY])
        assertEquals("qa", row[PaymentsAuditSupabaseSchema.Columns.ENVIRONMENT])
        assertEquals("dev-gateway", row[PaymentsAuditSupabaseSchema.Columns.GATEWAY])
        assertEquals("agent-7", row[PaymentsAuditSupabaseSchema.Columns.OPERATOR_ID])
        assertEquals("req-9", row[PaymentsAuditSupabaseSchema.Columns.REQUEST_ID])
        assertEquals("kiosk-1", row[PaymentsAuditSupabaseSchema.Columns.KIOSK_ID])
        val details = row[PaymentsAuditSupabaseSchema.Columns.DETAILS]
        assertTrue(details is JSONObject)
        assertEquals("testing", details.getString("reason"))
    }

    @Test
    fun `additional fields override base columns`() {
        val event = PaymentAuditEvent(
            type = "status_checked",
            timestampIso = "2025-11-22T08:00:00Z",
            intentId = "pi_status",
            environment = PaymentEnvironment.PROD,
            currency = "RUB"
        )

        val row = PaymentsAuditSupabaseSchema.toRow(
            event = event,
            additionalFields = mapOf(
                PaymentsAuditSupabaseSchema.Columns.CURRENCY to "USD",
                PaymentsAuditSupabaseSchema.Columns.KIOSK_ID to "kiosk-prod"
            )
        )

        assertEquals("USD", row[PaymentsAuditSupabaseSchema.Columns.CURRENCY])
        assertEquals("kiosk-prod", row[PaymentsAuditSupabaseSchema.Columns.KIOSK_ID])
    }

    @Test
    fun `null optional fields remain nullable in payload`() {
        val event = PaymentAuditEvent(
            type = "webhook_handled",
            timestampIso = "2025-11-23T09:30:00Z",
            environment = PaymentEnvironment.DEV
        )

        val row = PaymentsAuditSupabaseSchema.toRow(event)

        assertEquals(null, row[PaymentsAuditSupabaseSchema.Columns.INTENT_ID])
        assertEquals(null, row[PaymentsAuditSupabaseSchema.Columns.OPERATOR_ID])
        assertEquals(null, row[PaymentsAuditSupabaseSchema.Columns.DETAILS])
    }
}
