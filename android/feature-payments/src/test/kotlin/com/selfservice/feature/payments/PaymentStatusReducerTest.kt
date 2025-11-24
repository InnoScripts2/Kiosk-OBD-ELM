package com.selfservice.feature.payments

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for PaymentStatusReducer
 * 
 * Tests state management, duplicate emission prevention, and state transitions
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaymentStatusReducerTest {
    
    private lateinit var reducer: PaymentStatusReducer
    
    @Before
    fun setup() {
        reducer = PaymentStatusReducer()
    }
    
    @Test
    fun `initial state is Initial`() {
        // Assert
        val state = reducer.state.value
        assertTrue(state is PaymentStatusReducer.PaymentStatusState.Initial)
    }
    
    @Test
    fun `updateStatus creates StatusUpdate state`() {
        // Arrange
        val intentId = "intent-123"
        val status = PaymentStatus.PENDING
        val timestamp = 1700000000L
        
        // Act
        reducer.updateStatus(status, intentId, timestamp)
        
        // Assert
        val state = reducer.state.value
        assertTrue(state is PaymentStatusReducer.PaymentStatusState.StatusUpdate)
        assertEquals(status, (state as PaymentStatusReducer.PaymentStatusState.StatusUpdate).status)
        assertEquals(intentId, state.intentId)
        assertEquals(timestamp, state.timestamp)
        assertNull(state.previousStatus)
    }
    
    @Test
    fun `updateStatus prevents duplicate emissions`() {
        // Arrange
        val intentId = "intent-456"
        val status = PaymentStatus.PENDING
        
        // Act
        reducer.updateStatus(status, intentId, 1000L)
        val state1 = reducer.state.value
        
        reducer.updateStatus(status, intentId, 2000L) // Same status
        val state2 = reducer.state.value
        
        // Assert - should be the same object (not updated)
        assertEquals(state1, state2)
        assertEquals(1000L, (state2 as PaymentStatusReducer.PaymentStatusState.StatusUpdate).timestamp)
    }
    
    @Test
    fun `updateStatus tracks previous status`() {
        // Arrange
        val intentId = "intent-789"
        
        // Act
        reducer.updateStatus(PaymentStatus.PENDING, intentId, 1000L)
        reducer.updateStatus(PaymentStatus.CONFIRMED, intentId, 2000L)
        
        // Assert
        val state = reducer.state.value
        assertTrue(state is PaymentStatusReducer.PaymentStatusState.StatusUpdate)
        assertEquals(PaymentStatus.CONFIRMED, (state as PaymentStatusReducer.PaymentStatusState.StatusUpdate).status)
        assertEquals(PaymentStatus.PENDING, state.previousStatus)
    }
    
    @Test
    fun `markProcessing creates Processing state`() {
        // Arrange
        val intentId = "intent-proc"
        
        // Act
        reducer.markProcessing(intentId)
        
        // Assert
        val state = reducer.state.value
        assertTrue(state is PaymentStatusReducer.PaymentStatusState.Processing)
        assertEquals(intentId, (state as PaymentStatusReducer.PaymentStatusState.Processing).intentId)
    }
    
    @Test
    fun `markCompleted creates Completed state`() {
        // Arrange
        val intentId = "intent-done"
        val finalStatus = PaymentStatus.CONFIRMED
        
        // Act
        reducer.markCompleted(intentId, finalStatus)
        
        // Assert
        val state = reducer.state.value
        assertTrue(state is PaymentStatusReducer.PaymentStatusState.Completed)
        assertEquals(intentId, (state as PaymentStatusReducer.PaymentStatusState.Completed).intentId)
        assertEquals(finalStatus, state.finalStatus)
    }
    
    @Test
    fun `markTimedOut creates TimedOut state`() {
        // Arrange
        val intentId = "intent-timeout"
        val elapsedMs = 600_000L // 10 minutes
        
        // Act
        reducer.markTimedOut(intentId, elapsedMs)
        
        // Assert
        val state = reducer.state.value
        assertTrue(state is PaymentStatusReducer.PaymentStatusState.TimedOut)
        assertEquals(intentId, (state as PaymentStatusReducer.PaymentStatusState.TimedOut).intentId)
        assertEquals(elapsedMs, state.elapsedMs)
    }
    
    @Test
    fun `markFailed creates Failed state`() {
        // Arrange
        val intentId = "intent-fail"
        val error = "Network error"
        
        // Act
        reducer.markFailed(intentId, error)
        
        // Assert
        val state = reducer.state.value
        assertTrue(state is PaymentStatusReducer.PaymentStatusState.Failed)
        assertEquals(intentId, (state as PaymentStatusReducer.PaymentStatusState.Failed).intentId)
        assertEquals(error, state.error)
    }
    
    @Test
    fun `reset returns to Initial state`() {
        // Arrange
        reducer.markProcessing("intent-reset")
        
        // Act
        reducer.reset()
        
        // Assert
        val state = reducer.state.value
        assertTrue(state is PaymentStatusReducer.PaymentStatusState.Initial)
    }
    
    @Test
    fun `state transitions maintain consistency`() {
        // Arrange
        val intentId = "intent-flow"
        
        // Act & Assert - Full flow
        reducer.markProcessing(intentId)
        assertTrue(reducer.state.value is PaymentStatusReducer.PaymentStatusState.Processing)
        
        reducer.updateStatus(PaymentStatus.PENDING, intentId)
        assertTrue(reducer.state.value is PaymentStatusReducer.PaymentStatusState.StatusUpdate)
        
        reducer.updateStatus(PaymentStatus.CONFIRMED, intentId)
        assertTrue(reducer.state.value is PaymentStatusReducer.PaymentStatusState.StatusUpdate)
        
        reducer.markCompleted(intentId, PaymentStatus.CONFIRMED)
        assertTrue(reducer.state.value is PaymentStatusReducer.PaymentStatusState.Completed)
    }
    
    @Test
    fun `updateStatus with different intentId updates state`() {
        // Arrange
        val intentId1 = "intent-001"
        val intentId2 = "intent-002"
        val status = PaymentStatus.PENDING
        
        // Act
        reducer.updateStatus(status, intentId1, 1000L)
        val state1 = reducer.state.value
        
        reducer.updateStatus(status, intentId2, 2000L)
        val state2 = reducer.state.value
        
        // Assert - should update even though status is same, because intentId differs
        assertTrue(state1 !== state2)
        assertEquals(intentId2, (state2 as PaymentStatusReducer.PaymentStatusState.StatusUpdate).intentId)
    }
}
