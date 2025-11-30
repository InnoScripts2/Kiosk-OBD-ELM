package com.selfservice.kiosk

import androidx.test.core.app.ApplicationProvider
import com.selfservice.feature.reports.DiagnosticsReport
import com.selfservice.feature.reports.DiagnosticsReportInput
import com.selfservice.feature.reports.DiagnosticsReportVehicle
import com.selfservice.kiosk.diagnostics.DiagnosticsReportWorkflow
import com.selfservice.obd.core.dtc.ObdDtcDefinition
import com.selfservice.obd.core.diagnostics.ObdDiagnosticsResult
import com.selfservice.obd.core.passthru.PassThruDiagnosticsCoordinator
import com.selfservice.obd.core.protocol.ObdDtcBatch
import com.selfservice.obd.core.protocol.ObdDtcEntry
import com.selfservice.obd.core.session.ObdSessionState
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsProfileSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestKioskApp::class)
class KioskAppDiagnosticsReportMetadataTest {

    @Test
    fun `buildDiagnosticsReportMetadata stores enriched dtc fields`() {
        val app = ApplicationProvider.getApplicationContext<TestKioskApp>()
        val definition = ObdDtcDefinition(
            code = "P0420",
            system = ObdDtcDefinition.System.POWERTRAIN,
            label = "Catalyst efficiency below threshold",
            notes = "Проверьте датчики кислорода",
            status = ObdDtcDefinition.Availability.BOTH,
            sources = listOf("dtcmapping.json"),
            details = ObdDtcDefinition.Details(
                preferred = "Низкая эффективность катализатора",
                base = "Catalyst efficiency below threshold",
                app = "Катализатор: эффективность ниже нормы"
            ),
            brandOverrides = listOf("Toyota", "Lexus")
        )
        val metadata = app.buildDiagnosticsReportMetadata(
            diagnosticsResult(definition = definition)
        )

        val diagnostics = metadata["diagnostics"] as Map<*, *>
        val entries = diagnostics["dtc_entries"] as List<*>
        val entry = entries.first() as Map<*, *>

        assertEquals("P0420", entry["code"])
        assertEquals("Catalyst efficiency below threshold", entry["label"])
        assertEquals("powertrain", entry["system"])
        assertEquals("both", entry["status"])
        assertEquals("Низкая эффективность катализатора", entry["description"])
        assertEquals("preferred", entry["description_source"])
        assertFalse(entry["description_missing"] as Boolean)
        assertEquals("warning", entry["severity"])
        assertEquals(listOf("dtcmapping.json"), entry["sources"])
        assertEquals(listOf("Toyota", "Lexus"), entry["brand_overrides"])
        val details = entry["details"] as Map<*, *>
        assertEquals("Низкая эффективность катализатора", details["preferred"])
        assertEquals("Catalyst efficiency below threshold", details["base"])
        assertEquals("Катализатор: эффективность ниже нормы", details["app"])
    }

    @Test
    fun `buildDiagnosticsReportMetadata marks missing descriptions`() {
        val app = ApplicationProvider.getApplicationContext<TestKioskApp>()
        val metadata = app.buildDiagnosticsReportMetadata(
            diagnosticsResult(definition = null, code = "U0100")
        )
        val diagnostics = metadata["diagnostics"] as Map<*, *>
        val entry = (diagnostics["dtc_entries"] as List<*>).first() as Map<*, *>

        assertEquals("U0100", entry["code"])
        assertEquals("missing", entry["description_source"])
        assertTrue(entry["description_missing"] as Boolean)
        assertEquals("info", entry["severity"])
        assertFalse(entry.containsKey("description"))
    }

    private fun diagnosticsResult(
        definition: ObdDtcDefinition?,
        code: String = definition?.code ?: "P0001"
    ): DiagnosticsReportWorkflow.Result {
        val batch = ObdDtcBatch(
            entries = listOf(ObdDtcEntry(code = code, definition = definition)),
            timestampMillis = 1_000L
        )
        val result = ObdDiagnosticsResult(
            pidSamples = emptyList(),
            troubleCodes = batch,
            clearPerformed = false,
            failures = emptyList(),
            durationMillis = 2_000L
        )
        val run = PassThruDiagnosticsCoordinator.DiagnosticsRun(
            sessionState = ObdSessionState.Completed,
            diagnostics = result
        )
        val input = DiagnosticsReportInput(
            sessionId = "session-test",
            generatedAtMillis = 1_500L,
            snapshot = DiagnosticsProfileSnapshot(
                timestampMillis = 1_500L,
                metrics = emptyList()
            ),
            recommendations = emptyList(),
            vehicle = DiagnosticsReportVehicle(
                make = "Toyota",
                model = null,
                year = null,
                vin = null
            ),
            customer = null
        )
        val report = DiagnosticsReport(
            html = "<html />",
            pdfBytes = ByteArray(0)
        )
        return DiagnosticsReportWorkflow.Result(
            sessionId = input.sessionId,
            diagnosticsRun = run,
            evaluation = null,
            reportInput = input,
            report = report
        )
    }
}
