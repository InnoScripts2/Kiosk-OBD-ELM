package com.selfservice.kiosk.telemetry

import com.selfservice.core.DispatchersProvider
import com.selfservice.obd.core.connection.BleDevice
import com.selfservice.obd.core.connection.BleScannerConfig
import com.selfservice.obd.core.recovery.BleReconnectCoordinator
import com.selfservice.obd.core.session.ConnectedAdapter
import com.selfservice.obd.core.session.ObdSessionConfig
import com.selfservice.obd.core.session.telemetry.ObdSessionTelemetryEvent
import com.selfservice.platform.data.diagnostics.DiagnosticsTelemetryRecord
import com.selfservice.platform.data.diagnostics.DiagnosticsTelemetryStore
import com.selfservice.platform.data.diagnostics.DiagnosticsTelemetrySummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class RoomObdSessionTelemetryTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatchersProvider {
        override val io = dispatcher
        override val computation = dispatcher
        override val main = dispatcher
    }

    @Test
    fun sessionStartGeneratesSessionIdAndStoresMetadata() = runTest(dispatcher) {
        val store = RecordingTelemetryStore()
        val telemetry = RoomObdSessionTelemetry(
            store = store,
            dispatchers = dispatchers,
            sessionIdGenerator = { "session-001" }
        )
        val config = ObdSessionConfig(
            scanner = BleScannerConfig(targetSerialPattern = null, serviceUuids = listOf("1234"), timeoutMs = 5_000L),
            retryCount = 4,
            reconnectDelayMs = 1_500L
        )
        val event = ObdSessionTelemetryEvent.SessionStarted(
            attempt = 1,
            config = config,
            timestampMillis = 100L
        )

        telemetry.record(event)

        val recorded = store.records.single()
        assertEquals("SessionStarted", recorded.eventType)
        assertEquals("session-001", recorded.sessionId)
        val metadata = recorded.metadata
        assertEquals(4, metadata["retryCount"])
        @Suppress("UNCHECKED_CAST")
        val scanner = metadata["scanner"] as Map<String, Any?>
        assertEquals(listOf("1234"), scanner["serviceUuids"])
    }

    @Test
    fun adapterEventsReuseSessionId() = runTest(dispatcher) {
        val store = RecordingTelemetryStore()
        val telemetry = RoomObdSessionTelemetry(
            store = store,
            dispatchers = dispatchers,
            sessionIdGenerator = { "session-xyz" }
        )
        val adapter = ConnectedAdapter(
            device = BleDevice(address = "AA:BB:CC:DD:EE:FF", name = "DiagAdapter", rssi = -45),
            protocol = "BLE"
        )

        telemetry.record(
            ObdSessionTelemetryEvent.SessionStarted(
                attempt = 1,
                config = ObdSessionConfig(initialAdapter = adapter),
                timestampMillis = 10L
            )
        )
        telemetry.record(
            ObdSessionTelemetryEvent.AdapterReady(
                adapter = adapter,
                attempt = 1,
                timestampMillis = 20L
            )
        )

        val sessionId = store.records.first().sessionId
        assertNotNull(sessionId)
        assertEquals(sessionId, store.records[1].sessionId)
        @Suppress("UNCHECKED_CAST")
        val metadata = store.records[1].metadata["adapter"] as Map<String, Any?>
        assertEquals("DiagAdapter", metadata["name"])
        assertEquals("BLE", metadata["protocol"])
    }

    @Test
    fun sessionRetryKeepsSameSessionId() = runTest(dispatcher) {
        val store = RecordingTelemetryStore()
        var nextId = 0
        val telemetry = RoomObdSessionTelemetry(
            store = store,
            dispatchers = dispatchers,
            sessionIdGenerator = { "session-${'$'}{nextId++}" }
        )
        val adapter = ConnectedAdapter(
            device = BleDevice(address = "11:22:33:44:55:66", name = "RetryAdapter", rssi = -38),
            protocol = "BLE"
        )

        telemetry.record(
            ObdSessionTelemetryEvent.SessionStarted(
                attempt = 1,
                config = ObdSessionConfig(initialAdapter = adapter),
                timestampMillis = 5L
            )
        )
        telemetry.record(
            ObdSessionTelemetryEvent.SessionFailed(
                adapter = adapter,
                cause = IllegalStateException("disconnect"),
                durationMillis = 1_000L,
                timestampMillis = 15L
            )
        )
        telemetry.record(
            ObdSessionTelemetryEvent.SessionStarted(
                attempt = 2,
                config = ObdSessionConfig(initialAdapter = adapter),
                timestampMillis = 25L
            )
        )

        val firstSessionId = store.records.first().sessionId
        assertNotNull(firstSessionId)
        val secondStart = store.records.last()
        assertEquals(firstSessionId, secondStart.sessionId)
    }

    @Test
    fun sessionCompletionClearsSessionIdForNextStart() = runTest(dispatcher) {
        val store = RecordingTelemetryStore()
        var ids = listOf("session-a", "session-b").iterator()
        val telemetry = RoomObdSessionTelemetry(
            store = store,
            dispatchers = dispatchers,
            sessionIdGenerator = { ids.next() }
        )

        telemetry.record(
            ObdSessionTelemetryEvent.SessionStarted(
                attempt = 1,
                config = ObdSessionConfig(),
                timestampMillis = 50L
            )
        )
        telemetry.record(
            ObdSessionTelemetryEvent.SessionCompleted(
                adapter = null,
                durationMillis = 2_000L,
                timestampMillis = 60L
            )
        )
        telemetry.record(
            ObdSessionTelemetryEvent.SessionStarted(
                attempt = 1,
                config = ObdSessionConfig(),
                timestampMillis = 70L
            )
        )

        val firstSessionId = store.records[0].sessionId
        val secondSessionId = store.records[2].sessionId
        assertNotNull(firstSessionId)
        assertNotNull(secondSessionId)
        assertNotSame(firstSessionId, secondSessionId)
        assertEquals(firstSessionId, store.records[1].sessionId)
    }

    @Test
    fun connectionIssueMetadataIncludesCauseAndAdapter() = runTest(dispatcher) {
        val store = RecordingTelemetryStore()
        val telemetry = RoomObdSessionTelemetry(store = store, dispatchers = dispatchers)
        val adapter = ConnectedAdapter(
            device = BleDevice(address = "AA:BB", name = null, rssi = null),
            protocol = "BLE"
        )

        telemetry.record(
            ObdSessionTelemetryEvent.ConnectionIssue(
                issue = BleReconnectCoordinator.ConnectionIssue.GattDisconnected(status = 133),
                cause = IllegalArgumentException("gatt"),
                adapter = adapter,
                timestampMillis = 90L
            )
        )

        val record = store.records.single()
    val metadata = record.metadata
    assertEquals("GATT_DISCONNECTED", metadata["issueType"])
        @Suppress("UNCHECKED_CAST")
        val cause = metadata["cause"] as Map<String, Any?>
        assertEquals("IllegalArgumentException", cause["type"])
    }

    private class RecordingTelemetryStore : DiagnosticsTelemetryStore {
        val records = mutableListOf<DiagnosticsTelemetryRecord>()
        val markedThresholds = mutableListOf<Long>()
        val deletedThresholds = mutableListOf<Long>()
        var pendingRecords: List<DiagnosticsTelemetryRecord> = emptyList()

        override suspend fun record(record: DiagnosticsTelemetryRecord) {
            records += record
        }

        override suspend fun pending(): List<DiagnosticsTelemetryRecord> = pendingRecords

        override suspend fun markExportedUpTo(timestampMillis: Long) {
            markedThresholds += timestampMillis
        }

        override suspend fun deleteOlderThan(thresholdMillis: Long) {
            deletedThresholds += thresholdMillis
        }

        override suspend fun summary(): DiagnosticsTelemetrySummary = DiagnosticsTelemetrySummary.empty()
    }
}
