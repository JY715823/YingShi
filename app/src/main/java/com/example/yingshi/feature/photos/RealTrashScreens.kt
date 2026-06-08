package com.example.yingshi.feature.photos

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshi.data.model.RemoteTrashDetail
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val MissingOriginalMediaMessage = "没有找到可查看的原媒体。"
private const val EmptyTrashPreviewMessage = "没有更多可显示的内容。"
private const val MinRealTrashViewerScale = 1f
private const val MaxRealTrashViewerScale = 4f
private const val RealTrashViewerResetScale = 1.02f

private class RealTrashViewerZoomState {
    var scale by mutableStateOf(MinRealTrashViewerScale)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    val isZoomed: Boolean
        get() = scale > RealTrashViewerResetScale

    fun reset() {
        scale = MinRealTrashViewerScale
        offset = Offset.Zero
    }

    fun applyTransform(
        zoomChange: Float,
        panChange: Offset,
        containerSize: IntSize,
        contentSize: IntSize,
    ) {
        if (containerSize.width <= 0 || containerSize.height <= 0) return
        val nextScale = (scale * zoomChange).coerceIn(MinRealTrashViewerScale, MaxRealTrashViewerScale)
        if (nextScale <= RealTrashViewerResetScale) {
            reset()
            return
        }
        scale = nextScale
        offset = clampOffset(
            value = offset + panChange,
            currentScale = nextScale,
            containerSize = containerSize,
            contentSize = contentSize,
        )
    }

    fun panBy(
        panChange: Offset,
        containerSize: IntSize,
        contentSize: IntSize,
    ) {
        if (!isZoomed || containerSize.width <= 0 || containerSize.height <= 0) return
        offset = clampOffset(
            value = offset + panChange,
            currentScale = scale,
            containerSize = containerSize,
            contentSize = contentSize,
        )
    }

    private fun clampOffset(
        value: Offset,
        currentScale: Float,
        containerSize: IntSize,
        contentSize: IntSize,
    ): Offset {
        val scaledWidth = contentSize.width * currentScale
        val scaledHeight = contentSize.height * currentScale
        val maxX = ((scaledWidth - containerSize.width) / 2f).coerceAtLeast(0f)
        val maxY = ((scaledHeight - containerSize.height) / 2f).coerceAtLeast(0f)
        return Offset(
            x = value.x.coerceIn(-maxX, maxX),
            y = value.y.coerceIn(-maxY, maxY),
        )
    }
}

private fun Modifier.realTrashViewerZoomGesture(
    zoomState: RealTrashViewerZoomState,
    contentSize: IntSize,
): Modifier = pointerInput(zoomState, contentSize) {
    awaitEachGesture {
        while (true) {
            val event = awaitPointerEvent()
            val activeChanges = event.changes.filter { it.pressed }
            if (activeChanges.isEmpty()) break

            if (activeChanges.size >= 2) {
                val currentCentroid = activeChanges.realTrashCentroid(usePrevious = false)
                val previousCentroid = activeChanges.realTrashCentroid(usePrevious = true)
                val currentDistance = activeChanges.realTrashAverageDistanceTo(
                    centroid = currentCentroid,
                    usePrevious = false,
                )
                val previousDistance = activeChanges.realTrashAverageDistanceTo(
                    centroid = previousCentroid,
                    usePrevious = true,
                )
                val zoomChange = if (previousDistance > 0f) {
                    currentDistance / previousDistance
                } else {
                    MinRealTrashViewerScale
                }
                zoomState.applyTransform(
                    zoomChange = zoomChange,
                    panChange = currentCentroid - previousCentroid,
                    containerSize = size,
                    contentSize = contentSize,
                )
                activeChanges.forEach { it.consume() }
            } else if (zoomState.isZoomed) {
                zoomState.panBy(
                    panChange = activeChanges.first().positionChange(),
                    containerSize = size,
                    contentSize = contentSize,
                )
                activeChanges.forEach { it.consume() }
            }
        }
    }
}

private fun List<PointerInputChange>.realTrashCentroid(usePrevious: Boolean): Offset {
    val total = fold(Offset.Zero) { sum, change ->
        sum + if (usePrevious) change.previousPosition else change.position
    }
    return total / size.toFloat()
}

private fun List<PointerInputChange>.realTrashAverageDistanceTo(
    centroid: Offset,
    usePrevious: Boolean,
): Float {
    return sumOf { change ->
        val position = if (usePrevious) change.previousPosition else change.position
        (position - centroid).getDistance().toDouble()
    }.toFloat() / size
}

@Composable
private fun RealTrashViewerMetaCapsule(
    text: String,
    emphasized: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (emphasized) {
            colors.viewerSurface.copy(alpha = 0.92f)
        } else {
            colors.viewerSurface.copy(alpha = 0.76f)
        },
        border = BorderStroke(
            1.dp,
            if (emphasized) {
                colors.viewerAccent.copy(alpha = 0.44f)
            } else {
                colors.viewerAccent.copy(alpha = 0.14f)
            },
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (emphasized) {
                colors.viewerAccent
            } else {
                colors.viewerText.copy(alpha = 0.92f)
            },
        )
    }
}

private fun realTrashViewerMediaContentDescription(mediaType: AppMediaType): String {
    return when (mediaType) {
        AppMediaType.VIDEO -> "回收站视频"
        AppMediaType.IMAGE -> "回收站照片"
    }
}

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
    ReconnectRefreshEffect(
        shouldRefresh = uiState.isOfflineReadOnly ||
            uiState.errorMessage != null ||
            uiState.tokenMissing ||
            (uiState.isLoading && uiState.entries.isEmpty()),
        onReconnect = { viewModel.refresh(selectedType) },
    )

    if (selectedType.isRealMediaTrashType()) {
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
                    directory = collaboratorDirectory,
                    selectedCollaboratorUserIds = selectedCollaboratorUserIds,
                    menuExpanded = showCategoryMenu,
                    isMutating = uiState.isMutating,
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
                    onRestoreCurrent = { restoreFromTopBar() },
                    onRequestClearCurrent = { deleteFromTopBar() },
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
                entries.isEmpty() -> {
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
    } else if (selectedType == TrashEntryType.SMALL_ALBUM_DELETED) {
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
                    directory = collaboratorDirectory,
                    selectedCollaboratorUserIds = selectedCollaboratorUserIds,
                    menuExpanded = showCategoryMenu,
                    isMutating = uiState.isMutating,
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
                    onRestoreCurrent = { restoreFromTopBar() },
                    onRequestClearCurrent = { deleteFromTopBar() },
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
                entries.isEmpty() -> {
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
                    directory = collaboratorDirectory,
                    selectedCollaboratorUserIds = selectedCollaboratorUserIds,
                    menuExpanded = showCategoryMenu,
                    isMutating = uiState.isMutating,
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
                    onRestoreCurrent = { restoreFromTopBar() },
                    onRequestClearCurrent = { deleteFromTopBar() },
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
                entries.isEmpty() -> {
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

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空当前分类？") },
            text = {
                Text(
                    "将永久删除当前「${selectedType.label}」分类中的 ${uiState.entries.size} 项。属于媒体删除的项目会同时删除原文件；只从小相册移除的项目不会影响其他位置仍在使用的照片或视频。",
                )
            },
            containerColor = YingShiThemeTokens.colors.raisedSurface,
            titleContentColor = YingShiThemeTokens.colors.titleAccent,
            textContentColor = YingShiThemeTokens.colors.textSecondary,
            confirmButton = {
                TrashDialogActionButton(
                    text = "清空当前分类",
                    danger = true,
                    enabled = uiState.entries.isNotEmpty() && !uiState.isMutating,
                    onClick = {
                        showClearConfirm = false
                        viewModel.purgeEntries(
                            entries = entries,
                            selectedType = selectedType,
                        )
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
            title = { Text("删除选中项？") },
            text = {
                Text(
                    "将永久删除当前选中的 ${selectedEntries.size} 项。属于媒体删除的项目会同时删除原文件；只从小相册移除的项目不会影响其他位置仍在使用的照片或视频。",
                )
            },
            containerColor = YingShiThemeTokens.colors.raisedSurface,
            titleContentColor = YingShiThemeTokens.colors.titleAccent,
            textContentColor = YingShiThemeTokens.colors.textSecondary,
            confirmButton = {
                TrashDialogActionButton(
                    text = "删除选中项",
                    danger = true,
                    enabled = selectedEntries.isNotEmpty() && !uiState.isMutating,
                    onClick = {
                        showDeleteSelectedConfirm = false
                        viewModel.purgeEntries(
                            entries = selectedEntries,
                            selectedType = selectedType,
                        )
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
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
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
    directory: CollaboratorDirectorySnapshot,
    selectedCollaboratorUserIds: Set<String>,
    menuExpanded: Boolean,
    isMutating: Boolean,
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
        } else {
            Box {
                RealTrashIconActionButton(
                    text = "菜单",
                    icon = Icons.Filled.Menu,
                    enabled = !isMutating,
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
                                                colors.primaryContainer.copy(alpha = 0.64f)
                                            } else {
                                                colors.raisedSurface
                                            },
                                            androidx.compose.foundation.shape.RoundedCornerShape(
                                                YingShiThemeTokens.radius.md,
                                            ),
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
        RealTrashIconActionButton(
            text = if (isMutating) "处理中" else "恢复",
            icon = Icons.AutoMirrored.Filled.Undo,
            enabled = (entryCount > 0 || selectionMode) && !isMutating,
            onClick = onRestoreCurrent,
            emphasized = true,
        )
        RealTrashIconActionButton(
            text = "删除",
            icon = Icons.Filled.Delete,
            enabled = (entryCount > 0 || selectionMode) && !isMutating,
            onClick = onRequestClearCurrent,
            danger = true,
        )
    }
}

@Composable
private fun RealTrashSelectionActionRow(
    selectedCount: Int,
    isMutating: Boolean,
    onRestoreSelected: () -> Unit,
    onRequestDeleteSelected: () -> Unit,
    onCancelSelection: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = YingShiThemeTokens.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "已选 $selectedCount 项",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.titleAccent,
        )
        RealTrashIconActionButton(
            text = if (isMutating) "处理中" else "恢复",
            enabled = selectedCount > 0 && !isMutating,
            onClick = onRestoreSelected,
            emphasized = true,
        )
        RealTrashIconActionButton(
            text = if (isMutating) "处理中" else "删除",
            enabled = selectedCount > 0 && !isMutating,
            onClick = onRequestDeleteSelected,
            danger = true,
        )
        RealTrashIconActionButton(
            text = "取消",
            enabled = !isMutating,
            onClick = onCancelSelection,
        )
    }
}

@Composable
private fun RealTrashIconActionButton(
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
        !enabled -> colors.textSecondary.copy(alpha = 0.62f)
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
private fun RealTrashCenteredEmptyState(
    text: String = "当前分类为空",
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = YingShiThemeTokens.colors.textSecondary.copy(alpha = 0.46f),
        )
    }
}

@Composable
private fun RealTrashEntryPreview(
    entry: TrashEntryUiModel,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val media = entry.mediaSnapshot
    val mediaId = media?.mediaId ?: entry.previewMediaIds().firstOrNull()
    if (mediaId == null) {
        Surface(
            modifier = modifier,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.lg),
            color = colors.sectionBackground.copy(alpha = 0.58f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = entry.type.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
        }
        return
    }
    AppContentMediaThumbnail(
        mediaSource = media?.mediaSource ?: realTrashMediaSource(
            mediaId = mediaId,
            mediaType = media?.mediaType ?: AppMediaType.IMAGE,
            width = media?.width,
            height = media?.height,
            durationMillis = media?.videoDurationMillis,
        ),
        mediaType = media?.mediaType ?: AppMediaType.IMAGE,
        palette = realPaletteFor(mediaId),
        modifier = modifier,
        requestSize = 720,
        showLoadingIndicator = true,
        showStatusBadge = true,
        showVideoPlayOverlay = false,
    )
}

@Composable
private fun RealTrashGridMonthHeader(title: String) {
    val colors = YingShiThemeTokens.colors
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 10.dp, start = 2.dp),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        color = colors.titleAccent,
    )
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
                .clip(RoundedCornerShape(4.dp)),
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

@Composable
private fun RealTrashPostTitleChip(text: String) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = colors.sectionBackground.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.62f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = colors.textSecondary,
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun RealTrashPostGridCard(
    entry: TrashEntryUiModel,
    selected: Boolean,
    selectionMode: Boolean,
    actorIdentity: CollaboratorIdentityUiModel? = null,
    showActorBadge: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val mediaCount = entry.relatedMediaIds.size
    val coverMediaId = entry.previewMediaIds().firstOrNull()
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.12f)
                    .clip(RoundedCornerShape(YingShiThemeTokens.radius.md)),
            ) {
                if (coverMediaId.isNullOrBlank()) {
                    RealTrashDeletedMediaPlaceholder(
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    AppContentMediaThumbnail(
                        mediaSource = realTrashMediaSource(coverMediaId),
                        mediaType = AppMediaType.IMAGE,
                        palette = realPaletteFor(coverMediaId),
                        modifier = Modifier.matchParentSize(),
                        requestSize = 384,
                        showLoadingIndicator = true,
                        showStatusBadge = true,
                    )
                }
                RealTrashDaysBadge(
                    days = realTrashDaysSince(entry.deletedAtMillis),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
                RealTrashViewerMetaCapsule(
                    text = "${mediaCount.coerceAtLeast(0)}项媒体",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                )
                RealTrashSelectionOverlay(
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
            Text(
                text = entry.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
            )
            Text(
                text = entry.previewInfo,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Text(
                text = realFormatTrashEntryTime(entry.deletedAtMillis),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary.copy(alpha = 0.82f),
            )
        }
    }
}

@Composable
private fun RealTrashSelectionOverlay(
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
private fun RealTrashDaysBadge(
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
            color = colors.viewerText,
        )
    }
}

private fun TrashEntryUiModel.previewMediaIds(): List<String> {
    return buildList {
        commentTargetMediaId?.takeIf { it.isNotBlank() }?.let(::add)
        sourceMediaId?.takeIf { it.isNotBlank() }?.let(::add)
        addAll(relatedMediaIds.filter { it.isNotBlank() })
    }.distinct()
}

private fun TrashEntryUiModel.commentMediaId(): String? {
    return commentTargetMediaId?.takeIf { it.isNotBlank() }
        ?: sourceMediaId?.takeIf { it.isNotBlank() }
        ?: mediaSnapshot?.mediaId?.takeIf { it.isNotBlank() }
        ?: relatedMediaIds.firstOrNull { it.isNotBlank() }
}

private fun TrashEntryUiModel.toTrashPostDetailUiModel(mediaIds: List<String>): PostDetailUiModel {
    val postId = sourcePostId?.takeIf { it.isNotBlank() } ?: id
    return PostDetailUiModel(
        postId = postId,
        title = title.ifBlank { "回收站小相册" },
        summary = previewInfo.ifBlank { EmptyTrashPreviewMessage },
        contributorLabel = "回收站小相册",
        postDisplayTimeMillis = deletedAtMillis,
        albumIds = relatedPostIds.ifEmpty { listOf(postId) },
        albumChips = listOf("已删除", "媒体 ${mediaIds.size} 项"),
        mediaItems = mediaIds.map { mediaId ->
            PostDetailMediaUiModel(
                id = mediaId,
                displayTimeMillis = deletedAtMillis,
                commentCount = 0,
                palette = realPaletteFor(mediaId),
                mediaType = AppMediaType.IMAGE,
                aspectRatio = 1f,
                displayTimeSource = DisplayTimeSourceImported,
                mediaSource = realTrashMediaSource(mediaId),
            )
        },
        comments = emptyList(),
    )
}

@Composable
private fun RealTrashPostDetailTopBar(
    detail: RemoteTrashDetail,
    actorIdentity: CollaboratorIdentityUiModel?,
    isMutating: Boolean,
    onBack: () -> Unit,
    onRestore: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RealTrashIconActionButton(
            text = "返回",
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            enabled = !isMutating,
            onClick = onBack,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "小相册详情",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            actorIdentity?.let {
                CollaboratorMarkerBadge(
                    identity = it,
                    size = 18.dp,
                )
            }
        }
        if (detail.canRestore) {
            RealTrashPostTopIconButton(
                icon = RealTrashPostTopIcon.Restore,
                enabled = !isMutating,
                onClick = onRestore,
            )
        }
        if (detail.canMoveOutOfTrash) {
            RealTrashPostTopIconButton(
                icon = RealTrashPostTopIcon.Delete,
                enabled = !isMutating,
                onClick = onRemove,
            )
        }
    }
}

private enum class RealTrashPostTopIcon {
    Restore,
    Delete,
}

@Composable
private fun RealTrashPostTopIconButton(
    icon: RealTrashPostTopIcon,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(YingShiThemeTokens.radius.capsule))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = colors.raisedSurface.copy(alpha = if (enabled) 0.94f else 0.54f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeColor = if (enabled) {
                colors.titleAccent
            } else {
                colors.textSecondary.copy(alpha = 0.72f)
            }
            val strokeWidth = 2.2.dp.toPx()
            when (icon) {
                RealTrashPostTopIcon.Restore -> {
                    drawArc(
                        color = strokeColor,
                        startAngle = 138f,
                        sweepAngle = 260f,
                        useCenter = false,
                        topLeft = Offset(size.width * 0.27f, size.height * 0.27f),
                        size = Size(size.width * 0.48f, size.height * 0.48f),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                    drawLine(
                        color = strokeColor,
                        start = Offset(size.width * 0.28f, size.height * 0.38f),
                        end = Offset(size.width * 0.27f, size.height * 0.22f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = strokeColor,
                        start = Offset(size.width * 0.28f, size.height * 0.38f),
                        end = Offset(size.width * 0.43f, size.height * 0.38f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
                RealTrashPostTopIcon.Delete -> {
                    drawLine(
                        color = strokeColor,
                        start = Offset(size.width * 0.35f, size.height * 0.35f),
                        end = Offset(size.width * 0.65f, size.height * 0.35f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = strokeColor,
                        start = Offset(size.width * 0.43f, size.height * 0.28f),
                        end = Offset(size.width * 0.57f, size.height * 0.28f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawRoundRect(
                        color = strokeColor,
                        topLeft = Offset(size.width * 0.38f, size.height * 0.41f),
                        size = Size(size.width * 0.24f, size.height * 0.28f),
                        style = Stroke(width = strokeWidth),
                    )
                }
            }
        }
    }
}

private fun realTrashEntrySourceLine(entry: TrashEntryUiModel): String {
    return when (entry.type) {
        TrashEntryType.SMALL_ALBUM_DELETED -> {
            val mediaCount = entry.relatedMediaIds.size
            "小相册删除 · 媒体 $mediaCount 项"
        }
        TrashEntryType.MEDIA_REMOVED -> {
            val source = entry.sourcePostId ?: entry.relatedPostIds.firstOrNull() ?: "当前小相册"
            "从小相册移除 · 来源 $source"
        }
        TrashEntryType.MEDIA_SYSTEM_DELETED -> {
            val postCount = entry.relatedPostIds.size
            "媒体删除 · 影响小相册 $postCount 个"
        }
    }
}

@Composable
fun RealTrashDetailScreen(
    route: TrashDetailRoute,
    onBack: () -> Unit,
    onEntryRemoved: () -> Unit,
    onEntryRestored: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessionKey = realBackendSessionKey("real-trash-detail-${route.entryId}")
    val viewModel: RealTrashDetailViewModel = viewModel(
        key = sessionKey,
        factory = RealTrashDetailViewModel.factory(route),
    )
    val uiState by viewModel.uiState.collectAsState()
    val backendMutationEvent by RealBackendMutationBus.latestEvent.collectAsState()
    val detail = uiState.detail
    val spacing = YingShiThemeTokens.spacing
    val collaboratorDirectory = rememberCollaboratorDirectorySnapshot(fallbackToFakeProfile = false)

    LaunchedEffect(backendMutationEvent.version) {
        if (backendMutationEvent.version > 0 && backendMutationEvent.affectsTrash()) {
            viewModel.refresh()
        }
    }
    ReconnectRefreshEffect(
        shouldRefresh = uiState.isOfflineReadOnly ||
            uiState.errorMessage != null ||
            uiState.tokenMissing ||
            (uiState.isLoading && uiState.detail == null),
        onReconnect = viewModel::refresh,
    )

    val mediaDetailEntry = detail?.item?.toTrashEntryUiModel()
    if (mediaDetailEntry?.type?.isRealMediaTrashType() == true) {
        val sameTypeEntries by produceState(listOf(mediaDetailEntry), detail.item.itemType, detail.item.trashItemId) {
            value = when (val result = RepositoryProvider.trashRepository.getTrashItems(detail.item.itemType)) {
                is ApiResult.Success -> result.data
                    .map { it.toTrashEntryUiModel() }
                    .filter { it.type == mediaDetailEntry.type && it.mediaSnapshot != null }
                    .distinctBy { it.businessIdentityKey() }
                    .ifEmpty { listOf(mediaDetailEntry) }
                else -> listOf(mediaDetailEntry)
            }
        }
        RealTrashMediaViewerDetailPagerContent(
            detail = detail,
            directory = collaboratorDirectory,
            entries = sameTypeEntries,
            statusMessage = uiState.statusMessage,
            errorMessage = uiState.errorMessage,
            isMutating = uiState.isMutating,
            onBack = onBack,
            onRestoreEntry = { targetEntry ->
                viewModel.restoreItem(targetEntry.id) { restoredItem ->
                    val mediaIds = restoredItem.toTrashEntryUiModel().restoreTargetMediaIds()
                    onEntryRestored(mediaIds)
                }
            },
            onRemoveEntry = { targetEntry -> viewModel.removeItem(targetEntry.id, onEntryRemoved) },
            modifier = modifier,
        )
        return
    }
    if (detail != null && mediaDetailEntry?.type == TrashEntryType.SMALL_ALBUM_DELETED) {
        RealTrashPostViewerDetailContent(
            detail = detail,
            directory = collaboratorDirectory,
            statusMessage = uiState.statusMessage,
            errorMessage = uiState.errorMessage,
            isMutating = uiState.isMutating,
            onBack = onBack,
            onRestore = {
                viewModel.restore { restoredItem ->
                    val mediaIds = restoredItem.toTrashEntryUiModel().restoreTargetMediaIds()
                    onEntryRestored(mediaIds)
                }
            },
            onRemove = { viewModel.remove(onEntryRemoved) },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        val actorIdentity = detail?.item?.toTrashEntryUiModel()?.let {
            resolveTrashActorIdentity(it, collaboratorDirectory)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RealTrashIconActionButton(text = "返回", onClick = onBack)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "回收站详情",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                actorIdentity?.let {
                    CollaboratorMarkerBadge(
                        identity = it,
                        size = 18.dp,
                    )
                }
            }
        }

        when {
            uiState.tokenMissing -> {
                RealTrashSectionCard(
                    title = "需要重新登录",
                    body = uiState.errorMessage ?: "请先完成登录后再查看回收站。",
                )
            }
            uiState.isLoading && detail == null -> {
                RealTrashSectionCard(
                    title = "读取中",
                    body = "正在读取回收站详情…",
                )
            }
            uiState.errorMessage != null && detail == null -> {
                RealTrashSectionCard(
                    title = "读取失败",
                    body = uiState.errorMessage ?: "当前无法读取回收站详情。",
                )
            }
            detail == null -> {
                RealTrashSectionCard(
                    title = "详情不可用",
                    body = "这个回收站项目可能已经被恢复或移出。",
                )
            }
            else -> {
                RealTrashDetailContent(
                    detail = detail,
                    actorIdentity = actorIdentity,
                    statusMessage = uiState.statusMessage,
                    errorMessage = uiState.errorMessage,
                    isMutating = uiState.isMutating,
                    onRestore = {
                        viewModel.restore { restoredItem ->
                            val mediaIds = restoredItem.toTrashEntryUiModel().restoreTargetMediaIds()
                            onEntryRestored(mediaIds)
                        }
                    },
                    onRemove = { viewModel.remove(onEntryRemoved) },
                    onUndoRemove = viewModel::undoRemove,
                )
            }
        }
    }
}

@Composable
private fun RealTrashMediaViewerDetailPagerContent(
    detail: RemoteTrashDetail,
    directory: CollaboratorDirectorySnapshot,
    entries: List<TrashEntryUiModel>,
    statusMessage: String?,
    errorMessage: String?,
    isMutating: Boolean,
    onBack: () -> Unit,
    onRestoreEntry: (TrashEntryUiModel) -> Unit,
    onRemoveEntry: (TrashEntryUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val accessToken = AuthSessionManager.peekAccessToken()
    val initialEntry = detail.item.toTrashEntryUiModel()
    val viewerEntries = remember(entries, initialEntry.id) {
        entries
            .filter { it.mediaSnapshot != null }
            .ifEmpty { listOf(initialEntry) }
            .distinctBy { it.businessIdentityKey() }
    }
    val initialPage = viewerEntries.indexOfFirst { it.id == initialEntry.id }
        .takeIf { it >= 0 } ?: 0
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { viewerEntries.size },
    )
    val currentEntry = viewerEntries[pagerState.currentPage.coerceIn(0, viewerEntries.lastIndex)]
    val currentMedia = currentEntry.mediaSnapshot
    val actorIdentity = resolveTrashActorIdentity(currentEntry, directory)
    val currentCommentMediaId = currentEntry.commentMediaId()
    val commentBindings = currentCommentMediaId?.let { rememberViewerCommentBindings(it) }
    val target = currentMedia?.let {
        RealOriginalMediaTarget(
            mediaId = it.mediaId,
            mediaType = it.mediaType,
            mediaSource = it.mediaSource,
        )
    }
    val originalLoadState = target?.let(RealOriginalLoadRepository::getState)
        ?: OriginalLoadState.NotLoaded
    var showPermanentDeleteConfirm by remember(currentEntry.id) {
        mutableStateOf(false)
    }
    var showRestoreConfirm by remember(currentEntry.id) {
        mutableStateOf(false)
    }
    var isImmersive by remember { mutableStateOf(false) }
    var showCommentPreview by remember(currentEntry.id) {
        mutableStateOf(false)
    }
    var videoPlaybackState by remember {
        mutableStateOf(ViewerVideoPlaybackState())
    }
    var videoControlsVisible by remember { mutableStateOf(true) }
    var videoControlsActivityNonce by remember { mutableIntStateOf(0) }
    val zoomState = remember { RealTrashViewerZoomState() }

    LaunchedEffect(currentEntry.id) {
        showCommentPreview = false
        videoPlaybackState = ViewerVideoPlaybackState(
            mediaId = currentMedia?.mediaId?.takeIf { currentMedia.mediaType == AppMediaType.VIDEO },
        )
        videoControlsVisible = true
        videoControlsActivityNonce += 1
        zoomState.reset()
    }
    LaunchedEffect(
        currentMedia?.mediaId,
        currentMedia?.mediaType,
        videoControlsVisible,
        videoControlsActivityNonce,
        videoPlaybackState.isPlaying,
        videoPlaybackState.isLoading,
        videoPlaybackState.errorMessage,
        videoPlaybackState.isCompleted,
    ) {
        if (currentMedia?.mediaType != AppMediaType.VIDEO || !videoControlsVisible) return@LaunchedEffect
        if (videoPlaybackState.isLoading || videoPlaybackState.errorMessage != null) return@LaunchedEffect
        kotlinx.coroutines.delay(2800)
        videoControlsVisible = false
    }
    BackHandler(enabled = !isImmersive && showCommentPreview) {
        showCommentPreview = false
    }
    BackHandler(enabled = !isImmersive && zoomState.isZoomed) {
        zoomState.reset()
    }
    BackHandler(enabled = isImmersive) {
        onBack()
    }
    ViewerStatusBarEffect(immersive = isImmersive)

    fun revealVideoControls() {
        videoControlsVisible = true
        videoControlsActivityNonce += 1
    }

    fun toggleImmersive() {
        val nextImmersive = !isImmersive
        applyViewerStatusBarVisibility(view, nextImmersive)
        isImmersive = nextImmersive
        if (nextImmersive) {
            showCommentPreview = false
            videoControlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(YingShiThemeTokens.colors.viewerBackground)
            .viewerSingleTapGesture { position, size ->
                if (currentMedia?.mediaType == AppMediaType.VIDEO) {
                    val topTapZonePx = with(density) { if (isImmersive) 0.dp.toPx() else 68.dp.toPx() }
                    val bottomTapZonePx = with(density) { if (isImmersive) 40.dp.toPx() else 104.dp.toPx() }
                    if (position.y <= topTapZonePx || position.y >= size.height - bottomTapZonePx) {
                        toggleImmersive()
                    }
                } else {
                    toggleImmersive()
                }
            },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = viewerEntries.size > 1 && !zoomState.isZoomed,
            key = { page -> viewerEntries[page].id },
        ) { page ->
            val pageMedia = viewerEntries[page].mediaSnapshot
            if (pageMedia == null) {
                RealTrashSectionCard(
                    title = "原媒体预览不可用",
                    body = MissingOriginalMediaMessage,
                )
            } else {
                val pageTarget = RealOriginalMediaTarget(
                    mediaId = pageMedia.mediaId,
                    mediaType = pageMedia.mediaType,
                    mediaSource = pageMedia.mediaSource,
                )
                TrashViewerMediaCanvas(
                    media = pageMedia,
                    originalLoadState = RealOriginalLoadRepository.getState(pageTarget),
                    onOriginalLoadStateChange = { state -> RealOriginalLoadRepository.setState(pageTarget, state) },
                    immersive = isImmersive,
                    zoomState = if (page == pagerState.currentPage && pageMedia.mediaType == AppMediaType.IMAGE) {
                        zoomState
                    } else {
                        null
                    },
                    videoPlaybackState = if (page == pagerState.currentPage) videoPlaybackState else null,
                    videoControlsVisible = page == pagerState.currentPage && videoControlsVisible,
                    onVideoAreaClick = {
                        if (videoControlsVisible) {
                            videoControlsVisible = false
                            videoControlsActivityNonce += 1
                        } else {
                            revealVideoControls()
                        }
                    },
                    onTogglePlayback = {
                        revealVideoControls()
                        val durationMillis = videoPlaybackState.durationMillis
                            ?: pageMedia.viewerVideoDurationMillis()
                        val shouldRestart = videoPlaybackState.isCompleted ||
                            (durationMillis > 0L && videoPlaybackState.progressMillis >= durationMillis)
                        videoPlaybackState = if (videoPlaybackState.errorMessage != null) {
                            videoPlaybackState.retryState().copy(mediaId = pageMedia.mediaId)
                        } else if (videoPlaybackState.isPlaying) {
                            videoPlaybackState.copy(isPlaying = false)
                        } else {
                            videoPlaybackState.copy(
                                mediaId = pageMedia.mediaId,
                                isPlaying = true,
                                progressMillis = if (shouldRestart) 0L else videoPlaybackState.progressMillis,
                                seekRequestMillis = if (shouldRestart) 0L else videoPlaybackState.seekRequestMillis,
                                seekRequestNonce = if (shouldRestart) {
                                    videoPlaybackState.seekRequestNonce + 1
                                } else {
                                    videoPlaybackState.seekRequestNonce
                                },
                                errorMessage = null,
                                isCompleted = false,
                            )
                        }
                    },
                    onSeekPlayback = { progressMillis ->
                        revealVideoControls()
                        val durationMillis = videoPlaybackState.durationMillis
                            ?: pageMedia.viewerVideoDurationMillis()
                        val targetMillis = progressMillis.coerceIn(0L, durationMillis.coerceAtLeast(0L))
                        videoPlaybackState = videoPlaybackState.copy(
                            mediaId = pageMedia.mediaId,
                            progressMillis = targetMillis,
                            seekRequestMillis = targetMillis,
                            seekRequestNonce = videoPlaybackState.seekRequestNonce + 1,
                            errorMessage = null,
                            isCompleted = false,
                        )
                    },
                    onVideoPlaybackStateChange = { mediaId, state ->
                        if (mediaId == currentMedia?.mediaId) {
                            videoPlaybackState = state
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (!isImmersive) {
            TrashViewerTopScrim(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(124.dp),
            )
        }

        if (!isImmersive) {
            TrashViewerBottomScrim(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(188.dp),
            )
        }

        if (!isImmersive) {
            RealTrashViewerTopBar(
                actorIdentity = actorIdentity,
                postTitle = currentEntry.takeIf { it.type == TrashEntryType.MEDIA_REMOVED }
                    ?.let(::realTrashGridPostTitle),
                isMutating = isMutating,
                canRestore = detail.canRestore,
                canRemove = detail.canMoveOutOfTrash,
                onBack = onBack,
                onRestore = { showRestoreConfirm = true },
                onRemove = { showPermanentDeleteConfirm = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 10.dp, top = 6.dp),
            )
        }

        if (!isImmersive) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(
                        end = YingShiThemeTokens.spacing.lg,
                        bottom = 0.dp,
                    ),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
            ) {
                statusMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = YingShiThemeTokens.colors.viewerTextSecondary.copy(alpha = 0.92f),
                    )
                }
                errorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.94f),
                    )
                }
            }
        }

        if (!isImmersive) {
            RealTrashViewerEdgeActions(
                commentCountLabel = commentBindings?.comments?.size?.toString() ?: "0",
                timeLabel = currentMedia?.displayTimeMillis?.let(::realFormatTrashEntryTime)
                    ?: realFormatTrashEntryTime(currentEntry.deletedAtMillis),
                originalActionLabel = if (target != null) {
                    originalLoadState.actionLabel()
                } else {
                    "原媒体不可用"
                },
                originalActionEnabled = target != null && originalLoadState != OriginalLoadState.Loading,
                originalActionEmphasized = target != null && originalLoadState == OriginalLoadState.Loaded,
                previewExpanded = showCommentPreview,
                onToggleComments = { showCommentPreview = !showCommentPreview },
                onOpenOriginal = {
                    if (target != null &&
                        originalLoadState != OriginalLoadState.Loaded &&
                        originalLoadState != OriginalLoadState.Loading
                    ) {
                        RealOriginalLoadRepository.requestOriginal(context, target, accessToken)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        start = YingShiThemeTokens.spacing.lg,
                        end = YingShiThemeTokens.spacing.lg,
                        bottom = 0.dp,
                    ),
            )
        }

        if (showCommentPreview && !isImmersive) {
            RealTrashViewerCommentPreview(
                comments = commentBindings?.comments.orEmpty(),
                isLoading = commentBindings?.isLoading == true,
                errorMessage = commentBindings?.errorMessage,
                onRetry = { commentBindings?.onRetry?.invoke() },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(
                        start = YingShiThemeTokens.spacing.lg,
                        bottom = 64.dp,
                    ),
            )
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("确认恢复？") },
            text = { Text("将恢复当前回收站媒体条目。") },
            containerColor = YingShiThemeTokens.colors.raisedSurface,
            titleContentColor = YingShiThemeTokens.colors.titleAccent,
            textContentColor = YingShiThemeTokens.colors.textSecondary,
            confirmButton = {
                TrashDialogActionButton(
                    text = "恢复",
                    emphasized = true,
                    enabled = !isMutating,
                    onClick = {
                        showRestoreConfirm = false
                        onRestoreEntry(currentEntry)
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { showRestoreConfirm = false })
            },
        )
    }

    if (showPermanentDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showPermanentDeleteConfirm = false },
            title = { Text("永久删除该回收站项目？") },
            text = {
                Text(
                    "确认后会删除回收站记录。属于媒体删除的项目会同时删除对应的原文件和预览文件，删除后无法恢复。",
                )
            },
            containerColor = YingShiThemeTokens.colors.raisedSurface,
            titleContentColor = YingShiThemeTokens.colors.titleAccent,
            textContentColor = YingShiThemeTokens.colors.textSecondary,
            confirmButton = {
                TrashDialogActionButton(
                    text = "永久删除",
                    danger = true,
                    enabled = !isMutating,
                    onClick = {
                        showPermanentDeleteConfirm = false
                        onRemoveEntry(currentEntry)
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { showPermanentDeleteConfirm = false })
            },
        )
    }
}

@Composable
private fun RealTrashMediaViewerDetailContent(
    detail: RemoteTrashDetail,
    statusMessage: String?,
    errorMessage: String?,
    isMutating: Boolean,
    onBack: () -> Unit,
    onRestore: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val item = detail.item
    val entry = item.toTrashEntryUiModel()
    val media = entry.mediaSnapshot
    val commentBindings = entry.commentMediaId()?.let { rememberViewerCommentBindings(it) }
    var showPermanentDeleteConfirm by remember(item.trashItemId) {
        mutableStateOf(false)
    }
    val target = media?.let {
        RealOriginalMediaTarget(
            mediaId = it.mediaId,
            mediaType = it.mediaType,
            mediaSource = it.mediaSource,
        )
    }
    val originalLoadState = target?.let(RealOriginalLoadRepository::getState)
        ?: OriginalLoadState.NotLoaded
    val accessToken = AuthSessionManager.peekAccessToken()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(YingShiThemeTokens.colors.viewerBackground),
    ) {
        if (media == null) {
            RealTrashSectionCard(
                title = "原媒体预览不可用",
                body = MissingOriginalMediaMessage,
            )
        } else {
            AppContentMediaThumbnail(
                mediaSource = media.mediaSource,
                mediaType = media.mediaType,
                palette = media.palette,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .aspectRatio(media.aspectRatio.coerceIn(0.45f, 2.2f)),
                contentDescription = realTrashViewerMediaContentDescription(media.mediaType),
                contentScale = ContentScale.Fit,
                requestSize = 1080,
                showLoadingIndicator = true,
                showStatusBadge = true,
                showVideoPlayOverlay = media.mediaType == AppMediaType.VIDEO,
                originalLoadState = originalLoadState,
                onOriginalLoadStateChange = { state ->
                    target?.let { RealOriginalLoadRepository.setState(it, state) }
                },
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(
                    horizontal = YingShiThemeTokens.spacing.lg,
                    vertical = YingShiThemeTokens.spacing.md,
                ),
            horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RealTrashViewerOverlayButton(text = "返回", onClick = onBack)
            Box(modifier = Modifier.weight(1f))
            if (detail.canRestore) {
                RealTrashViewerOverlayButton(
                    text = "恢复",
                    enabled = !isMutating,
                    onClick = onRestore,
                )
            }
            if (detail.canMoveOutOfTrash) {
                RealTrashViewerOverlayButton(
                    text = "删除",
                    destructive = true,
                    enabled = !isMutating,
                    onClick = { showPermanentDeleteConfirm = true },
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(YingShiThemeTokens.spacing.lg),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
        ) {
            if (target != null) {
                RealTrashViewerOverlayButton(
                    text = originalLoadState.actionLabel(),
                    onClick = {
                        if (originalLoadState != OriginalLoadState.Loaded &&
                            originalLoadState != OriginalLoadState.Loading
                        ) {
                            RealOriginalLoadRepository.requestOriginal(context, target, accessToken)
                        }
                    },
                )
            }
            if (entry.type == TrashEntryType.MEDIA_REMOVED) {
                RealTrashViewerMetaCapsule(text = realTrashGridPostTitle(entry))
            }
            statusMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = YingShiThemeTokens.colors.viewerTextSecondary.copy(alpha = 0.92f),
                )
            }
            errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.94f),
                )
            }
        }
        RealTrashViewerCommentPreview(
            comments = commentBindings?.comments.orEmpty(),
            isLoading = commentBindings?.isLoading == true,
            errorMessage = commentBindings?.errorMessage,
            onRetry = { commentBindings?.onRetry?.invoke() },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(YingShiThemeTokens.spacing.lg),
        )
    }

    if (showPermanentDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showPermanentDeleteConfirm = false },
            title = { Text("永久删除该回收站项目？") },
            text = {
                Text(
                    "确认后会删除回收站记录。属于媒体删除的项目会同时删除对应的原文件和预览文件，删除后无法恢复。",
                )
            },
            containerColor = YingShiThemeTokens.colors.raisedSurface,
            titleContentColor = YingShiThemeTokens.colors.titleAccent,
            textContentColor = YingShiThemeTokens.colors.textSecondary,
            confirmButton = {
                TrashDialogActionButton(
                    text = "永久删除",
                    danger = true,
                    enabled = !isMutating,
                    onClick = {
                        showPermanentDeleteConfirm = false
                        onRemove()
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { showPermanentDeleteConfirm = false })
            },
        )
    }
}

@Composable
private fun TrashViewerTopScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    YingShiThemeTokens.colors.viewerBackground.copy(alpha = 0.34f),
                    YingShiThemeTokens.colors.viewerBackground.copy(alpha = 0.14f),
                    Color.Transparent,
                ),
            ),
        ),
    )
}

@Composable
private fun TrashViewerBottomScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    YingShiThemeTokens.colors.viewerBackground.copy(alpha = 0.12f),
                    YingShiThemeTokens.colors.viewerBackground.copy(alpha = 0.30f),
                ),
            ),
        ),
    )
}

@Composable
private fun TrashViewerMediaCanvas(
    media: TrashMediaSnapshot,
    originalLoadState: OriginalLoadState,
    onOriginalLoadStateChange: (OriginalLoadState) -> Unit,
    immersive: Boolean,
    zoomState: RealTrashViewerZoomState?,
    videoPlaybackState: ViewerVideoPlaybackState?,
    videoControlsVisible: Boolean,
    onVideoAreaClick: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekPlayback: (Long) -> Unit,
    onVideoPlaybackStateChange: (String, ViewerVideoPlaybackState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val density = LocalDensity.current
    val topPadding by animateDpAsState(
        targetValue = if (immersive) 0.dp else 68.dp,
        label = "trashViewerCanvasTopPadding",
    )
    val bottomPadding by animateDpAsState(
        targetValue = if (immersive) {
            if (media.mediaType == AppMediaType.VIDEO) 40.dp else 0.dp
        } else {
            104.dp
        },
        label = "trashViewerCanvasBottomPadding",
    )
    BoxWithConstraints(
        modifier = modifier.padding(top = topPadding, bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        val mediaAspectRatio = media.aspectRatio.coerceIn(0.05f, 20f)
        val fittedMediaWidth = if (maxHeight * mediaAspectRatio <= maxWidth) {
            maxHeight * mediaAspectRatio
        } else {
            maxWidth
        }
        val fittedMediaHeight = if (maxWidth / mediaAspectRatio <= maxHeight) {
            maxWidth / mediaAspectRatio
        } else {
            maxHeight
        }
        val canvasWidth = if (media.mediaType == AppMediaType.VIDEO) maxWidth else fittedMediaWidth
        val canvasHeight = if (media.mediaType == AppMediaType.VIDEO) maxHeight else fittedMediaHeight
        val contentSize = with(density) {
            IntSize(canvasWidth.roundToPx(), canvasHeight.roundToPx())
        }
        val zoomTransformModifier = if (zoomState != null) {
            Modifier.graphicsLayer {
                scaleX = zoomState.scale
                scaleY = zoomState.scale
                translationX = zoomState.offset.x
                translationY = zoomState.offset.y
            }
        } else {
            Modifier
        }
        val gestureModifier = if (zoomState != null) {
            Modifier.realTrashViewerZoomGesture(
                zoomState = zoomState,
                contentSize = contentSize,
            )
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier),
            contentAlignment = Alignment.Center,
        ) {
            if (media.mediaType == AppMediaType.VIDEO) {
                Box(
                    modifier = Modifier
                        .width(canvasWidth)
                        .height(canvasHeight)
                        .background(YingShiThemeTokens.colors.viewerBackground),
                ) {
                    val viewerMedia = remember(media) { media.toViewerPhotoFeedItem() }
                    ViewerVideoCanvas(
                        media = viewerMedia,
                        playbackState = videoPlaybackState,
                        isCurrent = videoPlaybackState?.mediaId == media.mediaId,
                        originalLoadState = originalLoadState,
                        onPlaybackStateChange = onVideoPlaybackStateChange,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (videoPlaybackState?.errorMessage == null) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable(
                                    interactionSource = remember(media.mediaId) {
                                        androidx.compose.foundation.interaction.MutableInteractionSource()
                                    },
                                    indication = androidx.compose.foundation.LocalIndication.current,
                                    onClick = onVideoAreaClick,
                                ),
                        )
                    }
                    if (videoControlsVisible && videoPlaybackState != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .clickable(onClick = onTogglePlayback),
                            shape = CircleShape,
                            color = YingShiThemeTokens.colors.viewerSurface.copy(
                                alpha = if (videoPlaybackState.isPlaying) 0.74f else 0.82f,
                            ),
                            border = BorderStroke(1.dp, YingShiThemeTokens.colors.viewerAccent.copy(alpha = 0.22f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .padding(22.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                VideoGlyph(
                                    state = if (videoPlaybackState.isPlaying) VideoGlyphState.PAUSE else VideoGlyphState.PLAY,
                                    tint = YingShiThemeTokens.colors.viewerText.copy(alpha = 0.92f),
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        ViewerVideoControls(
                            playbackState = videoPlaybackState,
                            durationMillis = videoPlaybackState.durationMillis
                                ?: media.viewerVideoDurationMillis(),
                            onTogglePlayback = onTogglePlayback,
                            onSeekPlayback = onSeekPlayback,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = spacing.lg, vertical = spacing.lg),
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .width(canvasWidth)
                        .height(canvasHeight)
                        .background(YingShiThemeTokens.colors.viewerBackground)
                        .then(zoomTransformModifier),
                ) {
                    AppContentMediaThumbnail(
                        mediaSource = media.mediaSource,
                        mediaType = media.mediaType,
                        palette = media.palette,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = realTrashViewerMediaContentDescription(media.mediaType),
                        contentScale = ContentScale.Fit,
                        requestSize = 1080,
                        showLoadingIndicator = true,
                        showStatusBadge = true,
                        showVideoPlayOverlay = false,
                        originalLoadState = originalLoadState,
                        onOriginalLoadStateChange = onOriginalLoadStateChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun RealTrashViewerTopBar(
    actorIdentity: CollaboratorIdentityUiModel?,
    postTitle: String?,
    isMutating: Boolean,
    canRestore: Boolean,
    canRemove: Boolean,
    onBack: () -> Unit,
    onRestore: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Row(
            modifier = Modifier.align(Alignment.TopStart),
            horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RealTrashViewerIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
            )
            postTitle?.takeIf { it.isNotBlank() }?.let { title ->
                RealTrashViewerMetaCapsule(
                    text = title,
                    emphasized = true,
                    modifier = Modifier.widthIn(max = 180.dp),
                )
            }
        }
        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actorIdentity?.let {
                CollaboratorMarkerBadge(
                    identity = it,
                    size = 44.dp,
                )
            }
            if (canRestore) {
                RealTrashViewerIconButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "恢复",
                    enabled = !isMutating,
                    onClick = onRestore,
                )
            }
            if (canRemove) {
                RealTrashViewerIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "删除",
                    destructive = true,
                    enabled = !isMutating,
                    onClick = onRemove,
                )
            }
        }
    }
}

@Composable
private fun RealTrashViewerEdgeActions(
    commentCountLabel: String,
    timeLabel: String,
    originalActionLabel: String,
    originalActionEnabled: Boolean,
    originalActionEmphasized: Boolean,
    previewExpanded: Boolean,
    onToggleComments: () -> Unit,
    onOpenOriginal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(YingShiThemeTokens.radius.capsule))
                .clickable(onClick = onToggleComments),
            horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = colors.viewerSurface.copy(alpha = if (previewExpanded) 0.82f else 0.74f),
                border = BorderStroke(1.dp, colors.viewerAccent.copy(alpha = 0.14f)),
            ) {
                Text(
                    text = "评",
                    modifier = Modifier.padding(horizontal = YingShiThemeTokens.spacing.sm, vertical = YingShiThemeTokens.spacing.sm),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.viewerText.copy(alpha = 0.94f),
                )
            }
            if (commentCountLabel != "0") {
                RealTrashViewerCapsule(
                    text = commentCountLabel,
                    emphasized = true,
                    surfaceAlpha = if (previewExpanded) 0.18f else 0.14f,
                )
            }
        }
        RealTrashViewerCapsule(
            text = timeLabel,
            emphasized = false,
            surfaceAlpha = 0.10f,
            contentAlpha = 0.86f,
        )
        RealTrashViewerCapsule(
            text = originalActionLabel,
            emphasized = originalActionEmphasized,
            enabled = originalActionEnabled,
            onClick = onOpenOriginal,
        )
    }
}

@Composable
private fun RealTrashViewerIconButton(
    icon: ImageVector,
    contentDescription: String,
    destructive: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = if (destructive) {
            MaterialTheme.colorScheme.error.copy(alpha = if (enabled) 0.88f else 0.42f)
        } else {
            colors.viewerSurface.copy(alpha = if (enabled) 0.82f else 0.44f)
        },
        border = BorderStroke(
            1.dp,
            if (destructive) {
                MaterialTheme.colorScheme.error.copy(alpha = if (enabled) 0.24f else 0.10f)
            } else {
                colors.viewerAccent.copy(alpha = if (enabled) 0.16f else 0.08f)
            },
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colors.viewerText.copy(alpha = if (enabled) 0.94f else 0.58f),
                modifier = Modifier.size(23.dp),
            )
        }
    }
}

@Composable
private fun RealTrashViewerCapsule(
    text: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    surfaceAlpha: Float = if (emphasized) 0.14f else 0.10f,
    contentAlpha: Float = 0.94f,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    Surface(
        modifier = modifier
            .clip(shape)
            .then(
                if (onClick != null && enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
        shape = shape,
        color = when {
            destructive -> MaterialTheme.colorScheme.error.copy(alpha = if (enabled) 0.88f else 0.42f)
            else -> colors.viewerSurface.copy(alpha = if (enabled) surfaceAlpha + 0.68f else 0.44f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = when {
                destructive -> MaterialTheme.colorScheme.error.copy(alpha = if (enabled) 0.24f else 0.10f)
                else -> colors.viewerAccent.copy(alpha = if (enabled) surfaceAlpha + 0.06f else 0.08f)
            },
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = YingShiThemeTokens.spacing.sm, vertical = YingShiThemeTokens.spacing.xs),
            style = if (emphasized) {
                MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            } else {
                MaterialTheme.typography.labelLarge
            },
            color = colors.viewerText.copy(alpha = if (enabled) contentAlpha else 0.58f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RealTrashViewerOverlayButton(
    text: String,
    destructive: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(YingShiThemeTokens.radius.capsule))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (destructive) {
            MaterialTheme.colorScheme.error.copy(alpha = if (enabled) 0.88f else 0.42f)
        } else {
            colors.viewerSurface.copy(alpha = if (enabled) 0.82f else 0.44f)
        },
        border = BorderStroke(
            1.dp,
            if (destructive) {
                MaterialTheme.colorScheme.error.copy(alpha = if (enabled) 0.24f else 0.10f)
            } else {
                colors.viewerAccent.copy(alpha = if (enabled) 0.16f else 0.08f)
            },
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.viewerText.copy(alpha = if (enabled) 0.94f else 0.58f),
        )
    }
}

@Composable
private fun RealTrashViewerCommentPreview(
    comments: List<CommentUiModel>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier.fillMaxWidth(0.62f),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = colors.viewerSurface.copy(alpha = 0.74f),
        border = BorderStroke(1.dp, colors.viewerAccent.copy(alpha = 0.14f)),
    ) {
        Column(
            modifier = Modifier.padding(YingShiThemeTokens.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xxs),
        ) {
            Text(
                text = "媒体评论",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.viewerText.copy(alpha = 0.92f),
            )
            when {
                isLoading -> Text(
                    text = "加载中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.viewerTextSecondary.copy(alpha = 0.92f),
                )
                errorMessage != null -> Text(
                    text = errorMessage.ifBlank { "评论加载失败，点击重试" },
                    modifier = Modifier.clickable(onClick = onRetry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.94f),
                )
                comments.isEmpty() -> Text(
                    text = "还没有评论",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.viewerTextSecondary.copy(alpha = 0.92f),
                )
                else -> comments.take(2).forEach { comment ->
                    Text(
                        text = "${comment.author}: ${comment.content}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.viewerTextSecondary.copy(alpha = 0.92f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RealTrashPostViewerDetailContent(
    detail: RemoteTrashDetail,
    directory: CollaboratorDirectorySnapshot,
    statusMessage: String?,
    errorMessage: String?,
    isMutating: Boolean,
    onBack: () -> Unit,
    onRestore: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = detail.item
    val entry = item.toTrashEntryUiModel()
    val actorIdentity = remember(entry, directory) {
        resolveTrashActorIdentity(entry, directory)
    }
    var showPermanentDeleteConfirm by remember(item.trashItemId) {
        mutableStateOf(false)
    }
    var showRestoreConfirm by remember(item.trashItemId) {
        mutableStateOf(false)
    }
    var selectedMediaId by remember(item.trashItemId) {
        mutableStateOf<String?>(null)
    }
    val mediaIds = entry.previewMediaIds()
    val postDetail = remember(entry, mediaIds) {
        entry.toTrashPostDetailUiModel(mediaIds)
    }
    val mediaPagerState = rememberPagerState(
        pageCount = { postDetail.mediaItems.size.coerceAtLeast(1) },
    )
    val currentMediaPage = mediaPagerState.currentPage.coerceIn(
        0,
        (postDetail.mediaItems.size - 1).coerceAtLeast(0),
    )
    val currentMedia = postDetail.mediaItems[currentMediaPage]
    val originalTargets = remember(postDetail.mediaItems) {
        postDetail.mediaItems.map {
            RealOriginalMediaTarget(
                mediaId = it.id,
                mediaType = it.mediaType,
                mediaSource = it.mediaSource,
            )
        }
    }
    val originalSummary = RealOriginalLoadRepository.getPostSummaryForTargets(originalTargets)
    val postCommentsState by produceState<List<CommentUiModel>>(emptyList(), entry.sourcePostId) {
        val postId = entry.sourcePostId?.takeIf { it.isNotBlank() } ?: return@produceState
        value = when (val result = CommentGateway.repository.getPostComments(postId)) {
            is ApiResult.Success -> result.data.comments
                .map { it.toCommentUiModel() }
                .sortedByDescending { it.createdAtMillis }
            else -> emptyList()
        }
    }

    BackHandler(enabled = selectedMediaId != null) {
        selectedMediaId = null
    }

    if (selectedMediaId != null) {
        RealTrashPostMediaViewerOverlay(
            entry = entry,
            actorIdentity = actorIdentity,
            mediaId = selectedMediaId,
            isMutating = isMutating,
            onBack = { selectedMediaId = null },
            onRestorePost = { showRestoreConfirm = true },
            onRequestDeletePost = { showPermanentDeleteConfirm = true },
        )
    } else {
        PostDetailBodyLayout(
            modifier = modifier
                .fillMaxSize(),
            topBar = {
                RealTrashPostDetailTopBar(
                    detail = detail,
                    actorIdentity = actorIdentity,
                    isMutating = isMutating,
                    onBack = onBack,
                    onRestore = { showRestoreConfirm = true },
                    onRemove = { showPermanentDeleteConfirm = true },
                )
            },
            mediaArea = {
                if (postDetail.mediaItems.isEmpty()) {
                    RealTrashSectionCard(
                        title = "小相册媒体不可用",
                        body = "这个小相册的媒体已经无法预览。",
                    )
                } else {
                    PostMediaArea(
                        detail = postDetail,
                        currentPage = currentMediaPage,
                        modifier = Modifier.fillMaxWidth(),
                        onOpenMedia = { selectedMediaId = currentMedia.id },
                    ) {
                        HorizontalPager(
                            state = mediaPagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(372.dp),
                            beyondViewportPageCount = 1,
                            key = { page -> postDetail.mediaItems[page].id },
                        ) { page ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                PostMediaCard(
                                    media = postDetail.mediaItems[page],
                                    originalLoadState = RealOriginalLoadRepository.getState(originalTargets[page]),
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { selectedMediaId = postDetail.mediaItems[page].id },
                                )
                            }
                        }
                    }
                }
            },
            mediaInfo = {
                if (postDetail.mediaItems.isNotEmpty()) {
                    val target = originalTargets[currentMediaPage]
                    PostMediaInfoRow(
                        media = currentMedia,
                        commentCount = CommentGateway.mediaCommentCount(currentMedia.id),
                        originalLoadState = RealOriginalLoadRepository.getState(target),
                        showOriginalAction = currentMedia.mediaType == AppMediaType.IMAGE,
                        onCommentClick = { selectedMediaId = currentMedia.id },
                        onOriginalClick = { RealOriginalLoadRepository.setState(target, OriginalLoadState.Loaded) },
                    )
                }
            },
            postInfo = {
                Column(verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm)) {
                    statusMessage?.let { RealTrashSectionCard(title = "操作结果", body = it, emphasized = true) }
                    errorMessage?.let { RealTrashSectionCard(title = "操作失败", body = it) }
                    PostInfoSection(
                        detail = postDetail,
                        originalSummary = originalSummary,
                        onOpenComments = {},
                        onLoadAllOriginals = {
                            originalTargets.forEach { RealOriginalLoadRepository.setState(it, OriginalLoadState.Loaded) }
                        },
                    )
                }
            },
            comments = {
                RealTrashReadOnlyCommentCard(
                    title = "小相册评论",
                    emptyText = "当前小相册没有可展示的评论。",
                    comments = postCommentsState,
                )
            },
        )
    }

    if (showPermanentDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showPermanentDeleteConfirm = false },
            title = { Text("永久删除该回收站项目？") },
            text = {
                Text("确认后会删除回收站记录。属于媒体删除的项目会同时删除对应的原文件和预览文件，删除后无法恢复。")
            },
            containerColor = YingShiThemeTokens.colors.raisedSurface,
            titleContentColor = YingShiThemeTokens.colors.titleAccent,
            textContentColor = YingShiThemeTokens.colors.textSecondary,
            confirmButton = {
                TrashDialogActionButton(
                    text = "永久删除",
                    danger = true,
                    enabled = !isMutating,
                    onClick = {
                        showPermanentDeleteConfirm = false
                        onRemove()
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { showPermanentDeleteConfirm = false })
            },
        )
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("确认恢复？") },
            text = { Text("将恢复当前回收站小相册。") },
            containerColor = YingShiThemeTokens.colors.raisedSurface,
            titleContentColor = YingShiThemeTokens.colors.titleAccent,
            textContentColor = YingShiThemeTokens.colors.textSecondary,
            confirmButton = {
                TrashDialogActionButton(
                    text = "恢复",
                    emphasized = true,
                    enabled = !isMutating,
                    onClick = {
                        showRestoreConfirm = false
                        onRestore()
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { showRestoreConfirm = false })
            },
        )
    }
}

@Composable
private fun RealTrashPostMediaGridTile(
    mediaId: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = mediaId.isNotBlank(), onClick = onClick),
    ) {
        if (mediaId.isBlank()) {
            RealTrashDeletedMediaPlaceholder(modifier = Modifier.matchParentSize())
        } else {
            AppContentMediaThumbnail(
                mediaSource = realTrashMediaSource(mediaId),
                mediaType = AppMediaType.IMAGE,
                palette = realPaletteFor(mediaId),
                modifier = Modifier.matchParentSize(),
                requestSize = 384,
                showLoadingIndicator = true,
                showStatusBadge = true,
            )
        }
    }
}

@Composable
private fun RealTrashReadOnlyCommentCard(
    title: String,
    emptyText: String,
    comments: List<CommentUiModel>,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
    ) {
        Column(
            modifier = Modifier.padding(YingShiThemeTokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            if (comments.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            } else {
                comments.take(10).forEach { comment ->
                    Column(verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xxs)) {
                        Text(
                            text = "${comment.author} · ${realFormatTrashEntryTime(comment.createdAtMillis)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.titleAccent,
                        )
                        Text(
                            text = comment.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RealTrashPostMediaViewerOverlay(
    entry: TrashEntryUiModel,
    actorIdentity: CollaboratorIdentityUiModel?,
    mediaId: String?,
    isMutating: Boolean,
    onBack: () -> Unit,
    onRestorePost: () -> Unit,
    onRequestDeletePost: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val accessToken = AuthSessionManager.peekAccessToken()
    val target = mediaId?.takeIf { it.isNotBlank() }?.let {
        RealOriginalMediaTarget(
            mediaId = it,
            mediaType = AppMediaType.IMAGE,
            mediaSource = realTrashMediaSource(it),
        )
    }
    val originalLoadState = target?.let(RealOriginalLoadRepository::getState)
        ?: OriginalLoadState.NotLoaded
    val commentBindings = mediaId?.takeIf { it.isNotBlank() }?.let { rememberViewerCommentBindings(it) }
    var isImmersive by remember { mutableStateOf(false) }
    var showCommentPreview by remember(mediaId) {
        mutableStateOf(false)
    }

    BackHandler(enabled = !isImmersive && showCommentPreview) {
        showCommentPreview = false
    }
    BackHandler(enabled = isImmersive) {
        onBack()
    }
    ViewerStatusBarEffect(immersive = isImmersive)

    fun toggleImmersive() {
        val nextImmersive = !isImmersive
        applyViewerStatusBarVisibility(view, nextImmersive)
        isImmersive = nextImmersive
        if (nextImmersive) {
            showCommentPreview = false
        }
    }

    val topPadding by animateDpAsState(
        targetValue = if (isImmersive) 0.dp else 68.dp,
        label = "trashPostMediaViewerTopPadding",
    )
    val bottomPadding by animateDpAsState(
        targetValue = if (isImmersive) 0.dp else 104.dp,
        label = "trashPostMediaViewerBottomPadding",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(YingShiThemeTokens.colors.viewerBackground)
            .viewerSingleTapGesture { _, _ -> toggleImmersive() },
    ) {
        if (target == null) {
            RealTrashDeletedMediaPlaceholder(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f),
            )
        } else {
            AppContentMediaThumbnail(
                mediaSource = target.mediaSource,
                mediaType = target.mediaType,
                palette = realPaletteFor(target.mediaId),
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .padding(top = topPadding, bottom = bottomPadding),
                contentScale = ContentScale.Fit,
                requestSize = 1080,
                showLoadingIndicator = true,
                showStatusBadge = true,
                originalLoadState = originalLoadState,
                onOriginalLoadStateChange = { state ->
                    RealOriginalLoadRepository.setState(target, state)
                },
            )
        }

        if (!isImmersive) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(
                        horizontal = YingShiThemeTokens.spacing.lg,
                        vertical = YingShiThemeTokens.spacing.md,
                    ),
                horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RealTrashViewerIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                )
                Box(modifier = Modifier.weight(1f))
                actorIdentity?.let {
                    CollaboratorMarkerBadge(
                        identity = it,
                        size = 44.dp,
                    )
                }
                RealTrashViewerIconButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "恢复",
                    enabled = !isMutating,
                    onClick = onRestorePost,
                )
                RealTrashViewerIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "删除",
                    destructive = true,
                    enabled = !isMutating,
                    onClick = onRequestDeletePost,
                )
            }
        }

        if (!isImmersive) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(YingShiThemeTokens.spacing.lg),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
            ) {
                RealTrashViewerMetaCapsule(text = entry.title)
            }
        }

        if (!isImmersive) {
            RealTrashViewerEdgeActions(
                commentCountLabel = commentBindings?.comments?.size?.toString() ?: "0",
                timeLabel = realFormatTrashEntryTime(entry.deletedAtMillis),
                originalActionLabel = if (target != null) {
                    originalLoadState.actionLabel()
                } else {
                    "原媒体不可用"
                },
                originalActionEnabled = target != null && originalLoadState != OriginalLoadState.Loading,
                originalActionEmphasized = target != null && originalLoadState == OriginalLoadState.Loaded,
                previewExpanded = showCommentPreview,
                onToggleComments = { showCommentPreview = !showCommentPreview },
                onOpenOriginal = {
                    if (target != null &&
                        originalLoadState != OriginalLoadState.Loaded &&
                        originalLoadState != OriginalLoadState.Loading
                    ) {
                        RealOriginalLoadRepository.requestOriginal(context, target, accessToken)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        start = YingShiThemeTokens.spacing.lg,
                        end = YingShiThemeTokens.spacing.lg,
                        bottom = 0.dp,
                    ),
            )
        }

        if (showCommentPreview && !isImmersive) {
            RealTrashViewerCommentPreview(
                comments = commentBindings?.comments.orEmpty(),
                isLoading = commentBindings?.isLoading == true,
                errorMessage = commentBindings?.errorMessage,
                onRetry = { commentBindings?.onRetry?.invoke() },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(
                        start = YingShiThemeTokens.spacing.lg,
                        bottom = 64.dp,
                    ),
            )
        }
    }
}

@Composable
private fun RealTrashDeletedMediaPlaceholder(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(YingShiThemeTokens.radius.lg))
            .background(YingShiThemeTokens.colors.viewerSurface.copy(alpha = 0.62f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "已删除",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = YingShiThemeTokens.colors.viewerTextSecondary,
        )
    }
}

@Composable
private fun RealTrashDetailContent(
    detail: RemoteTrashDetail,
    actorIdentity: CollaboratorIdentityUiModel?,
    statusMessage: String?,
    errorMessage: String?,
    isMutating: Boolean,
    onRestore: () -> Unit,
    onRemove: () -> Unit,
    onUndoRemove: () -> Unit,
) {
    val item = detail.item
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val entryType = item.toTrashEntryUiModel().type
    val relatedPostCount = buildSet {
        item.sourcePostId?.takeIf { it.isNotBlank() }?.let(::add)
        addAll(item.relatedPostIds.filter { it.isNotBlank() })
    }.size
    val relatedMediaCount = buildSet {
        item.sourceMediaId?.takeIf { it.isNotBlank() }?.let(::add)
        item.commentTargetMediaId?.takeIf { it.isNotBlank() }?.let(::add)
        addAll(item.relatedMediaIds.filter { it.isNotBlank() })
    }.size
    var showPermanentDeleteConfirm by remember(item.trashItemId) {
        mutableStateOf(false)
    }

    if (statusMessage != null) {
        RealTrashSectionCard(
            title = "操作结果",
            body = statusMessage,
            emphasized = true,
        )
    }
    if (errorMessage != null) {
        RealTrashSectionCard(
            title = "操作失败",
            body = errorMessage,
        )
    }

    RealTrashSectionCard(
        title = item.title.ifBlank { "回收站项目" },
        body = item.previewInfo.ifBlank { EmptyTrashPreviewMessage },
    )

    RealTrashDeletedPreview(item = item)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            actorIdentity?.let {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CollaboratorMarkerBadge(
                        identity = it,
                        size = 18.dp,
                    )
                    Text(
                        text = if (it.isCurrentUser) "由我移入回收站" else "由对方移入回收站",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
            Text(
                text = "类型：${entryType.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            Text(
                text = "状态：${item.state.toTrashStateLabel()}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            if (relatedPostCount > 0) {
                Text(
                    text = "关联小相册：$relatedPostCount 个",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            if (relatedMediaCount > 0) {
                Text(
                    text = "关联媒体：$relatedMediaCount 项",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                if (detail.canRestore) {
                    RealTrashIconActionButton(
                        text = if (isMutating) "处理中" else "恢复",
                        emphasized = true,
                        enabled = !isMutating,
                        onClick = onRestore,
                    )
                }
                if (detail.canMoveOutOfTrash) {
                    RealTrashIconActionButton(
                        text = if (isMutating) "处理中" else "永久删除",
                        danger = true,
                        enabled = !isMutating,
                        onClick = { showPermanentDeleteConfirm = true },
                    )
                }
                if (detail.pendingCleanup != null) {
                    // Pending cleanup is no longer exposed in the client UI.
                }
            }
        }
    }

    if (showPermanentDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showPermanentDeleteConfirm = false },
            title = { Text("永久删除该回收站项目？") },
            text = {
                Text(
                    "确认后会删除回收站记录。属于媒体删除的项目会同时删除对应的原文件和预览文件，删除后无法恢复。",
                )
            },
            containerColor = YingShiThemeTokens.colors.raisedSurface,
            titleContentColor = YingShiThemeTokens.colors.titleAccent,
            textContentColor = YingShiThemeTokens.colors.textSecondary,
            confirmButton = {
                TrashDialogActionButton(
                    text = "永久删除",
                    danger = true,
                    enabled = !isMutating,
                    onClick = {
                        showPermanentDeleteConfirm = false
                        onRemove()
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { showPermanentDeleteConfirm = false })
            },
        )
    }
}

@Composable
private fun RealTrashDeletedPreview(
    item: com.example.yingshi.data.model.RemoteTrashItem,
) {
    val mediaIds = buildList {
        item.sourceMediaId?.takeIf { it.isNotBlank() }?.let(::add)
        addAll(item.relatedMediaIds.filter { it.isNotBlank() })
    }.distinct()
    val type = item.toTrashEntryUiModel().type

    when {
        mediaIds.isNotEmpty() -> {
            RealTrashMediaStrip(
                title = when (type) {
                    TrashEntryType.SMALL_ALBUM_DELETED -> "原小相册媒体"
                    TrashEntryType.MEDIA_REMOVED -> "被移除的媒体"
                    TrashEntryType.MEDIA_SYSTEM_DELETED -> "被删除的媒体"
                },
                mediaIds = mediaIds,
            )
        }

        type == TrashEntryType.SMALL_ALBUM_DELETED -> {
            RealTrashSectionCard(
                title = "原小相册内容",
                body = "这个小相册的照片内容已不可查看，仅保留标题和说明。",
            )
        }

        else -> {
            RealTrashSectionCard(
                title = "原媒体预览不可用",
                body = MissingOriginalMediaMessage,
            )
        }
    }
}

@Composable
private fun RealTrashMediaStrip(
    title: String,
    mediaIds: List<String>,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            mediaIds.chunked(3).forEach { rowIds ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    rowIds.forEach { mediaId ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                        ) {
                            AppContentMediaThumbnail(
                                mediaSource = realTrashMediaSource(mediaId),
                                mediaType = AppMediaType.IMAGE,
                                palette = realPaletteFor(mediaId),
                                modifier = Modifier.fillMaxSize(),
                                contentDescription = realTrashViewerMediaContentDescription(AppMediaType.IMAGE),
                                requestSize = 384,
                                showLoadingIndicator = true,
                                showStatusBadge = true,
                            )
                        }
                    }
                    repeat(3 - rowIds.size) {
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

internal fun realTrashMediaSource(
    mediaId: String,
    mediaType: AppMediaType = AppMediaType.IMAGE,
    width: Int? = null,
    height: Int? = null,
    durationMillis: Long? = null,
    mimeType: String? = null,
): AppContentMediaSource {
    val baseUrl = BackendDebugConfig.currentBaseUrl().trimEnd('/')
    val previewUrl = "$baseUrl/api/media/files/$mediaId?variant=preview"
    val originalUrl = "$baseUrl/api/media/files/$mediaId"
    val coverUrl = "$baseUrl/api/media/files/$mediaId?variant=cover"
    return AppContentMediaSource(
        thumbnailUrl = previewUrl,
        mediaUrl = originalUrl,
        originalUrl = originalUrl,
        videoUrl = if (mediaType == AppMediaType.VIDEO) originalUrl else null,
        coverUrl = if (mediaType == AppMediaType.VIDEO) coverUrl else previewUrl,
        mimeType = mimeType,
        width = width,
        height = height,
        durationMillis = durationMillis,
    )
}

private fun TrashEntryType.isRealMediaTrashType(): Boolean {
    return this == TrashEntryType.MEDIA_SYSTEM_DELETED || this == TrashEntryType.MEDIA_REMOVED
}

private data class RealTrashMonthGroup(
    val key: String,
    val title: String,
    val entries: List<TrashEntryUiModel>,
)

private fun realTrashMonthGroups(entries: List<TrashEntryUiModel>): List<RealTrashMonthGroup> {
    val formatter = SimpleDateFormat("yyyy年M月", Locale.CHINA)
    val keyFormatter = SimpleDateFormat("yyyy-MM", Locale.CHINA)
    return entries
        .sortedByDescending { it.deletedAtMillis }
        .groupBy { keyFormatter.format(Date(it.deletedAtMillis)) }
        .map { (key, groupEntries) ->
            RealTrashMonthGroup(
                key = key,
                title = formatter.format(Date(groupEntries.first().deletedAtMillis)),
                entries = groupEntries,
            )
        }
}

private fun realTrashDaysSince(timeMillis: Long): Long {
    val now = System.currentTimeMillis()
    if (timeMillis <= 0L || now <= timeMillis) return 0L
    return TimeUnit.MILLISECONDS.toDays(now - timeMillis)
}

private fun realFormatTrashEntryTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}

private fun realTrashGridPostTitle(entry: TrashEntryUiModel): String {
    return entry.mediaSnapshot?.sourcePostTitle
        ?: entry.title
        ?: entry.sourcePostId
        ?: "来源小相册"
}

private fun TrashMediaSnapshot.toViewerPhotoFeedItem(): PhotoFeedItem {
    val date = java.util.Calendar.getInstance(Locale.CHINA).apply {
        timeInMillis = displayTimeMillis
    }
    return PhotoFeedItem(
        mediaId = mediaId,
        mediaDisplayTimeMillis = displayTimeMillis,
        displayYear = date.get(java.util.Calendar.YEAR),
        displayMonth = date.get(java.util.Calendar.MONTH) + 1,
        displayDay = date.get(java.util.Calendar.DAY_OF_MONTH),
        commentCount = 0,
        smallAlbumIds = sourcePostId?.let(::listOf).orEmpty(),
        palette = palette,
        mediaType = mediaType,
        aspectRatio = aspectRatio,
        width = width,
        height = height,
        videoDurationMillis = videoDurationMillis,
        displayTimeSource = DisplayTimeSourceImported,
        mediaSource = mediaSource,
    )
}

private fun TrashMediaSnapshot.viewerVideoDurationMillis(): Long {
    return videoDurationMillis ?: 18_000L
}

private fun String?.toTrashStateLabel(): String {
    return when (this?.trim()?.lowercase(Locale.ROOT)) {
        null, "", "intrash", "in_trash" -> "在回收站"
        "pendingcleanup", "pending_cleanup" -> "待清理"
        "restored" -> "已恢复"
        "purged", "deleted" -> "已删除"
        else -> "在回收站"
    }
}

@Composable
private fun RealTrashSectionCard(
    title: String,
    body: String,
    emphasized: Boolean = false,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = if (emphasized) {
            colors.primaryContainer.copy(alpha = 0.62f)
        } else {
            colors.raisedSurface.copy(alpha = 0.94f)
        },
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
    ) {
        Column(
            modifier = Modifier.padding(YingShiThemeTokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}
