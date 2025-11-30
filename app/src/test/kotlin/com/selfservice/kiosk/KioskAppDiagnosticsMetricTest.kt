package com.selfservice.kiosk

import androidx.test.core.app.ApplicationProvider
import com.selfservice.kiosk.diagnostics.installDiagnosticsDefinitions
import com.selfservice.obd.core.pid.ObdPidDefinition
import com.selfservice.obd.core.protocol.ObdPidSample
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricAdvice
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricDefinition
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricThresholds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestKioskApp::class)
class KioskAppDiagnosticsMetricTest {

    @Test
    fun `evaluateDiagnosticsMetrics updates snapshot`() {
        val app = ApplicationProvider.getApplicationContext<TestKioskApp>()
        val definition = DiagnosticsMetricDefinition(
            id = "control_module_voltage",
            mode = "01",
            pid = "42",
            label = "Напряжение бортовой сети",
            unit = "V",
            thresholds = DiagnosticsMetricThresholds(
                warningLow = 11.5,
                criticalLow = 11.0,
                warningHigh = 14.5,
                criticalHigh = 15.0
            ),
            advice = DiagnosticsMetricAdvice(
                messages = emptyMap(),
                defaultMessage = "Проверьте питание",
                noDataMessage = "Нет данных"
            )
        )
        app.installDiagnosticsDefinitions(definition)

        val sample = ObdPidSample(
            definition = ObdPidDefinition(
                mode = definition.mode,
                pid = definition.pid,
                label = definition.label,
                unit = definition.unit
            ),
            rawPayload = byteArrayOf(0x00.toByte()),
            rawHex = "00",
            value = 12.8,
            unit = definition.unit,
            timestampMillis = 1_000L
        )

    val evaluation = app.evaluateDiagnosticsMetrics(listOf(sample))
    assertNotNull(evaluation)

    val snapshot = evaluation.snapshot

    val insight = snapshot.metricById("control_module_voltage")
        assertNotNull(insight)
    assertEquals(12.8, insight.value!!, 0.0001)
        assertEquals(definition.unit, insight.unit)
        assertEquals(definition.label, insight.definition.label)

        val state = app.diagnosticsMetricSnapshotState().value
        assertEquals(snapshot, state)
    }

    @Test
    fun `evaluateDiagnosticsMetrics updates trends`() {
        val app = ApplicationProvider.getApplicationContext<TestKioskApp>()
        val definition = DiagnosticsMetricDefinition(
            id = "intake_pressure",
            mode = "01",
            pid = "0B",
            label = "Давление",
            unit = "кПа",
            thresholds = DiagnosticsMetricThresholds(),
            advice = DiagnosticsMetricAdvice(
                messages = emptyMap(),
                defaultMessage = "",
                noDataMessage = ""
            )
        )
        app.installDiagnosticsDefinitions(definition)

        val sample = ObdPidSample(
            definition = ObdPidDefinition(
                mode = definition.mode,
                pid = definition.pid,
                label = definition.label,
                unit = definition.unit
            ),
            rawPayload = byteArrayOf(0x00.toByte()),
            rawHex = "00",
            value = 45.0,
            unit = definition.unit,
            timestampMillis = 1_500L
        )
        app.evaluateDiagnosticsMetrics(listOf(sample))

        val trendsAfterFirst = app.diagnosticsMetricTrendsState().value
        val trend = requireNotNull(trendsAfterFirst[definition.id])
        assertEquals(1, trend.points.size)
        assertEquals(45.0, trend.points.first().value)

        val secondSample = sample.copy(value = 47.5, timestampMillis = 2_000L)
        app.evaluateDiagnosticsMetrics(listOf(secondSample))

        val trendsAfterSecond = app.diagnosticsMetricTrendsState().value
        val updatedTrend = requireNotNull(trendsAfterSecond[definition.id])
        assertEquals(2, updatedTrend.points.size)
        assertEquals(47.5, updatedTrend.points.last().value)
    }

    @Test
    fun `resetDiagnosticsMetrics clears snapshot`() {
        val app = ApplicationProvider.getApplicationContext<TestKioskApp>()
        val definition = DiagnosticsMetricDefinition(
            id = "engine_coolant_temperature",
            mode = "01",
            pid = "05",
            label = "Температура охлаждающей жидкости",
            unit = "°C",
            thresholds = DiagnosticsMetricThresholds(),
            advice = DiagnosticsMetricAdvice(
                messages = emptyMap(),
                defaultMessage = "Нет рекомендаций",
                noDataMessage = "Нет данных"
            )
        )
        app.installDiagnosticsDefinitions(definition)

        val evaluation = app.evaluateDiagnosticsMetrics(
            listOf(
                ObdPidSample(
                    definition = ObdPidDefinition(
                        mode = definition.mode,
                        pid = definition.pid,
                        label = definition.label,
                        unit = definition.unit
                    ),
                    rawPayload = byteArrayOf(0x00.toByte()),
                    rawHex = "00",
                    value = null,
                    unit = definition.unit,
                    timestampMillis = 2_000L
                )
            )
        )
        assertNotNull(evaluation)
        val snapshot = evaluation.snapshot
        assertNotNull(app.diagnosticsMetricSnapshotState().value)

        app.resetDiagnosticsMetrics()
        assertNull(app.diagnosticsMetricSnapshotState().value)
    }
}
