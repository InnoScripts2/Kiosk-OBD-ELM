package com.selfservice.kiosk.telemetry

import com.selfservice.core.DefaultDispatchersProvider
import com.selfservice.core.DispatchersProvider
import com.selfservice.platform.data.diagnostics.DiagnosticsTelemetrySummary
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Applies a time-based retention policy to diagnostics telemetry once records have been exported
 * to Supabase. Ensures on-device storage does not grow indefinitely while avoiding the removal of
 * pending telemetry that still requires export.
 */
class DiagnosticsTelemetryRetentionCoordinator(
    private val summaryState: StateFlow<DiagnosticsTelemetrySummary>,
    private val requestSummaryRefresh: () -> Unit,
    private val scope: CoroutineScope,
    private val dispatchers: DispatchersProvider = DefaultDispatchersProvider(),
    private val pruneAction: suspend (Long) -> Unit,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val retentionMillis: Long = DEFAULT_RETENTION_MILLIS,
    private val minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS,
    private val onPruneError: (Throwable) -> Unit = {},
) : Closeable {

    private val job: Job
    private var lastPrunedAtMillis: Long = Long.MIN_VALUE

    init {
        job = scope.launch(dispatchers.io) {
            summaryState.collect { summary ->
                evaluate(summary)
            }
        }
        requestSummaryRefresh()
    }

    private suspend fun evaluate(summary: DiagnosticsTelemetrySummary) {
        if (retentionMillis <= 0) {
            return
        }
        if (summary.totalCount <= 0) {
            return
        }
        if (summary.pendingCount > 0) {
            return
        }
        val now = clock()
        if (shouldSkipDueToInterval(now)) {
            return
        }
        val threshold = now - retentionMillis
        if (threshold <= 0L) {
            return
        }
        try {
            pruneAction(threshold)
            lastPrunedAtMillis = now
            requestSummaryRefresh()
        } catch (error: Throwable) {
            onPruneError(error)
        }
    }

    private fun shouldSkipDueToInterval(now: Long): Boolean {
        if (minIntervalMillis <= 0L) {
            return false
        }
        val lastPruned = lastPrunedAtMillis
        if (lastPruned == Long.MIN_VALUE) {
            return false
        }
        val elapsed = now - lastPruned
        return elapsed >= 0 && elapsed < minIntervalMillis
    }

    override fun close() {
        job.cancel()
    }

    companion object {
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L
        private const val MILLIS_PER_HOUR = 60L * 60L * 1_000L
        const val DEFAULT_RETENTION_MILLIS: Long = 14L * MILLIS_PER_DAY
        const val DEFAULT_MIN_INTERVAL_MILLIS: Long = 6L * MILLIS_PER_HOUR
    }
}