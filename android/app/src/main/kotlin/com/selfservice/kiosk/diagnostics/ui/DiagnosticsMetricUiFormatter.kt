package com.selfservice.kiosk.diagnostics.ui

import android.content.res.Resources
import com.selfservice.kiosk.R
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricCategory
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricInsight
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricStatus
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricTrend
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import java.util.concurrent.TimeUnit

/**
 * Формирует UI-модель плиток диагностических метрик.
 */
class DiagnosticsMetricUiFormatter(private val resources: Resources) {

    fun map(
        insight: DiagnosticsMetricInsight,
        trend: DiagnosticsMetricTrend? = null
    ): DiagnosticsMetricTileModel {
        val unit = insight.unit ?: insight.definition.unit
        val sparklineModel = mapSparkline(trend)
        return DiagnosticsMetricTileModel(
            id = insight.definition.id,
            pidKey = insight.definition.normalizedKey,
            title = insight.definition.label,
            valueText = formatValue(insight.value),
            unitText = unit,
            statusLabel = formatStatusLabel(insight.status),
            adviceText = insight.advice,
            severity = mapSeverity(insight.status),
            category = insight.definition.category,
            categoryLabel = formatCategoryLabel(insight.definition.category),
            sparkline = sparklineModel,
            sparklineLabel = formatSparklineLabel(trend, sparklineModel)
        )
    }

    private fun formatValue(value: Double?): String {
        if (value == null) {
            return resources.getString(R.string.diagnostics_metric_value_placeholder)
        }
        val magnitude = abs(value)
        val pattern = if (magnitude >= 100) "%.0f" else "%.1f"
        return String.format(Locale.US, pattern, value)
    }

    private fun formatStatusLabel(status: DiagnosticsMetricStatus): String {
        val resId = when (status) {
            DiagnosticsMetricStatus.OK -> R.string.diagnostics_metric_status_ok
            DiagnosticsMetricStatus.WARNING_LOW -> R.string.diagnostics_metric_status_warning_low
            DiagnosticsMetricStatus.WARNING_HIGH -> R.string.diagnostics_metric_status_warning_high
            DiagnosticsMetricStatus.CRITICAL_LOW -> R.string.diagnostics_metric_status_critical_low
            DiagnosticsMetricStatus.CRITICAL_HIGH -> R.string.diagnostics_metric_status_critical_high
            DiagnosticsMetricStatus.NO_DATA -> R.string.diagnostics_metric_status_no_data
        }
        return resources.getString(resId)
    }

    private fun mapSeverity(status: DiagnosticsMetricStatus): DiagnosticsMetricSeverity {
        return when (status) {
            DiagnosticsMetricStatus.OK -> DiagnosticsMetricSeverity.NORMAL
            DiagnosticsMetricStatus.WARNING_LOW,
            DiagnosticsMetricStatus.WARNING_HIGH -> DiagnosticsMetricSeverity.WARNING
            DiagnosticsMetricStatus.CRITICAL_LOW,
            DiagnosticsMetricStatus.CRITICAL_HIGH -> DiagnosticsMetricSeverity.CRITICAL
            DiagnosticsMetricStatus.NO_DATA -> DiagnosticsMetricSeverity.UNKNOWN
        }
    }

    private fun formatCategoryLabel(category: DiagnosticsMetricCategory): String {
        val resId = when (category) {
            DiagnosticsMetricCategory.ELECTRICAL -> R.string.diagnostics_metric_category_electrical
            DiagnosticsMetricCategory.THERMAL -> R.string.diagnostics_metric_category_thermal
            DiagnosticsMetricCategory.FUEL_SYSTEM -> R.string.diagnostics_metric_category_fuel
            DiagnosticsMetricCategory.AIRFLOW -> R.string.diagnostics_metric_category_airflow
            DiagnosticsMetricCategory.PRESSURE -> R.string.diagnostics_metric_category_pressure
            DiagnosticsMetricCategory.GENERAL -> R.string.diagnostics_metric_category_general
        }
        return resources.getString(resId)
    }

    private fun mapSparkline(trend: DiagnosticsMetricTrend?): DiagnosticsMetricSparklineModel? {
        trend ?: return null
        if (trend.points.size < 2) {
            return null
        }
        val values = trend.points.map { it.value }
        val minValue = values.minOrNull() ?: return null
        val maxValue = values.maxOrNull() ?: return null
        val range = max(maxValue - minValue, 0.0)
        val normalized = if (range == 0.0) {
            values.map { 0.5f }
        } else {
            values.map { value ->
                val clamped = min(maxValue, max(minValue, value))
                ((clamped - minValue) / range).toFloat()
            }
        }
        return DiagnosticsMetricSparklineModel(points = normalized)
    }

    private fun formatSparklineLabel(
        trend: DiagnosticsMetricTrend?,
        sparkline: DiagnosticsMetricSparklineModel?
    ): String? {
        if (trend == null || sparkline == null) {
            return null
        }
        val count = trend.points.size
        if (count < 2) {
            return null
        }
        val spanMillis = (trend.points.last().timestampMillis - trend.points.first().timestampMillis)
            .coerceAtLeast(0L)
        val spanLabel = formatTrendSpan(spanMillis)
        return if (spanLabel != null) {
            resources.getString(
                R.string.diagnostics_metric_sparkline_label_with_span,
                count,
                spanLabel
            )
        } else {
            resources.getString(R.string.diagnostics_metric_sparkline_label, count)
        }
    }

    private fun formatTrendSpan(spanMillis: Long): String? {
        if (spanMillis <= 0L) {
            return null
        }
        val days = TimeUnit.MILLISECONDS.toDays(spanMillis)
        if (days > 0L) {
            return resources.getQuantityString(
                R.plurals.diagnostics_metric_sparkline_span_days,
                days.toInt(),
                days
            )
        }
        val hours = TimeUnit.MILLISECONDS.toHours(spanMillis)
        if (hours > 0L) {
            return resources.getQuantityString(
                R.plurals.diagnostics_metric_sparkline_span_hours,
                hours.toInt(),
                hours
            )
        }
        val minutes = TimeUnit.MILLISECONDS.toMinutes(spanMillis)
        if (minutes > 0L) {
            return resources.getQuantityString(
                R.plurals.diagnostics_metric_sparkline_span_minutes,
                minutes.toInt(),
                minutes
            )
        }
        val seconds = TimeUnit.MILLISECONDS.toSeconds(spanMillis)
        if (seconds > 0L) {
            return resources.getQuantityString(
                R.plurals.diagnostics_metric_sparkline_span_seconds,
                seconds.toInt(),
                seconds
            )
        }
        return null
    }
}

data class DiagnosticsMetricSparklineModel(
    val points: List<Float>
)
