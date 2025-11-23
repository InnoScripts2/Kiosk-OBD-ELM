package com.selfservice.kiosk.telemetry

import com.selfservice.core.supabase.SupabaseCanonicalizer
import com.selfservice.platform.data.diagnostics.DiagnosticsTelemetryRecord
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONObject

/** Defines Supabase schema bindings for diagnostics telemetry uploads. */
object DiagnosticsTelemetrySupabaseSchema {

    const val TABLE_NAME: String = "diagnostics_telemetry"

    object Columns {
        const val EVENT_ID: String = "event_id"
        const val DEVICE_TIMESTAMP_MS: String = "device_timestamp_ms"
        const val EVENT_TYPE: String = "event_type"
        const val SESSION_ID: String = "session_id"
        const val METADATA: String = "metadata"
        const val KIOSK_ID: String = "kiosk_id"
        const val ENVIRONMENT: String = "environment"
    }

    fun toRow(
        record: DiagnosticsTelemetryRecord,
        additionalFields: Map<String, Any?> = emptyMap()
    ): Map<String, Any?> {
        val metadataValue = if (record.metadata.isEmpty()) {
            JSONObject()
        } else {
            JSONObject(record.metadata)
        }
        val base = mutableMapOf<String, Any?>(
            Columns.EVENT_ID to stableId(record),
            Columns.DEVICE_TIMESTAMP_MS to record.timestampMillis,
            Columns.EVENT_TYPE to record.eventType,
            Columns.SESSION_ID to record.sessionId,
            Columns.METADATA to metadataValue
        )
        if (additionalFields.isNotEmpty()) {
            additionalFields.forEach { (key, value) -> base[key] = value }
        }
        return base
    }

    fun stableId(record: DiagnosticsTelemetryRecord): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(record.eventType.toByteArray())
        digest.update(SEPARATOR)
        digest.update((record.sessionId ?: SESSION_NULL_SENTINEL).toByteArray())
        digest.update(SEPARATOR)
        digest.update(record.timestampMillis.toString().toByteArray())
        digest.update(SEPARATOR)
        digest.update(SupabaseCanonicalizer.canonicalize(record.metadata).toByteArray())
        return digest.digest().joinToString(separator = "") { byte ->
            String.format(Locale.US, "%02x", byte)
        }
    }

    private const val SESSION_NULL_SENTINEL = "<null>"
    private val SEPARATOR = byteArrayOf(0)
}
