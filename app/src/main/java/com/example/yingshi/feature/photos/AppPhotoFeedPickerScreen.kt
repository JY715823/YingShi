package com.example.yingshi.feature.photos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshi.ui.components.YingShiNotice
import com.example.yingshi.ui.components.YingShiNoticeHost
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
fun AppPhotoFeedPickerScreen(
    confirmLabel: String,
    onBack: () -> Unit,
    onConfirm: (List<CreatePostAppMediaItem>) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "照片流",
    excludedMediaIds: Set<String> = emptySet(),
    initialSelectedMediaIds: Set<String> = emptySet(),
    disabledMediaIds: Set<String> = emptySet(),
    disabledSelectionLabel: String? = null,
    confirmBackWhenSelected: Boolean = false,
) {
    val colors = YingShiThemeTokens.colors
    var selectionState by remember(initialSelectedMediaIds) {
        mutableStateOf(
            PhotoFeedSelectionState(
                selectedMediaIds = initialSelectedMediaIds,
                isInSelectionMode = true,
            ),
        )
    }
    var showExitConfirm by remember { mutableStateOf(false) }
    var viewerRoute by remember { mutableStateOf<PhotoViewerRoute?>(null) }
    val localPageStateStore = remember { PhotoFeedPageStateStore() }
    var notice by remember { mutableStateOf<YingShiNotice?>(null) }
    var noticeNonce by rememberSaveable { mutableStateOf(0) }

    fun showNotice(message: String) {
        noticeNonce += 1
        notice = YingShiNotice(message = message, nonce = noticeNonce)
    }

    fun handleBackRequest() {
        if (confirmBackWhenSelected && selectionState.selectedCount > 0) {
            showExitConfirm = true
        } else {
            onBack()
        }
    }

    BackHandler(onBack = ::handleBackRequest)

    val sessionKey = realBackendSessionKey("photo-feed-picker")
    val viewModel: RealPhotoFeedViewModel = viewModel(
        key = sessionKey,
        factory = RealPhotoFeedViewModel.factory(),
    )
    val uiState by viewModel.uiState.collectAsState()
    val visibleItems = remember(uiState.feedItems, excludedMediaIds) {
        uiState.feedItems.filterNot { excludedMediaIds.contains(it.mediaId) }
    }
    Box(modifier = modifier.fillMaxSize()) {
        PhotoFeedPickerScaffold(
            title = title,
            confirmLabel = confirmLabel,
            selectionState = selectionState,
            onSelectionStateChange = {
                selectionState = it.copy(isInSelectionMode = true)
            },
            onBack = ::handleBackRequest,
            onConfirm = {
                val selectedIdSet = selectionState.selectedMediaIds
                val selectedItems = visibleItems
                    .filter { selectedIdSet.contains(it.mediaId) }
                    .map(PhotoFeedItem::toCreatePostAppMediaItem)
                onConfirm(selectedItems)
            },
            modifier = Modifier.fillMaxSize(),
            body = {
                when {
                    uiState.tokenMissing -> {
                        BackendNoticeCard(
                            title = "需要连接服务",
                            text = uiState.errorMessage ?: "请先完成登录后再选择照片。",
                            actionLabel = "重试",
                            onAction = viewModel::refresh,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    uiState.isLoading && visibleItems.isEmpty() -> {
                        BackendLoadingCard(
                            text = "正在读取照片流…",
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    uiState.errorMessage != null && visibleItems.isEmpty() -> {
                        BackendNoticeCard(
                            title = "读取照片流失败",
                            text = uiState.errorMessage ?: "当前无法读取照片流。",
                            actionLabel = "重试",
                            onAction = viewModel::refresh,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    visibleItems.isEmpty() -> {
                        BackendNoticeCard(
                            title = "没有可选媒体",
                            text = "当前照片流里没有可加入的媒体。",
                            actionLabel = "返回",
                            onAction = onBack,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    else -> {
                        PhotoFeedScreen(
                            feedItems = visibleItems,
                            modifier = Modifier.fillMaxSize(),
                            pageStateStore = localPageStateStore,
                            selectionState = selectionState,
                            bottomOverlayPadding = 88.dp,
                            isLoadingMore = uiState.isLoadingMore,
                            hasMore = uiState.hasMore,
                            loadMoreErrorMessage = uiState.loadMoreErrorMessage,
                            onSelectionStateChange = {
                                selectionState = it.copy(isInSelectionMode = true)
                            },
                            onLoadMore = viewModel::loadNextPage,
                            onRetryLoadMore = viewModel::retryLoadNextPage,
                            onOpenViewer = { viewerRoute = it },
                            onShowNotice = ::showNotice,
                            inlineVideoAutoPlayEnabled = false,
                            allowOpenMediaWhileSelecting = true,
                            disabledMediaIds = disabledMediaIds,
                            disabledSelectionLabel = disabledSelectionLabel,
                        )
                    }
                }
            },
        )
        if (showExitConfirm) {
            PickerExitConfirmDialog(
                onDismiss = { showExitConfirm = false },
                onLeave = {
                    showExitConfirm = false
                    onBack()
                },
            )
        }
        viewerRoute?.let { route ->
            PhotoViewerScreen(
                route = route,
                onBack = { viewerRoute = null },
                modifier = Modifier.fillMaxSize(),
            )
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
                .padding(top = YingShiThemeTokens.spacing.sm),
        )
    }
}

@Composable
private fun PhotoFeedPickerScaffold(
    title: String,
    confirmLabel: String,
    selectionState: PhotoFeedSelectionState,
    onSelectionStateChange: (PhotoFeedSelectionState) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    body: @Composable BoxScope.() -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .statusBarsPadding()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PickerBackButton(onClick = onBack)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
                Text(
                    text = if (selectionState.selectedCount > 0) {
                        "已选 ${selectionState.selectedCount} 项"
                    } else {
                        "长按或滑动选择媒体"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            PickerActionChip(
                text = "清空",
                emphasized = false,
                enabled = selectionState.selectedCount > 0,
                onClick = { onSelectionStateChange(selectionState.clear().copy(isInSelectionMode = true)) },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            body()

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = colors.raisedSurface.copy(alpha = 0.97f),
                border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.74f)),
                shadowElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (selectionState.selectedCount > 0) {
                            "已选 ${selectionState.selectedCount} 项"
                        } else {
                            "请选择媒体"
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                    )
                    PickerActionChip(
                        text = confirmLabel,
                        enabled = selectionState.selectedCount > 0,
                        onClick = onConfirm,
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerExitConfirmDialog(
    onDismiss: () -> Unit,
    onLeave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "放弃这次选择？")
        },
        text = {
            Text(text = "已经选中的媒体还没有加入当前相册，返回后这次选择会丢失。")
        },
        confirmButton = {
            PickerActionChip(text = "返回", onClick = onLeave)
        },
        dismissButton = {
            PickerActionChip(
                text = "继续选择",
                onClick = onDismiss,
                emphasized = false,
            )
        },
    )
}

@Composable
private fun PickerBackButton(
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier,
        shape = RoundedCornerShape(14.dp),
        color = colors.sectionBackground.copy(alpha = 0.80f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
        onClick = onClick,
    ) {
        Text(
            text = "<",
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.titleAccent,
        )
    }
}

@Composable
private fun PickerActionChip(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    emphasized: Boolean = true,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = when {
            !enabled -> colors.sectionBackground.copy(alpha = 0.56f)
            emphasized -> colors.primaryContainer.copy(alpha = 0.82f)
            else -> colors.sectionBackground.copy(alpha = 0.76f)
        },
        border = BorderStroke(
            1.dp,
            if (emphasized) {
                colors.glassStroke.copy(alpha = 0.76f)
            } else {
                colors.dividerSoft.copy(alpha = 0.72f)
            },
        ),
        onClick = onClick,
        enabled = enabled,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) colors.titleAccent else colors.textSecondary,
        )
    }
}
