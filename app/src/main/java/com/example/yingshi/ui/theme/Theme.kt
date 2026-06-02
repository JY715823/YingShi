package com.example.yingshi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val AppSpacing = YingShiSpacing()
private val AppRadius = YingShiRadius()
private val AppColors = YingShiColors()

private val DarkColorScheme = darkColorScheme(
    primary = YingShiViewerAccent,
    onPrimary = YingShiViewerBackground,
    primaryContainer = YingShiViewerSurface,
    onPrimaryContainer = YingShiViewerText,
    secondary = YingShiViewerTextSecondary,
    onSecondary = YingShiViewerBackground,
    background = YingShiViewerBackground,
    onBackground = YingShiViewerText,
    surface = YingShiViewerSurface,
    onSurface = YingShiViewerText,
    surfaceVariant = Color(0xFF203A44),
    onSurfaceVariant = YingShiViewerTextSecondary,
    outline = YingShiNightDivider,
    outlineVariant = Color(0xFF2F4850),
)

private val LightColorScheme = lightColorScheme(
    primary = YingShiPrimaryAction,
    onPrimary = YingShiRaisedSurface,
    primaryContainer = YingShiPrimaryContainer,
    onPrimaryContainer = YingShiOnPrimaryContainer,
    secondary = YingShiSoftGreenAction,
    onSecondary = YingShiRaisedSurface,
    secondaryContainer = YingShiSoftGreenContainer,
    onSecondaryContainer = YingShiTextPrimary,
    tertiary = YingShiMemoryAccent,
    onTertiary = YingShiRaisedSurface,
    tertiaryContainer = YingShiMemoryContainer,
    onTertiaryContainer = YingShiOnMemoryContainer,
    background = YingShiAppBackground,
    onBackground = YingShiTextPrimary,
    surface = YingShiRaisedSurface,
    onSurface = YingShiTextPrimary,
    surfaceVariant = YingShiSectionBackground,
    onSurfaceVariant = YingShiTextSecondary,
    outline = YingShiGlassStroke,
    outlineVariant = YingShiDividerSoft,
)

@Composable
fun YingShiTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(
        LocalYingShiSpacing provides AppSpacing,
        LocalYingShiRadius provides AppRadius,
        LocalYingShiColors provides AppColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = yingShiShapes(AppRadius),
            content = content,
        )
    }
}
