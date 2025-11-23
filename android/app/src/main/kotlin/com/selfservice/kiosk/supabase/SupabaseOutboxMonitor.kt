package com.selfservice.kiosk.supabase

import java.io.Closeable
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import org.json.JSONObject

/**
 * Observes the Supabase outbox directory and underlying sync state to surface queue health for UI
 * and diagnostics layers. Periodically polls the outbox file and supplements it with flush
 * metadata emitted by [SupabaseOutboxSync].
 */
class SupabaseOutboxMonitor(
		private val directory: File,
		private val fileName: String = SupabaseOutboxWriter.DEFAULT_FILE_NAME,
		private val syncState: StateFlow<SupabaseOutboxSync.State>,
		parentScope: CoroutineScope,
		private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MS,
		private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
		private val clock: () -> Long = { System.currentTimeMillis() }
) : Closeable {

	private val monitorJob = Job(parentScope.coroutineContext[Job])
	private val scope = CoroutineScope(parentScope.coroutineContext + monitorJob)
	private val refreshMutex = Mutex()
	private val statusFlow = MutableStateFlow(Status())

	val status: StateFlow<Status> = statusFlow.asStateFlow()

	init {
		scope.launch { collectSyncState() }
		scope.launch { pollingLoop() }
		scope.launch { refreshPending() }
	}

	/**
	 * Triggers an on-demand refresh of queue metrics. Called when new items are enqueued so the
	 * monitor does not have to wait for the next polling tick.
	 */
	fun requestRefresh() {
		scope.launch { refreshPending() }
	}

	override fun close() {
	monitorJob.cancel()
	}

	private suspend fun collectSyncState() {
		syncState.collect { state ->
			statusFlow.update { current ->
				val runAt = state.lastRunAtMillis
				val isNewRun = runAt != null && runAt != current.lastRunAtMillis
				val isSuccessRun =
						isNewRun && state.lastError == null && state.lastResult?.isSuccess == true
				val isFailureRun = isNewRun && state.lastError != null

				val pendingCount =
						if (isSuccessRun) {
							state.lastResult?.remaining ?: current.pendingCount
						} else {
							current.pendingCount
						}

				val lastRun = runAt ?: current.lastRunAtMillis
				val lastSuccess = if (isSuccessRun) runAt else current.lastSuccessAtMillis
				val lastFailure = if (isFailureRun) runAt else current.lastFailureAtMillis

				current.copy(
						isRunning = state.isRunning,
						pendingCount = pendingCount,
						lastRunAtMillis = lastRun,
						lastSuccessAtMillis = lastSuccess,
						lastFailureAtMillis = lastFailure,
						lastError = state.lastError
				)
			}
		}
	}

	private suspend fun pollingLoop() {
		if (pollIntervalMillis <= 0L) {
			return
		}
		while (scope.isActive) {
			delay(pollIntervalMillis)
			refreshPending()
		}
	}

	private suspend fun refreshPending() {
		val snapshot = refreshMutex.withLock { readQueueSnapshot() }
		statusFlow.update { current ->
			current.copy(
					pendingCount = snapshot.pendingCount,
					oldestPendingAtMillis = snapshot.oldestPendingAtMillis,
					lastRefreshAtMillis = clock(),
					lastReadError = snapshot.error,
					pendingCountsByTable = snapshot.pendingCountsByTable,
					oldestPendingAtByTable = snapshot.oldestPendingAtByTable
			)
		}
	}

	private suspend fun readQueueSnapshot(): QueueSnapshot =
			withContext(dispatcher) {
				try {
					val file = File(directory, fileName)
					if (!file.exists()) {
						return@withContext QueueSnapshot()
					}

					var count = 0
					var oldest: Long? = null
					val perTableCounts = mutableMapOf<String, Int>()
					val perTableOldest = mutableMapOf<String, Long>()
					file.forEachLine { line ->
						if (line.isBlank()) {
							return@forEachLine
						}
						try {
							val json = JSONObject(line)
							val payload = json.optJSONObject(KEY_PAYLOAD) ?: return@forEachLine
							payload.length() // touch payload to validate JSON structure
							count += 1
							val writtenAt = json.optLong(KEY_WRITTEN_AT, 0L)
							val table = json.optString(KEY_TABLE, "")
							if (writtenAt > 0) {
								oldest = when (val current = oldest) {
									null -> writtenAt
									else -> minOf(current, writtenAt)
								}
							}
							if (table.isNotBlank()) {
								perTableCounts[table] = (perTableCounts[table] ?: 0) + 1
								if (writtenAt > 0) {
									val currentOldest = perTableOldest[table]
									perTableOldest[table] = when (currentOldest) {
										null -> writtenAt
										else -> minOf(currentOldest, writtenAt)
									}
								}
							}
						} catch (_: JSONException) {
							// Skip malformed lines; uploader will discard them on next flush
						}
					}

					QueueSnapshot(
							pendingCount = count,
							oldestPendingAtMillis = oldest,
							pendingCountsByTable = perTableCounts.toMap(),
							oldestPendingAtByTable = perTableOldest.mapValues { it.value }
					)
				} catch (throwable: Throwable) {
					QueueSnapshot(error = throwable)
				}
			}

	data class Status(
			val isRunning: Boolean = false,
			val pendingCount: Int = 0,
			val oldestPendingAtMillis: Long? = null,
			val lastRunAtMillis: Long? = null,
			val lastSuccessAtMillis: Long? = null,
			val lastFailureAtMillis: Long? = null,
			val lastError: Throwable? = null,
			val lastReadError: Throwable? = null,
			val lastRefreshAtMillis: Long? = null,
			val pendingCountsByTable: Map<String, Int> = emptyMap(),
			val oldestPendingAtByTable: Map<String, Long?> = emptyMap()
	)

	private data class QueueSnapshot(
			val pendingCount: Int = 0,
			val oldestPendingAtMillis: Long? = null,
			val pendingCountsByTable: Map<String, Int> = emptyMap(),
			val oldestPendingAtByTable: Map<String, Long?> = emptyMap(),
			val error: Throwable? = null
	)

	companion object {
		private const val KEY_WRITTEN_AT = "written_at_ms"
		private const val KEY_PAYLOAD = "payload"
		private const val KEY_TABLE = "table"
		private const val DEFAULT_POLL_INTERVAL_MS = 30_000L
	}
}
