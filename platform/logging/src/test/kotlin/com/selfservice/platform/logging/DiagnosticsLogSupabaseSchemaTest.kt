package com.selfservice.platform.logging

import com.selfservice.core.logging.DiagnosticsLogEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.json.JSONObject

class DiagnosticsLogSupabaseSchemaTest {

    @Test
    fun `toRow maps entry into supabase payload`() {
        val entry = DiagnosticsLogEntry(
            category = "session",
            message = "started",
            timestampMillis = 1_733_000_000L,
            metadata = mapOf("step" to 3, "tag" to "demo")
        )

        val row = DiagnosticsLogSupabaseSchema.toRow(
            entry,
            mapOf(
                DiagnosticsLogSupabaseSchema.Columns.KIOSK_ID to "kiosk-42",
                DiagnosticsLogSupabaseSchema.Columns.ENVIRONMENT to "prod"
            )
        )

        assertEquals("kiosk-42", row[DiagnosticsLogSupabaseSchema.Columns.KIOSK_ID])
        assertEquals("prod", row[DiagnosticsLogSupabaseSchema.Columns.ENVIRONMENT])
        assertEquals(entry.timestampMillis, row[DiagnosticsLogSupabaseSchema.Columns.DEVICE_TIMESTAMP_MS])
        assertEquals(entry.category, row[DiagnosticsLogSupabaseSchema.Columns.CATEGORY])
        assertEquals(entry.message, row[DiagnosticsLogSupabaseSchema.Columns.MESSAGE])
        val metadata = row[DiagnosticsLogSupabaseSchema.Columns.METADATA] as JSONObject
        assertEquals(3, metadata.getInt("step"))
        assertEquals("demo", metadata.getString("tag"))
        val entryId = row[DiagnosticsLogSupabaseSchema.Columns.ENTRY_ID] as String
        assertNotNull(entryId)
        assertEquals(64, entryId.length)
    }

    @Test
    fun `stableId produces deterministic hash`() {
        val entry = DiagnosticsLogEntry(
            category = "session",
            message = "started",
            timestampMillis = 123L,
            metadata = mapOf("order" to listOf(1, 2, 3))
        )

        val id1 = DiagnosticsLogSupabaseSchema.stableId(entry)
        val id2 = DiagnosticsLogSupabaseSchema.stableId(entry.copy())

        assertEquals(id1, id2)
    }
}
