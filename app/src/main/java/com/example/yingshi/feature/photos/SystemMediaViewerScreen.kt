package com.example.yingshi.feature.photos

import android.app.Activity
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
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
    var pendingTrashIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val uploadTasks = LocalSystemMediaBridgeRepository.uploadTasks
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
                onDismiss = { showAddToPostDialog = false },
                onPostSelected = { postId ->
                    val addedCount = LocalSystemMediaBridgeRepository.enqueueAddToExistingPostUpload(
                        context = context,
                        postId = postId,
                        mediaItems = listOf(item),
                    )
                    showAddToPostDialog = false
                    Toast.makeText(
                        context,
                        if (addedCount > 0) {
                            "已加入已有帖子，并同步刷新相关页面。"
                        } else {
                            "该媒体已经在目标帖子里了。"
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
    }

    DisposableEffect(currentItem?.id) {
        zoomState.reset()
        onDispose { }
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
                onBack = onBack,
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

        if (uploadTasks.isNotEmpty()) {
            SystemMediaUploadTaskPanel(
                tasks = uploadTasks,
                onCancelTask = LocalSystemMediaBridgeRepository::cancelUploadTask,
                onDismissTask = LocalSystemMediaBridgeRepository::dismissUploadTask,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = YingShiThemeTokens.spacing.lg, vertical = YingShiThemeTokens.spacing.md),
            )
        }
    }

    if (showMenuSheet && currentItem != null) {
        SystemMediaViewerMenuSheet(
            onDismiss = { showMenuSheet = false },
            onCreatePost = {
                showMenuSheet = false
                val createdCount = LocalSystemMediaBridgeRepository.enqueueCreatePostUpload(
                    context = context,
                    mediaItems = listOf(currentItem),
                )
                Toast.makeText(
                    context,
                    if (createdCount > 0) {
                        "已发成新帖子，并同步刷新照片与相册页。"
                    } else {
                        "当前媒体无法处理。"
                    },
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onAddToPost = {
                showMenuSheet = false
                if (destinationUiState.errorMessage != null && posts.isEmpty()) {
                    Toast.makeText(context, destinationUiState.errorMessage, Toast.LENGTH_SHORT).show()
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
                    SystemMediaViewerVideoCanvas(
                        item = item,
                        isCurrent = isCurrent,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(transformModifier),
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemMediaViewerVideoCanvas(
    item: SystemMediaItem,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
) {
    var isPlaying by remember(item.id) { mutableStateOf(false) }
    val videoViewRef = remember(item.id) { mutableStateOf<VideoView?>(null) }
    val context = LocalContext.current
    val videoThumbnail = rememberSystemVideoThumbnail(context, item.uri)

    DisposableEffect(item.id) {
        onDispose {
            videoViewRef.value?.pause()
            videoViewRef.value?.stopPlayback()
            videoViewRef.value = null
        }
    }

    DisposableEffect(isCurrent) {
        if (!isCurrent) {
            videoViewRef.value?.pause()
            isPlaying = false
        }
        onDispose { }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (videoThumbnail != null) {
            Image(
                bitmap = videoThumbnail.toComposeBitmap(),
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            AsyncImage(
                model = item.uri,
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setVideoURI(item.uri)
                    setOnPreparedListener { player ->
                        player.isLooping = true
                    }
                    videoViewRef.value = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { videoView ->
                videoViewRef.value = videoView
                videoView.alpha = if (isPlaying) 1f else 0f
                if (isCurrent) {
                    if (isPlaying && !videoView.isPlaying) {
                        videoView.start()
                    } else if (!isPlaying && videoView.isPlaying) {
                        videoView.pause()
                    }
                } else if (videoView.isPlaying) {
                    videoView.pause()
                }
            },
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
            color = Color.Black.copy(alpha = 0.42f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            onClick = {
                val next = !isPlaying
                isPlaying = next
                if (next) {
                    videoViewRef.value?.start()
                } else {
                    videoViewRef.value?.pause()
                }
            },
        ) {
            Text(
                text = if (isPlaying) "暂停视频" else "播放视频",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SystemMediaViewerMenuSheet(
    onDismiss: () -> Unit,
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
                title = "发成新帖子",
                subtitle = "把当前系统媒体发成一个新的 app 帖子。",
                onClick = onCreatePost,
            )
            SystemMediaViewerMenuAction(
                title = "加入已有帖子",
                subtitle = "选择已有相册和帖子，把当前媒体加入进去。",
                onClick = onAddToPost,
            )
            SystemMediaViewerMenuAction(
                title = "移到系统回收站",
                subtitle = "走 Android 系统确认流程，不进入 app 回收站。",
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
