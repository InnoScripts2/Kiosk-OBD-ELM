package com.selfservice.feature.payments

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Audit trail event emitted by [PaymentModule] and PSP adapters.
 */
@Serializable
data class PaymentAuditEvent(
    val type: String,
    val timestampIso: String,
    val intentId: String? = null,
    val sessionId: String? = null,
    val serviceType: String? = null,
    val status: PaymentStatus? = null,
    val amount: Long? = null,
    val currency: String? = null,
    val environment: PaymentEnvironment,
    val gateway: String? = null,
    val operatorId: String? = null,
    val requestId: String? = null,
    val details: JsonObject? = null,
)

fun interface PaymentAuditSink {
    fun record(event: PaymentAuditEvent)

    companion object {
        val NO_OP: PaymentAuditSink = PaymentAuditSink { _ -> }
    }
}
