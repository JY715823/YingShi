package com.example.yingshi.feature.photos

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshi.data.model.RemoteTrashDetail
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun RealTrashPageScreen(
    modifier: Modifier = Modifier,
    selectedTypeName: String = TrashEntryType.MEDIA_SYSTEM_DELETED.name,
    onSelectedTypeNameChange: (String) -> Unit = { },
    showPendingCleanup: Boolean = false,
    onShowPendingCleanupChange: (Boolean) -> Unit = { },
    onOpenTrashDetail: (TrashDetailRoute) -> Unit = { },
    onRestoreTargetMediaIds: (List<String>) -> Unit = { },
    selectionExitNonce: Int = 0,
    onSelectionModeChange: (Boolean) -> Unit = { },
) {
    val sessionKey = realBackendSessionKey("real-trash-list")
    val viewModel: RealTrashListViewModel = viewModel(
        key = sessionKey,
        factory = RealTrashListViewModel.factory(),
    )
    val uiState by viewModel.uiState.collectAsState()
    val backendMutationEvent by RealBackendMutationBus.latestEvent.collectAsState()
    val selectedType = TrashEntryType.valueOf(selectedTypeName)
    val spacing = YingShiThemeTokens.spacing
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var pendingRestoreEntries by remember { mutableStateOf(emptyList<TrashEntryUiModel>()) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedEntryIds by remember { mutableStateOf(emptySet<String>()) }
    val selectedEntries = uiState.entries.filter { it.id in selectedEntryIds }

    fun toggleSelection(entry: TrashEntryUiModel) {
        selectionMode = true
        selectedEntryIds = if (entry.id in selectedEntryIds) {
            selectedEntryIds - entry.id
        } else {
            selectedEntryIds + entry.id
        }
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
            pendingRestoreEntries = uiState.entries
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
        selectedEntryIds = emptySet()
        selectionMode = false
    }

    LaunchedEffect(selectionExitNonce) {
        if (selectionExitNonce > 0) {
            selectedEntryIds = emptySet()
            selectionMode = false
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

    if (selectedType.isRealMediaTrashType()) {
        val mediaEntries = uiState.entries.sortedByDescending { it.deletedAtMillis }
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
                onSelectionChange = { selectedEntryIds = it },
                onAutoScroll = { delta -> mediaGridState.scrollBy(delta) },
            ),
            state = mediaGridState,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                RealTrashCategoryActionRow(
                    selectedType = selectedType,
                    entryCount = uiState.entries.size,
                    menuExpanded = showCategoryMenu,
                    isMutating = uiState.isMutating,
                    onMenuExpandedChange = { showCategoryMenu = it },
                    onTypeSelected = { onSelectedTypeNameChange(it.name) },
                    selectionMode = selectionMode,
                    selectedCount = selectedEntries.size,
                    onCancelSelection = {
                        selectionMode = false
                        selectedEntryIds = emptySet()
                    },
                    onRestoreCurrent = { restoreFromTopBar() },
                    onRequestClearCurrent = { deleteFromTopBar() },
                )
            }
            uiState.statusMessage?.let { message ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RealTrashSectionCard(title = "操作结果", body = message, emphasized = true)
                }
            }
            uiState.errorMessage?.let { message ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RealTrashSectionCard(title = "请求失败", body = message)
                }
            }
            when {
                uiState.isLoading && uiState.entries.isEmpty() -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        RealTrashSectionCard(title = "读取中", body = "正在从后端读取回收站列表…")
                    }
                }
                uiState.entries.isEmpty() -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        RealTrashSectionCard(title = "当前分类为空", body = "这一类回收站项目还没有内容。")
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
                                    modifier = Modifier.weight(1f),
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
    } else if (selectedType == TrashEntryType.POST_DELETED) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                RealTrashCategoryActionRow(
                    selectedType = selectedType,
                    entryCount = uiState.entries.size,
                    menuExpanded = showCategoryMenu,
                    isMutating = uiState.isMutating,
                    onMenuExpandedChange = { showCategoryMenu = it },
                    onTypeSelected = { onSelectedTypeNameChange(it.name) },
                    selectionMode = selectionMode,
                    selectedCount = selectedEntries.size,
                    onCancelSelection = {
                        selectionMode = false
                        selectedEntryIds = emptySet()
                    },
                    onRestoreCurrent = { restoreFromTopBar() },
                    onRequestClearCurrent = { deleteFromTopBar() },
                )
            }
            uiState.statusMessage?.let { message ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RealTrashSectionCard(title = "操作结果", body = message, emphasized = true)
                }
            }
            uiState.errorMessage?.let { message ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RealTrashSectionCard(title = "请求失败", body = message)
                }
            }
            when {
                uiState.isLoading && uiState.entries.isEmpty() -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        RealTrashSectionCard(title = "读取中", body = "正在从后端读取回收站列表…")
                    }
                }
                uiState.entries.isEmpty() -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        RealTrashSectionCard(title = "当前分类为空", body = "这一类回收站项目还没有内容。")
                    }
                }
                else -> {
                    gridItems(
                        items = uiState.entries.sortedByDescending { it.deletedAtMillis },
                        key = { it.id },
                    ) { entry ->
                        RealTrashPostGridCard(
                            entry = entry,
                            selected = entry.id in selectedEntryIds,
                            selectionMode = selectionMode,
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
                    entryCount = uiState.entries.size,
                    menuExpanded = showCategoryMenu,
                    isMutating = uiState.isMutating,
                    onMenuExpandedChange = { showCategoryMenu = it },
                    onTypeSelected = { onSelectedTypeNameChange(it.name) },
                    selectionMode = selectionMode,
                    selectedCount = selectedEntries.size,
                    onCancelSelection = {
                        selectionMode = false
                        selectedEntryIds = emptySet()
                    },
                    onRestoreCurrent = { restoreFromTopBar() },
                    onRequestClearCurrent = { deleteFromTopBar() },
                )
            }

            if (uiState.statusMessage != null) {
                item {
                    RealTrashSectionCard(
                        title = "操作结果",
                        body = uiState.statusMessage ?: "",
                        emphasized = true,
                    )
                }
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
                            body = "正在从后端读取回收站列表…",
                        )
                    }
                }
                uiState.entries.isEmpty() -> {
                    item {
                        RealTrashSectionCard(
                            title = "当前分类为空",
                            body = "这一类回收站项目还没有内容，可以先在 REAL 照片流里删除一项媒体试试。",
                        )
                    }
                }
                else -> {
                    items(
                        items = uiState.entries,
                        key = { it.id },
                    ) { entry ->
                        RealTrashEntryRow(
                            entry = entry,
                            selected = entry.id in selectedEntryIds,
                            selectionMode = selectionMode,
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
                                        color = MaterialTheme.colorScheme.primary,
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
                    "将永久删除当前「${selectedType.label}」分类中的 ${uiState.entries.size} 项。媒体删除类会删除对应 Server local-storage 文件；帖子删除、媒体移除不会误删仍被其他地方引用的媒体文件。",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = uiState.entries.isNotEmpty() && !uiState.isMutating,
                    onClick = {
                        showClearConfirm = false
                        viewModel.purgeEntries(
                            entries = uiState.entries,
                            selectedType = selectedType,
                        )
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

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirm = false
                pendingRestoreEntries = emptyList()
            },
            title = { Text("确认恢复？") },
            text = {
                Text("将恢复 ${pendingRestoreEntries.size} 个回收站条目。恢复后会回到对应照片流或帖子关系。")
            },
            confirmButton = {
                TextButton(
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
                        selectedEntryIds = emptySet()
                        selectionMode = false
                    },
                ) {
                    Text("恢复")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirm = false
                        pendingRestoreEntries = emptyList()
                    },
                ) {
                    Text("取消")
                }
            },
        )
    }

    if (showDeleteSelectedConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedConfirm = false },
            title = { Text("删除选中项？") },
            text = {
                Text(
                    "将永久删除当前选中的 ${selectedEntries.size} 项。媒体删除类会删除对应 Server local-storage 文件；帖子删除、媒体移除不会误删仍被其他地方引用的媒体文件。",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = selectedEntries.isNotEmpty() && !uiState.isMutating,
                    onClick = {
                        showDeleteSelectedConfirm = false
                        viewModel.purgeEntries(
                            entries = selectedEntries,
                            selectedType = selectedType,
                        )
                        selectedEntryIds = emptySet()
                        selectionMode = false
                    },
                ) {
                    Text("删除选中项")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedConfirm = false }) {
                    Text("取消")
                }
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
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val spacing = YingShiThemeTokens.spacing
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
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier.padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RealTrashEntryPreview(
                entry = entry,
                modifier = Modifier
                    .weight(0.26f)
                    .aspectRatio(1f),
            )
            Column(
                modifier = Modifier.weight(0.74f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = entry.type.label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = entry.previewInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = realTrashEntrySourceLine(entry),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                )
            }
            trailing?.invoke()
            if (selectionMode) {
                Text(
                    text = if (selected) "已选" else "选择",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RealTrashCategoryActionRow(
    selectedType: TrashEntryType,
    entryCount: Int,
    menuExpanded: Boolean,
    isMutating: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onTypeSelected: (TrashEntryType) -> Unit,
    selectionMode: Boolean,
    selectedCount: Int,
    onCancelSelection: () -> Unit,
    onRestoreCurrent: () -> Unit,
    onRequestClearCurrent: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
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
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Box {
                RealTrashIconActionButton(
                    text = "☰",
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
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                            } else {
                                                MaterialTheme.colorScheme.surface
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
        }
        Box(modifier = Modifier.weight(1f))
        TextButton(
            enabled = (entryCount > 0 || selectionMode) && !isMutating,
            onClick = onRestoreCurrent,
        ) {
            Text(if (isMutating) "…" else "↩")
        }
        RealTrashIconActionButton(
            text = "🗑",
            enabled = (entryCount > 0 || selectionMode) && !isMutating,
            onClick = onRequestClearCurrent,
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
            color = MaterialTheme.colorScheme.primary,
        )
        RealTrashIconActionButton(
            text = if (isMutating) "…" else "↩",
            enabled = selectedCount > 0 && !isMutating,
            onClick = onRestoreSelected,
        )
        RealTrashIconActionButton(
            text = if (isMutating) "…" else "🗑",
            enabled = selectedCount > 0 && !isMutating,
            onClick = onRequestDeleteSelected,
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
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.capsule),
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
private fun RealTrashEntryPreview(
    entry: TrashEntryUiModel,
    modifier: Modifier = Modifier,
) {
    val media = entry.mediaSnapshot
    val mediaId = media?.mediaId ?: entry.previewMediaIds().firstOrNull()
    if (mediaId == null) {
        Surface(
            modifier = modifier,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.lg),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = entry.type.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 10.dp, start = 2.dp),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun RealTrashMediaGridCell(
    entry: TrashEntryUiModel,
    showPostTitle: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val media = entry.mediaSnapshot
    Column(
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
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
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp, bottom = 6.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = Color.Black.copy(alpha = 0.32f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .padding(9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        VideoGlyph(
                            state = VideoGlyphState.PLAY,
                            tint = Color.White.copy(alpha = 0.94f),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            RealTrashSelectionOverlay(
                selected = selected,
                visible = selectionMode,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
        if (showPostTitle) {
            RealTrashPostTitleChip(text = realTrashGridPostTitle(entry))
        }
    }
}

@Composable
private fun RealTrashPostTitleChip(text: String) {
    Surface(
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun RealTrashPostGridCard(
    entry: TrashEntryUiModel,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val mediaCount = entry.relatedMediaIds.size
    val coverMediaId = entry.previewMediaIds().firstOrNull()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
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
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                    shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                    color = Color.Black.copy(alpha = 0.38f),
                ) {
                    Text(
                        text = "${mediaCount.coerceAtLeast(0)}项媒体",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                }
                RealTrashSelectionOverlay(
                    selected = selected,
                    visible = selectionMode,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
            Text(
                text = entry.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = entry.previewInfo,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = realFormatTrashEntryTime(entry.deletedAtMillis),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
            )
        }
    }
}

@Composable
private fun RealTrashSelectionOverlay(
    selected: Boolean,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Box(
        modifier = modifier.size(46.dp),
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
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (danger) Color(0xFFE5484D).copy(alpha = 0.88f) else Color.Black.copy(alpha = 0.36f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    ) {
        Text(
            text = "${days.coerceAtLeast(0)}天",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
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
        title = title.ifBlank { "回收站帖子" },
        summary = previewInfo.ifBlank { "后端没有返回额外说明。" },
        contributorLabel = "回收站帖子",
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
                mediaSource = realTrashMediaSource(mediaId),
            )
        },
        comments = emptyList(),
    )
}

@Composable
private fun RealTrashPostDetailTopBar(
    detail: RemoteTrashDetail,
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
        RealTrashIconActionButton(text = "<", enabled = !isMutating, onClick = onBack)
        Text(
            text = "帖子详情",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
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
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(YingShiThemeTokens.radius.capsule))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (enabled) 0.94f else 0.52f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeColor = if (enabled) Color(0xFF22323A) else Color(0xFF8A969C)
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
        TrashEntryType.POST_DELETED -> {
            val mediaCount = entry.relatedMediaIds.size
            "帖子删除 · 媒体 $mediaCount 项"
        }
        TrashEntryType.MEDIA_REMOVED -> {
            val source = entry.sourcePostId ?: entry.relatedPostIds.firstOrNull() ?: "当前帖子"
            "从帖子移除 · 来源 $source"
        }
        TrashEntryType.MEDIA_SYSTEM_DELETED -> {
            val postCount = entry.relatedPostIds.size
            "媒体删除 · 影响帖子 $postCount 个"
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

    LaunchedEffect(backendMutationEvent.version) {
        if (backendMutationEvent.version > 0 && backendMutationEvent.affectsTrash()) {
            viewModel.refresh()
        }
    }

    val mediaDetailEntry = detail?.item?.toTrashEntryUiModel()
    if (mediaDetailEntry?.type?.isRealMediaTrashType() == true) {
        val sameTypeEntries by produceState(listOf(mediaDetailEntry), detail.item.itemType, detail.item.trashItemId) {
            value = when (val result = RepositoryProvider.trashRepository.getTrashItems(detail.item.itemType)) {
                is ApiResult.Success -> result.data
                    .map { it.toTrashEntryUiModel() }
                    .filter { it.type == mediaDetailEntry.type && it.mediaSnapshot != null }
                    .ifEmpty { listOf(mediaDetailEntry) }
                else -> listOf(mediaDetailEntry)
            }
        }
        RealTrashMediaViewerDetailPagerContent(
            detail = detail,
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
    if (detail != null && mediaDetailEntry?.type == TrashEntryType.POST_DELETED) {
        RealTrashPostViewerDetailContent(
            detail = detail,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("返回")
            }
            Text(
                text = "回收站详情",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        when {
            uiState.tokenMissing -> {
                RealTrashSectionCard(
                    title = "REAL 模式需要登录",
                    body = uiState.errorMessage ?: "请先到联调诊断页完成登录。",
                )
            }
            uiState.isLoading && detail == null -> {
                RealTrashSectionCard(
                    title = "读取中",
                    body = "正在从后端读取回收站详情…",
                )
            }
            uiState.errorMessage != null && detail == null -> {
                RealTrashSectionCard(
                    title = "读取失败",
                    body = uiState.errorMessage ?: "暂时无法读取回收站详情。",
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
    val accessToken = AuthSessionManager.getAccessToken()
    val initialEntry = detail.item.toTrashEntryUiModel()
    val viewerEntries = remember(entries, initialEntry.id) {
        entries.filter { it.mediaSnapshot != null }.ifEmpty { listOf(initialEntry) }
    }
    val initialPage = viewerEntries.indexOfFirst { it.id == initialEntry.id }
        .takeIf { it >= 0 } ?: 0
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { viewerEntries.size },
    )
    val currentEntry = viewerEntries[pagerState.currentPage.coerceIn(0, viewerEntries.lastIndex)]
    val currentMedia = currentEntry.mediaSnapshot
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

    LaunchedEffect(currentEntry.id) {
        showCommentPreview = false
        videoPlaybackState = ViewerVideoPlaybackState(
            mediaId = currentMedia?.mediaId?.takeIf { currentMedia.mediaType == AppMediaType.VIDEO },
        )
        videoControlsVisible = true
        videoControlsActivityNonce += 1
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
            .background(Color(0xFF050608))
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
            userScrollEnabled = viewerEntries.size > 1,
            key = { page -> viewerEntries[page].id },
        ) { page ->
            val pageMedia = viewerEntries[page].mediaSnapshot
            if (pageMedia == null) {
                RealTrashSectionCard(
                    title = "原媒体预览不可用",
                    body = "当前删除项没有返回 commentTargetMediaId、sourceMediaId 或 relatedMediaIds，无法定位原媒体文件。",
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
                timeLabel = currentMedia?.displayTimeMillis?.let(::realFormatTrashEntryTime)
                    ?: realFormatTrashEntryTime(currentEntry.deletedAtMillis),
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
                if (target != null) {
                    RealTrashViewerCapsule(
                        text = originalLoadState.actionLabel(),
                        emphasized = originalLoadState == OriginalLoadState.Loaded,
                        enabled = originalLoadState != OriginalLoadState.Loading,
                        onClick = {
                            if (originalLoadState != OriginalLoadState.Loaded &&
                                originalLoadState != OriginalLoadState.Loading
                            ) {
                                RealOriginalLoadRepository.requestOriginal(context, target, accessToken)
                            }
                        },
                    )
                }
                if (currentEntry.type == TrashEntryType.MEDIA_REMOVED) {
                    RealTrashViewerCapsule(
                        text = realTrashGridPostTitle(currentEntry),
                        emphasized = false,
                        onClick = {},
                    )
                }
                statusMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.86f),
                    )
                }
                errorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFB4AB),
                    )
                }
            }
        }

        if (!isImmersive) {
            RealTrashViewerEdgeActions(
                commentCountLabel = commentBindings?.comments?.size?.toString() ?: "0",
                previewExpanded = showCommentPreview,
                onToggleComments = { showCommentPreview = !showCommentPreview },
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
            confirmButton = {
                TextButton(
                    enabled = !isMutating,
                    onClick = {
                        showRestoreConfirm = false
                        onRestoreEntry(currentEntry)
                    },
                ) {
                    Text("恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showPermanentDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showPermanentDeleteConfirm = false },
            title = { Text("永久删除该回收站项目？") },
            text = {
                Text(
                    "确认后会删除回收站记录。媒体删除项还会删除 Server local-storage 中该媒体明确归属的原文件、preview-v2 和 cover 文件，无法恢复。",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isMutating,
                    onClick = {
                        showPermanentDeleteConfirm = false
                        onRemoveEntry(currentEntry)
                    },
                ) {
                    Text("永久删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermanentDeleteConfirm = false }) {
                    Text("取消")
                }
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
    val accessToken = AuthSessionManager.getAccessToken()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07111F)),
    ) {
        if (media == null) {
            RealTrashSectionCard(
                title = "原媒体预览不可用",
                body = "当前删除项没有返回 commentTargetMediaId、sourceMediaId 或 relatedMediaIds，无法定位原媒体文件。",
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
                contentDescription = media.mediaId,
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
            RealTrashViewerOverlayButton(text = "<", onClick = onBack)
            Box(modifier = Modifier.weight(1f))
            if (detail.canRestore) {
                RealTrashViewerOverlayButton(
                    text = if (isMutating) "…" else "↩",
                    onClick = onRestore,
                )
            }
            if (detail.canMoveOutOfTrash) {
                RealTrashViewerOverlayButton(
                    text = if (isMutating) "…" else "🗑",
                    destructive = true,
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
                Surface(
                    shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                    color = Color.Black.copy(alpha = 0.38f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                ) {
                    Text(
                        text = realTrashGridPostTitle(entry),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }
            }
            statusMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.86f),
                )
            }
            errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFB4AB),
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
                    "确认后会删除回收站记录。媒体删除项还会删除 Server local-storage 中该媒体明确归属的原文件、preview-v2 和 cover 文件，无法恢复。",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isMutating,
                    onClick = {
                        showPermanentDeleteConfirm = false
                        onRemove()
                    },
                ) {
                    Text("永久删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermanentDeleteConfirm = false }) {
                    Text("取消")
                }
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
                    Color.Black.copy(alpha = 0.30f),
                    Color.Black.copy(alpha = 0.12f),
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
                    Color.Black.copy(alpha = 0.10f),
                    Color.Black.copy(alpha = 0.28f),
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
    videoPlaybackState: ViewerVideoPlaybackState?,
    videoControlsVisible: Boolean,
    onVideoAreaClick: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekPlayback: (Long) -> Unit,
    onVideoPlaybackStateChange: (String, ViewerVideoPlaybackState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
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

        Box(
            modifier = Modifier
                .width(canvasWidth)
                .height(canvasHeight)
                .background(Color(0xFF050608)),
            contentAlignment = Alignment.Center,
        ) {
            if (media.mediaType == AppMediaType.VIDEO) {
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
                                indication = null,
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
                        color = Color.White.copy(alpha = if (videoPlaybackState.isPlaying) 0.14f else 0.18f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .padding(22.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            VideoGlyph(
                                state = if (videoPlaybackState.isPlaying) VideoGlyphState.PAUSE else VideoGlyphState.PLAY,
                                tint = Color.White.copy(alpha = 0.92f),
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
            } else {
                AppContentMediaThumbnail(
                    mediaSource = media.mediaSource,
                    mediaType = media.mediaType,
                    palette = media.palette,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = media.mediaId,
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

@Composable
private fun RealTrashViewerTopBar(
    timeLabel: String,
    isMutating: Boolean,
    canRestore: Boolean,
    canRemove: Boolean,
    onBack: () -> Unit,
    onRestore: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(42.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "<",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
        RealTrashViewerCapsule(
            text = timeLabel,
            emphasized = false,
            modifier = Modifier.align(Alignment.TopCenter),
            surfaceAlpha = 0.06f,
            contentAlpha = 0.78f,
        )
        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canRestore) {
                RealTrashViewerCapsule(
                    text = if (isMutating) "…" else "↩",
                    emphasized = true,
                    enabled = !isMutating,
                    onClick = onRestore,
                )
            }
            if (canRemove) {
                RealTrashViewerCapsule(
                    text = if (isMutating) "…" else "🗑",
                    emphasized = true,
                    enabled = !isMutating,
                    surfaceAlpha = 0.18f,
                    onClick = onRemove,
                )
            }
        }
    }
}

@Composable
private fun RealTrashViewerEdgeActions(
    commentCountLabel: String,
    previewExpanded: Boolean,
    onToggleComments: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                color = Color.White.copy(alpha = if (previewExpanded) 0.18f else 0.12f),
            ) {
                Text(
                    text = "评",
                    modifier = Modifier.padding(horizontal = YingShiThemeTokens.spacing.sm, vertical = YingShiThemeTokens.spacing.sm),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.94f),
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
        Box(modifier = Modifier.size(1.dp))
    }
}

@Composable
private fun RealTrashViewerCapsule(
    text: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    surfaceAlpha: Float = if (emphasized) 0.14f else 0.10f,
    contentAlpha: Float = 0.94f,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
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
        color = Color.White.copy(alpha = if (enabled) surfaceAlpha else 0.07f),
        border = BorderStroke(
            width = 1.dp,
            color = Color.White.copy(alpha = if (enabled) surfaceAlpha + 0.04f else 0.08f),
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
            color = Color.White.copy(alpha = if (enabled) contentAlpha else 0.58f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RealTrashViewerOverlayButton(
    text: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(YingShiThemeTokens.radius.capsule))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (destructive) {
            Color(0xFFE5484D).copy(alpha = 0.88f)
        } else {
            Color.Black.copy(alpha = 0.38f)
        },
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White.copy(alpha = 0.94f),
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
    Surface(
        modifier = modifier.fillMaxWidth(0.62f),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = Color.Black.copy(alpha = 0.36f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Column(
            modifier = Modifier.padding(YingShiThemeTokens.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xxs),
        ) {
            Text(
                text = "媒体评论",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.92f),
            )
            when {
                isLoading -> Text(
                    text = "加载中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                )
                errorMessage != null -> Text(
                    text = errorMessage.ifBlank { "评论加载失败，点击重试" },
                    modifier = Modifier.clickable(onClick = onRetry),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFFDAD6),
                )
                comments.isEmpty() -> Text(
                    text = "还没有评论",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                )
                else -> comments.take(2).forEach { comment ->
                    Text(
                        text = "${comment.author}: ${comment.content}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.86f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RealTrashPostViewerDetailContent(
    detail: RemoteTrashDetail,
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
                    isMutating = isMutating,
                    onBack = onBack,
                    onRestore = { showRestoreConfirm = true },
                    onRemove = { showPermanentDeleteConfirm = true },
                )
            },
            mediaArea = {
                if (postDetail.mediaItems.isEmpty()) {
                    RealTrashSectionCard(
                        title = "帖子媒体不可用",
                        body = "当前删除项没有返回媒体快照或媒体 ID，该位置按已删除处理。",
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
                        onLoadAllOriginals = {
                            originalTargets.forEach { RealOriginalLoadRepository.setState(it, OriginalLoadState.Loaded) }
                        },
                    )
                }
            },
            comments = {
                RealTrashReadOnlyCommentCard(
                    title = "帖子评论",
                    emptyText = "当前帖子没有可展示的评论。",
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
                Text("确认后会删除回收站记录。媒体删除项还会删除 Server local-storage 中该媒体明确归属的原文件、preview-v2 和 cover 文件，无法恢复。")
            },
            confirmButton = {
                TextButton(
                    enabled = !isMutating,
                    onClick = {
                        showPermanentDeleteConfirm = false
                        onRemove()
                    },
                ) {
                    Text("永久删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermanentDeleteConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("确认恢复？") },
            text = { Text("将恢复当前回收站帖子。") },
            confirmButton = {
                TextButton(
                    enabled = !isMutating,
                    onClick = {
                        showRestoreConfirm = false
                        onRestore()
                    },
                ) {
                    Text("恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text("取消")
                }
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(YingShiThemeTokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (comments.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                comments.take(10).forEach { comment ->
                    Column(verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xxs)) {
                        Text(
                            text = "${comment.author} · ${realFormatTrashEntryTime(comment.createdAtMillis)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = comment.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
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
    mediaId: String?,
    isMutating: Boolean,
    onBack: () -> Unit,
    onRestorePost: () -> Unit,
    onRequestDeletePost: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val accessToken = AuthSessionManager.getAccessToken()
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
            .background(Color(0xFF07111F))
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
                RealTrashViewerOverlayButton(text = "<", onClick = onBack)
                Box(modifier = Modifier.weight(1f))
                RealTrashViewerOverlayButton(text = if (isMutating) "…" else "↩", onClick = onRestorePost)
                RealTrashViewerOverlayButton(
                    text = if (isMutating) "…" else "🗑",
                    destructive = true,
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
                Surface(
                    shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                    color = Color.Black.copy(alpha = 0.38f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                ) {
                    Text(
                        text = entry.title,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }
            }
        }

        if (!isImmersive) {
            RealTrashViewerEdgeActions(
                commentCountLabel = commentBindings?.comments?.size?.toString() ?: "0",
                previewExpanded = showCommentPreview,
                onToggleComments = { showCommentPreview = !showCommentPreview },
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
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "已删除",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RealTrashDetailContent(
    detail: RemoteTrashDetail,
    statusMessage: String?,
    errorMessage: String?,
    isMutating: Boolean,
    onRestore: () -> Unit,
    onRemove: () -> Unit,
    onUndoRemove: () -> Unit,
) {
    val item = detail.item
    val spacing = YingShiThemeTokens.spacing
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
        body = item.previewInfo.ifBlank { "后端没有返回额外说明。" },
    )

    RealTrashDeletedPreview(item = item)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = "类型：${item.toTrashEntryUiModel().type.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "状态：${item.state ?: "inTrash"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.sourcePostId != null) {
                Text(
                    text = "来源帖子：${item.sourcePostId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.sourceMediaId != null) {
                Text(
                    text = "来源媒体：${item.sourceMediaId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.relatedPostIds.isNotEmpty()) {
                Text(
                    text = "关联帖子：${item.relatedPostIds.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.relatedMediaIds.isNotEmpty()) {
                Text(
                    text = "关联媒体：${item.relatedMediaIds.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                if (detail.canRestore) {
                    TextButton(
                        enabled = !isMutating,
                        onClick = onRestore,
                    ) {
                        Text(if (isMutating) "处理中…" else "恢复")
                    }
                }
                if (detail.canMoveOutOfTrash) {
                    TextButton(
                        enabled = !isMutating,
                        onClick = { showPermanentDeleteConfirm = true },
                    ) {
                        Text(if (isMutating) "处理中…" else "永久删除")
                    }
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
                    "确认后会删除回收站记录。媒体删除项还会删除 Server local-storage 中该媒体明确归属的原文件、preview-v2 和 cover 文件，无法恢复。",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isMutating,
                    onClick = {
                        showPermanentDeleteConfirm = false
                        onRemove()
                    },
                ) {
                    Text("永久删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermanentDeleteConfirm = false }) {
                    Text("取消")
                }
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
                    TrashEntryType.POST_DELETED -> "原帖子媒体"
                    TrashEntryType.MEDIA_REMOVED -> "被移除的媒体"
                    TrashEntryType.MEDIA_SYSTEM_DELETED -> "被删除的媒体"
                },
                mediaIds = mediaIds,
            )
        }

        type == TrashEntryType.POST_DELETED -> {
            RealTrashSectionCard(
                title = "原帖子内容",
                body = "当前后端删除项没有返回媒体快照，只能展示帖子标题和说明；后续可扩展更完整的帖子快照契约。",
            )
        }

        else -> {
            RealTrashSectionCard(
                title = "原媒体预览不可用",
                body = "当前删除项没有返回 commentTargetMediaId、sourceMediaId 或 relatedMediaIds，无法定位原媒体文件。",
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
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
                                contentDescription = mediaId,
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
        ?: "来源帖子"
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
        postIds = sourcePostId?.let(::listOf).orEmpty(),
        palette = palette,
        mediaType = mediaType,
        aspectRatio = aspectRatio,
        width = width,
        height = height,
        videoDurationMillis = videoDurationMillis,
        mediaSource = mediaSource,
    )
}

private fun TrashMediaSnapshot.viewerVideoDurationMillis(): Long {
    return videoDurationMillis ?: 18_000L
}

@Composable
private fun RealTrashSectionCard(
    title: String,
    body: String,
    emphasized: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(YingShiThemeTokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (emphasized) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
