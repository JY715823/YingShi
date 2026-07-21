package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SystemMediaViewerTopBar(
    currentIndex: Int,
    totalCount: Int,
    showMenu: Boolean,
    overlaysVisible: Boolean,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (overlaysVisible) 1f else 0.35f),
        horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SystemMediaViewerCircleButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "返回",
            onClick = onBack,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "系统媒体",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.viewerText,
            )
            Text(
                text = if (totalCount > 0) "${currentIndex + 1} / $totalCount" else "0 / 0",
                style = MaterialTheme.typography.bodySmall,
                color = colors.viewerTextSecondary,
            )
        }
        if (showMenu) {
            SystemMediaViewerCircleButton(
                icon = Icons.Rounded.MoreHoriz,
                contentDescription = "媒体操作",
                onClick = onOpenMenu,
            )
        }
    }
}

@Composable
internal fun SystemMediaViewerInfoCard(
    item: SystemMediaItem,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            colors.viewerSurface.copy(alpha = 0.82f),
                            colors.viewerSurface.copy(alpha = 0.55f),
                        ),
                    ),
                )
            },
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = Color.Transparent,
        border = BorderStroke(1.dp, colors.viewerOverlayBorder),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.type.label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.viewerText,
            )
            Text(
                text = formatSystemViewerDisplayTime(item.displayTimeMillis),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = colors.viewerTextSecondary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (item.isImportedToApp) "已导入" else "未导入",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (item.isImportedToApp) colors.viewerAccent else colors.viewerTextSecondary,
            )
        }
    }
}

@Composable
internal fun SystemMediaVideoControls(
    playbackState: ViewerVideoPlaybackState,
    onTogglePlayback: () -> Unit,
    onSeekPlayback: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val durationMillis = (playbackState.durationMillis ?: 0L).coerceAtLeast(0L)
    val progressFraction = if (durationMillis <= 0L) 0f else {
        (playbackState.progressMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
    }
    var draggedFraction by remember(playbackState.mediaId) { mutableStateOf<Float?>(null) }
    val displayedFraction = draggedFraction ?: progressFraction
    val displayedProgressMillis = if (durationMillis <= 0L) {
        0L
    } else {
        (displayedFraction * durationMillis).toLong().coerceIn(0L, durationMillis)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            colors.viewerBackground.copy(alpha = 0.86f),
                            colors.viewerSurface.copy(alpha = 0.62f),
                        ),
                    ),
                )
            },
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = Color.Transparent,
        border = BorderStroke(1.dp, colors.viewerOverlayBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = colors.viewerSurface.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, colors.viewerAccent.copy(alpha = 0.18f)),
                onClick = onTogglePlayback,
            ) {
                Box(
                    modifier = Modifier.padding(11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    VideoGlyph(
                        state = if (playbackState.isPlaying) {
                            VideoGlyphState.PAUSE
                        } else {
                            VideoGlyphState.PLAY
                        },
                        tint = colors.viewerText,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = "${formatSystemVideoProgress(displayedProgressMillis)} / ${formatSystemVideoProgress(durationMillis)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.viewerTextSecondary,
                )
                Slider(
                    value = displayedFraction,
                    onValueChange = { draggedFraction = it.coerceIn(0f, 1f) },
                    onValueChangeFinished = {
                        val targetFraction = draggedFraction ?: displayedFraction
                        val targetMillis = if (durationMillis <= 0L) {
                            0L
                        } else {
                            (targetFraction * durationMillis).toLong().coerceIn(0L, durationMillis)
                        }
                        draggedFraction = null
                        onSeekPlayback(targetMillis)
                    },
                    enabled = durationMillis > 0L && playbackState.errorMessage == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = colors.viewerAccent,
                        activeTrackColor = colors.viewerAccent,
                        inactiveTrackColor = colors.viewerSurface.copy(alpha = 0.96f),
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SystemMediaViewerMenuSheet(
    itemImported: Boolean,
    onDismiss: () -> Unit,
    onImportToApp: () -> Unit,
    onAddToPost: () -> Unit,
    onMoveToTrash: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.viewerSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = "媒体操作",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.viewerText,
            )
            SystemMediaViewerMenuAction(
                title = if (itemImported) "已导入照片流" else "导入照片流",
                enabled = !itemImported,
                onClick = onImportToApp,
            )
            SystemMediaViewerMenuAction(
                title = "加入已有小相册",
                onClick = onAddToPost,
            )
            SystemMediaViewerMenuAction(
                title = "移到系统回收站",
                danger = true,
                onClick = onMoveToTrash,
            )
        }
    }
}

@Composable
internal fun SystemMediaViewerMenuAction(
    title: String,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = if (danger) {
            colors.destructive.copy(alpha = 0.06f)
        } else {
            colors.viewerBackground.copy(alpha = 0.82f)
        },
        enabled = enabled,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .alpha(if (enabled) 1f else 0.42f)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (danger) colors.destructive else colors.viewerText,
            )
        }
    }
}

@Composable
internal fun SystemMediaViewerCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier
            .size(46.dp)
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            colors.viewerSurface.copy(alpha = 0.68f),
                            colors.viewerSurface.copy(alpha = 0.46f),
                        ),
                    ),
                )
            },
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, colors.viewerOverlayBorder),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colors.viewerText,
                modifier = Modifier.size(23.dp),
            )
        }
    }
}

internal fun formatSystemVideoProgress(timeMillis: Long): String {
    val totalSeconds = (timeMillis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(Locale.ROOT, minutes, seconds)
}

internal fun formatSystemViewerDisplayTime(timeMillis: Long?): String {
    if (timeMillis == null || timeMillis <= 0L) return "时间未知"
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}
