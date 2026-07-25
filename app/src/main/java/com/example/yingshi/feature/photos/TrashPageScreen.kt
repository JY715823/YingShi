package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

@Composable
fun TrashPageScreen(
    modifier: Modifier = Modifier,
    selectedTypeName: String = TrashEntryType.MEDIA_SYSTEM_DELETED.name,
    onSelectedTypeNameChange: (String) -> Unit = { },
    showPendingCleanup: Boolean = false,
    onShowPendingCleanupChange: (Boolean) -> Unit = { },
    selectionMode: Boolean = false,
    selectedEntryIds: Set<String> = emptySet(),
    onSelectionStateChange: (Boolean, Set<String>) -> Unit = { _, _ -> },
    onOpenTrashDetail: (TrashDetailRoute) -> Unit = { },
    onRestoreTargetMediaIds: (List<String>) -> Unit = { },
    selectionExitNonce: Int = 0,
    onSelectionModeChange: (Boolean) -> Unit = { },
) {
    RealTrashPageScreen(
        modifier = modifier,
        selectedTypeName = selectedTypeName,
        onSelectedTypeNameChange = onSelectedTypeNameChange,
        showPendingCleanup = showPendingCleanup,
        onShowPendingCleanupChange = onShowPendingCleanupChange,
        selectionMode = selectionMode,
        selectedEntryIds = selectedEntryIds,
        onSelectionStateChange = onSelectionStateChange,
        onOpenTrashDetail = onOpenTrashDetail,
        onRestoreTargetMediaIds = onRestoreTargetMediaIds,
        selectionExitNonce = selectionExitNonce,
        onSelectionModeChange = onSelectionModeChange,
    )
}

@Composable
private fun TrashCategoryActionRow(
    selectedType: TrashEntryType,
    entryCount: Int,
    directory: CollaboratorDirectorySnapshot,
    selectedCollaboratorUserIds: Set<String>,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onTypeSelected: (TrashEntryType) -> Unit,
    onToggleCollaborator: (String) -> Unit,
    selectionMode: Boolean,
    selectedCount: Int,
    onCancelSelection: () -> Unit,
    onRestoreCurrent: () -> Unit,
    onRequestClearCurrent: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            TrashIconActionButton(
                text = "取消",
                onClick = onCancelSelection,
            )
            Text(
                text = "已选 $selectedCount 项",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
        } else {
            Box {
                TrashIconActionButton(
                    text = "菜单",
                    icon = Icons.Filled.Menu,
                    emphasized = true,
                    onClick = { onMenuExpandedChange(true) },
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { onMenuExpandedChange(false) },
                ) {
                    TrashCategoryMenuTypes.forEach { type ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (type == selectedType) {
                                                colors.primaryContainer.copy(alpha = 0.54f)
                                            } else {
                                                Color.Transparent
                                            },
                                            RoundedCornerShape(YingShiThemeTokens.radius.md),
                                        )
                                        .padding(horizontal = spacing.xs, vertical = spacing.xxs),
                                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = type.label,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (type == selectedType) {
                                                FontWeight.SemiBold
                                            } else {
                                                FontWeight.Medium
                                            },
                                        ),
                                        color = if (type == selectedType) {
                                            colors.titleAccent
                                        } else {
                                            colors.textPrimary
                                        },
                                    )
                                    if (type == selectedType) {
                                        Text(
                                            text = "✓",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = colors.titleAccent,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onMenuExpandedChange(false)
                                onTypeSelected(type)
                            },
                        )
                    }
                }
            }
        }
        Box(modifier = Modifier.weight(1f))
        if (!selectionMode && directory.all.isNotEmpty()) {
            CollaboratorFilterChipRow(
                directory = directory,
                selectedUserIds = selectedCollaboratorUserIds,
                onToggleCollaborator = onToggleCollaborator,
                modifier = Modifier.padding(end = spacing.xxs),
            )
        }
        TrashIconActionButton(
            text = "恢复",
            icon = Icons.AutoMirrored.Filled.Undo,
            emphasized = true,
            enabled = entryCount > 0 || selectionMode,
            onClick = onRestoreCurrent,
        )
        TrashIconActionButton(
            text = "删除",
            icon = Icons.Filled.Delete,
            danger = true,
            enabled = entryCount > 0 || selectionMode,
            onClick = onRequestClearCurrent,
        )
    }
}

@Composable
private fun TrashIconActionButton(
    text: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    val containerColor = when {
        !enabled -> colors.sectionBackground.copy(alpha = 0.52f)
        danger -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.88f)
        emphasized -> colors.primaryContainer.copy(alpha = 0.78f)
        else -> colors.raisedSurface.copy(alpha = 0.96f)
    }
    val contentColor = when {
        !enabled -> colors.textSecondary.copy(alpha = 0.64f)
        danger -> MaterialTheme.colorScheme.onErrorContainer
        else -> colors.titleAccent
    }
    val borderColor = if (danger) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
    } else {
        colors.dividerSoft.copy(alpha = 0.66f)
    }
    Surface(
        modifier = (if (icon != null) Modifier.size(48.dp) else Modifier)
            .yingShiClickable(
                enabled = enabled,
                shape = shape,
                pressedScale = 0.96f,
                onClick = onClick,
            ),
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    modifier = Modifier.size(25.dp),
                    tint = contentColor,
                )
            }
        } else {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor,
            )
        }
    }
}

@Composable
internal fun TrashDialogActionButton(
    text: String,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    TrashIconActionButton(
        text = text,
        enabled = enabled,
        emphasized = emphasized,
        danger = danger,
        onClick = onClick,
    )
}

@Composable
private fun TrashSnackbarCard(message: String) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = colors.raisedSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.66f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(
                horizontal = YingShiThemeTokens.spacing.md,
                vertical = YingShiThemeTokens.spacing.sm,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.titleAccent,
        )
    }
}

@Composable
private fun TrashGridMonthHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = YingShiThemeTokens.colors.titleAccent,
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TrashMediaGridCell(
    entry: TrashEntryUiModel,
    showPostTitle: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    actorIdentity: CollaboratorIdentityUiModel?,
    showActorBadge: Boolean,
    modifier: Modifier = Modifier,
    onOpenDetail: () -> Unit,
    onToggleSelection: () -> Unit,
    onLongClick: () -> Unit,
) {
    val media = entry.primaryPreviewMedia()
    val selectionHotspotOnly = selectionMode
    Column(
        modifier = modifier
            .combinedClickable(
                onClick = onOpenDetail,
                onLongClick = onLongClick,
            ),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(4.dp)),
        ) {
            TrashEntryPreview(
                entry = entry,
                modifier = Modifier.matchParentSize(),
            )
            TrashDaysBadge(
                days = trashDaysSince(entry.deletedAtMillis),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 5.dp, end = 5.dp),
            )
            if (media?.mediaType == AppMediaType.VIDEO) {
                InlineVideoPlaybackButton(
                    isPlaying = false,
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp, bottom = 6.dp),
                )
            }
            TrashSelectionOverlay(
                selected = selected,
                visible = selectionMode,
                onClick = onToggleSelection,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
            if (showActorBadge && actorIdentity != null) {
                CollaboratorMarkerBadge(
                    identity = actorIdentity,
                    size = 22.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = if (selectionHotspotOnly) 30.dp else 6.dp, bottom = 6.dp),
                )
            }
        }

        if (showPostTitle) {
            val colors = YingShiThemeTokens.colors
            Surface(
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                color = colors.raisedSurface.copy(alpha = 0.86f),
                border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.54f)),
            ) {
                Text(
                    text = trashGridPostTitle(entry),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun TrashDaysBadge(
    days: Long,
    modifier: Modifier = Modifier,
) {
    val danger = days > 25
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (danger) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.88f)
        } else {
            colors.viewerBackground.copy(alpha = 0.36f)
        },
        border = BorderStroke(
            1.dp,
            if (danger) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.24f)
            } else {
                colors.viewerAccent.copy(alpha = 0.14f)
            },
        ),
    ) {
        Text(
            text = "${days.coerceAtLeast(0)}天",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (danger) {
                MaterialTheme.colorScheme.onError
            } else {
                colors.viewerText.copy(alpha = 0.92f)
            },
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TrashEntryRow(
    entry: TrashEntryUiModel,
    selected: Boolean,
    selectionMode: Boolean,
    actorIdentity: CollaboratorIdentityUiModel?,
    showActorBadge: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.58f)),
    ) {
        Row(
            modifier = Modifier.padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(0.28f)
                    .aspectRatio(1f),
            ) {
                TrashEntryPreview(
                    entry = entry,
                    modifier = Modifier.matchParentSize(),
                )
                TrashSelectionOverlay(
                    selected = selected,
                    visible = selectionMode,
                    onClick = onClick,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
                if (showActorBadge && actorIdentity != null) {
                    CollaboratorMarkerBadge(
                        identity = actorIdentity,
                        size = 22.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = if (selectionMode) 30.dp else 6.dp, bottom = 6.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(0.72f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = entry.type.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
                Text(
                    text = trashEntryTypeDescription(entry),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.titleAccent,
                )
                Text(
                    text = entry.previewInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                Text(
                    text = "${trashEntrySourceLine(entry)} · ${formatTrashEntryTime(entry.deletedAtMillis)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary.copy(alpha = 0.82f),
                )
            }
            if (!selectionMode) {
                Text(
                    text = "查看",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
            }
        }
    }
}

@Composable
private fun TrashSelectionOverlay(
    selected: Boolean,
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Box(
        modifier = modifier
            .size(46.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomEnd,
    ) {
        AppMediaSelectionBadge(
            selected = selected,
            modifier = Modifier.padding(end = 2.dp, bottom = 2.dp),
        )
    }
}

@Composable
private fun TrashEntryPreview(
    entry: TrashEntryUiModel,
    modifier: Modifier = Modifier,
) {
    val media = entry.primaryPreviewMedia()
    if (media?.mediaSource != null) {
        AppContentMediaThumbnail(
            mediaSource = media.mediaSource,
            mediaType = media.mediaType,
            palette = media.palette,
            modifier = modifier,
            requestSize = 256,
            showLoadingIndicator = true,
            showStatusBadge = true,
            showVideoPlayOverlay = false,
        )
        return
    }

    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(entry.palette.start, entry.palette.end),
                ),
                shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
            ),
    ) {
        val label = when (media?.mediaType) {
            AppMediaType.VIDEO -> "视频"
            AppMediaType.IMAGE -> "图片"
            null -> "记录"
        }
        Surface(
            modifier = Modifier
                .padding(YingShiThemeTokens.spacing.xs)
                .align(Alignment.BottomStart),
            shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
            color = YingShiThemeTokens.colors.raisedSurface.copy(alpha = 0.84f),
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = YingShiThemeTokens.colors.titleAccent,
            )
        }
    }
}

private fun TrashEntryUiModel.primaryPreviewMedia(): TrashMediaSnapshot? {
    return mediaSnapshot
        ?: albumSnapshot?.postSnapshots?.firstOrNull()?.mediaSnapshots?.firstOrNull { it.isCover }
        ?: albumSnapshot?.postSnapshots?.firstOrNull()?.mediaSnapshots?.firstOrNull()
        ?: postSnapshot?.mediaSnapshots?.firstOrNull { it.isCover }
        ?: postSnapshot?.mediaSnapshots?.firstOrNull()
}

private fun trashEntryTypeDescription(entry: TrashEntryUiModel): String {
    return when (entry.type) {
        TrashEntryType.LARGE_ALBUM_DELETED -> "大相册和所含小相册已一起进入回收站"
        TrashEntryType.SMALL_ALBUM_DELETED -> "小相册已进入回收站"
        TrashEntryType.MEDIA_REMOVED -> "只移除了当前小相册关联"
        TrashEntryType.MEDIA_SYSTEM_DELETED -> "媒体已从照片流和相关小相册删除"
    }
}

private fun trashEntrySourceLine(entry: TrashEntryUiModel): String {
    return when (entry.type) {
        TrashEntryType.LARGE_ALBUM_DELETED -> {
            val postCount = entry.albumSnapshot?.postSnapshots?.size ?: entry.relatedPostIds.size
            val mediaCount = entry.albumSnapshot?.postSnapshots?.sumOf { it.mediaSnapshots.size }
                ?: entry.relatedMediaIds.size
            "整册删除 · 小相册 $postCount 个 · 媒体 $mediaCount 项"
        }
        TrashEntryType.SMALL_ALBUM_DELETED -> {
            val albumCount = entry.postSnapshot?.post?.albumIds?.size?.coerceAtLeast(1) ?: 0
            val mediaCount = entry.postSnapshot?.mediaSnapshots?.size ?: entry.relatedMediaIds.size
            "所属相册 $albumCount 个 · 媒体 $mediaCount 项"
        }
        TrashEntryType.MEDIA_REMOVED -> {
            val postTitle = entry.relationSnapshots.firstOrNull()?.postTitle
                ?: entry.mediaSnapshot?.sourcePostTitle
                ?: entry.sourcePostId
                ?: "当前小相册"
            "来源小相册：$postTitle"
        }
        TrashEntryType.MEDIA_SYSTEM_DELETED -> {
            val postCount = entry.relationSnapshots.size.takeIf { it > 0 }
                ?: entry.relatedPostIds.size
            "影响小相册 $postCount 个"
        }
    }
}

@Composable
private fun TrashEmptyCard(text: String) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = colors.sectionBackground.copy(alpha = 0.56f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.60f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(YingShiThemeTokens.spacing.lg),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

private fun formatTrashEntryTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}

private fun TrashEntryType.isMediaTrashType(): Boolean {
    return this == TrashEntryType.MEDIA_SYSTEM_DELETED || this == TrashEntryType.MEDIA_REMOVED
}

private data class TrashMonthGroup(
    val key: String,
    val title: String,
    val entries: List<TrashEntryUiModel>,
)

private fun trashMonthGroups(entries: List<TrashEntryUiModel>): List<TrashMonthGroup> {
    val formatter = SimpleDateFormat("yyyy年M月", Locale.CHINA)
    val keyFormatter = SimpleDateFormat("yyyy-MM", Locale.CHINA)
    return entries
        .sortedByDescending { it.deletedAtMillis }
        .groupBy { keyFormatter.format(Date(it.deletedAtMillis)) }
        .map { (key, groupEntries) ->
            TrashMonthGroup(
                key = key,
                title = formatter.format(Date(groupEntries.first().deletedAtMillis)),
                entries = groupEntries,
            )
        }
}

private fun trashDaysSince(timeMillis: Long): Long {
    val now = System.currentTimeMillis()
    if (timeMillis <= 0L || now <= timeMillis) return 0L
    return TimeUnit.MILLISECONDS.toDays(now - timeMillis)
}

private fun trashGridPostTitle(entry: TrashEntryUiModel): String {
    return entry.relationSnapshots.firstOrNull()?.postTitle
        ?: entry.mediaSnapshot?.sourcePostTitle
        ?: entry.title.removePrefix("从「").substringBefore("」移除媒体")
        ?: entry.sourcePostId
        ?: "来源小相册"
}
