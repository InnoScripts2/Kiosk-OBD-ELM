package com.selfservice.kiosk

import androidx.test.core.app.ApplicationProvider
import com.selfservice.feature.reports.DiagnosticsReport
import com.selfservice.feature.reports.DiagnosticsReportCustomer
import com.selfservice.feature.reports.DiagnosticsReportInput
import com.selfservice.feature.reports.DiagnosticsReportVehicle
import com.selfservice.kiosk.diagnostics.DiagnosticsReportWorkflow
import com.selfservice.kiosk.diagnostics.installDiagnosticsDefinitions
import com.selfservice.obd.core.connection.BleDevice
import com.selfservice.obd.core.diagnostics.ObdDiagnosticsRequest
import com.selfservice.obd.core.diagnostics.ObdDiagnosticsResult
import com.selfservice.obd.core.passthru.PassThruDiagnosticsCoordinator
import com.selfservice.obd.core.pid.ObdPidDefinition
import com.selfservice.obd.core.protocol.ObdPidSample
import com.selfservice.obd.core.session.ConnectedAdapter
import com.selfservice.obd.core.session.ObdSessionState
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricAdvice
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricDefinition
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricThresholds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.fail
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestKioskApp::class)
class KioskAppDiagnosticsReportWorkflowTest {

    @Test
    fun runDiagnosticsReportGeneratesReportAndUpdatesState() = runTest {
        val app = ApplicationProvider.getApplicationContext<TestKioskApp>()
        val definition = DiagnosticsMetricDefinition(
            id = "battery_voltage",
            mode = "01",
            pid = "42",
            label = "Бортовое напряжение",
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

        val adapter = ConnectedAdapter(
            device = BleDevice(
                address = "00:11:22:33:44:55",
                name = "PassThru",
                rssi = -42
            ),
            protocol = "J2534"
        )

        val pidSample = ObdPidSample(
            definition = ObdPidDefinition(
                mode = definition.mode,
                pid = definition.pid,
                label = definition.label,
                unit = definition.unit
            ),
            rawPayload = byteArrayOf(0x00),
            rawHex = "00",
            value = 12.8,
            unit = definition.unit,
            timestampMillis = 5_000L
        )

        val diagnosticsResult = ObdDiagnosticsResult(
            pidSamples = listOf(pidSample),
            troubleCodes = null,
            clearPerformed = null,
            failures = emptyList(),
            durationMillis = 1_500L
        )

        val diagnosticsRun = PassThruDiagnosticsCoordinator.DiagnosticsRun(
            sessionState = ObdSessionState.Completed,
            diagnostics = diagnosticsResult
        )

        var capturedRequest: ObdDiagnosticsRequest? = null
        var capturedInput: DiagnosticsReportInput? = null
        val expectedReport = DiagnosticsReport(
            html = "<html></html>",
            pdfBytes = byteArrayOf(0x01, 0x02)
        )

        val workflow = DiagnosticsReportWorkflow(
            diagnosticsRunner = { receivedAdapter, request ->
                assertSame(adapter, receivedAdapter)
                capturedRequest = request
                diagnosticsRun
            },
            metricEvaluator = { samples ->
                app.evaluateDiagnosticsMetrics(samples)
            },
            reportProducer = DiagnosticsReportWorkflow.ReportProducer { input ->
                capturedInput = input
                expectedReport
            },
            timeProvider = { 10_000L },
            sessionIdProvider = { "session-test" }
        )

        assertNull(app.diagnosticsReportState().value)
        app.overrideDiagnosticsReportWorkflow(workflow)

        val metadata = DiagnosticsReportWorkflow.Metadata(
            vehicle = DiagnosticsReportVehicle(
                make = "Toyota",
                model = "Camry",
                year = 2022,
                vin = "VIN123"
            ),
            customer = DiagnosticsReportCustomer(
                phone = "+1234567890",
                email = "owner@example.com"
            )
        )

        val request = ObdDiagnosticsRequest()
        val result = app.runDiagnosticsReport(
            request = request,
            metadata = metadata,
            connectionOptions = KioskApp.DiagnosticsConnectionOptions(adapter = adapter)
        )

        assertSame(expectedReport, result.report)
        assertEquals("session-test", result.sessionId)
        assertSame(diagnosticsRun, result.diagnosticsRun)
        assertSame(workflow, app.diagnosticsReportWorkflow())
        assertSame(request, capturedRequest)

        val input = capturedInput ?: fail("DiagnosticsReportInput should be captured")
        assertEquals(metadata.vehicle, input.vehicle)
        assertEquals(metadata.customer, input.customer)
        assertEquals("session-test", input.sessionId)
        assertEquals(10_000L, input.generatedAtMillis)
        assertEquals(result.reportInput, input)

        val snapshot = app.diagnosticsMetricSnapshotState().value
        assertNotNull(snapshot)
        val metric = snapshot.metricById(definition.id)
        assertNotNull(metric)
        assertEquals(pidSample.value, metric.value)
        assertEquals(result.evaluation?.snapshot, snapshot)
        assertEquals(result.evaluation?.recommendations, app.diagnosticsRecommendationsState().value)

        val reportState = app.diagnosticsReportState().value
        assertSame(result, reportState)
    }

    @Test
    fun runDiagnosticsReportFailsWithoutConnection() = runTest {
        val app = ApplicationProvider.getApplicationContext<TestKioskApp>()
        val workflow = DiagnosticsReportWorkflow(
            diagnosticsRunner = { _, _ ->
                fail("Workflow should not run without connection")
            },
            metricEvaluator = { null },
            reportProducer = DiagnosticsReportWorkflow.ReportProducer {
                fail("Report producer should not execute")
            }
        )
        app.overrideObdConnectionController(null)
        app.overrideDiagnosticsReportWorkflow(workflow)

        try {
            app.runDiagnosticsReport(ObdDiagnosticsRequest())
            fail("Expected IllegalStateException when controller is absent")
        } catch (expected: IllegalStateException) {
            // Expected path
        }
    }
}
