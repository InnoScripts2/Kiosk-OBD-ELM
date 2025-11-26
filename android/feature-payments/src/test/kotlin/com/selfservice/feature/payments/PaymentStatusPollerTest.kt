package com.selfservice.feature.payments

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for PaymentStatusPoller
 * 
 * Tests polling logic, timeout handling, and state transitions
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaymentStatusPollerTest {
    
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var testScope: TestScope
    private lateinit var statusProvider: FakePaymentStatusProvider
    private lateinit var testClock: SchedulerClock
    
    @Before
    fun setup() {
        val dispatcher = StandardTestDispatcher()
        testDispatcher = dispatcher
        testScope = TestScope(testDispatcher)
        statusProvider = FakePaymentStatusProvider()
        testClock = SchedulerClock(dispatcher.scheduler)
    }
    
    @After
    fun tearDown() {
        // Cleanup
    }
    
    @Test
    fun `startPolling polls status until confirmed`() = testScope.runTest {
        // Arrange
        statusProvider.setStatus("intent-123", PaymentStatus.PENDING)
        val poller = PaymentStatusPoller(
            statusProvider = statusProvider,
            intentId = "intent-123",
            scope = this,
            clock = testClock,
            pollIntervalMs = 100,
            timeoutMs = 10_000
        )
        
        // Act
        poller.startPolling()
        advanceTimeBy(50) // First poll
        
        // Assert - still polling
        assertTrue(poller.pollingState.value is PaymentStatusPoller.PollingState.Polling)
        // Simulate confirmation
        statusProvider.setStatus("intent-123", PaymentStatus.CONFIRMED)
        advanceTimeBy(150) // Second poll
        
        // Assert - completed
        val state2 = poller.pollingState.value
        assertTrue(state2 is PaymentStatusPoller.PollingState.Completed)
        assertEquals(PaymentStatus.CONFIRMED, (state2 as PaymentStatusPoller.PollingState.Completed).finalStatus)
    }
    
    @Test
    fun `startPolling times out after configured duration`() = testScope.runTest {
        // Arrange
        statusProvider.setStatus("intent-456", PaymentStatus.PENDING)
        val poller = PaymentStatusPoller(
            statusProvider = statusProvider,
            intentId = "intent-456",
            scope = this,
            clock = testClock,
            pollIntervalMs = 100,
            timeoutMs = 1_000 // 1 second timeout
        )
        
        // Act
        poller.startPolling()
        advanceTimeBy(1_100) // Exceed timeout
        
        // Assert
        val state = poller.pollingState.value
        assertTrue(state is PaymentStatusPoller.PollingState.TimedOut)
        assertTrue((state as PaymentStatusPoller.PollingState.TimedOut).elapsedMs >= 1_000)
    }
    
    @Test
    fun `stopPolling stops active polling`() = testScope.runTest {
        // Arrange
        statusProvider.setStatus("intent-789", PaymentStatus.PENDING)
        val poller = PaymentStatusPoller(
            statusProvider = statusProvider,
            intentId = "intent-789",
            scope = this,
            clock = testClock,
            pollIntervalMs = 100,
            timeoutMs = 10_000
        )
        
        // Act
        poller.startPolling()
        advanceTimeBy(50)
        poller.stopPolling()
        advanceTimeBy(100)
        
        // Assert
        val state = poller.pollingState.value
        assertTrue(state is PaymentStatusPoller.PollingState.Stopped)
    }
    
    @Test
    fun `getRemainingTimeMs returns correct value`() = testScope.runTest {
        // Arrange
        val poller = PaymentStatusPoller(
            statusProvider = statusProvider,
            intentId = "intent-999",
            scope = this,
            clock = testClock,
            pollIntervalMs = 100,
            timeoutMs = 5_000
        )
        
        // Act
        poller.startPolling()
        advanceTimeBy(1_000)
        
        // Assert
        val remaining = poller.getRemainingTimeMs()
        assertTrue(remaining != null && remaining <= 4_000 && remaining >= 3_900)
        poller.stopPolling()
    }
    
    @Test
    fun `isTimedOut returns true when timeout reached`() = testScope.runTest {
        // Arrange
        statusProvider.setStatus("intent-000", PaymentStatus.PENDING)
        val poller = PaymentStatusPoller(
            statusProvider = statusProvider,
            intentId = "intent-000",
            scope = this,
            clock = testClock,
            pollIntervalMs = 100,
            timeoutMs = 500
        )
        
        // Act
        poller.startPolling()
        advanceTimeBy(600)
        
        // Assert
        assertTrue(poller.isTimedOut())
    }
    
    @Test
    fun `polling handles failed status`() = testScope.runTest {
        // Arrange
        statusProvider.setStatus("intent-fail", PaymentStatus.PENDING)
        val poller = PaymentStatusPoller(
            statusProvider = statusProvider,
            intentId = "intent-fail",
            scope = this,
            clock = testClock,
            pollIntervalMs = 100,
            timeoutMs = 5_000
        )
        
        // Act
        poller.startPolling()
        advanceTimeBy(50)
        
        statusProvider.setStatus("intent-fail", PaymentStatus.FAILED)
        advanceTimeBy(150)
        
        // Assert
        val state = poller.pollingState.value
        assertTrue(state is PaymentStatusPoller.PollingState.Failed)
        assertEquals(PaymentStatus.FAILED, (state as PaymentStatusPoller.PollingState.Failed).finalStatus)
    }
    
    @Test
    fun `polling prevents duplicate start`() = testScope.runTest {
        // Arrange
        statusProvider.setStatus("intent-dup", PaymentStatus.PENDING)
        val poller = PaymentStatusPoller(
            statusProvider = statusProvider,
            intentId = "intent-dup",
            scope = this,
            clock = testClock,
            pollIntervalMs = 100,
            timeoutMs = 5_000
        )
        
        // Act
        poller.startPolling()
        poller.startPolling() // Duplicate call
        
        // Assert - should not crash and still be polling
        val state = poller.pollingState.value
        assertTrue(state is PaymentStatusPoller.PollingState.Polling)
        poller.stopPolling()
    }
    
    private class SchedulerClock(
        private val scheduler: TestCoroutineScheduler,
        private val zoneId: ZoneId = ZoneId.systemDefault()
    ) : Clock() {
        override fun getZone(): ZoneId = zoneId

        override fun withZone(zone: ZoneId): Clock = SchedulerClock(scheduler, zone)

        override fun instant(): Instant = Instant.ofEpochMilli(scheduler.currentTime)
    }

    private class FakePaymentStatusProvider : PaymentStatusProvider {
        private val statuses = mutableMapOf<String, PaymentStatus>()

        fun setStatus(intentId: String, status: PaymentStatus) {
            statuses[intentId] = status
        }

        override suspend fun getStatus(intentId: String): PaymentStatus? = statuses[intentId]
    }
}
