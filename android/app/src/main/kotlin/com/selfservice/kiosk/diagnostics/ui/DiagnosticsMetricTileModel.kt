package com.selfservice.kiosk.diagnostics.ui

import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricCategory

/**
 * UI-модель отдельной плитки метрики.
 */
data class DiagnosticsMetricTileModel(
    val id: String,
    val pidKey: String? = null,
    val title: String,
    val valueText: String,
    val unitText: String?,
    val statusLabel: String,
    val adviceText: String,
    val severity: DiagnosticsMetricSeverity,
    val category: DiagnosticsMetricCategory = DiagnosticsMetricCategory.GENERAL,
    val categoryLabel: String? = null,
    val sparkline: DiagnosticsMetricSparklineModel? = null,
    val sparklineLabel: String? = null
)
