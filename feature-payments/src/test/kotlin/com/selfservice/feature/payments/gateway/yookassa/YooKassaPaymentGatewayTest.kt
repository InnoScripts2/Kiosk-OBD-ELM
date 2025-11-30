package com.selfservice.feature.payments.gateway.yookassa

import com.selfservice.feature.payments.PaymentContact
import com.selfservice.feature.payments.PaymentEnvironment
import com.selfservice.feature.payments.PaymentGatewayCreateRequest
import com.selfservice.feature.payments.PaymentStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.text.Charsets
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class YooKassaPaymentGatewayTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `createIntent posts payload with metadata and receipt`() = runBlocking {
        val client = TestHttpClient(successResponse())
        val gateway = createGateway(client)
        val response = gateway.createIntent(
            PaymentGatewayCreateRequest(
                idempotencyKey = "idem-1",
                amount = 500,
                currency = "rub",
                sessionId = "sess-1",
                serviceType = "obd",
                contact = PaymentContact(email = "user@example.org", phone = "+79998887766"),
                meta = JsonObject(mapOf("campaign" to JsonPrimitive("q1"))),
                expiresInMs = 60_000,
                description = "Diagnostics"
            )
        )
        assertEquals("pi_123", response.intentId)
        assertEquals(PaymentStatus.PENDING, response.status)
        val request = client.lastRequest
        assertNotNull(request)
        assertEquals("POST", request.method)
        assertEquals("https://api.test/payments", request.url)
        val body = request.body?.toString(Charsets.UTF_8) ?: error("body missing")
        val payload = json.parseToJsonElement(body).jsonObject
        val metadata = payload["metadata"]?.jsonObject
        assertEquals("sess-1", metadata?.get("session_id")?.jsonPrimitive?.content)
        assertEquals("obd", metadata?.get("service_type")?.jsonPrimitive?.content)
        assertEquals("q1", metadata?.get("meta_campaign")?.jsonPrimitive?.content)
        val receipt = payload["receipt"]?.jsonObject
        assertNotNull(receipt)
        val items = receipt["items"]
        assertNotNull(items)
        assertTrue(items.toString().contains("Diagnostics"))
    }

    @Test
    fun `getStatus decodes gateway response`() = runBlocking {
        val client = TestHttpClient(successResponse(status = "succeeded"))
        val gateway = createGateway(client)
        val status = gateway.getStatus("pi_123")
        assertEquals(PaymentStatus.CONFIRMED, status.status)
    }

    @Test
    fun `handleWebhook returns PaymentGatewayWebhookResult`() {
        val rawPayload = """
            {
              "event": "payment.succeeded",
              "object": {
                "id": "pi_123",
                "status": "succeeded",
                "amount": { "value": "500.00", "currency": "RUB" },
                "confirmation": { "type": "redirect" }
              }
            }
        """
        val payload = json.decodeFromString<JsonElement>(rawPayload)
        val gateway = createGateway(
            TestHttpClient(successResponse(status = "pending")),
            webhookSecret = WEBHOOK_SECRET,
        )
        val headers = mapOf(
            "x-yookassa-signature-sha256" to listOf("sha256=${sign(rawPayload, WEBHOOK_SECRET)}"),
        )
        val result = runBlocking { gateway.handleWebhook(payload, headers, rawPayload) }
        assertNotNull(result)
        assertEquals("pi_123", result.intentId)
        assertEquals(PaymentStatus.CONFIRMED, result.status)
    }

    @Test
    fun `webhook rejected when signature invalid`() = runBlocking {
        val rawPayload = """
            {
              "event": "payment.canceled",
              "object": {
                "id": "pi_456",
                "status": "canceled",
                "amount": { "value": "200.00", "currency": "RUB" }
              }
            }
        """
        val payload = json.decodeFromString<JsonElement>(rawPayload)
        val gateway = createGateway(TestHttpClient(successResponse()), webhookSecret = WEBHOOK_SECRET)
        val headers = mapOf("x-yookassa-signature-sha256" to listOf("sha256=deadbeef"))
        val result = gateway.handleWebhook(payload, headers, rawPayload)
        assertNull(result)
    }

    @Test
    fun `missing webhook payload returns null`() = runBlocking {
        val gateway = createGateway(TestHttpClient(successResponse()))
        val result = gateway.handleWebhook(null, null)
        assertNull(result)
    }

    private fun createGateway(client: TestHttpClient, webhookSecret: String? = null): YooKassaPaymentGateway = YooKassaPaymentGateway(
        environment = PaymentEnvironment.PROD,
        config = YooKassaGatewayConfig(
            shopId = "shop",
            secretKey = "secret",
            returnUrl = "https://example.org/return",
            apiBaseUrl = "https://api.test",
            webhookSecret = webhookSecret,
        ),
        httpClient = client,
        json = json,
    )

    private fun successResponse(status: String = "pending"): String = """
        {
          "id": "pi_123",
          "status": "$status",
          "amount": { "value": "500.00", "currency": "RUB" },
          "confirmation": {
            "type": "redirect",
            "confirmation_url": "https://pay.example/redirect",
            "confirmation_data": {
              "qr_code": "QRDATA",
              "qrcode_url": "https://pay.example/qr"
            }
          }
        }
    """

    private class TestHttpClient(var responseBody: String) : HttpClient {
        var lastRequest: HttpRequest? = null

        override suspend fun execute(request: HttpRequest): HttpResponse {
            lastRequest = request
            return HttpResponse(
                statusCode = 200,
                body = responseBody,
                headers = emptyMap(),
            )
        }
    }

    private fun sign(payload: String, secret: String, algorithm: String = "HmacSHA256"): String {
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), algorithm))
        val digest = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            ((byte.toInt() and 0xFF) + 0x100).toString(16).substring(1)
        }
    }

    companion object {
        private const val WEBHOOK_SECRET = "whsec_test"
    }
}
