package com.selfservice.kiosk.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selfservice.feature.payments.*
import com.selfservice.kiosk.BuildConfig
import com.selfservice.kiosk.ui.state.PaymentStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel for payment processing
 * 
 * Manages payment intent creation, status polling, and timeout handling.
 * Integrates with feature-payments module and handles DEV/QA/PROD modes.
 * 
 * Key features:
 * - Creates payment intents through PaymentModule
 * - Polls status with 10-minute timeout
 * - Prevents double emissions via PaymentStatusReducer
 * - Handles webhook callbacks
 * - Manages button locking when PAYMENT_MOCK=false
 */
class PaymentViewModel(
    private val paymentModule: PaymentModule,
) : ViewModel() {
    
    private val statusReducer = PaymentStatusReducer()
    private var currentPoller: PaymentStatusPoller? = null
    private var pollerObserverJob: Job? = null
    private var remainingTimeJob: Job? = null
    
    // StateFlow with replay=1 (MutableStateFlow default) for deterministic state updates
    // UI subscribers always receive the latest state immediately upon collection
    private val _paymentState = MutableStateFlow<PaymentUiState>(PaymentUiState.Idle)
    val paymentState: StateFlow<PaymentUiState> = _paymentState.asStateFlow()
    
    private val _qrCode = MutableStateFlow<PaymentQrCodeData?>(null)
    val qrCode: StateFlow<PaymentQrCodeData?> = _qrCode.asStateFlow()
    
    private val _remainingTime = MutableStateFlow<Long?>(null)
    val remainingTime: StateFlow<Long?> = _remainingTime.asStateFlow()
    
    init {
        // Observe status reducer state changes
        viewModelScope.launch {
            statusReducer.state.collect { reducerState ->
                handleReducerStateChange(reducerState)
            }
        }
    }
    
    /**
     * Creates a payment intent and starts polling
     * 
     * @param sessionId Current session ID
     * @param amount Payment amount in kopecks/cents
     * @param serviceType Service type ("thickness" or "obd")
     * @param contact Customer contact information
     */
    fun createPaymentIntent(
        sessionId: String,
        amount: Long,
        serviceType: String,
        contact: PaymentContact? = null
    ) {
        if (_paymentState.value is PaymentUiState.Processing) {
            Timber.w("[PaymentViewModel] Already processing payment, ignoring duplicate request")
            return
        }
        
        viewModelScope.launch {
            try {
                _paymentState.value = PaymentUiState.CreatingIntent
                
                val result = paymentModule.createIntent(
                    CreatePaymentIntentInput(
                        amount = amount,
                        currency = "RUB",
                        sessionId = sessionId,
                        serviceType = serviceType,
                        contact = contact
                    )
                )
                
                val intent = result.intent
                _qrCode.value = intent.qrCode?.let { qr ->
                    PaymentQrCodeData(
                        data = qr.data ?: "",
                        url = qr.url
                    )
                }
                
                _paymentState.value = PaymentUiState.Processing(
                    intentId = intent.id,
                    amount = amount,
                    currency = intent.currency
                )
                
                statusReducer.markProcessing(intent.id)
                
                // Start polling for status updates
                startPolling(intent.id)
                
                Timber.d("[PaymentViewModel] Intent created: ${intent.id}")
            } catch (e: Exception) {
                Timber.e(e, "[PaymentViewModel] Failed to create intent")
                _paymentState.value = PaymentUiState.Error(
                    message = e.message ?: "Failed to create payment intent"
                )
                statusReducer.markFailed("", e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Confirms payment in DEV mode
     * 
     * This is only available when BuildConfig.PAYMENT_MOCK == true
     */
    fun confirmPaymentDev(intentId: String) {
        if (!isDevMode()) {
            Timber.w("[PaymentViewModel] confirmPaymentDev called in non-DEV mode")
            return
        }
        
        viewModelScope.launch {
            try {
                val result = paymentModule.confirmDev(intentId)
                if (result != null) {
                    statusReducer.markCompleted(intentId, result.intent.status)
                    Timber.d("[PaymentViewModel] DEV confirmation successful")
                }
            } catch (e: Exception) {
                Timber.e(e, "[PaymentViewModel] DEV confirmation failed")
                statusReducer.markFailed(intentId, e.message ?: "Confirmation failed")
            }
        }
    }
    
    /**
     * Cancels current payment
     */
    fun cancelPayment() {
        pollerObserverJob?.cancel()
        pollerObserverJob = null
        remainingTimeJob?.cancel()
        remainingTimeJob = null
        currentPoller?.stopPolling()
        currentPoller = null
        _paymentState.value = PaymentUiState.Cancelled
        _qrCode.value = null
        _remainingTime.value = null
        statusReducer.reset()
        Timber.d("[PaymentViewModel] Payment cancelled")
    }
    
    /**
     * Resets payment state to idle
     */
    fun reset() {
        cancelPayment()
        _paymentState.value = PaymentUiState.Idle
        Timber.d("[PaymentViewModel] Payment reset")
    }
    
    /**
     * Gets current payment status from module
     */
    suspend fun refreshStatus(intentId: String): com.selfservice.feature.payments.PaymentStatus? {
        return try {
            paymentModule.getStatus(intentId)
        } catch (e: Exception) {
            Timber.e(e, "[PaymentViewModel] Failed to refresh status")
            null
        }
    }
    
    /**
     * Checks if running in DEV mode with mocked payments
     */
    fun isDevMode(): Boolean {
        return BuildConfig.PAYMENT_MOCK
    }
    
    /**
     * Starts polling for payment status
     */
    private fun startPolling(intentId: String) {
        // Cancel previous poller and observers
        pollerObserverJob?.cancel()
        remainingTimeJob?.cancel()
        currentPoller?.stopPolling()
        
        val poller = PaymentStatusPoller(
            statusProvider = paymentModule,
            intentId = intentId,
            scope = viewModelScope
        )
        
        currentPoller = poller
        
        // Observe poller state changes
        pollerObserverJob = viewModelScope.launch {
            poller.pollingState.collect { pollingState ->
                handlePollingStateChange(pollingState)
            }
        }
        
        // Start the actual polling
        poller.startPolling()
        
        // Update remaining time periodically
        remainingTimeJob = viewModelScope.launch {
            while (poller.pollingState.value is PaymentStatusPoller.PollingState.Polling) {
                _remainingTime.value = poller.getRemainingTimeMs()
                kotlinx.coroutines.delay(1000) // Update every second
            }
            _remainingTime.value = null
        }
    }
    
    /**
     * Handles reducer state changes
     */
    private fun handleReducerStateChange(state: PaymentStatusReducer.PaymentStatusState) {
        when (state) {
            is PaymentStatusReducer.PaymentStatusState.Completed -> {
                _paymentState.value = PaymentUiState.Completed(
                    intentId = state.intentId,
                    timestamp = state.timestamp
                )
            }
            is PaymentStatusReducer.PaymentStatusState.TimedOut -> {
                _paymentState.value = PaymentUiState.TimedOut(
                    intentId = state.intentId,
                    elapsedMs = state.elapsedMs
                )
            }
            is PaymentStatusReducer.PaymentStatusState.Failed -> {
                _paymentState.value = PaymentUiState.Error(
                    message = state.error
                )
            }
            else -> {
                // Other states handled by poller
            }
        }
    }
    
    /**
     * Handles polling state changes
     */
    private fun handlePollingStateChange(state: PaymentStatusPoller.PollingState) {
        when (state) {
            is PaymentStatusPoller.PollingState.Completed -> {
                statusReducer.markCompleted(
                    intentId = (_paymentState.value as? PaymentUiState.Processing)?.intentId ?: "",
                    finalStatus = state.finalStatus
                )
            }
            is PaymentStatusPoller.PollingState.TimedOut -> {
                statusReducer.markTimedOut(
                    intentId = (_paymentState.value as? PaymentUiState.Processing)?.intentId ?: "",
                    elapsedMs = state.elapsedMs
                )
            }
            is PaymentStatusPoller.PollingState.Error -> {
                statusReducer.markFailed(
                    intentId = (_paymentState.value as? PaymentUiState.Processing)?.intentId ?: "",
                    error = state.message
                )
            }
            else -> {
                // Other states don't require action
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        pollerObserverJob?.cancel()
        remainingTimeJob?.cancel()
        currentPoller?.stopPolling()
        Timber.d("[PaymentViewModel] ViewModel cleared")
    }
}

/**
 * UI state for payment screen
 */
sealed class PaymentUiState {
    /** Idle, no payment in progress */
    object Idle : PaymentUiState()
    
    /** Creating payment intent */
    object CreatingIntent : PaymentUiState()
    
    /** Processing payment, waiting for confirmation */
    data class Processing(
        val intentId: String,
        val amount: Long,
        val currency: String
    ) : PaymentUiState()
    
    /** Payment completed successfully */
    data class Completed(
        val intentId: String,
        val timestamp: Long
    ) : PaymentUiState()
    
    /** Payment timed out after 10 minutes */
    data class TimedOut(
        val intentId: String,
        val elapsedMs: Long
    ) : PaymentUiState()
    
    /** Payment cancelled by user */
    object Cancelled : PaymentUiState()
    
    /** Error occurred */
    data class Error(
        val message: String
    ) : PaymentUiState()
}

/**
 * QR code data for display
 */
data class PaymentQrCodeData(
    val data: String,
    val url: String?
)

