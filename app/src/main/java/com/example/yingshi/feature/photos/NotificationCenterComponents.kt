package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
internal fun NotificationActorChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.yingShiClickable(
            shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
            pressedScale = 0.96f,
            onClick = onClick,
        ),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (selected) colors.memoryContainer.copy(alpha = 0.90f) else colors.sectionBackground.copy(alpha = 0.56f),
        border = BorderStroke(1.dp, if (selected) colors.memoryAccent.copy(alpha = 0.32f) else colors.dividerSoft.copy(alpha = 0.52f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.memoryAccent else colors.dividerSoft),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
        }
    }
}

@Composable
internal fun NotificationFilterSectionLabel(
    text: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = YingShiThemeTokens.colors.textSecondary,
    )
}

@Composable
internal fun NotificationFilterChip(
    text: String,
    selected: Boolean,
    count: Int,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val badgeLabel = when {
        count > 99 -> "99+"
        count > 0 -> count.toString()
        else -> null
    }

    Box(
        modifier = Modifier.padding(top = 5.dp, end = 6.dp),
    ) {
        Surface(
            modifier = Modifier
                .yingShiClickable(
                    shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                    pressedScale = 0.97f,
                    onClick = onClick,
                ),
            shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
            color = if (selected) {
                colors.primaryContainer.copy(alpha = 0.88f)
            } else {
                colors.sectionBackground.copy(alpha = 0.72f)
            },
            border = BorderStroke(
                1.dp,
                if (selected) {
                    colors.glassStroke.copy(alpha = 0.78f)
                } else {
                    colors.dividerSoft.copy(alpha = 0.64f)
                },
            ),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                color = colors.titleAccent,
            )
        }
        if (badgeLabel != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 5.dp, y = (-5).dp),
                shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                color = colors.memoryAccent,
                border = BorderStroke(1.dp, colors.appBackground.copy(alpha = 0.90f)),
            ) {
                Text(
                    text = badgeLabel,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.onMemoryContainer,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun NotificationCenterItemRow(
    item: NotificationCenterItemUiModel,
    onMediaClick: (NotificationCenterMediaItemUiModel) -> Unit,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val presentation = rememberNotificationPresentation(item)
    var mediaExpanded by remember(item.id) { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.xl))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(radius.xl),
        color = if (item.isRead) {
            colors.raisedSurface.copy(alpha = 0.94f)
        } else {
            colors.memoryWash.copy(alpha = 0.96f)
        },
        border = BorderStroke(
            1.dp,
            if (item.isRead) {
                colors.dividerSoft.copy(alpha = 0.54f)
            } else {
                colors.memoryAccent.copy(alpha = 0.24f)
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NotificationTypeChip(type = item.type)
                Text(
                    text = formatNotificationTime(item.createdAtMillis),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                NotificationStatusBadge(
                    text = if (item.isRead) "已读" else "未读",
                    emphasized = !item.isRead,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                if (!item.isRead) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colors.memoryAccent),
                    )
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                    ) {
                        Text(
                            text = presentation.title,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.titleAccent,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = presentation.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (item.mediaItems.isNotEmpty()) {
                        NotificationMediaStrip(
                            mediaItems = item.mediaItems,
                            isCollapsed = item.mediaItems.size > 1 && !mediaExpanded,
                            onMediaClick = onMediaClick,
                        )
                    } else if (presentation.visual != NotificationVisual.None) {
                        NotificationVisualPane(
                            visual = presentation.visual,
                            modifier = Modifier
                                .width(96.dp)
                                .height(96.dp),
                        )
                    }
                }
            }
            if (item.mediaItems.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    NotificationActionPill(
                        text = if (mediaExpanded) "折叠" else "展开 ${item.mediaItems.size} 项",
                        enabled = true,
                        onClick = { mediaExpanded = !mediaExpanded },
                    )
                }
            }
        }
    }
}

@Composable
internal fun NotificationMediaStrip(
    mediaItems: List<NotificationCenterMediaItemUiModel>,
    isCollapsed: Boolean,
    onMediaClick: (NotificationCenterMediaItemUiModel) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val visibleItems = if (isCollapsed) mediaItems.take(3) else mediaItems
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visibleItems.forEachIndexed { index, media ->
            NotificationMediaTile(
                media = media,
                modifier = Modifier
                    .size(76.dp)
                    .offset(x = if (isCollapsed) (-index * 22).dp else 0.dp, y = if (isCollapsed) (index * 5).dp else 0.dp)
                    .alpha(if (index == 0) 1f else 0.72f),
                onClick = { onMediaClick(media) },
            )
        }
        if (mediaItems.size > visibleItems.size) {
            Text(
                text = "+${mediaItems.size - visibleItems.size}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = YingShiThemeTokens.colors.textSecondary,
            )
        }
    }
}

@Composable
internal fun NotificationMediaTile(
    media: NotificationCenterMediaItemUiModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier.yingShiClickable(
            shape = RoundedCornerShape(radius.lg),
            pressedScale = 0.96f,
            onClick = onClick,
        ),
        shape = RoundedCornerShape(radius.lg),
        color = colors.sectionBackground.copy(alpha = 0.56f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.60f)),
    ) {
        AppContentMediaThumbnail(
            mediaSource = media.mediaSource,
            mediaType = media.mediaType,
            palette = realPaletteFor(media.mediaId),
            modifier = Modifier.fillMaxSize(),
            contentDescription = if (media.mediaType == AppMediaType.VIDEO) "通知视频预览" else "通知图片预览",
            requestSize = 240,
            showLoadingIndicator = false,
            showStatusBadge = true,
            showVideoPlayOverlay = false,
        )
    }
}

@Composable
internal fun NotificationVisualPane(
    visual: NotificationVisual,
    modifier: Modifier = Modifier,
) {
    when (visual) {
        NotificationVisual.None -> Unit
        is NotificationVisual.Media -> NotificationMediaVisualPane(
            visual = visual,
            modifier = modifier,
        )
        is NotificationVisual.SmallAlbum -> NotificationSmallAlbumVisualPane(
            visual = visual,
            modifier = modifier,
        )
    }
}

@Composable
internal fun NotificationMediaVisualPane(
    visual: NotificationVisual.Media,
    modifier: Modifier = Modifier,
) {
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius.lg),
        color = colors.sectionBackground.copy(alpha = 0.54f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.60f)),
    ) {
        AppContentMediaThumbnail(
            mediaSource = visual.mediaSource,
            mediaType = visual.mediaType,
            palette = visual.palette,
            modifier = Modifier.fillMaxSize(),
            contentDescription = if (visual.mediaType == AppMediaType.VIDEO) "通知视频预览" else "通知图片预览",
            requestSize = 360,
            showLoadingIndicator = true,
            showStatusBadge = true,
            showVideoPlayOverlay = false,
        )
    }
}

@Composable
internal fun NotificationSmallAlbumVisualPane(
    visual: NotificationVisual.SmallAlbum,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val previewMedia = visual.previewMedia.distinctBy(AlbumPostPreviewMediaUiModel::id).take(2)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius.lg),
        color = colors.raisedSurface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.60f)),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (previewMedia.isNotEmpty()) {
                if (previewMedia.size == 1) {
                    NotificationSmallAlbumPreviewTile(
                        media = previewMedia.first(),
                        title = visual.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        NotificationSmallAlbumPreviewTile(
                            media = previewMedia[0],
                            title = visual.title,
                            modifier = Modifier
                                .weight(1.35f)
                                .fillMaxHeight(),
                        )
                        NotificationSmallAlbumPreviewTile(
                            media = previewMedia[1],
                            title = visual.title,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(colors.sectionBackground.copy(alpha = 0.50f)),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.sm, vertical = spacing.xs),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = visual.title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = visual.metaLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun NotificationSmallAlbumPreviewTile(
    media: AlbumPostPreviewMediaUiModel,
    title: String,
    modifier: Modifier = Modifier,
) {
    AppContentMediaThumbnail(
        mediaSource = media.mediaSource,
        mediaType = media.mediaType,
        palette = media.palette,
        modifier = modifier,
        contentDescription = title,
        requestSize = 320,
        showLoadingIndicator = true,
        showStatusBadge = true,
        showVideoPlayOverlay = false,
    )
}

@Composable
internal fun NotificationTypeChip(
    type: NotificationCenterItemType,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val containerColor = when (type) {
        NotificationCenterItemType.COMMENT -> colors.memoryContainer
        NotificationCenterItemType.CONTENT_UPDATE -> colors.primaryContainer
        NotificationCenterItemType.DELETE_RESTORE -> colors.softGreenContainer
        NotificationCenterItemType.SYSTEM -> colors.glowWash
    }

    Surface(
        shape = RoundedCornerShape(radius.capsule),
        color = containerColor.copy(alpha = 0.84f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.44f)),
    ) {
        Text(
            text = type.label,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.titleAccent,
        )
    }
}

@Composable
internal fun NotificationStatusBadge(
    text: String,
    emphasized: Boolean,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        shape = RoundedCornerShape(radius.capsule),
        color = if (emphasized) {
            colors.memoryContainer
        } else {
            colors.sectionBackground.copy(alpha = 0.58f)
        },
        border = if (emphasized) {
            BorderStroke(1.dp, colors.memoryAccent.copy(alpha = 0.22f))
        } else {
            BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.42f))
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (emphasized) {
                colors.onMemoryContainer
            } else {
                colors.textSecondary
            },
        )
    }
}

@Composable
internal fun NotificationCenterLoadingState(
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.54f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.4.dp,
                color = colors.primaryAction,
            )
            Text(
                text = "正在读取通知…",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
internal fun NotificationCenterMessageCard(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.54f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            NotificationActionPill(
                text = actionLabel,
                enabled = true,
                modifier = Modifier.align(Alignment.End),
                onClick = onAction,
            )
        }
    }
}

@Composable
internal fun NotificationCenterEmptyState(
    filter: NotificationCenterFilter,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius.xl),
        color = colors.sectionBackground.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.54f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = "${filter.label}暂无通知",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            Text(
                text = "有新的照片或生活提醒时会出现在这里。",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
internal fun NotificationDateSectionHeader(
    title: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = YingShiThemeTokens.colors.titleAccent,
        modifier = Modifier.padding(top = YingShiThemeTokens.spacing.xs),
    )
}

@Composable
internal fun NotificationIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val containerColor = when {
        !enabled -> colors.sectionBackground.copy(alpha = 0.42f)
        danger -> colors.destructiveContainer.copy(alpha = 0.74f)
        else -> colors.sectionBackground.copy(alpha = 0.78f)
    }
    val borderColor = when {
        !enabled -> colors.dividerSoft.copy(alpha = 0.42f)
        danger -> colors.destructive.copy(alpha = 0.20f)
        else -> colors.dividerSoft.copy(alpha = 0.72f)
    }
    val iconTint = when {
        !enabled -> colors.textSecondary.copy(alpha = 0.56f)
        danger -> colors.onDestructiveContainer
        else -> colors.titleAccent
    }
    Surface(
        modifier = Modifier
            .size(44.dp)
            .yingShiClickable(
                enabled = enabled,
                shape = CircleShape,
                pressedScale = 0.94f,
                onClick = onClick,
            ),
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
internal fun NotificationActionPill(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier.yingShiClickable(
            enabled = enabled,
            shape = RoundedCornerShape(radius.capsule),
            pressedScale = 0.96f,
            onClick = onClick,
        ),
        shape = RoundedCornerShape(radius.capsule),
        color = if (enabled) colors.primaryContainer.copy(alpha = 0.90f) else colors.sectionBackground.copy(alpha = 0.52f),
        border = BorderStroke(1.dp, if (enabled) colors.glassStroke.copy(alpha = 0.30f) else colors.dividerSoft.copy(alpha = 0.56f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) colors.titleAccent else colors.textSecondary,
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun NotificationCenterScreenPreview() {
    YingShiTheme {
        NotificationCenterScreen(
            route = NotificationCenterRoute(),
            onBack = { },
            onOpenNotificationDetail = { },
        )
    }
}