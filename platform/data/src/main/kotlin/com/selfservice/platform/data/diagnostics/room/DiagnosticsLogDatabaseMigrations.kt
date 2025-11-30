package com.selfservice.platform.data.diagnostics.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DiagnosticsLogDatabaseMigrations {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE diagnostics_logs ADD COLUMN exported INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE diagnostics_logs ADD COLUMN exported_at_ms INTEGER")
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS diagnostics_telemetry (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "timestamp_ms INTEGER NOT NULL, " +
                    "event_type TEXT NOT NULL, " +
                    "session_id TEXT, " +
                    "metadata_json TEXT NOT NULL, " +
                    "exported INTEGER NOT NULL DEFAULT 0, " +
                    "exported_at_ms INTEGER" +
                    ")"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_diagnostics_telemetry_timestamp_ms " +
                    "ON diagnostics_telemetry(timestamp_ms)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_diagnostics_telemetry_session_id " +
                    "ON diagnostics_telemetry(session_id)"
            )
        }
    }

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_diagnostics_logs_timestamp_ms " +
                    "ON diagnostics_logs(timestamp_ms)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_diagnostics_logs_exported_timestamp_ms " +
                    "ON diagnostics_logs(exported, timestamp_ms)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_diagnostics_telemetry_exported_timestamp_ms " +
                    "ON diagnostics_telemetry(exported, timestamp_ms)"
            )
        }
    }

    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS diagnostics_reports (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "session_id TEXT NOT NULL, " +
                    "generated_at_ms INTEGER NOT NULL, " +
                    "report_html TEXT NOT NULL, " +
                    "report_pdf BLOB NOT NULL, " +
                    "metadata_json TEXT NOT NULL, " +
                    "exported INTEGER NOT NULL DEFAULT 0, " +
                    "exported_at_ms INTEGER" +
                    ")"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_diagnostics_reports_generated_at_ms " +
                    "ON diagnostics_reports(generated_at_ms)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_diagnostics_reports_session_id " +
                    "ON diagnostics_reports(session_id)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_diagnostics_reports_exported_generated_at_ms " +
                    "ON diagnostics_reports(exported, generated_at_ms)"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
