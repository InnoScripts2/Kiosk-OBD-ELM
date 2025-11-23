package com.selfservice.feature.payments.store

import com.selfservice.feature.payments.PaymentContact
import com.selfservice.feature.payments.PaymentEnvironment
import com.selfservice.feature.payments.PaymentIntent
import com.selfservice.feature.payments.PaymentIntentMetadata
import com.selfservice.feature.payments.PaymentIntentQrCode
import com.selfservice.feature.payments.PaymentIntentStoreMetrics
import com.selfservice.feature.payments.PaymentIntentStoreOptions
import com.selfservice.feature.payments.PaymentIntentStoreRecord
import com.selfservice.feature.payments.PaymentStatus
import com.selfservice.feature.payments.PaymentStatus.PENDING
import com.selfservice.feature.payments.PaymentStatus.values
import com.selfservice.feature.payments.UpdatePaymentIntentOptions
import com.selfservice.feature.payments.PaymentIntentStorePrunePolicy
import com.selfservice.feature.payments.crypto.EncryptedPayload
import com.selfservice.feature.payments.crypto.PaymentEncryption
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.util.LinkedHashSet
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private const val STORE_VERSION = 1
private const val PENDING_STALE_THRESHOLD_MS = 90_000L

class PaymentIntentStore(options: PaymentIntentStoreOptions) {
    private val clock: Clock = Clock.systemUTC()
    private val file: File = options.file
    private val encryption = PaymentEncryption(options.encryptionKey)
    private val environment: PaymentEnvironment = options.environment
    private val intents = ConcurrentHashMap<String, PersistedIntent>()
    private val idempotencyIndex = ConcurrentHashMap<String, String>()
    private val json = Json {
        prettyPrint = false
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    init {
        ensureDirectory()
        loadFromDisk()
    }

    data class CreateStoredIntentInput(
        val id: String,
        val gateway: String,
        val amount: Long,
        val currency: String,
        val status: PaymentStatus,
        val sessionId: String? = null,
        val serviceType: String? = null,
        val qrCode: PaymentIntentQrCode? = null,
        val meta: PaymentIntentMetadata? = null,
        val contact: PaymentContact? = null,
        val idempotencyKey: String,
        val createdAt: String? = null,
        val updatedAt: String? = null,
        val confirmedAt: String? = null,
        val expiresAt: String? = null,
    )

    fun create(input: CreateStoredIntentInput): PaymentIntentStoreRecord {
        intents[input.id]?.let { return hydrate(it) }
        idempotencyIndex[input.idempotencyKey]?.let { existingId ->
            intents[existingId]?.let { return hydrate(it) }
        }
        val now = Instant.now(clock).toString()
        val metaJson = encodeMetadata(input.meta)
        val contactJson = encodeContact(input.contact)
        val persisted = PersistedIntent(
            id = input.id,
            gateway = input.gateway,
            amount = input.amount,
            currency = input.currency,
            status = input.status,
            environment = environment,
            sessionId = input.sessionId,
            serviceType = input.serviceType,
            createdAt = input.createdAt ?: now,
            updatedAt = input.updatedAt ?: now,
            confirmedAt = input.confirmedAt,
            expiresAt = input.expiresAt,
            qrCode = input.qrCode,
            idempotencyKey = input.idempotencyKey,
            metaHash = encryption.hashString(metaJson),
            contactHash = encryption.hashString(contactJson),
            metaEncrypted = encryption.encryptString(metaJson),
            contactEncrypted = encryption.encryptString(contactJson),
        )
        intents[persisted.id] = persisted
        idempotencyIndex[persisted.idempotencyKey] = persisted.id
        persist()
        return hydrate(persisted)
    }

    fun get(id: String): PaymentIntentStoreRecord? = intents[id]?.let(::hydrate)

    fun getByIdempotencyKey(key: String): PaymentIntentStoreRecord? {
        val intentId = idempotencyIndex[key] ?: return null
        return get(intentId)
    }

    fun update(id: String, updates: UpdatePaymentIntentOptions): PaymentIntentStoreRecord? {
        val existing = intents[id] ?: return null
        val metaJson = updates.meta?.let { encodeMetadata(it) }
        val contactJson = updates.contact?.let { encodeContact(it) }
        val next = existing.copy(
            status = updates.status ?: existing.status,
            confirmedAt = updates.confirmedAt ?: existing.confirmedAt,
            expiresAt = updates.expiresAt ?: existing.expiresAt,
            updatedAt = Instant.now(clock).toString(),
            qrCode = updates.qrCode ?: existing.qrCode,
            metaHash = metaJson?.let(encryption::hashString) ?: existing.metaHash,
            contactHash = contactJson?.let(encryption::hashString) ?: existing.contactHash,
            metaEncrypted = metaJson?.let(encryption::encryptString) ?: existing.metaEncrypted,
            contactEncrypted = contactJson?.let(encryption::encryptString) ?: existing.contactEncrypted,
        )
        intents[id] = next
        persist()
        return hydrate(next)
    }

    fun list(): List<PaymentIntent> = intents.values.map(::hydrate).map { it.toIntent() }

    fun metrics(now: Instant = Instant.now(clock)): PaymentIntentStoreMetrics {
        val byStatus = values().associateWith { 0 }.toMutableMap()
        var pendingOlderThanMs = 0L
        var pendingOlderThanCount = 0
        intents.values.forEach { intent ->
            byStatus[intent.status] = (byStatus[intent.status] ?: 0) + 1
            if (intent.status == PENDING) {
                val createdAt = runCatching { Instant.parse(intent.createdAt) }.getOrNull()
                val age = if (createdAt != null) now.toEpochMilli() - createdAt.toEpochMilli() else 0L
                pendingOlderThanMs = maxOf(pendingOlderThanMs, age)
                if (age >= PENDING_STALE_THRESHOLD_MS) {
                    pendingOlderThanCount += 1
                }
            }
        }
        return PaymentIntentStoreMetrics(
            total = intents.size,
            byStatus = byStatus.toMap(),
            pendingOlderThanMs = pendingOlderThanMs,
            pendingOlderThanCount = pendingOlderThanCount,
        )
    }

    fun prune(policy: PaymentIntentStorePrunePolicy = PaymentIntentStorePrunePolicy()): Int {
        if (intents.isEmpty()) {
            return 0
        }
        val finalStatuses = policy.finalStatuses
        if (finalStatuses.isEmpty()) {
            return 0
        }
        val candidates = intents.values
            .filter { finalStatuses.contains(it.status) }
            .sortedBy { parseInstant(it.updatedAt) ?: Instant.EPOCH }
        if (candidates.isEmpty()) {
            return 0
        }
        val idsToRemove = LinkedHashSet<String>()
        val minRetain = policy.minEntriesToRetain.coerceAtLeast(0)
        val minAgeInstant = policy.minAgeMillis
            .takeIf { it > 0 }
            ?.let { Instant.now(clock).minusMillis(it) }

        if (minAgeInstant != null) {
            for (intent in candidates) {
                if (!isOlderThan(intent.updatedAt, minAgeInstant)) {
                    continue
                }
                if (minRetain > 0) {
                    val projectedRemaining = intents.size - (idsToRemove.size + 1)
                    if (projectedRemaining < minRetain) {
                        break
                    }
                }
                idsToRemove += intent.id
            }
        }

        var currentSize = intents.size - idsToRemove.size
        if (policy.maxEntries > 0 && currentSize > policy.maxEntries) {
            candidates.asSequence()
                .filter { !idsToRemove.contains(it.id) }
                .forEach { intent ->
                    if (currentSize <= policy.maxEntries) {
                        return@forEach
                    }
                    if (minRetain > 0 && currentSize - 1 < minRetain) {
                        return@forEach
                    }
                    idsToRemove += intent.id
                    currentSize -= 1
                }
        }

        if (idsToRemove.isEmpty()) {
            return 0
        }

        var removed = 0
        idsToRemove.forEach { id ->
            val intent = intents.remove(id) ?: return@forEach
            removed += 1
            idempotencyIndex.entries.removeIf { entry -> entry.value == intent.id }
        }
        if (removed > 0) {
            persist()
        }
        return removed
    }

    private fun hydrate(persisted: PersistedIntent): PaymentIntentStoreRecord {
        val meta = decodeMetadata(encryption.decryptString(persisted.metaEncrypted))
        val contact = decodeContact(encryption.decryptString(persisted.contactEncrypted))
        return PaymentIntentStoreRecord(
            id = persisted.id,
            gateway = persisted.gateway,
            amount = persisted.amount,
            currency = persisted.currency,
            status = persisted.status,
            environment = persisted.environment,
            sessionId = persisted.sessionId,
            serviceType = persisted.serviceType,
            createdAt = persisted.createdAt,
            updatedAt = persisted.updatedAt,
            confirmedAt = persisted.confirmedAt,
            expiresAt = persisted.expiresAt,
            qrCode = persisted.qrCode,
            meta = meta,
            contact = contact,
            idempotencyKey = persisted.idempotencyKey,
            metaHash = persisted.metaHash,
            contactHash = persisted.contactHash,
        )
    }

    private fun PaymentIntentStoreRecord.toIntent(): PaymentIntent = PaymentIntent(
        id = id,
        gateway = gateway,
        amount = amount,
        currency = currency,
        status = status,
        environment = environment,
        sessionId = sessionId,
        serviceType = serviceType,
        createdAt = createdAt,
        updatedAt = updatedAt,
        confirmedAt = confirmedAt,
        expiresAt = expiresAt,
        qrCode = qrCode,
        meta = meta,
        contact = contact,
    )

    private fun ensureDirectory() {
        file.parentFile?.takeIf { !it.exists() }?.mkdirs()
    }

    private fun loadFromDisk() {
        if (!file.exists()) {
            return
        }
        try {
            val parsed = json.decodeFromString(PersistedFile.serializer(), file.readText())
            if (parsed.version != STORE_VERSION) {
                return
            }
            intents.clear()
            idempotencyIndex.clear()
            parsed.intents.forEach { intent ->
                intents[intent.id] = intent
                idempotencyIndex[intent.idempotencyKey] = intent.id
            }
        } catch (error: Exception) {
            System.err.println("[PaymentIntentStore] failed to load store: $error")
        }
    }

    private fun persist() {
        val payload = PersistedFile(
            version = STORE_VERSION,
            updatedAt = Instant.now(clock).toString(),
            intents = intents.values.sortedBy { it.createdAt },
        )
        val parentDir = file.parentFile ?: file.absoluteFile.parentFile ?: File(".")
        val tmpFile = File(parentDir, "${file.name}.tmp")
        try {
            tmpFile.writeText(json.encodeToString(payload))
            if (!tmpFile.renameTo(file)) {
                tmpFile.copyTo(file, overwrite = true)
                tmpFile.delete()
            }
        } catch (error: IOException) {
            System.err.println("[PaymentIntentStore] failed to persist store: $error")
        }
    }

    private fun encodeMetadata(meta: PaymentIntentMetadata?): String? = meta?.let {
        json.encodeToString(JsonObject.serializer(), it)
    }

    private fun encodeContact(contact: PaymentContact?): String? = contact?.let {
        json.encodeToString(PaymentContact.serializer(), it)
    }

    private fun decodeMetadata(raw: String?): PaymentIntentMetadata? = raw?.let {
        runCatching { json.decodeFromString(JsonObject.serializer(), it) }.getOrNull()
    }

    private fun decodeContact(raw: String?): PaymentContact? = raw?.let {
        runCatching { json.decodeFromString(PaymentContact.serializer(), it) }.getOrNull()
    }

    private fun parseInstant(value: String?): Instant? = value?.let {
        runCatching { Instant.parse(it) }.getOrNull()
    }

    private fun isOlderThan(value: String?, threshold: Instant): Boolean {
        val instant = parseInstant(value) ?: return false
        return instant.isBefore(threshold)
    }

    @Serializable
    private data class PersistedFile(
        val version: Int,
        val updatedAt: String,
        val intents: List<PersistedIntent>,
    )

    @Serializable
    private data class PersistedIntent(
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
        val idempotencyKey: String,
        val metaHash: String? = null,
        val contactHash: String? = null,
        val metaEncrypted: EncryptedPayload? = null,
        val contactEncrypted: EncryptedPayload? = null,
    )
}
