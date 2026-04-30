package com.example.yingshi.feature.photos

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.navigation.PhotosTopDestination
import com.example.yingshi.ui.components.TitleTabs
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.launch

private val PhotoSelectionActionBarPadding = 88.dp

private object PhotosRootEntryCallbacks {
    var onOpenNotifications: (() -> Unit)? = null
}

@Composable
fun PhotosRootScreen(
    modifier: Modifier = Modifier,
    selectedTopDestinationName: String = PhotosTopDestination.PHOTOS.name,
    onSelectedTopDestinationChange: (String) -> Unit = { },
    trashSelectedTypeName: String = TrashEntryType.POST_DELETED.name,
    onTrashSelectedTypeNameChange: (String) -> Unit = { },
    trashShowPendingCleanup: Boolean = false,
    onTrashShowPendingCleanupChange: (Boolean) -> Unit = { },
    onOpenViewer: (PhotoViewerRoute) -> Unit = { },
    onOpenPostDetail: (PostDetailPlaceholderRoute) -> Unit = { },
    onOpenTrashDetail: (TrashDetailRoute) -> Unit = { },
    onOpenSystemMedia: () -> Unit = { },
    onOpenNotifications: () -> Unit = { },
) {
    val spacing = YingShiThemeTokens.spacing
    val context = LocalContext.current
    val notificationUnreadCount = FakeNotificationRepository.unreadCount()
    var photoSelectionState by remember {
        mutableStateOf(PhotoFeedSelectionState())
    }
    var showDeleteConfirm by rememberSaveable {
        mutableStateOf(false)
    }
    val coroutineScope = rememberCoroutineScope()
    val albumSummaries = remember { FakeAlbumRepository.getAlbums() }
    val albumPosts = remember { FakeAlbumRepository.getPosts() }
    val initialPage = rememberSaveable(selectedTopDestinationName) {
        PhotosTopDestination.valueOf(selectedTopDestinationName).ordinal
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { PhotosTopDestination.entries.size },
    )
    val selectedSection = PhotosTopDestination.entries[pagerState.currentPage]
    val isPhotoSelectionMode =
        selectedSection == PhotosTopDestination.PHOTOS && photoSelectionState.isInSelectionMode

    LaunchedEffect(pagerState.currentPage) {
        val pageName = PhotosTopDestination.entries[pagerState.currentPage].name
        if (pageName != selectedTopDestinationName) {
            onSelectedTopDestinationChange(pageName)
        }
    }

    if (isPhotoSelectionMode) {
        BackHandler {
            photoSelectionState = photoSelectionState.clear()
        }
    }
    SideEffect {
        PhotosRootEntryCallbacks.onOpenNotifications = onOpenNotifications
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = spacing.lg)
            .padding(top = 0.dp, bottom = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = {
                    Text(text = "确认删除该媒体？")
                },
                text = {
                    Text(text = "删除后会进入回收站，可在回收站中恢复。")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = false
                            val selectedIds = photoSelectionState.selectedMediaIds
                            val feedItems = FakePhotoFeedRepository.getPhotoFeed()
                            val selectedMedia = feedItems.filter { selectedIds.contains(it.mediaId) }
                            if (selectedMedia.isEmpty()) {
                                photoSelectionState = photoSelectionState.clear()
                                return@TextButton
                            }

                            val outcome = FakeAlbumRepository.previewGlobalMediaDelete(selectedIds)
                            val deletedPostSnapshots = outcome.deletedPostIds.mapNotNull(
                                FakeAlbumRepository::snapshotPost,
                            )
                            val relationSnapshotsByMediaId =
                                FakeAlbumRepository.snapshotMediaRelations(selectedIds)

                            FakeTrashRepository.recordSystemDeletedMedia(
                                mediaSnapshots = selectedMedia.map { item ->
                                    TrashMediaSnapshot(
                                        mediaId = item.mediaId,
                                        displayTimeMillis = item.mediaDisplayTimeMillis,
                                        palette = item.palette,
                                        mediaType = item.mediaType,
                                        aspectRatio = item.aspectRatio,
                                        width = item.width,
                                        height = item.height,
                                        videoDurationMillis = item.videoDurationMillis,
                                        sourcePostId = item.postIds.firstOrNull(),
                                        sourcePostTitle = item.postIds.firstOrNull()
                                            ?.let(FakeAlbumRepository::getPost)
                                            ?.title,
                                    )
                                },
                                relationSnapshotsByMediaId = relationSnapshotsByMediaId,
                            )
                            deletedPostSnapshots.forEach { snapshot ->
                                FakeTrashRepository.recordDeletedPost(snapshot = snapshot)
                            }
                            val appliedOutcome = FakeAlbumRepository.applyGlobalMediaDelete(selectedIds)
                            FakeAlbumRepository.deletePostsLocally(appliedOutcome.deletedPostIds)
                            photoSelectionState = photoSelectionState.clear()
                            Toast.makeText(
                                context,
                                "已执行本地系统删，并写入回收站",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    ) {
                        Text(text = "删除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(text = "取消")
                    }
                },
            )
        }

        PhotoTopBar(
            selectedSection = selectedSection,
            notificationUnreadCount = notificationUnreadCount,
            selectionState = if (selectedSection == PhotosTopDestination.PHOTOS) {
                photoSelectionState
            } else {
                PhotoFeedSelectionState()
            },
            onCancelSelection = { photoSelectionState = photoSelectionState.clear() },
            onSelected = { index ->
                if (pagerState.currentPage == index) return@PhotoTopBar
                coroutineScope.launch {
                    pagerState.animateScrollToPage(index)
                }
            },
            onOpenSystemMedia = onOpenSystemMedia,
            onOpenNotifications = {
                Toast.makeText(context, "通知中心将在后续阶段接入", Toast.LENGTH_SHORT).show()
            },
        )

        Box(modifier = Modifier.weight(1f)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { page -> PhotosTopDestination.entries[page].name },
            ) { page ->
                when (PhotosTopDestination.entries[page]) {
                    PhotosTopDestination.PHOTOS -> {
                        if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
                            RealPhotoFeedPage(
                                modifier = Modifier.fillMaxSize(),
                                selectionState = photoSelectionState,
                                onSelectionStateChange = { photoSelectionState = it },
                                onOpenViewer = onOpenViewer,
                            )
                        } else {
                            val feedItems = FakePhotoFeedRepository.getPhotoFeed()
                            Box(modifier = Modifier.fillMaxSize()) {
                                PhotoFeedScreen(
                                    feedItems = feedItems,
                                    modifier = Modifier.fillMaxSize(),
                                    selectionState = photoSelectionState,
                                    bottomOverlayPadding = if (isPhotoSelectionMode) {
                                        PhotoSelectionActionBarPadding
                                    } else {
                                        0.dp
                                    },
                                    onSelectionStateChange = { photoSelectionState = it },
                                    onOpenViewer = onOpenViewer,
                                )

                                androidx.compose.animation.AnimatedVisibility(
                                    visible = isPhotoSelectionMode,
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = spacing.sm),
                                ) {
                                    PhotoSelectionActionBar(
                                        selectedCount = photoSelectionState.selectedCount,
                                        onDelete = {
                                            if (photoSelectionState.selectedMediaIds.isEmpty()) {
                                                photoSelectionState = photoSelectionState.clear()
                                            } else {
                                                showDeleteConfirm = true
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    PhotosTopDestination.ALBUMS -> {
                        AlbumPageScreen(
                            albums = albumSummaries,
                            posts = albumPosts,
                            onOpenPost = onOpenPostDetail,
                            onManageAlbums = {
                                Toast.makeText(context, "相册管理入口占位", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    PhotosTopDestination.TRASH -> {
                        TrashPageScreen(
                            modifier = Modifier.fillMaxSize(),
                            selectedTypeName = trashSelectedTypeName,
                            onSelectedTypeNameChange = onTrashSelectedTypeNameChange,
                            showPendingCleanup = trashShowPendingCleanup,
                            onShowPendingCleanupChange = onTrashShowPendingCleanupChange,
                            onOpenTrashDetail = onOpenTrashDetail,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoTopBar(
    selectedSection: PhotosTopDestination,
    notificationUnreadCount: Int,
    selectionState: PhotoFeedSelectionState,
    onCancelSelection: () -> Unit,
    onSelected: (Int) -> Unit,
    onOpenSystemMedia: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val isSelectionContext =
        selectedSection == PhotosTopDestination.PHOTOS && selectionState.isInSelectionMode

    if (isSelectionContext) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancelSelection) {
                Text(text = "取消")
            }

            Text(
                text = "已选 ${selectionState.selectedCount} 项",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.width(68.dp))
        }

        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TitleTabs(
            tabs = PhotosTopDestination.entries.map { it.label },
            selectedIndex = selectedSection.ordinal,
            modifier = Modifier.weight(1f),
            onSelected = onSelected,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PhotoTopToolButton(
                text = "系统",
                onClick = onOpenSystemMedia,
            )
            PhotoBellButton(
                unreadCount = notificationUnreadCount,
                onClick = {
                    PhotosRootEntryCallbacks.onOpenNotifications?.invoke() ?: onOpenNotifications()
                },
            )
        }
    }
}

@Composable
private fun PhotoTopToolButton(
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
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PhotoBellButton(
    unreadCount: Int,
    onClick: () -> Unit,
) {
    val iconColor = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
        ),
    ) {
        Box {
            Canvas(modifier = Modifier.padding(8.dp)) {
                val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round)
                drawArc(
                    color = iconColor,
                    startAngle = 202f,
                    sweepAngle = 136f,
                    useCenter = false,
                    style = stroke,
                )
                drawLine(
                    color = iconColor,
                    start = center.copy(x = size.width * 0.22f, y = size.height * 0.62f),
                    end = center.copy(x = size.width * 0.78f, y = size.height * 0.62f),
                    strokeWidth = 2.2f,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = iconColor,
                    radius = 1.8f,
                    center = center.copy(y = size.height * 0.78f),
                )
            }

            if (unreadCount > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 1.dp, end = 1.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoSelectionActionBar(
    selectedCount: Int,
    onDelete: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.sm, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "已选 $selectedCount 项",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SelectionActionChip(text = "整理", onClick = {})
            SelectionActionChip(text = "导出", onClick = {})
            SelectionActionChip(text = "删除", onClick = onDelete)
        }
    }
}

@Composable
private fun SelectionActionChip(
    text: String,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(radius.capsule),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PhotosRootScreenPreview() {
    YingShiTheme {
        PhotosRootScreen()
    }
}
