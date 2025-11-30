package com.selfservice.obd.core.persistence

/** Declares the storage engine selected for diagnostics persistence. */
enum class StorageEngine {
    ROOM
}

data class DiagnosticsTableSpec(
        val name: String,
        val primaryKey: String,
        val columns: List<String>
)

data class MigrationSpec(val fromVersion: Int, val toVersion: Int, val description: String)

data class DiagnosticsDatabaseSpec(
        val engine: StorageEngine,
        val schemaVersion: Int,
        val tables: List<DiagnosticsTableSpec>,
        val migrations: List<MigrationSpec>
)

object DiagnosticsDatabaseDefaults {
    val telemetryTable: DiagnosticsTableSpec =
            DiagnosticsTableSpec(
                    name = "telemetry",
                    primaryKey = "id",
                    columns =
                            listOf(
                                    "id TEXT NOT NULL",
                                    "session_id TEXT NOT NULL",
                                    "payload TEXT NOT NULL",
                                    "created_at INTEGER NOT NULL"
                            )
            )

    val database: DiagnosticsDatabaseSpec =
            DiagnosticsDatabaseSpec(
                    engine = StorageEngine.ROOM,
                    schemaVersion = 1,
                    tables = listOf(telemetryTable),
                    migrations =
                            listOf(
                                    MigrationSpec(
                                            fromVersion = 0,
                                            toVersion = 1,
                                            description =
                                                    "Initial Room schema for telemetry entries"
                                    )
                            )
            )
}
