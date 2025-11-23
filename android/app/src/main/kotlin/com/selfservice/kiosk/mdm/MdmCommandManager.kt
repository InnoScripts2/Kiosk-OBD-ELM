package com.selfservice.kiosk.mdm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.selfservice.kiosk.mdm.DeviceCommandSupabaseSchema.Status
import com.selfservice.kiosk.mdm.DeviceEventSupabaseSchema.Severity
import com.selfservice.kiosk.supabase.SupabaseOutboxWriter
import java.util.LinkedHashMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.set
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


class MdmCommandManager(
    private val context: Context,
    private val scope: CoroutineScope,
    writer: SupabaseOutboxWriter,
    private val identityProvider: () -> DeviceIdentity,
    private val handler: MdmCommandHandler,
    private val clock: () -> Long = { System.currentTimeMillis() },
    enableDebugReceiver: Boolean = false
) {

    data class CommandState(
        val id: String,
        val status: Status,
        val updatedAtMillis: Long
    )

    private data class ActiveCommand(
        val command: MdmCommand,
        val requestedAtMillis: Long,
        val attempt: Int
    )

    private val deviceCommandRecorder = DeviceCommandRecorder(writer, identityProvider)
    private val deviceEventRecorder = DeviceEventRecorder(writer, identityProvider)
    private val activeCommands = ConcurrentHashMap<String, ActiveCommand>()
    private val isShutdown = AtomicBoolean(false)
    private val _lastCommandState = MutableStateFlow<CommandState?>(null)
    val lastCommandState: StateFlow<CommandState?> = _lastCommandState

    private val debugReceiver: BroadcastReceiver? = if (enableDebugReceiver) {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent ?: return
                val command = MdmCommandIntentParser.fromIntent(
                    intent,
                    fallbackSource = intent.getStringExtra(MdmCommandIntents.EXTRA_COMMAND_SOURCE)
                        ?: "debug-broadcast"
                )
                if (command == null) {
                    Log.w(TAG, "Ignoring debug MDM command without type")
                    return
                }
                submit(command)
            }
        }.also { receiver ->
            context.registerReceiver(receiver, IntentFilter(MdmCommandIntents.ACTION_DEBUG_COMMAND))
        }
    } else {
        null
    }

    fun submit(command: MdmCommand) {
        if (isShutdown.get()) {
            Log.w(TAG, "Ignoring MDM command after shutdown: ${command.id}")
            return
        }
        val normalizedId = command.id.ifBlank { UUID.randomUUID().toString() }
        val attempt = activeCommands[normalizedId]?.attempt?.plus(1) ?: 1
        val active = ActiveCommand(command.copy(id = normalizedId), command.issuedAtMillis, attempt)
        activeCommands[normalizedId] = active
        recordStatus(active, Status.RECEIVED, payload = command.payload, metadata = command.metadata)
        scope.launch {
            processCommand(active)
        }
    }

    fun shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            debugReceiver?.let { receiver ->
                runCatching { context.unregisterReceiver(receiver) }
                    .onFailure { Log.w(TAG, "Failed to unregister debug receiver", it) }
            }
            activeCommands.clear()
        }
    }

    private suspend fun processCommand(active: ActiveCommand) {
        if (isShutdown.get()) return
        performStage(active, Status.ACKNOWLEDGED) { acknowledgedAt ->
            deviceEventRecorder.record(
                eventType = EVENT_COMMAND_ACK,
                recordedAtMillis = acknowledgedAt,
                severity = Severity.INFO,
                message = "Command ${active.command.type} acknowledged",
                payload = active.command.payload,
                commandId = active.command.id,
                commandStatus = Status.ACKNOWLEDGED.wireValue,
                commandType = active.command.type,
                source = active.command.source
            )
        }
        performStage(active, Status.IN_PROGRESS) { startedAt ->
            deviceEventRecorder.record(
                eventType = EVENT_COMMAND_STARTED,
                recordedAtMillis = startedAt,
                severity = Severity.INFO,
                message = "Command ${active.command.type} started",
                commandId = active.command.id,
                commandStatus = Status.IN_PROGRESS.wireValue,
                commandType = active.command.type,
                source = active.command.source
            )
        }
        val result = runCatching { handler.handle(active.command) }
        val completedAt = clock()
        val finalResult = result.getOrElse { error ->
            Log.w(TAG, "MDM command execution failed", error)
            MdmCommandResult.failure(error.message ?: error.javaClass.simpleName)
        }
        recordStatus(
            active = active,
            status = finalResult.status,
            completedAtMillis = completedAt,
            resultPayload = finalResult.resultPayload,
            errorMessage = finalResult.errorMessage,
            metadata = mergeMetadata(active.command.metadata, finalResult.metadata)
        )
        deviceEventRecorder.record(
            eventType = when (finalResult.status) {
                Status.SUCCEEDED -> EVENT_COMMAND_SUCCEEDED
                Status.CANCELLED -> EVENT_COMMAND_CANCELLED
                else -> EVENT_COMMAND_FAILED
            },
            recordedAtMillis = completedAt,
            severity = when (finalResult.status) {
                Status.FAILED -> Severity.ERROR
                Status.CANCELLED -> Severity.WARNING
                else -> Severity.INFO
            },
            message = finalResult.message,
            payload = finalResult.resultPayload ?: emptyMap(),
            commandId = active.command.id,
            commandStatus = finalResult.status.wireValue,
            commandType = active.command.type,
            source = active.command.source,
            metadata = finalResult.metadata
        )
        activeCommands.remove(active.command.id)
        _lastCommandState.value = CommandState(active.command.id, finalResult.status, completedAt)
    }

    private suspend fun performStage(active: ActiveCommand, status: Status, callback: (Long) -> Unit) {
        val timestamp = clock()
        when (status) {
            Status.ACKNOWLEDGED -> recordStatus(active, status, acknowledgedAtMillis = timestamp)
            Status.IN_PROGRESS -> recordStatus(active, status, startedAtMillis = timestamp)
            else -> recordStatus(active, status)
        }
        callback(timestamp)
    }

    private fun recordStatus(
        active: ActiveCommand,
        status: Status,
        acknowledgedAtMillis: Long? = null,
        startedAtMillis: Long? = null,
        completedAtMillis: Long? = null,
        resultPayload: Map<String, Any?>? = null,
        errorMessage: String? = null,
        payload: Map<String, Any?>? = null,
        metadata: Map<String, Any?> = emptyMap()
    ) {
        val latencyMillis = when {
            completedAtMillis != null -> completedAtMillis - active.requestedAtMillis
            else -> null
        }
        val row = DeviceCommandSupabaseSchema.Row(
            commandId = active.command.id,
            commandType = active.command.type,
            status = status,
            requestedAtMillis = active.requestedAtMillis,
            acknowledgedAtMillis = acknowledgedAtMillis,
            startedAtMillis = startedAtMillis,
            completedAtMillis = completedAtMillis,
            latencyMillis = latencyMillis,
            attempt = active.attempt,
            payload = payload,
            resultPayload = resultPayload,
            errorMessage = errorMessage,
            source = active.command.source,
            metadata = metadata.ifEmpty { active.command.metadata }
        )
        deviceCommandRecorder.record(row)
    }

    private fun mergeMetadata(base: Map<String, Any?>, extra: Map<String, Any?>): Map<String, Any?> {
        if (base.isEmpty() && extra.isEmpty()) return emptyMap()
        if (extra.isEmpty()) return base
        if (base.isEmpty()) return extra
        return LinkedHashMap<String, Any?>(base).apply { putAll(extra) }
    }

    companion object {
        private const val TAG = "MdmCommandManager"
        private const val EVENT_COMMAND_ACK = "command_ack"
        private const val EVENT_COMMAND_STARTED = "command_started"
        private const val EVENT_COMMAND_SUCCEEDED = "command_succeeded"
        private const val EVENT_COMMAND_FAILED = "command_failed"
        private const val EVENT_COMMAND_CANCELLED = "command_cancelled"
    }
}
