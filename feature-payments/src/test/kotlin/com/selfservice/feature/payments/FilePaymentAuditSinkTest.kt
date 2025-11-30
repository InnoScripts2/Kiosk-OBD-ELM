package com.selfservice.feature.payments

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class FilePaymentAuditSinkTest {
    private lateinit var tempDir: Path

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("payment-audit-sink-test")
    }

    @AfterTest
    fun teardown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `writes ndjson entries`() {
        val file = tempDir.resolve("audit.ndjson").toFile()
        val sink = FilePaymentAuditSink(file, maxBytes = 128 * 1024)
        sink.record(sampleEvent(intentId = "pi_a"))
        sink.record(sampleEvent(intentId = "pi_b"))
        val lines = file.readLines()
        assertTrue(lines.size == 2)
        assertTrue(lines.all { it.contains("\"intentId\"") })
    }

    @Test
    fun `truncates file when exceeding limit`() {
        val file = tempDir.resolve("audit.ndjson").toFile()
        val sink = FilePaymentAuditSink(file, maxBytes = 256)
        repeat(20) { index ->
            sink.record(sampleEvent(intentId = "pi_$index"))
        }
        assertTrue(file.length() <= 256)
        val lines = file.readLines()
        assertTrue(lines.isNotEmpty())
    }

    private fun sampleEvent(intentId: String): PaymentAuditEvent = PaymentAuditEvent(
        type = "test_event",
        timestampIso = "2025-11-21T12:00:00Z",
        intentId = intentId,
        sessionId = "session-x",
        serviceType = "obd",
        status = PaymentStatus.PENDING,
        amount = 1_000,
        currency = "RUB",
        environment = PaymentEnvironment.DEV,
        gateway = "dev-gateway",
        details = buildJsonObject { put("sample", "value") },
    )
}
