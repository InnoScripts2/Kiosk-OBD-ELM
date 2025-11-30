package com.selfservice.platform.data.diagnostics.profile

/**
 * Преобразует срез диагностических метрик в список рекомендаций.
 */
class DiagnosticsRecommendationEngine {

    fun recommendations(snapshot: DiagnosticsProfileSnapshot?): List<DiagnosticsRecommendation> {
        if (snapshot == null) {
            return emptyList()
        }
        val orderById = snapshot.metrics.mapIndexed { index, insight ->
            insight.definition.id to index
        }.toMap()
        val recommendations = snapshot.metrics.mapNotNull { insight ->
            val status = insight.status
            val message = insight.advice.trim()
            if (status == DiagnosticsMetricStatus.OK) {
                return@mapNotNull null
            }
            if (message.isEmpty()) {
                return@mapNotNull null
            }
            val severity = mapSeverity(status)
            DiagnosticsRecommendation(
                metricId = insight.definition.id,
                pidKey = insight.definition.normalizedKey,
                title = insight.definition.label,
                message = message,
                severity = severity,
                status = status,
                priority = mapPriority(severity),
                threshold = buildThreshold(insight)
            )
        }
        if (recommendations.isEmpty()) {
            return emptyList()
        }
        val severityWeight = mapOf(
            DiagnosticsRecommendationSeverity.CRITICAL to 0,
            DiagnosticsRecommendationSeverity.WARNING to 1,
            DiagnosticsRecommendationSeverity.INFO to 2
        )
        return recommendations.sortedWith(
            compareBy<DiagnosticsRecommendation> { severityWeight[it.severity] ?: Int.MAX_VALUE }
                .thenBy { orderById[it.metricId] ?: Int.MAX_VALUE }
        )
    }

    private fun mapSeverity(status: DiagnosticsMetricStatus): DiagnosticsRecommendationSeverity {
        return when (status) {
            DiagnosticsMetricStatus.CRITICAL_LOW,
            DiagnosticsMetricStatus.CRITICAL_HIGH -> DiagnosticsRecommendationSeverity.CRITICAL
            DiagnosticsMetricStatus.WARNING_LOW,
            DiagnosticsMetricStatus.WARNING_HIGH -> DiagnosticsRecommendationSeverity.WARNING
            DiagnosticsMetricStatus.NO_DATA -> DiagnosticsRecommendationSeverity.INFO
            DiagnosticsMetricStatus.OK -> DiagnosticsRecommendationSeverity.INFO
        }
    }

    private fun mapPriority(severity: DiagnosticsRecommendationSeverity): DiagnosticsRecommendationPriority {
        return when (severity) {
            DiagnosticsRecommendationSeverity.CRITICAL -> DiagnosticsRecommendationPriority.HIGH
            DiagnosticsRecommendationSeverity.WARNING -> DiagnosticsRecommendationPriority.MEDIUM
            DiagnosticsRecommendationSeverity.INFO -> DiagnosticsRecommendationPriority.LOW
        }
    }

    private fun buildThreshold(insight: DiagnosticsMetricInsight): DiagnosticsRecommendationThreshold? {
        val unit = insight.unit ?: insight.definition.unit
        return when (insight.status) {
            DiagnosticsMetricStatus.CRITICAL_LOW -> insight.definition.thresholds.criticalLow?.let {
                DiagnosticsRecommendationThreshold(
                    type = DiagnosticsRecommendationThresholdType.LOWER_OR_EQUAL,
                    value = it,
                    unit = unit
                )
            }
            DiagnosticsMetricStatus.WARNING_LOW -> insight.definition.thresholds.warningLow?.let {
                DiagnosticsRecommendationThreshold(
                    type = DiagnosticsRecommendationThresholdType.LOWER_OR_EQUAL,
                    value = it,
                    unit = unit
                )
            }
            DiagnosticsMetricStatus.WARNING_HIGH -> insight.definition.thresholds.warningHigh?.let {
                DiagnosticsRecommendationThreshold(
                    type = DiagnosticsRecommendationThresholdType.GREATER_OR_EQUAL,
                    value = it,
                    unit = unit
                )
            }
            DiagnosticsMetricStatus.CRITICAL_HIGH -> insight.definition.thresholds.criticalHigh?.let {
                DiagnosticsRecommendationThreshold(
                    type = DiagnosticsRecommendationThresholdType.GREATER_OR_EQUAL,
                    value = it,
                    unit = unit
                )
            }
            DiagnosticsMetricStatus.NO_DATA,
            DiagnosticsMetricStatus.OK -> null
        }
    }
}

data class DiagnosticsRecommendation(
    val metricId: String,
    val pidKey: String,
    val title: String,
    val message: String,
    val severity: DiagnosticsRecommendationSeverity,
    val status: DiagnosticsMetricStatus,
    val priority: DiagnosticsRecommendationPriority,
    val threshold: DiagnosticsRecommendationThreshold?
)

enum class DiagnosticsRecommendationSeverity {
    INFO,
    WARNING,
    CRITICAL
}

enum class DiagnosticsRecommendationPriority {
    LOW,
    MEDIUM,
    HIGH
}

data class DiagnosticsRecommendationThreshold(
    val type: DiagnosticsRecommendationThresholdType,
    val value: Double,
    val unit: String?
)

enum class DiagnosticsRecommendationThresholdType {
    LOWER_OR_EQUAL,
    GREATER_OR_EQUAL
}
