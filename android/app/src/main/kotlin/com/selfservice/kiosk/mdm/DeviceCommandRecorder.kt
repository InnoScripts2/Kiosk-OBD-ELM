package com.selfservice.kiosk.mdm

import com.selfservice.kiosk.supabase.SupabaseOutboxWriter

/**
 * Thin helper that transforms [DeviceCommandSupabaseSchema.Row] structures into Supabase
 * outbox entries enriched with the current [DeviceIdentity].
 */
class DeviceCommandRecorder(
    private val writer: SupabaseOutboxWriter,
    private val identityProvider: () -> DeviceIdentity
) {

    fun record(row: DeviceCommandSupabaseSchema.Row) {
        val payload = DeviceCommandSupabaseSchema.toRow(identityProvider(), row)
        writer.enqueue(DeviceCommandSupabaseSchema.TABLE_NAME, payload)
    }
}
