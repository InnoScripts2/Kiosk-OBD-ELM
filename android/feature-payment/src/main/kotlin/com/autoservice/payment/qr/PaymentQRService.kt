package com.autoservice.payment.qr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PaymentQRService(
    private val qrGenerator: QRGenerator = QRGenerator()
) {
    suspend fun createPaymentCode(amountRubles: Int, orderId: String): PaymentQrModel = withContext(Dispatchers.Default) {
        val payload = "autoservice://payment?amount=$amountRubles&orderId=$orderId"
        val bitmap = qrGenerator.generateBitmap(payload)
        PaymentQrModel(
            orderId = orderId,
            amountRubles = amountRubles,
            qrBitmap = bitmap,
            payload = payload
        )
    }
}

data class PaymentQrModel(
    val orderId: String,
    val amountRubles: Int,
    val qrBitmap: android.graphics.Bitmap,
    val payload: String
)
