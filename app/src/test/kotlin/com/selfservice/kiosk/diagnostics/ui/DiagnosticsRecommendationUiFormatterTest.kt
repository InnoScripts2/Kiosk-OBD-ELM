package com.selfservice.kiosk.diagnostics.ui

import androidx.test.core.app.ApplicationProvider
import com.selfservice.kiosk.R
import com.selfservice.kiosk.TestKioskApp
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricStatus
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendation
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendationPriority
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendationSeverity
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendationThreshold
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendationThresholdType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestKioskApp::class)
class DiagnosticsRecommendationUiFormatterTest {

    private val context: TestKioskApp = ApplicationProvider.getApplicationContext()
    private val formatter = DiagnosticsRecommendationUiFormatter(context.resources)

    @Test
    fun `map produces critical severity label`() {
        val recommendation = DiagnosticsRecommendation(
            metricId = "engine_oil_temp",
            pidKey = "01:5C",
            title = "Температура масла",
            message = "Перегрев масла",
            severity = DiagnosticsRecommendationSeverity.CRITICAL,
            status = DiagnosticsMetricStatus.CRITICAL_HIGH,
            priority = DiagnosticsRecommendationPriority.HIGH,
            threshold = DiagnosticsRecommendationThreshold(
                type = DiagnosticsRecommendationThresholdType.GREATER_OR_EQUAL,
                value = 120.0,
                unit = "°C"
            )
        )

        val model = formatter.map(recommendation)

        assertEquals("engine_oil_temp", model.metricId)
        assertEquals("01:5C", model.pidKey)
        assertEquals("Температура масла", model.title)
        assertEquals("Перегрев масла", model.message)
        assertEquals(DiagnosticsRecommendationUiSeverity.CRITICAL, model.severity)
        val expected = context.getString(R.string.diagnostics_recommendation_severity_critical)
        assertEquals(expected, model.severityLabel)
        val expectedPriority = context.getString(R.string.diagnostics_recommendation_priority_high)
        assertEquals(expectedPriority, model.priorityLabel)
        val thresholdRaw = ">= 120 °C"
        val expectedThreshold = context.getString(R.string.diagnostics_recommendation_threshold_template, thresholdRaw)
        assertEquals(expectedThreshold, model.thresholdSummary)
    }

    @Test
    fun `map produces info severity label`() {
        val recommendation = DiagnosticsRecommendation(
            metricId = "oxygen_sensor",
            pidKey = "01:24",
            title = "Датчик кислорода",
            message = "Нет данных",
            severity = DiagnosticsRecommendationSeverity.INFO,
            status = DiagnosticsMetricStatus.NO_DATA,
            priority = DiagnosticsRecommendationPriority.LOW,
            threshold = null
        )

        val model = formatter.map(recommendation)

        assertEquals(DiagnosticsRecommendationUiSeverity.INFO, model.severity)
        assertEquals("01:24", model.pidKey)
        val expected = context.getString(R.string.diagnostics_recommendation_severity_info)
        assertEquals(expected, model.severityLabel)
        val expectedPriority = context.getString(R.string.diagnostics_recommendation_priority_low)
        assertEquals(expectedPriority, model.priorityLabel)
        assertNull(model.thresholdSummary)
    }
}
