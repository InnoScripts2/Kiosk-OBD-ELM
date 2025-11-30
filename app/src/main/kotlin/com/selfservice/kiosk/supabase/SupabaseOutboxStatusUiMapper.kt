package com.selfservice.kiosk.supabase

import com.selfservice.kiosk.mdm.DeviceCommandSupabaseSchema
import com.selfservice.kiosk.mdm.DeviceEventSupabaseSchema
import com.selfservice.kiosk.mdm.DeviceStatusSupabaseSchema
import com.selfservice.platform.logging.DiagnosticsLogSupabaseSchema
import com.selfservice.kiosk.payments.PaymentsAuditSupabaseSchema
import com.selfservice.kiosk.reports.DiagnosticsReportDeliverySupabaseSchema
import com.selfservice.kiosk.reports.DiagnosticsReportSupabaseSchema
import com.selfservice.kiosk.telemetry.DiagnosticsTelemetrySupabaseSchema

/**
 * Maps low-level [SupabaseOutboxMonitor.Status] snapshots into a simplified model that can be
 * consumed by UI layers without duplicating business rules around visibility and severity.
 */
object SupabaseOutboxStatusUiMapper {

    const val STALE_REFRESH_THRESHOLD_MILLIS: Long = 2L * 60L * 1000L

    fun map(status: SupabaseOutboxMonitor.Status, nowMillis: Long = System.currentTimeMillis()): SupabaseOutboxStatusUiModel {
        val error = status.lastError ?: status.lastReadError
        val oldestAgeMillis = toAge(status.oldestPendingAtMillis, nowMillis)
        val staleAgeMillis = toAge(status.lastRefreshAtMillis, nowMillis)
        val isStale = staleAgeMillis != null && staleAgeMillis >= STALE_REFRESH_THRESHOLD_MILLIS
        val diagnosticsCount = status.pendingCountsByTable[DiagnosticsLogSupabaseSchema.TABLE_NAME] ?: 0
        val diagnosticsOldestAgeMillis = toAge(status.oldestPendingAtByTable[DiagnosticsLogSupabaseSchema.TABLE_NAME], nowMillis)
        val telemetryCount = status.pendingCountsByTable[DiagnosticsTelemetrySupabaseSchema.TABLE_NAME] ?: 0
        val telemetryOldestAgeMillis = toAge(status.oldestPendingAtByTable[DiagnosticsTelemetrySupabaseSchema.TABLE_NAME], nowMillis)
        val reportsCount = status.pendingCountsByTable[DiagnosticsReportSupabaseSchema.TABLE_NAME] ?: 0
        val reportsOldestAgeMillis = toAge(status.oldestPendingAtByTable[DiagnosticsReportSupabaseSchema.TABLE_NAME], nowMillis)
        val paymentsAuditCount = status.pendingCountsByTable[PaymentsAuditSupabaseSchema.TABLE_NAME] ?: 0
        val paymentsAuditOldestAgeMillis = toAge(status.oldestPendingAtByTable[PaymentsAuditSupabaseSchema.TABLE_NAME], nowMillis)
        val deviceStatusCount = status.pendingCountsByTable[DeviceStatusSupabaseSchema.TABLE_NAME] ?: 0
        val deviceStatusOldestAgeMillis = toAge(status.oldestPendingAtByTable[DeviceStatusSupabaseSchema.TABLE_NAME], nowMillis)
        val deviceCommandsCount = status.pendingCountsByTable[DeviceCommandSupabaseSchema.TABLE_NAME] ?: 0
        val deviceCommandsOldestAgeMillis = toAge(status.oldestPendingAtByTable[DeviceCommandSupabaseSchema.TABLE_NAME], nowMillis)
        val deviceEventsCount = status.pendingCountsByTable[DeviceEventSupabaseSchema.TABLE_NAME] ?: 0
        val deviceEventsOldestAgeMillis = toAge(status.oldestPendingAtByTable[DeviceEventSupabaseSchema.TABLE_NAME], nowMillis)
        val reportDeliveriesCount = status.pendingCountsByTable[DiagnosticsReportDeliverySupabaseSchema.TABLE_NAME] ?: 0
        val reportDeliveriesOldestAgeMillis = toAge(status.oldestPendingAtByTable[DiagnosticsReportDeliverySupabaseSchema.TABLE_NAME], nowMillis)

        if (error != null) {
            val description = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            return SupabaseOutboxStatusUiModel(
                    isVisible = true,
                    severity = SupabaseOutboxStatusUiModel.Severity.Error,
                    pendingCount = status.pendingCount,
                    oldestPendingAgeMillis = oldestAgeMillis,
                    isSyncActive = status.isRunning,
                    errorDescription = description,
                    diagnosticsPendingCount = diagnosticsCount,
                    diagnosticsOldestPendingAgeMillis = diagnosticsOldestAgeMillis,
                    telemetryPendingCount = telemetryCount,
                    telemetryOldestPendingAgeMillis = telemetryOldestAgeMillis,
                    reportsPendingCount = reportsCount,
                    reportsOldestPendingAgeMillis = reportsOldestAgeMillis,
                        paymentsAuditPendingCount = paymentsAuditCount,
                        paymentsAuditOldestPendingAgeMillis = paymentsAuditOldestAgeMillis,
                    deviceStatusPendingCount = deviceStatusCount,
                        deviceStatusOldestPendingAgeMillis = deviceStatusOldestAgeMillis,
                        deviceCommandsPendingCount = deviceCommandsCount,
                        deviceCommandsOldestPendingAgeMillis = deviceCommandsOldestAgeMillis,
                        deviceEventsPendingCount = deviceEventsCount,
                        deviceEventsOldestPendingAgeMillis = deviceEventsOldestAgeMillis,
                    reportDeliveriesPendingCount = reportDeliveriesCount,
                    reportDeliveriesOldestPendingAgeMillis = reportDeliveriesOldestAgeMillis,
                    isStale = isStale,
                    staleAgeMillis = staleAgeMillis
            )
        }

        if (status.pendingCount > 0) {
            return SupabaseOutboxStatusUiModel(
                    isVisible = true,
                    severity = SupabaseOutboxStatusUiModel.Severity.Pending,
                    pendingCount = status.pendingCount,
                    oldestPendingAgeMillis = oldestAgeMillis,
                    isSyncActive = status.isRunning,
                    errorDescription = null,
                    diagnosticsPendingCount = diagnosticsCount,
                    diagnosticsOldestPendingAgeMillis = diagnosticsOldestAgeMillis,
                    telemetryPendingCount = telemetryCount,
                    telemetryOldestPendingAgeMillis = telemetryOldestAgeMillis,
                    reportsPendingCount = reportsCount,
                    reportsOldestPendingAgeMillis = reportsOldestAgeMillis,
                        paymentsAuditPendingCount = paymentsAuditCount,
                        paymentsAuditOldestPendingAgeMillis = paymentsAuditOldestAgeMillis,
                    deviceStatusPendingCount = deviceStatusCount,
                        deviceStatusOldestPendingAgeMillis = deviceStatusOldestAgeMillis,
                        deviceCommandsPendingCount = deviceCommandsCount,
                        deviceCommandsOldestPendingAgeMillis = deviceCommandsOldestAgeMillis,
                        deviceEventsPendingCount = deviceEventsCount,
                        deviceEventsOldestPendingAgeMillis = deviceEventsOldestAgeMillis,
                    reportDeliveriesPendingCount = reportDeliveriesCount,
                    reportDeliveriesOldestPendingAgeMillis = reportDeliveriesOldestAgeMillis,
                    isStale = isStale,
                    staleAgeMillis = staleAgeMillis
            )
        }

        if (isStale) {
            return SupabaseOutboxStatusUiModel(
                    isVisible = true,
                    severity = SupabaseOutboxStatusUiModel.Severity.Stale,
                    pendingCount = status.pendingCount,
                    oldestPendingAgeMillis = oldestAgeMillis,
                    isSyncActive = status.isRunning,
                    errorDescription = null,
                    diagnosticsPendingCount = diagnosticsCount,
                    diagnosticsOldestPendingAgeMillis = diagnosticsOldestAgeMillis,
                    telemetryPendingCount = telemetryCount,
                    telemetryOldestPendingAgeMillis = telemetryOldestAgeMillis,
                    reportsPendingCount = reportsCount,
                    reportsOldestPendingAgeMillis = reportsOldestAgeMillis,
                    paymentsAuditPendingCount = paymentsAuditCount,
                    paymentsAuditOldestPendingAgeMillis = paymentsAuditOldestAgeMillis,
                    deviceStatusPendingCount = deviceStatusCount,
                    deviceStatusOldestPendingAgeMillis = deviceStatusOldestAgeMillis,
                    deviceCommandsPendingCount = deviceCommandsCount,
                    deviceCommandsOldestPendingAgeMillis = deviceCommandsOldestAgeMillis,
                    deviceEventsPendingCount = deviceEventsCount,
                    deviceEventsOldestPendingAgeMillis = deviceEventsOldestAgeMillis,
                    reportDeliveriesPendingCount = reportDeliveriesCount,
                    reportDeliveriesOldestPendingAgeMillis = reportDeliveriesOldestAgeMillis,
                    isStale = true,
                    staleAgeMillis = staleAgeMillis
            )
        }

        return SupabaseOutboxStatusUiModel.Hidden
    }

    private fun toAge(timestampMillis: Long?, nowMillis: Long): Long? {
        if (timestampMillis == null || timestampMillis <= 0) {
            return null
        }
        val age = nowMillis - timestampMillis
        return if (age >= 0) age else null
    }

}

/**
 * Presentation-friendly model for surfacing Supabase outbox health in UI layers.
 */
data class SupabaseOutboxStatusUiModel(
    val isVisible: Boolean,
    val severity: Severity = Severity.Pending,
    val pendingCount: Int = 0,
    val oldestPendingAgeMillis: Long? = null,
    val isSyncActive: Boolean = false,
    val errorDescription: String? = null,
    val diagnosticsPendingCount: Int = 0,
    val diagnosticsOldestPendingAgeMillis: Long? = null,
    val telemetryPendingCount: Int = 0,
    val telemetryOldestPendingAgeMillis: Long? = null,
    val reportsPendingCount: Int = 0,
    val reportsOldestPendingAgeMillis: Long? = null,
    val paymentsAuditPendingCount: Int = 0,
    val paymentsAuditOldestPendingAgeMillis: Long? = null,
    val reportDeliveriesPendingCount: Int = 0,
    val reportDeliveriesOldestPendingAgeMillis: Long? = null,
    val deviceStatusPendingCount: Int = 0,
    val deviceStatusOldestPendingAgeMillis: Long? = null,
    val deviceCommandsPendingCount: Int = 0,
    val deviceCommandsOldestPendingAgeMillis: Long? = null,
    val deviceEventsPendingCount: Int = 0,
    val deviceEventsOldestPendingAgeMillis: Long? = null,
    val isStale: Boolean = false,
    val staleAgeMillis: Long? = null
) {

    enum class Severity { Pending, Error, Stale }

    companion object {
        val Hidden = SupabaseOutboxStatusUiModel(isVisible = false)
    }
}
