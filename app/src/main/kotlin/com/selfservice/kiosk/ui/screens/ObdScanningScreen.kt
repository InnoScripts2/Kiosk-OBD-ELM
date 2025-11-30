package com.selfservice.kiosk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.selfservice.kiosk.ui.state.AdapterStatus
import com.selfservice.kiosk.ui.viewmodels.ObdFlowViewModel
import com.selfservice.platform.ui.components.KioskPanel
import com.selfservice.platform.ui.components.KioskPrimaryButton
import com.selfservice.platform.ui.components.KioskSecondaryButton
import com.selfservice.platform.ui.foundation.KioskTokens
import kotlin.math.roundToInt

@Composable
fun ObdScanningScreen(
    viewModel: ObdFlowViewModel,
    onScanComplete: () -> Unit,
    onAbort: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val spacing = KioskTokens.spacing
    val background = KioskTokens.gradients.hero
    val scrollState = rememberScrollState()
    val adapterStatus = state.adapterStatus

    LaunchedEffect(Unit) {
        viewModel.startScan()
    }

    LaunchedEffect(adapterStatus) {
        if (adapterStatus is AdapterStatus.Complete) {
            onScanComplete()
        }
    }

    val estimatedRemaining = remember(state.scanProgress) {
        val remaining = ((1f - state.scanProgress).coerceIn(0f, 1f) * 90f).roundToInt()
        remaining.coerceAtLeast(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .verticalScroll(scrollState)
            .padding(horizontal = spacing.xxl, vertical = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.xxl)
    ) {
        ObdScanHeroCard(
            adapterStatus = adapterStatus,
            scanDurationMs = state.scanDuration,
            estimatedRemainingSeconds = estimatedRemaining,
            progress = state.scanProgress
        )

        ObdScanMetaGrid(
            adapterStatus = adapterStatus,
            progress = state.scanProgress,
            scanDurationMs = state.scanDuration,
            estimatedRemainingSeconds = estimatedRemaining
        )

        ObdScanPanelsRow(
            panels = listOf(
                { panelModifier ->
                    ObdScanProgressPanel(
                        progress = state.scanProgress,
                        adapterStatus = adapterStatus,
                        estimatedRemaining = estimatedRemaining,
                        scanDurationMs = state.scanDuration,
                        modifier = panelModifier.defaultMinSize(minHeight = ScanPanelMinHeight)
                    )
                },
                { panelModifier ->
                    ObdScanChecklist(
                        progress = state.scanProgress,
                        modifier = panelModifier.defaultMinSize(minHeight = ScanPanelMinHeight)
                    )
                }
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (adapterStatus is AdapterStatus.Error) {
            AdapterErrorPanel(
                message = adapterStatus.message,
                onRetry = {
                    viewModel.connectAdapter()
                    viewModel.startScan()
                }
            )
        }

        KioskPanel(
            headline = "Можно прервать сканирование",
            supportingText = "Если нужно вернуться на главный экран или выбрать другую услугу"
        ) {
            KioskSecondaryButton(
                onClick = onAbort,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Прервать сканирование")
            }
        }
    }
}

@Composable
private fun ObdScanChip(entry: ScanSummaryChip) {
    val spacing = KioskTokens.spacing
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
            Text(
                text = entry.value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ObdScanHeroCard(
    adapterStatus: AdapterStatus,
    scanDurationMs: Long,
    estimatedRemainingSeconds: Int,
    progress: Float
) {
    val spacing = KioskTokens.spacing
    val chips = remember(adapterStatus, scanDurationMs, estimatedRemainingSeconds) {
        listOf(
            ScanSummaryChip(
                label = "Статус",
                value = obdScanAdapterStatusLabel(adapterStatus)
            ),
            ScanSummaryChip(
                label = "Прошло",
                value = formatScanDuration(scanDurationMs)
            ),
            ScanSummaryChip(
                label = "Осталось",
                value = formatEstimatedRemaining(estimatedRemainingSeconds)
            )
        )
    }
    val progressPercent = remember(progress) { (progress.coerceIn(0f, 1f) * 100).roundToInt() }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.lg, horizontal = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = "Сканируем системы автомобиля",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "OBD-адаптер читает блоки управления, фиксирует датчики и готовит список DTC.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyVerticalGrid(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 0.dp),
                columns = GridCells.Adaptive(minSize = 220.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                userScrollEnabled = false
            ) {
                items(chips) { chip -> ObdScanChip(chip) }
            }
            Text(
                text = "Прогресс $progressPercent% · телеметрия обновляется каждые 200 мс",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ObdScanMetaGrid(
    adapterStatus: AdapterStatus,
    progress: Float,
    scanDurationMs: Long,
    estimatedRemainingSeconds: Int
) {
    val spacing = KioskTokens.spacing
    val entries = remember(adapterStatus, progress, scanDurationMs, estimatedRemainingSeconds) {
        listOf(
            ObdScanMetaEntry(
                label = "Статус",
                value = obdScanAdapterStatusLabel(adapterStatus),
                supporting = "Обновляем телеметрию каждые 200 мс"
            ),
            ObdScanMetaEntry(
                label = "Прогресс",
                value = "${(progress * 100).roundToInt()}%",
                supporting = "Этапы проходят автоматически"
            ),
            ObdScanMetaEntry(
                label = "Прошло",
                value = formatScanDuration(scanDurationMs),
                supporting = "Считаем от старта сканирования"
            ),
            ObdScanMetaEntry(
                label = "Осталось",
                value = formatEstimatedRemaining(estimatedRemainingSeconds),
                supporting = "Можем завершиться раньше"
            )
        )
    }

    LazyVerticalGrid(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 0.dp),
        columns = GridCells.Adaptive(minSize = 260.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
        userScrollEnabled = false
    ) {
        items(entries, key = { it.label }) { entry ->
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
    }
}

@Composable
private fun ObdScanPanelsRow(
    panels: List<@Composable (Modifier) -> Unit>,
    modifier: Modifier = Modifier
) {
    val spacing = KioskTokens.spacing
    BoxWithConstraints(modifier = modifier) {
        val stackPanels = maxWidth < ScanPanelBreakpoint
        if (stackPanels) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.lg)
            ) {
                panels.forEach { panel ->
                    panel(Modifier.fillMaxWidth())
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.lg)
            ) {
                panels.forEach { panel ->
                    Box(modifier = Modifier.weight(1f)) {
                        panel(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun ObdScanProgressPanel(
    progress: Float,
    adapterStatus: AdapterStatus,
    estimatedRemaining: Int,
    scanDurationMs: Long,
    modifier: Modifier = Modifier
) {
    val spacing = KioskTokens.spacing
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 10.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(
                        text = "Статус",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = obdScanAdapterStatusLabel(adapterStatus),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Прошло ${formatScanDuration(scanDurationMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Осталось",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatEstimatedRemaining(estimatedRemaining),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = "Адаптер последовательно читает блоки и фиксирует результаты.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(spacing.sm),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ObdScanChecklist(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val spacing = KioskTokens.spacing
    val activeIndex = remember(progress) { checklistActiveIndex(progress) }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Text(
                text = "Этапы сканирования",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Активный шаг подсвечен цветом",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            scanSteps.forEachIndexed { index, step ->
                val state = checklistState(index, activeIndex)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.md)
                ) {
                    ChecklistBadge(index = index, state = state)
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(
                            text = step.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (state == ChecklistState.Active) FontWeight.SemiBold else FontWeight.Medium
                        )
                        Text(
                            text = step.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistBadge(index: Int, state: ChecklistState) {
    val spacing = KioskTokens.spacing
    val (background, contentColor, text) = when (state) {
        ChecklistState.Done -> Triple(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            MaterialTheme.colorScheme.primary,
            "✓"
        )
        ChecklistState.Active -> Triple(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary,
            "${index + 1}"
        )
        ChecklistState.Upcoming -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "${index + 1}"
        )
    }

    Surface(
        shape = CircleShape,
        color = background
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs)
        )
    }
}

@Composable
private fun AdapterErrorPanel(
    message: String,
    onRetry: () -> Unit
) {
    val spacing = KioskTokens.spacing
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Text(
                text = "Адаптер не отвечает",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "Проверьте питание адаптера и плотность подключения к разъёму, затем попробуйте снова.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
            )
            KioskPrimaryButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Повторить")
            }
        }
    }
}

private val ScanPanelMinHeight = 360.dp
private val ScanPanelBreakpoint = 1080.dp

private data class ObdScanMetaEntry(
    val label: String,
    val value: String,
    val supporting: String
)

private data class ScanSummaryChip(
    val label: String,
    val value: String
)

private data class ChecklistStep(
    val title: String,
    val description: String
)

private enum class ChecklistState {
    Upcoming,
    Active,
    Done
}

private val scanSteps = listOf(
    ChecklistStep(
        title = "Читаем VIN и базовые параметры",
        description = "Определяем конфигурацию авто и готовим адаптер"
    ),
    ChecklistStep(
        title = "Проверяем связи CAN-шины",
        description = "Отслеживаем активность блоков и статусы MIL"
    ),
    ChecklistStep(
        title = "Получаем активные DTC",
        description = "Формируем список кодов ошибок и рекомендаций"
    )
)

private fun checklistState(index: Int, activeIndex: Int): ChecklistState = when {
    index < activeIndex -> ChecklistState.Done
    index == activeIndex -> ChecklistState.Active
    else -> ChecklistState.Upcoming
}

internal fun checklistActiveIndex(progress: Float): Int {
    val normalized = progress.coerceIn(0f, 1f)
    return when {
        normalized < 0.34f -> 0
        normalized < 0.67f -> 1
        else -> 2
    }
}

internal fun formatEstimatedRemaining(seconds: Int): String {
    if (seconds <= 0) return "≈ 0 с"
    val safe = seconds.coerceAtMost(5 * 60 * 60) // не более 5 часов
    val minutes = safe / 60
    val remaining = safe % 60
    return buildString {
        append("≈ ")
        if (minutes > 0) {
            append(minutes)
            append(" мин")
            if (remaining > 0) append(' ')
        }
        if (remaining > 0 || minutes == 0) {
            append(remaining)
            append(" с")
        }
    }
}

internal fun obdScanAdapterStatusLabel(status: AdapterStatus): String = when (status) {
    AdapterStatus.Disconnected -> "Отключено"
    AdapterStatus.Connecting -> "Подключаемся"
    AdapterStatus.Connected -> "Готовимся"
    AdapterStatus.Scanning -> "Сканирование"
    AdapterStatus.Complete -> "Готово"
    is AdapterStatus.Error -> "Ошибка"
}
