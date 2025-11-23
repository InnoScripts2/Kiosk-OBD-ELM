package com.selfservice.kiosk.supabase

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SupabaseOutboxMonitorTest {

    private lateinit var root: File

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("supabase-outbox-monitor").toFile()
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun requestRefreshReadsOutboxSnapshot() = runTest(StandardTestDispatcher()) {
        val state = MutableStateFlow(SupabaseOutboxSync.State())
        var now = 0L
        val dispatcher = coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
        val monitor = SupabaseOutboxMonitor(
                directory = root,
                syncState = state,
                parentScope = this,
                pollIntervalMillis = Long.MAX_VALUE,
                dispatcher = dispatcher,
                clock = { now }
        )

    advanceUntilIdle()

        val writer = SupabaseOutboxWriter(root, clock = { 10L })
        writer.enqueue("adapter_selftests", mapOf("session_id" to "s1"))
        writer.enqueue("adapter_selftests", mapOf("session_id" to "s2"))

        now = 100L
        monitor.requestRefresh()
    advanceUntilIdle()

        val status = monitor.status.value
        assertEquals(2, status.pendingCount)
        assertEquals(10L, status.oldestPendingAtMillis)
        assertEquals(100L, status.lastRefreshAtMillis)
        assertNull(status.lastReadError)
    assertEquals(mapOf("adapter_selftests" to 2), status.pendingCountsByTable)
    assertEquals(10L, status.oldestPendingAtByTable["adapter_selftests"])

        monitor.close()
    }

    @Test
    fun syncStateUpdatesStatusTelemetry() = runTest(StandardTestDispatcher()) {
        val state = MutableStateFlow(SupabaseOutboxSync.State())
        val dispatcher = coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
        val monitor = SupabaseOutboxMonitor(
                directory = root,
                syncState = state,
                parentScope = this,
                pollIntervalMillis = 0L,
                dispatcher = dispatcher,
                clock = { currentTime }
        )

    advanceUntilIdle()

        state.value = SupabaseOutboxSync.State(
                isRunning = true,
                lastResult = SupabaseOutboxUploader.FlushResult(processed = 1, remaining = 5, discarded = 0),
                lastRunAtMillis = 200L
        )
    advanceUntilIdle()
        var status = monitor.status.value
        assertTrue(status.isRunning)
        assertEquals(5, status.pendingCount)
        assertEquals(200L, status.lastRunAtMillis)
        assertEquals(200L, status.lastSuccessAtMillis)
        assertNull(status.lastError)
        assertNull(status.lastFailureAtMillis)
    assertTrue(status.pendingCountsByTable.isEmpty())
    assertTrue(status.oldestPendingAtByTable.isEmpty())

        state.value = SupabaseOutboxSync.State(
                isRunning = false,
                lastError = IOException("network down"),
                lastRunAtMillis = 400L
        )
        runCurrent()
        status = monitor.status.value
    assertEquals(5, status.pendingCount)
    assertEquals(200L, status.lastSuccessAtMillis)
    assertEquals(400L, status.lastRunAtMillis)
    assertEquals(400L, status.lastFailureAtMillis)
    assertNotNull(status.lastError)

        monitor.close()
    }
}
