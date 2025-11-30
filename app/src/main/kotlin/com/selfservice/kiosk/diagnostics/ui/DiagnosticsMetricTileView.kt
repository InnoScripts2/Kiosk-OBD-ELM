package com.selfservice.kiosk.diagnostics.ui

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.selfservice.kiosk.R
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricCategory

/**
 * View-представление отдельной метрики.
 */
class DiagnosticsMetricTileView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val titleView: TextView
    private val categoryView: TextView
    private val valueView: TextView
    private val statusView: TextView
    private val adviceView: TextView
    private val sparklineView: DiagnosticsMetricSparklineView
    private val sparklineLabelView: TextView
    private val containerView: LinearLayout

    private var boundMetricId: String? = null
    private var boundSelection: DiagnosticsMetricSelection? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_diagnostics_metric_tile, this, true)
        containerView = findViewById(R.id.metricTileRoot)
        titleView = findViewById(R.id.metricTitle)
        categoryView = findViewById(R.id.metricCategory)
        valueView = findViewById(R.id.metricValue)
        statusView = findViewById(R.id.metricStatus)
        adviceView = findViewById(R.id.metricAdvice)
        sparklineView = findViewById(R.id.metricSparkline)
        sparklineLabelView = findViewById(R.id.metricSparklineLabel)
    }

    fun bind(model: DiagnosticsMetricTileModel) {
        boundMetricId = model.id
        boundSelection = DiagnosticsMetricSelection(metricId = model.id, pidKey = model.pidKey)
        tag = model.id
        titleView.text = model.title
        val categoryLabel = model.categoryLabel
        if (categoryLabel.isNullOrBlank()) {
            categoryView.visibility = View.GONE
        } else {
            categoryView.visibility = View.VISIBLE
            categoryView.text = categoryLabel
            ViewCompat.setBackgroundTintList(
                categoryView,
                ColorStateList.valueOf(resolveCategoryColor(model.category))
            )
        }
        valueView.text = buildValueText(model)
        statusView.text = model.statusLabel
        adviceView.text = model.adviceText
        adviceView.visibility = if (model.adviceText.isBlank()) View.GONE else View.VISIBLE
        statusView.setTextColor(resolveSeverityColor(model.severity))
        val label = model.sparklineLabel
        sparklineLabelView.text = label
        sparklineLabelView.visibility = if (label.isNullOrBlank()) View.GONE else View.VISIBLE
        sparklineView.render(model.sparkline)
    }

    fun setHighlighted(highlighted: Boolean) {
        containerView.isSelected = highlighted
    }

    fun selection(): DiagnosticsMetricSelection? = boundSelection

    fun metricId(): String? = boundMetricId

    fun matchesSelection(selection: DiagnosticsMetricSelection?): Boolean {
        val bound = boundSelection ?: return false
        return selection?.matches(bound.metricId, bound.pidKey) == true
    }

    private fun buildValueText(model: DiagnosticsMetricTileModel): CharSequence {
        val placeholder = resources.getString(R.string.diagnostics_metric_value_placeholder)
        val unit = model.unitText
        return if (!unit.isNullOrBlank() && model.valueText != placeholder) {
            resources.getString(
                R.string.diagnostics_metric_value_with_unit,
                model.valueText,
                unit
            )
        } else {
            model.valueText
        }
    }

    private fun resolveSeverityColor(severity: DiagnosticsMetricSeverity): Int {
        val colorRes = when (severity) {
            DiagnosticsMetricSeverity.NORMAL -> R.color.diagnostics_metric_ok
            DiagnosticsMetricSeverity.WARNING -> R.color.diagnostics_metric_warning
            DiagnosticsMetricSeverity.CRITICAL -> R.color.diagnostics_metric_critical
            DiagnosticsMetricSeverity.UNKNOWN -> R.color.diagnostics_metric_unknown
        }
        return ContextCompat.getColor(context, colorRes)
    }

    private fun resolveCategoryColor(category: DiagnosticsMetricCategory): Int {
        val colorRes = when (category) {
            DiagnosticsMetricCategory.ELECTRICAL -> R.color.diagnostics_metric_category_electrical
            DiagnosticsMetricCategory.THERMAL -> R.color.diagnostics_metric_category_thermal
            DiagnosticsMetricCategory.FUEL_SYSTEM -> R.color.diagnostics_metric_category_fuel
            DiagnosticsMetricCategory.AIRFLOW -> R.color.diagnostics_metric_category_airflow
            DiagnosticsMetricCategory.PRESSURE -> R.color.diagnostics_metric_category_pressure
            DiagnosticsMetricCategory.GENERAL -> R.color.diagnostics_metric_category_general
        }
        return ContextCompat.getColor(context, colorRes)
    }
}
