package com.selfservice.kiosk.diagnostics.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

/**
 * Контейнер для отображения списка расчётных метрик диагностики.
 */
class DiagnosticsMetricsPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var highlightedSelection: DiagnosticsMetricSelection? = null
    private var onTileClickListener: ((DiagnosticsMetricSelection) -> Unit)? = null

    init {
        orientation = VERTICAL
    }

    fun render(models: List<DiagnosticsMetricTileModel>) {
        visibility = if (models.isEmpty()) View.GONE else View.VISIBLE
        ensureChildren(models.size)
        models.forEachIndexed { index, model ->
            val tile = getChildAt(index) as DiagnosticsMetricTileView
            tile.bind(model)
            tile.setHighlighted(tile.matchesSelection(highlightedSelection))
            val listener = onTileClickListener
            if (listener != null) {
                tile.isClickable = true
                tile.isFocusable = true
                val selection = tile.selection()
                tile.setOnClickListener {
                    selection?.let(listener)
                }
            } else {
                tile.isClickable = false
                tile.isFocusable = false
                tile.setOnClickListener(null)
            }
        }
        trimChildren(models.size)
        val currentSelection = highlightedSelection
        if (currentSelection != null && models.none { currentSelection.matches(it.id, it.pidKey) }) {
            highlightedSelection = null
        }
    }

    fun setOnTileClickListener(listener: ((DiagnosticsMetricSelection) -> Unit)?) {
        onTileClickListener = listener
        refreshTileListeners()
    }

    fun highlightMetric(selection: DiagnosticsMetricSelection?) {
        highlightedSelection = selection
        forEachTile { tile ->
            tile.setHighlighted(tile.matchesSelection(selection))
        }
    }

    private fun ensureChildren(count: Int) {
        while (childCount < count) {
            val tile = DiagnosticsMetricTileView(context)
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

    private fun refreshTileListeners() {
        forEachTile { tile ->
            val listener = onTileClickListener
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

    private inline fun forEachTile(block: (DiagnosticsMetricTileView) -> Unit) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child is DiagnosticsMetricTileView) {
                block(child)
            }
        }
    }
}
