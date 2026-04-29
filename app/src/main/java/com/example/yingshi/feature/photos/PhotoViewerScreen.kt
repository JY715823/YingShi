package com.example.yingshi.feature.photos

import android.app.Activity
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ViewerNightTop = Color(0xFF07111C)
private val ViewerNightBottom = Color(0xFF03070E)
private val ViewerNightMiddle = Color(0xFF102032)
private val ViewerSurface = Color(0xFFFFFFFF)
private const val MinViewerScale = 1f
private const val MaxViewerScale = 4f
private const val ViewerZoomResetThreshold = 1.02f
private const val ViewerLongImageThreshold = 2.5f
private const val DefaultViewerVideoDurationMillis = 18_000L

private object ViewerLayoutTuning {
    val topBarStartInset = 4.dp
    val topBarEndInset = 10.dp
    val topBarTopInset = 6.dp
    val backButtonTouchSize = 42.dp
    val canvasHorizontalPadding = 0.dp
    val canvasTopPadding = 62.dp
    val canvasBottomPadding = 170.dp
    const val commentPreviewWidthFraction = 0.70f
    val commentPreviewMaxWidth = 288.dp
    val commentPreviewHeight = 172.dp
    val photoFlowEdgeActionsBottomPadding = 34.dp
    val inPostEdgeActionsBottomPadding = 50.dp
    val postSegmentBottomOffset = 12.dp
    const val commentSheetHeightFraction = 0.68f
    const val relatedPostsSheetHeightFraction = 0.42f
    const val zoomedOverlayAlpha = 0.42f
    const val previewCommentsMaxCount = 10
}

private data class ViewerCommentPanelState(
    val selectedCommentId: String? = null,
)

private data class ViewerVideoPlaybackState(
    val mediaId: String? = null,
    val isPlaying: Boolean = false,
    val progressMillis: Long = 0L,
)

private class ViewerZoomState {
    var scale by mutableStateOf(MinViewerScale)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    val isZoomed: Boolean
        get() = scale > ViewerZoomResetThreshold

    fun applyTransform(
        zoomChange: Float,
        panChange: Offset,
        containerSize: IntSize,
        contentSize: IntSize,
    ) {
        val nextScale = (scale * zoomChange).coerceIn(MinViewerScale, MaxViewerScale)
        if (nextScale <= ViewerZoomResetThreshold) {
            reset()
            return
        }

        scale = nextScale
        offset = clampOffset(offset + panChange, nextScale, containerSize, contentSize)
    }

    fun panBy(
        panChange: Offset,
        containerSize: IntSize,
        contentSize: IntSize,
    ) {
        if (!isZoomed) return
        offset = clampOffset(offset + panChange, scale, containerSize, contentSize)
    }

    fun reset() {
        scale = MinViewerScale
        offset = Offset.Zero
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

private fun Modifier.viewerZoomGesture(
    zoomState: ViewerZoomState,
    contentSize: IntSize,
): Modifier = pointerInput(zoomState, contentSize) {
    awaitEachGesture {
        while (true) {
            val event = awaitPointerEvent()
            val activeChanges = event.changes.filter { it.pressed }
            if (activeChanges.isEmpty()) break

            if (activeChanges.size >= 2) {
                val currentCentroid = activeChanges.centroid(usePrevious = false)
                val previousCentroid = activeChanges.centroid(usePrevious = true)
                val currentDistance = activeChanges.averageDistanceTo(currentCentroid, usePrevious = false)
                val previousDistance = activeChanges.averageDistanceTo(previousCentroid, usePrevious = true)
                val zoomChange = if (previousDistance > 0f) {
                    currentDistance / previousDistance
                } else {
                    MinViewerScale
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

private fun List<PointerInputChange>.centroid(usePrevious: Boolean): Offset {
    val total = fold(Offset.Zero) { sum, change ->
        sum + if (usePrevious) change.previousPosition else change.position
    }
    return total / size.toFloat()
}

private fun List<PointerInputChange>.averageDistanceTo(
    centroid: Offset,
    usePrevious: Boolean,
): Float {
    return sumOf { change ->
        val position = if (usePrevious) change.previousPosition else change.position
        (position - centroid).getDistance().toDouble()
    }.toFloat() / size
}

@Composable
private fun ViewerStatusBarEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        val activity = view.context.findActivity()
        val window = activity?.window
        val previousStatusBarColor = window?.statusBarColor
        val previousLightStatusBars = window?.let {
            WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars
        }

        if (window != null) {
            window.statusBarColor = android.graphics.Color.BLACK
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }

        onDispose {
            if (window != null && previousStatusBarColor != null && previousLightStatusBars != null) {
                window.statusBarColor = previousStatusBarColor
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                    previousLightStatusBars
            }
        }
    }
}

private tailrec fun android.content.Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

@Composable
fun PhotoViewerScreen(
    route: PhotoViewerRoute,
    onBack: () -> Unit,
    onOpenCacheManagement: (CacheManagementRoute) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (route.mediaItems.isEmpty()) {
        EmptyPhotoViewerScreen(
            onBack = onBack,
            modifier = modifier,
        )
        return
    }

    val context = LocalContext.current
    val spacing = YingShiThemeTokens.spacing
    val settingsState = FakeSettingsRepository.getSettingsState()
    val initialPage = route.initialIndex.coerceIn(0, route.mediaItems.lastIndex)
    val zoomState = remember { ViewerZoomState() }
    var showCommentPreview by remember { mutableStateOf(false) }
    var commentPanelState by remember { mutableStateOf<ViewerCommentPanelState?>(null) }
    var showRelatedPostsSheet by remember { mutableStateOf(false) }
    var showCacheActionSheet by remember { mutableStateOf(false) }
    var videoPlaybackState by remember {
        mutableStateOf(ViewerVideoPlaybackState())
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { route.mediaItems.size },
    )
    val currentIndex by remember(route.mediaItems, pagerState) {
        derivedStateOf {
            pagerState.currentPage.coerceIn(0, route.mediaItems.lastIndex)
        }
    }
    val currentItem = route.mediaItems[currentIndex]
    val currentVideoDurationMillis = currentItem.viewerVideoDurationMillis()
    val currentOriginalState = FakeOriginalLoadRepository.getState(currentItem.mediaId)
    val currentCacheState = FakeMediaCacheRepository.getState(
        mediaId = currentItem.mediaId,
        mediaType = currentItem.mediaType,
    )
    val mediaComments = CommentGateway.getMediaComments(currentItem.mediaId)
    val previewComments = mediaComments.take(ViewerLayoutTuning.previewCommentsMaxCount)
    val edgeActionsBottomPadding = if (route.showPostSegments) {
        ViewerLayoutTuning.inPostEdgeActionsBottomPadding
    } else {
        ViewerLayoutTuning.photoFlowEdgeActionsBottomPadding
    }
    val hideOverlaysWhenZoomed = settingsState.viewerPreferences.hideOverlaysWhenZoomed
    val overlaysVisible = !zoomState.isZoomed || !hideOverlaysWhenZoomed
    val overlayAlpha = if (zoomState.isZoomed && hideOverlaysWhenZoomed) {
        ViewerLayoutTuning.zoomedOverlayAlpha
    } else {
        1f
    }
    val relatedPosts = remember(currentItem.postIds) {
        fakeViewerRelatedPosts(currentItem)
    }
    val overlayUiModel = remember(
        currentItem,
        currentOriginalState,
        previewComments,
        relatedPosts,
    ) {
        PhotoViewerOverlayUiModel(
            commentCountLabel = mediaComments.size.toString(),
            timeLabel = formatViewerTime(currentItem.mediaDisplayTimeMillis),
            originalLoadState = currentOriginalState,
            relatedPostsLabel = if (currentItem.postIds.isNotEmpty()) {
                if (currentItem.postIds.size > 1) {
                    "所属帖子 ${currentItem.postIds.size}"
                } else {
                    "所属帖子"
                }
            } else {
                null
            },
            relatedPosts = relatedPosts,
            previewComments = previewComments,
        )
    }

    LaunchedEffect(currentIndex) {
        zoomState.reset()
        showCommentPreview = false
        commentPanelState = null
        showRelatedPostsSheet = false
        showCacheActionSheet = false
        videoPlaybackState = ViewerVideoPlaybackState(
            mediaId = currentItem.mediaId.takeIf { currentItem.mediaType == AppMediaType.VIDEO },
        )
    }
    LaunchedEffect(
        currentItem.mediaId,
        currentItem.mediaType,
        currentVideoDurationMillis,
        videoPlaybackState.isPlaying,
        videoPlaybackState.progressMillis,
    ) {
        if (currentItem.mediaType != AppMediaType.VIDEO) return@LaunchedEffect
        if (videoPlaybackState.mediaId != currentItem.mediaId) return@LaunchedEffect
        if (!videoPlaybackState.isPlaying) return@LaunchedEffect
        if (videoPlaybackState.progressMillis >= currentVideoDurationMillis) {
            videoPlaybackState = videoPlaybackState.copy(isPlaying = false)
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(220)
        val nextProgress = (videoPlaybackState.progressMillis + 220L)
            .coerceAtMost(currentVideoDurationMillis)
        videoPlaybackState = videoPlaybackState.copy(
            progressMillis = nextProgress,
            isPlaying = nextProgress < currentVideoDurationMillis,
        )
    }
    BackHandler(enabled = zoomState.isZoomed) {
        zoomState.reset()
    }
    BackHandler(enabled = showCommentPreview) {
        showCommentPreview = false
    }
    BackHandler(enabled = commentPanelState != null) {
        commentPanelState = null
    }
    BackHandler(enabled = showRelatedPostsSheet) {
        showRelatedPostsSheet = false
    }
    ViewerStatusBarEffect()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ViewerNightTop,
                        currentItem.palette.end.copy(alpha = 0.22f),
                        ViewerNightMiddle,
                        ViewerNightBottom,
                    ),
                ),
            ),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = route.mediaItems.size > 1 && !zoomState.isZoomed,
            key = { page -> route.mediaItems[page].mediaId },
        ) { page ->
            PhotoViewerCanvas(
                media = route.mediaItems[page],
                zoomState = if (page == currentIndex) zoomState else null,
                videoPlaybackState = if (page == currentIndex) videoPlaybackState else null,
                modifier = Modifier.fillMaxSize(),
            )
        }

        ViewerTopScrim(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(148.dp),
        )

        if (overlaysVisible) {
            ViewerBottomScrim(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(236.dp),
            )
        }

        PhotoViewerTopBar(
            onBack = onBack,
            timeLabel = overlayUiModel.timeLabel,
            onOpenSettings = {
                showCacheActionSheet = true
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = ViewerLayoutTuning.topBarStartInset,
                    end = ViewerLayoutTuning.topBarEndInset,
                    top = ViewerLayoutTuning.topBarTopInset,
                ),
            overlayAlpha = overlayAlpha,
        )

        if (overlaysVisible) {
            if (showCommentPreview) {
                ViewerCommentPreviewLayer(
                    comments = overlayUiModel.previewComments,
                    onOpenComment = { commentId ->
                        commentPanelState = ViewerCommentPanelState(selectedCommentId = commentId)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(
                            start = spacing.lg,
                            bottom = edgeActionsBottomPadding + 64.dp,
                        ),
                )
            }

            if (currentItem.mediaType == AppMediaType.VIDEO) {
                ViewerVideoControls(
                    playbackState = videoPlaybackState,
                    durationMillis = currentVideoDurationMillis,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = spacing.xxl,
                            end = spacing.xxl,
                            bottom = edgeActionsBottomPadding + 78.dp,
                        ),
                    onTogglePlayback = {
                        val shouldRestart = videoPlaybackState.progressMillis >= currentVideoDurationMillis
                        videoPlaybackState = if (videoPlaybackState.isPlaying) {
                            videoPlaybackState.copy(isPlaying = false)
                        } else {
                            videoPlaybackState.copy(
                                mediaId = currentItem.mediaId,
                                isPlaying = true,
                                progressMillis = if (shouldRestart) 0L else {
                                    videoPlaybackState.progressMillis
                                },
                            )
                        }
                    },
                )
            }

            PhotoViewerEdgeActions(
                overlayUiModel = overlayUiModel,
                showCommentPreview = showCommentPreview,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        start = spacing.lg,
                        end = spacing.lg,
                        top = spacing.lg,
                        bottom = edgeActionsBottomPadding,
                    ),
                onOpenComments = {
                    if (!showCommentPreview) {
                        showCommentPreview = true
                    } else {
                        showCommentPreview = false
                    }
                },
                onOpenOriginal = {
                    when (currentOriginalState) {
                        OriginalLoadState.NotLoaded -> {
                            FakeOriginalLoadRepository.loadOriginal(currentItem.mediaId)
                            Toast.makeText(context, "\u5f00\u59cb\u52a0\u8f7d\u539f\u56fe", Toast.LENGTH_SHORT).show()
                        }

                        OriginalLoadState.Loading -> {
                            Toast.makeText(context, "\u539f\u56fe\u52a0\u8f7d\u4e2d...", Toast.LENGTH_SHORT).show()
                        }

                        OriginalLoadState.Loaded -> {
                            Toast.makeText(context, "\u5df2\u52a0\u8f7d\u539f\u56fe", Toast.LENGTH_SHORT).show()
                        }

                        OriginalLoadState.Failed -> {
                            FakeOriginalLoadRepository.retryOriginal(currentItem.mediaId)
                            Toast.makeText(context, "\u91cd\u8bd5\u52a0\u8f7d\u539f\u56fe", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onOpenRelatedPosts = {
                    if (overlayUiModel.relatedPosts.size > 1) {
                        showRelatedPostsSheet = true
                    } else {
                        val post = overlayUiModel.relatedPosts.firstOrNull()
                        Toast.makeText(
                            context,
                            post?.let { "将进入「${it.title}」（占位）" } ?: "当前媒体暂无所属帖子",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
        }

        if (route.showPostSegments) {
            ViewerPostSegmentIndicator(
                currentIndex = currentIndex,
                total = route.mediaItems.size,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        start = spacing.xl,
                        end = spacing.xl,
                        bottom = ViewerLayoutTuning.postSegmentBottomOffset,
                    ),
                alpha = if (zoomState.isZoomed && hideOverlaysWhenZoomed) {
                    ViewerLayoutTuning.zoomedOverlayAlpha
                } else {
                    0.92f
                },
            )
        }

        commentPanelState?.let { panelState ->
            PhotoViewerCommentSheet(
                mediaId = currentItem.mediaId,
                comments = mediaComments,
                selectedCommentId = panelState.selectedCommentId,
                onDismiss = { commentPanelState = null },
            )
        }

        if (showRelatedPostsSheet) {
            ViewerRelatedPostsSheet(
                posts = overlayUiModel.relatedPosts,
                onSelectPost = { post ->
                    showRelatedPostsSheet = false
                    Toast.makeText(context, "将进入「${post.title}」（占位）", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showRelatedPostsSheet = false },
            )
        }

        if (showCacheActionSheet) {
            ViewerCacheActionSheet(
                cacheState = currentCacheState,
                onDismiss = { showCacheActionSheet = false },
                onClearPreviewCache = {
                    FakeMediaCacheRepository.clearPreviewCache(currentItem.mediaId)
                    Toast.makeText(context, "已清理预览缓存", Toast.LENGTH_SHORT).show()
                },
                onClearOriginalCache = {
                    FakeMediaCacheRepository.clearOriginalCache(currentItem.mediaId)
                    Toast.makeText(context, "已清理原图缓存", Toast.LENGTH_SHORT).show()
                },
                onClearVideoCache = if (currentItem.mediaType == AppMediaType.VIDEO) {
                    {
                        FakeMediaCacheRepository.clearVideoCache(currentItem.mediaId)
                        Toast.makeText(context, "已清理视频缓存", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    null
                },
                onOpenGlobalCacheManagement = {
                    showCacheActionSheet = false
                    onOpenCacheManagement(
                        CacheManagementRoute(
                            source = if (route.showPostSegments) {
                                "in-post-viewer"
                            } else {
                                "photo-flow-viewer"
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun ViewerPostSegmentIndicator(
    currentIndex: Int,
    total: Int,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    if (total <= 1) return

    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Row(
        modifier = modifier.alpha(alpha),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(radius.capsule))
                    .background(
                        if (index <= currentIndex) {
                            ViewerSurface.copy(alpha = 0.82f)
                        } else {
                            ViewerSurface.copy(alpha = 0.22f)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun ViewerTopScrim(modifier: Modifier = Modifier) {
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
private fun ViewerBottomScrim(modifier: Modifier = Modifier) {
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
private fun EmptyPhotoViewerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(ViewerNightTop, ViewerNightBottom),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onBack) {
                Text(text = "返回", color = ViewerSurface.copy(alpha = 0.90f))
            }
            Text(
                text = "当前没有可查看的媒体",
                style = MaterialTheme.typography.titleMedium,
                color = ViewerSurface.copy(alpha = 0.92f),
            )
        }
    }
}

@Composable
private fun PhotoViewerTopBar(
    onBack: () -> Unit,
    timeLabel: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    overlayAlpha: Float = 1f,
) {
    Box(
        modifier = modifier.alpha(overlayAlpha),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(ViewerLayoutTuning.backButtonTouchSize)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "<",
                style = MaterialTheme.typography.headlineSmall,
                color = ViewerSurface.copy(alpha = 0.92f),
            )
        }

        ViewerCapsule(
            text = timeLabel,
            emphasized = false,
            modifier = Modifier.align(Alignment.TopCenter),
            surfaceAlpha = 0.06f,
            contentAlpha = 0.78f,
        )

        ViewerCapsule(
            text = "设置",
            emphasized = false,
            modifier = Modifier.align(Alignment.TopEnd),
            surfaceAlpha = 0.08f,
            contentAlpha = 0.80f,
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun PhotoViewerCanvas(
    media: PhotoFeedItem,
    zoomState: ViewerZoomState?,
    videoPlaybackState: ViewerVideoPlaybackState?,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val density = LocalDensity.current
    val isVideo = media.mediaType == AppMediaType.VIDEO
    val isLongImage = media.isViewerLongImage()
    val canvasAspectRatio = if (isLongImage) {
        media.viewerAspectRatio()
    } else {
        media.viewerAspectRatio().coerceIn(0.75f, 1.35f)
    }
    val longImageScrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = modifier.padding(
            start = ViewerLayoutTuning.canvasHorizontalPadding,
            top = ViewerLayoutTuning.canvasTopPadding,
            end = ViewerLayoutTuning.canvasHorizontalPadding,
            bottom = ViewerLayoutTuning.canvasBottomPadding,
        ),
        contentAlignment = if (isLongImage) Alignment.TopCenter else Alignment.Center,
    ) {
        val canvasWidth = if (isLongImage) {
            maxWidth
        } else {
            val fillWidthHeight = maxWidth / canvasAspectRatio
            if (fillWidthHeight <= maxHeight) {
                maxWidth
            } else {
                maxHeight * canvasAspectRatio
            }
        }
        val canvasHeight = if (isLongImage) {
            canvasWidth / canvasAspectRatio.coerceAtLeast(0.2f)
        } else {
            val fillWidthHeight = maxWidth / canvasAspectRatio
            if (fillWidthHeight <= maxHeight) {
                fillWidthHeight
            } else {
                maxHeight
            }
        }
        val contentSize = with(density) {
            IntSize(canvasWidth.roundToPx(), canvasHeight.roundToPx())
        }
        val zoomModifier = if (zoomState != null) {
            Modifier
                .graphicsLayer {
                    scaleX = zoomState.scale
                    scaleY = zoomState.scale
                    translationX = zoomState.offset.x
                    translationY = zoomState.offset.y
                }
                .viewerZoomGesture(
                    zoomState = zoomState,
                    contentSize = contentSize,
                )
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isLongImage && zoomState?.isZoomed != true) {
                        Modifier.verticalScroll(longImageScrollState)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = if (isLongImage) Alignment.TopCenter else Alignment.Center,
        ) {
            if (isVideo) {
                ViewerVideoCanvas(
                    media = media,
                    playbackState = videoPlaybackState,
                    modifier = Modifier
                        .width(canvasWidth)
                        .height(canvasHeight),
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(canvasWidth)
                        .height(canvasHeight)
                        .then(zoomModifier)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    media.palette.start.copy(alpha = 0.98f),
                                    media.palette.end.copy(alpha = 0.90f),
                                ),
                            ),
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = spacing.lg, end = spacing.lg)
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(media.palette.accent.copy(alpha = 0.20f)),
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = spacing.lg, bottom = spacing.lg)
                            .fillMaxWidth(0.46f)
                            .aspectRatio(2.5f)
                            .clip(RoundedCornerShape(YingShiThemeTokens.radius.capsule))
                            .background(media.palette.accent.copy(alpha = 0.14f)),
                    )

                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.10f),
                                        Color.Transparent,
                                        ViewerNightTop.copy(alpha = 0.12f),
                                    ),
                                ),
                            ),
                    )

                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = spacing.md, bottom = spacing.md),
                        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                        color = ViewerNightTop.copy(alpha = 0.18f),
                    ) {
                        Text(
                            text = "媒体预览占位",
                            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                            style = MaterialTheme.typography.labelMedium,
                            color = ViewerSurface.copy(alpha = 0.82f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerVideoCanvas(
    media: PhotoFeedItem,
    playbackState: ViewerVideoPlaybackState?,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val isPlaying = playbackState?.isPlaying == true
    val progressFraction = if (playbackState == null) {
        0f
    } else {
        (playbackState.progressMillis.toFloat() / media.viewerVideoDurationMillis().toFloat())
            .coerceIn(0f, 1f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius.xl))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        ViewerNightTop.copy(alpha = 0.96f),
                        media.palette.end.copy(alpha = 0.34f),
                        ViewerNightBottom.copy(alpha = 0.98f),
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = spacing.lg, end = spacing.lg)
                .size(96.dp)
                .clip(CircleShape)
                .background(media.palette.accent.copy(alpha = 0.12f)),
        )

        VideoMediaMarker(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(spacing.md),
            showLabel = true,
        )

        Surface(
            modifier = Modifier.align(Alignment.Center),
            shape = CircleShape,
            color = Color.White.copy(alpha = if (isPlaying) 0.14f else 0.18f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .padding(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                VideoGlyph(
                    state = if (isPlaying) VideoGlyphState.PAUSE else VideoGlyphState.PLAY,
                    tint = ViewerSurface.copy(alpha = 0.92f),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Text(
            text = if (isPlaying) "正在播放本地视频壳子" else "视频预览占位",
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 112.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(radius.capsule),
                )
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelLarge,
            color = ViewerSurface.copy(alpha = 0.84f),
        )

        LinearProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.82f)
                .padding(bottom = spacing.lg)
                .height(4.dp)
                .clip(RoundedCornerShape(radius.capsule)),
            color = ViewerSurface.copy(alpha = 0.88f),
            trackColor = ViewerSurface.copy(alpha = 0.16f),
        )
    }
}

@Composable
private fun ViewerVideoControls(
    playbackState: ViewerVideoPlaybackState,
    durationMillis: Long,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val progressFraction = if (durationMillis <= 0L) {
        0f
    } else {
        (playbackState.progressMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius.xl),
        color = Color.Black.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, ViewerSurface.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.clickable(onClick = onTogglePlayback),
                    shape = CircleShape,
                    color = ViewerSurface.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, ViewerSurface.copy(alpha = 0.10f)),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .padding(11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        VideoGlyph(
                            state = if (playbackState.isPlaying) {
                                VideoGlyphState.PAUSE
                            } else {
                                VideoGlyphState.PLAY
                            },
                            tint = ViewerSurface.copy(alpha = 0.92f),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    Text(
                        text = if (playbackState.isPlaying) "播放中" else "已暂停",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = ViewerSurface.copy(alpha = 0.92f),
                    )
                    Text(
                        text = "${formatVideoProgress(playbackState.progressMillis)} / ${formatVideoProgress(durationMillis)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = ViewerSurface.copy(alpha = 0.68f),
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(radius.capsule)),
                color = ViewerSurface.copy(alpha = 0.88f),
                trackColor = ViewerSurface.copy(alpha = 0.18f),
            )
        }
    }
}

private fun PhotoFeedItem.viewerAspectRatio(): Float {
    val widthValue = width
    val heightValue = height
    if (widthValue != null && heightValue != null && widthValue > 0 && heightValue > 0) {
        return widthValue.toFloat() / heightValue.toFloat()
    }
    return aspectRatio.coerceAtLeast(0.2f)
}

private fun PhotoFeedItem.isViewerLongImage(): Boolean {
    if (mediaType == AppMediaType.VIDEO) return false
    val widthValue = width
    val heightValue = height
    if (widthValue != null && heightValue != null && widthValue > 0 && heightValue > 0) {
        return heightValue.toFloat() / widthValue.toFloat() >= ViewerLongImageThreshold
    }
    return aspectRatio > 0f && (1f / aspectRatio) >= ViewerLongImageThreshold
}

private fun PhotoFeedItem.viewerVideoDurationMillis(): Long {
    return videoDurationMillis ?: DefaultViewerVideoDurationMillis
}

private fun formatVideoProgress(timeMillis: Long): String {
    val totalSeconds = (timeMillis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun PhotoViewerEdgeActions(
    overlayUiModel: PhotoViewerOverlayUiModel,
    showCommentPreview: Boolean,
    onOpenComments: () -> Unit,
    onOpenOriginal: () -> Unit,
    onOpenRelatedPosts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        ViewerCommentEntry(
            commentCountLabel = overlayUiModel.commentCountLabel,
            previewExpanded = showCommentPreview,
            onClick = onOpenComments,
        )

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            ViewerCapsule(
                text = overlayUiModel.originalLoadState.label,
                emphasized = overlayUiModel.originalLoadState == OriginalLoadState.Loaded,
                enabled = overlayUiModel.originalLoadState != OriginalLoadState.Loading,
                onClick = onOpenOriginal,
            )
            overlayUiModel.relatedPostsLabel?.let { relatedPostsLabel ->
                ViewerCapsule(
                    text = relatedPostsLabel,
                    emphasized = false,
                    onClick = onOpenRelatedPosts,
                )
            }
        }
    }
}

@Composable
private fun ViewerCommentEntry(
    commentCountLabel: String,
    previewExpanded: Boolean,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(radius.capsule))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = ViewerSurface.copy(alpha = if (previewExpanded) 0.18f else 0.12f),
        ) {
            Text(
                text = "评",
                modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.sm),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ViewerSurface.copy(alpha = 0.94f),
            )
        }

        if (commentCountLabel != "0") {
            ViewerCapsule(
                text = commentCountLabel,
                emphasized = true,
                surfaceAlpha = if (previewExpanded) 0.18f else 0.14f,
            )
        }
    }
}

@Composable
private fun ViewerCapsule(
    text: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    surfaceAlpha: Float = if (emphasized) 0.14f else 0.10f,
    contentAlpha: Float = 0.94f,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val shape = RoundedCornerShape(radius.capsule)

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
        color = ViewerSurface.copy(alpha = if (enabled) surfaceAlpha else 0.07f),
        border = BorderStroke(
            width = 1.dp,
            color = ViewerSurface.copy(alpha = if (enabled) surfaceAlpha + 0.04f else 0.08f),
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = if (emphasized) {
                MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            } else {
                MaterialTheme.typography.labelLarge
            },
            color = ViewerSurface.copy(alpha = if (enabled) contentAlpha else 0.58f),
        )
    }
}

@Composable
private fun ViewerCommentPreviewLayer(
    comments: List<CommentUiModel>,
    onOpenComment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Column(
        modifier = modifier
            .fillMaxWidth(ViewerLayoutTuning.commentPreviewWidthFraction)
            .widthIn(max = ViewerLayoutTuning.commentPreviewMaxWidth)
            .height(ViewerLayoutTuning.commentPreviewHeight)
            .clip(RoundedCornerShape(radius.lg))
            .background(Color.Black.copy(alpha = 0.34f))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.md, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        if (comments.isEmpty()) {
            Text(
                text = "当前媒体还没有评论",
                modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.xs),
                style = MaterialTheme.typography.bodySmall,
                color = ViewerSurface.copy(alpha = 0.62f),
            )
        } else {
            comments.forEach { comment ->
                Text(
                    text = "${comment.author}：${comment.content}",
                    modifier = Modifier
                        .clip(RoundedCornerShape(radius.sm))
                        .clickable { onOpenComment(comment.id) }
                        .padding(horizontal = spacing.xs, vertical = spacing.xs),
                    style = MaterialTheme.typography.bodySmall,
                    color = ViewerSurface.copy(alpha = 0.86f),
                    maxLines = 2,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerCacheActionSheet(
    cacheState: AppMediaCacheState,
    onDismiss: () -> Unit,
    onClearPreviewCache: () -> Unit,
    onClearOriginalCache: () -> Unit,
    onClearVideoCache: (() -> Unit)?,
    onOpenGlobalCacheManagement: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ViewerNightTop,
        contentColor = ViewerSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = "清理缓存",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ViewerSurface.copy(alpha = 0.94f),
            )
            Text(
                text = "当前媒体 fake 缓存 ${cacheState.cacheSizeLabel} · 只做本地状态变化",
                style = MaterialTheme.typography.labelMedium,
                color = ViewerSurface.copy(alpha = 0.62f),
            )
            ViewerCacheActionRow(
                title = "清理预览缓存",
                subtitle = if (cacheState.previewCached) "当前标记为已缓存" else "当前已是未缓存状态",
                onClick = onClearPreviewCache,
            )
            ViewerCacheActionRow(
                title = "清理原图缓存",
                subtitle = if (cacheState.originalCached) {
                    "清理后会回到“加载原图”"
                } else {
                    "当前原图尚未缓存"
                },
                onClick = onClearOriginalCache,
            )
            if (onClearVideoCache != null) {
                ViewerCacheActionRow(
                    title = "清理视频缓存",
                    subtitle = if (cacheState.videoCached) "当前视频缓存可清理" else "当前视频已是未缓存状态",
                    onClick = onClearVideoCache,
                )
            }
            ViewerCacheActionRow(
                title = "打开全局缓存管理",
                subtitle = "查看 fake 总量并清理全部预览 / 原图 / 视频缓存",
                onClick = onOpenGlobalCacheManagement,
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(text = "关闭", color = ViewerSurface.copy(alpha = 0.88f))
            }
        }
    }
}

@Composable
private fun ViewerCacheActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.lg))
            .background(ViewerSurface.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = ViewerSurface.copy(alpha = 0.90f),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = ViewerSurface.copy(alpha = 0.62f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoViewerCommentSheet(
    mediaId: String,
    comments: List<CommentUiModel>,
    selectedCommentId: String?,
    onDismiss: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val context = LocalContext.current
    val copyComment = rememberCommentCopyHandler()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(mediaId) { mutableStateOf(false) }
    var actionCommentId by androidx.compose.runtime.saveable.rememberSaveable(mediaId) { mutableStateOf<String?>(null) }
    var editingCommentId by androidx.compose.runtime.saveable.rememberSaveable(mediaId) { mutableStateOf<String?>(null) }
    var editingDraft by androidx.compose.runtime.saveable.rememberSaveable(mediaId) { mutableStateOf("") }
    var selectedForCopyCommentId by androidx.compose.runtime.saveable.rememberSaveable(mediaId) { mutableStateOf<String?>(null) }
    var selectedCommentValue by androidx.compose.runtime.saveable.rememberSaveable(mediaId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val visibleComments = comments.visibleComments(expanded)

    BackHandler(enabled = selectedForCopyCommentId != null) {
        selectedForCopyCommentId = null
        selectedCommentValue = TextFieldValue("")
    }
    BackHandler(enabled = actionCommentId != null) {
        actionCommentId = null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ViewerNightTop,
        contentColor = ViewerSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(ViewerLayoutTuning.commentSheetHeightFraction)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = "媒体评论",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ViewerSurface.copy(alpha = 0.94f),
            )
            if (selectedCommentId != null) {
                Text(
                    text = "已定位到预览评论，占位高亮如下",
                    style = MaterialTheme.typography.labelMedium,
                    color = ViewerSurface.copy(alpha = 0.58f),
                )
            }
            if (visibleComments.isEmpty()) {
                Text(
                    text = "当前媒体还没有评论，先写下第一条本地媒体评论。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ViewerSurface.copy(alpha = 0.68f),
                )
            } else {
                visibleComments.forEach { comment ->
                    CommentListItem(
                        comment = comment,
                        timeLabel = formatViewerTime(comment.createdAtMillis),
                        onLongPress = {
                            selectedForCopyCommentId = null
                            selectedCommentValue = TextFieldValue("")
                            editingCommentId = null
                            editingDraft = ""
                            actionCommentId = comment.id
                        },
                        onClick = {
                            if (selectedForCopyCommentId != null) {
                                selectedForCopyCommentId = null
                                selectedCommentValue = TextFieldValue("")
                            }
                            actionCommentId = null
                        },
                        darkMode = true,
                        highlighted = comment.id == selectedCommentId,
                        showInlineActionMenu = actionCommentId == comment.id &&
                            selectedForCopyCommentId != comment.id &&
                            editingCommentId != comment.id,
                        onCopyFull = {
                            copyComment(comment.content)
                            actionCommentId = null
                        },
                        onSelectText = {
                            selectedForCopyCommentId = comment.id
                            selectedCommentValue = fullCommentSelectionValue(comment.content)
                            editingCommentId = null
                            editingDraft = ""
                            actionCommentId = null
                        },
                        onEdit = {
                            editingCommentId = comment.id
                            editingDraft = comment.content
                            selectedForCopyCommentId = null
                            selectedCommentValue = TextFieldValue("")
                            actionCommentId = null
                        },
                        onDelete = {
                            CommentGateway.deleteMediaComment(mediaId, comment.id)
                            if (selectedForCopyCommentId == comment.id) {
                                selectedForCopyCommentId = null
                                selectedCommentValue = TextFieldValue("")
                            }
                            if (editingCommentId == comment.id) {
                                editingCommentId = null
                                editingDraft = ""
                            }
                            actionCommentId = null
                            Toast.makeText(context, "评论已删除", Toast.LENGTH_SHORT).show()
                        },
                        isEditing = editingCommentId == comment.id,
                        editingValue = if (editingCommentId == comment.id) editingDraft else comment.content,
                        onEditingValueChange = { editingDraft = it },
                        onSaveEdit = {
                            CommentGateway.updateMediaComment(
                                mediaId = mediaId,
                                commentId = comment.id,
                                content = editingDraft,
                            )
                            editingCommentId = null
                            editingDraft = ""
                            actionCommentId = null
                            Toast.makeText(context, "评论已更新", Toast.LENGTH_SHORT).show()
                        },
                        onCancelEdit = {
                            editingCommentId = null
                            editingDraft = ""
                        },
                        selectionMode = selectedForCopyCommentId == comment.id,
                        selectionFieldValue = if (selectedForCopyCommentId == comment.id) {
                            selectedCommentValue
                        } else {
                            TextFieldValue(comment.content)
                        },
                        onSelectionFieldValueChange = { selectedCommentValue = it },
                        onCopySelection = if (selectedForCopyCommentId == comment.id) {
                            {
                                selectedCommentValue.selectedTextOrNull()?.let(copyComment)
                                selectedForCopyCommentId = null
                                selectedCommentValue = TextFieldValue("")
                            }
                        } else {
                            null
                        },
                    )
                }
            }
            if (comments.hasHiddenComments(expanded)) {
                TextButton(onClick = { expanded = true }) {
                    Text(text = "展开更多评论", color = ViewerSurface.copy(alpha = 0.88f))
                }
            }
            if (comments.canCollapseComments(expanded)) {
                TextButton(onClick = { expanded = false }) {
                    Text(text = "收起到最新 10 条", color = ViewerSurface.copy(alpha = 0.72f))
                }
            }
            CommentInputBar(
                stateKey = "media-comment-input-$mediaId",
                placeholder = "写一条媒体评论",
                darkMode = true,
                onSend = { content ->
                    CommentGateway.addMediaComment(mediaId, content)
                    expanded = false
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerRelatedPostsSheet(
    posts: List<ViewerRelatedPostUiModel>,
    onSelectPost: (ViewerRelatedPostUiModel) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ViewerNightTop,
        contentColor = ViewerSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(ViewerLayoutTuning.relatedPostsSheetHeightFraction)
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = "选择所属帖子",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ViewerSurface.copy(alpha = 0.94f),
            )
            Text(
                text = "当前为占位入口，正式跳转将在帖子详情阶段接入。",
                style = MaterialTheme.typography.labelMedium,
                color = ViewerSurface.copy(alpha = 0.58f),
            )
            posts.forEach { post ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(radius.lg))
                        .background(ViewerSurface.copy(alpha = 0.08f))
                        .clickable { onSelectPost(post) }
                        .padding(horizontal = spacing.md, vertical = spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Text(
                        text = post.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = ViewerSurface.copy(alpha = 0.88f),
                    )
                    Text(
                        text = post.subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = ViewerSurface.copy(alpha = 0.58f),
                    )
                }
            }
        }
    }
}

private fun fakeViewerPreviewComments(media: PhotoFeedItem): List<CommentUiModel> {
    if (media.commentCount <= 0) return emptyList()

    val bodies = listOf(
        "这张的光很温柔，像那天刚好慢下来了一点。",
        "我记得这里，当时风特别轻。",
        "这个角度好像比现场更安静。",
        "先留一条占位评论，后面接真实媒体评论。",
    )
    return List(media.commentCount.coerceAtMost(ViewerLayoutTuning.previewCommentsMaxCount)) { index ->
        CommentUiModel(
            id = "${media.mediaId}-preview-$index",
            targetType = CommentTargetType.Media,
            targetId = media.mediaId,
            author = if (index % 2 == 0) "我" else "你",
            content = bodies[index % bodies.size],
            createdAtMillis = media.mediaDisplayTimeMillis - (index * 7 * 60 * 1000L),
        )
    }
}

private fun fakeViewerRelatedPosts(media: PhotoFeedItem): List<ViewerRelatedPostUiModel> {
    return media.postIds.mapIndexed { index, postId ->
        ViewerRelatedPostUiModel(
            id = postId,
            title = placeholderPostTitle(postId),
            subtitle = if (media.postIds.size == 1) {
                "单个所属帖子 · 占位跳转"
            } else {
                "可选所属帖子 ${index + 1} / ${media.postIds.size}"
            },
        )
    }
}

private fun placeholderPostTitle(postId: String): String {
    return when (postId) {
        "post-night-walk" -> "夜晚散步"
        "post-april-window" -> "四月窗边"
        "post-sunday-brunch" -> "周日早午餐"
        "post-flower-table" -> "花桌小记"
        "post-morning-metro" -> "早班地铁"
        "post-late-return" -> "晚归路上"
        "post-river-night" -> "河边夜色"
        "post-new-year" -> "新年第一刻"
        "post-fireworks" -> "烟花倒影"
        "post-firework-reflection" -> "烟火倒影"
        "post-window-light" -> "四月窗边"
        "post-hill-road" -> "上坡那段路"
        else -> postId
            .removePrefix("post-")
            .split("-")
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
    }
}

private fun formatViewerTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}

@Preview(showBackground = true)
@Composable
private fun PhotoViewerScreenPreview() {
    YingShiTheme(darkTheme = true) {
        PhotoViewerScreen(
            route = PhotoViewerRoute(
                mediaItems = FakePhotoFeedRepository.getPhotoFeed(),
                initialIndex = 0,
                sourceLabel = "照片页全局媒体流",
                showPostSegments = false,
            ),
            onBack = { },
        )
    }
}

