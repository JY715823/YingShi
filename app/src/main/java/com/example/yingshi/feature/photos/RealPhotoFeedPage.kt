package com.example.yingshi.feature.photos

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
fun RealPhotoFeedPage(
    selectionState: PhotoFeedSelectionState,
    onSelectionStateChange: (PhotoFeedSelectionState) -> Unit,
    onOpenViewer: (PhotoViewerRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: RealPhotoFeedViewModel = viewModel(
        key = "real-photo-feed",
        factory = RealPhotoFeedViewModel.factory(),
    )
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val spacing = YingShiThemeTokens.spacing

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除这些媒体？") },
            text = { Text("REAL 模式会调用后端系统删除，并把媒体写入后端回收站。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteSelectedMedia(selectionState.selectedMediaIds)
                        onSelectionStateChange(selectionState.clear())
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.tokenMissing -> {
                RealFeedInfoCard(
                    title = "REAL 模式需要登录",
                    message = uiState.errorMessage ?: "请先到后端联调诊断页完成登录。",
                    actionLabel = "重试",
                    onAction = viewModel::refresh,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            uiState.isLoading && uiState.feedItems.isEmpty() -> {
                RealFeedLoadingCard(modifier = Modifier.align(Alignment.Center))
            }

            uiState.errorMessage != null && uiState.feedItems.isEmpty() -> {
                RealFeedInfoCard(
                    title = "读取照片流失败",
                    message = uiState.errorMessage ?: "暂时无法读取后端媒体流。",
                    actionLabel = "重试",
                    onAction = viewModel::refresh,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            uiState.feedItems.isEmpty() -> {
                RealFeedInfoCard(
                    title = "还没有后端媒体",
                    message = "当前空间的后端媒体流为空，可以先从系统媒体导入几张。",
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
                        RealInlineNotice(
                            text = statusMessage,
                            emphasized = true,
                        )
                    }
                    uiState.errorMessage?.let { errorMessage ->
                        RealInlineNotice(
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
                            onSelectionStateChange = onSelectionStateChange,
                            onOpenViewer = onOpenViewer,
                        )

                        androidx.compose.animation.AnimatedVisibility(
                            visible = selectionState.isInSelectionMode,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = spacing.sm),
                        ) {
                            RealFeedSelectionBar(
                                selectedCount = selectionState.selectedCount,
                                isDeleting = uiState.isDeleting,
                                onDelete = {
                                    if (selectionState.selectedMediaIds.isEmpty()) {
                                        onSelectionStateChange(selectionState.clear())
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
private fun RealFeedLoadingCard(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp)
            Text(
                text = "正在读取后端照片流…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RealFeedInfoCard(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun RealInlineNotice(
    text: String,
    emphasized: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = if (emphasized) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
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
private fun RealFeedSelectionBar(
    selectedCount: Int,
    isDeleting: Boolean,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "已选中 $selectedCount 项",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                enabled = !isDeleting,
                onClick = onDelete,
            ) {
                Text(if (isDeleting) "删除中…" else "删除到回收站")
            }
        }
    }
}
