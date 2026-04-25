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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    onCurrentPostDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val spacing = YingShiThemeTokens.spacing
    val post = FakeAlbumRepository.getPost(route.postId)
    val repoMediaItems = FakeAlbumRepository.getManagedPostMedia(route.postId)

    if (post == null || repoMediaItems == null) {
        MediaManagementMissingState(
            onBack = onBack,
            modifier = modifier,
        )
        return
    }

    var modeName by rememberSaveable(route.postId) {
        mutableStateOf(MediaManagementMode.NORMAL.name)
    }
    val mode = MediaManagementMode.valueOf(modeName)
    var selectedForDelete by rememberSaveable(route.postId) {
        mutableStateOf<List<String>>(emptyList())
    }
    var sortDraftOrder by rememberSaveable(route.postId) {
        mutableStateOf<List<String>>(emptyList())
    }
    var pendingDeleteSemanticName by rememberSaveable(route.postId) {
        mutableStateOf<String?>(null)
    }
    var showDeleteSemanticDialog by rememberSaveable(route.postId) {
        mutableStateOf(false)
    }
    var showEmptyPostDialog by rememberSaveable(route.postId) {
        mutableStateOf(false)
    }
    val gridState = rememberLazyGridState()

    val mediaItems = remember(repoMediaItems, mode, sortDraftOrder) {
        if (mode == MediaManagementMode.SORT && sortDraftOrder.isNotEmpty()) {
            sortDraftOrder.mapNotNull { mediaId ->
                repoMediaItems.firstOrNull { it.id == mediaId }
            }
        } else {
            repoMediaItems
        }
    }

    fun exitMode() {
        modeName = MediaManagementMode.NORMAL.name
        selectedForDelete = emptyList()
        sortDraftOrder = emptyList()
        pendingDeleteSemanticName = null
        showDeleteSemanticDialog = false
        showEmptyPostDialog = false
    }

    fun executeDelete(semantic: FakeAlbumRepository.MediaDeleteSemantic) {
        val selectedIds = selectedForDelete.toSet()
        if (selectedIds.isEmpty()) {
            exitMode()
            return
        }

        val outcome = FakeAlbumRepository.applyMediaDelete(
            postId = route.postId,
            mediaIds = selectedIds,
            semantic = semantic,
        )
        val removedPosts = FakeAlbumRepository.deletePostsLocally(outcome.deletedPostIds)
        if (removedPosts.contains(route.postId)) {
            Toast.makeText(context, "当前帖子已本地移除", Toast.LENGTH_SHORT).show()
            onCurrentPostDeleted()
            return
        }

        val actionLabel = when (semantic) {
            FakeAlbumRepository.MediaDeleteSemantic.DIRECTORY_ONLY -> "已从当前帖子移除"
            FakeAlbumRepository.MediaDeleteSemantic.SYSTEM_WIDE -> "已从本地全局媒体中移除"
        }
        Toast.makeText(context, actionLabel, Toast.LENGTH_SHORT).show()
        exitMode()
    }

    fun handleSemanticSelection(semantic: FakeAlbumRepository.MediaDeleteSemantic) {
        pendingDeleteSemanticName = semantic.name
        showDeleteSemanticDialog = false
        val outcome = FakeAlbumRepository.previewDeleteOutcome(
            postId = route.postId,
            mediaIds = selectedForDelete.toSet(),
            semantic = semantic,
        )
        if (outcome.deletedPostIds.contains(route.postId)) {
            showEmptyPostDialog = true
        } else {
            executeDelete(semantic)
        }
    }

    BackHandler(enabled = mode != MediaManagementMode.NORMAL || showDeleteSemanticDialog || showEmptyPostDialog) {
        if (showEmptyPostDialog) {
            showEmptyPostDialog = false
            pendingDeleteSemanticName = null
        } else if (showDeleteSemanticDialog) {
            showDeleteSemanticDialog = false
        } else {
            exitMode()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        MediaManagementTopBar(
            mode = mode,
            deleteCount = selectedForDelete.size,
            onBack = {
                if (mode == MediaManagementMode.NORMAL) {
                    onBack()
                } else {
                    exitMode()
                }
            },
            onDelete = { showDeleteSemanticDialog = true },
            onCancelMode = { exitMode() },
            onFinishMode = {
                if (mode == MediaManagementMode.SORT) {
                    val saved = FakeAlbumRepository.updatePostMediaOrder(
                        postId = route.postId,
                        orderedIds = sortDraftOrder.ifEmpty { repoMediaItems.map { it.id } },
                    )
                    if (saved) {
                        Toast.makeText(context, "当前帖子媒体顺序已保存", Toast.LENGTH_SHORT).show()
                    }
                }
                exitMode()
            },
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
                    text = post.title.ifBlank { "当前帖子" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = modeDescription(mode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (mode == MediaManagementMode.NORMAL) {
                    MediaManagementEntryRow(
                        onAddMedia = {
                            Toast.makeText(context, "添加媒体占位：后续接系统媒体选择", Toast.LENGTH_SHORT).show()
                        },
                        onDeleteMode = {
                            selectedForDelete = emptyList()
                            modeName = MediaManagementMode.DELETE.name
                        },
                        onSortMode = {
                            selectedForDelete = emptyList()
                            sortDraftOrder = FakeAlbumRepository.getManagedMediaOrder(route.postId)
                            modeName = MediaManagementMode.SORT.name
                        },
                        onSetCoverMode = {
                            selectedForDelete = emptyList()
                            modeName = MediaManagementMode.SET_COVER.name
                        },
                        onEditTimeMode = {
                            selectedForDelete = emptyList()
                            modeName = MediaManagementMode.EDIT_TIME.name
                        },
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            items(mediaItems, key = { it.id }) { media ->
                MediaManagementCard(
                    media = media,
                    mode = mode,
                    selected = selectedForDelete.contains(media.id),
                    canMoveUp = mode == MediaManagementMode.SORT && mediaItems.firstOrNull()?.id != media.id,
                    canMoveDown = mode == MediaManagementMode.SORT && mediaItems.lastOrNull()?.id != media.id,
                    onClick = {
                        when (mode) {
                            MediaManagementMode.NORMAL -> Unit
                            MediaManagementMode.DELETE -> {
                                selectedForDelete = selectedForDelete.toggleMediaSelection(media.id)
                            }
                            MediaManagementMode.SORT -> Unit
                            MediaManagementMode.SET_COVER -> {
                                if (FakeAlbumRepository.setPostCover(route.postId, media.id)) {
                                    Toast.makeText(context, "已设为封面", Toast.LENGTH_SHORT).show()
                                }
                                exitMode()
                            }
                            MediaManagementMode.EDIT_TIME -> {
                                Toast.makeText(context, "修改媒体时间入口占位：后续再接基础编辑", Toast.LENGTH_SHORT).show()
                                exitMode()
                            }
                        }
                    },
                    onMoveUp = {
                        sortDraftOrder = sortDraftOrder.moveMedia(media.id, direction = -1)
                    },
                    onMoveDown = {
                        sortDraftOrder = sortDraftOrder.moveMedia(media.id, direction = 1)
                    },
                )
            }
        }
    }

    if (showDeleteSemanticDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSemanticDialog = false },
            title = { Text("选择删除语义") },
            text = {
                Text("本轮先在本地状态里区分“从当前帖子移除”和“系统删除该媒体”，不接正式回收站。")
            },
            confirmButton = {
                TextButton(
                    onClick = { handleSemanticSelection(FakeAlbumRepository.MediaDeleteSemantic.SYSTEM_WIDE) },
                ) {
                    Text("系统删除该媒体")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { handleSemanticSelection(FakeAlbumRepository.MediaDeleteSemantic.DIRECTORY_ONLY) },
                ) {
                    Text("从当前帖子移除")
                }
            },
        )
    }

    if (showEmptyPostDialog) {
        AlertDialog(
            onDismissRequest = {
                showEmptyPostDialog = false
                pendingDeleteSemanticName = null
            },
            title = { Text("空帖保护") },
            text = {
                Text("继续删除会让当前帖子变成空帖子。本轮不允许直接留下空帖子，你可以删除整个帖子或取消本次删除。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val semantic = pendingDeleteSemanticName
                            ?.let(FakeAlbumRepository.MediaDeleteSemantic::valueOf)
                            ?: FakeAlbumRepository.MediaDeleteSemantic.DIRECTORY_ONLY
                        showEmptyPostDialog = false
                        executeDelete(semantic)
                    },
                ) {
                    Text("删除整个帖子")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEmptyPostDialog = false
                        pendingDeleteSemanticName = null
                    },
                ) {
                    Text("取消本次删除")
                }
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaManagementCircleButton(text = "<", onClick = onBack)
        Text(
            text = "媒体管理",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        when (mode) {
            MediaManagementMode.NORMAL -> {
                MediaManagementActionChip(text = "普通态", onClick = {})
            }
            MediaManagementMode.DELETE -> {
                TextButton(onClick = onDelete, enabled = deleteCount > 0) {
                    Text("删除（$deleteCount）")
                }
                TextButton(onClick = onCancelMode) {
                    Text("取消")
                }
            }
            MediaManagementMode.SORT -> {
                TextButton(onClick = onFinishMode) {
                    Text("完成")
                }
                TextButton(onClick = onCancelMode) {
                    Text("取消")
                }
            }
            MediaManagementMode.SET_COVER -> {
                MediaManagementActionChip(text = "设为封面", onClick = {})
                TextButton(onClick = onCancelMode) {
                    Text("取消")
                }
            }
            MediaManagementMode.EDIT_TIME -> {
                MediaManagementActionChip(text = "修改时间", onClick = {})
                TextButton(onClick = onCancelMode) {
                    Text("取消")
                }
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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.lg))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(radius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
            },
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(media.aspectRatio.coerceIn(0.92f, 1.12f))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(media.palette.start, media.palette.end),
                    ),
                ),
        ) {
            if (media.isCover) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = spacing.xs, top = spacing.xs),
                    shape = RoundedCornerShape(radius.capsule),
                    color = Color.Black.copy(alpha = 0.18f),
                ) {
                    Text(
                        text = "封面",
                        modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White.copy(alpha = 0.94f),
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = spacing.xs, top = spacing.xs),
                shape = RoundedCornerShape(radius.capsule),
                color = Color.Black.copy(alpha = 0.16f),
            ) {
                Text(
                    text = cardStateHint(mode = mode, isCover = media.isCover),
                    modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = spacing.md, bottom = spacing.md)
                    .fillMaxWidth(0.46f)
                    .height(24.dp)
                    .clip(RoundedCornerShape(radius.capsule))
                    .background(Color.White.copy(alpha = 0.12f)),
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = spacing.xs, bottom = spacing.xs),
                shape = RoundedCornerShape(radius.capsule),
                color = Color.Black.copy(alpha = 0.16f),
            ) {
                Text(
                    text = formatMediaManagementTime(media.displayTimeMillis),
                    modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.92f),
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
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(YingShiThemeTokens.radius.capsule))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (enabled) {
            Color.Black.copy(alpha = 0.18f)
        } else {
            Color.Black.copy(alpha = 0.08f)
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = YingShiThemeTokens.spacing.sm,
                vertical = YingShiThemeTokens.spacing.xs,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) Color.White.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.54f),
        )
    }
}

@Composable
private fun SelectionDot(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(28.dp),
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.White.copy(alpha = 0.2f)
        },
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
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
private fun MediaManagementActionChip(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(YingShiThemeTokens.radius.capsule))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = YingShiThemeTokens.spacing.sm,
                vertical = YingShiThemeTokens.spacing.xs,
            ),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun MediaManagementMissingState(
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
        MediaManagementTopBar(
            mode = MediaManagementMode.NORMAL,
            deleteCount = 0,
            onBack = onBack,
            onDelete = {},
            onCancelMode = {},
            onFinishMode = {},
        )
        Text(
            text = "当前帖子不存在，暂时无法进入媒体管理。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun modeDescription(mode: MediaManagementMode): String {
    return when (mode) {
        MediaManagementMode.NORMAL -> "两列网格管理当前帖子的媒体；本轮先打通目录删 / 系统删、本地排序和封面同步。"
        MediaManagementMode.DELETE -> "删除模式支持多选，点击“删除（x）”后先选择目录删或系统删，不直接删除。"
        MediaManagementMode.SORT -> "排序模式使用上移 / 下移完成本地调整；点击完成保存，点击取消恢复进入排序前的顺序。"
        MediaManagementMode.SET_COVER -> "点击某张媒体即可本地设为封面，并尽量同步到帖子详情页和相册页。"
        MediaManagementMode.EDIT_TIME -> "修改媒体时间继续保留入口占位，本轮不接复杂时间编辑器。"
    }
}

private fun cardStateHint(
    mode: MediaManagementMode,
    isCover: Boolean,
): String {
    return when (mode) {
        MediaManagementMode.NORMAL -> if (isCover) "当前封面" else "普通态"
        MediaManagementMode.DELETE -> "点击选择"
        MediaManagementMode.SORT -> "调整顺序"
        MediaManagementMode.SET_COVER -> if (isCover) "当前封面" else "点此设封面"
        MediaManagementMode.EDIT_TIME -> "时间入口"
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
