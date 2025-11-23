package com.selfservice.kiosk.supabase

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class SupabaseOutboxWriterTest {

    private lateinit var root: File

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("supabase-outbox").toFile()
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun enqueueAppendsEnvelope() {
        val writer = SupabaseOutboxWriter(root, clock = { 42L })

        writer.enqueue(
                table = "adapter_selftests",
                payload = mapOf("session_id" to "session-001", "succeeded" to true)
        )

        val file = File(root, "supabase_outbox.jsonl")
        assertTrue(file.exists())
        val json = JSONObject(file.readLines().single())
        assertEquals(42L, json.getLong("written_at_ms"))
        assertEquals("adapter_selftests", json.getString("table"))
        assertEquals("upsert", json.getString("operation"))
        val payload = json.getJSONObject("payload")
        assertEquals("session-001", payload.getString("session_id"))
        assertTrue(payload.getBoolean("succeeded"))
    }

    @Test
    fun enqueueNotifiesCallback() {
        var notifications = 0
        val writer = SupabaseOutboxWriter(root, clock = { 5L }) { notifications += 1 }

        writer.enqueue(table = "adapter_selftests", payload = mapOf("session_id" to "s-1"))
        writer.enqueue(table = "adapter_selftests", payload = mapOf("session_id" to "s-2"))

        assertEquals(2, notifications)
    }

    @Test
    fun daoDelegatesToWriter() = runBlocking {
        val writer = SupabaseOutboxWriter(root, clock = { 100L })
        val dao = SupabaseOutboxSelfTestResultDao(writer)

        dao.upsert(mapOf("session_id" to "session-42"))

        val file = File(root, "supabase_outbox.jsonl")
        val json = JSONObject(file.readLines().single())
        assertEquals("adapter_selftests", json.getString("table"))
        assertEquals("session-42", json.getJSONObject("payload").getString("session_id"))
    }
}
