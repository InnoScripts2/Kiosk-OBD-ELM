package com.selfservice.kiosk.mdm

import com.selfservice.kiosk.supabase.SupabaseOutboxWriter
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.json.JSONObject

class DeviceStatusReporterTest {

    private val tempDir: File = Files.createTempDirectory("device-status-reporter-test").toFile()

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun recordWritesSupabasePayload() {
        val writer = SupabaseOutboxWriter(directory = tempDir)
        val reporter = DeviceStatusReporter(
            writer = writer,
            clock = { 1700000000000L },
            timestampFormatter = { "2025-11-23T12:00:00Z" }
        )
        val snapshot = DeviceStatusSnapshot(
            kioskId = "kiosk-99",
            environment = "prod",
            mdmDeviceId = "mdm-99",
            serialNumber = "SERIAL",
            appVersionName = "1.0.0",
            appVersionCode = 100,
            status = DeviceStatusSnapshot.DeviceState.ACTIVE
        )

        reporter.record(snapshot)

        val outboxFile = File(tempDir, SupabaseOutboxWriter.DEFAULT_FILE_NAME)
        assertTrue(outboxFile.exists())
        val payloadLine = outboxFile.readLines().first { it.isNotBlank() }
        val envelope = JSONObject(payloadLine)
        assertEquals(DeviceStatusSupabaseSchema.TABLE_NAME, envelope.getString("table"))
        val payload = envelope.getJSONObject("payload")
        assertEquals("2025-11-23T12:00:00Z", payload.getString(DeviceStatusSupabaseSchema.Columns.RECORDED_AT))
        assertEquals("prod", payload.getString(DeviceStatusSupabaseSchema.Columns.ENVIRONMENT))
        assertEquals("kiosk-99", payload.getString(DeviceStatusSupabaseSchema.Columns.KIOSK_ID))
        assertEquals("mdm-99", payload.getString(DeviceStatusSupabaseSchema.Columns.MDM_DEVICE_ID))
        assertEquals("1.0.0", payload.getString(DeviceStatusSupabaseSchema.Columns.APP_VERSION_NAME))
        assertEquals(100, payload.getInt(DeviceStatusSupabaseSchema.Columns.APP_VERSION_CODE))
        assertEquals("active", payload.getString(DeviceStatusSupabaseSchema.Columns.STATUS))
    }
}
