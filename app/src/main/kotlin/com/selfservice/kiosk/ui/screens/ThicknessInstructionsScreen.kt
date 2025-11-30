package com.selfservice.kiosk.ui.screens

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.selfservice.kiosk.ui.state.DeviceStatus
import com.selfservice.kiosk.ui.viewmodels.ThicknessFlowViewModel
import com.selfservice.platform.ui.components.KioskPanel
import com.selfservice.platform.ui.components.KioskPrimaryButton
import com.selfservice.platform.ui.components.KioskSecondaryButton
import com.selfservice.platform.ui.foundation.KioskTokens

@Composable
fun ThicknessInstructionsScreen(
    viewModel: ThicknessFlowViewModel,
    onBack: () -> Unit,
    onStartMeasurements: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    ThicknessInstructionsContent(
        deviceStatus = state.deviceStatus,
        totalPoints = state.totalPoints,
        onBack = onBack,
        onStartMeasurements = onStartMeasurements
    )
}

@Composable
internal fun ThicknessInstructionsContent(
    deviceStatus: DeviceStatus,
    totalPoints: Int,
    onBack: () -> Unit,
    onStartMeasurements: () -> Unit
) {
    val spacing = KioskTokens.spacing
    val background = KioskTokens.gradients.hero
    val summaryChips = remember(deviceStatus, totalPoints) {
        buildInstructionSummaryChips(deviceStatus, totalPoints)
    }
    val metaItems = remember(deviceStatus, totalPoints) {
        buildInstructionMeta(deviceStatus, totalPoints)
    }
    val steps = remember { buildInstructionSteps() }
    val tipPairs = remember { buildInstructionTips() }
    val focusZones = remember(totalPoints) { buildZoneFocus(totalPoints) }

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
                .testTag(TAG_INSTRUCTION_SCROLL),
            verticalArrangement = Arrangement.spacedBy(spacing.xl)
        ) {
            InstructionHeroCard(
                totalPoints = totalPoints,
                summaryChips = summaryChips
            )

            InstructionMetaGrid(metaItems = metaItems)

            InstructionPanelsRow(
                panels = listOf(
                    { panelModifier ->
                        InstructionStepsPanel(
                            steps = steps,
                            modifier = panelModifier
                                .defaultMinSize(minHeight = InstructionPanelMinHeight)
                        )
                    },
                    { panelModifier ->
                        HoldingGuidelinesPanel(
                            modifier = panelModifier
                                .defaultMinSize(minHeight = InstructionPanelMinHeight)
                        )
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            InstructionPanelsRow(
                panels = listOf(
                    { panelModifier ->
                        DoDontPanel(
                            tips = tipPairs,
                            modifier = panelModifier
                                .defaultMinSize(minHeight = InstructionPanelMinHeight)
                        )
                    },
                    { panelModifier ->
                        ZoneFocusPanel(
                            zones = focusZones,
                            modifier = panelModifier
                                .defaultMinSize(minHeight = InstructionPanelMinHeight)
                        )
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            InstructionActionsRow(
                onBack = onBack,
                onStartMeasurements = onStartMeasurements,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun InstructionHeroCard(
    totalPoints: Int,
    summaryChips: List<InstructionSummaryChip>
) {
    val spacing = KioskTokens.spacing
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_INSTRUCTION_HERO),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = spacing.lg, horizontal = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = "Перед началом измерений",
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    text = "Следуйте инструкции, чтобы заполнить все $totalPoints зон кузова и получить корректный отчёт.",
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
                items(summaryChips, key = { it.label }) { chip ->
                    InstructionSummaryChipCard(chip = chip)
                }
            }
        }
    }
}

@Composable
private fun InstructionMetaGrid(metaItems: List<InstructionMetaEntry>) {
    val spacing = KioskTokens.spacing
    LazyVerticalGrid(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 0.dp)
            .testTag(TAG_INSTRUCTION_META_GRID),
        columns = GridCells.Adaptive(minSize = 220.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
        userScrollEnabled = false
    ) {
        items(metaItems, key = { it.label }) { entry ->
            InstructionMetaCard(entry = entry)
        }
    }
}

@Composable
private fun InstructionSummaryChipCard(
    chip: InstructionSummaryChip,
    modifier: Modifier = Modifier
) {
    val spacing = KioskTokens.spacing
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
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
private fun InstructionMetaCard(
    entry: InstructionMetaEntry,
    modifier: Modifier = Modifier
) {
    val spacing = KioskTokens.spacing
    Surface(
        modifier = modifier,
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

@Composable
private fun InstructionActionsRow(
    onBack: () -> Unit,
    onStartMeasurements: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = KioskTokens.spacing
    Column(
        modifier = modifier
            .testTag(TAG_INSTRUCTION_ACTIONS),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        Text(
            text = "Готовы приступить?",
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            KioskSecondaryButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Назад")
            }
            KioskPrimaryButton(
                onClick = onStartMeasurements,
                modifier = Modifier.weight(1f)
            ) {
                Text("Начать измерения")
            }
        }
    }
}

private val InstructionPanelMinHeight = 360.dp
private val InstructionPanelBreakpoint = 1080.dp

@Composable
private fun InstructionPanelsRow(
    panels: List<@Composable (Modifier) -> Unit>,
    modifier: Modifier = Modifier
) {
    val spacing = KioskTokens.spacing
    BoxWithConstraints(modifier = modifier) {
        val stackPanels = maxWidth < InstructionPanelBreakpoint
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
private fun InstructionStepsPanel(steps: List<InstructionStep>, modifier: Modifier) {
    val spacing = KioskTokens.spacing
    KioskPanel(
        modifier = modifier.testTag(TAG_INSTRUCTION_STEPS_PANEL),
        headline = "Основные шаги",
        supportingText = "Следуйте порядку, чтобы заполнить все точки"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            steps.forEachIndexed { index, step ->
                InstructionStepCard(index = index + 1, step = step)
            }
        }
    }
}

@Composable
private fun InstructionStepCard(index: Int, step: InstructionStep) {
    val spacing = KioskTokens.spacing
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            ) {
                Text(
                    text = index.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                step.highlight?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun HoldingGuidelinesPanel(modifier: Modifier) {
    val spacing = KioskTokens.spacing
    KioskPanel(
        modifier = modifier.testTag(TAG_INSTRUCTION_GUIDELINES_PANEL),
        headline = "Как держать датчик",
        supportingText = "Фиксируем угол и давление"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            holdingGuidelines.forEach { tip ->
                Text(
                    text = "• $tip",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(spacing.sm))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "Нажмите на датчик перпендикулярно кузову и удерживайте 2–3 секунды до сигнала.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.md)
                )
            }
        }
    }
}

@Composable
private fun DoDontPanel(tips: List<InstructionTip>, modifier: Modifier) {
    val spacing = KioskTokens.spacing
    KioskPanel(
        modifier = modifier.testTag(TAG_INSTRUCTION_TIPS_PANEL),
        headline = "Что можно / нельзя",
        supportingText = "Соблюдаем технику безопасности"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            tips.forEach { tip ->
                InstructionTipRow(tip = tip)
            }
        }
    }
}

@Composable
private fun InstructionTipRow(tip: InstructionTip) {
    val spacing = KioskTokens.spacing
    val colors = tipColors(tip.kind)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = colors.badge
            ) {
                Text(
                    text = if (tip.kind == InstructionTipKind.Do) "Можно" else "Нельзя",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.badgeContent,
                    modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs)
                )
            }
            Text(
                text = tip.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ZoneFocusPanel(zones: List<ZoneFocus>, modifier: Modifier) {
    val spacing = KioskTokens.spacing
    KioskPanel(
        modifier = modifier.testTag(TAG_INSTRUCTION_ZONES_PANEL),
        headline = "Зоны измерения",
        supportingText = "Равномерно проходим весь кузов"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            zones.forEach { zone ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        Text(
                            text = zone.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = zone.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private val holdingGuidelines = listOf(
    "Держите прибор за центральный корпус, не касаясь сенсора пальцами",
    "Снимайте показания на чистой и сухой поверхности",
    "Не перемещайте датчик во время считывания"
)

@VisibleForTesting
internal fun buildInstructionSummaryChips(
    deviceStatus: DeviceStatus,
    totalPoints: Int
): List<InstructionSummaryChip> = listOf(
    InstructionSummaryChip(
        label = "Статус",
        value = deviceStatusLabel(deviceStatus),
        supporting = "Обновляем каждые 200 мс"
    ),
    InstructionSummaryChip(
        label = "Всего точек",
        value = "$totalPoints",
        supporting = "Заполним кузов целиком"
    ),
    InstructionSummaryChip(
        label = "Среднее время",
        value = "≈ 4 мин",
        supporting = "Зависит от габаритов кузова"
    )
)

@VisibleForTesting
internal fun buildInstructionMeta(
    deviceStatus: DeviceStatus,
    totalPoints: Int
): List<InstructionMetaEntry> = listOf(
    InstructionMetaEntry(
        label = "Устройство",
        value = "Толщиномер BLE",
        supporting = "Автоподключение после выдачи"
    ),
    InstructionMetaEntry(
        label = "Последовательность",
        value = "$totalPoints зон",
        supporting = "Следуем маршруту крыша → двери → бамперы"
    ),
    InstructionMetaEntry(
        label = "Контроль",
        value = "Пошаговые подсказки",
        supporting = "Подсвечиваем пропущенные зоны"
    ),
    InstructionMetaEntry(
        label = "Состояние прибора",
        value = deviceStatusLabel(deviceStatus),
        supporting = "Статус обновляется в режиме реального времени"
    )
)

@VisibleForTesting
internal fun buildInstructionSteps(): List<InstructionStep> = listOf(
    InstructionStep(
        title = "Подготовьте кузов",
        description = "Протрите поверхность насухо и удалите пыль в зоне измерения.",
        highlight = "Влага и грязь искажают показания"
    ),
    InstructionStep(
        title = "Активируйте датчик",
        description = "Нажмите на кнопку на приборе и дождитесь синего индикатора готовности.",
        highlight = "Подключение занимает до 3 секунд"
    ),
    InstructionStep(
        title = "Снимайте значения",
        description = "Прижмите сенсор под прямым углом и удерживайте 2–3 секунды до сигнала.",
        highlight = null
    ),
    InstructionStep(
        title = "Отмечайте зоны",
        description = "Заполняйте точки последовательно — сначала крыша и стойки, затем двери, крылья и бамперы.",
        highlight = "Всего 60 точек, пропуски подсвечиваются"
    )
)

@VisibleForTesting
internal fun buildInstructionTips(): List<InstructionTip> = listOf(
    InstructionTip(
        kind = InstructionTipKind.Do,
        text = "Используйте лёгкое давление — прибор сам подаст сигнал"
    ),
    InstructionTip(
        kind = InstructionTipKind.Do,
        text = "Фиксируйте показания после каждого сигнала и переходите к следующей зоне"
    ),
    InstructionTip(
        kind = InstructionTipKind.Dont,
        text = "Не проводите датчиком по кузову — только касание"
    ),
    InstructionTip(
        kind = InstructionTipKind.Dont,
        text = "Не измеряйте на сколах, ржавчине или пластике"
    )
)

@VisibleForTesting
internal fun buildZoneFocus(totalPoints: Int): List<ZoneFocus> = listOf(
    ZoneFocus(
        label = "Крыша и стойки",
        description = "8 точек — помогают увидеть признаки перекраса после переворота"
    ),
    ZoneFocus(
        label = "Двери и крылья",
        description = "32 точки — основная площадь кузова, фиксируем расхождения > 40 μm"
    ),
    ZoneFocus(
        label = "Бамперы и пороги",
        description = "12 точек — косвенно показывают ремонт после ДТП"
    ),
    ZoneFocus(
        label = "Капот и крышка багажника",
        description = "8 точек — завершаем цикл и сверяемся с итогом ($totalPoints точек)"
    )
)

internal data class InstructionStep(
    val title: String,
    val description: String,
    val highlight: String?
)

internal data class InstructionTip(
    val kind: InstructionTipKind,
    val text: String
)

enum class InstructionTipKind { Do, Dont }

internal data class ZoneFocus(
    val label: String,
    val description: String
)

internal data class InstructionSummaryChip(
    val label: String,
    val value: String,
    val supporting: String
)

internal data class InstructionMetaEntry(
    val label: String,
    val value: String,
    val supporting: String
)

private data class TipColors(
    val background: Color,
    val badge: Color,
    val badgeContent: Color
)

@Composable
private fun tipColors(kind: InstructionTipKind): TipColors {
    val scheme = MaterialTheme.colorScheme
    return when (kind) {
        InstructionTipKind.Do -> TipColors(
            background = scheme.primary.copy(alpha = 0.1f),
            badge = scheme.primary,
            badgeContent = scheme.onPrimary
        )
        InstructionTipKind.Dont -> TipColors(
            background = scheme.error.copy(alpha = 0.1f),
            badge = scheme.error,
            badgeContent = scheme.onError
        )
    }
}

internal const val TAG_INSTRUCTION_SCROLL = "thickness-instructions-scroll"
internal const val TAG_INSTRUCTION_HERO = "thickness-instructions-hero"
internal const val TAG_INSTRUCTION_META_GRID = "thickness-instructions-meta"
internal const val TAG_INSTRUCTION_STEPS_PANEL = "thickness-instructions-steps"
internal const val TAG_INSTRUCTION_GUIDELINES_PANEL = "thickness-instructions-guidelines"
internal const val TAG_INSTRUCTION_TIPS_PANEL = "thickness-instructions-tips"
internal const val TAG_INSTRUCTION_ZONES_PANEL = "thickness-instructions-zones"
internal const val TAG_INSTRUCTION_ACTIONS = "thickness-instructions-actions"
