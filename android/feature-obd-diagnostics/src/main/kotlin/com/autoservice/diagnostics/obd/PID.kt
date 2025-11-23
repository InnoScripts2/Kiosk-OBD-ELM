package com.autoservice.diagnostics.obd

data class PID(
    val mode: ObdMode,
    val pid: String,
    val description: String,
    val unit: String? = null,
    val formula: (ByteArray) -> Double = { 0.0 },
    val thresholds: PidThresholds? = null
) {
    val key: String get() = "${mode.code}:${pid}".uppercase()
}
