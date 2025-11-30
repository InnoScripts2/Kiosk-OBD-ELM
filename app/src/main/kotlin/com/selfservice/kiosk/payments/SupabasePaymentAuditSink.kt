package com.selfservice.kiosk.payments

import com.selfservice.feature.payments.PaymentAuditEvent
import com.selfservice.feature.payments.PaymentAuditSink
import com.selfservice.kiosk.supabase.SupabaseOutboxWriter

class SupabasePaymentAuditSink(
    private val writer: SupabaseOutboxWriter,
    private val table: String = PaymentsAuditSupabaseSchema.TABLE_NAME,
    private val additionalFieldsProvider: () -> Map<String, Any?> = { emptyMap() }
) : PaymentAuditSink {

    override fun record(event: PaymentAuditEvent) {
        val payload = PaymentsAuditSupabaseSchema.toRow(event, additionalFieldsProvider())
        writer.enqueue(table = table, payload = payload)
    }
}
