package com.selfservice.platform.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.selfservice.platform.ui.foundation.KioskTokens

/**
 * Базовый контейнер для полноэкранных сцен киоска. Обеспечивает единый фон,
 * ограничения по ширине и шаг вертикального ритма, чтобы выдерживать 8pt сетку
 * и рекомендации дизайн-аудита.
 */
@Composable
fun KioskScreenLayout(
    modifier: Modifier = Modifier,
    background: Brush = KioskTokens.gradients.hero,
    maxWidth: Dp = KioskTokens.layout.wideMaxWidth,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = KioskTokens.spacing.xxl,
        vertical = KioskTokens.spacing.xl
    ),
    verticalSpacing: Dp = KioskTokens.spacing.xxl,
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .applyContentDescription(contentDescription)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = maxWidth)
                .padding(contentPadding)
                .padding(safeInsets)
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content
        )
    }
}

/**
 * Две симметричные колонки 60/40 или 50/50 с настраиваемым отступом.
 * Используется на hero и welcome-экранах, чтобы устранить асимметрию из аудита.
 */
@Composable
fun KioskTwoColumn(
    modifier: Modifier = Modifier,
    primaryWeight: Float = 6f,
    secondaryWeight: Float = 6f,
    horizontalSpacing: Dp = KioskTokens.spacing.xxl,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    contentDescription: String? = null,
    primaryContent: @Composable ColumnScope.() -> Unit,
    secondaryContent: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth().applyContentDescription(contentDescription),
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        verticalAlignment = verticalAlignment
    ) {
        Column(
            modifier = Modifier.weight(primaryWeight),
            verticalArrangement = Arrangement.spacedBy(KioskTokens.spacing.md),
            content = primaryContent
        )
        Column(
            modifier = Modifier.weight(secondaryWeight),
            verticalArrangement = Arrangement.spacedBy(KioskTokens.spacing.md),
            content = secondaryContent
        )
    }
}

/**
 * Унифицированная строка CTA с контролируемым отступом и выравниванием.
 */
@Composable
fun KioskActionRow(
    modifier: Modifier = Modifier,
    spacing: Dp = KioskTokens.spacing.md,
    alignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    contentDescription: String? = null,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .applyContentDescription(contentDescription),
        horizontalArrangement = Arrangement.spacedBy(spacing, alignment),
        content = content
    )
}

private fun Modifier.applyContentDescription(description: String?): Modifier {
    if (description.isNullOrBlank()) return this
    return this.semantics { this.contentDescription = description }
}
