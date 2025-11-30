package com.selfservice.kiosk.payments

import com.selfservice.feature.payments.PaymentAuditEvent
import com.selfservice.feature.payments.PaymentEnvironment
import com.selfservice.feature.payments.PaymentStatus
import com.selfservice.kiosk.supabase.SupabaseOutboxWriter
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.json.JSONObject

class SupabasePaymentAuditSinkTest {
    private lateinit var tempDir: java.nio.file.Path

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("payments-audit-sink-test")
    }

    @AfterTest
    fun teardown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `enqueues audit event`() {
        val writer = SupabaseOutboxWriter(directory = tempDir.toFile())
        val sink = SupabasePaymentAuditSink(writer) {
            mapOf(PaymentsAuditSupabaseSchema.Columns.KIOSK_ID to "kiosk-42")
        }
        val event = PaymentAuditEvent(
            type = "intent_created",
            timestampIso = "2025-11-21T12:00:00Z",
            intentId = "pi_123",
            sessionId = "session-1",
            serviceType = "obd",
            status = PaymentStatus.PENDING,
            amount = 1500,
            currency = "RUB",
            environment = PaymentEnvironment.DEV,
            gateway = "dev-gateway",
            operatorId = "operator-7"
        )
        sink.record(event)

        val lines = tempDir.resolve(SupabaseOutboxWriter.DEFAULT_FILE_NAME).toFile().readLines()
        val envelope = JSONObject(lines.single())
        assertEquals(PaymentsAuditSupabaseSchema.TABLE_NAME, envelope.getString("table"))
        val payload = envelope.getJSONObject("payload")
        assertEquals("intent_created", payload.getString("event_type"))
        assertEquals("pi_123", payload.getString("intent_id"))
        assertEquals("dev", payload.getString("environment"))
        assertEquals("kiosk-42", payload.getString("kiosk_id"))
    }
}
