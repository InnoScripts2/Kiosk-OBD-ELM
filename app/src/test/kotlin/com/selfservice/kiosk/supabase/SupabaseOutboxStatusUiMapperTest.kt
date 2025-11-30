package com.selfservice.kiosk.supabase

import com.selfservice.kiosk.mdm.DeviceCommandSupabaseSchema
import com.selfservice.kiosk.mdm.DeviceEventSupabaseSchema
import com.selfservice.kiosk.mdm.DeviceStatusSupabaseSchema
import com.selfservice.platform.logging.DiagnosticsLogSupabaseSchema
import com.selfservice.kiosk.payments.PaymentsAuditSupabaseSchema
import com.selfservice.kiosk.reports.DiagnosticsReportDeliverySupabaseSchema
import com.selfservice.kiosk.reports.DiagnosticsReportSupabaseSchema
import com.selfservice.kiosk.telemetry.DiagnosticsTelemetrySupabaseSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupabaseOutboxStatusUiMapperTest {

    @Test
    fun mapReturnsHiddenWhenQueueIsClearAndNoErrors() {
        val status = SupabaseOutboxMonitor.Status()

        val model = SupabaseOutboxStatusUiMapper.map(status, nowMillis = 1_000L)

        assertFalse(model.isVisible)
        assertEquals(0, model.pendingCount)
        assertEquals(SupabaseOutboxStatusUiModel.Severity.Pending, model.severity)
        assertEquals(0, model.diagnosticsPendingCount)
        assertEquals(null, model.diagnosticsOldestPendingAgeMillis)
        assertEquals(0, model.telemetryPendingCount)
        assertEquals(null, model.telemetryOldestPendingAgeMillis)
        assertEquals(0, model.reportsPendingCount)
        assertEquals(null, model.reportsOldestPendingAgeMillis)
        assertEquals(0, model.paymentsAuditPendingCount)
        assertEquals(null, model.paymentsAuditOldestPendingAgeMillis)
        assertEquals(0, model.reportDeliveriesPendingCount)
        assertEquals(null, model.reportDeliveriesOldestPendingAgeMillis)
        assertEquals(0, model.deviceStatusPendingCount)
        assertEquals(null, model.deviceStatusOldestPendingAgeMillis)
        assertEquals(0, model.deviceCommandsPendingCount)
        assertEquals(null, model.deviceCommandsOldestPendingAgeMillis)
        assertEquals(0, model.deviceEventsPendingCount)
        assertEquals(null, model.deviceEventsOldestPendingAgeMillis)
    }

    @Test
    fun mapReturnsPendingSeverityWhenEntriesExist() {
        val now = 200_000L
        val status = SupabaseOutboxMonitor.Status(
                pendingCount = 3,
                oldestPendingAtMillis = now - 120_000L,
                isRunning = true,
                pendingCountsByTable = mapOf(
                        DiagnosticsLogSupabaseSchema.TABLE_NAME to 2,
            DiagnosticsTelemetrySupabaseSchema.TABLE_NAME to 1,
                        DiagnosticsReportSupabaseSchema.TABLE_NAME to 1,
            PaymentsAuditSupabaseSchema.TABLE_NAME to 3,
            DiagnosticsReportDeliverySupabaseSchema.TABLE_NAME to 2,
            DeviceStatusSupabaseSchema.TABLE_NAME to 4,
            DeviceCommandSupabaseSchema.TABLE_NAME to 2,
            DeviceEventSupabaseSchema.TABLE_NAME to 6
                ),
                oldestPendingAtByTable = mapOf(
                        DiagnosticsLogSupabaseSchema.TABLE_NAME to now - 60_000L,
            DiagnosticsTelemetrySupabaseSchema.TABLE_NAME to now - 45_000L,
            DiagnosticsReportSupabaseSchema.TABLE_NAME to now - 30_000L,
            PaymentsAuditSupabaseSchema.TABLE_NAME to now - 20_000L,
            DiagnosticsReportDeliverySupabaseSchema.TABLE_NAME to now - 15_000L,
            DeviceStatusSupabaseSchema.TABLE_NAME to now - 10_000L,
            DeviceCommandSupabaseSchema.TABLE_NAME to now - 5_000L,
            DeviceEventSupabaseSchema.TABLE_NAME to now - 2_500L
                )
        )

        val model = SupabaseOutboxStatusUiMapper.map(status, nowMillis = now)

        assertTrue(model.isVisible)
        assertEquals(SupabaseOutboxStatusUiModel.Severity.Pending, model.severity)
        assertEquals(3, model.pendingCount)
        assertEquals(120_000L, model.oldestPendingAgeMillis)
        assertTrue(model.isSyncActive)
        assertEquals(2, model.diagnosticsPendingCount)
        assertEquals(60_000L, model.diagnosticsOldestPendingAgeMillis)
        assertEquals(1, model.telemetryPendingCount)
        assertEquals(45_000L, model.telemetryOldestPendingAgeMillis)
        assertEquals(1, model.reportsPendingCount)
        assertEquals(30_000L, model.reportsOldestPendingAgeMillis)
        assertEquals(3, model.paymentsAuditPendingCount)
        assertEquals(20_000L, model.paymentsAuditOldestPendingAgeMillis)
        assertEquals(4, model.deviceStatusPendingCount)
        assertEquals(10_000L, model.deviceStatusOldestPendingAgeMillis)
        assertEquals(2, model.deviceCommandsPendingCount)
        assertEquals(5_000L, model.deviceCommandsOldestPendingAgeMillis)
        assertEquals(6, model.deviceEventsPendingCount)
        assertEquals(2_500L, model.deviceEventsOldestPendingAgeMillis)
        assertEquals(2, model.reportDeliveriesPendingCount)
        assertEquals(15_000L, model.reportDeliveriesOldestPendingAgeMillis)
    }

    @Test
    fun mapNormalizesFutureOldestTimestamp() {
        val now = 5_000L
        val status = SupabaseOutboxMonitor.Status(
                pendingCount = 1,
                oldestPendingAtMillis = now + 1_000L
        )

        val model = SupabaseOutboxStatusUiMapper.map(status, nowMillis = now)

        assertTrue(model.isVisible)
        assertEquals(SupabaseOutboxStatusUiModel.Severity.Pending, model.severity)
        assertEquals(1, model.pendingCount)
        assertEquals(null, model.oldestPendingAgeMillis)
        assertEquals(0, model.diagnosticsPendingCount)
        assertEquals(null, model.diagnosticsOldestPendingAgeMillis)
        assertEquals(0, model.telemetryPendingCount)
        assertEquals(null, model.telemetryOldestPendingAgeMillis)
        assertEquals(0, model.reportsPendingCount)
        assertEquals(null, model.reportsOldestPendingAgeMillis)
        assertEquals(0, model.paymentsAuditPendingCount)
        assertEquals(null, model.paymentsAuditOldestPendingAgeMillis)
        assertEquals(0, model.reportDeliveriesPendingCount)
        assertEquals(null, model.reportDeliveriesOldestPendingAgeMillis)
        assertEquals(0, model.deviceStatusPendingCount)
        assertEquals(null, model.deviceStatusOldestPendingAgeMillis)
    }

    @Test
    fun mapUsesErrorSeverityWhenLastErrorPresent() {
        val status = SupabaseOutboxMonitor.Status(
                pendingCount = 4,
                lastError = IllegalStateException(""),
                isRunning = false
        )

        val model = SupabaseOutboxStatusUiMapper.map(status, nowMillis = 0L)

        assertTrue(model.isVisible)
        assertEquals(SupabaseOutboxStatusUiModel.Severity.Error, model.severity)
        assertEquals("IllegalStateException", model.errorDescription)
        assertEquals(4, model.pendingCount)
        assertEquals(0, model.diagnosticsPendingCount)
        assertEquals(null, model.diagnosticsOldestPendingAgeMillis)
        assertEquals(0, model.telemetryPendingCount)
        assertEquals(null, model.telemetryOldestPendingAgeMillis)
        assertEquals(0, model.reportsPendingCount)
        assertEquals(null, model.reportsOldestPendingAgeMillis)
        assertEquals(0, model.paymentsAuditPendingCount)
        assertEquals(null, model.paymentsAuditOldestPendingAgeMillis)
        assertEquals(0, model.deviceStatusPendingCount)
        assertEquals(null, model.deviceStatusOldestPendingAgeMillis)
        assertEquals(0, model.reportDeliveriesPendingCount)
        assertEquals(null, model.reportDeliveriesOldestPendingAgeMillis)
    }

    @Test
    fun mapFallsBackToReadErrorWhenFlushErrorMissing() {
        val status = SupabaseOutboxMonitor.Status(
                pendingCount = 0,
                lastReadError = RuntimeException("disk not ready")
        )

        val model = SupabaseOutboxStatusUiMapper.map(status, nowMillis = 0L)

        assertTrue(model.isVisible)
        assertEquals(SupabaseOutboxStatusUiModel.Severity.Error, model.severity)
        assertEquals("disk not ready", model.errorDescription)
        assertEquals(0, model.diagnosticsPendingCount)
        assertEquals(null, model.diagnosticsOldestPendingAgeMillis)
        assertEquals(0, model.telemetryPendingCount)
        assertEquals(null, model.telemetryOldestPendingAgeMillis)
        assertEquals(0, model.reportsPendingCount)
        assertEquals(null, model.reportsOldestPendingAgeMillis)
        assertEquals(0, model.paymentsAuditPendingCount)
        assertEquals(null, model.paymentsAuditOldestPendingAgeMillis)
        assertEquals(0, model.deviceStatusPendingCount)
        assertEquals(null, model.deviceStatusOldestPendingAgeMillis)
        assertEquals(0, model.reportDeliveriesPendingCount)
        assertEquals(null, model.reportDeliveriesOldestPendingAgeMillis)
    }

    @Test
    fun mapHandlesDiagnosticsBacklogWithoutGlobalEntries() {
        val now = 50_000L
        val status = SupabaseOutboxMonitor.Status(
                pendingCount = 0,
                pendingCountsByTable = mapOf(DiagnosticsLogSupabaseSchema.TABLE_NAME to 1),
                oldestPendingAtByTable = mapOf(DiagnosticsLogSupabaseSchema.TABLE_NAME to now - 15_000L)
        )

        val model = SupabaseOutboxStatusUiMapper.map(status, nowMillis = now)

        assertFalse(model.isVisible)
        assertEquals(0, model.diagnosticsPendingCount)
        assertEquals(null, model.diagnosticsOldestPendingAgeMillis)
        assertEquals(0, model.telemetryPendingCount)
        assertEquals(null, model.telemetryOldestPendingAgeMillis)
        assertEquals(0, model.paymentsAuditPendingCount)
        assertEquals(null, model.paymentsAuditOldestPendingAgeMillis)
        assertEquals(0, model.deviceStatusPendingCount)
        assertEquals(null, model.deviceStatusOldestPendingAgeMillis)
    }

    @Test
    fun mapEmitsStaleSeverityWhenRefreshIsOutdated() {
        val refreshAge = SupabaseOutboxStatusUiMapper.STALE_REFRESH_THRESHOLD_MILLIS + 1_000L
        val now = refreshAge + 10_000L
        val status = SupabaseOutboxMonitor.Status(
                pendingCount = 0,
                lastRefreshAtMillis = now - refreshAge
        )

        val model = SupabaseOutboxStatusUiMapper.map(status, nowMillis = now)

        assertTrue(model.isVisible)
        assertEquals(SupabaseOutboxStatusUiModel.Severity.Stale, model.severity)
        assertTrue(model.isStale)
        assertEquals(refreshAge, model.staleAgeMillis)
    }

    @Test
    fun mapKeepsPendingSeverityWhenQueueHasEntriesEvenIfStale() {
        val refreshAge = SupabaseOutboxStatusUiMapper.STALE_REFRESH_THRESHOLD_MILLIS + 500L
        val now = refreshAge + 90_000L
        val status = SupabaseOutboxMonitor.Status(
                pendingCount = 2,
                oldestPendingAtMillis = now - 5_000L,
                lastRefreshAtMillis = now - refreshAge
        )

        val model = SupabaseOutboxStatusUiMapper.map(status, nowMillis = now)

        assertTrue(model.isVisible)
        assertEquals(SupabaseOutboxStatusUiModel.Severity.Pending, model.severity)
        assertTrue(model.isStale)
        assertEquals(refreshAge, model.staleAgeMillis)
    }
}
