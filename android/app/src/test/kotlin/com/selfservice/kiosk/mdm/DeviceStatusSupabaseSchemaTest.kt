package com.selfservice.kiosk.mdm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceStatusSupabaseSchemaTest {

    @Test
    fun toRowIncludesAllFields() {
        val snapshot = DeviceStatusSnapshot(
            kioskId = "kiosk-42",
            environment = "qa",
            mdmDeviceId = "mdm-007",
            serialNumber = "ABC123",
            hardwareModel = "Pixel",
            hardwareManufacturer = "Google",
            osVersion = "14",
            osApiLevel = 34,
            appVersionName = "1.2.3",
            appVersionCode = 45,
            batteryPercent = 87,
            isCharging = true,
            networkType = "wifi",
            vpnActive = false,
            complianceState = "compliant",
            policyVersion = "2025.11",
            uptimeSeconds = 1234L,
            status = DeviceStatusSnapshot.DeviceState.MAINTENANCE,
            lastCommandId = "cmd-1",
            lastCommandStatus = "success",
            annotations = mapOf("foo" to "bar")
        )

        val row = DeviceStatusSupabaseSchema.toRow(
            snapshot = snapshot,
            recordedAtIso = "2025-11-23T10:00:00Z",
            additionalFields = mapOf("extra" to "value")
        )

        assertEquals("2025-11-23T10:00:00Z", row[DeviceStatusSupabaseSchema.Columns.RECORDED_AT])
        assertEquals("kiosk-42", row[DeviceStatusSupabaseSchema.Columns.KIOSK_ID])
        assertEquals("qa", row[DeviceStatusSupabaseSchema.Columns.ENVIRONMENT])
        assertEquals("mdm-007", row[DeviceStatusSupabaseSchema.Columns.MDM_DEVICE_ID])
        assertEquals("ABC123", row[DeviceStatusSupabaseSchema.Columns.SERIAL_NUMBER])
        assertEquals("Pixel", row[DeviceStatusSupabaseSchema.Columns.HARDWARE_MODEL])
        assertEquals("Google", row[DeviceStatusSupabaseSchema.Columns.HARDWARE_MANUFACTURER])
        assertEquals("14", row[DeviceStatusSupabaseSchema.Columns.OS_VERSION])
        assertEquals(34, row[DeviceStatusSupabaseSchema.Columns.OS_API_LEVEL])
        assertEquals("1.2.3", row[DeviceStatusSupabaseSchema.Columns.APP_VERSION_NAME])
        assertEquals(45, row[DeviceStatusSupabaseSchema.Columns.APP_VERSION_CODE])
        assertEquals(87, row[DeviceStatusSupabaseSchema.Columns.BATTERY_PERCENT])
        assertEquals(true, row[DeviceStatusSupabaseSchema.Columns.IS_CHARGING])
        assertEquals("wifi", row[DeviceStatusSupabaseSchema.Columns.NETWORK_TYPE])
        assertEquals(false, row[DeviceStatusSupabaseSchema.Columns.VPN_ACTIVE])
        assertEquals("compliant", row[DeviceStatusSupabaseSchema.Columns.COMPLIANCE_STATE])
        assertEquals("2025.11", row[DeviceStatusSupabaseSchema.Columns.POLICY_VERSION])
        assertEquals(1234L, row[DeviceStatusSupabaseSchema.Columns.UPTIME_SECONDS])
        assertEquals("maintenance", row[DeviceStatusSupabaseSchema.Columns.STATUS])
        assertEquals("cmd-1", row[DeviceStatusSupabaseSchema.Columns.LAST_COMMAND_ID])
        assertEquals("success", row[DeviceStatusSupabaseSchema.Columns.LAST_COMMAND_STATUS])
        assertEquals(mapOf("foo" to "bar"), row[DeviceStatusSupabaseSchema.Columns.ANNOTATIONS])
        assertTrue(row.containsKey("extra"))
        assertEquals("value", row["extra"])
    }
}
