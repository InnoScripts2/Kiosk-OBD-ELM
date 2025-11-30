package com.selfservice.kiosk.reports

import com.selfservice.core.DispatchersProvider
import com.selfservice.platform.data.diagnostics.DiagnosticsReportSummary
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
class DiagnosticsReportRetentionCoordinatorTest {

    @Test
    fun `skips prune while pending reports exist`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = testDispatchers(dispatcher)
        val summaryState = MutableStateFlow(DiagnosticsReportSummary.empty())
        val refreshRequests = mutableListOf<Unit>()
        val pruneThresholds = mutableListOf<Long>()

        val coordinator = DiagnosticsReportRetentionCoordinator(
            summaryState = summaryState,
            requestSummaryRefresh = { refreshRequests += Unit },
            scope = this,
            dispatchers = dispatchers,
            pruneAction = { threshold -> pruneThresholds += threshold },
            clock = { 20_000L },
            retentionMillis = 5_000L,
            minIntervalMillis = 1_000L
        )

        advanceUntilIdle()

        summaryState.value = DiagnosticsReportSummary(totalCount = 4, pendingCount = 2, oldestPendingAtMillis = 10_000L)
        runCurrent()

        assertEquals(1, refreshRequests.size)
        assertTrue(pruneThresholds.isEmpty())

        coordinator.close()
    }

    @Test
    fun `prunes when retention window exceeded`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = testDispatchers(dispatcher)
        val summaryState = MutableStateFlow(DiagnosticsReportSummary.empty())
        val refreshRequests = mutableListOf<Unit>()
        val pruneThresholds = mutableListOf<Long>()
        var now = 30_000L

        val coordinator = DiagnosticsReportRetentionCoordinator(
            summaryState = summaryState,
            requestSummaryRefresh = { refreshRequests += Unit },
            scope = this,
            dispatchers = dispatchers,
            pruneAction = { threshold -> pruneThresholds += threshold },
            clock = { now },
            retentionMillis = 10_000L,
            minIntervalMillis = 500L
        )

        advanceUntilIdle()

        summaryState.value = DiagnosticsReportSummary(totalCount = 3, pendingCount = 0, oldestPendingAtMillis = null)
        runCurrent()

        assertEquals(listOf(20_000L), pruneThresholds)
        assertEquals(2, refreshRequests.size)

        coordinator.close()
    }

    @Test
    fun `respects minimum interval between retention passes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = testDispatchers(dispatcher)
        val summaryState = MutableStateFlow(DiagnosticsReportSummary.empty())
        val refreshRequests = mutableListOf<Unit>()
        val pruneThresholds = mutableListOf<Long>()
        var now = 60_000L

        val coordinator = DiagnosticsReportRetentionCoordinator(
            summaryState = summaryState,
            requestSummaryRefresh = { refreshRequests += Unit },
            scope = this,
            dispatchers = dispatchers,
            pruneAction = { threshold -> pruneThresholds += threshold },
            clock = { now },
            retentionMillis = 20_000L,
            minIntervalMillis = 5_000L
        )

        advanceUntilIdle()

        summaryState.value = DiagnosticsReportSummary(totalCount = 8, pendingCount = 0, oldestPendingAtMillis = null)
        runCurrent()

        assertEquals(listOf(40_000L), pruneThresholds)
        assertEquals(2, refreshRequests.size)

        now += 2_000L
        summaryState.value = DiagnosticsReportSummary(totalCount = 9, pendingCount = 0, oldestPendingAtMillis = null)
        runCurrent()

        assertEquals(listOf(40_000L), pruneThresholds)
        assertEquals(2, refreshRequests.size)

        now += 6_000L
        summaryState.value = DiagnosticsReportSummary(totalCount = 10, pendingCount = 0, oldestPendingAtMillis = null)
        runCurrent()

    assertEquals(listOf(40_000L, 48_000L), pruneThresholds)
        assertEquals(3, refreshRequests.size)

        coordinator.close()
    }

    private fun testDispatchers(dispatcher: CoroutineDispatcher): DispatchersProvider {
        return object : DispatchersProvider {
            override val io: CoroutineDispatcher = dispatcher
            override val computation: CoroutineDispatcher = dispatcher
            override val main: CoroutineDispatcher = dispatcher
        }
    }
}
