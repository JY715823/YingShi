package com.example.yingshi.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
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
)

internal val LocalYingShiSpacing = staticCompositionLocalOf { YingShiSpacing() }
internal val LocalYingShiRadius = staticCompositionLocalOf { YingShiRadius() }
internal val LocalYingShiColors = staticCompositionLocalOf { YingShiColors() }

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
}
