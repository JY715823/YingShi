package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun TrashPageScreen(
    modifier: Modifier = Modifier,
    selectedTypeName: String = TrashEntryType.POST_DELETED.name,
    onSelectedTypeNameChange: (String) -> Unit = { },
    showPendingCleanup: Boolean = false,
    onShowPendingCleanupChange: (Boolean) -> Unit = { },
    onOpenTrashDetail: (TrashDetailRoute) -> Unit = { },
    onRestoreTargetMediaIds: (List<String>) -> Unit = { },
) {
    if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
        RealTrashPageScreen(
            modifier = modifier,
            selectedTypeName = selectedTypeName,
            onSelectedTypeNameChange = onSelectedTypeNameChange,
            showPendingCleanup = showPendingCleanup,
            onShowPendingCleanupChange = onShowPendingCleanupChange,
            onOpenTrashDetail = onOpenTrashDetail,
            onRestoreTargetMediaIds = onRestoreTargetMediaIds,
        )
        return
    }

    val spacing = YingShiThemeTokens.spacing
    var transientMessage by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    val selectedType = TrashEntryType.valueOf(selectedTypeName)
    val entries = FakeTrashRepository.getEntries(selectedType)
    val pendingEntries = FakeTrashRepository.getPendingCleanupEntries()
    val snackbarMessage = FakeTrashRepository.getSnackbarMessage()

    LaunchedEffect(snackbarMessage?.entryId) {
        val message = snackbarMessage ?: return@LaunchedEffect
        transientMessage = message.message
        delay(2200)
        if (transientMessage == message.message) {
            transientMessage = null
        }
        FakeTrashRepository.consumeSnackbarMessage(message.entryId)
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item {
            TrashOverviewCard(
                selectedType = selectedType,
                pendingCount = pendingEntries.size,
                showPendingCleanup = showPendingCleanup,
                onTypeSelected = { onSelectedTypeNameChange(it.name) },
                onPendingCleanupClick = { onShowPendingCleanupChange(!showPendingCleanup) },
            )
        }

        transientMessage?.let { message ->
            item {
                TrashSnackbarCard(message = message)
            }
        }

        if (showPendingCleanup) {
            if (pendingEntries.isEmpty()) {
                item {
                    TrashEmptyCard(
                        text = "暂无可撤销条目",
                    )
                }
            } else {
                items(
                    items = pendingEntries,
                    key = { it.entry.id },
                ) { pending ->
                    PendingCleanupRow(
                        pending = pending,
                        onUndo = { FakeTrashRepository.undoPendingRemoval(pending.entry.id) },
                    )
                }
            }
        }

        if (!showPendingCleanup) {
            if (entries.isEmpty()) {
                item {
                    TrashEmptyCard(
                        text = "暂无删除条目",
                    )
                }
            } else {
                item {
                    TrashBulkActionCard(
                        selectedType = selectedType,
                        count = entries.size,
                        onRestoreAll = {
                            var successCount = 0
                            var failureCount = 0
                            var firstRestoredMediaIds = emptyList<String>()
                            entries.forEach { entry ->
                                val targetIds = entry.restoreTargetMediaIds()
                                val result = FakeTrashRepository.restoreEntry(entry.id)
                                if (result.success) {
                                    successCount += 1
                                    if (firstRestoredMediaIds.isEmpty()) {
                                        firstRestoredMediaIds = targetIds
                                    }
                                } else {
                                    failureCount += 1
                                }
                            }
                            transientMessage = when {
                                successCount > 0 && failureCount > 0 -> "批量恢复完成：成功 $successCount 项，失败 $failureCount 项。"
                                successCount > 0 -> "已恢复 $successCount 项。"
                                else -> "批量恢复失败，条目已保留。"
                            }
                            if (firstRestoredMediaIds.isNotEmpty()) {
                                onRestoreTargetMediaIds(firstRestoredMediaIds)
                            }
                        },
                    )
                }
                items(
                    items = entries,
                    key = { it.id },
                ) { entry ->
                    TrashEntryRow(
                        entry = entry,
                        onClick = {
                            onOpenTrashDetail(
                                TrashDetailRoute(
                                    entryId = entry.id,
                                    entryType = entry.type,
                                    sourcePostId = entry.sourcePostId,
                                    sourceMediaId = entry.sourceMediaId,
                                ),
                            )
                        },
                    )
                }
            }
        }

    }
}

@Composable
private fun TrashBulkActionCard(
    selectedType: TrashEntryType,
    count: Int,
    onRestoreAll: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
    ) {
        Row(
            modifier = Modifier.padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = "批量恢复",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "恢复当前「${selectedType.label}」分类中的 $count 项；失败项会继续留在回收站。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRestoreAll) {
                Text("恢复全部")
            }
        }
    }
}
@Composable
private fun TrashOverviewCard(
    selectedType: TrashEntryType,
    pendingCount: Int,
    showPendingCleanup: Boolean,
    onTypeSelected: (TrashEntryType) -> Unit,
    onPendingCleanupClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    TrashEntryType.entries.forEach { type ->
                        TrashSegmentChip(
                            text = type.label,
                            selected = type == selectedType,
                            onClick = { onTypeSelected(type) },
                        )
                    }
                }
                TrashSegmentChip(
                    text = "24h可撤销 $pendingCount",
                    selected = showPendingCleanup,
                    onClick = onPendingCleanupClick,
                )
            }
        }
    }
}

@Composable
private fun TrashPendingCleanupCard(
    pendingCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val actionLabel = if (expanded) "收起" else "查看"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "24h 可撤销 / 待清理",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (pendingCount > 0) {
                        "移出回收站后的条目会先进入待清理列表，可以在这里撤销。"
                    } else {
                        "当前还没有待清理条目。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "$pendingCount 项 · $actionLabel",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PendingCleanupRow(
    pending: TrashPendingCleanupUiModel,
    onUndo: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Box(
                modifier = Modifier
                    .weight(0.22f)
                    .aspectRatio(1f),
            ) {
                TrashEntryPreview(
                    entry = pending.entry,
                    modifier = Modifier.matchParentSize(),
                )
            }
            Column(
                modifier = Modifier.weight(0.78f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = pending.entry.type.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = pending.entry.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${trashEntrySourceLine(pending.entry)} · 移出于 ${formatTrashEntryTime(pending.removedAtMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onUndo,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text(text = "撤销")
                }
            }
        }
    }
}

@Composable
private fun TrashSnackbarCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(
                horizontal = YingShiThemeTokens.spacing.md,
                vertical = YingShiThemeTokens.spacing.sm,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TrashSegmentChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            },
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = YingShiThemeTokens.spacing.sm,
                vertical = YingShiThemeTokens.spacing.xs,
            ),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun TrashEntryRow(
    entry: TrashEntryUiModel,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
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
            }
            Column(
                modifier = Modifier.weight(0.72f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = entry.type.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = trashEntryTypeDescription(entry),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = entry.previewInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${trashEntrySourceLine(entry)} · ${formatTrashEntryTime(entry.deletedAtMillis)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                )
            }
            Text(
                text = "查看",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
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
            showVideoPlayOverlay = media.mediaType == AppMediaType.VIDEO,
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
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun TrashEntryUiModel.primaryPreviewMedia(): TrashMediaSnapshot? {
    return mediaSnapshot
        ?: postSnapshot?.mediaSnapshots?.firstOrNull { it.isCover }
        ?: postSnapshot?.mediaSnapshots?.firstOrNull()
}

private fun trashEntryTypeDescription(entry: TrashEntryUiModel): String {
    return when (entry.type) {
        TrashEntryType.POST_DELETED -> "帖子整体进入 App 回收站"
        TrashEntryType.MEDIA_REMOVED -> "只移除了当前帖子关联"
        TrashEntryType.MEDIA_SYSTEM_DELETED -> "App 全局媒体删除"
    }
}

private fun trashEntrySourceLine(entry: TrashEntryUiModel): String {
    return when (entry.type) {
        TrashEntryType.POST_DELETED -> {
            val albumCount = entry.postSnapshot?.post?.albumIds?.size?.coerceAtLeast(1) ?: 0
            val mediaCount = entry.postSnapshot?.mediaSnapshots?.size ?: entry.relatedMediaIds.size
            "所属相册 $albumCount 个 · 媒体 $mediaCount 项"
        }
        TrashEntryType.MEDIA_REMOVED -> {
            val postTitle = entry.relationSnapshots.firstOrNull()?.postTitle
                ?: entry.mediaSnapshot?.sourcePostTitle
                ?: entry.sourcePostId
                ?: "当前帖子"
            "来源帖子：$postTitle"
        }
        TrashEntryType.MEDIA_SYSTEM_DELETED -> {
            val postCount = entry.relationSnapshots.size.takeIf { it > 0 }
                ?: entry.relatedPostIds.size
            "影响帖子 $postCount 个"
        }
    }
}

@Composable
private fun TrashEmptyCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(YingShiThemeTokens.spacing.lg),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatTrashEntryTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}
