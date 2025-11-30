package com.selfservice.platform.data.diagnostics.profile

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiagnosticsMetricCalculatorTest {

    @Test
    fun `calculate builds insight with evaluated status`() {
        val definition = DiagnosticsMetricDefinition(
            id = "coolant_temp",
            mode = "01",
            pid = "05",
            label = "Температура охлаждающей жидкости",
            unit = "°C",
            thresholds = DiagnosticsMetricThresholds(
                warningHigh = 95.0,
                criticalHigh = 105.0
            ),
            advice = DiagnosticsMetricAdvice(
                messages = mapOf(
                    DiagnosticsMetricStatus.OK to "Работа в норме",
                    DiagnosticsMetricStatus.WARNING_HIGH to "Проверьте охлаждение",
                    DiagnosticsMetricStatus.CRITICAL_HIGH to "Остановите двигатель"
                ),
                defaultMessage = "Нет рекомендаций",
                noDataMessage = "Нет данных"
            )
        )
        val calculator = DiagnosticsMetricCalculator(definitions = listOf(definition))
        val sample = DiagnosticsMetricSample(
            mode = "01",
            pid = "05",
            value = 110.0,
            unit = "°C",
            timestampMillis = 1_000L
        )

        val snapshot = calculator.calculate(listOf(sample))

        assertEquals(1_000L, snapshot.timestampMillis)
        val insight = snapshot.metricById("coolant_temp")
        requireNotNull(insight)
        assertEquals(110.0, insight.value)
        assertEquals(DiagnosticsMetricStatus.CRITICAL_HIGH, insight.status)
        assertEquals("Остановите двигатель", insight.advice)
        assertEquals("°C", insight.unit)
    }

    @Test
    fun `calculate returns default when sample missing`() {
        val definition = DiagnosticsMetricDefinition(
            id = "coolant_temp",
            mode = "01",
            pid = "05",
            label = "Температура охлаждающей жидкости",
            unit = "°C",
            thresholds = DiagnosticsMetricThresholds(),
            advice = DiagnosticsMetricAdvice(
                messages = mapOf(DiagnosticsMetricStatus.OK to "Работа в норме"),
                defaultMessage = "Нет рекомендаций",
                noDataMessage = "Нет данных"
            )
        )
        val calculator = DiagnosticsMetricCalculator(definitions = listOf(definition), clock = { 5000L })

        val snapshot = calculator.calculate(emptyList())

        assertEquals(5000L, snapshot.timestampMillis)
        val insight = snapshot.metricById("coolant_temp")
        requireNotNull(insight)
        assertNull(insight.value)
        assertEquals(DiagnosticsMetricStatus.NO_DATA, insight.status)
        assertEquals("Нет данных", insight.advice)
        assertEquals("°C", insight.unit)
        assertNull(insight.sourceTimestampMillis)
    }
}
