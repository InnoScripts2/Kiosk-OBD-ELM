package com.selfservice.kiosk.diagnostics.summary

import com.selfservice.kiosk.diagnostics.ui.DiagnosticsMetricSeverity
import com.selfservice.kiosk.diagnostics.ui.DiagnosticsMetricTileModel
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricAdvice
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricDefinition
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricInsight
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricStatus
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricThresholds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DiagnosticsMetricSummaryTest {

    @Test
    fun `fromTileModels counts severities and derives status`() {
        val models = listOf(
            tile("voltage", DiagnosticsMetricSeverity.CRITICAL),
            tile("coolant", DiagnosticsMetricSeverity.WARNING),
            tile("fuel", DiagnosticsMetricSeverity.UNKNOWN),
            tile("intake", DiagnosticsMetricSeverity.NORMAL),
            tile("rpm", DiagnosticsMetricSeverity.NORMAL)
        )

        val summary = DiagnosticsMetricSummary.fromTileModels(models)

        assertEquals(5, summary.totalCount)
        assertEquals(1, summary.criticalCount)
        assertEquals(1, summary.warningCount)
        assertEquals(1, summary.unknownCount)
        assertEquals(2, summary.normalCount)
        assertSame(DiagnosticsMetricSummary.Severity.CRITICAL, summary.severity)
        assertEquals(true, summary.hasData)
    }

    @Test
    fun `fromInsights maps statuses consistently`() {
        val insights = listOf(
            insight("ok", DiagnosticsMetricStatus.OK),
            insight("warning_high", DiagnosticsMetricStatus.WARNING_HIGH),
            insight("warning_low", DiagnosticsMetricStatus.WARNING_LOW),
            insight("critical_low", DiagnosticsMetricStatus.CRITICAL_LOW),
            insight("critical_high", DiagnosticsMetricStatus.CRITICAL_HIGH),
            insight("no_data", DiagnosticsMetricStatus.NO_DATA)
        )

        val summary = DiagnosticsMetricSummary.fromInsights(insights)

        assertEquals(6, summary.totalCount)
        assertEquals(2, summary.warningCount)
        assertEquals(2, summary.criticalCount)
        assertEquals(1, summary.unknownCount)
        assertEquals(1, summary.normalCount)
        assertSame(DiagnosticsMetricSummary.Severity.CRITICAL, summary.severity)
    }

    private fun tile(id: String, severity: DiagnosticsMetricSeverity): DiagnosticsMetricTileModel {
        return DiagnosticsMetricTileModel(
            id = id,
            title = "title_$id",
            valueText = "0",
            unitText = null,
            statusLabel = "status",
            adviceText = "advice",
            severity = severity
        )
    }

    private fun insight(id: String, status: DiagnosticsMetricStatus): DiagnosticsMetricInsight {
        val definition = DiagnosticsMetricDefinition(
            id = id,
            mode = "01",
            pid = "0C",
            label = id,
            unit = null,
            thresholds = DiagnosticsMetricThresholds(),
            advice = DiagnosticsMetricAdvice(emptyMap(), defaultMessage = "", noDataMessage = "")
        )
        return DiagnosticsMetricInsight(
            definition = definition,
            value = null,
            unit = null,
            status = status,
            advice = "",
            sourceTimestampMillis = null
        )
    }
}
