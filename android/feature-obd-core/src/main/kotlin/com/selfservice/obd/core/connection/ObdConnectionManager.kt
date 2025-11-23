package com.selfservice.obd.core.connection

import com.selfservice.core.DispatchersProvider
import com.selfservice.obd.core.passthru.PassThruSessionManager
import com.selfservice.obd.core.session.ConnectedAdapter
import com.selfservice.obd.core.session.ObdSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import kotlin.coroutines.CoroutineContext

/**
 * High-level controller that mirrors the legacy BLE-based [ObdConnectionManager] ergonomics
 * on top of the new `PassThruSessionManager`. It coordinates adapter discovery, exposes a
 * diagnostics-oriented snapshot and keeps track of the last error/connection timestamps.
 */
class ObdConnectionManager(
    private val sessionGateway: SessionGateway,
    private val adapterDiscovery: AdapterDiscovery,
    private val dispatchers: DispatchersProvider,
    private val defaultScannerConfig: BleScannerConfig = BleScannerConfig.Default,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : Closeable {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val snapshotMutex = Mutex()
    private val connectMutex = Mutex()

    private val _snapshot = MutableStateFlow(
        ObdConnectionSnapshot(
            sessionState = sessionGateway.state.value,
            adapter = null,
            lastError = null,
            lastConnectedAtMillis = null,
            lastFailureAtMillis = null,
            reconnectAttempts = 0,
            isScanning = false
        )
    )
    private var activeAdapter: ConnectedAdapter? = null
    private var consecutiveFailures: Int = 0

    init {
        scope.launch {
            sessionGateway.state.collect { state ->
                updateFromSession(state)
            }
        }
    }

    /** Current snapshot of the adapter connection lifecycle. */
    val snapshot: StateFlow<ObdConnectionSnapshot> = _snapshot

    /** Exposes the raw session state flow for UI bindings and diagnostics. */
    val sessionState: StateFlow<ObdSessionState> = sessionGateway.state

    /**
     * Attempts to establish a diagnostics session. When [force] is false the manager will reuse
     * an in-flight or established connection. Adapter discovery relies on BLE scanning supplied
     * by [adapterDiscovery]; callers may optionally provide a pre-selected [adapter].
     *
     * @throws AdapterUnavailableException when no adapter is discovered within the timeout
     * @throws Throwable when the underlying session gateway fails to start
     */
    suspend fun connect(
        force: Boolean = false,
        scannerConfig: BleScannerConfig = defaultScannerConfig,
        adapter: ConnectedAdapter? = null,
        timeoutMs: Long = scannerConfig.timeoutMs
    ): ObdConnectionSnapshot {
        return connectMutex.withLock {
            val current = snapshot.value
            if (!force && current.sessionState.isBusy()) {
                return@withLock current
            }
            val effectiveTimeout = timeoutMs.takeIf { it > 0L } ?: scannerConfig.timeoutMs
            val attempt = consecutiveFailures + 1
            snapshotMutex.withLock {
                _snapshot.value = _snapshot.value.copy(
                    sessionState = ObdSessionState.Scanning(attempt),
                    isScanning = true,
                    lastError = null
                )
            }
            adapterDiscovery.start(scannerConfig)
            val resolvedAdapter = adapter
                ?: adapterDiscovery.latest(scannerConfig)
                ?: adapterDiscovery.await(scannerConfig, effectiveTimeout)
            if (resolvedAdapter == null) {
                val failure = AdapterUnavailableException(
                    "Не удалось обнаружить OBD-адаптер за ${effectiveTimeout} мс"
                )
                handleConnectionFailure(failure)
                adapterDiscovery.stop()
                throw failure
            }
            snapshotMutex.withLock { activeAdapter = resolvedAdapter }
            return@withLock try {
                val state = sessionGateway.startWithAdapter(resolvedAdapter, scannerConfig)
                snapshotMutex.withLock {
                    if (state is ObdSessionState.Ready || state is ObdSessionState.Diagnostics) {
                        consecutiveFailures = 0
                        _snapshot.value = _snapshot.value.copy(
                            adapter = resolvedAdapter,
                            sessionState = state,
                            lastError = null,
                            lastConnectedAtMillis = timeProvider(),
                            isScanning = false,
                            reconnectAttempts = consecutiveFailures
                        )
                    } else {
                        _snapshot.value = _snapshot.value.copy(
                            adapter = resolvedAdapter,
                            sessionState = state,
                            lastError = null,
                            isScanning = state is ObdSessionState.Scanning,
                            reconnectAttempts = consecutiveFailures
                        )
                    }
                }
                _snapshot.value
            } catch (error: Throwable) {
                adapterDiscovery.stop()
                handleConnectionFailure(error)
                throw error
            }
        }
    }

    /** Cancels the active diagnostics session and stops adapter discovery. */
    suspend fun disconnect(): ObdConnectionSnapshot {
        return connectMutex.withLock {
            runCatching { sessionGateway.cancel() }
            runCatching { adapterDiscovery.stop() }
            snapshotMutex.withLock {
                activeAdapter = null
                consecutiveFailures = 0
                _snapshot.value = _snapshot.value.copy(
                    sessionState = ObdSessionState.Idle,
                    adapter = null,
                    isScanning = false
                )
                _snapshot.value
            }
        }
    }

    override fun close() {
        scope.cancel()
        runCatching { adapterDiscovery.close() }
    }

    fun shutdown() = close()

    private suspend fun updateFromSession(state: ObdSessionState) {
        snapshotMutex.withLock {
            val now = timeProvider()
            val current = _snapshot.value
            val adapted = when (state) {
                is ObdSessionState.Scanning -> current.copy(
                    sessionState = state,
                    isScanning = true
                )
                is ObdSessionState.Connecting -> current.copy(
                    sessionState = state,
                    isScanning = false
                )
                is ObdSessionState.Ready -> {
                    consecutiveFailures = 0
                    current.copy(
                        sessionState = state,
                        adapter = activeAdapter ?: current.adapter,
                        lastConnectedAtMillis = now,
                        lastError = null,
                        isScanning = false,
                        reconnectAttempts = consecutiveFailures
                    )
                }
                is ObdSessionState.Diagnostics -> {
                    consecutiveFailures = 0
                    current.copy(
                        sessionState = state,
                        adapter = activeAdapter ?: current.adapter,
                        lastConnectedAtMillis = current.lastConnectedAtMillis ?: now,
                        lastError = null,
                        isScanning = false,
                        reconnectAttempts = consecutiveFailures
                    )
                }
                ObdSessionState.Completed -> {
                    val adapterSnapshot = activeAdapter ?: current.adapter
                    consecutiveFailures = 0
                    activeAdapter = null
                    current.copy(
                        sessionState = state,
                        adapter = adapterSnapshot,
                        lastConnectedAtMillis = current.lastConnectedAtMillis ?: now,
                        lastError = null,
                        isScanning = false,
                        reconnectAttempts = consecutiveFailures
                    )
                }
                is ObdSessionState.Failed -> {
                    consecutiveFailures += 1
                    activeAdapter = null
                    current.copy(
                        sessionState = state,
                        lastError = state.reason,
                        lastFailureAtMillis = now,
                        isScanning = false,
                        reconnectAttempts = consecutiveFailures
                    )
                }
                ObdSessionState.Idle -> {
                    activeAdapter = null
                    current.copy(
                        sessionState = state,
                        isScanning = false
                    )
                }
                is ObdSessionState.PreconditionsMissing -> current.copy(
                    sessionState = state,
                    isScanning = false
                )
            }
            _snapshot.value = adapted
        }
    }

    private suspend fun handleConnectionFailure(cause: Throwable) {
        snapshotMutex.withLock {
            consecutiveFailures += 1
            activeAdapter = null
            _snapshot.value = _snapshot.value.copy(
                sessionState = ObdSessionState.Idle,
                lastError = cause,
                lastFailureAtMillis = timeProvider(),
                isScanning = false,
                reconnectAttempts = consecutiveFailures
            )
        }
    }

    private fun ObdSessionState.isBusy(): Boolean = when (this) {
        is ObdSessionState.Connecting,
        is ObdSessionState.Diagnostics,
        is ObdSessionState.Ready,
        is ObdSessionState.Scanning -> true
        else -> false
    }

    interface SessionGateway {
        val state: StateFlow<ObdSessionState>
        suspend fun startWithAdapter(
            adapter: ConnectedAdapter,
            scannerConfig: BleScannerConfig
        ): ObdSessionState
        suspend fun cancel()
    }

    interface AdapterDiscovery : Closeable {
        suspend fun start(config: BleScannerConfig)
        fun latest(config: BleScannerConfig): ConnectedAdapter?
        suspend fun await(config: BleScannerConfig, timeoutMs: Long): ConnectedAdapter?
        suspend fun stop()
    }

    companion object {
        fun sessionGateway(manager: PassThruSessionManager): SessionGateway = object : SessionGateway {
            override val state: StateFlow<ObdSessionState> = manager.state

            override suspend fun startWithAdapter(
                adapter: ConnectedAdapter,
                scannerConfig: BleScannerConfig
            ): ObdSessionState {
                return manager.startWithAdapter(adapter, scannerConfig)
            }

            override suspend fun cancel() {
                manager.cancel()
            }
        }

        fun adapterDiscovery(
            manager: BleAdapterManager,
            dispatchers: DispatchersProvider
        ): AdapterDiscovery = BleAdapterDiscovery(manager, dispatchers)
    }

    private class BleAdapterDiscovery(
        private val manager: BleAdapterManager,
        dispatchers: DispatchersProvider
    ) : AdapterDiscovery {

        override suspend fun start(config: BleScannerConfig) {
            manager.startScanning(config)
        }

        override fun latest(config: BleScannerConfig): ConnectedAdapter? = manager.latestDevice(config)

        override suspend fun await(config: BleScannerConfig, timeoutMs: Long): ConnectedAdapter? {
            manager.latestDevice(config)?.let { return it }
            if (timeoutMs <= 0L) return null
            return withTimeoutOrNull(timeoutMs) {
                manager.adapters
                    .mapNotNull { manager.latestDevice(config) }
                    .first()
            }
        }

        override suspend fun stop() {
            manager.stopScanning()
        }

        override fun close() {
            runCatching {
                runBlocking { manager.stopScanning() }
            }
            manager.clear()
        }
    }
}

/** Snapshot describing the current connection status for diagnostics UI layers. */
data class ObdConnectionSnapshot(
    val sessionState: ObdSessionState,
    val adapter: ConnectedAdapter?,
    val lastError: Throwable?,
    val lastConnectedAtMillis: Long?,
    val lastFailureAtMillis: Long?,
    val reconnectAttempts: Int,
    val isScanning: Boolean
)

class AdapterUnavailableException(message: String) : IllegalStateException(message)
