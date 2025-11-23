package com.selfservice.obd.core.dictionary

import com.selfservice.core.DispatchersProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.Closeable

/**
 * Supervises dictionary synchronisation in a shared coroutine scope so application layers do not
 * need to juggle coordinator lifecycle and scheduling details. Intended to run once per process
 * and expose manual refresh hooks for operator tooling.
 */
class DictionarySyncSupervisor(
    private val dispatchers: DispatchersProvider,
    private val scope: CoroutineScope,
    updateProvider: DictionaryUpdateProvider,
    private val schedule: DictionarySyncSchedule? = DictionarySyncDefaults.schedule,
    private val policy: DictionarySyncPolicy = DictionarySyncDefaults.policy,
    private val manager: ObdDictionaryManager = ObdDictionaryManager.shared
) : Closeable {

    private val synchronizer = ObdDictionarySynchronizer(manager, updateProvider, policy)
    private val internalCoordinator = DictionarySyncCoordinator(
        synchronizer = synchronizer,
        dispatchers = dispatchers,
        parentScope = scope
    )
    val coordinator: DictionarySyncCoordinator = internalCoordinator
    private val scheduledJob: Job? = schedule?.let { cadence ->
        internalCoordinator.schedulePeriodicSync(
                intervalMillis = cadence.intervalMillis,
                initialDelayMillis = cadence.initialDelayMillis,
                force = cadence.forceOnSchedule
        )
    }

    fun state(): StateFlow<DictionarySyncState> = internalCoordinator.state()

    val lastResult: DictionarySyncResult?
        get() = internalCoordinator.lastResult()

    fun requestSync(force: Boolean = false) {
        scope.launch(dispatchers.io) { internalCoordinator.triggerSync(force) }
    }

    suspend fun trigger(force: Boolean = false): DictionarySyncResult {
        return internalCoordinator.triggerSync(force)
    }

    fun triggerBlocking(force: Boolean = false): DictionarySyncResult =
            runBlocking(dispatchers.io) { internalCoordinator.triggerSync(force) }

    override fun close() {
        scheduledJob?.cancel()
        internalCoordinator.shutdown()
    }
}
