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
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
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
    if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
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
        return
    }

    val spacing = YingShiThemeTokens.spacing
    val collaboratorDirectory = rememberCollaboratorDirectorySnapshot()
    val allCollaboratorUserIds = remember(collaboratorDirectory) {
        collaboratorDirectory.all.mapTo(linkedSetOf()) { it.userId }
    }
    var transientMessage by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var showCategoryMenu by rememberSaveable {
        mutableStateOf(false)
    }
    var showClearConfirm by rememberSaveable {
        mutableStateOf(false)
    }
    var showDeleteSelectedConfirm by rememberSaveable {
        mutableStateOf(false)
    }
    var showRestoreConfirm by rememberSaveable {
        mutableStateOf(false)
    }
    var pendingRestoreEntries by remember {
        mutableStateOf(emptyList<TrashEntryUiModel>())
    }
    var previousSelectedTypeName by rememberSaveable {
        mutableStateOf(selectedTypeName)
    }
    var savedSelectedCollaboratorUserIds by rememberSaveable {
        mutableStateOf(emptyList<String>())
    }
    var collaboratorSelectionInitialized by rememberSaveable {
        mutableStateOf(false)
    }
    val selectedType = TrashEntryType.valueOf(selectedTypeName)
    LaunchedEffect(allCollaboratorUserIds, selectedTypeName) {
        if (allCollaboratorUserIds.isEmpty()) return@LaunchedEffect
        savedSelectedCollaboratorUserIds = if (!collaboratorSelectionInitialized) {
            defaultCollaboratorSelection(allCollaboratorUserIds).toList()
        } else {
            normalizeCollaboratorSelectionKeepingEmpty(
                selectedUserIds = savedSelectedCollaboratorUserIds.toSet(),
                allUserIds = allCollaboratorUserIds,
            ).toList()
        }
        collaboratorSelectionInitialized = true
    }
    val selectedCollaboratorUserIds = remember(savedSelectedCollaboratorUserIds, allCollaboratorUserIds) {
        normalizeCollaboratorSelectionKeepingEmpty(
            selectedUserIds = savedSelectedCollaboratorUserIds.toSet(),
            allUserIds = allCollaboratorUserIds,
        )
    }
    val rawEntries = FakeTrashRepository.getEntries(selectedType)
    val entries = remember(rawEntries, selectedCollaboratorUserIds, allCollaboratorUserIds) {
        filterTrashEntriesByCollaborator(
            entries = rawEntries,
            directory = collaboratorDirectory,
            selectedUserIds = selectedCollaboratorUserIds,
            allUserIds = allCollaboratorUserIds,
        ).distinctBy { it.businessIdentityKey() }
    }
    val showActorBadge = isAllCollaboratorsSelected(
        selectedUserIds = selectedCollaboratorUserIds,
        allUserIds = allCollaboratorUserIds,
    )
    val selectedEntries = entries.filter { it.id in selectedEntryIds }
    val snackbarMessage = FakeTrashRepository.getSnackbarMessage()

    fun toggleSelection(entry: TrashEntryUiModel) {
        onSelectionStateChange(
            true,
            if (entry.id in selectedEntryIds) selectedEntryIds - entry.id else selectedEntryIds + entry.id,
        )
    }

    fun restoreEntries(targetEntries: List<TrashEntryUiModel>) {
        var successCount = 0
        var failureCount = 0
        var firstRestoredMediaIds = emptyList<String>()
        targetEntries.forEach { entry ->
            val targetIds = entry.restoreTargetMediaIds()
            val result = FakeTrashRepository.restoreEntry(entry.id)
            if (result.success) {
                successCount += 1
                if (firstRestoredMediaIds.isEmpty()) firstRestoredMediaIds = targetIds
            } else {
                failureCount += 1
            }
        }
        transientMessage = when {
            successCount > 0 && failureCount > 0 -> "批量恢复完成：成功 $successCount 项，失败 $failureCount 项。失败项已保留。"
            successCount > 0 -> "已恢复 $successCount 项。"
            else -> "批量恢复失败，条目已保留。"
        }
        onSelectionStateChange(false, emptySet())
        if (firstRestoredMediaIds.isNotEmpty()) onRestoreTargetMediaIds(firstRestoredMediaIds)
    }

    fun requestRestoreFromTopBar() {
        val targetEntries = if (selectionMode) selectedEntries else entries
        if (selectionMode && targetEntries.isEmpty()) {
            transientMessage = "未选择可恢复的条目"
            return
        }
        pendingRestoreEntries = targetEntries
        showRestoreConfirm = true
    }

    fun deleteEntries(targetEntries: List<TrashEntryUiModel>, successPrefix: String) {
        var successCount = 0
        var failureCount = 0
        targetEntries.forEach { entry ->
            if (FakeTrashRepository.permanentlyDeleteEntry(entry.id)) successCount += 1 else failureCount += 1
        }
        transientMessage = when {
            successCount > 0 && failureCount > 0 -> "$successPrefix 完成：成功 $successCount 项，失败 $failureCount 项。失败项已保留。"
            successCount > 0 -> "$successPrefix $successCount 项。"
            else -> "$successPrefix 失败，条目已保留。"
        }
        onSelectionStateChange(false, emptySet())
    }

    fun requestDeleteFromTopBar() {
        if (selectionMode) {
            if (selectedEntries.isEmpty()) {
                transientMessage = "未选择可移除的条目"
            } else {
                showDeleteSelectedConfirm = true
            }
        } else {
            showClearConfirm = true
        }
    }

    LaunchedEffect(snackbarMessage?.entryId) {
        val message = snackbarMessage ?: return@LaunchedEffect
        transientMessage = message.message
        delay(2200)
        if (transientMessage == message.message) {
            transientMessage = null
        }
        FakeTrashRepository.consumeSnackbarMessage(message.entryId)
    }

    LaunchedEffect(selectedTypeName) {
        if (selectedTypeName != previousSelectedTypeName) {
            onSelectionStateChange(false, emptySet())
            if (selectedCollaboratorUserIds.isEmpty() && allCollaboratorUserIds.isNotEmpty()) {
                savedSelectedCollaboratorUserIds = defaultCollaboratorSelection(allCollaboratorUserIds).toList()
                collaboratorSelectionInitialized = true
            }
            previousSelectedTypeName = selectedTypeName
        }
    }

    LaunchedEffect(selectionExitNonce) {
        if (selectionExitNonce > 0) {
            onSelectionStateChange(false, emptySet())
        }
    }

    LaunchedEffect(selectionMode) {
        onSelectionModeChange(selectionMode)
    }

    if (selectedType.isMediaTrashType()) {
        val mediaEntries = entries.sortedByDescending { it.deletedAtMillis }
        val mediaGridState = rememberLazyGridState()
        val mediaGridColumns = 3
        val rowItems = remember(mediaEntries) { mediaEntries.chunked(mediaGridColumns) }
        val rowKeys = remember(rowItems) { rowItems.indices.map { "trash-media-row-$it" } }
        val rowKeyToMediaIds = remember(rowItems, rowKeys) {
            rowKeys.zip(rowItems).associate { (rowKey, rowEntries) ->
                rowKey to rowEntries.map { it.id }
            }
        }
        val rowKeyToIndex = remember(rowKeys) {
            rowKeys.withIndex().associate { it.value to it.index }
        }
        val mediaRowStartIndex = 1 + (if (transientMessage != null) 1 else 0)
        val hitTestAdapter = remember(
            mediaGridState,
            rowItems,
            rowKeys,
            rowKeyToMediaIds,
            rowKeyToIndex,
            mediaRowStartIndex,
        ) {
            MultiSelectHitTestAdapter(
                hitTest = { touchPos ->
                    val layout = mediaGridState.layoutInfo
                    val visible = layout.visibleItemsInfo.firstOrNull { item ->
                        item.index >= mediaRowStartIndex &&
                            touchPos.y.toInt() in item.offset.y until (item.offset.y + item.size.height)
                    } ?: return@MultiSelectHitTestAdapter null
                    val rowIndex = visible.index - mediaRowStartIndex
                    val row = rowItems.getOrNull(rowIndex) ?: return@MultiSelectHitTestAdapter null
                    val colWidth = (visible.size.width.toFloat() / mediaGridColumns).coerceAtLeast(1f)
                    val localX = (touchPos.x - visible.offset.x).coerceAtLeast(0f)
                    val colIndex = (localX / colWidth).toInt().coerceIn(0, mediaGridColumns - 1)
                    val entry = row.getOrNull(colIndex) ?: return@MultiSelectHitTestAdapter null
                    MultiSelectHitResult(
                        mediaId = entry.id,
                        rowKey = rowKeys.getOrNull(rowIndex),
                        rowIndex = rowIndex,
                        isSelectable = true,
                        colIndex = colIndex,
                        columnsInRow = row.size,
                    )
                },
                mediaIdsInRow = { rowKey -> rowKeyToMediaIds[rowKey].orEmpty() },
                rowKeyAtIndex = { rowIndex -> rowKeys.getOrNull(rowIndex) },
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = modifier.multiSelectSwipeGesture(
                enabled = selectionMode,
                hitTestAdapter = hitTestAdapter,
                selectedIds = selectedEntryIds,
                onSelectionChange = { onSelectionStateChange(true, it) },
                onAutoScroll = { delta -> mediaGridState.scrollBy(delta) },
            ),
            state = mediaGridState,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                TrashCategoryActionRow(
                    selectedType = selectedType,
                    entryCount = entries.size,
                    directory = collaboratorDirectory,
                    selectedCollaboratorUserIds = selectedCollaboratorUserIds,
                    menuExpanded = showCategoryMenu,
                    onMenuExpandedChange = { showCategoryMenu = it },
                    onTypeSelected = { onSelectedTypeNameChange(it.name) },
                    onToggleCollaborator = { userId ->
                        savedSelectedCollaboratorUserIds = toggleCollaboratorSelectionKeepingEmpty(
                            currentSelection = selectedCollaboratorUserIds,
                            toggledUserId = userId,
                            allUserIds = allCollaboratorUserIds,
                        ).toList()
                    },
                    selectionMode = selectionMode,
                    selectedCount = selectedEntries.size,
                    onCancelSelection = {
                        onSelectionStateChange(false, emptySet())
                    },
                    onRestoreCurrent = { requestRestoreFromTopBar() },
                    onRequestClearCurrent = { requestDeleteFromTopBar() },
                )
            }

            transientMessage?.let { message ->
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    TrashSnackbarCard(message = message)
                }
            }

            if (entries.isEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    TrashEmptyCard(
                        text = if (selectedCollaboratorUserIds.isEmpty()) {
                            "未选中任何账号，当前不展示条目"
                        } else {
                            "暂无删除条目"
                        },
                    )
                }
            } else {
                gridItems(
                    items = rowItems,
                    key = { row -> row.firstOrNull()?.id ?: "empty-row" },
                    span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                ) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        row.forEach { entry ->
                            TrashMediaGridCell(
                                entry = entry,
                                showPostTitle = selectedType == TrashEntryType.MEDIA_REMOVED,
                                selected = entry.id in selectedEntryIds,
                                selectionMode = selectionMode,
                                actorIdentity = resolveTrashActorIdentity(entry, collaboratorDirectory),
                                showActorBadge = showActorBadge,
                                modifier = Modifier.weight(1f),
                                onOpenDetail = {
                                    onOpenTrashDetail(
                                        TrashDetailRoute(
                                            entryId = entry.id,
                                            entryType = entry.type,
                                            sourcePostId = entry.sourcePostId,
                                            sourceMediaId = entry.sourceMediaId,
                                        ),
                                    )
                                },
                                onToggleSelection = { toggleSelection(entry) },
                                onLongClick = { toggleSelection(entry) },
                            )
                        }
                        repeat(mediaGridColumns - row.size) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                            )
                        }
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item {
                TrashCategoryActionRow(
                    selectedType = selectedType,
                    entryCount = entries.size,
                    directory = collaboratorDirectory,
                    selectedCollaboratorUserIds = selectedCollaboratorUserIds,
                    menuExpanded = showCategoryMenu,
                    onMenuExpandedChange = { showCategoryMenu = it },
                    onTypeSelected = { onSelectedTypeNameChange(it.name) },
                    onToggleCollaborator = { userId ->
                        savedSelectedCollaboratorUserIds = toggleCollaboratorSelectionKeepingEmpty(
                            currentSelection = selectedCollaboratorUserIds,
                            toggledUserId = userId,
                            allUserIds = allCollaboratorUserIds,
                        ).toList()
                    },
                    selectionMode = selectionMode,
                    selectedCount = selectedEntries.size,
                    onCancelSelection = {
                        onSelectionStateChange(false, emptySet())
                    },
                    onRestoreCurrent = { requestRestoreFromTopBar() },
                    onRequestClearCurrent = { requestDeleteFromTopBar() },
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
                    text = if (selectedCollaboratorUserIds.isEmpty()) {
                        "未选中任何账号，当前不展示条目"
                    } else {
                        "暂无删除条目"
                    },
                )
            }
        } else {
            items(
                items = entries,
                key = { it.id },
            ) { entry ->
                TrashEntryRow(
                    entry = entry,
                    selected = entry.id in selectedEntryIds,
                    selectionMode = selectionMode,
                    actorIdentity = resolveTrashActorIdentity(entry, collaboratorDirectory),
                    showActorBadge = showActorBadge,
                    onClick = {
                        if (selectionMode) {
                            toggleSelection(entry)
                        } else {
                            onOpenTrashDetail(
                                TrashDetailRoute(
                                    entryId = entry.id,
                                    entryType = entry.type,
                                    sourcePostId = entry.sourcePostId,
                                    sourceMediaId = entry.sourceMediaId,
                                ),
                            )
                        }
                    },
                    onLongClick = { toggleSelection(entry) },
                )
            }
        }
    }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空当前分类？") },
            text = {
                Text(
                    "将永久删除当前「${selectedType.label}」分类中的 ${entries.size} 项。属于媒体删除的项目会同时删除原文件；只从小相册移除的项目不会影响其他位置仍在使用的照片或视频。",
                )
            },
            confirmButton = {
                TrashDialogActionButton(
                    text = "清空当前分类",
                    danger = true,
                    enabled = entries.isNotEmpty(),
                    onClick = {
                        showClearConfirm = false
                        deleteEntries(entries, "已清空当前分类")
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { showClearConfirm = false })
            },
        )
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirm = false
                pendingRestoreEntries = emptyList()
            },
            title = { Text("确认恢复？") },
            text = { Text("将恢复 ${pendingRestoreEntries.size} 个回收站条目。恢复后会回到对应照片流或小相册关系。") },
            confirmButton = {
                TrashDialogActionButton(
                    text = "恢复",
                    emphasized = true,
                    enabled = pendingRestoreEntries.isNotEmpty(),
                    onClick = {
                        val restoringEntries = pendingRestoreEntries
                        showRestoreConfirm = false
                        pendingRestoreEntries = emptyList()
                        restoreEntries(restoringEntries)
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(
                    text = "取消",
                    onClick = {
                        showRestoreConfirm = false
                        pendingRestoreEntries = emptyList()
                    },
                )
            },
        )
    }

    if (showDeleteSelectedConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedConfirm = false },
            title = { Text("删除选中项？") },
            text = {
                Text(
                    "将永久删除当前选中的 ${selectedEntries.size} 项。属于媒体删除的项目会同时删除原文件；只从小相册移除的项目不会影响其他位置仍在使用的照片或视频。",
                )
            },
            confirmButton = {
                TrashDialogActionButton(
                    text = "删除选中项",
                    danger = true,
                    enabled = selectedEntries.isNotEmpty(),
                    onClick = {
                        showDeleteSelectedConfirm = false
                        deleteEntries(selectedEntries, "已删除选中项")
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { showDeleteSelectedConfirm = false })
            },
        )
    }
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
        ?: postSnapshot?.mediaSnapshots?.firstOrNull { it.isCover }
        ?: postSnapshot?.mediaSnapshots?.firstOrNull()
}

private fun trashEntryTypeDescription(entry: TrashEntryUiModel): String {
    return when (entry.type) {
        TrashEntryType.POST_DELETED -> "小相册已进入回收站"
        TrashEntryType.MEDIA_REMOVED -> "只移除了当前小相册关联"
        TrashEntryType.MEDIA_SYSTEM_DELETED -> "媒体已从照片流和相关小相册删除"
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
