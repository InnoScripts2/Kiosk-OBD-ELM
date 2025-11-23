package com.selfservice.core.logging

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.json.JSONObject

class DiagnosticsLogFileSinkTest {

    private val tempDir: File = Files.createTempDirectory("diagnostics-log-file-sink-test").toFile()

    @AfterTest
    fun cleanUp() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `persist appends entries as json lines`() {
        val sink = DiagnosticsLogFileSink(directory = tempDir)
        sink.persist(
                DiagnosticsLogEntry(
                        category = "session",
                        message = "started",
                        timestampMillis = 100L,
                        metadata = mapOf("case" to "ft-01")
                )
        )
        sink.persist(
                DiagnosticsLogEntry(
                        category = "session",
                        message = "completed",
                        timestampMillis = 200L,
                        metadata = mapOf("result" to "success")
                )
        )

        val file = File(tempDir, DiagnosticsLogFileSink.DEFAULT_FILE_NAME)
        assertTrue(file.exists(), "Expected diagnostics log file to exist")

        val lines = file.readLines().filter { it.isNotBlank() }
        assertEquals(2, lines.size)

        val first = JSONObject(lines[0])
        assertEquals("session", first.getString("category"))
        assertEquals("started", first.getString("message"))
        assertEquals(100L, first.getLong("timestamp_ms"))
        assertEquals("ft-01", first.getJSONObject("metadata").getString("case"))

        val second = JSONObject(lines[1])
        assertEquals("completed", second.getString("message"))
        assertEquals("success", second.getJSONObject("metadata").getString("result"))
    }

    @Test
    fun `clearOlderThan prunes outdated entries`() {
        val sink = DiagnosticsLogFileSink(directory = tempDir)
        sink.persist(DiagnosticsLogEntry("session", "old", 100L, emptyMap()))
        sink.persist(DiagnosticsLogEntry("session", "new", 500L, emptyMap()))

        sink.clearOlderThan(200L)

        val file = File(tempDir, DiagnosticsLogFileSink.DEFAULT_FILE_NAME)
        val lines = file.readLines().filter { it.isNotBlank() }
        assertEquals(1, lines.size)
        val remaining = JSONObject(lines.single())
        assertEquals("new", remaining.getString("message"))
    }

    @Test
    fun `clearOlderThan removes file when all entries pruned`() {
        val sink = DiagnosticsLogFileSink(directory = tempDir)
        sink.persist(DiagnosticsLogEntry("session", "old", 100L, emptyMap()))

        sink.clearOlderThan(200L)

        val file = File(tempDir, DiagnosticsLogFileSink.DEFAULT_FILE_NAME)
        assertFalse(file.exists(), "Expected diagnostics log file to be removed when empty")
    }

    @Test
    fun `snapshot returns parsed entries`() {
        val sink = DiagnosticsLogFileSink(directory = tempDir)
        sink.persist(
                DiagnosticsLogEntry(
                        category = "session",
                        message = "payload",
                        timestampMillis = 350L,
                        metadata = mapOf("step" to 4)
                )
        )

        val snapshot = sink.snapshot()
        assertEquals(1, snapshot.size)
        val entry = snapshot.single()
        assertEquals("payload", entry.message)
        assertEquals(350L, entry.timestampMillis)
        assertEquals(4, entry.metadata["step"])
    }
}
