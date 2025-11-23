package com.selfservice.kiosk.supabase

import android.util.Log
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Periodically flushes the Supabase outbox in the background so pending records are uploaded when
 * connectivity becomes available. The sync is resilient to individual failures and keeps retrying
 * on the configured interval.
 */
class SupabaseOutboxSync(
        private val flushBlock: suspend (Int) -> SupabaseOutboxUploader.FlushResult,
        private val scope: CoroutineScope,
        private val intervalMillis: Long = DEFAULT_INTERVAL_MS,
        private val maxEntriesPerFlush: Int = DEFAULT_BATCH_SIZE,
        private val onResult: (SupabaseOutboxUploader.FlushResult) -> Unit = { result ->
            if (result.processed > 0 || result.discarded > 0) {
                Log.i(TAG, "Supabase outbox flush processed=${result.processed}, discarded=${result.discarded}, remaining=${result.remaining}")
            }
            if (!result.isSuccess && result.failure != null) {
                Log.w(TAG, "Supabase outbox flush failed", result.failure)
            }
        },
        private val onError: (Throwable) -> Unit = { throwable ->
            Log.w(TAG, "Supabase outbox flush threw", throwable)
        },
        private val clock: () -> Long = { System.currentTimeMillis() }
) : Closeable {

    private var job: Job? = null
    private val mutex = Mutex()
    private val stateFlow = MutableStateFlow(State())

    val state: StateFlow<State> = stateFlow.asStateFlow()

    fun start() {
        if (job != null) return
        stateFlow.update { it.copy(isRunning = true) }
        job = scope.launch {
            while (isActive) {
                val outcome = runCatching { guardedFlush(maxEntriesPerFlush) }
                handleOutcome(outcome)
                delay(intervalMillis)
            }
        }
    }

    suspend fun flushOnce(): SupabaseOutboxUploader.FlushResult {
        val outcome = runCatching { guardedFlush(maxEntriesPerFlush) }
        handleOutcome(outcome)
        return outcome.getOrThrow()
    }

    fun stop() {
        job?.cancel()
        job = null
        stateFlow.update { it.copy(isRunning = false) }
    }

    override fun close() {
        stop()
    }

    private suspend fun guardedFlush(maxEntries: Int): SupabaseOutboxUploader.FlushResult =
            mutex.withLock { flushBlock(maxEntries) }

    private fun handleOutcome(outcome: Result<SupabaseOutboxUploader.FlushResult>) {
        val timestamp = clock()
        outcome
                .onSuccess { result ->
                    onResult(result)
                    stateFlow.update {
                        it.copy(
                                lastResult = result,
                                lastError = null,
                                lastRunAtMillis = timestamp
                        )
                    }
                }
                .onFailure { error ->
                    onError(error)
                    stateFlow.update {
                        it.copy(
                                lastError = error,
                                lastRunAtMillis = timestamp
                        )
                    }
                }
    }

    companion object {
        private const val TAG = "SupabaseOutboxSync"
        private const val DEFAULT_INTERVAL_MS = 5 * 60 * 1000L
        private const val DEFAULT_BATCH_SIZE = 32
    }

    data class State(
            val isRunning: Boolean = false,
            val lastResult: SupabaseOutboxUploader.FlushResult? = null,
            val lastError: Throwable? = null,
            val lastRunAtMillis: Long? = null
    )
}
