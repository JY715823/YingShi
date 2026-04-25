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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.delay
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
    ) {
        val nextScale = (scale * zoomChange).coerceIn(MinViewerScale, MaxViewerScale)
        if (nextScale <= ViewerZoomResetThreshold) {
            reset()
            return
        }

        scale = nextScale
        offset = clampOffset(offset + panChange, nextScale, containerSize)
    }

    fun panBy(
        panChange: Offset,
        containerSize: IntSize,
    ) {
        if (!isZoomed) return
        offset = clampOffset(offset + panChange, scale, containerSize)
    }

    fun reset() {
        scale = MinViewerScale
        offset = Offset.Zero
    }

    private fun clampOffset(
        value: Offset,
        currentScale: Float,
        containerSize: IntSize,
    ): Offset {
        val maxX = containerSize.width * (currentScale - MinViewerScale) / 2f
        val maxY = containerSize.height * (currentScale - MinViewerScale) / 2f
        return Offset(
            x = value.x.coerceIn(-maxX, maxX),
            y = value.y.coerceIn(-maxY, maxY),
        )
    }
}

private fun Modifier.viewerZoomGesture(zoomState: ViewerZoomState): Modifier = pointerInput(zoomState) {
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
                )
                activeChanges.forEach { it.consume() }
            } else if (zoomState.isZoomed) {
                zoomState.panBy(
                    panChange = activeChanges.first().positionChange(),
                    containerSize = size,
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
    val initialPage = route.initialIndex.coerceIn(0, route.mediaItems.lastIndex)
    val zoomState = remember { ViewerZoomState() }
    val originalLoadStates = remember(route.mediaItems) {
        mutableStateMapOf<String, ViewerOriginalLoadState>()
    }
    var showCommentPreview by remember { mutableStateOf(false) }
    var commentPanelState by remember { mutableStateOf<ViewerCommentPanelState?>(null) }
    var showRelatedPostsSheet by remember { mutableStateOf(false) }
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
    val currentOriginalState = originalLoadStates[currentItem.mediaId]
        ?: ViewerOriginalLoadState.NotLoaded
    val mediaComments = FakeCommentRepository.getMediaComments(currentItem.mediaId)
    val previewComments = mediaComments.take(ViewerLayoutTuning.previewCommentsMaxCount)
    val edgeActionsBottomPadding = if (route.showPostSegments) {
        ViewerLayoutTuning.inPostEdgeActionsBottomPadding
    } else {
        ViewerLayoutTuning.photoFlowEdgeActionsBottomPadding
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
    }
    LaunchedEffect(currentItem.mediaId, currentOriginalState) {
        if (currentOriginalState == ViewerOriginalLoadState.Loading) {
            delay(900)
            originalLoadStates[currentItem.mediaId] = ViewerOriginalLoadState.Loaded
        }
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
                modifier = Modifier.fillMaxSize(),
            )
        }

        ViewerTopScrim(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(148.dp),
        )

        if (!zoomState.isZoomed) {
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
                Toast.makeText(context, "设置入口占位", Toast.LENGTH_SHORT).show()
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
            overlayAlpha = if (zoomState.isZoomed) ViewerLayoutTuning.zoomedOverlayAlpha else 1f,
        )

        if (!zoomState.isZoomed) {
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
                            bottom = edgeActionsBottomPadding + 18.dp,
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
                },
                onOpenOriginal = {
                    when (currentOriginalState) {
                        ViewerOriginalLoadState.NotLoaded -> {
                            originalLoadStates[currentItem.mediaId] = ViewerOriginalLoadState.Loading
                            Toast.makeText(context, "开始加载原图占位", Toast.LENGTH_SHORT).show()
                        }

                        ViewerOriginalLoadState.Loading -> {
                            Toast.makeText(context, "原图占位加载中", Toast.LENGTH_SHORT).show()
                        }

                        ViewerOriginalLoadState.Loaded -> {
                            Toast.makeText(context, "已加载原图占位", Toast.LENGTH_SHORT).show()
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
                alpha = if (zoomState.isZoomed) ViewerLayoutTuning.zoomedOverlayAlpha else 0.92f,
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
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val canvasAspectRatio = media.aspectRatio.coerceIn(0.75f, 1.35f)
    val zoomModifier = if (zoomState != null) {
        Modifier
            .graphicsLayer {
                scaleX = zoomState.scale
                scaleY = zoomState.scale
                translationX = zoomState.offset.x
                translationY = zoomState.offset.y
            }
            .viewerZoomGesture(zoomState)
    } else {
        Modifier
    }

    BoxWithConstraints(
        modifier = modifier.padding(
            start = ViewerLayoutTuning.canvasHorizontalPadding,
            top = ViewerLayoutTuning.canvasTopPadding,
            end = ViewerLayoutTuning.canvasHorizontalPadding,
            bottom = ViewerLayoutTuning.canvasBottomPadding,
        ),
        contentAlignment = Alignment.Center,
    ) {
        val fillWidthHeight = maxWidth / canvasAspectRatio
        val canvasWidth = if (fillWidthHeight <= maxHeight) {
            maxWidth
        } else {
            maxHeight * canvasAspectRatio
        }
        val canvasHeight = if (fillWidthHeight <= maxHeight) {
            fillWidthHeight
        } else {
            maxHeight
        }

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
                emphasized = overlayUiModel.originalLoadState == ViewerOriginalLoadState.Loaded,
                enabled = overlayUiModel.originalLoadState != ViewerOriginalLoadState.Loading,
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
    val visibleComments = comments.visibleComments(expanded)
    val actionComment = comments.firstOrNull { it.id == actionCommentId }

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
                        onLongPress = { actionCommentId = comment.id },
                        darkMode = true,
                        highlighted = comment.id == selectedCommentId,
                        selectedForCopy = selectedForCopyCommentId == comment.id,
                        isEditing = editingCommentId == comment.id,
                        editingValue = if (editingCommentId == comment.id) editingDraft else comment.content,
                        onEditingValueChange = { editingDraft = it },
                        onSaveEdit = {
                            FakeCommentRepository.updateMediaComment(
                                mediaId = mediaId,
                                commentId = comment.id,
                                content = editingDraft,
                            )
                            editingCommentId = null
                            editingDraft = ""
                            Toast.makeText(context, "评论已更新", Toast.LENGTH_SHORT).show()
                        },
                        onCancelEdit = {
                            editingCommentId = null
                            editingDraft = ""
                        },
                        onCopySelection = if (selectedForCopyCommentId == comment.id) {
                            {
                                copyComment(comment.content)
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
                    FakeCommentRepository.addMediaComment(mediaId, content)
                    expanded = false
                },
            )
        }
    }

    actionComment?.let { comment ->
        CommentActionMenuSheet(
            comment = comment,
            darkMode = true,
            onDismiss = { actionCommentId = null },
            onEdit = {
                editingCommentId = comment.id
                editingDraft = comment.content
                selectedForCopyCommentId = null
                actionCommentId = null
            },
            onDelete = {
                FakeCommentRepository.deleteMediaComment(mediaId, comment.id)
                if (selectedForCopyCommentId == comment.id) {
                    selectedForCopyCommentId = null
                }
                if (editingCommentId == comment.id) {
                    editingCommentId = null
                    editingDraft = ""
                }
                actionCommentId = null
                Toast.makeText(context, "评论已删除", Toast.LENGTH_SHORT).show()
            },
            onSelect = {
                selectedForCopyCommentId = comment.id
                editingCommentId = null
                editingDraft = ""
                actionCommentId = null
                Toast.makeText(context, "已选中当前评论全文", Toast.LENGTH_SHORT).show()
            },
        )
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
