package com.selfservice.feature.payments.gateway

import com.selfservice.feature.payments.PaymentEnvironment
import com.selfservice.feature.payments.PaymentGateway
import com.selfservice.feature.payments.PaymentGatewayCreateRequest
import com.selfservice.feature.payments.PaymentGatewayCreateResponse
import com.selfservice.feature.payments.PaymentGatewayStatusResponse
import com.selfservice.feature.payments.PaymentGatewayWebhookResult
import com.selfservice.feature.payments.PaymentIntentQrCode
import com.selfservice.feature.payments.PaymentStatus
import java.net.URLEncoder
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

class DevPaymentGateway(
    override val environment: PaymentEnvironment = PaymentEnvironment.DEV,
    private val autoConfirmDelayMs: Long = 2_500,
    private val manualMode: Boolean = false,
    private val clock: Clock = Clock.systemUTC(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : PaymentGateway {
    override val name: String = "dev-gateway"

    private val intents = ConcurrentHashMap<String, TrackedIntent>()

    override suspend fun createIntent(request: PaymentGatewayCreateRequest): PaymentGatewayCreateResponse {
        val intentId = "pi_dev_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val qrCode = buildQrCode(request)
        val tracked = TrackedIntent(
            id = intentId,
            status = PaymentStatus.PENDING,
            createdAt = Instant.now(clock),
            qrCode = qrCode,
        )
        intents[intentId] = tracked
        if (!manualMode && autoConfirmDelayMs > 0) {
            scope.launch {
                delay(autoConfirmDelayMs)
                transition(intentId, PaymentStatus.CONFIRMED)
            }
        }
        val expiresAt = request.expiresInMs?.let { Instant.now(clock).plusMillis(it).toString() }
        return PaymentGatewayCreateResponse(
            intentId = intentId,
            status = tracked.status,
            qrCode = qrCode,
            expiresAt = expiresAt,
            reference = request.idempotencyKey,
        )
    }

    override suspend fun getStatus(intentId: String): PaymentGatewayStatusResponse {
        val intent = requireIntent(intentId)
        return PaymentGatewayStatusResponse(status = intent.status)
    }

    override suspend fun confirm(intentId: String): PaymentGatewayStatusResponse {
        val status = transition(intentId, PaymentStatus.CONFIRMED)
        return PaymentGatewayStatusResponse(status = status)
    }

    override suspend fun cancel(intentId: String): PaymentGatewayStatusResponse {
        val status = transition(intentId, PaymentStatus.EXPIRED)
        return PaymentGatewayStatusResponse(status = status)
    }

    override suspend fun handleWebhook(
        payload: JsonElement?,
        headers: Map<String, List<String>>?,
        rawPayload: String?,
    ): PaymentGatewayWebhookResult? = null

    private fun transition(intentId: String, status: PaymentStatus): PaymentStatus {
        val intent = requireIntent(intentId)
        val next = intent.copy(status = status)
        intents[intentId] = next
        return status
    }

    private fun requireIntent(intentId: String): TrackedIntent = intents[intentId]
        ?: throw IllegalArgumentException("[DevPaymentGateway] intent $intentId not found")

    private fun buildQrCode(request: PaymentGatewayCreateRequest): PaymentIntentQrCode = PaymentIntentQrCode(
        data = "DEV:${request.amount}:${request.currency}:${request.idempotencyKey}",
        url = "https://payments.dev.local/intent/${encode(request.idempotencyKey)}",
    )

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private data class TrackedIntent(
        val id: String,
        val status: PaymentStatus,
        val createdAt: Instant,
        val qrCode: PaymentIntentQrCode?,
    )
}
