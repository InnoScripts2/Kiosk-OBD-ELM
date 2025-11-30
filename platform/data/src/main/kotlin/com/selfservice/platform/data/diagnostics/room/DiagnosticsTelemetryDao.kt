package com.selfservice.platform.data.diagnostics.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DiagnosticsTelemetryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DiagnosticsTelemetryEntity): Long

    @Query("SELECT * FROM diagnostics_telemetry WHERE exported = 0 ORDER BY timestamp_ms ASC, id ASC")
    suspend fun pending(): List<DiagnosticsTelemetryEntity>

    @Query(
        "UPDATE diagnostics_telemetry SET exported = 1, exported_at_ms = :exportedAtMillis " +
            "WHERE exported = 0 AND timestamp_ms <= :timestampMillis"
    )
    suspend fun markExportedUpTo(timestampMillis: Long, exportedAtMillis: Long)

    @Query("DELETE FROM diagnostics_telemetry WHERE timestamp_ms < :thresholdMillis")
    suspend fun deleteOlderThan(thresholdMillis: Long)

    @Query(
        "SELECT COUNT(*) AS totalCount, " +
            "COALESCE(SUM(CASE WHEN exported = 0 THEN 1 ELSE 0 END), 0) AS pendingCount, " +
            "MIN(CASE WHEN exported = 0 THEN timestamp_ms ELSE NULL END) AS oldestPendingAt " +
            "FROM diagnostics_telemetry"
    )
    suspend fun aggregate(): DiagnosticsTelemetryAggregate?
}

data class DiagnosticsTelemetryAggregate(
    val totalCount: Int,
    val pendingCount: Int,
    val oldestPendingAt: Long?
)
