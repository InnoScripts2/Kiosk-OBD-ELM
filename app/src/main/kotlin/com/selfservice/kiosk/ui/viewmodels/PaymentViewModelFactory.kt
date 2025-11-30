package com.selfservice.kiosk.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.selfservice.feature.payments.PaymentModule

/**
 * Простая фабрика для [PaymentViewModel], позволяющая прокинуть [PaymentModule]
 * без DI-фреймворка, пока киоск постепенно мигрирует на Compose.
 */
class PaymentViewModelFactory(
    private val paymentModule: PaymentModule
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PaymentViewModel::class.java)) {
            return PaymentViewModel(paymentModule) as T
        }
        throw IllegalArgumentException("Unsupported ViewModel: ${modelClass.name}")
    }
}
