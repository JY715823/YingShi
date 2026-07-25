package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yingshi.feature.sync.StaleBanner
import com.example.yingshi.feature.sync.SyncModule
import com.example.yingshi.feature.sync.SyncVersionTracker
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
internal fun RealTrashPendingCleanupScreen(
    pendingEntries: List<TrashPendingCleanupUiModel>,
    directory: CollaboratorDirectorySnapshot,
    selectedType: TrashEntryType,
    isLoading: Boolean,
    isMutating: Boolean,
    errorMessage: String?,
    statusMessage: String?,
    onTypeSelected: (TrashEntryType) -> Unit,
    onRefresh: () -> Unit,
    onUndo: (TrashPendingCleanupUiModel) -> Unit,
    onPurge: (TrashPendingCleanupUiModel) -> Unit,
    onUndoAll: () -> Unit,
    onPurgeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPurgeAllConfirm by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.padding(horizontal = YingShiThemeTokens.spacing.md),
        verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.md),
    ) {
        item {
            RealTrashPendingActionRow(
                selectedType = selectedType,
                pendingCount = pendingEntries.size,
                isMutating = isMutating,
                menuExpanded = showCategoryMenu,
                onMenuExpandedChange = { showCategoryMenu = it },
                onTypeSelected = {
                    showCategoryMenu = false
                    onTypeSelected(it)
                },
                onUndoAll = onUndoAll,
                onPurgeAll = { showPurgeAllConfirm = true },
            )
        }
        item {
            RealTrashCategoryHeader(
                title = "待清理",
                subtitle = if (pendingEntries.isEmpty()) {
                    "没有等待清理的项目"
                } else {
                    "${pendingEntries.size} 项将在 24 小时窗口结束后自动永久删除"
                },
            )
        }
        item {
            StaleBanner(
                module = SyncModule.TRASH,
                onRefresh = {
                    onRefresh()
                    SyncVersionTracker.markRefreshed(SyncModule.TRASH)
                },
            )
        }
        // 不显示操作结果/错误提示卡片，避免操作后顶栏按钮被挤出视口
        when {
            isLoading -> {
                item { RealTrashSectionCard(title = "读取中", body = "正在读取待清理项目…") }
            }
            pendingEntries.isEmpty() -> {
                item { RealTrashCenteredEmptyState(text = "待清理为空") }
            }
            else -> {
                items(
                    items = pendingEntries.sortedBy { it.undoDeadlineMillis },
                    key = { it.entry.id },
                ) { pending ->
                    RealTrashPendingCleanupRow(
                        pending = pending,
                        actorIdentity = resolveTrashActorIdentity(pending.entry, directory),
                        isMutating = isMutating,
                        onUndo = { onUndo(pending) },
                        onPurge = { onPurge(pending) },
                    )
                }
            }
        }
    }

    if (showPurgeAllConfirm) {
        AlertDialog(
            onDismissRequest = { showPurgeAllConfirm = false },
            title = { Text("全部清空？") },
            text = { Text("将永久删除 ${pendingEntries.size} 项待清理内容，此操作不能撤销。") },
            containerColor = YingShiThemeTokens.colors.raisedSurface,
            titleContentColor = YingShiThemeTokens.colors.titleAccent,
            textContentColor = YingShiThemeTokens.colors.textSecondary,
            confirmButton = {
                TrashDialogActionButton(
                    text = "全部清空",
                    danger = true,
                    enabled = !isMutating,
                    onClick = {
                        showPurgeAllConfirm = false
                        onPurgeAll()
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { showPurgeAllConfirm = false })
            },
        )
    }
}

@Composable
private fun RealTrashPendingActionRow(
    selectedType: TrashEntryType,
    pendingCount: Int,
    isMutating: Boolean,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onTypeSelected: (TrashEntryType) -> Unit,
    onUndoAll: () -> Unit,
    onPurgeAll: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧留空，按钮都放右边，和其他分类布局一致
        Box(modifier = Modifier.weight(1f))
        Box {
            RealTrashIconActionButton(
                text = "分类",
                icon = Icons.Filled.Menu,
                enabled = !isMutating,
                onClick = { onMenuExpandedChange(true) },
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { onMenuExpandedChange(false) },
                containerColor = colors.raisedSurface.copy(alpha = 0.96f),
                shadowElevation = 8.dp,
                tonalElevation = 0.dp,
            ) {
                // 当前已在"待清理"页面，其他分类不显示选中勾
                TrashCategoryMenuTypes.forEach { type ->
                    DropdownMenuItem(
                        text = {
                            RealTrashMenuRow(
                                text = type.label,
                                trailing = null,
                                selected = false,
                            )
                        },
                        onClick = { onTypeSelected(type) },
                    )
                }
                DropdownMenuItem(
                    text = {
                        RealTrashMenuRow(
                            text = "待清理",
                            trailing = pendingCount.toString(),
                            selected = true,
                        )
                    },
                    onClick = { onMenuExpandedChange(false) },
                )
            }
        }
        RealTrashIconActionButton(
            text = if (isMutating) "处理中" else "全部恢复",
            icon = Icons.AutoMirrored.Filled.Undo,
            enabled = pendingCount > 0 && !isMutating,
            emphasized = true,
            onClick = onUndoAll,
        )
        RealTrashIconActionButton(
            text = "全部清空",
            icon = Icons.Filled.Delete,
            enabled = pendingCount > 0 && !isMutating,
            danger = true,
            onClick = onPurgeAll,
        )
    }
}

@Composable
private fun RealTrashPendingCleanupRow(
    pending: TrashPendingCleanupUiModel,
    actorIdentity: CollaboratorIdentityUiModel?,
    isMutating: Boolean,
    onUndo: () -> Unit,
    onPurge: () -> Unit,
) {
    var showPurgeConfirm by remember(pending.entry.id) { mutableStateOf(false) }
    val entry = pending.entry
    val colors = YingShiThemeTokens.colors
    val cardGradientColors = listOf(
        colors.memoryWash.copy(alpha = 0.50f),
        colors.raisedSurface.copy(alpha = 0.94f),
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(Brush.linearGradient(cardGradientColors))
            },
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = Color.Transparent,
        border = BorderStroke(1.dp, colors.memoryAccent.copy(alpha = 0.22f)),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(YingShiThemeTokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .aspectRatio(1f),
                ) {
                    RealTrashEntryPreview(entry = entry, modifier = Modifier.matchParentSize())
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xxs),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = entry.type.label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = YingShiThemeTokens.colors.titleAccent,
                        )
                        actorIdentity?.let { CollaboratorMarkerBadge(identity = it, size = 18.dp) }
                    }
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = YingShiThemeTokens.colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    RealTrashCountdownBadge(
                        remainingMillis = pending.undoDeadlineMillis - System.currentTimeMillis(),
                    )
                }
            }
            Text(
                text = pendingCleanupDeleteCopy(entry),
                style = MaterialTheme.typography.bodySmall,
                color = YingShiThemeTokens.colors.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
            ) {
                RealTrashIconActionButton(
                    text = "撤销移出",
                    icon = Icons.AutoMirrored.Filled.Undo,
                    enabled = !isMutating,
                    emphasized = true,
                    onClick = onUndo,
                )
                RealTrashIconActionButton(
                    text = "永久删除",
                    icon = Icons.Filled.Delete,
                    enabled = !isMutating,
                    danger = true,
                    onClick = { showPurgeConfirm = true },
                )
            }
        }
    }

    if (showPurgeConfirm) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirm = false },
            title = { Text("永久删除？") },
            text = { Text(pendingCleanupDeleteCopy(entry) + " 此操作不能撤销。") },
            containerColor = YingShiThemeTokens.colors.raisedSurface,
            titleContentColor = YingShiThemeTokens.colors.titleAccent,
            textContentColor = YingShiThemeTokens.colors.textSecondary,
            confirmButton = {
                TrashDialogActionButton(
                    text = "永久删除",
                    danger = true,
                    enabled = !isMutating,
                    onClick = {
                        showPurgeConfirm = false
                        onPurge()
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { showPurgeConfirm = false })
            },
        )
    }
}
