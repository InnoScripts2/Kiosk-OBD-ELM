package com.selfservice.kiosk.diagnostics.ui

import androidx.test.core.app.ApplicationProvider
import com.selfservice.kiosk.R
import com.selfservice.kiosk.TestKioskApp
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricAdvice
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricCategory
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricDefinition
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricInsight
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricStatus
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricThresholds
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricTrend
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricTrendPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestKioskApp::class)
class DiagnosticsMetricUiFormatterTest {

    private val context: TestKioskApp = ApplicationProvider.getApplicationContext()
    private val formatter = DiagnosticsMetricUiFormatter(context.resources)

    @Test
    fun `map produces model with severity`() {
        val definition = DiagnosticsMetricDefinition(
            id = "control_module_voltage",
            mode = "01",
            pid = "42",
            label = "Напряжение бортовой сети",
            unit = "V",
            category = DiagnosticsMetricCategory.ELECTRICAL,
            thresholds = DiagnosticsMetricThresholds(),
            advice = DiagnosticsMetricAdvice(
                messages = mapOf(DiagnosticsMetricStatus.OK to "Напряжение в норме"),
                defaultMessage = "Проверьте систему",
                noDataMessage = "Нет данных"
            )
        )
        val insight = DiagnosticsMetricInsight(
            definition = definition,
            value = 11.5,
            unit = "V",
            status = DiagnosticsMetricStatus.WARNING_LOW,
            advice = "Низкое напряжение",
            sourceTimestampMillis = 1_000L
        )

        val model = formatter.map(insight)

        assertEquals("control_module_voltage", model.id)
        assertEquals(DiagnosticsMetricSeverity.WARNING, model.severity)
        assertEquals("Низкое напряжение", model.adviceText)
        val expectedStatus = context.getString(R.string.diagnostics_metric_status_warning_low)
        assertEquals(expectedStatus, model.statusLabel)
        assertEquals(null, model.sparkline)
        val expectedCategoryLabel = context.getString(R.string.diagnostics_metric_category_electrical)
        assertEquals(expectedCategoryLabel, model.categoryLabel)
    }

    @Test
    fun `map handles missing value`() {
        val definition = DiagnosticsMetricDefinition(
            id = "engine_coolant_temperature",
            mode = "01",
            pid = "05",
            label = "Температура охлаждающей жидкости",
            unit = "\u00B0C",
            category = DiagnosticsMetricCategory.THERMAL,
            thresholds = DiagnosticsMetricThresholds(),
            advice = DiagnosticsMetricAdvice(
                messages = emptyMap(),
                defaultMessage = "Нет рекомендаций",
                noDataMessage = "Нет данных"
            )
        )
        val insight = DiagnosticsMetricInsight(
            definition = definition,
            value = null,
            unit = null,
            status = DiagnosticsMetricStatus.NO_DATA,
            advice = "Нет данных",
            sourceTimestampMillis = null
        )

        val model = formatter.map(insight)

        assertEquals(DiagnosticsMetricSeverity.UNKNOWN, model.severity)
        assertEquals(context.getString(R.string.diagnostics_metric_value_placeholder), model.valueText)
        assertEquals(context.getString(R.string.diagnostics_metric_status_no_data), model.statusLabel)
        assertEquals(null, model.sparkline)
    }

    @Test
    fun `map builds sparkline from trend`() {
        val definition = DiagnosticsMetricDefinition(
            id = "intake_pressure",
            mode = "01",
            pid = "0B",
            label = "Давление",
            unit = "кПа",
            category = DiagnosticsMetricCategory.PRESSURE,
            thresholds = DiagnosticsMetricThresholds(),
            advice = DiagnosticsMetricAdvice(
                messages = emptyMap(),
                defaultMessage = "",
                noDataMessage = ""
            )
        )
        val insight = DiagnosticsMetricInsight(
            definition = definition,
            value = 40.0,
            unit = "кПа",
            status = DiagnosticsMetricStatus.OK,
            advice = "",
            sourceTimestampMillis = 10_000L
        )
        val trend = DiagnosticsMetricTrend(
            metricId = definition.id,
            points = listOf(
                DiagnosticsMetricTrendPoint(timestampMillis = 1L, value = 20.0),
                DiagnosticsMetricTrendPoint(timestampMillis = 2L, value = 30.0),
                DiagnosticsMetricTrendPoint(timestampMillis = 3L, value = 40.0)
            )
        )

        val model = formatter.map(insight, trend)

        val sparkline = requireNotNull(model.sparkline)
        assertEquals(3, sparkline.points.size)
        assertEquals(0f, sparkline.points.first())
        assertEquals(1f, sparkline.points.last())
        val expectedLabel = context.getString(R.string.diagnostics_metric_sparkline_label, 3)
        assertEquals(expectedLabel, model.sparklineLabel)
    }

    @Test
    fun `map adds contextual sparkline label when trend spans minutes`() {
        val definition = DiagnosticsMetricDefinition(
            id = "fuel_trim",
            mode = "01",
            pid = "06",
            label = "Краткосрочная топливная коррекция",
            unit = "%%",
            category = DiagnosticsMetricCategory.FUEL_SYSTEM,
            thresholds = DiagnosticsMetricThresholds(),
            advice = DiagnosticsMetricAdvice(
                messages = emptyMap(),
                defaultMessage = "",
                noDataMessage = ""
            )
        )
        val insight = DiagnosticsMetricInsight(
            definition = definition,
            value = -3.5,
            unit = "%%",
            status = DiagnosticsMetricStatus.OK,
            advice = "",
            sourceTimestampMillis = 600_000L
        )
        val trend = DiagnosticsMetricTrend(
            metricId = definition.id,
            points = listOf(
                DiagnosticsMetricTrendPoint(timestampMillis = 0L, value = -2.5),
                DiagnosticsMetricTrendPoint(timestampMillis = 60_000L, value = -3.0),
                DiagnosticsMetricTrendPoint(timestampMillis = 120_000L, value = -3.5)
            )
        )

        val model = formatter.map(insight, trend)

        val sparklineLabel = requireNotNull(model.sparklineLabel)
        val duration = context.resources.getQuantityString(
            R.plurals.diagnostics_metric_sparkline_span_minutes,
            2,
            2
        )
        val expected = context.getString(
            R.string.diagnostics_metric_sparkline_label_with_span,
            3,
            duration
        )
        assertEquals(expected, sparklineLabel)
    }
}
