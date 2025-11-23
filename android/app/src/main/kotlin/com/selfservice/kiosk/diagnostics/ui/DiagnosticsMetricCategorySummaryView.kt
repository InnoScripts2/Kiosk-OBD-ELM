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
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricCategory
import java.util.Locale

class DiagnosticsMetricCategorySummaryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val rowsContainer: LinearLayout
    private val inflater: LayoutInflater = LayoutInflater.from(context)

    init {
        orientation = VERTICAL
        inflater.inflate(R.layout.view_diagnostics_metric_category_summary, this, true)
        rowsContainer = findViewById(R.id.categorySummaryRows)
        isVisible = false
    }

    fun render(models: List<DiagnosticsMetricTileModel>) {
        val summaries = summarize(models)
        if (summaries.isEmpty()) {
            rowsContainer.removeAllViews()
            isVisible = false
            contentDescription = null
            return
        }
        isVisible = true
        rowsContainer.removeAllViews()
        val descriptions = mutableListOf<String>()
        summaries.forEach { summary ->
            val row = inflater.inflate(
                R.layout.item_diagnostics_metric_category_summary,
                rowsContainer,
                false
            )
            bindRow(row, summary)
            rowsContainer.addView(row)
            descriptions += resources.getString(
                R.string.diagnostics_metric_category_summary_row_accessibility,
                summary.label,
                formatDetails(summary.summary)
            )
        }
        contentDescription = descriptions.joinToString(separator = ". ")
    }

    private fun bindRow(view: View, summary: CategorySummary) {
        val indicator = view.findViewById<View>(R.id.categorySummaryIndicator)
        val labelView = view.findViewById<TextView>(R.id.categorySummaryLabel)
        val detailsView = view.findViewById<TextView>(R.id.categorySummaryDetails)
        labelView.text = summary.label
        val details = formatDetails(summary.summary)
        detailsView.text = details
        ViewCompat.setBackgroundTintList(
            indicator,
            ColorStateList.valueOf(resolveSeverityColor(summary.summary.severity))
        )
        view.contentDescription = resources.getString(
            R.string.diagnostics_metric_category_summary_row_accessibility,
            summary.label,
            details
        )
    }

    private fun summarize(models: List<DiagnosticsMetricTileModel>): List<CategorySummary> {
        if (models.isEmpty()) {
            return emptyList()
        }
        val grouped = models.groupBy { it.category }
        return grouped.entries.map { (category, tiles) ->
            val summary = DiagnosticsMetricSummary.fromTileModels(tiles)
            CategorySummary(
                category = category,
                label = tiles.firstNotNullOfOrNull { it.categoryLabel } ?: fallbackLabel(category),
                summary = summary
            )
        }.sortedWith(categoryComparator)
    }

    private fun formatDetails(summary: DiagnosticsMetricSummary): String {
        return resources.getString(
            R.string.diagnostics_metric_summary_details,
            summary.criticalCount,
            summary.warningCount,
            summary.unknownCount,
            summary.normalCount
        )
    }

    private fun resolveSeverityColor(severity: DiagnosticsMetricSummary.Severity): Int {
        val colorRes = when (severity) {
            DiagnosticsMetricSummary.Severity.CRITICAL -> R.color.diagnostics_metric_critical
            DiagnosticsMetricSummary.Severity.WARNING -> R.color.diagnostics_metric_warning
            DiagnosticsMetricSummary.Severity.UNKNOWN -> R.color.diagnostics_metric_unknown
            DiagnosticsMetricSummary.Severity.NORMAL -> R.color.diagnostics_metric_ok
        }
        return ContextCompat.getColor(context, colorRes)
    }

    private fun fallbackLabel(category: DiagnosticsMetricCategory): String {
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

    private data class CategorySummary(
        val category: DiagnosticsMetricCategory,
        val label: String,
        val summary: DiagnosticsMetricSummary
    )

    private val categoryComparator = Comparator<CategorySummary> { left, right ->
        val severityDiff = severityRank(left.summary.severity) - severityRank(right.summary.severity)
        if (severityDiff != 0) {
            severityDiff
        } else {
            left.label.lowercase(Locale.getDefault()).compareTo(right.label.lowercase(Locale.getDefault()))
        }
    }

    private fun severityRank(severity: DiagnosticsMetricSummary.Severity): Int = when (severity) {
        DiagnosticsMetricSummary.Severity.CRITICAL -> 0
        DiagnosticsMetricSummary.Severity.WARNING -> 1
        DiagnosticsMetricSummary.Severity.UNKNOWN -> 2
        DiagnosticsMetricSummary.Severity.NORMAL -> 3
    }
}
