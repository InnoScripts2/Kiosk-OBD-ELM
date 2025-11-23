package com.selfservice.kiosk.payments

import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.selfservice.feature.payments.CreatePaymentIntentInput
import com.selfservice.feature.payments.ManualConfirmationInput
import com.selfservice.feature.payments.PaymentContact
import com.selfservice.feature.payments.PaymentEnvironment
import com.selfservice.feature.payments.PaymentIntent
import com.selfservice.feature.payments.PaymentIntentMetadata
import com.selfservice.feature.payments.PaymentIntentQrCode
import com.selfservice.feature.payments.PaymentModule
import com.selfservice.feature.payments.PaymentModuleMetricsSnapshot
import com.selfservice.feature.payments.PaymentSessionBreakdown
import com.selfservice.feature.payments.PaymentSessionRecord
import com.selfservice.feature.payments.PaymentStatus
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject

/**
 * Bridges WebView JavaScript paywall client calls into the Kotlin [PaymentModule].
 * Incoming messages must look like `{ "requestId": "...", "action": "create_intent", "payload": { ... } }`.
 * Results are delivered back into JS via `window.dispatchEvent(new CustomEvent('kiosk-payment', { detail }))`.
 */
class PaymentsJavascriptBridge(
    private val webView: WebView,
    private val payments: PaymentModule,
    private val scope: CoroutineScope,
) {
    @Volatile
    private var disposed = false
    private val json = Json { ignoreUnknownKeys = true }

    @JavascriptInterface
    fun postMessage(raw: String?) {
        if (disposed) return
        val parsed = runCatching { parseMessage(raw) }
            .onFailure { error ->
                Log.w(TAG, "Failed to parse JS payment message", error)
                emitError(
                    requestId = null,
                    action = null,
                    code = "invalid_request",
                    message = error.message ?: error::class.java.simpleName,
                )
            }
            .getOrNull() ?: return
        when (parsed.action.lowercase(Locale.ROOT)) {
            "create_intent" -> handleCreateIntent(parsed)
            "get_intent" -> handleGetIntent(parsed)
            "get_status" -> handleGetStatus(parsed)
            "confirm_dev" -> handleConfirmDev(parsed)
            "manual_confirm" -> handleManualConfirm(parsed)
            "module_info" -> handleModuleInfo(parsed)
            else -> emitError(
                requestId = parsed.requestId,
                action = parsed.action,
                code = "unknown_action",
                message = "Unsupported action ${'$'}{parsed.action}",
            )
        }
    }

    fun dispose() {
        disposed = true
    }

    private fun handleCreateIntent(message: BridgeMessage) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val payload = message.payload ?: error("payload is required")
                val input = parseCreatePayload(payload)
                val result = payments.createIntent(input)
                val data = JSONObject().apply {
                    put("intent", result.intent.toJson())
                    put("breakdown", result.breakdown.toJson())
                }
                emitSuccess(message.requestId, message.action, data)
            }.onFailure { error ->
                Log.w(TAG, "create_intent failed", error)
                emitError(
                    requestId = message.requestId,
                    action = message.action,
                    code = error.codeFor(),
                    message = error.message ?: error::class.java.simpleName,
                )
            }
        }
    }

    private fun handleGetIntent(message: BridgeMessage) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val payload = message.payload ?: error("payload is required")
                val id = payload.optString("id").takeIf { it.isNotBlank() }
                    ?: error("id is required")
                val record = payments.getIntent(id) ?: error("Intent ${'$'}id not found")
                emitSuccess(message.requestId, message.action, record.toJson())
            }.onFailure { error ->
                Log.w(TAG, "get_intent failed", error)
                emitError(
                    requestId = message.requestId,
                    action = message.action,
                    code = error.codeFor(notFound = error.message?.contains("not found") == true),
                    message = error.message ?: error::class.java.simpleName,
                )
            }
        }
    }

    private fun handleGetStatus(message: BridgeMessage) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val payload = message.payload ?: error("payload is required")
                val id = payload.optString("id").takeIf { it.isNotBlank() }
                    ?: error("id is required")
                val status = payments.getStatus(id)
                    ?: error("Intent ${'$'}id not found")
                val data = JSONObject().apply {
                    put("id", id)
                    put("status", status.toWire())
                }
                emitSuccess(message.requestId, message.action, data)
            }.onFailure { error ->
                Log.w(TAG, "get_status failed", error)
                emitError(
                    requestId = message.requestId,
                    action = message.action,
                    code = error.codeFor(notFound = error.message?.contains("not found") == true),
                    message = error.message ?: error::class.java.simpleName,
                )
            }
        }
    }

    private fun handleConfirmDev(message: BridgeMessage) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val payload = message.payload ?: error("payload is required")
                val id = payload.optString("id").takeIf { it.isNotBlank() }
                    ?: error("id is required")
                val record = payments.confirmDev(id)
                    ?: error("Intent ${'$'}id not found or cannot confirm")
                emitSuccess(message.requestId, message.action, record.toJson())
            }.onFailure { error ->
                Log.w(TAG, "confirm_dev failed", error)
                emitError(
                    requestId = message.requestId,
                    action = message.action,
                    code = error.codeFor(),
                    message = error.message ?: error::class.java.simpleName,
                )
            }
        }
    }

    private fun handleManualConfirm(message: BridgeMessage) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val payload = message.payload ?: error("payload is required")
                val id = payload.optString("id").takeIf { it.isNotBlank() }
                    ?: error("id is required")
                val operatorId = payload.optString("operatorId").takeIf { it.isNotBlank() }
                    ?: error("operatorId is required")
                val note = payload.optString("note").takeIf { it.isNotBlank() }
                val meta = payload.opt("meta")?.toPaymentIntentMetadata()
                val record = payments.manualConfirm(
                    ManualConfirmationInput(
                        id = id,
                        operatorId = operatorId,
                        note = note,
                        meta = meta,
                    ),
                ) ?: error("Intent ${'$'}id not found")
                emitSuccess(message.requestId, message.action, record.toJson())
            }.onFailure { error ->
                Log.w(TAG, "manual_confirm failed", error)
                emitError(
                    requestId = message.requestId,
                    action = message.action,
                    code = error.codeFor(),
                    message = error.message ?: error::class.java.simpleName,
                )
            }
        }
    }

    private fun handleModuleInfo(message: BridgeMessage) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val snapshot = payments.getMetricsSnapshot()
                emitSuccess(message.requestId, message.action, snapshot.toJson())
            }.onFailure { error ->
                Log.w(TAG, "module_info failed", error)
                emitError(
                    requestId = message.requestId,
                    action = message.action,
                    code = error.codeFor(),
                    message = error.message ?: error::class.java.simpleName,
                )
            }
        }
    }

    private fun parseCreatePayload(payload: JSONObject): CreatePaymentIntentInput {
        val amount = payload.getNumber("amount")?.toLong()
            ?: error("amount is required and must be numeric")
        val currency = payload.optString("currency").ifBlank { "RUB" }
        val sessionId = payload.optString("sessionId").takeIf { it.isNotBlank() }
        val serviceType = payload.optString("serviceType").takeIf { it.isNotBlank() }
        val idempotencyKey = payload.optString("idempotencyKey").takeIf { it.isNotBlank() }
        val expiresInMs = payload.getNumber("expiresInMs")?.toLong()
        val contact = payload.optJSONObject("contact")?.let { contactJson ->
            val email = contactJson.optString("email").takeIf { it.isNotBlank() }
            val phone = contactJson.optString("phone").takeIf { it.isNotBlank() }
            if (email == null && phone == null) null else PaymentContact(email = email, phone = phone)
        }
        val meta = payload.opt("meta")?.let { raw -> raw.toPaymentIntentMetadata() }
        return CreatePaymentIntentInput(
            amount = amount,
            currency = currency,
            sessionId = sessionId,
            serviceType = serviceType,
            contact = contact,
            meta = meta,
            idempotencyKey = idempotencyKey,
            expiresInMs = expiresInMs,
        )
    }

    private fun parseMessage(raw: String?): BridgeMessage {
        if (raw.isNullOrBlank()) {
            error("empty payload")
        }
        val json = JSONObject(raw)
        val action = json.optString("action").takeIf { it.isNotBlank() }
            ?: error("action is required")
        val requestId = json.optString("requestId").takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()
        val payload = json.optJSONObject("payload")
        return BridgeMessage(requestId = requestId, action = action, payload = payload)
    }

    private fun emitSuccess(requestId: String?, action: String, data: JSONObject) {
        if (disposed) return
        val envelope = JSONObject().apply {
            put("type", "payment_response")
            put("ok", true)
            put("action", action)
            requestId?.let { put("requestId", it) }
            put("data", data)
        }
        dispatch(envelope)
    }

    private fun emitError(requestId: String?, action: String?, code: String, message: String) {
        if (disposed) return
        val envelope = JSONObject().apply {
            put("type", "payment_response")
            put("ok", false)
            action?.let { put("action", it) }
            requestId?.let { put("requestId", it) }
            put("error", JSONObject().apply {
                put("code", code)
                put("message", message)
            })
        }
        dispatch(envelope)
    }

    private fun dispatch(detail: JSONObject) {
        if (disposed) return
        val script = "window.dispatchEvent(new CustomEvent(\"${EVENT_NAME}\", { detail: ${detail.toString()} }));"
        webView.post {
            if (!disposed) {
                webView.evaluateJavascript(script, null)
            }
        }
    }

    private fun Throwable.codeFor(notFound: Boolean = false): String = when {
        notFound -> "not_found"
        this is IllegalArgumentException -> "invalid_argument"
        else -> "internal_error"
    }

    private fun PaymentIntent.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("gateway", gateway)
        put("amount", amount)
        put("currency", currency)
        put("status", status.toWire())
        put("environment", environment.toWire())
        sessionId?.let { put("sessionId", it) }
        serviceType?.let { put("serviceType", it) }
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        confirmedAt?.let { put("confirmedAt", it) }
        expiresAt?.let { put("expiresAt", it) }
        qrCode?.toJson()?.let { put("qrCode", it) }
        meta?.toJson()?.let { put("meta", it) }
        contact?.toJson()?.let { put("contact", it) }
    }

    private fun PaymentIntentQrCode.toJson(): JSONObject = JSONObject().apply {
        data?.let { put("data", it) }
        url?.let { put("url", it) }
    }

    private fun PaymentContact.toJson(): JSONObject = JSONObject().apply {
        email?.let { put("email", it) }
        phone?.let { put("phone", it) }
    }

    private fun PaymentSessionBreakdown.toJson(): JSONObject = JSONObject().apply {
        put("gross", gross)
        put("net", net)
        partner?.let { partnerShare ->
            put("partner", JSONObject().apply {
                put("name", partnerShare.name)
                put("sharePercent", partnerShare.sharePercent)
                put("shareAmount", partnerShare.shareAmount)
            })
        }
    }

    private fun PaymentSessionRecord.toJson(): JSONObject = JSONObject().apply {
        put("intent", intent.toJson())
        put("breakdown", breakdown.toJson())
        put("createdAt", createdAtIso)
        put("lastStatus", lastStatus.toWire())
    }

    private fun PaymentModuleMetricsSnapshot.toJson(): JSONObject = JSONObject().apply {
        put("environment", environment.toWire())
        put("gateway", gateway)
        put("capturedAt", capturedAtIso)
        put("store", JSONObject().apply {
            put("total", store.total)
            val byStatusJson = JSONObject()
            store.byStatus.forEach { (status, count) ->
                byStatusJson.put(status.toWire(), count)
            }
            put("byStatus", byStatusJson)
            put("pendingOlderThanMs", store.pendingOlderThanMs)
            put("pendingOlderThanCount", store.pendingOlderThanCount)
        })
    }

    private fun PaymentStatus.toWire(): String = name.lowercase(Locale.ROOT)

    private fun PaymentEnvironment.toWire(): String = name.lowercase(Locale.ROOT)

    private fun PaymentIntentMetadata.toJson(): JSONObject? = runCatching {
        JSONObject(this.toString())
    }.getOrNull()

    private fun JSONObject.getNumber(key: String): Number? {
        if (!has(key)) {
            return null
        }
        val value = opt(key)
        return when (value) {
            is Number -> value
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    private fun Any.toPaymentIntentMetadata(): JsonObject? = when (this) {
        is JSONObject -> runCatching { json.parseToJsonElement(this.toString()).jsonObject }.getOrNull()
        is String -> runCatching { json.parseToJsonElement(this).jsonObject }.getOrNull()
        else -> null
    }

    private data class BridgeMessage(
        val requestId: String,
        val action: String,
        val payload: JSONObject?,
    )

    companion object {
        const val INTERFACE_NAME: String = "KioskPayments"
        private const val EVENT_NAME: String = "kiosk-payment"
        private const val TAG = "PaymentsJsBridge"
    }
}
