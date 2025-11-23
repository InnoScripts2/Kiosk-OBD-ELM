package com.selfservice.feature.payments.gateway.yookassa

import com.selfservice.feature.payments.PaymentContact
import com.selfservice.feature.payments.PaymentEnvironment
import com.selfservice.feature.payments.PaymentGateway
import com.selfservice.feature.payments.PaymentGatewayCreateRequest
import com.selfservice.feature.payments.PaymentGatewayCreateResponse
import com.selfservice.feature.payments.PaymentGatewayStatusResponse
import com.selfservice.feature.payments.PaymentGatewayWebhookResult
import com.selfservice.feature.payments.PaymentIntentQrCode
import com.selfservice.feature.payments.PaymentLogger
import com.selfservice.feature.payments.PaymentStatus
import com.selfservice.feature.payments.PaymentStatus.CONFIRMED
import com.selfservice.feature.payments.PaymentStatus.EXPIRED
import com.selfservice.feature.payments.PaymentStatus.FAILED
import com.selfservice.feature.payments.PaymentStatus.PENDING
import com.selfservice.feature.payments.gateway.DevPaymentGateway
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Payment gateway implementation backed by YooKassa REST API.
 */
class YooKassaPaymentGateway(
    override val environment: PaymentEnvironment,
    private val config: YooKassaGatewayConfig,
    private val logger: PaymentLogger? = null,
    private val httpClient: HttpClient = HttpUrlConnectionClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val clock: Clock = Clock.systemUTC(),
) : PaymentGateway {

    override val name: String = "yookassa"

    override suspend fun createIntent(request: PaymentGatewayCreateRequest): PaymentGatewayCreateResponse {
        val payload = YooKassaCreatePaymentRequest(
            amount = request.amount.toYooAmount(request.currency),
            capture = config.capture,
            description = sanitizeDescription(request.description, request.serviceType, request.sessionId),
            confirmation = YooKassaConfirmationRequest(
                type = config.confirmationType,
                returnUrl = config.returnUrl,
            ),
            paymentMethodData = config.paymentMethodType?.let { YooKassaPaymentMethodData(type = it) },
            metadata = buildMetadata(request),
            receipt = buildReceipt(request),
            expiresAt = request.expiresInMs?.let { Instant.now(clock).plusMillis(it).toString() },
        )
        val response = call(
            method = "POST",
            path = "/payments",
            idempotencyKey = request.idempotencyKey,
            body = json.encodeToString(payload),
        )
        val parsed = parsePayment(response)
        return PaymentGatewayCreateResponse(
            intentId = parsed.id,
            status = parsed.status.toPaymentStatus(),
            qrCode = parsed.confirmation.toQrCode(),
            expiresAt = parsed.expiresAt,
            reference = parsed.id,
            rawPayload = response.bodyJson,
        )
    }

    override suspend fun getStatus(intentId: String): PaymentGatewayStatusResponse {
        val response = call(method = "GET", path = "/payments/$intentId")
        val parsed = parsePayment(response)
        return PaymentGatewayStatusResponse(
            status = parsed.status.toPaymentStatus(),
            reference = parsed.id,
            rawPayload = response.bodyJson,
        )
    }

    override suspend fun confirm(intentId: String): PaymentGatewayStatusResponse {
        val response = call(method = "POST", path = "/payments/$intentId/capture", body = "{}")
        val parsed = parsePayment(response)
        return PaymentGatewayStatusResponse(
            status = parsed.status.toPaymentStatus(),
            reference = parsed.id,
            rawPayload = response.bodyJson,
        )
    }

    override suspend fun handleWebhook(
        payload: JsonElement?,
        headers: Map<String, List<String>>?,
        rawPayload: String?,
    ): PaymentGatewayWebhookResult? {
        val element = payload ?: return null
        if (!verifyWebhookSignature(rawPayload, headers)) {
            return null
        }
        val webhook = runCatching { json.decodeFromJsonElement(YooKassaWebhookPayload.serializer(), element) }
            .getOrNull() ?: return null
        val parsed = webhook.data
        return PaymentGatewayWebhookResult(
            intentId = parsed.id,
            status = parsed.status.toPaymentStatus(),
            reference = parsed.id,
            rawPayload = element,
        )
    }

    private fun verifyWebhookSignature(
        rawPayload: String?,
        headers: Map<String, List<String>>?,
    ): Boolean {
        val secret = config.webhookSecret ?: return true
        if (rawPayload.isNullOrBlank()) {
            logger?.warn(
                "[yookassa] webhook rejected: missing raw payload for signature verification",
            )
            return false
        }
        val headerValue = headers.findSignatureHeader(config.webhookSignatureHeaders)
            ?: run {
                logger?.warn("[yookassa] webhook rejected: signature header absent")
                return false
            }
        val parsed = parseSignatureHeader(headerValue)
        val algorithm = parsed.algorithm ?: config.webhookSignatureAlgorithm
        val signatureBytes = computeHmac(rawPayload, secret, algorithm)
            ?: run {
                logger?.warn(
                    "[yookassa] webhook rejected: unsupported HMAC algorithm",
                    mapOf("algorithm" to algorithm),
                )
                return false
            }
        val provided = parsed.value.trim()
        val computedHex = signatureBytes.toHexLower()
        val computedBase64 = Base64.getEncoder().encodeToString(signatureBytes)
        val matches = constantTimeEquals(provided.lowercase(Locale.ROOT), computedHex)
            || constantTimeEquals(provided, computedBase64)
        if (!matches) {
            logger?.warn(
                "[yookassa] webhook rejected: signature mismatch",
                mapOf("algorithm" to algorithm),
            )
        }
        return matches
    }

    private suspend fun call(
        method: String,
        path: String,
        idempotencyKey: String? = null,
        body: String? = null,
    ): GatewayHttpResponse {
        val request = HttpRequest(
            method = method,
            url = "${config.apiBaseUrl.trimEnd('/')}$path",
            headers = buildHeaders(idempotencyKey),
            body = body?.toByteArray(Charsets.UTF_8),
            timeoutMillis = config.timeoutMillis,
        )
        return try {
            val response = httpClient.execute(request)
            if (response.statusCode !in 200..299) {
                val message = response.parseErrorMessage(json)
                logger?.warn(
                    "[yookassa] HTTP ${response.statusCode} ${method.uppercase(Locale.ROOT)} $path failed",
                    mapOf("body" to message),
                )
                throw YooKassaGatewayException(message ?: "YooKassa request failed", response.statusCode)
            }
            GatewayHttpResponse(response.body, response.body.asJsonElement(json))
        } catch (error: IOException) {
            logger?.error("[yookassa] network failure", mapOf("error" to (error.message ?: "io")))
            throw YooKassaGatewayException("Network error: ${error.message ?: error::class.java.simpleName}")
        }
    }

    private fun buildHeaders(idempotencyKey: String?): Map<String, String> {
        val headers = mutableMapOf(
            "Authorization" to config.authHeader,
            "Content-Type" to "application/json",
            "User-Agent" to config.userAgent,
        )
        if (!idempotencyKey.isNullOrBlank()) {
            headers["Idempotence-Key"] = idempotencyKey
        }
        return headers
    }

    private fun parsePayment(response: GatewayHttpResponse): YooKassaPaymentResponse {
        val jsonElement = response.bodyJson
            ?: throw YooKassaGatewayException("Empty response payload")
        return json.decodeFromJsonElement(YooKassaPaymentResponse.serializer(), jsonElement)
    }

    private fun buildMetadata(request: PaymentGatewayCreateRequest): Map<String, String>? {
        val metadata = mutableMapOf<String, String>()
        request.sessionId?.takeIf { it.isNotBlank() }?.let { metadata["session_id"] = it }
        request.serviceType?.takeIf { it.isNotBlank() }?.let { metadata["service_type"] = it }
        request.contact?.email?.takeIf { it.isNotBlank() }?.let { metadata["contact_email"] = it }
        request.contact?.phone?.takeIf { it.isNotBlank() }?.let { metadata["contact_phone"] = it }
        request.meta?.let { meta ->
            meta.forEach { (key, value) ->
                val primitive = value as? JsonPrimitive
                val serialized = primitive?.contentOrNull ?: primitive?.content ?: value.toString()
                metadata["meta_$key"] = serialized
            }
        }
        return metadata.takeIf { it.isNotEmpty() }
    }

    private fun buildReceipt(request: PaymentGatewayCreateRequest): YooKassaReceipt? {
        if (!config.issueReceipt) return null
        val description = config.receiptItemDescription
            ?: sanitizeDescription(request.description, request.serviceType, request.sessionId)
            ?: "Diagnostics session"
        val item = YooKassaReceiptItem(
            description = description.take(128),
            quantity = config.receiptQuantity,
            vatCode = config.receiptVatCode,
            amount = request.amount.toYooAmount(request.currency),
        )
        val customer = request.contact?.toReceiptCustomer()
        if (customer == null && config.requireCustomerForReceipt) {
            return null
        }
        return YooKassaReceipt(
            customer = customer,
            items = listOf(item),
            taxSystemCode = config.taxSystemCode,
        )
    }

    private fun PaymentContact.toReceiptCustomer(): YooKassaReceiptCustomer? {
        val phoneNormalized = phone?.trim()?.takeIf { it.isNotEmpty() }
        val emailNormalized = email?.trim()?.takeIf { it.isNotEmpty() }
        if (phoneNormalized == null && emailNormalized == null) {
            return null
        }
        return YooKassaReceiptCustomer(phone = phoneNormalized, email = emailNormalized)
    }

    private fun sanitizeDescription(description: String?, serviceType: String?, sessionId: String?): String? {
        val base = description?.takeIf { it.isNotBlank() }
            ?: serviceType?.let { "${it.uppercase(Locale.ROOT)} session" }
            ?: sessionId?.let { "Session $it" }
        return base?.take(128)
    }

    private fun Long.toYooAmount(currency: String): YooKassaAmount {
        val formatted = BigDecimal.valueOf(this)
            .setScale(2, RoundingMode.HALF_UP)
            .toPlainString()
        return YooKassaAmount(value = formatted, currency = currency.uppercase(Locale.ROOT))
    }

    private fun YooKassaPaymentStatus.toPaymentStatus(): PaymentStatus = when (this) {
        YooKassaPaymentStatus.SUCCEEDED -> CONFIRMED
        YooKassaPaymentStatus.CANCELED -> FAILED
        YooKassaPaymentStatus.EXPIRED -> EXPIRED
        YooKassaPaymentStatus.WAITING_FOR_CAPTURE,
        YooKassaPaymentStatus.PENDING,
        YooKassaPaymentStatus.WAITING_FOR_PAYMENT -> PENDING
    }

    private fun YooKassaConfirmationResponse?.toQrCode(): PaymentIntentQrCode? {
        if (this == null) return null
        val url = confirmationData?.qrUrl ?: confirmationUrl
        val qrData = confirmationData?.qrCode
        if (url.isNullOrBlank() && qrData.isNullOrBlank()) {
            return null
        }
        return PaymentIntentQrCode(data = qrData, url = url)
    }

    data class GatewayHttpResponse(
        val body: String?,
        val bodyJson: JsonElement?,
    )

    class YooKassaGatewayException(message: String, val statusCode: Int? = null) : RuntimeException(message)

    companion object {
        fun fallback(environment: PaymentEnvironment): PaymentGateway =
            DevPaymentGateway(environment = environment, manualMode = environment != PaymentEnvironment.DEV)
    }
}

/** Configuration for YooKassa gateway credentials and behaviour. */
data class YooKassaGatewayConfig(
    val shopId: String,
    val secretKey: String,
    val returnUrl: String,
    val apiBaseUrl: String = DEFAULT_API_URL,
    val capture: Boolean = true,
    val paymentMethodType: String? = "sbp",
    val confirmationType: String = "redirect",
    val issueReceipt: Boolean = true,
    val receiptItemDescription: String? = null,
    val receiptVatCode: Int? = null,
    val taxSystemCode: Int? = null,
    val receiptQuantity: String = "1.0",
    val requireCustomerForReceipt: Boolean = false,
    val timeoutMillis: Int = 15_000,
    val userAgent: String = DEFAULT_USER_AGENT,
    val webhookSecret: String? = null,
    val webhookSignatureHeaders: List<String> = DEFAULT_WEBHOOK_SIGNATURE_HEADERS,
    val webhookSignatureAlgorithm: String = "HmacSHA256",
) {
    val authHeader: String = "Basic ${Base64.getEncoder().encodeToString("$shopId:$secretKey".toByteArray(Charsets.UTF_8))}"

    companion object {
        const val DEFAULT_API_URL = "https://api.yookassa.ru/v3"
        const val DEFAULT_USER_AGENT = "SelfServiceKiosk/1.0"
        val DEFAULT_WEBHOOK_SIGNATURE_HEADERS: List<String> = listOf(
            "x-yookassa-signature-sha256",
            "x-yookassa-signature",
            "x-yandex-signature",
            "sha1_hash",
        )
    }
}

/** Simple HTTP request abstraction for gateway clients. */
interface HttpClient {
    @Throws(IOException::class)
    suspend fun execute(request: HttpRequest): HttpResponse
}

data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val timeoutMillis: Int = 15_000,
)

data class HttpResponse(
    val statusCode: Int,
    val body: String?,
    val headers: Map<String, List<String>> = emptyMap(),
)

class HttpUrlConnectionClient(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HttpClient {
    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(dispatcher) {
        val connection = (URL(request.url).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = request.method.uppercase(Locale.ROOT)
            connection.connectTimeout = request.timeoutMillis
            connection.readTimeout = request.timeoutMillis
            connection.doInput = true
            request.headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            val payload = request.body
            if (payload != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(payload) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bodyBytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            val body = bodyBytes.toString(Charsets.UTF_8)
            val headers = connection.headerFields
                .filterKeys { it != null }
                .mapValues { entry -> entry.value.filterNotNull() }
            HttpResponse(statusCode = status, body = body, headers = headers)
        } finally {
            connection.disconnect()
        }
    }
}

@Serializable
private data class YooKassaCreatePaymentRequest(
    val amount: YooKassaAmount,
    val capture: Boolean,
    val description: String? = null,
    val confirmation: YooKassaConfirmationRequest,
    @SerialName("payment_method_data")
    val paymentMethodData: YooKassaPaymentMethodData? = null,
    val metadata: Map<String, String>? = null,
    val receipt: YooKassaReceipt? = null,
    @SerialName("expires_at")
    val expiresAt: String? = null,
)

@Serializable
data class YooKassaAmount(
    val value: String,
    val currency: String,
)

@Serializable
data class YooKassaConfirmationRequest(
    val type: String,
    @SerialName("return_url")
    val returnUrl: String,
)

@Serializable
data class YooKassaPaymentMethodData(
    val type: String,
)

@Serializable
data class YooKassaReceipt(
    val customer: YooKassaReceiptCustomer? = null,
    val items: List<YooKassaReceiptItem>,
    @SerialName("tax_system_code")
    val taxSystemCode: Int? = null,
)

@Serializable
data class YooKassaReceiptCustomer(
    val phone: String? = null,
    val email: String? = null,
)

@Serializable
data class YooKassaReceiptItem(
    val description: String,
    val quantity: String,
    val amount: YooKassaAmount,
    @SerialName("vat_code")
    val vatCode: Int? = null,
)

@Serializable
private data class YooKassaPaymentResponse(
    val id: String,
    val status: YooKassaPaymentStatus,
    val amount: YooKassaAmount,
    val description: String? = null,
    @SerialName("expires_at")
    val expiresAt: String? = null,
    val confirmation: YooKassaConfirmationResponse? = null,
)

@Serializable
enum class YooKassaPaymentStatus {
    @SerialName("pending")
    PENDING,
    @SerialName("waiting_for_capture")
    WAITING_FOR_CAPTURE,
    @SerialName("waiting_for_payment")
    WAITING_FOR_PAYMENT,
    @SerialName("succeeded")
    SUCCEEDED,
    @SerialName("canceled")
    CANCELED,
    @SerialName("expired")
    EXPIRED,
}

@Serializable
data class YooKassaConfirmationResponse(
    val type: String,
    @SerialName("confirmation_url")
    val confirmationUrl: String? = null,
    @SerialName("confirmation_data")
    val confirmationData: YooKassaConfirmationData? = null,
)

@Serializable
data class YooKassaConfirmationData(
    @SerialName("qrcode_url")
    val qrUrl: String? = null,
    @SerialName("qr_code")
    val qrCode: String? = null,
)

@Serializable
private data class YooKassaWebhookPayload(
    val event: String,
    @SerialName("object")
    val data: YooKassaPaymentResponse,
)

private fun HttpResponse.parseErrorMessage(json: Json): String? {
    val element = body.asJsonElement(json) as? JsonObject ?: return body
    val description = element["description"]?.jsonPrimitive?.contentOrNull
    val type = element["type"]?.jsonPrimitive?.contentOrNull
    return description ?: type
}

private fun String?.asJsonElement(parser: Json): JsonElement? =
    this?.takeIf { it.isNotBlank() }?.let { runCatching { parser.parseToJsonElement(it) }.getOrNull() }

private fun Map<String, List<String>>?.findSignatureHeader(candidates: List<String>): String? {
    if (this.isNullOrEmpty()) {
        return null
    }
    val normalized = this.entries.mapNotNull { (rawKey, values) ->
        val key = rawKey.trim().lowercase(Locale.ROOT)
        if (key.isEmpty()) {
            null
        } else {
            key to values
        }
    }.toMap()
    candidates.forEach { candidate ->
        val headerValues = normalized[candidate.lowercase(Locale.ROOT)] ?: return@forEach
        val value = headerValues.firstOrNull { it.isNotBlank() }?.trim()
        if (!value.isNullOrEmpty()) {
            return value
        }
    }
    return null
}

private fun parseSignatureHeader(value: String): ParsedSignature {
    val trimmed = value.trim()
    val eqIndex = trimmed.indexOf('=')
    if (eqIndex in 1 until trimmed.lastIndex) {
        val prefix = trimmed.substring(0, eqIndex).trim().lowercase(Locale.ROOT)
        val signature = trimmed.substring(eqIndex + 1).trim()
        if (signature.isNotEmpty()) {
            val algorithm = when (prefix) {
                "sha1" -> "HmacSHA1"
                "sha256" -> "HmacSHA256"
                "sha512" -> "HmacSHA512"
                else -> null
            }
            return ParsedSignature(algorithm = algorithm, value = signature)
        }
    }
    return ParsedSignature(algorithm = null, value = trimmed)
}

private fun computeHmac(payload: String, secret: String, algorithm: String): ByteArray? = runCatching {
    val mac = Mac.getInstance(algorithm)
    val keySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), algorithm)
    mac.init(keySpec)
    mac.doFinal(payload.toByteArray(Charsets.UTF_8))
}.getOrNull()

private fun ByteArray.toHexLower(): String = joinToString(separator = "") { byte ->
    ((byte.toInt() and 0xFF) + 0x100).toString(16).substring(1)
}

private fun constantTimeEquals(left: String, right: String): Boolean {
    if (left.length != right.length) {
        return false
    }
    var diff = 0
    for (index in left.indices) {
        diff = diff or (left[index].code xor right[index].code)
    }
    return diff == 0
}

private data class ParsedSignature(
    val algorithm: String?,
    val value: String,
)
