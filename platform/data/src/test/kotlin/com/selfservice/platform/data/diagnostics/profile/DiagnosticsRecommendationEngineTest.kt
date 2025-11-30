package com.selfservice.platform.data.diagnostics.profile

import kotlin.test.Test
import kotlin.test.assertEquals

class DiagnosticsRecommendationEngineTest {

    private val engine = DiagnosticsRecommendationEngine()

    @Test
    fun `recommendations skip ok metrics and sort by severity`() {
        val definitionVoltage = definition(
            id = "control_module_voltage",
            label = "Напряжение",
            unit = "V",
            thresholds = DiagnosticsMetricThresholds(criticalLow = 11.8)
        )
        val definitionTemperature = definition(
            id = "engine_coolant_temperature",
            label = "Температура",
            unit = "°C",
            thresholds = DiagnosticsMetricThresholds(warningHigh = 105.0)
        )
        val definitionIntake = definition(
            id = "intake_air_temperature",
            label = "Температура впуска"
        )
        val snapshot = DiagnosticsProfileSnapshot(
            timestampMillis = 1_000L,
            metrics = listOf(
                DiagnosticsMetricInsight(
                    definition = definitionVoltage,
                    value = 11.2,
                    unit = "V",
                    status = DiagnosticsMetricStatus.CRITICAL_LOW,
                    advice = "Критически низкое напряжение",
                    sourceTimestampMillis = 1_000L
                ),
                DiagnosticsMetricInsight(
                    definition = definitionTemperature,
                    value = 110.0,
                    unit = "°C",
                    status = DiagnosticsMetricStatus.WARNING_HIGH,
                    advice = "Температура повышена",
                    sourceTimestampMillis = 1_000L
                ),
                DiagnosticsMetricInsight(
                    definition = definitionIntake,
                    value = 30.0,
                    unit = "°C",
                    status = DiagnosticsMetricStatus.OK,
                    advice = "Температура в норме",
                    sourceTimestampMillis = 1_000L
                )
            )
        )

        val recommendations = engine.recommendations(snapshot)

        assertEquals(2, recommendations.size)
        assertEquals("control_module_voltage", recommendations[0].metricId)
        assertEquals(DiagnosticsRecommendationSeverity.CRITICAL, recommendations[0].severity)
        assertEquals(DiagnosticsRecommendationPriority.HIGH, recommendations[0].priority)
        val voltageThreshold = recommendations[0].threshold
        requireNotNull(voltageThreshold)
        assertEquals(DiagnosticsRecommendationThresholdType.LOWER_OR_EQUAL, voltageThreshold.type)
        assertEquals(11.8, voltageThreshold.value)
        assertEquals("V", voltageThreshold.unit)
        assertEquals("engine_coolant_temperature", recommendations[1].metricId)
        assertEquals(DiagnosticsRecommendationSeverity.WARNING, recommendations[1].severity)
        assertEquals(DiagnosticsRecommendationPriority.MEDIUM, recommendations[1].priority)
        val temperatureThreshold = recommendations[1].threshold
        requireNotNull(temperatureThreshold)
        assertEquals(DiagnosticsRecommendationThresholdType.GREATER_OR_EQUAL, temperatureThreshold.type)
        assertEquals(105.0, temperatureThreshold.value)
        assertEquals("°C", temperatureThreshold.unit)
    }

    @Test
    fun `recommendations skip entries without advice`() {
        val definition = definition(id = "engine_oil_temp", label = "Температура масла", unit = "°C")
        val snapshot = DiagnosticsProfileSnapshot(
            timestampMillis = 2_000L,
            metrics = listOf(
                DiagnosticsMetricInsight(
                    definition = definition,
                    value = null,
                    unit = "°C",
                    status = DiagnosticsMetricStatus.NO_DATA,
                    advice = "   ",
                    sourceTimestampMillis = 500L
                )
            )
        )

        val recommendations = engine.recommendations(snapshot)

        assertEquals(emptyList(), recommendations)
    }

    @Test
    fun `warning low produces threshold with lower comparator`() {
        val definition = definition(
            id = "short_term_fuel_trim_bank1",
            label = "Краткосрочная топливная коррекция",
            unit = "%",
            thresholds = DiagnosticsMetricThresholds(warningLow = -20.0)
        )
        val snapshot = DiagnosticsProfileSnapshot(
            timestampMillis = 3_000L,
            metrics = listOf(
                DiagnosticsMetricInsight(
                    definition = definition,
                    value = -18.5,
                    unit = "%",
                    status = DiagnosticsMetricStatus.WARNING_LOW,
                    advice = "Смесь переобогащена",
                    sourceTimestampMillis = 3_000L
                )
            )
        )

        val recommendations = engine.recommendations(snapshot)

        assertEquals(1, recommendations.size)
        val recommendation = recommendations.first()
        assertEquals(DiagnosticsRecommendationSeverity.WARNING, recommendation.severity)
        assertEquals(DiagnosticsRecommendationPriority.MEDIUM, recommendation.priority)
        val threshold = recommendation.threshold
        requireNotNull(threshold)
        assertEquals(DiagnosticsRecommendationThresholdType.LOWER_OR_EQUAL, threshold.type)
        assertEquals(-20.0, threshold.value)
        assertEquals("%", threshold.unit)
    }

    private fun definition(
        id: String,
        label: String,
        unit: String? = null,
        thresholds: DiagnosticsMetricThresholds = DiagnosticsMetricThresholds()
    ): DiagnosticsMetricDefinition {
        return DiagnosticsMetricDefinition(
            id = id,
            mode = "01",
            pid = "00",
            label = label,
            unit = unit,
            thresholds = thresholds,
            advice = DiagnosticsMetricAdvice(
                messages = emptyMap(),
                defaultMessage = "Нет рекомендаций",
                noDataMessage = "Нет данных"
            )
        )
    }
}
