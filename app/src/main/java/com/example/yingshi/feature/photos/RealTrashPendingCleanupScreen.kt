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
import androidx.compose.material3.AlertDialog
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
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
internal fun RealTrashPendingCleanupScreen(
    pendingEntries: List<TrashPendingCleanupUiModel>,
    directory: CollaboratorDirectorySnapshot,
    isLoading: Boolean,
    isMutating: Boolean,
    errorMessage: String?,
    statusMessage: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onUndo: (TrashPendingCleanupUiModel) -> Unit,
    onPurge: (TrashPendingCleanupUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = YingShiThemeTokens.spacing.md),
        verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.md),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RealTrashIconActionButton(text = "返回", enabled = !isMutating, onClick = onBack)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "待清理",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = YingShiThemeTokens.colors.textPrimary,
                    )
                    Text(
                        text = if (pendingEntries.isEmpty()) {
                            "没有等待清理的项目"
                        } else {
                            "${pendingEntries.size} 项将在 24 小时窗口结束后自动永久删除"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = YingShiThemeTokens.colors.textSecondary,
                    )
                }
                RealTrashIconActionButton(text = "刷新", enabled = !isMutating, onClick = onRefresh)
            }
        }
        statusMessage?.let { message ->
            item { RealTrashSectionCard(title = "操作结果", body = message, emphasized = true) }
        }
        errorMessage?.let { message ->
            item { RealTrashSectionCard(title = "请求失败", body = message) }
        }
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
