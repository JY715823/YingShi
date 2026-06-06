package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
internal fun YingShiMediaFrame(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(0.dp),
    selected: Boolean = false,
    memoryActive: Boolean = false,
    topScrimAlpha: Float = 0.18f,
    bottomGlowAlpha: Float = 0.18f,
    borderAlpha: Float = 0.54f,
) {
    val colors = YingShiThemeTokens.colors
    Box(modifier = modifier.clip(shape)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.viewerBackground.copy(alpha = topScrimAlpha),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(58.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            colors.glowWash.copy(alpha = bottomGlowAlpha),
                            colors.viewerText.copy(alpha = 0.08f),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = if (selected || memoryActive) 1.5.dp else 1.dp,
                    color = when {
                        memoryActive -> colors.memoryAccent.copy(alpha = 0.34f)
                        selected -> colors.primaryContainer.copy(alpha = 0.58f)
                        else -> colors.glassStroke.copy(alpha = borderAlpha)
                    },
                    shape = shape,
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(1.dp)
                .border(
                    width = 1.dp,
                    color = colors.viewerText.copy(alpha = if (memoryActive) 0.22f else 0.14f),
                    shape = shape,
                ),
        )
    }
}

@Composable
internal fun YingShiToolSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    highlighted: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier,
        shape = shape,
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(
            width = if (highlighted) 1.5.dp else 1.dp,
            color = if (highlighted) {
                colors.glassStroke.copy(alpha = 0.84f)
            } else {
                colors.dividerSoft.copy(alpha = 0.62f)
            },
        ),
        shadowElevation = if (highlighted) 2.dp else 1.dp,
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.glowWash.copy(alpha = if (highlighted) 0.26f else 0.14f),
                            Color.Transparent,
                        ),
                    ),
                )
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
internal fun YingShiStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    warm: Boolean = false,
) {
    val colors = YingShiThemeTokens.colors
    val radius = YingShiThemeTokens.radius
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius.capsule),
        color = when {
            warm -> colors.memoryContainer.copy(alpha = 0.94f)
            selected -> colors.primaryContainer.copy(alpha = 0.84f)
            else -> colors.sectionBackground.copy(alpha = 0.68f)
        },
        border = BorderStroke(
            1.dp,
            when {
                warm -> colors.memoryAccent.copy(alpha = 0.24f)
                selected -> colors.glassStroke.copy(alpha = 0.74f)
                else -> colors.dividerSoft.copy(alpha = 0.58f)
            },
        ),
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (warm) colors.onMemoryContainer else colors.titleAccent,
            maxLines = 1,
        )
    }
}

@Composable
internal fun YingShiMemoryBadge(
    text: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = YingShiThemeTokens.colors
    val radius = YingShiThemeTokens.radius
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius.capsule),
        color = colors.memoryContainer.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, colors.memoryAccent.copy(alpha = 0.28f)),
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = if (compact) 7.dp else 9.dp,
                vertical = if (compact) 3.dp else 4.dp,
            ),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onMemoryContainer,
            maxLines = 1,
        )
    }
}

internal fun PostDetailMediaUiModel.toAlbumPostPreviewMediaUiModel(): AlbumPostPreviewMediaUiModel {
    return AlbumPostPreviewMediaUiModel(
        id = id,
        palette = palette,
        mediaType = mediaType,
        aspectRatio = aspectRatio,
        mediaSource = mediaSource,
    )
}
