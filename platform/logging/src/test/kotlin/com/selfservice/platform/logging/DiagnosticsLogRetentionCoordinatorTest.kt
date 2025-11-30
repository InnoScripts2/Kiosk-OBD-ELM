package com.selfservice.platform.logging

import com.selfservice.core.DispatchersProvider
import com.selfservice.platform.data.diagnostics.DiagnosticsLogSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsLogRetentionCoordinatorTest {

    @Test
    fun `does not prune when pending entries remain`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = dispatcherProvider(dispatcher)
        val summaryState = MutableStateFlow(DiagnosticsLogSummary.empty())
        val refreshRequests = mutableListOf<Unit>()
        val pruneThresholds = mutableListOf<Long>()

        val coordinator = DiagnosticsLogRetentionCoordinator(
            summaryState = summaryState,
            requestSummaryRefresh = { refreshRequests += Unit },
            scope = this,
            dispatchers = dispatchers,
            pruneAction = { threshold -> pruneThresholds += threshold },
            clock = { 5_000L },
            retentionMillis = 1_000L,
            minIntervalMillis = 100L
        )

        advanceUntilIdle()

        summaryState.value = DiagnosticsLogSummary(totalCount = 4, pendingCount = 2, oldestPendingAtMillis = 123L)
        runCurrent()

        assertEquals(1, refreshRequests.size)
        assertTrue(pruneThresholds.isEmpty())

        coordinator.close()
    }

    @Test
    fun `prunes exported entries once retention exceeded`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = dispatcherProvider(dispatcher)
        val summaryState = MutableStateFlow(DiagnosticsLogSummary.empty())
        val refreshRequests = mutableListOf<Unit>()
        val pruneThresholds = mutableListOf<Long>()
        var now = 5_000L

        val coordinator = DiagnosticsLogRetentionCoordinator(
            summaryState = summaryState,
            requestSummaryRefresh = { refreshRequests += Unit },
            scope = this,
            dispatchers = dispatchers,
            pruneAction = { threshold -> pruneThresholds += threshold },
            clock = { now },
            retentionMillis = 1_000L,
            minIntervalMillis = 100L
        )

        advanceUntilIdle()

        summaryState.value = DiagnosticsLogSummary(totalCount = 3, pendingCount = 0, oldestPendingAtMillis = 321L)
        runCurrent()

        assertEquals(listOf(4_000L), pruneThresholds)
        assertEquals(2, refreshRequests.size)

        coordinator.close()
    }

    @Test
    fun `respects minimum interval between prune attempts`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = dispatcherProvider(dispatcher)
        val summaryState = MutableStateFlow(DiagnosticsLogSummary.empty())
        val refreshRequests = mutableListOf<Unit>()
        val pruneThresholds = mutableListOf<Long>()
        var now = 5_000L

        val coordinator = DiagnosticsLogRetentionCoordinator(
            summaryState = summaryState,
            requestSummaryRefresh = { refreshRequests += Unit },
            scope = this,
            dispatchers = dispatchers,
            pruneAction = { threshold -> pruneThresholds += threshold },
            clock = { now },
            retentionMillis = 2_000L,
            minIntervalMillis = 1_500L
        )

        advanceUntilIdle()

        summaryState.value = DiagnosticsLogSummary(totalCount = 5, pendingCount = 0, oldestPendingAtMillis = null)
        runCurrent()

        assertEquals(listOf(3_000L), pruneThresholds)
        assertEquals(2, refreshRequests.size)

        now += 1_000L
        summaryState.value = DiagnosticsLogSummary(totalCount = 6, pendingCount = 0, oldestPendingAtMillis = null)
        runCurrent()

        assertEquals(listOf(3_000L), pruneThresholds)
        assertEquals(2, refreshRequests.size)

        now += 1_600L
        summaryState.value = DiagnosticsLogSummary(totalCount = 7, pendingCount = 0, oldestPendingAtMillis = null)
        runCurrent()

        assertEquals(listOf(3_000L, 5_600L), pruneThresholds)
        assertEquals(3, refreshRequests.size)

        coordinator.close()
    }

    private fun dispatcherProvider(dispatcher: CoroutineDispatcher) = object : DispatchersProvider {
        override val io: CoroutineDispatcher = dispatcher
        override val computation: CoroutineDispatcher = dispatcher
        override val main: CoroutineDispatcher = dispatcher
    }
}
