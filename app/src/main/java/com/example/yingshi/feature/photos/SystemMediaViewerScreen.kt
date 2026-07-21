package com.example.yingshi.feature.photos

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.components.YingShiNotice
import com.example.yingshi.ui.components.YingShiNoticeHost
import com.example.yingshi.ui.components.YingShiNoticeTone
import com.example.yingshi.ui.components.rememberYingShiMotionEnabled
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.launch

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
    val motion = YingShiThemeTokens.motion
    val motionEnabled = rememberYingShiMotionEnabled()
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
    val zoomState = remember { ViewerZoomState() }
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
                enter = fadeIn(tween(if (motionEnabled) motion.floatingMillis else 0, easing = motion.easing)) +
                    slideInVertically(
                        animationSpec = tween(if (motionEnabled) motion.floatingMillis else 0, easing = motion.easing),
                        initialOffsetY = { -it / 6 },
                    ),
                exit = fadeOut(tween(if (motionEnabled) motion.floatingMillis else 0, easing = motion.easing)) +
                    slideOutVertically(
                        animationSpec = tween(if (motionEnabled) motion.floatingMillis else 0, easing = motion.easing),
                        targetOffsetY = { -it / 6 },
                    ),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    SystemMediaViewerTopScrim(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(124.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
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
                }
            }

            AnimatedVisibility(
                visible = !isImmersive && !zoomState.isZoomed,
                enter = fadeIn(tween(if (motionEnabled) motion.floatingMillis else 0, easing = motion.easing)) +
                    slideInVertically(
                        animationSpec = tween(if (motionEnabled) motion.floatingMillis else 0, easing = motion.easing),
                        initialOffsetY = { it / 6 },
                    ),
                exit = fadeOut(tween(if (motionEnabled) motion.floatingMillis else 0, easing = motion.easing)) +
                    slideOutVertically(
                        animationSpec = tween(if (motionEnabled) motion.floatingMillis else 0, easing = motion.easing),
                        targetOffsetY = { it / 6 },
                    ),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    SystemMediaViewerBottomScrim(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(188.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = YingShiThemeTokens.spacing.lg, vertical = YingShiThemeTokens.spacing.md),
                    ) {
                        SystemMediaViewerInfoCard(item = currentItem)
                    }
                }
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
