package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshi.ui.components.YingShiNotice
import com.example.yingshi.ui.components.YingShiNoticeHost
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PhotoFeedDeleteStatusAutoHideMillis = 3200L

@Composable
fun RealPhotoFeedPage(
    selectionState: PhotoFeedSelectionState,
    onSelectionStateChange: (PhotoFeedSelectionState) -> Unit,
    onSelectionShellStateChange: (PhotosRootSelectionUiState) -> Unit = { },
    selectionAction: PhotoSelectionShellAction? = null,
    selectionActionNonce: Int = 0,
    onOpenViewer: (PhotoViewerRoute) -> Unit,
    onOpenCreatePost: (CreatePostRoute) -> Unit,
    onAddedMediaToPost: (PostDetailPlaceholderRoute) -> Unit,
    modifier: Modifier = Modifier,
    scrollTrigger: Int = 0,
    inlineVideoAutoPlayEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionKey = realBackendSessionKey("real-photo-feed")
    val viewModel: RealPhotoFeedViewModel = viewModel(
        key = sessionKey,
        factory = RealPhotoFeedViewModel.factory(),
    )
    val uiState by viewModel.uiState.collectAsState()
    val backendMutationEvent by RealBackendMutationBus.latestEvent.collectAsState()
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showAddToPostDialog by rememberSaveable { mutableStateOf(false) }
    var addToPostError by rememberSaveable { mutableStateOf<String?>(null) }
    var addToPostPendingPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<YingShiNotice?>(null) }
    var noticeNonce by rememberSaveable { mutableStateOf(0) }
    var shareInFlight by remember { mutableStateOf(false) }
    val destinationUiState by rememberSystemMediaDestinationUiState()
    val albums = destinationUiState.albums
    val posts = destinationUiState.posts
    val spacing = YingShiThemeTokens.spacing
    val selectionShellState = if (selectionState.isInSelectionMode) {
        PhotosRootSelectionUiState(
            isActive = true,
            selectedCount = selectionState.selectedCount,
            writeEnabled = !uiState.isOfflineReadOnly,
            isDeleting = uiState.isDeleting,
        )
    } else {
        PhotosRootSelectionUiState()
    }

    fun showNotice(message: String) {
        noticeNonce += 1
        notice = YingShiNotice(message = message, nonce = noticeNonce)
    }
    fun shareSelectedMedia() {
        val selectedItems = uiState.feedItems.filter { item ->
            selectionState.selectedMediaIds.contains(item.mediaId)
        }
        if (selectedItems.isEmpty()) {
            showNotice("没有找到可分享的媒体。")
            return
        }
        if (shareInFlight) {
            showNotice("正在准备分享文件…")
            return
        }
        scope.launch {
            shareInFlight = true
            showNotice("正在准备分享文件…")
            try {
                when (
                    val result = MediaShareManager.shareMedia(
                        context = context,
                        items = selectedItems.map(PhotoFeedItem::toShareableMediaItem),
                        packageBaseName = "映世-照片流-${selectedItems.size}项",
                    )
                ) {
                    is MediaShareLaunchResult.Success -> {
                        showNotice(result.toNoticeMessage())
                    }

                    is MediaShareLaunchResult.Error -> {
                        showNotice(result.message)
                    }
                }
            } finally {
                shareInFlight = false
            }
        }
    }
    fun createPostFromSelection() {
        val selectedIds = selectionState.selectedMediaIds.toList()
        if (selectedIds.isEmpty()) return
        val selectedItems = uiState.feedItems
            .filter { item -> selectedIds.contains(item.mediaId) }
            .map(PhotoFeedItem::toCreatePostAppMediaItem)
        onOpenCreatePost(
            CreatePostRoute(
                source = "real-photo-feed-selection",
                initialAppMediaIds = selectedIds,
                initialAppMediaItems = selectedItems,
            ),
        )
    }
    fun addSelectionToPost() {
        if (selectionState.selectedMediaIds.isEmpty()) return
        addToPostError = null
        showAddToPostDialog = true
    }
    fun requestDeleteSelection() {
        if (selectionState.selectedMediaIds.isEmpty() || uiState.isDeleting) return
        showDeleteConfirm = true
    }
    androidx.compose.runtime.LaunchedEffect(backendMutationEvent.version) {
        if (backendMutationEvent.version > 0 && backendMutationEvent.affectsPhotoFeed()) {
            viewModel.refresh()
        }
    }

    ReconnectRefreshEffect(
        shouldRefresh = uiState.isOfflineReadOnly ||
            uiState.errorMessage != null ||
            uiState.loadMoreErrorMessage != null ||
            uiState.isLoading,
        onReconnect = viewModel::refresh,
        onDisconnect = viewModel::handleConnectivityLost,
    )
    androidx.compose.runtime.LaunchedEffect(selectionShellState) {
        onSelectionShellStateChange(selectionShellState)
    }
    androidx.compose.runtime.LaunchedEffect(selectionActionNonce) {
        if (selectionActionNonce <= 0 || !selectionState.isInSelectionMode) {
            return@LaunchedEffect
        }
        when (selectionAction) {
            PhotoSelectionShellAction.SHARE -> shareSelectedMedia()
            PhotoSelectionShellAction.CREATE -> createPostFromSelection()
            PhotoSelectionShellAction.ADD -> addSelectionToPost()
            PhotoSelectionShellAction.DELETE -> requestDeleteSelection()
            null -> Unit
        }
    }
    androidx.compose.runtime.LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage ?: return@LaunchedEffect
        if (!message.startsWith("已删除")) return@LaunchedEffect
        delay(PhotoFeedDeleteStatusAutoHideMillis)
        viewModel.clearStatusMessage(message)
    }

    if (showDeleteConfirm) {
        val selectedIds = selectionState.selectedMediaIds
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = YingShiThemeTokens.colors.raisedSurface,
            titleContentColor = YingShiThemeTokens.colors.titleAccent,
            textContentColor = YingShiThemeTokens.colors.textSecondary,
            title = { Text("删除媒体到回收站？") },
            confirmButton = {
                TrashDialogActionButton(
                    text = "删除到回收站",
                    danger = true,
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteSelectedMedia(selectedIds) { deletedIds ->
                            onSelectionStateChange(selectionState.without(deletedIds))
                        }
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { showDeleteConfirm = false })
            },
        )
    }

    if (showAddToPostDialog) {
        val selectedItems = uiState.feedItems.filter { item ->
            selectionState.selectedMediaIds.contains(item.mediaId)
        }
        SystemMediaPostDestinationDialog(
            albums = albums,
            posts = posts,
            isLoading = destinationUiState.isLoading,
            isSubmitting = addToPostPendingPostId != null,
            pendingPostId = addToPostPendingPostId,
            errorMessage = addToPostError ?: destinationUiState.errorMessage,
            onDismiss = {
                showAddToPostDialog = false
                addToPostError = null
                addToPostPendingPostId = null
            },
            onPostSelected = { postId ->
                if (selectedItems.isEmpty()) {
                    addToPostError = "没有找到可添加的媒体，请重新选择。"
                    return@SystemMediaPostDestinationDialog
                }
                addToPostError = null
                addToPostPendingPostId = postId
                scope.launch {
                    when (
                        val result = com.example.yingshi.data.repository.RepositoryProvider.postRepository.addMediaToPost(
                            postId = postId,
                            mediaIds = selectedItems.map { it.mediaId },
                        )
                    ) {
                        is com.example.yingshi.data.remote.result.ApiResult.Success -> {
                            notifyRealBackendContentChanged(
                                postIds = setOf(postId),
                                mediaIds = selectedItems.map { it.mediaId }.toSet(),
                            )
                            viewModel.refresh()
                            onSelectionStateChange(
                                selectionState.without(selectedItems.mapTo(linkedSetOf()) { it.mediaId }),
                            )
                            showAddToPostDialog = false
                            addToPostPendingPostId = null
                            addToPostError = null
                            onAddedMediaToPost(
                                result.data.toPostDetailPlaceholderRoute(
                                    selectedAlbumId = result.data.albumIds.firstOrNull()
                                        ?: posts.firstOrNull { it.id == postId }?.albumId.orEmpty(),
                                ).copy(
                                    entryNotice = "已加入小相册",
                                    highlightMediaIds = selectedItems.map { it.mediaId }.distinct(),
                                    focusMediaId = selectedItems.firstOrNull()?.mediaId,
                                ),
                            )
                        }
                        is com.example.yingshi.data.remote.result.ApiResult.Error -> {
                            addToPostPendingPostId = null
                            addToPostError = result.toBackendUiMessage("加入已有小相册失败，请重试。")
                        }
                        com.example.yingshi.data.remote.result.ApiResult.Loading -> {
                            addToPostPendingPostId = null
                        }
                    }
                }
            },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.tokenMissing -> {
                BackendNoticeCard(
                    title = "需要连接服务",
                    text = uiState.errorMessage ?: "请先在连接设置完成登录。",
                    actionLabel = "重试",
                    onAction = viewModel::refresh,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            uiState.isLoading && uiState.feedItems.isEmpty() -> {
                BackendLoadingCard(
                    text = "正在读取照片流…",
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            uiState.errorMessage != null && uiState.feedItems.isEmpty() -> {
                BackendNoticeCard(
                    title = "读取照片流失败",
                    text = uiState.errorMessage ?: "当前无法读取照片流。",
                    actionLabel = "重试",
                    onAction = viewModel::refresh,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            uiState.feedItems.isEmpty() -> {
                BackendNoticeCard(
                    title = "还没有媒体",
                    text = "当前空间的照片流为空，可以先从系统媒体导入几张。",
                    actionLabel = "刷新",
                    onAction = viewModel::refresh,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            else -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    uiState.statusMessage?.let { statusMessage ->
                        BackendInlineNotice(
                            text = statusMessage,
                            emphasized = true,
                        )
                    }
                    uiState.errorMessage?.let { errorMessage ->
                        BackendInlineNotice(
                            text = errorMessage,
                            actionLabel = "重试",
                            onAction = viewModel::refresh,
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        PhotoFeedScreen(
                            feedItems = uiState.feedItems,
                            modifier = Modifier.fillMaxSize(),
                            selectionState = selectionState,
                            isLoadingMore = uiState.isLoadingMore,
                            hasMore = uiState.hasMore,
                            loadMoreErrorMessage = uiState.loadMoreErrorMessage,
                            onSelectionStateChange = onSelectionStateChange,
                            onOpenViewer = onOpenViewer,
                            onLoadMore = viewModel::loadNextPage,
                            onRetryLoadMore = viewModel::retryLoadNextPage,
                            onShowNotice = ::showNotice,
                            scrollTrigger = scrollTrigger,
                            inlineVideoAutoPlayEnabled = inlineVideoAutoPlayEnabled,
                            presentation = PhotoFeedPresentation.MAIN_STREAM,
                        )
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
                .padding(top = spacing.sm),
        )
    }
}

