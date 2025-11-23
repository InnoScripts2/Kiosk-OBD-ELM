package com.selfservice.platform.data.diagnostics.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticsMetricTrendRecorderTest {

    private val recorder = DiagnosticsMetricTrendRecorder(capacity = 3)

    @Test
    fun `record keeps sliding window`() {
        val definition = definition()
        repeat(4) { index ->
            val insight = DiagnosticsMetricInsight(
                definition = definition,
                value = 10.0 + index,
                unit = definition.unit,
                status = DiagnosticsMetricStatus.OK,
                advice = "",
                sourceTimestampMillis = 1_000L + index
            )
            val snapshot = DiagnosticsProfileSnapshot(
                timestampMillis = insight.sourceTimestampMillis!!,
                metrics = listOf(insight)
            )
            recorder.record(snapshot)
        }

        val trends = recorder.trends()
        val trend = requireNotNull(trends[definition.id])
        assertEquals(3, trend.points.size)
        assertEquals(11.0, trend.points.first().value)
        assertEquals(13.0, trend.points.last().value)
    }

    @Test
    fun `record ignores null values`() {
        val definition = definition(id = "temp")
        val insight = DiagnosticsMetricInsight(
            definition = definition,
            value = null,
            unit = definition.unit,
            status = DiagnosticsMetricStatus.NO_DATA,
            advice = "",
            sourceTimestampMillis = 2_000L
        )
        val snapshot = DiagnosticsProfileSnapshot(
            timestampMillis = insight.sourceTimestampMillis!!,
            metrics = listOf(insight)
        )

        recorder.record(snapshot)

        val trends = recorder.trends()
        assertTrue(trends.isEmpty())
    }

    @Test
    fun `record uses snapshot timestamp when sample missing`() {
        val definition = definition(id = "pressure")
        val insight = DiagnosticsMetricInsight(
            definition = definition,
            value = 25.0,
            unit = definition.unit,
            status = DiagnosticsMetricStatus.OK,
            advice = "",
            sourceTimestampMillis = null
        )
        val snapshot = DiagnosticsProfileSnapshot(
            timestampMillis = 9_000L,
            metrics = listOf(insight)
        )

        recorder.record(snapshot)

        val trend = requireNotNull(recorder.trends()[definition.id])
        assertEquals(9_000L, trend.points.first().timestampMillis)
    }

    private fun definition(id: String = "metric" ) = DiagnosticsMetricDefinition(
        id = id,
        mode = "01",
        pid = "00",
        label = "Метрика",
        unit = "V",
        thresholds = DiagnosticsMetricThresholds(),
        advice = DiagnosticsMetricAdvice(
            messages = emptyMap(),
            defaultMessage = "",
            noDataMessage = ""
        )
    )
}
