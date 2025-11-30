package com.selfservice.feature.payments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class PaymentAuditSinksTest {

    @Test
    fun `combine returns no-op when empty`() {
        val combined = combinePaymentAuditSinks(null)
        assertSame(PaymentAuditSink.NO_OP, combined)
    }

    @Test
    fun `combine returns single sink`() {
        val sink = RecordingSink()
        val combined = combinePaymentAuditSinks(sink)
        assertSame(sink, combined)
    }

    @Test
    fun `composite forwards to all sinks`() {
        val events = mutableListOf<String>()
        val sinkA = RecordingSink { events += "A" }
        val sinkB = RecordingSink { events += "B" }
        val combined = combinePaymentAuditSinks(sinkA, sinkB)
        combined.record(sampleEvent())
        assertEquals(listOf("A", "B"), events)
    }

    private fun sampleEvent(): PaymentAuditEvent = PaymentAuditEvent(
        type = "test",
        timestampIso = "2025-11-21T12:00:00Z",
        environment = PaymentEnvironment.DEV,
    )

    private class RecordingSink(private val onRecord: (() -> Unit)? = null) : PaymentAuditSink {
        override fun record(event: PaymentAuditEvent) {
            onRecord?.invoke()
        }
    }
}
