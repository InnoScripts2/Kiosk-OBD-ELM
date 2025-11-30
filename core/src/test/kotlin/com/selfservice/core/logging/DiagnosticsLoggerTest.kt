package com.selfservice.core.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class RecordingSink : DiagnosticsLogSink {
    val entries: MutableList<DiagnosticsLogEntry> = mutableListOf()
    val clearedThresholds: MutableList<Long> = mutableListOf()

    override fun persist(entry: DiagnosticsLogEntry) {
        entries += entry
    }

    override fun clearOlderThan(thresholdMillis: Long) {
        clearedThresholds += thresholdMillis
        entries.removeAll { it.timestampMillis < thresholdMillis }
    }
}

class DiagnosticsLoggerTest {

    @Test
    fun recordPersistsEntry() {
        val sink = RecordingSink()
        var now = 100L
        val logger = DiagnosticsLogger(sink) { now }

        val entry = logger.record("session", "started", mapOf("case" to "ft-01"))

        assertEquals("session", entry.category)
        assertEquals("started", entry.message)
        assertEquals(now, entry.timestampMillis)
        assertEquals(mapOf("case" to "ft-01"), entry.metadata)
        assertSame(entry, sink.entries.single())
    }

    @Test
    fun pruneDelegatesToSink() {
        val sink = RecordingSink()
        val logger = DiagnosticsLogger(sink) { 100L }
        sink.entries += DiagnosticsLogEntry("session", "init", 10L)
        sink.entries += DiagnosticsLogEntry("session", "continue", 90L)

        logger.pruneOlderThan(50L)

        assertEquals(listOf(90L), sink.entries.map { it.timestampMillis })
        assertTrue(sink.clearedThresholds.contains(50L))
    }
}
