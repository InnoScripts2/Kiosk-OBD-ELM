package com.selfservice.kiosk.supabase

import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test

class SupabaseOutboxUploaderTest {

    private lateinit var root: File
    private lateinit var writer: SupabaseOutboxWriter

    @Before
    fun setUp() {
        root = Files.createTempDirectory("supabase-outbox-uploader").toFile()
        writer = SupabaseOutboxWriter(root) { System.currentTimeMillis() }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun flushReturnsImmediatelyWhenFileMissing() = runBlocking {
        val client = RecordingClient()
        val uploader = SupabaseOutboxUploader(root, client)

        val result = uploader.flush()

        assertTrue(result.isSuccess)
        assertEquals(0, result.processed)
        assertEquals(0, result.remaining)
        assertEquals(0, result.discarded)
        assertNull(result.failure)
    }

    @Test
    fun flushDeliversEntriesAndClearsFile() = runBlocking {
        val client = RecordingClient()
        val uploader = SupabaseOutboxUploader(root, client)
        writer.enqueue("adapter_selftests", mapOf("session_id" to "A"))
        writer.enqueue("adapter_selftests", mapOf("session_id" to "B"))

        val result = uploader.flush()

        assertTrue(result.isSuccess)
        assertEquals(2, result.processed)
        assertEquals(0, result.remaining)
        assertEquals(0, result.discarded)
        assertEquals(2, client.operations.size)
        val outboxFile = File(root, SupabaseOutboxWriter.DEFAULT_FILE_NAME)
        assertTrue(!outboxFile.exists() || outboxFile.length() == 0L)
    }

    @Test
    fun flushRespectsMaxEntries() = runBlocking {
        val client = RecordingClient()
        val uploader = SupabaseOutboxUploader(root, client)
        writer.enqueue("adapter_selftests", mapOf("session_id" to "1"))
        writer.enqueue("adapter_selftests", mapOf("session_id" to "2"))
        writer.enqueue("adapter_selftests", mapOf("session_id" to "3"))

        val first = uploader.flush(maxEntries = 2)
        assertEquals(2, first.processed)
        assertEquals(1, first.remaining)
        assertEquals(2, client.operations.size)

        val second = uploader.flush()
        assertEquals(1, second.processed)
        assertEquals(0, second.remaining)
        assertEquals(3, client.operations.size)
    }

    @Test
    fun flushPreservesPendingEntriesOnFailure() = runBlocking {
        val client = RecordingClient(failAtIndex = 1)
        val uploader = SupabaseOutboxUploader(root, client)
        writer.enqueue("adapter_selftests", mapOf("session_id" to "alpha"))
        writer.enqueue("adapter_selftests", mapOf("session_id" to "beta"))

        val result = uploader.flush()

        assertTrue(!result.isSuccess)
        assertEquals(1, result.processed)
        assertEquals(1, result.remaining)
        assertNotNull(result.failure)
        assertEquals(1, client.operations.size)
        val file = File(root, SupabaseOutboxWriter.DEFAULT_FILE_NAME)
        assertTrue(file.exists())
        val remainingLines = file.readLines().filter { it.isNotBlank() }
        assertEquals(1, remainingLines.size)
        val payload = JSONObject(remainingLines.single()).getJSONObject("payload")
        assertEquals("beta", payload.getString("session_id"))
    }

    @Test
    fun flushDropsInvalidLines() = runBlocking {
        val file = File(root, SupabaseOutboxWriter.DEFAULT_FILE_NAME)
        file.writeText("not-json\n")
        writer.enqueue("adapter_selftests", mapOf("session_id" to "gamma"))
        val client = RecordingClient()
        val uploader = SupabaseOutboxUploader(root, client)

        val result = uploader.flush()

        assertTrue(result.isSuccess)
        assertEquals(1, result.processed)
        assertEquals(0, result.remaining)
        assertEquals(1, result.discarded)
        assertEquals("gamma", client.operations.single().payload.getString("session_id"))
        assertTrue(!file.exists())
    }

    private class RecordingClient(private val failAtIndex: Int? = null) : SupabaseOutboxUploader.Client {
        val operations: MutableList<SupabaseOutboxUploader.Operation> = mutableListOf()

        override suspend fun execute(operation: SupabaseOutboxUploader.Operation) {
            val index = operations.size
            if (failAtIndex != null && index == failAtIndex) {
                throw IllegalStateException("Failure at index ${failAtIndex}")
            }
            operations += operation
        }
    }
}
