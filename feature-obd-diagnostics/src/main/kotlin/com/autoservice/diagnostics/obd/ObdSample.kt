package com.autoservice.diagnostics.obd

/**
 * Represents a single PID measurement captured from an adapter.
 */
data class ObdSample(
    val pid: PID,
    val value: Double,
    val unit: String? = pid.unit,
    val rawResponse: String,
    val timestampMillis: Long,
)
