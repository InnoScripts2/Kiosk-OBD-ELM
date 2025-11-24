package com.selfservice.feature.payments

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for payment timeout use case
 * 
 * Verifies that:
 * - 10-minute timeout is enforced
 * - Timeout state transitions are correct
 * - Remaining time calculations are accurate
 * - Timeout triggers proper cleanup
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaymentTimeoutUseCaseTest {
    
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var testScope: TestCoroutineScope
    private lateinit var mockPaymentModule: MockPaymentModule
    private lateinit var mockClock: MockClock
    
    @Before
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        testScope = TestCoroutineScope(testDispatcher)
        mockPaymentModule = MockPaymentModule()
        mockClock = MockClock()
    }
    
    @After
    fun tearDown() {
        testScope.cleanupTestCoroutines()
    }
    
    @Test
    fun `timeout triggers after 10 minutes`() = testScope.runTest {
        val intentId = "test-intent-timeout"
        val timeoutMs = 10 * 60 * 1000L // 10 minutes
        
        val poller = PaymentStatusPoller(
            paymentModule = mockPaymentModule,
            intentId = intentId,
            scope = this,
            clock = mockClock,
            pollIntervalMs = 100L,
            timeoutMs = timeoutMs
        )
        
        // Start polling
        poller.startPolling()
        advanceTimeBy(100L) // First poll
        
        // Verify initial state
        assertEquals(PaymentStatusPoller.PollingState.Polling, poller.pollingState.value)
        
        // Advance time to just before timeout
        mockClock.advanceBy(timeoutMs - 1000L)
        advanceTimeBy(100L) // Poll again
        assertEquals(PaymentStatusPoller.PollingState.Polling, poller.pollingState.value)
        
        // Advance time past timeout
        mockClock.advanceBy(1000L)
        advanceTimeBy(100L) // Poll again
        
        // Verify timeout state
        val finalState = poller.pollingState.value
        assertTrue(finalState is PaymentStatusPoller.PollingState.TimedOut)
        assertTrue((finalState as PaymentStatusPoller.PollingState.TimedOut).elapsedMs >= timeoutMs)
    }
    
    @Test
    fun `remaining time decreases correctly`() = testScope.runTest {
        val intentId = "test-intent-remaining"
        val timeoutMs = 10 * 60 * 1000L // 10 minutes
        
        val poller = PaymentStatusPoller(
            paymentModule = mockPaymentModule,
            intentId = intentId,
            scope = this,
            clock = mockClock,
            timeoutMs = timeoutMs
        )
        
        poller.startPolling()
        advanceTimeBy(100L)
        
        // Check remaining time at start
        val initialRemaining = poller.getRemainingTimeMs()
        assertNotNull(initialRemaining)
        assertTrue(initialRemaining!! <= timeoutMs)
        
        // Advance time and check again
        mockClock.advanceBy(5 * 60 * 1000L) // 5 minutes
        val midRemaining = poller.getRemainingTimeMs()
        assertNotNull(midRemaining)
        assertTrue(midRemaining!! < initialRemaining)
        assertTrue(midRemaining >= 4 * 60 * 1000L) // At least 4 minutes left
        
        // Advance to near timeout
        mockClock.advanceBy(4 * 60 * 1000L) // Another 4 minutes
        val nearEndRemaining = poller.getRemainingTimeMs()
        assertNotNull(nearEndRemaining)
        assertTrue(nearEndRemaining!! < 2 * 60 * 1000L) // Less than 2 minutes left
    }
    
    @Test
    fun `isTimedOut returns false before timeout`() = testScope.runTest {
        val poller = PaymentStatusPoller(
            paymentModule = mockPaymentModule,
            intentId = "test-intent",
            scope = this,
            clock = mockClock,
            timeoutMs = 10 * 60 * 1000L
        )
        
        poller.startPolling()
        advanceTimeBy(100L)
        
        // Should not be timed out initially
        assertEquals(false, poller.isTimedOut())
        
        // Should not be timed out after 5 minutes
        mockClock.advanceBy(5 * 60 * 1000L)
        assertEquals(false, poller.isTimedOut())
    }
    
    @Test
    fun `isTimedOut returns true after timeout`() = testScope.runTest {
        val timeoutMs = 10 * 60 * 1000L
        val poller = PaymentStatusPoller(
            paymentModule = mockPaymentModule,
            intentId = "test-intent",
            scope = this,
            clock = mockClock,
            timeoutMs = timeoutMs
        )
        
        poller.startPolling()
        advanceTimeBy(100L)
        
        // Advance past timeout
        mockClock.advanceBy(timeoutMs + 1000L)
        advanceTimeBy(100L)
        
        // Should be timed out
        assertEquals(true, poller.isTimedOut())
    }
    
    @Test
    fun `timeout state includes elapsed time`() = testScope.runTest {
        val timeoutMs = 10 * 60 * 1000L
        val poller = PaymentStatusPoller(
            paymentModule = mockPaymentModule,
            intentId = "test-intent",
            scope = this,
            clock = mockClock,
            timeoutMs = timeoutMs
        )
        
        poller.startPolling()
        advanceTimeBy(100L)
        
        // Advance past timeout
        val extraTime = 5000L
        mockClock.advanceBy(timeoutMs + extraTime)
        advanceTimeBy(100L)
        
        // Verify timeout state has correct elapsed time
        val state = poller.pollingState.value
        assertTrue(state is PaymentStatusPoller.PollingState.TimedOut)
        val elapsed = (state as PaymentStatusPoller.PollingState.TimedOut).elapsedMs
        assertTrue(elapsed >= timeoutMs)
        assertTrue(elapsed <= timeoutMs + extraTime + 1000L)
    }
    
    @Test
    fun `poller stops after timeout`() = testScope.runTest {
        val timeoutMs = 1000L // Short timeout for test
        val poller = PaymentStatusPoller(
            paymentModule = mockPaymentModule,
            intentId = "test-intent",
            scope = this,
            clock = mockClock,
            pollIntervalMs = 100L,
            timeoutMs = timeoutMs
        )
        
        var pollCount = 0
        launch {
            poller.pollingState.collect { state ->
                if (state is PaymentStatusPoller.PollingState.Polling) {
                    pollCount++
                }
            }
        }
        
        poller.startPolling()
        advanceTimeBy(100L) // First poll
        
        // Advance past timeout
        mockClock.advanceBy(timeoutMs + 100L)
        advanceTimeBy(200L) // Try to poll again
        
        // Verify poller stopped
        assertTrue(poller.pollingState.value is PaymentStatusPoller.PollingState.TimedOut)
        
        // Advance more time and verify no more polls
        val pollCountAfterTimeout = pollCount
        advanceTimeBy(1000L)
        assertEquals(pollCountAfterTimeout, pollCount)
    }
    
    @Test
    fun `payment confirmed before timeout stops polling`() = testScope.runTest {
        val intentId = "test-intent-confirmed"
        mockPaymentModule.setStatus(intentId, PaymentStatus.PENDING)
        
        val poller = PaymentStatusPoller(
            paymentModule = mockPaymentModule,
            intentId = intentId,
            scope = this,
            clock = mockClock,
            pollIntervalMs = 100L,
            timeoutMs = 10 * 60 * 1000L
        )
        
        poller.startPolling()
        advanceTimeBy(100L) // First poll
        
        // Confirm payment after 5 seconds
        mockClock.advanceBy(5000L)
        mockPaymentModule.setStatus(intentId, PaymentStatus.CONFIRMED)
        advanceTimeBy(100L) // Poll again
        
        // Verify completed state, not timeout
        val state = poller.pollingState.value
        assertTrue(state is PaymentStatusPoller.PollingState.Completed)
        assertEquals(PaymentStatus.CONFIRMED, (state as PaymentStatusPoller.PollingState.Completed).finalStatus)
    }
    
    /**
     * Mock clock for testing time-dependent behavior
     */
    private class MockClock : Clock() {
        private var currentTime = Instant.parse("2025-11-24T00:00:00Z")
        
        fun advanceBy(millis: Long) {
            currentTime = currentTime.plusMillis(millis)
        }
        
        override fun instant(): Instant = currentTime
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
    }
    
    /**
     * Mock payment module for testing
     */
    private class MockPaymentModule : PaymentModule {
        private val statuses = mutableMapOf<String, PaymentStatus>()
        
        fun setStatus(intentId: String, status: PaymentStatus) {
            statuses[intentId] = status
        }
        
        override suspend fun createIntent(input: CreatePaymentIntentInput): CreatePaymentIntentResult {
            throw NotImplementedError("Not used in this test")
        }
        
        override suspend fun getStatus(intentId: String): PaymentStatus {
            return statuses[intentId] ?: PaymentStatus.PENDING
        }
        
        override suspend fun getIntent(intentId: String): PaymentIntent {
            throw NotImplementedError("Not used in this test")
        }
        
        override suspend fun confirmDev(intentId: String): GetPaymentIntentResult? {
            throw NotImplementedError("Not used in this test")
        }
    }
}
