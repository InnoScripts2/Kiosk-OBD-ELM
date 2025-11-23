package com.selfservice.kiosk.diagnostics.ui

import android.view.ContextThemeWrapper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.test.core.app.ApplicationProvider
import com.selfservice.kiosk.R
import com.selfservice.kiosk.TestKioskApp
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricCategory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = TestKioskApp::class)
class DiagnosticsMetricCategorySummaryViewTest {

    private val baseContext = ApplicationProvider.getApplicationContext<TestKioskApp>()
    private val themedContext = ContextThemeWrapper(baseContext, R.style.AppTheme)

    @Test
    fun `render hides view when no metrics`() {
        val view = DiagnosticsMetricCategorySummaryView(themedContext)

        view.render(emptyList())

        assertEquals(View.GONE, view.visibility)
        assertFalse(view.isVisible)
        assertEquals(null, view.contentDescription)
        val rows = view.findViewById<LinearLayout>(R.id.categorySummaryRows)
        assertEquals(0, rows.childCount)
    }

    @Test
    fun `render groups categories and orders by severity`() {
        val view = DiagnosticsMetricCategorySummaryView(themedContext)
        val electricalLabel = "Электрика"
        val thermalLabel = "Охлаждение"
        val airflowLabel = "Воздушный поток"
        val models = listOf(
            createMetric(
                id = "voltage",
                category = DiagnosticsMetricCategory.ELECTRICAL,
                categoryLabel = electricalLabel,
                severity = DiagnosticsMetricSeverity.CRITICAL
            ),
            createMetric(
                id = "coolant",
                category = DiagnosticsMetricCategory.THERMAL,
                categoryLabel = thermalLabel,
                severity = DiagnosticsMetricSeverity.WARNING
            ),
            createMetric(
                id = "intake",
                category = DiagnosticsMetricCategory.AIRFLOW,
                categoryLabel = airflowLabel,
                severity = DiagnosticsMetricSeverity.NORMAL
            )
        )

        view.render(models)

        assertEquals(View.VISIBLE, view.visibility)
        assertTrue(view.isVisible)

        val rows = view.findViewById<LinearLayout>(R.id.categorySummaryRows)
        assertEquals(3, rows.childCount)

        val firstRow = rows.getChildAt(0)
        val secondRow = rows.getChildAt(1)
        val thirdRow = rows.getChildAt(2)

        assertEquals(electricalLabel, firstRow.findViewById<TextView>(R.id.categorySummaryLabel).text)
        assertEquals(thermalLabel, secondRow.findViewById<TextView>(R.id.categorySummaryLabel).text)
        assertEquals(airflowLabel, thirdRow.findViewById<TextView>(R.id.categorySummaryLabel).text)

        val expectedCriticalColor = ContextCompat.getColor(themedContext, R.color.diagnostics_metric_critical)
        val indicator = firstRow.findViewById<View>(R.id.categorySummaryIndicator)
        val tintList = ViewCompat.getBackgroundTintList(indicator)
        assertEquals(expectedCriticalColor, tintList?.defaultColor)

        val expectedDetailsCritical = themedContext.getString(
            R.string.diagnostics_metric_summary_details,
            1,
            0,
            0,
            0
        )
        val expectedDetailsWarning = themedContext.getString(
            R.string.diagnostics_metric_summary_details,
            0,
            1,
            0,
            0
        )
        val expectedDetailsNormal = themedContext.getString(
            R.string.diagnostics_metric_summary_details,
            0,
            0,
            0,
            1
        )

        assertEquals(expectedDetailsCritical, firstRow.findViewById<TextView>(R.id.categorySummaryDetails).text)
        assertEquals(expectedDetailsWarning, secondRow.findViewById<TextView>(R.id.categorySummaryDetails).text)
        assertEquals(expectedDetailsNormal, thirdRow.findViewById<TextView>(R.id.categorySummaryDetails).text)

        val rowDescriptions = listOf(
            themedContext.getString(
                R.string.diagnostics_metric_category_summary_row_accessibility,
                electricalLabel,
                expectedDetailsCritical
            ),
            themedContext.getString(
                R.string.diagnostics_metric_category_summary_row_accessibility,
                thermalLabel,
                expectedDetailsWarning
            ),
            themedContext.getString(
                R.string.diagnostics_metric_category_summary_row_accessibility,
                airflowLabel,
                expectedDetailsNormal
            )
        )
        val expectedContentDescription = rowDescriptions.joinToString(separator = ". ")
        assertEquals(expectedContentDescription, view.contentDescription)
    }

    private fun createMetric(
        id: String,
        category: DiagnosticsMetricCategory,
        categoryLabel: String,
        severity: DiagnosticsMetricSeverity
    ): DiagnosticsMetricTileModel {
        return DiagnosticsMetricTileModel(
            id = id,
            title = "title_$id",
            valueText = "0",
            unitText = "V",
            statusLabel = "status",
            adviceText = "",
            severity = severity,
            category = category,
            categoryLabel = categoryLabel
        )
    }
}
