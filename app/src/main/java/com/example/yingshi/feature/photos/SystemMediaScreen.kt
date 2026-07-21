package com.example.yingshi.feature.photos

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshi.feature.sync.SyncVersionTracker
import com.example.yingshi.ui.components.YingShiNotice
import com.example.yingshi.ui.components.YingShiNoticeHost
import com.example.yingshi.ui.components.YingShiNoticeTone
import com.example.yingshi.ui.components.rememberYingShiMotionEnabled
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val InitialSystemMediaRenderCount = 120
private const val SystemMediaRenderPageSize = 90

private fun systemMediaInitialRenderCount(density: PhotoFeedDensity): Int {
    return when (density) {
        PhotoFeedDensity.OVERVIEW_16 -> 640
        PhotoFeedDensity.OVERVIEW_8 -> 360
        else -> InitialSystemMediaRenderCount
    }
}

private fun systemMediaRenderPageSize(density: PhotoFeedDensity): Int {
    return when (density) {
        PhotoFeedDensity.OVERVIEW_16 -> 512
        PhotoFeedDensity.OVERVIEW_8 -> 240
        else -> SystemMediaRenderPageSize
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val backendMutationEvent by RealBackendMutationBus.latestEvent.collectAsState()
    val syncStaleState by SyncVersionTracker.staleState.collectAsState()
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
    var showAlbumSheet by rememberSaveable { mutableStateOf(false) }
    var addToPostError by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingImportPreview by remember {
        mutableStateOf<SystemMediaImportPreview?>(null)
    }
    var pendingTrashIds by rememberSaveable {
        mutableStateOf(emptyList<String>())
    }
    var notice by remember { mutableStateOf<YingShiNotice?>(null) }
    var noticeNonce by remember { mutableIntStateOf(0) }
    var densityName by rememberSaveable {
        mutableStateOf(
            LocalSystemMediaPageStateStore.savedDensityName ?: PhotoFeedDensity.COMFORT_3.name,
        )
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
    var scrubberScrollJob by remember { mutableStateOf<Job?>(null) }
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val coroutineScope = rememberCoroutineScope()
    val settingsState = SettingsRepository.getSettingsState()
    LaunchedEffect(densityName) {
        LocalSystemMediaPageStateStore.savedDensityName = densityName
    }
    val density = PhotoFeedDensity.valueOf(densityName)
    val motion = YingShiThemeTokens.motion
    val gridEnterAlpha = remember { Animatable(1f) }
    LaunchedEffect(densityName) {
        gridEnterAlpha.snapTo(0f)
        gridEnterAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = motion.densityPreviewMillis + motion.densitySettleMillis,
                easing = motion.easing,
            ),
        )
    }
    val densityWarmWindow = remember(density) { systemMediaThumbnailWarmWindow(density) }
    val densityWarmBatchSize = remember(density) { systemMediaThumbnailWarmBatchSize(density) }
    val initialRenderCount = remember(density, uiState.filteredItems.size) {
        if (density == PhotoFeedDensity.OVERVIEW_16) {
            uiState.filteredItems.size
        } else {
            systemMediaInitialRenderCount(density)
        }
    }
    val renderPageSize = remember(density) { systemMediaRenderPageSize(density) }
    var renderedCount by rememberSaveable(uiState.selectedFilter, densityName) {
        mutableIntStateOf(initialRenderCount)
    }
    val thumbnailRequestSize = remember(density) { systemMediaThumbnailRequestSize(density) }
    fun showNotice(
        message: String,
        tone: YingShiNoticeTone = YingShiNoticeTone.INFO,
    ) {
        noticeNonce += 1
        notice = YingShiNotice(message = message, tone = tone, nonce = noticeNonce)
    }

    val inlineVideoAutoPlayAllowed = inlineVideoAutoPlayEnabled &&
        !selectionMode &&
        density.columns <= 4
    val selectedIdSet = selectedIds.toSet()
    val selectedItems = uiState.filteredItems.filter { selectedIdSet.contains(it.id) }
    val spacingPx = with(LocalDensity.current) { 2.dp.toPx() }
    val visibleItems by remember(uiState.filteredItems, renderedCount, density) {
        derivedStateOf {
            if (density == PhotoFeedDensity.OVERVIEW_16) {
                uiState.filteredItems
            } else {
                uiState.filteredItems.take(renderedCount.coerceAtMost(uiState.filteredItems.size))
            }
        }
    }
    val gridBlocks = remember(visibleItems, density) {
        buildSystemMediaGridBlocks(visibleItems, density)
    }
    val scrubberTargetBlockIndexByMediaId = remember(gridBlocks) {
        buildSystemMediaScrubberTargetIndexMap(gridBlocks)
    }
    val visibleItemIndexById = remember(visibleItems) {
        visibleItems.mapIndexed { index, item -> item.id to index }.toMap()
    }
    SystemMediaThumbnailWarmer(
        gridState = gridState,
        gridBlocks = gridBlocks,
        visibleItems = visibleItems,
        thumbnailRequestSize = thumbnailRequestSize,
        warmWindow = densityWarmWindow,
        warmBatchSize = densityWarmBatchSize,
        scrubberInteracting = scrubberInteracting,
        visibleItemIndexById = visibleItemIndexById,
    )
    var densityGhost by remember { mutableStateOf<SystemMediaDensityGhost?>(null) }
    val updateDensity = remember(density, gridBlocks, gridState) {
        { nextDensity: PhotoFeedDensity ->
            if (nextDensity != density) {
                densityGhost = SystemMediaDensityGhost(
                    density = density,
                    blocks = gridBlocks,
                    firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
                    nonce = System.nanoTime(),
                )
                densityName = nextDensity.name
            }
        }
    }
    val inlineVideoController = rememberSystemMediaInlineVideoController(
        gridState = gridState,
        gridBlocks = gridBlocks,
        inlineVideoAutoPlayAllowed = inlineVideoAutoPlayAllowed,
    )
    val activeInlineVideoId = inlineVideoController.activeInlineVideoId
    val playingInlineVideoId = inlineVideoController.playingInlineVideoId
    val pausedInlineVideoIds = inlineVideoController.pausedInlineVideoIds
    val inlineVideoProgressById = inlineVideoController.inlineVideoProgressById
    val onToggleInlineVideo = inlineVideoController.onToggleInlineVideo
    val systemRowMapping = remember(gridBlocks, density.columns) {
        buildSystemMediaRowMapping(gridBlocks, density.columns)
    }
    val gridEdgePadding = systemMediaGridEdgePadding(density)
    val edgePaddingPx = with(LocalDensity.current) { gridEdgePadding.toPx() }
    val hitTestAdapter = rememberSystemMediaHitTestAdapter(
        gridState = gridState,
        gridBlocks = gridBlocks,
        systemRowMapping = systemRowMapping,
        columns = density.columns,
        spacingPx = spacingPx,
        edgePaddingPx = edgePaddingPx,
    )
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
    val scrubberYearMarkers = remember(uiState.filteredItems) {
        buildSystemMediaScrubberYearMarkers(uiState.filteredItems)
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
    SystemMediaPendingScrollHandler(
        scrollTrigger = scrollTrigger,
        filteredItems = uiState.filteredItems,
        density = density,
        visibleItems = visibleItems,
        gridBlocks = gridBlocks,
        gridState = gridState,
        renderPageSize = renderPageSize,
        renderedCount = renderedCount,
        onRenderedCountChange = { renderedCount = it },
    )

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
                    viewModel.refresh(forceRefresh = true)
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
            invalidateSystemMediaMetadataCache(context, clearDisk = true)
            selectionMode = false
            selectedIds = emptyList()
            viewModel.refresh(forceRefresh = true)
            showNotice(
                message = if (hiddenCount > 0) {
                    "已移到系统回收站。"
                } else {
                    "这些媒体已经处理过了。"
                },
                tone = if (hiddenCount > 0) YingShiNoticeTone.SUCCESS else YingShiNoticeTone.INFO,
            )
        } else {
            showNotice("已取消移到系统回收站。")
        }
    }

    fun launchSystemTrashRequest(items: List<SystemMediaItem>) {
        if (items.isEmpty()) {
            showNotice("请先选择要移到系统回收站的媒体。", YingShiNoticeTone.WARNING)
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
                    showNotice(
                        throwable.message ?: "无法拉起系统回收站确认流程。",
                        YingShiNoticeTone.WARNING,
                    )
                }
            }
            .onFailure { throwable ->
                showNotice(
                    throwable.message ?: systemMediaTrashUnsupportedMessage(),
                    YingShiNoticeTone.WARNING,
                )
            }
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission && !permissionRequestedOnce) {
            permissionRequestedOnce = true
            permissionLauncher.launch(requiredSystemMediaPermissions())
        } else if (hasPermission) {
            viewModel.refresh(forceRefresh = true)
        }
    }

    LaunchedEffect(uiState.selectedFilter, uiState.filteredItems.size, initialRenderCount) {
        renderedCount = initialRenderCount.coerceAtMost(uiState.filteredItems.size)
    }

    LaunchedEffect(bridgeMutationEvent.version) {
        viewModel.handleBridgeMutation(bridgeMutationEvent)
    }

    LaunchedEffect(backendMutationEvent.version) {
        if (backendMutationEvent.version > 0 &&
            (
                backendMutationEvent.affectsPhotoFeed() ||
                    backendMutationEvent.affectsAlbums() ||
                    backendMutationEvent.affectsTrash() ||
                    backendMutationEvent.affectsSystemMediaDestinations()
                )
        ) {
            viewModel.refresh(forceRefresh = true)
        }
    }

    LaunchedEffect(hasPermission) {
        snapshotFlow { syncStaleState.photoFeedStale || syncStaleState.trashStale }
            .collect { shouldRefreshImportStatus ->
                if (hasPermission && shouldRefreshImportStatus) {
                    invalidateSystemMediaMetadataCache(context)
                    viewModel.refresh(forceRefresh = true)
                }
            }
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
            if (density != PhotoFeedDensity.OVERVIEW_16 &&
                shouldLoadMore &&
                renderedCount < uiState.filteredItems.size
            ) {
                renderedCount = (renderedCount + renderPageSize)
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
            isLoading = destinationUiState.isLoading,
            errorMessage = addToPostError ?: destinationUiState.errorMessage,
            onDismiss = {
                showAddToPostDialog = false
                addToPostError = null
            },
            onPostSelected = { postId ->
                val addedCount = LocalSystemMediaBridgeRepository.enqueueAddToExistingPostUpload(
                    context = context,
                    postId = postId,
                    mediaItems = selectedItems,
                )
                if (addedCount > 0) {
                    showAddToPostDialog = false
                    addToPostError = null
                    selectedIds = emptyList()
                    selectionMode = false
                    showNotice(
                        "已加入上传队列，成功项会进入目标小相册。",
                        YingShiNoticeTone.SUCCESS,
                    )
                } else {
                    addToPostError = "这些媒体已经在目标小相册里，或没有可添加的媒体。"
                }
            },
            onPostChosen = { post ->
                val addedCount = LocalSystemMediaBridgeRepository.enqueueAddToExistingPostUpload(
                    context = context,
                    postId = post.id,
                    mediaItems = selectedItems,
                    postTitle = post.title,
                )
                if (addedCount > 0) {
                    showAddToPostDialog = false
                    addToPostError = null
                    selectedIds = emptyList()
                    selectionMode = false
                    showNotice(
                        "已加入上传队列，成功项会进入目标小相册。",
                        YingShiNoticeTone.SUCCESS,
                    )
                } else {
                    addToPostError = "这些媒体已经在目标小相册里，或没有可添加的媒体。"
                }
            },
        )
    }

    pendingImportPreview?.let { preview ->
        SystemMediaImportPreviewDialog(
            preview = preview,
            timePreferenceLabel = settingsState.mediaTimePreference.label,
            onDismiss = { pendingImportPreview = null },
            onConfirmImport = {
                val previewSnapshot = pendingImportPreview ?: return@SystemMediaImportPreviewDialog
                if (!previewSnapshot.hasImportableItems) {
                    pendingImportPreview = null
                    return@SystemMediaImportPreviewDialog
                }
                val importedCount = LocalSystemMediaBridgeRepository.enqueueImportToAppUpload(
                    context = context,
                    mediaItems = previewSnapshot.importableItems,
                )
                pendingImportPreview = null
                if (importedCount > 0) {
                    selectedIds = emptyList()
                    selectionMode = false
                    showNotice("已加入导入队列，完成后会出现在照片流。", YingShiNoticeTone.SUCCESS)
                } else {
                    showNotice("这些媒体已经在导入队列里，或没有可导入的媒体。", YingShiNoticeTone.WARNING)
                }
            },
        )
    }

    if (showAlbumSheet) {
        SystemMediaAlbumSheet(
            albums = uiState.albums,
            selectedAlbum = uiState.selectedAlbum,
            onDismiss = { showAlbumSheet = false },
            onAlbumSelected = { album ->
                viewModel.onAlbumSelected(album)
                showAlbumSheet = false
            },
        )
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = {
            invalidateSystemMediaMetadataCache(context, clearDisk = true)
            viewModel.refresh(forceRefresh = true)
        },
        modifier = modifier
            .fillMaxSize()
            .background(colors.appBackground),
    ) {
        SystemMediaAtmosphereLayer(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 10.dp, bottom = spacing.md),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SystemMediaTopBar(
                    selectedFilter = uiState.selectedFilter,
                    selectionMode = selectionMode,
                    selectedCount = selectedIds.size,
                    totalCount = uiState.filteredItems.size,
                    selectedAlbum = uiState.selectedAlbum,
                    onBack = onBack,
                    onFilterSelected = viewModel::onFilterSelected,
                    onRefresh = {
                        invalidateSystemMediaMetadataCache(context, clearDisk = true)
                        viewModel.refresh(forceRefresh = true)
                    },
                    onOpenAlbums = { showAlbumSheet = true },
                    isManualRefreshing = uiState.isRefreshing,
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
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = gridEnterAlpha.value },
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            contentPadding = PaddingValues(
                                start = gridEdgePadding,
                                end = gridEdgePadding,
                                top = 2.dp,
                                bottom = 112.dp,
                            ),
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
                                        val cardModifier = if (density == PhotoFeedDensity.OVERVIEW_16) {
                                            Modifier
                                        } else {
                                            Modifier.animateItem()
                                        }
                                        SystemMediaCard(
                                            item = item,
                                            modifier = cardModifier,
                                            density = density,
                                            thumbnailRequestSize = thumbnailRequestSize,
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
                                                inlineVideoController.onInlineVideoProgressChange(item.id, progress)
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        densityGhost?.let { ghost ->
                            SystemMediaDensityGhostOverlay(
                                ghost = ghost,
                                gridEdgePadding = systemMediaGridEdgePadding(ghost.density),
                                onFinished = {
                                    if (densityGhost?.nonce == ghost.nonce) {
                                        densityGhost = null
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = scrubberVisible && uiState.filteredItems.size > 1,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(184.dp),
                            ) {
                                PhotoFeedTimeScrubber(
                                    modifier = Modifier.matchParentSize(),
                                    progress = displayedScrubberProgress,
                                    label = displayedScrubberLabel,
                                    showLabel = scrubberInteracting,
                                    yearMarkers = scrubberYearMarkers,
                                    onSeekToProgress = { progress ->
                                        val targetIndex = (progress * (uiState.filteredItems.lastIndex).coerceAtLeast(0))
                                            .roundToInt()
                                            .coerceIn(0, (uiState.filteredItems.size - 1).coerceAtLeast(0))
                                        scrubberDragProgress = progress.coerceIn(0f, 1f)
                                        val targetItem = uiState.filteredItems.getOrNull(targetIndex)
                                        scrubberDragLabel = targetItem?.toSystemMediaScrubberLabel().orEmpty()
                                        val scrubberStep = systemMediaScrubberIndexStep(density)
                                        if (
                                            targetIndex == lastRequestedScrubberIndex ||
                                            lastRequestedScrubberIndex >= 0 &&
                                            abs(targetIndex - lastRequestedScrubberIndex) < scrubberStep
                                        ) {
                                            return@PhotoFeedTimeScrubber
                                        }
                                        lastRequestedScrubberIndex = targetIndex
                                        val targetBlockIndex = targetItem
                                            ?.let { item -> scrubberTargetBlockIndexByMediaId[item.id] }
                                            ?.takeIf { it >= 0 }
                                            ?: 0
                                        scrubberScrollJob?.cancel()
                                        scrubberScrollJob = coroutineScope.launch {
                                            gridState.scrollToItem(
                                                index = targetBlockIndex,
                                                scrollOffset = calculatePhotoFeedLikeSystemScrubberScrollOffset(gridState),
                                            )
                                        }
                                    },
                                    onInteractingChanged = { interacting ->
                                        scrubberInteracting = interacting
                                        if (interacting) {
                                            scrubberDragProgress = currentScrollProgress
                                            scrubberDragLabel = currentScrubberLabel
                                            if (density != PhotoFeedDensity.OVERVIEW_16 &&
                                                renderedCount < uiState.filteredItems.size
                                            ) {
                                                renderedCount = uiState.filteredItems.size
                                            }
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
                    if (selectedItems.isEmpty()) {
                        showNotice("请先选择要导入照片流的媒体。", YingShiNoticeTone.WARNING)
                        return@SystemMediaSelectionBar
                    }
                    pendingImportPreview = LocalSystemMediaBridgeRepository.buildImportToAppPreview(
                        selectedItems,
                    )
                },
                onCreatePost = {
                    val selectedSnapshot = selectedItems
                    if (selectedSnapshot.isEmpty()) {
                        showNotice("请先选择要新建小相册的媒体。", YingShiNoticeTone.WARNING)
                    } else {
                        selectedIds = emptyList()
                        selectionMode = false
                        onOpenCreatePost(
                            CreatePostRoute(
                                source = "system-media-selection",
                                initialMediaItems = selectedSnapshot,
                            ),
                        )
                    }
                },
                onAddToPost = {
                    if (selectedItems.isEmpty()) {
                        showNotice("请先选择要加入小相册的媒体。", YingShiNoticeTone.WARNING)
                        return@SystemMediaSelectionBar
                    }
                    addToPostError = null
                    val destinationError = destinationUiState.errorMessage
                    if (destinationError != null && posts.isEmpty()) {
                        showNotice(destinationError, YingShiNoticeTone.WARNING)
                    } else {
                        showAddToPostDialog = true
                    }
                },
                onMoveToTrash = {
                    if (selectedItems.isEmpty()) {
                        showNotice("请先选择要移到系统回收站的媒体。", YingShiNoticeTone.WARNING)
                        return@SystemMediaSelectionBar
                    }
                    launchSystemTrashRequest(selectedItems)
                },
            )
        }

        YingShiNoticeHost(
            notice = notice,
            onExpired = { nonce ->
                if (notice?.nonce == nonce) {
                    notice = null
                }
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = spacing.md),
        )

    }
}

