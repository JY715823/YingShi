package com.example.yingshi.feature.photos
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.components.rememberYingShiMotionEnabled
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.components.yingShiMemoryGlow
import com.example.yingshi.ui.components.yingShiShimmerSweep
import com.example.yingshi.ui.components.yingShiSoftReveal
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiColors
import com.example.yingshi.ui.theme.YingShiMotion
import com.example.yingshi.ui.theme.YingShiThemeTokens
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val PhotoFeedLeadingItemCount = 0
private const val PhotoFeedPendingTargetRefreshGraceMillis = 450L
private const val PhotoFeedNewImportBadgeMillis = 12_000L
private const val PhotoFeedDensityMorphVisibleLimit = 28
private const val PhotoFeedDensitySourceVisibleLimit = 48
private const val PhotoFeedDensityTransitionThumbnailMax = 256

private enum class PhotoFeedDensityTransitionStage {
    IDLE,
    PREVIEWING,
    REBOUNDING,
    COMMITTING,
    SETTLING,
}

private data class PhotoFeedDensityTransitionRequest(
    val token: Int,
    val fromDensity: PhotoFeedDensity,
    val toDensity: PhotoFeedDensity,
    val anchorMediaId: String?,
    val overlayCandidates: List<PhotoFeedDensityTransitionCandidate>,
    val releasePreviewBoundsByMediaId: Map<String, Rect>,
)

private data class PhotoFeedDensityTransitionCandidate(
    val item: PhotoFeedItem,
    val startBounds: Rect,
    val distanceScore: Float,
)

private data class PhotoFeedDensityTransitionOverlayEntry(
    val item: PhotoFeedItem,
    val startBounds: Rect,
    val endBounds: Rect,
)

private data class PhotoFeedHeaderTransitionOverlayEntry(
    val snapshot: PhotoFeedVisibleHeaderSnapshot,
    val startBounds: Rect,
    val endBounds: Rect,
    val matchedFromSource: Boolean,
)

private sealed interface PhotoFeedVisibleHeaderSnapshot {
    val bounds: Rect

    data class Section(
        val header: PhotoFeedSectionHeader,
        val density: PhotoFeedDensity,
        val presentation: PhotoFeedPresentation,
        override val bounds: Rect,
    ) : PhotoFeedVisibleHeaderSnapshot

    data class Day(
        val header: PhotoFeedDayHeader,
        val density: PhotoFeedDensity,
        val presentation: PhotoFeedPresentation,
        override val bounds: Rect,
    ) : PhotoFeedVisibleHeaderSnapshot

    data class TimeBucket(
        val header: PhotoFeedTimeBucketHeader,
        val density: PhotoFeedDensity,
        override val bounds: Rect,
    ) : PhotoFeedVisibleHeaderSnapshot

    data class Collaborator(
        val identity: CollaboratorIdentityUiModel,
        override val bounds: Rect,
    ) : PhotoFeedVisibleHeaderSnapshot

    data class Divider(
        override val bounds: Rect,
    ) : PhotoFeedVisibleHeaderSnapshot
}

private data class PhotoFeedTimeScrubberSnapshot(
    val progress: Float,
    val label: String,
    val showLabel: Boolean,
    val yearMarkers: List<PhotoFeedScrubberYearMarker>,
)

private data class PhotoFeedDensityPreviewScene(
    val sourceLiveAlpha: Float,
    val sourceOverlayAlpha: Float,
    val targetOverlayAlpha: Float,
    val liveTargetAlpha: Float,
    val liveHeaderScale: Float,
    val targetOverlayScale: Float,
)

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
    inlineVideoAutoPlayEnabled: Boolean = false,
    allowOpenMediaWhileSelecting: Boolean = true,
    disabledMediaIds: Set<String> = emptySet(),
    disabledSelectionLabel: String? = null,
    presentation: PhotoFeedPresentation = PhotoFeedPresentation.EMBEDDED,
    animatingDeleteMediaIds: Set<String> = emptySet(),
    isSilentlyRefreshing: Boolean = false,
) {
    val spacing = YingShiThemeTokens.spacing
    val motion = YingShiThemeTokens.motion
    val motionEnabled = rememberYingShiMotionEnabled()
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
    val mediaById = remember(displayFeedItems) {
        displayFeedItems.associateBy { it.mediaId }
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
    var feedViewportBounds by remember { mutableStateOf<Rect?>(null) }
    val itemBoundsByMediaId = remember { mutableStateMapOf<String, Rect>() }
    var densityTransitionStage by remember { mutableStateOf(PhotoFeedDensityTransitionStage.IDLE) }
    // P1: 记录最后滚动方向（正=向下，负=向上，0=静止），供滚动停止后二次预加载使用
    var lastScrollDirection by remember { mutableIntStateOf(0) }
    var densityPreviewState by remember { mutableStateOf<DiscreteZoomPreviewState<PhotoFeedDensity>?>(null) }
    var densityTransitionAnchorMediaId by remember { mutableStateOf<String?>(null) }
    var densityTransitionPreviewCandidates by remember {
        mutableStateOf<List<PhotoFeedDensityTransitionCandidate>>(emptyList())
    }
    var densityTransitionSourceVisibleCandidates by remember {
        mutableStateOf<List<PhotoFeedDensityTransitionCandidate>>(emptyList())
    }
    var densityTransitionToken by remember { mutableIntStateOf(0) }
    var densityTransitionRequest by remember { mutableStateOf<PhotoFeedDensityTransitionRequest?>(null) }
    var densityTransitionOverlayEntries by remember {
        mutableStateOf<List<PhotoFeedDensityTransitionOverlayEntry>>(emptyList())
    }
    var densityTransitionOverlayAnimationActive by remember { mutableStateOf(false) }
    var densityTransitionPreviewProgress by remember { mutableStateOf(0f) }
    var densityTransitionReleaseProgress by remember { mutableStateOf(0f) }
    var densityTransitionTemporarilySuspendInlineVideo by remember { mutableStateOf(false) }
    var densityTransitionSourceHeaderSnapshots by remember {
        mutableStateOf<List<PhotoFeedVisibleHeaderSnapshot>>(emptyList())
    }
    var densityTransitionSourceScrubberSnapshot by remember { mutableStateOf<PhotoFeedTimeScrubberSnapshot?>(null) }
    var densityTransitionTargetHeaderSnapshots by remember {
        mutableStateOf<List<PhotoFeedVisibleHeaderSnapshot>>(emptyList())
    }
    var densityTransitionTargetScrubberSnapshot by remember { mutableStateOf<PhotoFeedTimeScrubberSnapshot?>(null) }
    var densityTransitionTargetLocalBoundsByMediaId by remember {
        mutableStateOf<Map<String, Rect>>(emptyMap())
    }
    val densityMorphProgress = remember { Animatable(1f) }
    val densityHeaderRevealAlpha = remember { Animatable(1f) }
    val feedContentAlpha = remember { Animatable(1f) }

    LaunchedEffect(selectedCollaboratorUserIds) {
        if (!motionEnabled) return@LaunchedEffect
        feedContentAlpha.snapTo(0.6f)
        feedContentAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        )
    }

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
    val densityTransitionEnabled = presentation == PhotoFeedPresentation.MAIN_STREAM &&
        displayFeedItems.isNotEmpty() &&
        !selectionState.isInSelectionMode &&
        !isLoadingMore &&
        loadMoreErrorMessage == null
    val densityTransitionAnimated = densityTransitionEnabled && motionEnabled
    LaunchedEffect(densityName) {
        pageStateStore.savedDensityName = densityName
    }
    val gridEdgePadding = rowSpacing(density)
    val thumbnailRequestSize = photoFeedThumbnailRequestSize(density)
    val transitionThumbnailRequestSize = minOf(
        thumbnailRequestSize,
        PhotoFeedDensityTransitionThumbnailMax,
    )
    PrefetchPhotoFeedThumbnails(
        feedItems = displayFeedItems,
        density = density,
        requestSize = thumbnailRequestSize,
    )
    val inlineVideoAutoPlayAllowed = inlineVideoAutoPlayEnabled &&
        !selectionState.isInSelectionMode &&
        density.columns <= 4 &&
        !densityTransitionTemporarilySuspendInlineVideo
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
    val densityTransitionBlockCache = remember(
        displayFeedItems,
        collaboratorDirectory,
        selectedCollaboratorUserIds,
        timeBucketHours,
    ) {
        mutableMapOf<PhotoFeedDensity, List<PhotoFeedBlock>>()
    }
    fun resolveBlocksForDensity(targetDensity: PhotoFeedDensity): List<PhotoFeedBlock> {
        if (targetDensity == density) return blocks
        return densityTransitionBlockCache.getOrPut(targetDensity) {
            buildCollaborativePhotoFeedBlocks(
                items = displayFeedItems,
                density = targetDensity,
                directory = collaboratorDirectory,
                selectedUserIds = selectedCollaboratorUserIds,
                timeBucketHours = timeBucketHours,
            )
        }
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
    val context = LocalContext.current
    val sessionVersion = AuthSessionManager.sessionVersion
    val accessToken = remember(sessionVersion) {
        AuthSessionManager.peekAccessToken()?.takeIf { it.isNotBlank() }
    }
    LaunchedEffect(visibleInlineVideoIds, mediaById, accessToken, thumbnailRequestSize) {
        val imageLoader = context.imageLoader
        visibleInlineVideoIds.forEach { mediaId ->
            val item = mediaById[mediaId] ?: return@forEach
            val mediaSource = item.mediaSource
            val posterImageUrl = mediaSource.videoPosterImageUrl(item.mediaType)
            val posterImageCacheKey = mediaSource.videoPosterImageCacheKey(item.mediaType)
            val posterImageDiskCacheKey = mediaSource.videoPosterImageDiskCacheKey(item.mediaType)
            if (!posterImageUrl.isNullOrBlank()) {
                backendMediaImageRequest(
                    context = context,
                    url = posterImageUrl,
                    accessToken = accessToken,
                    memoryCacheKey = photoFeedPreviewMemoryCacheKey(
                        url = posterImageUrl,
                        cacheKey = posterImageCacheKey,
                        requestSize = transitionThumbnailRequestSize,
                    ),
                    placeholderMemoryCacheKey = posterImageCacheKey ?: sharedPreviewMemoryCacheKey(posterImageUrl),
                    diskCacheKey = posterImageDiskCacheKey,
                    size = transitionThumbnailRequestSize,
                )?.let(imageLoader::enqueue)
                return@forEach
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
    val currentVisibleDateLabel by remember(listState, blocks, displayFeedItems, density) {
        derivedStateOf {
            resolveCurrentVisibleDateLabel(
                itemIndex = listState.firstVisibleItemIndex,
                blocks = blocks,
                fallbackItems = displayFeedItems,
                density = density,
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
    val densityTransitionOverlayActive = densityTransitionOverlayEntries.isNotEmpty()
    val densityTransitionActiveOverlayProgress = when (densityTransitionStage) {
        PhotoFeedDensityTransitionStage.PREVIEWING -> densityTransitionPreviewProgress
        PhotoFeedDensityTransitionStage.REBOUNDING -> if (densityTransitionOverlayAnimationActive) {
            densityMorphProgress.value
        } else {
            densityTransitionPreviewProgress
        }

        PhotoFeedDensityTransitionStage.COMMITTING -> if (densityTransitionOverlayAnimationActive) {
            densityMorphProgress.value
        } else {
            densityTransitionReleaseProgress
        }

        PhotoFeedDensityTransitionStage.SETTLING -> 1f
        else -> 0f
    }
    val densityTransitionSourceProgress = when (densityTransitionStage) {
        PhotoFeedDensityTransitionStage.PREVIEWING -> densityTransitionPreviewProgress
        PhotoFeedDensityTransitionStage.REBOUNDING -> densityMorphProgress.value
        else -> 0f
    }
    val densityTransitionPreviewTargetProgress = when (densityTransitionStage) {
        PhotoFeedDensityTransitionStage.PREVIEWING -> densityTransitionPreviewProgress
        PhotoFeedDensityTransitionStage.REBOUNDING -> densityMorphProgress.value
        else -> 0f
    }
    val densityTransitionTargetMorphProgress = when (densityTransitionStage) {
        PhotoFeedDensityTransitionStage.PREVIEWING -> densityTransitionPreviewProgress
        PhotoFeedDensityTransitionStage.REBOUNDING -> densityMorphProgress.value
        PhotoFeedDensityTransitionStage.COMMITTING -> if (densityTransitionOverlayAnimationActive) {
            lerpPhotoFeedFloat(
                start = densityTransitionReleaseProgress,
                end = 1f,
                progress = densityMorphProgress.value,
            )
        } else {
            densityTransitionReleaseProgress
        }

        PhotoFeedDensityTransitionStage.SETTLING,
        PhotoFeedDensityTransitionStage.IDLE,
        -> 1f
    }
    val densityTransitionSourceLiveAlpha = when (densityTransitionStage) {
        PhotoFeedDensityTransitionStage.PREVIEWING,
        PhotoFeedDensityTransitionStage.REBOUNDING,
        -> photoFeedDensitySourceSceneAlpha(densityTransitionSourceProgress)

        PhotoFeedDensityTransitionStage.IDLE -> 1f
        else -> 0f
    }
    val densityTransitionTargetPreviewAlpha = photoFeedDensityTargetSceneAlpha(
        progress = densityTransitionPreviewTargetProgress,
    )
    val densityTransitionReleaseTargetAlpha = photoFeedDensityTargetSceneAlpha(
        progress = densityTransitionReleaseProgress,
    )
    val densityTransitionHasTargetSupplementaryOverlay =
        densityTransitionTargetHeaderSnapshots.isNotEmpty() ||
            densityTransitionTargetScrubberSnapshot != null
    val densityTransitionTargetLiveAlpha = when (densityTransitionStage) {
        PhotoFeedDensityTransitionStage.COMMITTING -> if (densityTransitionOverlayAnimationActive) {
            if (densityTransitionHasTargetSupplementaryOverlay) {
                photoFeedDensityCommitLiveTargetAlpha(densityMorphProgress.value)
            } else {
                photoFeedDensityCommitFallbackLiveTargetAlpha(
                    releaseAlpha = densityTransitionReleaseTargetAlpha,
                    progress = densityMorphProgress.value,
                )
            }
        } else {
            densityTransitionReleaseTargetAlpha
        }
        PhotoFeedDensityTransitionStage.SETTLING -> densityHeaderRevealAlpha.value
        PhotoFeedDensityTransitionStage.IDLE -> 1f
        else -> 0f
    }
    val densityTransitionReleaseSourceMediaAlpha = photoFeedDensitySourceMediaAlpha(
        progress = densityTransitionReleaseProgress,
    )
    val densityTransitionSourceOverlayAlpha = when (densityTransitionStage) {
        PhotoFeedDensityTransitionStage.PREVIEWING,
        PhotoFeedDensityTransitionStage.REBOUNDING,
        -> 0f

        PhotoFeedDensityTransitionStage.COMMITTING -> if (densityTransitionOverlayAnimationActive) {
            photoFeedDensityCommitSourceOverlayAlpha(
                releaseAlpha = densityTransitionReleaseSourceMediaAlpha,
                progress = densityMorphProgress.value,
            )
        } else {
            densityTransitionReleaseSourceMediaAlpha
        }

        else -> 0f
    }
    val densityTransitionSourceSupplementaryOverlayAlpha = when (densityTransitionStage) {
        PhotoFeedDensityTransitionStage.PREVIEWING,
        PhotoFeedDensityTransitionStage.REBOUNDING,
        -> 0f

        PhotoFeedDensityTransitionStage.COMMITTING -> if (densityTransitionOverlayAnimationActive) {
            photoFeedDensityCommitSourceOverlayAlpha(
                releaseAlpha = photoFeedDensitySourceSupplementaryAlpha(densityTransitionReleaseProgress),
                progress = densityMorphProgress.value,
            )
        } else {
            photoFeedDensitySourceSupplementaryAlpha(densityTransitionReleaseProgress)
        }

        else -> 0f
    }
    val densityTransitionPreviewTargetHeaderScale = 1f
    val densityTransitionCommitTargetHeaderScale = 1f
    val densityTransitionSettleHeaderScale = 1f
    val densityTransitionTargetOverlayAlpha = when (densityTransitionStage) {
        PhotoFeedDensityTransitionStage.PREVIEWING -> densityTransitionTargetPreviewAlpha
        PhotoFeedDensityTransitionStage.REBOUNDING -> densityTransitionTargetPreviewAlpha
        PhotoFeedDensityTransitionStage.COMMITTING -> if (densityTransitionOverlayAnimationActive) {
            photoFeedDensityCommitTargetOverlayAlpha(
                releaseAlpha = densityTransitionReleaseTargetAlpha,
                progress = densityMorphProgress.value,
            )
        } else {
            densityTransitionReleaseTargetAlpha
        }

        PhotoFeedDensityTransitionStage.SETTLING -> 1f - densityHeaderRevealAlpha.value
        PhotoFeedDensityTransitionStage.IDLE -> 0f
    }
    val densityTransitionTargetSupplementaryOverlayAlpha = when (densityTransitionStage) {
        PhotoFeedDensityTransitionStage.PREVIEWING,
        PhotoFeedDensityTransitionStage.REBOUNDING,
        -> densityTransitionTargetOverlayAlpha * 0.82f

        PhotoFeedDensityTransitionStage.COMMITTING -> densityTransitionTargetOverlayAlpha
        PhotoFeedDensityTransitionStage.SETTLING -> 1f - densityHeaderRevealAlpha.value
        else -> 0f
    }
    val densityTransitionTargetOverlayScale = when (densityTransitionStage) {
        PhotoFeedDensityTransitionStage.PREVIEWING,
        PhotoFeedDensityTransitionStage.REBOUNDING -> densityTransitionPreviewTargetHeaderScale
        PhotoFeedDensityTransitionStage.COMMITTING -> densityTransitionCommitTargetHeaderScale
        PhotoFeedDensityTransitionStage.SETTLING -> densityTransitionSettleHeaderScale
        PhotoFeedDensityTransitionStage.IDLE -> 1f
    }
    val densityTransitionFallbackPreviewActive = (
        densityTransitionStage == PhotoFeedDensityTransitionStage.PREVIEWING ||
            densityTransitionStage == PhotoFeedDensityTransitionStage.REBOUNDING
        ) && densityTransitionOverlayEntries.isEmpty()
    val densityTransitionContentScale = if (densityTransitionFallbackPreviewActive) {
        photoFeedDensityFallbackContentScale(
            previewState = densityPreviewState,
            progress = densityTransitionSourceProgress,
        )
    } else {
        1f
    }
    val densityTransitionSourceOverlayScale = 1f
    val densityTransitionPreviewScene = PhotoFeedDensityPreviewScene(
        sourceLiveAlpha = densityTransitionSourceLiveAlpha,
        sourceOverlayAlpha = densityTransitionSourceOverlayAlpha,
        targetOverlayAlpha = densityTransitionTargetOverlayAlpha,
        liveTargetAlpha = densityTransitionTargetLiveAlpha,
        liveHeaderScale = when (densityTransitionStage) {
            PhotoFeedDensityTransitionStage.PREVIEWING -> 1f - 0.04f * densityTransitionPreviewProgress
            PhotoFeedDensityTransitionStage.REBOUNDING -> 1f - 0.04f * densityTransitionReleaseProgress
            PhotoFeedDensityTransitionStage.COMMITTING -> 0.96f + 0.04f * densityMorphProgress.value
            PhotoFeedDensityTransitionStage.SETTLING -> densityTransitionSettleHeaderScale
            else -> 1f
        },
        targetOverlayScale = densityTransitionTargetOverlayScale,
    )
    val densityTransitionHeaderOverlayProgress = when (densityTransitionStage) {
        PhotoFeedDensityTransitionStage.PREVIEWING -> densityTransitionPreviewTargetProgress
        PhotoFeedDensityTransitionStage.REBOUNDING -> densityTransitionPreviewTargetProgress
        PhotoFeedDensityTransitionStage.COMMITTING -> densityTransitionTargetMorphProgress
        PhotoFeedDensityTransitionStage.SETTLING -> 1f
        PhotoFeedDensityTransitionStage.IDLE -> 0f
    }
    val densityTransitionTargetHeaderMorphEntries = remember(
        densityTransitionSourceHeaderSnapshots,
        densityTransitionTargetHeaderSnapshots,
    ) {
        buildPhotoFeedHeaderTransitionOverlayEntries(
            sourceSnapshots = densityTransitionSourceHeaderSnapshots,
            targetSnapshots = densityTransitionTargetHeaderSnapshots,
        )
    }
    val densityTransitionMatchedHeaderKeys = remember(
        densityTransitionTargetHeaderMorphEntries,
    ) {
        densityTransitionTargetHeaderMorphEntries
            .filter { it.matchedFromSource }
            .mapNotNull { entry -> photoFeedHeaderTransitionKey(entry.snapshot) }
            .toSet()
    }
    val densityTransitionLiveSupplementaryAlpha = when (densityTransitionStage) {
        PhotoFeedDensityTransitionStage.PREVIEWING,
        PhotoFeedDensityTransitionStage.REBOUNDING,
        -> photoFeedDensitySourceSupplementaryAlpha(densityTransitionSourceProgress)

        PhotoFeedDensityTransitionStage.COMMITTING -> densityTransitionTargetLiveAlpha
        PhotoFeedDensityTransitionStage.SETTLING -> densityTransitionPreviewScene.liveTargetAlpha
        PhotoFeedDensityTransitionStage.IDLE -> 1f
    }
    val densityTransitionLiveMediaAlpha = when (densityTransitionStage) {
        PhotoFeedDensityTransitionStage.PREVIEWING,
        PhotoFeedDensityTransitionStage.REBOUNDING,
        -> photoFeedDensitySourceMediaAlpha(densityTransitionSourceProgress)

        PhotoFeedDensityTransitionStage.IDLE,
        -> 1f

        PhotoFeedDensityTransitionStage.COMMITTING -> 0.88f
        PhotoFeedDensityTransitionStage.SETTLING -> lerpPhotoFeedFloat(
            start = 0.88f,
            end = 1f,
            progress = densityHeaderRevealAlpha.value,
        )
    }
    fun resetDensityTransitionState() {
        densityPreviewState = null
        densityTransitionAnchorMediaId = null
        densityTransitionPreviewCandidates = emptyList()
        densityTransitionSourceVisibleCandidates = emptyList()
        densityTransitionRequest = null
        densityTransitionOverlayEntries = emptyList()
        densityTransitionOverlayAnimationActive = false
        densityTransitionPreviewProgress = 0f
        densityTransitionReleaseProgress = 0f
        densityTransitionTemporarilySuspendInlineVideo = false
        densityTransitionSourceHeaderSnapshots = emptyList()
        densityTransitionSourceScrubberSnapshot = null
        densityTransitionTargetHeaderSnapshots = emptyList()
        densityTransitionTargetScrubberSnapshot = null
        densityTransitionTargetLocalBoundsByMediaId = emptyMap()
        densityTransitionStage = PhotoFeedDensityTransitionStage.IDLE
    }
    fun startDensityPreviewFallback(previewState: DiscreteZoomPreviewState<PhotoFeedDensity>) {
        densityPreviewState = previewState
        densityTransitionAnchorMediaId = null
        densityTransitionPreviewCandidates = emptyList()
        densityTransitionOverlayEntries = emptyList()
        densityTransitionTargetHeaderSnapshots = emptyList()
        densityTransitionTargetScrubberSnapshot = null
        densityTransitionTargetLocalBoundsByMediaId = emptyMap()
        densityTransitionOverlayAnimationActive = false
        densityTransitionPreviewProgress = previewState.renderProgress
        densityTransitionTemporarilySuspendInlineVideo = true
        densityTransitionStage = PhotoFeedDensityTransitionStage.PREVIEWING
    }
    fun syncDensityPreview(previewState: DiscreteZoomPreviewState<PhotoFeedDensity>) {
        val viewportBounds = feedViewportBounds ?: run {
            startDensityPreviewFallback(previewState)
            return
        }
        val targetBlocks = resolveBlocksForDensity(previewState.targetLevel)
        val shouldInitializePreview = densityTransitionPreviewCandidates.isEmpty() ||
            densityTransitionAnchorMediaId == null ||
            densityPreviewState?.sourceLevel != previewState.sourceLevel ||
            densityPreviewState?.targetLevel != previewState.targetLevel ||
            densityTransitionTargetLocalBoundsByMediaId.isEmpty()
        val visibleCandidates = if (shouldInitializePreview) {
            visiblePhotoFeedDensityTransitionCandidates(
                blocks = blocks,
                listState = listState,
                itemBoundsByMediaId = itemBoundsByMediaId,
                viewportBounds = viewportBounds,
                density = density,
                densityScope = densityScope,
            )
        } else {
            emptyList()
        }
        val candidates = if (shouldInitializePreview) {
            visibleCandidates.take(PhotoFeedDensityMorphVisibleLimit)
        } else {
            densityTransitionPreviewCandidates
        }
        if (candidates.isEmpty()) {
            startDensityPreviewFallback(previewState)
            return
        }
        val candidateMediaIds = candidates.mapTo(linkedSetOf()) { it.item.mediaId }
        val anchorMediaId = listOfNotNull(
            if (shouldInitializePreview) null else densityTransitionAnchorMediaId,
            densityTransitionAnchorMediaId,
            candidates.firstOrNull()?.item?.mediaId,
            pageStateStore.savedFirstVisibleMediaId,
        ).firstOrNull { it in candidateMediaIds } ?: run {
            startDensityPreviewFallback(previewState)
            return
        }
        val targetLocalBoundsByMediaId = if (shouldInitializePreview) {
            buildPhotoFeedPredictedLocalBoundsByMediaId(
                blocks = resolveBlocksForDensity(previewState.targetLevel),
                targetDensity = previewState.targetLevel,
                viewportBounds = viewportBounds,
                densityScope = densityScope,
                presentation = presentation,
            )
        } else {
            densityTransitionTargetLocalBoundsByMediaId
        }
        val anchorStartBounds = candidates.firstOrNull { it.item.mediaId == anchorMediaId }?.startBounds
            ?: run {
                startDensityPreviewFallback(previewState)
                return
            }
        if (densityTransitionSourceHeaderSnapshots.isEmpty()) {
            densityTransitionSourceHeaderSnapshots = captureVisiblePhotoFeedHeaderSnapshots(
                blocks = blocks,
                listState = listState,
                viewportBounds = viewportBounds,
                density = density,
                presentation = presentation,
                contentStartPx = edgePaddingPx,
            )
        }
        if (densityTransitionSourceScrubberSnapshot == null && scrollAnchors.size > 1) {
            densityTransitionSourceScrubberSnapshot = PhotoFeedTimeScrubberSnapshot(
                progress = displayedScrubberProgress,
                label = displayedScrubberLabel,
                showLabel = scrubberInteracting,
                yearMarkers = scrubberYearMarkers,
            )
        }
        val targetHeaderSnapshots = if (
            shouldInitializePreview ||
            densityTransitionTargetHeaderSnapshots.isEmpty()
        ) {
            buildPhotoFeedPredictedHeaderSnapshots(
                blocks = targetBlocks,
                targetDensity = previewState.targetLevel,
                viewportBounds = viewportBounds,
                densityScope = densityScope,
                presentation = presentation,
                anchorMediaId = anchorMediaId,
                anchorStartBounds = anchorStartBounds,
                targetLocalBoundsByMediaId = targetLocalBoundsByMediaId,
            )
        } else {
            densityTransitionTargetHeaderSnapshots
        }
        val targetScrubberSnapshot = if (
            shouldInitializePreview ||
            densityTransitionTargetScrubberSnapshot == null
        ) {
            buildPhotoFeedTargetScrubberSnapshot(
                blocks = targetBlocks,
                density = previewState.targetLevel,
                fallbackItems = displayFeedItems,
                anchorMediaId = anchorMediaId,
                currentProgress = currentScrollProgress,
            )
        } else {
            densityTransitionTargetScrubberSnapshot
        }
        val overlayEntries = buildPhotoFeedDensityPreviewOverlayEntries(
            candidates = candidates,
            targetLocalBoundsByMediaId = targetLocalBoundsByMediaId,
            anchorMediaId = anchorMediaId,
        )
        if (overlayEntries.isEmpty()) {
            startDensityPreviewFallback(previewState)
            return
        }
        if (shouldInitializePreview) {
            densityTransitionTargetLocalBoundsByMediaId = targetLocalBoundsByMediaId
        }
        densityPreviewState = previewState
        densityTransitionAnchorMediaId = anchorMediaId
        densityTransitionPreviewCandidates = candidates
        densityTransitionOverlayEntries = overlayEntries
        densityTransitionTargetHeaderSnapshots = targetHeaderSnapshots
        densityTransitionTargetScrubberSnapshot = targetScrubberSnapshot
        densityTransitionOverlayAnimationActive = false
        densityTransitionPreviewProgress = previewState.renderProgress
        densityTransitionTemporarilySuspendInlineVideo = true
        densityTransitionStage = PhotoFeedDensityTransitionStage.PREVIEWING
    }
    fun reboundDensityPreview(finalPreviewState: DiscreteZoomPreviewState<PhotoFeedDensity>?) {
        val previewState = finalPreviewState ?: densityPreviewState ?: run {
            resetDensityTransitionState()
            return
        }
        densityPreviewState = previewState
        densityTransitionOverlayAnimationActive = false
        densityTransitionStage = PhotoFeedDensityTransitionStage.REBOUNDING
        coroutineScope.launch {
            densityTransitionOverlayAnimationActive = true
            densityMorphProgress.snapTo(previewState.renderProgress)
            densityMorphProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = if (densityTransitionAnimated) motion.densityPreviewMillis else 0,
                    easing = motion.easing,
                ),
            )
            resetDensityTransitionState()
        }
    }
    fun beginDensityTransition(
        fromDensity: PhotoFeedDensity,
        toDensity: PhotoFeedDensity,
        finalPreviewState: DiscreteZoomPreviewState<PhotoFeedDensity>?,
    ) {
        val viewportBounds = feedViewportBounds
        val previewState = finalPreviewState
        val candidates = densityTransitionPreviewCandidates.ifEmpty {
            visiblePhotoFeedDensityTransitionCandidates(
                blocks = blocks,
                listState = listState,
                itemBoundsByMediaId = itemBoundsByMediaId,
                viewportBounds = viewportBounds,
                density = density,
                densityScope = densityScope,
            ).take(PhotoFeedDensityMorphVisibleLimit)
        }
        if (densityTransitionSourceVisibleCandidates.isEmpty()) {
            densityTransitionSourceVisibleCandidates = visiblePhotoFeedDensityTransitionCandidates(
                blocks = blocks,
                listState = listState,
                itemBoundsByMediaId = itemBoundsByMediaId,
                viewportBounds = viewportBounds,
                density = density,
                densityScope = densityScope,
            ).take(PhotoFeedDensitySourceVisibleLimit)
        }
        if (densityTransitionSourceHeaderSnapshots.isEmpty() && previewState != null && viewportBounds != null) {
            densityTransitionSourceHeaderSnapshots = captureVisiblePhotoFeedHeaderSnapshots(
                blocks = blocks,
                listState = listState,
                viewportBounds = viewportBounds,
                density = fromDensity,
                presentation = presentation,
                contentStartPx = edgePaddingPx,
            )
        }
        if (densityTransitionSourceScrubberSnapshot == null && scrollAnchors.size > 1) {
            densityTransitionSourceScrubberSnapshot = PhotoFeedTimeScrubberSnapshot(
                progress = displayedScrubberProgress,
                label = displayedScrubberLabel,
                showLabel = scrubberInteracting,
                yearMarkers = scrubberYearMarkers,
            )
        }
        val candidateMediaIds = candidates.mapTo(linkedSetOf()) { it.item.mediaId }
        val anchorMediaId = listOfNotNull(
            densityTransitionAnchorMediaId,
            candidates.firstOrNull()?.item?.mediaId,
            pageStateStore.savedFirstVisibleMediaId,
        ).firstOrNull { it in candidateMediaIds }
        val targetLocalBoundsByMediaId = if (previewState != null && viewportBounds != null) {
            densityTransitionTargetLocalBoundsByMediaId.ifEmpty {
                buildPhotoFeedPredictedLocalBoundsByMediaId(
                    blocks = resolveBlocksForDensity(toDensity),
                    targetDensity = toDensity,
                    viewportBounds = viewportBounds,
                    densityScope = densityScope,
                    presentation = presentation,
                )
            }
        } else {
            emptyMap()
        }
        val anchorStartBounds = if (anchorMediaId != null) {
            candidates.firstOrNull { it.item.mediaId == anchorMediaId }?.startBounds
        } else {
            null
        }
        val targetHeaderSnapshots = if (
            previewState != null &&
            viewportBounds != null &&
            anchorMediaId != null &&
            anchorStartBounds != null
        ) {
            densityTransitionTargetHeaderSnapshots.ifEmpty {
                buildPhotoFeedPredictedHeaderSnapshots(
                    blocks = resolveBlocksForDensity(toDensity),
                    targetDensity = toDensity,
                    viewportBounds = viewportBounds,
                    densityScope = densityScope,
                    presentation = presentation,
                    anchorMediaId = anchorMediaId,
                    anchorStartBounds = anchorStartBounds,
                    targetLocalBoundsByMediaId = targetLocalBoundsByMediaId,
                )
            }
        } else {
            emptyList()
        }
        val targetScrubberSnapshot = if (previewState != null && anchorMediaId != null) {
            densityTransitionTargetScrubberSnapshot ?: buildPhotoFeedTargetScrubberSnapshot(
                blocks = resolveBlocksForDensity(toDensity),
                density = toDensity,
                fallbackItems = displayFeedItems,
                anchorMediaId = anchorMediaId,
                currentProgress = currentScrollProgress,
            )
        } else {
            null
        }
        val releasePreviewBoundsByMediaId = if (previewState != null && viewportBounds != null && anchorMediaId != null) {
            val targetBounds = buildPhotoFeedPredictedBoundsByMediaId(
                candidates = candidates,
                targetLocalBoundsByMediaId = targetLocalBoundsByMediaId,
                anchorMediaId = anchorMediaId,
            )
            candidates.associate { candidate ->
                val endBounds = targetBounds[candidate.item.mediaId] ?: candidate.startBounds
                candidate.item.mediaId to interpolatePhotoFeedRect(
                    start = candidate.startBounds,
                    end = endBounds,
                    progress = previewState.renderProgress,
                )
            }
        } else {
            emptyMap()
        }
        val shouldAnimate = densityTransitionAnimated &&
            viewportBounds != null &&
            previewState != null &&
            candidates.isNotEmpty() &&
            anchorMediaId != null &&
            releasePreviewBoundsByMediaId.isNotEmpty() &&
            densityTransitionStage != PhotoFeedDensityTransitionStage.COMMITTING
        densityPreviewState = previewState
        if (!shouldAnimate) {
            resetDensityTransitionState()
            return
        }
        densityTransitionToken += 1
        densityTransitionOverlayAnimationActive = false
        densityTransitionReleaseProgress = previewState.renderProgress
        densityTransitionTargetHeaderSnapshots = targetHeaderSnapshots
        densityTransitionTargetScrubberSnapshot = targetScrubberSnapshot
        densityTransitionTargetLocalBoundsByMediaId = targetLocalBoundsByMediaId
        densityTransitionRequest = PhotoFeedDensityTransitionRequest(
            token = densityTransitionToken,
            fromDensity = fromDensity,
            toDensity = toDensity,
            anchorMediaId = anchorMediaId,
            overlayCandidates = candidates,
            releasePreviewBoundsByMediaId = releasePreviewBoundsByMediaId,
        )
        densityTransitionTemporarilySuspendInlineVideo = true
        densityTransitionStage = PhotoFeedDensityTransitionStage.COMMITTING
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
    var notificationMediaIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var consumedNotificationNonce by remember { mutableIntStateOf(0) }

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

    LaunchedEffect(pageStateStore.pendingNotificationNonce) {
        val nonce = pageStateStore.pendingNotificationNonce
        if (nonce == 0 || nonce == consumedNotificationNonce) return@LaunchedEffect
        consumedNotificationNonce = nonce
        val ids = pageStateStore.pendingNotificationMediaIds
        if (ids.isEmpty()) return@LaunchedEffect
        notificationMediaIds = ids
        delay(PhotoFeedNewImportBadgeMillis)
        if (consumedNotificationNonce == nonce) {
            notificationMediaIds = emptySet()
            pageStateStore.pendingNotificationMediaIds = emptySet()
        }
    }

    LaunchedEffect(scrollTrigger, blocks) {
        val mediaId = pageStateStore.pendingScrollTargetMediaId ?: return@LaunchedEffect
        val highlightNonce = pageStateStore.pendingHighlightNonce
        Log.d("PhotoFeedScreen", "Pending scroll target: mediaId=$mediaId, highlightNonce=$highlightNonce, blocks.size=${blocks.size}")
        val targetBlockIndex = findBlockIndexForMedia(blocks, mediaId)
        if (mediaId != pendingTargetMediaIdSnapshot) {
            pendingTargetMediaIdSnapshot = mediaId
            pendingTargetLoadAttemptBlockCount = -1
        }
        if (targetBlockIndex < 0) {
            Log.d("PhotoFeedScreen", "Target media not found in current blocks, hasMore=$hasMore, isLoadingMore=$isLoadingMore")
            if (!hasMore) {
                if (pendingTargetLoadAttemptBlockCount != blocks.size) {
                    pendingTargetLoadAttemptBlockCount = blocks.size
                    return@LaunchedEffect
                }
                delay(PhotoFeedPendingTargetRefreshGraceMillis)
                if (pageStateStore.pendingScrollTargetMediaId != mediaId) {
                    return@LaunchedEffect
                }
                Log.w("PhotoFeedScreen", "Failed to locate target media: $mediaId")
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
        Log.d("PhotoFeedScreen", "Found target media at index $targetBlockIndex, scrolling...")
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
        if (pageStateStore.pendingAutoOpenViewer) {
            pageStateStore.pendingAutoOpenViewer = false
            val viewerIndex = mediaPositionLookup[mediaId] ?: 0
            Log.d("PhotoFeedScreen", "Auto opening viewer at index $viewerIndex, autoOpenComment=${pageStateStore.pendingAutoOpenComment}")
            onOpenViewer(
                PhotoViewerRoute(
                    mediaItems = displayFeedItems,
                    initialIndex = viewerIndex,
                    sourceLabel = "notification-media",
                ),
            )
        }
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

    // 滚动方向感知的持续预取：自然滑动时（非滑条跳转），按方向预热前方 N 行缩略图，
    // 让用户向上/向下滑时大概率命中内存缓存，毫秒级出图，消除"滑动后空白几秒"体验问题。
    // 复用现成的 prefetchPhotoFeedAroundAnchor 工具函数，零额外内存成本（仅入 Coil 队列）。
    // P1 优化：
    //   - leading/trailing 从 4/12 → 12/24，前方预热范围扩大 2-3 倍，覆盖快速翻页 2-3 屏
    //   - filter 上限从 12 → 24，快速大跳也能触发预加载
    //   - 移除 densityTransitionStage==IDLE 限制（密度切换中也允许预加载，避免切换后空白）
    //   - 记录最后滚动方向，供滚动停止后二次预加载使用
    LaunchedEffect(listState, blocks.size, density, accessToken, thumbnailRequestSize) {
        if (blocks.isEmpty()) return@LaunchedEffect
        var lastIndex = listState.firstVisibleItemIndex
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .drop(1) // 跳过初始位置（已由 PrefetchPhotoFeedThumbnails 覆盖）
            .filter { newIndex ->
                // 仅过滤 density 提交瞬间产生的瞬变（COMMITTING 阶段 blocks 在重组），
                // 其他阶段（PREVIEWING/REBOUNDING/SETTLING/IDLE）都允许预加载
                densityTransitionStage != PhotoFeedDensityTransitionStage.COMMITTING &&
                    abs(newIndex - lastIndex) in 1..24
            }
            .collect { newIndex ->
                val direction = newIndex - lastIndex
                val anchor = newIndex
                lastIndex = newIndex
                lastScrollDirection = direction
                // 向下滑（direction > 0）：多预热下方；向上滑：多预热上方
                // P1: 扩大到 12/24，让前方 2-3 屏都在预热中，配合 Coil 32 并发能跟上快速翻页
                val leading = if (direction < 0) 24 else 12
                val trailing = if (direction > 0) 24 else 12
                prefetchPhotoFeedAroundAnchor(
                    blocks = blocks,
                    anchorIndex = anchor,
                    leadingRows = leading,
                    trailingRows = trailing,
                    context = context,
                    imageLoader = context.imageLoader,
                    accessToken = accessToken,
                    requestSize = thumbnailRequestSize,
                )
            }
    }

    // P1 新增：滚动停止后二次预加载。
    // 用户快速翻页时第一次预加载可能跟不上（请求 enqueue 但未下载完），
    // 滚动停止后立刻再预热一次更大范围（前后 12/24 行），覆盖用户继续翻页的可能方向。
    // 利用 isScrollInProgress 的下降沿触发，仅在滚动结束时跑一次，开销可控。
    LaunchedEffect(listState, blocks.size, density, accessToken, thumbnailRequestSize) {
        if (blocks.isEmpty()) return@LaunchedEffect
        var wasScrolling = false
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling) {
                    wasScrolling = true
                } else if (wasScrolling) {
                    // 下降沿：滚动刚结束，触发二次预加载
                    wasScrolling = false
                    val anchor = listState.firstVisibleItemIndex
                    // 沿用最后滚动方向，侧重预热用户大概率继续翻的方向
                    val (leading, trailing) = if (lastScrollDirection >= 0) 12 to 24 else 24 to 12
                    prefetchPhotoFeedAroundAnchor(
                        blocks = blocks,
                        anchorIndex = anchor,
                        leadingRows = leading,
                        trailingRows = trailing,
                        context = context,
                        imageLoader = context.imageLoader,
                        accessToken = accessToken,
                        requestSize = thumbnailRequestSize,
                    )
                    // 重置方向，避免下次停止时沿用旧方向
                    lastScrollDirection = 0
                }
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
    LaunchedEffect(densityTransitionRequest?.token, density, blocks, feedViewportBounds) {
        val request = densityTransitionRequest ?: return@LaunchedEffect
        if (densityTransitionStage != PhotoFeedDensityTransitionStage.COMMITTING) return@LaunchedEffect
        if (request.toDensity != density) return@LaunchedEffect
        val viewportBounds = feedViewportBounds ?: run {
            resetDensityTransitionState()
            return@LaunchedEffect
        }
        val anchorMediaId = request.anchorMediaId ?: run {
            resetDensityTransitionState()
            return@LaunchedEffect
        }
        val targetBlockIndex = findBlockIndexForMedia(blocks, anchorMediaId)
        if (targetBlockIndex >= 0) {
            val targetScrollOffset = calculatePhotoFeedTargetScrollOffset(listState)
            listState.scrollToItem(
                index = targetBlockIndex,
                scrollOffset = targetScrollOffset,
            )
            withFrameNanos { }
            correctPhotoFeedDensityTransitionAnchorOffset(
                anchorMediaId = anchorMediaId,
                itemBoundsByMediaId = itemBoundsByMediaId,
                viewportBounds = viewportBounds,
                listState = listState,
            )
            withFrameNanos { }
        }
        val overlayEntries = buildPhotoFeedDensityCommitOverlayEntries(
            candidates = request.overlayCandidates,
            releasePreviewBoundsByMediaId = request.releasePreviewBoundsByMediaId,
            itemBoundsByMediaId = itemBoundsByMediaId,
        )
        densityHeaderRevealAlpha.snapTo(0f)
        if (overlayEntries.isEmpty()) {
            densityHeaderRevealAlpha.snapTo(1f)
            resetDensityTransitionState()
            return@LaunchedEffect
        }
        densityTransitionOverlayEntries = overlayEntries
        densityTransitionOverlayAnimationActive = true
        densityMorphProgress.snapTo(0f)
        densityMorphProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = if (densityTransitionAnimated) motion.densityMorphMillis else 0,
                easing = motion.easing,
            ),
        )
        densityTransitionOverlayAnimationActive = false
        densityTransitionOverlayEntries = emptyList()
        densityHeaderRevealAlpha.snapTo(1f)
        resetDensityTransitionState()
    }

    val densityTransitionSourceControlsAlpha = 0f
    val densityTransitionTargetControlsAlpha = when (densityTransitionStage) {
        PhotoFeedDensityTransitionStage.PREVIEWING,
        PhotoFeedDensityTransitionStage.REBOUNDING,
        PhotoFeedDensityTransitionStage.COMMITTING,
        -> densityTransitionTargetSupplementaryOverlayAlpha * 0.82f

        PhotoFeedDensityTransitionStage.SETTLING -> 1f - densityHeaderRevealAlpha.value
        PhotoFeedDensityTransitionStage.IDLE -> 0f
    }
    val densityTransitionLiveControlsAlpha = when (densityTransitionStage) {
        PhotoFeedDensityTransitionStage.PREVIEWING,
        PhotoFeedDensityTransitionStage.REBOUNDING,
        -> photoFeedDensitySourceSupplementaryAlpha(densityTransitionSourceProgress)

        PhotoFeedDensityTransitionStage.COMMITTING -> densityTransitionTargetLiveAlpha
        PhotoFeedDensityTransitionStage.SETTLING,
        PhotoFeedDensityTransitionStage.IDLE,
        -> 1f
    }
    val densityTransitionControlsScale by animateFloatAsState(
        targetValue = if (densityTransitionStage != PhotoFeedDensityTransitionStage.IDLE) 0.92f else 1f,
        animationSpec = if (motionEnabled) {
            spring(dampingRatio = 0.6f, stiffness = 400f)
        } else {
            tween(0)
        },
        label = "densityTransitionControlsScale",
    )

    Column(
        modifier = modifier
            .fillMaxSize(),
    ) {
        if (collaboratorDirectory.all.isNotEmpty()) {
            Box {
                densityPreviewState?.let { previewState ->
                    if (densityTransitionSourceControlsAlpha > 0f) {
                        PhotoFeedCollaboratorControlsSceneRow(
                            directory = collaboratorDirectory,
                            selectedUserIds = selectedCollaboratorUserIds,
                            timeBucketHours = timeBucketHours,
                            density = previewState.sourceLevel,
                            alpha = densityTransitionSourceControlsAlpha,
                            scale = densityTransitionControlsScale,
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
                    if (densityTransitionTargetControlsAlpha > 0f) {
                        PhotoFeedCollaboratorControlsSceneRow(
                            directory = collaboratorDirectory,
                            selectedUserIds = selectedCollaboratorUserIds,
                            timeBucketHours = timeBucketHours,
                            density = previewState.targetLevel,
                            alpha = densityTransitionTargetControlsAlpha,
                            scale = 1f,
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
                }
                if (densityTransitionLiveControlsAlpha > 0f) {
                    PhotoFeedCollaboratorControlsSceneRow(
                        directory = collaboratorDirectory,
                        selectedUserIds = selectedCollaboratorUserIds,
                        timeBucketHours = timeBucketHours,
                        density = density,
                        alpha = densityTransitionLiveControlsAlpha,
                        scale = densityTransitionControlsScale,
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
            }
        }
        AnimatedVisibility(
            visible = isSilentlyRefreshing,
            enter = fadeIn(animationSpec = if (motionEnabled) tween(300) else tween(0)),
            exit = fadeOut(animationSpec = if (motionEnabled) tween(300) else tween(0)),
        ) {
            val indicatorColor = YingShiThemeTokens.colors.glassStroke.copy(alpha = 0.44f)
            val infiniteTransition = rememberInfiniteTransition(label = "silentRefresh")
            val sweepProgress by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = if (motionEnabled) {
                    infiniteRepeatable(
                        animation = tween(1800, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    )
                } else {
                    infiniteRepeatable(
                        animation = tween(0),
                        repeatMode = RepeatMode.Restart,
                    )
                },
                label = "silentRefreshSweep",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .drawBehind {
                        val sweepWidth = size.width * 0.4f
                        val left = (size.width + sweepWidth) * sweepProgress - sweepWidth
                        drawRect(
                            color = indicatorColor,
                            topLeft = Offset(left, 0f),
                            size = Size(sweepWidth, size.height),
                        )
                    }
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    feedViewportBounds = coordinates.boundsInRoot()
                }
                .clipToBounds()
                .discreteZoomLevelGesture(
                    enabled = !selectionState.isInSelectionMode,
                    levels = PhotoFeedDensity.entries.toList(),
                    currentLevel = density,
                    onLevelChange = updateDensity,
                    commitOnGestureEnd = true,
                    onPreviewStateChange = { previewState ->
                        if (!densityTransitionAnimated ||
                            densityTransitionStage == PhotoFeedDensityTransitionStage.REBOUNDING ||
                            densityTransitionStage == PhotoFeedDensityTransitionStage.COMMITTING ||
                            densityTransitionStage == PhotoFeedDensityTransitionStage.SETTLING
                        ) {
                            return@discreteZoomLevelGesture
                        }
                        if (previewState != null) {
                            syncDensityPreview(previewState)
                        }
                    },
                    onGestureFinished = { committed, finalPreviewState, committedTargetLevel ->
                        densityPreviewState = finalPreviewState
                        val nextDensity = committedTargetLevel as? PhotoFeedDensity
                        when {
                            !committed || nextDensity == null || nextDensity == density -> {
                                if (
                                    densityTransitionAnimated &&
                                    densityTransitionStage == PhotoFeedDensityTransitionStage.PREVIEWING &&
                                    finalPreviewState != null
                                ) {
                                    reboundDensityPreview(finalPreviewState)
                                } else {
                                    resetDensityTransitionState()
                                }
                            }

                            densityTransitionAnimated -> {
                                beginDensityTransition(
                                    fromDensity = density,
                                    toDensity = nextDensity,
                                    finalPreviewState = finalPreviewState,
                                )
                            }

                            else -> {
                                resetDensityTransitionState()
                            }
                        }
                    },
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
            PhotoFeedAtmosphereLayer(
                modifier = Modifier.matchParentSize(),
                presentation = presentation,
            )

            val revealedRowKeys = remember { mutableStateSetOf<String>() }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = densityTransitionLiveMediaAlpha * feedContentAlpha.value
                        scaleX = densityTransitionContentScale
                        scaleY = densityTransitionContentScale
                        transformOrigin = TransformOrigin.Center
                    },
                verticalArrangement = Arrangement.spacedBy(sectionSpacing(density)),
                contentPadding = PaddingValues(
                    top = 0.dp,
                    start = gridEdgePadding,
                    end = gridEdgePadding,
                    bottom = spacing.lg + bottomOverlayPadding,
                ),
            ) {
                var gridRowCount = 0
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
                        is PhotoFeedSectionHeader -> Box(
                            modifier = Modifier.graphicsLayer {
                                alpha = photoFeedLiveHeaderAlpha(
                                    stage = densityTransitionStage,
                                    baseAlpha = densityTransitionLiveSupplementaryAlpha,
                                    matchedHeaderKeys = densityTransitionMatchedHeaderKeys,
                                    snapshot = PhotoFeedVisibleHeaderSnapshot.Section(
                                        header = block,
                                        density = density,
                                        presentation = presentation,
                                        bounds = Rect.Zero,
                                    ),
                                )
                                scaleX = densityTransitionPreviewScene.liveHeaderScale
                                scaleY = densityTransitionPreviewScene.liveHeaderScale
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                                .then(if (motionEnabled) Modifier.animateItem() else Modifier),
                        ) {
                            PhotoFeedSectionHeaderRow(
                                header = block,
                                density = density,
                                presentation = presentation,
                            )
                        }
                        is PhotoFeedDayHeader -> Box(
                            modifier = Modifier.graphicsLayer {
                                alpha = photoFeedLiveHeaderAlpha(
                                    stage = densityTransitionStage,
                                    baseAlpha = densityTransitionLiveSupplementaryAlpha,
                                    matchedHeaderKeys = densityTransitionMatchedHeaderKeys,
                                    snapshot = PhotoFeedVisibleHeaderSnapshot.Day(
                                        header = block,
                                        density = density,
                                        presentation = presentation,
                                        bounds = Rect.Zero,
                                    ),
                                )
                                scaleX = densityTransitionPreviewScene.liveHeaderScale
                                scaleY = densityTransitionPreviewScene.liveHeaderScale
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                                .then(if (motionEnabled) Modifier.animateItem() else Modifier),
                        ) {
                            PhotoFeedDayHeaderRow(
                                header = block,
                                density = density,
                                presentation = presentation,
                            )
                        }
                        is PhotoFeedTimeBucketHeader -> Box(
                            modifier = Modifier.graphicsLayer(
                                alpha = photoFeedLiveHeaderAlpha(
                                    stage = densityTransitionStage,
                                    baseAlpha = densityTransitionLiveSupplementaryAlpha,
                                    matchedHeaderKeys = densityTransitionMatchedHeaderKeys,
                                    snapshot = PhotoFeedVisibleHeaderSnapshot.TimeBucket(
                                        header = block,
                                        density = density,
                                        bounds = Rect.Zero,
                                    ),
                                ),
                            )
                                .then(if (motionEnabled) Modifier.animateItem() else Modifier),
                        ) {
                            PhotoFeedTimeBucketHeaderRow(
                                header = block,
                                density = density,
                            )
                        }
                        is PhotoFeedCollaboratorHeader -> Box(
                            modifier = Modifier.graphicsLayer(
                                alpha = photoFeedLiveHeaderAlpha(
                                    stage = densityTransitionStage,
                                    baseAlpha = densityTransitionLiveSupplementaryAlpha,
                                    matchedHeaderKeys = densityTransitionMatchedHeaderKeys,
                                    snapshot = PhotoFeedVisibleHeaderSnapshot.Collaborator(
                                        identity = block.identity,
                                        bounds = Rect.Zero,
                                    ),
                                ),
                            )
                                .then(if (motionEnabled) Modifier.animateItem() else Modifier),
                        ) {
                            PhotoFeedCollaboratorHeaderRow(
                                identity = block.identity,
                            )
                        }
                        is PhotoFeedCollaboratorDivider -> Box(
                            modifier = Modifier.graphicsLayer(
                                alpha = photoFeedLiveHeaderAlpha(
                                    stage = densityTransitionStage,
                                    baseAlpha = densityTransitionLiveSupplementaryAlpha,
                                    matchedHeaderKeys = densityTransitionMatchedHeaderKeys,
                                    snapshot = PhotoFeedVisibleHeaderSnapshot.Divider(
                                        bounds = Rect.Zero,
                                    ),
                                ),
                            )
                                .then(if (motionEnabled) Modifier.animateItem() else Modifier),
                        ) {
                            PhotoFeedCollaboratorDividerRow()
                        }
                        is PhotoFeedGridRow -> PhotoFeedGridRowContent(
                            row = block,
                            rowIndex = gridRowCount++,
                            rowKey = block.key,
                            revealedRowKeys = revealedRowKeys,
                            density = density,
                            selectionState = selectionState,
                            disabledMediaIds = disabledMediaIds,
                            disabledSelectionLabel = disabledSelectionLabel,
                            selectionFlash = selectionFlashByMediaId,
                            highlightedMediaId = highlightedTargetMediaId,
                            highlightNonce = highlightedTargetNonce,
                            newImportedMediaIds = newImportedMediaIds,
                            restoredMediaIds = restoredMediaIds,
                            notificationMediaIds = notificationMediaIds,
                            inlineVideoAutoPlayEnabled = inlineVideoAutoPlayAllowed,
                            allowOpenMediaWhileSelecting = allowOpenMediaWhileSelecting,
                            playingInlineVideoId = playingInlineVideoId,
                            activeInlineVideoId = activeInlineVideoId,
                            pausedInlineVideoIds = pausedInlineVideoIds,
                            inlineVideoProgressById = inlineVideoProgressById,
                            animatingDeleteMediaIds = animatingDeleteMediaIds,
                            // P1 修复：stagger 序号基于视口起点计算（跳转和翻页完全一致）。
                            // 旧逻辑用全局 rowIndex + skipRevealStagger 短路，导致滑条跳转无动画、
                            // 慢滚动新行 delay 8s。现统一为相对视口序号 ≤160ms。
                            staggerAnchorIndex = listState.firstVisibleItemIndex,
                            onToggleInlineVideo = onToggleInlineVideo,
                            onInlineVideoProgressChange = { item, progress ->
                                inlineVideoProgressById = inlineVideoProgressById + (item.mediaId to progress)
                            },
                            onItemBoundsChange = { mediaId, bounds ->
                                if (bounds == null) {
                                    itemBoundsByMediaId.remove(mediaId)
                                } else {
                                    itemBoundsByMediaId[mediaId] = bounds
                                }
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
                                        val heroOrigin = itemBoundsByMediaId[item.mediaId]?.let { bounds ->
                                            HeroOrigin(mediaId = item.mediaId, boundsInRoot = bounds)
                                        }
                                        onOpenViewer(
                                            PhotoViewerRoute(
                                                mediaItems = displayFeedItems,
                                                initialIndex = mediaPositionLookup[item.mediaId] ?: 0,
                                                sourceLabel = "photos-feed",
                                                showSmallAlbumSegments = false,
                                                heroOrigin = heroOrigin,
                                            ),
                                        )
                                        selectionState
                                    },
                                )
                            },
                            onOpenMedia = { item ->
                                val heroOrigin = itemBoundsByMediaId[item.mediaId]?.let { bounds ->
                                    HeroOrigin(mediaId = item.mediaId, boundsInRoot = bounds)
                                }
                                onOpenViewer(
                                    PhotoViewerRoute(
                                        mediaItems = displayFeedItems,
                                        initialIndex = mediaPositionLookup[item.mediaId] ?: 0,
                                        sourceLabel = "photos-feed",
                                        showSmallAlbumSegments = false,
                                        heroOrigin = heroOrigin,
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
                            modifier = if (motionEnabled) Modifier.animateItem() else Modifier,
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
            if (densityTransitionStage == PhotoFeedDensityTransitionStage.COMMITTING &&
                densityTransitionSourceSupplementaryOverlayAlpha > 0f &&
                densityTransitionSourceHeaderSnapshots.isNotEmpty()
            ) {
                PhotoFeedDensityHeaderOverlay(
                    snapshots = densityTransitionSourceHeaderSnapshots,
                    alpha = densityTransitionSourceSupplementaryOverlayAlpha,
                    scale = densityTransitionSourceOverlayScale,
                    viewportBounds = feedViewportBounds,
                    modifier = Modifier.matchParentSize(),
                )
            }

            if (densityTransitionStage == PhotoFeedDensityTransitionStage.COMMITTING &&
                densityTransitionSourceOverlayAlpha > 0f &&
                densityTransitionSourceVisibleCandidates.isNotEmpty()
            ) {
                PhotoFeedStaticMediaOverlay(
                    candidates = densityTransitionSourceVisibleCandidates,
                    viewportBounds = feedViewportBounds,
                    alpha = densityTransitionSourceOverlayAlpha,
                    thumbnailRequestSize = transitionThumbnailRequestSize,
                    modifier = Modifier.matchParentSize(),
                )
            }

            if (densityTransitionStage != PhotoFeedDensityTransitionStage.IDLE &&
                densityTransitionTargetSupplementaryOverlayAlpha > 0f &&
                densityTransitionTargetHeaderMorphEntries.isNotEmpty()
            ) {
                PhotoFeedDensityHeaderMorphOverlay(
                    entries = densityTransitionTargetHeaderMorphEntries,
                    progress = densityTransitionHeaderOverlayProgress,
                    alpha = densityTransitionTargetSupplementaryOverlayAlpha,
                    scale = densityTransitionPreviewScene.targetOverlayScale,
                    viewportBounds = feedViewportBounds,
                    modifier = Modifier.matchParentSize(),
                )
            }

            if (densityTransitionOverlayEntries.isNotEmpty()) {
                PhotoFeedDensityMorphOverlay(
                    entries = densityTransitionOverlayEntries,
                    viewportBounds = feedViewportBounds,
                    progress = densityTransitionActiveOverlayProgress,
                    thumbnailRequestSize = transitionThumbnailRequestSize,
                    fadeAtEdges = false,
                    sceneAlpha = densityTransitionTargetOverlayAlpha,
                    modifier = Modifier.matchParentSize(),
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = (scrubberVisible && scrollAnchors.size > 1) ||
                    (densityTransitionStage == PhotoFeedDensityTransitionStage.COMMITTING &&
                        densityTransitionSourceSupplementaryOverlayAlpha > 0f) ||
                    densityTransitionTargetSupplementaryOverlayAlpha > 0f ||
                    (densityTransitionStage != PhotoFeedDensityTransitionStage.IDLE &&
                        densityTransitionLiveSupplementaryAlpha > 0f),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp)
                    .fillMaxHeight(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(184.dp),
                ) {
                    if (scrubberVisible && scrollAnchors.size > 1) {
                        PhotoFeedTimeScrubber(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer {
                                    alpha = densityTransitionLiveSupplementaryAlpha
                                    scaleX = densityTransitionPreviewScene.liveHeaderScale * densityTransitionContentScale
                                    scaleY = densityTransitionPreviewScene.liveHeaderScale * densityTransitionContentScale
                                    transformOrigin = TransformOrigin(1f, 0.5f)
                                },
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
                                    ?.let { anchor -> formatScrubberDateLabel(anchor.timeMillis, density) }
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
                                        // 跳转后预热目标位置周围缩略图，消除"向上翻空白"体验问题。
                                        // 主因：LazyColumn 默认 prefetch 仅约 1 项，跳转后向上滑时
                                        // 新进入组合的行需现请求缩略图（带 accessToken 的 backend 请求），
                                        // 响应延迟可达数百毫秒至数秒，表现为"一片空白"。
                                        // P1: 扩大预热范围 8/4 → 16/8，覆盖前后各 2 屏，配合 Coil 32 并发
                                        // 让用户跳转后立即向上/下翻 1-2 屏都能命中缓存毫秒级出图。
                                        prefetchPhotoFeedAroundAnchor(
                                            blocks = blocks,
                                            anchorIndex = anchor.itemIndex,
                                            leadingRows = 16,
                                            trailingRows = 8,
                                            context = context,
                                            imageLoader = context.imageLoader,
                                            accessToken = accessToken,
                                            requestSize = thumbnailRequestSize,
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
                    densityTransitionTargetScrubberSnapshot?.let { snapshot ->
                        if (densityTransitionTargetSupplementaryOverlayAlpha > 0f) {
                            PhotoFeedTimeScrubber(
                                modifier = Modifier
                                    .matchParentSize()
                                    .graphicsLayer(
                                        alpha = densityTransitionTargetSupplementaryOverlayAlpha,
                                        scaleX = densityTransitionPreviewScene.targetOverlayScale,
                                        scaleY = densityTransitionPreviewScene.targetOverlayScale,
                                        transformOrigin = TransformOrigin(1f, 0.5f),
                                    ),
                                progress = snapshot.progress,
                                label = snapshot.label,
                                showLabel = snapshot.showLabel,
                                yearMarkers = snapshot.yearMarkers,
                                onSeekToProgress = {},
                                onInteractingChanged = {},
                            )
                        }
                    }
                    densityTransitionSourceScrubberSnapshot?.let { snapshot ->
                        if (densityTransitionStage == PhotoFeedDensityTransitionStage.COMMITTING &&
                            densityTransitionSourceSupplementaryOverlayAlpha > 0f
                        ) {
                            PhotoFeedTimeScrubber(
                                modifier = Modifier
                                    .matchParentSize()
                                    .graphicsLayer(
                                        alpha = densityTransitionSourceSupplementaryOverlayAlpha,
                                        scaleX = densityTransitionSourceOverlayScale,
                                        scaleY = densityTransitionSourceOverlayScale,
                                        transformOrigin = TransformOrigin(1f, 0.5f),
                                    ),
                                progress = snapshot.progress,
                                label = snapshot.label,
                                showLabel = snapshot.showLabel,
                                yearMarkers = snapshot.yearMarkers,
                                onSeekToProgress = {},
                                onInteractingChanged = {},
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoFeedAtmosphereLayer(
    modifier: Modifier = Modifier,
    presentation: PhotoFeedPresentation = PhotoFeedPresentation.EMBEDDED,
) {
    val colors = YingShiThemeTokens.colors
    val motion = YingShiThemeTokens.motion
    val atmosphereAlpha = if (presentation == PhotoFeedPresentation.MAIN_STREAM) {
        motion.feedAtmosphereAlpha * 1.42f
    } else {
        motion.feedAtmosphereAlpha * 0.82f
    }
    val breathOverlay = breathBrush(colors = colors, atmosphereAlpha = atmosphereAlpha)
    Box(
        modifier = modifier
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colors.glowWash.copy(alpha = 0.30f * atmosphereAlpha),
                        colors.sectionBackground.copy(alpha = 0.16f * atmosphereAlpha),
                        Color.Transparent,
                    ),
                    center = Offset(0f, 0f),
                    radius = 820f,
                ),
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.glowWash.copy(alpha = 0.10f * atmosphereAlpha),
                        Color.Transparent,
                        colors.sectionBackground.copy(alpha = 0.08f * atmosphereAlpha),
                    ),
                ),
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        if (presentation == PhotoFeedPresentation.MAIN_STREAM) {
                            colors.memoryContainer.copy(alpha = 0.18f * atmosphereAlpha)
                        } else {
                            colors.memoryContainer.copy(alpha = 0.08f * atmosphereAlpha)
                        },
                        Color.Transparent,
                    ),
                    center = Offset(980f, 180f),
                    radius = if (presentation == PhotoFeedPresentation.MAIN_STREAM) 640f else 420f,
                ),
            )
            .background(breathOverlay),
    )
}

@Composable
private fun breathBrush(
    colors: YingShiColors,
    atmosphereAlpha: Float,
): Brush {
    val motionEnabled = rememberYingShiMotionEnabled()
    val transition = rememberInfiniteTransition(label = "atmosphereBreath")
    val breathPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "atmosphereBreathPhase",
    )
    if (!motionEnabled) return Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
    val breathAlpha = (0.5f + 0.5f * kotlin.math.sin(breathPhase * 2 * Math.PI.toFloat())) * 0.06f * atmosphereAlpha
    return Brush.radialGradient(
        colors = listOf(
            colors.glowWash.copy(alpha = breathAlpha),
            Color.Transparent,
        ),
        center = Offset(640f, 300f),
        radius = 500f,
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
                when (item.mediaType) {
                    AppMediaType.IMAGE -> {
                        val url = mediaSource.thumbnailModelUrl(item.mediaType) ?: return@mapNotNull null
                        PrefetchTarget(
                            url = url,
                            cacheKey = mediaSource.thumbnailModelCacheKey(item.mediaType),
                            diskCacheKey = mediaSource.thumbnailModelDiskCacheKey(item.mediaType),
                            mediaType = item.mediaType,
                            mimeType = mediaSource?.mimeType,
                        )
                    }

                    AppMediaType.VIDEO -> {
                        val posterImageUrl = mediaSource.videoPosterImageUrl(item.mediaType)
                        if (!posterImageUrl.isNullOrBlank()) {
                            PrefetchTarget(
                                url = posterImageUrl,
                                cacheKey = mediaSource.videoPosterImageCacheKey(item.mediaType),
                                diskCacheKey = mediaSource.videoPosterImageDiskCacheKey(item.mediaType),
                                mediaType = item.mediaType,
                                mimeType = mediaSource?.mimeType,
                                extractVideoPoster = false,
                            )
                        } else {
                            val posterVideoUrl = mediaSource.videoPosterVideoUrl(item.mediaType)
                                ?: return@mapNotNull null
                            PrefetchTarget(
                                url = posterVideoUrl,
                                cacheKey = mediaSource.videoPosterVideoCacheKey(item.mediaType),
                                diskCacheKey = mediaSource.videoPosterVideoDiskCacheKey(item.mediaType),
                                mediaType = item.mediaType,
                                mimeType = mediaSource?.mimeType,
                                extractVideoPoster = true,
                            )
                        }
                    }
                }
            }
            .distinctBy { "${it.mediaType}:${it.extractVideoPoster}:${it.cacheKey ?: it.url}" }
    }

    LaunchedEffect(context, prefetchTargets, accessToken) {
        val imageLoader = context.imageLoader
        prefetchTargets.forEach { target ->
            if (target.mediaType == AppMediaType.VIDEO && target.extractVideoPoster) {
                prefetchVideoPoster(
                    context = context,
                    url = target.url,
                    accessToken = accessToken,
                    cacheKey = target.cacheKey,
                    diskCacheKey = target.diskCacheKey,
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
                diskCacheKey = target.diskCacheKey,
                size = requestSize,
            )?.let(imageLoader::enqueue)
        }
    }
}

private data class PrefetchTarget(
    val url: String,
    val cacheKey: String?,
    val diskCacheKey: String? = cacheKey,
    val mediaType: AppMediaType,
    val mimeType: String?,
    val extractVideoPoster: Boolean = false,
)

/**
 * 时间滑条跳转后预热目标位置周围的缩略图。
 *
 * 解决"滑条跳转后向上翻空白"体验问题：LazyColumn 默认 prefetch 仅约 1 项，
 * 跳转后向上滑时新进入组合的行需现请求缩略图（带 accessToken 的 backend 请求），
 * 响应延迟可达数百毫秒至数秒。本函数在跳转完成后立即将目标位置前后 N 行的
 * 缩略图塞入 Coil 队列，用户向上滑时大概率命中内存缓存，毫秒级出图。
 *
 * @param blocks 完整的 PhotoFeedBlock 列表
 * @param anchorIndex 跳转目标在 blocks 中的索引
 * @param leadingRows 向上预取的行数（向上翻的概率高，多预取）
 * @param trailingRows 向下预取的行数
 */
private fun prefetchPhotoFeedAroundAnchor(
    blocks: List<PhotoFeedBlock>,
    anchorIndex: Int,
    leadingRows: Int,
    trailingRows: Int,
    context: android.content.Context,
    imageLoader: coil.ImageLoader,
    accessToken: String?,
    requestSize: Int,
) {
    val fromIndex = (anchorIndex - leadingRows).coerceAtLeast(0)
    val toIndex = (anchorIndex + trailingRows).coerceAtMost(blocks.lastIndex)
    if (fromIndex > toIndex) return

    for (i in fromIndex..toIndex) {
        val row = blocks.getOrNull(i) as? PhotoFeedGridRow ?: continue
        row.items.forEach { item ->
            val mediaSource = item.mediaSource ?: return@forEach
            when (item.mediaType) {
                AppMediaType.IMAGE -> {
                    val url = mediaSource.thumbnailModelUrl(item.mediaType) ?: return@forEach
                    val cacheKey = mediaSource.thumbnailModelCacheKey(item.mediaType)
                    val diskKey = mediaSource.thumbnailModelDiskCacheKey(item.mediaType)
                    backendMediaImageRequest(
                        context = context,
                        url = url,
                        accessToken = accessToken,
                        memoryCacheKey = photoFeedPreviewMemoryCacheKey(
                            url = url,
                            cacheKey = cacheKey,
                            requestSize = requestSize,
                        ),
                        placeholderMemoryCacheKey = cacheKey ?: sharedPreviewMemoryCacheKey(url),
                        diskCacheKey = diskKey,
                        size = requestSize,
                    )?.let(imageLoader::enqueue)
                }

                AppMediaType.VIDEO -> {
                    val posterImageUrl = mediaSource.videoPosterImageUrl(item.mediaType)
                    if (!posterImageUrl.isNullOrBlank()) {
                        val cacheKey = mediaSource.videoPosterImageCacheKey(item.mediaType)
                        val diskKey = mediaSource.videoPosterImageDiskCacheKey(item.mediaType)
                        backendMediaImageRequest(
                            context = context,
                            url = posterImageUrl,
                            accessToken = accessToken,
                            memoryCacheKey = photoFeedPreviewMemoryCacheKey(
                                url = posterImageUrl,
                                cacheKey = cacheKey,
                                requestSize = requestSize,
                            ),
                            placeholderMemoryCacheKey = cacheKey ?: sharedPreviewMemoryCacheKey(posterImageUrl),
                            diskCacheKey = diskKey,
                            size = requestSize,
                        )?.let(imageLoader::enqueue)
                    }
                }
            }
        }
    }
}

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
    val motionEnabled = rememberYingShiMotionEnabled()
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

    var isInteracting by remember { mutableStateOf(false) }
    val thumbScale by animateFloatAsState(
        targetValue = if (isInteracting) 1.12f else 1f,
        animationSpec = tween(durationMillis = if (motionEnabled) 120 else 0),
        label = "thumbScale",
    )

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
            AnimatedContent(
                targetState = label,
                transitionSpec = {
                    if (motionEnabled) {
                        (fadeIn(tween(200, easing = FastOutSlowInEasing)) +
                            slideInVertically(tween(200, easing = FastOutSlowInEasing)) { it / 4 })
                            .togetherWith(
                                fadeOut(tween(200, easing = FastOutSlowInEasing)) +
                                    slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { -it / 4 }
                            )
                    } else {
                        fadeIn(tween(0)).togetherWith(fadeOut(tween(0)))
                    }
                },
                label = "scrubberLabelTransition",
            ) { targetLabel ->
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = colors.raisedSurface.copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.58f)),
                    shadowElevation = 1.dp,
                ) {
                    Text(
                        text = targetLabel,
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
                .graphicsLayer {
                    scaleX = thumbScale
                    scaleY = thumbScale
                }
                .clip(RoundedCornerShape(999.dp))
                .background(colors.glassStroke.copy(alpha = 0.28f))
                .border(
                    width = 1.5.dp,
                    color = colors.glassStroke.copy(alpha = 0.44f),
                    shape = RoundedCornerShape(999.dp),
                )
                .pointerInput(scrubberHeightPx) {
                    detectDragGestures(
                        onDragStart = {
                            isInteracting = true
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
                            isInteracting = false
                            lastDispatchedProgress = Float.NaN
                            latestOnInteractingChanged(false)
                        },
                        onDragCancel = {
                            isInteracting = false
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
internal fun PhotoFeedSectionHeaderRow(
    header: PhotoFeedSectionHeader,
    density: PhotoFeedDensity,
    presentation: PhotoFeedPresentation,
    modifier: Modifier = Modifier,
) {
    val metrics = photoFeedSectionHeaderMetrics(
        granularity = header.granularity,
        density = density,
        presentation = presentation,
    )
    val palette = photoFeedTimeTitlePalette(
        granularity = header.granularity,
        month = header.month,
        colors = YingShiThemeTokens.colors,
    )
    PhotoFeedTimeHeaderText(
        text = header.title,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 4.dp,
                top = metrics.topPadding,
                bottom = metrics.bottomPadding,
            ),
        textStyle = metrics.style,
        granularity = header.granularity,
        palette = palette,
        presentation = presentation,
    )
}

@Composable
internal fun PhotoFeedSectionHeaderRow(title: String) {
    val palette = photoFeedTimeTitlePalette(
        granularity = PhotoFeedTimeGranularity.MONTH,
        month = null,
        colors = YingShiThemeTokens.colors,
    )
    PhotoFeedTimeHeaderText(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
        textStyle = MaterialTheme.typography.headlineSmall.copy(
            fontSize = 30.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.ExtraBold,
        ),
        granularity = PhotoFeedTimeGranularity.MONTH,
        palette = palette,
        presentation = PhotoFeedPresentation.EMBEDDED,
    )
}

@Composable
private fun PhotoFeedCollaboratorControlsSceneRow(
    directory: CollaboratorDirectorySnapshot,
    selectedUserIds: Set<String>,
    timeBucketHours: Int,
    density: PhotoFeedDensity,
    alpha: Float,
    scale: Float,
    onToggleCollaborator: (String) -> Unit,
    onTimeBucketHoursChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (alpha <= 0f) return
    Box(
        modifier = modifier.graphicsLayer(
            alpha = alpha.coerceIn(0f, 1f),
            scaleX = scale,
            scaleY = scale,
            transformOrigin = TransformOrigin(1f, 0.5f),
        ),
    ) {
        PhotoFeedCollaboratorControlsRow(
            directory = directory,
            selectedUserIds = selectedUserIds,
            timeBucketHours = timeBucketHours,
            density = density,
            onToggleCollaborator = onToggleCollaborator,
            onTimeBucketHoursChange = onTimeBucketHoursChange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PhotoFeedCollaboratorControlsRow(
    directory: CollaboratorDirectorySnapshot,
    selectedUserIds: Set<String>,
    timeBucketHours: Int,
    density: PhotoFeedDensity,
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
            if (density.columns <= 4) {
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
    // 鲜艳蓝色调，与整体融洽：线条用浅蓝渐变，中心装饰用亮蓝
    val vividBlue = Color(0xFF4B9FFF)
    val lineColor = Color(0xFF7DC4FF).copy(alpha = 0.85f)
    val accentColor = vividBlue.copy(alpha = 0.85f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.5.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, lineColor, accentColor)
                    ),
                ),
        )
        Surface(
            modifier = Modifier
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(3.dp),
            color = Color.Transparent,
            shadowElevation = 2.dp,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFB4E0FF), vividBlue)
                        ),
                    )
                    .border(1.dp, vividBlue.copy(alpha = 0.65f), RoundedCornerShape(3.dp)),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.5.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(accentColor, lineColor, Color.Transparent)
                    ),
                ),
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
    val spacing = YingShiThemeTokens.spacing
    val motionEnabled = rememberYingShiMotionEnabled()
    val message = if (selectionEmpty) {
        "选择一个账号，发现属于你们的时刻"
    } else if (hasCollaborators) {
        "这里还没有照片，换个视角看看"
    } else {
        "还没有可以展示的协作账号"
    }
    Surface(
        modifier = modifier.yingShiSoftReveal(
            visible = true,
            motionEnabled = motionEnabled,
        ),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = colors.sectionBackground.copy(alpha = 0.68f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.58f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = colors.textSecondary.copy(alpha = 0.28f),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
internal fun PhotoFeedDayHeaderRow(
    header: PhotoFeedDayHeader,
    density: PhotoFeedDensity,
    presentation: PhotoFeedPresentation,
    modifier: Modifier = Modifier,
) {
    val metrics = photoFeedDayHeaderMetrics(
        density = density,
        presentation = presentation,
    )
    val palette = photoFeedTimeTitlePalette(
        granularity = PhotoFeedTimeGranularity.DAY,
        month = header.month,
        colors = YingShiThemeTokens.colors,
    )
    PhotoFeedTimeHeaderText(
        text = header.title,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = metrics.topPadding, bottom = metrics.bottomPadding),
        textStyle = metrics.style,
        granularity = PhotoFeedTimeGranularity.DAY,
        palette = palette,
        presentation = presentation,
    )
}

@Composable
internal fun PhotoFeedDayHeaderRow(title: String) {
    val palette = photoFeedTimeTitlePalette(
        granularity = PhotoFeedTimeGranularity.DAY,
        month = null,
        colors = YingShiThemeTokens.colors,
    )
    PhotoFeedTimeHeaderText(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 6.dp, bottom = 5.dp),
        textStyle = MaterialTheme.typography.titleLarge.copy(
            fontSize = 22.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
        ),
        granularity = PhotoFeedTimeGranularity.DAY,
        palette = palette,
        presentation = PhotoFeedPresentation.EMBEDDED,
    )
}

private data class PhotoFeedHeaderMetrics(
    val style: TextStyle,
    val topPadding: Dp,
    val bottomPadding: Dp,
)

private data class PhotoFeedTimeTitlePalette(
    val base: Color,
    val glint: Color,
    val glow: Color,
    val shadow: Color,
    val mist: Color,
)

@Composable
private fun PhotoFeedTimeHeaderText(
    text: String,
    textStyle: TextStyle,
    granularity: PhotoFeedTimeGranularity,
    palette: PhotoFeedTimeTitlePalette,
    presentation: PhotoFeedPresentation,
    modifier: Modifier = Modifier,
) {
    val motionEnabled = rememberYingShiMotionEnabled()
    val isYearHero = presentation == PhotoFeedPresentation.MAIN_STREAM &&
        granularity == PhotoFeedTimeGranularity.YEAR
    Box(
        modifier = modifier.yingShiSoftReveal(
            visible = true,
            motionEnabled = motionEnabled,
        ),
    ) {
        if (presentation == PhotoFeedPresentation.MAIN_STREAM) {
            if (isYearHero) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .yingShiShimmerSweep(enabled = true, motionEnabled = motionEnabled),
                )
                Text(
                    text = text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = 4.dp)
                        .graphicsLayer {
                            scaleX = 1.016f
                            scaleY = 1.04f
                        },
                    style = textStyle.copy(
                        shadow = Shadow(
                            color = palette.shadow.copy(alpha = 0.54f),
                            offset = Offset(0f, 5.2f),
                            blurRadius = 28f,
                        ),
                    ),
                    color = palette.mist.copy(alpha = 0.34f),
                )
                Text(
                    text = text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(x = (-1).dp, y = (-2).dp),
                    style = textStyle.copy(
                        shadow = Shadow(
                            color = palette.glint.copy(alpha = 0.96f),
                            offset = Offset(0f, -2.2f),
                            blurRadius = 24f,
                        ),
                    ),
                    color = palette.glint.copy(alpha = 0.56f),
                )
                Text(
                    text = text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = 1.008f
                            scaleY = 1.02f
                        },
                    style = textStyle.copy(
                        shadow = Shadow(
                            color = palette.glow.copy(alpha = 0.80f),
                            offset = Offset(0f, 0f),
                            blurRadius = 34f,
                        ),
                    ),
                    color = palette.glow.copy(alpha = 0.36f),
                )
            }
            Text(
                text = text,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = if (isYearHero) 3.dp else 2.dp),
                style = textStyle.copy(
                    shadow = Shadow(
                        color = palette.shadow.copy(alpha = if (isYearHero) 0.48f else 0.40f),
                        offset = Offset(0f, if (isYearHero) 4.4f else 3.8f),
                        blurRadius = if (isYearHero) 22f else 18f,
                    ),
                ),
                color = palette.mist.copy(alpha = if (isYearHero) 0.28f else 0.24f),
            )
            Text(
                text = text,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = 1.dp, y = if (isYearHero) (-2).dp else (-1).dp),
                style = textStyle.copy(
                    shadow = Shadow(
                        color = palette.glint.copy(alpha = if (isYearHero) 0.90f else 0.84f),
                        offset = Offset(0f, if (isYearHero) -2f else -1.6f),
                        blurRadius = if (isYearHero) 22f else 18f,
                    ),
                ),
                color = palette.glint.copy(alpha = if (isYearHero) 0.48f else 0.42f),
            )
            Text(
                text = text,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        if (isYearHero) {
                            scaleX = 1.012f
                            scaleY = 1.018f
                        }
                    },
                style = textStyle.copy(
                    shadow = Shadow(
                        color = palette.glow.copy(alpha = if (isYearHero) 0.70f else 0.58f),
                        offset = Offset(0f, 0f),
                        blurRadius = if (isYearHero) 28f else 22f,
                    ),
                ),
                color = palette.base.copy(alpha = if (isYearHero) 0.98f else 1f),
            )
        } else {
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                style = textStyle.copy(
                    shadow = Shadow(
                        color = palette.glow.copy(alpha = 0.18f),
                        offset = Offset(0f, 1.2f),
                        blurRadius = 8f,
                    ),
                ),
                color = palette.base.copy(alpha = 0.90f),
            )
        }
    }
}

@Composable
private fun photoFeedSectionHeaderMetrics(
    granularity: PhotoFeedTimeGranularity,
    density: PhotoFeedDensity,
    presentation: PhotoFeedPresentation,
): PhotoFeedHeaderMetrics {
    val isMain = presentation == PhotoFeedPresentation.MAIN_STREAM
    return when (granularity) {
        PhotoFeedTimeGranularity.YEAR -> PhotoFeedHeaderMetrics(
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = if (isMain) 40.sp else 32.sp,
                lineHeight = if (isMain) 44.sp else 36.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.34).sp,
            ),
            topPadding = if (isMain) 18.dp else 12.dp,
            bottomPadding = 8.dp,
        )

        PhotoFeedTimeGranularity.MONTH -> PhotoFeedHeaderMetrics(
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = if (isMain) {
                    if (density == PhotoFeedDensity.OVERVIEW_8) 34.sp else 32.sp
                } else {
                    29.sp
                },
                lineHeight = if (isMain) 38.sp else 34.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.15).sp,
            ),
            topPadding = if (isMain) 14.dp else 12.dp,
            bottomPadding = if (density.columns >= 8) 8.dp else 6.dp,
        )

        PhotoFeedTimeGranularity.DAY -> PhotoFeedHeaderMetrics(
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = if (isMain) 24.sp else 22.sp,
                lineHeight = if (isMain) 28.sp else 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.08).sp,
            ),
            topPadding = if (isMain) 7.dp else 6.dp,
            bottomPadding = 5.dp,
        )
    }
}

@Composable
private fun photoFeedDayHeaderMetrics(
    density: PhotoFeedDensity,
    presentation: PhotoFeedPresentation,
): PhotoFeedHeaderMetrics {
    return photoFeedSectionHeaderMetrics(
        granularity = PhotoFeedTimeGranularity.DAY,
        density = density,
        presentation = presentation,
    )
}

private fun photoFeedTimeTitlePalette(
    granularity: PhotoFeedTimeGranularity,
    month: Int?,
    colors: YingShiColors,
): PhotoFeedTimeTitlePalette {
    return when (granularity) {
        PhotoFeedTimeGranularity.YEAR -> PhotoFeedTimeTitlePalette(
            base = Color(0xFF305C83),
            glint = Color(0xFFFFF6E9),
            glow = Color(0xFF9CE4FF),
            shadow = Color(0xFF69CFFF),
            mist = Color(0xFFE9F7FF),
        )

        PhotoFeedTimeGranularity.MONTH -> when (month) {
            12, 1, 2 -> PhotoFeedTimeTitlePalette(
                base = Color(0xFF35546E),
                glint = Color(0xFFF1F7FF),
                glow = Color(0xFFBADFFF),
                shadow = Color(0xFF8FCFFF),
                mist = Color(0xFFDFF3FF),
            )

            3, 4, 5 -> PhotoFeedTimeTitlePalette(
                base = Color(0xFF266853),
                glint = Color(0xFFF0FFF8),
                glow = Color(0xFFB9F0E1),
                shadow = Color(0xFF8ADBC8),
                mist = Color(0xFFE5FFF5),
            )

            6, 7, 8 -> PhotoFeedTimeTitlePalette(
                base = Color(0xFF325B7A),
                glint = Color(0xFFEFFFFF),
                glow = Color(0xFFAEEBFF),
                shadow = Color(0xFF83D8FF),
                mist = Color(0xFFE5FAFF),
            )

            else -> PhotoFeedTimeTitlePalette(
                base = Color(0xFF7A5A3E),
                glint = Color(0xFFFFF2DE),
                glow = Color(0xFFFFD9B8),
                shadow = Color(0xFFEAB98C),
                mist = Color(0xFFFFF2E6),
            )
        }

        PhotoFeedTimeGranularity.DAY -> when (month) {
            12, 1, 2 -> PhotoFeedTimeTitlePalette(
                base = Color(0xFF486074),
                glint = Color(0xFFF4F9FF),
                glow = Color(0xFFD0E9FF),
                shadow = Color(0xFFB3D9FF),
                mist = Color(0xFFE8F5FF),
            )

            3, 4, 5 -> PhotoFeedTimeTitlePalette(
                base = Color(0xFF47675E),
                glint = Color(0xFFF5FFF9),
                glow = Color(0xFFD1F3E9),
                shadow = Color(0xFFB3E7D9),
                mist = Color(0xFFEFFFF7),
            )

            6, 7, 8 -> PhotoFeedTimeTitlePalette(
                base = Color(0xFF466175),
                glint = Color(0xFFF2FBFF),
                glow = Color(0xFFD1F0FF),
                shadow = Color(0xFFAFDEFF),
                mist = Color(0xFFE9F9FF),
            )

            else -> PhotoFeedTimeTitlePalette(
                base = Color(0xFF6A5A46),
                glint = Color(0xFFFFF6E8),
                glow = Color(0xFFFFE2C8),
                shadow = Color(0xFFF1CFAD),
                mist = Color(0xFFFFF5EA),
            )
        }
    }
}

@Composable
private fun PhotoFeedGridRowContent(
    row: PhotoFeedGridRow,
    rowIndex: Int,
    rowKey: String,
    revealedRowKeys: MutableSet<String>,
    density: PhotoFeedDensity,
    selectionState: PhotoFeedSelectionState,
    disabledMediaIds: Set<String>,
    disabledSelectionLabel: String?,
    selectionFlash: Map<String, SelectionNumberFlash>,
    highlightedMediaId: String?,
    highlightNonce: Int,
    newImportedMediaIds: Set<String>,
    restoredMediaIds: Set<String>,
    notificationMediaIds: Set<String>,
    inlineVideoAutoPlayEnabled: Boolean,
    allowOpenMediaWhileSelecting: Boolean,
    playingInlineVideoId: String?,
    activeInlineVideoId: String?,
    pausedInlineVideoIds: Set<String>,
    inlineVideoProgressById: Map<String, InlineVideoPlaybackProgress>,
    onToggleInlineVideo: (PhotoFeedItem) -> Unit,
    onInlineVideoProgressChange: (PhotoFeedItem, InlineVideoPlaybackProgress) -> Unit,
    onItemBoundsChange: (String, Rect?) -> Unit,
    thumbnailRequestSize: Int,
    onMediaClick: (PhotoFeedItem) -> Unit,
    onOpenMedia: (PhotoFeedItem) -> Unit,
    onMediaLongPress: (PhotoFeedItem) -> Unit,
    animatingDeleteMediaIds: Set<String> = emptySet(),
    // P1 修复：保留参数签名以减小改动面，但内部不再使用——所有场景统一走完整 stagger。
    @Suppress("UNUSED_PARAMETER") skipRevealStagger: Boolean = false,
    // 视口起点索引用于计算相对 stagger 序号；不传则用全局 rowIndex（仅降级兼容）
    staggerAnchorIndex: Int = 0,
    modifier: Modifier = Modifier,
) {
    val spacing = rowSpacing(density)
    val motionEnabled = rememberYingShiMotionEnabled()
    var rowVisible by remember { mutableStateOf(rowKey in revealedRowKeys) }
    LaunchedEffect(Unit) {
        if (!rowVisible) {
            // P1 修复：stagger 基于视口相对序号，而非全局 rowIndex。
            // 此前 `delay(rowIndex * 40L)` 用 LazyColumn 全局索引：
            //   - 慢滚动时新行 rowIndex=200+，delay=8000ms，导致"等几秒才开始动画"
            //   - 滑条跳转到第 100 行时 delay=4000ms，作者因此加了 skipRevealStagger 短路
            //     让滑条跳转完全跳过 stagger，结果"滑条定位没动画"，与前后翻页不一致
            // 现改为 `delay((rowIndex - staggerAnchorIndex).coerceIn(0, 4) * 40L)`：
            //   - 相对当前视口起点计算 stagger 序号（0/1/2/3/4）
            //   - 最大 160ms 上限，无论跳转还是慢滚动都立刻启动入场
            //   - 滑条跳转和前后翻页完全一致，所有行都有完整 220ms 入场动画 + 波浪感
            if (motionEnabled) {
                val staggerIndex = (rowIndex - staggerAnchorIndex).coerceIn(0, 4)
                delay(staggerIndex * 40L)
            }
            rowVisible = true
            revealedRowKeys.add(rowKey)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .yingShiSoftReveal(visible = rowVisible, motionEnabled = motionEnabled),
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        row.items.forEach { item ->
            key(item.mediaId) {
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
                    isNotificationHighlighted = item.mediaId in notificationMediaIds,
                    inlineVideoAutoPlayEnabled = inlineVideoAutoPlayEnabled,
                    allowOpenMediaWhileSelecting = allowOpenMediaWhileSelecting,
                    isInlineVideoPlaying = playingInlineVideoId == item.mediaId,
                    isInlineVideoActive = activeInlineVideoId == item.mediaId,
                    isInlineVideoPaused = item.mediaId in pausedInlineVideoIds,
                    inlineVideoProgress = inlineVideoProgressById[item.mediaId],
                    modifier = Modifier.weight(1f),
                    thumbnailRequestSize = thumbnailRequestSize,
                    onBoundsChange = { bounds -> onItemBoundsChange(item.mediaId, bounds) },
                    onClick = { onMediaClick(item) },
                    onOpenMedia = { onOpenMedia(item) },
                    onLongPress = { onMediaLongPress(item) },
                    onToggleInlineVideo = { onToggleInlineVideo(item) },
                    onInlineVideoProgressChange = { progress -> onInlineVideoProgressChange(item, progress) },
                    isDeleting = item.mediaId in animatingDeleteMediaIds,
                )
            }
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
    isNotificationHighlighted: Boolean,
    inlineVideoAutoPlayEnabled: Boolean,
    allowOpenMediaWhileSelecting: Boolean,
    isInlineVideoPlaying: Boolean,
    isInlineVideoActive: Boolean,
    isInlineVideoPaused: Boolean,
    inlineVideoProgress: InlineVideoPlaybackProgress?,
    modifier: Modifier = Modifier,
    thumbnailRequestSize: Int,
    onBoundsChange: (Rect?) -> Unit,
    onClick: () -> Unit,
    onOpenMedia: () -> Unit,
    onLongPress: () -> Unit,
    onToggleInlineVideo: () -> Unit,
    onInlineVideoProgressChange: (InlineVideoPlaybackProgress) -> Unit,
    isDeleting: Boolean = false,
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
    val deleteAlpha by animateFloatAsState(
        targetValue = if (isDeleting) 0f else 1f,
        animationSpec = tween(durationMillis = if (motionEnabled) 200 else 0, easing = FastOutSlowInEasing),
        label = "photoFeedCardDeleteAlpha",
    )
    val deleteScale by animateFloatAsState(
        targetValue = if (isDeleting) 0.8f else 1f,
        animationSpec = tween(durationMillis = if (motionEnabled) 200 else 0, easing = FastOutSlowInEasing),
        label = "photoFeedCardDeleteScale",
    )
    DisposableEffect(item.mediaId) {
        onDispose {
            onBoundsChange(null)
        }
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = itemScale * deleteScale
                scaleY = itemScale * deleteScale
                alpha = deleteAlpha
            }
            .clipToBounds()
            .background(Color.Transparent)
            .onGloballyPositioned { coordinates ->
                onBoundsChange(coordinates.boundsInRoot())
            }
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
            // 开启加载指示器：滑条跳转后缩略图未命中缓存时显示加载圈，
            // 避免 palette 占位色与背景色相近时"伪空白"体验。
            // 正常滚动时图片通常已缓存，不会触发加载圈。
            showLoadingIndicator = true,
            showVideoPlayOverlay = !(supportsInlineVideo || showSelectionVideoMarker),
        )

        YingShiMediaFrame(
            modifier = Modifier.matchParentSize(),
            selected = isSelected,
            memoryActive = isNewImported || isRestored || isHighlighted || isNotificationHighlighted,
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
            visible = isNewImported || isRestored || isHighlighted || isNotificationHighlighted,
            warm = isNewImported || isRestored,
            nonce = if (isHighlighted) highlightNonce else item.mediaId.hashCode(),
            motionEnabled = motionEnabled,
            modifier = Modifier.matchParentSize(),
        )

        if (isNewImported) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                colors.goldAccent.copy(alpha = 0.12f),
                                Color.Transparent,
                            ),
                            radius = 280f,
                        ),
                    ),
            )
        }

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
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 6.dp)
                    .background(colors.glassStroke.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                    .border(0.5.dp, colors.glassStroke.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
                    .padding(4.dp),
            ) {
                InlineVideoPlaybackButton(
                    isPlaying = isInlineVideoActive && !isInlineVideoPaused,
                    onClick = onToggleInlineVideo,
                )
            }
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
        } else if (isNotificationHighlighted) {
            YingShiMemoryBadge(
                text = "通知",
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

@Composable
private fun PhotoFeedDensityMorphOverlay(
    entries: List<PhotoFeedDensityTransitionOverlayEntry>,
    viewportBounds: Rect?,
    progress: Float,
    thumbnailRequestSize: Int,
    fadeAtEdges: Boolean,
    sceneAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val clampedSceneAlpha = sceneAlpha.coerceIn(0f, 1f)
    if (entries.isEmpty() || clampedSceneAlpha <= 0f) return
    val localDensity = LocalDensity.current
    val viewportLeft = viewportBounds?.left ?: 0f
    val viewportTop = viewportBounds?.top ?: 0f
    Box(modifier = modifier.clipToBounds()) {
        entries.forEach { entry ->
            key(entry.item.mediaId) {
                val currentBounds = interpolatePhotoFeedRect(
                    start = entry.startBounds,
                    end = entry.endBounds,
                    progress = clampedProgress,
                )
                val baseAlpha = if (fadeAtEdges) {
                    when {
                        clampedProgress < 0.12f -> 0.80f + (clampedProgress / 0.12f) * 0.20f
                        clampedProgress > 0.90f -> 1f - ((clampedProgress - 0.90f) / 0.10f).coerceIn(0f, 1f) * 0.28f
                        else -> 1f
                    }.coerceIn(0f, 1f)
                } else {
                    1f
                }
                val overlayAlpha = clampedSceneAlpha *
                    baseAlpha *
                    photoFeedMorphOverlayViewportAlpha(currentBounds, viewportBounds)
                val startBounds = entry.startBounds
                val widthDp = with(localDensity) { startBounds.width.toDp() }
                val heightDp = with(localDensity) { startBounds.height.toDp() }
                val translateX = currentBounds.left - startBounds.left
                val translateY = currentBounds.top - startBounds.top
                val scaleX = (currentBounds.width / startBounds.width.coerceAtLeast(1f)).coerceAtLeast(0.01f)
                val scaleY = (currentBounds.height / startBounds.height.coerceAtLeast(1f)).coerceAtLeast(0.01f)
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (startBounds.left - viewportLeft).roundToInt(),
                                y = (startBounds.top - viewportTop).roundToInt(),
                            )
                        }
                        .width(widthDp)
                        .height(heightDp)
                        .graphicsLayer {
                            alpha = overlayAlpha
                            translationX = translateX
                            translationY = translateY
                            this.scaleX = scaleX
                            this.scaleY = scaleY
                            transformOrigin = TransformOrigin(0f, 0f)
                        },
                ) {
                    AppContentMediaThumbnail(
                        mediaSource = entry.item.mediaSource,
                        mediaType = entry.item.mediaType,
                        palette = entry.item.palette,
                        modifier = Modifier.matchParentSize(),
                        contentDescription = entry.item.mediaId,
                        requestSize = thumbnailRequestSize,
                        showLoadingIndicator = false,
                        showVideoPlayOverlay = true,
                    )
                    YingShiMediaFrame(
                        modifier = Modifier.matchParentSize(),
                        selected = false,
                        memoryActive = false,
                        topScrimAlpha = if (entry.item.mediaType == AppMediaType.VIDEO) 0.20f else 0.12f,
                        bottomGlowAlpha = 0.12f,
                    )
                    if (entry.item.mediaType == AppMediaType.VIDEO) {
                        VideoDurationBadge(
                            durationMillis = entry.item.gridVideoBadgeDurationMillis(progress = null),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoFeedStaticMediaOverlay(
    candidates: List<PhotoFeedDensityTransitionCandidate>,
    viewportBounds: Rect?,
    alpha: Float,
    thumbnailRequestSize: Int,
    modifier: Modifier = Modifier,
) {
    val clampedAlpha = alpha.coerceIn(0f, 1f)
    if (candidates.isEmpty() || clampedAlpha <= 0f) return
    val localDensity = LocalDensity.current
    val viewportLeft = viewportBounds?.left ?: 0f
    val viewportTop = viewportBounds?.top ?: 0f
    Box(modifier = modifier.clipToBounds()) {
        candidates.forEach { candidate ->
            key(candidate.item.mediaId) {
                val bounds = candidate.startBounds
                val widthDp = with(localDensity) { bounds.width.toDp() }
                val heightDp = with(localDensity) { bounds.height.toDp() }
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (bounds.left - viewportLeft).roundToInt(),
                                y = (bounds.top - viewportTop).roundToInt(),
                            )
                        }
                        .width(widthDp)
                        .height(heightDp)
                        .graphicsLayer {
                            this.alpha = clampedAlpha *
                                photoFeedMorphOverlayViewportAlpha(bounds, viewportBounds)
                        },
                ) {
                    AppContentMediaThumbnail(
                        mediaSource = candidate.item.mediaSource,
                        mediaType = candidate.item.mediaType,
                        palette = candidate.item.palette,
                        modifier = Modifier.matchParentSize(),
                        contentDescription = candidate.item.mediaId,
                        requestSize = thumbnailRequestSize,
                        showLoadingIndicator = false,
                        showVideoPlayOverlay = true,
                    )
                    YingShiMediaFrame(
                        modifier = Modifier.matchParentSize(),
                        selected = false,
                        memoryActive = false,
                        topScrimAlpha = if (candidate.item.mediaType == AppMediaType.VIDEO) 0.20f else 0.12f,
                        bottomGlowAlpha = 0.12f,
                    )
                    if (candidate.item.mediaType == AppMediaType.VIDEO) {
                        VideoDurationBadge(
                            durationMillis = candidate.item.gridVideoBadgeDurationMillis(progress = null),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoFeedDensityHeaderOverlay(
    snapshots: List<PhotoFeedVisibleHeaderSnapshot>,
    alpha: Float,
    scale: Float,
    viewportBounds: Rect?,
    modifier: Modifier = Modifier,
) {
    if (snapshots.isEmpty() || alpha <= 0f) return
    val localDensity = LocalDensity.current
    val viewportLeft = viewportBounds?.left ?: 0f
    val viewportTop = viewportBounds?.top ?: 0f
    Box(modifier = modifier.clipToBounds()) {
        snapshots.forEach { snapshot ->
            val widthDp = with(localDensity) { snapshot.bounds.width.toDp() }
            val heightDp = with(localDensity) { snapshot.bounds.height.toDp() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (snapshot.bounds.left - viewportLeft).roundToInt(),
                            y = (snapshot.bounds.top - viewportTop).roundToInt(),
                        )
                    }
                    .width(widthDp)
                    .height(heightDp)
                    .graphicsLayer {
                        this.alpha = alpha
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = photoFeedDensityTransformOrigin(
                            bounds = snapshot.bounds,
                            viewportBounds = viewportBounds,
                        )
                    },
            ) {
                when (snapshot) {
                    is PhotoFeedVisibleHeaderSnapshot.Section -> PhotoFeedSectionHeaderRow(
                        header = snapshot.header,
                        density = snapshot.density,
                        presentation = snapshot.presentation,
                        modifier = Modifier.matchParentSize(),
                    )

                    is PhotoFeedVisibleHeaderSnapshot.Day -> PhotoFeedDayHeaderRow(
                        header = snapshot.header,
                        density = snapshot.density,
                        presentation = snapshot.presentation,
                        modifier = Modifier.matchParentSize(),
                    )

                    is PhotoFeedVisibleHeaderSnapshot.TimeBucket -> PhotoFeedTimeBucketHeaderRow(
                        header = snapshot.header,
                        density = snapshot.density,
                    )

                    is PhotoFeedVisibleHeaderSnapshot.Collaborator -> PhotoFeedCollaboratorHeaderRow(
                        identity = snapshot.identity,
                        modifier = Modifier.matchParentSize(),
                    )

                    is PhotoFeedVisibleHeaderSnapshot.Divider -> PhotoFeedCollaboratorDividerRow(
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoFeedDensityHeaderMorphOverlay(
    entries: List<PhotoFeedHeaderTransitionOverlayEntry>,
    progress: Float,
    alpha: Float,
    scale: Float,
    viewportBounds: Rect?,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty() || alpha <= 0f) return
    val localDensity = LocalDensity.current
    val viewportLeft = viewportBounds?.left ?: 0f
    val viewportTop = viewportBounds?.top ?: 0f
    val clampedProgress = progress.coerceIn(0f, 1f)
    Box(modifier = modifier.clipToBounds()) {
        entries.forEach { entry ->
            val currentBounds = if (entry.matchedFromSource) {
                interpolatePhotoFeedRect(
                    start = entry.startBounds,
                    end = entry.endBounds,
                    progress = clampedProgress,
                )
            } else {
                entry.endBounds
            }
            val widthDp = with(localDensity) { currentBounds.width.toDp() }
            val heightDp = with(localDensity) { currentBounds.height.toDp() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (currentBounds.left - viewportLeft).roundToInt(),
                            y = (currentBounds.top - viewportTop).roundToInt(),
                        )
                    }
                    .width(widthDp)
                    .height(heightDp)
                    .graphicsLayer {
                        this.alpha = if (entry.matchedFromSource) {
                            (alpha * lerpPhotoFeedFloat(
                                start = 0.62f,
                                end = 1f,
                                progress = photoFeedSmoothStep(clampedProgress),
                            )).coerceIn(0f, 1f)
                        } else {
                            (alpha * photoFeedSmoothStep(((clampedProgress - 0.12f) / 0.88f).coerceIn(0f, 1f)))
                                .coerceIn(0f, 1f)
                        }
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = photoFeedDensityTransformOrigin(
                            bounds = currentBounds,
                            viewportBounds = viewportBounds,
                        )
                    },
            ) {
                when (val snapshot = entry.snapshot) {
                    is PhotoFeedVisibleHeaderSnapshot.Section -> PhotoFeedSectionHeaderRow(
                        header = snapshot.header,
                        density = snapshot.density,
                        presentation = snapshot.presentation,
                        modifier = Modifier.matchParentSize(),
                    )

                    is PhotoFeedVisibleHeaderSnapshot.Day -> PhotoFeedDayHeaderRow(
                        header = snapshot.header,
                        density = snapshot.density,
                        presentation = snapshot.presentation,
                        modifier = Modifier.matchParentSize(),
                    )

                    is PhotoFeedVisibleHeaderSnapshot.TimeBucket -> PhotoFeedTimeBucketHeaderRow(
                        header = snapshot.header,
                        density = snapshot.density,
                    )

                    is PhotoFeedVisibleHeaderSnapshot.Collaborator -> PhotoFeedCollaboratorHeaderRow(
                        identity = snapshot.identity,
                        modifier = Modifier.matchParentSize(),
                    )

                    is PhotoFeedVisibleHeaderSnapshot.Divider -> PhotoFeedCollaboratorDividerRow(
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }
    }
}

private fun captureVisiblePhotoFeedHeaderSnapshots(
    blocks: List<PhotoFeedBlock>,
    listState: LazyListState,
    viewportBounds: Rect,
    density: PhotoFeedDensity,
    presentation: PhotoFeedPresentation,
    contentStartPx: Float,
): List<PhotoFeedVisibleHeaderSnapshot> {
    return listState.layoutInfo.visibleItemsInfo.mapNotNull { visibleItem ->
        val bounds = Rect(
            left = viewportBounds.left + contentStartPx,
            top = viewportBounds.top + visibleItem.offset,
            right = viewportBounds.right - contentStartPx,
            bottom = viewportBounds.top + visibleItem.offset + visibleItem.size,
        )
        when (val block = blocks.getOrNull(visibleItem.index)) {
            is PhotoFeedSectionHeader -> PhotoFeedVisibleHeaderSnapshot.Section(
                header = block,
                density = density,
                presentation = presentation,
                bounds = bounds,
            )

            is PhotoFeedDayHeader -> PhotoFeedVisibleHeaderSnapshot.Day(
                header = block,
                density = density,
                presentation = presentation,
                bounds = bounds,
            )

            is PhotoFeedTimeBucketHeader -> PhotoFeedVisibleHeaderSnapshot.TimeBucket(
                header = block,
                density = density,
                bounds = bounds,
            )

            is PhotoFeedCollaboratorHeader -> PhotoFeedVisibleHeaderSnapshot.Collaborator(
                identity = block.identity,
                bounds = bounds,
            )

            is PhotoFeedCollaboratorDivider -> PhotoFeedVisibleHeaderSnapshot.Divider(
                bounds = bounds,
            )

            else -> null
        }
    }
}

private fun buildPhotoFeedDensityPreviewOverlayEntries(
    candidates: List<PhotoFeedDensityTransitionCandidate>,
    targetLocalBoundsByMediaId: Map<String, Rect>,
    anchorMediaId: String,
): List<PhotoFeedDensityTransitionOverlayEntry> {
    val targetBounds = buildPhotoFeedPredictedBoundsByMediaId(
        candidates = candidates,
        targetLocalBoundsByMediaId = targetLocalBoundsByMediaId,
        anchorMediaId = anchorMediaId,
    )
    return candidates.mapNotNull { candidate ->
        val endBounds = targetBounds[candidate.item.mediaId] ?: return@mapNotNull null
        PhotoFeedDensityTransitionOverlayEntry(
            item = candidate.item,
            startBounds = candidate.startBounds,
            endBounds = endBounds,
        )
    }
}

private fun buildPhotoFeedHeaderTransitionOverlayEntries(
    sourceSnapshots: List<PhotoFeedVisibleHeaderSnapshot>,
    targetSnapshots: List<PhotoFeedVisibleHeaderSnapshot>,
): List<PhotoFeedHeaderTransitionOverlayEntry> {
    if (targetSnapshots.isEmpty()) return emptyList()
    val sourceByKey = sourceSnapshots.mapNotNull { snapshot ->
        photoFeedHeaderTransitionKey(snapshot)?.let { key -> key to snapshot }
    }.toMap()

    return targetSnapshots.map { targetSnapshot ->
        val transitionKey = photoFeedHeaderTransitionKey(targetSnapshot)
        val matchedSource = transitionKey
            ?.let(sourceByKey::get)
        PhotoFeedHeaderTransitionOverlayEntry(
            snapshot = targetSnapshot,
            startBounds = matchedSource?.bounds ?: targetSnapshot.bounds,
            endBounds = targetSnapshot.bounds,
            matchedFromSource = matchedSource != null,
        )
    }
}

private fun photoFeedHeaderTransitionKey(
    snapshot: PhotoFeedVisibleHeaderSnapshot,
): String? {
    return when (snapshot) {
        is PhotoFeedVisibleHeaderSnapshot.Section -> buildString {
            append("section:")
            append(snapshot.header.granularity.name)
            append(':')
            append(snapshot.header.year ?: -1)
            append(':')
            append(snapshot.header.month ?: -1)
            append(':')
            append(snapshot.header.anchorTimeMillis ?: -1L)
        }

        is PhotoFeedVisibleHeaderSnapshot.Day -> "day:${snapshot.header.year}:${snapshot.header.month}:${snapshot.header.day}"
        is PhotoFeedVisibleHeaderSnapshot.TimeBucket ->
            "bucket:${snapshot.header.anchorTimeMillis}:${snapshot.header.bucketHours}"

        is PhotoFeedVisibleHeaderSnapshot.Collaborator -> "collab:${snapshot.identity.userId}"
        is PhotoFeedVisibleHeaderSnapshot.Divider -> null
    }
}

private fun visiblePhotoFeedDensityTransitionCandidates(
    blocks: List<PhotoFeedBlock>,
    listState: LazyListState,
    itemBoundsByMediaId: Map<String, Rect>,
    viewportBounds: Rect?,
    density: PhotoFeedDensity,
    densityScope: androidx.compose.ui.unit.Density,
): List<PhotoFeedDensityTransitionCandidate> {
    val viewport = viewportBounds ?: return emptyList()
    val viewportCenter = viewport.center
    val spacingPx = with(densityScope) { rowSpacing(density).toPx() }
    val edgePaddingPx = spacingPx
    val contentWidth = (viewport.width - edgePaddingPx * 2f).coerceAtLeast(1f)
    val cellSize = ((contentWidth - (density.columns - 1) * spacingPx) / density.columns)
        .coerceAtLeast(1f)
    return listState.layoutInfo.visibleItemsInfo
        .mapNotNull { visibleItem ->
            val row = blocks.getOrNull(visibleItem.index) as? PhotoFeedGridRow ?: return@mapNotNull null
            visibleItem to row
        }
        .flatMap { (visibleItem, row) ->
            row.items.mapIndexedNotNull { columnIndex, item ->
                val bounds = itemBoundsByMediaId[item.mediaId] ?: Rect(
                    left = viewport.left + edgePaddingPx + columnIndex * (cellSize + spacingPx),
                    top = viewport.top + visibleItem.offset,
                    right = viewport.left + edgePaddingPx + columnIndex * (cellSize + spacingPx) + cellSize,
                    bottom = viewport.top + visibleItem.offset + cellSize,
                )
                val center = bounds.center
                val score = abs(center.x - viewportCenter.x) + abs(center.y - viewportCenter.y)
                PhotoFeedDensityTransitionCandidate(
                    item = item,
                    startBounds = bounds,
                    distanceScore = score,
                )
            }
        }
        .sortedBy { it.distanceScore }
}

private suspend fun correctPhotoFeedDensityTransitionAnchorOffset(
    anchorMediaId: String,
    itemBoundsByMediaId: Map<String, Rect>,
    viewportBounds: Rect,
    listState: LazyListState,
) {
    val anchorBounds = itemBoundsByMediaId[anchorMediaId] ?: return
    val deltaY = anchorBounds.center.y - viewportBounds.center.y
    if (abs(deltaY) < 1f) return
    listState.scrollBy(deltaY)
}

private fun buildPhotoFeedDensityCommitOverlayEntries(
    candidates: List<PhotoFeedDensityTransitionCandidate>,
    releasePreviewBoundsByMediaId: Map<String, Rect>,
    itemBoundsByMediaId: Map<String, Rect>,
): List<PhotoFeedDensityTransitionOverlayEntry> {
    return candidates.mapNotNull { candidate ->
        val startBounds = releasePreviewBoundsByMediaId[candidate.item.mediaId]
            ?: return@mapNotNull null
        val endBounds = itemBoundsByMediaId[candidate.item.mediaId] ?: return@mapNotNull null
        PhotoFeedDensityTransitionOverlayEntry(
            item = candidate.item,
            startBounds = startBounds,
            endBounds = endBounds,
        )
    }
}

// Mirror the real block stack so preview already reserves header slots before commit.
private fun buildPhotoFeedPredictedLocalBoundsByMediaId(
    blocks: List<PhotoFeedBlock>,
    targetDensity: PhotoFeedDensity,
    viewportBounds: Rect,
    densityScope: androidx.compose.ui.unit.Density,
    presentation: PhotoFeedPresentation,
): Map<String, Rect> {
    if (blocks.isEmpty()) return emptyMap()
    val rowSpacingPx = with(densityScope) { rowSpacing(targetDensity).toPx() }
    val sectionSpacingPx = with(densityScope) { sectionSpacing(targetDensity).toPx() }
    val edgePaddingPx = rowSpacingPx
    val columns = targetDensity.columns.coerceAtLeast(1)
    val contentWidth = (viewportBounds.width - edgePaddingPx * 2f).coerceAtLeast(1f)
    val cellSize = ((contentWidth - (columns - 1) * rowSpacingPx) / columns).coerceAtLeast(1f)
    var currentTop = 0f
    val boundsByMediaId = LinkedHashMap<String, Rect>()

    blocks.forEachIndexed { index, block ->
        when (block) {
            is PhotoFeedGridRow -> {
                block.items.forEachIndexed { columnIndex, item ->
                    val left = viewportBounds.left +
                        edgePaddingPx +
                        columnIndex * (cellSize + rowSpacingPx)
                    boundsByMediaId[item.mediaId] = Rect(
                        left = left,
                        top = currentTop,
                        right = left + cellSize,
                        bottom = currentTop + cellSize,
                    )
                }
                currentTop += cellSize
            }

            else -> {
                currentTop += photoFeedPredictedBlockHeightPx(
                    block = block,
                    density = targetDensity,
                    presentation = presentation,
                    densityScope = densityScope,
                )
            }
        }

        if (index != blocks.lastIndex) {
            currentTop += sectionSpacingPx
        }
    }
    return boundsByMediaId
}

private fun buildPhotoFeedPredictedHeaderSnapshots(
    blocks: List<PhotoFeedBlock>,
    targetDensity: PhotoFeedDensity,
    viewportBounds: Rect,
    densityScope: androidx.compose.ui.unit.Density,
    presentation: PhotoFeedPresentation,
    anchorMediaId: String,
    anchorStartBounds: Rect,
    targetLocalBoundsByMediaId: Map<String, Rect>,
): List<PhotoFeedVisibleHeaderSnapshot> {
    val anchorLocalBounds = targetLocalBoundsByMediaId[anchorMediaId] ?: return emptyList()
    val deltaY = anchorStartBounds.center.y - anchorLocalBounds.center.y
    val rowSpacingPx = with(densityScope) { rowSpacing(targetDensity).toPx() }
    val sectionSpacingPx = with(densityScope) { sectionSpacing(targetDensity).toPx() }
    val edgePaddingPx = rowSpacingPx
    val columns = targetDensity.columns.coerceAtLeast(1)
    val contentWidth = (viewportBounds.width - edgePaddingPx * 2f).coerceAtLeast(1f)
    val cellSize = ((contentWidth - (columns - 1) * rowSpacingPx) / columns).coerceAtLeast(1f)
    var currentTop = 0f
    val snapshots = mutableListOf<PhotoFeedVisibleHeaderSnapshot>()

    blocks.forEachIndexed { index, block ->
        val localBounds = when (block) {
            is PhotoFeedGridRow -> {
                val rect = Rect(
                    left = viewportBounds.left + edgePaddingPx,
                    top = currentTop,
                    right = viewportBounds.right - edgePaddingPx,
                    bottom = currentTop + cellSize,
                )
                currentTop += cellSize
                rect
            }

            else -> {
                val blockHeight = photoFeedPredictedBlockHeightPx(
                    block = block,
                    density = targetDensity,
                    presentation = presentation,
                    densityScope = densityScope,
                )
                val rect = Rect(
                    left = viewportBounds.left + edgePaddingPx,
                    top = currentTop,
                    right = viewportBounds.right - edgePaddingPx,
                    bottom = currentTop + blockHeight,
                )
                currentTop += blockHeight
                rect
            }
        }
        val translatedBounds = localBounds.translateY(deltaY)
        when (block) {
            is PhotoFeedSectionHeader -> snapshots += PhotoFeedVisibleHeaderSnapshot.Section(
                header = block,
                density = targetDensity,
                presentation = presentation,
                bounds = translatedBounds,
            )

            is PhotoFeedDayHeader -> snapshots += PhotoFeedVisibleHeaderSnapshot.Day(
                header = block,
                density = targetDensity,
                presentation = presentation,
                bounds = translatedBounds,
            )

            is PhotoFeedTimeBucketHeader -> snapshots += PhotoFeedVisibleHeaderSnapshot.TimeBucket(
                header = block,
                density = targetDensity,
                bounds = translatedBounds,
            )

            is PhotoFeedCollaboratorHeader -> snapshots += PhotoFeedVisibleHeaderSnapshot.Collaborator(
                identity = block.identity,
                bounds = translatedBounds,
            )

            is PhotoFeedCollaboratorDivider -> snapshots += PhotoFeedVisibleHeaderSnapshot.Divider(
                bounds = translatedBounds,
            )

            is PhotoFeedGridRow -> Unit
        }
        if (index != blocks.lastIndex) {
            currentTop += sectionSpacingPx
        }
    }
    val viewportMargin = max(cellSize * 3f, viewportBounds.height * 0.36f)
    return snapshots.filter { snapshot ->
        snapshot.bounds.intersectsViewport(viewportBounds, margin = viewportMargin)
    }
}

private fun buildPhotoFeedTargetScrubberSnapshot(
    blocks: List<PhotoFeedBlock>,
    density: PhotoFeedDensity,
    fallbackItems: List<PhotoFeedItem>,
    anchorMediaId: String,
    currentProgress: Float,
): PhotoFeedTimeScrubberSnapshot? {
    val anchors = buildPhotoFeedScrubberAnchors(
        blocks = blocks,
        density = density,
        leadingItemCount = PhotoFeedLeadingItemCount,
    )
    if (anchors.size <= 1) return null
    val targetBlockIndex = findBlockIndexForMedia(blocks, anchorMediaId)
        .takeIf { it >= 0 }
        ?: 0
    return PhotoFeedTimeScrubberSnapshot(
        progress = currentProgress.coerceIn(0f, 1f),
        label = resolveCurrentVisibleDateLabel(
            itemIndex = targetBlockIndex,
            blocks = blocks,
            fallbackItems = fallbackItems,
            density = density,
        ),
        showLabel = false,
        yearMarkers = buildPhotoFeedScrubberYearMarkers(anchors),
    )
}

private fun buildPhotoFeedPredictedBoundsByMediaId(
    candidates: List<PhotoFeedDensityTransitionCandidate>,
    targetLocalBoundsByMediaId: Map<String, Rect>,
    anchorMediaId: String,
): Map<String, Rect> {
    val anchorStartBounds = candidates.firstOrNull { it.item.mediaId == anchorMediaId }?.startBounds
        ?: return emptyMap()
    val anchorLocalBounds = targetLocalBoundsByMediaId[anchorMediaId] ?: return emptyMap()
    val deltaY = anchorStartBounds.center.y - anchorLocalBounds.center.y
    return candidates.mapNotNull { candidate ->
        val localBounds = targetLocalBoundsByMediaId[candidate.item.mediaId] ?: return@mapNotNull null
        candidate.item.mediaId to localBounds.translateY(deltaY)
    }.toMap()
}

private fun photoFeedPredictedBlockHeightPx(
    block: PhotoFeedBlock,
    density: PhotoFeedDensity,
    presentation: PhotoFeedPresentation,
    densityScope: androidx.compose.ui.unit.Density,
): Float {
    return when (block) {
        is PhotoFeedSectionHeader -> photoFeedPredictedHeaderHeightPx(
            granularity = block.granularity,
            density = density,
            presentation = presentation,
            densityScope = densityScope,
        )

        is PhotoFeedDayHeader -> photoFeedPredictedHeaderHeightPx(
            granularity = PhotoFeedTimeGranularity.DAY,
            density = density,
            presentation = presentation,
            densityScope = densityScope,
        )

        is PhotoFeedTimeBucketHeader -> photoFeedPredictedTimeBucketHeaderHeightPx(
            header = block,
            density = density,
            densityScope = densityScope,
        )

        is PhotoFeedCollaboratorHeader -> with(densityScope) { 42.dp.toPx() }
        is PhotoFeedCollaboratorDivider -> with(densityScope) { 34.dp.toPx() }
        is PhotoFeedGridRow -> 0f
    }
}

private fun photoFeedPredictedHeaderHeightPx(
    granularity: PhotoFeedTimeGranularity,
    density: PhotoFeedDensity,
    presentation: PhotoFeedPresentation,
    densityScope: androidx.compose.ui.unit.Density,
): Float = with(densityScope) {
    val isMain = presentation == PhotoFeedPresentation.MAIN_STREAM
    val lineHeight = when (granularity) {
        PhotoFeedTimeGranularity.YEAR -> if (isMain) 44.sp else 36.sp
        PhotoFeedTimeGranularity.MONTH -> if (isMain) 38.sp else 34.sp
        PhotoFeedTimeGranularity.DAY -> if (isMain) 28.sp else 26.sp
    }
    val topPadding = when (granularity) {
        PhotoFeedTimeGranularity.YEAR -> if (isMain) 18.dp else 12.dp
        PhotoFeedTimeGranularity.MONTH -> if (isMain) 14.dp else 12.dp
        PhotoFeedTimeGranularity.DAY -> if (isMain) 7.dp else 6.dp
    }
    val bottomPadding = when (granularity) {
        PhotoFeedTimeGranularity.YEAR -> 8.dp
        PhotoFeedTimeGranularity.MONTH -> if (density.columns >= 8) 8.dp else 6.dp
        PhotoFeedTimeGranularity.DAY -> 5.dp
    }
    topPadding.toPx() + lineHeight.toPx() + bottomPadding.toPx()
}

private fun photoFeedPredictedTimeBucketHeaderHeightPx(
    header: PhotoFeedTimeBucketHeader,
    density: PhotoFeedDensity,
    densityScope: androidx.compose.ui.unit.Density,
): Float = with(densityScope) {
    val topPadding = if (density.columns <= 4) 12.dp else 8.dp
    val bottomPadding = 6.dp
    val primaryRow = if (density.columns <= 4) 28.dp else 24.dp
    val comparisonRow = if (
        header.currentCount > 0 &&
        header.partnerCount > 0 &&
        density.columns <= 4
    ) {
        8.dp + 16.dp
    } else {
        0.dp
    }
    topPadding.toPx() + primaryRow.toPx() + comparisonRow.toPx() + bottomPadding.toPx()
}

private fun Rect.translateY(deltaY: Float): Rect {
    return Rect(
        left = left,
        top = top + deltaY,
        right = right,
        bottom = bottom + deltaY,
    )
}

private fun Rect.intersectsViewport(
    viewportBounds: Rect,
    margin: Float = 0f,
): Boolean {
    return right >= viewportBounds.left - margin &&
        left <= viewportBounds.right + margin &&
        bottom >= viewportBounds.top - margin &&
        top <= viewportBounds.bottom + margin
}

private fun photoFeedDensitySourceSceneAlpha(progress: Float): Float {
    return lerpPhotoFeedFloat(
        start = 1f,
        end = 0.40f,
        progress = progress.coerceIn(0f, 1f),
    )
}

private fun photoFeedDensitySourceMediaAlpha(progress: Float): Float {
    return lerpPhotoFeedFloat(
        start = 1f,
        end = 0.86f,
        progress = progress.coerceIn(0f, 1f),
    )
}

private fun photoFeedDensitySourceSupplementaryAlpha(progress: Float): Float {
    return lerpPhotoFeedFloat(
        start = 1f,
        end = 0.56f,
        progress = progress.coerceIn(0f, 1f),
    )
}

private fun photoFeedDensityTargetSceneAlpha(progress: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    return photoFeedSmoothStep(((t - 0.02f) / 0.90f).coerceIn(0f, 1f))
}

private fun photoFeedDensityFallbackContentScale(
    previewState: DiscreteZoomPreviewState<PhotoFeedDensity>?,
    progress: Float,
): Float {
    val direction = previewState?.direction ?: return 1f
    val targetScale = when (direction) {
        DiscreteZoomDirection.TO_SPARSE -> 1.035f
        DiscreteZoomDirection.TO_DENSE -> 0.965f
    }
    return lerpPhotoFeedFloat(
        start = 1f,
        end = targetScale,
        progress = photoFeedSmoothStep(progress.coerceIn(0f, 1f)),
    )
}

private fun photoFeedLiveHeaderAlpha(
    stage: PhotoFeedDensityTransitionStage,
    baseAlpha: Float,
    matchedHeaderKeys: Set<String>,
    snapshot: PhotoFeedVisibleHeaderSnapshot,
): Float {
    if (stage == PhotoFeedDensityTransitionStage.IDLE ||
        stage == PhotoFeedDensityTransitionStage.SETTLING ||
        stage == PhotoFeedDensityTransitionStage.COMMITTING
    ) {
        return baseAlpha
    }
    val key = photoFeedHeaderTransitionKey(snapshot) ?: return baseAlpha
    val isMatched = key in matchedHeaderKeys
    val multiplier = when (snapshot) {
        is PhotoFeedVisibleHeaderSnapshot.Collaborator,
        is PhotoFeedVisibleHeaderSnapshot.Divider,
        -> if (isMatched) 0.64f else 0.78f

        is PhotoFeedVisibleHeaderSnapshot.TimeBucket,
        -> if (isMatched) 0.66f else 0.80f

        is PhotoFeedVisibleHeaderSnapshot.Section,
        is PhotoFeedVisibleHeaderSnapshot.Day,
        -> if (isMatched) 0.70f else 0.84f
    }
    return (baseAlpha * multiplier).coerceIn(0f, 1f)
}

private fun photoFeedDensityCommitSourceOverlayAlpha(
    releaseAlpha: Float,
    progress: Float,
): Float {
    return lerpPhotoFeedFloat(
        start = releaseAlpha.coerceIn(0f, 1f),
        end = 0f,
        progress = photoFeedSmoothStep(progress),
    )
}

private fun photoFeedDensityCommitTargetOverlayAlpha(
    releaseAlpha: Float,
    progress: Float,
): Float {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val growth = lerpPhotoFeedFloat(
        start = releaseAlpha.coerceIn(0f, 1f),
        end = 1f,
        progress = (clampedProgress / 0.68f).coerceIn(0f, 1f),
    )
    val fadeProgress = ((clampedProgress - 0.58f) / 0.42f).coerceIn(0f, 1f)
    return (growth * (1f - photoFeedSmoothStep(fadeProgress))).coerceIn(0f, 1f)
}

private fun photoFeedDensityCommitLiveTargetAlpha(progress: Float): Float {
    val revealProgress = ((progress.coerceIn(0f, 1f) - 0.34f) / 0.66f).coerceIn(0f, 1f)
    return photoFeedSmoothStep(revealProgress)
}

private fun photoFeedDensityCommitFallbackLiveTargetAlpha(
    releaseAlpha: Float,
    progress: Float,
): Float {
    return lerpPhotoFeedFloat(
        start = releaseAlpha.coerceIn(0.34f, 1f),
        end = 1f,
        progress = photoFeedSmoothStep(progress.coerceIn(0f, 1f)),
    )
}

private fun photoFeedSmoothStep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun photoFeedDensityTransformOrigin(
    bounds: Rect,
    viewportBounds: Rect?,
): TransformOrigin {
    val viewport = viewportBounds ?: return TransformOrigin.Center
    val width = bounds.width.coerceAtLeast(1f)
    val height = bounds.height.coerceAtLeast(1f)
    return TransformOrigin(
        pivotFractionX = (viewport.center.x - bounds.left) / width,
        pivotFractionY = (viewport.center.y - bounds.top) / height,
    )
}

private fun interpolatePhotoFeedRect(
    start: Rect,
    end: Rect,
    progress: Float,
): Rect {
    val t = progress.coerceIn(0f, 1f)
    return Rect(
        left = lerpPhotoFeedFloat(start.left, end.left, t),
        top = lerpPhotoFeedFloat(start.top, end.top, t),
        right = lerpPhotoFeedFloat(start.right, end.right, t),
        bottom = lerpPhotoFeedFloat(start.bottom, end.bottom, t),
    )
}

private fun lerpPhotoFeedFloat(
    start: Float,
    end: Float,
    progress: Float,
): Float {
    return start + (end - start) * progress
}

private fun photoFeedMorphOverlayViewportAlpha(
    bounds: Rect,
    viewportBounds: Rect?,
): Float {
    val viewport = viewportBounds ?: return 1f
    val viewportCenter = viewport.center
    val distanceX = abs(bounds.center.x - viewportCenter.x)
    val distanceY = abs(bounds.center.y - viewportCenter.y)
    val maxDistanceX = (viewport.width * 0.62f).coerceAtLeast(1f)
    val maxDistanceY = (viewport.height * 0.62f).coerceAtLeast(1f)
    val normalizedX = (distanceX / maxDistanceX).coerceIn(0f, 1f)
    val normalizedY = (distanceY / maxDistanceY).coerceIn(0f, 1f)
    return 1f - (normalizedX * 0.28f + normalizedY * 0.24f)
}

internal fun resolveCurrentVisibleDateLabel(
    itemIndex: Int,
    blocks: List<PhotoFeedBlock>,
    fallbackItems: List<PhotoFeedItem>,
    density: PhotoFeedDensity,
): String {
    if (blocks.isEmpty()) {
        return fallbackItems.firstOrNull()?.toScrubberLabel(density).orEmpty()
    }

    val safeIndex = itemIndex.coerceIn(0, blocks.lastIndex)
    val nextRow = blocks
        .drop(safeIndex)
        .firstOrNull { it is PhotoFeedGridRow } as? PhotoFeedGridRow
    val previousRow = blocks
        .take(safeIndex + 1)
        .lastOrNull { it is PhotoFeedGridRow } as? PhotoFeedGridRow

    return nextRow?.items?.firstOrNull()?.toScrubberLabel(density)
        ?: previousRow?.items?.firstOrNull()?.toScrubberLabel(density)
        ?: fallbackItems.firstOrNull()?.toScrubberLabel(density)
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

private fun PhotoFeedItem.toScrubberLabel(density: PhotoFeedDensity): String {
    return formatScrubberLabel(
        year = displayYear,
        month = displayMonth,
        day = displayDay,
        density = density,
    )
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
        PhotoFeedDensity.COMFORT_2 -> 720
        PhotoFeedDensity.COMFORT_3 -> 720
        PhotoFeedDensity.DENSE_4 -> 512
        PhotoFeedDensity.OVERVIEW_8 -> 256
        PhotoFeedDensity.OVERVIEW_16 -> 160
    }
}

private fun photoFeedPrefetchCount(density: PhotoFeedDensity): Int {
    return when (density) {
        PhotoFeedDensity.COMFORT_2 -> 32
        PhotoFeedDensity.COMFORT_3 -> 40
        PhotoFeedDensity.DENSE_4 -> 56
        PhotoFeedDensity.OVERVIEW_8 -> 80
        PhotoFeedDensity.OVERVIEW_16 -> 128
    }
}

private fun photoFeedPreviewMemoryCacheKey(
    url: String,
    cacheKey: String?,
    @Suppress("UNUSED_PARAMETER") requestSize: Int,
): String {
    // P1 修复：与 AppContentMediaThumbnail.thumbnailMemoryCacheKey 保持一致，
    // 统一缓存键，不再按 requestSize 加 ":size:$size" 后缀。
    // 否则预取（COMFORT_3=720）和实际渲染（OVERVIEW_8=256）使用不同键，
    // 预取的图无法命中，等于白做功。统一后预取的图可被任意 density 复用。
    if (!cacheKey.isNullOrBlank()) return cacheKey
    return sharedPreviewMemoryCacheKey(url)
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
