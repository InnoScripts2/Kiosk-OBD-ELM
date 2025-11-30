package com.selfservice.platform.ui.foundation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val kioskDarkColors =
        darkColorScheme(
                primary = Color(0xFF3B82F6),
                onPrimary = Color(0xFFF8FAFC),
                primaryContainer = Color(0xFF1D4ED8),
                onPrimaryContainer = Color(0xFFE0F2FE),
                secondary = Color(0xFF60A5FA),
                onSecondary = Color(0xFFF8FAFC),
                background = Color(0xFF010916),
                onBackground = Color(0xFFE2E8F0),
                surface = Color(0xFF07142B),
                onSurface = Color(0xFFE2E8F0),
                surfaceVariant = Color(0xFF101B34),
                onSurfaceVariant = Color(0xFFA5B4FC),
                tertiary = Color(0xFF14B8A6),
                onTertiary = Color(0xFFE6FFFB),
                error = Color(0xFFEF4444),
                onError = Color(0xFFFFFBFF),
                errorContainer = Color(0xFF7F1D1D),
                outline = Color(0xFF4C566A),
                scrim = Color(0xCC000000)
        )

private val kioskTypography = DefaultDesignTokens.typography.asMaterialTypography()

private val kioskShapes =
        Shapes(
                small = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
        )

data class KioskGradients(val hero: Brush, val glass: Brush, val panelStroke: Brush)

private val LocalSpacing = staticCompositionLocalOf { DefaultDesignTokens.spacing }
private val LocalRadii = staticCompositionLocalOf { DefaultDesignTokens.radii }
private val LocalGradients = staticCompositionLocalOf { DefaultDesignTokens.toKioskGradients() }
private val LocalLayout = staticCompositionLocalOf { DefaultDesignTokens.layout }
private val LocalTypographyTokens = staticCompositionLocalOf { DefaultDesignTokens.typography }
private val LocalLayers = staticCompositionLocalOf { DefaultDesignTokens.layers }

@Composable
fun KioskTheme(
        darkTheme: Boolean = isSystemInDarkTheme(),
        designTokens: KioskDesignTokens = DefaultDesignTokens,
        spacing: KioskSpacing = designTokens.spacing,
        radii: KioskRadii = designTokens.radii,
        gradients: KioskGradients = designTokens.toKioskGradients(),
        layout: KioskLayoutTokens = designTokens.layout,
        typographyTokens: KioskTypographyTokens = designTokens.typography,
        layers: KioskLayerTokens = designTokens.layers,
        typography: Typography = typographyTokens.asMaterialTypography(),
        shapes: Shapes = kioskShapes,
        content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) kioskDarkColors else kioskDarkColors
    CompositionLocalProvider(
            LocalSpacing provides spacing,
            LocalRadii provides radii,
            LocalDesignTokens provides designTokens,
            LocalGradients provides gradients,
            LocalLayout provides layout,
            LocalTypographyTokens provides typographyTokens,
            LocalLayers provides layers
    ) {
        MaterialTheme(
                colorScheme = colorScheme,
                typography = typography,
                shapes = shapes,
                content = content
        )
    }
}

object KioskTokens {
    val spacing: KioskSpacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current

    val radii: KioskRadii
        @Composable @ReadOnlyComposable get() = LocalRadii.current

    val gradients: KioskGradients
        @Composable @ReadOnlyComposable get() = LocalGradients.current

    val design: KioskDesignTokens
        @Composable @ReadOnlyComposable get() = LocalDesignTokens.current

    val layout: KioskLayoutTokens
        @Composable @ReadOnlyComposable get() = LocalLayout.current

    val typography: KioskTypographyTokens
        @Composable @ReadOnlyComposable get() = LocalTypographyTokens.current

    val layers: KioskLayerTokens
        @Composable @ReadOnlyComposable get() = LocalLayers.current
}

private fun KioskDesignTokens.toKioskGradients(): KioskGradients =
        KioskGradients(
                hero = gradients.heroBackground,
                glass = gradients.glassOverlay,
                panelStroke = gradients.panelStroke
        )

private fun KioskTypographyTokens.asMaterialTypography(): Typography =
        Typography(
                displayLarge = heroTitle,
                displayMedium = heroLead,
                displaySmall = sectionLead,
                headlineLarge = sectionTitle,
                headlineMedium = statValue,
                headlineSmall = badge,
                titleLarge = sectionLead,
                titleMedium = body,
                titleSmall = bodyMuted,
                bodyLarge = body,
                bodyMedium = bodyMuted,
                bodySmall = overline,
                labelLarge = button,
                labelMedium = badge,
                labelSmall = heroEyebrow
        )
