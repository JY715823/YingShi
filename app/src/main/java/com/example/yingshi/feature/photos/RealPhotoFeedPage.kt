package com.example.yingshi.feature.photos

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.launch

@Composable
fun RealPhotoFeedPage(
    selectionState: PhotoFeedSelectionState,
    onSelectionStateChange: (PhotoFeedSelectionState) -> Unit,
    onOpenViewer: (PhotoViewerRoute) -> Unit,
    onOpenCreatePost: (CreatePostRoute) -> Unit,
    onAddedMediaToPost: (PostDetailPlaceholderRoute) -> Unit,
    modifier: Modifier = Modifier,
    scrollTrigger: Int = 0,
    inlineVideoAutoPlayEnabled: Boolean = true,
) {
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
    val destinationUiState by rememberSystemMediaDestinationUiState()
    val albums = destinationUiState.albums
    val posts = destinationUiState.posts
    val spacing = YingShiThemeTokens.spacing
    androidx.compose.runtime.LaunchedEffect(backendMutationEvent.version) {
        if (backendMutationEvent.version > 0 && backendMutationEvent.affectsPhotoFeed()) {
            viewModel.refresh()
        }
    }

    androidx.compose.runtime.LaunchedEffect(scrollTrigger) {
        if (scrollTrigger > 0) {
            viewModel.refresh()
        }
    }

    if (showDeleteConfirm) {
        val selectedIds = selectionState.selectedMediaIds
        val selectedCount = selectionState.selectedCount
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = YingShiThemeTokens.colors.raisedSurface,
            titleContentColor = YingShiThemeTokens.colors.titleAccent,
            textContentColor = YingShiThemeTokens.colors.textSecondary,
            title = { Text("删除媒体到回收站？") },
            text = {
                Text(
                    "将从照片流删除已选 $selectedCount 项媒体，并同步影响相关小相册里的引用。成功项会进入回收站，失败项会保留并显示原因。",
                )
            },
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
                            bottomOverlayPadding = if (selectionState.isInSelectionMode) 88.dp else 0.dp,
                            isLoadingMore = uiState.isLoadingMore,
                            hasMore = uiState.hasMore,
                            loadMoreErrorMessage = uiState.loadMoreErrorMessage,
                            onSelectionStateChange = onSelectionStateChange,
                            onOpenViewer = onOpenViewer,
                            onLoadMore = viewModel::loadNextPage,
                            onRetryLoadMore = viewModel::retryLoadNextPage,
                            scrollTrigger = scrollTrigger,
                            inlineVideoAutoPlayEnabled = inlineVideoAutoPlayEnabled,
                        )

                        androidx.compose.animation.AnimatedVisibility(
                            visible = selectionState.isInSelectionMode,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 0.dp),
                        ) {
                            RealFeedSelectionBarV2(
                                selectedCount = selectionState.selectedCount,
                                isDeleting = uiState.isDeleting,
                                onCreatePost = {
                                    val selectedIds = selectionState.selectedMediaIds.toList()
                                    if (selectedIds.isEmpty()) {
                                        return@RealFeedSelectionBarV2
                                    }
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
                                },
                                onAddToPost = {
                                    if (selectionState.selectedMediaIds.isEmpty()) {
                                        return@RealFeedSelectionBarV2
                                    }
                                    addToPostError = null
                                    showAddToPostDialog = true
                                },
                                onDelete = {
                                    if (selectionState.selectedMediaIds.isEmpty()) {
                                        return@RealFeedSelectionBarV2
                                    } else {
                                        showDeleteConfirm = true
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RealFeedSelectionBarV2(
    selectedCount: Int,
    isDeleting: Boolean,
    onCreatePost: () -> Unit,
    onAddToPost: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val chipShape = androidx.compose.foundation.shape.RoundedCornerShape(radius.capsule)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
        color = colors.raisedSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (selectedCount > 0) "已选中 $selectedCount 项" else "请选择媒体",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            RealFeedSelectionChip(
                text = "新建",
                enabled = !isDeleting,
                onClick = onCreatePost,
                shape = chipShape,
            )
            RealFeedSelectionChip(
                text = "加入",
                enabled = !isDeleting,
                onClick = onAddToPost,
                shape = chipShape,
            )
            RealFeedSelectionChip(
                text = if (isDeleting) "删除中…" else "回收站",
                enabled = !isDeleting,
                onClick = onDelete,
                shape = chipShape,
                destructive = true,
            )
        }
    }
}

@Composable
private fun RealFeedSelectionChip(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    destructive: Boolean = false,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.yingShiClickable(
            enabled = enabled,
            shape = shape,
            pressedScale = 0.96f,
            onClick = onClick,
        ),
        shape = shape,
        color = if (enabled) {
            if (destructive) {
                colors.memoryContainer.copy(alpha = 0.72f)
            } else {
                colors.primaryContainer.copy(alpha = 0.74f)
            }
        } else {
            colors.sectionBackground.copy(alpha = 0.64f)
        },
        border = BorderStroke(
            1.dp,
            if (destructive) {
                colors.memoryAccent.copy(alpha = 0.18f)
            } else {
                colors.glassStroke.copy(alpha = 0.70f)
            },
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            ),
            color = if (destructive) MaterialTheme.colorScheme.error else colors.titleAccent,
        )
    }
}

@Composable
private fun RealFeedSelectionBar(
    selectedCount: Int,
    isDeleting: Boolean,
    onDelete: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.md),
        color = colors.raisedSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (selectedCount > 0) "已选中 $selectedCount 项" else "请选择媒体",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            RealFeedSelectionChip(
                text = if (isDeleting) "删除中…" else "回收站",
                enabled = !isDeleting,
                onClick = onDelete,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                destructive = true,
            )
        }
    }
}
