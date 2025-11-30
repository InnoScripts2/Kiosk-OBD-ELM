package com.selfservice.kiosk.mdm

import java.util.Locale

/**
 * Supabase schema helper for зеркала очереди команд, поступающих из MDM.
 */
object DeviceCommandSupabaseSchema {

    const val TABLE_NAME: String = "device_commands"

    object Columns {
        const val COMMAND_ID = "command_id"
        const val COMMAND_TYPE = "command_type"
        const val STATUS = "status"
        const val REQUESTED_AT = "requested_at"
        const val ACKNOWLEDGED_AT = "acknowledged_at"
        const val STARTED_AT = "started_at"
        const val COMPLETED_AT = "completed_at"
        const val LATENCY_MS = "latency_ms"
        const val ATTEMPT = "attempt"
        const val PAYLOAD = "payload"
        const val RESULT_PAYLOAD = "result_payload"
        const val ERROR_MESSAGE = "error_message"
        const val SOURCE = "source"
        const val KIOSK_ID = "kiosk_id"
        const val ENVIRONMENT = "environment"
        const val MDM_DEVICE_ID = "mdm_device_id"
        const val METADATA = "metadata"
    }

    enum class Status(val wireValue: String) {
        RECEIVED("received"),
        ACKNOWLEDGED("acknowledged"),
        IN_PROGRESS("in_progress"),
        SUCCEEDED("succeeded"),
        FAILED("failed"),
        CANCELLED("cancelled");

        companion object {
            fun fromWireValue(value: String?): Status? {
                if (value.isNullOrBlank()) return null
                val normalized = value.lowercase(Locale.US)
                return values().firstOrNull { it.wireValue == normalized }
            }
        }
    }

    data class Row(
        val commandId: String,
        val commandType: String,
        val status: Status,
        val requestedAtMillis: Long,
        val acknowledgedAtMillis: Long? = null,
        val startedAtMillis: Long? = null,
        val completedAtMillis: Long? = null,
        val latencyMillis: Long? = null,
        val attempt: Int = 1,
        val payload: Map<String, Any?>? = null,
        val resultPayload: Map<String, Any?>? = null,
        val errorMessage: String? = null,
        val source: String? = null,
        val metadata: Map<String, Any?> = emptyMap()
    )

    fun toRow(identity: DeviceIdentity, row: Row): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>(
            Columns.COMMAND_ID to row.commandId,
            Columns.COMMAND_TYPE to row.commandType,
            Columns.STATUS to row.status.wireValue,
            Columns.REQUESTED_AT to iso(row.requestedAtMillis)
        )
        identity.kioskId?.let { result[Columns.KIOSK_ID] = it }
        result[Columns.ENVIRONMENT] = identity.environment
        identity.mdmDeviceId?.let { result[Columns.MDM_DEVICE_ID] = it }

        row.acknowledgedAtMillis?.let { result[Columns.ACKNOWLEDGED_AT] = iso(it) }
        row.startedAtMillis?.let { result[Columns.STARTED_AT] = iso(it) }
        row.completedAtMillis?.let { result[Columns.COMPLETED_AT] = iso(it) }
        row.latencyMillis?.let { result[Columns.LATENCY_MS] = it }
        if (row.attempt > 0) {
            result[Columns.ATTEMPT] = row.attempt
        }
        row.payload?.let { payload ->
            if (payload.isNotEmpty()) {
                result[Columns.PAYLOAD] = payload
            }
        }
        row.resultPayload?.let { payload ->
            if (payload.isNotEmpty()) {
                result[Columns.RESULT_PAYLOAD] = payload
            }
        }
        row.errorMessage?.takeIf { it.isNotBlank() }?.let { result[Columns.ERROR_MESSAGE] = it }
        row.source?.takeIf { it.isNotBlank() }?.let { result[Columns.SOURCE] = it }
        if (row.metadata.isNotEmpty()) {
            result[Columns.METADATA] = row.metadata
        }
        return result
    }

    private fun iso(value: Long): String = DeviceStatusReporter.defaultIsoTimestamp(value)
}