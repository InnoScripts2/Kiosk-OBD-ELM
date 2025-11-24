package com.selfservice.feature.payments

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

/**
 * Polls payment status periodically and manages timeout
 * 
 * This component polls the payment module for intent status updates and
 * handles the 10-minute timeout as specified in plan-payments-reports.md.
 * 
 * @property paymentModule The payment module to poll
 * @property intentId The payment intent ID to monitor
 * @property scope Coroutine scope for polling operations
 * @property clock Clock for timeout calculations
 * @property pollIntervalMs Polling interval in milliseconds (default 2 seconds)
 * @property timeoutMs Timeout in milliseconds (default 10 minutes)
 */
class PaymentStatusPoller(
    private val paymentModule: PaymentModule,
    private val intentId: String,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
    private val pollIntervalMs: Long = 2_000L,
    private val timeoutMs: Long = 10 * 60 * 1000L, // 10 minutes
) {
    private val _pollingState = MutableStateFlow<PollingState>(PollingState.Idle)
    val pollingState: StateFlow<PollingState> = _pollingState.asStateFlow()
    
    private val _currentStatus = MutableStateFlow<PaymentStatus?>(null)
    val currentStatus: StateFlow<PaymentStatus?> = _currentStatus.asStateFlow()
    
    private var pollingJob: Job? = null
    private var startTime: Instant? = null
    
    /**
     * Starts polling for payment status
     * 
     * Polls every [pollIntervalMs] milliseconds until:
     * - Status becomes CONFIRMED or MANUAL
     * - Timeout of [timeoutMs] is reached
     * - Polling is explicitly stopped
     */
    fun startPolling() {
        if (pollingJob?.isActive == true) {
            return // Already polling
        }
        
        startTime = Instant.now(clock)
        _pollingState.value = PollingState.Polling
        
        pollingJob = scope.launch {
            while (isActive) {
                try {
                    // Check timeout
                    val elapsed = Instant.now(clock).toEpochMilli() - (startTime?.toEpochMilli() ?: 0)
                    if (elapsed >= timeoutMs) {
                        _pollingState.value = PollingState.TimedOut(elapsed)
                        break
                    }
                    
                    // Poll status
                    val status = paymentModule.getStatus(intentId)
                    _currentStatus.value = status
                    
                    // Check if terminal state reached
                    if (status == PaymentStatus.CONFIRMED || status == PaymentStatus.MANUAL) {
                        _pollingState.value = PollingState.Completed(status)
                        break
                    }
                    
                    if (status == PaymentStatus.FAILED || status == PaymentStatus.EXPIRED) {
                        _pollingState.value = PollingState.Failed(status)
                        break
                    }
                    
                    // Wait before next poll
                    delay(pollIntervalMs)
                } catch (e: Exception) {
                    _pollingState.value = PollingState.Error(e.message ?: "Unknown error")
                    break
                }
            }
        }
    }
    
    /**
     * Stops polling
     */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        if (_pollingState.value is PollingState.Polling) {
            _pollingState.value = PollingState.Stopped
        }
    }
    
    /**
     * Gets remaining time until timeout in milliseconds
     * 
     * @return Remaining time in milliseconds, or null if not polling
     */
    fun getRemainingTimeMs(): Long? {
        val start = startTime ?: return null
        val elapsed = Instant.now(clock).toEpochMilli() - start.toEpochMilli()
        val remaining = timeoutMs - elapsed
        return if (remaining > 0) remaining else 0
    }
    
    /**
     * Checks if timeout has been reached
     * 
     * @return true if timeout reached, false otherwise
     */
    fun isTimedOut(): Boolean {
        val remaining = getRemainingTimeMs()
        return remaining != null && remaining == 0L
    }
    
    /**
     * State of the polling operation
     */
    sealed class PollingState {
        /** Idle, not polling */
        object Idle : PollingState()
        
        /** Currently polling */
        object Polling : PollingState()
        
        /** Polling completed successfully */
        data class Completed(val finalStatus: PaymentStatus) : PollingState()
        
        /** Polling failed */
        data class Failed(val finalStatus: PaymentStatus) : PollingState()
        
        /** Polling timed out after 10 minutes */
        data class TimedOut(val elapsedMs: Long) : PollingState()
        
        /** Polling stopped manually */
        object Stopped : PollingState()
        
        /** Error occurred during polling */
        data class Error(val message: String) : PollingState()
    }
}
