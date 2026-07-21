package com.example.yingshi.feature.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
internal fun SystemMediaViewerTopScrim(modifier: Modifier = Modifier) {
    val colors = YingShiThemeTokens.colors
    Box(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colors.viewerBackground.copy(alpha = 0.62f),
                        colors.viewerBackground.copy(alpha = 0.24f),
                        Color.Transparent,
                    ),
                ),
            )
            .drawBehind {
                drawLine(
                    color = colors.viewerOverlayEdgeGlow,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            },
    )
}

@Composable
internal fun SystemMediaViewerBottomScrim(modifier: Modifier = Modifier) {
    val colors = YingShiThemeTokens.colors
    Box(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        colors.viewerBackground.copy(alpha = 0.20f),
                        colors.viewerBackground.copy(alpha = 0.58f),
                    ),
                ),
            )
            .drawBehind {
                drawLine(
                    color = colors.viewerOverlayEdgeGlow,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            },
    )
}
