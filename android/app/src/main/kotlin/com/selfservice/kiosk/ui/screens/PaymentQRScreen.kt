package com.selfservice.kiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.selfservice.kiosk.BuildConfig
import com.selfservice.kiosk.ui.viewmodels.PaymentUiState
import com.selfservice.kiosk.ui.viewmodels.PaymentViewModel
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@Composable
fun PaymentQRScreen(
    viewModel: PaymentViewModel,
    amount: Int,
    onPaymentComplete: () -> Unit,
    onCancel: () -> Unit = {},
    onTimeout: () -> Unit = {}
) {
    val paymentState by viewModel.paymentState.collectAsState()
    val qrCode by viewModel.qrCode.collectAsState()
    val remainingTime by viewModel.remainingTime.collectAsState()
    
    LaunchedEffect(paymentState) {
        when (paymentState) {
            is PaymentUiState.Completed -> {
                delay(1000)
                onPaymentComplete()
            }
            is PaymentUiState.TimedOut -> {
                delay(2000)
                onTimeout()
            }
            else -> {}
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (val state = paymentState) {
            is PaymentUiState.Idle, 
            is PaymentUiState.CreatingIntent -> {
                LoadingScreen("Подготовка оплаты...")
            }
            
            is PaymentUiState.Processing -> {
                ProcessingPaymentContent(
                    amount = amount,
                    qrCode = qrCode,
                    remainingTime = remainingTime,
                    intentId = state.intentId,
                    onCancel = {
                        viewModel.cancelPayment()
                        onCancel()
                    },
                    onDevConfirm = if (BuildConfig.PAYMENT_MOCK) {
                        { viewModel.confirmPaymentDev(state.intentId) }
                    } else null
                )
            }
            
            is PaymentUiState.Completed -> {
                SuccessScreen("Оплата подтверждена!")
            }
            
            is PaymentUiState.TimedOut -> {
                ErrorScreen(
                    message = "Время ожидания оплаты истекло (10 минут)",
                    onRetry = onCancel
                )
            }
            
            is PaymentUiState.Cancelled -> {
                ErrorScreen(
                    message = "Оплата отменена",
                    onRetry = onCancel
                )
            }
            
            is PaymentUiState.Error -> {
                ErrorScreen(
                    message = state.message,
                    onRetry = onCancel
                )
            }
        }
    }
}

@Composable
private fun ProcessingPaymentContent(
    amount: Int,
    qrCode: com.selfservice.kiosk.ui.viewmodels.PaymentQrCodeData?,
    remainingTime: Long?,
    intentId: String,
    onCancel: () -> Unit,
    onDevConfirm: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Оплата услуги",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Text(
            text = "Сумма: $amount₽",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        qrCode?.let { qr ->
            Card(
                modifier = Modifier.size(320.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.size(280.dp),
                        color = Color.White,
                        tonalElevation = 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "QR КОД\n${qr.data.take(40)}...",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
        
        Text(
            text = "Отсканируйте QR-код для оплаты",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        remainingTime?.let { remaining ->
            val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
            val isLowTime = remaining < 2 * 60 * 1000
            
            LinearProgressIndicator(
                progress = remaining.toFloat() / (10 * 60 * 1000),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp)
                    .height(8.dp),
                color = if (isLowTime) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            
            Text(
                text = "Осталось времени: ${minutes}:${seconds.toString().padStart(2, '0')}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isLowTime) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onBackground
                }
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text("Отмена", style = MaterialTheme.typography.titleMedium)
            }
            
            if (onDevConfirm != null) {
                Button(
                    onClick = onDevConfirm,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFC857) // Warning yellow for DEV
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "[DEV MODE]",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Black
                        )
                        Text(
                            text = "Подтвердить",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Black
                        )
                    }
                }
            }
        }
        
        if (BuildConfig.PAYMENT_MOCK) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text(
                    text = "[MOCK MODE] Режим разработки",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(64.dp))
            Text(text = message, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun SuccessScreen(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                modifier = Modifier.size(96.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(text = message, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                modifier = Modifier.size(96.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "✗",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Text(text = message, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(0.6f).height(56.dp)
            ) {
                Text("Вернуться", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
