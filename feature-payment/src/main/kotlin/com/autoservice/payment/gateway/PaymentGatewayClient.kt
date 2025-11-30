package com.autoservice.payment.gateway

import kotlinx.coroutines.delay

class PaymentGatewayClient {
    suspend fun confirmPayment(orderId: String): Boolean {
        delay(500)
        return true
    }
}
