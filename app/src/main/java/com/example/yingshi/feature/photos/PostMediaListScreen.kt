package com.example.yingshi.feature.photos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlin.math.abs
import kotlin.math.roundToInt

private const val PostMediaViewerMinScale = 1f
private const val PostMediaViewerMaxScale = 4f
private const val PostMediaViewerResetScale = 1.02f

data class PostMediaListItem(
    val id: String,
    val displayName: String,
    val displayTimeMillis: Long,
    val palette: PhotoThumbnailPalette,
    val mediaType: AppMediaType = AppMediaType.IMAGE,
    val aspectRatio: Float = 1f,
    val videoDurationMillis: Long? = null,
    val mediaSource: AppContentMediaSource? = null,
    val systemMediaItem: SystemMediaItem? = null,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PostMediaListScreen(
    initialItems: List<PostMediaListItem>,
    initialCoverMediaId: String?,
    onCancel: () -> Unit,
    onConfirm: (List<PostMediaListItem>, String?) -> Unit,
    modifier: Modifier = Modifier,
    allowEmpty: Boolean = true,
) {
    val context = LocalContext.current
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val initialKey = remember(initialItems, initialCoverMediaId) {
        initialItems.joinToString("|") { it.id } + "::" + initialCoverMediaId.orEmpty()
    }
    var draftItems by remember(initialKey) { mutableStateOf(initialItems.distinctBy { it.id }) }
    var coverMediaId by remember(initialKey) {
        mutableStateOf(initialCoverMediaId?.takeIf { coverId -> initialItems.any { it.id == coverId } }
            ?: initialItems.firstOrNull()?.id)
    }
    var batchDeleteMode by remember(initialKey) { mutableStateOf(false) }
    var selectedIds by remember(initialKey) { mutableStateOf<Set<String>>(emptySet()) }
    var pendingSingleDeleteId by remember { mutableStateOf<String?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showKeepOneMediaDialog by remember { mutableStateOf(false) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragCenterInRoot by remember { mutableStateOf<Offset?>(null) }
    var draggedItemSize by remember { mutableStateOf(IntSize.Zero) }
    var dragAutoScrollVelocity by remember { mutableStateOf(0f) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    val itemBounds = remember { mutableStateMapOf<String, Rect>() }
    var gridBounds by remember { mutableStateOf<Rect?>(null) }
    val gridState = rememberLazyGridState()
    val rowItems = remember(draftItems) { draftItems.chunked(2) }
    val rowKeys = remember(rowItems) { rowItems.indices.map { "post-media-row-$it" } }
    val rowKeyToMediaIds = remember(rowItems, rowKeys) {
        rowKeys.zip(rowItems).associate { (rowKey, row) -> rowKey to row.map { it.id } }
    }
    val hitTestAdapter = remember(gridState, draftItems, rowItems, rowKeys, rowKeyToMediaIds) {
        MultiSelectHitTestAdapter(
            hitTest = { touchPos ->
                val visible = gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                    val left = item.offset.x
                    val top = item.offset.y
                    val right = left + item.size.width
                    val bottom = top + item.size.height
                    touchPos.x.toInt() in left until right && touchPos.y.toInt() in top until bottom
                } ?: return@MultiSelectHitTestAdapter null
                val media = draftItems.getOrNull(visible.index) ?: return@MultiSelectHitTestAdapter null
                val rowIndex = visible.index / 2
                MultiSelectHitResult(
                    mediaId = media.id,
                    rowKey = rowKeys.getOrNull(rowIndex),
                    rowIndex = rowIndex,
                    isSelectable = true,
                    colIndex = visible.index % 2,
                    columnsInRow = rowItems.getOrNull(rowIndex)?.size ?: 0,
                )
            },
            mediaIdsInRow = { rowKey -> rowKeyToMediaIds[rowKey].orEmpty() },
            rowKeyAtIndex = { rowIndex -> rowKeys.getOrNull(rowIndex) },
        )
    }

    viewerIndex?.let { index ->
        val safeIndex = index.coerceIn(0, draftItems.lastIndex)
        val item = draftItems.getOrNull(safeIndex)
        if (item != null) {
            PostMediaViewerScreen(
                item = item,
                isCover = item.id == coverMediaId,
                onBack = { viewerIndex = null },
                onConfirmSetCover = { coverMediaId = item.id },
                modifier = modifier,
            )
            return
        } else {
            viewerIndex = null
        }
    }

    fun normalizeCover() {
        if (coverMediaId != null && draftItems.none { it.id == coverMediaId }) {
            coverMediaId = draftItems.firstOrNull()?.id
        }
    }

    fun canRemove(ids: Set<String>): Boolean {
        return allowEmpty || draftItems.any { !ids.contains(it.id) }
    }

    fun removeIds(ids: Set<String>) {
        if (ids.isEmpty()) return
        if (!canRemove(ids)) {
            showKeepOneMediaDialog = true
            return
        }
        draftItems = draftItems.filterNot { ids.contains(it.id) }
        if (coverMediaId != null && ids.contains(coverMediaId)) {
            coverMediaId = draftItems.firstOrNull { !ids.contains(it.id) }?.id
        }
        selectedIds = selectedIds - ids
        normalizeCover()
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in draftItems.indices || toIndex !in draftItems.indices || fromIndex == toIndex) return
        val next = draftItems.toMutableList()
        val item = next.removeAt(fromIndex)
        next.add(toIndex, item)
        draftItems = next
    }

    fun reorderTargetIndexAtRoot(draggedMediaId: String, center: Offset): Int? {
        val currentIndex = draftItems.indexOfFirst { it.id == draggedMediaId }
        if (currentIndex < 0) return null
        val targetIndex = draftItems.indexOfFirst { item ->
            item.id != draggedMediaId && itemBounds[item.id]?.contains(center) == true
        }
        if (targetIndex < 0 || targetIndex == currentIndex) return null
        val targetBounds = itemBounds[draftItems[targetIndex].id] ?: return null
        val targetCenter = targetBounds.center
        val currentRow = currentIndex / 2
        val targetRow = targetIndex / 2
        val crossedCenterLine = if (currentRow == targetRow) {
            if (targetIndex > currentIndex) center.x > targetCenter.x else center.x < targetCenter.x
        } else {
            if (targetIndex > currentIndex) center.y > targetCenter.y else center.y < targetCenter.y
        }
        return targetIndex.takeIf { crossedCenterLine }
    }

    fun updateDragAutoScroll(rootY: Float) {
        val bounds = gridBounds ?: run {
            dragAutoScrollVelocity = 0f
            return
        }
        val edge = 168f.coerceAtMost(bounds.height / 3f).coerceAtLeast(88f)
        val topDistance = rootY - bounds.top
        val bottomDistance = bounds.bottom - rootY
        val minVelocity = 420f
        val maxVelocity = 2600f
        dragAutoScrollVelocity = when {
            topDistance < edge -> {
                val progress = ((edge - topDistance) / edge).coerceIn(0f, 1f)
                -(minVelocity + (maxVelocity - minVelocity) * progress)
            }
            bottomDistance < edge -> {
                val progress = ((edge - bottomDistance) / edge).coerceIn(0f, 1f)
                minVelocity + (maxVelocity - minVelocity) * progress
            }
            else -> 0f
        }
    }

    BackHandler {
        if (batchDeleteMode) {
            batchDeleteMode = false
            selectedIds = emptySet()
        } else {
            onCancel()
        }
    }

    LaunchedEffect(draftItems, coverMediaId) {
        normalizeCover()
    }

    LaunchedEffect(draggedId) {
        if (draggedId == null) {
            dragAutoScrollVelocity = 0f
            return@LaunchedEffect
        }
        var previousFrameNanos = 0L
        while (draggedId != null) {
            val frameNanos = withFrameNanos { it }
            val elapsedSeconds = if (previousFrameNanos == 0L) {
                1f / 60f
            } else {
                ((frameNanos - previousFrameNanos).toFloat() / 1_000_000_000f).coerceIn(1f / 120f, 1f / 30f)
            }
            previousFrameNanos = frameNanos
            val delta = dragAutoScrollVelocity * elapsedSeconds
            val mediaId = draggedId
            val center = dragCenterInRoot
            if (mediaId != null && center != null && abs(delta) > 0.1f) {
                val consumed = gridState.scrollBy(delta)
                if (abs(consumed) > 0.1f) {
                    val currentIndex = draftItems.indexOfFirst { it.id == mediaId }
                    val targetIndex = reorderTargetIndexAtRoot(mediaId, center)
                    if (currentIndex >= 0 && targetIndex != null && targetIndex != currentIndex) {
                        moveItem(currentIndex, targetIndex)
                    }
                }
            }
        }
        dragAutoScrollVelocity = 0f
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        PostMediaListTopBar(
            batchDeleteMode = batchDeleteMode,
            selectedCount = selectedIds.size,
            onCancel = {
                if (batchDeleteMode) {
                    batchDeleteMode = false
                    selectedIds = emptySet()
                } else {
                    onCancel()
                }
            },
            onConfirm = {
                onConfirm(draftItems, coverMediaId?.takeIf { id -> draftItems.any { it.id == id } })
            },
            onBatchDelete = {
                if (selectedIds.isNotEmpty()) {
                    showBatchDeleteConfirm = true
                }
            },
        )

        if (draftItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "当前没有媒体。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            gridBounds = coordinates.boundsInRoot()
                        }
                        .multiSelectSwipeGesture(
                            enabled = batchDeleteMode,
                            hitTestAdapter = hitTestAdapter,
                            selectedIds = selectedIds,
                            onSelectionChange = { selectedIds = it },
                            onAutoScroll = { delta -> gridState.scrollBy(delta) },
                        ),
                    contentPadding = PaddingValues(bottom = spacing.xl),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    itemsIndexed(
                        items = draftItems,
                        key = { _, item -> item.id },
                        contentType = { _, _ -> "post-media-list-item" },
                    ) { index, item ->
                        val isDragging = draggedId == item.id
                        PostMediaListCard(
                            item = item,
                            isCover = item.id == coverMediaId,
                            batchDeleteMode = batchDeleteMode,
                            selected = selectedIds.contains(item.id),
                            modifier = Modifier
                                .then(if (isDragging) Modifier else Modifier.animateItem())
                                .graphicsLayer { alpha = if (isDragging) 0f else 1f }
                                .onGloballyPositioned { coordinates ->
                                    itemBounds[item.id] = coordinates.boundsInRoot()
                                },
                            dragOffset = Offset.Zero,
                            onClick = {
                                if (batchDeleteMode) {
                                    viewerIndex = index
                                } else {
                                    viewerIndex = index
                                }
                            },
                            onToggleSelected = {
                                selectedIds = if (selectedIds.contains(item.id)) selectedIds - item.id else selectedIds + item.id
                            },
                            onDeleteClick = { pendingSingleDeleteId = item.id },
                            onDeleteLongClick = {
                                batchDeleteMode = true
                                selectedIds = setOf(item.id)
                            },
                            onReorderDragStart = {
                                if (batchDeleteMode) return@PostMediaListCard
                                val bounds = itemBounds[item.id]
                                draggedId = item.id
                                dragCenterInRoot = bounds?.center
                                draggedItemSize = bounds?.let {
                                    IntSize(it.width.roundToInt(), it.height.roundToInt())
                                } ?: IntSize.Zero
                                dragCenterInRoot?.let { updateDragAutoScroll(it.y) }
                            },
                            onReorderDrag = { dragAmount ->
                                if (batchDeleteMode || draggedId != item.id) return@PostMediaListCard
                                val currentBounds = itemBounds[item.id]
                                val nextCenter = (dragCenterInRoot ?: currentBounds?.center ?: Offset.Zero) + dragAmount
                                dragCenterInRoot = nextCenter
                                val currentIndex = draftItems.indexOfFirst { it.id == item.id }
                                updateDragAutoScroll(nextCenter.y)
                                val targetIndex = reorderTargetIndexAtRoot(item.id, nextCenter)
                                if (currentIndex >= 0 && targetIndex != null && targetIndex != currentIndex) {
                                    moveItem(currentIndex, targetIndex)
                                }
                            },
                            onReorderDragEnd = {
                                draggedId = null
                                dragCenterInRoot = null
                                draggedItemSize = IntSize.Zero
                                dragAutoScrollVelocity = 0f
                            },
                        )
                    }
                }

                val overlayItem = draggedId?.let { id -> draftItems.firstOrNull { it.id == id } }
                val center = dragCenterInRoot
                val bounds = gridBounds
                if (overlayItem != null && center != null && bounds != null && draggedItemSize.width > 0 && draggedItemSize.height > 0) {
                    PostMediaDragOverlay(
                        item = overlayItem,
                        isCover = overlayItem.id == coverMediaId,
                        widthPx = draggedItemSize.width,
                        heightPx = draggedItemSize.height,
                        offsetPx = Offset(
                            x = center.x - bounds.left - draggedItemSize.width / 2f,
                            y = center.y - bounds.top - draggedItemSize.height / 2f,
                        ),
                        modifier = Modifier.zIndex(10f),
                    )
                }
            }
        }
    }

    pendingSingleDeleteId?.let { mediaId ->
        AlertDialog(
            onDismissRequest = { pendingSingleDeleteId = null },
            title = { Text("移除媒体？") },
            text = { Text("确认从当前帖子媒体列表中移除这项媒体。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingSingleDeleteId = null
                        removeIds(setOf(mediaId))
                    },
                ) {
                    Text("移除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSingleDeleteId = null }) {
                    Text("取消")
                }
            },
        )
    }

    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("批量移除媒体？") },
            text = { Text("确认从当前帖子媒体列表中移除已选 ${selectedIds.size} 项媒体。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deleteIds = selectedIds
                        showBatchDeleteConfirm = false
                        removeIds(deleteIds)
                        if (canRemove(deleteIds)) {
                            batchDeleteMode = false
                            selectedIds = emptySet()
                        }
                    },
                    enabled = selectedIds.isNotEmpty(),
                ) {
                    Text("移除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showKeepOneMediaDialog) {
        AlertDialog(
            onDismissRequest = { showKeepOneMediaDialog = false },
            title = { Text("至少保留一项媒体") },
            text = { Text("当前帖子媒体管理暂不允许把帖子媒体全部移除。") },
            confirmButton = {
                TextButton(onClick = { showKeepOneMediaDialog = false }) {
                    Text("知道了")
                }
            },
        )
    }
}

@Composable
private fun PostMediaListTopBar(
    batchDeleteMode: Boolean,
    selectedCount: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onBatchDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel) {
            Text("取消")
        }
        Text(
            text = "帖子媒体列表",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (batchDeleteMode) {
            TrashIconButton(
                enabled = selectedCount > 0,
                onClick = onBatchDelete,
            )
        } else {
            TextButton(onClick = onConfirm) {
                Text("确定")
            }
        }
    }
}

private class PostMediaViewerZoomState {
    var scale by mutableStateOf(PostMediaViewerMinScale)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    val isZoomed: Boolean
        get() = scale > PostMediaViewerResetScale

    fun reset() {
        scale = PostMediaViewerMinScale
        offset = Offset.Zero
    }

    fun applyTransform(
        zoomChange: Float,
        panChange: Offset,
        containerSize: IntSize,
    ) {
        if (containerSize.width <= 0 || containerSize.height <= 0) return
        val nextScale = (scale * zoomChange).coerceIn(PostMediaViewerMinScale, PostMediaViewerMaxScale)
        if (nextScale <= PostMediaViewerResetScale) {
            reset()
            return
        }
        scale = nextScale
        val maxX = ((containerSize.width * nextScale - containerSize.width) / 2f).coerceAtLeast(0f)
        val maxY = ((containerSize.height * nextScale - containerSize.height) / 2f).coerceAtLeast(0f)
        offset = Offset(
            x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
            y = (offset.y + panChange.y).coerceIn(-maxY, maxY),
        )
    }
}

private fun Modifier.postMediaViewerZoomGesture(
    zoomState: PostMediaViewerZoomState,
    containerSize: IntSize,
): Modifier = pointerInput(zoomState, containerSize) {
    detectTransformGestures { _, pan, zoom, _ ->
        zoomState.applyTransform(
            zoomChange = zoom,
            panChange = pan,
            containerSize = containerSize,
        )
    }
}

@Composable
private fun PostMediaViewerScreen(
    item: PostMediaListItem,
    isCover: Boolean,
    onBack: () -> Unit,
    onConfirmSetCover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSetCoverConfirm by remember(item.id) { mutableStateOf(false) }
    val zoomState = remember { PostMediaViewerZoomState() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val zoomEnabled = item.mediaType == AppMediaType.IMAGE

    ViewerStatusBarEffect(immersive = true)

    BackHandler(onBack = onBack)
    LaunchedEffect(item.id) {
        zoomState.reset()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        PostMediaListThumbnail(
            item = item,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { canvasSize = it }
                .then(
                    if (zoomEnabled) {
                        Modifier
                            .postMediaViewerZoomGesture(
                                zoomState = zoomState,
                                containerSize = canvasSize,
                            )
                            .graphicsLayer {
                                scaleX = zoomState.scale
                                scaleY = zoomState.scale
                                translationX = zoomState.offset.x
                                translationY = zoomState.offset.y
                            }
                    } else {
                        Modifier
                    },
                ),
            requestSize = 1280,
            contentScale = ContentScale.Fit,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = YingShiThemeTokens.spacing.md, vertical = YingShiThemeTokens.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                color = Color.Black.copy(alpha = 0.46f),
            ) {
                TextButton(onClick = onBack) {
                    Text("返回", color = Color.White)
                }
            }
            Box(modifier = Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                color = Color.Black.copy(alpha = if (isCover) 0.28f else 0.46f),
            ) {
                TextButton(
                    enabled = !isCover,
                    onClick = { showSetCoverConfirm = true },
                ) {
                    Text(
                        text = "设为封面",
                        color = if (isCover) Color.White.copy(alpha = 0.54f) else Color.White,
                    )
                }
            }
        }
    }

    if (showSetCoverConfirm) {
        AlertDialog(
            onDismissRequest = { showSetCoverConfirm = false },
            title = { Text("设为封面？") },
            text = { Text("确认将这项媒体设为当前帖子的封面。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSetCoverConfirm = false
                        onConfirmSetCover()
                    },
                ) {
                    Text("设为封面")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetCoverConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostMediaListCard(
    item: PostMediaListItem,
    isCover: Boolean,
    batchDeleteMode: Boolean,
    selected: Boolean,
    modifier: Modifier,
    dragOffset: Offset,
    onClick: () -> Unit,
    onToggleSelected: () -> Unit,
    onDeleteClick: () -> Unit,
    onDeleteLongClick: () -> Unit,
    onReorderDragStart: () -> Unit,
    onReorderDrag: (Offset) -> Unit,
    onReorderDragEnd: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .offset { dragOffset.toIntOffset() },
        shape = RoundedCornerShape(radius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(item.aspectRatio.coerceIn(0.92f, 1.12f))
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onReorderDragStart,
                    )
                    .then(
                        if (batchDeleteMode) {
                            Modifier
                        } else {
                            Modifier.pointerInputForReorder(
                                onDragStart = onReorderDragStart,
                                onDrag = onReorderDrag,
                                onDragEnd = onReorderDragEnd,
                            )
                        },
                    ),
            ) {
                PostMediaListThumbnail(
                    item = item,
                    modifier = Modifier.fillMaxSize(),
                    requestSize = 448,
                )

                if (isCover) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = spacing.xs, top = spacing.xs),
                        shape = RoundedCornerShape(radius.capsule),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                    ) {
                        Text(
                            text = "封面",
                            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                    }
                }

                if (!batchDeleteMode) {
                    PostMediaDeleteButton(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = spacing.xs, top = spacing.xs),
                        onClick = onDeleteClick,
                        onLongClick = onDeleteLongClick,
                    )
                }

                if (item.mediaType == AppMediaType.VIDEO) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = spacing.xs, bottom = spacing.xs),
                        shape = RoundedCornerShape(radius.capsule),
                        color = Color.Black.copy(alpha = 0.36f),
                    ) {
                        Text(
                            text = "播放",
                            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                    }
                }

                if (batchDeleteMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(46.dp)
                            .clickable(onClick = onToggleSelected),
                        contentAlignment = Alignment.BottomEnd,
                    ) {
                        AppMediaSelectionBadge(
                            selected = selected,
                            modifier = Modifier.padding(end = 2.dp, bottom = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PostMediaDragOverlay(
    item: PostMediaListItem,
    isCover: Boolean,
    widthPx: Int,
    heightPx: Int,
    offsetPx: Offset,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val density = LocalDensity.current
    Surface(
        modifier = modifier
            .offset { offsetPx.toIntOffset() }
            .size(
                width = with(density) { widthPx.toDp() },
                height = with(density) { heightPx.toDp() },
            )
            .graphicsLayer {
                shadowElevation = 18f
                scaleX = 1.035f
                scaleY = 1.035f
            },
        shape = RoundedCornerShape(radius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PostMediaListThumbnail(
                item = item,
                modifier = Modifier.fillMaxSize(),
                requestSize = 448,
            )
            if (isCover) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = spacing.xs, top = spacing.xs),
                    shape = RoundedCornerShape(radius.capsule),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                ) {
                    Text(
                        text = "封面",
                        modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                }
            }
            if (item.mediaType == AppMediaType.VIDEO) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = spacing.xs, bottom = spacing.xs),
                    shape = RoundedCornerShape(radius.capsule),
                    color = Color.Black.copy(alpha = 0.36f),
                ) {
                    Text(
                        text = "播放",
                        modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PostMediaListThumbnail(
    item: PostMediaListItem,
    modifier: Modifier = Modifier,
    requestSize: Int = 320,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val systemItem = item.systemMediaItem
    if (systemItem != null) {
        val videoThumbnail = if (systemItem.type == SystemMediaType.VIDEO) {
            rememberSystemVideoThumbnail(context, systemItem.uri)
        } else {
            null
        }
        if (videoThumbnail != null) {
            Image(
                bitmap = videoThumbnail.toComposeBitmap(),
                contentDescription = item.displayName,
                modifier = modifier.background(item.palette.start.copy(alpha = 0.42f)),
                contentScale = contentScale,
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(systemItem.uri)
                    .size(requestSize)
                    .precision(Precision.INEXACT)
                    .crossfade(false)
                    .build(),
                contentDescription = item.displayName,
                modifier = modifier.background(item.palette.start.copy(alpha = 0.42f)),
                contentScale = contentScale,
            )
        }
    } else {
        AppContentMediaThumbnail(
            mediaSource = item.mediaSource,
            mediaType = item.mediaType,
            palette = item.palette,
            modifier = modifier,
            contentDescription = item.displayName,
            contentScale = contentScale,
            requestSize = requestSize,
            showLoadingIndicator = false,
            showVideoPlayOverlay = false,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostMediaDeleteButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .size(28.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.48f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "×",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TrashIconButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
            ),
        shape = CircleShape,
        color = if (enabled) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        border = BorderStroke(
            1.dp,
            if (enabled) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else Color.Transparent,
        ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            val color = if (enabled) Color(0xFFDC2626) else Color(0xFF9CA3AF)
            drawLine(color, Offset(size.width * 0.25f, size.height * 0.28f), Offset(size.width * 0.75f, size.height * 0.28f), strokeWidth = 2.2f, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.42f, size.height * 0.14f), Offset(size.width * 0.58f, size.height * 0.14f), strokeWidth = 2.2f, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.34f, size.height * 0.34f), Offset(size.width * 0.40f, size.height * 0.86f), strokeWidth = 2.2f, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.66f, size.height * 0.34f), Offset(size.width * 0.60f, size.height * 0.86f), strokeWidth = 2.2f, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.40f, size.height * 0.86f), Offset(size.width * 0.60f, size.height * 0.86f), strokeWidth = 2.2f, cap = StrokeCap.Round)
        }
    }
}

private fun Modifier.pointerInputForReorder(
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = pointerInput(Unit) {
    detectDragGesturesAfterLongPress(
        onDragStart = { onDragStart() },
        onDrag = { change, dragAmount ->
            change.consume()
            onDrag(dragAmount)
        },
        onDragEnd = onDragEnd,
        onDragCancel = onDragEnd,
    )
}

private fun Offset.toIntOffset(): IntOffset {
    return IntOffset(x.roundToInt(), y.roundToInt())
}

internal fun SystemMediaItem.toPostMediaListItem(): PostMediaListItem {
    return PostMediaListItem(
        id = id,
        displayName = displayName,
        displayTimeMillis = displayTimeMillis,
        palette = palette,
        mediaType = when (type) {
            SystemMediaType.IMAGE -> AppMediaType.IMAGE
            SystemMediaType.VIDEO -> AppMediaType.VIDEO
        },
        aspectRatio = aspectRatio,
        videoDurationMillis = videoDurationMillis,
        systemMediaItem = this,
    )
}

internal fun CreatePostAppMediaItem.toPostMediaListItem(): PostMediaListItem {
    return PostMediaListItem(
        id = mediaId,
        displayName = displayName,
        displayTimeMillis = 0L,
        palette = palette,
        mediaType = mediaType,
        aspectRatio = 1f,
        mediaSource = mediaSource,
    )
}

internal fun ManagedPostMediaUiModel.toPostMediaListItem(): PostMediaListItem {
    return PostMediaListItem(
        id = id,
        displayName = id,
        displayTimeMillis = displayTimeMillis,
        palette = palette,
        mediaType = mediaType,
        aspectRatio = aspectRatio,
        videoDurationMillis = videoDurationMillis,
        mediaSource = mediaSource,
    )
}
