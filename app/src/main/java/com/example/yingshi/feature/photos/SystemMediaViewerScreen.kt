package com.example.yingshi.feature.photos

import android.app.Activity
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private const val MinSystemViewerScale = 1f
private const val MaxSystemViewerScale = 4f
private const val SystemViewerResetScale = 1.02f

private class SystemViewerZoomState {
    var scale by mutableStateOf(MinSystemViewerScale)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    val isZoomed: Boolean
        get() = scale > SystemViewerResetScale

    fun reset() {
        scale = MinSystemViewerScale
        offset = Offset.Zero
    }

    fun applyTransform(
        zoomChange: Float,
        panChange: Offset,
        containerSize: IntSize,
    ) {
        val nextScale = (scale * zoomChange).coerceIn(MinSystemViewerScale, MaxSystemViewerScale)
        if (nextScale <= SystemViewerResetScale) {
            reset()
            return
        }
        scale = nextScale
        val maxX = ((containerSize.width * nextScale - containerSize.width) / 2f).coerceAtLeast(0f)
        val maxY = ((containerSize.height * nextScale - containerSize.height) / 2f).coerceAtLeast(0f)
        offset = Offset(
            x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
            y = (offset.y + panChange.y).coerceIn(-maxY, maxY),
        )
    }
}

private fun Modifier.systemViewerZoomGesture(
    zoomState: SystemViewerZoomState,
    contentSize: IntSize,
): Modifier = pointerInput(zoomState, contentSize) {
    awaitEachGesture {
        while (true) {
            val event = awaitPointerEvent()
            val activeChanges = event.changes.filter { it.pressed }
            if (activeChanges.isEmpty()) break

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
                    containerSize = size,
                )
                activeChanges.forEach { it.consume() }
            } else if (zoomState.isZoomed) {
                val change = activeChanges.first()
                zoomState.applyTransform(
                    zoomChange = 1f,
                    panChange = change.positionChange(),
                    containerSize = size,
                )
                activeChanges.forEach { it.consume() }
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
    var showSystemTrashConfirm by rememberSaveable { mutableStateOf(false) }
    var pendingTrashIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val destinationUiState by rememberSystemMediaDestinationUiState()
    val albums = destinationUiState.albums
    val posts = destinationUiState.posts
    val trashLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val processedIds = pendingTrashIds
        pendingTrashIds = emptyList()
        if (processedIds.isEmpty()) return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            val hiddenCount = LocalSystemMediaBridgeRepository.markMovedToSystemTrash(processedIds)
            val nextItems = viewerItems.filterNot { processedIds.contains(it.id) }
            Toast.makeText(
                context,
                if (hiddenCount > 0) {
                    "已移到系统回收站。"
                } else {
                    "这些媒体已经处理过了。"
                },
                Toast.LENGTH_SHORT,
            ).show()
            if (nextItems.isEmpty()) {
                onBack()
            } else {
                viewerItems = nextItems
                coroutineScope.launch {
                    pagerState.scrollToPage(currentIndex.coerceAtMost(nextItems.lastIndex))
                }
            }
        } else {
            Toast.makeText(context, "已取消移到系统回收站。", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(
                        context,
                        throwable.message ?: "无法拉起系统回收站确认流程。",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            .onFailure { throwable ->
                Toast.makeText(
                    context,
                    throwable.message ?: systemMediaTrashUnsupportedMessage(),
                    Toast.LENGTH_SHORT,
                ).show()
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
                        Toast.makeText(
                            context,
                            "已加入上传队列，成功后会进入目标小相册。",
                            Toast.LENGTH_SHORT,
                        ).show()
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
                        Toast.makeText(
                            context,
                            "已加入上传队列，成功后会进入目标小相册。",
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        addToPostError = "该媒体已经在目标小相册里，或没有可添加的媒体。"
                    }
                },
            )
        }

        if (showSystemTrashConfirm) {
            AlertDialog(
                onDismissRequest = { showSystemTrashConfirm = false },
                title = { Text("移到系统相册回收站？") },
                text = {
                    Text(
                        "这是系统相册操作：当前媒体会交给 Android 系统回收站处理，不会进入 App 回收站，也不会删除后端 App 媒体记录；确认后还会出现 Android 系统确认框。",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSystemTrashConfirm = false
                            launchSystemTrashRequest(item)
                        },
                    ) {
                        Text("继续系统回收站")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSystemTrashConfirm = false }) {
                        Text("取消")
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
        onBack()
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0E131A)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = YingShiThemeTokens.spacing.lg, vertical = YingShiThemeTokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.md),
        ) {
            SystemMediaViewerTopBar(
                currentIndex = currentIndex,
                totalCount = viewerItems.size,
                showMenu = currentItem != null,
                overlaysVisible = !zoomState.isZoomed,
                onBack = {
                    onBack()
                },
                onOpenMenu = { showMenuSheet = true },
            )

            if (viewerItems.isEmpty() || currentItem == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "当前没有可查看的系统媒体。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                }
            } else {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
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
                            zoomState = if (page == currentIndex) zoomState else null,
                        )
                    }
                }

                SystemMediaViewerInfoCard(item = currentItem)
            }
        }

    }

    if (showMenuSheet && currentItem != null) {
        SystemMediaViewerMenuSheet(
            onDismiss = { showMenuSheet = false },
            onImportToApp = {
                showMenuSheet = false
                val queuedCount = LocalSystemMediaBridgeRepository.enqueueImportToAppUpload(
                    context = context,
                    mediaItems = listOf(currentItem),
                )
                Toast.makeText(
                    context,
                    if (queuedCount > 0) {
                        "已加入导入 app 队列。"
                    } else {
                        "当前媒体无法导入 app。"
                    },
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onCreatePost = {
                showMenuSheet = false
                onOpenCreatePost(
                    CreatePostRoute(
                        source = "system-media-viewer",
                        initialMediaItems = listOf(currentItem),
                    ),
                )
            },
            onAddToPost = {
                addToPostError = null
                showMenuSheet = false
                if (destinationUiState.errorMessage != null && posts.isEmpty()) {
                    Toast.makeText(context, destinationUiState.errorMessage, Toast.LENGTH_SHORT).show()
                } else {
                    showAddToPostDialog = true
                }
            },
            onMoveToTrash = {
                showMenuSheet = false
                showSystemTrashConfirm = true
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (overlaysVisible) 1f else 0.35f),
        horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SystemMediaViewerCircleButton(
            text = "<",
            onClick = onBack,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "系统媒体",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
            Text(
                text = if (totalCount > 0) "${currentIndex + 1} / $totalCount" else "0 / 0",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
            )
        }
        if (showMenu) {
            SystemMediaViewerCircleButton(
                text = "≡",
                onClick = onOpenMenu,
            )
        }
    }
}

@Composable
private fun SystemMediaViewerCanvas(
    item: SystemMediaItem,
    isCurrent: Boolean,
    zoomState: SystemViewerZoomState?,
) {
    var containerSize by remember(item.id) { mutableStateOf(IntSize.Zero) }
    var videoPlaybackState by remember(item.id) {
        mutableStateOf(ViewerVideoPlaybackState())
    }
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
                scaleX = zoomState.scale,
                scaleY = zoomState.scale,
                translationX = zoomState.offset.x,
                translationY = zoomState.offset.y,
            )
    } else {
        Modifier
    }
    val gestureModifier = if (zoomState != null) {
        Modifier.systemViewerZoomGesture(
            zoomState = zoomState,
            contentSize = containerSize,
        )
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 4.dp, bottom = 8.dp)
            .background(Color.Black, RoundedCornerShape(28.dp))
            .padding(8.dp)
            .graphicsLayer { clip = true; shape = RoundedCornerShape(24.dp) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .graphicsLayer { clip = true }
                .then(gestureModifier)
                .then(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { containerSize = it },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (item.type) {
                SystemMediaType.IMAGE -> {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.displayName,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(transformModifier),
                        contentScale = ContentScale.Fit,
                    )
                }

                SystemMediaType.VIDEO -> {
                    Box(modifier = Modifier.fillMaxSize()) {
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
                                        indication = null,
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
                                color = Color.White.copy(alpha = if (videoPlaybackState.isPlaying) 0.14f else 0.18f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .padding(22.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    VideoGlyph(
                                        state = if (videoPlaybackState.isPlaying) VideoGlyphState.PAUSE else VideoGlyphState.PLAY,
                                        tint = Color.White.copy(alpha = 0.92f),
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
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp),
                color = Color.White.copy(alpha = 0.88f),
                strokeWidth = 2.dp,
            )
        }

        if (playbackState.errorMessage != null) {
            Text(
                text = playbackState.errorMessage,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(
                        color = Color.Black.copy(alpha = 0.42f),
                        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun SystemMediaVideoPlaceholder(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF12161D),
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
                    color = Color.White.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                ) {
                    Box(
                        modifier = Modifier.padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        VideoGlyph(
                            state = VideoGlyphState.PLAY,
                            tint = Color.White.copy(alpha = 0.88f),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.74f),
                )
            }
        }
    }
}

@Composable
private fun SystemMediaViewerInfoCard(
    item: SystemMediaItem,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(YingShiThemeTokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
        ) {
            Text(
                text = item.displayName.ifBlank { "未命名媒体" },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
            Text(
                text = "类型：${item.type.label}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.76f),
            )
            Text(
                text = "日期：${formatSystemMediaViewerTime(item.displayTimeMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.76f),
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
        color = Color.Black.copy(alpha = 0.48f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
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
                        tint = Color.White.copy(alpha = 0.92f),
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
                    color = Color.White.copy(alpha = 0.70f),
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
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SystemMediaViewerMenuSheet(
    onDismiss: () -> Unit,
    onImportToApp: () -> Unit,
    onCreatePost: () -> Unit,
    onAddToPost: () -> Unit,
    onMoveToTrash: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
                color = MaterialTheme.colorScheme.onSurface,
            )
            SystemMediaViewerMenuAction(
                title = "导入app",
                subtitle = "只导入到 app 照片流，不要求归属到小相册。",
                onClick = onImportToApp,
            )
            SystemMediaViewerMenuAction(
                title = "新建小相册",
                subtitle = "把当前系统媒体整理成一个新的 app 小相册。",
                onClick = onCreatePost,
            )
            SystemMediaViewerMenuAction(
                title = "加入已有小相册",
                subtitle = "选择已有大相册和小相册，把当前媒体加入进去。",
                onClick = onAddToPost,
            )
            SystemMediaViewerMenuAction(
                title = "移到系统回收站",
                subtitle = "系统相册操作：走 Android 系统确认流程，不进入 App 回收站。",
                danger = true,
                onClick = onMoveToTrash,
            )
        }
    }
}

@Composable
private fun SystemMediaViewerMenuAction(
    title: String,
    subtitle: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = if (danger) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.06f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
        },
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SystemMediaViewerCircleButton(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
        }
    }
}

private fun formatSystemMediaViewerTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}

private fun formatVideoProgress(timeMillis: Long): String {
    val totalSeconds = (timeMillis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(Locale.ROOT, minutes, seconds)
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
