package com.example.yingshi.feature.photos

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowInsets
import android.view.WindowInsetsAnimationControlListener
import android.view.WindowInsetsAnimationController
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.imageLoader
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.components.rememberYingShiMotionEnabled
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.components.yingShiMediaEnterMotion
import com.example.yingshi.ui.components.yingShiSoftReveal
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val ViewerNightTop = Color(0xFF1D333C)
private val ViewerNightBottom = Color(0xFF101F26)
private val ViewerNightMiddle = Color(0xFF182B33)
private val ViewerSurface = Color(0xFFF4FBFC)
private val ViewerAccent = Color(0xFFBDEFFF)
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
    val immersiveCanvasTopPadding = 0.dp
    val immersiveCanvasBottomPadding = 0.dp
    val immersiveVideoVerticalTapZone = 76.dp
    val immersiveVideoBottomExitZone = 40.dp
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

private data class ViewerNotice(
    val mediaId: String,
    val message: String,
    val emphasized: Boolean = false,
    val nonce: Int,
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

internal fun Modifier.viewerSingleTapGesture(
    enabled: Boolean = true,
    onTap: (Offset, IntSize) -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(onTap) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val start = down.position
            var pointerCountExceeded = false
            var moved = false
            var consumed = down.isConsumed
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size > 1) pointerCountExceeded = true
                event.changes.forEach { change ->
                    if (change.isConsumed) consumed = true
                    if ((change.position - start).getDistance() > viewConfiguration.touchSlop) {
                        moved = true
                    }
                }
                if (pressed.isEmpty()) {
                    val up = event.changes.firstOrNull { it.id == down.id }
                    if (!pointerCountExceeded && !moved && !consumed && up != null) {
                        onTap(up.position, size)
                    }
                    break
                }
            }
        }
    }
}

internal fun isViewerVideoImmersiveToggleTap(position: Offset, size: IntSize): Boolean {
    if (size.height <= 0) return false
    val topZone = size.height * 0.18f
    val bottomZone = size.height * 0.18f
    return position.y <= topZone || position.y >= size.height - bottomZone
}

internal fun applyViewerStatusBarVisibility(view: View, immersive: Boolean) {
    val activity = view.context.findActivity()
    val window = activity?.window ?: return
    val controller = WindowCompat.getInsetsController(window, view)
    if (immersive) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        controller.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.systemBarsBehavior =
                android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            window.insetsController?.controlWindowInsetsAnimation(
                WindowInsets.Type.statusBars(),
                0L,
                LinearInterpolator(),
                null,
                object : WindowInsetsAnimationControlListener {
                    override fun onReady(
                        animationController: WindowInsetsAnimationController,
                        types: Int,
                    ) {
                        animationController.setInsetsAndAlpha(
                            animationController.hiddenStateInsets,
                            0f,
                            1f,
                        )
                        animationController.finish(true)
                    }

                    override fun onFinished(animationController: WindowInsetsAnimationController) = Unit

                    override fun onCancelled(animationController: WindowInsetsAnimationController?) {
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility =
                            window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_FULLSCREEN
                    }
                },
            )
        } else {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN.inv() and
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY.inv()
        controller.show(WindowInsetsCompat.Type.statusBars())
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
internal fun ViewerStatusBarEffect(immersive: Boolean = false) {
    val view = LocalView.current
    DisposableEffect(view) {
        val activity = view.context.findActivity()
        val window = activity?.window
        val previousStatusBarColor = window?.statusBarColor
        val previousLightStatusBars = window?.let {
            WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars
        }
        val previousSystemBarsBehavior = window?.let {
            WindowCompat.getInsetsController(it, view).systemBarsBehavior
        }
        val previousWindowFlags = window?.attributes?.flags
        @Suppress("DEPRECATION")
        val previousSystemUiVisibility = window?.decorView?.systemUiVisibility

        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            window.statusBarColor = android.graphics.Color.rgb(0x18, 0x2A, 0x35)
            controller.isAppearanceLightStatusBars = false
        }

        onDispose {
            if (window != null && previousStatusBarColor != null && previousLightStatusBars != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.show(WindowInsetsCompat.Type.statusBars())
                if (previousWindowFlags != null &&
                    previousWindowFlags and WindowManager.LayoutParams.FLAG_FULLSCREEN != 0
                ) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                }
                if (previousSystemUiVisibility != null) {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = previousSystemUiVisibility
                }
                window.statusBarColor = previousStatusBarColor
                controller.isAppearanceLightStatusBars = previousLightStatusBars
                if (previousSystemBarsBehavior != null) {
                    controller.systemBarsBehavior = previousSystemBarsBehavior
                }
            }
        }
    }

    SideEffect {
        val window = view.context.findActivity()?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            window.statusBarColor = android.graphics.Color.rgb(0x10, 0x1F, 0x26)
            controller.isAppearanceLightStatusBars = false
        }
        applyViewerStatusBarVisibility(view, immersive)
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
    onOpenPostDetail: (PostDetailPlaceholderRoute) -> Unit = {},
    onOpenCreatePost: (CreatePostRoute) -> Unit = {},
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
    val view = LocalView.current
    val spacing = YingShiThemeTokens.spacing
    val motion = YingShiThemeTokens.motion
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val sessionVersion = AuthSessionManager.sessionVersion
    val viewerAccessToken = remember(sessionVersion) {
        AuthSessionManager.getAccessToken()?.takeIf { it.isNotBlank() }
    }
    val settingsState = FakeSettingsRepository.getSettingsState()
    val collaboratorDirectory = rememberCollaboratorDirectorySnapshot()
    var viewerItems by remember(route) {
        mutableStateOf(route.mediaItems)
    }
    val initialPage = route.initialIndex.coerceIn(0, viewerItems.lastIndex)
    val zoomState = remember { ViewerZoomState() }
    var isImmersive by remember { mutableStateOf(false) }
    var showCommentPreview by remember { mutableStateOf(false) }
    var commentPanelState by remember { mutableStateOf<ViewerCommentPanelState?>(null) }
    var showRelatedPostsSheet by remember { mutableStateOf(false) }
    var openCommentComposerOnSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showTimeEditorSheet by remember { mutableStateOf(false) }
    var viewerNotice by remember { mutableStateOf<ViewerNotice?>(null) }
    var viewerNoticeNonce by remember { mutableIntStateOf(0) }
    var videoPlaybackState by remember {
        mutableStateOf(ViewerVideoPlaybackState())
    }
    var videoControlsVisible by remember { mutableStateOf(true) }
    var videoControlsActivityNonce by remember { mutableIntStateOf(0) }
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
    val currentOriginalTarget = remember(currentItem) {
        currentItem.toRealOriginalMediaTarget()
    }
    val currentOriginalState = if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
        if (currentItem.mediaType == AppMediaType.IMAGE) {
            RealOriginalLoadRepository.getState(currentOriginalTarget)
        } else {
            OriginalLoadState.NotLoaded
        }
    } else {
        FakeOriginalLoadRepository.getState(currentItem.mediaId)
    }
    val currentCacheState = FakeMediaCacheRepository.getState(
        mediaId = currentItem.mediaId,
        mediaType = currentItem.mediaType,
    )
    val uploaderIdentity = remember(collaboratorDirectory, currentItem.uploadedByUserId) {
        collaboratorDirectory.resolve(currentItem.uploadedByUserId)
    }
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
    val appOverlaysVisible = overlaysVisible && !isImmersive
    val overlayAlpha = if (zoomState.isZoomed && hideOverlaysWhenZoomed) {
        ViewerLayoutTuning.zoomedOverlayAlpha
    } else {
        1f
    }
    val canOpenOriginal = remember(currentItem) {
        when (RepositoryProvider.currentMode) {
            RepositoryMode.REAL -> currentItem.mediaType == AppMediaType.IMAGE &&
                currentItem.mediaSource.hasMeaningfulViewerOriginal(currentItem.mediaType)
            RepositoryMode.FAKE -> currentItem.mediaType == AppMediaType.IMAGE
        }
    }
    var lastNotifiedOriginalState by remember(currentItem.mediaId) {
        mutableStateOf<OriginalLoadState?>(null)
    }
    fun showViewerNotice(message: String, emphasized: Boolean = false) {
        viewerNoticeNonce += 1
        viewerNotice = ViewerNotice(
            mediaId = currentItem.mediaId,
            message = message,
            emphasized = emphasized,
            nonce = viewerNoticeNonce,
        )
    }
    LaunchedEffect(currentItem.mediaId, currentOriginalState) {
        if (RepositoryProvider.currentMode != RepositoryMode.REAL || currentItem.mediaType != AppMediaType.IMAGE) {
            lastNotifiedOriginalState = currentOriginalState
            return@LaunchedEffect
        }
        val previousState = lastNotifiedOriginalState
        if (previousState == OriginalLoadState.Loading && currentOriginalState == OriginalLoadState.Loaded) {
            showViewerNotice("原图加载完毕", emphasized = true)
        } else if (previousState == OriginalLoadState.Loading && currentOriginalState == OriginalLoadState.Failed) {
            showViewerNotice("原图加载失败，已保留预览")
        }
        lastNotifiedOriginalState = currentOriginalState
    }
    val relatedPosts = remember(currentItem, route.sourcePostRoute) {
        buildViewerRelatedPosts(
            media = currentItem,
            sourcePostRoute = route.sourcePostRoute,
        )
    }
    val overlayUiModel = remember(
        currentItem,
        currentIndex,
        viewerItems.size,
        currentOriginalState,
        mediaComments.size,
        canOpenOriginal,
        previewComments,
        relatedPosts,
    ) {
        PhotoViewerOverlayUiModel(
            commentCountLabel = mediaComments.size.toString(),
            timeLabel = formatViewerTime(currentItem.mediaDisplayTimeMillis),
            pageLabel = "",
            originalLoadState = currentOriginalState,
            showOriginalAction = canOpenOriginal,
            relatedSmallAlbumsLabel = if (currentItem.smallAlbumIds.isNotEmpty()) {
                if (currentItem.smallAlbumIds.size > 1) {
                    "所属小相册 ${currentItem.smallAlbumIds.size}"
                } else {
                    "所属小相册"
                }
            } else {
                null
            },
            relatedSmallAlbums = relatedPosts,
            previewComments = previewComments,
        )
    }
    PrefetchViewerMediaAssets(
        items = viewerItems,
        currentIndex = currentIndex,
        accessToken = viewerAccessToken,
    )

    LaunchedEffect(currentIndex) {
        zoomState.reset()
        showCommentPreview = false
        commentPanelState = null
        showRelatedPostsSheet = false
        showTimeEditorSheet = false
        viewerNotice = null
        openCommentComposerOnSheet = false
        videoPlaybackState = ViewerVideoPlaybackState(
            mediaId = currentItem.mediaId.takeIf { currentItem.mediaType == AppMediaType.VIDEO },
        )
        videoControlsVisible = true
        videoControlsActivityNonce += 1
    }
    LaunchedEffect(
        currentItem.mediaId,
        currentItem.mediaType,
        videoControlsVisible,
        videoControlsActivityNonce,
        videoPlaybackState.isPlaying,
        videoPlaybackState.isLoading,
        videoPlaybackState.errorMessage,
        videoPlaybackState.isCompleted,
    ) {
        if (currentItem.mediaType != AppMediaType.VIDEO || !videoControlsVisible) return@LaunchedEffect
        if (videoPlaybackState.isLoading || videoPlaybackState.errorMessage != null) return@LaunchedEffect
        kotlinx.coroutines.delay(2800)
        videoControlsVisible = false
    }
    fun revealVideoControls() {
        videoControlsVisible = true
        videoControlsActivityNonce += 1
    }
    fun toggleImmersive() {
        val nextImmersive = !isImmersive
        applyViewerStatusBarVisibility(view, nextImmersive)
        isImmersive = nextImmersive
        if (nextImmersive) {
            showCommentPreview = false
            commentPanelState = null
            showRelatedPostsSheet = false
            showTimeEditorSheet = false
            videoControlsVisible = false
        }
    }
    BackHandler(enabled = !isImmersive && zoomState.isZoomed) {
        zoomState.reset()
    }
    BackHandler(enabled = !isImmersive && showCommentPreview) {
        showCommentPreview = false
    }
    BackHandler(enabled = !isImmersive && commentPanelState != null) {
        commentPanelState = null
    }
    BackHandler(enabled = !isImmersive && showRelatedPostsSheet) {
        showRelatedPostsSheet = false
    }
    BackHandler(enabled = !isImmersive && showTimeEditorSheet) {
        showTimeEditorSheet = false
    }
    BackHandler(enabled = !isImmersive && !zoomState.isZoomed && !showCommentPreview && commentPanelState == null && !showRelatedPostsSheet && !showTimeEditorSheet) {
        onBack()
    }
    BackHandler(enabled = isImmersive) {
        onBack()
    }
    ViewerStatusBarEffect(immersive = isImmersive)

    if (showDeleteConfirm) {
        val dialogColors = YingShiThemeTokens.colors
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = dialogColors.raisedSurface,
            titleContentColor = dialogColors.titleAccent,
            textContentColor = dialogColors.textSecondary,
            title = {
                Text(
                    text = "删除当前媒体到回收站？",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            },
            text = {
                Text(
                    text = "当前媒体会从照片流消失，并影响所有引用它的小相册。删除后会进入映世回收站，可以在回收站中恢复。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TrashDialogActionButton(
                    text = "删除到回收站",
                    danger = true,
                    onClick = {
                        showDeleteConfirm = false
                        val deletingItem = currentItem
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
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { showDeleteConfirm = false })
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ViewerNightBottom)
            .viewerSingleTapGesture { position, size ->
                if (currentItem.mediaType == AppMediaType.VIDEO) {
                    val topTapZonePx = with(density) {
                        if (isImmersive) {
                            ViewerLayoutTuning.immersiveCanvasTopPadding.toPx()
                        } else {
                            ViewerLayoutTuning.canvasTopPadding.toPx()
                        }
                    }
                    val bottomTapZonePx = with(density) {
                        if (isImmersive) {
                            ViewerLayoutTuning.immersiveVideoBottomExitZone.toPx()
                        } else {
                            ViewerLayoutTuning.canvasBottomPadding.toPx()
                        }
                    }
                    if (position.y <= topTapZonePx || position.y >= size.height - bottomTapZonePx) {
                        toggleImmersive()
                    }
                } else {
                    toggleImmersive()
                }
            },
    ) {
        ViewerAtmosphereLayer(
            modifier = Modifier
                .matchParentSize()
                .alpha(if (isImmersive) 0.42f else 1f),
        )

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
                    if (viewerItems[page].mediaType == AppMediaType.IMAGE) {
                        RealOriginalLoadRepository.getState(viewerItems[page].toRealOriginalMediaTarget())
                    } else {
                        OriginalLoadState.NotLoaded
                    }
                } else {
                    FakeOriginalLoadRepository.getState(viewerItems[page].mediaId)
                },
                overlaysVisible = overlaysVisible,
                immersive = isImmersive,
                videoControlsVisible = videoControlsVisible,
                onVideoAreaClick = {
                    if (videoControlsVisible) {
                        videoControlsVisible = false
                        videoControlsActivityNonce += 1
                    } else {
                        revealVideoControls()
                    }
                },
                onTogglePlayback = {
                    revealVideoControls()
                    val durationMillis = videoPlaybackState.durationMillis
                        ?: currentItem.viewerVideoDurationMillis()
                    val shouldRestart = videoPlaybackState.isCompleted ||
                        (durationMillis > 0L && videoPlaybackState.progressMillis >= durationMillis)
                    videoPlaybackState = if (videoPlaybackState.errorMessage != null) {
                        videoPlaybackState.retryState().copy(mediaId = currentItem.mediaId)
                    } else if (videoPlaybackState.isPlaying) {
                        videoPlaybackState.copy(isPlaying = false)
                    } else {
                        videoPlaybackState.copy(
                            mediaId = currentItem.mediaId,
                            isPlaying = true,
                            progressMillis = if (shouldRestart) 0L else videoPlaybackState.progressMillis,
                            seekRequestMillis = if (shouldRestart) 0L else videoPlaybackState.seekRequestMillis,
                            seekRequestNonce = if (shouldRestart) {
                                videoPlaybackState.seekRequestNonce + 1
                            } else {
                                videoPlaybackState.seekRequestNonce
                            },
                            errorMessage = null,
                            isCompleted = false,
                        )
                    }
                },
                onSeekPlayback = { progressMillis ->
                    revealVideoControls()
                    val durationMillis = videoPlaybackState.durationMillis
                        ?: currentItem.viewerVideoDurationMillis()
                    val targetMillis = progressMillis.coerceIn(0L, durationMillis.coerceAtLeast(0L))
                    videoPlaybackState = videoPlaybackState.copy(
                        mediaId = currentItem.mediaId,
                        progressMillis = targetMillis,
                        seekRequestMillis = targetMillis,
                        seekRequestNonce = videoPlaybackState.seekRequestNonce + 1,
                        errorMessage = null,
                        isCompleted = false,
                    )
                },
                onVideoPlaybackStateChange = { mediaId, state ->
                    if (mediaId == currentItem.mediaId) {
                        videoPlaybackState = state
                    }
                },
                onOriginalLoadStateChange = { mediaId, state ->
                    if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
                        val changedItem = viewerItems.firstOrNull { it.mediaId == mediaId }
                        if (changedItem != null) {
                            val changedTarget = changedItem.toRealOriginalMediaTarget()
                            val previousState = RealOriginalLoadRepository.getState(changedTarget)
                            if (previousState != state) {
                                RealOriginalLoadRepository.setState(changedTarget, state)
                                if (mediaId == currentItem.mediaId) {
                                    when (state) {
                                        OriginalLoadState.Loaded -> {
                                            showViewerNotice("原图加载完毕", emphasized = true)
                                        }
                                        OriginalLoadState.Failed -> {
                                            showViewerNotice("原图加载失败，已保留预览")
                                        }
                                        else -> Unit
                                    }
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (!isImmersive) {
            ViewerTopScrim(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(124.dp),
            )
        }

        if (appOverlaysVisible) {
            ViewerBottomScrim(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(188.dp),
            )
        }

        AnimatedVisibility(
            visible = !isImmersive,
            enter = fadeIn(tween(motion.floatingMillis, easing = motion.easing)) +
                slideInVertically(
                    animationSpec = tween(motion.floatingMillis, easing = motion.easing),
                    initialOffsetY = { -it / 4 },
                ),
            exit = fadeOut(tween(motion.stateMillis, easing = motion.easing)) +
                slideOutVertically(
                    animationSpec = tween(motion.stateMillis, easing = motion.easing),
                    targetOffsetY = { -it / 5 },
                ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        ) {
            PhotoViewerTopBar(
                onBack = {
                    onBack()
                },
                timeLabel = overlayUiModel.timeLabel,
                uploaderIdentity = uploaderIdentity,
                onShare = {
                    showViewerNotice("当前设备未提供可用分享入口")
                },
                onEditTime = { showTimeEditorSheet = true },
                onDelete = { showDeleteConfirm = true },
                onOpenRelatedPosts = { showRelatedPostsSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        start = ViewerLayoutTuning.topBarStartInset,
                        end = ViewerLayoutTuning.topBarEndInset,
                        top = ViewerLayoutTuning.topBarTopInset,
                    ),
                overlayAlpha = overlayAlpha,
            )
        }

        if (appOverlaysVisible) {
            AnimatedVisibility(
                visible = showCommentPreview,
                enter = fadeIn(tween(motion.floatingMillis, easing = motion.easing)) +
                    slideInVertically(
                        animationSpec = tween(motion.floatingMillis, easing = motion.easing),
                        initialOffsetY = { it / 5 },
                    ),
                exit = fadeOut(tween(motion.stateMillis, easing = motion.easing)) +
                    slideOutVertically(
                        animationSpec = tween(motion.stateMillis, easing = motion.easing),
                        targetOffsetY = { it / 6 },
                    ),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(
                        start = spacing.lg,
                        bottom = edgeActionsBottomPadding + 64.dp,
                    ),
            ) {
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
                )
            }
        }

        AnimatedVisibility(
            visible = appOverlaysVisible,
            enter = fadeIn(tween(motion.floatingMillis, easing = motion.easing)) +
                slideInVertically(
                    animationSpec = tween(motion.floatingMillis, easing = motion.easing),
                    initialOffsetY = { it / 4 },
                ),
            exit = fadeOut(tween(motion.stateMillis, easing = motion.easing)) +
                slideOutVertically(
                    animationSpec = tween(motion.stateMillis, easing = motion.easing),
                    targetOffsetY = { it / 5 },
                ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            PhotoViewerEdgeActions(
                overlayUiModel = overlayUiModel,
                showCommentPreview = showCommentPreview,
                modifier = Modifier
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
                        when {
                            currentItem.mediaType != AppMediaType.IMAGE ||
                                !currentItem.mediaSource.hasMeaningfulViewerOriginal(currentItem.mediaType) -> {
                                showViewerNotice("当前媒体没有独立原图")
                            }

                            currentOriginalState == OriginalLoadState.Loading -> {
                                showViewerNotice("原图加载中")
                            }

                            currentOriginalState == OriginalLoadState.Loaded -> {
                                showViewerNotice("已加载原图", emphasized = true)
                            }

                            else -> {
                                if (RealOriginalLoadRepository.requestOriginal(context, currentOriginalTarget, viewerAccessToken)) {
                                    showViewerNotice("开始加载原图")
                                } else {
                                    showViewerNotice("当前媒体没有独立原图")
                                }
                            }
                        }
                    } else {
                        when (currentOriginalState) {
                            OriginalLoadState.NotLoaded -> {
                                FakeOriginalLoadRepository.loadOriginal(currentItem.mediaId)
                                showViewerNotice("开始加载原图")
                            }

                            OriginalLoadState.Loading -> {
                                showViewerNotice("原图加载中")
                            }

                            OriginalLoadState.Loaded -> {
                                showViewerNotice("已加载原图", emphasized = true)
                            }

                            OriginalLoadState.Failed -> {
                                FakeOriginalLoadRepository.retryOriginal(currentItem.mediaId)
                                showViewerNotice("重试加载原图")
                            }
                        }
                    }
                },
            )
        }

        ViewerNoticeHost(
            notice = viewerNotice,
            currentMediaId = currentItem.mediaId,
            onExpired = { nonce ->
                if (viewerNotice?.nonce == nonce) {
                    viewerNotice = null
                }
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 58.dp),
        )

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
                    onOpenPostDetail(post.route)
                },
                onDismiss = { showRelatedPostsSheet = false },
            )
        }

        if (showTimeEditorSheet) {
            ViewerTimeEditorSheet(
                initialTimeMillis = currentItem.mediaDisplayTimeMillis,
                onDismiss = { showTimeEditorSheet = false },
                onConfirm = { nextTimeMillis ->
                    showTimeEditorSheet = false
                    FakePhotoFeedRepository.updateMediaDisplayTime(
                        mediaId = currentItem.mediaId,
                        displayTimeMillis = nextTimeMillis,
                    )
                    MediaTimeOverrides.put(currentItem.mediaId, nextTimeMillis)
                    // Trigger a real-backend refresh so the photo-feed re-maps with the new time
                    notifyRealBackendContentChanged(mediaIds = setOf(currentItem.mediaId))
                    val currentMediaId = currentItem.mediaId
                    val nextItems = viewerItems
                        .map { item ->
                            if (item.mediaId == currentMediaId) {
                                item.withViewerDisplayTime(nextTimeMillis)
                            } else {
                                item
                            }
                        }
                        .sortedByDescending { it.mediaDisplayTimeMillis }
                    viewerItems = nextItems
                    coroutineScope.launch {
                        val nextIndex = nextItems.indexOfFirst { it.mediaId == currentMediaId }
                            .takeIf { it >= 0 }
                            ?: currentIndex.coerceIn(0, nextItems.lastIndex)
                        pagerState.scrollToPage(nextIndex)
                    }
                    Toast.makeText(context, "时间已修改", Toast.LENGTH_SHORT).show()
                },
            )
        }

    }
}

@Composable
private fun PrefetchViewerMediaAssets(
    items: List<PhotoFeedItem>,
    currentIndex: Int,
    accessToken: String?,
) {
    if (RepositoryProvider.currentMode != RepositoryMode.REAL || items.isEmpty()) return

    val context = LocalContext.current
    val targets = remember(items, currentIndex) {
        buildList {
            listOf(currentIndex - 1, currentIndex, currentIndex + 1)
                .distinct()
                .forEach { index ->
                    val item = items.getOrNull(index) ?: return@forEach
                    add(item)
                }
        }
    }

    LaunchedEffect(context, targets, accessToken) {
        val imageLoader = context.imageLoader
        targets.forEach { item ->
            if (item.mediaType == AppMediaType.VIDEO) {
                val posterImageUrl = item.mediaSource
                    ?.thumbnailModelUrl(item.mediaType)
                    ?.takeUnless { looksLikeVideoSource(it, item.mediaSource?.mimeType) }
                if (posterImageUrl != null) {
                    backendMediaImageRequest(
                        context = context,
                        url = posterImageUrl,
                        accessToken = accessToken,
                        memoryCacheKey = sharedPreviewMemoryCacheKey(posterImageUrl),
                        size = 1280,
                    )?.let(imageLoader::enqueue)
                    return@forEach
                }
                item.mediaSource?.viewerVideoUrl(item.mediaType)?.let { videoUrl ->
                    prefetchVideoPoster(
                        context = context,
                        url = videoUrl,
                        accessToken = accessToken,
                    )
                }
            } else {
                item.mediaSource?.viewerPreviewImageUrl(item.mediaType)?.let { previewUrl ->
                    backendMediaImageRequest(
                        context = context,
                        url = previewUrl,
                        accessToken = accessToken,
                        memoryCacheKey = sharedPreviewMemoryCacheKey(previewUrl),
                        size = 1280,
                    )?.let(imageLoader::enqueue)
                }
            }
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
                    ViewerNightBottom.copy(alpha = 0.62f),
                    ViewerNightBottom.copy(alpha = 0.24f),
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
                    ViewerNightBottom.copy(alpha = 0.20f),
                    ViewerNightBottom.copy(alpha = 0.58f),
                ),
            ),
        ),
    )
}

@Composable
private fun ViewerNoticeHost(
    notice: ViewerNotice?,
    currentMediaId: String,
    onExpired: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = YingShiThemeTokens.motion
    val spacing = YingShiThemeTokens.spacing
    val motionEnabled = rememberYingShiMotionEnabled()
    val visibleNotice = notice?.takeIf { it.mediaId == currentMediaId }

    LaunchedEffect(visibleNotice?.nonce) {
        val activeNotice = visibleNotice ?: return@LaunchedEffect
        kotlinx.coroutines.delay(motion.viewerNoticeVisibleMillis.toLong())
        onExpired(activeNotice.nonce)
    }

    AnimatedVisibility(
        visible = visibleNotice != null,
        enter = fadeIn(tween(if (motionEnabled) motion.viewerNoticeMillis else 0, easing = motion.easing)) +
            slideInVertically(
                animationSpec = tween(if (motionEnabled) motion.viewerNoticeMillis else 0, easing = motion.easing),
                initialOffsetY = { -it / 5 },
            ),
        exit = fadeOut(tween(if (motionEnabled) motion.stateMillis else 0, easing = motion.easing)) +
            slideOutVertically(
                animationSpec = tween(if (motionEnabled) motion.stateMillis else 0, easing = motion.easing),
                targetOffsetY = { -it / 6 },
            ),
        modifier = modifier,
    ) {
        val activeNotice = visibleNotice ?: return@AnimatedVisibility
        Surface(
            modifier = Modifier
                .yingShiSoftReveal(visible = true, motionEnabled = motionEnabled)
                .widthIn(max = 320.dp),
            shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
            color = ViewerNightTop.copy(alpha = if (activeNotice.emphasized) 0.82f else 0.76f),
            border = BorderStroke(
                width = 1.dp,
                color = ViewerAccent.copy(alpha = if (activeNotice.emphasized) 0.34f else 0.20f),
            ),
        ) {
            Text(
                text = activeNotice.message,
                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (activeNotice.emphasized) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = ViewerSurface.copy(alpha = 0.94f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ViewerAtmosphereLayer(modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(ViewerNightBottom)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ViewerAccent.copy(alpha = 0.16f),
                            ViewerNightMiddle.copy(alpha = 0.16f),
                            Color.Transparent,
                        ),
                        center = Offset(0f, 0f),
                        radius = 980f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ViewerAccent.copy(alpha = 0.10f),
                            ViewerNightTop.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                        center = Offset(1200f, 2200f),
                        radius = 860f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ViewerNightTop.copy(alpha = 0.12f),
                            Color.Transparent,
                            ViewerNightBottom.copy(alpha = 0.34f),
                        ),
                    ),
                ),
        )
    }
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
            ViewerSheetActionButton(text = "返回", onClick = onBack)
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
    uploaderIdentity: CollaboratorIdentityUiModel?,
    onShare: () -> Unit,
    onEditTime: () -> Unit,
    onDelete: () -> Unit,
    onOpenRelatedPosts: () -> Unit,
    modifier: Modifier = Modifier,
    overlayAlpha: Float = 1f,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.alpha(overlayAlpha),
    ) {
        val topButtonShape = CircleShape
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(ViewerLayoutTuning.backButtonTouchSize)
                .yingShiClickable(shape = topButtonShape, pressedScale = 0.94f, onClick = onBack),
            shape = topButtonShape,
            color = ViewerNightTop.copy(alpha = 0.56f),
            border = BorderStroke(1.dp, ViewerAccent.copy(alpha = 0.18f)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "<",
                    style = MaterialTheme.typography.headlineSmall,
                    color = ViewerSurface.copy(alpha = 0.94f),
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            uploaderIdentity?.let { identity ->
                CollaboratorMarkerBadge(
                    identity = identity,
                    size = 44.dp,
                )
            }
            ViewerIconCircle(
                icon = Icons.Rounded.Download,
                contentDescription = "下载",
                onClick = onShare,
            )
            Box(
                modifier = Modifier.wrapContentSize(Alignment.TopEnd),
            ) {
                ViewerIconCircle(
                    icon = Icons.Rounded.MoreHoriz,
                    contentDescription = "更多",
                    onClick = { menuExpanded = true },
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.wrapContentSize(Alignment.TopEnd),
                ) {
                    DropdownMenuItem(
                        text = { Text(text = "分享") },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = "修改时间") },
                        onClick = {
                            menuExpanded = false
                            onEditTime()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = "删除媒体") },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = "所属小相册") },
                        onClick = {
                            menuExpanded = false
                            onOpenRelatedPosts()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewerIconCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val shape = CircleShape
    Surface(
        modifier = Modifier
            .size(46.dp)
            .yingShiClickable(shape = shape, pressedScale = 0.94f, onClick = onClick),
        shape = shape,
        color = ViewerNightTop.copy(alpha = 0.54f),
        border = BorderStroke(1.dp, ViewerAccent.copy(alpha = 0.18f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = ViewerSurface.copy(alpha = 0.90f),
                modifier = Modifier.size(23.dp),
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
    immersive: Boolean,
    videoControlsVisible: Boolean,
    onVideoAreaClick: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekPlayback: (Long) -> Unit,
    onVideoPlaybackStateChange: (String, ViewerVideoPlaybackState) -> Unit,
    onOriginalLoadStateChange: (String, OriginalLoadState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val density = LocalDensity.current
    val isVideo = media.mediaType == AppMediaType.VIDEO
    val topPadding by animateDpAsState(
        targetValue = if (immersive) ViewerLayoutTuning.immersiveCanvasTopPadding else ViewerLayoutTuning.canvasTopPadding,
        label = "viewerCanvasTopPadding",
    )
    val bottomPadding by animateDpAsState(
        targetValue = if (immersive) {
            if (isVideo) ViewerLayoutTuning.immersiveVideoBottomExitZone else ViewerLayoutTuning.immersiveCanvasBottomPadding
        } else {
            ViewerLayoutTuning.canvasBottomPadding
        },
        label = "viewerCanvasBottomPadding",
    )

    BoxWithConstraints(
        modifier = modifier.padding(
            start = ViewerLayoutTuning.canvasHorizontalPadding,
            top = topPadding,
            end = ViewerLayoutTuning.canvasHorizontalPadding,
            bottom = bottomPadding,
        ),
        contentAlignment = Alignment.Center,
    ) {
        val mediaAspectRatio = media.viewerAspectRatio().coerceIn(0.05f, 20f)
        val fittedMediaWidth = if (maxHeight * mediaAspectRatio <= maxWidth) {
            maxHeight * mediaAspectRatio
        } else {
            maxWidth
        }
        val fittedMediaHeight = if (maxWidth / mediaAspectRatio <= maxHeight) {
            maxWidth / mediaAspectRatio
        } else {
            maxHeight
        }
        val canvasWidth = if (isVideo) maxWidth else fittedMediaWidth
        val canvasHeight = if (isVideo) maxHeight else fittedMediaHeight
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
        var mediaEnterActive by remember(media.mediaId, zoomState != null) { mutableStateOf(false) }
        LaunchedEffect(media.mediaId, zoomState != null) {
            mediaEnterActive = zoomState != null
        }
        val mediaEnterModifier = if (zoomState != null) {
            Modifier
                .yingShiMediaEnterMotion(active = mediaEnterActive)
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
                    val revealInteractionSource = remember(media.mediaId) { MutableInteractionSource() }
                    ViewerVideoCanvas(
                        media = media,
                        playbackState = videoPlaybackState,
                        isCurrent = zoomState != null,
                        originalLoadState = originalLoadState,
                        onPlaybackStateChange = onVideoPlaybackStateChange,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(mediaEnterModifier)
                            .then(zoomTransformModifier),
                    )
                    if (videoPlaybackState?.errorMessage == null) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable(
                                    interactionSource = revealInteractionSource,
                                    indication = null,
                                    onClick = onVideoAreaClick,
                                ),
                        )
                    }
                    if (videoControlsVisible && videoPlaybackState != null) {
                        val playButtonShape = CircleShape
                        Surface(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .yingShiClickable(
                                    shape = playButtonShape,
                                    pressedScale = 0.94f,
                                    onClick = onTogglePlayback,
                                ),
                            shape = playButtonShape,
                            color = ViewerNightTop.copy(alpha = if (videoPlaybackState.isPlaying) 0.62f else 0.70f),
                            border = BorderStroke(1.dp, ViewerAccent.copy(alpha = 0.26f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .padding(22.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                VideoGlyph(
                                    state = if (videoPlaybackState.isPlaying) VideoGlyphState.PAUSE else VideoGlyphState.PLAY,
                                    tint = ViewerSurface.copy(alpha = 0.92f),
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                    if (videoControlsVisible && videoPlaybackState != null) {
                        val durationMillis = videoPlaybackState.durationMillis
                            ?: media.viewerVideoDurationMillis()
                        ViewerVideoControls(
                            playbackState = videoPlaybackState,
                            durationMillis = durationMillis,
                            onTogglePlayback = onTogglePlayback,
                            onSeekPlayback = onSeekPlayback,
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
                        .then(mediaEnterModifier)
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
                            color = ViewerNightTop.copy(alpha = 0.82f),
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
        backendMediaImageRequest(
            context = context,
            url = previewUrl,
            accessToken = accessToken,
            memoryCacheKey = previewUrl?.let(::sharedPreviewMemoryCacheKey),
        )
    }
    val originalRequest = remember(context, originalUrl, shouldRequestOriginal, accessToken) {
        if (shouldRequestOriginal) {
            backendMediaOriginalImageRequest(
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

    LaunchedEffect(media.mediaId, originalUrl, originalLoadState, originalState) {
        if (RepositoryProvider.currentMode != RepositoryMode.FAKE) return@LaunchedEffect
        when {
            originalLoadState == OriginalLoadState.Loading &&
                originalState is AsyncImagePainter.State.Success -> {
                onOriginalLoadStateChange(media.mediaId, OriginalLoadState.Loaded)
            }
            shouldRequestOriginal && originalState is AsyncImagePainter.State.Error -> {
                onOriginalLoadStateChange(media.mediaId, OriginalLoadState.Failed)
            }
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
                color = ViewerNightTop.copy(alpha = 0.84f),
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

@Composable
private fun ViewerImageFallback(
    reason: ViewerImageFailureReason,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val label = reason.message.takeIf { it.isNotBlank() } ?: return

    Surface(
        modifier = modifier.padding(spacing.lg),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = ViewerNightTop.copy(alpha = 0.82f),
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
private fun ViewerVideoPosterFallback(
    message: String,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing

    Box(
        modifier = modifier.background(ViewerNightBottom),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
            color = ViewerNightTop.copy(alpha = 0.68f),
            border = BorderStroke(1.dp, ViewerSurface.copy(alpha = 0.08f)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = CircleShape,
                    color = ViewerSurface.copy(alpha = 0.10f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        VideoGlyph(
                            state = VideoGlyphState.PLAY,
                            tint = ViewerSurface.copy(alpha = 0.88f),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelLarge,
                    color = ViewerSurface.copy(alpha = 0.82f),
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun ViewerVideoCanvas(
    media: PhotoFeedItem,
    playbackState: ViewerVideoPlaybackState?,
    isCurrent: Boolean,
    originalLoadState: OriginalLoadState,
    onPlaybackStateChange: (String, ViewerVideoPlaybackState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val context = LocalContext.current
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
    val posterImageUrl = remember(media.mediaSource, media.mediaType) {
        media.mediaSource
            .thumbnailModelUrl(media.mediaType)
            ?.takeUnless { looksLikeVideoSource(it, media.mediaSource?.mimeType) }
    }
    val posterImageRequest = remember(context, posterImageUrl, accessToken) {
        backendMediaImageRequest(
            context = context,
            url = posterImageUrl,
            accessToken = accessToken,
            memoryCacheKey = posterImageUrl?.let(::sharedPreviewMemoryCacheKey),
            size = 1280,
        )
    }
    val posterImagePainter = rememberAsyncImagePainter(model = posterImageRequest)
    val posterImageState = posterImagePainter.state
    val fallbackPosterVideoUrl = if (posterImageUrl.isNullOrBlank() ||
        posterImageState is AsyncImagePainter.State.Error
    ) {
        videoUrl
    } else {
        null
    }
    val videoPosterState = rememberVideoPosterState(
        url = fallbackPosterVideoUrl,
        accessToken = accessToken,
    ).value
    val extractedPosterPainter = rememberAsyncImagePainter(model = videoPosterState.model)
    val requestHeaders = remember(videoUrl, accessToken) {
        backendMediaRequestHeaders(videoUrl, accessToken)
    }
    var retryVersion by remember(media.mediaId) { mutableStateOf(0) }
    val retryRequestNonce = playbackState?.retryRequestNonce ?: 0
    var isPrepared by remember(media.mediaId, retryVersion, retryRequestNonce) { mutableStateOf(false) }
    val initialPositionMillis = playbackState?.progressMillis?.coerceAtLeast(0L) ?: 0L
    val player = remember(media.mediaId, videoUrl, requestHeaders, retryVersion, retryRequestNonce) {
        if (videoUrl.isNullOrBlank()) {
            null
        } else {
            ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(
                    Media3AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true,
                )
                setMediaSource(
                    ProgressiveMediaSource.Factory(
                        AppMediaVideoCache.dataSourceFactory(
                            context = context,
                            requestHeaders = requestHeaders,
                            connectTimeoutMs = 8_000,
                            readTimeoutMs = 8_000,
                        ),
                    ).createMediaSource(MediaItem.fromUri(videoUrl)),
                )
                if (initialPositionMillis > 0L) {
                    seekTo(initialPositionMillis)
                }
                prepare()
            }
        }
    }

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

    DisposableEffect(player) {
        if (player == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackStateValue: Int) {
                    when (playbackStateValue) {
                        Player.STATE_BUFFERING -> {
                            updatePlaybackState {
                                it.copy(
                                    isLoading = true,
                                    errorMessage = null,
                                )
                            }
                        }

                        Player.STATE_READY -> {
                            isPrepared = true
                            updatePlaybackState {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = null,
                                    isCompleted = false,
                                    durationMillis = player.viewerDurationMillis() ?: it.durationMillis,
                                )
                            }
                        }

                        Player.STATE_ENDED -> {
                            updatePlaybackState {
                                it.copy(
                                    isPlaying = false,
                                    isLoading = false,
                                    isCompleted = true,
                                    progressMillis = player.viewerDurationMillis() ?: it.progressMillis,
                                    durationMillis = player.viewerDurationMillis() ?: it.durationMillis,
                                )
                            }
                        }

                        else -> Unit
                    }
                }

                override fun onIsPlayingChanged(isPlayingValue: Boolean) {
                    updatePlaybackState {
                        it.copy(
                            isLoading = player.playbackState == Player.STATE_BUFFERING,
                            durationMillis = player.viewerDurationMillis() ?: it.durationMillis,
                        )
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    isPrepared = false
                    updatePlaybackState {
                        it.copy(
                            isPlaying = false,
                            isLoading = false,
                            errorMessage = "视频加载失败，请重试",
                            isCompleted = false,
                        )
                    }
                }
            }
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
                player.release()
            }
        }
    }

    DisposableEffect(isCurrent) {
        if (!isCurrent) {
            player?.pause()
            updatePlaybackState { it.copy(isPlaying = false) }
        }
        onDispose {
        }
    }

    LaunchedEffect(isCurrent, isPlaying, errorMessage, videoUrl, retryVersion) {
        if (player == null) return@LaunchedEffect
        player.playWhenReady = isCurrent && isPlaying && errorMessage == null
        if (isCurrent && isPlaying && errorMessage == null) {
            player.play()
        } else {
            player.pause()
        }
        while (isCurrent && videoUrl != null && errorMessage == null) {
            if (isPrepared) {
                updatePlaybackState {
                    it.copy(
                        progressMillis = player.currentPosition.coerceAtLeast(0L),
                        durationMillis = player.viewerDurationMillis() ?: it.durationMillis,
                        isLoading = player.playbackState == Player.STATE_BUFFERING,
                    )
                }
            }
            kotlinx.coroutines.delay(300)
        }
    }

    LaunchedEffect(playbackState?.seekRequestNonce, player) {
        val targetMillis = playbackState?.seekRequestMillis ?: return@LaunchedEffect
        player?.seekTo(targetMillis.coerceAtLeast(0L))
    }
    val hasServerPosterImage = posterImageRequest != null &&
        posterImageState !is AsyncImagePainter.State.Error
    val hasExtractedPosterImage = videoPosterState.model != null &&
        extractedPosterPainter.state !is AsyncImagePainter.State.Error
    val posterPainter = if (hasServerPosterImage) {
        posterImagePainter
    } else {
        extractedPosterPainter
    }
    val hasPosterImage = hasServerPosterImage || hasExtractedPosterImage
    val shouldShowPoster = hasPosterImage &&
        (!isPrepared || (playbackState?.progressMillis ?: 0L) <= 0L || errorMessage != null)

    Box(
        modifier = modifier
            .background(ViewerNightBottom),
    ) {
        if (!videoUrl.isNullOrBlank() && player != null) {
            key(retryVersion, retryRequestNonce) {
                AndroidView(
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            this.player = player
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { playerView ->
                        playerView.player = player
                        if (isCurrent && errorMessage == null && isPlaying) {
                            player.play()
                        } else {
                            player.pause()
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
        } else if (!isPrepared) {
            ViewerVideoPosterFallback(
                message = when {
                    videoUrl.isNullOrBlank() -> "暂无视频地址"
                    errorMessage != null -> "视频加载失败"
                    posterImageState is AsyncImagePainter.State.Loading || videoPosterState.isLoading || isLoading -> "视频准备中"
                    else -> "暂无视频封面"
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (posterImageState is AsyncImagePainter.State.Loading || videoPosterState.isLoading || isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp),
                color = ViewerSurface.copy(alpha = 0.88f),
                strokeWidth = 2.dp,
            )
        }

        if (errorMessage != null && !videoUrl.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 96.dp),
                shape = RoundedCornerShape(radius.capsule),
                color = ViewerNightTop.copy(alpha = 0.82f),
                border = BorderStroke(1.dp, ViewerSurface.copy(alpha = 0.10f)),
                onClick = {
                    retryVersion += 1
                    isPrepared = false
                    updatePlaybackState { it.retryState() }
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
internal fun ViewerVideoControls(
    playbackState: ViewerVideoPlaybackState,
    durationMillis: Long,
    onTogglePlayback: () -> Unit,
    onSeekPlayback: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val safeDurationMillis = durationMillis.coerceAtLeast(0L)
    val progressFraction = if (safeDurationMillis <= 0L) 0f else {
        (playbackState.progressMillis.toFloat() / safeDurationMillis.toFloat()).coerceIn(0f, 1f)
    }
    var draggedFraction by remember(playbackState.mediaId) { mutableStateOf<Float?>(null) }
    val displayedFraction = draggedFraction ?: progressFraction
    val displayedProgressMillis = if (safeDurationMillis <= 0L) {
        0L
    } else {
        (displayedFraction * safeDurationMillis).toLong().coerceIn(0L, safeDurationMillis)
    }

    Surface(
        modifier = modifier.widthIn(min = 240.dp, max = 420.dp),
        shape = RoundedCornerShape(radius.xl),
        color = ViewerNightTop.copy(alpha = 0.76f),
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
                        text = "${formatVideoProgress(displayedProgressMillis)} / ${formatVideoProgress(safeDurationMillis)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = ViewerSurface.copy(alpha = 0.68f),
                    )
                }
            }

            Slider(
                value = displayedFraction,
                onValueChange = { draggedFraction = it.coerceIn(0f, 1f) },
                onValueChangeFinished = {
                    val targetFraction = draggedFraction ?: displayedFraction
                    val targetMillis = if (safeDurationMillis <= 0L) {
                        0L
                    } else {
                        (targetFraction * safeDurationMillis).toLong().coerceIn(0L, safeDurationMillis)
                    }
                    draggedFraction = null
                    onSeekPlayback(targetMillis)
                },
                enabled = safeDurationMillis > 0L && playbackState.errorMessage == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
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

internal fun PhotoFeedItem.viewerVideoDurationMillis(): Long {
    return videoDurationMillis ?: DefaultViewerVideoDurationMillis
}

private fun ExoPlayer.viewerDurationMillis(): Long? {
    return duration.takeIf { it != C.TIME_UNSET && it > 0L }
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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ViewerCommentEntry(
            commentCountLabel = overlayUiModel.commentCountLabel,
            previewExpanded = showCommentPreview,
            onClick = onOpenComments,
        )

        ViewerCapsule(
            text = overlayUiModel.timeLabel,
            emphasized = false,
            surfaceAlpha = 0.10f,
            contentAlpha = 0.86f,
        )

        if (overlayUiModel.showOriginalAction) {
            ViewerCapsule(
                text = overlayUiModel.originalLoadState.actionLabel(),
                emphasized = overlayUiModel.originalLoadState == OriginalLoadState.Loaded,
                enabled = overlayUiModel.originalLoadState != OriginalLoadState.Loading,
                onClick = onOpenOriginal,
            )
        } else {
            ViewerCapsule(
                text = "原图已保存",
                emphasized = false,
                surfaceAlpha = 0.08f,
                contentAlpha = 0.78f,
            )
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
            .yingShiClickable(
                shape = RoundedCornerShape(radius.capsule),
                pressedScale = 0.96f,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = ViewerAccent.copy(alpha = if (previewExpanded) 0.24f else 0.14f),
            border = BorderStroke(1.dp, ViewerAccent.copy(alpha = 0.18f)),
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
            .then(
                if (onClick != null && enabled) {
                    Modifier.yingShiClickable(shape = shape, pressedScale = 0.96f, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        shape = shape,
        color = ViewerNightTop.copy(alpha = if (enabled) surfaceAlpha + 0.26f else 0.22f),
        border = BorderStroke(
            width = 1.dp,
            color = ViewerAccent.copy(alpha = if (enabled) surfaceAlpha + 0.10f else 0.08f),
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
    val shape = RoundedCornerShape(radius.lg)

    Surface(
        modifier = modifier
            .fillMaxWidth(ViewerLayoutTuning.commentPreviewWidthFraction)
            .widthIn(max = ViewerLayoutTuning.commentPreviewMaxWidth)
            .height(ViewerLayoutTuning.commentPreviewHeight),
        shape = shape,
        color = ViewerNightTop.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, ViewerAccent.copy(alpha = 0.20f)),
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ViewerAccent.copy(alpha = 0.14f),
                            Color.Transparent,
                        ),
                        center = Offset(0f, 0f),
                        radius = 360f,
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
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
                        color = ViewerSurface.copy(alpha = 0.90f),
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
                        color = ViewerSurface.copy(alpha = 0.64f),
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
                            color = ViewerSurface.copy(alpha = 0.88f),
                            maxLines = 2,
                        )
                    }
                }
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
                text = "当前媒体缓存 ${cacheState.cacheSizeLabel}",
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
                subtitle = "查看缓存占用并清理全部预览、原图和视频缓存",
                onClick = onOpenGlobalCacheManagement,
            )
            ViewerSheetActionButton(
                text = "关闭",
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            )
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

@Composable
private fun ViewerSheetActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        color = if (emphasized) {
            ViewerSurface.copy(alpha = 0.16f)
        } else {
            ViewerSurface.copy(alpha = 0.08f)
        },
        border = BorderStroke(1.dp, ViewerSurface.copy(alpha = if (emphasized) 0.18f else 0.10f)),
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) ViewerSurface.copy(alpha = 0.90f) else ViewerSurface.copy(alpha = 0.38f),
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
                    text = "已定位到这条评论",
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
                        ViewerSheetActionButton(text = "重试", emphasized = true, onClick = onRetry)
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
                ViewerSheetActionButton(text = "展开更多评论", onClick = { expanded = true })
            }
            if (comments.canCollapseComments(expanded)) {
                ViewerSheetActionButton(text = "收起到最新 10 条", onClick = { expanded = false })
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
                text = "所属小相册",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ViewerSurface.copy(alpha = 0.94f),
            )
            if (posts.isEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
            } else {
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
                    }
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
        "这张适合单独留一句。",
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

private fun buildViewerRelatedPosts(
    media: PhotoFeedItem,
    sourcePostRoute: PostDetailPlaceholderRoute?,
): List<ViewerRelatedPostUiModel> {
    return media.postIds.distinct().map { postId ->
        val route = buildViewerRelatedPostRoute(
            media = media,
            postId = postId,
            sourcePostRoute = sourcePostRoute,
        )
        ViewerRelatedPostUiModel(
            id = postId,
            title = route.title,
            subtitle = "",
            route = route,
        )
    }
}

private fun buildViewerRelatedPostRoute(
    media: PhotoFeedItem,
    postId: String,
    sourcePostRoute: PostDetailPlaceholderRoute?,
): PostDetailPlaceholderRoute {
    if (sourcePostRoute?.postId == postId) {
        return sourcePostRoute
    }
    if (RepositoryProvider.currentMode == RepositoryMode.FAKE) {
        FakeAlbumRepository.getPost(postId)?.let { post ->
            return FakeAlbumRepository.toPostDetailRoute(post)
        }
    }
    val fallbackTitle = sourcePostRoute
        ?.takeIf { it.postId == postId }
        ?.title
        ?: placeholderPostTitle(postId)
    val fallbackSummary = sourcePostRoute
        ?.takeIf { it.postId == postId }
        ?.summary
        ?: ""
    return PostDetailPlaceholderRoute(
        postId = postId,
        albumId = sourcePostRoute?.albumId ?: "viewer-related",
        albumIds = sourcePostRoute?.albumIds ?: listOf("viewer-related"),
        title = fallbackTitle,
        summary = fallbackSummary,
        postDisplayTimeMillis = media.mediaDisplayTimeMillis,
        mediaCount = 0,
        coverPalette = media.palette,
        coverMediaType = media.mediaType,
        coverAspectRatio = media.aspectRatio,
    )
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
                mediaSource = item.mediaSource,
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
        return "请先连接服务，再删除这项媒体。"
    }
    return when (val result = RepositoryProvider.mediaRepository.systemDeleteMedia(mediaId)) {
        is ApiResult.Success -> {
            TrashActorHintStore.record(
                item = result.data,
                fallbackActorUserId = currentCollaboratorActorUserId(),
            )
            notifyRealBackendContentChanged(
                mediaIds = setOf(mediaId),
            )
            null
        }
        is ApiResult.Error -> result.toBackendUiMessage("删除真实媒体失败。")
        ApiResult.Loading -> null
    }
}

private fun buildViewerTimeMillis(
    selectedDateMillis: Long,
    hour: Int,
    minute: Int,
): Long {
    return Calendar.getInstance(Locale.CHINA).run {
        timeInMillis = selectedDateMillis
        set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
        set(Calendar.MINUTE, minute.coerceIn(0, 59))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }
}

private fun PhotoFeedItem.withViewerDisplayTime(timeMillis: Long): PhotoFeedItem {
    val calendar = Calendar.getInstance(Locale.CHINA).apply {
        timeInMillis = timeMillis
    }
    return copy(
        mediaDisplayTimeMillis = timeMillis,
        displayYear = calendar.get(Calendar.YEAR),
        displayMonth = calendar.get(Calendar.MONTH) + 1,
        displayDay = calendar.get(Calendar.DAY_OF_MONTH),
    )
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
                showSmallAlbumSegments = false,
            ),
            onBack = { },
        )
    }
}

