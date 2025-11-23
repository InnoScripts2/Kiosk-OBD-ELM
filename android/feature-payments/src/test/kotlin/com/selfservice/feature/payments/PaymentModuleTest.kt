package com.selfservice.feature.payments

import com.selfservice.feature.payments.gateway.DevPaymentGateway
import com.selfservice.feature.payments.PaymentIntentStoreOptions
import com.selfservice.feature.payments.PaymentStatus
import com.selfservice.feature.payments.PaymentAuditEvent
import com.selfservice.feature.payments.PaymentAuditSink
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class PaymentModuleTest {
    private lateinit var tempDir: Path

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("payment-module-test")
    }

    @AfterTest
    fun teardown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `create intent returns partner breakdown`() {
        runBlocking {
            val module = createModule()
            val result = module.createIntent(
                CreatePaymentIntentInput(
                    amount = 1_000,
                    currency = "rub",
                    sessionId = "sessionA",
                    serviceType = "obd",
                    meta = buildJsonObject { put("service", "obd") },
                ),
            )
            assertEquals(1_000, result.intent.amount)
            val partner = result.breakdown.partner
            assertNotNull(partner)
            assertEquals("Diagzone PRO", partner.name)
        }
    }

    @Test
    fun `manual confirm enriches metadata`() {
        runBlocking {
            val module = createModule()
            val session = module.createIntent(
                CreatePaymentIntentInput(amount = 500, currency = "RUB", serviceType = "obd"),
            )
            val confirmed = module.manualConfirm(
                ManualConfirmationInput(
                    id = session.intent.id,
                    operatorId = "operator-1",
                    note = "approved",
                    meta = buildJsonObject { put("custom", "note") },
                ),
            )
            assertNotNull(confirmed)
            assertEquals(PaymentStatus.MANUAL, confirmed.intent.status)
            val manualMeta = confirmed.intent.meta?.get("manualConfirmation")?.jsonObject
            assertEquals("operator-1", manualMeta?.get("operatorId")?.jsonPrimitive?.content)
            assertEquals("approved", manualMeta?.get("note")?.jsonPrimitive?.content)
            assertEquals("note", confirmed.intent.meta?.get("custom")?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `confirmDev forbidden outside dev`() {
        runBlocking {
            val module = createModule(environment = PaymentEnvironment.QA)
            assertFailsWith<IllegalArgumentException> {
                module.confirmDev("pi_123")
            }
        }
    }

    @Test
    fun `audit sink captures create status and manual confirm`() {
        runBlocking {
            val events = mutableListOf<PaymentAuditEvent>()
            val module = createModule(auditSink = PaymentAuditSink { event -> events += event })
            val session = module.createIntent(
                CreatePaymentIntentInput(
                    amount = 2_500,
                    currency = "RUB",
                    sessionId = "session-audit",
                    serviceType = "obd",
                ),
            )
            module.getStatus(session.intent.id)
            module.manualConfirm(
                ManualConfirmationInput(
                    id = session.intent.id,
                    operatorId = "operator-audit",
                    note = "manual",
                ),
            )
            assertTrue(events.any { it.type == "intent_created" })
            assertTrue(events.any { it.type == "status_checked" })
            assertTrue(events.any { it.type == "intent_manual_confirmed" && it.operatorId == "operator-audit" })
        }
    }

    @Test
    fun `handle webhook updates store and emits audit event`() {
        runBlocking {
            val events = mutableListOf<PaymentAuditEvent>()
            val gateway = TestWebhookGateway()
            val module = createModule(
                auditSink = PaymentAuditSink { event -> events += event },
                gateway = gateway,
            )
            val session = module.createIntent(
                CreatePaymentIntentInput(
                    amount = 3_000,
                    currency = "RUB",
                    serviceType = "obd",
                ),
            )
            val payload = buildJsonObject { put("event", "payment.succeeded") }
            gateway.webhookResult = PaymentGatewayWebhookResult(
                intentId = session.intent.id,
                status = PaymentStatus.CONFIRMED,
                reference = "req-123",
                rawPayload = payload,
            )
            val headers = mapOf(
                "X-Request-Id" to listOf("req-123"),
            )
            val rawPayload = payload.toString()
            val result = module.handleWebhook(payload, headers, rawPayload)
            assertNotNull(result)
            assertTrue(result.updated)
            assertEquals(PaymentStatus.CONFIRMED, result.intent.status)
            assertEquals(rawPayload, gateway.lastRawPayload)
            val webhookEvent = events.lastOrNull { it.type == "webhook_handled" }
                ?: error("webhook audit event missing")
            assertEquals("req-123", webhookEvent.requestId)
            assertEquals(PaymentStatus.CONFIRMED, webhookEvent.status)
            assertEquals(session.intent.id, webhookEvent.intentId)
        }
    }

    private fun createModule(
        environment: PaymentEnvironment = PaymentEnvironment.DEV,
        auditSink: PaymentAuditSink? = null,
        gateway: PaymentGateway? = null,
    ): PaymentModule {
        val storeFile = tempDir.resolve("${'$'}{environment.name}.json").toFile()
        val options = PaymentModuleOptions(
            environment = environment,
            storeOptions = PaymentIntentStoreOptions(
                environment = environment,
                file = storeFile,
                encryptionKey = TEST_KEY,
            ),
            gateway = gateway,
            gatewayFactory = gateway?.let { null } ?: { DevPaymentGateway(environment = environment, manualMode = true, autoConfirmDelayMs = 0) },
            logger = object : PaymentLogger {},
            auditSink = auditSink,
        )
        return PaymentModule(options)
    }

    private class TestWebhookGateway : PaymentGateway {
        override val name: String = "test-webhook"
        override val environment: PaymentEnvironment = PaymentEnvironment.DEV
        private var counter: Int = 0
        var webhookResult: PaymentGatewayWebhookResult? = null
        var lastRawPayload: String? = null

        override suspend fun createIntent(request: PaymentGatewayCreateRequest): PaymentGatewayCreateResponse {
            counter += 1
            val id = "pi_test_${'$'}counter"
            return PaymentGatewayCreateResponse(
                intentId = id,
                status = PaymentStatus.PENDING,
            )
        }

        override suspend fun getStatus(intentId: String): PaymentGatewayStatusResponse =
            PaymentGatewayStatusResponse(status = PaymentStatus.PENDING)

        override suspend fun handleWebhook(
            payload: JsonElement?,
            headers: Map<String, List<String>>?,
            rawPayload: String?,
        ): PaymentGatewayWebhookResult? {
            lastRawPayload = rawPayload
            return webhookResult
        }
    }

    companion object {
        private const val TEST_KEY = "c2VsZnNlcnZpY2UtZGV2LWtleS1mb3ItdGVzdA=="
    }
}
