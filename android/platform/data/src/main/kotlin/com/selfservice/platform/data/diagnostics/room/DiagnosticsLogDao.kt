package com.selfservice.platform.data.diagnostics.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DiagnosticsLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DiagnosticsLogEntity): Long

    @Query(
        "UPDATE diagnostics_logs SET exported = 1, exported_at_ms = :exportedAtMillis " +
            "WHERE exported = 0 AND timestamp_ms <= :timestampMillis"
    )
    suspend fun markExportedUpTo(timestampMillis: Long, exportedAtMillis: Long)

    @Query("DELETE FROM diagnostics_logs WHERE timestamp_ms < :thresholdMillis")
    suspend fun deleteOlderThan(thresholdMillis: Long)

    @Query(
        "SELECT COUNT(*) AS totalCount, " +
            "COALESCE(SUM(CASE WHEN exported = 0 THEN 1 ELSE 0 END), 0) AS pendingCount, " +
            "MIN(CASE WHEN exported = 0 THEN timestamp_ms ELSE NULL END) AS oldestPendingAt " +
            "FROM diagnostics_logs"
    )
    suspend fun aggregate(): DiagnosticsLogAggregate?
}

data class DiagnosticsLogAggregate(
    val totalCount: Int,
    val pendingCount: Int,
    val oldestPendingAt: Long?
)
