package com.example.yingshi.feature.photos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.sync.StaleBanner
import com.example.yingshi.feature.sync.SyncModule
import com.example.yingshi.feature.sync.SyncVersionTracker
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PostDetailScreen(
    route: PostDetailPlaceholderRoute,
    onBack: () -> Unit,
    onOpenGearEdit: (GearEditRoute) -> Unit,
    onOpenPostDetail: (PostDetailPlaceholderRoute) -> Unit = {},
    onOpenCacheManagement: (CacheManagementRoute) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
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
            .background(colors.appBackground),
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
internal fun RealPostDetailScreen(
    route: PostDetailPlaceholderRoute,
    onBack: () -> Unit,
    onOpenGearEdit: () -> Unit,
    onOpenPostDetail: (PostDetailPlaceholderRoute) -> Unit,
    onOpenCacheManagement: (CacheManagementRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
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
            .background(colors.appBackground),
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
                        onLoadMore = viewModel::loadMorePostComments,
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
                        onLoadMore = { viewModel.loadMoreMediaComments(media.id) },
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
internal fun RealSmallAlbumDetailContent(
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

internal fun String?.meaningfulPostSummaryOrNull(): String? {
    val normalized = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return normalized.takeUnless {
        it == "还没有简介"
    }
}

private fun PostDetailUiModel.toSmallAlbumFeedItems(): List<PhotoFeedItem> {
    return mediaItems.toSmallAlbumFeedItems(postId)
}

internal fun List<PostDetailMediaUiModel>.toSmallAlbumFeedItems(postId: String): List<PhotoFeedItem> {
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

internal fun PostDetailUiModel.toDetailRoute(): PostDetailPlaceholderRoute {
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

internal fun List<PostDetailMediaUiModel>.sortedForSmallAlbumDisplay(): List<PostDetailMediaUiModel> {
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

internal fun formatPostTime(timeMillis: Long): String {
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
