package com.selfservice.kiosk.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfservice.kiosk.ui.state.PromoHighlightsCatalog
import com.selfservice.platform.ui.components.KioskActionRow
import com.selfservice.platform.ui.components.KioskPanel
import com.selfservice.platform.ui.components.KioskPrimaryButton
import com.selfservice.platform.ui.components.KioskScreenLayout
import com.selfservice.platform.ui.components.KioskSecondaryButton
import com.selfservice.platform.ui.components.KioskTwoColumn
import com.selfservice.platform.ui.foundation.KioskTokens

/**
 * Экран 1: Attract (Ожидание)
 * Начальный экран киоска с логотипом и триггером
 */
@Composable
fun AttractScreen(
    onTap: () -> Unit,
    onOpenDetails: () -> Unit = onTap
) {
    val attractGradient = KioskTokens.gradients.hero
    val spacing = KioskTokens.spacing
    val highlights = remember { PromoHighlightsCatalog.heroHighlights() }
    val quickStats = remember { PromoHighlightsCatalog.quickStats() }

    KioskScreenLayout(
        modifier = Modifier
            .fillMaxSize()
            .testTag("attract-screen")
            .clickable { onTap() },
        background = attractGradient,
        verticalSpacing = spacing.xl,
        contentDescription = "Экран привлечения клиентов"
    ) {
        HeroRow(
            quickStats = quickStats,
            onTap = onTap,
            onOpenDetails = onOpenDetails
        )

        HighlightsPanel(highlights = highlights)

        TapAnywhereHint()
    }
}

@Composable
private fun HeroRow(
    quickStats: List<PromoHighlightsCatalog.QuickStat>,
    onTap: () -> Unit,
    onOpenDetails: () -> Unit
) {
    val spacing = KioskTokens.spacing
    val design = KioskTokens.design
    KioskTwoColumn(
        modifier = Modifier.testTag("hero-row"),
        primaryWeight = 6f,
        secondaryWeight = 6f,
        horizontalSpacing = spacing.xxl,
        verticalAlignment = Alignment.CenterVertically,
        primaryContent = {
            HeroBadge(
                text = "Киоск самообслуживания",
                modifier = Modifier.align(Alignment.Start)
            )

            Text(
                text = "Толщинометрия ЛКП и диагностика OBD-II",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = design.colors.textOnDark
            )

            Text(
                text = "Киоск выдаёт оборудование, подсказывает шаги и отправляет отчёт. В среднем от первого касания до готовых результатов — не более 12 минут.",
                style = MaterialTheme.typography.titleMedium,
                color = design.colors.textMutedOnDark
            )

            HeroCtas(onTap = onTap, onOpenDetails = onOpenDetails)
        },
        secondaryContent = {
            HeroStatsPanel(
                modifier = Modifier.fillMaxWidth(),
                stats = quickStats
            )
        }
    )
}

@Composable
private fun HeroCtas(
    onTap: () -> Unit,
    onOpenDetails: () -> Unit
) {
    val spacing = KioskTokens.spacing
    KioskActionRow(
        spacing = spacing.md,
        alignment = Alignment.Start
    ) {
        KioskPrimaryButton(
            modifier = Modifier.weight(1f),
            onClick = onTap,
            contentDescription = "Начать диагностику"
        ) {
            Text(
                text = "НАЧАТЬ ДИАГНОСТИКУ",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.12.sp)
            )
        }

        KioskSecondaryButton(
            modifier = Modifier.weight(1f),
            onClick = onOpenDetails,
            contentDescription = "Открыть подробности"
        ) {
            Text(
                text = "УЗНАТЬ ПОДРОБНЕЕ",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.12.sp)
            )
        }
    }
}

@Composable
private fun TapAnywhereHint() {
    val spacing = KioskTokens.spacing
    val design = KioskTokens.design
    Surface(
        shape = MaterialTheme.shapes.large,
        color = design.surfaces.panel,
        modifier = Modifier
            .testTag("attract-tap-hint")
            .border(1.dp, design.surfaces.outlineMuted, MaterialTheme.shapes.large)
    ) {
        Text(
            text = "Нажмите в любом месте, чтобы начать",
            modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.md),
            style = MaterialTheme.typography.titleMedium,
            color = design.colors.textOnDark,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HighlightsPanel(highlights: List<PromoHighlightsCatalog.PromoHighlight>) {
    KioskPanel(
        headline = "Что вы получаете",
        supportingText = "Выбирайте услугу и получайте полный цикл без очередей",
        contentDescription = "Быстрые преимущества сервиса"
    ) {
        HighlightsGrid(highlights = highlights)
    }
}

@Composable
private fun HighlightCard(
    highlight: PromoHighlightsCatalog.PromoHighlight,
    modifier: Modifier = Modifier
) {
    val design = KioskTokens.design
    Surface(
        modifier = modifier
            .border(1.dp, design.surfaces.outlineMuted, MaterialTheme.shapes.medium),
        color = design.surfaces.panel,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(KioskTokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(KioskTokens.spacing.sm)
        ) {
            Text(
                text = highlight.accent.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = highlight.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = highlight.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun HighlightsGrid(highlights: List<PromoHighlightsCatalog.PromoHighlight>) {
    val spacing = KioskTokens.spacing
    LazyVerticalGrid(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("attract-highlights"),
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
        userScrollEnabled = false
    ) {
        items(highlights, key = { it.title }) { highlight ->
            HighlightCard(highlight = highlight)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun HeroStatsPanel(
    stats: List<PromoHighlightsCatalog.QuickStat>,
    modifier: Modifier = Modifier
) {
    val spacing = KioskTokens.spacing
    KioskPanel(
        modifier = modifier,
        headline = "24/7 киоск рядом",
        supportingText = "Выбирайте услугу, оплачивайте QR и получайте оборудование без очередей",
        contentDescription = "Статистика скорости обслуживания"
    ) {
        LazyVerticalGrid(
            modifier = Modifier.fillMaxWidth(),
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
            userScrollEnabled = false
        ) {
            items(stats, key = { it.label }) { stat ->
                HeroStatCard(stat = stat)
            }
        }
    }
}

@Composable
private fun HeroStatCard(stat: PromoHighlightsCatalog.QuickStat) {
    val spacing = KioskTokens.spacing
    val design = KioskTokens.design
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(KioskTokens.radii.lg))
            .background(design.surfaces.panel)
            .border(1.dp, design.surfaces.outlineMuted, RoundedCornerShape(KioskTokens.radii.lg))
            .padding(spacing.md)
    ) {
        Text(
            text = stat.value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(spacing.xs))
        Text(
            text = stat.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stat.helper,
            style = MaterialTheme.typography.bodySmall,
            color = design.colors.textMutedOnDark
        )
    }
}

@Composable
private fun HeroBadge(text: String, modifier: Modifier = Modifier) {
    val spacing = KioskTokens.spacing
    val design = KioskTokens.design
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(KioskTokens.radii.badge))
            .background(design.surfaces.heroPillBackground)
            .border(1.dp, design.surfaces.heroPillBorderAccent, RoundedCornerShape(KioskTokens.radii.badge))
            .padding(horizontal = spacing.md, vertical = spacing.xs)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = design.colors.textOnDark
        )
    }
}
