package com.selfservice.platform.data.diagnostics.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DiagnosticsReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DiagnosticsReportEntity): Long

    @Query("SELECT * FROM diagnostics_reports WHERE exported = 0 ORDER BY generated_at_ms ASC, id ASC")
    suspend fun pending(): List<DiagnosticsReportEntity>

    @Query(
        "UPDATE diagnostics_reports SET exported = 1, exported_at_ms = :exportedAtMillis " +
            "WHERE exported = 0 AND generated_at_ms <= :timestampMillis"
    )
    suspend fun markExportedUpTo(timestampMillis: Long, exportedAtMillis: Long)

    @Query("DELETE FROM diagnostics_reports WHERE generated_at_ms < :thresholdMillis")
    suspend fun deleteOlderThan(thresholdMillis: Long)

    @Query(
        "SELECT COUNT(*) AS totalCount, " +
            "COALESCE(SUM(CASE WHEN exported = 0 THEN 1 ELSE 0 END), 0) AS pendingCount, " +
            "MIN(CASE WHEN exported = 0 THEN generated_at_ms ELSE NULL END) AS oldestPendingAt " +
            "FROM diagnostics_reports"
    )
    suspend fun aggregate(): DiagnosticsReportAggregate?
}

data class DiagnosticsReportAggregate(
    val totalCount: Int,
    val pendingCount: Int,
    val oldestPendingAt: Long?
)