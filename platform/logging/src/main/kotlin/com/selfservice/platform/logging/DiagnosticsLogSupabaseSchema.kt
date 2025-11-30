package com.selfservice.platform.logging

import com.selfservice.core.logging.DiagnosticsLogEntry
import com.selfservice.core.supabase.SupabaseCanonicalizer
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONObject

/**
 * Defines the Supabase table schema used to upload diagnostics log entries from the kiosk.
 */
object DiagnosticsLogSupabaseSchema {

    const val TABLE_NAME: String = "diagnostics_logs"

    object Columns {
        const val ENTRY_ID: String = "entry_id"
        const val DEVICE_TIMESTAMP_MS: String = "device_timestamp_ms"
        const val CATEGORY: String = "category"
        const val MESSAGE: String = "message"
        const val METADATA: String = "metadata"
        const val KIOSK_ID: String = "kiosk_id"
        const val ENVIRONMENT: String = "environment"
    }

    fun toRow(
        entry: DiagnosticsLogEntry,
        additionalFields: Map<String, Any?> = emptyMap()
    ): Map<String, Any?> {
        val metadataValue = if (entry.metadata.isEmpty()) {
            JSONObject()
        } else {
            JSONObject(entry.metadata)
        }
        val base = mutableMapOf<String, Any?>(
            Columns.ENTRY_ID to stableId(entry),
            Columns.DEVICE_TIMESTAMP_MS to entry.timestampMillis,
            Columns.CATEGORY to entry.category,
            Columns.MESSAGE to entry.message,
            Columns.METADATA to metadataValue
        )
        if (additionalFields.isNotEmpty()) {
            additionalFields.forEach { (key, value) ->
                base[key] = value
            }
        }
        return base
    }

    fun stableId(entry: DiagnosticsLogEntry): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(entry.category.toByteArray())
        digest.update(SEPARATOR)
        digest.update(entry.message.toByteArray())
        digest.update(SEPARATOR)
        digest.update(entry.timestampMillis.toString().toByteArray())
        digest.update(SEPARATOR)
        digest.update(SupabaseCanonicalizer.canonicalize(entry.metadata).toByteArray())
        return digest.digest().joinToString(separator = "") { byte ->
            String.format(Locale.US, "%02x", byte)
        }
    }

    private val SEPARATOR = byteArrayOf(0)
}
