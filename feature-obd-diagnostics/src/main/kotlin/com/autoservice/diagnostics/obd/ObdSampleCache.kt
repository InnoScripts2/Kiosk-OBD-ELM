package com.autoservice.diagnostics.obd

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Small in-memory cache for OBD samples to avoid hammering the adapter with identical requests.
 */
internal class ObdSampleCache(
    private val ttlMillis: Long,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private val entries = mutableMapOf<String, CachedSample>()
    private val inFlight = mutableMapOf<String, CompletableDeferred<ObdSample>>()
    private val mutex = Mutex()
    private var generation: Long = 0L

    suspend fun getOrFetch(pid: PID, fetcher: suspend () -> ObdSample): ObdSample {
        if (ttlMillis <= 0L) return fetcher()
        val key = pid.key
        val now = clock()
        val handle = mutex.withLock {
            entries[key]?.takeIf { it.expiresAt >= now }?.let { return it.sample }
            val existing = inFlight[key]
            if (existing != null) {
                FetchHandle(existing, false, generation)
            } else {
                val created = CompletableDeferred<ObdSample>()
                inFlight[key] = created
                FetchHandle(created, true, generation)
            }
        }

        return if (handle.isOwner) {
            try {
                val sample = fetcher()
                val expiresAt = clock() + ttlMillis
                mutex.withLock {
                    if (generation == handle.generation) {
                        entries[key] = CachedSample(sample, expiresAt)
                    }
                    inFlight.remove(key)
                }
                handle.deferred.complete(sample)
                sample
            } catch (error: Throwable) {
                mutex.withLock { inFlight.remove(key) }
                handle.deferred.completeExceptionally(error)
                throw error
            }
        } else {
            handle.deferred.await()
        }
    }

    suspend fun invalidate(pid: PID) {
        val toCancel = mutex.withLock {
            generation += 1
            entries.remove(pid.key)
            inFlight.remove(pid.key)
        }
        toCancel?.cancel(CancellationException("PID ${pid.key} invalidated"))
    }

    suspend fun clear() {
        val toCancel = mutex.withLock {
            generation += 1
            entries.clear()
            val pending = inFlight.values.toList()
            inFlight.clear()
            pending
        }
        toCancel.forEach { deferred ->
            deferred.cancel(CancellationException("Cache cleared"))
        }
    }

    private data class CachedSample(val sample: ObdSample, val expiresAt: Long)

    private data class FetchHandle(
        val deferred: CompletableDeferred<ObdSample>,
        val isOwner: Boolean,
        val generation: Long
    )
}
