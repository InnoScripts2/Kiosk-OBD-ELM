@file:OptIn(ExperimentalLayoutApi::class)

package com.selfservice.kiosk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.selfservice.kiosk.ui.state.DeviceStatus
import com.selfservice.kiosk.ui.state.Measurement
import com.selfservice.kiosk.ui.state.MeasurementStatus
import com.selfservice.kiosk.ui.viewmodels.ThicknessFlowViewModel
import com.selfservice.platform.ui.components.KioskPanel
import com.selfservice.platform.ui.components.KioskPrimaryButton
import com.selfservice.platform.ui.foundation.KioskTokens
import kotlin.math.roundToInt

@Composable
fun ThicknessMeasurementScreen(
    viewModel: ThicknessFlowViewModel,
    onComplete: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.startMeasurements()
    }
    val spacing = KioskTokens.spacing
    val background = KioskTokens.gradients.hero

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = spacing.xxl, vertical = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.xl)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                text = "Измерения",
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text = "${state.completedPoints} из ${state.totalPoints} точек заполнено",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        MeasurementMetaRow(
            completed = state.completedPoints,
            total = state.totalPoints,
            deviceStatus = state.deviceStatus,
            currentZone = state.currentZone
        )

        MeasurementProgressPanel(progress = state.progress)
        DeviceStatusBanner(status = state.deviceStatus)

        FlowRow(
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg)
        ) {
            MeasurementGridPanel(
                totalPoints = state.totalPoints,
                measurements = state.measurements,
                modifier = Modifier.weight(1.5f, fill = true)
            )
            MeasurementGuidancePanel(
                currentZone = state.currentZone,
                modifier = Modifier.weight(1f, fill = true)
            )
        }

        KioskPrimaryButton(
            onClick = onComplete,
            enabled = state.isComplete,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Завершить измерения")
        }
    }
}

@Composable
private fun MeasurementGrid(
    totalPoints: Int,
    measurements: Map<String, Measurement>,
    modifier: Modifier = Modifier
) {
    val spacing = KioskTokens.spacing
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    ) {
        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxSize()
                .testTag("measurement-grid"),
            columns = GridCells.Adaptive(minSize = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(spacing.lg)
        ) {
            items(totalPoints) { index ->
                val zone = "Z${index + 1}"
                val measurement = measurements[zone]
                MeasurementCell(zone = zone, measurement = measurement)
            }
        }
    }
}

@Composable
private fun MeasurementCell(zone: String, measurement: Measurement?) {
    val colors = measurementColors(measurement?.status)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = MaterialTheme.shapes.medium,
        color = colors.background,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(KioskTokens.spacing.sm),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = zone,
                style = MaterialTheme.typography.labelSmall,
                color = colors.content
            )
            Text(
                text = measurement?.value?.toInt()?.let { "$it μm" } ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.content,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = measurementStatusLabel(measurement?.status),
                style = MaterialTheme.typography.bodySmall,
                color = colors.content.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun MeasurementLegend() {
    val spacing = KioskTokens.spacing
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        LegendChip(color = measurementColors(MeasurementStatus.Normal).background, label = "Норма")
        LegendChip(color = measurementColors(MeasurementStatus.Warning).background, label = "Предупреждение")
        LegendChip(color = measurementColors(MeasurementStatus.Critical).background, label = "Критично")
        LegendChip(color = measurementColors(null).background, label = "Не заполнено")
    }
}

@Composable
private fun LegendChip(color: Color, label: String) {
    val spacing = KioskTokens.spacing
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.9f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs)
        )
    }
}

@Composable
private fun MeasurementMetaRow(
    completed: Int,
    total: Int,
    deviceStatus: DeviceStatus,
    currentZone: String?
) {
    val spacing = KioskTokens.spacing
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        MeasurementMetaChip(label = "Заполнено", value = "$completed / $total точек")
        MeasurementMetaChip(label = "Зона", value = currentZone ?: "Ожидаем команду")
        MeasurementMetaChip(label = "Статус прибора", value = deviceStatusLabel(deviceStatus))
        MeasurementMetaChip(label = "Прогресс", value = "${((completed / total.toFloat()) * 100).roundToInt()}%")
    }
}

@Composable
private fun MeasurementMetaChip(label: String, value: String) {
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
private fun MeasurementProgressPanel(progress: Float) {
    val spacing = KioskTokens.spacing
    KioskPanel(
        headline = "Ход процесса",
        supportingText = "Следуйте подсказкам на приборе"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(spacing.sm)
                    .testTag("thickness-progress"),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            MeasurementLegend()
        }
    }
}

@Composable
private fun DeviceStatusBanner(status: DeviceStatus) {
    val spacing = KioskTokens.spacing
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Text(
            text = deviceStatusLabel(status),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xl, vertical = spacing.md)
                .testTag("device-status-banner")
        )
    }
}

@Composable
private fun MeasurementGridPanel(
    totalPoints: Int,
    measurements: Map<String, Measurement>,
    modifier: Modifier
) {
    KioskPanel(
        modifier = modifier,
        headline = "Сетка измерений",
        supportingText = "Заполняем все зоны кузова"
    ) {
        MeasurementGrid(totalPoints = totalPoints, measurements = measurements, modifier = Modifier.height(360.dp))
    }
}

@Composable
private fun MeasurementGuidancePanel(
    currentZone: String?,
    modifier: Modifier
) {
    val spacing = KioskTokens.spacing
    KioskPanel(
        modifier = modifier,
        headline = "Подсказки",
        supportingText = currentZone?.let { "Сейчас: $it" } ?: "Ожидаем первое значение"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            measurementTips.forEach { tip ->
                Text(
                    text = "• $tip",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private val measurementTips = listOf(
    "Плотно прижимайте датчик перпендикулярно поверхности",
    "Старайтесь не задерживаться на одной точке более 3 секунд",
    "Завершайте каждую зону прежде чем переходить к следующей"
)
