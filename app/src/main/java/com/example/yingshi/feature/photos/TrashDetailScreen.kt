package com.example.yingshi.feature.photos

import android.widget.Toast
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TrashDetailScreen(
    route: TrashDetailRoute,
    onBack: () -> Unit,
    onEntryRemoved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
        RealTrashDetailScreen(
            route = route,
            onBack = onBack,
            onEntryRemoved = onEntryRemoved,
            modifier = modifier,
        )
        return
    }

    val context = LocalContext.current
    val entry = FakeTrashRepository.resolveDetailEntry(route)

    if (entry == null) {
        TrashDetailMissingState(
            onBack = onBack,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = YingShiThemeTokens.spacing.lg,
                vertical = YingShiThemeTokens.spacing.md,
            ),
        verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.md),
    ) {
        TrashDetailTopBar(
            entry = entry,
            onBack = onBack,
            onRestore = {
                val result = FakeTrashRepository.restoreEntry(entry.id)
                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                if (result.success) {
                    onBack()
                }
            },
            onRemove = {
                if (FakeTrashRepository.moveEntryOutOfTrash(entry.id)) {
                    onEntryRemoved()
                } else {
                    Toast.makeText(context, "该删除项不存在或已被移出回收站。", Toast.LENGTH_SHORT).show()
                    onBack()
                }
            },
        )

        TrashDetailStatusCard(entry = entry)

        when (entry.type) {
            TrashEntryType.POST_DELETED -> TrashDeletedPostContent(entry = entry)
            TrashEntryType.MEDIA_REMOVED -> TrashDeletedMediaContent(
                entry = entry,
                systemWide = false,
            )
            TrashEntryType.MEDIA_SYSTEM_DELETED -> TrashDeletedMediaContent(
                entry = entry,
                systemWide = true,
            )
        }
    }
}

@Composable
private fun TrashDetailTopBar(
    entry: TrashEntryUiModel,
    onBack: () -> Unit,
    onRestore: () -> Unit,
    onRemove: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrashCircleButton(text = "<", onClick = onBack)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.type.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "删除态详情",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        TrashActionChip(text = "恢复", emphasized = true, onClick = onRestore)
        TrashActionChip(
            text = if (entry.type == TrashEntryType.POST_DELETED) "移出回收站" else "删除",
            emphasized = false,
            onClick = onRemove,
        )
    }
}

@Composable
private fun TrashDetailStatusCard(entry: TrashEntryUiModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(YingShiThemeTokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
        ) {
            Text(
                text = entry.title.ifBlank { "未命名删除项" },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = entry.previewInfo.ifBlank { "当前删除项将优先使用回收站快照展示。" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "删除于 ${formatTrashDetailTime(entry.deletedAtMillis)} · 当前仅支持只读查看、恢复和移出回收站",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
            )
        }
    }
}

@Composable
private fun TrashDeletedPostContent(entry: TrashEntryUiModel) {
    val snapshot = entry.postSnapshot
    if (snapshot == null) {
        TrashDetailEmptyCard(text = "当前帖子快照不存在，暂时无法展示删除态详情。")
        return
    }

    val mediaSnapshots = snapshot.mediaSnapshots
    val pagerState = rememberPagerState(pageCount = { mediaSnapshots.size.coerceAtLeast(1) })
    val currentPage = pagerState.currentPage.coerceIn(0, (mediaSnapshots.size - 1).coerceAtLeast(0))
    val currentMedia = mediaSnapshots.getOrNull(currentPage)
    val comments = remember(snapshot.post.id) {
        FakeCommentRepository.getPostComments(snapshot.post.id)
    }
    val albumChips = snapshot.post.albumIds.ifEmpty { listOf("未归入相册") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(YingShiThemeTokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.md),
        ) {
            Text(
                text = "只读帖子浏览",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (mediaSnapshots.isEmpty()) {
                TrashDetailEmptyCard(text = "当前帖子没有可展示的媒体快照。")
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    beyondViewportPageCount = 1,
                    key = { page -> mediaSnapshots[page].mediaId },
                ) { page ->
                    TrashMediaCanvas(
                        media = mediaSnapshots[page],
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(
                    text = "${currentPage + 1} / ${mediaSnapshots.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
            ) {
                Column(
                    modifier = Modifier.padding(YingShiThemeTokens.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
                ) {
                    Text(
                        text = snapshot.post.title.ifBlank { "未命名帖子" },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = snapshot.post.summary.ifBlank { "该删除态优先使用回收站中的帖子快照。正常列表里即使已经移除，也不会影响这里查看。" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "帖子时间 ${formatTrashDetailTime(snapshot.post.postDisplayTimeMillis)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TrashMetaChipRows(items = albumChips)
                    currentMedia?.let { media ->
                        Text(
                            text = "当前媒体时间 ${formatTrashDetailTime(media.displayTimeMillis)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                        )
                    }
                }
            }

            TrashReadOnlyCommentCard(
                title = "帖子评论（只读）",
                emptyText = "当前帖子没有可展示的帖子评论。",
                comments = comments,
            )
        }
    }
}

@Composable
private fun TrashDeletedMediaContent(
    entry: TrashEntryUiModel,
    systemWide: Boolean,
) {
    val media = entry.mediaSnapshot
    if (media == null) {
        TrashDetailEmptyCard(text = "当前媒体快照不存在，暂时无法展示删除态详情。")
        return
    }

    val comments = remember(media.mediaId) {
        FakeCommentRepository.getMediaComments(media.mediaId)
    }
    val relatedPosts = entry.relationSnapshots

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(YingShiThemeTokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.md),
        ) {
            Text(
                text = if (systemWide) "只读媒体浏览" else "只读移除态浏览",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            TrashMediaCanvas(
                media = media,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
            ) {
                Column(
                    modifier = Modifier.padding(YingShiThemeTokens.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
                ) {
                    Text(
                        text = if (systemWide) "媒体系统删说明" else "媒体移除说明",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (systemWide) {
                            "媒体本体已从照片页和相关帖子中本地隐藏。恢复后会重新回到照片流，并补回被清除的帖子关系。"
                        } else {
                            "本次只移除了当前帖子与该媒体的关系。媒体本体和媒体评论仍然保留，不影响其他帖子。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "媒体时间 ${formatTrashDetailTime(media.displayTimeMillis)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    media.sourcePostTitle?.let { sourceTitle ->
                        Text(
                            text = "来源帖子 $sourceTitle",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                        )
                    }
                    if (relatedPosts.isEmpty()) {
                        Text(
                            text = "当前没有可展示的帖子关系快照。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        TrashMetaChipRows(
                            items = relatedPosts.map { relation ->
                                relation.postTitle.ifBlank { relation.postId }
                            },
                        )
                    }
                }
            }

            TrashReadOnlyCommentCard(
                title = "媒体评论（只读）",
                emptyText = "当前媒体没有可展示的评论。",
                comments = comments,
            )
        }
    }
}

@Composable
private fun TrashMediaCanvas(
    media: TrashMediaSnapshot,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(media.aspectRatio.coerceIn(0.78f, 1.32f))
            .clip(RoundedCornerShape(YingShiThemeTokens.radius.lg))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(media.palette.start, media.palette.end),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(YingShiThemeTokens.spacing.lg)
                .size(88.dp)
                .clip(CircleShape)
                .background(media.palette.accent.copy(alpha = 0.18f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(YingShiThemeTokens.spacing.lg)
                .fillMaxWidth(0.46f)
                .height(28.dp)
                .clip(RoundedCornerShape(YingShiThemeTokens.radius.capsule))
                .background(Color.White.copy(alpha = 0.14f)),
        )
    }
}

@Composable
private fun TrashReadOnlyCommentCard(
    title: String,
    emptyText: String,
    comments: List<CommentUiModel>,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(YingShiThemeTokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (comments.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                comments.take(10).forEach { comment ->
                    Column(verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xxs)) {
                        Text(
                            text = "${comment.author} · ${formatTrashDetailTime(comment.createdAtMillis)}",
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
                        text = "其余评论保留在本地状态中，当前删除态详情先展示最新 10 条。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrashMetaChip(text: String) {
    Surface(
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = YingShiThemeTokens.spacing.sm,
                vertical = YingShiThemeTokens.spacing.xs,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun TrashMetaChipRows(items: List<String>) {
    val spacing = YingShiThemeTokens.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        items.chunked(3).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                rowItems.forEach { item ->
                    TrashMetaChip(text = item)
                }
            }
        }
    }
}

@Composable
private fun TrashCircleButton(
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
private fun TrashActionChip(
    text: String,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(YingShiThemeTokens.radius.capsule))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (emphasized) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            },
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = YingShiThemeTokens.spacing.sm,
                vertical = YingShiThemeTokens.spacing.xs,
            ),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrashDetailEmptyCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(YingShiThemeTokens.spacing.md),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrashDetailMissingState(
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
        TrashDetailTopBar(
            entry = TrashEntryUiModel(
                id = "missing",
                type = TrashEntryType.POST_DELETED,
                deletedAtMillis = System.currentTimeMillis(),
                title = "删除态详情不可用",
                previewInfo = "当前删除项不存在。",
                palette = PhotoThumbnailPalette(
                    start = MaterialTheme.colorScheme.surfaceVariant,
                    end = MaterialTheme.colorScheme.surface,
                    accent = MaterialTheme.colorScheme.primary,
                ),
            ),
            onBack = onBack,
            onRestore = { },
            onRemove = { },
        )
        TrashDetailEmptyCard(text = "该删除项不存在或已被移出回收站。若原始对象已经从正常列表移除，这里也不会再尝试去 active repository 强行取数。")
    }
}

private fun formatTrashDetailTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}

@Preview(showBackground = true)
@Composable
private fun TrashDetailMissingStatePreview() {
    YingShiTheme {
        TrashDetailMissingState(onBack = { })
    }
}
