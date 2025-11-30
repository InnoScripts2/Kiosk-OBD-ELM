package com.selfservice.platform.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfservice.platform.ui.foundation.KioskTokens

@Composable
fun KioskPrimaryButton(
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        onClick: () -> Unit,
        contentDescription: String? = null,
        content: @Composable () -> Unit
) {
    val design = KioskTokens.design
    val shape = RoundedCornerShape(KioskTokens.radii.pill)
    val motion = design.motion
    val transition = rememberInfiniteTransition(label = "hero-button-pulse")
    val pulseElevationValue =
            transition.animateFloat(
                            initialValue = motion.heroButtonPulseRange.start,
                            targetValue = motion.heroButtonPulseRange.endInclusive,
                            animationSpec =
                                    infiniteRepeatable(
                                            animation =
                                                    tween(
                                                            durationMillis =
                                                                    motion.heroButtonPulseDurationMillis,
                                                            easing = FastOutSlowInEasing
                                                    ),
                                            repeatMode = RepeatMode.Reverse
                                    ),
                            label = "hero-button-shadow"
                    )
                    .value
    val elevation = if (enabled) pulseElevationValue.dp else 0.dp
    val buttonModifier = modifier.applyContentDescription(contentDescription)

    Button(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            modifier =
                    buttonModifier.heightIn(min = KioskTokens.layout.buttonHeight)
                            .shadow(
                                    elevation = elevation,
                                    shape = shape,
                                    clip = false,
                                    ambientColor = design.buttons.primaryShadow,
                                    spotColor = design.buttons.primaryShadow
                            ),
            colors =
                    ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = design.colors.textOnDark,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = design.colors.textOnDark.copy(alpha = 0.4f)
                    ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            contentPadding = PaddingValues(0.dp)
    ) {
        val buttonAlpha = if (enabled) 1f else 0.35f
        Box(
                modifier =
                        Modifier.fillMaxWidth()
                                .clip(shape)
                                .background(design.buttons.primaryGradient, shape)
                                .alpha(buttonAlpha)
                                .padding(
                                        horizontal = KioskTokens.spacing.xl,
                                        vertical = KioskTokens.spacing.md
                                ),
                contentAlignment = Alignment.Center
        ) {
            val style =
                    MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.12.sp
                    )
            ProvideTextStyle(value = style) { content() }
        }
    }
}

@Composable
fun KioskSecondaryButton(
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        onClick: () -> Unit,
        contentDescription: String? = null,
        content: @Composable () -> Unit
) {
    val design = KioskTokens.design
    val pill = RoundedCornerShape(KioskTokens.radii.pill)
    val buttonModifier = modifier.applyContentDescription(contentDescription)
    Button(
            onClick = onClick,
            enabled = enabled,
            shape = pill,
            modifier = buttonModifier.heightIn(min = 52.dp),
            colors =
                    ButtonDefaults.buttonColors(
                            containerColor = design.buttons.secondaryBackground,
                            contentColor = design.colors.textOnDark,
                            disabledContainerColor =
                                    design.buttons.secondaryBackground.copy(alpha = 0.35f),
                            disabledContentColor = design.colors.textOnDark.copy(alpha = 0.4f)
                    ),
            border = BorderStroke(width = 1.dp, color = design.buttons.secondaryBorder),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            contentPadding =
                    PaddingValues(
                            horizontal = KioskTokens.spacing.lg,
                            vertical = KioskTokens.spacing.sm
                    )
    ) {
        val style =
                MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.08.sp
                )
        ProvideTextStyle(value = style) { content() }
    }
}

@Composable
fun KioskGhostButton(
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        onClick: () -> Unit,
        contentDescription: String? = null,
        content: @Composable () -> Unit
) {
    val pill = RoundedCornerShape(KioskTokens.radii.pill)
    val design = KioskTokens.design
    val buttonModifier = modifier.applyContentDescription(contentDescription)
    Button(
            onClick = onClick,
            enabled = enabled,
            shape = pill,
            modifier = buttonModifier.heightIn(min = 48.dp),
            colors =
                    ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = design.colors.textOnDark,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = design.colors.textOnDark.copy(alpha = 0.4f)
                    ),
            border = BorderStroke(width = 1.dp, color = design.buttons.ghostBorder),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            contentPadding =
                    PaddingValues(
                            horizontal = KioskTokens.spacing.lg,
                            vertical = KioskTokens.spacing.xs
                    )
    ) {
        val style =
                MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.08.sp
                )
        ProvideTextStyle(value = style) { content() }
    }
}

@Composable
fun KioskPanel(
        modifier: Modifier = Modifier,
        headline: String? = null,
        supportingText: String? = null,
        contentDescription: String? = null,
        content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(KioskTokens.radii.xl)
    val design = KioskTokens.design
    val shadow = design.shadows
    val panelModifier = modifier.applyContentDescription(contentDescription)
    Box(
            modifier =
                    panelModifier.shadow(
                                    elevation = shadow.mediumElevation,
                                    shape = shape,
                                    clip = false,
                                    ambientColor = shadow.mediumColor,
                                    spotColor = shadow.mediumColor
                            )
                            .clip(shape)
                            .background(design.gradients.floatingPanel, shape)
                            .border(BorderStroke(1.dp, design.surfaces.floatingCardBorder), shape)
    ) {
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .padding(KioskTokens.spacing.sm)
                                .border(
                                        BorderStroke(1.dp, design.surfaces.floatingCardInsetBorder),
                                        shape
                                )
        )
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .alpha(0.45f)
                                .background(design.gradients.floatingCardHighlight, shape)
        )
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .background(design.gradients.glassOverlay, shape)
                                .padding(KioskTokens.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(KioskTokens.spacing.md)
        ) {
            if (!headline.isNullOrBlank()) {
                Text(
                        text = headline,
                        style = MaterialTheme.typography.titleMedium,
                        color = design.colors.textOnDark,
                        fontWeight = FontWeight.SemiBold
                )
            }
            if (!supportingText.isNullOrBlank()) {
                Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = design.colors.textMutedOnDark
                )
            }
            content()
        }
    }
}

private fun Modifier.applyContentDescription(description: String?): Modifier {
    return if (description.isNullOrBlank()) this
    else this.semantics { this.contentDescription = description }
}
