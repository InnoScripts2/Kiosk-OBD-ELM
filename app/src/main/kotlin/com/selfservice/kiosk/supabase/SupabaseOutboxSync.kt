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
            var catchUpLoops = 0
            while (isActive) {
                val outcome = runCatching { guardedFlush(effectiveBatchSize(catchUpLoops)) }
                val result = outcome.getOrNull()
                handleOutcome(outcome)
                val remaining = result?.remaining ?: 0
                if (remaining > 0 && catchUpLoops < MAX_CATCH_UP_LOOPS) {
                    catchUpLoops += 1
                    continue
                }
                catchUpLoops = 0
                delay(intervalMillis)
            }
        }
    }

    suspend fun flushOnce(batchLimit: Int = maxEntriesPerFlush): SupabaseOutboxUploader.FlushResult {
        val outcome = runCatching { guardedFlush(batchLimit) }
        handleOutcome(outcome)
        return outcome.getOrThrow()
    }

    fun requestImmediateFlush() {
        scope.launch {
            runCatching { flushOnce(immediateFlushBatchSize) }
        }
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

    private fun effectiveBatchSize(loopIndex: Int): Int {
        if (loopIndex <= 0) {
            return maxEntriesPerFlush
        }
        val multiplier = (loopIndex + 1).coerceAtMost(MAX_CATCH_UP_MULTIPLIER)
        return maxEntriesPerFlush * multiplier
    }

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
        private const val DEFAULT_INTERVAL_MS = 60_000L
        private const val DEFAULT_BATCH_SIZE = 200
        private const val MAX_CATCH_UP_LOOPS = 3
        private const val MAX_CATCH_UP_MULTIPLIER = 4
        private const val IMMEDIATE_FLUSH_MULTIPLIER = 2
    }

    private val immediateFlushBatchSize: Int
        get() = (maxEntriesPerFlush * IMMEDIATE_FLUSH_MULTIPLIER).coerceAtLeast(maxEntriesPerFlush)

    data class State(
            val isRunning: Boolean = false,
            val lastResult: SupabaseOutboxUploader.FlushResult? = null,
            val lastError: Throwable? = null,
            val lastRunAtMillis: Long? = null
    )
}
