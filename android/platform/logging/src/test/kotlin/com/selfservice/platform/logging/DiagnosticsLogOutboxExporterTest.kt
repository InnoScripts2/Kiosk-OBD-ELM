package com.selfservice.platform.logging

import com.selfservice.core.logging.DiagnosticsLogEntry
import com.selfservice.core.logging.DiagnosticsLogFileSink
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticsLogOutboxExporterTest {

    private val root: File = Files.createTempDirectory("diag-exporter-test").toFile()
    private val sinkDir = File(root, "logs")

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    @Test
    fun `exportPending moves entries into outbox and prunes sink`() {
        val sink = DiagnosticsLogFileSink(directory = sinkDir)
        val writer = RecordingWriter()
        val exporter = DiagnosticsLogOutboxExporter(sink, writer) {
            mapOf(
                DiagnosticsLogSupabaseSchema.Columns.KIOSK_ID to "kiosk-777",
                DiagnosticsLogSupabaseSchema.Columns.ENVIRONMENT to "qa"
            )
        }

        sink.persist(DiagnosticsLogEntry("session", "start", 100L, mapOf("step" to 1)))
        sink.persist(DiagnosticsLogEntry("session", "end", 200L, mapOf("result" to "ok")))

        val result = exporter.exportPending()

        assertTrue(result.isSuccess)
        assertEquals(2, result.exported)
        assertEquals(200L, result.lastTimestampMillis)
        assertTrue(sink.snapshot().isEmpty(), "Expected sink to be pruned after export")

        assertEquals(2, writer.operations.size)
        val first = writer.operations.first()
        assertEquals(DiagnosticsLogSupabaseSchema.TABLE_NAME, first.table)
        assertEquals("kiosk-777", first.payload[DiagnosticsLogSupabaseSchema.Columns.KIOSK_ID])
        assertEquals("qa", first.payload[DiagnosticsLogSupabaseSchema.Columns.ENVIRONMENT])
    }

    private class RecordingWriter : DiagnosticsLogOutboxWriter {
        data class Operation(
            val table: String,
            val payload: Map<String, Any?>,
            val operation: String
        )

        val operations = mutableListOf<Operation>()

        override fun enqueue(table: String, payload: Map<String, Any?>, operation: String) {
            operations += Operation(table, payload, operation)
        }
    }
}
