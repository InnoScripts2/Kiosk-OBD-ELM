package com.selfservice.kiosk.diagnostics.ui

import android.view.ContextThemeWrapper
import android.view.View
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
class DiagnosticsRecommendationsPanelViewTest {

    @Test
    fun `render applies highlight and click listener`() {
    val baseContext = ApplicationProvider.getApplicationContext<TestKioskApp>()
    val context = ContextThemeWrapper(baseContext, R.style.AppTheme)
    val panel = DiagnosticsRecommendationsPanelView(context)
        var clickedSelection: DiagnosticsMetricSelection? = null
        panel.setOnRecommendationSelectedListener { clickedSelection = it }

        val model = DiagnosticsRecommendationTileModel(
            metricId = "engine_temp",
            pidKey = "01:05",
            title = "Температура",
            message = "Перегрев",
            severity = DiagnosticsRecommendationUiSeverity.CRITICAL,
            severityLabel = "Критично",
            priorityLabel = "Приоритет: высокий",
            thresholdSummary = null
        )

        panel.render(listOf(model))
        assertEquals(View.VISIBLE, panel.visibility)
        val tile = panel.getChildAt(0) as DiagnosticsRecommendationTileView
        assertFalse(tile.findViewById<View>(R.id.recommendationTileRoot).isSelected)

        tile.performClick()
        assertEquals("engine_temp", clickedSelection?.metricId)

        panel.highlightMetric(DiagnosticsMetricSelection(metricId = "engine_temp"))
        assertTrue(tile.findViewById<View>(R.id.recommendationTileRoot).isSelected)

        panel.highlightMetric(null)
        assertFalse(tile.findViewById<View>(R.id.recommendationTileRoot).isSelected)
    }

    @Test
    fun `highlightMetric matches by pidKey`() {
    val baseContext = ApplicationProvider.getApplicationContext<TestKioskApp>()
    val context = ContextThemeWrapper(baseContext, R.style.AppTheme)
    val panel = DiagnosticsRecommendationsPanelView(context)
        val model = DiagnosticsRecommendationTileModel(
            metricId = "engine_temp",
            pidKey = "01:05",
            title = "Температура",
            message = "Перегрев",
            severity = DiagnosticsRecommendationUiSeverity.CRITICAL,
            severityLabel = "Критично",
            priorityLabel = "Приоритет: высокий",
            thresholdSummary = null
        )

        panel.render(listOf(model))
        val tile = panel.getChildAt(0) as DiagnosticsRecommendationTileView

        panel.highlightMetric(DiagnosticsMetricSelection(pidKey = "01:05"))
        assertTrue(tile.findViewById<View>(R.id.recommendationTileRoot).isSelected)

        panel.highlightMetric(DiagnosticsMetricSelection(pidKey = "09:02"))
        assertFalse(tile.findViewById<View>(R.id.recommendationTileRoot).isSelected)
    }
}
