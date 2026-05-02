package com.example.yingshi.feature.photos

import android.app.Activity
import android.content.ContextWrapper
import android.net.Uri
import android.widget.VideoView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import coil.imageLoader
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.RemoteConfig
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val ViewerNightTop = Color(0xFF050608)
private val ViewerNightBottom = Color(0xFF050608)
private val ViewerNightMiddle = Color(0xFF050608)
private val ViewerSurface = Color(0xFFFFFFFF)
private const val MinViewerScale = 1f
private const val MaxViewerScale = 4f
private const val ViewerZoomResetThreshold = 1.02f
private const val DefaultViewerVideoDurationMillis = 18_000L

private object ViewerLayoutTuning {
    val topBarStartInset = 4.dp
    val topBarEndInset = 10.dp
    val topBarTopInset = 6.dp
    val backButtonTouchSize = 42.dp
    val canvasHorizontalPadding = 0.dp
    val canvasTopPadding = 68.dp
    val canvasBottomPadding = 104.dp
    const val commentPreviewWidthFraction = 0.70f
    val commentPreviewMaxWidth = 288.dp
    val commentPreviewHeight = 172.dp
    val photoFlowEdgeActionsBottomPadding = 0.dp
    val inPostEdgeActionsBottomPadding = 2.dp
    val postSegmentBottomOffset = 2.dp
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
    val durationMillis: Long? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

private enum class ViewerImageFailureReason {
    NONE,
    MISSING_URL,
    PREVIEW_FAILED,
    ORIGINAL_FAILED,
}

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
    val coroutineScope = rememberCoroutineScope()
    val sessionVersion = AuthSessionManager.sessionVersion
    val viewerAccessToken = remember(sessionVersion) {
        AuthSessionManager.getAccessToken()?.takeIf { it.isNotBlank() }
    }
    val settingsState = FakeSettingsRepository.getSettingsState()
    var viewerItems by remember(route) {
        mutableStateOf(route.mediaItems)
    }
    val initialPage = route.initialIndex.coerceIn(0, viewerItems.lastIndex)
    val zoomState = remember { ViewerZoomState() }
    var showCommentPreview by remember { mutableStateOf(false) }
    var commentPanelState by remember { mutableStateOf<ViewerCommentPanelState?>(null) }
    var showRelatedPostsSheet by remember { mutableStateOf(false) }
    var openCommentComposerOnSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var videoPlaybackState by remember {
        mutableStateOf(ViewerVideoPlaybackState())
    }
    val realOriginalStates = remember(route) {
        mutableStateMapOf<String, OriginalLoadState>()
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { viewerItems.size },
    )
    val currentIndex by remember(viewerItems, pagerState) {
        derivedStateOf {
            pagerState.currentPage.coerceIn(0, viewerItems.lastIndex)
        }
    }
    val currentItem = viewerItems[currentIndex]
    val currentOriginalState = if (
        RepositoryProvider.currentMode == RepositoryMode.REAL &&
        currentItem.mediaType == AppMediaType.IMAGE
    ) {
        realOriginalStates[currentItem.mediaId] ?: OriginalLoadState.NotLoaded
    } else {
        FakeOriginalLoadRepository.getState(currentItem.mediaId)
    }
    val currentCacheState = FakeMediaCacheRepository.getState(
        mediaId = currentItem.mediaId,
        mediaType = currentItem.mediaType,
    )
    val commentBindings = rememberViewerCommentBindings(currentItem.mediaId)
    val mediaComments = commentBindings.comments
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
        openCommentComposerOnSheet = false
        videoPlaybackState = ViewerVideoPlaybackState(
            mediaId = currentItem.mediaId.takeIf { currentItem.mediaType == AppMediaType.VIDEO },
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

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(text = "确认删除该媒体？") },
            text = { Text(text = "删除后会进入回收站，可在回收站中恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        val deletingItem = currentItem
                        if (route.showPostSegments) return@TextButton
                        when (RepositoryProvider.currentMode) {
                            RepositoryMode.FAKE -> {
                                deleteFakeViewerMedia(deletingItem)
                                val nextItems = viewerItems.filterNot { it.mediaId == deletingItem.mediaId }
                                if (nextItems.isEmpty()) {
                                    onBack()
                                } else {
                                    viewerItems = nextItems
                                    coroutineScope.launch {
                                        pagerState.scrollToPage(currentIndex.coerceAtMost(nextItems.lastIndex))
                                    }
                                }
                                Toast.makeText(context, "已删除当前媒体，并写入回收站。", Toast.LENGTH_SHORT).show()
                            }
                            RepositoryMode.REAL -> {
                                coroutineScope.launch {
                                    val message = deleteRealViewerMedia(deletingItem.mediaId)
                                    if (message != null) {
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                    val nextItems = viewerItems.filterNot { it.mediaId == deletingItem.mediaId }
                                    if (nextItems.isEmpty()) {
                                        onBack()
                                    } else {
                                        viewerItems = nextItems
                                        pagerState.scrollToPage(currentIndex.coerceAtMost(nextItems.lastIndex))
                                    }
                                }
                            }
                        }
                    },
                ) {
                    Text(text = "删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(text = "取消")
                }
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ViewerNightBottom),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = viewerItems.size > 1 && !zoomState.isZoomed,
            key = { page -> viewerItems[page].mediaId },
        ) { page ->
            PhotoViewerCanvas(
                media = viewerItems[page],
                zoomState = if (page == currentIndex) zoomState else null,
                videoPlaybackState = if (page == currentIndex) videoPlaybackState else null,
                originalLoadState = if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
                    realOriginalStates[viewerItems[page].mediaId] ?: OriginalLoadState.NotLoaded
                } else {
                    FakeOriginalLoadRepository.getState(viewerItems[page].mediaId)
                },
                overlaysVisible = overlaysVisible,
                onTogglePlayback = {
                    val durationMillis = videoPlaybackState.durationMillis
                        ?: currentItem.viewerVideoDurationMillis()
                    val shouldRestart = videoPlaybackState.progressMillis >= durationMillis
                    videoPlaybackState = if (videoPlaybackState.isPlaying) {
                        videoPlaybackState.copy(isPlaying = false)
                    } else {
                        videoPlaybackState.copy(
                            mediaId = currentItem.mediaId,
                            isPlaying = true,
                            progressMillis = if (shouldRestart) 0L else videoPlaybackState.progressMillis,
                            errorMessage = null,
                        )
                    }
                },
                onVideoPlaybackStateChange = { mediaId, state ->
                    if (mediaId == currentItem.mediaId) {
                        videoPlaybackState = state
                    }
                },
                onOriginalLoadStateChange = { mediaId, state ->
                    if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
                        realOriginalStates[mediaId] = state
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        ViewerTopScrim(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(124.dp),
        )

        if (overlaysVisible) {
            ViewerBottomScrim(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(188.dp),
            )
        }

        PhotoViewerTopBar(
            onBack = onBack,
            timeLabel = overlayUiModel.timeLabel,
            showDeleteAction = !route.showPostSegments,
            onDelete = { showDeleteConfirm = true },
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
                        openCommentComposerOnSheet = false
                        commentPanelState = ViewerCommentPanelState(selectedCommentId = commentId)
                    },
                    onAddComment = {
                        openCommentComposerOnSheet = true
                        commentPanelState = ViewerCommentPanelState()
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
                    openCommentComposerOnSheet = false
                },
                onOpenOriginal = {
                    if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
                        val originalUrl = currentItem.mediaSource.viewerOriginalImageUrl(currentItem.mediaType)
                        when {
                            currentItem.mediaType == AppMediaType.VIDEO -> {
                                Toast.makeText(context, "视频本轮暂不支持原图加载", Toast.LENGTH_SHORT).show()
                            }

                            originalUrl == null -> {
                                realOriginalStates[currentItem.mediaId] = OriginalLoadState.Failed
                                Toast.makeText(context, "暂无可用原图地址", Toast.LENGTH_SHORT).show()
                            }

                            currentOriginalState == OriginalLoadState.Loading -> {
                                Toast.makeText(context, "原图加载中...", Toast.LENGTH_SHORT).show()
                            }

                            currentOriginalState == OriginalLoadState.Loaded -> {
                                Toast.makeText(context, "已加载原图", Toast.LENGTH_SHORT).show()
                            }

                            else -> {
                                realOriginalStates[currentItem.mediaId] = OriginalLoadState.Loading
                                Toast.makeText(context, "开始加载原图", Toast.LENGTH_SHORT).show()
                                coroutineScope.launch {
                                    val loadSucceeded = loadRealViewerOriginal(
                                        context = context,
                                        media = currentItem,
                                        accessToken = viewerAccessToken,
                                    )
                                    realOriginalStates[currentItem.mediaId] = if (loadSucceeded) {
                                        FakeMediaCacheRepository.markOriginalCached(currentItem.mediaId)
                                        Toast.makeText(context, "原图加载完成", Toast.LENGTH_SHORT).show()
                                        OriginalLoadState.Loaded
                                    } else {
                                        Toast.makeText(context, "原图加载失败，已保留预览图", Toast.LENGTH_SHORT).show()
                                        OriginalLoadState.Failed
                                    }
                                }
                            }
                        }
                    } else {
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
                autoFocusInput = openCommentComposerOnSheet,
                onDismiss = { commentPanelState = null },
                isLoading = commentBindings.isLoading,
                isMutating = commentBindings.isMutating,
                errorMessage = commentBindings.errorMessage,
                statusMessage = commentBindings.statusMessage,
                onRetry = commentBindings.onRetry,
                onCreateComment = commentBindings.onCreateComment,
                onUpdateComment = commentBindings.onUpdateComment,
                onDeleteComment = commentBindings.onDeleteComment,
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
            .background(ViewerNightBottom),
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
    showDeleteAction: Boolean,
    onDelete: () -> Unit,
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

        if (showDeleteAction) {
            ViewerCapsule(
                text = "🗑",
                emphasized = false,
                modifier = Modifier.align(Alignment.TopEnd),
                surfaceAlpha = 0.08f,
                contentAlpha = 0.80f,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun PhotoViewerCanvas(
    media: PhotoFeedItem,
    zoomState: ViewerZoomState?,
    videoPlaybackState: ViewerVideoPlaybackState?,
    originalLoadState: OriginalLoadState,
    overlaysVisible: Boolean,
    onTogglePlayback: () -> Unit,
    onVideoPlaybackStateChange: (String, ViewerVideoPlaybackState) -> Unit,
    onOriginalLoadStateChange: (String, OriginalLoadState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val density = LocalDensity.current
    val isVideo = media.mediaType == AppMediaType.VIDEO
    val canvasAspectRatio = media.viewerAspectRatio().coerceIn(0.05f, 20f)

    BoxWithConstraints(
        modifier = modifier.padding(
            start = ViewerLayoutTuning.canvasHorizontalPadding,
            top = ViewerLayoutTuning.canvasTopPadding,
            end = ViewerLayoutTuning.canvasHorizontalPadding,
            bottom = ViewerLayoutTuning.canvasBottomPadding,
        ),
        contentAlignment = Alignment.Center,
    ) {
        val canvasWidth = if (maxHeight * canvasAspectRatio <= maxWidth) {
            maxHeight * canvasAspectRatio
        } else {
            maxWidth
        }
        val canvasHeight = if (maxWidth / canvasAspectRatio <= maxHeight) {
            maxWidth / canvasAspectRatio
        } else {
            maxHeight
        }
        val contentSize = with(density) {
            IntSize(canvasWidth.roundToPx(), canvasHeight.roundToPx())
        }
        val zoomTransformModifier = if (zoomState != null) {
            Modifier
                .graphicsLayer {
                    scaleX = zoomState.scale
                    scaleY = zoomState.scale
                    translationX = zoomState.offset.x
                    translationY = zoomState.offset.y
                }
        } else {
            Modifier
        }
        val gestureModifier = if (zoomState != null) {
            Modifier.viewerZoomGesture(
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
            if (isVideo) {
                Box(
                    modifier = Modifier
                        .width(canvasWidth)
                        .height(canvasHeight),
                ) {
                    ViewerVideoCanvas(
                        media = media,
                        playbackState = videoPlaybackState,
                        isCurrent = zoomState != null,
                        onPlaybackStateChange = onVideoPlaybackStateChange,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(zoomTransformModifier),
                    )
                    if (overlaysVisible && videoPlaybackState != null) {
                        val durationMillis = videoPlaybackState.durationMillis
                            ?: media.viewerVideoDurationMillis()
                        ViewerVideoControls(
                            playbackState = videoPlaybackState,
                            durationMillis = durationMillis,
                            onTogglePlayback = onTogglePlayback,
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
                        .then(zoomTransformModifier)
                        .background(ViewerNightBottom),
                ) {
                    if (media.mediaSource != null) {
                        ViewerImageCanvas(
                            media = media,
                            originalLoadState = originalLoadState,
                            onOriginalLoadStateChange = onOriginalLoadStateChange,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Surface(
                            modifier = Modifier.align(Alignment.Center),
                            shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                            color = Color.Black.copy(alpha = 0.32f),
                        ) {
                            Text(
                                text = "暂无可用媒体预览",
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
}

@Composable
private fun ViewerImageCanvas(
    media: PhotoFeedItem,
    originalLoadState: OriginalLoadState,
    onOriginalLoadStateChange: (String, OriginalLoadState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val mediaSource = media.mediaSource
    val previewUrl = remember(mediaSource, media.mediaType) {
        mediaSource.viewerPreviewImageUrl(media.mediaType)
    }
    val originalUrl = remember(mediaSource, media.mediaType) {
        mediaSource.viewerOriginalImageUrl(media.mediaType)
    }
    val shouldRequestOriginal = originalLoadState == OriginalLoadState.Loaded
    val sessionVersion = AuthSessionManager.sessionVersion
    val accessToken = remember(sessionVersion) {
        AuthSessionManager.getAccessToken()?.takeIf { it.isNotBlank() }
    }
    val previewRequest = remember(context, previewUrl, accessToken) {
        viewerImageRequest(
            context = context,
            url = previewUrl,
            accessToken = accessToken,
            memoryCacheKey = previewUrl?.let(::sharedPreviewMemoryCacheKey),
        )
    }
    val originalRequest = remember(context, originalUrl, shouldRequestOriginal, accessToken) {
        if (shouldRequestOriginal) {
            viewerImageRequest(
                context = context,
                url = originalUrl,
                accessToken = accessToken,
                memoryCacheKey = originalUrl?.let(::sharedOriginalMemoryCacheKey),
            )
        } else {
            null
        }
    }
    val previewPainter = rememberAsyncImagePainter(model = previewRequest)
    val originalPainter = rememberAsyncImagePainter(model = originalRequest)
    val previewState = previewPainter.state
    val originalState = originalPainter.state
    val showOriginal = originalLoadState == OriginalLoadState.Loaded &&
        originalState is AsyncImagePainter.State.Success
    val showPreview = previewRequest != null &&
        previewState !is AsyncImagePainter.State.Error &&
        !showOriginal
    val failureReason = when {
        previewUrl == null && originalUrl == null -> ViewerImageFailureReason.MISSING_URL
        showOriginal || showPreview -> ViewerImageFailureReason.NONE
        originalLoadState == OriginalLoadState.Failed -> ViewerImageFailureReason.ORIGINAL_FAILED
        previewRequest != null && previewState is AsyncImagePainter.State.Error -> ViewerImageFailureReason.PREVIEW_FAILED
        else -> ViewerImageFailureReason.NONE
    }

    LaunchedEffect(media.mediaId, originalLoadState, originalState) {
        if (originalLoadState == OriginalLoadState.Loaded &&
            originalState is AsyncImagePainter.State.Error
        ) {
            onOriginalLoadStateChange(media.mediaId, OriginalLoadState.Failed)
        }
    }

    Box(
        modifier = modifier.background(ViewerNightBottom),
        contentAlignment = Alignment.Center,
    ) {
        if (showPreview) {
            Image(
                painter = previewPainter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        if (showOriginal) {
            Image(
                painter = originalPainter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        if (previewRequest != null && previewState is AsyncImagePainter.State.Loading && !showOriginal) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = ViewerSurface.copy(alpha = 0.90f),
                strokeWidth = 2.dp,
            )
        }

        if (originalLoadState == OriginalLoadState.Loading) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = spacing.md),
                shape = RoundedCornerShape(radius.capsule),
                color = Color.Black.copy(alpha = 0.34f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = ViewerSurface.copy(alpha = 0.88f),
                        strokeWidth = 1.5.dp,
                    )
                    Text(
                        text = "原图加载中",
                        style = MaterialTheme.typography.labelMedium,
                        color = ViewerSurface.copy(alpha = 0.88f),
                    )
                }
            }
        }

        if (failureReason != ViewerImageFailureReason.NONE) {
            ViewerImageFallback(
                reason = failureReason,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

private suspend fun loadRealViewerOriginal(
    context: android.content.Context,
    media: PhotoFeedItem,
    accessToken: String?,
): Boolean {
    val originalUrl = media.mediaSource.viewerOriginalImageUrl(media.mediaType) ?: return false
    val request = viewerImageRequest(
        context = context,
        url = originalUrl,
        accessToken = accessToken,
        memoryCacheKey = sharedOriginalMemoryCacheKey(originalUrl),
    ) ?: return false
    return context.imageLoader.execute(request) is SuccessResult
}

private fun viewerImageRequest(
    context: android.content.Context,
    url: String?,
    accessToken: String?,
    memoryCacheKey: String? = url?.let(::sharedPreviewMemoryCacheKey),
): ImageRequest? {
    if (url.isNullOrBlank()) return null
    return ImageRequest.Builder(context).apply {
        data(url)
        memoryCacheKey?.let(::memoryCacheKey)
        diskCacheKey(sharedMediaDiskCacheKey(url))
        networkCachePolicy(CachePolicy.ENABLED)
        diskCachePolicy(CachePolicy.ENABLED)
        memoryCachePolicy(CachePolicy.ENABLED)
        precision(Precision.INEXACT)
        crossfade(false)
        accessToken
            ?.takeIf { url.startsWith("http", ignoreCase = true) }
            ?.let { token -> addHeader("Authorization", "${RemoteConfig.AUTH_SCHEME} $token") }
    }.build()
}

@Composable
private fun ViewerImageFallback(
    reason: ViewerImageFailureReason,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val label = when (reason) {
        ViewerImageFailureReason.MISSING_URL -> "暂无可用图片"
        ViewerImageFailureReason.PREVIEW_FAILED -> "图片加载失败"
        ViewerImageFailureReason.ORIGINAL_FAILED -> "原图加载失败，已保留预览"
        ViewerImageFailureReason.NONE -> null
    } ?: return

    Surface(
        modifier = modifier.padding(spacing.lg),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = Color.Black.copy(alpha = 0.32f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = ViewerSurface.copy(alpha = 0.88f),
        )
    }
}

@Composable
private fun ViewerVideoCanvas(
    media: PhotoFeedItem,
    playbackState: ViewerVideoPlaybackState?,
    isCurrent: Boolean,
    onPlaybackStateChange: (String, ViewerVideoPlaybackState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val videoUrl = remember(media.mediaSource, media.mediaType) {
        media.mediaSource.viewerVideoUrl(media.mediaType)
    }
    val isPlaying = playbackState?.isPlaying == true
    val isLoading = playbackState?.isLoading == true
    val errorMessage = playbackState?.errorMessage
    val sessionVersion = AuthSessionManager.sessionVersion
    val accessToken = remember(sessionVersion) {
        AuthSessionManager.getAccessToken()?.takeIf { it.isNotBlank() }
    }
    val videoPosterState = rememberVideoPosterState(
        url = videoUrl,
        accessToken = accessToken,
    ).value
    val posterPainter = rememberAsyncImagePainter(model = videoPosterState.model)
    val requestHeaders = remember(videoUrl, accessToken) {
        if (!videoUrl.isNullOrBlank() &&
            videoUrl.startsWith("http", ignoreCase = true) &&
            !accessToken.isNullOrBlank()
        ) {
            mapOf("Authorization" to "${RemoteConfig.AUTH_SCHEME} $accessToken")
        } else {
            emptyMap()
        }
    }
    val videoViewRef = remember(media.mediaId) { mutableStateOf<VideoView?>(null) }
    var retryVersion by remember(media.mediaId) { mutableStateOf(0) }
    var isPrepared by remember(media.mediaId, retryVersion) { mutableStateOf(false) }

    fun updatePlaybackState(transform: (ViewerVideoPlaybackState) -> ViewerVideoPlaybackState) {
        val current = playbackState ?: ViewerVideoPlaybackState(mediaId = media.mediaId)
        onPlaybackStateChange(
            media.mediaId,
            transform(current.copy(mediaId = media.mediaId)),
        )
    }

    LaunchedEffect(media.mediaId, videoUrl) {
        if (videoUrl.isNullOrBlank()) {
            onPlaybackStateChange(
                media.mediaId,
                ViewerVideoPlaybackState(
                    mediaId = media.mediaId,
                    errorMessage = "视频 URL 为空",
                ),
            )
        } else {
            onPlaybackStateChange(
                media.mediaId,
                ViewerVideoPlaybackState(
                    mediaId = media.mediaId,
                    isLoading = true,
                    durationMillis = media.viewerVideoDurationMillis(),
                ),
            )
        }
    }

    DisposableEffect(media.mediaId, retryVersion) {
        onDispose {
            videoViewRef.value?.pause()
            videoViewRef.value?.stopPlayback()
            videoViewRef.value = null
        }
    }

    DisposableEffect(isCurrent) {
        if (!isCurrent) {
            videoViewRef.value?.pause()
            updatePlaybackState { it.copy(isPlaying = false) }
        }
        onDispose { }
    }

    LaunchedEffect(isCurrent, isPlaying, errorMessage, videoUrl, retryVersion) {
        while (isCurrent && videoUrl != null && errorMessage == null) {
            val videoView = videoViewRef.value
            if (videoView != null && isPrepared) {
                updatePlaybackState {
                    it.copy(
                        progressMillis = videoView.currentPosition.toLong().coerceAtLeast(0L),
                        durationMillis = videoView.duration.toLong().takeIf { duration -> duration > 0 }
                            ?: it.durationMillis,
                    )
                }
            }
            kotlinx.coroutines.delay(300)
        }
    }
    val shouldShowPoster = videoPosterState.model != null &&
        (!isPrepared || (playbackState?.progressMillis ?: 0L) <= 0L || errorMessage != null)

    Box(
        modifier = modifier
            .background(ViewerNightBottom),
    ) {
        if (!videoUrl.isNullOrBlank()) {
            key(retryVersion) {
                AndroidView(
                    factory = { viewContext ->
                        VideoView(viewContext).apply {
                            setVideoURI(Uri.parse(videoUrl), requestHeaders)
                            setOnPreparedListener { player ->
                                isPrepared = true
                                player.isLooping = false
                                val resumePositionMillis = playbackState?.progressMillis?.toInt()?.coerceAtLeast(0) ?: 0
                                if (resumePositionMillis > 0) {
                                    seekTo(resumePositionMillis)
                                }
                                updatePlaybackState {
                                    it.copy(
                                        isLoading = false,
                                        errorMessage = null,
                                        durationMillis = duration.toLong().takeIf { value -> value > 0 }
                                            ?: it.durationMillis,
                                    )
                                }
                                if (isCurrent && playbackState?.isPlaying == true) {
                                    start()
                                }
                            }
                            setOnErrorListener { _, _, _ ->
                                isPrepared = false
                                updatePlaybackState {
                                    it.copy(
                                        isPlaying = false,
                                        isLoading = false,
                                        errorMessage = "视频加载失败，请重试",
                                    )
                                }
                                true
                            }
                            setOnCompletionListener {
                                updatePlaybackState {
                                    it.copy(
                                        isPlaying = false,
                                        progressMillis = duration.toLong().takeIf { value -> value > 0 }
                                            ?: it.progressMillis,
                                    )
                                }
                            }
                            videoViewRef.value = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { videoView ->
                        videoViewRef.value = videoView
                        if (!isCurrent || errorMessage != null) {
                            if (videoView.isPlaying) videoView.pause()
                            return@AndroidView
                        }
                        if (isPlaying && isPrepared && !videoView.isPlaying) {
                            videoView.start()
                        } else if (!isPlaying && videoView.isPlaying) {
                            videoView.pause()
                        }
                    },
                )
            }
        }

        if (shouldShowPoster) {
            Image(
                painter = posterPainter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

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
            text = when {
                videoUrl.isNullOrBlank() -> "暂无可播放的视频地址"
                isLoading -> "视频加载中…"
                errorMessage != null -> errorMessage
                isPlaying -> "正在播放视频"
                else -> "视频已暂停"
            },
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

        if (videoPosterState.isLoading || isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 174.dp)
                    .size(24.dp),
                color = ViewerSurface.copy(alpha = 0.88f),
                strokeWidth = 2.dp,
            )
        }

        if (errorMessage != null && !videoUrl.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 174.dp),
                shape = RoundedCornerShape(radius.capsule),
                color = Color.Black.copy(alpha = 0.32f),
                border = BorderStroke(1.dp, ViewerSurface.copy(alpha = 0.10f)),
                onClick = {
                    retryVersion += 1
                    isPrepared = false
                    updatePlaybackState {
                        it.copy(
                            isPlaying = false,
                            progressMillis = 0L,
                            isLoading = true,
                            errorMessage = null,
                        )
                    }
                },
            ) {
                Text(
                    text = "重试",
                    modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = ViewerSurface.copy(alpha = 0.90f),
                )
            }
        }
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
                        text = when {
                            playbackState.errorMessage != null -> "播放失败"
                            playbackState.isLoading -> "加载中"
                            playbackState.isPlaying -> "播放中"
                            else -> "已暂停"
                        },
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
    onAddComment: () -> Unit,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "媒体评论",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = ViewerSurface.copy(alpha = 0.88f),
            )
            ViewerCapsule(
                text = "添加评论",
                emphasized = false,
                surfaceAlpha = 0.12f,
                contentAlpha = 0.88f,
                onClick = onAddComment,
            )
        }
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
    autoFocusInput: Boolean,
    onDismiss: () -> Unit,
    isLoading: Boolean = false,
    isMutating: Boolean = false,
    errorMessage: String? = null,
    statusMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    onCreateComment: (String) -> Unit,
    onUpdateComment: (String, String) -> Unit,
    onDeleteComment: (String) -> Unit,
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
            if (statusMessage != null) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.labelMedium,
                    color = ViewerSurface.copy(alpha = 0.88f),
                )
            }
            if (selectedCommentId != null) {
                Text(
                    text = "已定位到预览评论，占位高亮如下",
                    style = MaterialTheme.typography.labelMedium,
                    color = ViewerSurface.copy(alpha = 0.58f),
                )
            }
            if (errorMessage != null) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ViewerSurface.copy(alpha = 0.82f),
                    )
                    if (onRetry != null) {
                        TextButton(onClick = onRetry) {
                            Text(text = "重试", color = ViewerSurface.copy(alpha = 0.88f))
                        }
                    }
                }
            }
            if (isLoading) {
                Text(
                    text = "正在读取媒体评论…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ViewerSurface.copy(alpha = 0.68f),
                )
            } else if (visibleComments.isEmpty()) {
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
                            onDeleteComment(comment.id)
                            if (selectedForCopyCommentId == comment.id) {
                                selectedForCopyCommentId = null
                                selectedCommentValue = TextFieldValue("")
                            }
                            if (editingCommentId == comment.id) {
                                editingCommentId = null
                                editingDraft = ""
                            }
                            actionCommentId = null
                            Toast.makeText(context, "评论操作已提交", Toast.LENGTH_SHORT).show()
                        },
                        isEditing = editingCommentId == comment.id,
                        editingValue = if (editingCommentId == comment.id) editingDraft else comment.content,
                        onEditingValueChange = { editingDraft = it },
                        onSaveEdit = {
                            onUpdateComment(comment.id, editingDraft)
                            editingCommentId = null
                            editingDraft = ""
                            actionCommentId = null
                            Toast.makeText(context, "评论操作已提交", Toast.LENGTH_SHORT).show()
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
            if (isMutating) {
                Text(
                    text = "正在提交评论操作…",
                    style = MaterialTheme.typography.labelMedium,
                    color = ViewerSurface.copy(alpha = 0.72f),
                )
            }
            CommentInputBar(
                stateKey = "media-comment-input-$mediaId",
                placeholder = "写一条媒体评论",
                darkMode = true,
                requestFocusOnShow = autoFocusInput,
                onSend = { content ->
                    onCreateComment(content)
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
        "post_001" -> "春日散步"
        "post_002" -> "灯下小物"
        "post_003" -> "车窗一瞬"
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

private fun deleteFakeViewerMedia(item: PhotoFeedItem) {
    val selectedIds = setOf(item.mediaId)
    val outcome = FakeAlbumRepository.previewGlobalMediaDelete(selectedIds)
    val deletedPostSnapshots = outcome.deletedPostIds.mapNotNull(FakeAlbumRepository::snapshotPost)
    val relationSnapshotsByMediaId = FakeAlbumRepository.snapshotMediaRelations(selectedIds)

    FakeTrashRepository.recordSystemDeletedMedia(
        mediaSnapshots = listOf(
            TrashMediaSnapshot(
                mediaId = item.mediaId,
                displayTimeMillis = item.mediaDisplayTimeMillis,
                palette = item.palette,
                mediaType = item.mediaType,
                aspectRatio = item.aspectRatio,
                width = item.width,
                height = item.height,
                videoDurationMillis = item.videoDurationMillis,
                sourcePostId = item.postIds.firstOrNull(),
                sourcePostTitle = item.postIds.firstOrNull()?.let(FakeAlbumRepository::getPost)?.title,
            ),
        ),
        relationSnapshotsByMediaId = relationSnapshotsByMediaId,
    )
    deletedPostSnapshots.forEach(FakeTrashRepository::recordDeletedPost)
    val appliedOutcome = FakeAlbumRepository.applyGlobalMediaDelete(selectedIds)
    FakeAlbumRepository.deletePostsLocally(appliedOutcome.deletedPostIds)
}

private suspend fun deleteRealViewerMedia(mediaId: String): String? {
    if (!AuthSessionManager.isLoggedIn) {
        return "请先到后端联调诊断页登录，再删除真实媒体。"
    }
    return when (val result = RepositoryProvider.mediaRepository.systemDeleteMedia(mediaId)) {
        is ApiResult.Success -> {
            RealBackendMutationBus.notifyChanged()
            null
        }
        is ApiResult.Error -> result.toBackendUiMessage("删除真实媒体失败。")
        ApiResult.Loading -> null
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

