package com.selfservice.kiosk.mdm

import com.selfservice.kiosk.supabase.SupabaseOutboxWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DeviceStatusReporter(
    private val writer: SupabaseOutboxWriter,
    private val tableName: String = DeviceStatusSupabaseSchema.TABLE_NAME,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val additionalFieldsProvider: () -> Map<String, Any?> = { emptyMap() },
    private val timestampFormatter: (Long) -> String = Companion::defaultIsoTimestamp
) {

    fun record(snapshot: DeviceStatusSnapshot) {
        val recordedAtIso = timestampFormatter(clock())
        val payload = DeviceStatusSupabaseSchema.toRow(
            snapshot = snapshot,
            recordedAtIso = recordedAtIso,
            additionalFields = additionalFieldsProvider()
        )
        writer.enqueue(table = tableName, payload = payload)
    }

    companion object {
        private val ISO_FORMAT = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }

        fun defaultIsoTimestamp(timestampMillis: Long): String {
            val formatter = ISO_FORMAT.get()
            return formatter.format(Date(timestampMillis))
        }
    }
}
