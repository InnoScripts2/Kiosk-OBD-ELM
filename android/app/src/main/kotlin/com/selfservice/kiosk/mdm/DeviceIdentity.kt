package com.selfservice.kiosk.mdm

/**
 * Immutable identity tuple that ties a kiosk heartbeat, command or event row to the
 * currently active environment/kiosk identifiers. The Supabase схемы используют эти значения
 * для партиционирования и построения дешбордов.
 */
data class DeviceIdentity(
    val kioskId: String?,
    val environment: String,
    val mdmDeviceId: String?
)