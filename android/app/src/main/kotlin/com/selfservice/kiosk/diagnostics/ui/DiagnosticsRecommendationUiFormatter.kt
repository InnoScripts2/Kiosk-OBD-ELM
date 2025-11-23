package com.selfservice.kiosk.diagnostics.ui

import android.content.res.Resources
import com.selfservice.kiosk.R
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendation
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendationPriority
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendationSeverity
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendationThreshold
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendationThresholdType
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Формирует UI-модель плиток рекомендаций на основе расчётных метрик.
 */
class DiagnosticsRecommendationUiFormatter(private val resources: Resources) {

    private val thresholdValueFormat = DecimalFormat(
        "0.##",
        DecimalFormatSymbols(Locale("ru", "RU"))
    )

    fun map(recommendation: DiagnosticsRecommendation): DiagnosticsRecommendationTileModel {
        return DiagnosticsRecommendationTileModel(
            metricId = recommendation.metricId,
            pidKey = recommendation.pidKey,
            title = recommendation.title,
            message = recommendation.message,
            severity = mapSeverity(recommendation.severity),
            severityLabel = formatSeverityLabel(recommendation.severity),
            priorityLabel = formatPriorityLabel(recommendation.priority),
            thresholdSummary = formatThresholdSummary(recommendation.threshold)
        )
    }

    private fun formatSeverityLabel(severity: DiagnosticsRecommendationSeverity): String {
        val resId = when (severity) {
            DiagnosticsRecommendationSeverity.INFO -> R.string.diagnostics_recommendation_severity_info
            DiagnosticsRecommendationSeverity.WARNING -> R.string.diagnostics_recommendation_severity_warning
            DiagnosticsRecommendationSeverity.CRITICAL -> R.string.diagnostics_recommendation_severity_critical
        }
        return resources.getString(resId)
    }

    private fun mapSeverity(severity: DiagnosticsRecommendationSeverity): DiagnosticsRecommendationUiSeverity {
        return when (severity) {
            DiagnosticsRecommendationSeverity.INFO -> DiagnosticsRecommendationUiSeverity.INFO
            DiagnosticsRecommendationSeverity.WARNING -> DiagnosticsRecommendationUiSeverity.WARNING
            DiagnosticsRecommendationSeverity.CRITICAL -> DiagnosticsRecommendationUiSeverity.CRITICAL
        }
    }

    private fun formatPriorityLabel(priority: DiagnosticsRecommendationPriority): String {
        val resId = when (priority) {
            DiagnosticsRecommendationPriority.HIGH -> R.string.diagnostics_recommendation_priority_high
            DiagnosticsRecommendationPriority.MEDIUM -> R.string.diagnostics_recommendation_priority_medium
            DiagnosticsRecommendationPriority.LOW -> R.string.diagnostics_recommendation_priority_low
        }
        return resources.getString(resId)
    }

    private fun formatThresholdSummary(threshold: DiagnosticsRecommendationThreshold?): String? {
        threshold ?: return null
        val comparator = when (threshold.type) {
            DiagnosticsRecommendationThresholdType.LOWER_OR_EQUAL -> "<="
            DiagnosticsRecommendationThresholdType.GREATER_OR_EQUAL -> ">="
        }
        val valueText = thresholdValueFormat.format(threshold.value)
        val unitSuffix = threshold.unit?.takeIf { it.isNotBlank() }
        val formattedValue = if (unitSuffix != null) {
            "$comparator $valueText $unitSuffix"
        } else {
            "$comparator $valueText"
        }
        return resources.getString(R.string.diagnostics_recommendation_threshold_template, formattedValue)
    }
}
