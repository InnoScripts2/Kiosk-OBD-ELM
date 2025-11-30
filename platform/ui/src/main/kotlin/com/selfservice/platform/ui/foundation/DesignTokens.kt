package com.selfservice.platform.ui.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DefaultSans = FontFamily.SansSerif
private val DefaultSerif = FontFamily.Serif
private val DefaultMono = FontFamily.Monospace

@Immutable
data class KioskColorTokens(
        val brandDark: Color = Color(0xFF010916),
        val brandMid: Color = Color(0xFF07142B),
        val brandAccent: Color = Color(0xFF7DD3FC),
        val accentPrimary: Color = Color(0xFF3B82F6),
        val accentSecondary: Color = Color(0xFF60A5FA),
        val textPrimary: Color = Color(0xFFE2E8F0),
        val textOnDark: Color = Color(0xFFF8FBFF),
        val textMutedOnDark: Color = Color(0xDB94A3B8),
        val textMuted: Color = Color(0xBF94A3B8),
        val ghostText: Color = Color(0xFF6B7280),
        val badgeOkBackground: Color = Color(0x2E10B981),
        val badgeOkBorder: Color = Color(0x7310B981),
        val badgeWarnBackground: Color = Color(0x29F59E0B),
        val badgeWarnBorder: Color = Color(0x73F59E0B),
        val badgeDangerBackground: Color = Color(0x29EF4444),
        val badgeDangerBorder: Color = Color(0x73EF4444),
        val badgeNeutralBackground: Color = Color(0x26070C1C),
        val badgeNeutralBorder: Color = Color(0x5994A3B8)
)

@Immutable
data class KioskSurfaceTokens(
        val panel: Color = Color(0xD1070C1C),
        val glass: Color = Color(0xC70D162C),
        val floatingCard: Color = Color(0xD60F172A),
        val floatingCardBorder: Color = Color(0x385EEAD4),
        val floatingCardInsetBorder: Color = Color(0x3D6366F1),
        val outlineMuted: Color = Color(0x3394A3B8),
        val outlineStrong: Color = Color(0x8060A5FA),
        val heroPillBackground: Color = Color(0x2694A3B8),
        val heroPillBorder: Color = Color(0x33E2E8F0),
        val heroPillBorderAccent: Color = Color(0x8060A5FA),
        val ghostButtonBorder: Color = Color(0x5994A3B8)
)

@Immutable
data class KioskGradientTokens(
        val heroBackground: Brush =
                Brush.linearGradient(
                        colors = listOf(Color(0xFF020817), Color(0xFF06142F), Color(0xFF0B1120)),
                        start = Offset.Zero,
                        end = Offset(1280f, 1600f)
                ),
        val floatingPanel: Brush =
                Brush.linearGradient(
                        colors = listOf(Color(0xF20B1834), Color(0xD8040A1C)),
                        start = Offset(0f, 0f),
                        end = Offset(0f, 960f)
                ),
        val floatingCardHighlight: Brush =
                Brush.radialGradient(
                        colors = listOf(Color(0x804A9EFF), Color.Transparent),
                        center = Offset.Zero,
                        radius = 680f
                ),
        val glassOverlay: Brush =
                Brush.linearGradient(
                        colors = listOf(Color(0x3314B8A6), Color(0x222563EB), Color(0x331D4ED8))
                ),
        val panelStroke: Brush =
                Brush.linearGradient(colors = listOf(Color(0x80F472B6), Color(0x8050EFFF)))
)

@Immutable
data class KioskButtonTokens(
        val primaryGradient: Brush =
                Brush.linearGradient(
                        colors = listOf(Color(0xFF4A9EFF), Color(0xFF2D7FD9), Color(0xFF1F5AE2))
                ),
        val primaryShadow: Color = Color(0x732D7FD9),
        val secondaryBackground: Color = Color(0xD10B142F),
        val secondaryBorder: Color = Color(0x7394A3B8),
        val ghostBorder: Color = Color(0x5994A3B8)
)

@Immutable
data class KioskShadowTokens(
        val softElevation: Dp = 18.dp,
        val mediumElevation: Dp = 28.dp,
        val strongElevation: Dp = 40.dp,
        val softColor: Color = Color(0xA6010814),
        val mediumColor: Color = Color(0x8C020817),
        val strongColor: Color = Color(0x8C000000)
)

@Immutable
data class KioskMotionTokens(
        val heroButtonPulseDurationMillis: Int = 4200,
        val heroButtonPulseRange: ClosedFloatingPointRange<Float> = 12f..28f,
        val floatYDurationMillis: Int = 3200
)

@Immutable
data class KioskSpacing(
        val xs: Dp = 4.dp,
        val sm: Dp = 8.dp,
        val md: Dp = 16.dp,
        val lg: Dp = 24.dp,
        val xl: Dp = 32.dp,
        val xxl: Dp = 48.dp,
        val sectionVertical: Dp = 32.dp,
        val sectionHorizontal: Dp = 24.dp,
        val cardPadding: Dp = 20.dp,
        val gridGap: Dp = 32.dp
)

@Immutable
data class KioskRadii(
        val sm: Dp = 4.dp,
        val md: Dp = 8.dp,
        val lg: Dp = 12.dp,
        val xl: Dp = 24.dp,
        val card: Dp = 24.dp,
        val badge: Dp = 20.dp,
        val pill: Dp = 999.dp
)

@Immutable
data class KioskLayoutTokens(
        val gridMaxWidth: Dp = 1200.dp,
        val wideMaxWidth: Dp = 1400.dp,
        val narrowMaxWidth: Dp = 760.dp,
        val buttonHeight: Dp = 58.dp,
        val buttonRadius: Dp = 999.dp,
        val cardMinHeight: Dp = 380.dp,
        val heroBreakpoint: Dp = 1024.dp
)

@Immutable
data class KioskTypographyTokens(
        val heroEyebrow: TextStyle =
                TextStyle(
                        fontFamily = DefaultSans,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp
                ),
        val heroTitle: TextStyle =
                TextStyle(
                        fontFamily = DefaultSerif,
                        fontSize = 48.sp,
                        lineHeight = 52.sp,
                        fontWeight = FontWeight.ExtraBold
                ),
        val heroLead: TextStyle =
                TextStyle(
                        fontFamily = DefaultSans,
                        fontSize = 18.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Medium
                ),
        val sectionTitle: TextStyle =
                TextStyle(
                        fontFamily = DefaultSans,
                        fontSize = 32.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Bold
                ),
        val sectionLead: TextStyle =
                TextStyle(
                        fontFamily = DefaultSans,
                        fontSize = 20.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Medium
                ),
        val body: TextStyle =
                TextStyle(
                        fontFamily = DefaultSans,
                        fontSize = 16.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Normal
                ),
        val bodyMuted: TextStyle =
                TextStyle(
                        fontFamily = DefaultSans,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Normal
                ),
        val statValue: TextStyle =
                TextStyle(
                        fontFamily = DefaultSans,
                        fontSize = 34.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Bold
                ),
        val statLabel: TextStyle =
                TextStyle(
                        fontFamily = DefaultSans,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.12.sp
                ),
        val badge: TextStyle =
                TextStyle(
                        fontFamily = DefaultSans,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold
                ),
        val button: TextStyle =
                TextStyle(
                        fontFamily = DefaultSans,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.1.sp
                ),
        val mono: TextStyle =
                TextStyle(
                        fontFamily = DefaultMono,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Medium
                ),
        val overline: TextStyle =
                TextStyle(
                        fontFamily = DefaultSans,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp
                )
)

@Immutable
data class KioskLayerTokens(
        val backdrop: Float = 0f,
        val backgroundGrid: Float = 1f,
        val backgroundGlow: Float = 2f,
        val app: Float = 10f,
        val overlay: Float = 900f,
        val modal: Float = 1000f,
        val admin: Float = 1050f,
        val devtools: Float = 1100f
)

@Immutable
data class KioskDesignTokens(
        val colors: KioskColorTokens = KioskColorTokens(),
        val surfaces: KioskSurfaceTokens = KioskSurfaceTokens(),
        val gradients: KioskGradientTokens = KioskGradientTokens(),
        val buttons: KioskButtonTokens = KioskButtonTokens(),
        val shadows: KioskShadowTokens = KioskShadowTokens(),
        val motion: KioskMotionTokens = KioskMotionTokens(),
        val spacing: KioskSpacing = KioskSpacing(),
        val radii: KioskRadii = KioskRadii(),
        val layout: KioskLayoutTokens = KioskLayoutTokens(),
        val typography: KioskTypographyTokens = KioskTypographyTokens(),
        val layers: KioskLayerTokens = KioskLayerTokens()
)

val DefaultDesignTokens = KioskDesignTokens()

val LocalDesignTokens = staticCompositionLocalOf { DefaultDesignTokens }
