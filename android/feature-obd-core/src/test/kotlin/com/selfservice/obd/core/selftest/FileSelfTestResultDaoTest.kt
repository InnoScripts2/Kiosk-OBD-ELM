package com.selfservice.obd.core.selftest

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.json.JSONObject

class FileSelfTestResultDaoTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("selftest-dao").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun upsertAppendsJsonLine() = runTest {
        val dao = FileSelfTestResultDao(directory = tempDir, clock = { 42L })
        val metadata = AdapterSelfTestUploadMetadata(
                sessionId = "session-123",
                adapterSerial = "AA:BB:CC",
                startedAtIso = "2025-01-01T10:00:00Z",
                completedAtIso = "2025-01-01T10:01:00Z"
        )
        val run = AdapterSelfTestRun(emptyList())
        val uploader = AdapterSelfTestUploader(dao)

        uploader.upload(run, metadata)

        val file = tempDir.resolve("adapter_selftests.jsonl")
        assertTrue(file.exists())
        val lines = file.readLines()
        assertEquals(1, lines.size)
        val json = JSONObject(lines.single())
        assertEquals(42L, json.getLong("written_at_ms"))
        val payload = json.getJSONObject("payload")
        assertEquals("session-123", payload.getString("session_id"))
        assertEquals("AA:BB:CC", payload.getString("adapter_serial"))
    }

    @Test
    fun upsertCreatesDirectoryWhenMissing() = runTest {
        val directory = tempDir.resolve("nested")
        val dao = FileSelfTestResultDao(directory = directory, clock = { 1L })

        dao.upsert(mapOf("foo" to "bar"))

        val file = directory.resolve("adapter_selftests.jsonl")
        assertTrue(file.exists())
        val json = JSONObject(file.readLines().single())
        assertEquals(1L, json.getLong("written_at_ms"))
    }
}
