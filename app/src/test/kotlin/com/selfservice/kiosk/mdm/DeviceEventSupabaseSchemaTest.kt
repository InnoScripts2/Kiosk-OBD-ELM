package com.selfservice.kiosk.mdm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeviceEventSupabaseSchemaTest {

    private val identity = DeviceIdentity(
        kioskId = "kiosk-99",
        environment = "prod",
        mdmDeviceId = "mdm-prod-2"
    )

    @Test
    fun `toRow contains identity and payload`() {
        val row = DeviceEventSupabaseSchema.Row(
            eventId = "evt-1",
            eventType = "command_ack",
            recordedAtMillis = 500L,
            severity = DeviceEventSupabaseSchema.Severity.WARNING,
            message = "Command queued",
            payload = mapOf("queue" to "mdm"),
            commandId = "cmd-7",
            commandStatus = "acknowledged",
            commandType = "reboot",
            source = "esper",
            metadata = mapOf("attempt" to 1)
        )

        val map = DeviceEventSupabaseSchema.toRow(identity, row)

        assertEquals("evt-1", map[DeviceEventSupabaseSchema.Columns.EVENT_ID])
        assertEquals("command_ack", map[DeviceEventSupabaseSchema.Columns.EVENT_TYPE])
        assertEquals("warning", map[DeviceEventSupabaseSchema.Columns.SEVERITY])
        assertEquals("Command queued", map[DeviceEventSupabaseSchema.Columns.MESSAGE])
        assertEquals("cmd-7", map[DeviceEventSupabaseSchema.Columns.COMMAND_ID])
        assertEquals("acknowledged", map[DeviceEventSupabaseSchema.Columns.COMMAND_STATUS])
        assertEquals("reboot", map[DeviceEventSupabaseSchema.Columns.COMMAND_TYPE])
        assertEquals("esper", map[DeviceEventSupabaseSchema.Columns.SOURCE])
        assertEquals(mapOf("queue" to "mdm"), map[DeviceEventSupabaseSchema.Columns.PAYLOAD])
        assertEquals(mapOf("attempt" to 1), map[DeviceEventSupabaseSchema.Columns.METADATA])
        assertEquals("kiosk-99", map[DeviceEventSupabaseSchema.Columns.KIOSK_ID])
        assertEquals("prod", map[DeviceEventSupabaseSchema.Columns.ENVIRONMENT])
        assertEquals("mdm-prod-2", map[DeviceEventSupabaseSchema.Columns.MDM_DEVICE_ID])
        assertEquals("1970-01-01T00:00:00.500+00:00", map[DeviceEventSupabaseSchema.Columns.RECORDED_AT])
    }

    @Test
    fun `optional values omitted when blank`() {
        val row = DeviceEventSupabaseSchema.Row(
            eventId = "evt-2",
            eventType = "heartbeat",
            recordedAtMillis = 1_000L,
            message = "",
            payload = emptyMap(),
            metadata = emptyMap(),
            commandId = null,
            commandStatus = null,
            commandType = null,
            source = null
        )

        val map = DeviceEventSupabaseSchema.toRow(identity.copy(mdmDeviceId = null), row)

        assertFalse(map.containsKey(DeviceEventSupabaseSchema.Columns.MESSAGE))
        assertFalse(map.containsKey(DeviceEventSupabaseSchema.Columns.PAYLOAD))
        assertFalse(map.containsKey(DeviceEventSupabaseSchema.Columns.METADATA))
        assertFalse(map.containsKey(DeviceEventSupabaseSchema.Columns.COMMAND_ID))
        assertFalse(map.containsKey(DeviceEventSupabaseSchema.Columns.COMMAND_STATUS))
        assertFalse(map.containsKey(DeviceEventSupabaseSchema.Columns.COMMAND_TYPE))
        assertFalse(map.containsKey(DeviceEventSupabaseSchema.Columns.SOURCE))
        assertFalse(map.containsKey(DeviceEventSupabaseSchema.Columns.MDM_DEVICE_ID))
    }
}