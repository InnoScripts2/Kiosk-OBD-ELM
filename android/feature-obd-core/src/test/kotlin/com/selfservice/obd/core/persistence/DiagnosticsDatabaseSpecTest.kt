package com.selfservice.obd.core.persistence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticsDatabaseSpecTest {

    @Test
    fun defaultSpecUsesRoomAndInitialSchema() {
        val spec = DiagnosticsDatabaseDefaults.database

        assertEquals(StorageEngine.ROOM, spec.engine)
        assertEquals(1, spec.schemaVersion)
        assertTrue(spec.tables.any { it.name == "telemetry" })
        assertTrue(spec.migrations.any { it.toVersion == 1 })
    }
}
