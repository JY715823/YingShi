package com.example.yingshi.feature.photos

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshi.data.model.RemoteTrashDetail
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
fun RealTrashDetailScreen(
    route: TrashDetailRoute,
    onBack: () -> Unit,
    onEntryRemoved: () -> Unit,
    onEntryRestored: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessionKey = realBackendSessionKey("real-trash-detail-${route.entryId}")
    val viewModel: RealTrashDetailViewModel = viewModel(
        key = sessionKey,
        factory = RealTrashDetailViewModel.factory(route),
    )
    val uiState by viewModel.uiState.collectAsState()
    val backendMutationEvent by RealBackendMutationBus.latestEvent.collectAsState()
    val detail = uiState.detail
    val spacing = YingShiThemeTokens.spacing
    val collaboratorDirectory = rememberCollaboratorDirectorySnapshot(fallbackToFakeProfile = false)

    LaunchedEffect(backendMutationEvent.version) {
        if (backendMutationEvent.version > 0 && backendMutationEvent.affectsTrash()) {
            viewModel.refresh()
        }
    }
    ReconnectRefreshEffect(
        shouldRefresh = uiState.isOfflineReadOnly ||
            uiState.errorMessage != null ||
            uiState.tokenMissing ||
            (uiState.isLoading && uiState.detail == null),
        onReconnect = viewModel::refresh,
    )

    val mediaDetailEntry = detail?.item?.toTrashEntryUiModel()
    if (mediaDetailEntry?.type?.isRealMediaTrashType() == true) {
        val sameTypeEntries by produceState(listOf(mediaDetailEntry), detail.item.itemType, detail.item.trashItemId) {
            value = when (val result = RepositoryProvider.trashRepository.getTrashItems(detail.item.itemType)) {
                is ApiResult.Success -> result.data
                    .map { it.toTrashEntryUiModel() }
                    .filter { it.type == mediaDetailEntry.type && it.mediaSnapshot != null }
                    .distinctBy { it.businessIdentityKey() }
                    .ifEmpty { listOf(mediaDetailEntry) }
                else -> listOf(mediaDetailEntry)
            }
        }
        RealTrashMediaViewerDetailPagerContent(
            detail = detail,
            directory = collaboratorDirectory,
            entries = sameTypeEntries,
            statusMessage = uiState.statusMessage,
            errorMessage = uiState.errorMessage,
            isMutating = uiState.isMutating,
            onBack = onBack,
            onRestoreEntry = { targetEntry ->
                viewModel.restoreItem(targetEntry.id) { restoredItem ->
                    val mediaIds = restoredItem.toTrashEntryUiModel().restoreTargetMediaIds()
                    onEntryRestored(mediaIds)
                }
            },
            onRemoveEntry = { targetEntry -> viewModel.removeItem(targetEntry.id, onEntryRemoved) },
            modifier = modifier,
        )
        return
    }
    if (detail != null && mediaDetailEntry?.type == TrashEntryType.SMALL_ALBUM_DELETED) {
        RealTrashPostViewerDetailContent(
            detail = detail,
            directory = collaboratorDirectory,
            statusMessage = uiState.statusMessage,
            errorMessage = uiState.errorMessage,
            isMutating = uiState.isMutating,
            isOfflineReadOnly = uiState.isOfflineReadOnly,
            onBack = onBack,
            onRestore = {
                viewModel.restore { restoredItem ->
                    val mediaIds = restoredItem.toTrashEntryUiModel().restoreTargetMediaIds()
                    onEntryRestored(mediaIds)
                }
            },
            onRemove = { viewModel.remove(onEntryRemoved) },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        val actorIdentity = detail?.item?.toTrashEntryUiModel()?.let {
            resolveTrashActorIdentity(it, collaboratorDirectory)
        }
        RealTrashDetailTopBar(
            title = detail?.item?.title?.ifBlank { "回收站详情" } ?: "回收站详情",
            onBack = onBack,
            isMutating = uiState.isMutating,
            isOfflineReadOnly = uiState.isOfflineReadOnly,
        )
        if (uiState.isOfflineReadOnly) {
            RealTrashOfflineBanner(message = "当前离线，仅可查看回收站内容。")
        }

        when {
            uiState.tokenMissing -> {
                RealTrashSectionCard(
                    title = "需要重新登录",
                    body = uiState.errorMessage ?: "请先完成登录后再查看回收站。",
                )
            }
            uiState.isLoading && detail == null -> {
                RealTrashSectionCard(
                    title = "读取中",
                    body = "正在读取回收站详情…",
                )
            }
            uiState.errorMessage != null && detail == null -> {
                RealTrashSectionCard(
                    title = "读取失败",
                    body = uiState.errorMessage ?: "当前无法读取回收站详情。",
                )
            }
            detail == null -> {
                RealTrashSectionCard(
                    title = "详情不可用",
                    body = "这个回收站项目可能已经被恢复或移出。",
                )
            }
            else -> {
                RealTrashDetailContent(
                    detail = detail,
                    actorIdentity = actorIdentity,
                    statusMessage = uiState.statusMessage,
                    errorMessage = uiState.errorMessage,
                    isMutating = uiState.isMutating,
                    isOfflineReadOnly = uiState.isOfflineReadOnly,
                    onRestore = {
                        viewModel.restore { restoredItem ->
                            val mediaIds = restoredItem.toTrashEntryUiModel().restoreTargetMediaIds()
                            onEntryRestored(mediaIds)
                        }
                    },
                    onRemove = { viewModel.remove(onEntryRemoved) },
                    onUndoRemove = viewModel::undoRemove,
                )
            }
        }
    }
}

@Composable
private fun RealTrashPostViewerDetailContent(
    detail: RemoteTrashDetail,
    directory: CollaboratorDirectorySnapshot,
    statusMessage: String?,
    errorMessage: String?,
    isMutating: Boolean,
    isOfflineReadOnly: Boolean,
    onBack: () -> Unit,
    onRestore: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = detail.item
    val entry = item.toTrashEntryUiModel()
    val actorIdentity = remember(entry, directory) {
        resolveTrashActorIdentity(entry, directory)
    }
    var showPermanentDeleteConfirm by remember(item.trashItemId) {
        mutableStateOf(false)
    }
    var showRestoreConfirm by remember(item.trashItemId) {
        mutableStateOf(false)
    }
    var selectedMediaId by remember(item.trashItemId) {
        mutableStateOf<String?>(null)
    }
    val mediaIds = entry.previewMediaIds()
    val postDetail = remember(entry, mediaIds) {
        entry.toTrashPostDetailUiModel(mediaIds)
    }
    val remoteFallbackDetail = remember(detail.item.trashItemId, detail.item.relatedMediaIds) {
        entry.toTrashPostDetailUiModelFromRemote(detail)
    }
    val effectivePostDetail = remember(postDetail, remoteFallbackDetail) {
        if (postDetail.mediaItems.isNotEmpty()) postDetail else remoteFallbackDetail
    }
    val mediaPagerState = rememberPagerState(
        pageCount = { effectivePostDetail.mediaItems.size.coerceAtLeast(1) },
    )
    val currentMediaPage = mediaPagerState.currentPage.coerceIn(
        0,
        (effectivePostDetail.mediaItems.size - 1).coerceAtLeast(0),
    )
    val currentMedia = effectivePostDetail.mediaItems.getOrNull(currentMediaPage)
    val originalTargets = remember(effectivePostDetail.mediaItems) {
        effectivePostDetail.mediaItems.map {
            RealOriginalMediaTarget(
                mediaId = it.id,
                mediaType = it.mediaType,
                mediaSource = it.mediaSource,
            )
        }
    }
    val originalSummary = RealOriginalLoadRepository.getPostSummaryForTargets(originalTargets)
    val postCommentsState by produceState<List<CommentUiModel>>(emptyList(), entry.sourcePostId) {
        val postId = entry.sourcePostId?.takeIf { it.isNotBlank() } ?: return@produceState
        value = when (val result = CommentGateway.repository.getPostComments(postId)) {
            is ApiResult.Success -> result.data.comments
                .filterNot { it.isDeleted }
                .map { it.toCommentUiModel() }
                .sortedByDescending { it.createdAtMillis }
            else -> emptyList()
        }
    }

    BackHandler(enabled = selectedMediaId != null) {
        selectedMediaId = null
    }

    if (selectedMediaId != null) {
        RealTrashPostMediaViewerOverlay(
            entry = entry,
            actorIdentity = actorIdentity,
            mediaId = selectedMediaId,
            isMutating = isMutating,
            onBack = { selectedMediaId = null },
            onRestorePost = { showRestoreConfirm = true },
            onRequestDeletePost = { showPermanentDeleteConfirm = true },
        )
    } else {
        PostDetailBodyLayout(
            modifier = modifier
                .fillMaxSize(),
            topBar = {
                Column {
                    RealTrashDetailTopBar(
                        title = entry.title.ifBlank { "小相册详情" },
                        onBack = onBack,
                        onRestore = if (detail.canRestore) { { showRestoreConfirm = true } } else null,
                        onRemove = if (detail.canMoveOutOfTrash) { { showPermanentDeleteConfirm = true } } else null,
                        isMutating = isMutating,
                        isOfflineReadOnly = isOfflineReadOnly,
                    )
                    if (isOfflineReadOnly) {
                        RealTrashOfflineBanner(message = "当前离线，仅可查看回收站内容。")
                    }
                }
            },
            mediaArea = {
                if (effectivePostDetail.mediaItems.isEmpty()) {
                    RealTrashSectionCard(
                        title = "小相册媒体不可用",
                        body = if (effectivePostDetail.mediaItems.isEmpty()) {
                            "这个小相册的媒体已经无法预览。"
                        } else {
                            "当前回收站详情为精简模式，已显示可恢复的媒体摘要。"
                        },
                    )
                } else {
                    PostMediaArea(
                        detail = effectivePostDetail,
                        currentPage = currentMediaPage,
                        modifier = Modifier.fillMaxWidth(),
                        onOpenMedia = { currentMedia?.id?.let { selectedMediaId = it } },
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val mediaAspectRatio = (currentMedia?.aspectRatio ?: 1f).coerceIn(0.5f, 2.5f)
                            val dynamicHeight = (maxWidth / mediaAspectRatio).coerceIn(240.dp, 520.dp)
                            HorizontalPager(
                                state = mediaPagerState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(dynamicHeight),
                                beyondViewportPageCount = 1,
                                key = { page -> effectivePostDetail.mediaItems[page].id },
                            ) { page ->
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    PostMediaCard(
                                        media = effectivePostDetail.mediaItems[page],
                                        originalLoadState = RealOriginalLoadRepository.getState(originalTargets[page]),
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = { selectedMediaId = effectivePostDetail.mediaItems[page].id },
                                    )
                                }
                            }
                        }
                    }
                }
            },
            mediaInfo = {
                if (effectivePostDetail.mediaItems.isNotEmpty() && currentMedia != null) {
                    val target = originalTargets[currentMediaPage]
                    PostMediaInfoRow(
                        media = currentMedia,
                        commentCount = 0, // R3-APP-001: Real comment count available in post detail, not trash context
                        originalLoadState = RealOriginalLoadRepository.getState(target),
                        showOriginalAction = currentMedia.mediaType == AppMediaType.IMAGE,
                        onCommentClick = { selectedMediaId = currentMedia.id },
                        onOriginalClick = { RealOriginalLoadRepository.setState(target, OriginalLoadState.Loaded) },
                    )
                }
            },
            postInfo = {
                Column(verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm)) {
                    statusMessage?.let { RealTrashSectionCard(title = "操作结果", body = it, emphasized = true) }
                    errorMessage?.let { RealTrashSectionCard(title = "操作失败", body = it) }
                    PostInfoSection(
                        detail = effectivePostDetail,
                        originalSummary = originalSummary,
                        onOpenComments = {},
                        onLoadAllOriginals = {
                            originalTargets.forEach { RealOriginalLoadRepository.setState(it, OriginalLoadState.Loaded) }
                        },
                    )
                }
            },
            comments = {
                RealTrashReadOnlyCommentCard(
                    title = "小相册评论",
                    emptyText = "当前小相册没有可展示的评论。",
                    comments = postCommentsState,
                )
            },
        )
    }

    if (showPermanentDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showPermanentDeleteConfirm = false },
            title = { Text("移出回收站？") },
            text = {
                Text("将把当前小相册移到待清理。24 小时内可撤销，也可以在待清理页永久删除。")
            },
            containerColor = YingShiThemeTokens.colors.raisedSurface,
            titleContentColor = YingShiThemeTokens.colors.titleAccent,
            textContentColor = YingShiThemeTokens.colors.textSecondary,
            confirmButton = {
                TrashDialogActionButton(
                    text = "移出回收站",
                    danger = true,
                    enabled = !isMutating,
                    onClick = {
                        showPermanentDeleteConfirm = false
                        onRemove()
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { showPermanentDeleteConfirm = false })
            },
        )
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("确认恢复？") },
            text = { Text("将恢复当前回收站小相册。") },
            containerColor = YingShiThemeTokens.colors.raisedSurface,
            titleContentColor = YingShiThemeTokens.colors.titleAccent,
            textContentColor = YingShiThemeTokens.colors.textSecondary,
            confirmButton = {
                TrashDialogActionButton(
                    text = "恢复",
                    emphasized = true,
                    enabled = !isMutating,
                    onClick = {
                        showRestoreConfirm = false
                        onRestore()
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { showRestoreConfirm = false })
            },
        )
    }
}

@Composable
private fun RealTrashReadOnlyCommentCard(
    title: String,
    emptyText: String,
    comments: List<CommentUiModel>,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
    ) {
        Column(
            modifier = Modifier.padding(YingShiThemeTokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            if (comments.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            } else {
                comments.take(10).forEach { comment ->
                    Column(verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xxs)) {
                        Text(
                            text = "${comment.author} · ${realFormatTrashEntryTime(comment.createdAtMillis)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.titleAccent,
                        )
                        Text(
                            text = comment.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RealTrashPostMediaViewerOverlay(
    entry: TrashEntryUiModel,
    actorIdentity: CollaboratorIdentityUiModel?,
    mediaId: String?,
    isMutating: Boolean,
    onBack: () -> Unit,
    onRestorePost: () -> Unit,
    onRequestDeletePost: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val accessToken = AuthSessionManager.peekAccessToken()
    val target = mediaId?.takeIf { it.isNotBlank() }?.let {
        RealOriginalMediaTarget(
            mediaId = it,
            mediaType = AppMediaType.IMAGE,
            mediaSource = realTrashMediaSource(it),
        )
    }
    val originalLoadState = target?.let(RealOriginalLoadRepository::getState)
        ?: OriginalLoadState.NotLoaded
    val commentBindings = mediaId?.takeIf { it.isNotBlank() }?.let { rememberViewerCommentBindings(it) }
    var isImmersive by remember { mutableStateOf(false) }
    var showCommentPreview by remember(mediaId) {
        mutableStateOf(false)
    }

    BackHandler(enabled = !isImmersive && showCommentPreview) {
        showCommentPreview = false
    }
    BackHandler(enabled = isImmersive) {
        onBack()
    }
    ViewerStatusBarEffect(immersive = isImmersive)

    fun toggleImmersive() {
        val nextImmersive = !isImmersive
        applyViewerStatusBarVisibility(view, nextImmersive)
        isImmersive = nextImmersive
        if (nextImmersive) {
            showCommentPreview = false
        }
    }

    val topPadding by animateDpAsState(
        targetValue = if (isImmersive) 0.dp else 68.dp,
        label = "trashPostMediaViewerTopPadding",
    )
    val bottomPadding by animateDpAsState(
        targetValue = if (isImmersive) 0.dp else 104.dp,
        label = "trashPostMediaViewerBottomPadding",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(YingShiThemeTokens.colors.viewerBackground)
            .viewerSingleTapGesture { _, _ -> toggleImmersive() },
    ) {
        if (target == null) {
            RealTrashDeletedMediaPlaceholder(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f),
            )
        } else {
            AppContentMediaThumbnail(
                mediaSource = target.mediaSource,
                mediaType = target.mediaType,
                palette = realPaletteFor(target.mediaId),
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .padding(top = topPadding, bottom = bottomPadding),
                contentScale = ContentScale.Fit,
                requestSize = 1080,
                showLoadingIndicator = true,
                showStatusBadge = true,
                originalLoadState = originalLoadState,
                onOriginalLoadStateChange = { state ->
                    RealOriginalLoadRepository.setState(target, state)
                },
            )
        }

        if (!isImmersive) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(
                        horizontal = YingShiThemeTokens.spacing.lg,
                        vertical = YingShiThemeTokens.spacing.md,
                    ),
                horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RealTrashViewerIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                )
                Box(modifier = Modifier.weight(1f))
                actorIdentity?.let {
                    CollaboratorMarkerBadge(
                        identity = it,
                        size = 44.dp,
                    )
                }
                RealTrashViewerIconButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "恢复",
                    enabled = !isMutating,
                    onClick = onRestorePost,
                )
                RealTrashViewerIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "移出回收站",
                    destructive = true,
                    enabled = !isMutating,
                    onClick = onRequestDeletePost,
                )
            }
        }

        if (!isImmersive) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(YingShiThemeTokens.spacing.lg),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
            ) {
                RealTrashViewerMetaCapsule(text = entry.title)
            }
        }

        if (!isImmersive) {
            RealTrashViewerEdgeActions(
                commentCountLabel = commentBindings?.comments?.size?.toString() ?: "0",
                timeLabel = realFormatTrashEntryTime(entry.deletedAtMillis),
                originalActionLabel = if (target != null) {
                    originalLoadState.actionLabel()
                } else {
                    "原媒体不可用"
                },
                originalActionEnabled = target != null && originalLoadState != OriginalLoadState.Loading,
                originalActionEmphasized = target != null && originalLoadState == OriginalLoadState.Loaded,
                previewExpanded = showCommentPreview,
                onToggleComments = { showCommentPreview = !showCommentPreview },
                onOpenOriginal = {
                    if (target != null &&
                        originalLoadState != OriginalLoadState.Loaded &&
                        originalLoadState != OriginalLoadState.Loading
                    ) {
                        RealOriginalLoadRepository.requestOriginal(context, target, accessToken)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        start = YingShiThemeTokens.spacing.lg,
                        end = YingShiThemeTokens.spacing.lg,
                        bottom = 0.dp,
                    ),
            )
        }

        if (showCommentPreview && !isImmersive) {
            RealTrashViewerCommentPreview(
                comments = commentBindings?.comments.orEmpty(),
                isLoading = commentBindings?.isLoading == true,
                errorMessage = commentBindings?.errorMessage,
                onRetry = { commentBindings?.onRetry?.invoke() },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(
                        start = YingShiThemeTokens.spacing.lg,
                        bottom = 64.dp,
                    ),
            )
        }
    }
}

@Composable
private fun RealTrashDetailContent(
    detail: RemoteTrashDetail,
    actorIdentity: CollaboratorIdentityUiModel?,
    statusMessage: String?,
    errorMessage: String?,
    isMutating: Boolean,
    isOfflineReadOnly: Boolean,
    onRestore: () -> Unit,
    onRemove: () -> Unit,
    onUndoRemove: () -> Unit,
) {
    val item = detail.item
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val entryType = item.toTrashEntryUiModel().type
    val entry = remember(item) { item.toTrashEntryUiModel() }
    val relatedPostCount = buildSet {
        item.sourcePostId?.takeIf { it.isNotBlank() }?.let(::add)
        addAll(item.relatedPostIds.filter { it.isNotBlank() })
    }.size
    val relatedMediaCount = buildSet {
        item.sourceMediaId?.takeIf { it.isNotBlank() }?.let(::add)
        item.commentTargetMediaId?.takeIf { it.isNotBlank() }?.let(::add)
        addAll(item.relatedMediaIds.filter { it.isNotBlank() })
    }.size
    var showPermanentDeleteConfirm by remember(item.trashItemId) {
        mutableStateOf(false)
    }

    if (isOfflineReadOnly) {
        RealTrashOfflineBanner(message = "当前离线，仅可查看回收站内容。")
    }
    if (statusMessage != null) {
        RealTrashSectionCard(
            title = "操作结果",
            body = statusMessage,
            emphasized = true,
        )
    }
    if (errorMessage != null) {
        RealTrashSectionCard(
            title = "操作失败",
            body = errorMessage,
        )
    }

    RealTrashSectionCard(
        title = item.title.ifBlank { "回收站项目" },
        body = item.previewInfo.ifBlank { EmptyTrashPreviewMessage },
    )

    RealTrashDeletedPreview(item = item)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            actorIdentity?.let {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CollaboratorMarkerBadge(
                        identity = it,
                        size = 18.dp,
                    )
                    Text(
                        text = if (it.isCurrentUser) "由我移入回收站" else "由对方移入回收站",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
            Text(
                text = "类型：${entryType.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            Text(
                text = "状态：${item.state.toTrashStateLabel()}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            if (relatedPostCount > 0) {
                Text(
                    text = "关联小相册：$relatedPostCount 个",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            if (relatedMediaCount > 0) {
                Text(
                    text = "关联媒体：$relatedMediaCount 项",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                if (detail.canRestore) {
                    RealTrashIconActionButton(
                        text = if (isMutating) "处理中" else "恢复",
                        emphasized = true,
                        enabled = !isMutating && !isOfflineReadOnly,
                        onClick = onRestore,
                    )
                }
                if (detail.canMoveOutOfTrash) {
                    RealTrashIconActionButton(
                        text = if (isMutating) "处理中" else "移出回收站",
                        danger = true,
                        enabled = !isMutating && !isOfflineReadOnly,
                        onClick = { showPermanentDeleteConfirm = true },
                    )
                }
            }
        }
    }

    if (showPermanentDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showPermanentDeleteConfirm = false },
            title = { Text("移出回收站？") },
            text = {
                Text(
                    "将把当前项目移到待清理。24 小时内可撤销，也可以在待清理页永久删除。",
                )
            },
            containerColor = YingShiThemeTokens.colors.raisedSurface,
            titleContentColor = YingShiThemeTokens.colors.titleAccent,
            textContentColor = YingShiThemeTokens.colors.textSecondary,
            confirmButton = {
                TrashDialogActionButton(
                    text = "移出回收站",
                    danger = true,
                    enabled = !isMutating,
                    onClick = {
                        showPermanentDeleteConfirm = false
                        onRemove()
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { showPermanentDeleteConfirm = false })
            },
        )
    }
}

@Composable
private fun RealTrashDeletedPreview(
    item: com.example.yingshi.data.model.RemoteTrashItem,
) {
    val mediaIds = buildList {
        item.sourceMediaId?.takeIf { it.isNotBlank() }?.let(::add)
        addAll(item.relatedMediaIds.filter { it.isNotBlank() })
    }.distinct()
    val entry = item.toTrashEntryUiModel()
    val type = entry.type

    when {
        mediaIds.isNotEmpty() -> {
            RealTrashMediaStrip(
                title = when (type) {
                    TrashEntryType.LARGE_ALBUM_DELETED -> "整册内的媒体"
                    TrashEntryType.SMALL_ALBUM_DELETED -> "原小相册媒体"
                    TrashEntryType.MEDIA_REMOVED -> "被移除的媒体"
                    TrashEntryType.MEDIA_SYSTEM_DELETED -> "被删除的媒体"
                },
                mediaIds = mediaIds,
            )
        }

        type == TrashEntryType.LARGE_ALBUM_DELETED -> {
            val postSnapshots = entry.albumSnapshot?.postSnapshots.orEmpty()
            if (postSnapshots.isNotEmpty()) {
                RealTrashLargeAlbumChildList(postSnapshots = postSnapshots)
            } else {
                RealTrashSectionCard(
                    title = "原大相册内容",
                    body = "这个大相册已进入回收站，恢复时会把同批小相册一起带回。",
                )
            }
        }

        type == TrashEntryType.SMALL_ALBUM_DELETED -> {
            RealTrashSectionCard(
                title = "原小相册内容",
                body = "这个小相册的照片内容已不可查看，仅保留标题和说明。",
            )
        }

        else -> {
            RealTrashSectionCard(
                title = "原媒体预览不可用",
                body = MissingOriginalMediaMessage,
            )
        }
    }
}

@Composable
private fun RealTrashMediaStrip(
    title: String,
    mediaIds: List<String>,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            mediaIds.chunked(3).forEach { rowIds ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    rowIds.forEach { mediaId ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                        ) {
                            AppContentMediaThumbnail(
                                mediaSource = realTrashMediaSource(mediaId),
                                mediaType = AppMediaType.IMAGE,
                                palette = realPaletteFor(mediaId),
                                modifier = Modifier.fillMaxSize(),
                                contentDescription = realTrashViewerMediaContentDescription(AppMediaType.IMAGE),
                                requestSize = 384,
                                showLoadingIndicator = true,
                                showStatusBadge = true,
                            )
                        }
                    }
                    repeat(3 - rowIds.size) {
                        Box(
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
