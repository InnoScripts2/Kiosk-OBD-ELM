package com.selfservice.kiosk.diagnostics.summary

import com.selfservice.kiosk.diagnostics.ui.DiagnosticsMetricSeverity
import com.selfservice.kiosk.diagnostics.ui.DiagnosticsMetricTileModel
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricInsight
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricStatus

/**
 * Универсальная сводка по набору диагностических метрик.
 */
data class DiagnosticsMetricSummary(
    val totalCount: Int,
    val criticalCount: Int,
    val warningCount: Int,
    val unknownCount: Int,
    val normalCount: Int,
) {

    val severity: Severity = when {
        criticalCount > 0 -> Severity.CRITICAL
        warningCount > 0 -> Severity.WARNING
        unknownCount > 0 -> Severity.UNKNOWN
        else -> Severity.NORMAL
    }

    val hasData: Boolean = totalCount > 0

    enum class Severity { CRITICAL, WARNING, UNKNOWN, NORMAL }

    companion object {
        val EMPTY = DiagnosticsMetricSummary(
            totalCount = 0,
            criticalCount = 0,
            warningCount = 0,
            unknownCount = 0,
            normalCount = 0,
        )

        fun fromTileModels(models: List<DiagnosticsMetricTileModel>): DiagnosticsMetricSummary {
            if (models.isEmpty()) {
                return EMPTY
            }
            var critical = 0
            var warning = 0
            var unknown = 0
            models.forEach { model ->
                when (model.severity) {
                    DiagnosticsMetricSeverity.CRITICAL -> critical += 1
                    DiagnosticsMetricSeverity.WARNING -> warning += 1
                    DiagnosticsMetricSeverity.UNKNOWN -> unknown += 1
                    DiagnosticsMetricSeverity.NORMAL -> Unit
                }
            }
            val total = models.size
            val normal = (total - critical - warning - unknown).coerceAtLeast(0)
            return DiagnosticsMetricSummary(
                totalCount = total,
                criticalCount = critical,
                warningCount = warning,
                unknownCount = unknown,
                normalCount = normal,
            )
        }

        fun fromInsights(metrics: List<DiagnosticsMetricInsight>): DiagnosticsMetricSummary {
            if (metrics.isEmpty()) {
                return EMPTY
            }
            var critical = 0
            var warning = 0
            var unknown = 0
            var normal = 0
            metrics.forEach { insight ->
                when (insight.status) {
                    DiagnosticsMetricStatus.NO_DATA -> unknown += 1
                    DiagnosticsMetricStatus.CRITICAL_LOW,
                    DiagnosticsMetricStatus.CRITICAL_HIGH -> critical += 1
                    DiagnosticsMetricStatus.WARNING_LOW,
                    DiagnosticsMetricStatus.WARNING_HIGH -> warning += 1
                    DiagnosticsMetricStatus.OK -> normal += 1
                }
            }
            val total = metrics.size
            return DiagnosticsMetricSummary(
                totalCount = total,
                criticalCount = critical,
                warningCount = warning,
                unknownCount = unknown,
                normalCount = normal,
            )
        }
    }
}
