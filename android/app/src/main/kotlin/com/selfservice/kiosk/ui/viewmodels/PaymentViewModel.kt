package com.selfservice.kiosk.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selfservice.kiosk.ui.state.PaymentStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * ViewModel for payment processing
 * Manages payment intent creation and status
 */
class PaymentViewModel : ViewModel() {
    
    private val _paymentState = MutableStateFlow<PaymentStatus>(PaymentStatus.Pending)
    val paymentState: StateFlow<PaymentStatus> = _paymentState.asStateFlow()
    
    private val _qrCode = MutableStateFlow<String?>(null)
    val qrCode: StateFlow<String?> = _qrCode.asStateFlow()
    
    /**
     * Create payment intent and generate QR code
     */
    fun createPaymentIntent(sessionId: String, amount: Int) {
        viewModelScope.launch {
            // Generate intent ID
            val intentId = "intent_${System.currentTimeMillis()}_${sessionId.take(8)}"
            
            // Update status to processing
            _paymentState.value = PaymentStatus.Processing(intentId)
            
            // Generate QR code URL (mock in DEV mode)
            val qrUrl = "https://payment.example.com/qr/$intentId?amount=$amount"
            _qrCode.value = qrUrl
            
            // In DEV mode, simulate payment confirmation after delay
            if (isDevMode()) {
                delay(5000) // 5 seconds delay
                confirmPayment(intentId)
            }
        }
    }
    
    /**
     * Confirm payment (webhook callback)
     */
    fun confirmPayment(intentId: String) {
        _paymentState.value = PaymentStatus.Completed(intentId, System.currentTimeMillis())
    }
    
    /**
     * Cancel payment
     */
    fun cancelPayment() {
        _paymentState.value = PaymentStatus.Pending
        _qrCode.value = null
    }
    
    /**
     * Reset payment state
     */
    fun reset() {
        _paymentState.value = PaymentStatus.Pending
        _qrCode.value = null
    }
    
    private fun isDevMode(): Boolean {
        // Check BuildConfig or environment variable
        return true // Placeholder
    }
}

