package com.selfservice.platform.data.diagnostics

/** Represents a persisted telemetry event emitted during diagnostic sessions. */
data class DiagnosticsTelemetryRecord(
    val timestampMillis: Long,
    val eventType: String,
    val sessionId: String?,
    val metadata: Map<String, Any?> = emptyMap()
)
