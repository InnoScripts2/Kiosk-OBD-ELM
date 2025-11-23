package com.selfservice.platform.data.diagnostics

import com.selfservice.core.DefaultDispatchersProvider
import com.selfservice.core.DispatchersProvider
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Observes [DiagnosticsLogStore] to expose the latest [DiagnosticsLogSummary].
 * The observer reacts to explicit refresh requests and optionally performs
 * periodic polling to keep the state up to date for long-running sessions.
 */
class DiagnosticsLogSummaryObserver(
    private val store: DiagnosticsLogStore,
    private val scope: CoroutineScope,
    private val dispatchers: DispatchersProvider = DefaultDispatchersProvider(),
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
) : Closeable {

    private val refreshCounter = MutableStateFlow(0L)
    private val stateFlow = MutableStateFlow(DiagnosticsLogSummary.empty())

    private val refreshJob: Job
    private val pollJob: Job?

    val state: StateFlow<DiagnosticsLogSummary> = stateFlow.asStateFlow()

    init {
        refreshJob = scope.launch(context = dispatchers.io, start = CoroutineStart.UNDISPATCHED) {
            refreshInternal()
            var lastHandled = refreshCounter.value
            refreshCounter.collect { version ->
                println("refreshCounter event version=$version lastHandled=$lastHandled")
                if (version == lastHandled) {
                    return@collect
                }
                println("refreshRequests consumed version=$version")
                lastHandled = version
                refreshInternal()
            }
        }
        pollJob = if (pollIntervalMillis > 0) {
            scope.launch(dispatchers.io) {
                while (isActive) {
                    delay(pollIntervalMillis)
                    enqueueRefresh()
                }
            }
        } else {
            null
        }
    }

    /**
     * Requests an immediate refresh of the summary. Multiple rapid calls are
     * coalesced to avoid redundant database work.
     */
    fun requestRefresh() {
        println("requestRefresh scopeActive=${scope.coroutineContext[Job]?.isActive}")
        enqueueRefresh()
    }

    private suspend fun refreshInternal() {
        println("refreshInternal fetch")
        runCatching { store.summary() }
            .onSuccess { summary -> stateFlow.value = summary }
    }

    private fun enqueueRefresh() {
        val scopeJob = scope.coroutineContext[Job]
        if (scopeJob?.isActive != true) {
            return
        }

        refreshCounter.update { current ->
            val next = current + 1
            println("enqueueRefresh queued version=$next")
            next
        }
    }

    override fun close() {
        refreshJob.cancel()
        pollJob?.cancel()
        // cancel jobs to stop background work
    }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MILLIS: Long = 15_000L
    }
}
