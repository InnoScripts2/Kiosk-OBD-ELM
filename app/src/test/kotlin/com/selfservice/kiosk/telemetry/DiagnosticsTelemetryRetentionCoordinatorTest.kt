package com.selfservice.kiosk.telemetry

import com.selfservice.core.DispatchersProvider
import com.selfservice.platform.data.diagnostics.DiagnosticsTelemetrySummary
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
class DiagnosticsTelemetryRetentionCoordinatorTest {

    @Test
    fun `skips prune when pending telemetry remains`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = testDispatchers(dispatcher)
        val summaryState = MutableStateFlow(DiagnosticsTelemetrySummary.empty())
        val refreshRequests = mutableListOf<Unit>()
        val pruneThresholds = mutableListOf<Long>()

        val coordinator = DiagnosticsTelemetryRetentionCoordinator(
            summaryState = summaryState,
            requestSummaryRefresh = { refreshRequests += Unit },
            scope = this,
            dispatchers = dispatchers,
            pruneAction = { threshold -> pruneThresholds += threshold },
            clock = { 10_000L },
            retentionMillis = 2_000L,
            minIntervalMillis = 500L
        )

        advanceUntilIdle()

        summaryState.value = DiagnosticsTelemetrySummary(totalCount = 5, pendingCount = 3, oldestPendingAtMillis = 100L)
        runCurrent()

        assertEquals(1, refreshRequests.size)
        assertTrue(pruneThresholds.isEmpty())

        coordinator.close()
    }

    @Test
    fun `prunes once retention exceeded and no pending telemetry`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = testDispatchers(dispatcher)
        val summaryState = MutableStateFlow(DiagnosticsTelemetrySummary.empty())
        val refreshRequests = mutableListOf<Unit>()
        val pruneThresholds = mutableListOf<Long>()
        var now = 8_000L

        val coordinator = DiagnosticsTelemetryRetentionCoordinator(
            summaryState = summaryState,
            requestSummaryRefresh = { refreshRequests += Unit },
            scope = this,
            dispatchers = dispatchers,
            pruneAction = { threshold -> pruneThresholds += threshold },
            clock = { now },
            retentionMillis = 3_000L,
            minIntervalMillis = 500L
        )

        advanceUntilIdle()

        summaryState.value = DiagnosticsTelemetrySummary(totalCount = 6, pendingCount = 0, oldestPendingAtMillis = null)
        runCurrent()

        assertEquals(listOf(5_000L), pruneThresholds)
        assertEquals(2, refreshRequests.size)

        coordinator.close()
    }

    @Test
    fun `respects minimum interval between prune attempts`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = testDispatchers(dispatcher)
        val summaryState = MutableStateFlow(DiagnosticsTelemetrySummary.empty())
        val refreshRequests = mutableListOf<Unit>()
        val pruneThresholds = mutableListOf<Long>()
        var now = 12_000L

        val coordinator = DiagnosticsTelemetryRetentionCoordinator(
            summaryState = summaryState,
            requestSummaryRefresh = { refreshRequests += Unit },
            scope = this,
            dispatchers = dispatchers,
            pruneAction = { threshold -> pruneThresholds += threshold },
            clock = { now },
            retentionMillis = 4_000L,
            minIntervalMillis = 2_000L
        )

        advanceUntilIdle()

        summaryState.value = DiagnosticsTelemetrySummary(totalCount = 10, pendingCount = 0, oldestPendingAtMillis = null)
        runCurrent()

        assertEquals(listOf(8_000L), pruneThresholds)
        assertEquals(2, refreshRequests.size)

        now += 1_000L
        summaryState.value = DiagnosticsTelemetrySummary(totalCount = 12, pendingCount = 0, oldestPendingAtMillis = null)
        runCurrent()

        assertEquals(listOf(8_000L), pruneThresholds)
        assertEquals(2, refreshRequests.size)

        now += 2_100L
        summaryState.value = DiagnosticsTelemetrySummary(totalCount = 13, pendingCount = 0, oldestPendingAtMillis = null)
        runCurrent()

        assertEquals(listOf(8_000L, 11_100L), pruneThresholds)
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