package com.example.yingshi.feature.photos

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
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
        } else {
            SmallAlbumDetailContent(
                detail = detail,
                highlightMediaIds = route.highlightMediaIds,
                focusMediaId = route.focusMediaId,
                feedbackNonce = route.feedbackNonce,
                onBack = onBack,
                onOpenGearEdit = { onOpenGearEdit(GearEditRoute(route.postId)) },
                onOpenMediaViewer = { page -> inPostViewerInitialPage = page },
                onOpenSmallAlbumComments = { showSmallAlbumComments = true },
                modifier = Modifier.fillMaxSize(),
            )

            if (showSmallAlbumComments) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.18f))
                        .clickable { showSmallAlbumComments = false },
                )
                FakeSmallAlbumCommentSheet(
                    postId = detail.postId,
                    onClose = { showSmallAlbumComments = false },
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
                        .background(Color.Black.copy(alpha = 0.18f))
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
    var inPostViewerInitialPage by rememberSaveable(route.postId) {
        mutableStateOf<Int?>(null)
    }
    var mediaCommentPage by rememberSaveable(route.postId) {
        mutableStateOf<Int?>(null)
    }
    var showSmallAlbumComments by rememberSaveable(route.postId) {
        mutableStateOf(false)
    }

    val detail = detailWithEntryNotice
    val detailMediaIds = detail?.mediaItems?.map { it.id }.orEmpty()
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val viewerInitialPage = inPostViewerInitialPage
        if (viewerInitialPage != null && detail != null) {
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
        } else {
            when {
                uiState.tokenMissing -> {
                    PostDetailInfoState(
                        title = "真实模式需要登录",
                        message = uiState.errorMessage
                            ?: "请先到后端联调诊断页登录，再打开这个小相册。",
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
                        ?: "读取后端小相册详情失败。"
                    PostDetailInfoState(
                        title = "后端请求失败",
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
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            if (showSmallAlbumComments && detail != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.18f))
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
                        .background(Color.Black.copy(alpha = 0.18f))
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hasMedia = detail.mediaItems.isNotEmpty()
    val sessionVersion = AuthSessionManager.sessionVersion
    val accessToken = remember(sessionVersion) {
        AuthSessionManager.getAccessToken()?.takeIf { it.isNotBlank() }
    }
    val feedItems = remember(detail.mediaItems) { detail.toSmallAlbumFeedItems() }
    val pageStateStore = remember(detail.postId) { PhotoFeedPageStateStore() }
    var detailScrollTrigger by remember(detail.postId) { mutableIntStateOf(0) }
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
            pageStateStore.pendingScrollTargetMediaId = targetId
            pageStateStore.pendingHighlightNonce = feedbackNonce
            pageStateStore.pendingLocateSuccessMessage = null
            pageStateStore.pendingLocateFailureMessage = null
            detailScrollTrigger += 1
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
    SmallAlbumDetailBodyLayout(
        modifier = modifier,
        topBar = {
            SmallAlbumDetailTopBar(
                onBack = onBack,
                onExport = {
                    Toast.makeText(context, "导出能力暂时仍为占位。", Toast.LENGTH_SHORT).show()
                },
                onEdit = onOpenGearEdit,
                onOpenComments = onOpenSmallAlbumComments,
            )
        },
        notice = {
            detail.entryNotice?.takeIf { showEntryNotice }?.let { message ->
                PostInlineNotice(text = message)
            }
            resultNotice?.let { message ->
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
                onLoadAllOriginals = {
                    Toast.makeText(context, "开始加载小相册原图", Toast.LENGTH_SHORT).show()
                    coroutineScope.launch {
                        val summary = RealOriginalLoadRepository.loadAllOriginals(
                            context = context,
                            targets = originalTargets,
                            accessToken = accessToken,
                        )
                        val message = "已加载 ${summary.successCount} 张原图，" +
                            "${summary.skippedCount} 张无原图，${summary.failedCount} 张失败"
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        },
        mediaSection = {
            SmallAlbumPhotoFeedSection(
                feedItems = feedItems,
                pageStateStore = pageStateStore,
                scrollTrigger = detailScrollTrigger,
                onOpenMediaViewer = onOpenMediaViewer,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        commentSummary = {
            SmallAlbumCommentSummaryCard(
                commentCount = uiState.postComments.comments.size,
                isLoading = uiState.postComments.isLoading,
                statusMessage = uiState.postComments.statusMessage,
                errorMessage = uiState.postComments.errorMessage,
                onOpenComments = onOpenSmallAlbumComments,
                onRetry = onRetrySmallAlbumComments,
                label = "小相册评论",
                emptyText = "评论入口已经移到右上角，这里只保留当前状态提示。",
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

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
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
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "这些评论只属于当前媒体 ${media.id.takeLast(6)}。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
) {
    val spacing = YingShiThemeTokens.spacing
    val copyComment = rememberCommentCopyHandler()
    var expanded by rememberSaveable(stateKeyPrefix) { mutableStateOf(false) }
    var actionCommentId by rememberSaveable(stateKeyPrefix) { mutableStateOf<String?>(null) }
    var editingCommentId by rememberSaveable(stateKeyPrefix) { mutableStateOf<String?>(null) }
    var editingDraft by rememberSaveable(stateKeyPrefix) { mutableStateOf("") }
    var selectedCommentId by rememberSaveable(stateKeyPrefix) { mutableStateOf<String?>(null) }
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

    if (state.statusMessage != null) {
        Text(
            text = state.statusMessage,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    if (state.errorMessage != null) {
        PostInlineNotice(
            text = state.errorMessage,
            actionLabel = "重试",
            onAction = onRetry,
        )
    }

    when {
        state.isLoading -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = "正在读取评论…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        visibleComments.isEmpty() -> {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        editingDraft = ""
                        actionCommentId = comment.id
                    },
                    onClick = {
                        if (selectedCommentId != null) {
                            selectedCommentId = null
                            selectedCommentValue = TextFieldValue("")
                        }
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
                        editingDraft = ""
                        actionCommentId = null
                    },
                    onEdit = {
                        editingCommentId = comment.id
                        editingDraft = comment.content
                        selectedCommentId = null
                        selectedCommentValue = TextFieldValue("")
                        actionCommentId = null
                    },
                    onDelete = {
                        onDeleteComment(comment.id)
                        if (selectedCommentId == comment.id) {
                            selectedCommentId = null
                            selectedCommentValue = TextFieldValue("")
                        }
                        if (editingCommentId == comment.id) {
                            editingCommentId = null
                            editingDraft = ""
                        }
                        actionCommentId = null
                    },
                    isEditing = editingCommentId == comment.id,
                    editingValue = if (editingCommentId == comment.id) editingDraft else comment.content,
                    onEditingValueChange = { editingDraft = it },
                    onSaveEdit = {
                        onUpdateComment(comment.id, editingDraft)
                        editingCommentId = null
                        editingDraft = ""
                        actionCommentId = null
                    },
                    onCancelEdit = {
                        editingCommentId = null
                        editingDraft = ""
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    CommentInputBar(
        stateKey = "$stateKeyPrefix-input",
        placeholder = "写一条评论",
        onSend = onCreateComment,
    )
}

@Composable
private fun PostDetailLoadingState(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PostDetailInfoState(
        title = "正在读取小相册详情",
        message = "正在从后端获取小相册详情和评论…",
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        SmallAlbumDetailTopBar(
            onBack = onBack,
            onExport = {},
            onEdit = {},
            onOpenComments = {},
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        ) {
            Column(
                modifier = Modifier.padding(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                if (actionLabel != null && onAction != null) {
                    TextButton(onClick = onAction) {
                        Text(actionLabel)
                    }
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun PostDetailMissingState(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            onBack = onBack,
            onExport = {},
            onEdit = {},
            onOpenComments = {},
        )
        Text(
            text = "当前小相册没有可展示的媒体，可能已经被删除、被移出关系，或仍处于系统删除状态。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val feedItems = remember(detail.mediaItems) { detail.toSmallAlbumFeedItems() }
    val pageStateStore = remember(detail.postId) { PhotoFeedPageStateStore() }
    var detailScrollTrigger by remember(detail.postId) { mutableIntStateOf(0) }
    val postMediaIds = remember(detail.mediaItems) {
        detail.mediaItems.map { it.id }
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
    LaunchedEffect(detail.postId, postMediaIds, focusMediaId, highlightKey) {
        val targetId = focusMediaId ?: highlightMediaIds.firstOrNull()
        if (targetId.isNullOrBlank() || detail.mediaItems.isEmpty()) return@LaunchedEffect
        val targetIndex = detail.mediaItems.indexOfFirst { it.id == targetId }
        if (targetIndex >= 0) {
            pageStateStore.pendingScrollTargetMediaId = targetId
            pageStateStore.pendingHighlightNonce = feedbackNonce
            detailScrollTrigger += 1
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
    val postOriginalSummary = FakeOriginalLoadRepository.getPostSummary(postMediaIds)

    SmallAlbumDetailBodyLayout(
        modifier = modifier,
        topBar = {
            SmallAlbumDetailTopBar(
                onBack = onBack,
                onExport = {
                    Toast.makeText(context, "导出 / 保存将在后续阶段接入", Toast.LENGTH_SHORT).show()
                },
                onEdit = onOpenGearEdit,
                onOpenComments = onOpenSmallAlbumComments,
            )
        },
        notice = {
            detail.entryNotice?.takeIf { showEntryNotice }?.let { message ->
                PostInlineNotice(text = message)
            }
            resultNotice?.let { message ->
                PostInlineNotice(text = message)
            }
        },
        infoSection = {
            SmallAlbumInfoSection(
                detail = detail,
                originalSummary = postOriginalSummary,
                onLoadAllOriginals = {
                    FakeOriginalLoadRepository.loadAllOriginals(postMediaIds)
                    Toast.makeText(context, "开始加载小相册原图", Toast.LENGTH_SHORT).show()
                },
            )
        },
        mediaSection = {
            SmallAlbumPhotoFeedSection(
                feedItems = feedItems,
                pageStateStore = pageStateStore,
                scrollTrigger = detailScrollTrigger,
                onOpenMediaViewer = onOpenMediaViewer,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        commentSummary = {
            SmallAlbumCommentSummaryCard(
                commentCount = detail.comments.size,
                isLoading = false,
                statusMessage = null,
                errorMessage = null,
                onOpenComments = onOpenSmallAlbumComments,
                onRetry = null,
                label = "小相册评论",
                emptyText = "评论入口已经移到右上角，这里只保留当前状态提示。",
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
    Column(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = spacing.lg)
            .padding(top = spacing.xs, bottom = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        topBar()
        notice()
        infoSection()
        commentSummary()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            mediaSection()
        }
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
    onBack: () -> Unit,
    onExport: () -> Unit,
    onEdit: () -> Unit,
    onOpenComments: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PostCircleButton(text = "<", onClick = onBack)
        Text(
            text = "小相册详情",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
        PostActionChip(text = "导出/保存", onClick = onExport)
        SmallAlbumCommentButton(onClick = onOpenComments)
        PostCircleButton(text = "齿", onClick = onEdit)
    }
}

@Composable
private fun SmallAlbumCommentButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.ModeComment,
                contentDescription = "打开小相册评论",
                tint = MaterialTheme.colorScheme.onSurface,
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

    BoxWithConstraints(
        modifier = modifier
            .clickable(onClick = onClick)
            .background(Color.White),
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
            contentDescription = media.id,
            requestSize = 720,
            showStatusBadge = true,
            contentScale = ContentScale.Fit,
            originalLoadState = originalLoadState,
            onOriginalLoadStateChange = onOriginalLoadStateChange,
        )
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
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                ) {
                    Text(
                        text = "新加入",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
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
    onLoadAllOriginals: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = detail.contributorLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = detail.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val summary = detail.summary.meaningfulPostSummaryOrNull()
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "还没有简介，内容可以慢慢补上。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                )
            }
            Text(
                text = formatPostTime(detail.postDisplayTimeMillis),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                detail.albumChips.forEach { chip ->
                    PostMetaCapsule(text = chip)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PostActionChip(text = originalSummary.buttonLabel, onClick = onLoadAllOriginals)
            }
        }
    }
}

@Composable
fun PostInfoSection(
    detail: PostDetailUiModel,
    originalSummary: PostOriginalLoadSummary,
    onLoadAllOriginals: () -> Unit,
) {
    SmallAlbumInfoSection(
        detail = detail,
        originalSummary = originalSummary,
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
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (detail.mediaItems.isNotEmpty()) {
                Text(
                    text = "${currentPage + 1} / ${detail.mediaItems.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val context = LocalContext.current
    val copyComment = rememberCommentCopyHandler()
    val comments = CommentGateway.getPostComments(postId)
    var expanded by rememberSaveable(postId) { mutableStateOf(false) }
    var actionCommentId by rememberSaveable(postId) { mutableStateOf<String?>(null) }
    var editingCommentId by rememberSaveable(postId) { mutableStateOf<String?>(null) }
    var editingDraft by rememberSaveable(postId) { mutableStateOf("") }
    var selectedCommentId by rememberSaveable(postId) { mutableStateOf<String?>(null) }
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
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
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
                        text = "小相册评论",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "这些评论属于当前小相册，不和媒体评论混在一起。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PostActionChip(text = "关闭", onClick = onClose)
            }
            if (visibleComments.isEmpty()) {
                Text(
                    text = "还没有评论。可以写下第一句，也可以先安静地留着。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            editingDraft = ""
                            actionCommentId = comment.id
                        },
                        onClick = {
                            if (selectedCommentId != null) {
                                selectedCommentId = null
                                selectedCommentValue = TextFieldValue("")
                            }
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
                            editingDraft = ""
                            actionCommentId = null
                        },
                        onEdit = {
                            editingCommentId = comment.id
                            editingDraft = comment.content
                            selectedCommentId = null
                            selectedCommentValue = TextFieldValue("")
                            actionCommentId = null
                        },
                        onDelete = {
                            CommentGateway.deletePostComment(postId, comment.id)
                            if (selectedCommentId == comment.id) {
                                selectedCommentId = null
                                selectedCommentValue = TextFieldValue("")
                            }
                            if (editingCommentId == comment.id) {
                                editingCommentId = null
                                editingDraft = ""
                            }
                            actionCommentId = null
                            Toast.makeText(context, "评论已删除", Toast.LENGTH_SHORT).show()
                        },
                        isEditing = editingCommentId == comment.id,
                        editingValue = if (editingCommentId == comment.id) editingDraft else comment.content,
                        onEditingValueChange = { editingDraft = it },
                        onSaveEdit = {
                            CommentGateway.updatePostComment(
                                postId = postId,
                                commentId = comment.id,
                                content = editingDraft,
                            )
                            editingCommentId = null
                            editingDraft = ""
                            actionCommentId = null
                            Toast.makeText(context, "评论已更新", Toast.LENGTH_SHORT).show()
                        },
                        onCancelEdit = {
                            editingCommentId = null
                            editingDraft = ""
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
            CommentInputBar(
                stateKey = "post-comment-input-$postId",
                placeholder = "写一条小相册评论",
                onSend = { content ->
                    CommentGateway.addPostComment(postId, content)
                    expanded = false
                },
            )
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

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
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
                        text = "小相册评论",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "这些评论只属于当前小相册 ${smallAlbumId.takeLast(6)}。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PostActionChip(text = "关闭", onClick = onClose)
            }

            RealCommentThreadContent(
                state = state,
                stateKeyPrefix = "real-small-album-comment-$smallAlbumId",
                emptyText = "还没有评论。可以写下第一句，也可以先安静地留着。",
                onRetry = onRetry,
                onCreateComment = onCreateComment,
                onUpdateComment = onUpdateComment,
                onDeleteComment = onDeleteComment,
            )
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
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
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = when {
                        isLoading -> "正在读取评论…"
                        !errorMessage.isNullOrBlank() -> errorMessage
                        !statusMessage.isNullOrBlank() -> statusMessage
                        commentCount > 0 -> "当前有 $commentCount 条评论，点击右上角图标查看和输入。"
                        else -> emptyText
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val comments = CommentGateway.getMediaComments(media.id)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
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
                        text = "媒体评论占位",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "只属于当前媒体，不混入小相册评论区",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PostActionChip(text = "关闭", onClick = onClose)
            }

            if (comments.isEmpty()) {
                Text(
                    text = "当前媒体暂无评论，后续接入真实媒体评论系统。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                comments.take(10).forEach { comment ->
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        Text(
                            text = "${comment.author} · ${formatPostTime(comment.createdAtMillis)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = comment.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (comments.size > 10) {
                    Text(
                        text = "更多媒体评论后续接入分页 / 展开能力",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(radius.lg),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
                ) {
                    Text(
                        text = "写一条媒体评论，占位输入入口",
                        modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PostCircleButton(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PostActionChip(
    text: String,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(radius.capsule))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(radius.capsule),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PostMetaCapsule(text: String) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        shape = RoundedCornerShape(radius.capsule),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun SmallAlbumPhotoFeedSection(
    feedItems: List<PhotoFeedItem>,
    pageStateStore: PhotoFeedPageStateStore,
    scrollTrigger: Int,
    onOpenMediaViewer: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mediaPositionLookup = remember(feedItems) {
        feedItems.mapIndexed { index, item -> item.mediaId to index }.toMap()
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
    ) {
        Text(
            text = "媒体",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = if (feedItems.isEmpty()) {
                "这个小相册里还没有媒体。标题和评论会先保留在这里，后续加入照片或视频就会展示出来。"
            } else {
                "按照片流样式浏览这个小相册里的媒体，支持时间分组、密度切换和时间 scrubber。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (feedItems.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "还没有媒体",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                PhotoFeedScreen(
                    feedItems = feedItems,
                    pageStateStore = pageStateStore,
                    modifier = Modifier.fillMaxSize(),
                    selectionState = PhotoFeedSelectionState(),
                    scrollTrigger = scrollTrigger,
                    onOpenViewer = { route ->
                        onOpenMediaViewer(route.initialIndex)
                    },
                )
            }
        }
    }
}

internal fun String?.meaningfulPostSummaryOrNull(): String? {
    val normalized = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return normalized.takeUnless {
        it == "还没有简介" || it == "杩樻病鏈夌畝浠?"
    }
}

private fun PostDetailUiModel.toSmallAlbumFeedItems(): List<PhotoFeedItem> {
    return mediaItems.map { media ->
        val parts = postViewerDateParts(media.displayTimeMillis)
        PhotoFeedItem(
            mediaId = media.id,
            mediaDisplayTimeMillis = media.displayTimeMillis,
            displayYear = parts.year,
            displayMonth = parts.month,
            displayDay = parts.day,
            commentCount = media.commentCount,
            smallAlbumIds = listOf(postId),
            palette = media.palette,
            mediaType = media.mediaType,
            aspectRatio = media.aspectRatio,
            width = media.width,
            height = media.height,
            videoDurationMillis = media.videoDurationMillis,
            mediaSource = media.mediaSource,
        )
    }
}

private fun PostDetailUiModel.toInPostViewerRoute(initialIndex: Int): PhotoViewerRoute {
    return PhotoViewerRoute(
        mediaItems = toSmallAlbumFeedItems(),
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

