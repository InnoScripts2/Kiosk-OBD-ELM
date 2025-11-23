package com.selfservice.kiosk

import androidx.annotation.VisibleForTesting
import com.selfservice.platform.logging.DiagnosticsLogSupabaseSchema
import com.selfservice.kiosk.payments.PaymentsAuditSupabaseSchema
import com.selfservice.kiosk.reports.DiagnosticsReportSupabaseSchema
import com.selfservice.kiosk.telemetry.DiagnosticsTelemetrySupabaseSchema

/**
 * Provides build-time configuration for the kiosk runtime. Consolidates metadata required by
 * diagnostics logging and other background services.
 */
object KioskRuntimeConfig {

    /** Returns the kiosk identifier if set in build configuration, otherwise null. */
    fun kioskId(rawValue: String = BuildConfig.KIOSK_ID): String? = rawValue.trim().takeIf { it.isNotEmpty() }

    /**
     * Resolves the deployment environment label. Defaults to "dev" for debug builds and
     * "prod" otherwise when the build config does not provide an explicit value.
     */
    fun environment(
        rawValue: String = BuildConfig.KIOSK_ENVIRONMENT,
        isDebug: Boolean = BuildConfig.DEBUG
    ): String {
        val normalized = rawValue.trim()
        if (normalized.isNotEmpty()) {
            return normalized
        }
        return if (isDebug) DEFAULT_DEBUG_ENVIRONMENT else DEFAULT_RELEASE_ENVIRONMENT
    }

    /**
     * Generates additional Supabase payload fields for diagnostics log uploads.
     */
    fun diagnosticsLogFields(
        rawKioskId: String = BuildConfig.KIOSK_ID,
        rawEnvironment: String = BuildConfig.KIOSK_ENVIRONMENT,
        isDebug: Boolean = BuildConfig.DEBUG
    ): Map<String, Any?> = supabaseCommonFields(
        rawKioskId = rawKioskId,
        rawEnvironment = rawEnvironment,
        isDebug = isDebug,
        kioskColumn = DiagnosticsLogSupabaseSchema.Columns.KIOSK_ID,
        environmentColumn = DiagnosticsLogSupabaseSchema.Columns.ENVIRONMENT
    )

    fun diagnosticsTelemetryFields(
        rawKioskId: String = BuildConfig.KIOSK_ID,
        rawEnvironment: String = BuildConfig.KIOSK_ENVIRONMENT,
        isDebug: Boolean = BuildConfig.DEBUG
    ): Map<String, Any?> = supabaseCommonFields(
        rawKioskId = rawKioskId,
        rawEnvironment = rawEnvironment,
        isDebug = isDebug,
        kioskColumn = DiagnosticsTelemetrySupabaseSchema.Columns.KIOSK_ID,
        environmentColumn = DiagnosticsTelemetrySupabaseSchema.Columns.ENVIRONMENT
    )

    fun diagnosticsReportFields(
        rawKioskId: String = BuildConfig.KIOSK_ID,
        rawEnvironment: String = BuildConfig.KIOSK_ENVIRONMENT,
        isDebug: Boolean = BuildConfig.DEBUG
    ): Map<String, Any?> = supabaseCommonFields(
        rawKioskId = rawKioskId,
        rawEnvironment = rawEnvironment,
        isDebug = isDebug,
        kioskColumn = DiagnosticsReportSupabaseSchema.Columns.KIOSK_ID,
        environmentColumn = DiagnosticsReportSupabaseSchema.Columns.ENVIRONMENT
    )

    fun paymentsAuditFields(
        rawKioskId: String = BuildConfig.KIOSK_ID,
        rawEnvironment: String = BuildConfig.KIOSK_ENVIRONMENT,
        isDebug: Boolean = BuildConfig.DEBUG
    ): Map<String, Any?> = supabaseCommonFields(
        rawKioskId = rawKioskId,
        rawEnvironment = rawEnvironment,
        isDebug = isDebug,
        kioskColumn = PaymentsAuditSupabaseSchema.Columns.KIOSK_ID,
        environmentColumn = PaymentsAuditSupabaseSchema.Columns.ENVIRONMENT
    )

    @VisibleForTesting
    internal const val DEFAULT_DEBUG_ENVIRONMENT = "dev"

    @VisibleForTesting
    internal const val DEFAULT_RELEASE_ENVIRONMENT = "prod"

    private fun supabaseCommonFields(
        rawKioskId: String,
        rawEnvironment: String,
        isDebug: Boolean,
        kioskColumn: String,
        environmentColumn: String
    ): Map<String, Any?> {
        val fields = mutableMapOf<String, Any?>(
            environmentColumn to environment(rawEnvironment, isDebug)
        )
        kioskId(rawKioskId)?.let { id ->
            fields[kioskColumn] = id
        }
        return fields
    }
}
