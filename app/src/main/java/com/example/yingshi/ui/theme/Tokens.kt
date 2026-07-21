package com.example.yingshi.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class YingShiSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

@Immutable
data class YingShiRadius(
    val sm: Dp = 12.dp,
    val md: Dp = 18.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 28.dp,
    val capsule: Dp = 999.dp,
)

@Immutable
data class YingShiColors(
    val appBackground: Color = YingShiAppBackground,
    val sectionBackground: Color = YingShiSectionBackground,
    val raisedSurface: Color = YingShiRaisedSurface,
    val selectedPillBg: Color = YingShiSelectedPillBg,
    val primaryContainer: Color = YingShiPrimaryContainer,
    val onPrimaryContainer: Color = YingShiOnPrimaryContainer,
    val primaryAction: Color = YingShiPrimaryAction,
    val primaryActionPressed: Color = YingShiPrimaryActionPressed,
    val titleAccent: Color = YingShiTitleAccent,
    val softGreenContainer: Color = YingShiSoftGreenContainer,
    val softGreenAction: Color = YingShiSoftGreenAction,
    val goldAccent: Color = YingShiGoldAccent,
    val memoryAccent: Color = YingShiMemoryAccent,
    val memoryContainer: Color = YingShiMemoryContainer,
    val onMemoryContainer: Color = YingShiOnMemoryContainer,
    val memoryWash: Color = YingShiMemoryWash,
    val dividerSoft: Color = YingShiDividerSoft,
    val glassStroke: Color = YingShiGlassStroke,
    val glowWash: Color = YingShiGlowWash,
    val textPrimary: Color = YingShiTextPrimary,
    val textSecondary: Color = YingShiTextSecondary,
    val viewerBackground: Color = YingShiViewerBackground,
    val viewerSurface: Color = YingShiViewerSurface,
    val viewerAccent: Color = YingShiViewerAccent,
    val viewerText: Color = YingShiViewerText,
    val viewerTextSecondary: Color = YingShiViewerTextSecondary,
    // Viewer Overlay 材质 token（派生自 viewerAccent，不引入新色相）
    val viewerOverlayEdgeGlow: Color = YingShiViewerOverlayEdgeGlow,
    val viewerOverlayBorder: Color = YingShiViewerOverlayBorder,
    // 标题光晕
    val titleAuraWarmGlow: Color = YingShiTitleAuraWarmGlow,
    val titleAuraCoolGlow: Color = YingShiTitleAuraCoolGlow,
    val titleAuraWarmSparkle: Color = YingShiTitleAuraWarmSparkle,
    val titleAuraCoolSparkle: Color = YingShiTitleAuraCoolSparkle,
    val titleAuraBottomCool: Color = YingShiTitleAuraBottomCool,
    val titleAuraBottomWarm: Color = YingShiTitleAuraBottomWarm,
    // 标题文字层
    val titleTextShadowGlow: Color = YingShiTitleTextShadowGlow,
    val titleTextShadowWarm: Color = YingShiTitleTextShadowWarm,
    val titleTextGlowWarm: Color = YingShiTitleTextGlowWarm,
    val titleTextShadowCool: Color = YingShiTitleTextShadowCool,
    val titleTextGlowCool: Color = YingShiTitleTextGlowCool,
    // Destructive
    val destructiveContainer: Color = YingShiDestructiveContainer,
    val destructive: Color = YingShiDestructive,
    val onDestructiveContainer: Color = YingShiOnDestructiveContainer,
    // 玻璃表面
    val glassSurfaceBase: Color = YingShiGlassSurfaceBase,
)

@Immutable
data class YingShiMotion(
    val tapMillis: Int = 150,
    val stateMillis: Int = 180,
    val routeMillis: Int = 220,
    val sectionMillis: Int = 240,
    val floatingMillis: Int = 220,
    val viewerTransitionMillis: Int = 320,
    val viewerNoticeMillis: Int = 240,
    // Viewer Overlay 毛玻璃模糊半径（API 31+ 生效，低版本 fallback 半透明）
    val viewerScrimBlur: Dp = 18.dp,
    val viewerCapsuleBlur: Dp = 12.dp,
    val noticeMillis: Int = 220,
    val viewerNoticeVisibleMillis: Int = 1600,
    val noticeVisibleMillis: Int = 1700,
    val commentPreviewMillis: Int = 240,
    val memoryGlowMillis: Int = 1100,
    val mediaFadeMillis: Int = 180,
    val densityPreviewMillis: Int = 140,
    val densityMorphMillis: Int = 280,
    val densitySettleMillis: Int = 160,
    val hapticEnabled: Boolean = true,
    val pressedScale: Float = 0.965f,
    val selectedMediaScale: Float = 0.972f,
    val mediaEnterScale: Float = 0.985f,
    val routeEnterScale: Float = 0.992f,
    val sectionEnterScale: Float = 0.986f,
    val densityPreviewZoomInScale: Float = 1.04f,
    val densityPreviewZoomOutScale: Float = 0.94f,
    val ambientGlowAlpha: Float = 0.18f,
    val feedAtmosphereAlpha: Float = 0.34f,
    val memorySweepAlpha: Float = 0.18f,
    val easing: Easing = FastOutSlowInEasing,
)

@Immutable
data class YingShiTypography(
    val cardTitle: TextStyle = TextStyle.Default,
    val sectionTitle: TextStyle = TextStyle.Default,
    val body: TextStyle = TextStyle.Default,
    val caption: TextStyle = TextStyle.Default,
    val statNumber: TextStyle = TextStyle.Default,
    val statLabel: TextStyle = TextStyle.Default,
)

internal val LocalYingShiSpacing = staticCompositionLocalOf { YingShiSpacing() }
internal val LocalYingShiRadius = staticCompositionLocalOf { YingShiRadius() }
internal val LocalYingShiColors = staticCompositionLocalOf { YingShiColors() }
internal val LocalYingShiMotion = staticCompositionLocalOf { YingShiMotion() }

object YingShiThemeTokens {
    val spacing: YingShiSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalYingShiSpacing.current

    val radius: YingShiRadius
        @Composable
        @ReadOnlyComposable
        get() = LocalYingShiRadius.current

    val colors: YingShiColors
        @Composable
        @ReadOnlyComposable
        get() = LocalYingShiColors.current

    val motion: YingShiMotion
        @Composable
        @ReadOnlyComposable
        get() = LocalYingShiMotion.current

    val typography: YingShiTypography
        @Composable
        @ReadOnlyComposable
        get() = YingShiTypography(
            cardTitle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            sectionTitle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            body = MaterialTheme.typography.bodyMedium,
            caption = MaterialTheme.typography.labelSmall,
            statNumber = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            statLabel = MaterialTheme.typography.labelMedium,
        )
}
