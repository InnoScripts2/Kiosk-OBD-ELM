package com.selfservice.platform.data.diagnostics.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "diagnostics_logs",
    indices = [
        Index(value = ["timestamp_ms"]),
        Index(value = ["exported", "timestamp_ms"])
    ]
)
data class DiagnosticsLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp_ms") val timestampMillis: Long,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "message") val message: String,
    @ColumnInfo(name = "metadata_json") val metadataJson: String,
    @ColumnInfo(name = "exported") val exported: Boolean = false,
    @ColumnInfo(name = "exported_at_ms") val exportedAtMillis: Long? = null
)
