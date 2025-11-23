package com.selfservice.kiosk.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.selfservice.kiosk.ui.viewmodels.PaymentViewModel
import com.selfservice.kiosk.ui.state.PaymentStatus

@Composable
fun PaymentQRScreen(
    viewModel: PaymentViewModel,
    amount: Int,
    onPaymentComplete: () -> Unit
) {
    val paymentState by viewModel.paymentState.collectAsState()
    val qrCode by viewModel.qrCode.collectAsState()
    
    when (val state = paymentState) {
        is PaymentStatus.Completed -> {
            onPaymentComplete()
        }
        else -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "Оплата услуги",
                    style = MaterialTheme.typography.headlineLarge
                )
                
                Text(
                    text = "Сумма: $amount₽",
                    style = MaterialTheme.typography.headlineMedium
                )
                
                qrCode?.let { qr ->
                    // QR Code placeholder
                    Surface(
                        modifier = Modifier.size(300.dp),
                        tonalElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("QR CODE
$qr", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                
                Text(
                    text = "Отсканируйте QR-код для оплаты",
                    style = MaterialTheme.typography.bodyLarge
                )
                
                if (state is PaymentStatus.Processing) {
                    CircularProgressIndicator()
                    Text("Ожидание оплаты...")
                }
            }
        }
    }
}
