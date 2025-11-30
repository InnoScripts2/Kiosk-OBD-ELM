@file:OptIn(ExperimentalLayoutApi::class)

package com.selfservice.kiosk.ui.screens

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.selfservice.kiosk.ui.state.Measurement
import com.selfservice.kiosk.ui.state.MeasurementStatus
import com.selfservice.kiosk.ui.state.ThicknessFlowState
import com.selfservice.kiosk.ui.viewmodels.ThicknessFlowViewModel
import com.selfservice.platform.ui.components.KioskPanel
import com.selfservice.platform.ui.components.KioskPrimaryButton
import com.selfservice.platform.ui.components.KioskSecondaryButton
import com.selfservice.platform.ui.foundation.KioskTokens
import kotlin.math.roundToInt

@Composable
fun ThicknessResultsScreen(
    viewModel: ThicknessFlowViewModel,
    onSendReport: () -> Unit,
    onBackToMain: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    ThicknessResultsContent(
        state = state,
        onSendReport = onSendReport,
        onBackToMain = onBackToMain
    )
}

@Composable
private fun ThicknessResultsContent(
    state: ThicknessFlowState,
    onSendReport: () -> Unit,
    onBackToMain: () -> Unit
) {
    val spacing = KioskTokens.spacing
    val background = KioskTokens.gradients.hero
    val summary = remember(state.measurements, state.totalPoints) {
        calculateThicknessSummary(state.measurements.values, state.totalPoints)
    }
    val highlights = remember(summary) { buildResultHighlights(summary) }
    val orderedMeasurements = remember(state.measurements, state.totalPoints) {
        (1..state.totalPoints).map { index ->
            val zone = "Z$index"
            zone to state.measurements[zone]
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = spacing.xxl, vertical = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.xl)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                text = "Замеры завершены",
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text = "Подготовили отчёт по ${summary.completedPoints} точкам. Отправим PDF и SMS после подтверждения.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ResultsMetaRow(summary = summary)

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg)
        ) {
            ResultsHighlightsPanel(
                highlights = highlights,
                modifier = Modifier.weight(1f, fill = true)
            )
            ResultsLegendPanel(
                summary = summary,
                modifier = Modifier.weight(1f, fill = true)
            )
        }

        ResultsTable(
            items = orderedMeasurements,
            modifier = Modifier.weight(1f, fill = true)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            KioskPrimaryButton(
                onClick = onSendReport,
                enabled = summary.completedPoints > 0,
                modifier = Modifier.weight(1f)
            ) {
                Text("Отправить отчёт")
            }
            KioskSecondaryButton(
                onClick = onBackToMain,
                modifier = Modifier.weight(1f)
            ) {
                Text("К главному экрану")
            }
        }
    }
}

@Composable
private fun ResultsMetaRow(summary: ThicknessResultSummary) {
    val spacing = KioskTokens.spacing
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        ResultsMetaChip("Заполнено", "${summary.completedPoints} / ${summary.totalPoints}")
        ResultsMetaChip("Отклонения", summary.deviationCount.toString())
        ResultsMetaChip("Критичные", summary.criticalCount.toString())
        ResultsMetaChip("Среднее", formatAverageThickness(summary.average))
    }
}

@Composable
private fun ResultsMetaChip(label: String, value: String) {
    val spacing = KioskTokens.spacing
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ResultsHighlightsPanel(highlights: List<String>, modifier: Modifier) {
    val spacing = KioskTokens.spacing
    KioskPanel(
        modifier = modifier,
        headline = "Основные выводы",
        supportingText = "Используем для рекомендаций клиенту"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            highlights.forEach { point ->
                Text(
                    text = "• $point",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ResultsLegendPanel(summary: ThicknessResultSummary, modifier: Modifier) {
    val spacing = KioskTokens.spacing
    KioskPanel(
        modifier = modifier,
        headline = "Статистика",
        supportingText = "Сводка по статусам"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            LegendRow(label = "Норма", value = summary.normalCount)
            LegendRow(label = "Предупреждение", value = summary.warningCount)
            LegendRow(label = "Критично", value = summary.criticalCount)
            LegendRow(label = "Ошибки", value = summary.invalidCount)
        }
    }
}

@Composable
private fun LegendRow(label: String, value: Int) {
    val spacing = KioskTokens.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
    Spacer(modifier = Modifier.height(spacing.xs))
}

@Composable
private fun ResultsTable(items: List<Pair<String, Measurement?>>, modifier: Modifier) {
    val spacing = KioskTokens.spacing
    KioskPanel(
        modifier = modifier,
        headline = "Таблица замеров",
        supportingText = "Значения в микронах (μm)"
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("thk-results-table"),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            items(items) { (zone, measurement) ->
                MeasurementResultRow(zone = zone, measurement = measurement)
            }
        }
    }
}

@Composable
private fun MeasurementResultRow(zone: String, measurement: Measurement?) {
    val spacing = KioskTokens.spacing
    val valueText = measurement?.value?.roundToInt()?.let { "$it μm" } ?: "—"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                text = zone,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        measurement?.let {
            ResultStatusChip(status = it.status)
        } ?: ResultStatusChip(status = null)
    }
}

@Composable
private fun ResultStatusChip(status: MeasurementStatus?) {
    val colors = measurementColors(status)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = colors.background
    ) {
        Text(
            text = measurementStatusLabel(status),
            style = MaterialTheme.typography.labelMedium,
            color = colors.content,
            modifier = Modifier.padding(horizontal = KioskTokens.spacing.md, vertical = KioskTokens.spacing.xs)
        )
    }
}

private fun buildResultHighlights(summary: ThicknessResultSummary): List<String> {
    if (summary.completedPoints == 0) return listOf("Нет данных для анализа")
    val highlights = mutableListOf<String>()
    highlights += "Средний слой ${formatAverageThickness(summary.average)}"
    if (summary.criticalCount > 0) {
        highlights += "Критичных зон: ${summary.criticalCount}. Рекомендуем ручную проверку"
    }
    if (summary.warningCount > 0) {
        highlights += "Зон с предупреждениями: ${summary.warningCount}"
    }
    if (summary.invalidCount > 0) {
        highlights += "Ошибка измерения в ${summary.invalidCount} точках"
    }
    if (summary.deviationCount == 0) {
        highlights += "Все зоны в пределах нормы"
    }
    return highlights
}

@VisibleForTesting
internal fun calculateThicknessSummary(
    measurements: Collection<Measurement>,
    totalPoints: Int
): ThicknessResultSummary {
    val completed = measurements.size
    if (completed == 0) {
        return ThicknessResultSummary(
            totalPoints = totalPoints,
            completedPoints = 0,
            warningCount = 0,
            criticalCount = 0,
            invalidCount = 0,
            average = 0f
        )
    }

    val warning = measurements.count { it.status == MeasurementStatus.Warning }
    val critical = measurements.count { it.status == MeasurementStatus.Critical }
    val invalid = measurements.count { it.status == MeasurementStatus.Invalid }
    val average = measurements.map { it.value }.average().toFloat()

    return ThicknessResultSummary(
        totalPoints = totalPoints,
        completedPoints = completed,
        warningCount = warning,
        criticalCount = critical,
        invalidCount = invalid,
        average = average
    )
}

@VisibleForTesting
internal fun formatAverageThickness(value: Float): String {
    if (value <= 0f) return "—"
    return "${value.roundToInt()} μm"
}

internal data class ThicknessResultSummary(
    val totalPoints: Int,
    val completedPoints: Int,
    val warningCount: Int,
    val criticalCount: Int,
    val invalidCount: Int,
    val average: Float
) {
    val deviationCount: Int get() = warningCount + criticalCount + invalidCount
    val normalCount: Int get() = completedPoints - deviationCount
}
