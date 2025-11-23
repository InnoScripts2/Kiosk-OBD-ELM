package com.selfservice.kiosk.diagnostics.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.selfservice.kiosk.R

/**
 * View плитки рекомендации по диагностике.
 */
class DiagnosticsRecommendationTileView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val severityView: TextView
    private val priorityView: TextView
    private val titleView: TextView
    private val messageView: TextView
    private val thresholdView: TextView
    private val containerView: LinearLayout

    private var boundMetricId: String? = null
    private var boundSelection: DiagnosticsMetricSelection? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_diagnostics_recommendation_tile, this, true)
        containerView = findViewById(R.id.recommendationTileRoot)
        severityView = findViewById(R.id.recommendationSeverity)
        priorityView = findViewById(R.id.recommendationPriority)
        titleView = findViewById(R.id.recommendationTitle)
        messageView = findViewById(R.id.recommendationMessage)
        thresholdView = findViewById(R.id.recommendationThreshold)
    }

    fun bind(model: DiagnosticsRecommendationTileModel) {
        boundMetricId = model.metricId
        boundSelection = DiagnosticsMetricSelection(metricId = model.metricId, pidKey = model.pidKey)
        tag = model.metricId
        severityView.text = model.severityLabel
        severityView.setTextColor(resolveSeverityColor(model.severity))
        priorityView.text = model.priorityLabel
        titleView.text = model.title
        messageView.text = model.message
        val hasThreshold = !model.thresholdSummary.isNullOrBlank()
        thresholdView.text = model.thresholdSummary
        thresholdView.isVisible = hasThreshold
    }

    fun setHighlighted(highlighted: Boolean) {
        containerView.isSelected = highlighted
    }

    fun metricId(): String? = boundMetricId

    fun selection(): DiagnosticsMetricSelection? = boundSelection

    fun matchesSelection(selection: DiagnosticsMetricSelection?): Boolean {
        val bound = boundSelection ?: return false
        return selection?.matches(bound.metricId, bound.pidKey) == true
    }

    private fun resolveSeverityColor(severity: DiagnosticsRecommendationUiSeverity): Int {
        val colorRes = when (severity) {
            DiagnosticsRecommendationUiSeverity.INFO -> R.color.diagnostics_metric_unknown
            DiagnosticsRecommendationUiSeverity.WARNING -> R.color.diagnostics_metric_warning
            DiagnosticsRecommendationUiSeverity.CRITICAL -> R.color.diagnostics_metric_critical
        }
        return ContextCompat.getColor(context, colorRes)
    }
}
