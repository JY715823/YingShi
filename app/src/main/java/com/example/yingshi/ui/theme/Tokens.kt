package com.example.yingshi.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
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

internal val LocalYingShiSpacing = staticCompositionLocalOf { YingShiSpacing() }
internal val LocalYingShiRadius = staticCompositionLocalOf { YingShiRadius() }

object YingShiThemeTokens {
    val spacing: YingShiSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalYingShiSpacing.current

    val radius: YingShiRadius
        @Composable
        @ReadOnlyComposable
        get() = LocalYingShiRadius.current
}
