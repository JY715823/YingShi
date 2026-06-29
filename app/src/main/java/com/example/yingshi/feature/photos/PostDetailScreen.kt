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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.sync.StaleBanner
import com.example.yingshi.feature.sync.SyncModule
import com.example.yingshi.feature.sync.SyncVersionTracker
import com.example.yingshi.ui.components.rememberYingShiMotionEnabled
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PostDetailScreen(
    route: PostDetailPlaceholderRoute,
    onBack: () -> Unit,
    onOpenGearEdit: (GearEditRoute) -> Unit,
    onOpenPostDetail: (PostDetailPlaceholderRoute) -> Unit = {},
    onOpenCacheManagement: (CacheManagementRoute) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
        RealPostDetailScreen(
            route = route,
            onBack = onBack,
            onOpenGearEdit = { onOpenGearEdit(GearEditRoute(route.postId)) },
            onOpenPostDetail = onOpenPostDetail,
            onOpenCacheManagement = onOpenCacheManagement,
            modifier = modifier,
        )
        return
    }

    val detail = FakeAlbumRepository.getPostDetail(route)
    var inPostViewerInitialPage by rememberSaveable(route.postId) {
        mutableStateOf<Int?>(null)
    }
    var mediaCommentPage by rememberSaveable(route.postId) {
        mutableStateOf<Int?>(null)
    }
    var showSmallAlbumComments by rememberSaveable(route.postId) {
        mutableStateOf(false)
    }
    var actionNotice by rememberSaveable(route.postId) {
        mutableStateOf<String?>(null)
    }
    var actionNoticeVersion by rememberSaveable(route.postId) {
        mutableStateOf(0)
    }

    fun showActionNotice(message: String) {
        actionNotice = message
        actionNoticeVersion += 1
    }

    LaunchedEffect(actionNoticeVersion) {
        val version = actionNoticeVersion
        if (version <= 0 || actionNotice.isNullOrBlank()) return@LaunchedEffect
        delay(2600L)
        if (actionNoticeVersion == version) {
            actionNotice = null
        }
    }

    LaunchedEffect(route.autoOpenComment, route.focusMediaId, detail) {
        if (!route.autoOpenComment) return@LaunchedEffect
        val targetId = route.focusMediaId
        if (targetId != null) {
            val targetIndex = detail.mediaItems.indexOfFirst { it.id == targetId }
            if (targetIndex >= 0) {
                mediaCommentPage = targetIndex
            }
        } else {
            showSmallAlbumComments = true
        }
    }

    BackHandler(enabled = mediaCommentPage != null) {
        mediaCommentPage = null
    }
    BackHandler(enabled = showSmallAlbumComments) {
        showSmallAlbumComments = false
    }
    BackHandler(enabled = inPostViewerInitialPage != null) {
        inPostViewerInitialPage = null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SmallAlbumDetailContent(
            detail = detail,
            highlightMediaIds = route.highlightMediaIds,
            focusMediaId = route.focusMediaId,
            feedbackNonce = route.feedbackNonce,
            onBack = onBack,
            onOpenGearEdit = { onOpenGearEdit(GearEditRoute(route.postId)) },
            onOpenMediaViewer = { page -> inPostViewerInitialPage = page },
            onOpenSmallAlbumComments = { showSmallAlbumComments = true },
            actionNotice = actionNotice,
            onShowActionNotice = ::showActionNotice,
            modifier = Modifier.fillMaxSize(),
        )

        if (showSmallAlbumComments) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(YingShiThemeTokens.colors.viewerBackground.copy(alpha = 0.16f))
                    .clickable { showSmallAlbumComments = false },
            )
            FakeSmallAlbumCommentSheet(
                postId = detail.postId,
                onClose = { showSmallAlbumComments = false },
                onShowNotice = ::showActionNotice,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = YingShiThemeTokens.spacing.lg)
                    .padding(bottom = YingShiThemeTokens.spacing.lg),
            )
        }

        mediaCommentPage?.let { page ->
            val media = detail.mediaItems.getOrNull(page.coerceAtLeast(0))
            if (media == null) {
                mediaCommentPage = null
                return@let
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(YingShiThemeTokens.colors.viewerBackground.copy(alpha = 0.16f))
                    .clickable { mediaCommentPage = null },
            )
            MediaCommentPlaceholderSheet(
                media = media,
                onClose = { mediaCommentPage = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = YingShiThemeTokens.spacing.lg)
                    .padding(bottom = YingShiThemeTokens.spacing.lg),
            )
        }

        val viewerInitialPage = inPostViewerInitialPage
        if (viewerInitialPage != null) {
            PhotoViewerScreen(
                route = detail.toInPostViewerRoute(initialIndex = viewerInitialPage),
                onBack = { inPostViewerInitialPage = null },
                onOpenPostDetail = {
                    inPostViewerInitialPage = null
                    onOpenPostDetail(it)
                },
                onOpenCacheManagement = onOpenCacheManagement,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun RealPostDetailScreen(
    route: PostDetailPlaceholderRoute,
    onBack: () -> Unit,
    onOpenGearEdit: () -> Unit,
    onOpenPostDetail: (PostDetailPlaceholderRoute) -> Unit,
    onOpenCacheManagement: (CacheManagementRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sessionKey = realBackendSessionKey("real-post-detail-${route.postId}")
    val viewModel: PostDetailRealViewModel = viewModel(
        key = sessionKey,
        factory = PostDetailRealViewModel.factory(route),
    )
    val uiState by viewModel.uiState.collectAsState()
    val detailWithEntryNotice = uiState.detail?.copy(entryNotice = route.entryNotice)
    val backendMutationEvent by RealBackendMutationBus.latestEvent.collectAsState()
    val syncStaleState by SyncVersionTracker.staleState.collectAsState()
    var inPostViewerInitialPage by rememberSaveable(route.postId) {
        mutableStateOf<Int?>(null)
    }
    var mediaCommentPage by rememberSaveable(route.postId) {
        mutableStateOf<Int?>(null)
    }
    var showSmallAlbumComments by rememberSaveable(route.postId) {
        mutableStateOf(false)
    }
    var actionNotice by rememberSaveable(route.postId) {
        mutableStateOf<String?>(null)
    }
    var actionNoticeVersion by rememberSaveable(route.postId) {
        mutableStateOf(0)
    }

    fun showActionNotice(message: String) {
        actionNotice = message
        actionNoticeVersion += 1
    }

    LaunchedEffect(actionNoticeVersion) {
        val version = actionNoticeVersion
        if (version <= 0 || actionNotice.isNullOrBlank()) return@LaunchedEffect
        delay(2600L)
        if (actionNoticeVersion == version) {
            actionNotice = null
        }
    }

    val detail = detailWithEntryNotice
    val detailMediaIds = detail?.mediaItems?.map { it.id }.orEmpty()

    LaunchedEffect(route.autoOpenComment, route.focusMediaId, detail?.postId) {
        if (!route.autoOpenComment) return@LaunchedEffect
        val targetId = route.focusMediaId
        if (targetId != null) {
            val items = detail?.mediaItems ?: return@LaunchedEffect
            val targetIndex = items.indexOfFirst { it.id == targetId }
            if (targetIndex >= 0) {
                mediaCommentPage = targetIndex
            }
        } else {
            detail ?: return@LaunchedEffect
            showSmallAlbumComments = true
            viewModel.retryPostComments()
            delay(800L)
            viewModel.retryPostComments()
        }
    }

    val selectedMedia = mediaCommentPage?.let { page ->
        detail?.mediaItems?.getOrNull(page.coerceAtLeast(0))
    }

    selectedMedia?.let { media ->
        LaunchedEffect(media.id) {
            viewModel.ensureMediaComments(media.id)
        }
    }

    BackHandler(enabled = mediaCommentPage != null) {
        mediaCommentPage = null
    }
    BackHandler(enabled = showSmallAlbumComments) {
        showSmallAlbumComments = false
    }
    BackHandler(enabled = inPostViewerInitialPage != null) {
        inPostViewerInitialPage = null
    }
    LaunchedEffect(backendMutationEvent.version, detailMediaIds) {
        if (backendMutationEvent.version <= 0) return@LaunchedEffect
        when {
            backendMutationEvent.isCommentMutationForPostDetail(route.postId, detailMediaIds) -> {
                viewModel.handleExternalCommentMutation(backendMutationEvent)
            }
            backendMutationEvent.affectsPostDetail(route.postId, detailMediaIds) -> {
                viewModel.refresh()
            }
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { Triple(syncStaleState.photoFeedStale, syncStaleState.albumsStale, syncStaleState.trashStale) }
            .collect { (photoStale, albumsStale, trashStale) ->
                if (photoStale || albumsStale || trashStale) {
                    viewModel.refresh()
                    if (albumsStale) SyncVersionTracker.markRefreshed(SyncModule.ALBUMS)
                }
            }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            uiState.tokenMissing -> {
                PostDetailInfoState(
                    title = "需要登录",
                    message = uiState.errorMessage
                        ?: "请先连接服务，再打开这个小相册。",
                    onBack = onBack,
                    actionLabel = "重试",
                    onAction = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            uiState.isLoading && detail == null -> {
                PostDetailLoadingState(
                    onBack = onBack,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            uiState.errorMessage != null && detail == null -> {
                val errorMessage = uiState.errorMessage
                    ?: "读取小相册详情失败。"
                PostDetailInfoState(
                    title = "读取失败",
                    message = errorMessage,
                    onBack = onBack,
                    actionLabel = "重试",
                    onAction = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            detail == null -> {
                PostDetailMissingState(
                    onBack = onBack,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                RealSmallAlbumDetailContent(
                    detail = detail,
                    uiState = uiState,
                    highlightMediaIds = route.highlightMediaIds,
                    focusMediaId = route.focusMediaId,
                    feedbackNonce = route.feedbackNonce,
                    onBack = onBack,
                    onRefresh = viewModel::refresh,
                    onOpenGearEdit = onOpenGearEdit,
                    onOpenMediaViewer = { page -> inPostViewerInitialPage = page },
                    onOpenSmallAlbumComments = { showSmallAlbumComments = true },
                    onRetrySmallAlbumComments = viewModel::retryPostComments,
                    onCreatePostComment = viewModel::createPostComment,
                    onUpdatePostComment = viewModel::updatePostComment,
                    onDeletePostComment = viewModel::deletePostComment,
                    actionNotice = actionNotice,
                    onShowActionNotice = ::showActionNotice,
                    modifier = Modifier.fillMaxSize(),
                )

                if (showSmallAlbumComments) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(YingShiThemeTokens.colors.viewerBackground.copy(alpha = 0.16f))
                            .clickable { showSmallAlbumComments = false },
                    )
                    RealSmallAlbumCommentSheet(
                        smallAlbumId = detail.postId,
                        state = uiState.postComments,
                        onClose = { showSmallAlbumComments = false },
                        onRetry = viewModel::retryPostComments,
                        onCreateComment = viewModel::createPostComment,
                        onUpdateComment = viewModel::updatePostComment,
                        onDeleteComment = viewModel::deletePostComment,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = YingShiThemeTokens.spacing.lg)
                            .padding(bottom = YingShiThemeTokens.spacing.lg),
                    )
                }

                selectedMedia?.let { media ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(YingShiThemeTokens.colors.viewerBackground.copy(alpha = 0.16f))
                            .clickable { mediaCommentPage = null },
                    )
                    RealMediaCommentSheet(
                        media = media,
                        state = uiState.mediaComments[media.id] ?: RealCommentThreadUiState(isLoading = true),
                        onClose = { mediaCommentPage = null },
                        onRetry = { viewModel.retryMediaComments(media.id) },
                        onCreateComment = { content -> viewModel.createMediaComment(media.id, content) },
                        onUpdateComment = { commentId, content ->
                            viewModel.updateMediaComment(media.id, commentId, content)
                        },
                        onDeleteComment = { commentId -> viewModel.deleteMediaComment(media.id, commentId) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = YingShiThemeTokens.spacing.lg)
                            .padding(bottom = YingShiThemeTokens.spacing.lg),
                    )
                }

                val viewerInitialPage = inPostViewerInitialPage
                if (viewerInitialPage != null) {
                    PhotoViewerScreen(
                        route = detail.toInPostViewerRoute(initialIndex = viewerInitialPage),
                        onBack = { inPostViewerInitialPage = null },
                        onOpenPostDetail = {
                            inPostViewerInitialPage = null
                            onOpenPostDetail(it)
                        },
                        onOpenCacheManagement = onOpenCacheManagement,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                StaleBanner(
                    module = SyncModule.ALBUMS,
                    onRefresh = {
                        viewModel.refresh()
                        SyncVersionTracker.markRefreshed(SyncModule.ALBUMS)
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = YingShiThemeTokens.spacing.md),
                )
            }
        }
    }
}

@Composable
private fun RealSmallAlbumDetailContent(
    detail: PostDetailUiModel,
    uiState: PostDetailRealUiState,
    highlightMediaIds: List<String>,
    focusMediaId: String?,
    feedbackNonce: Int,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenGearEdit: () -> Unit,
    onOpenMediaViewer: (Int) -> Unit,
    onOpenSmallAlbumComments: () -> Unit,
    onRetrySmallAlbumComments: () -> Unit,
    onCreatePostComment: (String) -> Unit,
    onUpdatePostComment: (String, String) -> Unit,
    onDeletePostComment: (String) -> Unit,
    actionNotice: String?,
    onShowActionNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showPhotoFeedPicker by rememberSaveable(detail.postId) {
        mutableStateOf(false)
    }
    var shareAllInFlight by remember { mutableStateOf(false) }
    val collaboratorDirectory = rememberCollaboratorDirectorySnapshot()
    val ownershipAvatars = remember(detail.participantUserIds, collaboratorDirectory) {
        collaboratorDirectory.resolveOrdered(detail.participantUserIds)
    }
    val sessionVersion = AuthSessionManager.sessionVersion
    val accessToken = remember(sessionVersion) {
        AuthSessionManager.peekAccessToken()?.takeIf { it.isNotBlank() }
    }
    val postMediaIds = remember(detail.mediaItems) { detail.mediaItems.map { it.id } }
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
    LaunchedEffect(detail.postId, postMediaIds, focusMediaId, highlightKey) {
        val targetId = focusMediaId ?: highlightMediaIds.firstOrNull()
        if (targetId.isNullOrBlank() || detail.mediaItems.isEmpty()) return@LaunchedEffect
        val targetIndex = detail.mediaItems.indexOfFirst { it.id == targetId }
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
    LaunchedEffect(detail.postId, highlightKey) {
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
    val originalTargets = remember(detail.mediaItems) {
        detail.mediaItems.map { it.toRealOriginalMediaTarget() }
    }
    val postOriginalSummary = RealOriginalLoadRepository.getPostSummaryForTargets(originalTargets)
    if (showPhotoFeedPicker) {
        AppPhotoFeedPickerScreen(
            title = "照片流",
            confirmLabel = "加入当前相册",
            disabledMediaIds = detail.mediaItems.map { it.id }.toSet(),
            disabledSelectionLabel = "已在相册中",
            confirmBackWhenSelected = true,
            onBack = { showPhotoFeedPicker = false },
            onConfirm = { items ->
                val mediaIds = items.map { it.mediaId }.distinct()
                if (mediaIds.isEmpty()) {
                    showPhotoFeedPicker = false
                    return@AppPhotoFeedPickerScreen
                }
                coroutineScope.launch {
                    when (val result = RepositoryProvider.postRepository.addMediaToPost(detail.postId, mediaIds)) {
                        is ApiResult.Success -> {
                            notifyRealBackendContentChangedWithoutPhotoFeed(
                                postIds = setOf(detail.postId),
                                mediaIds = mediaIds.toSet(),
                            )
                            showPhotoFeedPicker = false
                            onRefresh()
                            onShowActionNotice("已加入当前相册")
                        }
                        is ApiResult.Error -> {
                            onShowActionNotice(result.message)
                        }
                        ApiResult.Loading -> Unit
                    }
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
                title = detail.title.ifBlank { "小相册" },
                ownershipAvatars = ownershipAvatars,
                onBack = onBack,
                onShareAll = {
                    val shareItems = detail.mediaItems.map(PostDetailMediaUiModel::toShareableMediaItem)
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
                                        packageBaseName = detail.title.ifBlank {
                                            "映世小相册-${detail.postId}"
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
            uiState.errorMessage?.let { message ->
                PostInlineNotice(
                    text = message,
                    actionLabel = "重试",
                    onAction = onRefresh,
                )
            }
        },
        infoSection = {
            SmallAlbumInfoSection(
                detail = detail,
                originalSummary = postOriginalSummary,
                onOpenComments = onOpenSmallAlbumComments,
                onLoadAllOriginals = {
                    onShowActionNotice("开始加载小相册原图")
                    coroutineScope.launch {
                        val summary = RealOriginalLoadRepository.loadAllOriginals(
                            context = context,
                            targets = originalTargets,
                            accessToken = accessToken,
                        )
                        val message = "已加载 ${summary.successCount} 张原图，" +
                            "${summary.skippedCount} 张无原图，${summary.failedCount} 张失败"
                        onShowActionNotice(message)
                    }
                },
            )
        },
        mediaSection = {
            SmallAlbumMediaGridSection(
                postId = detail.postId,
                mediaItems = detail.mediaItems,
                highlightMediaIds = if (showNewAddedState) highlightMediaIds else emptyList(),
                onOpenMediaViewer = onOpenMediaViewer,
                onAddMedia = { showPhotoFeedPicker = true },
                onRefreshRequest = onRefresh,
                onEmptyAfterDelete = onBack,
                onShowNotice = onShowActionNotice,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

@Composable
private fun RealMediaCommentSheet(
    media: PostDetailMediaUiModel,
    state: RealCommentThreadUiState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onCreateComment: (String) -> Unit,
    onUpdateComment: (String, String) -> Unit,
    onDeleteComment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "媒体评论",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
                PostActionChip(text = "关闭", onClick = onClose)
            }

            RealCommentThreadContent(
                state = state,
                stateKeyPrefix = "real-media-comment-${media.id}",
                emptyText = "当前还没有媒体评论，来发第一条吧。",
                onRetry = onRetry,
                onCreateComment = onCreateComment,
                onUpdateComment = onUpdateComment,
                onDeleteComment = onDeleteComment,
            )
        }
    }
}

@Composable
private fun RealCommentThreadCard(
    title: String,
    subtitle: String,
    stateKeyPrefix: String,
    emptyText: String,
    state: RealCommentThreadUiState,
    onRetry: () -> Unit,
    onCreateComment: (String) -> Unit,
    onUpdateComment: (String, String) -> Unit,
    onDeleteComment: (String) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.68f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            RealCommentThreadContent(
                state = state,
                stateKeyPrefix = stateKeyPrefix,
                emptyText = emptyText,
                onRetry = onRetry,
                onCreateComment = onCreateComment,
                onUpdateComment = onUpdateComment,
                onDeleteComment = onDeleteComment,
            )
        }
    }
}

@Composable
private fun RealCommentThreadContent(
    state: RealCommentThreadUiState,
    stateKeyPrefix: String,
    emptyText: String,
    onRetry: () -> Unit,
    onCreateComment: (String) -> Unit,
    onUpdateComment: (String, String) -> Unit,
    onDeleteComment: (String) -> Unit,
    showInput: Boolean = true,
    inputPlaceholder: String = "写一条评论",
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val motion = YingShiThemeTokens.motion
    val motionEnabled = rememberYingShiMotionEnabled()
    val copyComment = rememberCommentCopyHandler()
    var expanded by rememberSaveable(stateKeyPrefix) { mutableStateOf(false) }
    var actionCommentId by rememberSaveable(stateKeyPrefix) { mutableStateOf<String?>(null) }
    var editingCommentId by rememberSaveable(stateKeyPrefix) { mutableStateOf<String?>(null) }
    var editingDraft by rememberSaveable(stateKeyPrefix, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var selectedCommentId by rememberSaveable(stateKeyPrefix) { mutableStateOf<String?>(null) }
    var pendingDeleteCommentId by rememberSaveable(stateKeyPrefix) { mutableStateOf<String?>(null) }
    var selectedCommentValue by rememberSaveable(stateKeyPrefix, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val visibleComments = state.comments.visibleComments(expanded)

    BackHandler(enabled = selectedCommentId != null) {
        selectedCommentId = null
        selectedCommentValue = TextFieldValue("")
    }
    BackHandler(enabled = actionCommentId != null) {
        actionCommentId = null
    }

    if (state.errorMessage != null) {
        PostInlineNotice(
            text = state.errorMessage,
            actionLabel = "重试",
            onAction = onRetry,
        )
    }
    if (state.isLoading && visibleComments.isNotEmpty()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Text(
                text = "评论同步中…",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }

    when {
        state.isLoading && visibleComments.isEmpty() -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = "正在读取评论…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }

        visibleComments.isEmpty() -> {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }

        else -> {
            visibleComments.forEach { comment ->
                CommentListItem(
                    comment = comment,
                    timeLabel = formatPostTime(comment.createdAtMillis),
                    onLongPress = {
                        selectedCommentId = null
                        selectedCommentValue = TextFieldValue("")
                        editingCommentId = null
                        editingDraft = TextFieldValue("")
                        pendingDeleteCommentId = null
                        actionCommentId = comment.id
                    },
                    onClick = {
                        if (selectedCommentId != null) {
                            selectedCommentId = null
                            selectedCommentValue = TextFieldValue("")
                        }
                        pendingDeleteCommentId = null
                        actionCommentId = null
                    },
                    showInlineActionMenu = actionCommentId == comment.id &&
                        selectedCommentId != comment.id &&
                        editingCommentId != comment.id,
                    onCopyFull = {
                        copyComment(comment.content)
                        actionCommentId = null
                    },
                    onSelectText = {
                        selectedCommentId = comment.id
                        selectedCommentValue = fullCommentSelectionValue(comment.content)
                        editingCommentId = null
                        editingDraft = TextFieldValue("")
                        pendingDeleteCommentId = null
                        actionCommentId = null
                    },
                    onEdit = {
                        editingCommentId = comment.id
                        editingDraft = endOfCommentEditValue(comment.content)
                        selectedCommentId = null
                        selectedCommentValue = TextFieldValue("")
                        pendingDeleteCommentId = null
                        actionCommentId = null
                    },
                    onDelete = {
                        if (pendingDeleteCommentId == comment.id) {
                            onDeleteComment(comment.id)
                            if (selectedCommentId == comment.id) {
                                selectedCommentId = null
                                selectedCommentValue = TextFieldValue("")
                            }
                            if (editingCommentId == comment.id) {
                                editingCommentId = null
                                editingDraft = TextFieldValue("")
                            }
                            pendingDeleteCommentId = null
                            actionCommentId = null
                        } else {
                            pendingDeleteCommentId = comment.id
                            actionCommentId = comment.id
                        }
                    },
                    confirmingDelete = pendingDeleteCommentId == comment.id,
                    isEditing = editingCommentId == comment.id,
                    editingValue = if (editingCommentId == comment.id) editingDraft else endOfCommentEditValue(comment.content),
                    onEditingValueChange = { editingDraft = it },
                    onSaveEdit = {
                        onUpdateComment(comment.id, editingDraft.text)
                        editingCommentId = null
                        editingDraft = TextFieldValue("")
                        pendingDeleteCommentId = null
                        actionCommentId = null
                    },
                    onCancelEdit = {
                        editingCommentId = null
                        editingDraft = TextFieldValue("")
                    },
                    selectionMode = selectedCommentId == comment.id,
                    selectionFieldValue = if (selectedCommentId == comment.id) {
                        selectedCommentValue
                    } else {
                        TextFieldValue(comment.content)
                    },
                    onSelectionFieldValueChange = { selectedCommentValue = it },
                    onCopySelection = if (selectedCommentId == comment.id) {
                        {
                            selectedCommentValue.selectedTextOrNull()?.let(copyComment)
                            selectedCommentId = null
                            selectedCommentValue = TextFieldValue("")
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }

    if (state.comments.hasHiddenComments(expanded)) {
        PostActionChip(text = "展开更多", onClick = { expanded = true })
    }
    if (state.comments.canCollapseComments(expanded)) {
        PostActionChip(text = "收起", onClick = { expanded = false })
    }
    if (state.isMutating) {
        Text(
            text = "正在提交…",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
    }
    if (showInput) {
        CommentInputBar(
            stateKey = "$stateKeyPrefix-input",
            placeholder = inputPlaceholder,
            elevated = true,
            onSend = onCreateComment,
        )
    }
}

@Composable
private fun PostDetailLoadingState(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PostDetailInfoState(
        title = "正在读取小相册详情",
        message = "正在读取小相册详情和评论…",
        onBack = onBack,
        modifier = modifier,
        loading = true,
    )
}

@Composable
private fun PostDetailInfoState(
    title: String,
    message: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    loading: Boolean = false,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .statusBarsPadding()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        SmallAlbumDetailTopBar(
            title = title,
            onBack = onBack,
            onShareAll = {},
            onEdit = {},
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
            color = colors.raisedSurface.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
        ) {
            Column(
                modifier = Modifier.padding(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = colors.primaryAction,
                    )
                }
                if (actionLabel != null && onAction != null) {
                    PostDetailActionButton(text = actionLabel, onClick = onAction)
                }
            }
        }
    }
}

@Composable
private fun PostInlineNotice(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = colors.sectionBackground.copy(alpha = 0.64f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.56f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            if (actionLabel != null && onAction != null) {
                PostDetailActionButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

@Composable
private fun PostDetailActionButton(
    text: String,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    Surface(
        modifier = Modifier.yingShiClickable(shape = shape, pressedScale = 0.96f, onClick = onClick),
        shape = shape,
        color = colors.primaryContainer.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.74f)),
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.titleAccent,
        )
    }
}

@Composable
private fun PostDetailMissingState(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(
                horizontal = YingShiThemeTokens.spacing.lg,
                vertical = YingShiThemeTokens.spacing.md,
            ),
        verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.md),
    ) {
        SmallAlbumDetailTopBar(
            title = "小相册",
            onBack = onBack,
            onShareAll = {},
            onEdit = {},
        )
        Text(
            text = "当前小相册没有可展示的媒体，可能已经被删除、被移出关系，或仍处于系统删除状态。",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun SmallAlbumDetailContent(
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
fun SmallAlbumDetailBodyLayout(
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
private fun SmallAlbumAtmosphereBackdrop(
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val motion = YingShiThemeTokens.motion
    val atmosphereAlpha = motion.feedAtmosphereAlpha * 1.16f
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.glowWash.copy(alpha = 0.30f * atmosphereAlpha),
                            colors.sectionBackground.copy(alpha = 0.16f * atmosphereAlpha),
                            Color.Transparent,
                        ),
                        center = androidx.compose.ui.geometry.Offset(0f, 0f),
                        radius = 820f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.memoryContainer.copy(alpha = 0.20f * atmosphereAlpha),
                            Color.Transparent,
                        ),
                        center = androidx.compose.ui.geometry.Offset(980f, 180f),
                        radius = 640f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.glowWash.copy(alpha = 0.12f * atmosphereAlpha),
                            Color.Transparent,
                            colors.sectionBackground.copy(alpha = 0.10f * atmosphereAlpha),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun SmallAlbumMediaAtmospherePanel(
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val motion = YingShiThemeTokens.motion
    val atmosphereAlpha = motion.feedAtmosphereAlpha
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.glowWash.copy(alpha = 0.16f * atmosphereAlpha),
                        Color.Transparent,
                        colors.sectionBackground.copy(alpha = 0.12f * atmosphereAlpha),
                    ),
                ),
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colors.glowWash.copy(alpha = 0.26f * atmosphereAlpha),
                        Color.Transparent,
                    ),
                    center = androidx.compose.ui.geometry.Offset(0f, 0f),
                    radius = 720f,
                ),
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colors.memoryContainer.copy(alpha = 0.18f * atmosphereAlpha),
                        Color.Transparent,
                    ),
                    center = androidx.compose.ui.geometry.Offset(980f, 180f),
                    radius = 620f,
                ),
            ),
    )
}

@Composable
private fun SmallAlbumInfoSurface(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape,
    contentPadding: PaddingValues,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier,
        shape = shape,
        color = colors.raisedSurface.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.78f)),
        shadowElevation = 1.dp,
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.glowWash.copy(alpha = 0.28f),
                            colors.memoryWash.copy(alpha = 0.16f),
                            Color.Transparent,
                        ),
                    ),
                )
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun PostDetailBodyLayout(
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
private fun SmallAlbumDetailTopBar(
    title: String,
    ownershipAvatars: List<CollaboratorIdentityUiModel> = emptyList(),
    onBack: () -> Unit,
    onShareAll: () -> Unit,
    onEdit: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PostIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "返回",
            onClick = onBack,
            buttonSize = 44.dp,
            iconSize = 22.dp,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (ownershipAvatars.isNotEmpty()) {
                CollaboratorAvatarStack(
                    identities = ownershipAvatars,
                    avatarSize = 24.dp,
                )
            }
        }
        PostIconButton(icon = Icons.Default.IosShare, contentDescription = "分享整个小相册", onClick = onShareAll)
        PostIconButton(icon = Icons.Rounded.Edit, contentDescription = "整理", onClick = onEdit)
    }
}

@Composable
private fun PostIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    buttonSize: Dp = 48.dp,
    iconSize: Dp = 25.dp,
) {
    val colors = YingShiThemeTokens.colors
    val shape = CircleShape
    Surface(
        modifier = modifier
            .size(buttonSize)
            .yingShiClickable(shape = shape, pressedScale = 0.94f, onClick = onClick),
        shape = shape,
        color = if (emphasized) {
            colors.primaryContainer.copy(alpha = 0.92f)
        } else {
            colors.raisedSurface.copy(alpha = 0.94f)
        },
        border = BorderStroke(
            1.dp,
            if (emphasized) {
                colors.glassStroke.copy(alpha = 0.82f)
            } else {
                colors.dividerSoft.copy(alpha = 0.72f)
            },
        ),
        shadowElevation = if (emphasized) 3.dp else 1.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colors.titleAccent,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
fun PostMediaCard(
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
fun PostMediaInfoRow(
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
private fun PostMediaEmptyInfoRow() {
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
fun SmallAlbumInfoSection(
    detail: PostDetailUiModel,
    originalSummary: PostOriginalLoadSummary,
    onOpenComments: () -> Unit,
    onLoadAllOriginals: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    var summaryExpanded by rememberSaveable(detail.postId, detail.summary) { mutableStateOf(false) }
    val summary = detail.summary.meaningfulPostSummaryOrNull()
    val albumPalette = remember(detail.albumIds) {
        resolveLargeAlbumPalette(detail.albumIds.firstOrNull().orEmpty())
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        SmallAlbumPrimaryMetaRow(
            timeLabel = formatPostTime(detail.postDisplayTimeMillis),
            mediaCountLabel = "${detail.mediaItems.size} 张",
        )
        if (summary != null) {
            SmallAlbumSummaryText(
                summary = summary,
                expanded = summaryExpanded,
                onToggleExpanded = { summaryExpanded = !summaryExpanded },
                modifier = Modifier.padding(horizontal = spacing.xs, vertical = 2.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                SmallAlbumBelongChip(
                    text = detail.albumChips.firstOrNull().orEmpty(),
                    palette = albumPalette,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PostActionChip(
                    text = originalSummary.buttonLabel,
                    onClick = onLoadAllOriginals,
                    containerColor = colors.primaryContainer.copy(alpha = 0.70f),
                )
                PostIconButton(
                    icon = Icons.Rounded.ChatBubbleOutline,
                    contentDescription = if (detail.comments.isEmpty()) "打开评论" else "打开评论，当前 ${detail.comments.size} 条",
                    onClick = onOpenComments,
                    buttonSize = 40.dp,
                    iconSize = 20.dp,
                )
            }
        }
    }
}

@Composable
private fun SmallAlbumPrimaryMetaRow(
    timeLabel: String,
    mediaCountLabel: String,
) {
    val spacing = YingShiThemeTokens.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YingShiStatusPill(text = timeLabel)
        YingShiStatusPill(text = mediaCountLabel)
    }
}

@Composable
private fun SmallAlbumMemoryPreviewStrip(
    mediaItems: List<PostDetailMediaUiModel>,
    totalCount: Int,
    onOpenMedia: (Int) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "精选预览",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            Text(
                text = "$totalCount 张记忆",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = colors.textSecondary,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            mediaItems.forEachIndexed { index, media ->
                SmallAlbumPreviewTile(
                    media = media,
                    prominent = index == 0,
                    modifier = Modifier
                        .weight(if (index == 0) 1.45f else 1f)
                        .fillMaxHeight(),
                    onClick = { onOpenMedia(index) },
                )
            }
            repeat((3 - mediaItems.size).coerceAtLeast(0)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(radius.md))
                        .background(colors.sectionBackground.copy(alpha = 0.44f)),
                )
            }
        }
    }
}

@Composable
private fun SmallAlbumPreviewTile(
    media: PostDetailMediaUiModel,
    prominent: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val radius = YingShiThemeTokens.radius
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius.md))
            .yingShiClickable(
                shape = RoundedCornerShape(radius.md),
                pressedScale = 0.985f,
                onClick = onClick,
            ),
    ) {
        AppContentMediaThumbnail(
            mediaSource = media.mediaSource,
            mediaType = media.mediaType,
            palette = media.palette,
            modifier = Modifier.fillMaxSize(),
            contentDescription = postDetailMediaContentDescription(media.mediaType),
            requestSize = if (prominent) 512 else 384,
            contentScale = ContentScale.Crop,
            showLoadingIndicator = false,
        )
        YingShiMediaFrame(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(radius.md),
            topScrimAlpha = if (media.mediaType == AppMediaType.VIDEO) 0.24f else 0.14f,
            bottomGlowAlpha = if (prominent) 0.24f else 0.16f,
        )
        if (media.commentCount > 0) {
            YingShiMemoryBadge(
                text = "${media.commentCount} 评",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
                compact = true,
            )
        }
    }
}

@Composable
private fun SmallAlbumBelongChip(
    text: String,
    palette: PhotoThumbnailPalette?,
) {
    if (text.isBlank()) return
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val accentColor = remember(palette, colors.primaryActionPressed) {
        palette?.let { resolvedPalette ->
            listOf(resolvedPalette.end, resolvedPalette.accent, resolvedPalette.start)
                .minByOrNull { it.luminance() }
        } ?: colors.primaryActionPressed
    }
    val barStartColor = palette?.start ?: colors.primaryContainer
    val emphasizedColor = remember(accentColor, colors.titleAccent) {
        if (accentColor.luminance() > 0.62f) {
            lerp(accentColor, colors.titleAccent, 0.32f)
        } else {
            lerp(accentColor, colors.titleAccent, 0.12f)
        }
    }
    val containerColor = remember(barStartColor, colors.raisedSurface) {
        lerp(colors.raisedSurface, barStartColor, 0.22f)
    }
    val borderColor = remember(emphasizedColor) {
        emphasizedColor.copy(alpha = 0.34f)
    }

    Surface(
        shape = RoundedCornerShape(radius.capsule),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 5.dp, height = 16.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(barStartColor, emphasizedColor),
                        ),
                    ),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                ),
                color = emphasizedColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun resolveLargeAlbumPalette(albumId: String): PhotoThumbnailPalette? {
    if (albumId.isBlank()) return null
    return when (RepositoryProvider.currentMode) {
        RepositoryMode.REAL -> realPaletteFor(albumId)
        else -> FakeAlbumRepository.getAlbums().firstOrNull { it.id == albumId }?.accent
            ?: realPaletteFor(albumId)
    }
}

@Composable
fun PostInfoSection(
    detail: PostDetailUiModel,
    originalSummary: PostOriginalLoadSummary,
    onOpenComments: () -> Unit,
    onLoadAllOriginals: () -> Unit,
) {
    SmallAlbumInfoSection(
        detail = detail,
        originalSummary = originalSummary,
        onOpenComments = onOpenComments,
        onLoadAllOriginals = onLoadAllOriginals,
    )
}

@Composable
fun PostMediaArea(
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

@Composable
private fun FakeSmallAlbumCommentSheet(
    postId: String,
    onClose: () -> Unit,
    onShowNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val copyComment = rememberCommentCopyHandler()
    val comments = CommentGateway.getPostComments(postId)
    var expanded by rememberSaveable(postId) { mutableStateOf(false) }
    var actionCommentId by rememberSaveable(postId) { mutableStateOf<String?>(null) }
    var editingCommentId by rememberSaveable(postId) { mutableStateOf<String?>(null) }
    var editingDraft by rememberSaveable(postId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var selectedCommentId by rememberSaveable(postId) { mutableStateOf<String?>(null) }
    var pendingDeleteCommentId by rememberSaveable(postId) { mutableStateOf<String?>(null) }
    var selectedCommentValue by rememberSaveable(postId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val visibleComments = comments.visibleComments(expanded)

    BackHandler(enabled = selectedCommentId != null) {
        selectedCommentId = null
        selectedCommentValue = TextFieldValue("")
    }
    BackHandler(enabled = actionCommentId != null) {
        actionCommentId = null
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.64f)
            .navigationBarsPadding(),
        shape = RoundedCornerShape(radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, colors.goldAccent.copy(alpha = 0.22f)),
        shadowElevation = 8.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.memoryWash.copy(alpha = 0.72f),
                            colors.raisedSurface.copy(alpha = 0.98f),
                            colors.glowWash.copy(alpha = 0.36f),
                        ),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 6.dp, height = 24.dp)
                            .clip(RoundedCornerShape(radius.capsule))
                            .background(colors.goldAccent.copy(alpha = 0.78f)),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "小相册评论",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.titleAccent,
                        )
                        Text(
                            text = if (comments.isEmpty()) "给这段记忆留一句" else "${comments.size} 条留言",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                    }
                    PostActionChip(text = "关闭", onClick = onClose)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    if (visibleComments.isEmpty()) {
                        Text(
                            text = "还没有留言，给这段记忆留一句。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    } else {
                        visibleComments.forEach { comment ->
                    CommentListItem(
                        comment = comment,
                        timeLabel = formatPostTime(comment.createdAtMillis),
                        onLongPress = {
                            selectedCommentId = null
                            selectedCommentValue = TextFieldValue("")
                            editingCommentId = null
                            editingDraft = TextFieldValue("")
                            pendingDeleteCommentId = null
                            actionCommentId = comment.id
                        },
                        onClick = {
                            if (selectedCommentId != null) {
                                selectedCommentId = null
                                selectedCommentValue = TextFieldValue("")
                            }
                            pendingDeleteCommentId = null
                            actionCommentId = null
                        },
                        showInlineActionMenu = actionCommentId == comment.id &&
                            selectedCommentId != comment.id &&
                            editingCommentId != comment.id,
                        onCopyFull = {
                            copyComment(comment.content)
                            actionCommentId = null
                        },
                        onSelectText = {
                            selectedCommentId = comment.id
                            selectedCommentValue = fullCommentSelectionValue(comment.content)
                            editingCommentId = null
                            editingDraft = TextFieldValue("")
                            pendingDeleteCommentId = null
                            actionCommentId = null
                        },
                        onEdit = {
                            editingCommentId = comment.id
                            editingDraft = endOfCommentEditValue(comment.content)
                            selectedCommentId = null
                            selectedCommentValue = TextFieldValue("")
                            pendingDeleteCommentId = null
                            actionCommentId = null
                        },
                        onDelete = {
                            if (pendingDeleteCommentId == comment.id) {
                                CommentGateway.deletePostComment(postId, comment.id)
                                if (selectedCommentId == comment.id) {
                                    selectedCommentId = null
                                    selectedCommentValue = TextFieldValue("")
                                }
                                if (editingCommentId == comment.id) {
                                    editingCommentId = null
                                    editingDraft = TextFieldValue("")
                                }
                                pendingDeleteCommentId = null
                                actionCommentId = null
                                onShowNotice("评论已删除")
                            } else {
                                pendingDeleteCommentId = comment.id
                                actionCommentId = comment.id
                            }
                        },
                        confirmingDelete = pendingDeleteCommentId == comment.id,
                        isEditing = editingCommentId == comment.id,
                        editingValue = if (editingCommentId == comment.id) editingDraft else endOfCommentEditValue(comment.content),
                        onEditingValueChange = { editingDraft = it },
                        onSaveEdit = {
                            CommentGateway.updatePostComment(
                                postId = postId,
                                commentId = comment.id,
                                content = editingDraft.text,
                            )
                            editingCommentId = null
                            editingDraft = TextFieldValue("")
                            pendingDeleteCommentId = null
                            actionCommentId = null
                            onShowNotice("评论已更新")
                        },
                        onCancelEdit = {
                            editingCommentId = null
                            editingDraft = TextFieldValue("")
                        },
                        selectionMode = selectedCommentId == comment.id,
                        selectionFieldValue = if (selectedCommentId == comment.id) {
                            selectedCommentValue
                        } else {
                            TextFieldValue(comment.content)
                        },
                        onSelectionFieldValueChange = { selectedCommentValue = it },
                        onCopySelection = if (selectedCommentId == comment.id) {
                            {
                                selectedCommentValue.selectedTextOrNull()?.let(copyComment)
                                selectedCommentId = null
                                selectedCommentValue = TextFieldValue("")
                            }
                        } else {
                            null
                        },
                        )
                    }
                }
                if (comments.hasHiddenComments(expanded)) {
                    PostActionChip(text = "展开更多评论", onClick = { expanded = true })
                }
                if (comments.canCollapseComments(expanded)) {
                    PostActionChip(text = "收起到最新 10 条", onClick = { expanded = false })
                }
            }
                CommentInputBar(
                    stateKey = "post-comment-input-$postId",
                    placeholder = "写一条小相册评论",
                    modifier = Modifier.imePadding(),
                    elevated = true,
                    onSend = { content ->
                        CommentGateway.addPostComment(postId, content)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RealSmallAlbumCommentSheet(
    smallAlbumId: String,
    state: RealCommentThreadUiState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onCreateComment: (String) -> Unit,
    onUpdateComment: (String, String) -> Unit,
    onDeleteComment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.64f)
            .navigationBarsPadding(),
        shape = RoundedCornerShape(radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, colors.goldAccent.copy(alpha = 0.22f)),
        shadowElevation = 8.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.memoryWash.copy(alpha = 0.72f),
                            colors.raisedSurface.copy(alpha = 0.98f),
                            colors.glowWash.copy(alpha = 0.36f),
                        ),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 6.dp, height = 24.dp)
                            .clip(RoundedCornerShape(radius.capsule))
                            .background(colors.goldAccent.copy(alpha = 0.78f)),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "小相册评论",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.titleAccent,
                        )
                        Text(
                            text = if (state.comments.isEmpty()) "给这段记忆留一句" else "${state.comments.size} 条留言",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                    }
                    PostActionChip(text = "关闭", onClick = onClose)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    RealCommentThreadContent(
                        state = state,
                        stateKeyPrefix = "real-small-album-comment-$smallAlbumId",
                        emptyText = "还没有留言，给这段记忆留一句。",
                        onRetry = onRetry,
                        onCreateComment = onCreateComment,
                        onUpdateComment = onUpdateComment,
                        onDeleteComment = onDeleteComment,
                        showInput = false,
                    )
                }
                CommentInputBar(
                    stateKey = "real-small-album-comment-$smallAlbumId-input",
                    placeholder = "写一条小相册评论",
                    modifier = Modifier.imePadding(),
                    elevated = true,
                    onSend = onCreateComment,
                )
            }
        }
    }
}

@Composable
private fun SmallAlbumCommentSummaryCard(
    commentCount: Int,
    isLoading: Boolean,
    statusMessage: String?,
    errorMessage: String?,
    onOpenComments: () -> Unit,
    onRetry: (() -> Unit)?,
    label: String,
    emptyText: String,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.xl),
        color = colors.sectionBackground.copy(alpha = 0.70f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
                Text(
                    text = when {
                        isLoading && commentCount == 0 -> "正在读取评论…"
                        !errorMessage.isNullOrBlank() -> errorMessage
                        commentCount > 0 -> "当前有 $commentCount 条评论，点击右上角图标查看和输入。"
                        else -> emptyText
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            if (onRetry != null && !errorMessage.isNullOrBlank()) {
                PostActionChip(text = "重试", onClick = onRetry)
            }
            PostActionChip(text = "打开评论", onClick = onOpenComments)
        }
    }
}

@Composable
private fun MediaCommentPlaceholderSheet(
    media: PostDetailMediaUiModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val comments = CommentGateway.getMediaComments(media.id)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "媒体评论",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.titleAccent,
                    )
                    Text(
                        text = "只属于当前媒体，不混入小相册评论区",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                PostActionChip(text = "关闭", onClick = onClose)
            }

            if (comments.isEmpty()) {
                Text(
                    text = "当前媒体暂无评论。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            } else {
                comments.take(10).forEach { comment ->
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        Text(
                            text = "${comment.author} · ${formatPostTime(comment.createdAtMillis)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = YingShiThemeTokens.colors.titleAccent,
                        )
                        Text(
                            text = comment.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                    }
                }
                if (comments.size > 10) {
                    Text(
                        text = "还有更多评论",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(radius.lg),
                    color = colors.sectionBackground.copy(alpha = 0.62f),
                ) {
                    Text(
                        text = "写一条媒体评论",
                        modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PostActionChip(
    text: String,
    onClick: () -> Unit,
    containerColor: Color = YingShiThemeTokens.colors.primaryContainer.copy(alpha = 0.42f),
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(radius.capsule)

    Surface(
        modifier = Modifier
            .yingShiClickable(shape = shape, pressedScale = 0.96f, onClick = onClick),
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.62f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.titleAccent,
        )
    }
}

@Composable
private fun PostMetaCapsule(
    text: String,
    containerColor: Color = YingShiThemeTokens.colors.softGreenContainer.copy(alpha = 0.58f),
    contentColor: Color = YingShiThemeTokens.colors.textSecondary,
    borderColor: Color? = null,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelMedium,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        shape = RoundedCornerShape(radius.capsule),
        color = containerColor,
        border = borderColor?.let { BorderStroke(1.dp, it) },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = textStyle,
            color = contentColor,
        )
    }
}

private fun PostDetailMediaUiModel.displayAspectRatio(): Float {
    val actualWidth = width
    val actualHeight = height
    if (actualWidth != null && actualHeight != null && actualWidth > 0 && actualHeight > 0) {
        return (actualWidth.toFloat() / actualHeight.toFloat()).coerceIn(0.05f, 20f)
    }
    return aspectRatio.coerceIn(0.05f, 20f)
}

private const val SmallAlbumDensityMorphVisibleLimit = 28
private const val SmallAlbumDensityTransitionThumbnailMax = 256

private enum class SmallAlbumDensityTransitionStage {
    IDLE,
    PREVIEWING,
    REBOUNDING,
    COMMITTING,
}

private data class SmallAlbumDensityTransitionCandidate(
    val item: PhotoFeedItem,
    val startBounds: Rect,
    val distanceScore: Float,
)

private data class SmallAlbumDensityTransitionOverlayEntry(
    val item: PhotoFeedItem,
    val startBounds: Rect,
    val endBounds: Rect,
)

@Composable
private fun SmallAlbumMediaGridSection(
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

    fun deleteSelectedMedia() {
        val pendingIds = selectedIds.toSet()
        if (pendingIds.isEmpty()) return
        if (RepositoryProvider.currentMode == RepositoryMode.FAKE) {
            FakeAlbumRepository.applyMediaDelete(
                postId = postId,
                mediaIds = pendingIds,
                semantic = FakeAlbumRepository.MediaDeleteSemantic.DIRECTORY_ONLY,
            )
            showDeleteSelectedConfirm = false
            selectedIds = emptySet()
            selectionMode = false
            if (FakeAlbumRepository.getPost(postId) == null) {
                onEmptyAfterDelete()
            } else {
                onRefreshRequest()
            }
            onShowNotice("已移出当前小相册")
            return
        }
        scope.launch {
            isMutating = true
            showDeleteSelectedConfirm = false
            var successCount = 0
            pendingIds.forEach { mediaId ->
                when (val result = RepositoryProvider.mediaRepository.deleteMediaFromPost(
                    smallAlbumId = postId,
                    mediaId = mediaId,
                    deleteMode = "directory",
                )) {
                    is com.example.yingshi.data.remote.result.ApiResult.Success -> successCount += 1
                    is com.example.yingshi.data.remote.result.ApiResult.Error -> {
                        onShowNotice(result.message)
                    }
                    com.example.yingshi.data.remote.result.ApiResult.Loading -> Unit
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
                onShowNotice("已移出当前小相册")
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
                text = {
                    Text(
                        text = if (selectedIds.size == 1) {
                            "确认后会把这张媒体从当前小相册移除，并写入回收站。"
                        } else {
                            "确认后会把这 ${selectedIds.size} 项媒体从当前小相册移除，并写入回收站。"
                        },
                    )
                },
                confirmButton = {
                    TrashDialogActionButton(
                        text = "确认移出",
                        enabled = !isMutating,
                        danger = true,
                        onClick = { deleteSelectedMedia() },
                    )
                },
                dismissButton = {
                    TrashDialogActionButton(
                        text = "取消",
                        onClick = { showDeleteSelectedConfirm = false },
                    )
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
private fun SmallAlbumSelectionTopBar(
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
private fun SmallAlbumSelectionBottomBar(
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
private fun SmallAlbumEmptyState(
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
private fun SmallAlbumGridMediaTile(
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
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clipToBounds()
            .graphicsLayer {
                scaleX = itemScale
                scaleY = itemScale
            }
            .onGloballyPositioned { coordinates ->
                onBoundsChange(coordinates.boundsInRoot())
            }
            .background(Color.Transparent)
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
                    .background(colors.viewerBackground.copy(alpha = if (selected) 0.18f else 0.06f)),
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

private fun postDetailMediaContentDescription(mediaType: AppMediaType): String {
    return when (mediaType) {
        AppMediaType.VIDEO -> "小相册视频"
        AppMediaType.IMAGE -> "小相册照片"
    }
}

private fun visibleSmallAlbumVideoIds(
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

private fun centeredSmallAlbumVideoId(
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

private fun PhotoFeedItem.smallAlbumGridVideoBadgeDurationMillis(
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
private fun SmallAlbumDensityMorphOverlay(
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

private fun visibleSmallAlbumDensityTransitionCandidates(
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

private fun smallAlbumDensityFallbackContentScale(
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

private fun smallAlbumSourceMediaAlpha(progress: Float): Float {
    return smallAlbumLerpFloat(
        start = 1f,
        end = 0.86f,
        progress = progress.coerceIn(0f, 1f),
    )
}

private fun smallAlbumTargetSceneAlpha(progress: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    return smallAlbumSmoothStep(((t - 0.02f) / 0.90f).coerceIn(0f, 1f))
}

private fun smallAlbumCommitTargetOverlayAlpha(
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

private fun smallAlbumSmoothStep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun smallAlbumLerpFloat(
    start: Float,
    end: Float,
    progress: Float,
): Float {
    return start + (end - start) * progress
}

private fun smallAlbumInterpolateRect(
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

private fun smallAlbumMorphOverlayViewportAlpha(
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

private fun buildSmallAlbumPredictedLocalBoundsByMediaId(
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

private fun buildSmallAlbumPredictedBoundsByMediaId(
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
private fun SmallAlbumNewBadge(
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

private fun smallAlbumSectionSpacing(density: PhotoFeedDensity): Dp {
    return smallAlbumRowSpacing(density)
}

private fun smallAlbumRowSpacing(density: PhotoFeedDensity): Dp {
    return rowSpacing(density)
}

private fun smallAlbumThumbnailRequestSize(density: PhotoFeedDensity): Int {
    return photoFeedThumbnailRequestSize(density)
}

@Composable
private fun SmallAlbumMonthHeaderRow(title: String) {
    PhotoFeedSectionHeaderRow(title = title)
}

@Composable
private fun SmallAlbumDayHeaderRow(title: String) {
    PhotoFeedDayHeaderRow(title = title)
}

internal fun String?.meaningfulPostSummaryOrNull(): String? {
    val normalized = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return normalized.takeUnless {
        it == "还没有简介" || it == "杩樻病鏈夌畝浠?"
    }
}

@Composable
private fun SmallAlbumSummaryText(
    summary: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp,
    )
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val canExpand = remember(summary, maxWidthPx, textStyle) {
            maxWidthPx > 0 && summaryExceedsTwoLines(
                summary = summary,
                textMeasurer = textMeasurer,
                textStyle = textStyle,
                maxWidthPx = maxWidthPx,
            )
        }
        val annotatedText = remember(summary, expanded, canExpand, maxWidthPx, textStyle) {
            buildSmallAlbumSummaryAnnotatedString(
                summary = summary,
                expanded = expanded,
                canExpand = canExpand,
                textMeasurer = textMeasurer,
                textStyle = textStyle,
                maxWidthPx = maxWidthPx,
                actionColor = colors.memoryAccent,
            )
        }
        Text(
            text = annotatedText,
            modifier = if (canExpand) Modifier.clickable(onClick = onToggleExpanded) else Modifier,
            style = textStyle,
            color = colors.titleAccent.copy(alpha = 0.94f),
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Clip,
        )
    }
}

private fun summaryExceedsTwoLines(
    summary: String,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Int,
): Boolean {
    if (maxWidthPx <= 0) return false
    return textMeasurer.measure(
        text = summary,
        style = textStyle,
        constraints = Constraints(maxWidth = maxWidthPx),
        maxLines = 2,
    ).hasVisualOverflow
}

private fun buildSmallAlbumSummaryAnnotatedString(
    summary: String,
    expanded: Boolean,
    canExpand: Boolean,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Int,
    actionColor: Color,
): androidx.compose.ui.text.AnnotatedString {
    val actionLabel = if (expanded) " 收起" else " 展开"
    val actionStyle = SpanStyle(
        color = actionColor,
        fontWeight = FontWeight.SemiBold,
    )
    if (!canExpand || maxWidthPx <= 0) {
        return buildAnnotatedString { append(summary) }
    }
    if (expanded) {
        return buildAnnotatedString {
            append(summary)
            withStyle(actionStyle) {
                append(actionLabel)
            }
        }
    }

    val suffix = "…$actionLabel"
    var low = 0
    var high = summary.length
    var best = 0
    while (low <= high) {
        val middle = (low + high) / 2
        val candidate = summary.take(middle).trimForSummaryPreview() + suffix
        val fits = !textMeasurer.measure(
            text = candidate,
            style = textStyle,
            constraints = Constraints(maxWidth = maxWidthPx),
            maxLines = 2,
        ).hasVisualOverflow
        if (fits) {
            best = middle
            low = middle + 1
        } else {
            high = middle - 1
        }
    }
    val preview = summary.take(best).trimForSummaryPreview().ifBlank { summary.take(1) }
    return buildAnnotatedString {
        append(preview)
        append("…")
        withStyle(actionStyle) {
            append(actionLabel)
        }
    }
}

private fun String.trimForSummaryPreview(): String {
    return trimEnd { character ->
        character == ' ' ||
            character == '\n' ||
            character == '，' ||
            character == '。' ||
            character == '、' ||
            character == ',' ||
            character == '.'
    }
}

private fun PostDetailUiModel.toSmallAlbumFeedItems(): List<PhotoFeedItem> {
    return mediaItems.toSmallAlbumFeedItems(postId)
}

private fun List<PostDetailMediaUiModel>.toSmallAlbumFeedItems(postId: String): List<PhotoFeedItem> {
    return map { media ->
        val parts = postViewerDateParts(media.displayTimeMillis)
        PhotoFeedItem(
            mediaId = media.id,
            mediaDisplayTimeMillis = media.displayTimeMillis,
            displayYear = parts.year,
            displayMonth = parts.month,
            displayDay = parts.day,
            commentCount = media.commentCount,
            smallAlbumIds = listOf(postId),
            uploadedByUserId = media.uploadedByUserId,
            palette = media.palette,
            mediaType = media.mediaType,
            aspectRatio = media.aspectRatio,
            width = media.width,
            height = media.height,
            videoDurationMillis = media.videoDurationMillis,
            capturedAtMillis = media.capturedAtMillis,
            importedAtMillis = media.importedAtMillis,
            displayTimeSource = media.displayTimeSource,
            mediaSource = media.mediaSource,
        )
    }
}

private fun PostDetailUiModel.toDetailRoute(): PostDetailPlaceholderRoute {
    return PostDetailPlaceholderRoute(
        postId = postId,
        albumId = albumIds.firstOrNull().orEmpty(),
        albumIds = albumIds,
        title = title,
        summary = summary,
        postDisplayTimeMillis = postDisplayTimeMillis,
        mediaCount = mediaItems.size,
        coverPalette = mediaItems.firstOrNull()?.palette ?: realPaletteFor(postId),
        coverMediaType = mediaItems.firstOrNull()?.mediaType ?: AppMediaType.IMAGE,
        coverAspectRatio = mediaItems.firstOrNull()?.displayAspectRatio() ?: 1f,
    )
}

private fun PostDetailUiModel.toInPostViewerRoute(initialIndex: Int): PhotoViewerRoute {
    val sortedMediaItems = mediaItems.sortedForSmallAlbumDisplay()
    return PhotoViewerRoute(
        mediaItems = sortedMediaItems.toSmallAlbumFeedItems(postId),
        initialIndex = initialIndex,
        sourceLabel = title,
        showSmallAlbumSegments = true,
        sourceSmallAlbumRoute = PostDetailPlaceholderRoute(
            postId = postId,
            albumId = albumIds.firstOrNull() ?: "viewer-post",
            albumIds = albumIds.ifEmpty { listOf("viewer-post") },
            title = title,
            summary = summary,
            postDisplayTimeMillis = postDisplayTimeMillis,
            mediaCount = mediaItems.size,
            coverPalette = mediaItems.firstOrNull()?.palette ?: realPaletteFor(postId),
            coverMediaType = mediaItems.firstOrNull()?.mediaType ?: AppMediaType.IMAGE,
            coverAspectRatio = mediaItems.firstOrNull()?.displayAspectRatio() ?: 1f,
        ),
    )
}

private fun List<PostDetailMediaUiModel>.sortedForSmallAlbumDisplay(): List<PostDetailMediaUiModel> {
    return sortedWith(
        compareByDescending<PostDetailMediaUiModel> { it.displayTimeMillis }
            .thenByDescending { it.id },
    )
}

private data class PostViewerDateParts(
    val year: Int,
    val month: Int,
    val day: Int,
)

private fun postViewerDateParts(timeMillis: Long): PostViewerDateParts {
    val calendar = Calendar.getInstance(Locale.CHINA).apply {
        this.timeInMillis = timeMillis
    }
    return PostViewerDateParts(
        year = calendar.get(Calendar.YEAR),
        month = calendar.get(Calendar.MONTH) + 1,
        day = calendar.get(Calendar.DAY_OF_MONTH),
    )
}

fun formatPostTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}

@Preview(showBackground = true)
@Composable
private fun PostDetailScreenPreview() {
    YingShiTheme {
        PostDetailScreen(
            route = FakeAlbumRepository.toPostDetailRoute(FakeAlbumRepository.getPosts().first()),
            onBack = { },
            onOpenGearEdit = { },
        )
    }
}
