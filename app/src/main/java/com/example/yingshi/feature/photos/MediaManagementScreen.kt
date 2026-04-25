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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val spacing = YingShiThemeTokens.spacing
    val post = FakeAlbumRepository.getPost(route.postId)
    val mediaItems = FakeAlbumRepository.getManagedPostMedia(route.postId)

    if (post == null || mediaItems == null) {
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
        mutableStateOf(setOf<String>())
    }
    val gridState = rememberLazyGridState()

    fun exitMode() {
        modeName = MediaManagementMode.NORMAL.name
        selectedForDelete = emptySet()
    }

    BackHandler(enabled = mode != MediaManagementMode.NORMAL) {
        exitMode()
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
            onDelete = {
                Toast.makeText(
                    context,
                    "删除语义占位：已选择 ${selectedForDelete.size} 项，Stage 6.3 再接正式流程",
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onCancelMode = { exitMode() },
            onFinishMode = {
                if (mode == MediaManagementMode.SORT) {
                    Toast.makeText(context, "排序模式结构已保留，本轮先不接复杂拖动", Toast.LENGTH_SHORT).show()
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
                            selectedForDelete = emptySet()
                            modeName = MediaManagementMode.DELETE.name
                        },
                        onSortMode = {
                            selectedForDelete = emptySet()
                            modeName = MediaManagementMode.SORT.name
                        },
                        onSetCoverMode = {
                            selectedForDelete = emptySet()
                            modeName = MediaManagementMode.SET_COVER.name
                        },
                        onEditTimeMode = {
                            selectedForDelete = emptySet()
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
                    onClick = {
                        when (mode) {
                            MediaManagementMode.NORMAL -> Unit
                            MediaManagementMode.DELETE -> {
                                selectedForDelete = if (selectedForDelete.contains(media.id)) {
                                    selectedForDelete - media.id
                                } else {
                                    selectedForDelete + media.id
                                }
                            }
                            MediaManagementMode.SORT -> {
                                Toast.makeText(context, "排序拖动占位：Stage 6.2 先保留模式结构", Toast.LENGTH_SHORT).show()
                            }
                            MediaManagementMode.SET_COVER -> {
                                if (FakeAlbumRepository.setPostCover(route.postId, media.id)) {
                                    Toast.makeText(context, "已设为封面", Toast.LENGTH_SHORT).show()
                                }
                                exitMode()
                            }
                            MediaManagementMode.EDIT_TIME -> {
                                Toast.makeText(context, "修改媒体时间入口占位：后续可接基础编辑", Toast.LENGTH_SHORT).show()
                                exitMode()
                            }
                        }
                    },
                )
            }
        }
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
    onClick: () -> Unit,
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
        }
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
        MediaManagementMode.NORMAL -> "两列网格管理当前帖子的媒体；本轮先打通本地结构、删除模式和设为封面。"
        MediaManagementMode.DELETE -> "删除模式支持多选，但本轮先停在占位确认，不进入正式目录删 / 系统删语义。"
        MediaManagementMode.SORT -> "排序模式先保留页面结构和完成 / 取消入口，复杂拖拽留到后续阶段。"
        MediaManagementMode.SET_COVER -> "点某张媒体即可本地设为封面，并尽量同步到帖子详情页和相册页。"
        MediaManagementMode.EDIT_TIME -> "修改媒体时间先保留轻量入口，本轮不接复杂时间编辑器。"
    }
}

private fun cardStateHint(
    mode: MediaManagementMode,
    isCover: Boolean,
): String {
    return when (mode) {
        MediaManagementMode.NORMAL -> if (isCover) "当前封面" else "普通态"
        MediaManagementMode.DELETE -> "点击选择"
        MediaManagementMode.SORT -> "排序占位"
        MediaManagementMode.SET_COVER -> if (isCover) "当前封面" else "点此设封面"
        MediaManagementMode.EDIT_TIME -> "时间入口"
    }
}

private fun formatMediaManagementTime(timeMillis: Long): String {
    return SimpleDateFormat("M月d日", Locale.CHINA).format(Date(timeMillis))
}

@Preview(showBackground = true)
@Composable
private fun MediaManagementScreenPreview() {
    YingShiTheme {
        MediaManagementScreen(
            route = MediaManagementRoute(postId = "post-window-light"),
            onBack = {},
        )
    }
}
