package com.selfservice.platform.data.diagnostics

import com.selfservice.core.DispatchersProvider
import kotlin.coroutines.ContinuationInterceptor
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
class DiagnosticsLogSummaryObserverTest {

    @Test
    fun `initial summary emitted on start`() = runTest {
        val store = SequencedSummaryStore(
            summaries = mutableListOf(
                DiagnosticsLogSummary(totalCount = 1, pendingCount = 1, oldestPendingAtMillis = 10L)
            )
        )

        withObserver(store = store) { observer ->
            advanceUntilIdle()

            assertEquals(1, observer.state.value.totalCount)
            assertEquals(1, store.summaryCalls)
        }
    }

    @Test
    fun `request refresh updates state`() = runTest {
        val store = SequencedSummaryStore(
            summaries = mutableListOf(
                DiagnosticsLogSummary(totalCount = 0, pendingCount = 0, oldestPendingAtMillis = null),
                DiagnosticsLogSummary(totalCount = 3, pendingCount = 2, oldestPendingAtMillis = 21L)
            )
        )

        withObserver(store = store) { observer ->
            advanceUntilIdle()
            observer.requestRefresh()
            runCurrent()
            advanceUntilIdle()

            val summary = observer.state.value
            assertEquals(3, summary.totalCount)
            assertEquals(2, summary.pendingCount)
            assertEquals(21L, summary.oldestPendingAtMillis)
        }
    }

    @Test
    fun `periodic poll triggers refresh`() = runTest {
        val store = SequencedSummaryStore(
            summaries = mutableListOf(
                DiagnosticsLogSummary(totalCount = 0, pendingCount = 0, oldestPendingAtMillis = null),
                DiagnosticsLogSummary(totalCount = 5, pendingCount = 1, oldestPendingAtMillis = 33L)
            )
        )

        withObserver(store = store, pollIntervalMillis = 1000L) { observer ->
            advanceUntilIdle()
            testScheduler.advanceTimeBy(1000L)
            runCurrent()

            val summary = observer.state.value
            assertEquals(5, summary.totalCount)
            assertEquals(1, summary.pendingCount)
            assertEquals(33L, summary.oldestPendingAtMillis)
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

    private suspend fun TestScope.withObserver(
        store: DiagnosticsLogStore,
        pollIntervalMillis: Long = DiagnosticsLogSummaryObserver.DEFAULT_POLL_INTERVAL_MILLIS,
        block: suspend TestScope.(DiagnosticsLogSummaryObserver) -> Unit
    ) {
        val observer = DiagnosticsLogSummaryObserver(
            store = store,
            scope = backgroundScope,
            dispatchers = testDispatchers(),
            pollIntervalMillis = pollIntervalMillis
        )
        try {
            advanceUntilIdle()
            block(observer)
        } finally {
            observer.close()
            advanceUntilIdle()
        }
    }

    private class SequencedSummaryStore(
        private val summaries: MutableList<DiagnosticsLogSummary>
    ) : DiagnosticsLogStore {

        var summaryCalls: Int = 0
            private set

        override suspend fun record(entry: com.selfservice.core.logging.DiagnosticsLogEntry) {
            throw UnsupportedOperationException()
        }

        override suspend fun markExportedUpTo(timestampMillis: Long) {
            throw UnsupportedOperationException()
        }

        override suspend fun deleteOlderThan(thresholdMillis: Long) {
            throw UnsupportedOperationException()
        }

        override suspend fun summary(): DiagnosticsLogSummary {
            summaryCalls += 1
            println("SequencedSummaryStore.summary() call=$summaryCalls returning=${summaries.firstOrNull()}")
            return summaries.removeFirstOrNull() ?: DiagnosticsLogSummary.empty()
        }
    }
}
