package com.selfservice.kiosk.mdm

import java.util.Locale

/**
 * Supabase schema helper для исторического журнала MDM событий.
 */
object DeviceEventSupabaseSchema {

    const val TABLE_NAME: String = "device_events"

    object Columns {
        const val EVENT_ID = "event_id"
        const val EVENT_TYPE = "event_type"
        const val SEVERITY = "severity"
        const val MESSAGE = "message"
        const val RECORDED_AT = "recorded_at"
        const val KIOSK_ID = "kiosk_id"
        const val ENVIRONMENT = "environment"
        const val MDM_DEVICE_ID = "mdm_device_id"
        const val COMMAND_ID = "command_id"
        const val COMMAND_STATUS = "command_status"
        const val COMMAND_TYPE = "command_type"
        const val SOURCE = "source"
        const val PAYLOAD = "payload"
        const val METADATA = "metadata"
    }

    enum class Severity(val wireValue: String) {
        INFO("info"),
        WARNING("warning"),
        ERROR("error");

        companion object {
            fun fromWireValue(value: String?): Severity? {
                if (value.isNullOrBlank()) return null
                val normalized = value.lowercase(Locale.US)
                return values().firstOrNull { it.wireValue == normalized }
            }
        }
    }

    data class Row(
        val eventId: String,
        val eventType: String,
        val recordedAtMillis: Long,
        val severity: Severity = Severity.INFO,
        val message: String? = null,
        val payload: Map<String, Any?> = emptyMap(),
        val commandId: String? = null,
        val commandStatus: String? = null,
        val commandType: String? = null,
        val source: String? = null,
        val metadata: Map<String, Any?> = emptyMap()
    )

    fun toRow(identity: DeviceIdentity, row: Row): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>(
            Columns.EVENT_ID to row.eventId,
            Columns.EVENT_TYPE to row.eventType,
            Columns.RECORDED_AT to iso(row.recordedAtMillis),
            Columns.SEVERITY to row.severity.wireValue
        )
        identity.kioskId?.let { result[Columns.KIOSK_ID] = it }
        result[Columns.ENVIRONMENT] = identity.environment
        identity.mdmDeviceId?.let { result[Columns.MDM_DEVICE_ID] = it }
        row.message?.takeIf { it.isNotBlank() }?.let { result[Columns.MESSAGE] = it }
        if (row.payload.isNotEmpty()) {
            result[Columns.PAYLOAD] = row.payload
        }
        row.commandId?.takeIf { it.isNotBlank() }?.let { result[Columns.COMMAND_ID] = it }
        row.commandStatus?.takeIf { it.isNotBlank() }?.let { result[Columns.COMMAND_STATUS] = it }
        row.commandType?.takeIf { it.isNotBlank() }?.let { result[Columns.COMMAND_TYPE] = it }
        row.source?.takeIf { it.isNotBlank() }?.let { result[Columns.SOURCE] = it }
        if (row.metadata.isNotEmpty()) {
            result[Columns.METADATA] = row.metadata
        }
        return result
    }

    private fun iso(value: Long): String = DeviceStatusReporter.defaultIsoTimestamp(value)
}