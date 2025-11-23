package com.selfservice.kiosk.payments

import com.selfservice.feature.payments.PaymentAuditEvent
import java.util.Locale
import org.json.JSONObject

object PaymentsAuditSupabaseSchema {
    const val TABLE_NAME = "payments_audit"

    object Columns {
        const val EVENT_TYPE = "event_type"
        const val RECORDED_AT = "recorded_at"
        const val INTENT_ID = "intent_id"
        const val SESSION_ID = "session_id"
        const val SERVICE_TYPE = "service_type"
        const val STATUS = "status"
        const val AMOUNT = "amount_minor"
        const val CURRENCY = "currency"
        const val ENVIRONMENT = "environment"
        const val GATEWAY = "gateway"
        const val OPERATOR_ID = "operator_id"
        const val REQUEST_ID = "request_id"
        const val DETAILS = "details"
        const val KIOSK_ID = "kiosk_id"
    }

    fun toRow(event: PaymentAuditEvent, additionalFields: Map<String, Any?> = emptyMap()): Map<String, Any?> {
        val row = mutableMapOf<String, Any?>(
            Columns.EVENT_TYPE to event.type,
            Columns.RECORDED_AT to event.timestampIso,
            Columns.INTENT_ID to event.intentId,
            Columns.SESSION_ID to event.sessionId,
            Columns.SERVICE_TYPE to event.serviceType,
            Columns.STATUS to event.status?.name?.lowercase(Locale.US),
            Columns.AMOUNT to event.amount,
            Columns.CURRENCY to event.currency,
            Columns.ENVIRONMENT to event.environment.name.lowercase(Locale.US),
            Columns.GATEWAY to event.gateway,
            Columns.OPERATOR_ID to event.operatorId,
            Columns.REQUEST_ID to event.requestId
        )
        val details = event.details
        if (details != null) {
            val json = runCatching { JSONObject(details.toString()) }.getOrNull()
            row[Columns.DETAILS] = json ?: details.toString()
        }
        if (additionalFields.isNotEmpty()) {
            row.putAll(additionalFields)
        }
        return row
    }
}
