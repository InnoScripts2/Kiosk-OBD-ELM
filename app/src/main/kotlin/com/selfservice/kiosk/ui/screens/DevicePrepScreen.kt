package com.selfservice.kiosk.ui.screens

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfservice.kiosk.ui.viewmodels.ObdFlowViewModel
import com.selfservice.kiosk.ui.viewmodels.ThicknessFlowViewModel
import com.selfservice.platform.ui.components.KioskPanel
import com.selfservice.platform.ui.foundation.KioskTokens
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun DevicePrepScreen(
    deviceName: String,
    onReady: () -> Unit,
    targetProgressDurationMs: Long = 4_500,
    completionHoldMs: Long = 600
) {
    var progress by remember { mutableStateOf(0f) }
    val spacing = KioskTokens.spacing
    val background = KioskTokens.gradients.hero
    val checklist = remember(deviceName) { devicePrepChecklist(deviceName) }

    LaunchedEffect(deviceName, targetProgressDurationMs, completionHoldMs) {
        val perStepDelay = (targetProgressDurationMs / 100).coerceAtLeast(10)
        for (i in 1..100) {
            delay(perStepDelay)
            progress = i / 100f
        }
        if (completionHoldMs > 0) {
            delay(completionHoldMs)
        }
        onReady()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .testTag("device-prep-screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 1440.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.xxl, vertical = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.xxl)
        ) {
            DevicePrepHeader(deviceName = deviceName)
            DevicePrepMetaGrid(deviceName = deviceName, progress = progress)
            DevicePrepStatusRow(progress = progress)

            KioskPanel(
                headline = "Что происходит",
                supportingText = "Последовательно готовим устройство, чтобы выдача прошла безопасно"
            ) {
                DevicePrepChecklist(checklist = checklist)
            }
        }
    }
}

@Composable
@VisibleForTesting
internal fun DevicePrepProgressBlock(progress: Float, modifier: Modifier = Modifier) {
    val spacing = KioskTokens.spacing
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 10.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(
                        text = "Готовность оборудования",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Контролируем питание и BLE-сессию",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${(progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("device-prep-progress-value"),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(spacing.sm)
                    .testTag("device-prep-progress-bar"),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SlotInstructionBanner(isReady: Boolean, modifier: Modifier = Modifier) {
    val spacing = KioskTokens.spacing
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = if (isReady) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        tonalElevation = 6.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xl, vertical = spacing.md)
                .testTag("device-prep-slot-banner"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isReady) {
                    "Слот открыт. Аккуратно достаньте устройство и закройте крышку."
                } else {
                    "Подготавливаем слот выдачи. Не открывайте отсек до завершения 100%."
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DevicePrepHeader(deviceName: String) {
    val spacing = KioskTokens.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(
            text = "Подготавливаем $deviceName",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            text = "Проверяем питание, BLE-сессию и управляем слотом выдачи. Процесс занимает до 5 секунд.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f),
            lineHeight = 28.sp
        )
    }
}

@Composable
private fun DevicePrepStatusRow(progress: Float) {
    val spacing = KioskTokens.spacing
    val isCompactLayout = LocalConfiguration.current.screenWidthDp < 980
    val isReady = progress >= 1f
    if (isCompactLayout) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            DevicePrepProgressBlock(progress = progress, modifier = Modifier.fillMaxWidth())
            SlotInstructionBanner(isReady = isReady, modifier = Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DevicePrepProgressBlock(progress = progress, modifier = Modifier.weight(1f))
            SlotInstructionBanner(isReady = isReady, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DevicePrepMetaGrid(deviceName: String, progress: Float) {
    val spacing = KioskTokens.spacing
    val slotLabel = if (progress >= 1f) "Слот открыт" else "Слот готовится"
    val entries = remember(deviceName, progress) {
        listOf(
            DevicePrepMeta(
                label = "Устройство",
                value = deviceName,
                supporting = "Толщиномер BLE, контроль питания"
            ),
            DevicePrepMeta(
                label = "Готовность",
                value = "${(progress * 100).roundToInt()}%",
                supporting = "Обновляем статус раз в 50 мс"
            ),
            DevicePrepMeta(
                label = "Статус слота",
                value = slotLabel,
                supporting = "Откроется автоматически на 100%"
            ),
            DevicePrepMeta(
                label = "Время подготовки",
                value = "≤ 5 сек",
                supporting = "Логируем каждую операцию"
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
            DevicePrepMetaCard(entry = entry)
        }
    }
}

@Composable
private fun DevicePrepMetaCard(entry: DevicePrepMeta) {
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
private fun DevicePrepChecklist(checklist: List<DevicePrepStep>) {
    val spacing = KioskTokens.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        checklist.forEachIndexed { index, step ->
            DevicePrepStepItem(index = index + 1, step = step)
        }
    }
}

@Composable
private fun DevicePrepStepItem(index: Int, step: DevicePrepStep) {
    val spacing = KioskTokens.spacing
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            ) {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private data class DevicePrepMeta(val label: String, val value: String, val supporting: String)

private data class DevicePrepStep(val title: String, val description: String)

private fun devicePrepChecklist(deviceName: String): List<DevicePrepStep> = listOf(
    DevicePrepStep(
        title = "Проверяем питание",
        description = "Контроллер убеждается, что ${deviceName.lowercase()} заряжен и готов к работе."
    ),
    DevicePrepStep(
        title = "Инициализируем подключение",
        description = "Запускаем Bluetooth-пару и тестируем канал связи (до 5 попыток)."
    ),
    DevicePrepStep(
        title = "Открываем слот выдачи",
        description = "Управляем замком терминала и фиксируем событие в журнале."
    )
)

@Composable
fun ThicknessPrepScreen(
    viewModel: ThicknessFlowViewModel,
    onReady: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.connectDevice()
    }
    DevicePrepScreen(
        deviceName = "толщиномер",
        onReady = onReady
    )
}

@Composable
fun ObdPrepScreen(
    viewModel: ObdFlowViewModel,
    onReady: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.connectAdapter()
    }
    DevicePrepScreen(
        deviceName = "OBD-адаптер",
        onReady = onReady,
        targetProgressDurationMs = 3_000,
        completionHoldMs = 0
    )
}
