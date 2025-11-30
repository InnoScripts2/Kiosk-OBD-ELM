package com.selfservice.feature.reports

import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricInsight
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricStatus
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsProfileSnapshot
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendation
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendationPriority
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendationSeverity
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendationThreshold
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendationThresholdType
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

data class ReportViewModel(
    val sessionId: String,
    val generatedAt: ZonedDateTime,
    val vehicleLine: String?,
    val vehicleVin: String?,
    val contacts: List<String>,
    val summary: ReportSummary,
    val metrics: List<ReportMetricRow>,
    val recommendations: List<ReportRecommendationRow>
)

data class ReportSummary(
    val total: Int,
    val normal: Int,
    val warning: Int,
    val critical: Int,
    val noData: Int
)

data class ReportMetricRow(
    val title: String,
    val valueText: String,
    val statusLabel: String,
    val severityLabel: String,
    val advice: String
)

data class ReportRecommendationRow(
    val title: String,
    val severityLabel: String,
    val priorityLabel: String,
    val message: String,
    val thresholdLabel: String?
)

class DiagnosticsReportViewModelMapper(
    private val locale: Locale = Locale("ru", "RU"),
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd MMMM yyyy HH:mm", locale)

    internal fun map(input: DiagnosticsReportInput): ReportViewModel {
        val generatedAt = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(input.generatedAtMillis),
            zoneId
        )
        val metrics = input.snapshot.metrics.map { metric ->
            ReportMetricRow(
                title = metric.definition.label,
                valueText = formatValue(metric),
                statusLabel = statusLabel(metric.status),
                severityLabel = severityLabel(metric.status),
                advice = metric.advice.trim()
            )
        }
        val recommendations = input.recommendations.map { recommendation ->
            ReportRecommendationRow(
                title = recommendation.title,
                severityLabel = recommendationSeverityLabel(recommendation.severity),
                priorityLabel = recommendationPriorityLabel(recommendation.priority),
                message = recommendation.message.trim(),
                thresholdLabel = recommendation.threshold?.let(::formatThreshold)
            )
        }
        val summary = buildSummary(input.snapshot, metrics)
        val vehicleLine = input.vehicle?.let { vehicle ->
            val parts = listOfNotNull(
                vehicle.make.ifBlank { null },
                vehicle.model?.takeIf { it.isNotBlank() },
                vehicle.year?.takeIf { it > 0 }?.toString()
            )
            parts.joinToString(separator = " ").ifBlank { null }
        }
        val vehicleVin = input.vehicle?.vin?.takeIf { it.isNotBlank() }
        val contacts = buildContacts(input)
        return ReportViewModel(
            sessionId = input.sessionId,
            generatedAt = generatedAt,
            vehicleLine = vehicleLine,
            vehicleVin = vehicleVin,
            contacts = contacts,
            summary = summary,
            metrics = metrics,
            recommendations = recommendations
        )
    }

    private fun buildSummary(
        snapshot: DiagnosticsProfileSnapshot,
        metrics: List<ReportMetricRow>
    ): ReportSummary {
        val total = metrics.size
        var normal = 0
        var warning = 0
        var critical = 0
        var noData = 0
        snapshot.metrics.forEach { insight ->
            when (insight.status) {
                DiagnosticsMetricStatus.OK -> normal += 1
                DiagnosticsMetricStatus.WARNING_HIGH,
                DiagnosticsMetricStatus.WARNING_LOW -> warning += 1
                DiagnosticsMetricStatus.CRITICAL_HIGH,
                DiagnosticsMetricStatus.CRITICAL_LOW -> critical += 1
                DiagnosticsMetricStatus.NO_DATA -> noData += 1
            }
        }
        return ReportSummary(
            total = total,
            normal = normal,
            warning = warning,
            critical = critical,
            noData = noData
        )
    }

    private fun buildContacts(input: DiagnosticsReportInput): List<String> {
        val contacts = mutableListOf<String>()
        input.customer?.phone?.takeIf { it.isNotBlank() }?.let { phone ->
            contacts += "Телефон: ${phone.trim()}"
        }
        input.customer?.email?.takeIf { it.isNotBlank() }?.let { email ->
            contacts += "Email: ${email.trim()}"
        }
        return contacts
    }

    private fun formatValue(metric: DiagnosticsMetricInsight): String {
        val value = metric.value ?: return "—"
        val unit = metric.unit ?: metric.definition.unit
        val magnitude = abs(value)
        val pattern = if (magnitude >= 100.0) "%.0f" else "%.1f"
        val formatted = String.format(Locale.US, pattern, value)
        return if (unit.isNullOrBlank()) {
            formatted
        } else {
            "$formatted ${unit.trim()}"
        }
    }

    private fun formatThreshold(threshold: DiagnosticsRecommendationThreshold): String {
        val comparator = when (threshold.type) {
            DiagnosticsRecommendationThresholdType.LOWER_OR_EQUAL -> "≤"
            DiagnosticsRecommendationThresholdType.GREATER_OR_EQUAL -> "≥"
        }
        val value = String.format(Locale.US, "%.2f", threshold.value)
        val unit = threshold.unit?.takeIf { it.isNotBlank() }
        return listOfNotNull("Порог", "$comparator $value", unit).joinToString(separator = " ")
    }

    private fun statusLabel(status: DiagnosticsMetricStatus): String = when (status) {
        DiagnosticsMetricStatus.OK -> "Норма"
        DiagnosticsMetricStatus.WARNING_LOW -> "Ниже нормы"
        DiagnosticsMetricStatus.WARNING_HIGH -> "Выше нормы"
        DiagnosticsMetricStatus.CRITICAL_LOW -> "Критически низко"
        DiagnosticsMetricStatus.CRITICAL_HIGH -> "Критически высоко"
        DiagnosticsMetricStatus.NO_DATA -> "Нет данных"
    }

    private fun severityLabel(status: DiagnosticsMetricStatus): String = when (status) {
        DiagnosticsMetricStatus.OK -> "NORMAL"
        DiagnosticsMetricStatus.WARNING_LOW,
        DiagnosticsMetricStatus.WARNING_HIGH -> "WARNING"
        DiagnosticsMetricStatus.CRITICAL_LOW,
        DiagnosticsMetricStatus.CRITICAL_HIGH -> "CRITICAL"
        DiagnosticsMetricStatus.NO_DATA -> "UNKNOWN"
    }

    private fun recommendationSeverityLabel(
        severity: DiagnosticsRecommendationSeverity
    ): String = when (severity) {
        DiagnosticsRecommendationSeverity.INFO -> "Информация"
        DiagnosticsRecommendationSeverity.WARNING -> "Внимание"
        DiagnosticsRecommendationSeverity.CRITICAL -> "Критично"
    }

    private fun recommendationPriorityLabel(
        priority: DiagnosticsRecommendationPriority
    ): String = when (priority) {
        DiagnosticsRecommendationPriority.LOW -> "Низкий приоритет"
        DiagnosticsRecommendationPriority.MEDIUM -> "Средний приоритет"
        DiagnosticsRecommendationPriority.HIGH -> "Высокий приоритет"
    }

    internal fun formatGeneratedAt(viewModel: ReportViewModel): String {
        return dateFormatter.format(viewModel.generatedAt)
    }
}
