package com.selfservice.feature.payments

import java.util.concurrent.CopyOnWriteArrayList

class CompositePaymentAuditSink(
    sinks: List<PaymentAuditSink>
) : PaymentAuditSink {

    private val delegates = CopyOnWriteArrayList(sinks)

    override fun record(event: PaymentAuditEvent) {
        for (sink in delegates) {
            runCatching { sink.record(event) }
        }
    }
}

class MutablePaymentAuditSink(
    initial: PaymentAuditSink = PaymentAuditSink.NO_OP
) : PaymentAuditSink {

    @Volatile
    private var delegate: PaymentAuditSink = initial

    override fun record(event: PaymentAuditEvent) {
        delegate.record(event)
    }

    fun setDelegate(next: PaymentAuditSink) {
        delegate = next
    }
}

fun combinePaymentAuditSinks(vararg sinks: PaymentAuditSink?): PaymentAuditSink {
    val filtered = sinks.filterNotNull()
    return when (filtered.size) {
        0 -> PaymentAuditSink.NO_OP
        1 -> filtered.first()
        else -> CompositePaymentAuditSink(filtered)
    }
}
