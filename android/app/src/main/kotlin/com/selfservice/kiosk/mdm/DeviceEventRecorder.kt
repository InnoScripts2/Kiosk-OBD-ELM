package com.selfservice.kiosk.mdm

import com.selfservice.kiosk.supabase.SupabaseOutboxWriter
import java.util.UUID

/** Records high-level MDM events (command lifecycle, compliance updates, alerts) to Supabase. */
class DeviceEventRecorder(
    private val writer: SupabaseOutboxWriter,
    private val identityProvider: () -> DeviceIdentity
) {

    fun record(row: DeviceEventSupabaseSchema.Row) {
        val payload = DeviceEventSupabaseSchema.toRow(identityProvider(), row)
        writer.enqueue(DeviceEventSupabaseSchema.TABLE_NAME, payload)
    }

    fun record(
        eventType: String,
        recordedAtMillis: Long,
        severity: DeviceEventSupabaseSchema.Severity = DeviceEventSupabaseSchema.Severity.INFO,
        message: String? = null,
        payload: Map<String, Any?> = emptyMap(),
        commandId: String? = null,
        commandStatus: String? = null,
        commandType: String? = null,
        source: String? = null,
        metadata: Map<String, Any?> = emptyMap()
    ) {
        val row = DeviceEventSupabaseSchema.Row(
            eventId = UUID.randomUUID().toString(),
            eventType = eventType,
            recordedAtMillis = recordedAtMillis,
            severity = severity,
            message = message,
            payload = payload,
            commandId = commandId,
            commandStatus = commandStatus,
            commandType = commandType,
            source = source,
            metadata = metadata
        )
        record(row)
    }
}
