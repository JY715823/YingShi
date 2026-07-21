package com.example.yingshi.feature.photos

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.connectivity.NetworkConnectivityMonitor
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.life.LifeLocationPickerActivity
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.launch

@Composable
fun PhotoViewerScreen(
    route: PhotoViewerRoute,
    onBack: () -> Unit,
    onOpenPostDetail: (PostDetailPlaceholderRoute) -> Unit = {},
    onOpenCreatePost: (CreatePostRoute) -> Unit = {},
    onOpenCacheManagement: (CacheManagementRoute) -> Unit = {},
    onRouteSnapshotChange: (PhotoViewerRoute) -> Unit = {},
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
    val networkState by NetworkConnectivityMonitor.state.collectAsState()
    val sessionVersion = AuthSessionManager.sessionVersion
    val viewerAccessToken = remember(sessionVersion) {
        AuthSessionManager.peekAccessToken()?.takeIf { it.isNotBlank() }
    }
    val settingsState = SettingsRepository.getSettingsState()
    val collaboratorDirectory = rememberCollaboratorDirectorySnapshot()
    var viewerItems by remember(route) {
        mutableStateOf(route.mediaItems)
    }
    val initialPage = route.initialIndex.coerceIn(0, viewerItems.lastIndex)
    val zoomState = remember { ViewerZoomState() }
    val heroOrigin = route.heroOrigin
    val heroProgress = remember { Animatable(if (heroOrigin != null) 0f else 1f) }
    var heroExiting by remember { mutableStateOf(false) }
    LaunchedEffect(route, heroExiting) {
        if (heroOrigin != null && !heroExiting && heroProgress.value < 1f) {
            heroProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(HeroTransitionMillis, easing = HeroEasing),
            )
        }
    }
    val handleBack: () -> Unit = {
        if (heroOrigin != null && !heroExiting && heroProgress.value >= 1f) {
            heroExiting = true
            coroutineScope.launch {
                heroProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(HeroTransitionMillis, easing = HeroEasing),
                )
                onBack()
                heroExiting = false
            }
        } else {
            onBack()
        }
    }
    var isImmersive by remember { mutableStateOf(false) }
    // Round 8 第十七轮: 缩放状态下单击切换 overlay 可见性的 override 标记.
    // 当 zoomed + hideOverlaysWhenZoomed 时, overlaysVisible=false 导致 appOverlaysVisible=false,
    // 单击只能 toggle isImmersive, 但 appOverlaysVisible 仍为 false, overlay 无法恢复.
    // 现在缩放状态下单击改 toggle forceShowOverlays, 让用户能临时呼出 chrome.
    var forceShowOverlays by remember { mutableStateOf(false) }
    var showCommentPreview by remember { mutableStateOf(false) }
    var commentPanelState by remember { mutableStateOf<ViewerCommentPanelState?>(null) }
    var showRelatedPostsSheet by remember { mutableStateOf(false) }
    var openCommentComposerOnSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showTimeEditorSheet by remember { mutableStateOf(false) }
    var shareInFlight by remember { mutableStateOf(false) }
    var viewerNotice by remember { mutableStateOf<ViewerNotice?>(null) }
    var viewerNoticeNonce by remember { mutableIntStateOf(0) }
    var videoPlaybackState by remember {
        mutableStateOf(ViewerVideoPlaybackState())
    }
    var videoPlaybackStateCache by remember { mutableStateOf<Map<String, ViewerVideoPlaybackState>>(emptyMap()) }
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
    LaunchedEffect(route, viewerItems, currentIndex) {
        onRouteSnapshotChange(
            route.copy(
                mediaItems = viewerItems,
                initialIndex = currentIndex,
            ),
        )
    }

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
    val autoPauseVideoOnMediaSwitch = settingsState.viewerPreferences.autoPauseVideoOnMediaSwitch
    // Round 8 第十七轮: forceShowOverlays 让缩放状态下也能临时呼出 overlay
    val overlaysVisible = !zoomState.isZoomed || !hideOverlaysWhenZoomed || forceShowOverlays
    val appOverlaysVisible = overlaysVisible && !isImmersive
    val baseOverlayAlpha = if (zoomState.isZoomed && hideOverlaysWhenZoomed && !forceShowOverlays) {
        ViewerLayoutTuning.zoomedOverlayAlpha
    } else {
        1f
    }
    val overlayAlpha = baseOverlayAlpha
    val canOpenOriginal = remember(currentItem) {
        when (RepositoryProvider.currentMode) {
            RepositoryMode.REAL -> currentItem.mediaType == AppMediaType.IMAGE &&
                currentItem.mediaSource.hasMeaningfulViewerOriginal(currentItem.mediaType)
            RepositoryMode.FAKE -> currentItem.mediaType == AppMediaType.IMAGE
        }
    }
    val originalActionLabel = if (
        RepositoryProvider.currentMode == RepositoryMode.REAL &&
        currentOriginalState == OriginalLoadState.Loading &&
        !networkState.isConnected
    ) {
        "等待网络恢复"
    } else {
        currentOriginalState.actionLabel()
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
    fun revealVideoControls() {
        videoControlsVisible = true
        videoControlsActivityNonce += 1
    }
    fun toggleImmersive() {
        // Round 8 第十七轮: 缩放状态下, 单击改 toggle forceShowOverlays 而不是 immersive.
        // 这样用户在 zoomed 状态下单击能呼出/隐藏 chrome (TopBar + EdgeActions),
        // 而不是只能切换 immersive (immersive 无法恢复 overlay, 因为 overlaysVisible=false).
        if (zoomState.isZoomed && hideOverlaysWhenZoomed) {
            forceShowOverlays = !forceShowOverlays
            // 如果之前进入了 immersive, 顺便退出 immersive 让状态栏恢复
            if (isImmersive) {
                applyViewerStatusBarVisibility(view, false)
                isImmersive = false
            }
            if (!forceShowOverlays) {
                showCommentPreview = false
                commentPanelState = null
                showRelatedPostsSheet = false
                showTimeEditorSheet = false
                videoControlsVisible = false
            }
            return
        }
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
    // Round 8 第十六轮: 地点胶囊点击跳地图页 (复用 LifeLocationPickerActivity), 返回后调用服务端更新
    val locationPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data ?: return@rememberLauncherForActivityResult
        val lat = data.getDoubleExtra(LifeLocationPickerActivity.EXTRA_RESULT_LAT, Double.NaN)
        val lng = data.getDoubleExtra(LifeLocationPickerActivity.EXTRA_RESULT_LNG, Double.NaN)
        val label = data.getStringExtra(LifeLocationPickerActivity.EXTRA_RESULT_LABEL)
        val targetMediaId = currentItem.mediaId
        val safeLat = if (lat.isNaN()) null else lat
        val safeLng = if (lng.isNaN()) null else lng
        coroutineScope.launch {
            when (RepositoryProvider.currentMode) {
                RepositoryMode.REAL -> {
                    val res = RepositoryProvider.lifeConsoleRepository.updateMediaLocation(
                        mediaId = targetMediaId,
                        latitude = safeLat,
                        longitude = safeLng,
                        locationLabel = label,
                    )
                    when (res) {
                        is ApiResult.Success -> showViewerNotice("位置已更新", emphasized = true)
                        else -> showViewerNotice("位置更新失败")
                    }
                }
                RepositoryMode.FAKE -> {
                    showViewerNotice("位置已更新", emphasized = true)
                }
            }
            // 本地立即更新 viewerItems, 触发 UI 重绘
            viewerItems = viewerItems.map { item ->
                if (item.mediaId == targetMediaId) {
                    item.copy(
                        locationLabel = label,
                        locationLat = safeLat,
                        locationLng = safeLng,
                    )
                } else {
                    item
                }
            }
            notifyRealBackendContentChanged(mediaIds = setOf(targetMediaId))
        }
    }
    fun openLocationPicker() {
        locationPickerLauncher.launch(
            LifeLocationPickerActivity.intent(
                context = context,
                initialLat = currentItem.locationLat,
                initialLng = currentItem.locationLng,
                initialLabel = currentItem.locationLabel,
                title = "调整照片位置",
            )
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
    var relatedPostRoutes by remember(currentItem.mediaId, route.sourcePostRoute) {
        mutableStateOf<Map<String, PostDetailPlaceholderRoute>>(emptyMap())
    }
    var relatedAlbumTitleById by remember(currentItem.mediaId, route.sourcePostRoute) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }
    LaunchedEffect(currentItem.mediaId, currentItem.postIds, route.sourcePostRoute) {
        val postIds = currentItem.postIds.distinct()
        relatedAlbumTitleById = buildCachedViewerAlbumTitleMap()
        val fallbackRoutes = postIds.associateWith { postId ->
            buildViewerRelatedPostRoute(
                media = currentItem,
                postId = postId,
                sourcePostRoute = route.sourcePostRoute,
            )
        }
        val cachedRoutes = buildCachedViewerRelatedPostRoutes(
            postIds = postIds,
            sourcePostRoute = route.sourcePostRoute,
        )
        relatedPostRoutes = fallbackRoutes + cachedRoutes
        if (RepositoryProvider.currentMode != RepositoryMode.REAL || postIds.isEmpty()) {
            return@LaunchedEffect
        }
        when (val albumResult = RepositoryProvider.albumRepository.getAlbums()) {
            is ApiResult.Success -> {
                relatedAlbumTitleById = albumResult.data.associate { it.albumId to it.title }
            }
            else -> Unit
        }
        when (val result = RepositoryProvider.postRepository.getPosts()) {
            is ApiResult.Success -> {
                val summariesById = result.data.associateBy { it.postId }
                relatedPostRoutes = postIds.associateWith { postId ->
                    val cachedOrFallback = cachedRoutes[postId] ?: fallbackRoutes.getValue(postId)
                    when {
                        route.sourcePostRoute?.postId == postId -> route.sourcePostRoute
                        else -> summariesById[postId]?.toPostDetailPlaceholderRoute(
                            selectedAlbumId = summariesById[postId]
                                ?.albumIds
                                ?.firstOrNull()
                                .orEmpty()
                                .ifBlank { route.sourcePostRoute?.albumId ?: "viewer-related" },
                        )
                    } ?: cachedOrFallback
                }
            }
            else -> Unit
        }
    }
    val relatedPosts = remember(currentItem, route.sourcePostRoute, relatedPostRoutes, relatedAlbumTitleById) {
        buildViewerRelatedPosts(
            media = currentItem,
            sourcePostRoute = route.sourcePostRoute,
            routeOverrides = relatedPostRoutes,
            albumTitleById = relatedAlbumTitleById,
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
            timeLabel = formatMediaDisplayTime(currentItem.mediaDisplayTimeMillis),
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

    LaunchedEffect(currentIndex, currentItem.mediaId, autoPauseVideoOnMediaSwitch) {
        zoomState.reset()
        forceShowOverlays = false
        showCommentPreview = false
        commentPanelState = null
        showRelatedPostsSheet = false
        showTimeEditorSheet = false
        viewerNotice = null
        openCommentComposerOnSheet = false
        videoPlaybackState = if (currentItem.mediaType == AppMediaType.VIDEO) {
            if (autoPauseVideoOnMediaSwitch) {
                ViewerVideoPlaybackState(mediaId = currentItem.mediaId)
            } else {
                videoPlaybackStateCache[currentItem.mediaId]?.copy(
                    mediaId = currentItem.mediaId,
                    errorMessage = null,
                    isLoading = false,
                ) ?: ViewerVideoPlaybackState(mediaId = currentItem.mediaId)
            }
        } else {
            ViewerVideoPlaybackState()
        }
        videoControlsVisible = true
        videoControlsActivityNonce += 1

        if (GlobalPhotoFeedPageStateStore.pendingAutoOpenComment) {
            GlobalPhotoFeedPageStateStore.pendingAutoOpenComment = false
            commentPanelState = ViewerCommentPanelState()
        }
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
    BackHandler(enabled = zoomState.isZoomed) {
        zoomState.reset()
        forceShowOverlays = false
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
        handleBack()
    }
    BackHandler(enabled = isImmersive && !zoomState.isZoomed) {
        handleBack()
    }
    ViewerStatusBarEffect(immersive = isImmersive)

    if (showDeleteConfirm) {
        ViewerDeleteConfirmDialog(
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                val deletingItem = currentItem
                when (RepositoryProvider.currentMode) {
                    RepositoryMode.FAKE -> {
                        deleteFakeViewerMedia(deletingItem)
                        val nextItems = viewerItems.filterNot { it.mediaId == deletingItem.mediaId }
                        if (nextItems.isEmpty()) {
                            handleBack()
                        } else {
                            viewerItems = nextItems
                            coroutineScope.launch {
                                pagerState.scrollToPage(currentIndex.coerceAtMost(nextItems.lastIndex))
                            }
                            showViewerNotice("已删除当前媒体，并写入回收站。", emphasized = true)
                        }
                    }
                    RepositoryMode.REAL -> {
                        coroutineScope.launch {
                            val message = deleteRealViewerMedia(deletingItem.mediaId)
                            if (message != null) {
                                showViewerNotice(message)
                                return@launch
                            }
                            val nextItems = viewerItems.filterNot { it.mediaId == deletingItem.mediaId }
                            if (nextItems.isEmpty()) {
                                handleBack()
                            } else {
                                viewerItems = nextItems
                                pagerState.scrollToPage(currentIndex.coerceAtMost(nextItems.lastIndex))
                                showViewerNotice("已删除当前媒体，并写入回收站。", emphasized = true)
                            }
                        }
                    }
                }
            },
        )
    }

    val heroActive = heroOrigin != null && heroProgress.value < 1f
    // Hero 过渡：内容层淡入/淡出 + 微缩放，背景始终全黑不闪烁
    val heroContentAlpha = if (heroOrigin != null) heroProgress.value else 1f
    val heroContentScale = if (heroOrigin != null) 0.97f + 0.03f * heroProgress.value else 1f
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ViewerNightBottom)
            .viewerSingleTapGesture(
                onTap = { position, size ->
                    handleViewerTap(position, size, currentItem, isImmersive, density, videoControlsVisible,
                        { videoControlsVisible = it }, ::toggleImmersive, { videoControlsActivityNonce += 1 })
                },
                onDoubleTap = { position, size -> handleViewerDoubleTap(position, size, currentItem, zoomState) },
            ),
    ) {
        // 内容层：淡入/淡出 + 微缩放
        Box(
            modifier = Modifier
                .matchParentSize()
                .alpha(heroContentAlpha)
                .graphicsLayer {
                    scaleX = heroContentScale
                    scaleY = heroContentScale
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
                userScrollEnabled = viewerItems.size > 1 && !zoomState.isZoomed && !heroActive,
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
                originalLoadingLabel = if (
                    RepositoryProvider.currentMode == RepositoryMode.REAL &&
                    viewerItems[page].mediaType == AppMediaType.IMAGE &&
                    RealOriginalLoadRepository.getState(viewerItems[page].toRealOriginalMediaTarget()) ==
                    OriginalLoadState.Loading &&
                    !networkState.isConnected
                ) {
                    "等待网络恢复"
                } else {
                    "原图加载中"
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
                onTogglePlayback = { toggleVideoPlayback(videoPlaybackState, currentItem, { videoPlaybackState = it }, ::revealVideoControls) },
                onSeekPlayback = { progressMillis ->
                    seekVideoPlayback(progressMillis, videoPlaybackState, currentItem, { videoPlaybackState = it }, ::revealVideoControls)
                },
                onVideoPlaybackStateChange = { mediaId, state ->
                    val mergedState = state.mergePendingSeekDisplay(videoPlaybackStateCache[mediaId])
                    videoPlaybackStateCache = videoPlaybackStateCache + (mediaId to mergedState)
                    if (mediaId == currentItem.mediaId) {
                        videoPlaybackState = mergedState
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
                autoLongImageReading = settingsState.viewerPreferences.autoLongImageReading,
                autoPauseVideoOnMediaSwitch = autoPauseVideoOnMediaSwitch,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (appOverlaysVisible) {
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
            visible = appOverlaysVisible,
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
                    handleBack()
                },
                uploaderIdentity = uploaderIdentity,
                onShare = { shareCurrentMedia(context, currentItem, coroutineScope, shareInFlight, { shareInFlight = it }, ::showViewerNotice) },
                onEditTime = { showTimeEditorSheet = true },
                onDelete = { showDeleteConfirm = true },
                onOpenRelatedPosts = { showRelatedPostsSheet = true },
                locationLabel = currentItem.locationLabel,
                onOpenLocation = { openLocationPicker() },
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
                originalActionLabel = originalActionLabel,
                timeLabel = overlayUiModel.timeLabel,
                showCommentPreview = showCommentPreview,
                onEditTime = { showTimeEditorSheet = true },
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
                                showViewerNotice(
                                    if (networkState.isConnected) {
                                        "原图加载中"
                                    } else {
                                        "网络已断开，恢复后继续加载原图"
                                    },
                                )
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
                onShowNotice = { message, emphasized ->
                    showViewerNotice(message, emphasized)
                },
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
                    applyTimeEdit(nextTimeMillis, currentItem, viewerItems, currentIndex, coroutineScope, pagerState,
                        { showTimeEditorSheet = it }, { viewerItems = it }, ::showViewerNotice)
                },
            )
        }

        } // close inner content Box
    } // close outer Box
}

