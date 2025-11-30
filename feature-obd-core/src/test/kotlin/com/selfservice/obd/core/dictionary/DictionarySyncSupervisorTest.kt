package com.selfservice.obd.core.dictionary

import com.selfservice.core.DispatchersProvider
import com.selfservice.obd.core.dtc.ObdDtcDefinition
import com.selfservice.obd.core.pid.ObdPidDefinition
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

private fun testDispatcher(context: CoroutineContext): CoroutineDispatcher =
        context[ContinuationInterceptor] as? CoroutineDispatcher
                ?: error("Test coroutine context missing dispatcher")

private class TestDispatchers(private val dispatcher: CoroutineDispatcher) : DispatchersProvider {
    override val io = dispatcher
    override val computation = dispatcher
    override val main = dispatcher
}

@OptIn(ExperimentalCoroutinesApi::class)
class DictionarySyncSupervisorTest {

    @Test
    fun triggerSyncAppliesUpdatesAndPublishesState() = runTest {
                val dispatcher = testDispatcher(coroutineContext)
        val dispatchers = TestDispatchers(dispatcher)
        val manager = ObdDictionaryManager.default()
        val provider = DictionaryUpdateProvider {
            DictionaryUpdateBatch(
                    pidDefinitions = listOf(
                            ObdPidDefinition("0x01", "0x0C", "Engine RPM")
                    ),
                    dtcDefinitions = listOf(
                            ObdDtcDefinition("P0001", ObdDtcDefinition.System.POWERTRAIN, "Fuel Volume Regulator")
                    ),
                    source = "test",
                    pidVersionLabel = "upd1",
                    dtcVersionLabel = "upd1"
            )
        }

        val supervisor = DictionarySyncSupervisor(
                dispatchers = dispatchers,
                scope = this,
                updateProvider = provider,
                schedule = null,
                manager = manager
        )

        val result = supervisor.trigger(force = true)
        advanceUntilIdle()

        assertTrue(result is DictionarySyncResult.Performed)
        assertEquals("upd1", manager.pidRevision().versionLabel)
        assertEquals("upd1", manager.dtcRevision().versionLabel)

        val state = supervisor.state().value
        assertTrue(state is DictionarySyncState.Completed)
        assertTrue(supervisor.lastResult is DictionarySyncResult.Performed)
        supervisor.close()
    }

    @Test
    fun requestSyncRunsInProvidedScope() = runTest {
                val dispatcher = testDispatcher(coroutineContext)
        val dispatchers = TestDispatchers(dispatcher)
        val manager = ObdDictionaryManager.default()
        val provider = DictionaryUpdateProvider { null }

        val supervisor = DictionarySyncSupervisor(
                dispatchers = dispatchers,
                scope = this,
                updateProvider = provider,
                schedule = null,
                manager = manager
        )

        supervisor.requestSync(force = true)
        advanceUntilIdle()

        assertTrue(supervisor.lastResult is DictionarySyncResult.Skipped)
        supervisor.close()
    }
}
