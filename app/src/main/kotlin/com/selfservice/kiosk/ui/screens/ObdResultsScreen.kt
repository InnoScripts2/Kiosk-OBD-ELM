package com.selfservice.kiosk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.selfservice.kiosk.ui.state.AdapterStatus
import com.selfservice.kiosk.ui.state.ClearResult
import com.selfservice.kiosk.ui.state.DtcCode
import com.selfservice.kiosk.ui.state.DtcSeverity
import com.selfservice.kiosk.ui.state.ObdFlowState
import com.selfservice.kiosk.ui.state.SystemStatus
import com.selfservice.platform.ui.components.KioskPanel
import com.selfservice.platform.ui.components.KioskPrimaryButton
import com.selfservice.platform.ui.components.KioskSecondaryButton
import com.selfservice.platform.ui.foundation.KioskTokens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ObdResultsScreen(
    state: ObdFlowState,
    onSendReport: () -> Unit,
    onViewDetails: () -> Unit,
    onFinish: () -> Unit
) {
    val spacing = KioskTokens.spacing
    val background = KioskTokens.gradients.hero
    val colorScheme = MaterialTheme.colorScheme
    val statusLabel = remember(state.systemStatus, colorScheme) {
        systemStatusLabel(state.systemStatus, colorScheme)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = spacing.xxl, vertical = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.xxl)
    ) {
        ObdResultsHeader(statusLabel)
        ObdSummaryCard(state = state, statusLabel = statusLabel)

        KioskPanel(
            headline = if (state.hasErrors) "Коды неисправностей" else "Сессия завершена",
            supportingText = if (state.hasErrors) {
                "Нажмите на карточку, чтобы увидеть расшифровку и рекомендации."
            } else {
                "Ошибок нет, можно отправить короткий отчёт клиенту."
            }
        ) {
            ObdDtcList(codes = state.dtcCodes)
        }

        ObdResultsActions(
            hasErrors = state.hasErrors,
            onSendReport = onSendReport,
            onViewDetails = onViewDetails,
            onFinish = onFinish
        )
    }
}

@Composable
private fun ObdResultsHeader(statusLabel: StatusLabel) {
    val spacing = KioskTokens.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(
            text = "Результаты диагностики",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            text = "Сводка фиксирует статус систем, историю сканирования и найденные коды DTC.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
        )
        Text(
            text = statusLabel.subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ObdSummaryCard(state: ObdFlowState, statusLabel: StatusLabel) {
    val spacing = KioskTokens.spacing
    val colorScheme = MaterialTheme.colorScheme
    val chipEntries = remember(state, statusLabel, colorScheme) {
        listOf(
            SummaryChip(
                label = "Статус",
                value = statusLabel.title,
                emphasisColor = statusLabel.color
            ),
            SummaryChip(
                label = "Сканирование",
                value = formatScanDuration(state.scanDuration),
                emphasisColor = colorScheme.primary
            ),
            SummaryChip(
                label = "Адаптер",
                    value = obdResultsAdapterStatusLabel(state.adapterStatus),
                emphasisColor = colorScheme.secondary
            )
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 8.dp,
        color = colorScheme.surface.copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Text(
                text = "Сводка диагностики",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Фиксируем статус систем и последние действия адаптера.",
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                chipEntries.forEach { chip ->
                    ObdSummaryChip(chip)
                }
            }
            ObdResultsMetaGrid(state = state)
            state.clearResult?.let { result ->
                ObdClearResultBanner(result = result)
            }
        }
    }
}

@Composable
private fun ObdSummaryChip(entry: SummaryChip) {
    val spacing = KioskTokens.spacing
    Surface(
        color = entry.emphasisColor.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.labelSmall,
                color = entry.emphasisColor.copy(alpha = 0.8f)
            )
            Text(
                text = entry.value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = entry.emphasisColor
            )
        }
    }
}

@Composable
private fun ObdResultsActions(
    hasErrors: Boolean,
    onSendReport: () -> Unit,
    onViewDetails: () -> Unit,
    onFinish: () -> Unit
) {
    val spacing = KioskTokens.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        KioskPrimaryButton(
            onClick = if (hasErrors) onViewDetails else onFinish,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (hasErrors) "Показать результаты" else "Вернуться")
        }

        KioskSecondaryButton(
            onClick = onSendReport,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Отправить отчёт")
        }

        KioskSecondaryButton(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("На главный экран")
        }
    }
}

@Composable
private fun ObdResultsMetaGrid(state: ObdFlowState) {
    val spacing = KioskTokens.spacing
    val colorScheme = MaterialTheme.colorScheme
    val entries = remember(state, colorScheme) {
        listOf(
            ObdResultMeta(
                label = "Статус",
                value = systemStatusLabel(state.systemStatus, colorScheme).title,
                supporting = "По данным последнего сканирования"
            ),
            ObdResultMeta(
                label = "Ошибки",
                value = state.dtcCodes.size.toString(),
                supporting = "Все активные DTC"
            ),
            ObdResultMeta(
                label = "Критические",
                value = state.criticalCount.toString(),
                supporting = "Требуют немедленного внимания"
            ),
            ObdResultMeta(
                label = "Предупреждения",
                value = state.warningCount.toString(),
                supporting = "Можно продолжить движение"
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
private fun ObdDtcList(codes: List<DtcCode>) {
    val spacing = KioskTokens.spacing
    if (codes.isEmpty()) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Text(
                text = "Нет активных кодов неисправностей",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.lg)
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        codes.take(6).forEach { code ->
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
                    Text(
                        text = code.code,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${code.system} · ${formatDtcTimestamp(code.timestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = code.description,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    ObdSeverityBadge(severity = code.severity)
                }
            }
        }
        if (codes.size > 6) {
            Text(
                text = "+ ещё ${codes.size - 6} кодов",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class ObdResultMeta(
    val label: String,
    val value: String,
    val supporting: String
)

private data class StatusLabel(
    val title: String,
    val subtitle: String,
    val color: Color
)

private data class SummaryChip(
    val label: String,
    val value: String,
    val emphasisColor: Color
)

@Composable
private fun ObdClearResultBanner(result: ClearResult) {
    val spacing = KioskTokens.spacing
    val (text, color) = when (result) {
        is ClearResult.Success -> "Ошибки сброшены успешно" to MaterialTheme.colorScheme.primary
        is ClearResult.Failure -> result.error to MaterialTheme.colorScheme.error
    }
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            color = color
        )
    }
}

private fun systemStatusLabel(status: SystemStatus, colors: ColorScheme): StatusLabel = when (status) {
    SystemStatus.Unknown -> StatusLabel(
        title = "Диагностика выполняется",
        subtitle = "Подождите, пока адаптер завершит сканирование",
        color = colors.onPrimary
    )
    SystemStatus.AllGood -> StatusLabel(
        title = "Все системы в порядке",
        subtitle = "Ошибок и предупреждений не найдено",
        color = colors.onPrimary
    )
    SystemStatus.HasWarnings -> StatusLabel(
        title = "Есть предупреждения",
        subtitle = "Можно продолжить путь, но проверьте детали",
        color = colors.tertiary
    )
    SystemStatus.HasErrors -> StatusLabel(
        title = "Обнаружены ошибки",
        subtitle = "Рекомендуем ознакомиться с расшифровкой кодов",
        color = colors.error
    )
}

internal fun formatScanDuration(durationMillis: Long): String {
    if (durationMillis <= 0L) return "—"
    val totalSeconds = durationMillis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return buildString {
        if (minutes > 0) {
            append(minutes)
            append(" мин ")
        }
        append(seconds)
        append(" с")
    }.trim()
}

internal fun obdResultsAdapterStatusLabel(status: AdapterStatus): String = when (status) {
    AdapterStatus.Disconnected -> "Нет подключения"
    AdapterStatus.Connecting -> "Подключение"
    AdapterStatus.Connected -> "Готов к сканированию"
    AdapterStatus.Scanning -> "Сканирование"
    AdapterStatus.Complete -> "Готово"
    is AdapterStatus.Error -> status.message
}

private val dtcFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm, dd MMM", Locale("ru", "RU"))

internal fun formatDtcTimestamp(timestamp: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
    if (timestamp <= 0L) return "—"
    return runCatching {
        dtcFormatter.withZone(zoneId).format(Instant.ofEpochMilli(timestamp))
    }.getOrElse { "—" }
}
