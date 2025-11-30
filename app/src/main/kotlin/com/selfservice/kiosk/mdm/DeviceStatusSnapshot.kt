package com.selfservice.kiosk.mdm

/**
 * Represents a single heartbeat/status payload for a kiosk device that should be mirrored to Supabase.
 */
data class DeviceStatusSnapshot(
    val kioskId: String?,
    val environment: String,
    val mdmDeviceId: String? = null,
    val serialNumber: String? = null,
    val hardwareModel: String? = null,
    val hardwareManufacturer: String? = null,
    val osVersion: String? = null,
    val osApiLevel: Int? = null,
    val appVersionName: String? = null,
    val appVersionCode: Int? = null,
    val batteryPercent: Int? = null,
    val isCharging: Boolean? = null,
    val networkType: String? = null,
    val vpnActive: Boolean? = null,
    val complianceState: String? = null,
    val policyVersion: String? = null,
    val uptimeSeconds: Long? = null,
    val status: DeviceState? = null,
    val lastCommandId: String? = null,
    val lastCommandStatus: String? = null,
    val annotations: Map<String, Any?> = emptyMap()
) {

    enum class DeviceState {
        ACTIVE,
        MAINTENANCE,
        DEGRADED,
        OFFLINE
    }
}
