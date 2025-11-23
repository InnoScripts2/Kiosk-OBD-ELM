package com.selfservice.kiosk.diagnostics

import android.util.Log
import com.selfservice.core.DispatchersProvider
import com.selfservice.obd.core.connection.BleScannerConfig
import com.selfservice.obd.core.connection.ObdConnectionManager
import com.selfservice.obd.core.connection.ObdConnectionSnapshot
import com.selfservice.obd.core.session.ConnectedAdapter
import com.selfservice.obd.core.session.ObdSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Управляет подключениями к OBD-адаптеру от имени UI, скрывая асинхронные детали.
 */
class ObdConnectionController(
    private val manager: ObdConnectionManager,
    private val scope: CoroutineScope,
    private val dispatchers: DispatchersProvider,
    private val logTag: String = "ObdConnectionController"
) {

    val snapshot: StateFlow<ObdConnectionSnapshot> = manager.snapshot
    val sessionState: StateFlow<ObdSessionState> = manager.sessionState

    fun connectAsync(
        force: Boolean = false,
        scannerConfig: BleScannerConfig? = null,
        adapter: ConnectedAdapter? = null,
        timeoutMs: Long? = null
    ): Job = scope.launch(dispatchers.io) {
        runCatching {
            connectInternal(force, scannerConfig, adapter, timeoutMs)
        }.onFailure { error ->
            Log.w(logTag, "Не удалось подключиться к OBD-адаптеру", error)
        }
    }

    suspend fun connect(
        force: Boolean = false,
        scannerConfig: BleScannerConfig? = null,
        adapter: ConnectedAdapter? = null,
        timeoutMs: Long? = null
    ): ObdConnectionSnapshot = withContext(dispatchers.io) {
        connectInternal(force, scannerConfig, adapter, timeoutMs)
    }

    fun disconnectAsync(): Job = scope.launch(dispatchers.io) {
        runCatching { manager.disconnect() }
            .onFailure { error ->
                Log.w(logTag, "Не удалось завершить OBD-сессию", error)
            }
    }

    suspend fun disconnect(): ObdConnectionSnapshot = withContext(dispatchers.io) {
        manager.disconnect()
    }

    private suspend fun connectInternal(
        force: Boolean,
        scannerConfig: BleScannerConfig?,
        adapter: ConnectedAdapter?,
        timeoutMs: Long?
    ): ObdConnectionSnapshot {
        return when {
            scannerConfig != null && timeoutMs != null ->
                manager.connect(
                    force = force,
                    scannerConfig = scannerConfig,
                    adapter = adapter,
                    timeoutMs = timeoutMs
                )
            scannerConfig != null ->
                manager.connect(
                    force = force,
                    scannerConfig = scannerConfig,
                    adapter = adapter
                )
            timeoutMs != null ->
                manager.connect(
                    force = force,
                    adapter = adapter,
                    timeoutMs = timeoutMs
                )
            else ->
                manager.connect(
                    force = force,
                    adapter = adapter
                )
        }
    }
}
