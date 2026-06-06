package com.example.yingshi.feature.photos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius.capsule),
        color = colors.viewerBackground.copy(alpha = 0.28f),
        border = BorderStroke(1.dp, colors.viewerAccent.copy(alpha = 0.12f)),
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
                    .background(colors.viewerSurface.copy(alpha = 0.34f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                VideoGlyph(
                    state = VideoGlyphState.PLAY,
                    tint = colors.viewerText.copy(alpha = 0.94f),
                    modifier = Modifier.size(if (showLabel) 12.dp else 10.dp),
                )
            }
            if (showLabel) {
                Text(
                    text = "VIDEO",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.viewerText.copy(alpha = 0.90f),
                )
            }
        }
    }
}

@Composable
internal fun InlineVideoPlaybackButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = if (enabled) modifier.clickable(onClick = onClick) else modifier,
        shape = CircleShape,
        color = colors.viewerBackground.copy(alpha = 0.34f),
        border = BorderStroke(
            width = 1.dp,
            color = colors.viewerAccent.copy(alpha = 0.18f),
        ),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .padding(9.dp),
            contentAlignment = Alignment.Center,
        ) {
            VideoGlyph(
                state = if (isPlaying) VideoGlyphState.PAUSE else VideoGlyphState.PLAY,
                tint = colors.viewerText.copy(alpha = 0.94f),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun VideoDurationBadge(
    durationMillis: Long?,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val label = formatVideoDurationLabel(durationMillis) ?: return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = colors.viewerBackground.copy(alpha = 0.36f),
        border = BorderStroke(1.dp, colors.viewerAccent.copy(alpha = 0.14f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.viewerText.copy(alpha = 0.92f),
        )
    }
}

internal fun formatVideoDurationLabel(durationMillis: Long?): String? {
    val safeMillis = durationMillis?.takeIf { it > 0L } ?: return null
    val totalSeconds = (safeMillis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
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
