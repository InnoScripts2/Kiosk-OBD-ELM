package com.selfservice.platform.data.diagnostics.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "diagnostics_telemetry",
    indices = [
        Index(value = ["timestamp_ms"]),
        Index(value = ["session_id"]),
        Index(value = ["exported", "timestamp_ms"])
    ]
)
data class DiagnosticsTelemetryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp_ms") val timestampMillis: Long,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "session_id") val sessionId: String?,
    @ColumnInfo(name = "metadata_json") val metadataJson: String,
    @ColumnInfo(name = "exported") val exported: Boolean = false,
    @ColumnInfo(name = "exported_at_ms") val exportedAtMillis: Long? = null
)
