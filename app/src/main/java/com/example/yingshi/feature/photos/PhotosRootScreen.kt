package com.example.yingshi.feature.photos

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yingshi.data.model.CreateAlbumPayload
import com.example.yingshi.data.model.UploadState
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.navigation.PhotosTopDestination
import com.example.yingshi.ui.components.YingShiMistBackground
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.launch

private val PhotoSelectionActionBarPadding = 88.dp

@Composable
fun PhotosRootScreen(
    modifier: Modifier = Modifier,
    selectedTopDestinationName: String = PhotosTopDestination.PHOTOS.name,
    onSelectedTopDestinationChange: (String) -> Unit = { },
    trashSelectedTypeName: String = TrashEntryType.MEDIA_SYSTEM_DELETED.name,
    onTrashSelectedTypeNameChange: (String) -> Unit = { },
    trashShowPendingCleanup: Boolean = false,
    onTrashShowPendingCleanupChange: (Boolean) -> Unit = { },
    onOpenViewer: (PhotoViewerRoute) -> Unit = { },
    onOpenPostDetail: (PostDetailPlaceholderRoute) -> Unit = { },
    onOpenTrashDetail: (TrashDetailRoute) -> Unit = { },
    onTrashRestoreTargetMediaIds: (List<String>) -> Unit = { },
    onOpenSystemMedia: () -> Unit = { },
    onOpenTransferCenter: () -> Unit = { },
    onOpenCreatePost: (CreatePostRoute) -> Unit = { },
    onAddedMediaToPost: (PostDetailPlaceholderRoute) -> Unit = { },
    photoFeedScrollTrigger: Int = 0,
    photoSelectionClearTrigger: Int = 0,
    inlineVideoAutoPlayEnabled: Boolean = true,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val context = LocalContext.current
    val transferTasks = LocalSystemMediaBridgeRepository.uploadTasks
    val hasTransferFailure = transferTasks.any { it.canRetry || it.state == UploadState.FAILURE }
    val runningTransferCount = transferTasks.count {
        it.state == UploadState.WAITING || it.state == UploadState.UPLOADING
    }
    var photoSelectionState by remember {
        mutableStateOf(PhotoFeedSelectionState())
    }
    var showDeleteConfirm by rememberSaveable {
        mutableStateOf(false)
    }
    var trashSelectionMode by rememberSaveable {
        mutableStateOf(false)
    }
    var trashSelectionExitNonce by rememberSaveable {
        mutableIntStateOf(0)
    }
    var showAddToPostDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var addToPostDialogMessage by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var showCreateAlbumDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var createAlbumTitle by rememberSaveable {
        mutableStateOf("")
    }
    var createAlbumSubtitle by rememberSaveable {
        mutableStateOf("")
    }
    var createAlbumErrorMessage by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var isCreatingAlbum by rememberSaveable {
        mutableStateOf(false)
    }
    val coroutineScope = rememberCoroutineScope()
    val albumSummaries = FakeAlbumRepository.getAlbums()
    val albumPosts = FakeAlbumRepository.getPosts()
    val feedItems = FakePhotoFeedRepository.getPhotoFeed()
    val initialPage = rememberSaveable(selectedTopDestinationName) {
        PhotosTopDestination.valueOf(selectedTopDestinationName).ordinal
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { PhotosTopDestination.entries.size },
    )
    val backendSessionKey = realBackendSessionKey("photos-root")
    val selectedSection = PhotosTopDestination.entries[pagerState.currentPage]
    val isPhotoSelectionMode =
        selectedSection == PhotosTopDestination.PHOTOS && photoSelectionState.isInSelectionMode
    val isTrashSelectionMode =
        selectedSection == PhotosTopDestination.TRASH && trashSelectionMode

    LaunchedEffect(pagerState.currentPage) {
        val pageName = PhotosTopDestination.entries[pagerState.currentPage].name
        if (pageName != selectedTopDestinationName) {
            onSelectedTopDestinationChange(pageName)
        }
    }
    LaunchedEffect(backendSessionKey) {
        photoSelectionState = photoSelectionState.clear()
        showDeleteConfirm = false
        showAddToPostDialog = false
        addToPostDialogMessage = null
        showCreateAlbumDialog = false
        createAlbumTitle = ""
        createAlbumSubtitle = ""
        createAlbumErrorMessage = null
        isCreatingAlbum = false
    }
    LaunchedEffect(photoSelectionClearTrigger) {
        if (photoSelectionClearTrigger <= 0) return@LaunchedEffect
        photoSelectionState = photoSelectionState.clear()
        showDeleteConfirm = false
        showAddToPostDialog = false
        addToPostDialogMessage = null
    }

    if (showCreateAlbumDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isCreatingAlbum) {
                    showCreateAlbumDialog = false
                    createAlbumErrorMessage = null
                }
            },
            title = {
                Text(text = "新建大相册")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    OutlinedTextField(
                        value = createAlbumTitle,
                        onValueChange = { createAlbumTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isCreatingAlbum,
                        label = { Text("大相册标题") },
                        placeholder = { Text("例如：2026 夏天") },
                    )
                    OutlinedTextField(
                        value = createAlbumSubtitle,
                        onValueChange = { createAlbumSubtitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCreatingAlbum,
                        minLines = 3,
                        label = { Text("一句说明") },
                        placeholder = { Text("可以写这一组内容的大致主题") },
                    )
                    createAlbumErrorMessage?.let { message ->
                        BackendInlineNotice(
                            text = message,
                            emphasized = true,
                        )
                    }
                }
            },
            confirmButton = {
                SelectionActionChip(
                    text = if (isCreatingAlbum) "创建中…" else "创建",
                    enabled = !isCreatingAlbum && createAlbumTitle.trim().isNotEmpty(),
                    onClick = {
                        if (createAlbumTitle.trim().isEmpty()) {
                            createAlbumErrorMessage = "请先写一个大相册标题。"
                            return@SelectionActionChip
                        }
                        createAlbumErrorMessage = null
                        isCreatingAlbum = true
                        coroutineScope.launch {
                            when (
                                val result = RepositoryProvider.albumRepository.createAlbum(
                                    CreateAlbumPayload(
                                        title = createAlbumTitle.trim(),
                                        subtitle = createAlbumSubtitle.trim(),
                                    ),
                                )
                            ) {
                                is ApiResult.Success -> {
                                    AlbumPageStateStore.pendingSelectedAlbumId = result.data.albumId
                                    if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
                                        notifyRealBackendAlbumsChanged()
                                    }
                                    isCreatingAlbum = false
                                    showCreateAlbumDialog = false
                                    createAlbumTitle = ""
                                    createAlbumSubtitle = ""
                                    createAlbumErrorMessage = null
                                    Toast.makeText(context, "已创建大相册", Toast.LENGTH_SHORT).show()
                                }

                                is ApiResult.Error -> {
                                    isCreatingAlbum = false
                                    createAlbumErrorMessage =
                                        result.toBackendUiMessage("创建大相册失败，请稍后重试。")
                                }

                                ApiResult.Loading -> Unit
                            }
                        }
                    },
                )
            },
            dismissButton = {
                SelectionActionChip(
                    text = "取消",
                    enabled = !isCreatingAlbum,
                    emphasized = false,
                    onClick = {
                        showCreateAlbumDialog = false
                        createAlbumErrorMessage = null
                    },
                )
            },
        )
    }

    if (isPhotoSelectionMode) {
        BackHandler {
            photoSelectionState = photoSelectionState.clear()
        }
    }
    if (isTrashSelectionMode) {
        BackHandler {
            trashSelectionMode = false
            trashSelectionExitNonce += 1
        }
    }
    YingShiMistBackground(
        modifier = modifier
            .fillMaxSize()
            .background(YingShiThemeTokens.colors.appBackground),
        showWaves = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 16.dp, bottom = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            if (showDeleteConfirm) {
                val selectedIds = photoSelectionState.selectedMediaIds
                val selectedCount = photoSelectionState.selectedCount
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = {
                        Text(text = "删除媒体到回收站？")
                    },
                    text = {
                        Text(
                            text = "将从照片流删除已选 $selectedCount 项媒体，并同步影响相关小相册里的引用。媒体会进入映世回收站，可以在回收站中恢复。",
                        )
                    },
                    confirmButton = {
                        SelectionActionChip(
                            text = "删除到回收站",
                            destructive = true,
                            onClick = {
                                showDeleteConfirm = false
                                val feedItems = FakePhotoFeedRepository.getPhotoFeed()
                                val selectedMedia = feedItems.filter { selectedIds.contains(it.mediaId) }
                                if (selectedMedia.isEmpty()) {
                                    Toast.makeText(
                                        context,
                                        "没有找到可删除的媒体，可能已经被移除。",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
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
                                                mediaSource = item.mediaSource,
                                                sourcePostId = item.smallAlbumIds.firstOrNull(),
                                                sourcePostTitle = item.smallAlbumIds.firstOrNull()
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
                                    photoSelectionState = photoSelectionState.without(selectedIds)
                                    Toast.makeText(
                                        context,
                                        "已删除 $selectedCount 项媒体，并写入回收站。",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                    },
                    dismissButton = {
                        SelectionActionChip(
                            text = "取消",
                            emphasized = false,
                            onClick = { showDeleteConfirm = false },
                        )
                    },
                )
            }

        if (showAddToPostDialog) {
            val selectedItems = feedItems.filter { item ->
                photoSelectionState.selectedMediaIds.contains(item.mediaId)
            }
            val selectedItemIds = selectedItems.map { it.mediaId }
            SystemMediaPostDestinationDialog(
                albums = albumSummaries,
                posts = albumPosts,
                errorMessage = addToPostDialogMessage,
                onDismiss = {
                    showAddToPostDialog = false
                    addToPostDialogMessage = null
                },
                onPostSelected = { postId ->
                    if (selectedItems.isEmpty()) {
                        addToPostDialogMessage = "没有找到可加入的媒体，请重新选择。"
                        return@SystemMediaPostDestinationDialog
                    }
                    val existingMediaIds = FakeAlbumRepository.getManagedPostMedia(postId)
                        ?.mapTo(mutableSetOf()) { it.id }
                        .orEmpty()
                    val addedCount = FakeAlbumRepository.appendPhotoFeedItemsToPost(
                        postId = postId,
                        mediaItems = selectedItems,
                    )
                    if (addedCount <= 0) {
                        addToPostDialogMessage = "这些媒体已经在目标小相册里了，可换一个小相册或取消。"
                        return@SystemMediaPostDestinationDialog
                    }
                    showAddToPostDialog = false
                    addToPostDialogMessage = null
                    photoSelectionState = photoSelectionState.without(selectedItemIds.toSet())
                    Toast.makeText(
                        context,
                        "已加入小相册",
                        Toast.LENGTH_SHORT,
                    ).show()
                    val addedMediaIds = selectedItemIds
                        .distinct()
                        .filterNot { existingMediaIds.contains(it) }
                    FakeAlbumRepository.getPost(postId)
                        ?.let(FakeAlbumRepository::toPostDetailRoute)
                        ?.copy(
                            entryNotice = "已加入小相册",
                            highlightMediaIds = addedMediaIds,
                            focusMediaId = addedMediaIds.firstOrNull(),
                        )
                        ?.let(onAddedMediaToPost)
                },
            )
        }

        Box(modifier = Modifier.padding(horizontal = 6.dp)) {
            PhotoTopBar(
                selectedSection = selectedSection,
                hasTransferFailure = hasTransferFailure,
                runningTransferCount = runningTransferCount,
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
                onOpenTransferCenter = onOpenTransferCenter,
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = !isPhotoSelectionMode && !isTrashSelectionMode,
                key = { page -> PhotosTopDestination.entries[page].name },
            ) { page ->
                key(backendSessionKey, PhotosTopDestination.entries[page].name) {
                    when (PhotosTopDestination.entries[page]) {
                        PhotosTopDestination.PHOTOS -> {
                            if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
                                RealPhotoFeedPage(
                                    modifier = Modifier.fillMaxSize(),
                                    selectionState = photoSelectionState,
                                    onSelectionStateChange = { photoSelectionState = it },
                                    onOpenViewer = onOpenViewer,
                                    onOpenCreatePost = onOpenCreatePost,
                                    onAddedMediaToPost = onAddedMediaToPost,
                                    scrollTrigger = photoFeedScrollTrigger,
                                    inlineVideoAutoPlayEnabled = inlineVideoAutoPlayEnabled,
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
                                        scrollTrigger = photoFeedScrollTrigger,
                                        inlineVideoAutoPlayEnabled = inlineVideoAutoPlayEnabled,
                                    )

                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = isPhotoSelectionMode,
                                        enter = fadeIn(),
                                        exit = fadeOut(),
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 0.dp),
                                    ) {
                                        PhotoSelectionActionBarV2(
                                            selectedCount = photoSelectionState.selectedCount,
                                            onCreatePost = {
                                                val selectedIds = photoSelectionState.selectedMediaIds.toList()
                                                if (selectedIds.isEmpty()) {
                                                    return@PhotoSelectionActionBarV2
                                                }
                                                val selectedItems = feedItems
                                                    .filter { item -> selectedIds.contains(item.mediaId) }
                                                    .map(PhotoFeedItem::toCreatePostAppMediaItem)
                                                onOpenCreatePost(
                                                    CreatePostRoute(
                                                        source = "photo-feed-selection",
                                                        initialAppMediaIds = selectedIds,
                                                        initialAppMediaItems = selectedItems,
                                                    ),
                                                )
                                            },
                                            onAddToPost = {
                                                if (photoSelectionState.selectedMediaIds.isEmpty()) {
                                                    return@PhotoSelectionActionBarV2
                                                }
                                                addToPostDialogMessage = null
                                                showAddToPostDialog = true
                                            },
                                            onDelete = {
                                                if (photoSelectionState.selectedMediaIds.isEmpty()) {
                                                    return@PhotoSelectionActionBarV2
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
                                onCreateLargeAlbum = {
                                    createAlbumErrorMessage = null
                                    showCreateAlbumDialog = true
                                },
                                onCreateSmallAlbum = { albumId ->
                                    onOpenCreatePost(
                                        CreatePostRoute(
                                            source = "album-page",
                                            initialAppMediaIds = emptyList(),
                                            initialAppMediaItems = emptyList(),
                                            initialAlbumId = albumId,
                                        ),
                                    )
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
                                onRestoreTargetMediaIds = onTrashRestoreTargetMediaIds,
                                selectionExitNonce = trashSelectionExitNonce,
                                onSelectionModeChange = { trashSelectionMode = it },
                            )
                        }
                    }
                }
            }

        }
    }
    }

}

@Composable
private fun PhotoTopBar(
    selectedSection: PhotosTopDestination,
    hasTransferFailure: Boolean,
    runningTransferCount: Int,
    selectionState: PhotoFeedSelectionState,
    onCancelSelection: () -> Unit,
    onSelected: (Int) -> Unit,
    onOpenSystemMedia: () -> Unit,
    onOpenTransferCenter: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val isSelectionContext =
        selectedSection == PhotosTopDestination.PHOTOS && selectionState.isInSelectionMode

    if (isSelectionContext) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = colors.sectionBackground.copy(alpha = 0.74f),
            border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.68f)),
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectionActionChip(
                    text = "取消",
                    emphasized = false,
                    onClick = onCancelSelection,
                )

                Text(
                    text = if (selectionState.selectedCount > 0) {
                        "已选 ${selectionState.selectedCount} 项"
                    } else {
                        "请选择媒体"
                    },
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                )

                Spacer(modifier = Modifier.width(68.dp))
            }
        }

        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoBrandTabs(
            selectedSection = selectedSection,
            modifier = Modifier.weight(1f),
            onSelected = onSelected,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PhotoCircleToolButton(
                icon = Icons.Rounded.Image,
                contentDescription = "系统媒体",
                onClick = onOpenSystemMedia,
            )
            PhotoCircleToolButton(
                icon = Icons.Rounded.Sync,
                contentDescription = if (hasTransferFailure) {
                    "传输中心，有失败待处理"
                } else if (runningTransferCount > 0) {
                    "传输中心，有进行中任务"
                } else {
                    "传输中心"
                },
                badgeText = if (hasTransferFailure) {
                    "!"
                } else if (runningTransferCount > 99) {
                    "99+"
                } else if (runningTransferCount > 0) {
                    runningTransferCount.toString()
                } else {
                    null
                },
                badgeIsError = hasTransferFailure,
                onClick = onOpenTransferCenter,
            )
        }
    }
}

@Composable
private fun PhotoBrandTabs(
    selectedSection: PhotosTopDestination,
    modifier: Modifier = Modifier,
    onSelected: (Int) -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        PhotosTopDestination.entries.forEachIndexed { index, destination ->
            val selected = destination == selectedSection
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    modifier = Modifier
                        .offset(y = if (selected) (-2).dp else 0.dp)
                        .yingShiClickable(
                            shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                            onClick = { onSelected(index) },
                        ),
                    shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                    color = if (selected) colors.primaryContainer.copy(alpha = 1f) else Color.Transparent,
                    border = if (selected) {
                        BorderStroke(1.6.dp, colors.titleAccent.copy(alpha = 0.24f))
                    } else {
                        null
                    },
                    tonalElevation = if (selected) 2.dp else 0.dp,
                    shadowElevation = if (selected) 15.dp else 0.dp,
                ) {
                    Text(
                        text = destination.label,
                        modifier = Modifier.padding(
                            horizontal = if (selected) 18.dp else 7.dp,
                            vertical = if (selected) 11.dp else 8.dp,
                        ),
                        textAlign = TextAlign.Center,
                        style = if (selected) {
                            MaterialTheme.typography.titleLarge.copy(
                                fontSize = 28.sp,
                                lineHeight = 32.sp,
                                fontWeight = FontWeight.Black,
                                shadow = Shadow(
                                    color = colors.raisedSurface.copy(alpha = 0.92f),
                                    offset = Offset(-1.2f, -1.2f),
                                    blurRadius = 0.5f,
                                ),
                            )
                        } else {
                            MaterialTheme.typography.titleMedium.copy(
                                fontSize = 19.sp,
                                lineHeight = 23.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        color = if (selected) {
                            colors.titleAccent
                        } else {
                            colors.textSecondary.copy(alpha = 0.86f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (selected) {
                    Box(
                        modifier = Modifier
                            .offset(y = (-1).dp)
                            .width(38.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(YingShiThemeTokens.radius.capsule))
                            .background(colors.titleAccent.copy(alpha = 0.20f)),
                    )
                } else {
                    Spacer(modifier = Modifier.height(5.dp))
                }
            }
        }
    }
}

@Composable
private fun PhotoQuickAddEntry(
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoTopToolButton(
            text = text,
            onClick = onClick,
        )
    }
}

@Composable
private fun PhotoTopToolButton(
    text: String,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(radius.capsule)

    Surface(
        modifier = Modifier
            .yingShiClickable(shape = shape, pressedScale = 0.96f, onClick = onClick),
        shape = shape,
        color = colors.raisedSurface.copy(alpha = 0.96f),
        border = BorderStroke(
            width = 1.dp,
            color = colors.dividerSoft.copy(alpha = 0.72f),
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = colors.titleAccent,
        )
    }
}

@Composable
private fun PhotoCircleToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    unreadCount: Int = 0,
    badgeText: String? = if (unreadCount > 0) {
        if (unreadCount > 99) "99+" else unreadCount.toString()
    } else null,
    badgeIsError: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val badgeBackground = if (badgeIsError) MaterialTheme.colorScheme.error else colors.memoryContainer
    val badgeForeground = if (badgeIsError) Color.White else colors.onMemoryContainer
    val badgeBorder = if (badgeIsError) {
        MaterialTheme.colorScheme.error
    } else {
        colors.memoryAccent.copy(alpha = 0.20f)
    }

    Box(
        modifier = Modifier
            .size(54.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .size(40.dp)
                .yingShiClickable(shape = CircleShape, pressedScale = 0.94f, onClick = onClick),
            shape = CircleShape,
            color = colors.sectionBackground.copy(alpha = 0.76f),
            border = BorderStroke(
                width = 1.dp,
                color = colors.dividerSoft.copy(alpha = 0.72f),
            ),
            shadowElevation = 1.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.titleAccent,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (!badgeText.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = 0.dp),
                shape = RoundedCornerShape(999.dp),
                color = badgeBackground,
                border = BorderStroke(1.dp, badgeBorder),
            ) {
                Text(
                    text = badgeText,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = badgeForeground,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun PhotoBellButton(
    unreadCount: Int,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val iconColor = colors.titleAccent

    Surface(
        modifier = Modifier
            .size(34.dp)
            .yingShiClickable(shape = CircleShape, pressedScale = 0.94f, onClick = onClick),
        shape = CircleShape,
        color = colors.raisedSurface.copy(alpha = 0.96f),
        border = BorderStroke(
            width = 1.dp,
            color = colors.dividerSoft.copy(alpha = 0.72f),
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .padding(8.dp),
            ) {
                val stroke = Stroke(width = 2.6f, cap = StrokeCap.Round)
                drawArc(
                    color = iconColor,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    style = stroke,
                )
                drawLine(
                    color = iconColor,
                    start = center.copy(x = size.width * 0.22f, y = size.height * 0.66f),
                    end = center.copy(x = size.width * 0.78f, y = size.height * 0.66f),
                    strokeWidth = 2.6f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = iconColor,
                    start = center.copy(x = size.width * 0.50f, y = size.height * 0.10f),
                    end = center.copy(x = size.width * 0.50f, y = size.height * 0.20f),
                    strokeWidth = 2.6f,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = iconColor,
                    radius = 2.2f,
                    center = center.copy(y = size.height * 0.82f),
                )
            }

            if (unreadCount > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 1.dp, end = 1.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = colors.memoryAccent,
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.raisedSurface,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoSelectionActionBarV2(
    selectedCount: Int,
    onCreatePost: () -> Unit,
    onAddToPost: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    var showActions by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = colors.raisedSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (selectedCount > 0) "已选 $selectedCount 项" else "请选择媒体",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.textPrimary,
            )

            SelectionActionChip(text = "操作", onClick = { showActions = true })
        }
    }

    if (showActions) {
        ModalBottomSheet(
            onDismissRequest = { showActions = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.raisedSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg, vertical = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = if (selectedCount > 0) "已选 $selectedCount 项" else "请选择媒体",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                )
                SelectionActionRow(
                    text = "新建小相册",
                    onClick = {
                        showActions = false
                        onCreatePost()
                    },
                )
                SelectionActionRow(
                    text = "加入已有小相册",
                    onClick = {
                        showActions = false
                        onAddToPost()
                    },
                )
                SelectionActionRow(
                    text = "移入回收站",
                    destructive = true,
                    onClick = {
                        showActions = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun SelectionActionChip(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(radius.capsule)

    Surface(
        modifier = modifier.yingShiClickable(
            enabled = enabled,
            shape = shape,
            pressedScale = 0.96f,
            onClick = onClick,
        ),
        shape = shape,
        color = when {
            !enabled -> colors.sectionBackground.copy(alpha = 0.54f)
            destructive -> colors.memoryContainer.copy(alpha = 0.92f)
            emphasized -> colors.primaryContainer.copy(alpha = 0.76f)
            else -> colors.raisedSurface.copy(alpha = 0.92f)
        },
        border = BorderStroke(
            1.dp,
            when {
                !enabled -> colors.dividerSoft.copy(alpha = 0.46f)
                destructive -> colors.memoryAccent.copy(alpha = 0.28f)
                emphasized -> colors.glassStroke.copy(alpha = 0.72f)
                else -> colors.dividerSoft.copy(alpha = 0.66f)
            },
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = when {
                !enabled -> colors.textSecondary.copy(alpha = 0.70f)
                destructive -> colors.memoryAccent
                emphasized -> colors.titleAccent
                else -> colors.textSecondary
            },
        )
    }
}

@Composable
private fun SelectionActionRow(
    text: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.lg)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .yingShiClickable(shape = shape, pressedScale = 0.96f, onClick = onClick),
        shape = shape,
        color = if (destructive) {
            colors.goldAccent.copy(alpha = 0.18f)
        } else {
            colors.sectionBackground.copy(alpha = 0.62f)
        },
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.md),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (destructive) {
                MaterialTheme.colorScheme.error
            } else {
                colors.textPrimary
            },
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
