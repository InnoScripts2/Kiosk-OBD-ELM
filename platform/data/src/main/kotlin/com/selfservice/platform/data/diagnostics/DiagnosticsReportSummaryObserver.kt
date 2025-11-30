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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DiagnosticsReportSummaryObserver(
    private val store: DiagnosticsReportStore,
    private val scope: CoroutineScope,
    private val dispatchers: DispatchersProvider = DefaultDispatchersProvider(),
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS
) : Closeable {

    private val refreshCounter = MutableStateFlow(0L)
    private val stateFlow = MutableStateFlow(DiagnosticsReportSummary.empty())

    private val refreshJob: Job
    private val pollJob: Job?

    val state: StateFlow<DiagnosticsReportSummary> = stateFlow.asStateFlow()

    init {
        refreshJob = scope.launch(context = dispatchers.io, start = CoroutineStart.UNDISPATCHED) {
            refreshInternal()
            var lastHandled = refreshCounter.value
            refreshCounter.collect { version ->
                if (version == lastHandled) {
                    return@collect
                }
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

    fun requestRefresh() {
        enqueueRefresh()
    }

    private suspend fun refreshInternal() {
        runCatching { store.summary() }
            .onSuccess { summary -> stateFlow.value = summary }
    }

    private fun enqueueRefresh() {
        val scopeJob = scope.coroutineContext[Job]
        if (scopeJob?.isActive != true) {
            return
        }
        refreshCounter.update { current -> current + 1 }
    }

    override fun close() {
        refreshJob.cancel()
        pollJob?.cancel()
    }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MILLIS: Long = 15_000L
    }
}