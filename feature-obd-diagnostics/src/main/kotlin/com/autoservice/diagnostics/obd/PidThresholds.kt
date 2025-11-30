package com.autoservice.diagnostics.obd

/**
 * Optional threshold boundaries for interpreting PID samples.
 */
data class PidThresholds(
    val warningLow: Double? = null,
    val criticalLow: Double? = null,
    val warningHigh: Double? = null,
    val criticalHigh: Double? = null
) {
    val isEmpty: Boolean
        get() = warningLow == null && criticalLow == null && warningHigh == null && criticalHigh == null
}
