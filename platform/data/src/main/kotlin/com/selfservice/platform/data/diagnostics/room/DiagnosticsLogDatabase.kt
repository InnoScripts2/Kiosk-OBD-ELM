package com.selfservice.platform.data.diagnostics.room

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        DiagnosticsLogEntity::class,
        DiagnosticsTelemetryEntity::class,
        DiagnosticsReportEntity::class
    ],
    version = DiagnosticsLogDatabase.VERSION,
    exportSchema = false
)
abstract class DiagnosticsLogDatabase : RoomDatabase() {
    abstract fun diagnosticsLogDao(): DiagnosticsLogDao
    abstract fun diagnosticsTelemetryDao(): DiagnosticsTelemetryDao
    abstract fun diagnosticsReportDao(): DiagnosticsReportDao

    companion object {
        const val VERSION = 5
        const val NAME = "diagnostics_logs.db"
    }
}
