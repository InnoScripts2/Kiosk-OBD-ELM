package com.selfservice.kiosk.diagnostics.ui

import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
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
class DiagnosticsMetricSummaryViewTest {

    private val baseContext = ApplicationProvider.getApplicationContext<TestKioskApp>()
    private val themedContext = ContextThemeWrapper(baseContext, R.style.AppTheme)

    @Test
    fun `render hides view when no metrics`() {
        val view = DiagnosticsMetricSummaryView(themedContext)

        view.render(emptyList())

        assertEquals(View.GONE, view.visibility)
        assertFalse(view.isVisible)
        assertEquals(null, view.contentDescription)
    }

    @Test
    fun `render shows aggregate counts and indicator color`() {
        val view = DiagnosticsMetricSummaryView(themedContext)
        val models = listOf(
            createMetric("voltage", DiagnosticsMetricSeverity.CRITICAL),
            createMetric("coolant", DiagnosticsMetricSeverity.WARNING),
            createMetric("fuel", DiagnosticsMetricSeverity.UNKNOWN),
            createMetric("intake", DiagnosticsMetricSeverity.NORMAL)
        )

        view.render(models)

        assertEquals(View.VISIBLE, view.visibility)
        assertTrue(view.isVisible)

        val expectedTitle = themedContext.getString(
            R.string.diagnostics_metric_summary_title_critical,
            1,
            models.size
        )
        val expectedDetails = themedContext.getString(
            R.string.diagnostics_metric_summary_details,
            1,
            1,
            1,
            1
        )

        val titleView = view.findViewById<View>(R.id.metricSummaryTitle) as TextView
        val detailsView = view.findViewById<View>(R.id.metricSummaryDetails) as TextView
        val indicatorView = view.findViewById<View>(R.id.metricSummaryIndicator)

        assertEquals(expectedTitle, titleView.text.toString())
        assertEquals(expectedDetails, detailsView.text.toString())
        assertTrue(detailsView.isVisible)

        val expectedColor = ContextCompat.getColor(themedContext, R.color.diagnostics_metric_critical)
        val tintList = ViewCompat.getBackgroundTintList(indicatorView)
        assertEquals(expectedColor, tintList?.defaultColor)

        val expectedDescription = "$expectedTitle. $expectedDetails"
        assertEquals(expectedDescription, view.contentDescription)
    }

    private fun createMetric(id: String, severity: DiagnosticsMetricSeverity): DiagnosticsMetricTileModel {
        return DiagnosticsMetricTileModel(
            id = id,
            title = "title_$id",
            valueText = "0",
            unitText = "V",
            statusLabel = "status",
            adviceText = "",
            severity = severity
        )
    }
}
