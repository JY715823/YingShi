package com.example.yingshi.feature.photos
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.components.rememberYingShiMotionEnabled
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.components.yingShiMemoryGlow
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val PhotoFeedLeadingItemCount = 0
private const val PhotoFeedPendingTargetRefreshGraceMillis = 450L
private const val PhotoFeedNewImportBadgeMillis = 12_000L

@Composable
fun PhotoFeedScreen(
    feedItems: List<PhotoFeedItem> = FakePhotoFeedRepository.getPhotoFeed(),
    modifier: Modifier = Modifier,
    pageStateStore: PhotoFeedPageStateStore = GlobalPhotoFeedPageStateStore,
    selectionState: PhotoFeedSelectionState = PhotoFeedSelectionState(),
    bottomOverlayPadding: Dp = 0.dp,
    isLoadingMore: Boolean = false,
    hasMore: Boolean = false,
    loadMoreErrorMessage: String? = null,
    onSelectionStateChange: (PhotoFeedSelectionState) -> Unit = { },
    onOpenViewer: (PhotoViewerRoute) -> Unit = { },
    onLoadMore: () -> Unit = { },
    onRetryLoadMore: () -> Unit = { },
    onShowNotice: (String) -> Unit = {},
    scrollTrigger: Int = 0,
    inlineVideoAutoPlayEnabled: Boolean = true,
    allowOpenMediaWhileSelecting: Boolean = true,
    disabledMediaIds: Set<String> = emptySet(),
    disabledSelectionLabel: String? = null,
) {
    val spacing = YingShiThemeTokens.spacing
    val settingsState = SettingsRepository.getSettingsState()
    val collaboratorDirectory = rememberCollaboratorDirectorySnapshot()
    val currentCollaboratorUserId = collaboratorDirectory.currentUser?.userId
    val partnerCollaboratorUserId = collaboratorDirectory.partner?.userId
    val allCollaboratorUserIds = remember(collaboratorDirectory) {
        collaboratorDirectory.all.mapTo(linkedSetOf()) { it.userId }
    }
    LaunchedEffect(allCollaboratorUserIds) {
        val normalizedSelection = initialOrNormalizedCollaboratorSelection(
            selectedUserIds = pageStateStore.selectedCollaboratorUserIds,
            allUserIds = allCollaboratorUserIds,
            initialized = pageStateStore.collaboratorSelectionInitialized,
        )
        if (normalizedSelection != pageStateStore.selectedCollaboratorUserIds) {
            pageStateStore.selectedCollaboratorUserIds = normalizedSelection
        }
        if (allCollaboratorUserIds.isNotEmpty()) {
            pageStateStore.collaboratorSelectionInitialized = true
        }
    }
    val selectedCollaboratorUserIds = remember(
        pageStateStore.selectedCollaboratorUserIds,
        allCollaboratorUserIds,
    ) {
        normalizeCollaboratorSelectionKeepingEmpty(
            selectedUserIds = pageStateStore.selectedCollaboratorUserIds,
            allUserIds = allCollaboratorUserIds,
        )
    }
    val timeBucketHours = pageStateStore.timeBucketHours
        .takeIf { it in CollaborativeBucketHoursOptions }
        ?: DefaultCollaborativeBucketHours
    val displayFeedItems = remember(
        feedItems,
        currentCollaboratorUserId,
        partnerCollaboratorUserId,
        selectedCollaboratorUserIds,
    ) {
        if (selectedCollaboratorUserIds.isEmpty()) {
            emptyList()
        } else {
            feedItems.filter { item ->
                resolveCollaborativeOwnerUserId(
                    item = item,
                    currentUserId = currentCollaboratorUserId,
                    partnerUserId = partnerCollaboratorUserId,
                ) in selectedCollaboratorUserIds
            }
        }
    }
    val mediaPositionLookup = remember(displayFeedItems) {
        displayFeedItems.mapIndexed { index, item -> item.mediaId to index }.toMap()
    }
    LaunchedEffect(displayFeedItems) {
        pageStateStore.visibleMediaIds = displayFeedItems.mapTo(linkedSetOf()) { it.mediaId }
    }
    var densityName by rememberSaveable { mutableStateOf<String?>(null) }
    var scrubberVisible by remember { mutableStateOf(false) }
    var scrubberInteracting by remember { mutableStateOf(false) }
    var scrubberDragProgress by remember { mutableStateOf<Float?>(null) }
    var scrubberDragLabel by remember { mutableStateOf("") }
    val liveSelectedIds = remember { mutableStateOf(selectionState.selectedMediaIds) }
    var selectionFlashNonce by remember { mutableIntStateOf(0) }
    var selectionFlashByMediaId by remember { mutableStateOf<Map<String, SelectionNumberFlash>>(emptyMap()) }
    var highlightedTargetMediaId by remember { mutableStateOf<String?>(null) }
    var highlightedTargetNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectionState.selectedMediaIds) {
        liveSelectedIds.value = selectionState.selectedMediaIds
    }

    LaunchedEffect(Unit) {
        if (densityName == null) {
            densityName = pageStateStore.savedDensityName ?: settingsState.defaultPhotoFeedDensity.name
        }
    }

    val density = PhotoFeedDensity.valueOf(
        densityName ?: settingsState.defaultPhotoFeedDensity.name,
    )
    LaunchedEffect(densityName) {
        pageStateStore.savedDensityName = densityName
    }
    val gridEdgePadding = rowSpacing(density)
    val thumbnailRequestSize = photoFeedThumbnailRequestSize(density)
    PrefetchPhotoFeedThumbnails(
        feedItems = displayFeedItems,
        density = density,
        requestSize = thumbnailRequestSize,
    )
    val inlineVideoAutoPlayAllowed = inlineVideoAutoPlayEnabled &&
        !selectionState.isInSelectionMode &&
        density.columns <= 4
    val blocks = remember(
        displayFeedItems,
        density,
        collaboratorDirectory,
        selectedCollaboratorUserIds,
        timeBucketHours,
    ) {
        buildCollaborativePhotoFeedBlocks(
            items = displayFeedItems,
            density = density,
            directory = collaboratorDirectory,
            selectedUserIds = selectedCollaboratorUserIds,
            timeBucketHours = timeBucketHours,
        )
    }
    val rowKeyToMediaIds = remember(blocks) {
        buildMap {
            blocks.filterIsInstance<PhotoFeedGridRow>().forEach { row ->
                put(row.key, row.items.map { it.mediaId })
            }
        }
    }
    val rowKeys = remember(blocks) {
        blocks.filterIsInstance<PhotoFeedGridRow>().map { it.key }
    }
    val rowKeyToIndex = remember(rowKeys) {
        rowKeys.mapIndexed { index, rowKey -> rowKey to index }.toMap()
    }
    val scrollAnchors = remember(blocks, density) {
        buildPhotoFeedScrubberAnchors(
            blocks = blocks,
            density = density,
            leadingItemCount = PhotoFeedLeadingItemCount,
        )
    }
    val scrubberYearMarkers = remember(scrollAnchors) {
        buildPhotoFeedScrubberYearMarkers(scrollAnchors)
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = pageStateStore.savedFirstVisibleItemIndex
            .coerceIn(0, blocks.lastIndex.coerceAtLeast(0)),
        initialFirstVisibleItemScrollOffset = pageStateStore.savedFirstVisibleItemScrollOffset,
    )
    val densityScope = LocalDensity.current
    val spacingPx = with(densityScope) { rowSpacing(density).toPx() }
    val edgePaddingPx = with(densityScope) { gridEdgePadding.toPx() }
    var manualInlineVideoId by remember { mutableStateOf<String?>(null) }
    var pausedInlineVideoIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var inlineVideoProgressById by remember { mutableStateOf<Map<String, InlineVideoPlaybackProgress>>(emptyMap()) }
    val visibleInlineVideoIds by remember(listState, blocks) {
        derivedStateOf {
            visiblePhotoFeedVideoIds(
                listState = listState,
                blocks = blocks,
            )
        }
    }
    val centeredInlineVideoId by remember(listState, blocks, density, spacingPx, edgePaddingPx) {
        derivedStateOf {
            centeredPhotoFeedVideoId(
                listState = listState,
                blocks = blocks,
                density = density,
                colSpacingPx = spacingPx,
                edgePaddingPx = edgePaddingPx,
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
        toggle@{ item: PhotoFeedItem ->
            if (!inlineVideoAutoPlayAllowed || item.mediaType != AppMediaType.VIDEO) {
                return@toggle
            }
            if (activeInlineVideoId == item.mediaId) {
                pausedInlineVideoIds = if (item.mediaId in pausedInlineVideoIds) {
                    pausedInlineVideoIds - item.mediaId
                } else {
                    pausedInlineVideoIds + item.mediaId
                }
            } else {
                manualInlineVideoId = item.mediaId
                pausedInlineVideoIds = pausedInlineVideoIds - item.mediaId
            }
        }
    }
    val hitTestAdapter = remember(
        listState,
        blocks,
        density,
        rowKeyToMediaIds,
        rowKeys,
        rowKeyToIndex,
        spacingPx,
        edgePaddingPx,
    ) {
        val colSpacingPx = spacingPx
        val horizontalEdgePaddingPx = edgePaddingPx
        MultiSelectHitTestAdapter(
            hitTest = { touchPos ->
                val layout = listState.layoutInfo
                val ty = touchPos.y.toInt()
                val tx = (touchPos.x - horizontalEdgePaddingPx).toInt()
                val viewportW = layout.viewportSize.width.coerceAtLeast(1)
                val contentW = (viewportW - horizontalEdgePaddingPx * 2f).coerceAtLeast(1f)
                val totalSpacing = (density.columns - 1) * colSpacingPx
                val cellWidth = ((contentW - totalSpacing) / density.columns).coerceAtLeast(1f)
                val segmentWidth = cellWidth + colSpacingPx
                for (item in layout.visibleItemsInfo) {
                    val itemEnd = item.offset + item.size
                    if (ty in item.offset until itemEnd) {
                        val block = blocks.getOrNull(item.index)
                        if (block == null) {
                            return@MultiSelectHitTestAdapter null
                        }
                        if (block !is PhotoFeedGridRow) {
                            return@MultiSelectHitTestAdapter null
                        }
                        val colIndex = (tx / segmentWidth).toInt().coerceIn(0, density.columns - 1)
                        val mediaItem = block.items.getOrNull(colIndex)
                        if (mediaItem == null) {
                            return@MultiSelectHitTestAdapter null
                        }
                        return@MultiSelectHitTestAdapter MultiSelectHitResult(
                            mediaId = mediaItem.mediaId,
                            rowKey = block.key,
                            rowIndex = rowKeyToIndex[block.key] ?: -1,
                            isSelectable = mediaItem.mediaId !in disabledMediaIds,
                            colIndex = colIndex,
                            columnsInRow = block.items.size,
                        )
                    }
                }
                null
            },
            mediaIdsInRow = { rowKey -> rowKeyToMediaIds[rowKey].orEmpty() },
            rowKeyAtIndex = { rowIndex -> rowKeys.getOrNull(rowIndex) },
        )
    }
    val coroutineScope = rememberCoroutineScope()
    var lastRequestedAnchorIndex by remember { mutableIntStateOf(-1) }
    val currentVisibleDateLabel by remember(listState, blocks, displayFeedItems) {
        derivedStateOf {
            resolveCurrentVisibleDateLabel(
                itemIndex = listState.firstVisibleItemIndex,
                blocks = blocks,
                fallbackItems = displayFeedItems,
            )
        }
    }
    val currentScrollProgress by remember(listState, scrollAnchors) {
        derivedStateOf {
            calculatePhotoFeedScrollProgress(
                listState = listState,
                anchorCount = scrollAnchors.size,
            )
        }
    }
    val displayedScrubberProgress = if (scrubberInteracting) {
        scrubberDragProgress ?: currentScrollProgress
    } else {
        currentScrollProgress
    }
    val displayedScrubberLabel = if (scrubberInteracting) {
        scrubberDragLabel.ifBlank { currentVisibleDateLabel }
    } else {
        currentVisibleDateLabel
    }
    val updateDensity = remember(density) {
        { nextDensity: PhotoFeedDensity ->
            if (nextDensity != density) {
                densityName = nextDensity.name
            }
        }
    }
    var pendingTargetLoadAttemptBlockCount by remember { mutableIntStateOf(-1) }
    var pendingTargetMediaIdSnapshot by remember { mutableStateOf<String?>(null) }
    var restoredSavedAnchorMediaId by remember { mutableStateOf<String?>(null) }
    var newImportedMediaIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var consumedNewImportedNonce by remember { mutableIntStateOf(0) }
    var restoredMediaIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var consumedRestoredNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(currentScrollProgress, scrubberInteracting, scrollAnchors.size) {
        if (scrollAnchors.size <= 1) {
            scrubberVisible = false
            return@LaunchedEffect
        }
        scrubberVisible = true
        if (!scrubberInteracting) {
            lastRequestedAnchorIndex = (currentScrollProgress * scrollAnchors.lastIndex)
                .roundToInt()
                .coerceIn(0, scrollAnchors.lastIndex)
            delay(900)
            if (!scrubberInteracting) {
                scrubberVisible = false
            }
        }
    }

    LaunchedEffect(pageStateStore.pendingNewImportedNonce) {
        val nonce = pageStateStore.pendingNewImportedNonce
        if (nonce == 0 || nonce == consumedNewImportedNonce) return@LaunchedEffect
        consumedNewImportedNonce = nonce
        val ids = pageStateStore.pendingNewImportedMediaIds
        if (ids.isEmpty()) return@LaunchedEffect
        newImportedMediaIds = ids
        delay(PhotoFeedNewImportBadgeMillis)
        if (consumedNewImportedNonce == nonce) {
            newImportedMediaIds = emptySet()
            pageStateStore.pendingNewImportedMediaIds = emptySet()
        }
    }

    LaunchedEffect(pageStateStore.pendingRestoredNonce) {
        val nonce = pageStateStore.pendingRestoredNonce
        if (nonce == 0 || nonce == consumedRestoredNonce) return@LaunchedEffect
        consumedRestoredNonce = nonce
        val ids = pageStateStore.pendingRestoredMediaIds
        if (ids.isEmpty()) return@LaunchedEffect
        restoredMediaIds = ids
        delay(PhotoFeedNewImportBadgeMillis)
        if (consumedRestoredNonce == nonce) {
            restoredMediaIds = emptySet()
            pageStateStore.pendingRestoredMediaIds = emptySet()
        }
    }

    LaunchedEffect(scrollTrigger, blocks) {
        val mediaId = pageStateStore.pendingScrollTargetMediaId ?: return@LaunchedEffect
        val highlightNonce = pageStateStore.pendingHighlightNonce
        val targetBlockIndex = findBlockIndexForMedia(blocks, mediaId)
        if (mediaId != pendingTargetMediaIdSnapshot) {
            pendingTargetMediaIdSnapshot = mediaId
            pendingTargetLoadAttemptBlockCount = -1
        }
        if (targetBlockIndex < 0) {
            if (!hasMore) {
                delay(PhotoFeedPendingTargetRefreshGraceMillis)
                if (pageStateStore.pendingScrollTargetMediaId != mediaId) {
                    return@LaunchedEffect
                }
                pageStateStore.pendingScrollTargetMediaId = null
                pageStateStore.pendingScrollAnchorOriginalIndex = -1
                pageStateStore.pendingLocateFailureMessage?.let { message ->
                    onShowNotice(message)
                }
                pageStateStore.pendingLocateSuccessMessage = null
                pageStateStore.pendingLocateFailureMessage = null
                pageStateStore.pendingImportHasRetryableItems = false
                pendingTargetMediaIdSnapshot = null
                pendingTargetLoadAttemptBlockCount = -1
                return@LaunchedEffect
            }
            if (!isLoadingMore && pendingTargetLoadAttemptBlockCount != blocks.size) {
                pendingTargetLoadAttemptBlockCount = blocks.size
                onLoadMore()
            }
            return@LaunchedEffect
        }
        val targetScrollOffset = calculatePhotoFeedTargetScrollOffset(listState)
        listState.scrollToItem(
            index = targetBlockIndex,
            scrollOffset = targetScrollOffset,
        )
        highlightedTargetMediaId = mediaId
        highlightedTargetNonce = highlightNonce
        restoredSavedAnchorMediaId = mediaId
        pageStateStore.savedFirstVisibleItemIndex = targetBlockIndex
        pageStateStore.savedFirstVisibleItemScrollOffset = targetScrollOffset
        pageStateStore.savedFirstVisibleMediaId = mediaId
        pageStateStore.pendingScrollTargetMediaId = null
        pageStateStore.pendingScrollAnchorOriginalIndex = -1
        pageStateStore.pendingLocateSuccessMessage?.let { message ->
            onShowNotice(message)
        }
        pageStateStore.pendingLocateSuccessMessage = null
        pageStateStore.pendingLocateFailureMessage = null
        pageStateStore.pendingImportHasRetryableItems = false
        pendingTargetMediaIdSnapshot = null
        pendingTargetLoadAttemptBlockCount = -1
    }

    LaunchedEffect(blocks) {
        if (pageStateStore.pendingScrollTargetMediaId != null) return@LaunchedEffect
        val savedMediaId = pageStateStore.savedFirstVisibleMediaId ?: return@LaunchedEffect
        if (savedMediaId == restoredSavedAnchorMediaId) return@LaunchedEffect
        val targetBlockIndex = findBlockIndexForMedia(blocks, savedMediaId)
        if (targetBlockIndex < 0) return@LaunchedEffect
        val currentIndex = listState.firstVisibleItemIndex
        if (abs(currentIndex - targetBlockIndex) <= 1) {
            restoredSavedAnchorMediaId = savedMediaId
            return@LaunchedEffect
        }
        restoredSavedAnchorMediaId = savedMediaId
        listState.scrollToItem(
            index = targetBlockIndex,
            scrollOffset = pageStateStore.savedFirstVisibleItemScrollOffset,
        )
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val firstVisible = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            val itemIndex = firstVisible?.index ?: listState.firstVisibleItemIndex
            val itemOffset = firstVisible?.offset ?: listState.firstVisibleItemScrollOffset
            val firstMediaId = (blocks.getOrNull(itemIndex) as? PhotoFeedGridRow)
                ?.items
                ?.firstOrNull()
                ?.mediaId
            PhotoFeedVisiblePosition(
                index = itemIndex,
                offset = itemOffset,
                mediaId = firstMediaId,
            )
        }.collect { (index, offset, mediaId) ->
            pageStateStore.savedFirstVisibleItemIndex = index
            pageStateStore.savedFirstVisibleItemScrollOffset = offset
            pageStateStore.savedFirstVisibleMediaId = mediaId
        }
    }

    LaunchedEffect(listState, blocks.size, hasMore, isLoadingMore, loadMoreErrorMessage) {
        snapshotFlow {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisibleIndex >= blocks.lastIndex - 3
        }.collect { shouldLoadMore ->
            if (shouldLoadMore && hasMore && !isLoadingMore && loadMoreErrorMessage == null) {
                onLoadMore()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(YingShiThemeTokens.colors.appBackground),
    ) {
        if (collaboratorDirectory.all.isNotEmpty()) {
            PhotoFeedCollaboratorControlsRow(
                directory = collaboratorDirectory,
                selectedUserIds = selectedCollaboratorUserIds,
                timeBucketHours = timeBucketHours,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.xs, vertical = spacing.xxs),
                onToggleCollaborator = { userId ->
                    pageStateStore.selectedCollaboratorUserIds = toggleCollaboratorSelectionKeepingEmpty(
                        currentSelection = selectedCollaboratorUserIds,
                        toggledUserId = userId,
                        allUserIds = allCollaboratorUserIds,
                    )
                    pageStateStore.collaboratorSelectionInitialized = true
                },
                onTimeBucketHoursChange = { nextHours ->
                    pageStateStore.timeBucketHours = nextHours
                },
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .discreteZoomLevelGesture(
                    enabled = !selectionState.isInSelectionMode,
                    levels = PhotoFeedDensity.entries.toList(),
                    currentLevel = density,
                    onLevelChange = updateDensity,
                )
                .multiSelectSwipeGesture(
                    enabled = selectionState.isInSelectionMode,
                    hitTestAdapter = hitTestAdapter,
                    selectedIds = liveSelectedIds.value,
                    onSelectionChange = { newIds ->
                        val hiddenSelectedIds = selectionState.selectedMediaIds - pageStateStore.visibleMediaIds
                        onSelectionStateChange(
                            PhotoFeedSelectionState(
                                selectedMediaIds = hiddenSelectedIds + newIds,
                                isInSelectionMode = true,
                            ),
                        )
                    },
                    onAutoScroll = { delta -> listState.scrollBy(delta) },
                ),
        ) {
            PhotoFeedAtmosphereLayer(modifier = Modifier.matchParentSize())

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(sectionSpacing(density)),
                contentPadding = PaddingValues(
                    top = 0.dp,
                    start = gridEdgePadding,
                    end = gridEdgePadding,
                    bottom = spacing.lg + bottomOverlayPadding,
                ),
            ) {
                items(
                    items = blocks,
                    key = { it.key },
                    contentType = { block ->
                        when (block) {
                            is PhotoFeedSectionHeader -> "section"
                            is PhotoFeedDayHeader -> "day"
                            is PhotoFeedTimeBucketHeader -> "time-bucket"
                            is PhotoFeedCollaboratorHeader -> "collaborator-header"
                            is PhotoFeedCollaboratorDivider -> "collaborator-divider"
                            is PhotoFeedGridRow -> "grid-${density.columns}"
                        }
                    },
                ) { block ->
                    when (block) {
                        is PhotoFeedSectionHeader -> PhotoFeedSectionHeaderRow(title = block.title)
                        is PhotoFeedDayHeader -> PhotoFeedDayHeaderRow(title = block.title)
                        is PhotoFeedTimeBucketHeader -> PhotoFeedTimeBucketHeaderRow(
                            header = block,
                            density = density,
                        )
                        is PhotoFeedCollaboratorHeader -> PhotoFeedCollaboratorHeaderRow(
                            identity = block.identity,
                        )
                        is PhotoFeedCollaboratorDivider -> PhotoFeedCollaboratorDividerRow()
                        is PhotoFeedGridRow -> PhotoFeedGridRowContent(
                            row = block,
                            density = density,
                            selectionState = selectionState,
                            disabledMediaIds = disabledMediaIds,
                            disabledSelectionLabel = disabledSelectionLabel,
                            selectionFlash = selectionFlashByMediaId,
                            highlightedMediaId = highlightedTargetMediaId,
                            highlightNonce = highlightedTargetNonce,
                            newImportedMediaIds = newImportedMediaIds,
                            restoredMediaIds = restoredMediaIds,
                            inlineVideoAutoPlayEnabled = inlineVideoAutoPlayAllowed,
                            allowOpenMediaWhileSelecting = allowOpenMediaWhileSelecting,
                            playingInlineVideoId = playingInlineVideoId,
                            activeInlineVideoId = activeInlineVideoId,
                            pausedInlineVideoIds = pausedInlineVideoIds,
                            inlineVideoProgressById = inlineVideoProgressById,
                            onToggleInlineVideo = onToggleInlineVideo,
                            onInlineVideoProgressChange = { item, progress ->
                                inlineVideoProgressById = inlineVideoProgressById + (item.mediaId to progress)
                            },
                            thumbnailRequestSize = thumbnailRequestSize,
                            onMediaClick = { item ->
                                if (item.mediaId in disabledMediaIds && selectionState.isInSelectionMode) {
                                    return@PhotoFeedGridRowContent
                                }
                                onSelectionStateChange(
                                    if (selectionState.isInSelectionMode) {
                                        selectionState.toggle(item.mediaId).also { nextState ->
                                            if (item.mediaId !in selectionState.selectedMediaIds &&
                                                item.mediaId in nextState.selectedMediaIds
                                            ) {
                                                selectionFlashNonce += 1
                                                selectionFlashByMediaId = selectionFlashByMediaId +
                                                    (item.mediaId to SelectionNumberFlash(nextState.selectedCount, selectionFlashNonce))
                                            }
                                        }
                                    } else {
                                        onOpenViewer(
                                            PhotoViewerRoute(
                                                mediaItems = displayFeedItems,
                                                initialIndex = mediaPositionLookup[item.mediaId] ?: 0,
                                                sourceLabel = "photos-feed",
                                                showSmallAlbumSegments = false,
                                            ),
                                        )
                                        selectionState
                                    },
                                )
                            },
                            onOpenMedia = { item ->
                                onOpenViewer(
                                    PhotoViewerRoute(
                                        mediaItems = displayFeedItems,
                                        initialIndex = mediaPositionLookup[item.mediaId] ?: 0,
                                        sourceLabel = "photos-feed",
                                        showSmallAlbumSegments = false,
                                    ),
                                )
                            },
                            onMediaLongPress = { item ->
                                if (item.mediaId in disabledMediaIds) {
                                    return@PhotoFeedGridRowContent
                                }
                                onSelectionStateChange(
                                    if (selectionState.isInSelectionMode) {
                                        selectionState.toggle(item.mediaId)
                                    } else {
                                        selectionState.enterWith(item.mediaId)
                                    },
                                )
                            },
                        )
                    }
                }
                if (blocks.isEmpty()) {
                    item(key = "photo-feed-empty-filter", contentType = "empty-filter") {
                        PhotoFeedEmptyCollaboratorState(
                            hasCollaborators = collaboratorDirectory.all.isNotEmpty(),
                            selectionEmpty = selectedCollaboratorUserIds.isEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = spacing.lg),
                        )
                    }
                }
                if (isLoadingMore) {
                    item(key = "photo-feed-loading-more", contentType = "loading-more") {
                        Text(
                            text = "正在加载…",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = YingShiThemeTokens.colors.textSecondary,
                        )
                    }
                }
                if (!isLoadingMore && loadMoreErrorMessage != null) {
                    item(key = "photo-feed-load-more-error", contentType = "load-more-error") {
                        PhotoFeedLoadMoreErrorRow(
                            message = loadMoreErrorMessage,
                            onRetry = onRetryLoadMore,
                        )
                    }
                } else if (!isLoadingMore && !hasMore && displayFeedItems.isNotEmpty()) {
                    item(key = "photo-feed-no-more", contentType = "no-more") {
                        Text(
                            text = "没有更多了",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = YingShiThemeTokens.colors.textSecondary.copy(alpha = 0.70f),
                        )
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = scrubberVisible && scrollAnchors.size > 1,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp)
                    .fillMaxHeight(),
            ) {
                PhotoFeedTimeScrubber(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(184.dp),
                    progress = displayedScrubberProgress,
                    label = displayedScrubberLabel,
                    showLabel = scrubberInteracting,
                    yearMarkers = scrubberYearMarkers,
                    onSeekToProgress = { progress ->
                        if (scrollAnchors.isEmpty()) {
                            return@PhotoFeedTimeScrubber
                        }
                        val anchorIndex = (progress * scrollAnchors.lastIndex)
                            .roundToInt()
                            .coerceIn(0, scrollAnchors.lastIndex)
                        scrubberDragProgress = progress.coerceIn(0f, 1f)
                        scrubberDragLabel = scrollAnchors.getOrNull(anchorIndex)
                            ?.let { anchor -> formatScrubberDateLabel(anchor.timeMillis) }
                            .orEmpty()
                        if (anchorIndex == lastRequestedAnchorIndex) {
                            return@PhotoFeedTimeScrubber
                        }
                        scrollAnchors.getOrNull(anchorIndex)?.let { anchor ->
                            lastRequestedAnchorIndex = anchorIndex
                            coroutineScope.launch {
                                listState.scrollToItem(
                                    index = anchor.itemIndex,
                                    scrollOffset = calculatePhotoFeedScrubberScrollOffset(listState),
                                )
                            }
                        }
                    },
                    onInteractingChanged = { interacting ->
                        scrubberInteracting = interacting
                        if (interacting) {
                            scrubberDragProgress = currentScrollProgress
                            scrubberDragLabel = currentVisibleDateLabel
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

@Composable
private fun PhotoFeedAtmosphereLayer(
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val motion = YingShiThemeTokens.motion
    Box(
        modifier = modifier
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colors.glowWash.copy(alpha = 0.30f * motion.feedAtmosphereAlpha),
                        colors.sectionBackground.copy(alpha = 0.16f * motion.feedAtmosphereAlpha),
                        Color.Transparent,
                    ),
                    center = Offset(0f, 0f),
                    radius = 820f,
                ),
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.glowWash.copy(alpha = 0.10f * motion.feedAtmosphereAlpha),
                        Color.Transparent,
                        colors.sectionBackground.copy(alpha = 0.08f * motion.feedAtmosphereAlpha),
                    ),
                ),
            ),
    )
}

@Composable
private fun PrefetchPhotoFeedThumbnails(feedItems: List<PhotoFeedItem>) {
    PrefetchPhotoFeedThumbnails(
        feedItems = feedItems,
        density = PhotoFeedDensity.COMFORT_3,
        requestSize = photoFeedThumbnailRequestSize(PhotoFeedDensity.COMFORT_3),
    )
}

@Composable
private fun PrefetchPhotoFeedThumbnails(
    feedItems: List<PhotoFeedItem>,
    density: PhotoFeedDensity,
    requestSize: Int,
) {
    if (RepositoryProvider.currentMode != RepositoryMode.REAL) return

    val context = LocalContext.current
    val sessionVersion = AuthSessionManager.sessionVersion
    val accessToken = remember(sessionVersion) {
        AuthSessionManager.peekAccessToken()?.takeIf { it.isNotBlank() }
    }
    val prefetchTargets = remember(feedItems, density, requestSize) {
        feedItems
            .take(photoFeedPrefetchCount(density))
            .mapNotNull { item ->
                val mediaSource = item.mediaSource
                val url = mediaSource.thumbnailModelUrl(item.mediaType) ?: return@mapNotNull null
                PrefetchTarget(
                    url = url,
                    cacheKey = mediaSource.thumbnailModelCacheKey(item.mediaType),
                    mediaType = item.mediaType,
                    mimeType = mediaSource?.mimeType,
                )
            }
            .distinctBy { "${it.mediaType}:${it.cacheKey ?: it.url}" }
    }

    LaunchedEffect(context, prefetchTargets, accessToken) {
        val imageLoader = context.imageLoader
        prefetchTargets.forEach { target ->
            if (target.mediaType == AppMediaType.VIDEO &&
                looksLikeVideoSource(target.url, target.mimeType)
            ) {
                prefetchVideoPoster(
                    context = context,
                    url = target.url,
                    accessToken = accessToken,
                    cacheKey = target.cacheKey,
                )
                return@forEach
            }
            backendMediaImageRequest(
                context = context,
                url = target.url,
                accessToken = accessToken,
                memoryCacheKey = photoFeedPreviewMemoryCacheKey(
                    url = target.url,
                    cacheKey = target.cacheKey,
                    requestSize = requestSize,
                ),
                placeholderMemoryCacheKey = target.cacheKey ?: sharedPreviewMemoryCacheKey(target.url),
                diskCacheKey = target.cacheKey,
                size = requestSize,
            )?.let(imageLoader::enqueue)
        }
    }
}

private data class PrefetchTarget(
    val url: String,
    val cacheKey: String?,
    val mediaType: AppMediaType,
    val mimeType: String?,
)

private data class PhotoFeedVisiblePosition(
    val index: Int,
    val offset: Int,
    val mediaId: String?,
)

@Composable
private fun PhotoFeedLoadMoreErrorRow(
    message: String,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.labelMedium,
            color = YingShiThemeTokens.colors.textSecondary,
            maxLines = 2,
        )
        Text(
            text = "\u91cd\u8bd5",
            modifier = Modifier.yingShiClickable(onClick = onRetry),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = YingShiThemeTokens.colors.titleAccent,
        )
    }
}

@Composable
private fun PhotoFeedToolbar(
    mediaCount: Int,
    selectedCount: Int,
    selectedDensity: PhotoFeedDensity,
    enabled: Boolean,
    onDensitySelected: (PhotoFeedDensity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    YingShiToolSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.capsule),
        contentPadding = PaddingValues(horizontal = spacing.sm, vertical = spacing.xs),
        highlighted = selectedCount > 0,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YingShiStatusPill(
                text = if (selectedCount > 0) {
                    "\u5df2\u9009 $selectedCount"
                } else {
                    "$mediaCount \u9879 · ${selectedDensity.label}"
                },
                modifier = Modifier.weight(1f),
                selected = selectedCount > 0,
            )
            PhotoFeedDensitySwitcher(
                selectedDensity = selectedDensity,
                enabled = enabled,
                onDensitySelected = onDensitySelected,
            )
        }
    }
}

@Composable
internal fun PhotoFeedDensitySwitcher(
    selectedDensity: PhotoFeedDensity,
    enabled: Boolean,
    onDensitySelected: (PhotoFeedDensity) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val chipShape = RoundedCornerShape(radius.capsule)

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoFeedDensity.entries.forEach { density ->
            val selected = density == selectedDensity
            val backgroundColor = when {
                !enabled -> colors.sectionBackground.copy(alpha = 0.48f)
                selected -> colors.primaryContainer.copy(alpha = 0.86f)
                else -> colors.sectionBackground.copy(alpha = 0.74f)
            }
            val textColor = when {
                !enabled -> colors.textSecondary.copy(alpha = 0.60f)
                selected -> colors.titleAccent
                else -> colors.textSecondary
            }

            Surface(
                modifier = Modifier
                    .yingShiClickable(
                        enabled = enabled,
                        shape = chipShape,
                        pressedScale = 0.96f,
                        onClick = { onDensitySelected(density) },
                    ),
                shape = chipShape,
                color = backgroundColor,
                border = BorderStroke(
                    1.dp,
                    if (selected) colors.glassStroke.copy(alpha = 0.74f) else colors.dividerSoft.copy(alpha = 0.42f),
                ),
            ) {
                Text(
                    text = density.label,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = textColor,
                )
            }
        }
    }
}

@Composable
internal fun PhotoFeedTimeScrubber(
    progress: Float,
    label: String,
    showLabel: Boolean,
    yearMarkers: List<PhotoFeedScrubberYearMarker>,
    onSeekToProgress: (Float) -> Unit,
    onInteractingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val thumbWidth = 26.dp
    val thumbHeight = 64.dp
    val endMargin = 2.dp
    val trackInsetY = 10.dp
    val overviewReveal = 76.dp

    var scrubberWidthPx by remember { mutableIntStateOf(0) }
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
    val labelGapPx = with(density) { 40.dp.roundToPx() }
    val yearLabelGapPx = with(density) { 8.dp.roundToPx() }
    val yearLabelWidthPx = with(density) { 60.dp.roundToPx() }
    val trackInsetYPx = with(density) { trackInsetY.roundToPx() }
    val overviewRevealPx = with(density) { overviewReveal.toPx() }
    val travelHeightPx = (scrubberHeightPx - thumbHeightPx).coerceAtLeast(1)
    val normalizedProgress = progress.coerceIn(0f, 1f)
    val thumbTopPx = (travelHeightPx * normalizedProgress).roundToInt()
    val labelTopPx = (thumbTopPx + (thumbHeightPx / 2) - (labelHeightPx / 2))
        .coerceIn(0, (scrubberHeightPx - labelHeightPx).coerceAtLeast(0))
    val showYearOverview = showLabel && yearMarkers.isNotEmpty()
    val overviewExpansion = remember { Animatable(0f) }

    LaunchedEffect(showYearOverview) {
        if (showYearOverview) {
            overviewExpansion.snapTo(0f)
            overviewExpansion.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
            )
        } else {
            overviewExpansion.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged {
                scrubberWidthPx = it.width
                scrubberHeightPx = it.height
            },
    ) {
        val minTrackCenterXPx = thumbWidthPx / 2f
        val maxTrackCenterXPx = (
            scrubberWidthPx.toFloat() - (thumbWidthPx / 2f)
            ).coerceAtLeast(minTrackCenterXPx)
        val trackCenterXPx = (
            scrubberWidthPx.toFloat() -
                endMarginPx.toFloat() -
                (thumbWidthPx / 2f)
            ).coerceIn(
                minTrackCenterXPx,
                maxTrackCenterXPx,
            )
        val thumbLeftPx = (trackCenterXPx - (thumbWidthPx / 2f)).roundToInt()
        val labelLeftPx = (
            trackCenterXPx -
                (thumbWidthPx / 2f) -
                labelGapPx -
                scrubberLabelWidthPx
            ).roundToInt()
        val yearLabelLeftPx = (
            trackCenterXPx -
                yearLabelGapPx -
                yearLabelWidthPx
            ).roundToInt()
        Canvas(
            modifier = Modifier.matchParentSize(),
        ) {
            val canvasMinCenterX = thumbWidthPx / 2f
            val canvasMaxCenterX = (size.width - (thumbWidthPx / 2f)).coerceAtLeast(canvasMinCenterX)
            val centerX = trackCenterXPx.coerceIn(canvasMinCenterX, canvasMaxCenterX)
            val topY = trackInsetYPx.toFloat()
            val bottomY = (size.height - trackInsetYPx.toFloat()).coerceAtLeast(topY)
            val thumbCenterY = (thumbTopPx + thumbHeightPx / 2f).coerceIn(topY, bottomY)
            val expansion = overviewExpansion.value
            if (expansion > 0f) {
                val expandedTopY = thumbCenterY - ((thumbCenterY - topY) * expansion)
                val expandedBottomY = thumbCenterY + ((bottomY - thumbCenterY) * expansion)
                drawLine(
                    color = colors.textSecondary.copy(alpha = 0.44f * expansion),
                    start = Offset(centerX, expandedTopY),
                    end = Offset(centerX, expandedBottomY),
                    strokeWidth = 1.8.dp.toPx() + 1.3.dp.toPx() * expansion,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
                yearMarkers.forEach { marker ->
                    val dotY = topY + (bottomY - topY) * marker.progress.coerceIn(0f, 1f)
                    drawLine(
                        color = colors.titleAccent.copy(alpha = 0.36f * expansion),
                        start = Offset(centerX - 10.dp.toPx() * expansion, dotY),
                        end = Offset(centerX, dotY),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                    drawCircle(
                        color = colors.titleAccent.copy(alpha = 0.92f),
                        radius = 2.1.dp.toPx() + (1.2.dp.toPx() * expansion),
                        center = Offset(centerX, dotY),
                    )
                }
            }
        }

        if (overviewExpansion.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(),
            ) {
                yearMarkers.forEach { marker ->
                    val dotY = (
                        trackInsetYPx.toFloat() +
                            (scrubberHeightPx - trackInsetYPx * 2).coerceAtLeast(0) *
                            marker.progress.coerceIn(0f, 1f)
                        ).roundToInt()
                    Text(
                        text = marker.year.toString(),
                        modifier = Modifier
                            .width(60.dp)
                            .align(Alignment.TopStart)
                            .offset {
                                IntOffset(
                                    x = yearLabelLeftPx,
                                    y = (dotY - 13.dp.roundToPx()).coerceAtLeast(0),
                                )
                            }
                            .graphicsLayer(
                                alpha = overviewExpansion.value,
                                translationX = (1f - overviewExpansion.value) * (overviewRevealPx * 0.20f),
                            ),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                        ),
                        textAlign = TextAlign.End,
                        color = colors.titleAccent.copy(alpha = 0.94f),
                    )
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showLabel && label.isNotBlank(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    IntOffset(
                        x = labelLeftPx,
                        y = labelTopPx,
                    )
                },
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = colors.raisedSurface.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.58f)),
                shadowElevation = 1.dp,
            ) {
                Text(
                    text = label,
                    modifier = Modifier
                        .width(132.dp)
                        .padding(horizontal = spacing.sm, vertical = 9.dp)
                        .onSizeChanged {
                            scrubberLabelWidthPx = it.width
                            labelHeightPx = it.height
                        },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    ),
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    color = colors.titleAccent,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    IntOffset(
                        x = thumbLeftPx,
                        y = thumbTopPx,
                    )
                }
                .size(width = thumbWidth, height = thumbHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(colors.raisedSurface.copy(alpha = 0.94f))
                .border(
                    width = 1.dp,
                    color = colors.dividerSoft.copy(alpha = 0.58f),
                    shape = RoundedCornerShape(999.dp),
                )
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
                    .padding(top = 9.dp)
                    .size(7.dp),
            ) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(w / 2f, 0f)
                    lineTo(0f, h)
                    lineTo(w, h)
                    close()
                }
                drawPath(path, color = colors.titleAccent.copy(alpha = 0.82f))
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = 14.dp, height = 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.dividerSoft.copy(alpha = 0.82f)),
            )

            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 9.dp)
                    .size(7.dp),
            ) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(w / 2f, h)
                    close()
                }
                drawPath(path, color = colors.titleAccent.copy(alpha = 0.82f))
            }
        }
    }
}

@Composable
internal fun PhotoFeedSectionHeaderRow(title: String) {
    val colors = YingShiThemeTokens.colors
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
        style = MaterialTheme.typography.headlineSmall.copy(
            fontSize = 32.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.ExtraBold,
        ),
        color = colors.titleAccent,
    )
}

@Composable
private fun PhotoFeedCollaboratorControlsRow(
    directory: CollaboratorDirectorySnapshot,
    selectedUserIds: Set<String>,
    timeBucketHours: Int,
    onToggleCollaborator: (String) -> Unit,
    onTimeBucketHoursChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            directory.all.forEach { identity ->
                CollaboratorFilterChip(
                    identity = identity,
                    selected = identity.userId in selectedUserIds,
                    onClick = { onToggleCollaborator(identity.userId) },
                    labelText = identity.displayName,
                )
            }
            Box {
                PhotoFeedTimeBucketButton(
                    timeBucketHours = timeBucketHours,
                    onClick = { expanded = true },
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    CollaborativeBucketHoursOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${option}h",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (option == timeBucketHours) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Medium
                                        },
                                    ),
                                )
                            },
                            onClick = {
                                expanded = false
                                onTimeBucketHoursChange(option)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoFeedTimeBucketButton(
    timeBucketHours: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    Surface(
        modifier = modifier.yingShiClickable(
            shape = shape,
            pressedScale = 0.97f,
            onClick = onClick,
        ),
        shape = shape,
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.66f)),
    ) {
        Text(
            text = "${timeBucketHours}h",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.titleAccent,
        )
    }
}

@Composable
private fun PhotoFeedTimeBucketHeaderRow(
    header: PhotoFeedTimeBucketHeader,
    density: PhotoFeedDensity,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val totalCount = header.currentCount + header.partnerCount
    val currentRatio = if (totalCount > 0) {
        header.currentCount.toFloat() / totalCount.toFloat()
    } else {
        0f
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (density.columns <= 4) 12.dp else 8.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = header.title,
                style = if (density.columns <= 4) {
                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                } else {
                    MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                },
                color = colors.textSecondary.copy(alpha = 0.82f),
            )
            Text(
                text = "${totalCount} 项",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.textSecondary,
            )
        }
        if (header.currentCount > 0 && header.partnerCount > 0 && density.columns <= 4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.sectionBackground.copy(alpha = 0.78f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(currentRatio.coerceIn(0.001f, 0.999f))
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.primaryContainer.copy(alpha = 0.92f))
                                .padding(vertical = 3.dp),
                        )
                        Box(
                            modifier = Modifier
                                .weight((1f - currentRatio).coerceIn(0.001f, 0.999f))
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.glowWash.copy(alpha = 0.90f))
                                .padding(vertical = 3.dp),
                        )
                    }
                }
                Text(
                    text = "${header.currentCount}:${header.partnerCount}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun PhotoFeedCollaboratorHeaderRow(
    identity: CollaboratorIdentityUiModel,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
            color = colors.sectionBackground.copy(alpha = 0.82f),
            border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.60f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CollaboratorAvatar(identity = identity, size = 22.dp)
                Text(
                    text = identity.displayName,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
            }
        }
    }
}

@Composable
private fun PhotoFeedCollaboratorDividerRow(
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.glassStroke.copy(alpha = 0.55f)),
        )
    }
}

@Composable
private fun PhotoFeedEmptyCollaboratorState(
    hasCollaborators: Boolean,
    selectionEmpty: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val message = if (selectionEmpty) {
        "未选中任何账号，当前不展示媒体"
    } else if (hasCollaborators) {
        "当前筛选下还没有媒体"
    } else {
        "还没有可以展示的协作账号"
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = colors.sectionBackground.copy(alpha = 0.68f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.58f)),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 22.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = colors.textSecondary,
        )
    }
}

@Composable
internal fun PhotoFeedDayHeaderRow(title: String) {
    val colors = YingShiThemeTokens.colors
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 6.dp, bottom = 5.dp),
        style = MaterialTheme.typography.titleLarge.copy(
            fontSize = 22.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
        ),
        color = colors.textPrimary.copy(alpha = 0.84f),
    )
}

@Composable
private fun PhotoFeedGridRowContent(
    row: PhotoFeedGridRow,
    density: PhotoFeedDensity,
    selectionState: PhotoFeedSelectionState,
    disabledMediaIds: Set<String>,
    disabledSelectionLabel: String?,
    selectionFlash: Map<String, SelectionNumberFlash>,
    highlightedMediaId: String?,
    highlightNonce: Int,
    newImportedMediaIds: Set<String>,
    restoredMediaIds: Set<String>,
    inlineVideoAutoPlayEnabled: Boolean,
    allowOpenMediaWhileSelecting: Boolean,
    playingInlineVideoId: String?,
    activeInlineVideoId: String?,
    pausedInlineVideoIds: Set<String>,
    inlineVideoProgressById: Map<String, InlineVideoPlaybackProgress>,
    onToggleInlineVideo: (PhotoFeedItem) -> Unit,
    onInlineVideoProgressChange: (PhotoFeedItem, InlineVideoPlaybackProgress) -> Unit,
    thumbnailRequestSize: Int,
    onMediaClick: (PhotoFeedItem) -> Unit,
    onOpenMedia: (PhotoFeedItem) -> Unit,
    onMediaLongPress: (PhotoFeedItem) -> Unit,
) {
    val spacing = rowSpacing(density)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        row.items.forEach { item ->
            PhotoFeedCard(
                item = item,
                density = density,
                isInSelectionMode = selectionState.isInSelectionMode,
                isSelected = selectionState.contains(item.mediaId),
                disabled = item.mediaId in disabledMediaIds,
                disabledSelectionLabel = disabledSelectionLabel,
                selectionFlash = selectionFlash[item.mediaId],
                isHighlighted = highlightedMediaId == item.mediaId,
                highlightNonce = highlightNonce,
                isNewImported = item.mediaId in newImportedMediaIds,
                isRestored = item.mediaId in restoredMediaIds,
                inlineVideoAutoPlayEnabled = inlineVideoAutoPlayEnabled,
                allowOpenMediaWhileSelecting = allowOpenMediaWhileSelecting,
                isInlineVideoPlaying = playingInlineVideoId == item.mediaId,
                isInlineVideoActive = activeInlineVideoId == item.mediaId,
                isInlineVideoPaused = item.mediaId in pausedInlineVideoIds,
                inlineVideoProgress = inlineVideoProgressById[item.mediaId],
                modifier = Modifier.weight(1f),
                thumbnailRequestSize = thumbnailRequestSize,
                onClick = { onMediaClick(item) },
                onOpenMedia = { onOpenMedia(item) },
                onLongPress = { onMediaLongPress(item) },
                onToggleInlineVideo = { onToggleInlineVideo(item) },
                onInlineVideoProgressChange = { progress -> onInlineVideoProgressChange(item, progress) },
            )
        }

        repeat(density.columns - row.items.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoFeedCard(
    item: PhotoFeedItem,
    density: PhotoFeedDensity,
    isInSelectionMode: Boolean,
    isSelected: Boolean,
    disabled: Boolean,
    disabledSelectionLabel: String?,
    selectionFlash: SelectionNumberFlash?,
    isHighlighted: Boolean,
    highlightNonce: Int,
    isNewImported: Boolean,
    isRestored: Boolean,
    inlineVideoAutoPlayEnabled: Boolean,
    allowOpenMediaWhileSelecting: Boolean,
    isInlineVideoPlaying: Boolean,
    isInlineVideoActive: Boolean,
    isInlineVideoPaused: Boolean,
    inlineVideoProgress: InlineVideoPlaybackProgress?,
    modifier: Modifier = Modifier,
    thumbnailRequestSize: Int,
    onClick: () -> Unit,
    onOpenMedia: () -> Unit,
    onLongPress: () -> Unit,
    onToggleInlineVideo: () -> Unit,
    onInlineVideoProgressChange: (InlineVideoPlaybackProgress) -> Unit,
) {
    val motion = YingShiThemeTokens.motion
    val colors = YingShiThemeTokens.colors
    val motionEnabled = rememberYingShiMotionEnabled()
    val selectionHotspotOnly = isInSelectionMode && allowOpenMediaWhileSelecting && density.columns in 2..4
    val supportsInlineVideo = inlineVideoAutoPlayEnabled &&
        !isInSelectionMode &&
        item.mediaType == AppMediaType.VIDEO &&
        density.columns <= 4
    val showSelectionVideoMarker = isInSelectionMode &&
        item.mediaType == AppMediaType.VIDEO &&
        density.columns <= 4
    val itemScale by animateFloatAsState(
        targetValue = if (isSelected) motion.selectedMediaScale else 1f,
        animationSpec = tween(if (motionEnabled) motion.stateMillis else 0, easing = motion.easing),
        label = "photoFeedCardSelectionScale",
    )
    val selectionBorderAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.46f else 0f,
        animationSpec = tween(if (motionEnabled) motion.stateMillis else 0, easing = motion.easing),
        label = "photoFeedCardSelectionBorder",
    )
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = itemScale
                scaleY = itemScale
            }
            .clipToBounds()
            .background(Color.Transparent)
            .combinedClickable(
                onClick = if (disabled) ({}) else if (selectionHotspotOnly) onOpenMedia else onClick,
                onLongClick = if (disabled) ({}) else onLongPress,
            ),
    ) {
        AppContentMediaThumbnail(
            mediaSource = item.mediaSource,
            mediaType = item.mediaType,
            palette = item.palette,
            modifier = Modifier.matchParentSize(),
            contentDescription = item.mediaId,
            requestSize = thumbnailRequestSize,
            showLoadingIndicator = false,
            showVideoPlayOverlay = !(supportsInlineVideo || showSelectionVideoMarker),
        )

        YingShiMediaFrame(
            modifier = Modifier.matchParentSize(),
            selected = isSelected,
            memoryActive = isNewImported || isRestored || isHighlighted,
            topScrimAlpha = if (item.mediaType == AppMediaType.VIDEO) 0.22f else 0.14f,
            bottomGlowAlpha = if (isSelected) 0.24f else 0.16f,
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .yingShiMemoryGlow(
                    visible = isNewImported || isRestored || isHighlighted,
                    warm = isNewImported || isRestored,
                    motionEnabled = motionEnabled,
                ),
        )
        MemoryStatusSweepOverlay(
            visible = isNewImported || isRestored || isHighlighted,
            warm = isNewImported || isRestored,
            nonce = if (isHighlighted) highlightNonce else item.mediaId.hashCode(),
            motionEnabled = motionEnabled,
            modifier = Modifier.matchParentSize(),
        )

        if (supportsInlineVideo && isInlineVideoPlaying) {
            AppContentInlineVideoPlayer(
                mediaSource = item.mediaSource,
                mediaType = item.mediaType,
                playWhenReady = true,
                modifier = Modifier.matchParentSize(),
                onPlaybackProgressChange = onInlineVideoProgressChange,
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

        if (item.mediaType == AppMediaType.VIDEO) {
            VideoDurationBadge(
                durationMillis = item.gridVideoBadgeDurationMillis(inlineVideoProgress),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp),
            )
        }

        if (isNewImported) {
            YingShiMemoryBadge(
                text = "新导入",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 6.dp, top = 6.dp),
                compact = density.columns >= 4,
            )
        } else if (isRestored) {
            YingShiMemoryBadge(
                text = "已恢复",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 6.dp, top = 6.dp),
                compact = density.columns >= 4,
            )
        }

        if (disabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(colors.raisedSurface.copy(alpha = 0.24f)),
            )
            disabledSelectionLabel?.takeIf { it.isNotBlank() }?.let { label ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp, bottom = 6.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = colors.viewerBackground.copy(alpha = 0.44f),
                    border = BorderStroke(1.dp, colors.viewerText.copy(alpha = 0.18f)),
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.viewerText.copy(alpha = 0.92f),
                    )
                }
            }
        }

        if (isInSelectionMode) {
            val selectionVeilAlpha by animateFloatAsState(
                targetValue = if (isSelected) 0.20f else 0.06f,
                animationSpec = tween(if (motionEnabled) motion.stateMillis else 0, easing = motion.easing),
                label = "photoFeedSelectionVeil",
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(colors.viewerBackground.copy(alpha = selectionVeilAlpha)),
            )
            if (selectionHotspotOnly) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(46.dp)
                        .clickable(enabled = !disabled, onClick = onClick),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    SelectionBadge(
                        selected = isSelected,
                        disabled = disabled,
                        modifier = Modifier.padding(end = 2.dp, bottom = 2.dp),
                    )
                }
            } else {
                SelectionBadge(
                    selected = isSelected,
                    disabled = disabled,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 2.dp, bottom = 2.dp),
                )
            }
        }
        if (selectionBorderAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = 2.dp,
                        color = YingShiThemeTokens.colors.glassStroke.copy(alpha = selectionBorderAlpha),
                    ),
            )
        }
        SelectionNumberFlashOverlay(
            flash = selectionFlash,
            modifier = Modifier.align(Alignment.Center),
        )
        TargetMediaHighlightOverlay(
            visible = isHighlighted,
            nonce = highlightNonce,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
private fun MemoryStatusSweepOverlay(
    visible: Boolean,
    warm: Boolean,
    nonce: Int,
    motionEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val colors = YingShiThemeTokens.colors
    val motion = YingShiThemeTokens.motion
    val sweep = remember(nonce, warm) { Animatable(0f) }
    LaunchedEffect(nonce, warm, motionEnabled) {
        sweep.snapTo(0f)
        if (motionEnabled) {
            sweep.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = motion.memoryGlowMillis, easing = motion.easing),
            )
        } else {
            sweep.snapTo(1f)
        }
    }

    val accent = if (warm) colors.memoryAccent else colors.primaryContainer
    val progress = sweep.value
    val sweepAlpha = when {
        progress < 0.18f -> progress / 0.18f
        progress > 0.72f -> (1f - progress) / 0.28f
        else -> 1f
    }.coerceIn(0f, 1f)
    val sweepStart = -420f + progress * 840f

    Box(
        modifier = modifier
            .border(
                width = 1.5.dp,
                color = accent.copy(alpha = 0.36f * sweepAlpha),
            )
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        accent.copy(alpha = motion.memorySweepAlpha * sweepAlpha),
                        colors.glowWash.copy(alpha = 0.24f * sweepAlpha),
                        Color.Transparent,
                    ),
                    start = Offset(sweepStart, 0f),
                    end = Offset(sweepStart + 260f, 260f),
                ),
            ),
    )
}

private fun PhotoFeedItem.gridVideoBadgeDurationMillis(
    progress: InlineVideoPlaybackProgress?,
): Long? {
    val totalMillis = progress?.durationMillis
        ?: videoDurationMillis
        ?: mediaSource?.durationMillis
    if (totalMillis == null || totalMillis <= 0L) return null
    val positionMillis = progress?.positionMillis ?: 0L
    return (totalMillis - positionMillis).coerceIn(0L, totalMillis)
}

@Composable
private fun NewImportedBadge(
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = colors.memoryContainer.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, colors.memoryAccent.copy(alpha = 0.20f)),
    ) {
        Text(
            text = "新导入",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onMemoryContainer,
            maxLines = 1,
        )
    }
}

@Composable
private fun SelectionBadge(
    selected: Boolean,
    disabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    AppMediaSelectionBadge(selected = selected, disabled = disabled, modifier = modifier)
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
                .background(YingShiThemeTokens.colors.raisedSurface.copy(alpha = 0.92f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = flash.number.toString(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                ),
                color = YingShiThemeTokens.colors.titleAccent,
            )
        }
    }
}

@Composable
private fun TargetMediaHighlightOverlay(
    visible: Boolean,
    nonce: Int,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val alpha = remember(nonce) { Animatable(0f) }
    val highlightColor = YingShiThemeTokens.colors.primaryContainer
    LaunchedEffect(nonce) {
        alpha.snapTo(0f)
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        )
        delay(420)
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 760),
        )
    }

    if (alpha.value > 0f) {
        Box(
            modifier = modifier
                .background(highlightColor.copy(alpha = 0.26f * alpha.value))
                .border(
                    width = 3.dp,
                    color = YingShiThemeTokens.colors.glassStroke.copy(alpha = 0.90f * alpha.value),
                ),
        )
    }
}

internal fun resolveCurrentVisibleDateLabel(
    itemIndex: Int,
    blocks: List<PhotoFeedBlock>,
    fallbackItems: List<PhotoFeedItem>,
): String {
    if (blocks.isEmpty()) {
        return fallbackItems.firstOrNull()?.toScrubberLabel().orEmpty()
    }

    val safeIndex = itemIndex.coerceIn(0, blocks.lastIndex)
    val nextRow = blocks
        .drop(safeIndex)
        .firstOrNull { it is PhotoFeedGridRow } as? PhotoFeedGridRow
    val previousRow = blocks
        .take(safeIndex + 1)
        .lastOrNull { it is PhotoFeedGridRow } as? PhotoFeedGridRow

    return nextRow?.items?.firstOrNull()?.toScrubberLabel()
        ?: previousRow?.items?.firstOrNull()?.toScrubberLabel()
        ?: fallbackItems.firstOrNull()?.toScrubberLabel()
        ?: ""
}

internal fun calculatePhotoFeedScrollProgress(
    listState: LazyListState,
    anchorCount: Int,
): Float {
    if (anchorCount <= 1) return 0f

    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    if (totalItems <= 1) return 0f

    val firstVisible = layoutInfo.visibleItemsInfo.firstOrNull() ?: return 0f
    val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(1)
    val visibleItemCount = layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    val scrollableStart = (totalItems - visibleItemCount).coerceAtLeast(1)
    val offsetFraction = ((-firstVisible.offset).toFloat() / maxOf(firstVisible.size, viewportHeight).toFloat())
        .coerceIn(0f, 1f)

    return ((listState.firstVisibleItemIndex + offsetFraction) / scrollableStart.toFloat())
        .coerceIn(0f, 1f)
}

private fun calculatePhotoFeedTargetScrollOffset(listState: LazyListState): Int {
    val layoutInfo = listState.layoutInfo
    val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
        .takeIf { it > 0 }
        ?: layoutInfo.viewportSize.height
    return -(viewportHeight * 0.36f).roundToInt()
}

internal fun calculatePhotoFeedScrubberScrollOffset(listState: LazyListState): Int {
    val layoutInfo = listState.layoutInfo
    val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
        .takeIf { it > 0 }
        ?: layoutInfo.viewportSize.height
    return -(viewportHeight * 0.24f).roundToInt()
}

private fun visiblePhotoFeedVideoIds(
    listState: LazyListState,
    blocks: List<PhotoFeedBlock>,
): Set<String> {
    return listState.layoutInfo.visibleItemsInfo
        .mapNotNull { visibleItem ->
            blocks.getOrNull(visibleItem.index) as? PhotoFeedGridRow
        }
        .flatMap { row -> row.items }
        .filter { item -> item.mediaType == AppMediaType.VIDEO }
        .map { item -> item.mediaId }
        .toSet()
}

private fun centeredPhotoFeedVideoId(
    listState: LazyListState,
    blocks: List<PhotoFeedBlock>,
    density: PhotoFeedDensity,
    colSpacingPx: Float,
    edgePaddingPx: Float,
): String? {
    val layoutInfo = listState.layoutInfo
    if (layoutInfo.visibleItemsInfo.isEmpty()) return null
    val viewportWidth = layoutInfo.viewportSize.width.coerceAtLeast(1)
    val viewportCenterX = viewportWidth / 2f
    val viewportCenterY = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    val contentWidth = (viewportWidth - edgePaddingPx * 2f).coerceAtLeast(1f)
    val totalSpacing = (density.columns - 1) * colSpacingPx
    val cellWidth = ((contentWidth - totalSpacing) / density.columns).coerceAtLeast(1f)
    val segmentWidth = cellWidth + colSpacingPx

    return layoutInfo.visibleItemsInfo
        .flatMap { visibleItem ->
            val row = blocks.getOrNull(visibleItem.index) as? PhotoFeedGridRow
            if (row == null) {
                emptyList()
            } else {
                row.items.mapIndexedNotNull { colIndex, item ->
                    if (item.mediaType != AppMediaType.VIDEO) return@mapIndexedNotNull null
                    val centerX = edgePaddingPx + colIndex * segmentWidth + cellWidth / 2f
                    val centerY = visibleItem.offset + visibleItem.size / 2f
                    val score = abs(centerX - viewportCenterX) + abs(centerY - viewportCenterY)
                    item.mediaId to score
                }
            }
        }
        .minByOrNull { it.second }
        ?.first
}

private fun PhotoFeedItem.toScrubberLabel(): String {
    return "${displayYear}年${displayMonth}月${displayDay}日"
}

internal fun formatScrubberDateLabel(timeMillis: Long): String {
    val calendar = Calendar.getInstance(Locale.CHINA).apply {
        this.timeInMillis = timeMillis
    }
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    return "${year}年${month}月${day}日"
}

private fun sectionSpacing(density: PhotoFeedDensity): Dp {
    return rowSpacing(density)
}

@Composable
private fun RestoredBadge(
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = colors.softGreenContainer.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, colors.softGreenAction.copy(alpha = 0.18f)),
    ) {
        Text(
            text = "已恢复",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.softGreenAction,
            maxLines = 1,
        )
    }
}

internal fun rowSpacing(density: PhotoFeedDensity): Dp {
    return when (density) {
        PhotoFeedDensity.COMFORT_2 -> 5.dp
        PhotoFeedDensity.COMFORT_3 -> 4.dp
        PhotoFeedDensity.DENSE_4 -> 4.dp
        PhotoFeedDensity.OVERVIEW_8 -> 2.dp
        PhotoFeedDensity.OVERVIEW_16 -> 2.dp
    }
}

internal fun photoFeedThumbnailRequestSize(density: PhotoFeedDensity): Int {
    return when (density) {
        PhotoFeedDensity.COMFORT_2 -> 960
        PhotoFeedDensity.COMFORT_3 -> 720
        PhotoFeedDensity.DENSE_4 -> 512
        PhotoFeedDensity.OVERVIEW_8 -> 256
        PhotoFeedDensity.OVERVIEW_16 -> 160
    }
}

private fun photoFeedPrefetchCount(density: PhotoFeedDensity): Int {
    return when (density) {
        PhotoFeedDensity.COMFORT_2 -> 24
        PhotoFeedDensity.COMFORT_3 -> 30
        PhotoFeedDensity.DENSE_4 -> 40
        PhotoFeedDensity.OVERVIEW_8 -> 64
        PhotoFeedDensity.OVERVIEW_16 -> 96
    }
}

private fun photoFeedPreviewMemoryCacheKey(
    url: String,
    cacheKey: String?,
    requestSize: Int,
): String {
    if (!cacheKey.isNullOrBlank()) {
        return if (requestSize >= 512) {
            cacheKey
        } else {
            "$cacheKey:size:$requestSize"
        }
    }
    return if (requestSize >= 512) {
        sharedPreviewMemoryCacheKey(url)
    } else {
        sharedSizedPreviewMemoryCacheKey(url, requestSize)
    }
}

@Preview(showBackground = true)
@Composable
private fun PhotoFeedScreenPreview() {
    YingShiTheme {
        PhotoFeedScreen(
            selectionState = PhotoFeedSelectionState(),
        )
    }
}
