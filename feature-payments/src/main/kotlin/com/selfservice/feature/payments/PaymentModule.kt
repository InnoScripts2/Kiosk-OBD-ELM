package com.selfservice.feature.payments

import com.selfservice.feature.payments.PaymentSessionBreakdown.PartnerShare
import com.selfservice.feature.payments.PaymentGatewayStatusResponse
import com.selfservice.feature.payments.gateway.DevPaymentGateway
import com.selfservice.feature.payments.store.PaymentIntentStore
import com.selfservice.feature.payments.store.PaymentIntentStore.CreateStoredIntentInput
import java.time.Clock
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private val DEFAULT_REVSHARE = System.getenv("REVSHARE_PCT")?.toDoubleOrNull() ?: 0.04

class PaymentModule(
    options: PaymentModuleOptions,
    private val clock: Clock = Clock.systemUTC(),
) : PaymentStatusProvider {
    private val environment: PaymentEnvironment = options.environment
    private val logger: PaymentLogger = options.logger ?: object : PaymentLogger {}
    private val revSharePercent: Double = clampPercent(options.revSharePercent ?: DEFAULT_REVSHARE)
    private val store: PaymentIntentStore = options.store ?: run {
        val storeOptions = options.storeOptions
            ?: error("PaymentIntentStoreOptions required when store is not provided")
        PaymentIntentStore(storeOptions)
    }
    private val audit: PaymentAuditSink = options.auditSink ?: PaymentAuditSink.NO_OP
    private val prunePolicy: PaymentIntentStorePrunePolicy? = options.storePrunePolicy ?: PaymentIntentStorePrunePolicy()
    private var createCountSincePrune: Int = 0
    private val gateway: PaymentGateway = resolveGateway(
        provided = options.gateway,
        factory = options.gatewayFactory,
        logger = logger,
    )

    suspend fun createIntent(input: CreatePaymentIntentInput): CreatePaymentIntentResult {
        val normalized = normalizeCreateInput(input)
        val idempotencyKey = normalized.idempotencyKey ?: buildIdempotencyKey(normalized)
        store.getByIdempotencyKey(idempotencyKey)?.let { existing ->
            logger.debug("[payments] using existing intent due to idempotency", mapOf("id" to existing.id))
            val session = buildSessionRecord(existing)
            return CreatePaymentIntentResult(intent = session.intent, breakdown = session.breakdown)
        }

        val gatewayResponse = gateway.createIntent(
            PaymentGatewayCreateRequest(
                idempotencyKey = idempotencyKey,
                amount = normalized.amount,
                currency = normalized.currency,
                sessionId = normalized.sessionId,
                serviceType = normalized.serviceType,
                contact = normalized.contact,
                meta = normalized.meta,
                expiresInMs = normalized.expiresInMs,
                description = normalized.sessionId?.let { "Session $it payment" },
            ),
        )

        val stored = store.create(
            CreateStoredIntentInput(
                id = gatewayResponse.intentId,
                gateway = gateway.name,
                amount = normalized.amount,
                currency = normalized.currency,
                status = gatewayResponse.status,
                sessionId = normalized.sessionId,
                serviceType = normalized.serviceType,
                qrCode = gatewayResponse.qrCode,
                meta = normalized.meta,
                contact = normalized.contact,
                idempotencyKey = idempotencyKey,
                expiresAt = gatewayResponse.expiresAt,
            ),
        )
        val session = buildSessionRecord(stored)
        val auditDetails = buildJsonObject {
            put("idempotencyKey", JsonPrimitive(idempotencyKey))
            session.intent.qrCode?.data?.let { put("qrData", JsonPrimitive(it)) }
            session.intent.qrCode?.url?.let { put("qrUrl", JsonPrimitive(it)) }
            session.breakdown.partner?.let { partner ->
                put("partnerName", JsonPrimitive(partner.name))
                put("partnerSharePercent", JsonPrimitive(partner.sharePercent))
                put("partnerShareAmount", JsonPrimitive(partner.shareAmount))
            }
        }
        recordAudit(
            type = "intent_created",
            intent = session.intent,
            status = session.intent.status,
            details = auditDetails,
        )
        maybePruneStore()
        return CreatePaymentIntentResult(intent = session.intent, breakdown = session.breakdown)
    }

    suspend fun getIntent(id: String): PaymentSessionRecord? = store.get(id)?.let(::buildSessionRecord)

    override suspend fun getStatus(id: String): PaymentStatus? {
        val record = store.get(id) ?: return null
        var response: PaymentGatewayStatusResponse? = null
        val statusResponse = runCatching {
            gateway.getStatus(id).also { response = it }
        }.onFailure { error ->
            logger.warn("[payments] gateway status check failed, fallback to cached status", mapOf(
                "intentId" to id,
                "error" to (error.message ?: error::class.java.simpleName),
            ))
        }.getOrNull()
        val updatedRecord = if (statusResponse != null && statusResponse.status != record.status) {
            store.update(id, UpdatePaymentIntentOptions(
                status = statusResponse.status,
                confirmedAt = if (statusResponse.status == PaymentStatus.CONFIRMED) Instant.now(clock).toString() else null,
            )) ?: record
        } else {
            record
        }
        val session = buildSessionRecord(updatedRecord)
        val finalStatus = statusResponse?.status ?: updatedRecord.status
        val details = buildJsonObject {
            put("source", JsonPrimitive(if (statusResponse != null) "gateway" else "store"))
            response?.reference?.let { put("reference", JsonPrimitive(it)) }
            response?.rawPayload?.let { put("rawPayload", it) }
            put("statusChanged", JsonPrimitive(updatedRecord !== record))
        }
        recordAudit(
            type = "status_checked",
            intent = session.intent,
            status = finalStatus,
            details = details,
        )
        return finalStatus
    }

    suspend fun confirmDev(id: String): PaymentSessionRecord? {
        require(environment == PaymentEnvironment.DEV) {
            "confirmDev is only available in DEV environment"
        }
        val record = store.get(id) ?: return null
        val nextStatus = try {
            gateway.confirm(id).status
        } catch (_: UnsupportedOperationException) {
            PaymentStatus.CONFIRMED
        }
        val updated = store.update(id, UpdatePaymentIntentOptions(
            status = nextStatus,
            confirmedAt = Instant.now(clock).toString(),
        )) ?: return null
        val session = buildSessionRecord(updated)
        val details = buildJsonObject {
            put("mode", JsonPrimitive("dev"))
        }
        recordAudit(
            type = "intent_confirmed_dev",
            intent = session.intent,
            status = session.intent.status,
            details = details,
        )
        return session
    }

    suspend fun manualConfirm(input: ManualConfirmationInput): PaymentSessionRecord? {
        require(input.operatorId.isNotBlank()) { "operatorId is required for manual confirmation" }
        val record = store.get(input.id) ?: return null
        val timestamp = Instant.now(clock).toString()
        val mergedMeta = mergeManualConfirmation(record.meta, input.meta, input.operatorId, input.note, timestamp)
        val updated = store.update(
            input.id,
            UpdatePaymentIntentOptions(
                status = PaymentStatus.MANUAL,
                confirmedAt = timestamp,
                meta = mergedMeta,
            ),
        ) ?: return null
        val session = buildSessionRecord(updated)
        val details = buildJsonObject {
            input.note?.takeIf { it.isNotBlank() }?.let { put("note", JsonPrimitive(it)) }
            put("metaKeys", JsonPrimitive(session.intent.meta?.size ?: 0))
        }
        recordAudit(
            type = "intent_manual_confirmed",
            intent = session.intent,
            status = session.intent.status,
            operatorId = input.operatorId,
            details = details,
        )
        return session
    }

    suspend fun handleWebhook(
        payload: JsonElement?,
        headers: Map<String, List<String>>? = null,
        rawPayload: String? = null,
    ): PaymentWebhookHandlingResult? {
        val result = gateway.handleWebhook(payload, headers, rawPayload) ?: return null
        val before = store.get(result.intentId)
        val updated = store.update(
            result.intentId,
            UpdatePaymentIntentOptions(
                status = result.status,
                confirmedAt = if (result.status == PaymentStatus.CONFIRMED) Instant.now(clock).toString() else before?.confirmedAt,
            ),
        ) ?: return null
        val session = buildSessionRecord(updated)
        val requestId = extractWebhookRequestId(headers)
        val webhookResult = PaymentWebhookHandlingResult(
            intent = session.intent,
            updated = before?.status != updated.status,
            previousStatus = before?.status ?: PaymentStatus.CREATED,
        )
        val details = buildJsonObject {
            result.reference?.let { put("reference", JsonPrimitive(it)) }
            result.rawPayload?.let { put("rawPayload", it) }
            put("updated", JsonPrimitive(webhookResult.updated))
        }
        recordAudit(
            type = "webhook_handled",
            intent = session.intent,
            status = result.status,
            requestId = requestId,
            details = details,
        )
        return webhookResult
    }

    fun getMetricsSnapshot(): PaymentModuleMetricsSnapshot = PaymentModuleMetricsSnapshot(
        environment = environment,
        gateway = gateway.name,
        store = store.metrics(),
        capturedAtIso = Instant.now(clock).toString(),
    )

    fun getMetricsEvents(): List<PaymentModuleMetricsSnapshot> = listOf(getMetricsSnapshot())

    private fun resolveGateway(
        provided: PaymentGateway?,
        factory: PaymentGatewayFactory?,
        logger: PaymentLogger,
    ): PaymentGateway {
        if (provided != null) {
            return provided
        }
        if (factory != null) {
            return factory(PaymentGatewayFactoryOptions(environment, logger))
        }
        return DevPaymentGateway(environment = environment, manualMode = environment != PaymentEnvironment.DEV)
    }

    private fun normalizeCreateInput(input: CreatePaymentIntentInput): NormalizedCreateInput {
        require(input.amount > 0) { "amount must be positive" }
        val currency = (input.currency.ifBlank { "RUB" }).trim().uppercase(Locale.ROOT)
        val sessionId = sanitizeIdentifier(input.sessionId)
        val serviceType = sanitizeIdentifier(input.serviceType)
        val contact = sanitizeContact(input.contact)
        val idempotencyKey = input.idempotencyKey?.trim()?.takeIf { it.isNotEmpty() }
        return NormalizedCreateInput(
            amount = input.amount,
            currency = currency,
            sessionId = sessionId,
            serviceType = serviceType,
            contact = contact,
            meta = input.meta,
            idempotencyKey = idempotencyKey,
            expiresInMs = input.expiresInMs,
        )
    }

    private fun buildIdempotencyKey(input: NormalizedCreateInput): String {
        input.idempotencyKey?.let { return it }
        input.sessionId?.let { session ->
            val service = input.serviceType ?: "general"
            return "session:$session:$service"
        }
        val service = input.serviceType ?: "general"
        return "auto:$service:${System.currentTimeMillis()}:${UUID.randomUUID().toString().take(8)}"
    }

    private fun buildSessionRecord(record: PaymentIntentStoreRecord): PaymentSessionRecord {
        val intent = PaymentIntent(
            id = record.id,
            gateway = record.gateway,
            amount = record.amount,
            currency = record.currency,
            status = record.status,
            environment = record.environment,
            sessionId = record.sessionId,
            serviceType = record.serviceType,
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
            confirmedAt = record.confirmedAt,
            expiresAt = record.expiresAt,
            qrCode = record.qrCode,
            meta = record.meta,
            contact = record.contact,
        )
        val serviceType = record.serviceType ?: extractServiceFromMeta(record.meta)
        val breakdown = computeBreakdown(record.amount, serviceType, revSharePercent)
        return PaymentSessionRecord(
            intent = intent,
            breakdown = breakdown,
            createdAtIso = record.createdAt,
            lastStatus = record.status,
        )
    }

    private fun mergeManualConfirmation(
        currentMeta: PaymentIntentMetadata?,
        extra: PaymentIntentMetadata?,
        operatorId: String,
        note: String?,
        timestamp: String,
    ): PaymentIntentMetadata {
        val manualMeta = buildJsonObject {
            put("operatorId", JsonPrimitive(operatorId))
            note?.takeIf { it.isNotBlank() }?.let { put("note", JsonPrimitive(it)) }
            put("at", JsonPrimitive(timestamp))
        }
        return buildJsonObject {
            currentMeta?.forEach { (key, value) -> put(key, value) }
            extra?.forEach { (key, value) -> put(key, value) }
            put("manualConfirmation", manualMeta)
        }
    }

    private fun recordAudit(
        type: String,
        intent: PaymentIntent,
        status: PaymentStatus,
        operatorId: String? = null,
        requestId: String? = null,
        details: JsonObject? = null,
    ) {
        val event = PaymentAuditEvent(
            type = type,
            timestampIso = Instant.now(clock).toString(),
            intentId = intent.id,
            sessionId = intent.sessionId,
            serviceType = intent.serviceType,
            status = status,
            amount = intent.amount,
            currency = intent.currency,
            environment = environment,
            gateway = gateway.name,
            operatorId = operatorId,
            requestId = requestId,
            details = details,
        )
        audit.record(event)
    }

    private fun extractWebhookRequestId(headers: Map<String, List<String>>?): String? {
        if (headers.isNullOrEmpty()) {
            return null
        }
        val normalized = headers.entries.associate { (key, value) ->
            key.lowercase(Locale.US) to value
        }
        for (candidate in WEBHOOK_REQUEST_ID_HEADERS) {
            val value = normalized[candidate]?.firstOrNull { it.isNotBlank() }?.trim()
            if (!value.isNullOrEmpty()) {
                return value
            }
        }
        return null
    }

    private fun sanitizeIdentifier(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val filtered = value.trim().replace(Regex("[^a-zA-Z0-9_-]"), "")
        return filtered.takeIf { it.isNotEmpty() }?.take(64)
    }

    private fun sanitizeContact(contact: PaymentContact?): PaymentContact? {
        if (contact == null) return null
        val email = contact.email?.trim()?.takeIf { it.isNotEmpty() }
        val phone = contact.phone?.trim()?.takeIf { it.isNotEmpty() }
        if (email == null && phone == null) {
            return null
        }
        return PaymentContact(email = email, phone = phone)
    }

    private fun extractServiceFromMeta(meta: PaymentIntentMetadata?): String? {
        val serviceEntry = meta?.get("service") ?: return null
        val value = serviceEntry.jsonPrimitive.contentOrNull
        return value?.takeIf { it.isNotBlank() }
    }

    private fun computeBreakdown(amount: Long, serviceType: String?, revSharePercent: Double): PaymentSessionBreakdown {
        val partner = resolvePartner(amount, serviceType, revSharePercent)
        val net = if (partner != null) maxOf(0, amount - partner.shareAmount) else amount
        return PaymentSessionBreakdown(
            gross = amount,
            net = net,
            partner = partner,
        )
    }

    private fun resolvePartner(amount: Long, serviceType: String?, revSharePercent: Double): PartnerShare? {
        if (serviceType.isNullOrBlank()) {
            return null
        }
        val sharePercent = clampPercent(revSharePercent)
        if (sharePercent <= 0.0) {
            return null
        }
        val normalizedService = serviceType.lowercase(Locale.ROOT)
        val partnerName = when (normalizedService) {
            "obd" -> "Diagzone PRO"
            "thickness" -> "rDevice"
            else -> null
        } ?: return null
        val shareAmount = calculateShareAmount(amount, sharePercent)
        if (shareAmount <= 0) {
            return null
        }
        return PartnerShare(
            name = partnerName,
            sharePercent = sharePercent,
            shareAmount = shareAmount,
        )
    }

    private fun calculateShareAmount(amount: Long, sharePercent: Double): Long {
        if (amount <= 0 || !sharePercent.isFinite()) {
            return 0
        }
        val computed = (amount * sharePercent).roundToLong()
        if (computed <= 0) {
            return 0
        }
        return min(amount, computed)
    }

    private fun clampPercent(value: Double): Double {
        if (!value.isFinite() || value <= 0.0) {
            return 0.0
        }
        return min(0.5, value)
    }

    private data class NormalizedCreateInput(
        val amount: Long,
        val currency: String,
        val sessionId: String?,
        val serviceType: String?,
        val contact: PaymentContact?,
        val meta: PaymentIntentMetadata?,
        val idempotencyKey: String?,
        val expiresInMs: Long?,
    )

    private fun maybePruneStore() {
        val policy = prunePolicy ?: return
        createCountSincePrune += 1
        if (createCountSincePrune < PRUNE_CHECK_INTERVAL) {
            return
        }
        createCountSincePrune = 0
        runCatching { store.prune(policy) }
            .onSuccess { removed ->
                if (removed > 0) {
                    logger.info(
                        "[payments] store pruned",
                        mapOf(
                            "removed" to removed,
                            "policyMinAgeMs" to policy.minAgeMillis,
                            "policyMaxEntries" to policy.maxEntries,
                        ),
                    )
                }
            }
            .onFailure { error ->
                logger.warn(
                    "[payments] store prune failed",
                    mapOf("error" to (error.message ?: error::class.java.simpleName)),
                )
            }
    }

    companion object {
        private const val PRUNE_CHECK_INTERVAL = 10
        private val WEBHOOK_REQUEST_ID_HEADERS = listOf(
            "x-request-id",
            "request-id",
            "x-yookassa-request-id",
            "x-yookassa-trace-id",
            "x-correlation-id",
            "x-amzn-trace-id",
            "idempotence-key",
            "x-idempotence-key",
        )
    }
}
