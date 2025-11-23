package com.selfservice.kiosk.diagnostics.ui

/**
 * Унифицированный идентификатор диагностической метрики или рекомендации.
 */
data class DiagnosticsMetricSelection(
    val metricId: String? = null,
    val pidKey: String? = null
) {
    fun matches(targetMetricId: String?, targetPidKey: String?): Boolean {
        if (metricId != null && metricId.isNotBlank() && metricId == targetMetricId) {
            return true
        }
        if (!pidKey.isNullOrBlank() && !targetPidKey.isNullOrBlank()) {
            return pidKey.equals(targetPidKey, ignoreCase = true)
        }
        return false
    }

    companion object {
        val None = DiagnosticsMetricSelection()
    }
}
