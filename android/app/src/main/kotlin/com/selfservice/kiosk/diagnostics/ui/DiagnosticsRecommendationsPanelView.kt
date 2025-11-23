package com.selfservice.kiosk.diagnostics.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

/**
 * Контейнер для отображения рекомендаций по диагностике.
 */
class DiagnosticsRecommendationsPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var highlightedSelection: DiagnosticsMetricSelection? = null
    private var onRecommendationSelected: ((DiagnosticsMetricSelection) -> Unit)? = null

    init {
        orientation = VERTICAL
    }

    fun render(models: List<DiagnosticsRecommendationTileModel>) {
        visibility = if (models.isEmpty()) View.GONE else View.VISIBLE
        ensureChildren(models.size)
        models.forEachIndexed { index, model ->
            val tile = getChildAt(index) as DiagnosticsRecommendationTileView
            tile.bind(model)
            tile.setHighlighted(tile.matchesSelection(highlightedSelection))
            val listener = onRecommendationSelected
            if (listener != null) {
                tile.isClickable = true
                tile.isFocusable = true
                val selection = tile.selection()
                tile.setOnClickListener { selection?.let(listener) }
            } else {
                tile.isClickable = false
                tile.isFocusable = false
                tile.setOnClickListener(null)
            }
        }
        trimChildren(models.size)
        val currentSelection = highlightedSelection
        if (currentSelection != null && models.none { currentSelection.matches(it.metricId, it.pidKey) }) {
            highlightedSelection = null
        }
    }

    fun setOnRecommendationSelectedListener(listener: ((DiagnosticsMetricSelection) -> Unit)?) {
        onRecommendationSelected = listener
        refreshClickListeners()
    }

    fun highlightMetric(selection: DiagnosticsMetricSelection?) {
        highlightedSelection = selection
        forEachTile { tile ->
            tile.setHighlighted(tile.matchesSelection(selection))
        }
    }

    private fun ensureChildren(count: Int) {
        while (childCount < count) {
            val tile = DiagnosticsRecommendationTileView(context)
            addView(tile, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    private fun trimChildren(targetCount: Int) {
        val surplus = childCount - targetCount
        if (surplus <= 0) {
            return
        }
        removeViews(targetCount, surplus)
    }

    private fun refreshClickListeners() {
        forEachTile { tile ->
            val listener = onRecommendationSelected
            if (listener != null && tile.selection() != null) {
                tile.isClickable = true
                tile.isFocusable = true
                val selection = tile.selection()
                tile.setOnClickListener { selection?.let(listener) }
            } else {
                tile.isClickable = false
                tile.isFocusable = false
                tile.setOnClickListener(null)
            }
        }
    }

    private inline fun forEachTile(block: (DiagnosticsRecommendationTileView) -> Unit) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child is DiagnosticsRecommendationTileView) {
                block(child)
            }
        }
    }
}
