package com.selfservice.kiosk.ui.screens

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.selfservice.kiosk.ui.state.AdapterStatus
import com.selfservice.kiosk.ui.state.ClearResult
import com.selfservice.kiosk.ui.state.DtcCode
import com.selfservice.kiosk.ui.state.ObdFlowState
import com.selfservice.kiosk.ui.state.SystemStatus
import com.selfservice.kiosk.ui.viewmodels.ObdFlowViewModel
import com.selfservice.platform.ui.components.KioskPanel
import com.selfservice.platform.ui.components.KioskPrimaryButton
import com.selfservice.platform.ui.components.KioskSecondaryButton
import com.selfservice.platform.ui.foundation.KioskTokens

@Composable
fun ObdDetailsScreen(
    state: ObdFlowState,
    viewModel: ObdFlowViewModel,
    onSendReport: () -> Unit,
    onClose: () -> Unit
) {
    val spacing = KioskTokens.spacing
    val background = KioskTokens.gradients.hero

    val summaryChips = remember(state.systemStatus, state.dtcCodes, state.clearRequested, state.clearResult) {
        buildObdDetailsSummaryChips(state)
    }
    val metaEntries = remember(state) { buildObdDetailsMeta(state) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 1440.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.xxl, vertical = spacing.xl)
                .testTag(TAG_OBD_DETAILS_SCROLL),
            verticalArrangement = Arrangement.spacedBy(spacing.xl)
        ) {
            ObdDetailsHeroCard(summaryChips = summaryChips)

            ObdDetailsMetaGrid(metaEntries = metaEntries)

            ObdDetailsPanelsRow(
                panels = listOf(
                    { panelModifier ->
                        ObdDtcPanel(
                            codes = state.dtcCodes,
                            modifier = panelModifier
                                .defaultMinSize(minHeight = ObdDetailsPanelMinHeight)
                        )
                    },
                    { panelModifier ->
                        ObdClearPanel(
                            state = state,
                            onClear = { viewModel.clearDtcCodes() },
                            modifier = panelModifier
                                .defaultMinSize(minHeight = ObdDetailsPanelMinHeight)
                        )
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            ObdDetailsActionsRow(
                onSendReport = onSendReport,
                onClose = onClose,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ObdDetailsHeroCard(summaryChips: List<ObdDetailsSummaryChip>) {
    val spacing = KioskTokens.spacing
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_OBD_DETAILS_HERO),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = spacing.lg, horizontal = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = "Детальная расшифровка",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Показываем активные DTC, статус MIL и рекомендации по сбросу.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyVerticalGrid(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_OBD_DETAILS_SUMMARY_GRID),
                columns = GridCells.Adaptive(minSize = 220.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                userScrollEnabled = false
            ) {
                items(summaryChips, key = { it.label }) { chip ->
                    ObdDetailsSummaryChipCard(chip = chip)
                }
            }
        }
    }
}

@Composable
private fun ObdDetailsSummaryChipCard(chip: ObdDetailsSummaryChip) {
    val spacing = KioskTokens.spacing
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = scheme.primary.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Text(
                text = chip.label,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.primary.copy(alpha = 0.7f)
            )
            Text(
                text = chip.value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = chip.supporting,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ObdDetailsMetaGrid(metaEntries: List<ObdDetailsMetaEntry>) {
    val spacing = KioskTokens.spacing
    LazyVerticalGrid(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_OBD_DETAILS_META_GRID),
        columns = GridCells.Adaptive(minSize = 220.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
        userScrollEnabled = false
    ) {
        items(metaEntries, key = { it.label }) { entry ->
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
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
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
private fun ObdDetailsPanelsRow(
    panels: List<@Composable (Modifier) -> Unit>,
    modifier: Modifier = Modifier
) {
    val spacing = KioskTokens.spacing
    BoxWithConstraints(modifier = modifier) {
        val stackPanels = maxWidth < ObdDetailsPanelBreakpoint
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
private fun ObdDtcPanel(codes: List<DtcCode>, modifier: Modifier) {
    val spacing = KioskTokens.spacing
    KioskPanel(
        modifier = modifier.testTag(TAG_OBD_DETAILS_DTC_PANEL),
        headline = "Список кодов ошибок",
        supportingText = "Каждый код фиксируем с расшифровкой и уровнем критичности"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            if (codes.isEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "Коды ошибок не найдены",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.lg)
                    )
                }
            } else {
                codes.forEach { code ->
                    ObdDetailedRow(code = code)
                }
            }
        }
    }
}

@Composable
private fun ObdDetailedRow(code: DtcCode) {
    val spacing = KioskTokens.spacing
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(
                        text = code.code,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = code.system,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ObdSeverityBadge(severity = code.severity)
            }
            Text(
                text = code.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ObdClearPanel(
    state: ObdFlowState,
    onClear: () -> Unit,
    modifier: Modifier
) {
    val spacing = KioskTokens.spacing
    KioskPanel(
        modifier = modifier.testTag(TAG_OBD_DETAILS_CLEAR_PANEL),
        headline = "Сброс ошибок",
        supportingText = "Выполняем команду Clear DTC. Сессия логирует попытку и результат"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            ObdDetailsClearResultBanner(result = state.clearResult)
            KioskPrimaryButton(
                onClick = onClear,
                enabled = !state.clearRequested,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.clearRequested) "Выполняем..." else "Сбросить ошибки")
            }
        }
    }
}

@Composable
private fun ObdDetailsActionsRow(
    onSendReport: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = KioskTokens.spacing
    Column(
        modifier = modifier.testTag(TAG_OBD_DETAILS_ACTIONS),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        KioskPrimaryButton(
            onClick = onSendReport,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Отправить отчёт")
        }
        KioskSecondaryButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("К главному экрану")
        }
    }
}

@Composable
private fun ObdDetailsClearResultBanner(result: ClearResult?) {
    val spacing = KioskTokens.spacing
    if (result == null) return
    val descriptor = when (result) {
        ClearResult.Success -> "Ошибки успешно сброшены" to MaterialTheme.colorScheme.primary
        is ClearResult.Failure -> result.error to MaterialTheme.colorScheme.error
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = descriptor.second.copy(alpha = 0.15f)
    ) {
        Text(
            text = descriptor.first,
            style = MaterialTheme.typography.bodyMedium,
            color = descriptor.second,
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md)
        )
    }
}

@VisibleForTesting
internal fun buildObdDetailsSummaryChips(state: ObdFlowState): List<ObdDetailsSummaryChip> = listOf(
    ObdDetailsSummaryChip(
        label = "Статус систем",
        value = obdDetailsSystemStatusLabel(state.systemStatus),
        supporting = "По результатам последнего сканирования"
    ),
    ObdDetailsSummaryChip(
        label = "Активные коды",
        value = state.dtcCodes.size.toString(),
        supporting = "Критические: ${state.criticalCount}, предупреждения: ${state.warningCount}"
    ),
    ObdDetailsSummaryChip(
        label = "Статус MIL",
        value = obdDetailsMilLabel(state.hasErrors),
        supporting = if (state.hasErrors) "Сигнал Check Engine включён" else "Индикатор выключен"
    )
)

@VisibleForTesting
internal fun buildObdDetailsMeta(state: ObdFlowState): List<ObdDetailsMetaEntry> {
    val lastCodeTimestamp = state.dtcCodes.maxByOrNull { it.timestamp }?.timestamp ?: 0L
    return listOf(
        ObdDetailsMetaEntry(
            label = "Адаптер",
            value = obdResultsAdapterStatusLabel(state.adapterStatus),
            supporting = "Обновляем статус подключения"
        ),
        ObdDetailsMetaEntry(
            label = "Сканирование",
            value = formatScanDuration(state.scanDuration),
            supporting = "Длительность последней сессии"
        ),
        ObdDetailsMetaEntry(
            label = "Очистка DTC",
            value = obdDetailsClearStatusLabel(state.clearRequested, state.clearResult),
            supporting = "Протоколируем каждую попытку"
        ),
        ObdDetailsMetaEntry(
            label = "Последний код",
            value = formatDtcTimestamp(lastCodeTimestamp),
            supporting = "Время фиксации события"
        )
    )
}

@VisibleForTesting
internal fun obdDetailsSystemStatusLabel(status: SystemStatus): String = when (status) {
    SystemStatus.Unknown -> "Диагностика выполняется"
    SystemStatus.AllGood -> "Все системы в порядке"
    SystemStatus.HasWarnings -> "Есть предупреждения"
    SystemStatus.HasErrors -> "Обнаружены ошибки"
}

@VisibleForTesting
internal fun obdDetailsMilLabel(hasErrors: Boolean): String = if (hasErrors) "Активен" else "Выключен"

@VisibleForTesting
internal fun obdDetailsClearStatusLabel(clearRequested: Boolean, clearResult: ClearResult?): String = when {
    clearRequested -> "Выполняем"
    clearResult is ClearResult.Success -> "Успешно"
    clearResult is ClearResult.Failure -> "Ошибка"
    else -> "Не выполнялось"
}

internal data class ObdDetailsSummaryChip(
    val label: String,
    val value: String,
    val supporting: String
)

internal data class ObdDetailsMetaEntry(
    val label: String,
    val value: String,
    val supporting: String
)

private val ObdDetailsPanelMinHeight = 360.dp
private val ObdDetailsPanelBreakpoint = 1080.dp

internal const val TAG_OBD_DETAILS_SCROLL = "obd-details-scroll"
internal const val TAG_OBD_DETAILS_HERO = "obd-details-hero"
internal const val TAG_OBD_DETAILS_SUMMARY_GRID = "obd-details-summary-grid"
internal const val TAG_OBD_DETAILS_META_GRID = "obd-details-meta"
internal const val TAG_OBD_DETAILS_DTC_PANEL = "obd-details-dtc"
internal const val TAG_OBD_DETAILS_CLEAR_PANEL = "obd-details-clear"
internal const val TAG_OBD_DETAILS_ACTIONS = "obd-details-actions"
