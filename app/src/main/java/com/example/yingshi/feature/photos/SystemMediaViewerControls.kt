package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SystemMediaViewerTopBar(
    currentIndex: Int,
    totalCount: Int,
    overlaysVisible: Boolean,
    itemImported: Boolean,
    locationLabel: String? = null,
    latitude: Double? = null,
    longitude: Double? = null,
    onBack: () -> Unit,
    onImportToApp: () -> Unit,
    onAddToPost: () -> Unit,
    onMoveToTrash: () -> Unit,
    onOpenLocation: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    // 对标照片流 PhotoViewerTopBar: 第一行按钮, 第二行地点胶囊右对齐.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (overlaysVisible) 1f else 0.35f),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
            SystemMediaViewerCircleButton(
                icon = Icons.Outlined.SystemUpdateAlt,
                contentDescription = if (itemImported) "已导入照片流" else "导入照片流",
                enabled = !itemImported,
                onClick = onImportToApp,
            )
            SystemMediaViewerCircleButton(
                icon = Icons.Outlined.PhotoAlbum,
                contentDescription = "加入已有小相册",
                onClick = onAddToPost,
            )
            SystemMediaViewerCircleButton(
                icon = Icons.Outlined.Delete,
                contentDescription = "移到系统回收站",
                onClick = onMoveToTrash,
            )
        }
        // 第二行: 地点胶囊右对齐. 显示 EXIF GPS 逆地理编码结果, 无 GPS 显示"无"占位.
        // 与照片流/今日痕迹一致: 始终用 LocationOn 图标 (不用 +, 系统媒体地点只读不可主动添加);
        // 有 GPS 坐标时点击跳转 LifeLocationPickerActivity 只读地图; 无 GPS 时不可点击.
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            SystemMediaViewerLocationCapsule(
                text = locationLabel,
                latitude = latitude,
                longitude = longitude,
                onClick = onOpenLocation,
            )
        }
    }
}

@Composable
internal fun SystemMediaViewerInfoCard(
    item: SystemMediaItem,
    modifier: Modifier = Modifier,
) {
    // 对标照片流 PhotoViewerEdgeActions: 单行三端对齐.
    //   [导入状态居左]  [时间居中]  [类型居右]
    // 时间不可点击 (系统媒体时间由 EXIF/MediaStore 决定, 暂不支持手动修改).
    val colors = YingShiThemeTokens.colors
    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = if (item.isImportedToApp) "已导入" else "未导入",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (item.isImportedToApp) colors.viewerAccent else colors.viewerTextSecondary,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            text = formatSystemViewerDisplayTime(item.displayTimeMillis),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.viewerText,
            modifier = Modifier.align(Alignment.Center),
        )
        Text(
            text = item.type.label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.viewerTextSecondary,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
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

@Composable
internal fun SystemMediaViewerCircleButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier
            .size(46.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        colors.viewerSurface.copy(alpha = 0.68f),
                        colors.viewerSurface.copy(alpha = 0.46f),
                    ),
                ),
                shape = CircleShape,
            ),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, colors.viewerOverlayBorder),
        enabled = enabled,
        onClick = onClick,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.alpha(if (enabled) 1f else 0.42f),
        ) {
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

// 对标照片流 ViewerLocationCapsule / 今日痕迹 LifeQuickViewerLocationCapsule.
// 差异: 系统媒体地点只读, 无 GPS 时显示"无"且不可点击; 始终用 LocationOn 图标.
@Composable
private fun SystemMediaViewerLocationCapsule(
    text: String?,
    latitude: Double?,
    longitude: Double?,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    val hasGps = latitude != null && longitude != null
    val placeholder = !hasGps
    // 有 GPS 但 label 为空 (理论上不会出现, 逆地理失败也会回退坐标格式), 用"未知位置"占位.
    val displayText = text?.takeIf { it.isNotBlank() }
        ?: if (hasGps) "未知位置" else "无"
    Surface(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .then(
                if (hasGps) {
                    Modifier.yingShiClickable(shape = shape, pressedScale = 0.96f, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        shape = shape,
        color = colors.viewerSurface.copy(alpha = if (placeholder) 0.40f else 0.56f),
        border = BorderStroke(1.dp, colors.viewerOverlayBorder.copy(alpha = if (placeholder) 0.50f else 1f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            // 始终用 LocationOn 定位图标 (无 GPS 时不用 +, 因为系统媒体地点只读不可主动添加).
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = colors.viewerAccent.copy(alpha = if (placeholder) 0.66f else 0.94f),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = displayText,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (placeholder) FontWeight.Normal else FontWeight.Medium,
                ),
                color = colors.viewerText.copy(alpha = if (placeholder) 0.72f else 0.94f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
