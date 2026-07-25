package com.example.yingshi.feature.photos

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.sync.StaleBanner
import com.example.yingshi.feature.sync.SyncModule
import com.example.yingshi.feature.sync.SyncVersionTracker
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private enum class MediaManagementMode {
    NORMAL,
    DELETE,
    SORT,
    SET_COVER,
    EDIT_TIME,
}

@Composable
fun MediaManagementScreen(
    route: MediaManagementRoute,
    onBack: () -> Unit,
    onPostUpdated: (postId: String) -> Unit = {},
    onCurrentPostDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UnifiedPostMediaManagementScreen(
        route = route,
        onBack = onBack,
        onPostUpdated = onPostUpdated,
        onCurrentPostDeleted = onCurrentPostDeleted,
        modifier = modifier,
    )
    return
}

@Composable
private fun UnifiedPostMediaManagementScreen(
    route: MediaManagementRoute,
    onBack: () -> Unit,
    onPostUpdated: (postId: String) -> Unit,
    onCurrentPostDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UnifiedRealPostMediaManagementScreen(
        route = route,
        onBack = onBack,
        onPostUpdated = onPostUpdated,
        onCurrentPostDeleted = onCurrentPostDeleted,
        modifier = modifier,
    )
}

@Composable
private fun UnifiedFakePostMediaManagementScreen(
    route: MediaManagementRoute,
    onBack: () -> Unit,
    onPostUpdated: (postId: String) -> Unit,
    onCurrentPostDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val post = FakeAlbumRepository.getPost(route.postId)
    val mediaItems = FakeAlbumRepository.getManagedPostMedia(route.postId)
    if (post == null || mediaItems == null) {
        MediaManagementMissingState(onBack = onBack, modifier = modifier)
        return
    }

    val initialItems = remember(route.postId, mediaItems) {
        mediaItems.map(ManagedPostMediaUiModel::toPostMediaListItem)
    }
    val initialCoverId = remember(route.postId, mediaItems) {
        mediaItems.firstOrNull { it.isCover }?.id ?: mediaItems.firstOrNull()?.id
    }
    var showDeleteCurrentPostConfirm by rememberSaveable(route.postId) { mutableStateOf(false) }

    PostMediaListScreen(
        initialItems = initialItems,
        initialCoverMediaId = initialCoverId,
        allowEmpty = true,
        onCancel = onBack,
        onConfirm = { finalItems, finalCoverId ->
            val finalIds = finalItems.map { it.id }
            if (finalIds.isEmpty()) {
                showDeleteCurrentPostConfirm = true
                return@PostMediaListScreen
            }
            val originalIds = mediaItems.map { it.id }
            val removedIds = (originalIds - finalIds.toSet()).toSet()
            if (removedIds.isNotEmpty()) {
                val selectedMediaSnapshots = FakeAlbumRepository.snapshotPostMedia(
                    postId = route.postId,
                    mediaIds = removedIds,
                )
                FakeAlbumRepository.applyMediaDelete(
                    postId = route.postId,
                    mediaIds = removedIds,
                    semantic = FakeAlbumRepository.MediaDeleteSemantic.DIRECTORY_ONLY,
                )
                FakeTrashRepository.recordRemovedMedia(post, selectedMediaSnapshots)
            }
            if (finalIds.isNotEmpty()) {
                FakeAlbumRepository.updatePostMediaOrder(
                    postId = route.postId,
                    orderedIds = finalIds,
                )
                finalCoverId?.let { coverId ->
                    FakeAlbumRepository.setPostCover(route.postId, coverId)
                }
            }
            onPostUpdated(route.postId)
            Toast.makeText(context, "小相册媒体列表已保存", Toast.LENGTH_SHORT).show()
            onBack()
        },
        modifier = modifier,
    )

    if (showDeleteCurrentPostConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteCurrentPostConfirm = false },
            title = { Text("删除整个小相册？") },
            text = { Text("当前媒体列表已经清空，确认后会把这个小相册整体移入回收站。") },
            confirmButton = {
                Text(
                    text = "确认删除",
                    modifier = Modifier.clickable {
                        val postSnapshot = FakeAlbumRepository.snapshotPost(route.postId)
                        showDeleteCurrentPostConfirm = false
                        if (postSnapshot == null) {
                            Toast.makeText(context, "当前小相册已不存在。", Toast.LENGTH_SHORT).show()
                            onCurrentPostDeleted()
                            return@clickable
                        }
                        FakeTrashRepository.recordDeletedPost(postSnapshot)
                        FakeAlbumRepository.deletePostsLocally(listOf(route.postId))
                        Toast.makeText(context, "小相册已移入回收站", Toast.LENGTH_SHORT).show()
                        onCurrentPostDeleted()
                    },
                )
            },
            dismissButton = {
                Text(
                    text = "取消",
                    modifier = Modifier.clickable {
                        showDeleteCurrentPostConfirm = false
                    },
                )
            },
        )
    }
}

@Composable
private fun UnifiedRealPostMediaManagementScreen(
    route: MediaManagementRoute,
    onBack: () -> Unit,
    onPostUpdated: (postId: String) -> Unit,
    onCurrentPostDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionKey = realBackendSessionKey("real-unified-media-management-${route.postId}")
    val viewModel: RealMediaManagementViewModel = viewModel(
        key = sessionKey,
        factory = RealMediaManagementViewModel.factory(route),
    )
    val uiState by viewModel.uiState.collectAsState()
    val syncStaleState by SyncVersionTracker.staleState.collectAsState()
    var isSaving by rememberSaveable(route.postId) { mutableStateOf(false) }
    var showDeleteCurrentPostConfirm by rememberSaveable(route.postId) { mutableStateOf(false) }

    when {
        uiState.tokenMissing -> {
            MediaManagementMissingState(onBack = onBack, modifier = modifier)
        }
        uiState.isLoading && uiState.mediaItems.isEmpty() -> {
            val colors = YingShiThemeTokens.colors
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(colors.appBackground)
                    .statusBarsPadding()
                    .padding(horizontal = YingShiThemeTokens.spacing.lg, vertical = YingShiThemeTokens.spacing.md),
                verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.md),
            ) {
                MediaManagementTopBar(
                    mode = MediaManagementMode.NORMAL,
                    deleteCount = 0,
                    onBack = onBack,
                    onDelete = {},
                    onCancelMode = {},
                    onFinishMode = {},
                )
                Text(
                    text = "正在读取媒体列表…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                )
            }
        }
        uiState.errorMessage != null && uiState.mediaItems.isEmpty() -> {
            MediaManagementMissingState(onBack = onBack, modifier = modifier)
        }
        else -> {
            val initialItems = remember(route.postId, uiState.mediaItems) {
                uiState.mediaItems.map(ManagedPostMediaUiModel::toPostMediaListItem)
            }
            val initialCoverId = remember(route.postId, uiState.mediaItems) {
                uiState.mediaItems.firstOrNull { it.isCover }?.id ?: uiState.mediaItems.firstOrNull()?.id
            }
            Box(modifier = Modifier.fillMaxSize()) {
                PostMediaListScreen(
                    initialItems = initialItems,
                    initialCoverMediaId = initialCoverId,
                    allowEmpty = true,
                    onCancel = onBack,
                    onConfirm = { finalItems, finalCoverId ->
                    if (isSaving) return@PostMediaListScreen
                    val finalIds = finalItems.map { it.id }
                    if (finalIds.isEmpty()) {
                        showDeleteCurrentPostConfirm = true
                        return@PostMediaListScreen
                    }
                    val originalIds = uiState.mediaItems.map { it.id }
                    val removedIds = originalIds.filterNot { finalIds.contains(it) }
                    val orderChanged = finalIds != originalIds.filter { finalIds.contains(it) }
                    val coverChanged = finalCoverId != initialCoverId && !finalCoverId.isNullOrBlank()
                    scope.launch {
                        isSaving = true
                        var firstFailure: String? = null
                        var successChanged = false
                        removedIds.forEach { mediaId ->
                            when (
                                val result = RepositoryProvider.mediaRepository.deleteMediaFromPost(
                                    smallAlbumId = route.postId,
                                    mediaId = mediaId,
                                    deleteMode = "directory",
                                )
                            ) {
                                is ApiResult.Success -> successChanged = true
                                is ApiResult.Error -> if (firstFailure == null) {
                                    firstFailure = result.toBackendUiMessage("移除媒体失败。")
                                }
                                ApiResult.Loading -> Unit
                            }
                        }
                        if (orderChanged && firstFailure == null) {
                            when (val result = RepositoryProvider.postRepository.updatePostMediaOrder(route.postId, finalIds)) {
                                is ApiResult.Success -> successChanged = true
                                is ApiResult.Error -> firstFailure = result.toBackendUiMessage("保存媒体顺序失败。")
                                ApiResult.Loading -> Unit
                            }
                        }
                        if (coverChanged && firstFailure == null) {
                            when (val result = RepositoryProvider.postRepository.setPostCover(route.postId, finalCoverId)) {
                                is ApiResult.Success -> successChanged = true
                                is ApiResult.Error -> firstFailure = result.toBackendUiMessage("设置封面失败。")
                                ApiResult.Loading -> Unit
                            }
                        }
                        isSaving = false
                        if (firstFailure == null) {
                            if (successChanged) {
                                notifyRealBackendContentChangedWithoutPhotoFeed(
                                    postIds = setOf(route.postId),
                                    mediaIds = (removedIds + finalIds).toSet(),
                                )
                            }
                            SyncVersionTracker.markLocalMutation(SyncModule.ALBUMS)
                            SyncVersionTracker.markLocalMutation(SyncModule.NOTIFICATIONS)
                            onPostUpdated(route.postId)
                            Toast.makeText(context, "小相册媒体列表已保存", Toast.LENGTH_SHORT).show()
                            onBack()
                        } else {
                            Toast.makeText(context, firstFailure, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = modifier,
            )
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

    if (showDeleteCurrentPostConfirm) {
        AlertDialog(
            onDismissRequest = {
                if (!isSaving) {
                    showDeleteCurrentPostConfirm = false
                }
            },
            title = { Text("删除整个小相册？") },
            text = { Text("当前媒体列表已经清空，确认后会把这个小相册整体移入回收站。") },
            confirmButton = {
                Text(
                    text = if (isSaving) "处理中…" else "确认删除",
                    modifier = Modifier.clickable(enabled = !isSaving) {
                        scope.launch {
                            isSaving = true
                            when (val result = RepositoryProvider.postRepository.deleteSmallAlbum(route.postId)) {
                                is ApiResult.Success -> {
                                    SyncVersionTracker.markLocalMutation(SyncModule.ALBUMS)
                                    SyncVersionTracker.markLocalMutation(SyncModule.TRASH)
                                    SyncVersionTracker.markLocalMutation(SyncModule.NOTIFICATIONS)
                                    notifyRealBackendContentChangedWithoutPhotoFeed(postIds = setOf(route.postId))
                                    showDeleteCurrentPostConfirm = false
                                    isSaving = false
                                    Toast.makeText(context, "小相册已移入回收站", Toast.LENGTH_SHORT).show()
                                    onCurrentPostDeleted()
                                }
                                is ApiResult.Error -> {
                                    isSaving = false
                                    Toast.makeText(
                                        context,
                                        result.toBackendUiMessage("删除小相册失败。"),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                ApiResult.Loading -> Unit
                            }
                        }
                    },
                )
            },
            dismissButton = {
                Text(
                    text = "取消",
                    modifier = Modifier.clickable(enabled = !isSaving) {
                        showDeleteCurrentPostConfirm = false
                    },
                )
            },
        )
    }
}

@Composable
private fun MediaManagementTopBar(
    mode: MediaManagementMode,
    deleteCount: Int,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onCancelMode: () -> Unit,
    onFinishMode: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaManagementCircleButton(text = "<", onClick = onBack)
        Text(
            text = "媒体管理",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.titleAccent,
        )
        when (mode) {
            MediaManagementMode.NORMAL -> {
                MediaManagementActionChip(text = "整理", onClick = {})
            }
            MediaManagementMode.DELETE -> {
                MediaManagementActionChip(text = "删除 $deleteCount", enabled = deleteCount > 0, danger = true, onClick = onDelete)
                MediaManagementActionChip(text = "取消", onClick = onCancelMode)
            }
            MediaManagementMode.SORT -> {
                MediaManagementActionChip(text = "完成", emphasized = true, onClick = onFinishMode)
                MediaManagementActionChip(text = "取消", onClick = onCancelMode)
            }
            MediaManagementMode.SET_COVER -> {
                MediaManagementActionChip(text = "设为封面", onClick = {})
                MediaManagementActionChip(text = "取消", onClick = onCancelMode)
            }
            MediaManagementMode.EDIT_TIME -> {
                MediaManagementActionChip(text = "修改时间", onClick = {})
                MediaManagementActionChip(text = "取消", onClick = onCancelMode)
            }
        }
    }
}

@Composable
private fun MediaManagementEntryRow(
    onAddMedia: () -> Unit,
    onDeleteMode: () -> Unit,
    onSortMode: () -> Unit,
    onSetCoverMode: () -> Unit,
    onEditTimeMode: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
        ) {
            MediaManagementActionChip(text = "添加媒体", onClick = onAddMedia)
            MediaManagementActionChip(text = "删除模式", onClick = onDeleteMode)
            MediaManagementActionChip(text = "排序模式", onClick = onSortMode)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
        ) {
            MediaManagementActionChip(text = "设为封面", onClick = onSetCoverMode)
            MediaManagementActionChip(text = "修改时间", onClick = onEditTimeMode)
        }
    }
}

@Composable
private fun MediaManagementCard(
    media: ManagedPostMediaUiModel,
    mode: MediaManagementMode,
    selected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val stateHint = cardStateHint(mode = mode, isCover = media.isCover)

    Surface(
        modifier = Modifier.fillMaxWidth().yingShiClickable(
            shape = RoundedCornerShape(radius.lg),
            pressedScale = 0.985f,
            onClick = onClick,
        ),
        shape = RoundedCornerShape(radius.lg),
        color = colors.raisedSurface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                colors.glassStroke.copy(alpha = 0.82f)
            } else {
                colors.dividerSoft.copy(alpha = 0.62f)
            },
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(media.aspectRatio.coerceIn(0.92f, 1.12f))
        ) {
            AppContentMediaThumbnail(
                mediaSource = media.mediaSource,
                mediaType = media.mediaType,
                palette = media.palette,
                modifier = Modifier.fillMaxSize(),
                contentDescription = media.id,
                requestSize = 448,
            )

            if (media.isCover) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = spacing.xs, top = spacing.xs),
                    shape = RoundedCornerShape(radius.capsule),
                    color = colors.primaryContainer.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.72f)),
                ) {
                    Text(
                        text = "封面",
                        modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.titleAccent,
                    )
                }
            }

            if (stateHint.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = spacing.xs, top = spacing.xs),
                    shape = RoundedCornerShape(radius.capsule),
                    color = colors.raisedSurface.copy(alpha = 0.90f),
                    border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
                ) {
                    Text(
                        text = stateHint,
                        modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.titleAccent,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = spacing.xs, bottom = spacing.xs),
                shape = RoundedCornerShape(radius.capsule),
                color = colors.raisedSurface.copy(alpha = 0.90f),
                border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
            ) {
                Text(
                    text = formatMediaManagementTime(media.displayTimeMillis),
                    modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.titleAccent,
                )
            }

            if (mode == MediaManagementMode.DELETE) {
                SelectionDot(
                    selected = selected,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = spacing.sm),
                )
            }

            if (mode == MediaManagementMode.SORT) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    SortControlChip(
                        text = "上移",
                        enabled = canMoveUp,
                        onClick = onMoveUp,
                    )
                    SortControlChip(
                        text = "下移",
                        enabled = canMoveDown,
                        onClick = onMoveDown,
                    )
                }
            }
        }
    }
}

@Composable
private fun SortControlChip(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    Surface(
        modifier = Modifier.yingShiClickable(
            enabled = enabled,
            shape = shape,
            pressedScale = 0.96f,
            onClick = onClick,
        ),
        shape = shape,
        color = if (enabled) {
            colors.primaryContainer.copy(alpha = 0.84f)
        } else {
            colors.sectionBackground.copy(alpha = 0.58f)
        },
        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = if (enabled) 0.72f else 0.42f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = YingShiThemeTokens.spacing.sm,
                vertical = YingShiThemeTokens.spacing.xs,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) colors.titleAccent else colors.textSecondary,
        )
    }
}

@Composable
private fun SelectionDot(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier.size(28.dp),
        shape = CircleShape,
        color = if (selected) {
            colors.primaryContainer.copy(alpha = 0.94f)
        } else {
            colors.raisedSurface.copy(alpha = 0.82f)
        },
        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.86f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
            }
        }
    }
}

@Composable
private fun MediaManagementCircleButton(
    text: String,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val shape = CircleShape
    Surface(
        modifier = Modifier.size(44.dp).yingShiClickable(
            shape = shape,
            pressedScale = 0.94f,
            onClick = onClick,
        ),
        shape = shape,
        color = colors.sectionBackground.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.76f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
        }
    }
}

@Composable
private fun MediaManagementActionChip(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    danger: Boolean = false,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    val containerColor = when {
        danger -> MaterialTheme.colorScheme.errorContainer.copy(alpha = if (enabled) 0.82f else 0.46f)
        emphasized -> colors.primaryContainer.copy(alpha = if (enabled) 0.86f else 0.48f)
        else -> colors.softGreenContainer.copy(alpha = if (enabled) 0.66f else 0.36f)
    }
    val borderColor = when {
        danger -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
        emphasized -> colors.glassStroke.copy(alpha = 0.78f)
        else -> colors.dividerSoft.copy(alpha = 0.72f)
    }
    val contentColor = when {
        danger -> MaterialTheme.colorScheme.onErrorContainer
        else -> colors.titleAccent
    }
    Surface(
        modifier = Modifier.yingShiClickable(
            enabled = enabled,
            shape = shape,
            pressedScale = 0.96f,
            onClick = onClick,
        ),
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = YingShiThemeTokens.spacing.sm,
                vertical = YingShiThemeTokens.spacing.xs,
            ),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = contentColor,
        )
    }
}

@Composable
private fun MediaManagementMissingState(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .statusBarsPadding()
            .padding(
                horizontal = YingShiThemeTokens.spacing.lg,
                vertical = YingShiThemeTokens.spacing.md,
            ),
        verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.md),
    ) {
        MediaManagementTopBar(
            mode = MediaManagementMode.NORMAL,
            deleteCount = 0,
            onBack = onBack,
            onDelete = {},
            onCancelMode = {},
            onFinishMode = {},
        )
        Text(
            text = "没有找到这个小相册。",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
        )
    }
}

private fun modeDescription(mode: MediaManagementMode): String {
    return when (mode) {
        MediaManagementMode.NORMAL -> "整理这个小相册里的照片和视频。"
        MediaManagementMode.DELETE -> "选择要移除的媒体。"
        MediaManagementMode.SORT -> "调整顺序后点完成保存。"
        MediaManagementMode.SET_COVER -> "点一张媒体设为封面。"
        MediaManagementMode.EDIT_TIME -> "当前无法修改媒体时间。"
    }
}

private fun cardStateHint(
    mode: MediaManagementMode,
    isCover: Boolean,
): String {
    return when (mode) {
        MediaManagementMode.NORMAL -> ""
        MediaManagementMode.DELETE -> "选择"
        MediaManagementMode.SORT -> "排序"
        MediaManagementMode.SET_COVER -> if (isCover) "" else "设为封面"
        MediaManagementMode.EDIT_TIME -> "时间"
    }
}

private fun formatMediaManagementTime(timeMillis: Long): String {
    return SimpleDateFormat("M月d日", Locale.CHINA).format(Date(timeMillis))
}

private fun List<String>.toggleMediaSelection(mediaId: String): List<String> {
    return if (contains(mediaId)) {
        filterNot { it == mediaId }
    } else {
        this + mediaId
    }
}

private fun List<String>.moveMedia(
    mediaId: String,
    direction: Int,
): List<String> {
    val currentIndex = indexOf(mediaId)
    if (currentIndex < 0) return this
    val targetIndex = currentIndex + direction
    if (targetIndex !in indices) return this

    val mutable = toMutableList()
    val item = mutable.removeAt(currentIndex)
    mutable.add(targetIndex, item)
    return mutable
}

@Preview(showBackground = true)
@Composable
private fun MediaManagementScreenPreview() {
    YingShiTheme {
        MediaManagementScreen(
            route = MediaManagementRoute(postId = "post-window-light"),
            onBack = {},
            onCurrentPostDeleted = {},
        )
    }
}
