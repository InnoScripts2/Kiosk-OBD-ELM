package com.selfservice.kiosk.mdm

import java.util.Locale

object DeviceStatusSupabaseSchema {
    const val TABLE_NAME = "device_status"

    object Columns {
        const val RECORDED_AT = "recorded_at"
        const val KIOSK_ID = "kiosk_id"
        const val ENVIRONMENT = "environment"
        const val MDM_DEVICE_ID = "mdm_device_id"
        const val SERIAL_NUMBER = "serial_number"
        const val HARDWARE_MODEL = "hardware_model"
        const val HARDWARE_MANUFACTURER = "hardware_manufacturer"
        const val OS_VERSION = "os_version"
        const val OS_API_LEVEL = "os_api_level"
        const val APP_VERSION_NAME = "app_version"
        const val APP_VERSION_CODE = "app_build"
        const val BATTERY_PERCENT = "battery_percent"
        const val IS_CHARGING = "battery_is_charging"
        const val NETWORK_TYPE = "network_type"
        const val VPN_ACTIVE = "vpn_active"
        const val COMPLIANCE_STATE = "compliance_state"
        const val POLICY_VERSION = "policy_version"
        const val UPTIME_SECONDS = "uptime_seconds"
        const val STATUS = "status"
        const val LAST_COMMAND_ID = "last_command_id"
        const val LAST_COMMAND_STATUS = "last_command_status"
        const val ANNOTATIONS = "annotations"
    }

    fun toRow(
        snapshot: DeviceStatusSnapshot,
        recordedAtIso: String,
        additionalFields: Map<String, Any?> = emptyMap()
    ): Map<String, Any?> {
        val row = mutableMapOf<String, Any?>(
            Columns.RECORDED_AT to recordedAtIso,
            Columns.ENVIRONMENT to snapshot.environment
        )
        snapshot.kioskId?.let { row[Columns.KIOSK_ID] = it }
        snapshot.mdmDeviceId?.let { row[Columns.MDM_DEVICE_ID] = it }
        snapshot.serialNumber?.let { row[Columns.SERIAL_NUMBER] = it }
        snapshot.hardwareModel?.let { row[Columns.HARDWARE_MODEL] = it }
        snapshot.hardwareManufacturer?.let { row[Columns.HARDWARE_MANUFACTURER] = it }
        snapshot.osVersion?.let { row[Columns.OS_VERSION] = it }
        snapshot.osApiLevel?.let { row[Columns.OS_API_LEVEL] = it }
        snapshot.appVersionName?.let { row[Columns.APP_VERSION_NAME] = it }
        snapshot.appVersionCode?.let { row[Columns.APP_VERSION_CODE] = it }
        snapshot.batteryPercent?.let { row[Columns.BATTERY_PERCENT] = it }
        snapshot.isCharging?.let { row[Columns.IS_CHARGING] = it }
        snapshot.networkType?.let { row[Columns.NETWORK_TYPE] = it }
        snapshot.vpnActive?.let { row[Columns.VPN_ACTIVE] = it }
        snapshot.complianceState?.let { row[Columns.COMPLIANCE_STATE] = it }
        snapshot.policyVersion?.let { row[Columns.POLICY_VERSION] = it }
        snapshot.uptimeSeconds?.let { row[Columns.UPTIME_SECONDS] = it }
        snapshot.status?.let { state ->
            row[Columns.STATUS] = state.name.lowercase(Locale.US)
        }
        snapshot.lastCommandId?.let { row[Columns.LAST_COMMAND_ID] = it }
        snapshot.lastCommandStatus?.let { row[Columns.LAST_COMMAND_STATUS] = it }
        if (snapshot.annotations.isNotEmpty()) {
            row[Columns.ANNOTATIONS] = snapshot.annotations
        }
        if (additionalFields.isNotEmpty()) {
            row.putAll(additionalFields)
        }
        return row
    }
}
