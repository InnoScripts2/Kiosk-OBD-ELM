package com.selfservice.platform.data.diagnostics.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "diagnostics_reports",
    indices = [
        Index(value = ["generated_at_ms"]),
        Index(value = ["session_id"]),
        Index(value = ["exported", "generated_at_ms"])
    ]
)
data class DiagnosticsReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "generated_at_ms") val generatedAtMillis: Long,
    @ColumnInfo(name = "report_html") val reportHtml: String,
    @ColumnInfo(name = "report_pdf", typeAffinity = ColumnInfo.BLOB) val reportPdf: ByteArray,
    @ColumnInfo(name = "metadata_json") val metadataJson: String,
    @ColumnInfo(name = "exported") val exported: Boolean = false,
    @ColumnInfo(name = "exported_at_ms") val exportedAtMillis: Long? = null
)