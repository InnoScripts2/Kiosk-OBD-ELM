package com.autoservice.payment.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.autoservice.payment.qr.PaymentQRService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun QRDisplayScreen(
    amountRubles: Int,
    orderId: String,
    modifier: Modifier = Modifier,
    qrService: PaymentQRService = PaymentQRService(),
    onShare: (String) -> Unit = {}
) {
    val qrModelState = remember { mutableStateOf<com.autoservice.payment.qr.PaymentQrModel?>(null) }

    LaunchedEffect(amountRubles, orderId) {
        qrModelState.value = withContext(Dispatchers.Default) {
            qrService.createPaymentCode(amountRubles, orderId)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Сумма: $amountRubles ₽", modifier = Modifier.align(Alignment.CenterHorizontally))
        qrModelState.value?.let { model ->
            Image(
                bitmap = model.qrBitmap.asImageBitmap(),
                contentDescription = "QR для оплаты",
                modifier = Modifier.size(240.dp)
            )
            Button(onClick = { onShare(model.payload) }) {
                Text("Поделиться ссылкой")
            }
        } ?: Text("Генерация QR...")
    }
}
