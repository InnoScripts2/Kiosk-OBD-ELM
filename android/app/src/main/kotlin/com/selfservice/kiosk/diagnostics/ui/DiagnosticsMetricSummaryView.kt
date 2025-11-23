package com.selfservice.kiosk.diagnostics.ui

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.selfservice.kiosk.R
import com.selfservice.kiosk.diagnostics.summary.DiagnosticsMetricSummary

/**
 * Отображает сводку по расчётным метрикам: количество критических, предупреждений и отсутствующих данных.
 */
class DiagnosticsMetricSummaryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val indicatorView: View
    private val titleView: TextView
    private val detailsView: TextView

    init {
        orientation = HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.view_diagnostics_metric_summary, this, true)
        indicatorView = findViewById(R.id.metricSummaryIndicator)
        titleView = findViewById(R.id.metricSummaryTitle)
        detailsView = findViewById(R.id.metricSummaryDetails)
        isVisible = false
    }

    fun render(models: List<DiagnosticsMetricTileModel>) {
        val summary = DiagnosticsMetricSummary.fromTileModels(models)
        if (!summary.hasData) {
            isVisible = false
            contentDescription = null
            return
        }

        val uiState = summary.asUiState()
        isVisible = true
        titleView.text = uiState.title
        detailsView.text = uiState.details
        detailsView.isVisible = uiState.details.isNotBlank()
        ViewCompat.setBackgroundTintList(
            indicatorView,
            ColorStateList.valueOf(uiState.indicatorColor)
        )
        contentDescription = uiState.contentDescription
    }

    private fun DiagnosticsMetricSummary.asUiState(): SummaryState {
        val title = when (severity) {
            DiagnosticsMetricSummary.Severity.CRITICAL ->
                resources.getString(R.string.diagnostics_metric_summary_title_critical, criticalCount, totalCount)
            DiagnosticsMetricSummary.Severity.WARNING ->
                resources.getString(R.string.diagnostics_metric_summary_title_warning, warningCount, totalCount)
            DiagnosticsMetricSummary.Severity.UNKNOWN ->
                resources.getString(R.string.diagnostics_metric_summary_title_unknown, unknownCount, totalCount)
            DiagnosticsMetricSummary.Severity.NORMAL ->
                resources.getString(R.string.diagnostics_metric_summary_title_normal, totalCount)
        }
        val details = resources.getString(
            R.string.diagnostics_metric_summary_details,
            criticalCount,
            warningCount,
            unknownCount,
            normalCount
        )
        val indicatorColorRes = when (severity) {
            DiagnosticsMetricSummary.Severity.CRITICAL -> R.color.diagnostics_metric_critical
            DiagnosticsMetricSummary.Severity.WARNING -> R.color.diagnostics_metric_warning
            DiagnosticsMetricSummary.Severity.UNKNOWN -> R.color.diagnostics_metric_unknown
            DiagnosticsMetricSummary.Severity.NORMAL -> R.color.diagnostics_metric_ok
        }
        val indicatorColor = ContextCompat.getColor(context, indicatorColorRes)
        val description = buildString {
            append(title)
            if (details.isNotBlank()) {
                append('.').append(' ')
                append(details)
            }
        }
        return SummaryState(title, details, indicatorColor, description)
    }

    private data class SummaryState(
        val title: String,
        val details: String,
        val indicatorColor: Int,
        val contentDescription: String
    )
}
