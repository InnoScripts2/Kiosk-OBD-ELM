package com.selfservice.kiosk.telemetry

import com.selfservice.core.DefaultDispatchersProvider
import com.selfservice.core.DispatchersProvider
import com.selfservice.core.permissions.BluetoothEnvironmentStatus
import com.selfservice.core.permissions.BluetoothPermissionHelper
import com.selfservice.core.permissions.BluetoothPrerequisiteAction
import com.selfservice.obd.core.connection.BleScannerConfig
import com.selfservice.obd.core.platform.BluetoothPrerequisiteResult
import com.selfservice.obd.core.recovery.BleReconnectCoordinator
import com.selfservice.obd.core.session.ConnectedAdapter
import com.selfservice.obd.core.session.telemetry.ObdSessionTelemetry
import com.selfservice.obd.core.session.telemetry.ObdSessionTelemetryEvent
import com.selfservice.platform.data.diagnostics.DiagnosticsTelemetryRecord
import com.selfservice.platform.data.diagnostics.DiagnosticsTelemetryStore
import java.util.UUID
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persists [ObdSessionTelemetryEvent] instances into the local diagnostics telemetry store using
 * a Room-backed implementation. A session identifier is generated for the first attempt and reused
 * for subsequent retries until the session completes or is cancelled.
 */
class RoomObdSessionTelemetry(
    private val store: DiagnosticsTelemetryStore,
    private val dispatchers: DispatchersProvider = DefaultDispatchersProvider(),
    private val sessionIdGenerator: () -> String = { UUID.randomUUID().toString() },
    private val onRecord: (() -> Unit)? = null
) : ObdSessionTelemetry {

    private val sessionMutex = Mutex()
    private var activeSessionId: String? = null

    override suspend fun record(event: ObdSessionTelemetryEvent) {
        val sessionId = sessionMutex.withLock { updateSessionId(event) }
        val metadata = buildMetadata(event)
        val record = DiagnosticsTelemetryRecord(
            timestampMillis = event.timestampMillis,
            eventType = event::class.simpleName ?: event::class.qualifiedName ?: "unknown_event",
            sessionId = sessionId,
            metadata = metadata
        )
        withContext(dispatchers.io) {
            store.record(record)
            onRecord?.invoke()
        }
    }

    private fun updateSessionId(event: ObdSessionTelemetryEvent): String? = when (event) {
        is ObdSessionTelemetryEvent.SessionStarted -> {
            val current = activeSessionId
            val nextId = if (event.attempt <= 1 || current == null) {
                sessionIdGenerator()
            } else {
                current
            }
            activeSessionId = nextId
            nextId
        }
        is ObdSessionTelemetryEvent.SessionCompleted,
        is ObdSessionTelemetryEvent.SessionCancelled -> {
            val current = activeSessionId
            activeSessionId = null
            current
        }
        else -> activeSessionId
    }

    private fun buildMetadata(event: ObdSessionTelemetryEvent): Map<String, Any?> =
        when (event) {
            is ObdSessionTelemetryEvent.SessionStarted -> sessionStartedMetadata(event)
            is ObdSessionTelemetryEvent.ScannerStarted -> scannerMetadata(event.config)
            is ObdSessionTelemetryEvent.ScannerStopped -> emptyMap()
            is ObdSessionTelemetryEvent.AdapterSelected -> adapterEventMetadata(event.adapter, event.attempt)
            is ObdSessionTelemetryEvent.AdapterReady -> adapterEventMetadata(event.adapter, event.attempt)
            is ObdSessionTelemetryEvent.HandshakeCompleted -> adapterEventMetadata(event.adapter, event.attempt)
            is ObdSessionTelemetryEvent.DiagnosticsStarted -> adapterEventMetadata(event.adapter, event.attempt)
            is ObdSessionTelemetryEvent.DiagnosticsHeartbeat -> adapterMetadata(event.adapter)?.let { mapOf("adapter" to it) } ?: emptyMap()
            is ObdSessionTelemetryEvent.RetryScheduled -> retryMetadata(event)
            is ObdSessionTelemetryEvent.ConnectionIssue -> connectionIssueMetadata(event)
            is ObdSessionTelemetryEvent.SessionCompleted -> sessionCompletionMetadata(event.adapter, event.durationMillis)
            is ObdSessionTelemetryEvent.SessionFailed -> sessionFailureMetadata(event.adapter, event.cause, event.durationMillis)
            is ObdSessionTelemetryEvent.SessionCancelled -> sessionCompletionMetadata(event.adapter, event.durationMillis)
            is ObdSessionTelemetryEvent.PrerequisitesNotMet -> prerequisitesMetadata(event.result)
        }

    private fun sessionStartedMetadata(event: ObdSessionTelemetryEvent.SessionStarted): Map<String, Any?> {
        val data = mutableMapOf<String, Any?>(
            "attempt" to event.attempt,
            "retryCount" to event.config.retryCount,
            "reconnectDelayMs" to event.config.reconnectDelayMs,
            "hasTransportFactory" to (event.config.transportFactory != null)
        )
        val scanner = scannerMetadata(event.config.scanner)
        if (scanner.isNotEmpty()) {
            data["scanner"] = scanner
        }
        adapterMetadata(event.config.initialAdapter)?.let { data["initialAdapter"] = it }
        return data
    }

    private fun scannerMetadata(config: BleScannerConfig): Map<String, Any?> {
        val data = mutableMapOf<String, Any?>(
            "timeoutMs" to config.timeoutMs
        )
        if (config.serviceUuids.isNotEmpty()) {
            data["serviceUuids"] = config.serviceUuids
        }
        config.targetSerialPattern?.pattern?.let { pattern ->
            data["targetSerialPattern"] = pattern
        }
        return data
    }

    private fun adapterEventMetadata(adapter: ConnectedAdapter, attempt: Int): Map<String, Any?> {
        val data = mutableMapOf<String, Any?>(
            "attempt" to attempt
        )
        adapterMetadata(adapter)?.let { data["adapter"] = it }
        return data
    }

    private fun retryMetadata(event: ObdSessionTelemetryEvent.RetryScheduled): Map<String, Any?> {
        val decision = event.decision
        val data = mutableMapOf<String, Any?>(
            "attempt" to decision.attempt,
            "maxAttempts" to decision.maxAttempts,
            "delayMillis" to decision.delayMillis,
            "jitterMillis" to decision.jitterMillis
        )
        adapterMetadata(event.adapter)?.let { data["adapter"] = it }
        return data
    }

    private fun connectionIssueMetadata(event: ObdSessionTelemetryEvent.ConnectionIssue): Map<String, Any?> {
        val data = mutableMapOf<String, Any?>(
            "issueType" to event.issue.type.name
        )
        when (val issue = event.issue) {
            is BleReconnectCoordinator.ConnectionIssue.GattDisconnected -> data["status"] = issue.status
            is BleReconnectCoordinator.ConnectionIssue.CharacteristicWriteFailure -> data["status"] = issue.status
            is BleReconnectCoordinator.ConnectionIssue.Unknown -> issue.detail?.let { data["detail"] = it }
            else -> Unit
        }
        event.cause?.let { cause ->
            val causePayload = mutableMapOf<String, Any?>(
                "type" to (cause::class.simpleName ?: cause::class.qualifiedName ?: "Unknown"),
                "message" to cause.message
            ).filterValues { it != null }
            if (causePayload.isNotEmpty()) {
                data["cause"] = causePayload
            }
        }
        adapterMetadata(event.adapter)?.let { data["adapter"] = it }
        return data
    }

    private fun sessionCompletionMetadata(adapter: ConnectedAdapter?, durationMillis: Long): Map<String, Any?> {
        val data = mutableMapOf<String, Any?>(
            "durationMillis" to durationMillis.coerceAtLeast(0L)
        )
        adapterMetadata(adapter)?.let { data["adapter"] = it }
        return data
    }

    private fun sessionFailureMetadata(
        adapter: ConnectedAdapter?,
        cause: Throwable,
        durationMillis: Long
    ): Map<String, Any?> {
        val data = mutableMapOf<String, Any?>(
            "durationMillis" to durationMillis.coerceAtLeast(0L)
        )
        adapterMetadata(adapter)?.let { data["adapter"] = it }
        val causePayload = mutableMapOf<String, Any?>(
            "type" to (cause::class.simpleName ?: cause::class.qualifiedName ?: "Unknown"),
            "message" to cause.message
        ).filterValues { it != null }
        if (causePayload.isNotEmpty()) {
            data["cause"] = causePayload
        }
        return data
    }

    private fun prerequisitesMetadata(result: BluetoothPrerequisiteResult): Map<String, Any?> {
        val data = mutableMapOf<String, Any?>(
            "action" to when (val action = result.action) {
                BluetoothPrerequisiteAction.Ready -> "Ready"
                BluetoothPrerequisiteAction.EnableBluetooth -> "EnableBluetooth"
                BluetoothPrerequisiteAction.EnableLocation -> "EnableLocation"
                is BluetoothPrerequisiteAction.RequestPermissions -> "RequestPermissions"
            },
            "status" to (result.status::class.simpleName ?: result.status::class.qualifiedName),
            "bluetoothEnabled" to result.state.bluetoothEnabled,
            "locationEnabled" to result.state.locationEnabled,
            "evaluatedAtMillis" to result.timestampMillis
        )
        val action = result.action
        if (action is BluetoothPrerequisiteAction.RequestPermissions) {
            data["requestedPermissions"] = action.permissions
        }
        val status = result.status
        if (status is BluetoothEnvironmentStatus.MissingPermissions) {
            data["missingPermissions"] = status.permissions
        }
        when (val permissionStatus = result.state.permissionStatus) {
            is BluetoothPermissionHelper.PermissionStatus.Granted -> data["permissionsGranted"] = true
            is BluetoothPermissionHelper.PermissionStatus.Missing -> {
                data["permissionsGranted"] = false
                data["missingRuntimePermissions"] = permissionStatus.permissions
            }
        }
        return data
    }

    private fun adapterMetadata(adapter: ConnectedAdapter?): Map<String, Any?>? {
        val value = adapter ?: return null
        val data = mutableMapOf<String, Any?>(
            "address" to value.device.address,
            "protocol" to value.protocol
        )
        value.device.name?.let { data["name"] = it }
        value.device.rssi?.let { data["rssi"] = it }
        return data
    }
}
