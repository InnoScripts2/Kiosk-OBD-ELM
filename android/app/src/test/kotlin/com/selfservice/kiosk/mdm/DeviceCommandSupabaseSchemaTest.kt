package com.selfservice.kiosk.mdm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeviceCommandSupabaseSchemaTest {

    private val identity = DeviceIdentity(
        kioskId = "kiosk-42",
        environment = "qa",
        mdmDeviceId = "mdm-123"
    )

    @Test
    fun `toRow populates all fields`() {
        val row = DeviceCommandSupabaseSchema.Row(
            commandId = "cmd-1",
            commandType = "reboot",
            status = DeviceCommandSupabaseSchema.Status.IN_PROGRESS,
            requestedAtMillis = 100L,
            acknowledgedAtMillis = 150L,
            startedAtMillis = 160L,
            completedAtMillis = 300L,
            latencyMillis = 140L,
            attempt = 2,
            payload = mapOf("reason" to "maintenance"),
            resultPayload = mapOf("queued" to true),
            errorMessage = "",
            source = "debug",
            metadata = mapOf("operator" to "qa-op")
        )

        val map = DeviceCommandSupabaseSchema.toRow(identity, row)

        assertEquals("cmd-1", map[DeviceCommandSupabaseSchema.Columns.COMMAND_ID])
        assertEquals("reboot", map[DeviceCommandSupabaseSchema.Columns.COMMAND_TYPE])
        assertEquals("in_progress", map[DeviceCommandSupabaseSchema.Columns.STATUS])
        assertEquals("kiosk-42", map[DeviceCommandSupabaseSchema.Columns.KIOSK_ID])
        assertEquals("qa", map[DeviceCommandSupabaseSchema.Columns.ENVIRONMENT])
        assertEquals("mdm-123", map[DeviceCommandSupabaseSchema.Columns.MDM_DEVICE_ID])
        assertEquals(140L, map[DeviceCommandSupabaseSchema.Columns.LATENCY_MS])
        assertEquals(2, map[DeviceCommandSupabaseSchema.Columns.ATTEMPT])
        assertEquals(mapOf("reason" to "maintenance"), map[DeviceCommandSupabaseSchema.Columns.PAYLOAD])
        assertEquals(mapOf("queued" to true), map[DeviceCommandSupabaseSchema.Columns.RESULT_PAYLOAD])
        assertEquals("debug", map[DeviceCommandSupabaseSchema.Columns.SOURCE])
        assertEquals(mapOf("operator" to "qa-op"), map[DeviceCommandSupabaseSchema.Columns.METADATA])
        assertEquals("1970-01-01T00:00:00.100+00:00", map[DeviceCommandSupabaseSchema.Columns.REQUESTED_AT])
        assertEquals("1970-01-01T00:00:00.150+00:00", map[DeviceCommandSupabaseSchema.Columns.ACKNOWLEDGED_AT])
        assertEquals("1970-01-01T00:00:00.160+00:00", map[DeviceCommandSupabaseSchema.Columns.STARTED_AT])
        assertEquals("1970-01-01T00:00:00.300+00:00", map[DeviceCommandSupabaseSchema.Columns.COMPLETED_AT])
    }

    @Test
    fun `empty optional values are omitted`() {
        val row = DeviceCommandSupabaseSchema.Row(
            commandId = "cmd-2",
            commandType = "ota",
            status = DeviceCommandSupabaseSchema.Status.RECEIVED,
            requestedAtMillis = 250L,
            payload = emptyMap(),
            metadata = emptyMap(),
            attempt = 0,
            latencyMillis = null,
            source = null
        )

        val map = DeviceCommandSupabaseSchema.toRow(identity.copy(kioskId = null), row)

        assertFalse(map.containsKey(DeviceCommandSupabaseSchema.Columns.KIOSK_ID))
        assertFalse(map.containsKey(DeviceCommandSupabaseSchema.Columns.PAYLOAD))
        assertFalse(map.containsKey(DeviceCommandSupabaseSchema.Columns.RESULT_PAYLOAD))
        assertFalse(map.containsKey(DeviceCommandSupabaseSchema.Columns.LATENCY_MS))
        assertFalse(map.containsKey(DeviceCommandSupabaseSchema.Columns.SOURCE))
        assertFalse(map.containsKey(DeviceCommandSupabaseSchema.Columns.METADATA))
    }
}