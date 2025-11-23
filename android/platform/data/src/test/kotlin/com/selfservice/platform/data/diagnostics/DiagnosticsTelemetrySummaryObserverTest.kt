package com.selfservice.platform.data.diagnostics

import com.selfservice.core.DispatchersProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsTelemetrySummaryObserverTest {

    @Test
    fun `initial summary emitted on start`() = runTest {
        val store = SequencedTelemetryStore(
            summaries = mutableListOf(
                DiagnosticsTelemetrySummary(totalCount = 2, pendingCount = 1, oldestPendingAtMillis = 50L)
            )
        )

        withObserver(store) { observer ->
            advanceUntilIdle()

            assertEquals(2, observer.state.value.totalCount)
            assertEquals(1, store.summaryCalls)
        }
    }

    @Test
    fun `request refresh updates state`() = runTest {
        val store = SequencedTelemetryStore(
            summaries = mutableListOf(
                DiagnosticsTelemetrySummary(totalCount = 0, pendingCount = 0, oldestPendingAtMillis = null),
                DiagnosticsTelemetrySummary(totalCount = 4, pendingCount = 0, oldestPendingAtMillis = null)
            )
        )

        withObserver(store) { observer ->
            advanceUntilIdle()
            observer.requestRefresh()
            runCurrent()
            advanceUntilIdle()

            assertEquals(4, observer.state.value.totalCount)
        }
    }

    @Test
    fun `periodic poll triggers refresh`() = runTest {
        val store = SequencedTelemetryStore(
            summaries = mutableListOf(
                DiagnosticsTelemetrySummary(totalCount = 1, pendingCount = 1, oldestPendingAtMillis = 10L),
                DiagnosticsTelemetrySummary(totalCount = 5, pendingCount = 0, oldestPendingAtMillis = null)
            )
        )

        withObserver(store, pollIntervalMillis = 1_000L) { observer ->
            advanceUntilIdle()
            testScheduler.advanceTimeBy(1_000L)
            runCurrent()

            assertEquals(5, observer.state.value.totalCount)
            assertEquals(0, observer.state.value.pendingCount)
        }
    }

    private suspend fun TestScope.withObserver(
        store: DiagnosticsTelemetryStore,
        pollIntervalMillis: Long = DiagnosticsTelemetrySummaryObserver.DEFAULT_POLL_INTERVAL_MILLIS,
        block: suspend TestScope.(DiagnosticsTelemetrySummaryObserver) -> Unit
    ) {
        val observer = DiagnosticsTelemetrySummaryObserver(
            store = store,
            scope = backgroundScope,
            dispatchers = testDispatchers(),
            pollIntervalMillis = pollIntervalMillis
        )
        try {
            block(observer)
        } finally {
            observer.close()
            advanceUntilIdle()
        }
    }

    private fun TestScope.testDispatchers(): DispatchersProvider {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return object : DispatchersProvider {
            override val io: CoroutineDispatcher = dispatcher
            override val computation: CoroutineDispatcher = dispatcher
            override val main: CoroutineDispatcher = dispatcher
        }
    }

    private class SequencedTelemetryStore(
        private val summaries: MutableList<DiagnosticsTelemetrySummary>
    ) : DiagnosticsTelemetryStore {

        var summaryCalls: Int = 0
            private set

        override suspend fun record(record: DiagnosticsTelemetryRecord) {
            error("Not implemented")
        }

        override suspend fun pending(): List<DiagnosticsTelemetryRecord> = emptyList()

        override suspend fun markExportedUpTo(timestampMillis: Long) {
            error("Not implemented")
        }

        override suspend fun deleteOlderThan(thresholdMillis: Long) {
            error("Not implemented")
        }

        override suspend fun summary(): DiagnosticsTelemetrySummary {
            summaryCalls += 1
            return summaries.removeFirstOrNull() ?: DiagnosticsTelemetrySummary.empty()
        }
    }
}