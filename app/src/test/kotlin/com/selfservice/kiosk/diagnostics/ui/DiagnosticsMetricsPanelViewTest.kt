package com.selfservice.kiosk.diagnostics.ui

import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.selfservice.kiosk.R
import com.selfservice.kiosk.TestKioskApp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = TestKioskApp::class)
class DiagnosticsMetricsPanelViewTest {

    @Test
    fun `render toggles visibility and updates tiles`() {
    val baseContext = ApplicationProvider.getApplicationContext<TestKioskApp>()
    val context = ContextThemeWrapper(baseContext, R.style.AppTheme)
    val panel = DiagnosticsMetricsPanelView(context)

        panel.render(emptyList())
        assertEquals(View.GONE, panel.visibility)
        assertEquals(0, panel.childCount)

        val primaryModel = DiagnosticsMetricTileModel(
            id = "voltage",
            title = "Бортовое напряжение",
            valueText = "12.8",
            unitText = "V",
            statusLabel = "Норма",
            adviceText = "",
            severity = DiagnosticsMetricSeverity.NORMAL
        )
        val secondaryModel = primaryModel.copy(id = "temperature", title = "Температура", valueText = "90", unitText = "°C")

        panel.render(listOf(primaryModel, secondaryModel))
        assertEquals(View.VISIBLE, panel.visibility)
        assertEquals(2, panel.childCount)

        val firstTile = panel.getChildAt(0) as DiagnosticsMetricTileView
        val titleView = firstTile.findViewById<TextView>(R.id.metricTitle)
        assertEquals(primaryModel.title, titleView.text.toString())
        val sparklineHidden = firstTile.findViewById<DiagnosticsMetricSparklineView>(R.id.metricSparkline)
        assertEquals(View.GONE, sparklineHidden.visibility)
        val sparklineLabelHidden = firstTile.findViewById<TextView>(R.id.metricSparklineLabel)
        assertEquals(View.GONE, sparklineLabelHidden.visibility)

        panel.render(listOf(primaryModel))
        assertEquals(1, panel.childCount)

        val expectedLabel = context.getString(R.string.diagnostics_metric_sparkline_label, 3)
        val sparklineModel = primaryModel.copy(
            sparkline = DiagnosticsMetricSparklineModel(points = listOf(0f, 0.5f, 1f)),
            sparklineLabel = expectedLabel
        )
        panel.render(listOf(sparklineModel))
        val sparkTile = panel.getChildAt(0) as DiagnosticsMetricTileView
        val sparklineVisible = sparkTile.findViewById<DiagnosticsMetricSparklineView>(R.id.metricSparkline)
        assertEquals(View.VISIBLE, sparklineVisible.visibility)
        val sparklineLabelVisible = sparkTile.findViewById<TextView>(R.id.metricSparklineLabel)
        assertEquals(View.VISIBLE, sparklineLabelVisible.visibility)
        assertEquals(expectedLabel, sparklineLabelVisible.text.toString())

        panel.render(emptyList())
        assertEquals(0, panel.childCount)
        assertEquals(View.GONE, panel.visibility)
    }

    @Test
    fun `highlightMetric toggles tile selection`() {
    val baseContext = ApplicationProvider.getApplicationContext<TestKioskApp>()
    val context = ContextThemeWrapper(baseContext, R.style.AppTheme)
    val panel = DiagnosticsMetricsPanelView(context)
        val model = DiagnosticsMetricTileModel(
            id = "voltage",
            title = "Напряжение",
            valueText = "12.8",
            unitText = "V",
            statusLabel = "Норма",
            adviceText = "",
            severity = DiagnosticsMetricSeverity.NORMAL
        )

        panel.render(listOf(model))
        val tile = panel.getChildAt(0) as DiagnosticsMetricTileView
        assertFalse(tile.findViewById<View>(R.id.metricTileRoot).isSelected)

        panel.highlightMetric(DiagnosticsMetricSelection(metricId = "voltage"))
        assertTrue(tile.findViewById<View>(R.id.metricTileRoot).isSelected)

        panel.highlightMetric(null)
        assertFalse(tile.findViewById<View>(R.id.metricTileRoot).isSelected)
    }

    @Test
    fun `highlightMetric matches by pidKey`() {
    val baseContext = ApplicationProvider.getApplicationContext<TestKioskApp>()
    val context = ContextThemeWrapper(baseContext, R.style.AppTheme)
    val panel = DiagnosticsMetricsPanelView(context)
        val model = DiagnosticsMetricTileModel(
            id = "voltage",
            pidKey = "01:42",
            title = "Напряжение",
            valueText = "12.8",
            unitText = "V",
            statusLabel = "Норма",
            adviceText = "",
            severity = DiagnosticsMetricSeverity.NORMAL
        )

        panel.render(listOf(model))
        val tile = panel.getChildAt(0) as DiagnosticsMetricTileView

        panel.highlightMetric(DiagnosticsMetricSelection(pidKey = "01:42"))
        assertTrue(tile.findViewById<View>(R.id.metricTileRoot).isSelected)

        panel.highlightMetric(DiagnosticsMetricSelection(pidKey = "09:02"))
        assertFalse(tile.findViewById<View>(R.id.metricTileRoot).isSelected)
    }
}