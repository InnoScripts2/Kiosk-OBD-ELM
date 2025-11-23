package com.selfservice.kiosk.reports

import android.util.Log
import android.util.Patterns
import com.selfservice.platform.data.diagnostics.DiagnosticsReportRecord
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.Locale
import java.util.regex.Pattern
import org.json.JSONObject

/**
 * Привязки таблицы Supabase для очереди доставки диагностических отчётов клиентам.
 */
object DiagnosticsReportDeliverySupabaseSchema {

    const val TABLE_NAME: String = "diagnostics_report_deliveries"

    object Columns {
        const val DELIVERY_ID: String = "delivery_id"
        const val REPORT_ID: String = "report_id"
        const val SESSION_ID: String = "session_id"
        const val GENERATED_AT_MS: String = "generated_at_ms"
        const val CHANNEL: String = "channel"
        const val RECIPIENT: String = "recipient"
        const val STATUS: String = "status"
        const val METADATA: String = "metadata"
        const val KIOSK_ID: String = "kiosk_id"
        const val ENVIRONMENT: String = "environment"
        const val ATTEMPTS: String = "attempts"
        const val LAST_ERROR: String = "last_error"
        const val LAST_ATTEMPT_AT: String = "last_attempt_at"
        const val DISPATCHED_AT: String = "dispatched_at"
        const val UPDATED_AT: String = "updated_at"
    }

    enum class Channel(val wireValue: String) {
        Email("email"),
        Sms("sms");

        companion object {
            fun fromWireValue(value: String): Channel? =
                when (value.lowercase(Locale.US)) {
                    Email.wireValue -> Email
                    Sms.wireValue -> Sms
                    else -> null
                }
        }
    }

    fun toRows(
        record: DiagnosticsReportRecord,
        additionalFields: Map<String, Any?> = emptyMap()
    ): List<Map<String, Any?>> {
        val deliveries = extractDeliveries(record)
        if (deliveries.isEmpty()) {
            return emptyList()
        }
        val reportId = DiagnosticsReportSupabaseSchema.stableId(record)
        return deliveries.map { delivery ->
            val base = mutableMapOf<String, Any?>(
                Columns.DELIVERY_ID to stableId(reportId, delivery.channel, delivery.recipient),
                Columns.REPORT_ID to reportId,
                Columns.SESSION_ID to record.sessionId,
                Columns.GENERATED_AT_MS to record.generatedAtMillis,
                Columns.CHANNEL to delivery.channel.wireValue,
                Columns.RECIPIENT to delivery.recipient,
                Columns.STATUS to STATUS_QUEUED,
                Columns.METADATA to JSONObject(delivery.metadata)
            )
            if (additionalFields.isNotEmpty()) {
                additionalFields.forEach { (key, value) ->
                    base[key] = value
                }
            }
            base
        }
    }

    fun stableId(
        reportId: String,
        channel: Channel,
        recipient: String
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(reportId.toByteArray())
        digest.update(SEPARATOR)
        digest.update(channel.wireValue.toByteArray())
        digest.update(SEPARATOR)
        digest.update(normalizeRecipientForId(channel, recipient).toByteArray())
        return digest.digest().joinToString(separator = "") { byte ->
            String.format(Locale.US, "%02x", byte)
        }
    }

    private fun extractDeliveries(
        record: DiagnosticsReportRecord
    ): List<Delivery> {
        val metadata = record.metadata
        val customerRaw = metadata["customer"] ?: return emptyList()
        val customer = when (customerRaw) {
            is Map<*, *> -> customerRaw
            is JSONObject -> customerRaw.toMap()
            else -> return emptyList()
        }
        val deliveries = ArrayList<Delivery>(2)

        val emailRaw = (customer["email"] as? String)?.trim().orEmpty()
        if (emailRaw.isNotEmpty()) {
            val normalizedEmail = emailRaw.lowercase(Locale.US)
            if (isValidEmail(normalizedEmail)) {
                deliveries += Delivery(
                    channel = Channel.Email,
                    recipient = normalizedEmail,
                    metadata = buildDeliveryMetadata(record)
                )
            } else {
                Log.w(TAG, "Skipping diagnostics report email delivery: invalid address")
            }
        }

        val phone = (customer["phone"] as? String)?.trim().orEmpty()
        if (phone.isNotEmpty()) {
            val normalized = normalizePhone(phone)
            if (normalized.isNotEmpty()) {
                deliveries += Delivery(
                    channel = Channel.Sms,
                    recipient = normalized,
                    metadata = buildDeliveryMetadata(record)
                )
            } else {
                Log.w(TAG, "Skipping diagnostics report SMS delivery: invalid phone")
            }
        }

        return deliveries
    }

    private fun buildDeliveryMetadata(
        record: DiagnosticsReportRecord
    ): Map<String, Any?> {
        val metadata = record.metadata
        val deliveryMetadata = LinkedHashMap<String, Any?>(3)
        (metadata["vehicle"] as? Any?)?.let { deliveryMetadata["vehicle"] = it }
        (metadata["summary"] as? Any?)?.let { deliveryMetadata["summary"] = it }
        (metadata["customer"] as? Any?)?.let { deliveryMetadata["customer"] = it }
        return deliveryMetadata
    }

    private fun JSONObject.toMap(): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = this[key]
        }
        return result
    }

    private fun normalizePhone(value: String): String {
        val digits = buildString(value.length) {
            value.forEach { ch ->
                when {
                    ch.isDigit() -> append(ch)
                    ch == '+' && isEmpty() -> append(ch)
                }
            }
        }
        return digits
    }

    private fun normalizeRecipientForId(channel: Channel, recipient: String): String =
        when (channel) {
            Channel.Email -> recipient.trim().lowercase(Locale.US)
            Channel.Sms -> normalizePhone(recipient)
        }

    private fun isValidEmail(value: String): Boolean {
        return try {
            val pattern: Pattern? = Patterns.EMAIL_ADDRESS
            if (pattern != null) {
                pattern.matcher(value).matches()
            } else {
                FALLBACK_EMAIL_PATTERN.matcher(value).matches()
            }
        } catch (_: Throwable) {
            FALLBACK_EMAIL_PATTERN.matcher(value).matches()
        }
    }

    private data class Delivery(
        val channel: Channel,
        val recipient: String,
        val metadata: Map<String, Any?>
    )
    private val SEPARATOR = byteArrayOf(0)
    private val FALLBACK_EMAIL_PATTERN: Pattern =
        Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE)

    private const val TAG = "DiagReportDelivery"
    const val STATUS_QUEUED = "queued"
    const val STATUS_PROCESSING = "processing"
    const val STATUS_SENT = "sent"
    const val STATUS_FAILED = "failed"
}
