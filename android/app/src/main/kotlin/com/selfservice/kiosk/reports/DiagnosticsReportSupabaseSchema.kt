package com.selfservice.kiosk.reports

import com.selfservice.core.supabase.SupabaseCanonicalizer
import com.selfservice.platform.data.diagnostics.DiagnosticsReportRecord
import java.security.MessageDigest
import java.util.Locale
import java.util.Base64
import org.json.JSONObject

/**
 * Привязки таблицы Supabase для загрузки диагностических отчётов.
 */
object DiagnosticsReportSupabaseSchema {

    const val TABLE_NAME: String = "diagnostics_reports"

    object Columns {
        const val REPORT_ID: String = "report_id"
        const val SESSION_ID: String = "session_id"
        const val GENERATED_AT_MS: String = "generated_at_ms"
        const val REPORT_HTML: String = "report_html"
        const val REPORT_PDF_BASE64: String = "report_pdf_base64"
        const val METADATA: String = "metadata"
        const val KIOSK_ID: String = "kiosk_id"
        const val ENVIRONMENT: String = "environment"
    }

    fun toRow(
        record: DiagnosticsReportRecord,
        additionalFields: Map<String, Any?> = emptyMap()
    ): Map<String, Any?> {
        val metadataValue = if (record.metadata.isEmpty()) {
            JSONObject()
        } else {
            JSONObject(record.metadata)
        }
        val base = mutableMapOf<String, Any?>(
            Columns.REPORT_ID to stableId(record),
            Columns.SESSION_ID to record.sessionId,
            Columns.GENERATED_AT_MS to record.generatedAtMillis,
            Columns.REPORT_HTML to record.html,
            Columns.REPORT_PDF_BASE64 to Base64.getEncoder().encodeToString(record.pdfBytes),
            Columns.METADATA to metadataValue
        )
        if (additionalFields.isNotEmpty()) {
            additionalFields.forEach { (key, value) ->
                base[key] = value
            }
        }
        return base
    }

    fun stableId(record: DiagnosticsReportRecord): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(record.sessionId.toByteArray())
        digest.update(SEPARATOR)
        digest.update(record.generatedAtMillis.toString().toByteArray())
        digest.update(SEPARATOR)
        digest.update(SupabaseCanonicalizer.canonicalize(record.metadata).toByteArray())
        return digest.digest().joinToString(separator = "") { byte ->
            String.format(Locale.US, "%02x", byte)
        }
    }

    private val SEPARATOR = byteArrayOf(0)
}
