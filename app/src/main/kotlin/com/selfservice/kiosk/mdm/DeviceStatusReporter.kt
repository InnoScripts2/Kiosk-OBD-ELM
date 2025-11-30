package com.selfservice.kiosk.mdm

import com.selfservice.kiosk.supabase.SupabaseOutboxWriter
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder

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
        private val ISO_FORMATTER: DateTimeFormatter = DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
            .appendOffset("+HH:MM", "+00:00")
            .toFormatter()
            .withZone(ZoneOffset.UTC)

        fun defaultIsoTimestamp(timestampMillis: Long): String {
            return ISO_FORMATTER.format(Instant.ofEpochMilli(timestampMillis))
        }
    }
}
