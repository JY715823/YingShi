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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PostDetailScreen(
    route: PostDetailPlaceholderRoute,
    onBack: () -> Unit,
    onOpenGearEdit: (GearEditRoute) -> Unit,
    onOpenCacheManagement: (CacheManagementRoute) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val detail = FakeAlbumRepository.getPostDetail(route)
    if (detail.mediaItems.isEmpty()) {
        PostDetailMissingState(
            onBack = onBack,
            modifier = modifier,
        )
        return
    }
    var inPostViewerInitialPage by rememberSaveable(route.postId) {
        mutableStateOf<Int?>(null)
    }
    var mediaCommentPage by rememberSaveable(route.postId) {
        mutableStateOf<Int?>(null)
    }

    BackHandler(enabled = mediaCommentPage != null) {
        mediaCommentPage = null
    }
    BackHandler(enabled = inPostViewerInitialPage != null) {
        inPostViewerInitialPage = null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val viewerInitialPage = inPostViewerInitialPage
        if (viewerInitialPage != null) {
            PhotoViewerScreen(
                route = detail.toInPostViewerRoute(initialIndex = viewerInitialPage),
                onBack = { inPostViewerInitialPage = null },
                onOpenCacheManagement = onOpenCacheManagement,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PostDetailContent(
                detail = detail,
                onBack = onBack,
                onOpenGearEdit = { onOpenGearEdit(GearEditRoute(route.postId)) },
                onOpenMediaViewer = { page -> inPostViewerInitialPage = page },
                onOpenMediaComments = { page -> mediaCommentPage = page },
                modifier = Modifier.fillMaxSize(),
            )

            mediaCommentPage?.let { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.18f))
                        .clickable { mediaCommentPage = null },
                )
                MediaCommentPlaceholderSheet(
                    media = detail.mediaItems[page.coerceIn(0, detail.mediaItems.lastIndex)],
                    onClose = { mediaCommentPage = null },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = YingShiThemeTokens.spacing.lg)
                        .padding(bottom = YingShiThemeTokens.spacing.lg),
                )
            }
        }
    }
}

@Composable
private fun PostDetailMissingState(
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
        PostDetailTopBar(
            onBack = onBack,
            onExport = {},
            onEdit = {},
        )
        Text(
            text = "当前帖子没有可展示的媒体，可能已经被删除、被移出关系，或仍处于系统删除状态。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PostDetailContent(
    detail: PostDetailUiModel,
    onBack: () -> Unit,
    onOpenGearEdit: () -> Unit,
    onOpenMediaViewer: (Int) -> Unit,
    onOpenMediaComments: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val context = LocalContext.current
    var showPostCacheDialog by rememberSaveable(detail.postId) { mutableStateOf(false) }
    val pagerState = rememberPagerState(
        pageCount = { detail.mediaItems.size },
    )
    val currentPage = pagerState.currentPage.coerceIn(0, detail.mediaItems.lastIndex)
    val currentMedia = detail.mediaItems[currentPage]
    val postMediaIds = remember(detail.mediaItems) {
        detail.mediaItems.map { it.id }
    }
    val currentOriginalState = FakeOriginalLoadRepository.getState(currentMedia.id)
    val postOriginalSummary = FakeOriginalLoadRepository.getPostSummary(postMediaIds)
    val postCacheSummary = FakeMediaCacheRepository.getSummary(postMediaIds)


    Column(
        modifier = modifier
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.lg)
            .padding(top = spacing.xs, bottom = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        PostDetailTopBar(
            onBack = onBack,
            onExport = {
                Toast.makeText(context, "导出 / 保存将在后续阶段接入", Toast.LENGTH_SHORT).show()
            },
            onEdit = onOpenGearEdit,
        )

        PostMediaArea(
            detail = detail,
            currentPage = currentPage,
            modifier = Modifier.fillMaxWidth(),
            onOpenMedia = { onOpenMediaViewer(currentPage) },
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(372.dp),
                beyondViewportPageCount = 1,
                key = { page -> detail.mediaItems[page].id },
            ) { page ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    PostMediaCard(
                        media = detail.mediaItems[page],
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenMediaViewer(page) },
                    )
                }
            }
        }

        PostMediaInfoRow(
            media = currentMedia,
            commentCount = FakeCommentRepository.mediaCommentCount(currentMedia.id),
            originalLoadState = currentOriginalState,
            onCommentClick = { onOpenMediaComments(currentPage) },
            onOriginalClick = {
                when (currentOriginalState) {
                    OriginalLoadState.NotLoaded -> {
                        FakeOriginalLoadRepository.loadOriginal(currentMedia.id)
                        Toast.makeText(context, "\u5f00\u59cb\u52a0\u8f7d\u539f\u56fe", Toast.LENGTH_SHORT).show()
                    }

                    OriginalLoadState.Loading -> {
                        Toast.makeText(context, "\u539f\u56fe\u52a0\u8f7d\u4e2d...", Toast.LENGTH_SHORT).show()
                    }

                    OriginalLoadState.Loaded -> {
                        Toast.makeText(context, "\u5df2\u52a0\u8f7d\u539f\u56fe", Toast.LENGTH_SHORT).show()
                    }

                    OriginalLoadState.Failed -> {
                        FakeOriginalLoadRepository.retryOriginal(currentMedia.id)
                        Toast.makeText(context, "\u91cd\u8bd5\u52a0\u8f7d\u539f\u56fe", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )

        PostInfoSection(
            detail = detail,
            originalSummary = postOriginalSummary,
            cacheSummary = postCacheSummary,
            onLoadAllOriginals = {
                FakeOriginalLoadRepository.loadAllOriginals(postMediaIds)
                Toast.makeText(context, "\u5f00\u59cb\u52a0\u8f7d\u5168\u5e16\u539f\u56fe", Toast.LENGTH_SHORT).show()
            },
            onClearPostCache = { showPostCacheDialog = true },
        )

        PostCommentSection(postId = detail.postId)
    }

    if (showPostCacheDialog) {
        AlertDialog(
            onDismissRequest = { showPostCacheDialog = false },
            title = { Text("清理本帖缓存") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
                ) {
                    Text("当前帖子共 ${postCacheSummary.mediaCount} 个媒体，fake 缓存总量 ${postCacheSummary.totalSizeLabel}。")
                    Text(
                        text = "这里只清理当前帖内媒体的原图 / 视频缓存状态，不影响媒体本体和评论。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs)) {
                    TextButton(
                        onClick = {
                            FakeMediaCacheRepository.clearPostOriginalCaches(postMediaIds)
                            showPostCacheDialog = false
                            Toast.makeText(context, "已清理本帖原图缓存", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Text("清原图")
                    }
                    TextButton(
                        onClick = {
                            FakeMediaCacheRepository.clearPostVideoCaches(postMediaIds)
                            showPostCacheDialog = false
                            Toast.makeText(context, "已清理本帖视频缓存", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Text("清视频")
                    }
                    TextButton(
                        onClick = {
                            FakeMediaCacheRepository.clearPostOriginalCaches(postMediaIds)
                            FakeMediaCacheRepository.clearPostVideoCaches(postMediaIds)
                            showPostCacheDialog = false
                            Toast.makeText(context, "已清理本帖缓存", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Text("全清")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showPostCacheDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun PostDetailTopBar(
    onBack: () -> Unit,
    onExport: () -> Unit,
    onEdit: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PostCircleButton(text = "<", onClick = onBack)
        Text(
            text = "帖子详情",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
        PostActionChip(text = "导出/保存", onClick = onExport)
        PostCircleButton(text = "齿", onClick = onEdit)
    }
}

@Composable
private fun PostMediaArea(
    detail: PostDetailUiModel,
    currentPage: Int,
    modifier: Modifier = Modifier,
    onOpenMedia: () -> Unit,
    pager: @Composable () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(radius.xl),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        ) {
            Box(
                modifier = Modifier
                    .padding(spacing.xs)
                    .clip(RoundedCornerShape(radius.lg)),
            ) {
                pager()
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${currentPage + 1} / ${detail.mediaItems.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "同帖媒体序列",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            )
            Spacer(modifier = Modifier.weight(1f))
            PostActionChip(text = "查看态占位", onClick = onOpenMedia)
        }
    }
}

@Composable
private fun PostMediaCard(
    media: PostDetailMediaUiModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Box(
        modifier = modifier
            .aspectRatio(
                if (media.mediaType == AppMediaType.VIDEO) {
                    media.aspectRatio.coerceIn(1.33f, 1.78f)
                } else {
                    media.aspectRatio.coerceIn(0.86f, 1.18f)
                },
            )
            .clip(RoundedCornerShape(radius.lg))
            .clickable(onClick = onClick)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(media.palette.start, media.palette.end),
                ),
            ),
    ) {
        if (media.mediaType == AppMediaType.VIDEO) {
            VideoMediaMarker(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(spacing.md),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(spacing.lg)
                .size(74.dp)
                .clip(CircleShape)
                .background(media.palette.accent.copy(alpha = 0.16f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(spacing.lg)
                .fillMaxWidth(0.48f)
                .height(30.dp)
                .clip(RoundedCornerShape(radius.capsule))
                .background(Color.White.copy(alpha = 0.13f)),
        )
    }
}

@Composable
private fun PostMediaInfoRow(
    media: PostDetailMediaUiModel,
    commentCount: Int,
    originalLoadState: OriginalLoadState,
    onCommentClick: () -> Unit,
    onOriginalClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PostMetaCapsule(text = formatPostTime(media.displayTimeMillis))
        Spacer(modifier = Modifier.weight(1f))
        PostActionChip(text = "评", onClick = onCommentClick)
        PostMetaCapsule(text = commentCount.toString())
        PostActionChip(
            text = originalLoadState.label,
            onClick = onOriginalClick,
        )
    }
}

@Composable
private fun PostInfoSection(
    detail: PostDetailUiModel,
    originalSummary: PostOriginalLoadSummary,
    cacheSummary: AppMediaCacheSummary,
    onLoadAllOriginals: () -> Unit,
    onClearPostCache: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = detail.contributorLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = detail.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatPostTime(detail.postDisplayTimeMillis),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                detail.albumChips.forEach { chip ->
                    PostMetaCapsule(text = chip)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PostActionChip(text = originalSummary.buttonLabel, onClick = onLoadAllOriginals)
                PostMetaCapsule(text = "缓存 ${cacheSummary.totalSizeLabel}")
                PostActionChip(text = "清理本帖缓存", onClick = onClearPostCache)
            }
        }
    }
}

@Composable
private fun PostCommentSection(postId: String) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val context = LocalContext.current
    val copyComment = rememberCommentCopyHandler()
    val comments = FakeCommentRepository.getPostComments(postId)
    var expanded by rememberSaveable(postId) { mutableStateOf(false) }
    var actionCommentId by rememberSaveable(postId) { mutableStateOf<String?>(null) }
    var editingCommentId by rememberSaveable(postId) { mutableStateOf<String?>(null) }
    var editingDraft by rememberSaveable(postId) { mutableStateOf("") }
    var selectedCommentId by rememberSaveable(postId) { mutableStateOf<String?>(null) }
    var selectedCommentValue by rememberSaveable(postId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val visibleComments = comments.visibleComments(expanded)

    BackHandler(enabled = selectedCommentId != null) {
        selectedCommentId = null
        selectedCommentValue = TextFieldValue("")
    }
    BackHandler(enabled = actionCommentId != null) {
        actionCommentId = null
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = "帖子评论",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (visibleComments.isEmpty()) {
                Text(
                    text = "当前帖子还没有评论，先写下第一条本地评论。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                visibleComments.forEach { comment ->
                    CommentListItem(
                        comment = comment,
                        timeLabel = formatPostTime(comment.createdAtMillis),
                        onLongPress = {
                            selectedCommentId = null
                            selectedCommentValue = TextFieldValue("")
                            editingCommentId = null
                            editingDraft = ""
                            actionCommentId = comment.id
                        },
                        onClick = {
                            if (selectedCommentId != null) {
                                selectedCommentId = null
                                selectedCommentValue = TextFieldValue("")
                            }
                            actionCommentId = null
                        },
                        showInlineActionMenu = actionCommentId == comment.id &&
                            selectedCommentId != comment.id &&
                            editingCommentId != comment.id,
                        onCopyFull = {
                            copyComment(comment.content)
                            actionCommentId = null
                        },
                        onSelectText = {
                            selectedCommentId = comment.id
                            selectedCommentValue = fullCommentSelectionValue(comment.content)
                            editingCommentId = null
                            editingDraft = ""
                            actionCommentId = null
                        },
                        onEdit = {
                            editingCommentId = comment.id
                            editingDraft = comment.content
                            selectedCommentId = null
                            selectedCommentValue = TextFieldValue("")
                            actionCommentId = null
                        },
                        onDelete = {
                            FakeCommentRepository.deletePostComment(postId, comment.id)
                            if (selectedCommentId == comment.id) {
                                selectedCommentId = null
                                selectedCommentValue = TextFieldValue("")
                            }
                            if (editingCommentId == comment.id) {
                                editingCommentId = null
                                editingDraft = ""
                            }
                            actionCommentId = null
                            Toast.makeText(context, "评论已删除", Toast.LENGTH_SHORT).show()
                        },
                        isEditing = editingCommentId == comment.id,
                        editingValue = if (editingCommentId == comment.id) editingDraft else comment.content,
                        onEditingValueChange = { editingDraft = it },
                        onSaveEdit = {
                            FakeCommentRepository.updatePostComment(
                                postId = postId,
                                commentId = comment.id,
                                content = editingDraft,
                            )
                            editingCommentId = null
                            editingDraft = ""
                            actionCommentId = null
                            Toast.makeText(context, "评论已更新", Toast.LENGTH_SHORT).show()
                        },
                        onCancelEdit = {
                            editingCommentId = null
                            editingDraft = ""
                        },
                        selectionMode = selectedCommentId == comment.id,
                        selectionFieldValue = if (selectedCommentId == comment.id) {
                            selectedCommentValue
                        } else {
                            TextFieldValue(comment.content)
                        },
                        onSelectionFieldValueChange = { selectedCommentValue = it },
                        onCopySelection = if (selectedCommentId == comment.id) {
                            {
                                selectedCommentValue.selectedTextOrNull()?.let(copyComment)
                                selectedCommentId = null
                                selectedCommentValue = TextFieldValue("")
                            }
                        } else {
                            null
                        },
                    )
                }
            }
            if (comments.hasHiddenComments(expanded)) {
                PostActionChip(text = "展开更多评论", onClick = { expanded = true })
            }
            if (comments.canCollapseComments(expanded)) {
                PostActionChip(text = "收起到最新 10 条", onClick = { expanded = false })
            }
            CommentInputBar(
                stateKey = "post-comment-input-$postId",
                placeholder = "写一条帖子评论",
                onSend = { content ->
                    FakeCommentRepository.addPostComment(postId, content)
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun MediaCommentPlaceholderSheet(
    media: PostDetailMediaUiModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val comments = FakeCommentRepository.getMediaComments(media.id)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "媒体评论占位",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "只属于当前媒体，不混入帖子评论区",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PostActionChip(text = "关闭", onClick = onClose)
            }

            if (comments.isEmpty()) {
                Text(
                    text = "当前媒体暂无评论，后续接入真实媒体评论系统。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                comments.take(10).forEach { comment ->
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        Text(
                            text = "${comment.author} · ${formatPostTime(comment.createdAtMillis)}",
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
                        text = "更多媒体评论后续接入分页 / 展开能力",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(radius.lg),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
                ) {
                    Text(
                        text = "写一条媒体评论，占位输入入口",
                        modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PostCircleButton(
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
private fun PostActionChip(
    text: String,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(radius.capsule))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(radius.capsule),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PostMetaCapsule(text: String) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        shape = RoundedCornerShape(radius.capsule),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun PostDetailUiModel.toInPostViewerRoute(initialIndex: Int): PhotoViewerRoute {
    return PhotoViewerRoute(
        mediaItems = mediaItems.map { media ->
            val parts = postViewerDateParts(media.displayTimeMillis)
            PhotoFeedItem(
                mediaId = media.id,
                mediaDisplayTimeMillis = media.displayTimeMillis,
                displayYear = parts.year,
                displayMonth = parts.month,
                displayDay = parts.day,
                commentCount = media.commentCount,
                postIds = listOf(postId),
                palette = media.palette,
                mediaType = media.mediaType,
                aspectRatio = media.aspectRatio,
                width = media.width,
                height = media.height,
                videoDurationMillis = media.videoDurationMillis,
            )
        },
        initialIndex = initialIndex,
        sourceLabel = "帖子内媒体",
        showPostSegments = true,
    )
}

private data class PostViewerDateParts(
    val year: Int,
    val month: Int,
    val day: Int,
)

private fun postViewerDateParts(timeMillis: Long): PostViewerDateParts {
    val calendar = Calendar.getInstance(Locale.CHINA).apply {
        this.timeInMillis = timeMillis
    }
    return PostViewerDateParts(
        year = calendar.get(Calendar.YEAR),
        month = calendar.get(Calendar.MONTH) + 1,
        day = calendar.get(Calendar.DAY_OF_MONTH),
    )
}

private fun formatPostTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}

@Preview(showBackground = true)
@Composable
private fun PostDetailScreenPreview() {
    YingShiTheme {
        PostDetailScreen(
            route = FakeAlbumRepository.toPostDetailRoute(FakeAlbumRepository.getPosts().first()),
            onBack = { },
            onOpenGearEdit = { },
        )
    }
}

