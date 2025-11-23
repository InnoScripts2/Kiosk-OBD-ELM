package com.selfservice.kiosk.supabase

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent

@OptIn(ExperimentalCoroutinesApi::class)
class SupabaseOutboxSyncTest {

    @Test
    fun startFlushesPeriodically() = runTest(StandardTestDispatcher()) {
        var flushCalls = 0
        val results = mutableListOf<SupabaseOutboxUploader.FlushResult>()
        var now = 0L
        val sync = SupabaseOutboxSync(
                flushBlock = {
                    flushCalls += 1
                    SupabaseOutboxUploader.FlushResult(processed = 1, remaining = 0, discarded = 0)
                },
                scope = this,
                intervalMillis = 1_000L,
                maxEntriesPerFlush = 5,
                onResult = { results += it },
                clock = { now }
        )

        assertFalse(sync.state.value.isRunning)
        assertNull(sync.state.value.lastResult)

        sync.start()
        runCurrent()
        assertEquals(1, flushCalls)
        assertTrue(sync.state.value.isRunning)
        assertEquals(0L, sync.state.value.lastRunAtMillis)
        assertEquals(1, results.size)
        assertEquals(1, sync.state.value.lastResult?.processed)

        now = 1_000L
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(2, flushCalls)
        assertEquals(1_000L, sync.state.value.lastRunAtMillis)
        assertEquals(2, results.size)

        now = 2_000L
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(3, flushCalls)
        assertEquals(2_000L, sync.state.value.lastRunAtMillis)
        assertEquals(3, results.size)
        assertNotNull(sync.state.value.lastResult)

        sync.stop()
        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(3, flushCalls)
        assertEquals(3, results.size)
        assertFalse(sync.state.value.isRunning)
    }

    @Test
    fun flushOncePropagatesFailure() = runTest(StandardTestDispatcher()) {
        val errors = mutableListOf<Throwable>()
        val sync = SupabaseOutboxSync(
                flushBlock = {
                    throw IOException("network down")
                },
                scope = this,
                onError = { errors += it },
                clock = { 42L }
        )

        val exception = assertFailsWith<IOException> { sync.flushOnce() }

        assertTrue(errors.isNotEmpty())
        assertTrue(exception.message?.contains("network") == true)
        assertEquals(42L, sync.state.value.lastRunAtMillis)
        assertNotNull(sync.state.value.lastError)
        assertFalse(sync.state.value.isRunning)
    }
}
