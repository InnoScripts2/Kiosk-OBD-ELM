package com.selfservice.feature.payments

import java.io.File
import com.selfservice.feature.payments.store.PaymentIntentStore
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Supported payment environments. */
@Serializable
enum class PaymentEnvironment {
    DEV,
    QA,
    PROD
}

/** Normalized payment status values mirrored from the PSP. */
@Serializable
enum class PaymentStatus {
    @SerialName("created")
    CREATED,
    @SerialName("pending")
    PENDING,
    @SerialName("confirmed")
    CONFIRMED,
    @SerialName("manual")
    MANUAL,
    @SerialName("expired")
    EXPIRED,
    @SerialName("failed")
    FAILED
}

/** Optional contact data used for sending receipts. */
@Serializable
data class PaymentContact(
    val email: String? = null,
    val phone: String? = null,
)

/** QR-code payload that kiosk UI can render. */
@Serializable
data class PaymentIntentQrCode(
    val data: String? = null,
    val url: String? = null,
)

/** Generic metadata accompanying a payment intent. */
typealias PaymentIntentMetadata = JsonObject

/** Input payload for creating payment intents. */
data class CreatePaymentIntentInput(
    val amount: Long,
    val currency: String,
    val sessionId: String? = null,
    val serviceType: String? = null,
    val contact: PaymentContact? = null,
    val meta: PaymentIntentMetadata? = null,
    val idempotencyKey: String? = null,
    val expiresInMs: Long? = null,
)

/** Snapshot of the payment intent returned to callers. */
@Serializable
data class PaymentIntent(
    val id: String,
    val gateway: String,
    val amount: Long,
    val currency: String,
    val status: PaymentStatus,
    val environment: PaymentEnvironment,
    val sessionId: String? = null,
    val serviceType: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val confirmedAt: String? = null,
    val expiresAt: String? = null,
    val qrCode: PaymentIntentQrCode? = null,
    val meta: PaymentIntentMetadata? = null,
    val contact: PaymentContact? = null,
)

data class PaymentIntentStoreRecord(
    val id: String,
    val gateway: String,
    val amount: Long,
    val currency: String,
    val status: PaymentStatus,
    val environment: PaymentEnvironment,
    val sessionId: String? = null,
    val serviceType: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val confirmedAt: String? = null,
    val expiresAt: String? = null,
    val qrCode: PaymentIntentQrCode? = null,
    val meta: PaymentIntentMetadata? = null,
    val contact: PaymentContact? = null,
    val idempotencyKey: String,
    val metaHash: String? = null,
    val contactHash: String? = null,
)

data class PaymentSessionBreakdown(
    val gross: Long,
    val net: Long,
    val partner: PartnerShare? = null,
) {
    data class PartnerShare(
        val name: String,
        val sharePercent: Double,
        val shareAmount: Long,
    )
}

data class PaymentSessionRecord(
    val intent: PaymentIntent,
    val breakdown: PaymentSessionBreakdown,
    val createdAtIso: String,
    val lastStatus: PaymentStatus,
)

data class CreatePaymentIntentResult(
    val intent: PaymentIntent,
    val breakdown: PaymentSessionBreakdown,
)

/** File/E2EE configuration for the intent store. */
data class PaymentIntentStoreOptions(
    val environment: PaymentEnvironment,
    val file: File,
    val encryptionKey: String? = null,
)

data class PaymentIntentStorePrunePolicy(
    val minAgeMillis: Long = DEFAULT_MIN_AGE_MILLIS,
    val finalStatuses: Set<PaymentStatus> = DEFAULT_FINAL_STATUSES,
    val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    val minEntriesToRetain: Int = DEFAULT_MIN_ENTRIES_TO_RETAIN,
) {
    companion object {
        const val DEFAULT_MIN_AGE_MILLIS: Long = 7L * 24L * 60L * 60L * 1000L
        val DEFAULT_FINAL_STATUSES: Set<PaymentStatus> = setOf(
            PaymentStatus.CONFIRMED,
            PaymentStatus.MANUAL,
            PaymentStatus.EXPIRED,
            PaymentStatus.FAILED,
        )
        const val DEFAULT_MAX_ENTRIES: Int = 2_000
        const val DEFAULT_MIN_ENTRIES_TO_RETAIN: Int = 128
    }
}

data class UpdatePaymentIntentOptions(
    val status: PaymentStatus? = null,
    val confirmedAt: String? = null,
    val expiresAt: String? = null,
    val qrCode: PaymentIntentQrCode? = null,
    val meta: PaymentIntentMetadata? = null,
    val contact: PaymentContact? = null,
)

data class PaymentIntentStoreMetrics(
    val total: Int,
    val byStatus: Map<PaymentStatus, Int>,
    val pendingOlderThanMs: Long,
    val pendingOlderThanCount: Int,
)

data class PaymentModuleMetricsSnapshot(
    val environment: PaymentEnvironment,
    val gateway: String,
    val store: PaymentIntentStoreMetrics,
    val capturedAtIso: String,
)

data class ManualConfirmationInput(
    val id: String,
    val operatorId: String,
    val note: String? = null,
    val meta: PaymentIntentMetadata? = null,
)

interface PaymentLogger {
    fun debug(message: String, context: Map<String, Any?>? = null) = Unit
    fun info(message: String, context: Map<String, Any?>? = null) = Unit
    fun warn(message: String, context: Map<String, Any?>? = null) = Unit
    fun error(message: String, context: Map<String, Any?>? = null) = Unit
}

/**
 * Minimal контракт для компонентов, которым достаточно читать статус Intent'а.
 * Используется в DEV-симуляциях и unit-тестах, чтобы не тянуть полноценный PaymentModule.
 */
fun interface PaymentStatusProvider {
    suspend fun getStatus(intentId: String): PaymentStatus?
}

data class PaymentModuleOptions(
    val environment: PaymentEnvironment,
    val logger: PaymentLogger? = null,
    val gateway: PaymentGateway? = null,
    val gatewayFactory: PaymentGatewayFactory? = null,
    val store: PaymentIntentStore? = null,
    val storeOptions: PaymentIntentStoreOptions? = null,
    val revSharePercent: Double? = null,
    val auditSink: PaymentAuditSink? = null,
    val storePrunePolicy: PaymentIntentStorePrunePolicy? = null,
)

/** Webhook handling result used by PSP adapters. */
data class PaymentWebhookHandlingResult(
    val intent: PaymentIntent,
    val updated: Boolean,
    val previousStatus: PaymentStatus,
)

/** Gateway contract mirrors PSP REST API behaviour. */
interface PaymentGateway {
    val name: String
    val environment: PaymentEnvironment

    suspend fun createIntent(request: PaymentGatewayCreateRequest): PaymentGatewayCreateResponse
    suspend fun getStatus(intentId: String): PaymentGatewayStatusResponse
    suspend fun confirm(intentId: String): PaymentGatewayStatusResponse =
        throw UnsupportedOperationException("Gateway $name does not support confirm()")
    suspend fun cancel(intentId: String): PaymentGatewayStatusResponse =
        throw UnsupportedOperationException("Gateway $name does not support cancel()")
    suspend fun handleWebhook(
        payload: JsonElement?,
        headers: Map<String, List<String>>? = null,
        rawPayload: String? = null,
    ): PaymentGatewayWebhookResult? = null
}

data class PaymentGatewayCreateRequest(
    val idempotencyKey: String,
    val amount: Long,
    val currency: String,
    val sessionId: String? = null,
    val serviceType: String? = null,
    val contact: PaymentContact? = null,
    val meta: PaymentIntentMetadata? = null,
    val expiresInMs: Long? = null,
    val description: String? = null,
)

data class PaymentGatewayCreateResponse(
    val intentId: String,
    val status: PaymentStatus,
    val qrCode: PaymentIntentQrCode? = null,
    val expiresAt: String? = null,
    val reference: String? = null,
    val rawPayload: JsonElement? = null,
)

data class PaymentGatewayStatusResponse(
    val status: PaymentStatus,
    val reference: String? = null,
    val rawPayload: JsonElement? = null,
)

data class PaymentGatewayWebhookResult(
    val intentId: String,
    val status: PaymentStatus,
    val reference: String? = null,
    val rawPayload: JsonElement? = null,
)

data class PaymentGatewayFactoryOptions(
    val environment: PaymentEnvironment,
    val logger: PaymentLogger? = null,
)

typealias PaymentGatewayFactory = (PaymentGatewayFactoryOptions) -> PaymentGateway

