package com.selfservice.kiosk

import com.selfservice.platform.logging.DiagnosticsLogSupabaseSchema
import com.selfservice.kiosk.reports.DiagnosticsReportSupabaseSchema
import com.selfservice.kiosk.telemetry.DiagnosticsTelemetrySupabaseSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class KioskRuntimeConfigTest {

    @Test
    fun `kioskId returns null for blank values`() {
        assertNull(KioskRuntimeConfig.kioskId(""))
        assertNull(KioskRuntimeConfig.kioskId("   "))
    }

    @Test
    fun `kioskId trims whitespace`() {
        assertEquals("kiosk-7", KioskRuntimeConfig.kioskId("  kiosk-7  "))
    }

    @Test
    fun `environment falls back to debug default`() {
        val environment = KioskRuntimeConfig.environment(rawValue = " ", isDebug = true)
        assertEquals(KioskRuntimeConfig.DEFAULT_DEBUG_ENVIRONMENT, environment)
    }

    @Test
    fun `environment falls back to release default`() {
        val environment = KioskRuntimeConfig.environment(rawValue = "", isDebug = false)
        assertEquals(KioskRuntimeConfig.DEFAULT_RELEASE_ENVIRONMENT, environment)
    }

    @Test
    fun `diagnosticsLogFields contains kiosk metadata`() {
        val fields = KioskRuntimeConfig.diagnosticsLogFields(
                rawKioskId = " kiosk-22 ",
                rawEnvironment = " qa ",
                isDebug = false
        )

        assertEquals("kiosk-22", fields[DiagnosticsLogSupabaseSchema.Columns.KIOSK_ID])
        assertEquals("qa", fields[DiagnosticsLogSupabaseSchema.Columns.ENVIRONMENT])
        assertFalse(fields.containsKey("unused"))
    }

    @Test
    fun `diagnosticsTelemetryFields mirrors diagnostics log metadata`() {
        val fields = KioskRuntimeConfig.diagnosticsTelemetryFields(
            rawKioskId = " kiosk-100 ",
            rawEnvironment = "  ",
            isDebug = true
        )

        assertEquals("kiosk-100", fields[DiagnosticsTelemetrySupabaseSchema.Columns.KIOSK_ID])
        assertEquals(KioskRuntimeConfig.DEFAULT_DEBUG_ENVIRONMENT, fields[DiagnosticsTelemetrySupabaseSchema.Columns.ENVIRONMENT])
    }

    @Test
    fun `diagnosticsReportFields align with telemetry metadata`() {
        val fields = KioskRuntimeConfig.diagnosticsReportFields(
            rawKioskId = " kiosk-310 ",
            rawEnvironment = "prod",
            isDebug = false
        )

        assertEquals("kiosk-310", fields[DiagnosticsReportSupabaseSchema.Columns.KIOSK_ID])
        assertEquals("prod", fields[DiagnosticsReportSupabaseSchema.Columns.ENVIRONMENT])
    }
}
