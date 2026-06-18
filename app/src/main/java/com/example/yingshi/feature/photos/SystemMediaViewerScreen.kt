package com.example.yingshi.feature.photos

import android.app.Activity
import android.os.SystemClock
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.yingshi.ui.components.YingShiNotice
import com.example.yingshi.ui.components.YingShiNoticeHost
import com.example.yingshi.ui.components.YingShiNoticeTone
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

private const val MinSystemViewerScale = 1f
private const val StableMaxSystemViewerScale = 6f
private const val ElasticMaxSystemViewerScale = 9f
private const val SystemViewerDoubleTapScale = 2.5f
private const val SystemViewerFastDoubleTapWindowMillis = 260L
private const val SystemViewerResetScale = 1.02f
private const val SystemLongImageHeightWidthRatioThreshold = 3.8f

private object SystemViewerLayoutTuning {
    val canvasHorizontalPadding = 0.dp
    val canvasTopPadding = 88.dp
    val canvasBottomPadding = 84.dp
    val immersiveCanvasTopPadding = 0.dp
    val immersiveCanvasBottomPadding = 0.dp
    val immersiveVideoBottomExitZone = 64.dp
}

private class SystemViewerZoomState {
    var scale by mutableStateOf(MinSystemViewerScale)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    private var latestContentSize by mutableStateOf(IntSize.Zero)
    private var latestContentTopLeft by mutableStateOf(Offset.Zero)
    private var latestMinimumScale by mutableStateOf(MinSystemViewerScale)

    val isZoomed: Boolean
        get() = abs(scale - MinSystemViewerScale) > SystemViewerResetScale - MinSystemViewerScale

    fun reset() {
        scale = MinSystemViewerScale
        offset = Offset.Zero
    }

    fun updateContentGeometry(
        contentSize: IntSize,
        contentTopLeft: Offset,
        minimumScale: Float = MinSystemViewerScale,
    ) {
        if (contentSize.width > 0 && contentSize.height > 0) {
            latestContentSize = contentSize
            latestContentTopLeft = contentTopLeft
            latestMinimumScale = minimumScale.coerceIn(0.05f, MinSystemViewerScale)
        }
    }

    fun toggleDoubleTap(
        tapPosition: Offset,
        containerSize: IntSize,
    ) {
        if (isZoomed) {
            reset()
        } else {
            val targetScale = SystemViewerDoubleTapScale.coerceIn(
                MinSystemViewerScale,
                StableMaxSystemViewerScale,
            )
            scale = targetScale
            val contentSize = latestContentSize.takeIf { it.width > 0 && it.height > 0 } ?: containerSize
            val contentTopLeft = latestContentTopLeft
            val normalizedTap = tapPosition - contentTopLeft
            offset = clampOffset(
                value = tapPosition - contentTopLeft - normalizedTap * targetScale,
                currentScale = targetScale,
                containerSize = containerSize,
                contentSize = contentSize,
                contentTopLeft = contentTopLeft,
            )
        }
    }

    fun settle(
        containerSize: IntSize,
        contentSize: IntSize,
        contentTopLeft: Offset,
    ) {
        if (scale > StableMaxSystemViewerScale) {
            val previousScale = scale
            scale = StableMaxSystemViewerScale
            offset = clampOffset(
                value = offset * (StableMaxSystemViewerScale / previousScale),
                currentScale = StableMaxSystemViewerScale,
                containerSize = containerSize,
                contentSize = contentSize,
                contentTopLeft = contentTopLeft,
            )
            return
        }
        clampOffset(containerSize, contentSize = contentSize, contentTopLeft = contentTopLeft)
    }

    fun applyTransform(
        zoomChange: Float,
        panChange: Offset,
        focalPoint: Offset,
        containerSize: IntSize,
        contentSize: IntSize,
        contentTopLeft: Offset,
    ) {
        val minimumScale = latestMinimumScale
        val nextScale = (scale * zoomChange).coerceIn(minimumScale, ElasticMaxSystemViewerScale)
        if (minimumScale >= MinSystemViewerScale && nextScale <= SystemViewerResetScale) {
            reset()
            return
        }
        val previousScale = scale
        scale = nextScale
        val scaleRatio = if (previousScale > 0f) nextScale / previousScale else MinSystemViewerScale
        val focalAnchoredOffset = focalPoint -
            contentTopLeft -
            (focalPoint - contentTopLeft - offset) * scaleRatio +
            panChange
        offset = clampOffset(
            value = focalAnchoredOffset,
            currentScale = nextScale,
            containerSize = containerSize,
            contentSize = contentSize,
            contentTopLeft = contentTopLeft,
        )
    }

    private fun clampOffset(
        containerSize: IntSize,
        panChange: Offset = Offset.Zero,
        contentSize: IntSize = latestContentSize.takeIf { it.width > 0 && it.height > 0 } ?: containerSize,
        contentTopLeft: Offset = latestContentTopLeft,
    ) {
        if (containerSize.width <= 0 || containerSize.height <= 0) {
            offset = Offset.Zero
            return
        }
        offset = clampOffset(offset + panChange, scale, containerSize, contentSize, contentTopLeft)
    }

    private fun clampOffset(
        value: Offset,
        currentScale: Float,
        containerSize: IntSize,
        contentSize: IntSize,
        contentTopLeft: Offset,
    ): Offset {
        if (containerSize.width <= 0 || containerSize.height <= 0) {
            return Offset.Zero
        }
        val scaledWidth = contentSize.width * currentScale
        val scaledHeight = contentSize.height * currentScale
        val minX = if (scaledWidth <= containerSize.width) {
            ((containerSize.width - scaledWidth) / 2f) - contentTopLeft.x
        } else {
            containerSize.width - contentTopLeft.x - scaledWidth
        }
        val maxX = if (scaledWidth <= containerSize.width) minX else -contentTopLeft.x
        val minY = if (scaledHeight <= containerSize.height) {
            ((containerSize.height - scaledHeight) / 2f) - contentTopLeft.y
        } else {
            containerSize.height - contentTopLeft.y - scaledHeight
        }
        val maxY = if (scaledHeight <= containerSize.height) minY else -contentTopLeft.y
        return Offset(
            x = value.x.coerceIn(minX, maxX),
            y = value.y.coerceIn(minY, maxY),
        )
    }
}

private fun Modifier.systemViewerZoomGesture(
    zoomState: SystemViewerZoomState,
    contentSize: IntSize,
    contentTopLeft: Offset,
    onDoubleTap: ((Offset, IntSize) -> Unit)? = null,
): Modifier = pointerInput(zoomState, contentSize, contentTopLeft) {
    var lastTapUptimeMillis = 0L
    var lastTapPosition: Offset? = null
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val start = down.position
        val wasZoomedAtGestureStart = zoomState.isZoomed
        var pointerCountExceeded = false
        var moved = false
        var consumedByTransform = false
        var upPosition = start
        while (true) {
            val event = awaitPointerEvent()
            val activeChanges = event.changes.filter { it.pressed }
            if (activeChanges.isEmpty()) {
                event.changes.firstOrNull { it.id == down.id }?.let { upPosition = it.position }
                break
            }
            if (activeChanges.size > 1) pointerCountExceeded = true
            event.changes.forEach { change ->
                if ((change.position - start).getDistance() > viewConfiguration.touchSlop) {
                    moved = true
                }
            }

            if (activeChanges.size >= 2) {
                val currentCentroid = activeChanges.systemViewerCentroid(usePrevious = false)
                val previousCentroid = activeChanges.systemViewerCentroid(usePrevious = true)
                val currentDistance = activeChanges.systemViewerAverageDistanceTo(
                    centroid = currentCentroid,
                    usePrevious = false,
                )
                val previousDistance = activeChanges.systemViewerAverageDistanceTo(
                    centroid = previousCentroid,
                    usePrevious = true,
                )
                val zoomChange = if (previousDistance > 0f) {
                    currentDistance / previousDistance
                } else {
                    MinSystemViewerScale
                }
                zoomState.applyTransform(
                    zoomChange = zoomChange,
                    panChange = currentCentroid - previousCentroid,
                    focalPoint = currentCentroid,
                    containerSize = size,
                    contentSize = contentSize,
                    contentTopLeft = contentTopLeft,
                )
                activeChanges.forEach { it.consume() }
                consumedByTransform = true
            } else if (zoomState.isZoomed) {
                val change = activeChanges.first()
                val panChange = change.positionChange()
                if (panChange.getDistance() > viewConfiguration.touchSlop / 3f) {
                    zoomState.applyTransform(
                        zoomChange = 1f,
                        panChange = panChange,
                        focalPoint = change.position,
                        containerSize = size,
                        contentSize = contentSize,
                        contentTopLeft = contentTopLeft,
                    )
                    activeChanges.forEach { it.consume() }
                    consumedByTransform = true
                }
            }
        }
        zoomState.settle(
            containerSize = size,
            contentSize = contentSize,
            contentTopLeft = contentTopLeft,
        )
        if (wasZoomedAtGestureStart &&
            !pointerCountExceeded &&
            !moved &&
            !consumedByTransform &&
            onDoubleTap != null
        ) {
            val now = SystemClock.uptimeMillis()
            val previousTapPosition = lastTapPosition
            val doubleTapDistance = viewConfiguration.touchSlop * 8f
            val isDoubleTap = previousTapPosition != null &&
                now - lastTapUptimeMillis <= SystemViewerFastDoubleTapWindowMillis &&
                (upPosition - previousTapPosition).getDistance() <= doubleTapDistance
            if (isDoubleTap) {
                lastTapUptimeMillis = 0L
                lastTapPosition = null
                onDoubleTap(upPosition, size)
            } else {
                lastTapUptimeMillis = now
                lastTapPosition = upPosition
            }
        }
    }
}

private fun List<PointerInputChange>.systemViewerCentroid(usePrevious: Boolean): Offset {
    val total = fold(Offset.Zero) { sum, change ->
        sum + if (usePrevious) change.previousPosition else change.position
    }
    return total / size.toFloat()
}

private fun List<PointerInputChange>.systemViewerAverageDistanceTo(
    centroid: Offset,
    usePrevious: Boolean,
): Float {
    return sumOf { change ->
        val position = if (usePrevious) change.previousPosition else change.position
        (position - centroid).getDistance().toDouble()
    }.toFloat() / size
}

@Composable
fun SystemMediaViewerScreen(
    route: SystemMediaViewerRoute,
    onBack: () -> Unit,
    onOpenCreatePost: (CreatePostRoute) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val viewerColors = YingShiThemeTokens.colors
    val bridgeMutationEvent = LocalSystemMediaBridgeRepository.latestMutationEvent
    var viewerItems by remember(route) {
        mutableStateOf(route.mediaItems)
    }
    val pagerState = rememberPagerState(
        initialPage = route.initialIndex.coerceIn(0, (viewerItems.size - 1).coerceAtLeast(0)),
        pageCount = { viewerItems.size.coerceAtLeast(1) },
    )
    val currentIndex = pagerState.currentPage.coerceIn(0, (viewerItems.size - 1).coerceAtLeast(0))
    val currentItem = viewerItems.getOrNull(currentIndex)
    val zoomState = remember { SystemViewerZoomState() }
    var showMenuSheet by rememberSaveable { mutableStateOf(false) }
    var showAddToPostDialog by rememberSaveable { mutableStateOf(false) }
    var addToPostError by rememberSaveable { mutableStateOf<String?>(null) }
    var isImmersive by rememberSaveable { mutableStateOf(false) }
    var pendingTrashIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var notice by remember { mutableStateOf<YingShiNotice?>(null) }
    var noticeNonce by remember { mutableIntStateOf(0) }
    val destinationUiState by rememberSystemMediaDestinationUiState()
    val albums = destinationUiState.albums
    val posts = destinationUiState.posts

    fun showNotice(
        message: String,
        tone: YingShiNoticeTone = YingShiNoticeTone.INFO,
    ) {
        noticeNonce += 1
        notice = YingShiNotice(message = message, tone = tone, nonce = noticeNonce)
    }

    val trashLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val processedIds = pendingTrashIds
        pendingTrashIds = emptyList()
        if (processedIds.isEmpty()) return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            val hiddenCount = LocalSystemMediaBridgeRepository.markMovedToSystemTrash(processedIds)
            val nextItems = viewerItems.filterNot { processedIds.contains(it.id) }
            showNotice(
                message = if (hiddenCount > 0) {
                    "已移到系统回收站。"
                } else {
                    "这些媒体已经处理过了。"
                },
                tone = if (hiddenCount > 0) YingShiNoticeTone.SUCCESS else YingShiNoticeTone.INFO,
            )
            if (nextItems.isEmpty()) {
                onBack()
            } else {
                viewerItems = nextItems
                coroutineScope.launch {
                    pagerState.scrollToPage(currentIndex.coerceAtMost(nextItems.lastIndex))
                }
            }
        } else {
            showNotice("已取消移到系统回收站。")
        }
    }

    fun launchSystemTrashRequest(item: SystemMediaItem) {
        createSystemMediaTrashRequest(context, listOf(item))
            .onSuccess { pendingIntent ->
                pendingTrashIds = listOf(item.id)
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

    currentItem?.let { item ->
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
                        mediaItems = listOf(item),
                    )
                    if (addedCount > 0) {
                        showAddToPostDialog = false
                        addToPostError = null
                        showNotice("已加入上传队列，成功后会进入目标小相册。", YingShiNoticeTone.SUCCESS)
                    } else {
                        addToPostError = "该媒体已经在目标小相册里，或没有可添加的媒体。"
                    }
                },
                onPostChosen = { post ->
                    val addedCount = LocalSystemMediaBridgeRepository.enqueueAddToExistingPostUpload(
                        context = context,
                        postId = post.id,
                        mediaItems = listOf(item),
                        postTitle = post.title,
                    )
                    if (addedCount > 0) {
                        showAddToPostDialog = false
                        addToPostError = null
                        showNotice("已加入上传队列，成功后会进入目标小相册。", YingShiNoticeTone.SUCCESS)
                    } else {
                        addToPostError = "该媒体已经在目标小相册里，或没有可添加的媒体。"
                    }
                },
            )
        }

    }

    DisposableEffect(currentItem?.id) {
        zoomState.reset()
        onDispose { }
    }

    BackHandler {
        if (zoomState.isZoomed) {
            zoomState.reset()
        } else if (isImmersive) {
            isImmersive = false
        } else {
            onBack()
        }
    }

    LaunchedEffect(bridgeMutationEvent.version) {
        if (bridgeMutationEvent.version <= 0) return@LaunchedEffect
        val nextItems = LocalSystemMediaBridgeRepository.applyOverlay(viewerItems)
        if (nextItems == viewerItems) return@LaunchedEffect
        val nextIndex = pagerState.currentPage.coerceAtMost((nextItems.size - 1).coerceAtLeast(0))
        if (nextItems.isEmpty()) {
            onBack()
        } else {
            viewerItems = nextItems
            coroutineScope.launch {
                pagerState.scrollToPage(nextIndex)
            }
        }
    }

    ViewerStatusBarEffect(immersive = isImmersive)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(viewerColors.viewerBackground)
            .viewerSingleTapGesture(
                onTap = { _, _ ->
                    if (currentItem != null) {
                        isImmersive = !isImmersive
                    }
                },
                onDoubleTap = { position, size ->
                    if (currentItem?.type == SystemMediaType.IMAGE) {
                        zoomState.toggleDoubleTap(
                            tapPosition = position,
                            containerSize = size,
                        )
                    }
                },
            ),
    ) {
        SystemMediaViewerAtmosphereLayer(modifier = Modifier.fillMaxSize())

        if (viewerItems.isEmpty() || currentItem == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "当前没有可查看的系统媒体。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = viewerColors.viewerTextSecondary,
                )
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { page -> viewerItems[page].id },
                userScrollEnabled = viewerItems.size > 1 && !zoomState.isZoomed,
            ) { page ->
                val item = viewerItems[page]
                SystemMediaViewerCanvas(
                    item = item,
                    isCurrent = page == currentIndex,
                    immersive = isImmersive,
                    zoomState = if (page == currentIndex) zoomState else null,
                )
            }

            AnimatedVisibility(
                visible = !isImmersive,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = YingShiThemeTokens.spacing.lg, vertical = YingShiThemeTokens.spacing.md),
            ) {
                SystemMediaViewerTopBar(
                    currentIndex = currentIndex,
                    totalCount = viewerItems.size,
                    showMenu = true,
                    overlaysVisible = !zoomState.isZoomed,
                    onBack = {
                        if (zoomState.isZoomed) {
                            zoomState.reset()
                        } else {
                            onBack()
                        }
                    },
                    onOpenMenu = { showMenuSheet = true },
                )
            }

            AnimatedVisibility(
                visible = !isImmersive && !zoomState.isZoomed,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = YingShiThemeTokens.spacing.lg, vertical = YingShiThemeTokens.spacing.md),
            ) {
                SystemMediaViewerInfoCard(item = currentItem)
            }
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
                .padding(top = YingShiThemeTokens.spacing.md),
        )
    }

    if (showMenuSheet && currentItem != null) {
        SystemMediaViewerMenuSheet(
            itemImported = currentItem.isImportedToApp,
            onDismiss = { showMenuSheet = false },
            onImportToApp = {
                showMenuSheet = false
                val queuedCount = LocalSystemMediaBridgeRepository.enqueueImportToAppUpload(
                    context = context,
                    mediaItems = listOf(currentItem),
                )
                showNotice(
                    message = if (queuedCount > 0) {
                        "已加入导入队列。"
                    } else {
                        "当前媒体无法导入照片流。"
                    },
                    tone = if (queuedCount > 0) YingShiNoticeTone.SUCCESS else YingShiNoticeTone.WARNING,
                )
            },
            onAddToPost = {
                addToPostError = null
                showMenuSheet = false
                val destinationError = destinationUiState.errorMessage
                if (destinationError != null && posts.isEmpty()) {
                    showNotice(destinationError, YingShiNoticeTone.WARNING)
                } else {
                    showAddToPostDialog = true
                }
            },
            onMoveToTrash = {
                showMenuSheet = false
                launchSystemTrashRequest(currentItem)
            },
        )
    }
}

@Composable
private fun SystemMediaViewerTopBar(
    currentIndex: Int,
    totalCount: Int,
    showMenu: Boolean,
    overlaysVisible: Boolean,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (overlaysVisible) 1f else 0.35f),
        horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SystemMediaViewerCircleButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "返回",
            onClick = onBack,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "系统媒体",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.viewerText,
            )
            Text(
                text = if (totalCount > 0) "${currentIndex + 1} / $totalCount" else "0 / 0",
                style = MaterialTheme.typography.bodySmall,
                color = colors.viewerTextSecondary,
            )
        }
        if (showMenu) {
            SystemMediaViewerCircleButton(
                icon = Icons.Rounded.MoreHoriz,
                contentDescription = "媒体操作",
                onClick = onOpenMenu,
            )
        }
    }
}

@Composable
private fun SystemMediaViewerCanvas(
    item: SystemMediaItem,
    isCurrent: Boolean,
    immersive: Boolean,
    zoomState: SystemViewerZoomState?,
) {
    var containerSize by remember(item.id) { mutableStateOf(IntSize.Zero) }
    var videoPlaybackState by remember(item.id) {
        mutableStateOf(ViewerVideoPlaybackState())
    }
    val colors = YingShiThemeTokens.colors
    val density = LocalDensity.current
    var videoRetryVersion by remember(item.id) { mutableStateOf(0) }
    var videoControlsVisible by remember(item.id) { mutableStateOf(true) }
    var videoControlsActivityNonce by remember(item.id) { mutableIntStateOf(0) }
    LaunchedEffect(
        item.id,
        item.type,
        videoControlsVisible,
        videoControlsActivityNonce,
        videoPlaybackState.isPlaying,
        videoPlaybackState.isLoading,
        videoPlaybackState.errorMessage,
        videoPlaybackState.isCompleted,
    ) {
        if (item.type != SystemMediaType.VIDEO || !videoControlsVisible) return@LaunchedEffect
        if (videoPlaybackState.isLoading || videoPlaybackState.errorMessage != null) return@LaunchedEffect
        kotlinx.coroutines.delay(2800)
        videoControlsVisible = false
    }
    fun revealVideoControls() {
        videoControlsVisible = true
        videoControlsActivityNonce += 1
    }
    fun toggleVideoControlsFromVideoArea() {
        videoControlsVisible = !videoControlsVisible
        videoControlsActivityNonce += 1
    }
    val transformModifier = if (zoomState != null) {
        Modifier
            .graphicsLayer(
                transformOrigin = TransformOrigin(0f, 0f),
                scaleX = zoomState.scale,
                scaleY = zoomState.scale,
                translationX = zoomState.offset.x,
                translationY = zoomState.offset.y,
            )
    } else {
        Modifier
    }
    val topPadding by animateDpAsState(
        targetValue = if (immersive) {
            SystemViewerLayoutTuning.immersiveCanvasTopPadding
        } else {
            SystemViewerLayoutTuning.canvasTopPadding
        },
        label = "systemViewerCanvasTopPadding",
    )
    val bottomPadding by animateDpAsState(
        targetValue = if (immersive) {
            if (item.type == SystemMediaType.VIDEO) {
                SystemViewerLayoutTuning.immersiveVideoBottomExitZone
            } else {
                SystemViewerLayoutTuning.immersiveCanvasBottomPadding
            }
        } else {
            SystemViewerLayoutTuning.canvasBottomPadding
        },
        label = "systemViewerCanvasBottomPadding",
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = SystemViewerLayoutTuning.canvasHorizontalPadding,
                top = topPadding,
                end = SystemViewerLayoutTuning.canvasHorizontalPadding,
                bottom = bottomPadding,
            )
            .onSizeChanged { containerSize = it },
        contentAlignment = Alignment.Center,
    ) {
        val mediaAspectRatio = item.systemViewerAspectRatio()
        val useLongImageReading = item.type == SystemMediaType.IMAGE &&
            item.shouldUseSystemLongImageReading()
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
        val canvasWidth = if (item.type == SystemMediaType.VIDEO || useLongImageReading) {
            maxWidth
        } else {
            fittedMediaWidth
        }
        val canvasHeight = if (item.type == SystemMediaType.VIDEO) {
            maxHeight
        } else if (useLongImageReading) {
            (maxWidth / mediaAspectRatio).coerceAtLeast(maxHeight)
        } else {
            fittedMediaHeight
        }
        val contentSize = with(density) {
            IntSize(canvasWidth.roundToPx(), canvasHeight.roundToPx())
        }
        val longImageScrollState = rememberScrollState()
        LaunchedEffect(item.id, useLongImageReading) {
            if (useLongImageReading) {
                longImageScrollState.scrollTo(0)
            }
        }
        val containerSizePx = with(density) {
            IntSize(maxWidth.roundToPx(), maxHeight.roundToPx())
        }
        val longImageMinimumScale = if (useLongImageReading &&
            contentSize.width > 0 &&
            contentSize.height > 0 &&
            containerSizePx.width > 0 &&
            containerSizePx.height > 0
        ) {
            min(
                containerSizePx.width.toFloat() / contentSize.width.toFloat(),
                containerSizePx.height.toFloat() / contentSize.height.toFloat(),
            ).coerceIn(0.05f, MinSystemViewerScale)
        } else {
            MinSystemViewerScale
        }
        val contentTopLeft = Offset(
            x = ((containerSizePx.width - contentSize.width) / 2f),
            y = if (useLongImageReading) {
                -longImageScrollState.value.toFloat()
            } else {
                ((containerSizePx.height - contentSize.height) / 2f)
            },
        )
        zoomState?.updateContentGeometry(
            contentSize = contentSize,
            contentTopLeft = contentTopLeft,
            minimumScale = longImageMinimumScale,
        )
        val gestureModifier = if (zoomState != null) {
            Modifier.systemViewerZoomGesture(
                zoomState = zoomState,
                contentSize = contentSize,
                contentTopLeft = contentTopLeft,
                onDoubleTap = { position, size ->
                    zoomState.toggleDoubleTap(
                        tapPosition = position,
                        containerSize = size,
                    )
                },
            )
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier),
            contentAlignment = if (useLongImageReading) Alignment.TopCenter else Alignment.Center,
        ) {
            when (item.type) {
                SystemMediaType.IMAGE -> {
                    if (useLongImageReading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(
                                    state = longImageScrollState,
                                    enabled = zoomState?.isZoomed != true,
                                ),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = item.displayName,
                                modifier = Modifier
                                    .width(canvasWidth)
                                    .height(canvasHeight)
                                    .then(transformModifier),
                                contentScale = ContentScale.FillWidth,
                            )
                        }
                    } else {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = item.displayName,
                            modifier = Modifier
                                .width(canvasWidth)
                                .height(canvasHeight)
                                .then(transformModifier),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }

                SystemMediaType.VIDEO -> {
                    Box(
                        modifier = Modifier
                            .width(canvasWidth)
                            .height(canvasHeight),
                    ) {
                        val revealInteractionSource = remember(item.id) { MutableInteractionSource() }
                        SystemMediaViewerVideoCanvas(
                            item = item,
                            isCurrent = isCurrent,
                            playbackState = videoPlaybackState,
                            retryVersion = videoRetryVersion,
                            onPlaybackStateChange = { videoPlaybackState = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .then(transformModifier),
                        )
                        if (videoPlaybackState.errorMessage == null) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable(
                                        interactionSource = revealInteractionSource,
                                        indication = androidx.compose.foundation.LocalIndication.current,
                                        onClick = { toggleVideoControlsFromVideoArea() },
                                    ),
                            )
                        }
                        if (videoControlsVisible) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .clickable {
                                        revealVideoControls()
                                        val durationMillis = videoPlaybackState.durationMillis ?: 0L
                                        val shouldRestart = videoPlaybackState.isCompleted ||
                                            durationMillis > 0L &&
                                            videoPlaybackState.progressMillis >= durationMillis
                                        videoPlaybackState = if (videoPlaybackState.errorMessage != null) {
                                            videoPlaybackState.retryState()
                                        } else if (videoPlaybackState.isPlaying) {
                                            videoPlaybackState.copy(isPlaying = false)
                                        } else {
                                            videoPlaybackState.copy(
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
                                shape = CircleShape,
                                color = colors.viewerSurface.copy(alpha = if (videoPlaybackState.isPlaying) 0.74f else 0.82f),
                                border = BorderStroke(1.dp, colors.viewerAccent.copy(alpha = 0.22f)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .padding(22.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    VideoGlyph(
                                        state = if (videoPlaybackState.isPlaying) VideoGlyphState.PAUSE else VideoGlyphState.PLAY,
                                        tint = colors.viewerText,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                        if (videoControlsVisible) {
                            SystemMediaVideoControls(
                            playbackState = videoPlaybackState,
                            onTogglePlayback = {
                                revealVideoControls()
                                val durationMillis = videoPlaybackState.durationMillis ?: 0L
                                val shouldRestart = videoPlaybackState.isCompleted ||
                                    durationMillis > 0L &&
                                    videoPlaybackState.progressMillis >= durationMillis
                                videoPlaybackState = if (videoPlaybackState.errorMessage != null) {
                                    videoPlaybackState.retryState()
                                } else if (videoPlaybackState.isPlaying) {
                                    videoPlaybackState.copy(isPlaying = false)
                                } else {
                                    videoPlaybackState.copy(
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
                                val durationMillis = (videoPlaybackState.durationMillis ?: 0L).coerceAtLeast(0L)
                                val targetMillis = progressMillis.coerceIn(0L, durationMillis)
                                videoPlaybackState = videoPlaybackState.copy(
                                    progressMillis = targetMillis,
                                    seekRequestMillis = targetMillis,
                                    seekRequestNonce = videoPlaybackState.seekRequestNonce + 1,
                                    errorMessage = null,
                                    isCompleted = false,
                                )
                            },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 16.dp, bottom = 18.dp, end = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemMediaViewerVideoCanvas(
    item: SystemMediaItem,
    isCurrent: Boolean,
    playbackState: ViewerVideoPlaybackState,
    retryVersion: Int,
    onPlaybackStateChange: (ViewerVideoPlaybackState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val videoViewRef = remember(item.id) { mutableStateOf<VideoView?>(null) }
    val videoThumbnail = rememberSystemVideoThumbnail(LocalContext.current, item.uri)
    var isPrepared by remember(item.id, retryVersion, playbackState.retryRequestNonce) { mutableStateOf(false) }

    LaunchedEffect(item.id, retryVersion) {
        onPlaybackStateChange(ViewerVideoPlaybackState(isLoading = true))
    }

    DisposableEffect(item.id, retryVersion, playbackState.retryRequestNonce) {
        onDispose {
            videoViewRef.value?.pause()
            videoViewRef.value?.stopPlayback()
            videoViewRef.value = null
        }
    }

    LaunchedEffect(playbackState.seekRequestNonce, isPrepared) {
        val targetMillis = playbackState.seekRequestMillis ?: return@LaunchedEffect
        val videoView = videoViewRef.value ?: return@LaunchedEffect
        if (isPrepared) {
            videoView.seekTo(targetMillis.toInt().coerceAtLeast(0))
        }
    }

    DisposableEffect(isCurrent) {
        if (!isCurrent) {
            videoViewRef.value?.pause()
            onPlaybackStateChange(playbackState.copy(isPlaying = false))
        }
        onDispose { }
    }

    LaunchedEffect(isCurrent, playbackState.isPlaying, playbackState.errorMessage) {
        while (isCurrent && playbackState.errorMessage == null) {
            val videoView = videoViewRef.value
            if (videoView != null && isPrepared) {
                onPlaybackStateChange(
                    playbackState.copy(
                        progressMillis = videoView.currentPosition.toLong().coerceAtLeast(0L),
                        durationMillis = videoView.duration.toLong().takeIf { it > 0 }
                            ?: playbackState.durationMillis,
                    ),
                )
            }
            kotlinx.coroutines.delay(300)
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        SystemMediaVideoPlaceholder(
            message = when {
                playbackState.errorMessage != null -> "视频加载失败"
                playbackState.isLoading -> "视频准备中"
                else -> "正在显示视频封面"
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (videoThumbnail != null) {
            Image(
                bitmap = videoThumbnail.toComposeBitmap(),
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        key(retryVersion, playbackState.retryRequestNonce) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setVideoURI(item.uri)
                    setOnPreparedListener { player ->
                        player.isLooping = false
                        isPrepared = true
                        onPlaybackStateChange(
                            playbackState.copy(
                                isLoading = false,
                                errorMessage = null,
                                isCompleted = false,
                                durationMillis = duration.toLong().takeIf { it > 0 },
                            ),
                        )
                        if (isCurrent && playbackState.isPlaying) {
                            start()
                        }
                    }
                    setOnErrorListener { _, _, _ ->
                        isPrepared = false
                        onPlaybackStateChange(
                            playbackState.copy(
                                isPlaying = false,
                                isLoading = false,
                                errorMessage = "视频加载失败，请重试",
                                isCompleted = false,
                            ),
                        )
                        true
                    }
                    setOnCompletionListener {
                        onPlaybackStateChange(
                            playbackState.copy(
                                isPlaying = false,
                                isCompleted = true,
                                progressMillis = duration.toLong().takeIf { it > 0 }
                                    ?: playbackState.progressMillis,
                            ),
                        )
                    }
                    videoViewRef.value = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { videoView ->
                videoViewRef.value = videoView
                videoView.alpha = if (playbackState.isPlaying) 1f else 0f
                if (isCurrent && playbackState.errorMessage == null) {
                    if (playbackState.isPlaying && isPrepared && !videoView.isPlaying) {
                        videoView.start()
                    } else if (!playbackState.isPlaying && videoView.isPlaying) {
                        videoView.pause()
                    }
                } else if (videoView.isPlaying) {
                    videoView.pause()
                }
            },
        )
        }

        if (playbackState.isLoading) {
            val colors = YingShiThemeTokens.colors
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp),
                color = colors.viewerAccent,
                strokeWidth = 2.dp,
            )
        }

        if (playbackState.errorMessage != null) {
            val colors = YingShiThemeTokens.colors
            Text(
                text = playbackState.errorMessage,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(
                        color = colors.viewerBackground.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.viewerText,
            )
        }
    }
}

private fun SystemMediaItem.shouldUseSystemLongImageReading(): Boolean {
    if (type != SystemMediaType.IMAGE) return false
    val widthValue = width
    val heightValue = height
    if (widthValue != null && heightValue != null && widthValue > 0 && heightValue > 0) {
        return heightValue.toFloat() / widthValue.toFloat() >= SystemLongImageHeightWidthRatioThreshold
    }
    val normalizedAspectRatio = aspectRatio.takeIf { it > 0f } ?: return false
    return 1f / normalizedAspectRatio >= SystemLongImageHeightWidthRatioThreshold
}

private fun SystemMediaItem.systemViewerAspectRatio(): Float {
    val widthValue = width
    val heightValue = height
    if (widthValue != null && heightValue != null && widthValue > 0 && heightValue > 0) {
        return (widthValue.toFloat() / heightValue.toFloat()).coerceIn(0.05f, 20f)
    }
    return aspectRatio.coerceIn(0.05f, 20f)
}

@Composable
private fun SystemMediaViewerAtmosphereLayer(
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Box(modifier = modifier.background(colors.viewerBackground)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.viewerAccent.copy(alpha = 0.18f),
                            colors.viewerSurface.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                        center = Offset(0f, 0f),
                        radius = 980f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.primaryContainer.copy(alpha = 0.12f),
                            colors.viewerBackground.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                        center = Offset(1180f, 2140f),
                        radius = 920f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.viewerSurface.copy(alpha = 0.08f),
                            Color.Transparent,
                            colors.viewerBackground.copy(alpha = 0.34f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun SystemMediaVideoPlaceholder(
    message: String,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier,
        color = colors.viewerBackground,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = colors.viewerSurface.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, colors.viewerAccent.copy(alpha = 0.14f)),
                ) {
                    Box(
                        modifier = Modifier.padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        VideoGlyph(
                            state = VideoGlyphState.PLAY,
                            tint = colors.viewerText,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.viewerTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun SystemMediaViewerInfoCard(
    item: SystemMediaItem,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = colors.viewerSurface.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, colors.viewerAccent.copy(alpha = 0.14f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.type.label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.viewerText,
            )
            Text(
                text = formatSystemViewerDisplayTime(item.displayTimeMillis),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = colors.viewerTextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                text = if (item.isImportedToApp) "已导入" else "未导入",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (item.isImportedToApp) colors.viewerAccent else colors.viewerTextSecondary,
            )
        }
    }
}

@Composable
private fun SystemMediaVideoControls(
    playbackState: ViewerVideoPlaybackState,
    onTogglePlayback: () -> Unit,
    onSeekPlayback: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val durationMillis = (playbackState.durationMillis ?: 0L).coerceAtLeast(0L)
    val progressFraction = if (durationMillis <= 0L) 0f else {
        (playbackState.progressMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
    }
    var draggedFraction by remember(playbackState.mediaId) { mutableStateOf<Float?>(null) }
    val displayedFraction = draggedFraction ?: progressFraction
    val displayedProgressMillis = if (durationMillis <= 0L) {
        0L
    } else {
        (displayedFraction * durationMillis).toLong().coerceIn(0L, durationMillis)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = colors.viewerBackground.copy(alpha = 0.86f),
        border = BorderStroke(1.dp, colors.viewerAccent.copy(alpha = 0.14f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = colors.viewerSurface.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, colors.viewerAccent.copy(alpha = 0.18f)),
                onClick = onTogglePlayback,
            ) {
                Box(
                    modifier = Modifier.padding(11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    VideoGlyph(
                        state = if (playbackState.isPlaying) {
                            VideoGlyphState.PAUSE
                        } else {
                            VideoGlyphState.PLAY
                        },
                        tint = colors.viewerText,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = "${formatVideoProgress(displayedProgressMillis)} / ${formatVideoProgress(durationMillis)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.viewerTextSecondary,
                )
                Slider(
                    value = displayedFraction,
                    onValueChange = { draggedFraction = it.coerceIn(0f, 1f) },
                    onValueChangeFinished = {
                        val targetFraction = draggedFraction ?: displayedFraction
                        val targetMillis = if (durationMillis <= 0L) {
                            0L
                        } else {
                            (targetFraction * durationMillis).toLong().coerceIn(0L, durationMillis)
                        }
                        draggedFraction = null
                        onSeekPlayback(targetMillis)
                    },
                    enabled = durationMillis > 0L && playbackState.errorMessage == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = colors.viewerAccent,
                        activeTrackColor = colors.viewerAccent,
                        inactiveTrackColor = colors.viewerSurface.copy(alpha = 0.96f),
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SystemMediaViewerMenuSheet(
    itemImported: Boolean,
    onDismiss: () -> Unit,
    onImportToApp: () -> Unit,
    onAddToPost: () -> Unit,
    onMoveToTrash: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.viewerSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = "媒体操作",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.viewerText,
            )
            SystemMediaViewerMenuAction(
                title = if (itemImported) "已导入照片流" else "导入照片流",
                enabled = !itemImported,
                onClick = onImportToApp,
            )
            SystemMediaViewerMenuAction(
                title = "加入已有小相册",
                onClick = onAddToPost,
            )
            SystemMediaViewerMenuAction(
                title = "移到系统回收站",
                danger = true,
                onClick = onMoveToTrash,
            )
        }
    }
}

@Composable
private fun SystemMediaViewerMenuAction(
    title: String,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = if (danger) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.06f)
        } else {
            colors.viewerBackground.copy(alpha = 0.82f)
        },
        enabled = enabled,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .alpha(if (enabled) 1f else 0.42f)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (danger) MaterialTheme.colorScheme.error else colors.viewerText,
            )
        }
    }
}

@Composable
private fun SystemMediaViewerCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = colors.viewerSurface.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, colors.viewerAccent.copy(alpha = 0.14f)),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colors.viewerText,
            )
        }
    }
}

private fun formatVideoProgress(timeMillis: Long): String {
    val totalSeconds = (timeMillis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(Locale.ROOT, minutes, seconds)
}

private fun formatSystemViewerDisplayTime(timeMillis: Long?): String {
    if (timeMillis == null || timeMillis <= 0L) return "时间未知"
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}

@Preview(showBackground = true)
@Composable
private fun SystemMediaViewerScreenPreview() {
    YingShiTheme {
        SystemMediaViewerScreen(
            route = SystemMediaViewerRoute(
                mediaItems = emptyList(),
                initialIndex = 0,
            ),
            onBack = {},
        )
    }
}
