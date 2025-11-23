package com.selfservice.feature.payments.store

import com.selfservice.feature.payments.PaymentContact
import com.selfservice.feature.payments.PaymentEnvironment
import com.selfservice.feature.payments.PaymentIntentStoreOptions
import com.selfservice.feature.payments.PaymentIntentStorePrunePolicy
import com.selfservice.feature.payments.PaymentStatus
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PaymentIntentStoreTest {
    private lateinit var tempDir: Path

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("payment-store-test")
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `create and retrieve intent with encrypted meta`() {
        val store = createStore()
        val meta = buildJsonObject { put("service", "obd") }
        val contact = PaymentContact(email = "user@example.com", phone = "+10000000000")
        val created = store.create(
            PaymentIntentStore.CreateStoredIntentInput(
                id = "pi_test",
                gateway = "dev",
                amount = 950,
                currency = "RUB",
                status = PaymentStatus.PENDING,
                sessionId = "session123",
                serviceType = "obd",
                meta = meta,
                contact = contact,
                idempotencyKey = "key-1",
            ),
        )

        val loaded = store.get("pi_test")
        assertNotNull(loaded)
        assertEquals(created.id, loaded.id)
        assertEquals(meta, loaded.meta)
        assertEquals(contact.email, loaded.contact?.email)
        assertTrue(loaded.metaHash?.isNotBlank() == true)
        assertTrue(loaded.contactHash?.isNotBlank() == true)
        val metrics = store.metrics()
        assertEquals(1, metrics.total)
        assertEquals(1, metrics.byStatus[PaymentStatus.PENDING])
    }

    @Test
    fun `idempotency key returns existing intent`() {
        val store = createStore()
        val input = PaymentIntentStore.CreateStoredIntentInput(
            id = "pi_original",
            gateway = "dev",
            amount = 500,
            currency = "RUB",
            status = PaymentStatus.PENDING,
            idempotencyKey = "same-key",
        )
        val first = store.create(input)
        val second = store.create(input.copy(id = "pi_new"))
        assertEquals(first.id, second.id)
    }

    @Test
    fun `prune removes old confirmed intents`() {
        val store = createStore()
        val now = Instant.now()
        val oldTs = now.minus(Duration.ofDays(10)).toString()
        val recentTs = now.minus(Duration.ofDays(1)).toString()

        store.create(
            PaymentIntentStore.CreateStoredIntentInput(
                id = "pi_old",
                gateway = "dev",
                amount = 100,
                currency = "RUB",
                status = PaymentStatus.CONFIRMED,
                idempotencyKey = "key-old",
                createdAt = oldTs,
                updatedAt = oldTs,
            ),
        )
        store.create(
            PaymentIntentStore.CreateStoredIntentInput(
                id = "pi_pending",
                gateway = "dev",
                amount = 200,
                currency = "RUB",
                status = PaymentStatus.PENDING,
                idempotencyKey = "key-pending",
                createdAt = oldTs,
                updatedAt = oldTs,
            ),
        )
        store.create(
            PaymentIntentStore.CreateStoredIntentInput(
                id = "pi_recent",
                gateway = "dev",
                amount = 300,
                currency = "RUB",
                status = PaymentStatus.CONFIRMED,
                idempotencyKey = "key-recent",
                createdAt = recentTs,
                updatedAt = recentTs,
            ),
        )

        val removed = store.prune(
            PaymentIntentStorePrunePolicy(
                minAgeMillis = Duration.ofDays(7).toMillis(),
                finalStatuses = setOf(PaymentStatus.CONFIRMED),
                maxEntries = 100,
                minEntriesToRetain = 0,
            ),
        )

        assertEquals(1, removed)
        assertNull(store.get("pi_old"))
        assertNotNull(store.get("pi_recent"))
        assertNotNull(store.get("pi_pending"))
    }

    @Test
    fun `prune enforces max entries constraint`() {
        val store = createStore()
        val now = Instant.now()
        val entries = listOf(
            "pi_old" to Duration.ofMinutes(30),
            "pi_mid" to Duration.ofMinutes(20),
            "pi_new" to Duration.ofMinutes(10),
        )
        entries.forEachIndexed { index, (id, offset) ->
            val ts = now.minus(offset).toString()
            store.create(
                PaymentIntentStore.CreateStoredIntentInput(
                    id = id,
                    gateway = "dev",
                    amount = 100L + index,
                    currency = "RUB",
                    status = PaymentStatus.CONFIRMED,
                    idempotencyKey = "key-$id",
                    createdAt = ts,
                    updatedAt = ts,
                ),
            )
        }

        val removed = store.prune(
            PaymentIntentStorePrunePolicy(
                minAgeMillis = 0,
                finalStatuses = setOf(PaymentStatus.CONFIRMED),
                maxEntries = 2,
                minEntriesToRetain = 0,
            ),
        )

        assertEquals(1, removed)
        assertNull(store.get("pi_old"))
        assertNotNull(store.get("pi_mid"))
        assertNotNull(store.get("pi_new"))
    }

    private fun createStore(): PaymentIntentStore {
        val file = tempDir.resolve("intents.json").toFile()
        return PaymentIntentStore(
            PaymentIntentStoreOptions(
                environment = PaymentEnvironment.DEV,
                file = file,
                encryptionKey = TEST_KEY,
            ),
        )
    }

    companion object {
        private const val TEST_KEY = "c2VsZnNlcnZpY2UtZGV2LWtleS1mb3ItdGVzdA=="
    }
}
