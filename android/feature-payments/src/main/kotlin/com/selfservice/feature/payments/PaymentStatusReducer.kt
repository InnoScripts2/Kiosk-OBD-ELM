package com.selfservice.feature.payments

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Reduces payment status updates into a single state flow
 * 
 * Prevents double emissions and manages state transitions according to
 * plan-payments-reports.md specifications.
 * 
 * This component implements a state machine for payment status to ensure:
 * - No duplicate status emissions
 * - Proper state transitions
 * - Idempotent updates
 */
class PaymentStatusReducer {
    private val _state = MutableStateFlow<PaymentStatusState>(PaymentStatusState.Initial)
    val state: StateFlow<PaymentStatusState> = _state.asStateFlow()
    
    /**
     * Updates the payment status
     * 
     * Only emits if the status actually changes to prevent duplicate emissions.
     * 
     * @param status New payment status
     * @param intentId Payment intent ID
     * @param timestamp Timestamp of the update
     */
    fun updateStatus(status: PaymentStatus, intentId: String, timestamp: Long = System.currentTimeMillis()) {
        _state.update { currentState ->
            // Prevent duplicate emissions
            if (currentState is PaymentStatusState.StatusUpdate &&
                currentState.status == status &&
                currentState.intentId == intentId) {
                return@update currentState
            }
            
            // Create new state
            PaymentStatusState.StatusUpdate(
                status = status,
                intentId = intentId,
                timestamp = timestamp,
                previousStatus = (currentState as? PaymentStatusState.StatusUpdate)?.status
            )
        }
    }
    
    /**
     * Marks status as processing
     */
    fun markProcessing(intentId: String) {
        _state.update {
            PaymentStatusState.Processing(
                intentId = intentId,
                timestamp = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Marks status as completed
     */
    fun markCompleted(intentId: String, finalStatus: PaymentStatus) {
        _state.update {
            PaymentStatusState.Completed(
                intentId = intentId,
                finalStatus = finalStatus,
                timestamp = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Marks status as timed out
     */
    fun markTimedOut(intentId: String, elapsedMs: Long) {
        _state.update {
            PaymentStatusState.TimedOut(
                intentId = intentId,
                elapsedMs = elapsedMs,
                timestamp = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Marks status as failed
     */
    fun markFailed(intentId: String, error: String) {
        _state.update {
            PaymentStatusState.Failed(
                intentId = intentId,
                error = error,
                timestamp = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Resets to initial state
     */
    fun reset() {
        _state.value = PaymentStatusState.Initial
    }
    
    /**
     * Payment status state
     */
    sealed class PaymentStatusState {
        /** Initial state before any payment */
        object Initial : PaymentStatusState()
        
        /** Processing payment */
        data class Processing(
            val intentId: String,
            val timestamp: Long
        ) : PaymentStatusState()
        
        /** Status update received */
        data class StatusUpdate(
            val status: PaymentStatus,
            val intentId: String,
            val timestamp: Long,
            val previousStatus: PaymentStatus?
        ) : PaymentStatusState()
        
        /** Payment completed successfully */
        data class Completed(
            val intentId: String,
            val finalStatus: PaymentStatus,
            val timestamp: Long
        ) : PaymentStatusState()
        
        /** Payment timed out */
        data class TimedOut(
            val intentId: String,
            val elapsedMs: Long,
            val timestamp: Long
        ) : PaymentStatusState()
        
        /** Payment failed */
        data class Failed(
            val intentId: String,
            val error: String,
            val timestamp: Long
        ) : PaymentStatusState()
    }
}
