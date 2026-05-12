package com.example.yingshi.feature.photos

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val InitialSystemMediaRenderCount = 120
private const val SystemMediaRenderPageSize = 90
private const val SystemMediaThumbnailRequestSize = 384

@Composable
fun SystemMediaScreen(
    onBack: () -> Unit,
    onOpenViewer: (SystemMediaViewerRoute) -> Unit,
    onOpenPostDetail: (PostDetailPlaceholderRoute) -> Unit,
    onOpenCreatePost: (CreatePostRoute) -> Unit,
    modifier: Modifier = Modifier,
    scrollTrigger: Int = 0,
    inlineVideoAutoPlayEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext as Application
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: SystemMediaViewModel = viewModel(
        factory = SystemMediaViewModel.factory(
            application = appContext,
            initialFilter = LocalSystemMediaPageStateStore.selectedFilter,
        ),
    )
    val uiState by viewModel.uiState.collectAsState()
    val bridgeMutationEvent = LocalSystemMediaBridgeRepository.latestMutationEvent
    val destinationUiState by rememberSystemMediaDestinationUiState()
    val albums = destinationUiState.albums
    val posts = destinationUiState.posts
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = LocalSystemMediaPageStateStore.firstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = LocalSystemMediaPageStateStore.firstVisibleItemScrollOffset,
    )
    var hasPermission by rememberSaveable {
        mutableStateOf(hasSystemMediaReadAccess(context))
    }
    var permissionRequestedOnce by rememberSaveable {
        mutableStateOf(false)
    }
    var renderedCount by rememberSaveable(uiState.selectedFilter) {
        mutableIntStateOf(InitialSystemMediaRenderCount)
    }
    var selectionMode by rememberSaveable {
        mutableStateOf(false)
    }
    var selectedIds by rememberSaveable {
        mutableStateOf(emptyList<String>())
    }
    var selectionFlashNonce by remember { mutableIntStateOf(0) }
    var selectionFlashByMediaId by remember { mutableStateOf<Map<String, SelectionNumberFlash>>(emptyMap()) }
    var showAddToPostDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var pendingTrashIds by rememberSaveable {
        mutableStateOf(emptyList<String>())
    }
    var pendingSystemTrashItems by remember {
        mutableStateOf<List<SystemMediaItem>>(emptyList())
    }
    var densityName by rememberSaveable {
        mutableStateOf(PhotoFeedDensity.DENSE_4.name)
    }
    var scrubberInteracting by remember {
        mutableStateOf(false)
    }
    var scrubberVisible by remember {
        mutableStateOf(false)
    }
    var scrubberDragProgress by remember {
        mutableStateOf<Float?>(null)
    }
    var scrubberDragLabel by remember {
        mutableStateOf("")
    }
    var scrubberLabelWidthPx by remember {
        mutableIntStateOf(0)
    }
    var lastRequestedScrubberIndex by remember {
        mutableIntStateOf(-1)
    }
    var pendingTargetMediaIdSnapshot by remember { mutableStateOf<String?>(null) }
    var pendingTargetRenderedCount by remember { mutableIntStateOf(-1) }
    val spacing = YingShiThemeTokens.spacing
    val coroutineScope = rememberCoroutineScope()
    val density = PhotoFeedDensity.valueOf(densityName)
    val inlineVideoAutoPlayAllowed = inlineVideoAutoPlayEnabled &&
        !selectionMode &&
        density.columns <= 4
    val updateDensity = remember(density) {
        { nextDensity: PhotoFeedDensity ->
            if (nextDensity != density) {
                densityName = nextDensity.name
            }
        }
    }
    val selectedIdSet = selectedIds.toSet()
    val selectedItems = uiState.filteredItems.filter { selectedIdSet.contains(it.id) }
    val spacingPx = with(LocalDensity.current) { 2.dp.toPx() }
    val visibleItems by remember(uiState.filteredItems, renderedCount) {
        derivedStateOf {
            uiState.filteredItems.take(renderedCount.coerceAtMost(uiState.filteredItems.size))
        }
    }
    val gridBlocks = remember(visibleItems) {
        buildSystemMediaGridBlocks(visibleItems)
    }
    var manualInlineVideoId by remember { mutableStateOf<String?>(null) }
    var pausedInlineVideoIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var inlineVideoProgressById by remember { mutableStateOf<Map<String, InlineVideoPlaybackProgress>>(emptyMap()) }
    val visibleInlineVideoIds by remember(gridState, gridBlocks) {
        derivedStateOf {
            visibleSystemMediaVideoIds(
                gridState = gridState,
                gridBlocks = gridBlocks,
            )
        }
    }
    val centeredInlineVideoId by remember(gridState, gridBlocks) {
        derivedStateOf {
            centeredSystemMediaVideoId(
                gridState = gridState,
                gridBlocks = gridBlocks,
            )
        }
    }
    LaunchedEffect(inlineVideoAutoPlayAllowed) {
        if (!inlineVideoAutoPlayAllowed) {
            manualInlineVideoId = null
        }
    }
    LaunchedEffect(visibleInlineVideoIds) {
        val manualId = manualInlineVideoId
        if (manualId != null && manualId !in visibleInlineVideoIds) {
            manualInlineVideoId = null
        }
    }
    val activeInlineVideoId = if (inlineVideoAutoPlayAllowed) {
        manualInlineVideoId?.takeIf { it in visibleInlineVideoIds } ?: centeredInlineVideoId
    } else {
        null
    }
    val playingInlineVideoId = activeInlineVideoId?.takeUnless { it in pausedInlineVideoIds }
    val onToggleInlineVideo = remember(inlineVideoAutoPlayAllowed, activeInlineVideoId, pausedInlineVideoIds) {
        toggle@{ item: SystemMediaItem ->
            if (!inlineVideoAutoPlayAllowed || item.type != SystemMediaType.VIDEO) {
                return@toggle
            }
            if (activeInlineVideoId == item.id) {
                pausedInlineVideoIds = if (item.id in pausedInlineVideoIds) {
                    pausedInlineVideoIds - item.id
                } else {
                    pausedInlineVideoIds + item.id
                }
            } else {
                manualInlineVideoId = item.id
                pausedInlineVideoIds = pausedInlineVideoIds - item.id
            }
        }
    }
    val systemRowMapping = remember(gridBlocks, density.columns) {
        val mediaToRow = mutableMapOf<String, String>()
        val mediaToColumn = mutableMapOf<String, Int>()
        val rowToMedia = mutableMapOf<String, MutableList<String>>()
        val rowKeys = mutableListOf<String>()
        var mediaInRow = 0
        var rowIndex = 0
        gridBlocks.forEach { block ->
            when (block) {
                is SystemMediaGridBlock.Media -> {
                    val rowKey = "sys-row-$rowIndex"
                    if (mediaInRow == 0) {
                        rowKeys += rowKey
                    }
                    mediaToRow[block.item.id] = rowKey
                    mediaToColumn[block.item.id] = mediaInRow
                    rowToMedia.getOrPut(rowKey) { mutableListOf() }.add(block.item.id)
                    mediaInRow++
                    if (mediaInRow >= density.columns) {
                        mediaInRow = 0
                        rowIndex++
                    }
                }
                is SystemMediaGridBlock.MonthHeader,
                is SystemMediaGridBlock.DayHeader,
                -> {
                    if (mediaInRow > 0) {
                        mediaInRow = 0
                        rowIndex++
                    }
                }
            }
        }
        SystemMediaRowMapping(
            mediaToRow = mediaToRow,
            mediaToColumn = mediaToColumn,
            rowToMedia = rowToMedia.mapValues { it.value.toList() },
            rowKeys = rowKeys,
        )
    }
    val hitTestAdapter = remember(gridState, gridBlocks, systemRowMapping, density.columns, spacingPx) {
        val colSpacingPx = spacingPx
        MultiSelectHitTestAdapter(
            hitTest = { touchPos ->
                val layout = gridState.layoutInfo
                val tx = touchPos.x.toInt()
                val ty = touchPos.y.toInt()
                val viewportW = layout.viewportSize.width.coerceAtLeast(1)
                val totalSpacing = (density.columns - 1) * colSpacingPx
                val cellWidth = ((viewportW - totalSpacing).toFloat() / density.columns)
                val segmentWidth = cellWidth + colSpacingPx
                for (vi in layout.visibleItemsInfo) {
                    val block = gridBlocks.getOrNull(vi.index) as? SystemMediaGridBlock.Media ?: continue
                    val itemEndY = vi.offset.y + vi.size.height
                    if (ty !in vi.offset.y until itemEndY) continue

                    val rowKey = systemRowMapping.mediaToRow[block.item.id] ?: continue
                    val rowItems = systemRowMapping.rowToMedia[rowKey].orEmpty()
                    if (rowItems.isEmpty()) continue

                    val colIndex = (tx / segmentWidth).toInt().coerceIn(0, density.columns - 1)
                    val mediaId = rowItems.getOrNull(colIndex) ?: return@MultiSelectHitTestAdapter null
                    return@MultiSelectHitTestAdapter MultiSelectHitResult(
                        mediaId = mediaId,
                        rowKey = rowKey,
                        rowIndex = systemRowMapping.rowIndexForMedia(mediaId),
                        isSelectable = true,
                        colIndex = colIndex,
                        columnsInRow = rowItems.size,
                    )
                }
                null
            },
            mediaIdsInRow = { rowKey -> systemRowMapping.rowToMedia[rowKey].orEmpty() },
            rowKeyAtIndex = { rowIndex -> systemRowMapping.rowKeys.getOrNull(rowIndex) },
        )
    }
    val currentScrollProgress by remember(gridState, gridBlocks, uiState.filteredItems.size) {
        derivedStateOf {
            calculateSystemMediaScrollProgress(
                gridState = gridState,
                blocks = gridBlocks,
                itemCount = uiState.filteredItems.size,
            )
        }
    }
    val currentScrubberLabel by remember(gridState, gridBlocks, uiState.filteredItems) {
        derivedStateOf {
            resolveCurrentSystemMediaVisibleLabel(
                itemIndex = gridState.firstVisibleItemIndex,
                blocks = gridBlocks,
                fallbackItems = uiState.filteredItems,
            )
        }
    }
    val displayedScrubberProgress = if (scrubberInteracting) {
        scrubberDragProgress ?: currentScrollProgress
    } else {
        currentScrollProgress
    }
    val displayedScrubberLabel = if (scrubberInteracting) {
        scrubberDragLabel.ifBlank { currentScrubberLabel }
    } else {
        currentScrubberLabel
    }
    LaunchedEffect(currentScrollProgress, scrubberInteracting, uiState.filteredItems.size) {
        if (uiState.filteredItems.size <= 1) {
            scrubberVisible = false
            return@LaunchedEffect
        }
        scrubberVisible = true
        if (!scrubberInteracting) {
            delay(900)
            if (!scrubberInteracting) {
                scrubberVisible = false
            }
        }
    }
    LaunchedEffect(
        scrollTrigger,
        uiState.filteredItems,
        renderedCount,
        density.columns,
    ) {
        val mediaId = LocalSystemMediaPageStateStore.pendingScrollTargetMediaId ?: return@LaunchedEffect
        val targetIndex = uiState.filteredItems.indexOfFirst { it.id == mediaId }
        if (mediaId != pendingTargetMediaIdSnapshot) {
            pendingTargetMediaIdSnapshot = mediaId
            pendingTargetRenderedCount = -1
        }
        if (targetIndex < 0) {
            pendingTargetMediaIdSnapshot = null
            pendingTargetRenderedCount = -1
            LocalSystemMediaPageStateStore.pendingScrollTargetMediaId = null
            LocalSystemMediaPageStateStore.pendingScrollAnchorOriginalIndex = -1
            return@LaunchedEffect
        }
        if (targetIndex >= visibleItems.size && targetIndex < uiState.filteredItems.size) {
            val nextRenderedCount = (targetIndex + SystemMediaRenderPageSize)
                .coerceAtMost(uiState.filteredItems.size)
            if (nextRenderedCount > renderedCount && pendingTargetRenderedCount != nextRenderedCount) {
                pendingTargetRenderedCount = nextRenderedCount
                renderedCount = nextRenderedCount
            }
            return@LaunchedEffect
        }
        if (targetIndex in gridState.layoutInfo.visibleItemsInfo.map { it.index }) {
            pendingTargetMediaIdSnapshot = null
            pendingTargetRenderedCount = -1
            LocalSystemMediaPageStateStore.pendingScrollTargetMediaId = null
            LocalSystemMediaPageStateStore.pendingScrollAnchorOriginalIndex = -1
            return@LaunchedEffect
        }
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.isNotEmpty() }
            .first { it }
        gridState.scrollToItem(targetIndex)
        pendingTargetMediaIdSnapshot = null
        pendingTargetRenderedCount = -1
        LocalSystemMediaPageStateStore.pendingScrollTargetMediaId = null
        LocalSystemMediaPageStateStore.pendingScrollAnchorOriginalIndex = -1
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasPermission = hasSystemMediaReadAccess(context)
        if (hasPermission) {
            viewModel.refresh(forceRefresh = true)
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = hasSystemMediaReadAccess(context)
                hasPermission = granted
                if (granted) {
                    viewModel.ensureLoaded()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val trashLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val processedIds = pendingTrashIds
        pendingTrashIds = emptyList()
        if (processedIds.isEmpty()) return@rememberLauncherForActivityResult

        if (result.resultCode == Activity.RESULT_OK) {
            val hiddenCount = LocalSystemMediaBridgeRepository.markMovedToSystemTrash(processedIds)
            selectionMode = false
            selectedIds = emptyList()
            viewModel.refresh(forceRefresh = true)
            Toast.makeText(
                context,
                if (hiddenCount > 0) {
                    "已移到系统回收站。"
                } else {
                    "这些媒体已经处理过了。"
                },
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            Toast.makeText(context, "已取消移到系统回收站。", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchSystemTrashRequest(items: List<SystemMediaItem>) {
        if (items.isEmpty()) {
            Toast.makeText(context, "请先选择要移到系统回收站的媒体。", Toast.LENGTH_SHORT).show()
            return
        }
        createSystemMediaTrashRequest(context, items)
            .onSuccess { pendingIntent ->
                pendingTrashIds = items.map { it.id }
                runCatching {
                    trashLauncher.launch(
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                    )
                }.onFailure { throwable ->
                    pendingTrashIds = emptyList()
                    Toast.makeText(
                        context,
                        throwable.message ?: "无法拉起系统回收站确认流程。",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            .onFailure { throwable ->
                Toast.makeText(
                    context,
                    throwable.message ?: systemMediaTrashUnsupportedMessage(),
                    Toast.LENGTH_SHORT,
                ).show()
            }
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission && !permissionRequestedOnce) {
            permissionRequestedOnce = true
            permissionLauncher.launch(requiredSystemMediaPermissions())
        } else if (hasPermission) {
            viewModel.ensureLoaded()
        }
    }

    LaunchedEffect(uiState.selectedFilter, uiState.filteredItems.size) {
        renderedCount = InitialSystemMediaRenderCount.coerceAtMost(uiState.filteredItems.size)
    }

    LaunchedEffect(bridgeMutationEvent.version) {
        viewModel.handleBridgeMutation(bridgeMutationEvent)
    }

    LaunchedEffect(uiState.selectedFilter) {
        LocalSystemMediaPageStateStore.selectedFilter = uiState.selectedFilter
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }.collectLatest { (index, offset) ->
            LocalSystemMediaPageStateStore.firstVisibleItemIndex = index
            LocalSystemMediaPageStateStore.firstVisibleItemScrollOffset = offset
        }
    }

    LaunchedEffect(gridState, gridBlocks.size, uiState.filteredItems.size) {
        snapshotFlow {
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisibleIndex >= (gridBlocks.lastIndex - 24).coerceAtLeast(0)
        }.collectLatest { shouldLoadMore ->
            if (shouldLoadMore && renderedCount < uiState.filteredItems.size) {
                renderedCount = (renderedCount + SystemMediaRenderPageSize)
                    .coerceAtMost(uiState.filteredItems.size)
            }
        }
    }

    if (selectionMode) {
        BackHandler {
            selectionMode = false
            selectedIds = emptyList()
        }
    }

    if (showAddToPostDialog) {
        SystemMediaPostDestinationDialog(
            albums = albums,
            posts = posts,
            onDismiss = { showAddToPostDialog = false },
            onPostSelected = { postId ->
                val addedCount = LocalSystemMediaBridgeRepository.enqueueAddToExistingPostUpload(
                    context = context,
                    postId = postId,
                    mediaItems = selectedItems,
                )
                showAddToPostDialog = false
                selectedIds = emptyList()
                selectionMode = false
                Toast.makeText(
                    context,
                    if (addedCount > 0) {
                        "已加入已有帖子，并同步刷新到照片与相册页。"
                    } else {
                        "这些媒体已经在目标帖子里了。"
                    },
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
    }

    if (pendingSystemTrashItems.isNotEmpty()) {
        val trashCount = pendingSystemTrashItems.size
        AlertDialog(
            onDismissRequest = { pendingSystemTrashItems = emptyList() },
            title = { Text("移到系统相册回收站？") },
            text = {
                Text(
                    "将对已选 $trashCount 项系统相册媒体发起 Android 系统回收站操作。它们不会进入 App 回收站，也不会删除后端 App 媒体记录；确认后还会出现 Android 系统确认框。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val items = pendingSystemTrashItems
                        pendingSystemTrashItems = emptyList()
                        launchSystemTrashRequest(items)
                    },
                ) {
                    Text("继续系统回收站")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSystemTrashItems = emptyList() }) {
                    Text("取消")
                }
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = spacing.md, bottom = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                SystemMediaTopBar(
                    selectedFilter = uiState.selectedFilter,
                    selectionMode = selectionMode,
                    selectedCount = selectedIds.size,
                    onBack = onBack,
                    onRefresh = { viewModel.refresh(forceRefresh = true) },
                    onToggleSelectionMode = {
                        selectionMode = !selectionMode
                        if (!selectionMode) {
                            selectedIds = emptyList()
                        }
                    },
                )

                SystemMediaFilterRow(
                    selectedFilter = uiState.selectedFilter,
                    onFilterSelected = viewModel::onFilterSelected,
                )
            }

            when {
                !hasPermission -> {
                    SystemMediaPermissionState(
                        modifier = Modifier.weight(1f),
                        onRequestPermission = {
                            permissionLauncher.launch(requiredSystemMediaPermissions())
                        },
                        onOpenSettings = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                ),
                            )
                        },
                    )
                }

                uiState.isLoading -> {
                    SystemMediaLoadingState(modifier = Modifier.weight(1f))
                }

                uiState.hasError && uiState.allItems.isEmpty() -> {
                    SystemMediaErrorState(
                        message = uiState.errorMessage.orEmpty(),
                        onRetry = { viewModel.refresh(forceRefresh = true) },
                        modifier = Modifier.weight(1f),
                    )
                }

                uiState.filteredItems.isEmpty() -> {
                    SystemMediaEmptyState(
                        text = if (uiState.selectedFilter == SystemMediaFilter.ALL) {
                            "当前没有可显示的本地媒体。"
                        } else {
                            "当前筛选下没有可显示的媒体。"
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                else -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .discreteZoomLevelGesture(
                                enabled = !selectionMode,
                                levels = PhotoFeedDensity.entries.toList(),
                                currentLevel = density,
                                onLevelChange = updateDensity,
                            )
                            .multiSelectSwipeGesture(
                                enabled = selectionMode,
                                hitTestAdapter = hitTestAdapter,
                                selectedIds = selectedIdSet,
                                onSelectionChange = { newIds ->
                                    selectedIds = newIds.toList()
                                },
                                onAutoScroll = { delta -> gridState.scrollBy(delta) },
                            ),
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(density.columns),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            contentPadding = PaddingValues(bottom = 112.dp),
                        ) {
                            items(
                                items = gridBlocks,
                                key = { it.key },
                                span = { block ->
                                    when (block) {
                                        is SystemMediaGridBlock.Media -> GridItemSpan(1)
                                        is SystemMediaGridBlock.MonthHeader,
                                        is SystemMediaGridBlock.DayHeader,
                                        -> GridItemSpan(maxLineSpan)
                                    }
                                },
                                contentType = { block ->
                                    when (block) {
                                        is SystemMediaGridBlock.MonthHeader -> "system-month"
                                        is SystemMediaGridBlock.DayHeader -> "system-day"
                                        is SystemMediaGridBlock.Media -> "${block.item.type}-${density.columns}"
                                    }
                                },
                            ) { block ->
                                when (block) {
                                    is SystemMediaGridBlock.MonthHeader -> {
                                        SystemMediaMonthHeaderRow(title = block.title)
                                    }
                                    is SystemMediaGridBlock.DayHeader -> {
                                        SystemMediaDayHeaderRow(title = block.title)
                                    }
                                    is SystemMediaGridBlock.Media -> {
                                        val item = block.item
                                        SystemMediaCard(
                                            item = item,
                                            density = density,
                                            selectionMode = selectionMode,
                                            selected = selectedIdSet.contains(item.id),
                                            selectionFlash = selectionFlashByMediaId[item.id],
                                            inlineVideoAutoPlayEnabled = inlineVideoAutoPlayAllowed,
                                            isInlineVideoPlaying = playingInlineVideoId == item.id,
                                            isInlineVideoActive = activeInlineVideoId == item.id,
                                            isInlineVideoPaused = item.id in pausedInlineVideoIds,
                                            inlineVideoProgress = inlineVideoProgressById[item.id],
                                            onClick = {
                                                if (selectionMode) {
                                                    val wasSelected = selectedIds.contains(item.id)
                                                    selectedIds = selectedIds.toggleSystemMediaId(item.id)
                                                    if (!wasSelected && selectedIds.contains(item.id)) {
                                                        selectionFlashNonce += 1
                                                        selectionFlashByMediaId = selectionFlashByMediaId +
                                                            (item.id to SelectionNumberFlash(selectedIds.size, selectionFlashNonce))
                                                    }
                                                } else {
                                                    onOpenViewer(
                                                        SystemMediaViewerRoute(
                                                            mediaItems = uiState.filteredItems,
                                                            initialIndex = uiState.filteredItems.indexOfFirst { it.id == item.id }
                                                                .coerceAtLeast(0),
                                                        ),
                                                    )
                                                }
                                            },
                                            onOpenMedia = {
                                                onOpenViewer(
                                                    SystemMediaViewerRoute(
                                                        mediaItems = uiState.filteredItems,
                                                        initialIndex = uiState.filteredItems.indexOfFirst { it.id == item.id }
                                                            .coerceAtLeast(0),
                                                    ),
                                                )
                                            },
                                            onLongPress = {
                                                selectionMode = true
                                                selectedIds = selectedIds.toggleSystemMediaId(item.id)
                                            },
                                            onToggleInlineVideo = { onToggleInlineVideo(item) },
                                            onInlineVideoProgressChange = { progress ->
                                                inlineVideoProgressById = inlineVideoProgressById + (item.id to progress)
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = scrubberVisible && uiState.filteredItems.size > 1,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight(),
                        ) {
                            SystemMediaTimeScrubber(
                                modifier = Modifier.fillMaxHeight(),
                                progress = displayedScrubberProgress,
                                label = displayedScrubberLabel,
                                showLabel = scrubberInteracting,
                                onSeekToProgress = { progress ->
                                    val targetIndex = (progress * (uiState.filteredItems.lastIndex).coerceAtLeast(0))
                                        .roundToInt()
                                        .coerceIn(0, (uiState.filteredItems.size - 1).coerceAtLeast(0))
                                    scrubberDragProgress = progress.coerceIn(0f, 1f)
                                    val targetItem = uiState.filteredItems.getOrNull(targetIndex)
                                    scrubberDragLabel = targetItem?.toSystemMediaScrubberLabel().orEmpty()
                                    if (targetIndex == lastRequestedScrubberIndex) {
                                        return@SystemMediaTimeScrubber
                                    }
                                    lastRequestedScrubberIndex = targetIndex
                                    val nextRenderedCount = if (targetIndex >= visibleItems.size && targetIndex < uiState.filteredItems.size) {
                                        (targetIndex + SystemMediaRenderPageSize)
                                            .coerceAtMost(uiState.filteredItems.size)
                                    } else {
                                        renderedCount
                                    }
                                    if (targetIndex >= visibleItems.size && targetIndex < uiState.filteredItems.size) {
                                        renderedCount = nextRenderedCount
                                    }
                                    val targetBlocks = if (nextRenderedCount != visibleItems.size) {
                                        buildSystemMediaGridBlocks(uiState.filteredItems.take(nextRenderedCount))
                                    } else {
                                        gridBlocks
                                    }
                                    val targetBlockIndex = targetItem
                                        ?.let { item -> targetBlocks.indexOfFirst { block -> block is SystemMediaGridBlock.Media && block.item.id == item.id } }
                                        ?.takeIf { it >= 0 }
                                        ?: 0
                                    coroutineScope.launch {
                                        if (nextRenderedCount != visibleItems.size) {
                                            delay(16)
                                        }
                                        gridState.scrollToItem(targetBlockIndex)
                                    }
                                },
                                onInteractingChanged = { interacting ->
                                    scrubberInteracting = interacting
                                    if (interacting) {
                                        scrubberDragProgress = currentScrollProgress
                                        scrubberDragLabel = currentScrubberLabel
                                    } else {
                                        scrubberDragProgress = null
                                        scrubberDragLabel = ""
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = selectionMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
        ) {
            SystemMediaSelectionBar(
                selectedCount = selectedIds.size,
                onImportToApp = {
                    val importedCount = LocalSystemMediaBridgeRepository.enqueueImportToAppUpload(
                        context = context,
                        mediaItems = selectedItems,
                    )
                    selectedIds = emptyList()
                    selectionMode = false
                    Toast.makeText(
                        context,
                        if (importedCount > 0) {
                            "已加入导入 app 队列，完成后会出现在照片流。"
                        } else {
                            "请先选择要导入 app 的媒体。"
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onCreatePost = {
                    val selectedSnapshot = selectedItems
                    selectedIds = emptyList()
                    selectionMode = false
                    if (selectedSnapshot.isEmpty()) {
                        Toast.makeText(context, "请先选择要发成新帖子的媒体。", Toast.LENGTH_SHORT).show()
                    } else {
                        onOpenCreatePost(
                            CreatePostRoute(
                                source = "system-media-selection",
                                initialMediaItems = selectedSnapshot,
                            ),
                        )
                    }
                },
                onAddToPost = {
                    if (destinationUiState.errorMessage != null && posts.isEmpty()) {
                        Toast.makeText(context, destinationUiState.errorMessage, Toast.LENGTH_SHORT).show()
                    } else {
                        showAddToPostDialog = true
                    }
                },
                onMoveToTrash = {
                    pendingSystemTrashItems = selectedItems
                },
                onCancel = {
                    selectionMode = false
                    selectedIds = emptyList()
                },
            )
        }

    }
}

@Composable
private fun SystemMediaPermissionState(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "需要图片和视频权限才能显示系统媒体。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onRequestPermission) {
                        Text(text = "继续授权")
                    }
                    TextButton(onClick = onOpenSettings) {
                        Text(text = "去设置")
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemMediaTopBar(
    selectedFilter: SystemMediaFilter,
    selectionMode: Boolean,
    selectedCount: Int,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleSelectionMode: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SystemMediaCircleButton(
            text = "<",
            onClick = onBack,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "系统媒体",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (selectionMode) {
                    if (selectedCount > 0) "多选中 $selectedCount 项" else "请选择媒体"
                } else {
                    "筛选：${selectedFilter.label}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SystemMediaActionChip(
            text = "刷新",
            emphasized = false,
            onClick = onRefresh,
        )
        SystemMediaActionChip(
            text = if (selectionMode) "取消多选" else "多选",
            emphasized = selectionMode,
            onClick = onToggleSelectionMode,
        )
    }
}

@Composable
private fun SystemMediaFilterRow(
    selectedFilter: SystemMediaFilter,
    onFilterSelected: (SystemMediaFilter) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        SystemMediaFilter.entries.forEach { filter ->
            SystemMediaActionChip(
                text = filter.label,
                emphasized = filter == selectedFilter,
                onClick = { onFilterSelected(filter) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SystemMediaCard(
    item: SystemMediaItem,
    density: PhotoFeedDensity,
    selectionMode: Boolean,
    selected: Boolean,
    selectionFlash: SelectionNumberFlash?,
    inlineVideoAutoPlayEnabled: Boolean,
    isInlineVideoPlaying: Boolean,
    isInlineVideoActive: Boolean,
    isInlineVideoPaused: Boolean,
    inlineVideoProgress: InlineVideoPlaybackProgress?,
    onClick: () -> Unit,
    onOpenMedia: () -> Unit,
    onLongPress: () -> Unit,
    onToggleInlineVideo: () -> Unit,
    onInlineVideoProgressChange: (InlineVideoPlaybackProgress) -> Unit,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(0.dp)
    val videoThumbnail = if (item.type == SystemMediaType.VIDEO) {
        rememberSystemVideoThumbnail(context, item.uri)
    } else {
        null
    }
    val selectionHotspotOnly = selectionMode && density.columns in 2..4
    val supportsInlineVideo = inlineVideoAutoPlayEnabled &&
        !selectionMode &&
        item.type == SystemMediaType.VIDEO &&
        density.columns <= 4
    val showSelectionVideoMarker = selectionMode &&
        item.type == SystemMediaType.VIDEO &&
        density.columns <= 4

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .combinedClickable(
                onClick = if (selectionHotspotOnly) onOpenMedia else onClick,
                onLongClick = onLongPress,
            ),
    ) {
        if (videoThumbnail != null) {
            Image(
                bitmap = videoThumbnail.toComposeBitmap(),
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.uri)
                    .size(SystemMediaThumbnailRequestSize)
                    .precision(Precision.INEXACT)
                    .crossfade(false)
                    .build(),
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        if (supportsInlineVideo && isInlineVideoPlaying) {
            SystemMediaInlineVideoPlayer(
                item = item,
                playWhenReady = true,
                modifier = Modifier.fillMaxSize(),
                onPlaybackProgressChange = onInlineVideoProgressChange,
            )
        }

        if (item.linkedPostIds.isNotEmpty()) {
            SystemMediaBadge(
                text = "已发帖",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            )
        }

        if (!selectionMode && item.type != SystemMediaType.VIDEO) {
            SystemMediaTypeBadge(
                text = item.type.label,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            )
        }

        if (item.type == SystemMediaType.VIDEO && !supportsInlineVideo) {
            SystemMediaBadge(
                text = "视频",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
            )
        }

        if (supportsInlineVideo) {
            InlineVideoPlaybackButton(
                isPlaying = isInlineVideoActive && !isInlineVideoPaused,
                onClick = onToggleInlineVideo,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 6.dp),
            )
        }

        if (showSelectionVideoMarker) {
            InlineVideoPlaybackButton(
                isPlaying = false,
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 6.dp),
            )
        }

        if (item.type == SystemMediaType.VIDEO) {
            VideoDurationBadge(
                durationMillis = item.gridVideoBadgeDurationMillis(inlineVideoProgress),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp),
            )
        }

        if (selectionMode) {
            if (selectionHotspotOnly) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(46.dp)
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    SystemMediaSelectionBadge(
                        selected = selected,
                        modifier = Modifier.padding(end = 2.dp, bottom = 2.dp),
                    )
                }
            } else {
                SystemMediaSelectionBadge(
                    selected = selected,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 2.dp, bottom = 2.dp),
                )
            }
        }
        SelectionNumberFlashOverlay(
            flash = selectionFlash,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

private fun SystemMediaItem.gridVideoBadgeDurationMillis(
    progress: InlineVideoPlaybackProgress?,
): Long? {
    val totalMillis = progress?.durationMillis ?: videoDurationMillis
    if (totalMillis == null || totalMillis <= 0L) return null
    val positionMillis = progress?.positionMillis ?: 0L
    return (totalMillis - positionMillis).coerceIn(0L, totalMillis)
}

@Composable
private fun SystemMediaBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SystemMediaTypeBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = Color.Black.copy(alpha = 0.20f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

@Composable
private fun SystemMediaSelectionBar(
    selectedCount: Int,
    onImportToApp: () -> Unit,
    onCreatePost: () -> Unit,
    onAddToPost: () -> Unit,
    onMoveToTrash: () -> Unit,
    onCancel: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = if (selectedCount > 0) "已选 $selectedCount 项" else "请选择媒体",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    SystemMediaActionChip(
                        text = "导入app",
                        emphasized = true,
                        modifier = Modifier.weight(1f),
                        onClick = onImportToApp,
                    )
                    SystemMediaActionChip(
                        text = "发成新帖子",
                        emphasized = false,
                        modifier = Modifier.weight(1f),
                        onClick = onCreatePost,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    SystemMediaActionChip(
                        text = "加入已有帖子",
                        emphasized = false,
                        modifier = Modifier.weight(1f),
                        onClick = onAddToPost,
                    )
                    SystemMediaActionChip(
                        text = "移到系统回收站",
                        emphasized = false,
                        modifier = Modifier.weight(1f),
                        onClick = onMoveToTrash,
                    )
                }
                SystemMediaActionChip(
                    text = "取消",
                    emphasized = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCancel,
                )
            }
        }
    }
}

@Composable
private fun SystemMediaActionChip(
    text: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (emphasized) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            },
        ),
    ) {
        TextButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = if (emphasized) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun SystemMediaCircleButton(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SystemMediaSelectionBadge(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val blueColor = Color(0xFF3B82F6)
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(
                if (selected) blueColor else Color.Black.copy(alpha = 0.10f),
            )
            .border(
                width = 1.5.dp,
                color = if (selected) blueColor else Color.White.copy(alpha = 0.88f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun SelectionNumberFlashOverlay(
    flash: SelectionNumberFlash?,
    modifier: Modifier = Modifier,
) {
    if (flash == null) return
    val alpha = remember(flash.nonce) { Animatable(0f) }
    LaunchedEffect(flash.nonce) {
        alpha.snapTo(0f)
        alpha.animateTo(1f, animationSpec = tween(durationMillis = 300))
        delay(800)
        alpha.animateTo(0f, animationSpec = tween(durationMillis = 500))
    }

    if (alpha.value > 0f) {
        Box(
            modifier = modifier
                .alpha(alpha.value)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF202124).copy(alpha = 0.72f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = flash.number.toString(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                ),
                color = Color.White,
            )
        }
    }
}

private sealed interface SystemMediaGridBlock {
    val key: String

    data class MonthHeader(
        override val key: String,
        val title: String,
    ) : SystemMediaGridBlock

    data class DayHeader(
        override val key: String,
        val title: String,
    ) : SystemMediaGridBlock

    data class Media(
        override val key: String,
        val item: SystemMediaItem,
    ) : SystemMediaGridBlock
}

private data class SystemMediaRowMapping(
    val mediaToRow: Map<String, String>,
    val mediaToColumn: Map<String, Int>,
    val rowToMedia: Map<String, List<String>>,
    val rowKeys: List<String>,
) {
    private val rowKeyToIndex: Map<String, Int> = rowKeys
        .mapIndexed { index, rowKey -> rowKey to index }
        .toMap()

    fun rowIndexForMedia(mediaId: String): Int {
        return mediaToRow[mediaId]?.let { rowKey -> rowKeyToIndex[rowKey] } ?: -1
    }
}

@Composable
private fun SystemMediaMonthHeaderRow(title: String) {
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 10.dp),
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun SystemMediaDayHeaderRow(title: String) {
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SystemMediaTimeScrubber(
    progress: Float,
    label: String,
    showLabel: Boolean,
    onSeekToProgress: (Float) -> Unit,
    onInteractingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val spacing = YingShiThemeTokens.spacing
    val thumbWidth = 22.dp
    val thumbHeight = 76.dp
    val endMargin = 8.dp

    var scrubberHeightPx by remember { mutableIntStateOf(0) }
    var labelHeightPx by remember { mutableIntStateOf(0) }
    var scrubberLabelWidthPx by remember { mutableIntStateOf(0) }
    var lastDispatchedProgress by remember { mutableStateOf(Float.NaN) }
    var dragStartProgress by remember { mutableStateOf(0f) }
    var dragAccumulatedPx by remember { mutableStateOf(0f) }
    val latestProgress by rememberUpdatedState(progress.coerceIn(0f, 1f))
    val latestOnSeekToProgress by rememberUpdatedState(onSeekToProgress)
    val latestOnInteractingChanged by rememberUpdatedState(onInteractingChanged)
    val thumbWidthPx = with(density) { thumbWidth.roundToPx() }
    val thumbHeightPx = with(density) { thumbHeight.roundToPx() }
    val endMarginPx = with(density) { endMargin.roundToPx() }
    val labelGapPx = with(density) { 12.dp.roundToPx() }
    val travelHeightPx = (scrubberHeightPx - thumbHeightPx).coerceAtLeast(1)
    val normalizedProgress = progress.coerceIn(0f, 1f)
    val thumbTopPx = (travelHeightPx * normalizedProgress).roundToInt()
    val labelTopPx = (thumbTopPx + (thumbHeightPx / 2) - (labelHeightPx / 2))
        .coerceIn(0, (scrubberHeightPx - labelHeightPx).coerceAtLeast(0))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { scrubberHeightPx = it.height },
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = showLabel && label.isNotBlank(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(x = -(scrubberLabelWidthPx + thumbWidthPx + endMarginPx + labelGapPx), y = labelTopPx) },
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.96f),
            ) {
                Text(
                    text = label,
                    modifier = Modifier
                        .padding(horizontal = spacing.md, vertical = 8.dp)
                        .onSizeChanged {
                            scrubberLabelWidthPx = it.width
                            labelHeightPx = it.height
                        },
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    color = Color.Black,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(x = -endMarginPx, y = thumbTopPx) }
                .size(width = thumbWidth, height = thumbHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.96f))
                .pointerInput(scrubberHeightPx) {
                    detectDragGestures(
                        onDragStart = {
                            latestOnInteractingChanged(true)
                            dragStartProgress = latestProgress
                            dragAccumulatedPx = 0f
                            lastDispatchedProgress = Float.NaN
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragAccumulatedPx += dragAmount.y
                            val nextProgress = (dragStartProgress + (dragAccumulatedPx / travelHeightPx.toFloat()))
                                .coerceIn(0f, 1f)
                            if (!lastDispatchedProgress.isNaN() && abs(lastDispatchedProgress - nextProgress) < 0.005f) {
                                return@detectDragGestures
                            }
                            lastDispatchedProgress = nextProgress
                            latestOnSeekToProgress(nextProgress)
                        },
                        onDragEnd = {
                            lastDispatchedProgress = Float.NaN
                            latestOnInteractingChanged(false)
                        },
                        onDragCancel = {
                            lastDispatchedProgress = Float.NaN
                            latestOnInteractingChanged(false)
                        },
                    )
                },
        ) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .size(8.dp),
            ) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(w / 2f, 0f)
                    lineTo(0f, h)
                    lineTo(w, h)
                    close()
                }
                drawPath(path, color = Color.Black.copy(alpha = 0.7f))
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = 14.dp, height = 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Gray.copy(alpha = 0.55f)),
            )

            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .size(8.dp),
            ) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(w / 2f, h)
                    close()
                }
                drawPath(path, color = Color.Black.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun SystemMediaLoadingState(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = "正在读取本地媒体…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SystemMediaErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry) {
                    Text(text = "重试")
                }
            }
        }
    }
}

@Composable
private fun SystemMediaEmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun List<String>.toggleSystemMediaId(id: String): List<String> {
    return if (contains(id)) {
        filterNot { it == id }
    } else {
        this + id
    }
}

private fun calculateSystemMediaScrollProgress(
    gridState: LazyGridState,
    blocks: List<SystemMediaGridBlock>,
    itemCount: Int,
): Float {
    if (itemCount <= 1) return 0f
    val layoutInfo = gridState.layoutInfo
    val firstVisible = layoutInfo.visibleItemsInfo.firstOrNull() ?: return 0f
    val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(1)
    val visibleMediaCount = layoutInfo.visibleItemsInfo
        .count { visible -> blocks.getOrNull(visible.index) is SystemMediaGridBlock.Media }
        .coerceAtLeast(1)
    val scrollableStart = (itemCount - visibleMediaCount).coerceAtLeast(1)
    val firstMediaOrdinal = blocks
        .take((firstVisible.index + 1).coerceAtMost(blocks.size))
        .count { it is SystemMediaGridBlock.Media }
        .coerceAtLeast(1) - 1
    val offsetFraction = ((-firstVisible.offset.y).toFloat() / maxOf(firstVisible.size.height, viewportHeight).toFloat())
        .coerceIn(0f, 1f)
    return ((firstMediaOrdinal + offsetFraction) / scrollableStart.toFloat())
        .coerceIn(0f, 1f)
}

private fun visibleSystemMediaVideoIds(
    gridState: LazyGridState,
    gridBlocks: List<SystemMediaGridBlock>,
): Set<String> {
    return gridState.layoutInfo.visibleItemsInfo
        .mapNotNull { visible ->
            val block = gridBlocks.getOrNull(visible.index) as? SystemMediaGridBlock.Media
            block?.item?.takeIf { it.type == SystemMediaType.VIDEO }?.id
        }
        .toSet()
}

private fun centeredSystemMediaVideoId(
    gridState: LazyGridState,
    gridBlocks: List<SystemMediaGridBlock>,
): String? {
    val layoutInfo = gridState.layoutInfo
    if (layoutInfo.visibleItemsInfo.isEmpty()) return null
    val viewportCenterX = layoutInfo.viewportSize.width / 2f
    val viewportCenterY = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f

    return layoutInfo.visibleItemsInfo
        .mapNotNull { visible ->
            val block = gridBlocks.getOrNull(visible.index) as? SystemMediaGridBlock.Media
                ?: return@mapNotNull null
            val item = block.item.takeIf { it.type == SystemMediaType.VIDEO } ?: return@mapNotNull null
            val centerX = visible.offset.x + visible.size.width / 2f
            val centerY = visible.offset.y + visible.size.height / 2f
            val score = abs(centerX - viewportCenterX) + abs(centerY - viewportCenterY)
            item.id to score
        }
        .minByOrNull { it.second }
        ?.first
}

private fun SystemMediaItem.toSystemMediaScrubberLabel(): String {
    return SimpleDateFormat("yyyy.MM.dd", Locale.CHINA).format(Date(displayTimeMillis))
}

private fun buildSystemMediaGridBlocks(items: List<SystemMediaItem>): List<SystemMediaGridBlock> {
    if (items.isEmpty()) return emptyList()
    val monthFormat = SimpleDateFormat("yyyy年M月", Locale.CHINA)
    val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.CHINA)
    val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    val blocks = mutableListOf<SystemMediaGridBlock>()
    var lastMonthKey: String? = null
    var lastDayKey: String? = null

    items.forEach { item ->
        val date = Date(item.displayTimeMillis)
        val monthKey = monthKeyFormat.format(date)
        if (monthKey != lastMonthKey) {
            blocks += SystemMediaGridBlock.MonthHeader(
                key = "system-month-$monthKey",
                title = monthFormat.format(date),
            )
            lastMonthKey = monthKey
            lastDayKey = null
        }
        val dayKey = dayKeyFormat.format(date)
        if (dayKey != lastDayKey) {
            blocks += SystemMediaGridBlock.DayHeader(
                key = "system-day-$dayKey",
                title = formatSystemMediaDayHeader(item.displayTimeMillis),
            )
            lastDayKey = dayKey
        }
        blocks += SystemMediaGridBlock.Media(
            key = "system-media-${item.id}",
            item = item,
        )
    }
    return blocks
}

private fun resolveCurrentSystemMediaVisibleLabel(
    itemIndex: Int,
    blocks: List<SystemMediaGridBlock>,
    fallbackItems: List<SystemMediaItem>,
): String {
    if (blocks.isEmpty()) {
        return fallbackItems.firstOrNull()?.toSystemMediaScrubberLabel().orEmpty()
    }
    val safeIndex = itemIndex.coerceIn(0, blocks.lastIndex)
    val nextMedia = blocks
        .drop(safeIndex)
        .firstOrNull { it is SystemMediaGridBlock.Media } as? SystemMediaGridBlock.Media
    val previousMedia = blocks
        .take(safeIndex + 1)
        .lastOrNull { it is SystemMediaGridBlock.Media } as? SystemMediaGridBlock.Media
    return nextMedia?.item?.toSystemMediaScrubberLabel()
        ?: previousMedia?.item?.toSystemMediaScrubberLabel()
        ?: fallbackItems.firstOrNull()?.toSystemMediaScrubberLabel()
        ?: ""
}

private fun formatSystemMediaDayHeader(timeMillis: Long): String {
    val target = Calendar.getInstance(Locale.CHINA).apply {
        timeInMillis = timeMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val today = Calendar.getInstance(Locale.CHINA).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val yesterday = today.clone() as Calendar
    yesterday.add(Calendar.DAY_OF_YEAR, -1)
    return when (target.timeInMillis) {
        today.timeInMillis -> "今天"
        yesterday.timeInMillis -> "昨天"
        else -> SimpleDateFormat("M月d日", Locale.CHINA).format(Date(timeMillis))
    }
}

@Preview(showBackground = true)
@Composable
private fun SystemMediaEmptyStatePreview() {
    YingShiTheme {
        SystemMediaEmptyState(text = "当前没有可显示的本地媒体。")
    }
}
