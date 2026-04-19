package com.example.yingshi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val ColorWhite = Color(0xFFFFFFFF)
private val AppSpacing = YingShiSpacing()
private val AppRadius = YingShiRadius()

private val DarkColorScheme = darkColorScheme(
    primary = YingShiBlueLight,
    onPrimary = YingShiBlueDark,
    secondary = YingShiNightMuted,
    onSecondary = YingShiNight,
    background = YingShiNight,
    onBackground = ColorWhite,
    surface = YingShiNightSurface,
    onSurface = ColorWhite,
    surfaceVariant = YingShiBlueDark,
    onSurfaceVariant = YingShiNightMuted,
    outline = YingShiNightDivider,
)

private val LightColorScheme = lightColorScheme(
    primary = YingShiBlue,
    onPrimary = ColorWhite,
    secondary = YingShiBlueGray,
    onSecondary = ColorWhite,
    background = YingShiBackground,
    onBackground = YingShiInk,
    surface = YingShiSurfaceRaised,
    onSurface = YingShiInk,
    surfaceVariant = YingShiMist,
    onSurfaceVariant = YingShiMuted,
    outline = YingShiDivider,
)

@Composable
fun YingShiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = yingShiShapes(AppRadius),
            content = content,
        )
    }
}
