package com.selfservice.kiosk.diagnostics.ui

/**
 * UI-модель плитки рекомендации по диагностике.
 */
data class DiagnosticsRecommendationTileModel(
    val metricId: String,
    val pidKey: String? = null,
    val title: String,
    val message: String,
    val severity: DiagnosticsRecommendationUiSeverity,
    val severityLabel: String,
    val priorityLabel: String,
    val thresholdSummary: String?
)

enum class DiagnosticsRecommendationUiSeverity {
    INFO,
    WARNING,
    CRITICAL
}
