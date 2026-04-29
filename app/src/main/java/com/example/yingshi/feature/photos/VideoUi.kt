package com.example.yingshi.feature.photos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.theme.YingShiThemeTokens

internal enum class VideoGlyphState {
    PLAY,
    PAUSE,
}

@Composable
internal fun VideoMediaMarker(
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius.capsule),
        color = Color.Black.copy(alpha = 0.24f),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (showLabel) spacing.sm else spacing.xs,
                vertical = spacing.xs,
            ),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(if (showLabel) 18.dp else 16.dp)
                    .background(Color.White.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                VideoGlyph(
                    state = VideoGlyphState.PLAY,
                    tint = Color.White.copy(alpha = 0.94f),
                    modifier = Modifier.size(if (showLabel) 12.dp else 10.dp),
                )
            }
            if (showLabel) {
                Text(
                    text = "VIDEO",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.90f),
                )
            }
        }
    }
}

@Composable
internal fun VideoGlyph(
    state: VideoGlyphState,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        when (state) {
            VideoGlyphState.PLAY -> {
                val path = Path().apply {
                    moveTo(size.width * 0.26f, size.height * 0.18f)
                    lineTo(size.width * 0.78f, size.height * 0.50f)
                    lineTo(size.width * 0.26f, size.height * 0.82f)
                    close()
                }
                drawPath(path = path, color = tint)
            }

            VideoGlyphState.PAUSE -> {
                val barWidth = size.width * 0.22f
                val gap = size.width * 0.12f
                val top = size.height * 0.18f
                val barHeight = size.height * 0.64f
                drawRect(
                    color = tint,
                    topLeft = Offset(x = size.width * 0.22f, y = top),
                    size = Size(width = barWidth, height = barHeight),
                )
                drawRect(
                    color = tint,
                    topLeft = Offset(x = size.width * 0.22f + barWidth + gap, y = top),
                    size = Size(width = barWidth, height = barHeight),
                )
            }
        }
    }
}
