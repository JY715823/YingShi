package com.example.yingshi.feature.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
internal fun SmallAlbumAtmosphereBackdrop(
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val motion = YingShiThemeTokens.motion
    val atmosphereAlpha = motion.feedAtmosphereAlpha * 1.16f
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.glowWash.copy(alpha = 0.30f * atmosphereAlpha),
                            colors.sectionBackground.copy(alpha = 0.16f * atmosphereAlpha),
                            Color.Transparent,
                        ),
                        center = androidx.compose.ui.geometry.Offset(0f, 0f),
                        radius = 820f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.memoryContainer.copy(alpha = 0.20f * atmosphereAlpha),
                            Color.Transparent,
                        ),
                        center = androidx.compose.ui.geometry.Offset(980f, 180f),
                        radius = 640f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.glowWash.copy(alpha = 0.12f * atmosphereAlpha),
                            Color.Transparent,
                            colors.sectionBackground.copy(alpha = 0.10f * atmosphereAlpha),
                        ),
                    ),
                ),
        )
    }
}

@Composable
internal fun SmallAlbumMediaAtmospherePanel(
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val motion = YingShiThemeTokens.motion
    val atmosphereAlpha = motion.feedAtmosphereAlpha
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.glowWash.copy(alpha = 0.16f * atmosphereAlpha),
                        Color.Transparent,
                        colors.sectionBackground.copy(alpha = 0.12f * atmosphereAlpha),
                    ),
                ),
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colors.glowWash.copy(alpha = 0.26f * atmosphereAlpha),
                        Color.Transparent,
                    ),
                    center = androidx.compose.ui.geometry.Offset(0f, 0f),
                    radius = 720f,
                ),
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colors.memoryContainer.copy(alpha = 0.18f * atmosphereAlpha),
                        Color.Transparent,
                    ),
                    center = androidx.compose.ui.geometry.Offset(980f, 180f),
                    radius = 620f,
                ),
            ),
    )
}
