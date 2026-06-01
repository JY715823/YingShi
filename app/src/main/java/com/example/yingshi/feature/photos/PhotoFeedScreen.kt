package com.example.yingshi.feature.photos

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    scrollTrigger: Int = 0,
    inlineVideoAutoPlayEnabled: Boolean = true,
) {
    val spacing = YingShiThemeTokens.spacing
    val settingsState = FakeSettingsRepository.getSettingsState()
    val mediaPositionLookup = remember(feedItems) {
        feedItems.mapIndexed { index, item -> item.mediaId to index }.toMap()
    }
    var densityName by rememberSaveable { mutableStateOf<String?>(null) }
    var scrubberVisible by remember { mutableStateOf(false) }
    var scrubberInteracting by remember { mutableStateOf(false) }
    var scrubberDragProgress by remember { mutableStateOf<Float?>(null) }
    var scrubberDragLabel by remember { mutableStateOf("") }
    var scrubberLabelWidthPx by remember { mutableIntStateOf(0) }
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
            densityName = PhotoFeedDensity.DENSE_4.name
        }
    }

    val density = PhotoFeedDensity.valueOf(
        densityName ?: settingsState.defaultPhotoFeedDensity.name,
    )
    val thumbnailRequestSize = photoFeedThumbnailRequestSize(density)
    PrefetchPhotoFeedThumbnails(
        feedItems = feedItems,
        density = density,
        requestSize = thumbnailRequestSize,
    )
    val inlineVideoAutoPlayAllowed = inlineVideoAutoPlayEnabled &&
        !selectionState.isInSelectionMode &&
        density.columns <= 4
    val context = LocalContext.current
    val blocks = remember(feedItems, density) {
        buildPhotoFeedBlocks(
            items = feedItems,
            density = density,
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
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = pageStateStore.savedFirstVisibleItemIndex
            .coerceIn(0, blocks.lastIndex.coerceAtLeast(0)),
        initialFirstVisibleItemScrollOffset = pageStateStore.savedFirstVisibleItemScrollOffset,
    )
    val spacingPx = with(LocalDensity.current) { 2.dp.toPx() }
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
    val centeredInlineVideoId by remember(listState, blocks, density, spacingPx) {
        derivedStateOf {
            centeredPhotoFeedVideoId(
                listState = listState,
                blocks = blocks,
                density = density,
                colSpacingPx = spacingPx,
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
    val hitTestAdapter = remember(listState, blocks, density, rowKeyToMediaIds, rowKeys, rowKeyToIndex, spacingPx) {
        val colSpacingPx = spacingPx
        MultiSelectHitTestAdapter(
            hitTest = { touchPos ->
                val layout = listState.layoutInfo
                val ty = touchPos.y.toInt()
                val tx = touchPos.x.toInt()
                val viewportW = layout.viewportSize.width.coerceAtLeast(1)
                val totalSpacing = (density.columns - 1) * colSpacingPx
                val cellWidth = ((viewportW - totalSpacing).toFloat() / density.columns)
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
                            isSelectable = true,
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
    val currentVisibleDateLabel by remember(listState, blocks, feedItems) {
        derivedStateOf {
            resolveCurrentVisibleDateLabel(
                itemIndex = listState.firstVisibleItemIndex,
                blocks = blocks,
                fallbackItems = feedItems,
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
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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

    Column(modifier = modifier.fillMaxSize()) {
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
                        onSelectionStateChange(
                            PhotoFeedSelectionState(
                                selectedMediaIds = newIds,
                                isInSelectionMode = true,
                            ),
                        )
                    },
                    onAutoScroll = { delta -> listState.scrollBy(delta) },
                ),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(sectionSpacing(density)),
                contentPadding = PaddingValues(
                    top = spacing.xs,
                    bottom = spacing.xxl + bottomOverlayPadding,
                ),
            ) {
                items(
                    items = blocks,
                    key = { it.key },
                    contentType = { block ->
                        when (block) {
                            is PhotoFeedSectionHeader -> "section"
                            is PhotoFeedDayHeader -> "day"
                            is PhotoFeedGridRow -> "grid-${density.columns}"
                        }
                    },
                ) { block ->
                    when (block) {
                        is PhotoFeedSectionHeader -> PhotoFeedSectionHeaderRow(title = block.title)
                        is PhotoFeedDayHeader -> PhotoFeedDayHeaderRow(title = block.title)
                        is PhotoFeedGridRow -> PhotoFeedGridRowContent(
                            row = block,
                            density = density,
                            selectionState = selectionState,
                            selectionFlash = selectionFlashByMediaId,
                            highlightedMediaId = highlightedTargetMediaId,
                            highlightNonce = highlightedTargetNonce,
                            newImportedMediaIds = newImportedMediaIds,
                            inlineVideoAutoPlayEnabled = inlineVideoAutoPlayAllowed,
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
                                                mediaItems = feedItems,
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
                                        mediaItems = feedItems,
                                        initialIndex = mediaPositionLookup[item.mediaId] ?: 0,
                                        sourceLabel = "photos-feed",
                                        showSmallAlbumSegments = false,
                                    ),
                                )
                            },
                            onMediaLongPress = { item ->
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
                if (isLoadingMore) {
                    item(key = "photo-feed-loading-more", contentType = "loading-more") {
                        Text(
                            text = "加载更多中...",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                } else if (!isLoadingMore && !hasMore && feedItems.isNotEmpty()) {
                    item(key = "photo-feed-no-more", contentType = "no-more") {
                        Text(
                            text = "没有更多了",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
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
                    .fillMaxHeight(),
            ) {
                PhotoFeedTimeScrubber(
                    modifier = Modifier.fillMaxHeight(),
                    progress = displayedScrubberProgress,
                    label = displayedScrubberLabel,
                    showLabel = scrubberInteracting,
                    onSeekToProgress = { progress ->
                        if (scrollAnchors.isEmpty()) {
                            return@PhotoFeedTimeScrubber
                        }
                        val anchorIndex = (progress * scrollAnchors.lastIndex)
                            .roundToInt()
                            .coerceIn(0, scrollAnchors.lastIndex)
                        scrubberDragProgress = progress.coerceIn(0f, 1f)
                        scrubberDragLabel = scrollAnchors.getOrNull(anchorIndex)?.label.orEmpty()
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
private fun PrefetchPhotoFeedThumbnails(feedItems: List<PhotoFeedItem>) {
    PrefetchPhotoFeedThumbnails(
        feedItems = feedItems,
        density = PhotoFeedDensity.DENSE_4,
        requestSize = photoFeedThumbnailRequestSize(PhotoFeedDensity.DENSE_4),
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
        AuthSessionManager.getAccessToken()?.takeIf { it.isNotBlank() }
    }
    val prefetchTargets = remember(feedItems, density, requestSize) {
        feedItems
            .take(photoFeedPrefetchCount(density))
            .mapNotNull { item ->
                val url = item.mediaSource.thumbnailModelUrl(item.mediaType) ?: return@mapNotNull null
                PrefetchTarget(
                    url = url,
                    mediaType = item.mediaType,
                    mimeType = item.mediaSource?.mimeType,
                )
            }
            .distinctBy { "${it.mediaType}:${it.url}" }
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
                )
                return@forEach
            }
            backendMediaImageRequest(
                context = context,
                url = target.url,
                accessToken = accessToken,
                memoryCacheKey = photoFeedPreviewMemoryCacheKey(target.url, requestSize),
                placeholderMemoryCacheKey = sharedPreviewMemoryCacheKey(target.url),
                size = requestSize,
            )?.let(imageLoader::enqueue)
        }
    }
}

private data class PrefetchTarget(
    val url: String,
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
        Text(
            text = "重试",
            modifier = Modifier.clickable(onClick = onRetry),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
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

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.capsule),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (selectedCount > 0) {
                    "已选 $selectedCount"
                } else {
                    "$mediaCount 项"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun PhotoFeedDensitySwitcher(
    selectedDensity: PhotoFeedDensity,
    enabled: Boolean,
    onDensitySelected: (PhotoFeedDensity) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoFeedDensity.entries.forEach { density ->
            val selected = density == selectedDensity
            val backgroundColor = when {
                !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
                selected -> Color.White
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
            }
            val textColor = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f)
                selected -> Color.Black
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(radius.capsule))
                    .background(backgroundColor)
                    .clickable(enabled = enabled) { onDensitySelected(density) }
                    .padding(horizontal = spacing.xs, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = density.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = textColor,
                )
            }
        }
    }
}

@Composable
private fun PhotoFeedTimeScrubber(
    progress: Float,
    label: String,
    showLabel: Boolean,
    onSeekToProgress: (Float) -> Unit,
    onInteractingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val spacing = YingShiThemeTokens.spacing
    val thumbWidth = 24.dp
    val thumbHeight = 78.dp
    val endMargin = 4.dp

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
                drawPath(path, color = Color.Black.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun PhotoFeedSectionHeaderRow(title: String) {
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
private fun PhotoFeedDayHeaderRow(title: String) {
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
private fun PhotoFeedGridRowContent(
    row: PhotoFeedGridRow,
    density: PhotoFeedDensity,
    selectionState: PhotoFeedSelectionState,
    selectionFlash: Map<String, SelectionNumberFlash>,
    highlightedMediaId: String?,
    highlightNonce: Int,
    newImportedMediaIds: Set<String>,
    inlineVideoAutoPlayEnabled: Boolean,
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
                selectionFlash = selectionFlash[item.mediaId],
                isHighlighted = highlightedMediaId == item.mediaId,
                highlightNonce = highlightNonce,
                isNewImported = item.mediaId in newImportedMediaIds,
                inlineVideoAutoPlayEnabled = inlineVideoAutoPlayEnabled,
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
    selectionFlash: SelectionNumberFlash?,
    isHighlighted: Boolean,
    highlightNonce: Int,
    isNewImported: Boolean,
    inlineVideoAutoPlayEnabled: Boolean,
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
    val selectionHotspotOnly = isInSelectionMode && density.columns in 2..4
    val supportsInlineVideo = inlineVideoAutoPlayEnabled &&
        !isInSelectionMode &&
        item.mediaType == AppMediaType.VIDEO &&
        density.columns <= 4
    val showSelectionVideoMarker = isInSelectionMode &&
        item.mediaType == AppMediaType.VIDEO &&
        density.columns <= 4
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clipToBounds()
            .background(Color.Transparent)
            .combinedClickable(
                onClick = if (selectionHotspotOnly) onOpenMedia else onClick,
                onLongClick = onLongPress,
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
            NewImportedBadge(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 6.dp, top = 6.dp),
            )
        }

        if (isInSelectionMode) {
            if (selectionHotspotOnly) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(46.dp)
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    SelectionBadge(
                        selected = isSelected,
                        modifier = Modifier.padding(end = 2.dp, bottom = 2.dp),
                    )
                }
            } else {
                SelectionBadge(
                    selected = isSelected,
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
        TargetMediaHighlightOverlay(
            visible = isHighlighted,
            nonce = highlightNonce,
            modifier = Modifier.matchParentSize(),
        )
    }
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
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.46f)),
    ) {
        Text(
            text = "新导入",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            maxLines = 1,
        )
    }
}

@Composable
private fun SelectionBadge(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    AppMediaSelectionBadge(selected = selected, modifier = modifier)
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

@Composable
private fun TargetMediaHighlightOverlay(
    visible: Boolean,
    nonce: Int,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val alpha = remember(nonce) { Animatable(0f) }
    val highlightColor = MaterialTheme.colorScheme.primary
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
                .background(highlightColor.copy(alpha = 0.22f * alpha.value))
                .border(
                    width = 4.dp,
                    color = highlightColor.copy(alpha = 0.92f * alpha.value),
                ),
        )
    }
}

private fun resolveCurrentVisibleDateLabel(
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

private fun calculatePhotoFeedScrollProgress(
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

private fun calculatePhotoFeedScrubberScrollOffset(listState: LazyListState): Int {
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
): String? {
    val layoutInfo = listState.layoutInfo
    if (layoutInfo.visibleItemsInfo.isEmpty()) return null
    val viewportWidth = layoutInfo.viewportSize.width.coerceAtLeast(1)
    val viewportCenterX = viewportWidth / 2f
    val viewportCenterY = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    val totalSpacing = (density.columns - 1) * colSpacingPx
    val cellWidth = ((viewportWidth - totalSpacing).toFloat() / density.columns).coerceAtLeast(1f)
    val segmentWidth = cellWidth + colSpacingPx

    return layoutInfo.visibleItemsInfo
        .flatMap { visibleItem ->
            val row = blocks.getOrNull(visibleItem.index) as? PhotoFeedGridRow
            if (row == null) {
                emptyList()
            } else {
                row.items.mapIndexedNotNull { colIndex, item ->
                    if (item.mediaType != AppMediaType.VIDEO) return@mapIndexedNotNull null
                    val centerX = colIndex * segmentWidth + cellWidth / 2f
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
    return "%04d.%02d.%02d".format(displayYear, displayMonth, displayDay)
}

private fun sectionSpacing(density: PhotoFeedDensity): Dp {
    return rowSpacing(density)
}

private fun rowSpacing(density: PhotoFeedDensity): Dp {
    return 2.dp
}

private fun photoFeedThumbnailRequestSize(density: PhotoFeedDensity): Int {
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
    requestSize: Int,
): String {
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
