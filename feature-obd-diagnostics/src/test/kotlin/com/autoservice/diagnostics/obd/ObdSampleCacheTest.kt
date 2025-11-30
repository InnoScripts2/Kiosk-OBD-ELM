package com.autoservice.diagnostics.obd

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking

class ObdSampleCacheTest {

    private val pid = StandardPids.ENGINE_RPM

    @Test
    fun `returns cached sample within ttl and refreshes after expiry`() = runBlocking {
        var clock = 0L
        val cache = ObdSampleCache(ttlMillis = 1_000) { clock }
        var fetchCount = 0

        suspend fun fetcher(): ObdSample {
            fetchCount += 1
            return sample(value = fetchCount.toDouble(), timestamp = clock)
        }

        val first = cache.getOrFetch(pid, ::fetcher)
        clock += 500
        val second = cache.getOrFetch(pid, ::fetcher)
        assertEquals(1, fetchCount, "Value should be served from cache before TTL expires")
        assertEquals(first.value, second.value)

        clock += 600
        val third = cache.getOrFetch(pid, ::fetcher)
        assertEquals(2, fetchCount, "Cache must refresh after TTL has elapsed")
        assertEquals(2.0, third.value)
    }

    @Test
    fun `clear removes in flight handles so new requests refetch`() = runBlocking {
        var clock = 0L
        val cache = ObdSampleCache(ttlMillis = 10_000) { clock }
        val fetches = AtomicInteger(0)
        val startSignals = Channel<Int>(capacity = Channel.UNLIMITED)
        val resumeSignals = Channel<Double>(capacity = Channel.UNLIMITED)

        try {
            val fetcher: suspend () -> ObdSample = {
                val index = fetches.incrementAndGet()
                startSignals.send(index)
                val value = resumeSignals.receive()
                sample(value = value, timestamp = clock)
            }

            val first = async { cache.getOrFetch(pid, fetcher) }
            assertEquals(1, startSignals.receive())

            cache.clear()

            val second = async { cache.getOrFetch(pid, fetcher) }
            assertEquals(2, startSignals.receive())

            resumeSignals.send(100.0)
            assertEquals(100.0, first.await().value)

            resumeSignals.send(200.0)
            assertEquals(200.0, second.await().value)
        } finally {
            startSignals.cancel()
            resumeSignals.cancel()
        }
    }

    @Test
    fun `invalidate removes in flight entry for pid`() = runBlocking {
        var clock = 0L
        val cache = ObdSampleCache(ttlMillis = 10_000) { clock }
        val fetches = AtomicInteger(0)
        val startSignals = Channel<Int>(capacity = Channel.UNLIMITED)
        val resumeSignals = Channel<Double>(capacity = Channel.UNLIMITED)

        try {
            val fetcher: suspend () -> ObdSample = {
                val index = fetches.incrementAndGet()
                startSignals.send(index)
                val value = resumeSignals.receive()
                sample(value = value, timestamp = clock)
            }

            val first = async { cache.getOrFetch(pid, fetcher) }
            assertEquals(1, startSignals.receive())

            cache.invalidate(pid)

            val second = async { cache.getOrFetch(pid, fetcher) }
            assertEquals(2, startSignals.receive())

            resumeSignals.send(300.0)
            assertEquals(300.0, first.await().value)

            resumeSignals.send(400.0)
            assertEquals(400.0, second.await().value)
        } finally {
            startSignals.cancel()
            resumeSignals.cancel()
        }
    }

    private fun sample(value: Double, timestamp: Long): ObdSample = ObdSample(
        pid = pid,
        value = value,
        rawResponse = "raw",
        timestampMillis = timestamp,
    )
}
