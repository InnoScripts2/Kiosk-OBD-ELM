package com.selfservice.core.logging

/** Immutable log entry captured during diagnostics or self-tests. */
data class DiagnosticsLogEntry(
        val category: String,
        val message: String,
        val timestampMillis: Long,
        val metadata: Map<String, Any?> = emptyMap()
)

interface DiagnosticsLogSink {
    fun persist(entry: DiagnosticsLogEntry)
    fun clearOlderThan(thresholdMillis: Long)
}

class DiagnosticsLogger(
        private val sink: DiagnosticsLogSink,
        private val clock: () -> Long = { System.currentTimeMillis() }
) {

    fun record(
            category: String,
            message: String,
            metadata: Map<String, Any?> = emptyMap()
    ): DiagnosticsLogEntry {
        val entry =
                DiagnosticsLogEntry(
                        category = category,
                        message = message,
                        timestampMillis = clock(),
                        metadata = metadata
                )
        sink.persist(entry)
        return entry
    }

    fun pruneOlderThan(thresholdMillis: Long) {
        sink.clearOlderThan(thresholdMillis)
    }
}
