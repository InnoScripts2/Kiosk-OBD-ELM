package com.selfservice.platform.data.diagnostics.room

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiagnosticsLogDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName: String = "room-migration-test.db"

    @AfterTest
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationFrom3To4_createsExportIndexes() {
        createVersion3Database()

        val database = Room.databaseBuilder(
            context,
            DiagnosticsLogDatabase::class.java,
            databaseName
        )
            .addMigrations(
                DiagnosticsLogDatabaseMigrations.MIGRATION_3_4,
                DiagnosticsLogDatabaseMigrations.MIGRATION_4_5
            )
            .build()

        val migratedDb = database.openHelper.writableDatabase
        val logIndexes = migratedDb.indexNames("diagnostics_logs")
        val telemetryIndexes = migratedDb.indexNames("diagnostics_telemetry")

        database.close()

        assertTrue(logIndexes.contains("index_diagnostics_logs_timestamp_ms"))
        assertTrue(logIndexes.contains("index_diagnostics_logs_exported_timestamp_ms"))
        assertTrue(telemetryIndexes.contains("index_diagnostics_telemetry_timestamp_ms"))
        assertTrue(telemetryIndexes.contains("index_diagnostics_telemetry_session_id"))
        assertTrue(telemetryIndexes.contains("index_diagnostics_telemetry_exported_timestamp_ms"))
    }

    private fun createVersion3Database() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS diagnostics_logs (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "timestamp_ms INTEGER NOT NULL, " +
                            "category TEXT NOT NULL, " +
                            "message TEXT NOT NULL, " +
                            "metadata_json TEXT NOT NULL, " +
                            "exported INTEGER NOT NULL DEFAULT 0, " +
                            "exported_at_ms INTEGER" +
                            ")"
                    )
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

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) {
                    // No-op for test schema bootstrap.
                }
            })
            .build()

        FrameworkSQLiteOpenHelperFactory()
            .create(configuration)
            .useHelper { helper ->
                helper.writableDatabase.close()
            }
    }

    private fun SupportSQLiteDatabase.indexNames(table: String): Set<String> =
        buildSet {
            query("PRAGMA index_list('$table')").use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) {
                    add(cursor.getString(nameColumn))
                }
            }
        }

    private inline fun SupportSQLiteOpenHelper.useHelper(block: (SupportSQLiteOpenHelper) -> Unit) {
        try {
            block(this)
        } finally {
            close()
        }
    }
}
