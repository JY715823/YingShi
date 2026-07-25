package com.example.yingshi.feature.photos

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.components.rememberYingShiMotionEnabled
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun SmallAlbumDetailContent(
    detail: PostDetailUiModel,
    highlightMediaIds: List<String>,
    focusMediaId: String?,
    feedbackNonce: Int,
    onBack: () -> Unit,
    onOpenGearEdit: () -> Unit,
    onOpenMediaViewer: (Int) -> Unit,
    onOpenSmallAlbumComments: () -> Unit,
    actionNotice: String?,
    onShowActionNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var displayDetail by remember(detail.postId) { mutableStateOf(detail) }
    var showPhotoFeedPicker by rememberSaveable(detail.postId) {
        mutableStateOf(false)
    }
    var shareAllInFlight by remember { mutableStateOf(false) }
    val collaboratorDirectory = rememberCollaboratorDirectorySnapshot()
    LaunchedEffect(detail) {
        displayDetail = detail
    }
    val ownershipAvatars = remember(displayDetail.participantUserIds, collaboratorDirectory) {
        collaboratorDirectory.resolveOrdered(displayDetail.participantUserIds)
    }
    val postMediaIds = remember(displayDetail.mediaItems) {
        displayDetail.mediaItems.map { it.id }
    }
    val feedbackKey = "${detail.postId}:$feedbackNonce"
    val highlightKey = remember(highlightMediaIds, feedbackNonce) {
        "$feedbackNonce:${highlightMediaIds.distinct().joinToString("|")}"
    }
    var showEntryNotice by rememberSaveable(feedbackKey, detail.entryNotice) {
        mutableStateOf(!detail.entryNotice.isNullOrBlank())
    }
    var showNewAddedState by rememberSaveable(detail.postId, highlightKey) {
        mutableStateOf(highlightMediaIds.isNotEmpty())
    }
    var resultNotice by rememberSaveable(detail.postId, highlightKey, focusMediaId) {
        mutableStateOf<String?>(null)
    }
    LaunchedEffect(displayDetail.postId, postMediaIds, focusMediaId, highlightKey) {
        val targetId = focusMediaId ?: highlightMediaIds.firstOrNull()
        if (targetId.isNullOrBlank() || displayDetail.mediaItems.isEmpty()) return@LaunchedEffect
        val targetIndex = displayDetail.mediaItems.indexOfFirst { it.id == targetId }
        if (targetIndex >= 0) {
            val otherCount = highlightMediaIds.distinct().size - 1
            resultNotice = if (otherCount > 0) {
                "已定位到刚加入媒体，另有 $otherCount 项新加入"
            } else {
                "已定位到刚加入媒体"
            }
        } else {
            resultNotice = "暂未在详情里找到刚处理的媒体，可稍后刷新再查看。"
        }
    }
    LaunchedEffect(displayDetail.postId, highlightKey) {
        if (highlightMediaIds.isEmpty()) return@LaunchedEffect
        delay(4500L)
        showNewAddedState = false
        resultNotice = null
    }
    LaunchedEffect(feedbackKey, detail.entryNotice) {
        if (detail.entryNotice.isNullOrBlank()) return@LaunchedEffect
        delay(3200L)
        showEntryNotice = false
    }
    val postOriginalSummary = FakeOriginalLoadRepository.getPostSummary(postMediaIds)
    if (showPhotoFeedPicker) {
        AppPhotoFeedPickerScreen(
            title = "照片流",
            confirmLabel = "加入当前相册",
            disabledMediaIds = displayDetail.mediaItems.map { it.id }.toSet(),
            disabledSelectionLabel = "已在相册中",
            confirmBackWhenSelected = true,
            onBack = { showPhotoFeedPicker = false },
            onConfirm = { items ->
                val feedSelections = items
                    .mapNotNull { selected -> FakePhotoFeedRepository.findPhotoFeedItem(selected.mediaId) }
                val addedCount = FakeAlbumRepository.appendPhotoFeedItemsToPost(displayDetail.postId, feedSelections)
                showPhotoFeedPicker = false
                if (addedCount > 0) {
                    displayDetail = FakeAlbumRepository.getPostDetail(displayDetail.toDetailRoute())
                    onShowActionNotice("已加入当前相册")
                } else {
                    onShowActionNotice("这些媒体已经在当前相册里")
                }
            },
            modifier = modifier,
        )
        return
    }

    SmallAlbumDetailBodyLayout(
        modifier = modifier,
        topBar = {
            SmallAlbumDetailTopBar(
                title = displayDetail.title.ifBlank { "小相册" },
                ownershipAvatars = ownershipAvatars,
                onBack = onBack,
                onShareAll = {
                    val shareItems = displayDetail.mediaItems.map(PostDetailMediaUiModel::toShareableMediaItem)
                    if (shareItems.isEmpty()) {
                        onShowActionNotice("当前小相册还没有可分享的媒体。")
                    } else if (shareAllInFlight) {
                        onShowActionNotice("正在准备分享文件…")
                    } else {
                        coroutineScope.launch {
                            shareAllInFlight = true
                            onShowActionNotice("正在准备分享文件…")
                            try {
                                when (
                                    val result = MediaShareManager.shareMedia(
                                        context = context,
                                        items = shareItems,
                                        packageBaseName = displayDetail.title.ifBlank {
                                            "映世小相册-${displayDetail.postId}"
                                        },
                                    )
                                ) {
                                    is MediaShareLaunchResult.Success -> {
                                        onShowActionNotice(result.toNoticeMessage())
                                    }
                                    is MediaShareLaunchResult.Error -> {
                                        onShowActionNotice(result.message)
                                    }
                                }
                            } finally {
                                shareAllInFlight = false
                            }
                        }
                    }
                },
                onEdit = onOpenGearEdit,
            )
        },
        notice = {
            detail.entryNotice?.takeIf { showEntryNotice }?.let { message ->
                PostInlineNotice(text = message)
            }
            resultNotice?.let { message ->
                PostInlineNotice(text = message)
            }
            actionNotice?.let { message ->
                PostInlineNotice(text = message)
            }
        },
        infoSection = {
            SmallAlbumInfoSection(
                detail = displayDetail,
                originalSummary = postOriginalSummary,
                onOpenComments = onOpenSmallAlbumComments,
                onLoadAllOriginals = {
                    FakeOriginalLoadRepository.loadAllOriginals(postMediaIds)
                    onShowActionNotice("开始加载小相册原图")
                },
            )
        },
        mediaSection = {
            SmallAlbumMediaGridSection(
                postId = displayDetail.postId,
                mediaItems = displayDetail.mediaItems,
                highlightMediaIds = if (showNewAddedState) highlightMediaIds else emptyList(),
                onOpenMediaViewer = onOpenMediaViewer,
                onAddMedia = { showPhotoFeedPicker = true },
                onRefreshRequest = {
                    displayDetail = FakeAlbumRepository.getPostDetail(displayDetail.toDetailRoute())
                },
                onEmptyAfterDelete = onBack,
                onShowNotice = onShowActionNotice,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

@Composable
internal fun SmallAlbumDetailBodyLayout(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit,
    notice: @Composable () -> Unit = {},
    infoSection: @Composable () -> Unit,
    mediaSection: @Composable () -> Unit,
    commentSummary: @Composable () -> Unit = {},
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    Box(
        modifier = modifier
            .background(colors.appBackground)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.glowWash.copy(alpha = 0.58f),
                        colors.sectionBackground.copy(alpha = 0.82f),
                        colors.appBackground,
                        colors.memoryWash.copy(alpha = 0.44f),
                    ),
                ),
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colors.glowWash.copy(alpha = 0.42f),
                        colors.sectionBackground.copy(alpha = 0.18f),
                        Color.Transparent,
                    ),
                    center = androidx.compose.ui.geometry.Offset(0f, 80f),
                    radius = 820f,
                ),
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colors.memoryContainer.copy(alpha = 0.36f),
                        colors.memoryWash.copy(alpha = 0.16f),
                        Color.Transparent,
                    ),
                    center = androidx.compose.ui.geometry.Offset(980f, 280f),
                    radius = 700f,
                ),
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colors.softGreenContainer.copy(alpha = 0.24f),
                        colors.glowWash.copy(alpha = 0.10f),
                        Color.Transparent,
                    ),
                    center = androidx.compose.ui.geometry.Offset(320f, 900f),
                    radius = 780f,
                ),
            ),
    ) {
        SmallAlbumAtmosphereBackdrop(modifier = Modifier.matchParentSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 8.dp)
                .padding(top = spacing.xxs, bottom = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            SmallAlbumInfoSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
                contentPadding = PaddingValues(horizontal = spacing.sm, vertical = spacing.sm),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    topBar()
                    infoSection()
                }
            }
            notice()
            commentSummary()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                SmallAlbumMediaAtmospherePanel(modifier = Modifier.matchParentSize())
                mediaSection()
            }
        }
    }
}

@Composable
internal fun PostDetailBodyLayout(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit,
    mediaArea: @Composable () -> Unit,
    mediaInfo: @Composable () -> Unit = {},
    postInfo: @Composable () -> Unit,
    comments: @Composable () -> Unit = {},
) {
    SmallAlbumDetailBodyLayout(
        modifier = modifier,
        topBar = topBar,
        infoSection = {
            Column(verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm)) {
                postInfo()
                comments()
            }
        },
        mediaSection = {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
            ) {
                mediaArea()
                mediaInfo()
            }
        },
    )
}

@Composable
internal fun PostMediaCard(
    media: PostDetailMediaUiModel,
    isNewlyAdded: Boolean = false,
    originalLoadState: OriginalLoadState = OriginalLoadState.NotLoaded,
    onOriginalLoadStateChange: (OriginalLoadState) -> Unit = {},
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val cardAspectRatio = media.displayAspectRatio()
    val colors = YingShiThemeTokens.colors

    BoxWithConstraints(
        modifier = modifier
            .yingShiClickable(pressedScale = 0.985f, onClick = onClick)
            .background(colors.raisedSurface),
    ) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        val fittedWidth = if (availableHeight * cardAspectRatio <= availableWidth) {
            availableHeight * cardAspectRatio
        } else {
            availableWidth
        }
        val fittedHeight = if (availableWidth / cardAspectRatio <= availableHeight) {
            availableWidth / cardAspectRatio
        } else {
            availableHeight
        }

        AppContentMediaThumbnail(
            mediaSource = media.mediaSource,
            mediaType = media.mediaType,
            palette = media.palette,
            modifier = Modifier
                .align(Alignment.Center)
                .width(fittedWidth)
                .height(fittedHeight),
            contentDescription = postDetailMediaContentDescription(media.mediaType),
            requestSize = 720,
            showStatusBadge = true,
            contentScale = ContentScale.Fit,
            originalLoadState = originalLoadState,
            onOriginalLoadStateChange = onOriginalLoadStateChange,
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(fittedWidth)
                .height(fittedHeight),
        ) {
            YingShiMediaFrame(
                modifier = Modifier.matchParentSize(),
                shape = RoundedCornerShape(10.dp),
                memoryActive = isNewlyAdded,
                topScrimAlpha = if (media.mediaType == AppMediaType.VIDEO) 0.18f else 0.10f,
                bottomGlowAlpha = 0.14f,
            )
        }
        if (isNewlyAdded) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(fittedWidth)
                    .height(fittedHeight)
                    .background(Color.Transparent)
                    .padding(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Transparent),
                )
                YingShiMemoryBadge(
                    text = "新加入",
                    modifier = Modifier.align(Alignment.TopEnd),
                    compact = true,
                )
            }
        }
    }
}

@Composable
internal fun PostMediaInfoRow(
    media: PostDetailMediaUiModel,
    commentCount: Int,
    originalLoadState: OriginalLoadState,
    showOriginalAction: Boolean,
    onCommentClick: () -> Unit,
    onOriginalClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PostMetaCapsule(text = formatPostTime(media.displayTimeMillis))
        Spacer(modifier = Modifier.weight(1f))
        PostActionChip(text = "评", onClick = onCommentClick)
        PostMetaCapsule(text = commentCount.toString())
        if (showOriginalAction) {
            PostActionChip(
                text = originalLoadState.actionLabel(),
                onClick = onOriginalClick,
            )
        }
    }
}

@Composable
internal fun PostMediaEmptyInfoRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PostMetaCapsule(text = "暂无媒体")
        PostMetaCapsule(text = "可继续发表评论")
    }
}

@Composable
internal fun PostMediaArea(
    detail: PostDetailUiModel,
    currentPage: Int,
    modifier: Modifier = Modifier,
    onOpenMedia: () -> Unit,
    content: @Composable () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "媒体",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            if (detail.mediaItems.isNotEmpty()) {
                Text(
                    text = "${currentPage + 1} / ${detail.mediaItems.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
            }
        }
        content()
        if (detail.mediaItems.isNotEmpty()) {
            PostActionChip(text = "查看媒体", onClick = onOpenMedia)
        }
    }
}

internal fun PostDetailMediaUiModel.displayAspectRatio(): Float {
    val actualWidth = width
    val actualHeight = height
    if (actualWidth != null && actualHeight != null && actualWidth > 0 && actualHeight > 0) {
        return (actualWidth.toFloat() / actualHeight.toFloat()).coerceIn(0.05f, 20f)
    }
    return aspectRatio.coerceIn(0.05f, 20f)
}

internal const val SmallAlbumDensityMorphVisibleLimit = 28
internal const val SmallAlbumDensityTransitionThumbnailMax = 256

internal enum class SmallAlbumDensityTransitionStage {
    IDLE,
    PREVIEWING,
    REBOUNDING,
    COMMITTING,
}

internal data class SmallAlbumDensityTransitionCandidate(
    val item: PhotoFeedItem,
    val startBounds: Rect,
    val distanceScore: Float,
)

internal data class SmallAlbumDensityTransitionOverlayEntry(
    val item: PhotoFeedItem,
    val startBounds: Rect,
    val endBounds: Rect,
)

@Composable
internal fun SmallAlbumMediaGridSection(
    postId: String,
    mediaItems: List<PostDetailMediaUiModel>,
    highlightMediaIds: List<String>,
    onOpenMediaViewer: (Int) -> Unit,
    onAddMedia: () -> Unit,
    onRefreshRequest: () -> Unit,
    onEmptyAfterDelete: () -> Unit,
    onShowNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val motion = YingShiThemeTokens.motion
    val motionEnabled = rememberYingShiMotionEnabled()
    var densityName by rememberSaveable(postId) { mutableStateOf(PhotoFeedDensity.COMFORT_3.name) }
    var selectionMode by rememberSaveable(postId) { mutableStateOf(false) }
    var selectedIds by rememberSaveable(postId) { mutableStateOf(emptySet<String>()) }
    var isMutating by rememberSaveable(postId) { mutableStateOf(false) }
    var showDeleteSelectedConfirm by rememberSaveable(postId) { mutableStateOf(false) }
    var shareInFlight by remember { mutableStateOf(false) }
    val density = PhotoFeedDensity.valueOf(densityName)
    val sortedMediaItems = remember(mediaItems) { mediaItems.sortedForSmallAlbumDisplay() }
    val mediaById = remember(sortedMediaItems) { sortedMediaItems.associateBy { it.id } }
    val mediaPositionLookup = remember(sortedMediaItems) {
        sortedMediaItems.mapIndexed { index, item -> item.id to index }.toMap()
    }
    val currentMediaIdSet = remember(sortedMediaItems) { sortedMediaItems.map { it.id }.toSet() }
    val feedItems = remember(sortedMediaItems, postId) { sortedMediaItems.toSmallAlbumFeedItems(postId) }
    val blocks = remember(feedItems, density) {
        buildPhotoFeedBlocks(items = feedItems, density = density)
    }
    val listState = rememberLazyListState()
    val densityScope = LocalDensity.current
    val gridSpacing = smallAlbumRowSpacing(density)
    val gridEdgePadding = gridSpacing
    val spacingPx = with(densityScope) { gridSpacing.toPx() }
    val edgePaddingPx = with(densityScope) { gridSpacing.toPx() }
    var manualInlineVideoId by remember { mutableStateOf<String?>(null) }
    var pausedInlineVideoIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var inlineVideoProgressById by remember { mutableStateOf<Map<String, InlineVideoPlaybackProgress>>(emptyMap()) }
    val visibleInlineVideoIds by remember(listState, blocks) {
        derivedStateOf {
            visibleSmallAlbumVideoIds(
                listState = listState,
                blocks = blocks,
            )
        }
    }
    val centeredInlineVideoId by remember(listState, blocks, density, spacingPx, edgePaddingPx) {
        derivedStateOf {
            centeredSmallAlbumVideoId(
                listState = listState,
                blocks = blocks,
                density = density,
                colSpacingPx = spacingPx,
                edgePaddingPx = edgePaddingPx,
            )
        }
    }
    val inlineVideoAutoPlayAllowed = !selectionMode && density.columns <= 4
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
    var viewportBounds by remember { mutableStateOf<Rect?>(null) }
    val itemBoundsByMediaId = remember { mutableStateMapOf<String, Rect>() }
    var densityTransitionStage by remember { mutableStateOf(SmallAlbumDensityTransitionStage.IDLE) }
    var densityPreviewState by remember { mutableStateOf<DiscreteZoomPreviewState<PhotoFeedDensity>?>(null) }
    var densityTransitionOverlayEntries by remember {
        mutableStateOf<List<SmallAlbumDensityTransitionOverlayEntry>>(emptyList())
    }
    val densityMorphProgress = remember { androidx.compose.animation.core.Animatable(0f) }
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
    val scrollAnchors = remember(blocks, density) {
        buildPhotoFeedScrubberAnchors(
            blocks = blocks,
            density = density,
            leadingItemCount = 0,
        )
    }
    val scrubberYearMarkers = remember(scrollAnchors) {
        buildPhotoFeedScrubberYearMarkers(scrollAnchors)
    }
    val rowKeyToIndex = remember(rowKeys) {
        rowKeys.mapIndexed { index, rowKey -> rowKey to index }.toMap()
    }
    val transitionThumbnailRequestSize = minOf(
        smallAlbumThumbnailRequestSize(density),
        SmallAlbumDensityTransitionThumbnailMax,
    )
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
                    val itemEndY = item.offset + item.size
                    if (ty !in item.offset until itemEndY) continue
                    val block = blocks.getOrNull(item.index)
                    if (block == null) {
                        return@MultiSelectHitTestAdapter null
                    }
                    if (block !is PhotoFeedGridRow) {
                        return@MultiSelectHitTestAdapter null
                    }
                    val colIndex = (tx / segmentWidth).toInt().coerceIn(0, density.columns - 1)
                    val mediaItem = block.items.getOrNull(colIndex) ?: return@MultiSelectHitTestAdapter null
                    return@MultiSelectHitTestAdapter MultiSelectHitResult(
                        mediaId = mediaItem.mediaId,
                        rowKey = block.key,
                        rowIndex = rowKeyToIndex[block.key] ?: -1,
                        isSelectable = true,
                        colIndex = colIndex,
                        columnsInRow = block.items.size,
                    )
                }
                null
            },
            mediaIdsInRow = { rowKey -> rowKeyToMediaIds[rowKey].orEmpty() },
            rowKeyAtIndex = { rowIndex -> rowKeys.getOrNull(rowIndex) },
        )
    }

    LaunchedEffect(currentMediaIdSet) {
        selectedIds = selectedIds.intersect(currentMediaIdSet)
        if (selectedIds.isEmpty()) {
            selectionMode = false
            showDeleteSelectedConfirm = false
        }
    }
    val liveSelectedIds = remember { mutableStateOf(selectedIds) }
    LaunchedEffect(selectedIds) {
        liveSelectedIds.value = selectedIds
    }
    var scrubberVisible by remember { mutableStateOf(false) }
    var scrubberInteracting by remember { mutableStateOf(false) }
    var scrubberDragProgress by remember { mutableStateOf<Float?>(null) }
    var scrubberDragLabel by remember { mutableStateOf("") }
    var lastRequestedAnchorIndex by remember { mutableStateOf(-1) }
    val currentVisibleDateLabel by remember(listState, blocks, feedItems, density) {
        derivedStateOf {
            resolveCurrentVisibleDateLabel(
                itemIndex = listState.firstVisibleItemIndex,
                blocks = blocks,
                fallbackItems = feedItems,
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

    fun resetDensityTransitionState() {
        densityPreviewState = null
        densityTransitionOverlayEntries = emptyList()
        densityTransitionStage = SmallAlbumDensityTransitionStage.IDLE
    }

    fun startDensityPreviewFallback(previewState: DiscreteZoomPreviewState<PhotoFeedDensity>) {
        densityPreviewState = previewState
        densityTransitionOverlayEntries = emptyList()
        densityTransitionStage = SmallAlbumDensityTransitionStage.PREVIEWING
    }

    fun syncDensityPreview(previewState: DiscreteZoomPreviewState<PhotoFeedDensity>) {
        val viewport = viewportBounds ?: run {
            startDensityPreviewFallback(previewState)
            return
        }
        val candidates = visibleSmallAlbumDensityTransitionCandidates(
            blocks = blocks,
            listState = listState,
            itemBoundsByMediaId = itemBoundsByMediaId,
            viewportBounds = viewport,
            density = density,
            densityScope = densityScope,
        ).take(SmallAlbumDensityMorphVisibleLimit)
        if (candidates.isEmpty()) {
            startDensityPreviewFallback(previewState)
            return
        }
        val anchorMediaId = candidates.first().item.mediaId
        val targetLocalBoundsByMediaId = buildSmallAlbumPredictedLocalBoundsByMediaId(
            blocks = buildPhotoFeedBlocks(items = feedItems, density = previewState.targetLevel),
            targetDensity = previewState.targetLevel,
            viewportBounds = viewport,
            densityScope = densityScope,
        )
        val targetBounds = buildSmallAlbumPredictedBoundsByMediaId(
            candidates = candidates,
            targetLocalBoundsByMediaId = targetLocalBoundsByMediaId,
            anchorMediaId = anchorMediaId,
        )
        val overlayEntries = candidates.mapNotNull { candidate ->
            val endBounds = targetBounds[candidate.item.mediaId] ?: return@mapNotNull null
            SmallAlbumDensityTransitionOverlayEntry(
                item = candidate.item,
                startBounds = candidate.startBounds,
                endBounds = endBounds,
            )
        }
        if (overlayEntries.isEmpty()) {
            startDensityPreviewFallback(previewState)
            return
        }
        densityPreviewState = previewState
        densityTransitionOverlayEntries = overlayEntries
        densityTransitionStage = SmallAlbumDensityTransitionStage.PREVIEWING
    }

    fun reboundDensityPreview(finalPreviewState: DiscreteZoomPreviewState<PhotoFeedDensity>?) {
        val previewState = finalPreviewState ?: densityPreviewState ?: run {
            resetDensityTransitionState()
            return
        }
        densityPreviewState = previewState
        densityTransitionStage = SmallAlbumDensityTransitionStage.REBOUNDING
        scope.launch {
            densityMorphProgress.snapTo(previewState.renderProgress)
            densityMorphProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = if (motionEnabled) motion.densityPreviewMillis else 0,
                    easing = motion.easing,
                ),
            )
            resetDensityTransitionState()
        }
    }

    fun beginDensityTransition(
        toDensity: PhotoFeedDensity,
        finalPreviewState: DiscreteZoomPreviewState<PhotoFeedDensity>?,
    ) {
        val previewState = finalPreviewState ?: run {
            densityName = toDensity.name
            resetDensityTransitionState()
            return
        }
        densityPreviewState = previewState
        densityTransitionStage = SmallAlbumDensityTransitionStage.COMMITTING
        scope.launch {
            densityMorphProgress.snapTo(previewState.renderProgress)
            densityName = toDensity.name
            densityMorphProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = if (motionEnabled) motion.densityPreviewMillis else 0,
                    easing = motion.easing,
                ),
            )
            resetDensityTransitionState()
        }
    }

    val densityTransitionContentScale = when (densityTransitionStage) {
        SmallAlbumDensityTransitionStage.PREVIEWING ->
            smallAlbumDensityFallbackContentScale(
                previewState = densityPreviewState,
                progress = densityPreviewState?.renderProgress ?: 0f,
            )
        SmallAlbumDensityTransitionStage.REBOUNDING ->
            smallAlbumDensityFallbackContentScale(
                previewState = densityPreviewState,
                progress = densityMorphProgress.value,
            )
        else -> 1f
    }
    val densityTransitionLiveMediaAlpha = when (densityTransitionStage) {
        SmallAlbumDensityTransitionStage.PREVIEWING ->
            smallAlbumSourceMediaAlpha(densityPreviewState?.renderProgress ?: 0f)
        SmallAlbumDensityTransitionStage.REBOUNDING ->
            smallAlbumSourceMediaAlpha(densityMorphProgress.value)
        else -> 1f
    }
    val densityTransitionOverlayProgress = when (densityTransitionStage) {
        SmallAlbumDensityTransitionStage.PREVIEWING -> densityPreviewState?.renderProgress ?: 0f
        SmallAlbumDensityTransitionStage.REBOUNDING -> densityMorphProgress.value
        SmallAlbumDensityTransitionStage.COMMITTING -> densityMorphProgress.value
        SmallAlbumDensityTransitionStage.IDLE -> 0f
    }
    val densityTransitionOverlayAlpha = when (densityTransitionStage) {
        SmallAlbumDensityTransitionStage.PREVIEWING ->
            smallAlbumTargetSceneAlpha(densityPreviewState?.renderProgress ?: 0f)
        SmallAlbumDensityTransitionStage.REBOUNDING ->
            smallAlbumTargetSceneAlpha(densityMorphProgress.value)
        SmallAlbumDensityTransitionStage.COMMITTING ->
            smallAlbumCommitTargetOverlayAlpha(
                releaseAlpha = smallAlbumTargetSceneAlpha(densityPreviewState?.renderProgress ?: 0f),
                progress = densityMorphProgress.value,
            )
        SmallAlbumDensityTransitionStage.IDLE -> 0f
    }

    fun deleteSelectedMedia(deleteMode: String = "directory") {
        val pendingIds = selectedIds.toSet()
        if (pendingIds.isEmpty()) return
        scope.launch {
            isMutating = true
            showDeleteSelectedConfirm = false
            var successCount = 0
            pendingIds.forEach { mediaId ->
                when (val result = RepositoryProvider.mediaRepository.deleteMediaFromPost(
                    smallAlbumId = postId,
                    mediaId = mediaId,
                    deleteMode = deleteMode,
                )) {
                    is ApiResult.Success -> successCount += 1
                    is ApiResult.Error -> {
                        onShowNotice(result.message)
                    }
                    ApiResult.Loading -> Unit
                }
            }
            if (successCount > 0) {
                notifyRealBackendContentChangedWithoutPhotoFeed(
                    postIds = setOf(postId),
                    mediaIds = pendingIds,
                )
                selectedIds = emptySet()
                selectionMode = false
                if (pendingIds.size >= currentMediaIdSet.size) {
                    onEmptyAfterDelete()
                } else {
                    onRefreshRequest()
                }
                onShowNotice(if (deleteMode == "system") "已系统删除并进入回收站" else "已移出当前小相册")
            }
            isMutating = false
        }
    }

    LaunchedEffect(currentScrollProgress, scrubberInteracting, scrollAnchors.size, selectionMode) {
        if (scrollAnchors.size <= 1 || selectionMode) {
            scrubberVisible = false
            return@LaunchedEffect
        }
        scrubberVisible = true
        if (!scrubberInteracting) {
            lastRequestedAnchorIndex = (currentScrollProgress * scrollAnchors.lastIndex)
                .roundToInt()
                .coerceIn(0, scrollAnchors.lastIndex)
            delay(900)
            if (!scrubberInteracting && !selectionMode) {
                scrubberVisible = false
            }
        }
    }

    BackHandler(enabled = selectionMode) {
        showDeleteSelectedConfirm = false
        selectionMode = false
        selectedIds = emptySet()
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        if (showDeleteSelectedConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteSelectedConfirm = false },
                containerColor = colors.raisedSurface,
                titleContentColor = colors.titleAccent,
                textContentColor = colors.textSecondary,
                title = {
                    Text(
                        text = if (selectedIds.size == 1) "移出这张媒体" else "移出选中媒体",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TrashDialogActionButton(
                            text = "取消",
                            onClick = { showDeleteSelectedConfirm = false },
                        )
                        TrashDialogActionButton(
                            text = "系统删除",
                            enabled = !isMutating,
                            danger = true,
                            onClick = { deleteSelectedMedia("system") },
                        )
                        TrashDialogActionButton(
                            text = "确认移出",
                            enabled = !isMutating,
                            onClick = { deleteSelectedMedia("directory") },
                        )
                    }
                },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    viewportBounds = coordinates.boundsInRoot()
                }
                .clipToBounds()
                .discreteZoomLevelGesture(
                    enabled = !selectionMode,
                    levels = PhotoFeedDensity.entries.toList(),
                    currentLevel = density,
                    onLevelChange = { densityName = it.name },
                    commitOnGestureEnd = true,
                    onPreviewStateChange = { previewState ->
                        if (previewState != null) {
                            syncDensityPreview(previewState)
                        }
                    },
                    onGestureFinished = { committed, finalPreviewState, committedTargetLevel ->
                        val nextDensity = committedTargetLevel as? PhotoFeedDensity
                        when {
                            !committed || nextDensity == null || nextDensity == density -> {
                                if (densityTransitionStage == SmallAlbumDensityTransitionStage.PREVIEWING &&
                                    finalPreviewState != null
                                ) {
                                    reboundDensityPreview(finalPreviewState)
                                } else {
                                    resetDensityTransitionState()
                                }
                            }
                            else -> beginDensityTransition(
                                toDensity = nextDensity,
                                finalPreviewState = finalPreviewState,
                            )
                        }
                    },
                )
                .multiSelectSwipeGesture(
                    enabled = selectionMode,
                    hitTestAdapter = hitTestAdapter,
                    selectedIds = liveSelectedIds.value,
                    onSelectionChange = { selectedIds = it },
                    onAutoScroll = { delta -> listState.scrollBy(delta) },
                ),
        ) {
            if (mediaItems.isEmpty()) {
                SmallAlbumEmptyState(
                    onAddMedia = { if (!isMutating) onAddMedia() },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = densityTransitionLiveMediaAlpha
                            scaleX = densityTransitionContentScale
                            scaleY = densityTransitionContentScale
                            transformOrigin = TransformOrigin.Center
                        },
                    verticalArrangement = Arrangement.spacedBy(smallAlbumSectionSpacing(density)),
                    contentPadding = PaddingValues(
                        top = if (selectionMode) 58.dp else 0.dp,
                        start = gridEdgePadding,
                        end = gridEdgePadding,
                        bottom = if (selectionMode) 92.dp else 24.dp,
                    ),
                ) {
                    lazyItems(
                        items = blocks,
                        key = { it.key },
                    ) { block ->
                        when (block) {
                            is PhotoFeedSectionHeader -> SmallAlbumMonthHeaderRow(title = block.title)
                            is PhotoFeedDayHeader -> SmallAlbumDayHeaderRow(title = block.title)
                            is PhotoFeedTimeBucketHeader,
                            is PhotoFeedCollaboratorHeader,
                            is PhotoFeedCollaboratorDivider -> Unit
                            is PhotoFeedGridRow -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                                ) {
                                    block.items.forEach { item ->
                                        val media = mediaById[item.mediaId] ?: return@forEach
                                        val mediaIndex = mediaPositionLookup[item.mediaId] ?: 0
                                        SmallAlbumGridMediaTile(
                                            item = item,
                                            media = media,
                                            highlighted = item.mediaId in highlightMediaIds,
                                            selected = item.mediaId in selectedIds,
                                            selectionMode = selectionMode,
                                            density = density,
                                            inlineVideoAutoPlayEnabled = inlineVideoAutoPlayAllowed,
                                            isInlineVideoPlaying = playingInlineVideoId == item.mediaId,
                                            isInlineVideoActive = activeInlineVideoId == item.mediaId,
                                            isInlineVideoPaused = item.mediaId in pausedInlineVideoIds,
                                            inlineVideoProgress = inlineVideoProgressById[item.mediaId],
                                            modifier = Modifier.weight(1f),
                                            onBoundsChange = { bounds ->
                                                if (bounds == null) {
                                                    itemBoundsByMediaId.remove(item.mediaId)
                                                } else {
                                                    itemBoundsByMediaId[item.mediaId] = bounds
                                                }
                                            },
                                            onClick = {
                                                if (selectionMode) {
                                                    selectedIds = if (selectedIds.contains(item.mediaId)) {
                                                        selectedIds - item.mediaId
                                                    } else {
                                                        selectedIds + item.mediaId
                                                    }
                                                } else {
                                                    onOpenMediaViewer(mediaIndex)
                                                }
                                            },
                                            onOpenMedia = {
                                                if (mediaIndex >= 0) {
                                                    onOpenMediaViewer(mediaIndex)
                                                }
                                            },
                                            onLongClick = {
                                                selectionMode = true
                                                selectedIds = setOf(item.mediaId)
                                            },
                                            onToggleInlineVideo = { onToggleInlineVideo(item) },
                                            onInlineVideoProgressChange = { progress ->
                                                inlineVideoProgressById = inlineVideoProgressById.toMutableMap().apply {
                                                    put(item.mediaId, progress)
                                                }
                                            },
                                        )
                                    }
                                    repeat(density.columns - block.items.size) {
                                        Spacer(
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
                if (densityTransitionOverlayEntries.isNotEmpty()) {
                    SmallAlbumDensityMorphOverlay(
                        entries = densityTransitionOverlayEntries,
                        viewportBounds = viewportBounds,
                        progress = densityTransitionOverlayProgress,
                        thumbnailRequestSize = transitionThumbnailRequestSize,
                        alpha = densityTransitionOverlayAlpha,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
            if (selectionMode) {
                SmallAlbumSelectionTopBar(
                    selectedCount = selectedIds.size,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp, start = gridSpacing, end = gridSpacing),
                )
                SmallAlbumSelectionBottomBar(
                    onCancel = {
                        showDeleteSelectedConfirm = false
                        selectionMode = false
                        selectedIds = emptySet()
                    },
                    onShare = {
                        val selectedItems = sortedMediaItems.filter { media -> selectedIds.contains(media.id) }
                        if (selectedItems.isEmpty()) {
                            onShowNotice("没有找到可分享的媒体。")
                        } else if (shareInFlight) {
                            onShowNotice("正在准备分享文件…")
                        } else {
                            scope.launch {
                                shareInFlight = true
                                onShowNotice("正在准备分享文件…")
                                try {
                                    when (
                                        val result = MediaShareManager.shareMedia(
                                            context = context,
                                            items = selectedItems.map(PostDetailMediaUiModel::toShareableMediaItem),
                                            packageBaseName = "映世小相册-${selectedItems.size}项",
                                        )
                                    ) {
                                        is MediaShareLaunchResult.Success -> {
                                            onShowNotice(result.toNoticeMessage())
                                        }
                                        is MediaShareLaunchResult.Error -> {
                                            onShowNotice(result.message)
                                        }
                                    }
                                } finally {
                                    shareInFlight = false
                                }
                            }
                        }
                    },
                    onDelete = {
                        if (!isMutating && selectedIds.isNotEmpty()) {
                            showDeleteSelectedConfirm = true
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = spacing.lg, end = spacing.lg, bottom = 10.dp),
                )
            }
            if (!selectionMode && scrubberVisible && scrollAnchors.size > 1) {
                PhotoFeedTimeScrubber(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(184.dp),
                    progress = displayedScrubberProgress,
                    label = displayedScrubberLabel,
                    showLabel = scrubberInteracting,
                    yearMarkers = scrubberYearMarkers,
                    onSeekToProgress = { progress ->
                        if (scrollAnchors.isEmpty()) return@PhotoFeedTimeScrubber
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
                            scope.launch {
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
            if (!selectionMode && mediaItems.isNotEmpty()) {
                PostIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "从照片流加入媒体",
                    onClick = { if (!isMutating) onAddMedia() },
                    emphasized = true,
                    buttonSize = 50.dp,
                    iconSize = 26.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = gridEdgePadding, bottom = 18.dp),
                )
            }
        }
    }
}

@Composable
internal fun SmallAlbumSelectionTopBar(
    selectedCount: Int,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.sectionBackground.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.68f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = YingShiThemeTokens.spacing.sm, vertical = YingShiThemeTokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(68.dp))
            Text(
                text = if (selectedCount > 0) "已选 $selectedCount 项" else "请选择媒体",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.width(68.dp))
        }
    }
}

@Composable
internal fun SmallAlbumSelectionBottomBar(
    onCancel: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PostActionChip(
                text = "取消",
                onClick = onCancel,
            )
            PostActionChip(
                text = "分享",
                onClick = onShare,
            )
            PostIconButton(
                icon = Icons.Filled.Delete,
                contentDescription = "移出选中媒体",
                onClick = onDelete,
                buttonSize = 44.dp,
                iconSize = 22.dp,
            )
        }
    }
}

@Composable
internal fun SmallAlbumEmptyState(
    onAddMedia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        YingShiToolSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg),
            shape = RoundedCornerShape(radius.xl),
            contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.lg),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = "这段记忆还没放进照片",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
                Text(
                    text = "从照片流挑几张加入这里，封面、时间线和详情氛围就会马上完整起来。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                PostActionChip(
                    text = "去挑照片",
                    onClick = onAddMedia,
                    containerColor = colors.primaryContainer.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SmallAlbumGridMediaTile(
    item: PhotoFeedItem,
    media: PostDetailMediaUiModel,
    highlighted: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    density: PhotoFeedDensity,
    inlineVideoAutoPlayEnabled: Boolean,
    isInlineVideoPlaying: Boolean,
    isInlineVideoActive: Boolean,
    isInlineVideoPaused: Boolean,
    inlineVideoProgress: InlineVideoPlaybackProgress?,
    modifier: Modifier = Modifier,
    onBoundsChange: (Rect?) -> Unit = {},
    onClick: () -> Unit,
    onOpenMedia: () -> Unit,
    onLongClick: () -> Unit,
    onToggleInlineVideo: () -> Unit,
    onInlineVideoProgressChange: (InlineVideoPlaybackProgress) -> Unit,
) {
    val selectionHotspotOnly = selectionMode && density.columns in 2..4
    val colors = YingShiThemeTokens.colors
    val motion = YingShiThemeTokens.motion
    val motionEnabled = rememberYingShiMotionEnabled()
    val supportsInlineVideo = inlineVideoAutoPlayEnabled &&
        !selectionMode &&
        media.mediaType == AppMediaType.VIDEO &&
        density.columns <= 4
    val showSelectionVideoMarker = selectionMode &&
        media.mediaType == AppMediaType.VIDEO &&
        density.columns <= 4
    val itemScale by animateFloatAsState(
        targetValue = if (selected) motion.selectedMediaScale else 1f,
        animationSpec = tween(if (motionEnabled) motion.stateMillis else 0, easing = motion.easing),
        label = "smallAlbumGridTileSelectionScale",
    )
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = itemScale
                scaleY = itemScale
            },
        shape = RoundedCornerShape(YingShiThemeTokens.radius.sm),
        color = colors.glassSurfaceBase.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.52f)),
        shadowElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    onBoundsChange(coordinates.boundsInRoot())
                }
                .combinedClickable(
                    onClick = if (selectionHotspotOnly) onOpenMedia else onClick,
                    onLongClick = onLongClick,
                ),
        ) {
        AppContentMediaThumbnail(
            mediaSource = media.mediaSource,
            mediaType = media.mediaType,
            palette = media.palette,
            modifier = Modifier.matchParentSize(),
            contentDescription = postDetailMediaContentDescription(media.mediaType),
            requestSize = smallAlbumThumbnailRequestSize(density),
            contentScale = ContentScale.Crop,
            showLoadingIndicator = false,
            showVideoPlayOverlay = !(supportsInlineVideo || showSelectionVideoMarker),
        )
        if (supportsInlineVideo && isInlineVideoPlaying) {
            AppContentInlineVideoPlayer(
                mediaSource = media.mediaSource,
                mediaType = media.mediaType,
                playWhenReady = true,
                modifier = Modifier.matchParentSize(),
                onPlaybackProgressChange = onInlineVideoProgressChange,
            )
        }
        YingShiMediaFrame(
            modifier = Modifier.matchParentSize(),
            selected = selected,
            memoryActive = highlighted,
            topScrimAlpha = if (media.mediaType == AppMediaType.VIDEO) 0.22f else 0.14f,
            bottomGlowAlpha = if (selected) 0.24f else 0.16f,
        )
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
        if (media.mediaType == AppMediaType.VIDEO) {
            VideoDurationBadge(
                durationMillis = item.smallAlbumGridVideoBadgeDurationMillis(inlineVideoProgress),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp),
            )
        }
        if (highlighted) {
            YingShiMemoryBadge(
                text = "新加入",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
                compact = density.columns >= 4,
            )
        }
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                colors.glassStroke.copy(alpha = if (selected) 0.22f else 0.06f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            if (selectionHotspotOnly) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(46.dp)
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    AppMediaSelectionBadge(
                        selected = selected,
                        modifier = Modifier.padding(end = 2.dp, bottom = 2.dp),
                    )
                }
            } else {
                AppMediaSelectionBadge(
                    selected = selected,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 2.dp, bottom = 2.dp),
                )
            }
        }
    }
    }
}

internal fun postDetailMediaContentDescription(mediaType: AppMediaType): String {
    return when (mediaType) {
        AppMediaType.VIDEO -> "小相册视频"
        AppMediaType.IMAGE -> "小相册照片"
    }
}

internal fun visibleSmallAlbumVideoIds(
    listState: androidx.compose.foundation.lazy.LazyListState,
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

internal fun centeredSmallAlbumVideoId(
    listState: androidx.compose.foundation.lazy.LazyListState,
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
                row.items.mapIndexedNotNull { colIndex, rowItem ->
                    if (rowItem.mediaType != AppMediaType.VIDEO) return@mapIndexedNotNull null
                    val centerX = edgePaddingPx + colIndex * segmentWidth + cellWidth / 2f
                    val centerY = visibleItem.offset + visibleItem.size / 2f
                    val score = kotlin.math.abs(centerX - viewportCenterX) + kotlin.math.abs(centerY - viewportCenterY)
                    rowItem.mediaId to score
                }
            }
        }
        .minByOrNull { it.second }
        ?.first
}

internal fun PhotoFeedItem.smallAlbumGridVideoBadgeDurationMillis(
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
internal fun SmallAlbumDensityMorphOverlay(
    entries: List<SmallAlbumDensityTransitionOverlayEntry>,
    viewportBounds: Rect?,
    progress: Float,
    thumbnailRequestSize: Int,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    val clampedAlpha = alpha.coerceIn(0f, 1f)
    if (entries.isEmpty() || clampedAlpha <= 0f) return
    val localDensity = LocalDensity.current
    val viewportLeft = viewportBounds?.left ?: 0f
    val viewportTop = viewportBounds?.top ?: 0f
    Box(modifier = modifier.clip(RectangleShape)) {
        entries.forEach { entry ->
            key(entry.item.mediaId) {
                val currentBounds = smallAlbumInterpolateRect(
                    start = entry.startBounds,
                    end = entry.endBounds,
                    progress = progress.coerceIn(0f, 1f),
                )
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
                            this.alpha = clampedAlpha *
                                smallAlbumMorphOverlayViewportAlpha(currentBounds, viewportBounds)
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
                    if (entry.item.mediaType == AppMediaType.VIDEO && entry.item.videoDurationMillis != null) {
                        VideoDurationBadge(
                            durationMillis = entry.item.videoDurationMillis,
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

internal fun visibleSmallAlbumDensityTransitionCandidates(
    blocks: List<PhotoFeedBlock>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    itemBoundsByMediaId: Map<String, Rect>,
    viewportBounds: Rect?,
    density: PhotoFeedDensity,
    densityScope: androidx.compose.ui.unit.Density,
): List<SmallAlbumDensityTransitionCandidate> {
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
                val score = kotlin.math.abs(center.x - viewportCenter.x) + kotlin.math.abs(center.y - viewportCenter.y)
                SmallAlbumDensityTransitionCandidate(
                    item = item,
                    startBounds = bounds,
                    distanceScore = score,
                )
            }
        }
        .sortedBy { it.distanceScore }
}

internal fun smallAlbumDensityFallbackContentScale(
    previewState: DiscreteZoomPreviewState<PhotoFeedDensity>?,
    progress: Float,
): Float {
    val direction = previewState?.direction ?: return 1f
    val targetScale = when (direction) {
        DiscreteZoomDirection.TO_SPARSE -> 1.035f
        DiscreteZoomDirection.TO_DENSE -> 0.965f
    }
    return smallAlbumLerpFloat(
        start = 1f,
        end = targetScale,
        progress = smallAlbumSmoothStep(progress.coerceIn(0f, 1f)),
    )
}

internal fun smallAlbumSourceMediaAlpha(progress: Float): Float {
    return smallAlbumLerpFloat(
        start = 1f,
        end = 0.86f,
        progress = progress.coerceIn(0f, 1f),
    )
}

internal fun smallAlbumTargetSceneAlpha(progress: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    return smallAlbumSmoothStep(((t - 0.02f) / 0.90f).coerceIn(0f, 1f))
}

internal fun smallAlbumCommitTargetOverlayAlpha(
    releaseAlpha: Float,
    progress: Float,
): Float {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val growth = smallAlbumLerpFloat(
        start = releaseAlpha.coerceIn(0f, 1f),
        end = 1f,
        progress = (clampedProgress / 0.68f).coerceIn(0f, 1f),
    )
    val fadeProgress = ((clampedProgress - 0.58f) / 0.42f).coerceIn(0f, 1f)
    return (growth * (1f - smallAlbumSmoothStep(fadeProgress))).coerceIn(0f, 1f)
}

internal fun smallAlbumSmoothStep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun smallAlbumLerpFloat(
    start: Float,
    end: Float,
    progress: Float,
): Float {
    return start + (end - start) * progress
}

internal fun smallAlbumInterpolateRect(
    start: Rect,
    end: Rect,
    progress: Float,
): Rect {
    val t = progress.coerceIn(0f, 1f)
    return Rect(
        left = smallAlbumLerpFloat(start.left, end.left, t),
        top = smallAlbumLerpFloat(start.top, end.top, t),
        right = smallAlbumLerpFloat(start.right, end.right, t),
        bottom = smallAlbumLerpFloat(start.bottom, end.bottom, t),
    )
}

internal fun smallAlbumMorphOverlayViewportAlpha(
    bounds: Rect,
    viewportBounds: Rect?,
): Float {
    val viewport = viewportBounds ?: return 1f
    val viewportCenter = viewport.center
    val distanceX = kotlin.math.abs(bounds.center.x - viewportCenter.x)
    val distanceY = kotlin.math.abs(bounds.center.y - viewportCenter.y)
    val maxDistanceX = (viewport.width * 0.62f).coerceAtLeast(1f)
    val maxDistanceY = (viewport.height * 0.62f).coerceAtLeast(1f)
    val normalizedX = (distanceX / maxDistanceX).coerceIn(0f, 1f)
    val normalizedY = (distanceY / maxDistanceY).coerceIn(0f, 1f)
    return 1f - (normalizedX * 0.28f + normalizedY * 0.24f)
}

internal fun buildSmallAlbumPredictedLocalBoundsByMediaId(
    blocks: List<PhotoFeedBlock>,
    targetDensity: PhotoFeedDensity,
    viewportBounds: Rect,
    densityScope: androidx.compose.ui.unit.Density,
): Map<String, Rect> {
    if (blocks.isEmpty()) return emptyMap()
    val rowSpacingPx = with(densityScope) { rowSpacing(targetDensity).toPx() }
    val sectionSpacingPx = rowSpacingPx
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

            is PhotoFeedSectionHeader -> {
                currentTop += with(densityScope) {
                    when (block.granularity) {
                        PhotoFeedTimeGranularity.YEAR -> 56.dp.toPx()
                        PhotoFeedTimeGranularity.MONTH -> 52.dp.toPx()
                        PhotoFeedTimeGranularity.DAY -> 37.dp.toPx()
                    }
                }
            }

            is PhotoFeedDayHeader -> currentTop += with(densityScope) { 37.dp.toPx() }
            else -> Unit
        }
        if (index != blocks.lastIndex) {
            currentTop += sectionSpacingPx
        }
    }
    return boundsByMediaId
}

internal fun buildSmallAlbumPredictedBoundsByMediaId(
    candidates: List<SmallAlbumDensityTransitionCandidate>,
    targetLocalBoundsByMediaId: Map<String, Rect>,
    anchorMediaId: String,
): Map<String, Rect> {
    val anchorStartBounds = candidates.firstOrNull { it.item.mediaId == anchorMediaId }?.startBounds
        ?: return emptyMap()
    val anchorLocalBounds = targetLocalBoundsByMediaId[anchorMediaId] ?: return emptyMap()
    val deltaY = anchorStartBounds.center.y - anchorLocalBounds.center.y
    return candidates.mapNotNull { candidate ->
        val localBounds = targetLocalBoundsByMediaId[candidate.item.mediaId] ?: return@mapNotNull null
        candidate.item.mediaId to Rect(
            left = localBounds.left,
            top = localBounds.top + deltaY,
            right = localBounds.right,
            bottom = localBounds.bottom + deltaY,
        )
    }.toMap()
}

@Composable
internal fun SmallAlbumNewBadge(
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
            text = "新加入",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onMemoryContainer,
            maxLines = 1,
        )
    }
}

internal fun smallAlbumSectionSpacing(density: PhotoFeedDensity): Dp {
    return smallAlbumRowSpacing(density)
}

internal fun smallAlbumRowSpacing(density: PhotoFeedDensity): Dp {
    return rowSpacing(density)
}

internal fun smallAlbumThumbnailRequestSize(density: PhotoFeedDensity): Int {
    return photoFeedThumbnailRequestSize(density)
}

@Composable
internal fun SmallAlbumMonthHeaderRow(title: String) {
    PhotoFeedSectionHeaderRow(title = title)
}

@Composable
internal fun SmallAlbumDayHeaderRow(title: String) {
    PhotoFeedDayHeaderRow(title = title)
}
