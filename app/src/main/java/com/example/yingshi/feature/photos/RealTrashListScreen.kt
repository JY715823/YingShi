package com.example.yingshi.feature.photos

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshi.feature.sync.StaleBanner
import com.example.yingshi.feature.sync.SyncModule
import com.example.yingshi.feature.sync.SyncVersionTracker
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
fun RealTrashPageScreen(
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
    val sessionKey = realBackendSessionKey("real-trash-list")
    val selectedType = parseTrashEntryTypeOrDefault(
        value = selectedTypeName,
        default = TrashEntryType.MEDIA_SYSTEM_DELETED,
    )
    val viewModel: RealTrashListViewModel = viewModel(
        key = sessionKey,
        factory = RealTrashListViewModel.factory(initialSelectedType = selectedType),
    )
    val uiState by viewModel.uiState.collectAsState()
    val backendMutationEvent by RealBackendMutationBus.latestEvent.collectAsState()
    val spacing = YingShiThemeTokens.spacing
    val collaboratorDirectory = rememberCollaboratorDirectorySnapshot(fallbackToFakeProfile = false)
    val allCollaboratorUserIds = remember(collaboratorDirectory) {
        collaboratorDirectory.all.mapTo(linkedSetOf()) { it.userId }
    }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var pendingRestoreEntries by remember { mutableStateOf(emptyList<TrashEntryUiModel>()) }
    var previousSelectedTypeName by rememberSaveable { mutableStateOf(selectedTypeName) }
    var savedSelectedCollaboratorUserIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var collaboratorSelectionInitialized by rememberSaveable { mutableStateOf(false) }
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
    val entries = remember(uiState.entries, selectedCollaboratorUserIds, allCollaboratorUserIds) {
        filterTrashEntriesByCollaborator(
            entries = uiState.entries,
            directory = collaboratorDirectory,
            selectedUserIds = selectedCollaboratorUserIds,
            allUserIds = allCollaboratorUserIds,
        ).distinctBy { it.businessIdentityKey() }
    }
    val pendingEntries = uiState.pendingEntries
    val showActorBadge = isAllCollaboratorsSelected(
        selectedUserIds = selectedCollaboratorUserIds,
        allUserIds = allCollaboratorUserIds,
    )
    val selectedEntries = entries.filter { it.id in selectedEntryIds }

    fun toggleSelection(entry: TrashEntryUiModel) {
        onSelectionStateChange(
            true,
            if (entry.id in selectedEntryIds) {
                selectedEntryIds - entry.id
            } else {
                selectedEntryIds + entry.id
            },
        )
    }

    fun restoreFromTopBar() {
        if (selectionMode) {
            if (selectedEntries.isEmpty()) {
                viewModel.showSelectionMessage("未选择可恢复的条目")
            } else {
                pendingRestoreEntries = selectedEntries
                showRestoreConfirm = true
            }
        } else {
            pendingRestoreEntries = entries
            showRestoreConfirm = true
        }
    }

    fun deleteFromTopBar() {
        if (selectionMode) {
            if (selectedEntries.isEmpty()) {
                viewModel.showSelectionMessage("未选择可移除的条目")
            } else {
                showDeleteSelectedConfirm = true
            }
        } else {
            showClearConfirm = true
        }
    }

    fun undoPendingEntry(pending: TrashPendingCleanupUiModel) {
        viewModel.undoPendingCleanup(pending.entry.id, selectedType)
        SyncVersionTracker.markLocalMutation(SyncModule.TRASH)
    }

    fun purgePendingEntry(pending: TrashPendingCleanupUiModel) {
        viewModel.purgePendingCleanupEntries(listOf(pending), selectedType)
        SyncVersionTracker.markLocalMutation(SyncModule.TRASH)
        SyncVersionTracker.markLocalMutation(SyncModule.PHOTO_FEED)
        SyncVersionTracker.markLocalMutation(SyncModule.ALBUMS)
        SyncVersionTracker.markLocalMutation(SyncModule.SYSTEM_MEDIA)
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

    LaunchedEffect(selectedTypeName) {
        viewModel.refresh(selectedType)
    }
    LaunchedEffect(backendMutationEvent.version) {
        if (backendMutationEvent.version > 0 && backendMutationEvent.affectsTrash()) {
            viewModel.refresh(selectedType)
        }
    }
    val syncStaleState by SyncVersionTracker.staleState.collectAsState()
    LaunchedEffect(Unit) {
        snapshotFlow { syncStaleState.trashStale }
            .collect { currentlyStale ->
                if (currentlyStale) {
                    viewModel.refresh(selectedType)
                    SyncVersionTracker.markRefreshed(SyncModule.TRASH)
                }
            }
    }
    ReconnectRefreshEffect(
        shouldRefresh = uiState.isOfflineReadOnly ||
            uiState.errorMessage != null ||
            uiState.tokenMissing ||
            (uiState.isLoading && uiState.entries.isEmpty()),
        onReconnect = { viewModel.refresh(selectedType) },
    )

    if (showPendingCleanup) {
        RealTrashPendingCleanupScreen(
            pendingEntries = pendingEntries,
            directory = collaboratorDirectory,
            isLoading = uiState.isLoading && pendingEntries.isEmpty(),
            isMutating = uiState.isMutating,
            errorMessage = uiState.errorMessage,
            statusMessage = uiState.statusMessage,
            onBack = { onShowPendingCleanupChange(false) },
            onRefresh = { viewModel.refresh(selectedType) },
            onUndo = ::undoPendingEntry,
            onPurge = ::purgePendingEntry,
            modifier = modifier,
        )
        return
    }

    val motionSectionMillis = YingShiThemeTokens.motion.sectionMillis
    val motionEasing = YingShiThemeTokens.motion.easing
    AnimatedContent(
        targetState = selectedType,
        transitionSpec = {
            fadeIn(animationSpec = tween(motionSectionMillis, easing = motionEasing)) togetherWith
                fadeOut(animationSpec = tween(motionSectionMillis, easing = motionEasing))
        },
        label = "trash-list-switch",
    ) { targetType ->
        if (targetType.isRealMediaTrashType()) {
        val mediaEntries = entries.sortedByDescending { it.deletedAtMillis }
        val mediaGridState = rememberLazyGridState()
        val mediaGridColumns = 3
        val rowItems = remember(mediaEntries) {
            mediaEntries.chunked(mediaGridColumns)
        }
        val rowKeys = remember(rowItems) {
            rowItems.indices.map { "trash-media-row-$it" }
        }
        val rowKeyToMediaIds = remember(rowItems, rowKeys) {
            rowKeys.zip(rowItems).associate { (rowKey, rowEntries) ->
                rowKey to rowEntries.map { it.id }
            }
        }
        val rowKeyToIndex = remember(rowKeys) {
            rowKeys.withIndex().associate { it.value to it.index }
        }
        val mediaRowStartIndex = 1 +
            (if (uiState.statusMessage != null) 1 else 0) +
            (if (uiState.errorMessage != null) 1 else 0)
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
            item(span = { GridItemSpan(maxLineSpan) }) {
                RealTrashCategoryActionRow(
                    selectedType = selectedType,
                    entryCount = entries.size,
                    pendingCount = pendingEntries.size,
                    directory = collaboratorDirectory,
                    selectedCollaboratorUserIds = selectedCollaboratorUserIds,
                    menuExpanded = showCategoryMenu,
                    isMutating = uiState.isMutating,
                    onMenuExpandedChange = { showCategoryMenu = it },
                    onTypeSelected = { onSelectedTypeNameChange(it.name) },
                    onOpenPendingCleanup = { onShowPendingCleanupChange(true) },
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
                    onRestoreCurrent = { restoreFromTopBar() },
                    onRequestClearCurrent = { deleteFromTopBar() },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                RealTrashCategoryHeader(
                    title = targetType.label,
                    subtitle = if (entries.isNotEmpty()) "共 ${entries.size} 项" else null,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                StaleBanner(
                    module = SyncModule.TRASH,
                    onRefresh = {
                        viewModel.refresh(selectedType)
                        SyncVersionTracker.markRefreshed(SyncModule.TRASH)
                    },
                )
            }
            uiState.errorMessage?.let { message ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RealTrashSectionCard(title = "请求失败", body = message)
                }
            }
            when {
                uiState.isLoading && uiState.entries.isEmpty() -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        RealTrashSectionCard(title = "读取中", body = "正在读取回收站列表…")
                    }
                }
                entries.isEmpty() && uiState.errorMessage == null -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        RealTrashCenteredEmptyState(
                            text = if (selectedCollaboratorUserIds.isEmpty()) {
                                "未选中任何账号，当前不展示条目"
                            } else {
                                "当前分类为空"
                            },
                        )
                    }
                }
                else -> {
                    gridItems(
                        items = rowItems,
                        key = { row -> row.firstOrNull()?.id ?: "empty-row" },
                        span = { GridItemSpan(maxLineSpan) },
                    ) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            row.forEach { entry ->
                                RealTrashMediaGridCell(
                                    entry = entry,
                                    showPostTitle = selectedType == TrashEntryType.MEDIA_REMOVED,
                                    selected = entry.id in selectedEntryIds,
                                    selectionMode = selectionMode,
                                    actorIdentity = resolveTrashActorIdentity(entry, collaboratorDirectory),
                                    showActorBadge = showActorBadge,
                                    modifier = Modifier.weight(1f),
                                    onOpenDetail = {
                                        onOpenTrashDetail(TrashDetailRoute(entryId = entry.id))
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
        }
    } else if (targetType == TrashEntryType.SMALL_ALBUM_DELETED) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                RealTrashCategoryActionRow(
                    selectedType = selectedType,
                    entryCount = entries.size,
                    pendingCount = pendingEntries.size,
                    directory = collaboratorDirectory,
                    selectedCollaboratorUserIds = selectedCollaboratorUserIds,
                    menuExpanded = showCategoryMenu,
                    isMutating = uiState.isMutating,
                    onMenuExpandedChange = { showCategoryMenu = it },
                    onTypeSelected = { onSelectedTypeNameChange(it.name) },
                    onOpenPendingCleanup = { onShowPendingCleanupChange(true) },
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
                    onRestoreCurrent = { restoreFromTopBar() },
                    onRequestClearCurrent = { deleteFromTopBar() },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                RealTrashCategoryHeader(
                    title = targetType.label,
                    subtitle = if (entries.isNotEmpty()) "共 ${entries.size} 项" else null,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                StaleBanner(
                    module = SyncModule.TRASH,
                    onRefresh = {
                        viewModel.refresh(selectedType)
                        SyncVersionTracker.markRefreshed(SyncModule.TRASH)
                    },
                )
            }
            uiState.errorMessage?.let { message ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RealTrashSectionCard(title = "请求失败", body = message)
                }
            }
            when {
                uiState.isLoading && uiState.entries.isEmpty() -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        RealTrashSectionCard(title = "读取中", body = "正在读取回收站列表…")
                    }
                }
                entries.isEmpty() && uiState.errorMessage == null -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        RealTrashCenteredEmptyState(
                            text = if (selectedCollaboratorUserIds.isEmpty()) {
                                "未选中任何账号，当前不展示条目"
                            } else {
                                "当前分类为空"
                            },
                        )
                    }
                }
                else -> {
                    gridItems(
                        items = entries.sortedByDescending { it.deletedAtMillis },
                        key = { it.id },
                    ) { entry ->
                        RealTrashPostGridCard(
                            entry = entry,
                            selected = entry.id in selectedEntryIds,
                            selectionMode = selectionMode,
                            actorIdentity = resolveTrashActorIdentity(entry, collaboratorDirectory),
                            showActorBadge = showActorBadge,
                            onClick = {
                                if (selectionMode) {
                                    toggleSelection(entry)
                                } else {
                                    onOpenTrashDetail(TrashDetailRoute(entryId = entry.id))
                                }
                            },
                            onLongClick = { toggleSelection(entry) },
                        )
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
                RealTrashCategoryActionRow(
                    selectedType = selectedType,
                    entryCount = entries.size,
                    pendingCount = pendingEntries.size,
                    directory = collaboratorDirectory,
                    selectedCollaboratorUserIds = selectedCollaboratorUserIds,
                    menuExpanded = showCategoryMenu,
                    isMutating = uiState.isMutating,
                    onMenuExpandedChange = { showCategoryMenu = it },
                    onTypeSelected = { onSelectedTypeNameChange(it.name) },
                    onOpenPendingCleanup = { onShowPendingCleanupChange(true) },
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
                    onRestoreCurrent = { restoreFromTopBar() },
                    onRequestClearCurrent = { deleteFromTopBar() },
                )
            }

            item {
                RealTrashCategoryHeader(
                    title = targetType.label,
                    subtitle = if (entries.isNotEmpty()) "共 ${entries.size} 项" else null,
                )
            }

            item {
                StaleBanner(
                    module = SyncModule.TRASH,
                    onRefresh = {
                        viewModel.refresh(selectedType)
                        SyncVersionTracker.markRefreshed(SyncModule.TRASH)
                    },
                )
            }

            if (uiState.errorMessage != null) {
                item {
                    RealTrashSectionCard(
                        title = "请求失败",
                        body = uiState.errorMessage ?: "",
                    )
                }
            }

            when {
                uiState.isLoading && uiState.entries.isEmpty() -> {
                    item {
                        RealTrashSectionCard(
                            title = "读取中",
                            body = "正在读取回收站列表…",
                        )
                    }
                }
                entries.isEmpty() && uiState.errorMessage == null -> {
                    item {
                        RealTrashCenteredEmptyState(
                            text = if (selectedCollaboratorUserIds.isEmpty()) {
                                "未选中任何账号，当前不展示条目"
                            } else {
                                "当前分类为空"
                            },
                        )
                    }
                }
                else -> {
                    items(
                        items = entries,
                        key = { it.id },
                    ) { entry ->
                        RealTrashEntryRow(
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
                                        TrashDetailRoute(entryId = entry.id),
                                    )
                                }
                            },
                            onLongClick = { toggleSelection(entry) },
                            trailing = if (selectionMode) {
                                null
                            } else {
                                {
                                    Text(
                                        text = "查看",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = YingShiThemeTokens.colors.titleAccent,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("移出当前分类？") },
            text = {
                Text(
                    "将把当前「${selectedType.label}」分类中的 ${entries.size} 项移到待清理。24 小时内可撤销，也可以在待清理页永久删除。",
                )
            },
            containerColor = YingShiThemeTokens.colors.raisedSurface,
            titleContentColor = YingShiThemeTokens.colors.titleAccent,
            textContentColor = YingShiThemeTokens.colors.textSecondary,
            confirmButton = {
                TrashDialogActionButton(
                    text = "移出回收站",
                    danger = true,
                    enabled = entries.isNotEmpty() && !uiState.isMutating,
                    onClick = {
                        showClearConfirm = false
                        viewModel.moveEntriesToPendingCleanup(
                            entries = entries,
                            selectedType = selectedType,
                        )
                        SyncVersionTracker.markLocalMutation(SyncModule.TRASH)
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
            text = {
                Text("将恢复 ${pendingRestoreEntries.size} 个回收站条目。恢复后会回到对应照片流或小相册关系。")
            },
            containerColor = YingShiThemeTokens.colors.raisedSurface,
            titleContentColor = YingShiThemeTokens.colors.titleAccent,
            textContentColor = YingShiThemeTokens.colors.textSecondary,
            confirmButton = {
                TrashDialogActionButton(
                    text = "恢复",
                    emphasized = true,
                    enabled = pendingRestoreEntries.isNotEmpty() && !uiState.isMutating,
                    onClick = {
                        val restoringEntries = pendingRestoreEntries
                        showRestoreConfirm = false
                        pendingRestoreEntries = emptyList()
                        viewModel.restoreEntries(
                            entries = restoringEntries,
                            selectedType = selectedType,
                            onFirstRestoredMediaIds = onRestoreTargetMediaIds,
                        )
                        SyncVersionTracker.markLocalMutation(SyncModule.PHOTO_FEED)
                        SyncVersionTracker.markLocalMutation(SyncModule.ALBUMS)
                        SyncVersionTracker.markLocalMutation(SyncModule.TRASH)
                        SyncVersionTracker.markLocalMutation(SyncModule.NOTIFICATIONS)
                        SyncVersionTracker.markLocalMutation(SyncModule.SYSTEM_MEDIA)
                        onSelectionStateChange(false, emptySet())
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
            title = { Text("移出选中项？") },
            text = {
                Text(
                    "将把当前选中的 ${selectedEntries.size} 项移到待清理。24 小时内可撤销，也可以在待清理页永久删除。",
                )
            },
            containerColor = YingShiThemeTokens.colors.raisedSurface,
            titleContentColor = YingShiThemeTokens.colors.titleAccent,
            textContentColor = YingShiThemeTokens.colors.textSecondary,
            confirmButton = {
                TrashDialogActionButton(
                    text = "移出选中项",
                    danger = true,
                    enabled = selectedEntries.isNotEmpty() && !uiState.isMutating,
                    onClick = {
                        showDeleteSelectedConfirm = false
                        viewModel.moveEntriesToPendingCleanup(
                            entries = selectedEntries,
                            selectedType = selectedType,
                        )
                        SyncVersionTracker.markLocalMutation(SyncModule.TRASH)
                        onSelectionStateChange(false, emptySet())
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
@OptIn(ExperimentalFoundationApi::class)
private fun RealTrashEntryRow(
    entry: TrashEntryUiModel,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    actorIdentity: CollaboratorIdentityUiModel? = null,
    showActorBadge: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(Brush.linearGradient(listOf(colors.glowWash.copy(alpha = 0.18f), Color.Transparent)))
            }
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                },
            ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.viewerOverlayBorder),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(0.26f)
                    .aspectRatio(1f),
            ) {
                RealTrashEntryPreview(
                    entry = entry,
                    modifier = Modifier.matchParentSize(),
                )
                if (showActorBadge && actorIdentity != null) {
                    CollaboratorMarkerBadge(
                        identity = actorIdentity,
                        size = 20.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = if (selectionMode) 30.dp else 6.dp, bottom = 6.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(0.74f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = entry.type.label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                )
                Text(
                    text = entry.previewInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                Text(
                    text = realTrashEntrySourceLine(entry),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary.copy(alpha = 0.82f),
                )
            }
            trailing?.invoke()
            if (selectionMode) {
                Text(
                    text = if (selected) "已选" else "选择",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) colors.titleAccent else colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun RealTrashCategoryActionRow(
    selectedType: TrashEntryType,
    entryCount: Int,
    pendingCount: Int,
    directory: CollaboratorDirectorySnapshot,
    selectedCollaboratorUserIds: Set<String>,
    menuExpanded: Boolean,
    isMutating: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onTypeSelected: (TrashEntryType) -> Unit,
    onOpenPendingCleanup: () -> Unit,
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
            RealTrashIconActionButton(
                text = "取消",
                enabled = !isMutating,
                onClick = onCancelSelection,
            )
            Text(
                text = "已选 $selectedCount 项",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
        }
        if (!selectionMode && directory.all.isNotEmpty()) {
            CollaboratorFilterChipRow(
                directory = directory,
                selectedUserIds = selectedCollaboratorUserIds,
                onToggleCollaborator = onToggleCollaborator,
                modifier = Modifier.padding(end = spacing.xxs),
            )
        }
        Box(modifier = Modifier.weight(1f))
        if (!selectionMode) {
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
                    TrashCategoryMenuTypes.forEach { type ->
                        DropdownMenuItem(
                            text = {
                                RealTrashMenuRow(
                                    text = type.label,
                                    trailing = if (type == selectedType) "✓" else null,
                                    selected = type == selectedType,
                                )
                            },
                            onClick = {
                                onMenuExpandedChange(false)
                                onTypeSelected(type)
                            },
                        )
                    }
                    if (pendingCount > 0) {
                        DropdownMenuItem(
                            text = {
                                RealTrashMenuRow(
                                    text = "待清理",
                                    trailing = pendingCount.toString(),
                                    selected = false,
                                )
                            },
                            onClick = {
                                onMenuExpandedChange(false)
                                onOpenPendingCleanup()
                            },
                        )
                    }
                }
            }
        }
        RealTrashIconActionButton(
            text = if (isMutating) "处理中" else "恢复",
            icon = Icons.AutoMirrored.Filled.Undo,
            enabled = (entryCount > 0 || selectionMode) && !isMutating,
            onClick = onRestoreCurrent,
            emphasized = true,
        )
        RealTrashIconActionButton(
            text = "移出",
            icon = Icons.Filled.Delete,
            enabled = (entryCount > 0 || selectionMode) && !isMutating,
            onClick = onRequestClearCurrent,
            danger = true,
        )
    }
}

@Composable
private fun RealTrashMenuRow(
    text: String,
    trailing: String?,
    selected: Boolean,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) colors.primaryContainer.copy(alpha = 0.64f) else colors.raisedSurface,
                RoundedCornerShape(YingShiThemeTokens.radius.md),
            )
            .padding(horizontal = spacing.xs, vertical = spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (selected) colors.titleAccent else colors.textPrimary,
        )
        trailing?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
        }
    }
}

@Composable
internal fun RealTrashIconActionButton(
    text: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val shape = CircleShape
    val bgColor = when {
        !enabled -> colors.sectionBackground.copy(alpha = 0.30f)
        danger -> colors.destructiveContainer.copy(alpha = 0.12f)
        emphasized -> colors.primaryContainer.copy(alpha = 0.50f)
        else -> colors.raisedSurface.copy(alpha = 0.70f)
    }
    val contentColor = when {
        !enabled -> colors.textSecondary.copy(alpha = 0.45f)
        danger -> colors.destructive
        else -> colors.titleAccent
    }
    val borderColor = when {
        !enabled -> colors.dividerSoft.copy(alpha = 0.30f)
        danger -> colors.destructive.copy(alpha = 0.18f)
        else -> colors.dividerSoft.copy(alpha = 0.50f)
    }
    Surface(
        modifier = (if (icon != null) Modifier.size(44.dp) else Modifier)
            .yingShiClickable(
                enabled = enabled,
                shape = shape,
                pressedScale = 0.94f,
                onClick = onClick,
            ),
        shape = shape,
        color = bgColor,
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
                    modifier = Modifier.size(22.dp),
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
@OptIn(ExperimentalFoundationApi::class)
private fun RealTrashMediaGridCell(
    entry: TrashEntryUiModel,
    showPostTitle: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    actorIdentity: CollaboratorIdentityUiModel? = null,
    showActorBadge: Boolean = false,
    modifier: Modifier = Modifier,
    onOpenDetail: () -> Unit,
    onToggleSelection: () -> Unit,
    onLongClick: () -> Unit,
) {
    val media = entry.mediaSnapshot
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
                .clip(RoundedCornerShape(YingShiThemeTokens.radius.sm))
                .then(
                    if (selected) {
                        Modifier.border(BorderStroke(1.dp, YingShiThemeTokens.colors.viewerAccent.copy(alpha = 0.58f)), RoundedCornerShape(YingShiThemeTokens.radius.sm))
                    } else {
                        Modifier
                    },
                ),
        ) {
            RealTrashEntryPreview(
                entry = entry,
                modifier = Modifier.matchParentSize(),
            )
            RealTrashDaysBadge(
                days = realTrashDaysSince(entry.deletedAtMillis),
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
            RealTrashSelectionOverlay(
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
            RealTrashPostTitleChip(text = realTrashGridPostTitle(entry))
        }
    }
}


