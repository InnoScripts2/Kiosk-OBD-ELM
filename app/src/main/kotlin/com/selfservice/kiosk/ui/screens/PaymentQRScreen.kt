package com.selfservice.kiosk.ui.screens

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import com.selfservice.kiosk.BuildConfig
import com.selfservice.kiosk.ui.viewmodels.PaymentQrCodeData
import com.selfservice.kiosk.ui.viewmodels.PaymentUiState
import com.selfservice.kiosk.ui.viewmodels.PaymentViewModel
import com.selfservice.platform.ui.components.KioskPanel
import com.selfservice.platform.ui.components.KioskPrimaryButton
import com.selfservice.platform.ui.components.KioskSecondaryButton
import com.selfservice.platform.ui.foundation.KioskTokens
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

private const val PAYMENT_TIMEOUT_MS = 10 * 60 * 1000L

@Composable
fun PaymentQRScreen(
    viewModel: PaymentViewModel,
    amount: Int,
    onPaymentComplete: () -> Unit,
    onCancel: () -> Unit = {},
    onTimeout: () -> Unit = {},
    title: String = "Оплата услуги",
    subtitle: String = "Сканируйте QR-код через приложение банка. Сессия автоматически завершится через 10 минут."
) {
    val paymentState by viewModel.paymentState.collectAsState()
    val qrCode by viewModel.qrCode.collectAsState()
    val remainingTime by viewModel.remainingTime.collectAsState()
    val spacing = KioskTokens.spacing
    val background = KioskTokens.gradients.hero

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
            else -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        when (val state = paymentState) {
            is PaymentUiState.Processing -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
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
                        } else null,
                        title = title,
                        subtitle = subtitle
                    )
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = spacing.xxl, vertical = spacing.xl),
                    contentAlignment = Alignment.Center
                ) {
                    when (state) {
                        is PaymentUiState.Idle,
                        is PaymentUiState.CreatingIntent -> LoadingScreen("Подготовка оплаты...")

                        is PaymentUiState.Completed -> SuccessScreen("Оплата подтверждена!")

                        is PaymentUiState.TimedOut -> ErrorScreen(
                            message = "Время ожидания оплаты истекло (10 минут)",
                            onRetry = onCancel
                        )

                        is PaymentUiState.Cancelled -> ErrorScreen(
                            message = "Оплата отменена",
                            onRetry = onCancel
                        )

                        is PaymentUiState.Error -> ErrorScreen(
                            message = state.message,
                            onRetry = onCancel
                        )

                        is PaymentUiState.Processing -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessingPaymentContent(
    amount: Int,
    qrCode: PaymentQrCodeData?,
    remainingTime: Long?,
    intentId: String,
    onCancel: () -> Unit,
    onDevConfirm: (() -> Unit)?,
    title: String,
    subtitle: String
) {
    val spacing = KioskTokens.spacing
    val scrollState = rememberScrollState()
    val isCompactLayout = LocalConfiguration.current.screenWidthDp < 1080

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 1440.dp)
            .verticalScroll(scrollState)
            .padding(horizontal = spacing.xxl, vertical = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.xxl)
    ) {
        PaymentHeader(title = title, subtitle = subtitle)
        PaymentMetaGrid(
            amount = amount,
            intentId = intentId,
            remainingTime = remainingTime
        )
        PaymentPanelsSection(
            amount = amount,
            intentId = intentId,
            remainingTime = remainingTime,
            qrCode = qrCode,
            isCompactLayout = isCompactLayout
        )
        PaymentActionsSection(
            onCancel = onCancel,
            onDevConfirm = onDevConfirm,
            isCompactLayout = isCompactLayout
        )
    }
}

@Composable
private fun PaymentHeader(
    title: String,
    subtitle: String
) {
    val spacing = KioskTokens.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
            lineHeight = 28.sp
        )
    }
}

@Composable
private fun PaymentPanelsSection(
    amount: Int,
    intentId: String,
    remainingTime: Long?,
    qrCode: PaymentQrCodeData?,
    isCompactLayout: Boolean
) {
    val spacing = KioskTokens.spacing
    if (isCompactLayout) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            PaymentSummaryPanel(
                amount = amount,
                intentId = intentId,
                remainingTime = remainingTime,
                modifier = Modifier.fillMaxWidth()
            )
            PaymentQrPanel(
                qrCode = qrCode,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.lg)
        ) {
            PaymentSummaryPanel(
                amount = amount,
                intentId = intentId,
                remainingTime = remainingTime,
                modifier = Modifier.weight(1f)
            )
            PaymentQrPanel(
                qrCode = qrCode,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PaymentActionsSection(
    onCancel: () -> Unit,
    onDevConfirm: (() -> Unit)?,
    isCompactLayout: Boolean
) {
    val spacing = KioskTokens.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        if (isCompactLayout) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                KioskSecondaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCancel
                ) {
                    Text("Отмена")
                }
                if (onDevConfirm != null) {
                    DevConfirmButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onDevConfirm
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                KioskSecondaryButton(
                    modifier = Modifier.weight(1f),
                    onClick = onCancel
                ) {
                    Text("Отмена")
                }
                if (onDevConfirm != null) {
                    DevConfirmButton(
                        modifier = Modifier.weight(1f),
                        onClick = onDevConfirm
                    )
                }
            }
        }

        if (BuildConfig.PAYMENT_MOCK) {
            DevMockBanner()
        }
    }
}

@Composable
private fun DevConfirmButton(
    modifier: Modifier,
    onClick: () -> Unit
) {
    KioskPrimaryButton(
        modifier = modifier,
        onClick = onClick
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "[DEV MODE]",
                style = MaterialTheme.typography.labelSmall
            )
            Text("Подтвердить")
        }
    }
}

@Composable
private fun PaymentMetaGrid(
    amount: Int,
    intentId: String,
    remainingTime: Long?
) {
    val spacing = KioskTokens.spacing
    val normalizedTime = remainingTime ?: PAYMENT_TIMEOUT_MS
    val entries = remember(amount, intentId, normalizedTime) {
        val statusValue = if (remainingTime != null && remainingTime < 120_000) {
            "Осталось < 2 мин"
        } else {
            "Ожидаем оплату"
        }
        listOf(
            PaymentMetaEntry(
                label = "Сумма",
                value = "${amount} ₽",
                supporting = "Фиксированная стоимость услуги"
            ),
            PaymentMetaEntry(
                label = "ID платежа",
                value = intentId.takeLast(8).uppercase(),
                supporting = "Используем последние символы"
            ),
            PaymentMetaEntry(
                label = "Время на оплату",
                value = formatDuration(normalizedTime),
                supporting = "Сессия завершается автоматически"
            ),
            PaymentMetaEntry(
                label = "Статус",
                value = statusValue,
                supporting = "Обновляем данные каждые 1 сек"
            )
        )
    }

    LazyVerticalGrid(
        modifier = Modifier.fillMaxWidth(),
        columns = GridCells.Adaptive(minSize = 260.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
        userScrollEnabled = false
    ) {
        items(entries, key = { it.label }) { entry ->
            PaymentMetaCard(entry)
        }
    }
}

@Composable
private fun PaymentMetaCard(entry: PaymentMetaEntry) {
    val spacing = KioskTokens.spacing
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = entry.value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = entry.supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PaymentSummaryPanel(
    amount: Int,
    intentId: String,
    remainingTime: Long?,
    modifier: Modifier = Modifier
) {
    val spacing = KioskTokens.spacing
    KioskPanel(
        modifier = modifier,
        headline = "Детали оплаты",
        supportingText = "ID платежа: $intentId"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "${amount} ₽",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm)
                )
            }
            Text(
                text = "Отсканируйте QR-код для оплаты",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (remainingTime != null) {
                PaymentCountdown(remainingTime)
            } else {
                Text(
                    text = "Ожидаем подтверждение от банка",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            PaymentGuidelines()
        }
    }
}

@VisibleForTesting
@Composable
internal fun PaymentCountdown(remaining: Long) {
    val spacing = KioskTokens.spacing
    val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
    val isLowTime = remaining < 2 * 60 * 1000

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        LinearProgressIndicator(
            progress = { (remaining.toFloat() / PAYMENT_TIMEOUT_MS).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(spacing.sm)
                .testTag("payment-countdown-progress"),
            color = if (isLowTime) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            text = "Осталось времени: ${minutes}:${seconds.toString().padStart(2, '0')}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isLowTime) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("payment-countdown-text")
        )
    }
}

@Composable
private fun PaymentQrPanel(
    qrCode: PaymentQrCodeData?,
    modifier: Modifier = Modifier
) {
    val spacing = KioskTokens.spacing
    KioskPanel(
        modifier = modifier,
        headline = "QR для оплаты",
        supportingText = "Сканируйте камерой или приложением банка"
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(spacing.lg),
                color = Color.White,
                shape = MaterialTheme.shapes.large,
                shadowElevation = 12.dp
            ) {
                PaymentQrPlaceholder(qrCode = qrCode)
            }
        }
    }
}


@Composable
private fun PaymentGuidelines() {
    val spacing = KioskTokens.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = "Как пройти оплату",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        listOf(
            "Откройте приложение банка и выберите сканер QR",
            "Проверьте сумму и подтвердите перевод",
            "Возвращайтесь к терминалу — статус обновится автоматически"
        ).forEach { step ->
            Text(
                text = "• $step",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PaymentQrPlaceholder(qrCode: PaymentQrCodeData?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = qrCode?.data?.take(60)?.let { "QR-КОД\n$it…" } ?: "QR-код генерируется…",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black,
            textAlign = TextAlign.Center
        )
        qrCode?.url?.let {
            Spacer(modifier = Modifier.height(KioskTokens.spacing.sm))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Black,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class PaymentMetaEntry(val label: String, val value: String, val supporting: String)

private fun formatDuration(remaining: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
private fun DevMockBanner() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = "[MOCK MODE] Режим разработки",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = KioskTokens.spacing.lg, vertical = KioskTokens.spacing.sm)
        )
    }
}

@Composable
private fun LoadingScreen(message: String) {
    val spacing = KioskTokens.spacing
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(72.dp))
        Spacer(modifier = Modifier.height(spacing.lg))
        Text(
            text = message,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SuccessScreen(message: String) {
    PaymentCenteredState(
        icon = "✓",
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        iconColor = MaterialTheme.colorScheme.primary,
        title = message
    )
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    PaymentCenteredState(
        icon = "✗",
        containerColor = MaterialTheme.colorScheme.errorContainer,
        iconColor = MaterialTheme.colorScheme.error,
        title = message,
        actionLabel = "Вернуться",
        onAction = onRetry
    )
}

@Composable
private fun PaymentCenteredState(
    icon: String,
    containerColor: Color,
    iconColor: Color,
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val spacing = KioskTokens.spacing
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = MaterialTheme.shapes.large,
            color = containerColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.displayLarge,
                    color = iconColor,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(modifier = Modifier.height(spacing.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        actionLabel?.let { label ->
            if (onAction != null) {
                Spacer(modifier = Modifier.height(spacing.lg))
                KioskPrimaryButton(onClick = onAction) {
                    Text(label)
                }
            }
        }
    }
}
