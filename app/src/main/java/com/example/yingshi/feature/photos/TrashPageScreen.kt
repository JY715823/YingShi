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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
    selectedTypeName: String = TrashEntryType.MEDIA_SYSTEM_DELETED.name,
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
    var showCategoryMenu by rememberSaveable {
        mutableStateOf(false)
    }
    var showClearConfirm by rememberSaveable {
        mutableStateOf(false)
    }
    val selectedType = TrashEntryType.valueOf(selectedTypeName)
    val entries = FakeTrashRepository.getEntries(selectedType)
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
            TrashCategoryActionRow(
                selectedType = selectedType,
                entryCount = entries.size,
                menuExpanded = showCategoryMenu,
                onMenuExpandedChange = { showCategoryMenu = it },
                onTypeSelected = { onSelectedTypeNameChange(it.name) },
                onRestoreCurrent = {
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
                        successCount > 0 && failureCount > 0 -> "批量恢复完成：成功 $successCount 项，失败 $failureCount 项。失败项已保留。"
                        successCount > 0 -> "已恢复当前分类 $successCount 项。"
                        else -> "批量恢复失败，条目已保留。"
                    }
                    if (firstRestoredMediaIds.isNotEmpty()) {
                        onRestoreTargetMediaIds(firstRestoredMediaIds)
                    }
                },
                onRequestClearCurrent = { showClearConfirm = true },
            )
        }

        transientMessage?.let { message ->
            item {
                TrashSnackbarCard(message = message)
            }
        }

        if (entries.isEmpty()) {
            item {
                TrashEmptyCard(
                    text = "暂无删除条目",
                )
            }
        } else {
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

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空当前分类？") },
            text = {
                Text(
                    "将永久删除当前「${selectedType.label}」分类中的 ${entries.size} 项。媒体删除类会删除对应 Server local-storage 文件；帖子删除、媒体移除不会误删仍被其他地方引用的媒体文件。",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = entries.isNotEmpty(),
                    onClick = {
                        showClearConfirm = false
                        var successCount = 0
                        var failureCount = 0
                        entries.forEach { entry ->
                            if (FakeTrashRepository.permanentlyDeleteEntry(entry.id)) {
                                successCount += 1
                            } else {
                                failureCount += 1
                            }
                        }
                        transientMessage = when {
                            successCount > 0 && failureCount > 0 -> "清空当前分类完成：成功 $successCount 项，失败 $failureCount 项。失败项已保留。"
                            successCount > 0 -> "已清空当前分类 $successCount 项。"
                            else -> "清空当前分类失败，条目已保留。"
                        }
                    },
                ) {
                    Text("清空当前分类")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun TrashCategoryActionRow(
    selectedType: TrashEntryType,
    entryCount: Int,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onTypeSelected: (TrashEntryType) -> Unit,
    onRestoreCurrent: () -> Unit,
    onRequestClearCurrent: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            TrashIconActionButton(
                text = "☰",
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
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
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
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                if (type == selectedType) {
                                    Text(
                                        text = "✓",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
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
        Box(modifier = Modifier.weight(1f))
        TextButton(
            enabled = entryCount > 0,
            onClick = onRestoreCurrent,
        ) {
            Text("恢复当前分类")
        }
        TrashIconActionButton(
            text = "🗑",
            enabled = entryCount > 0,
            onClick = onRequestClearCurrent,
        )
    }
}

@Composable
private fun TrashIconActionButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (enabled) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f)
            },
        )
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
        TrashEntryType.MEDIA_SYSTEM_DELETED -> "媒体已从照片流和相关帖子删除"
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
