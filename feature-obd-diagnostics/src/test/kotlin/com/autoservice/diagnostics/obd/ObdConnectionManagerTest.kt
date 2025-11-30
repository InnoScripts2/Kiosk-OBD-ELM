package com.autoservice.diagnostics.obd

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ObdConnectionManagerTest {

    @Test
    fun `read returns parsed sample`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val pid = StandardPids.ENGINE_RPM
        val frame = PIDUtils.formatRequest(pid)
        val transport = StubTransport(mapOf(frame to listOf("41 0C 1A F8")))
        val manager = ObdConnectionManager(
            transport = transport,
            pollingPids = listOf(pid),
            dispatcher = dispatcher,
            clock = { 123L }
        )

        manager.connect()
        val sample = manager.read(pid)

        assertEquals(1726.0, sample.value, 0.1)
        assertEquals(123L, sample.timestampMillis)
        assertEquals("41 0C 1A F8", sample.rawResponse)
    }

    @Test
    fun `observeStandardPids emits batch`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coolant = StandardPids.ENGINE_COOLANT_TEMP
        val speed = StandardPids.VEHICLE_SPEED
        val transport = StubTransport(
            mapOf(
                PIDUtils.formatRequest(coolant) to listOf("41 05 64"),
                PIDUtils.formatRequest(speed) to listOf("41 0D 28")
            )
        )
        val manager = ObdConnectionManager(
            transport = transport,
            pollingPids = listOf(coolant, speed),
            dispatcher = dispatcher,
            clock = { 1_000L }
        )

        manager.connect()
        val batch = manager.observeStandardPids(pollIntervalMs = 1).first()

        assertEquals(2, batch.size)
        assertEquals(60.0, batch[0].value)
        assertEquals(40.0, batch[1].value)
    }

    @Test
    fun `read reuses cached sample within ttl`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coolant = StandardPids.ENGINE_COOLANT_TEMP
        val frame = PIDUtils.formatRequest(coolant)
        val clock = MutableClock(0L)
        val transport = StubTransport(mapOf(frame to listOf("41 05 50")))
        val manager = ObdConnectionManager(
            transport = transport,
            pollingPids = listOf(coolant),
            dispatcher = dispatcher,
            clock = { clock.now },
            cacheTtlMillis = 5_000L
        )

        manager.connect()
        val first = manager.read(coolant)
        clock.advance(1_000L)
        val second = manager.read(coolant)

        assertEquals(1, transport.executeCount)
        assertEquals(first, second, "Cached sample should be reused within TTL")
    }

    @Test
    fun `cache expires after ttl`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coolant = StandardPids.ENGINE_COOLANT_TEMP
        val frame = PIDUtils.formatRequest(coolant)
        val clock = MutableClock(0L)
        val transport = StubTransport(mapOf(frame to listOf("41 05 50", "41 05 64")))
        val manager = ObdConnectionManager(
            transport = transport,
            pollingPids = listOf(coolant),
            dispatcher = dispatcher,
            clock = { clock.now },
            cacheTtlMillis = 2_000L
        )

        manager.connect()
        val first = manager.read(coolant)
        clock.advance(3_000L)
        val second = manager.read(coolant)

        assertEquals(2, transport.executeCount)
        assertNotEquals(first.rawResponse, second.rawResponse)
    }

    @Test
    fun `force reconnect clears cache`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coolant = StandardPids.ENGINE_COOLANT_TEMP
        val frame = PIDUtils.formatRequest(coolant)
        val clock = MutableClock(0L)
        val transport = StubTransport(
            responses = mapOf(frame to listOf("41 05 32", "41 05 64"))
        )
        val manager = ObdConnectionManager(
            transport = transport,
            pollingPids = listOf(coolant),
            dispatcher = dispatcher,
            clock = { clock.now },
            cacheTtlMillis = 60_000L
        )

        assertTrue(manager.connect())
        val first = manager.read(coolant)
        val cached = manager.read(coolant)
        assertEquals(1, transport.executeCount)
        assertEquals(first, cached)

        assertTrue(manager.connect(forceReconnect = true))
        val afterReconnect = manager.read(coolant)

        assertEquals(2, transport.executeCount)
        assertEquals(60.0, afterReconnect.value, 0.1)
    }

    @Test
    fun `invalidate during inflight fetch prevents stale cache`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coolant = StandardPids.ENGINE_COOLANT_TEMP
        val frame = PIDUtils.formatRequest(coolant)
        val clock = MutableClock(0L)
        val transport = StubTransport(
            responses = mapOf(frame to listOf("41 05 28", "41 05 64")),
            executionDelayMillis = 100L
        )
        val manager = ObdConnectionManager(
            transport = transport,
            pollingPids = listOf(coolant),
            dispatcher = dispatcher,
            clock = { clock.now },
            cacheTtlMillis = 10_000L
        )

        assertTrue(manager.connect())
        val pending = async { manager.read(coolant) }
        testScheduler.advanceTimeBy(50L)
        manager.invalidateCache(coolant)
        testScheduler.advanceUntilIdle()
        val first = pending.await()

        val second = manager.read(coolant)

        assertEquals(2, transport.executeCount)
        assertNotEquals(first.rawResponse, second.rawResponse)
    }

    @Test
    fun `concurrent reads share inflight fetch`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coolant = StandardPids.ENGINE_COOLANT_TEMP
        val frame = PIDUtils.formatRequest(coolant)
        val transport = StubTransport(
            responses = mapOf(frame to listOf("41 05 64")),
            executionDelayMillis = 50L
        )
        val manager = ObdConnectionManager(
            transport = transport,
            pollingPids = listOf(coolant),
            dispatcher = dispatcher,
            clock = { 1_000L },
            cacheTtlMillis = 5_000L
        )

        manager.connect()
        val first = async { manager.read(coolant) }
        val second = async { manager.read(coolant) }

        testScheduler.advanceUntilIdle()

        assertEquals(1, transport.executeCount)
        assertEquals(first.await(), second.await())
    }

    private class MutableClock(var now: Long) {
        fun advance(delta: Long) { now += delta }
    }

    private class StubTransport(
        responses: Map<String, List<String>>,
        private val executionDelayMillis: Long = 0L,
        private val connectResult: Boolean = true
    ) : ObdTransport {
        private val queues = responses.mapValues { ArrayDeque(it.value) }.toMutableMap()
        var executeCount: Int = 0
            private set
        var connectCount: Int = 0
            private set
        override suspend fun connect(protocol: ObdProtocol): Boolean {
            connectCount += 1
            return connectResult
        }
        override suspend fun execute(frame: String): String {
            executeCount += 1
            if (executionDelayMillis > 0L) {
                kotlinx.coroutines.delay(executionDelayMillis)
            }
            val queue = queues[frame] ?: error("No response for frame $frame")
            if (queue.isEmpty()) error("No remaining responses for $frame")
            return queue.removeFirst()
        }
    }
}
