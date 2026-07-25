package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.yingshi.ui.components.YingShiNoticeTone
import com.example.yingshi.ui.components.yingShiClickable
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
    onEntryRestored: (List<String>) -> Unit = { },
    onShowNotice: (String, YingShiNoticeTone) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    RealTrashDetailScreen(
        route = route,
        onBack = onBack,
        onEntryRemoved = onEntryRemoved,
        onEntryRestored = onEntryRestored,
        modifier = modifier,
    )
}

@Composable
private fun TrashMediaViewerDetailPagerScreen(
    entry: TrashEntryUiModel,
    directory: CollaboratorDirectorySnapshot,
    showPermanentDeleteConfirm: Boolean,
    onShowPermanentDeleteConfirmChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onEntryRemoved: () -> Unit,
    onEntryRestored: (List<String>) -> Unit,
    onShowNotice: (String, YingShiNoticeTone) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val viewerEntries = remember(entry.id, entry.type) {
        FakeTrashRepository.getEntries(entry.type)
            .filter { it.type == entry.type && it.mediaSnapshot != null }
            .ifEmpty { listOf(entry) }
            .distinctBy { it.businessIdentityKey() }
    }
    val initialPage = viewerEntries.indexOfFirst { it.id == entry.id }
        .takeIf { it >= 0 } ?: 0
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { viewerEntries.size },
    )
    val currentEntry = viewerEntries[pagerState.currentPage.coerceIn(0, viewerEntries.lastIndex)]
    val currentMedia = currentEntry.mediaSnapshot
    val currentActorIdentity = resolveTrashActorIdentity(currentEntry, directory)
    val comments = remember(currentMedia?.mediaId) {
        currentMedia?.mediaId?.let(FakeCommentRepository::getMediaComments).orEmpty()
    }
    val originalLoadState = currentMedia?.mediaId?.let(FakeOriginalLoadRepository::getState)
        ?: OriginalLoadState.NotLoaded
    var showRestoreConfirm by remember(currentEntry.id) {
        mutableStateOf(false)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.viewerBackground),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = viewerEntries.size > 1,
            key = { page -> viewerEntries[page].id },
        ) { page ->
            val pageMedia = viewerEntries[page].mediaSnapshot
            if (pageMedia == null) {
                TrashDetailEmptyCard(
                    text = "当前媒体内容已不可查看。",
                    modifier = Modifier
                        .wrapContentSize(Alignment.Center)
                        .padding(YingShiThemeTokens.spacing.lg),
                )
            } else {
                val pageOriginalLoadState = FakeOriginalLoadRepository.getState(pageMedia.mediaId)
                TrashViewerMediaCanvas(
                    media = pageMedia,
                    originalLoadState = pageOriginalLoadState,
                    onOriginalLoadStateChange = { state ->
                        when (state) {
                            OriginalLoadState.Loaded -> FakeOriginalLoadRepository.loadOriginal(pageMedia.mediaId)
                            OriginalLoadState.Failed -> FakeOriginalLoadRepository.clearOriginal(pageMedia.mediaId)
                            else -> Unit
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrashViewerOverlayButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
            )
            Box(modifier = Modifier.weight(1f))
            currentActorIdentity?.let { actorIdentity ->
                CollaboratorMarkerBadge(
                    identity = actorIdentity,
                    size = 18.dp,
                )
            }
            TrashViewerOverlayButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "恢复",
                onClick = { showRestoreConfirm = true },
            )
            TrashViewerOverlayButton(
                icon = Icons.Filled.Delete,
                contentDescription = "永久删除",
                destructive = true,
                onClick = { onShowPermanentDeleteConfirmChange(true) },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(YingShiThemeTokens.spacing.lg),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
        ) {
            currentMedia?.let { snapshot ->
                TrashViewerOverlayButton(
                    text = originalLoadState.actionLabel(),
                    onClick = {
                        when (originalLoadState) {
                            OriginalLoadState.Loading,
                            OriginalLoadState.Loaded,
                            -> Unit
                            OriginalLoadState.NotLoaded,
                            OriginalLoadState.Failed,
                            -> FakeOriginalLoadRepository.loadOriginal(snapshot.mediaId)
                        }
                    },
                )
            }
            if (currentEntry.type == TrashEntryType.MEDIA_REMOVED) {
                TrashViewerMetaCapsule(text = trashViewerPostTitle(currentEntry))
            }
        }
        TrashViewerCommentPreview(
            comments = comments,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(YingShiThemeTokens.spacing.lg),
        )
    }

    if (showPermanentDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { onShowPermanentDeleteConfirmChange(false) },
            title = { Text("永久删除该回收站项目？") },
            text = { Text("确认后会删除回收站记录，无法恢复。") },
            containerColor = YingShiThemeTokens.colors.raisedSurface,
            titleContentColor = YingShiThemeTokens.colors.titleAccent,
            textContentColor = YingShiThemeTokens.colors.textSecondary,
            confirmButton = {
                TrashDialogActionButton(
                    text = "永久删除",
                    danger = true,
                    onClick = {
                        onShowPermanentDeleteConfirmChange(false)
                        if (FakeTrashRepository.permanentlyDeleteEntry(currentEntry.id)) {
                            onShowNotice("已永久删除回收站项目", YingShiNoticeTone.SUCCESS)
                            onEntryRemoved()
                        } else {
                            onShowNotice("该删除项不存在或已被移出回收站。", YingShiNoticeTone.WARNING)
                            onBack()
                        }
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { onShowPermanentDeleteConfirmChange(false) })
            },
        )
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("确认恢复？") },
            text = { Text("将恢复当前回收站媒体条目。") },
            containerColor = YingShiThemeTokens.colors.raisedSurface,
            titleContentColor = YingShiThemeTokens.colors.titleAccent,
            textContentColor = YingShiThemeTokens.colors.textSecondary,
            confirmButton = {
                TrashDialogActionButton(
                    text = "恢复",
                    emphasized = true,
                    onClick = {
                        showRestoreConfirm = false
                        val targetMediaIds = currentEntry.restoreTargetMediaIds()
                        val result = FakeTrashRepository.restoreEntry(currentEntry.id)
                        if (result.success) {
                            if (targetMediaIds.isEmpty()) {
                                onShowNotice("已恢复回照片页", YingShiNoticeTone.SUCCESS)
                            }
                            onEntryRestored(targetMediaIds)
                        } else {
                            onShowNotice(result.message, YingShiNoticeTone.WARNING)
                        }
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
private fun TrashMediaViewerDetailScreen(
    entry: TrashEntryUiModel,
    directory: CollaboratorDirectorySnapshot,
    showPermanentDeleteConfirm: Boolean,
    onShowPermanentDeleteConfirmChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onEntryRemoved: () -> Unit,
    onEntryRestored: (List<String>) -> Unit,
    onShowNotice: (String, YingShiNoticeTone) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val media = entry.mediaSnapshot
    val actorIdentity = resolveTrashActorIdentity(entry, directory)
    val comments = remember(media?.mediaId) {
        media?.mediaId?.let(FakeCommentRepository::getMediaComments).orEmpty()
    }
    val originalLoadState = media?.mediaId?.let(FakeOriginalLoadRepository::getState)
        ?: OriginalLoadState.NotLoaded

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.viewerBackground)
            .statusBarsPadding(),
    ) {
        if (media == null) {
            TrashDetailEmptyCard(
                text = "当前媒体内容已不可查看。",
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(YingShiThemeTokens.spacing.lg),
            )
        } else {
            AppContentMediaThumbnail(
                mediaSource = media.mediaSource,
                mediaType = media.mediaType,
                palette = media.palette,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .aspectRatio(media.aspectRatio.coerceIn(0.45f, 2.2f)),
                contentDescription = trashViewerMediaContentDescription(media),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                requestSize = 1080,
                showLoadingIndicator = true,
                showStatusBadge = true,
                showVideoPlayOverlay = media.mediaType == AppMediaType.VIDEO,
                originalLoadState = originalLoadState,
                onOriginalLoadStateChange = { state ->
                    if (media.mediaId.isNotBlank()) {
                        when (state) {
                            OriginalLoadState.Loaded -> FakeOriginalLoadRepository.loadOriginal(media.mediaId)
                            OriginalLoadState.Failed -> FakeOriginalLoadRepository.clearOriginal(media.mediaId)
                            else -> Unit
                        }
                    }
                },
            )
        }

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
            TrashViewerOverlayButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
            )
            Box(modifier = Modifier.weight(1f))
            actorIdentity?.let {
                CollaboratorMarkerBadge(
                    identity = it,
                    size = 18.dp,
                )
            }
            TrashViewerOverlayButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "恢复",
                onClick = {
                    val targetMediaIds = entry.restoreTargetMediaIds()
                    val result = FakeTrashRepository.restoreEntry(entry.id)
                    if (result.success) {
                        if (targetMediaIds.isEmpty()) {
                            onShowNotice("已恢复回照片页", YingShiNoticeTone.SUCCESS)
                        }
                        onEntryRestored(targetMediaIds)
                    } else {
                        onShowNotice(result.message, YingShiNoticeTone.WARNING)
                    }
                },
            )
            TrashViewerOverlayButton(
                icon = Icons.Filled.Delete,
                contentDescription = "永久删除",
                destructive = true,
                onClick = { onShowPermanentDeleteConfirmChange(true) },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(YingShiThemeTokens.spacing.lg),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
        ) {
            media?.let { snapshot ->
                TrashViewerOverlayButton(
                    text = originalLoadState.actionLabel(),
                    onClick = {
                        when (originalLoadState) {
                            OriginalLoadState.Loading,
                            OriginalLoadState.Loaded,
                            -> Unit
                            OriginalLoadState.NotLoaded,
                            OriginalLoadState.Failed,
                            -> FakeOriginalLoadRepository.loadOriginal(snapshot.mediaId)
                        }
                    },
                )
            }
            if (entry.type == TrashEntryType.MEDIA_REMOVED) {
                TrashViewerMetaCapsule(text = trashViewerPostTitle(entry))
            }
        }
        TrashViewerCommentPreview(
            comments = comments,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(YingShiThemeTokens.spacing.lg),
        )
    }

    if (showPermanentDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { onShowPermanentDeleteConfirmChange(false) },
            title = { Text("永久删除该回收站项目？") },
            text = {
                Text(
                    "确认后会删除回收站记录。属于媒体删除的项目会同时删除对应的原文件和预览文件，删除后无法恢复。",
                )
            },
            containerColor = YingShiThemeTokens.colors.raisedSurface,
            titleContentColor = YingShiThemeTokens.colors.titleAccent,
            textContentColor = YingShiThemeTokens.colors.textSecondary,
            confirmButton = {
                TrashDialogActionButton(
                    text = "永久删除",
                    danger = true,
                    onClick = {
                        onShowPermanentDeleteConfirmChange(false)
                        if (FakeTrashRepository.permanentlyDeleteEntry(entry.id)) {
                            onShowNotice("已永久删除回收站项目", YingShiNoticeTone.SUCCESS)
                            onEntryRemoved()
                        } else {
                            onShowNotice("该删除项不存在或已被移出回收站。", YingShiNoticeTone.WARNING)
                            onBack()
                        }
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { onShowPermanentDeleteConfirmChange(false) })
            },
        )
    }
}

@Composable
private fun TrashViewerOverlayButton(
    onClick: () -> Unit,
    text: String? = null,
    icon: ImageVector? = null,
    contentDescription: String = text.orEmpty(),
    destructive: Boolean = false,
) {
    val colors = YingShiThemeTokens.colors
    val contentColor = colors.viewerText.copy(alpha = 0.94f)
    Surface(
        modifier = Modifier
            .then(
                if (icon != null) {
                    Modifier.size(44.dp)
                } else {
                    Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                },
            )
            .clip(RoundedCornerShape(YingShiThemeTokens.radius.capsule))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (destructive) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.88f)
        } else {
            colors.viewerSurface.copy(alpha = 0.82f)
        },
        border = BorderStroke(
            1.dp,
            if (destructive) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.24f)
            } else {
                colors.viewerAccent.copy(alpha = 0.16f)
            },
        ),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            Text(
                text = text.orEmpty(),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor,
            )
        }
    }
}

@Composable
private fun TrashViewerCommentPreview(
    comments: List<CommentUiModel>,
    modifier: Modifier = Modifier,
) {
    if (comments.isEmpty()) return
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier.fillMaxWidth(0.62f),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = colors.viewerSurface.copy(alpha = 0.74f),
        border = BorderStroke(1.dp, colors.viewerAccent.copy(alpha = 0.14f)),
    ) {
        Column(
            modifier = Modifier.padding(YingShiThemeTokens.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xxs),
        ) {
            Text(
                text = "媒体评论",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.viewerText.copy(alpha = 0.92f),
            )
            comments.take(2).forEach { comment ->
                Text(
                    text = "${comment.author}: ${comment.content}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.viewerTextSecondary.copy(alpha = 0.92f),
                )
            }
        }
    }
}

@Composable
private fun TrashViewerMetaCapsule(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = colors.viewerSurface.copy(alpha = 0.76f),
        border = BorderStroke(1.dp, colors.viewerAccent.copy(alpha = 0.14f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.viewerText.copy(alpha = 0.92f),
        )
    }
}

private fun trashViewerMediaContentDescription(media: TrashMediaSnapshot): String {
    return when (media.mediaType) {
        AppMediaType.VIDEO -> "回收站视频"
        AppMediaType.IMAGE -> "回收站照片"
    }
}

@Composable
private fun TrashPostViewerDetailScreen(
    entry: TrashEntryUiModel,
    directory: CollaboratorDirectorySnapshot,
    showPermanentDeleteConfirm: Boolean,
    onShowPermanentDeleteConfirmChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onEntryRemoved: () -> Unit,
    onEntryRestored: (List<String>) -> Unit,
    onShowNotice: (String, YingShiNoticeTone) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val snapshot = entry.postSnapshot
    val actorIdentity = resolveTrashActorIdentity(entry, directory)
    val postComments = remember(snapshot?.post?.id) {
        snapshot?.post?.id?.let(FakeCommentRepository::getPostComments).orEmpty()
    }
    var selectedMedia by remember(entry.id) {
        mutableStateOf<TrashMediaSnapshot?>(null)
    }
    var showRestoreConfirm by remember(entry.id) {
        mutableStateOf(false)
    }

    fun restorePost() {
        val targetMediaIds = entry.restoreTargetMediaIds()
        val result = FakeTrashRepository.restoreEntry(entry.id)
        if (result.success) {
            if (targetMediaIds.isEmpty()) {
                onShowNotice("已恢复当前小相册", YingShiNoticeTone.SUCCESS)
            }
            onEntryRestored(targetMediaIds)
        } else {
            onShowNotice(result.message, YingShiNoticeTone.WARNING)
        }
    }

    if (selectedMedia != null) {
        TrashPostMediaViewerOverlay(
            entry = entry,
            actorIdentity = actorIdentity,
            media = selectedMedia,
            onBack = { selectedMedia = null },
            onRestorePost = { showRestoreConfirm = true },
            onRequestDeletePost = { onShowPermanentDeleteConfirmChange(true) },
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.appBackground)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = YingShiThemeTokens.spacing.lg,
                    vertical = YingShiThemeTokens.spacing.md,
                ),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.md),
        ) {
            TrashPostViewerTopBar(
                entry = entry,
                actorIdentity = actorIdentity,
                onBack = onBack,
                onRestore = { showRestoreConfirm = true },
                onRemove = { onShowPermanentDeleteConfirmChange(true) },
            )

            if (snapshot == null) {
                TrashDetailEmptyCard(text = "当前小相册内容已不可查看。")
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
                    color = colors.raisedSurface,
                    border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.58f)),
                ) {
                    Column(
                        modifier = Modifier.padding(YingShiThemeTokens.spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
                    ) {
                        Text(
                            text = snapshot.post.title.ifBlank { "未命名小相册" },
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.titleAccent,
                        )
                        Text(
                            text = snapshot.post.summary.ifBlank { entry.previewInfo },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                        Text(
                            text = "删除于 ${formatTrashDetailTime(entry.deletedAtMillis)} · 小相册时间 ${formatTrashDetailTime(snapshot.post.postDisplayTimeMillis)} · ${snapshot.mediaSnapshots.size} 项媒体",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary.copy(alpha = 0.84f),
                        )
                    }
                }

                if (snapshot.mediaSnapshots.isEmpty()) {
                    TrashDetailEmptyCard(text = "这个小相册里的媒体已不可用。")
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        snapshot.mediaSnapshots.chunked(3).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                row.forEach { media ->
                                    TrashPostMediaGridTile(
                                        media = media,
                                        onClick = { selectedMedia = media },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(3 - row.size) {
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
                TrashReadOnlyCommentCard(
                    title = "小相册评论",
                    emptyText = "当前小相册没有可展示的评论。",
                    comments = postComments,
                )
            }
        }
    }

    if (showPermanentDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { onShowPermanentDeleteConfirmChange(false) },
            title = { Text("永久删除该回收站项目？") },
            text = {
                Text("确认后会删除回收站记录。属于媒体删除的项目会同时删除对应的原文件和预览文件，删除后无法恢复。")
            },
            containerColor = YingShiThemeTokens.colors.raisedSurface,
            titleContentColor = YingShiThemeTokens.colors.titleAccent,
            textContentColor = YingShiThemeTokens.colors.textSecondary,
            confirmButton = {
                TrashDialogActionButton(
                    text = "永久删除",
                    danger = true,
                    onClick = {
                        onShowPermanentDeleteConfirmChange(false)
                        if (FakeTrashRepository.permanentlyDeleteEntry(entry.id)) {
                            onShowNotice("已永久删除回收站项目", YingShiNoticeTone.SUCCESS)
                            onEntryRemoved()
                        } else {
                            onShowNotice("该删除项不存在或已被移出回收站。", YingShiNoticeTone.WARNING)
                            onBack()
                        }
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { onShowPermanentDeleteConfirmChange(false) })
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
                    onClick = {
                        showRestoreConfirm = false
                        restorePost()
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
private fun TrashPostViewerTopBar(
    entry: TrashEntryUiModel,
    actorIdentity: CollaboratorIdentityUiModel?,
    onBack: () -> Unit,
    onRestore: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrashCircleButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "返回",
            onClick = onBack,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.type.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "回收站小相册查看",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
            }
            actorIdentity?.let {
                CollaboratorMarkerBadge(
                    identity = it,
                    size = 18.dp,
                )
            }
        }
        TrashActionChip(text = "恢复", emphasized = true, onClick = onRestore)
        TrashActionChip(text = "删除", emphasized = false, destructive = true, onClick = onRemove)
    }
}

@Composable
private fun TrashPostMediaGridTile(
    media: TrashMediaSnapshot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
    ) {
        TrashMediaCanvas(
            media = media,
            modifier = Modifier.matchParentSize(),
        )
        if (media.mediaSource == null) {
            TrashDeletedMediaOverlay(modifier = Modifier.matchParentSize())
        }
        if (media.mediaType == AppMediaType.VIDEO) {
            if (formatVideoDurationLabel(media.videoDurationMillis) != null) {
                VideoDurationBadge(
                    durationMillis = media.videoDurationMillis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(5.dp),
                )
            } else {
                VideoMediaMarker(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(5.dp),
                )
            }
        }
    }
}

@Composable
private fun TrashPostMediaViewerOverlay(
    entry: TrashEntryUiModel,
    actorIdentity: CollaboratorIdentityUiModel?,
    media: TrashMediaSnapshot?,
    onBack: () -> Unit,
    onRestorePost: () -> Unit,
    onRequestDeletePost: () -> Unit,
) {
    val originalLoadState = media?.mediaId?.let(FakeOriginalLoadRepository::getState)
        ?: OriginalLoadState.NotLoaded

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(YingShiThemeTokens.colors.viewerBackground)
            .statusBarsPadding(),
    ) {
        if (media == null || media.mediaSource == null) {
            TrashDeletedMediaOverlay(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f),
            )
        } else {
            AppContentMediaThumbnail(
                mediaSource = media.mediaSource,
                mediaType = media.mediaType,
                palette = media.palette,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .aspectRatio(media.aspectRatio.coerceIn(0.45f, 2.2f)),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                requestSize = 1080,
                showLoadingIndicator = true,
                showStatusBadge = true,
                showVideoPlayOverlay = media.mediaType == AppMediaType.VIDEO,
                originalLoadState = originalLoadState,
                onOriginalLoadStateChange = { state ->
                    when (state) {
                        OriginalLoadState.Loaded -> FakeOriginalLoadRepository.loadOriginal(media.mediaId)
                        OriginalLoadState.Failed -> FakeOriginalLoadRepository.clearOriginal(media.mediaId)
                        else -> Unit
                    }
                },
            )
        }

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
            TrashViewerOverlayButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
            )
            Box(modifier = Modifier.weight(1f))
            actorIdentity?.let {
                CollaboratorMarkerBadge(
                    identity = it,
                    size = 18.dp,
                )
            }
            TrashViewerOverlayButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "恢复",
                onClick = onRestorePost,
            )
            TrashViewerOverlayButton(
                icon = Icons.Filled.Delete,
                contentDescription = "永久删除",
                destructive = true,
                onClick = onRequestDeletePost,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(YingShiThemeTokens.spacing.lg),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
        ) {
            if (media?.mediaSource != null) {
                TrashViewerOverlayButton(
                    text = originalLoadState.actionLabel(),
                    onClick = {
                        when (originalLoadState) {
                            OriginalLoadState.Loading,
                            OriginalLoadState.Loaded,
                            -> Unit
                            OriginalLoadState.NotLoaded,
                            OriginalLoadState.Failed,
                            -> FakeOriginalLoadRepository.loadOriginal(media.mediaId)
                        }
                    },
                )
            }
            TrashViewerMetaCapsule(text = entry.postSnapshot?.post?.title ?: entry.title)
        }
    }
}

@Composable
private fun TrashDeletedMediaOverlay(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(YingShiThemeTokens.radius.lg))
            .background(YingShiThemeTokens.colors.viewerBackground.copy(alpha = 0.84f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "已删除",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = YingShiThemeTokens.colors.viewerText,
        )
    }
}

@Composable
private fun TrashDetailTopBar(
    entry: TrashEntryUiModel,
    actorIdentity: CollaboratorIdentityUiModel?,
    onBack: () -> Unit,
    onRestore: () -> Unit,
    onRemove: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrashCircleButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "返回",
            onClick = onBack,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.type.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "回收站详情",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
            }
            actorIdentity?.let {
                CollaboratorMarkerBadge(
                    identity = it,
                    size = 18.dp,
                )
            }
        }
        TrashActionChip(text = "恢复", emphasized = true, onClick = onRestore)
        TrashActionChip(
            text = "永久删除",
            emphasized = false,
            destructive = true,
            onClick = onRemove,
        )
    }
}

@Composable
private fun TrashDetailStatusCard(entry: TrashEntryUiModel) {
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = colors.raisedSurface,
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.58f)),
    ) {
        Column(
            modifier = Modifier.padding(YingShiThemeTokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
        ) {
            Text(
                text = entry.title.ifBlank { "未命名删除项" },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            Text(
                text = entry.previewInfo.ifBlank { "使用删除前保存的内容展示。" },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Text(
                text = "删除于 ${formatTrashDetailTime(entry.deletedAtMillis)} · 可在这里查看、恢复或永久删除",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary.copy(alpha = 0.84f),
            )
        }
    }
}

@Composable
private fun TrashDeletedLargeAlbumContent(entry: TrashEntryUiModel) {
    val colors = YingShiThemeTokens.colors
    val snapshot = entry.albumSnapshot
    if (snapshot == null) {
        TrashDetailEmptyCard(text = "当前大相册快照不可用。")
        return
    }

    val postCount = snapshot.postSnapshots.size
    val mediaCount = snapshot.postSnapshots.sumOf { it.mediaSnapshots.size }
    val previewTitles = snapshot.postSnapshots
        .map { it.post.title.ifBlank { "未命名小相册" } }
        .take(4)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = colors.raisedSurface,
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.58f)),
    ) {
        Column(
            modifier = Modifier.padding(YingShiThemeTokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.md),
        ) {
            Text(
                text = "大相册内容",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
                color = colors.sectionBackground.copy(alpha = 0.58f),
            ) {
                Column(
                    modifier = Modifier.padding(YingShiThemeTokens.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
                ) {
                    Text(
                        text = snapshot.album.title.ifBlank { "未命名大相册" },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.titleAccent,
                    )
                    Text(
                        text = snapshot.album.subtitle.ifBlank {
                            "恢复后会把这个大相册和本次一起删除的小相册整组带回。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Text(
                        text = "包含 $postCount 个小相册 · $mediaCount 项媒体快照",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                }
            }
            if (previewTitles.isEmpty()) {
                TrashDetailEmptyCard(text = "这个大相册删除时没有可展示的小相册快照。")
            } else {
                TrashMetaChipRows(items = previewTitles)
            }
        }
    }
}

@Composable
private fun TrashDeletedPostContent(entry: TrashEntryUiModel) {
    val colors = YingShiThemeTokens.colors
    val snapshot = entry.postSnapshot
    if (snapshot == null) {
        TrashDetailEmptyCard(text = "当前小相册内容已不可查看。")
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
        color = colors.raisedSurface,
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.58f)),
    ) {
        Column(
            modifier = Modifier.padding(YingShiThemeTokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.md),
        ) {
            Text(
                text = "小相册内容",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )

            if (mediaSnapshots.isEmpty()) {
                TrashDetailEmptyCard(text = "当前小相册没有可展示的媒体快照。")
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(372.dp),
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
                    color = colors.textSecondary,
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
                color = colors.sectionBackground.copy(alpha = 0.58f),
            ) {
                Column(
                    modifier = Modifier.padding(YingShiThemeTokens.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
                ) {
                    Text(
                        text = snapshot.post.title.ifBlank { "未命名小相册" },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.titleAccent,
                    )
                    Text(
                        text = snapshot.post.summary.ifBlank { "这是删除前保存的内容，恢复后会回到对应位置。" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Text(
                        text = "小相册时间 ${formatTrashDetailTime(snapshot.post.postDisplayTimeMillis)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                    TrashMetaChipRows(items = albumChips)
                    currentMedia?.let { media ->
                        Text(
                            text = "当前媒体时间 ${formatTrashDetailTime(media.displayTimeMillis)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary.copy(alpha = 0.84f),
                        )
                    }
                }
            }

            TrashReadOnlyCommentCard(
                title = "小相册评论",
                emptyText = "当前小相册没有可展示的小相册评论。",
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
    val colors = YingShiThemeTokens.colors
    val media = entry.mediaSnapshot
    if (media == null) {
        TrashDetailEmptyCard(text = "当前媒体内容已不可查看。")
        return
    }

    val comments = remember(media.mediaId) {
        FakeCommentRepository.getMediaComments(media.mediaId)
    }
    val relatedPosts = entry.relationSnapshots

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = colors.raisedSurface,
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.58f)),
    ) {
        Column(
            modifier = Modifier.padding(YingShiThemeTokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.md),
        ) {
            Text(
                text = "媒体内容",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
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
                color = colors.sectionBackground.copy(alpha = 0.58f),
            ) {
                Column(
                    modifier = Modifier.padding(YingShiThemeTokens.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
                ) {
                    Text(
                        text = if (systemWide) "恢复后会怎样" else "小相册关系",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.titleAccent,
                    )
                    Text(
                        text = if (systemWide) {
                            "媒体本体已从照片页和相关小相册中本地隐藏。恢复后会重新回到照片流，并补回被清除的小相册关系。"
                        } else {
                            "本次只移除了当前小相册与该媒体的关系。媒体本体和媒体评论仍然保留，不影响其他小相册。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Text(
                        text = "媒体时间 ${formatTrashDetailTime(media.displayTimeMillis)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                    media.sourcePostTitle?.let { sourceTitle ->
                        Text(
                            text = "来源小相册 $sourceTitle",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary.copy(alpha = 0.84f),
                        )
                    }
                    if (relatedPosts.isEmpty()) {
                        Text(
                            text = "当前没有可展示的小相册关系快照。",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
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
                title = "媒体评论",
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
    if (media.mediaSource != null) {
        AppContentMediaThumbnail(
            mediaSource = media.mediaSource,
            mediaType = media.mediaType,
            palette = media.palette,
            modifier = modifier
                .aspectRatio(media.aspectRatio.coerceIn(0.45f, 2.2f))
                .clip(RoundedCornerShape(YingShiThemeTokens.radius.lg)),
            requestSize = 720,
            showLoadingIndicator = true,
            showStatusBadge = true,
            showVideoPlayOverlay = media.mediaType == AppMediaType.VIDEO,
        )
        return
    }

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
                .background(YingShiThemeTokens.colors.viewerSurface.copy(alpha = 0.18f)),
        )
    }
}

@Composable
private fun TrashViewerMediaCanvas(
    media: TrashMediaSnapshot,
    originalLoadState: OriginalLoadState,
    onOriginalLoadStateChange: (OriginalLoadState) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .padding(top = 68.dp, bottom = 104.dp),
        contentAlignment = Alignment.Center,
    ) {
        val aspect = media.aspectRatio.coerceIn(0.45f, 2.2f)
        val availableWidth = maxWidth
        val availableHeight = maxHeight.coerceAtLeast(120.dp)
        val widthFromHeight = availableHeight * aspect
        val fittedWidth = if (widthFromHeight < availableWidth) widthFromHeight else availableWidth
        val fittedHeight = fittedWidth / aspect

        if (media.mediaSource != null) {
            AppContentMediaThumbnail(
                mediaSource = media.mediaSource,
                mediaType = media.mediaType,
                palette = media.palette,
                modifier = Modifier
                    .width(fittedWidth)
                    .height(fittedHeight),
                contentDescription = trashViewerMediaContentDescription(media),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                requestSize = 1080,
                showLoadingIndicator = true,
                showStatusBadge = true,
                showVideoPlayOverlay = media.mediaType == AppMediaType.VIDEO,
                originalLoadState = originalLoadState,
                onOriginalLoadStateChange = onOriginalLoadStateChange,
            )
        } else {
            TrashDeletedMediaOverlay(
                modifier = Modifier
                    .width(fittedWidth)
                    .height(fittedHeight),
            )
        }
    }
}

@Composable
private fun TrashReadOnlyCommentCard(
    title: String,
    emptyText: String,
    comments: List<CommentUiModel>,
) {
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = colors.raisedSurface,
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.58f)),
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
                            text = "${comment.author} · ${formatTrashDetailTime(comment.createdAtMillis)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        Text(
                            text = comment.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.titleAccent,
                        )
                    }
                }
                if (comments.size > 10) {
                    Text(
                        text = "还有 ${comments.size - 10} 条评论未展开",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                }
            }
        }

    }
}

@Composable
private fun TrashMetaChip(text: String) {
    val colors = YingShiThemeTokens.colors

    Surface(
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = colors.primaryContainer.copy(alpha = 0.58f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.56f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = YingShiThemeTokens.spacing.sm,
                vertical = YingShiThemeTokens.spacing.xs,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = colors.titleAccent,
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
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val shape = CircleShape

    Surface(
        modifier = Modifier
            .size(44.dp)
            .yingShiClickable(
                shape = shape,
                pressedScale = 0.94f,
                onClick = onClick,
            ),
        shape = shape,
        color = colors.raisedSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.66f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colors.titleAccent,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun TrashActionChip(
    text: String,
    emphasized: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)

    Surface(
        modifier = Modifier.yingShiClickable(
            shape = shape,
            pressedScale = 0.96f,
            onClick = onClick,
        ),
        shape = shape,
        color = when {
            emphasized -> colors.primaryContainer.copy(alpha = 0.78f)
            destructive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)
            else -> colors.raisedSurface.copy(alpha = 0.94f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = when {
                emphasized -> colors.glassStroke.copy(alpha = 0.68f)
                destructive -> MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
                else -> colors.dividerSoft.copy(alpha = 0.66f)
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
            color = when {
                emphasized -> colors.titleAccent
                destructive -> MaterialTheme.colorScheme.onErrorContainer
                else -> colors.textSecondary
            },
        )
    }
}

@Composable
private fun TrashDetailEmptyCard(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = colors.sectionBackground.copy(alpha = 0.56f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.54f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(YingShiThemeTokens.spacing.md),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun TrashDetailMissingState(
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
        TrashDetailTopBar(
            entry = TrashEntryUiModel(
                id = "missing",
                type = TrashEntryType.SMALL_ALBUM_DELETED,
                deletedAtMillis = System.currentTimeMillis(),
                title = "回收站项目不可用",
                previewInfo = "当前删除项不存在。",
                palette = PhotoThumbnailPalette(
                    start = colors.sectionBackground,
                    end = colors.raisedSurface,
                    accent = colors.primaryContainer,
                ),
            ),
            actorIdentity = null,
            onBack = onBack,
            onRestore = { },
            onRemove = { },
        )
        TrashDetailEmptyCard(text = "该项目不存在或已被移出回收站。")
    }
}

private fun formatTrashDetailTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}

private fun trashViewerPostTitle(entry: TrashEntryUiModel): String {
    return entry.relationSnapshots.firstOrNull()?.postTitle
        ?: entry.mediaSnapshot?.sourcePostTitle
        ?: entry.title.removePrefix("从「").substringBefore("」移除媒体")
        ?: entry.sourcePostId
        ?: "来源小相册"
}

@Preview(showBackground = true)
@Composable
private fun TrashDetailMissingStatePreview() {
    YingShiTheme {
        TrashDetailMissingState(onBack = { })
    }
}
