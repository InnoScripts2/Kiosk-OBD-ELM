package com.selfservice.kiosk.diagnostics.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.selfservice.kiosk.R

/**
 * Простая линия-спарклайн для визуализации истории значений метрики.
 */
class DiagnosticsMetricSparklineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.diagnostics_metric_sparkline_stroke)
        color = ContextCompat.getColor(context, R.color.diagnostics_metric_sparkline)
    }

    private var points: List<Float> = emptyList()

    fun render(model: DiagnosticsMetricSparklineModel?) {
        if (model == null || model.points.size < 2) {
            points = emptyList()
            isVisible = false
            invalidate()
            return
        }
        points = model.points
        isVisible = true
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 2) {
            return
        }
        val height = height.toFloat()
        val width = width.toFloat()
        if (height == 0f || width == 0f) {
            return
        }
        val step = width / (points.size - 1)
        path.reset()
        points.forEachIndexed { index, value ->
            val x = index * step
            val y = height - (value.coerceIn(0f, 1f) * height)
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        canvas.drawPath(path, paint)
    }
}
