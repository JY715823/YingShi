package com.example.yingshi.feature.photos
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.navigation.PhotosTopDestination
import com.example.yingshi.ui.components.YingShiAuroraBackdrop
import com.example.yingshi.ui.components.YingShiBackdropVariant
import com.example.yingshi.ui.components.YingShiNotice
import com.example.yingshi.ui.components.YingShiNoticeHost
import com.example.yingshi.ui.components.YingShiNoticeTone
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import kotlin.math.sin
import kotlin.math.PI
import com.example.yingshi.ui.components.rememberYingShiMotionEnabled
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode

@Immutable
data class PhotosRootSelectionUiState(
    val isActive: Boolean = false,
    val selectedCount: Int = 0,
    val writeEnabled: Boolean = true,
    val isDeleting: Boolean = false,
)

enum class PhotoSelectionShellAction {
    SHARE,
    CREATE,
    ADD,
    DELETE,
}

@Composable
fun PhotosRootScreen(
    modifier: Modifier = Modifier,
    selectedTopDestinationName: String = PhotosTopDestination.PHOTOS.name,
    hasTransferFailure: Boolean = false,
    runningTransferCount: Int = 0,
    onSystemMediaClick: () -> Unit = {},
    onTransferClick: () -> Unit = {},
    selectedSectionInitial: String? = null,
    trashParams: PhotosRootTrashParams = PhotosRootTrashParams(),
    selectionParams: PhotosRootSelectionParams = PhotosRootSelectionParams(),
    onOpenViewer: (PhotoViewerRoute) -> Unit = {},
    onOpenPostDetail: (PostDetailPlaceholderRoute) -> Unit = {},
    onOpenTrashDetail: (TrashDetailRoute) -> Unit = {},
    onOpenCreatePost: (CreatePostRoute) -> Unit = {},
    onAddedMediaToPost: (PostDetailPlaceholderRoute) -> Unit = {},
    onSelectedTopDestinationChange: (String) -> Unit = {},
    onPhotoSelectionShellStateChange: (PhotosRootSelectionUiState) -> Unit = { },
) {
    val vm: PhotosRootViewModel = viewModel()
    val dialogState by vm.dialogState.collectAsState()
    val createAlbumDraft by vm.createAlbumDraft.collectAsState()
    val trashUi by vm.trashUiState.collectAsState()
    val notice by vm.notice.collectAsState()
    val photoShareInFlight by vm.photoShareInFlight.collectAsState()
    val inlineVideoAutoPlayEnabled by vm.inlineVideoAutoPlayEnabled.collectAsState()
    val trashSelectionExitNonce by vm.trashSelectionExitNonce.collectAsState()

    val onTrashSelectedTypeNameChange: (String) -> Unit = { }
    val trashShowPendingCleanup: Boolean = false
    val onTrashShowPendingCleanupChange: (Boolean) -> Unit = { }
    val trashSelectionMode: Boolean = false
    val onTrashSelectionModeChange: (Boolean) -> Unit = { }
    val trashSelectedEntryIds: List<String> = emptyList()
    val onTrashSelectedEntryIdsChange: (List<String>) -> Unit = { }
    val onOpenSystemMedia: () -> Unit = onSystemMediaClick
    val onOpenTransferCenter: () -> Unit = onTransferClick
    val onTrashRestoreTargetMediaIds: (List<String>) -> Unit = { }
    val photoFeedScrollTrigger: Int = selectionParams.photoFeedScrollTrigger.toInt()
    val photoSelectionClearTrigger: Int = selectionParams.photoSelectionClearTrigger.toInt()
    val photoSelectionAction: PhotoSelectionShellAction? = null
    val photoSelectionActionNonce: Int = 0

    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val context = LocalContext.current
    var photoSelectionState by remember {
        mutableStateOf(PhotoFeedSelectionState())
    }
    val coroutineScope = rememberCoroutineScope()
    val albumSummaries = remember(false) {
        if (false) FakeAlbumRepository.getAlbums() else emptyList()
    }
    val albumPosts = remember(false) {
        if (false) FakeAlbumRepository.getPosts() else emptyList()
    }
    val feedItems = remember(false) {
        if (false) FakePhotoFeedRepository.getPhotoFeed() else emptyList()
    }
    val initialPage = remember(selectedTopDestinationName) {
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
        selectedSection == PhotosTopDestination.TRASH && trashUi.selectionMode
    var realPhotoSelectionUiState by remember {
        mutableStateOf(PhotosRootSelectionUiState())
    }
    val photoSelectionShellState = when {
        !isPhotoSelectionMode -> PhotosRootSelectionUiState()
        else -> {
            realPhotoSelectionUiState.copy(
                isActive = true,
                selectedCount = photoSelectionState.selectedCount,
            )
        }
    }

    fun showNotice(
        message: String,
        tone: YingShiNoticeTone = YingShiNoticeTone.INFO,
    ) {
        vm.showNotice(message, tone)
    }

    fun shareSelectedFakeMedia() {
        val selectedItems = feedItems.filter { item ->
            photoSelectionState.selectedMediaIds.contains(item.mediaId)
        }
        if (selectedItems.isEmpty()) {
            showNotice("没有找到可分享的媒体。", YingShiNoticeTone.WARNING)
            return
        }
        if (photoShareInFlight) {
            showNotice("正在准备分享文件…")
            return
        }
        coroutineScope.launch {
            vm.setShareInFlight(true)
            showNotice("正在准备分享文件…")
            try {
                when (
                    val result = MediaShareManager.shareMedia(
                        context = context,
                        items = selectedItems.map(PhotoFeedItem::toShareableMediaItem),
                        packageBaseName = "映世-照片流-${selectedItems.size}项",
                    )
                ) {
                    is MediaShareLaunchResult.Success -> {
                        showNotice(
                            result.toNoticeMessage(),
                            YingShiNoticeTone.SUCCESS,
                        )
                    }

                    is MediaShareLaunchResult.Error -> {
                        showNotice(result.message, YingShiNoticeTone.WARNING)
                    }
                }
            } finally {
                vm.setShareInFlight(false)
            }
        }
    }

    fun createPostFromFakeSelection() {
        val selectedIds = photoSelectionState.selectedMediaIds.toList()
        if (selectedIds.isEmpty()) return
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
    }

    fun addFakeSelectionToPost() {
        if (photoSelectionState.selectedMediaIds.isEmpty()) return
        vm.updateDialogState(dialogState.copy(addToPostDialogMessage = null, showAddToPostDialog = true))
    }

    fun requestFakeSelectionDelete() {
        if (photoSelectionState.selectedMediaIds.isEmpty()) return
        vm.updateDialogState(dialogState.copy(showDeleteConfirm = true))
    }

    LaunchedEffect(pagerState.currentPage) {
        val pageName = PhotosTopDestination.entries[pagerState.currentPage].name
        if (pageName != selectedTopDestinationName) {
            onSelectedTopDestinationChange(pageName)
        }
    }
    LaunchedEffect(backendSessionKey) {
        photoSelectionState = photoSelectionState.clear()
        vm.resetAll()
    }
    LaunchedEffect(trashParams.selectedTypeName) {
        vm.initTrashTypeName(trashParams.selectedTypeName)
    }
    LaunchedEffect(photoSelectionClearTrigger) {
        if (photoSelectionClearTrigger <= 0) return@LaunchedEffect
        photoSelectionState = photoSelectionState.clear()
        vm.updateDialogState(dialogState.copy(
            showDeleteConfirm = false,
            showAddToPostDialog = false,
            addToPostDialogMessage = null,
        ))
    }
    LaunchedEffect(isPhotoSelectionMode) {
        if (!isPhotoSelectionMode) {
            realPhotoSelectionUiState = PhotosRootSelectionUiState()
        }
    }
    LaunchedEffect(photoSelectionShellState) {
        onPhotoSelectionShellStateChange(photoSelectionShellState)
    }
    LaunchedEffect(photoSelectionActionNonce, selectedSection, isPhotoSelectionMode) {
        if (
            photoSelectionActionNonce <= 0 ||
            !isPhotoSelectionMode
        ) {
            return@LaunchedEffect
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            onPhotoSelectionShellStateChange(PhotosRootSelectionUiState())
        }
    }

    if (dialogState.showCreateAlbumDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!createAlbumDraft.isCreating) {
                    vm.updateDialogState(dialogState.copy(showCreateAlbumDialog = false))
                    vm.updateCreateAlbumDraft(createAlbumDraft.copy(errorMessage = null))
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = colors.raisedSurface.copy(alpha = 0.92f),
            tonalElevation = 4.dp,
            titleContentColor = colors.titleAccent,
            textContentColor = colors.textSecondary,
            title = {
                Text(
                    text = "新建大相册",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    OutlinedTextField(
                        value = createAlbumDraft.title,
                        onValueChange = { vm.updateCreateAlbumDraft(createAlbumDraft.copy(title = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !createAlbumDraft.isCreating,
                        label = { Text("大相册标题") },
                        placeholder = { Text("例如：2026 夏天") },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.titleAccent.copy(alpha = 0.60f),
                            unfocusedBorderColor = colors.dividerSoft.copy(alpha = 0.50f),
                        ),
                    )
                    OutlinedTextField(
                        value = createAlbumDraft.subtitle,
                        onValueChange = { vm.updateCreateAlbumDraft(createAlbumDraft.copy(subtitle = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !createAlbumDraft.isCreating,
                        minLines = 3,
                        label = { Text("一句说明") },
                        placeholder = { Text("可以写这一组内容的大致主题") },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.titleAccent.copy(alpha = 0.60f),
                            unfocusedBorderColor = colors.dividerSoft.copy(alpha = 0.50f),
                        ),
                    )
                    createAlbumDraft.errorMessage?.let { message ->
                        BackendInlineNotice(
                            text = message,
                            emphasized = true,
                        )
                    }
                }
            },
            confirmButton = {
                SelectionActionChip(
                    text = if (createAlbumDraft.isCreating) "创建中…" else "创建",
                    enabled = !createAlbumDraft.isCreating && createAlbumDraft.title.trim().isNotEmpty(),
                    onClick = { vm.createAlbum() },
                )
            },
            dismissButton = {
                SelectionActionChip(
                    text = "取消",
                    enabled = !createAlbumDraft.isCreating,
                    emphasized = false,
                    onClick = {
                        vm.updateDialogState(dialogState.copy(showCreateAlbumDialog = false))
                        vm.updateCreateAlbumDraft(createAlbumDraft.copy(errorMessage = null))
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
            vm.exitTrashSelection()
        }
    }
    YingShiAuroraBackdrop(
        modifier = modifier.fillMaxSize(),
        variant = YingShiBackdropVariant.PHOTOS,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 10.dp, bottom = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            if (dialogState.showDeleteConfirm) {
                val selectedIds = photoSelectionState.selectedMediaIds
                val selectedCount = photoSelectionState.selectedCount
                AlertDialog(
                    onDismissRequest = { vm.updateDialogState(dialogState.copy(showDeleteConfirm = false)) },
                    shape = RoundedCornerShape(24.dp),
                    containerColor = colors.raisedSurface.copy(alpha = 0.92f),
                    tonalElevation = 4.dp,
                    titleContentColor = colors.titleAccent,
                    textContentColor = colors.textSecondary,
                    title = {
                        Text(
                            text = "删除媒体到回收站？",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                    },
                    confirmButton = {
                        SelectionActionChip(
                            text = "删除到回收站",
                            destructive = true,
                            onClick = {
                                vm.updateDialogState(dialogState.copy(showDeleteConfirm = false))
                                if (false) {
                                    val feedItems = FakePhotoFeedRepository.getPhotoFeed()
                                    val selectedMedia = feedItems.filter { selectedIds.contains(it.mediaId) }
                                    if (selectedMedia.isEmpty()) {
                                        showNotice("没有找到可删除的媒体，可能已经被移除。", YingShiNoticeTone.WARNING)
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
                                        showNotice("已删除 $selectedCount 项媒体，并写入回收站。", YingShiNoticeTone.SUCCESS)
                                    }
                                }
                            },
                        )
                    },
                    dismissButton = {
                        SelectionActionChip(
                            text = "取消",
                            emphasized = false,
                            onClick = { vm.updateDialogState(dialogState.copy(showDeleteConfirm = false)) },
                        )
                    },
                )
            }

        if (dialogState.showAddToPostDialog) {
            val selectedItems = feedItems.filter { item ->
                photoSelectionState.selectedMediaIds.contains(item.mediaId)
            }
            val selectedItemIds = selectedItems.map { it.mediaId }
            SystemMediaPostDestinationDialog(
                albums = albumSummaries,
                posts = albumPosts,
                errorMessage = dialogState.addToPostDialogMessage,
                onDismiss = {
                    vm.updateDialogState(dialogState.copy(showAddToPostDialog = false, addToPostDialogMessage = null))
                },
                onPostSelected = { postId ->
                    if (selectedItems.isEmpty()) {
                        vm.updateDialogState(dialogState.copy(addToPostDialogMessage = "没有找到可加入的媒体，请重新选择。"))
                        return@SystemMediaPostDestinationDialog
                    }
                    if (false) {
                        val existingMediaIds = FakeAlbumRepository.getManagedPostMedia(postId)
                            ?.mapTo(mutableSetOf()) { it.id }
                            .orEmpty()
                        val addedCount = FakeAlbumRepository.appendPhotoFeedItemsToPost(
                            postId = postId,
                            mediaItems = selectedItems,
                        )
                        if (addedCount <= 0) {
                            vm.updateDialogState(dialogState.copy(addToPostDialogMessage = "这些媒体已经在目标小相册里了，可换一个小相册或取消。"))
                            return@SystemMediaPostDestinationDialog
                        }
                        vm.updateDialogState(dialogState.copy(showAddToPostDialog = false, addToPostDialogMessage = null))
                        photoSelectionState = photoSelectionState.without(selectedItemIds.toSet())
                        showNotice("已加入小相册", YingShiNoticeTone.SUCCESS)
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
                    }
                },
            )
        }

        Box(modifier = Modifier.padding(horizontal = 10.dp)) {
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
                            RealPhotoFeedPage(
                                modifier = Modifier.fillMaxSize(),
                                selectionState = photoSelectionState,
                                onSelectionStateChange = { photoSelectionState = it },
                                onSelectionShellStateChange = { realPhotoSelectionUiState = it },
                                selectionAction = photoSelectionAction,
                                selectionActionNonce = photoSelectionActionNonce,
                                onOpenViewer = onOpenViewer,
                                onOpenCreatePost = onOpenCreatePost,
                                onAddedMediaToPost = onAddedMediaToPost,
                                scrollTrigger = photoFeedScrollTrigger,
                                inlineVideoAutoPlayEnabled = inlineVideoAutoPlayEnabled,
                                isActive = selectedSection == PhotosTopDestination.PHOTOS,
                            )
                        }

                        PhotosTopDestination.ALBUMS -> {
                            AlbumPageScreen(
                                albums = albumSummaries,
                                posts = albumPosts,
                                onOpenPost = onOpenPostDetail,
                                onCreateLargeAlbum = {
                                    vm.updateCreateAlbumDraft(createAlbumDraft.copy(errorMessage = null))
                                    vm.updateDialogState(dialogState.copy(showCreateAlbumDialog = true))
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
                                selectedTypeName = trashUi.selectedTypeName,
                                onSelectedTypeNameChange = { name ->
                                    // 用 transform 原子更新，避免 stale closure 覆盖
                                    vm.updateTrashUiState { it.copy(selectedTypeName = name, showPendingCleanup = false) }
                                },
                                showPendingCleanup = trashUi.showPendingCleanup,
                                onShowPendingCleanupChange = { pending ->
                                    vm.updateTrashUiState { it.copy(showPendingCleanup = pending) }
                                },
                                selectionMode = trashUi.selectionMode,
                                selectedEntryIds = trashUi.selectedEntryIds.toSet(),
                                onSelectionStateChange = { mode, ids ->
                                    vm.updateTrashUiState { it.copy(selectionMode = mode, selectedEntryIds = ids.toList()) }
                                },
                                onOpenTrashDetail = onOpenTrashDetail,
                                onRestoreTargetMediaIds = onTrashRestoreTargetMediaIds,
                                selectionExitNonce = trashSelectionExitNonce,
                                onSelectionModeChange = { vm.updateTrashUiState(trashUi.copy(selectionMode = it)) },
                            )
                        }
                    }
                }
            }
            YingShiNoticeHost(
                notice = notice,
                onExpired = { nonce ->
                    vm.clearNotice(nonce)
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = spacing.sm),
            )
        }
    }
}
}

@Composable
private fun PhotoTopGlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    variant: YingShiBackdropVariant = YingShiBackdropVariant.PHOTOS,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val gradientTopColor = when (variant) {
        YingShiBackdropVariant.PHOTOS -> colors.titleAccent.copy(alpha = 0.08f)
        else -> Color.White.copy(alpha = 0.38f)
    }
    Surface(
        modifier = modifier,
        shape = shape,
        color = colors.glassSurfaceBase,
        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.70f)),
        shadowElevation = 3.dp,
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(
                    colors = listOf(
                        gradientTopColor,
                        colors.glowWash.copy(alpha = 0.24f),
                        Color.Transparent,
                    ),
                ),
            ),
        ) {
            content()
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
        PhotoTopGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "取消",
                    modifier = Modifier
                        .width(58.dp)
                        .yingShiClickable(
                            shape = RoundedCornerShape(14.dp),
                            pressedScale = 0.95f,
                            onClick = onCancelSelection,
                        )
                        .padding(vertical = 6.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Medium,
                        shadow = Shadow(
                            color = Color.White.copy(alpha = 0.44f),
                            offset = Offset(0f, -1f),
                            blurRadius = 8f,
                        ),
                    ),
                    color = colors.titleAccent.copy(alpha = 0.88f),
                )

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = colors.primaryContainer.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.40f)),
                    ) {
                        Text(
                            text = if (selectionState.selectedCount > 0) {
                                "已选 ${selectionState.selectedCount} 项"
                            } else {
                                "请选择媒体"
                            },
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                shadow = Shadow(
                                    color = Color.White.copy(alpha = 0.50f),
                                    offset = Offset(0f, -1f),
                                    blurRadius = 10f,
                                ),
                            ),
                            color = colors.titleAccent,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(58.dp))
            }
        }

        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoBrandTabs(
            selectedSection = selectedSection,
            modifier = Modifier.weight(1f),
            onSelected = onSelected,
        )

        PhotoTopGlassSurface(shape = RoundedCornerShape(22.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PhotoPillToolButton(
                    icon = Icons.Rounded.Image,
                    text = "系统",
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
        horizontalArrangement = Arrangement.spacedBy(1.dp, Alignment.Start),
        verticalAlignment = Alignment.Bottom,
    ) {
        PhotosTopDestination.entries.forEachIndexed { index, destination ->
            val selected = destination == selectedSection
            val slotWidth = when (destination) {
                PhotosTopDestination.TRASH -> if (selected) 92.dp else 64.dp
                else -> if (selected) 78.dp else 56.dp
            }
            Box(
                modifier = Modifier
                    .width(slotWidth)
                    .height(if (selected) 58.dp else 48.dp)
                    .yingShiClickable(
                        shape = RoundedCornerShape(24.dp),
                        pressedScale = 0.97f,
                        onClick = { onSelected(index) },
                    ),
                contentAlignment = Alignment.BottomStart,
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .offset(y = (-1).dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        PhotoSelectedTitleAura(modifier = Modifier.fillMaxSize())
                        Box(
                            modifier = Modifier.padding(start = 1.dp, end = 3.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = destination.label,
                                modifier = Modifier
                                    .offset(y = 3.dp)
                                    .graphicsLayer(
                                        scaleX = 1.14f,
                                        scaleY = 1.14f,
                                        transformOrigin = TransformOrigin(0.18f, 1f),
                                    ),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 24.sp,
                                    lineHeight = 26.sp,
                                    fontWeight = FontWeight.Black,
                                    shadow = Shadow(
                                        color = Color(0xFF85DFFF).copy(alpha = 0.48f),
                                        offset = Offset(0f, 3.8f),
                                        blurRadius = 20f,
                                    ),
                                ),
                                color = colors.titleAccent.copy(alpha = 0.18f),
                            )
                            Text(
                                text = destination.label,
                                modifier = Modifier
                                    .offset(x = (-1).dp, y = (-1).dp)
                                    .graphicsLayer(
                                        scaleX = 1.14f,
                                        scaleY = 1.14f,
                                        transformOrigin = TransformOrigin(0.18f, 1f),
                                    ),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 24.sp,
                                    lineHeight = 26.sp,
                                    fontWeight = FontWeight.Black,
                                    shadow = Shadow(
                                        color = Color(0xFFFFE8C6).copy(alpha = 0.82f),
                                        offset = Offset(-1.6f, -1.6f),
                                        blurRadius = 22f,
                                    ),
                                ),
                                color = Color(0xFFFFF2DE).copy(alpha = 0.46f),
                            )
                            Text(
                                text = destination.label,
                                modifier = Modifier
                                    .offset(x = 1.dp, y = (-1).dp)
                                    .graphicsLayer(
                                        scaleX = 1.14f,
                                        scaleY = 1.14f,
                                        transformOrigin = TransformOrigin(0.18f, 1f),
                                    ),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 24.sp,
                                    lineHeight = 26.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    shadow = Shadow(
                                        color = Color(0xFFB4F5FF).copy(alpha = 0.84f),
                                        offset = Offset(1.4f, -1.8f),
                                        blurRadius = 24f,
                                    ),
                                ),
                                color = Color(0xFFD7FBFF).copy(alpha = 0.54f),
                            )
                            Text(
                                text = destination.label,
                                modifier = Modifier.graphicsLayer(
                                    scaleX = 1.14f,
                                    scaleY = 1.14f,
                                    transformOrigin = TransformOrigin(0.18f, 1f),
                                ),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 24.sp,
                                    lineHeight = 26.sp,
                                    fontWeight = FontWeight.Black,
                                    shadow = Shadow(
                                        color = Color.White.copy(alpha = 0.90f),
                                        offset = Offset(0f, -1.6f),
                                        blurRadius = 24f,
                                    ),
                                ),
                                color = colors.titleAccent,
                            )
                        }
                    }
                } else {
                    Text(
                        text = destination.label,
                        modifier = Modifier
                            .padding(start = 1.dp, end = 1.dp, top = 8.dp, bottom = 8.dp),
                        textAlign = TextAlign.Start,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 19.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Bold,
                            shadow = Shadow(
                                color = Color.White.copy(alpha = 0.42f),
                                offset = Offset(0f, -1.1f),
                                blurRadius = 9f,
                            ),
                        ),
                        color = colors.titleAccent.copy(alpha = 0.74f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoSelectedTitleAura(
    modifier: Modifier = Modifier,
) {
        val themeColors = YingShiThemeTokens.colors
    val motionEnabled = rememberYingShiMotionEnabled()
    val infiniteTransition = rememberInfiniteTransition(label = "titleAuraBreath")
    val breathPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(durationMillis = 4000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "titleAuraBreath",
    )
    val breathMultiplier = if (motionEnabled) {
        (0.85f + 0.15f * (sin(breathPhase) + 1f) / 2f).coerceIn(0.85f, 1f)
    } else { 1f }

Canvas(modifier = modifier) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.78f),
                    Color(0xCBE7F8FF).copy(alpha = 0.60f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.50f, size.height * 0.78f),
                radius = size.minDimension * 0.78f,
            ),
            radius = size.minDimension * 0.78f,
            center = Offset(size.width * 0.50f, size.height * 0.78f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    themeColors.titleAuraWarmGlow,
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.68f, size.height * 0.26f),
                radius = size.minDimension * 0.48f,
            ),
            radius = size.minDimension * 0.48f,
            center = Offset(size.width * 0.68f, size.height * 0.26f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    themeColors.titleAuraCoolGlow,
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.22f, size.height * 0.34f),
                radius = size.minDimension * 0.40f,
            ),
            radius = size.minDimension * 0.40f,
            center = Offset(size.width * 0.22f, size.height * 0.34f),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.62f),
            radius = size.minDimension * 0.038f,
            center = Offset(size.width * 0.18f, size.height * 0.20f),
        )
        drawCircle(
            color = themeColors.titleAuraWarmSparkle.copy(alpha = 0.72f),
            radius = size.minDimension * 0.028f,
            center = Offset(size.width * 0.80f, size.height * 0.18f),
        )
        drawCircle(
            color = themeColors.titleAuraCoolSparkle.copy(alpha = 0.60f),
            radius = size.minDimension * 0.024f,
            center = Offset(size.width * 0.74f, size.height * 0.62f),
        )
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.30f),
                    themeColors.titleAuraBottomCool.copy(alpha = 0.32f),
                    themeColors.titleAuraBottomWarm.copy(alpha = 0.22f),
                    Color.Transparent,
                ),
            ),
            topLeft = Offset(size.width * 0.06f, size.height * 0.74f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.80f, size.height * 0.10f),
            cornerRadius = CornerRadius(size.height, size.height),
        )
    }
}

@Composable
fun PhotoSelectionBottomBar(
    selectedCount: Int,
    writeEnabled: Boolean,
    deleteInFlight: Boolean,
    modifier: Modifier = Modifier,
    onAction: (PhotoSelectionShellAction) -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val selectionReady = selectedCount > 0 && !deleteInFlight
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        colors.appBackground.copy(alpha = 0.90f),
                        colors.appBackground,
                    ),
                ),
            )
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp),
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(74.dp),
                shape = RoundedCornerShape(0.dp),
                color = colors.raisedSurface.copy(alpha = 0.95f),
                tonalElevation = 0.dp,
                shadowElevation = 2.dp,
                border = BorderStroke(0.dp, Color.Transparent),
            ) {}
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                colors.glowWash.copy(alpha = 0.38f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 2.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.dividerSoft.copy(alpha = 0.72f)),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 9.dp)
                    .height(66.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PhotoSelectionBottomBarAction(
                    icon = Icons.Rounded.Upload,
                    text = "分享",
                    enabled = selectionReady,
                    modifier = Modifier.weight(1f),
                    onClick = { onAction(PhotoSelectionShellAction.SHARE) },
                )
                PhotoSelectionBottomBarAction(
                    icon = Icons.Rounded.Add,
                    text = "新建",
                    enabled = selectionReady && writeEnabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onAction(PhotoSelectionShellAction.CREATE) },
                )
                PhotoSelectionBottomBarAction(
                    icon = Icons.Rounded.Folder,
                    text = "加入",
                    enabled = selectionReady && writeEnabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onAction(PhotoSelectionShellAction.ADD) },
                )
                PhotoSelectionBottomBarAction(
                    icon = Icons.Rounded.Delete,
                    text = if (deleteInFlight) "删除中" else "删除",
                    enabled = selectionReady && writeEnabled,
                    destructive = true,
                    modifier = Modifier.weight(1f),
                    onClick = { onAction(PhotoSelectionShellAction.DELETE) },
                )
            }
        }
    }
}

@Composable
private fun PhotoSelectionBottomBarAction(
    icon: ImageVector,
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier
            .height(56.dp)
            .yingShiClickable(
                enabled = enabled,
                shape = shape,
                pressedScale = 0.96f,
                onClick = onClick,
            ),
        shape = shape,
        color = when {
            !enabled -> colors.sectionBackground.copy(alpha = 0.54f)
            destructive -> colors.destructiveContainer.copy(alpha = 0.90f)
            else -> colors.primaryContainer.copy(alpha = 0.78f)
        },
        border = BorderStroke(
            1.dp,
            when {
                !enabled -> colors.dividerSoft.copy(alpha = 0.42f)
                destructive -> colors.destructive.copy(alpha = 0.22f)
                else -> colors.glassStroke.copy(alpha = 0.72f)
            },
        ),
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = when {
                        !enabled -> colors.textSecondary.copy(alpha = 0.58f)
                        destructive -> colors.destructive
                        else -> colors.titleAccent
                    },
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = when {
                        !enabled -> colors.textSecondary.copy(alpha = 0.58f)
                        destructive -> colors.destructive
                        else -> colors.titleAccent
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PhotoPillToolButton(
    icon: ImageVector,
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val motion = YingShiThemeTokens.motion
    val motionEnabled = rememberYingShiMotionEnabled()
    val shape = RoundedCornerShape(18.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = if (motionEnabled) motion.tapMillis else 0),
        label = "pillScale",
    )
    val pressedAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.72f else 1f,
        animationSpec = tween(durationMillis = if (motionEnabled) motion.tapMillis else 0),
        label = "pillAlpha",
    )

    Surface(
        modifier = Modifier
            .semantics { this.contentDescription = contentDescription }
            .graphicsLayer {
                scaleX = pressedScale
                scaleY = pressedScale
                alpha = pressedAlpha
            }
            .clip(shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = shape,
        color = Color.White.copy(alpha = 0.42f),
        border = BorderStroke(
            width = 1.dp,
            color = colors.dividerSoft.copy(alpha = 0.72f),
        ),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.28f),
                            colors.glowWash.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                    ),
                )
                .padding(horizontal = spacing.sm, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.titleAccent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
        }
    }
}

@Composable
private fun PhotoCircleToolButton(
    icon: ImageVector,
    contentDescription: String,
    badgeText: String? = null,
    badgeIsError: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val motion = YingShiThemeTokens.motion
    val motionEnabled = rememberYingShiMotionEnabled()
    val isActive = badgeIsError || !badgeText.isNullOrBlank()
    val badgeBackground = if (badgeIsError) {
        colors.destructiveContainer
    } else {
        colors.memoryContainer
    }
    val badgeForeground = if (badgeIsError) {
        colors.onDestructiveContainer
    } else {
        colors.onMemoryContainer
    }
    val badgeBorder = if (badgeIsError) {
        colors.destructive.copy(alpha = 0.28f)
    } else {
        colors.memoryAccent.copy(alpha = 0.20f)
    }
    val containerColor = when {
        badgeIsError -> colors.destructiveContainer.copy(alpha = 0.90f)
        isActive -> colors.primaryContainer.copy(alpha = 0.82f)
        else -> Color.White.copy(alpha = 0.42f)
    }
    val iconTint = if (badgeIsError) {
        colors.destructive
    } else {
        colors.titleAccent
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(durationMillis = if (motionEnabled) motion.tapMillis else 0),
        label = "circleScale",
    )
    val pressedAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.84f else 1f,
        animationSpec = tween(durationMillis = if (motionEnabled) motion.tapMillis else 0),
        label = "circleAlpha",
    )

    Box(
        modifier = Modifier
            .size(50.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .size(40.dp)
                .graphicsLayer {
                    scaleX = pressedScale
                    scaleY = pressedScale
                    alpha = pressedAlpha
                }
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
            shape = CircleShape,
            color = containerColor,
            border = BorderStroke(
                width = 1.dp,
                color = if (badgeIsError) {
                    colors.destructive.copy(alpha = 0.26f)
                } else if (isActive) {
                    colors.glassStroke.copy(alpha = 0.88f)
                } else {
                    colors.dividerSoft.copy(alpha = 0.72f)
                },
            ),
            shadowElevation = if (isActive) 2.dp else 1.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = if (badgeIsError) {
                                    listOf(
                                        Color.White.copy(alpha = 0.18f),
                                        colors.destructiveContainer.copy(alpha = 0.12f),
                                        Color.Transparent,
                                    )
                                } else {
                                    listOf(
                                        Color.White.copy(alpha = if (isActive) 0.28f else 0.16f),
                                        colors.glowWash.copy(alpha = if (isActive) 0.18f else 0.10f),
                                        Color.Transparent,
                                    )
                                },
                                radius = 52f,
                            ),
                        ),
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (!badgeText.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp),
                shape = RoundedCornerShape(999.dp),
                color = badgeBackground,
                border = BorderStroke(1.dp, badgeBorder),
            ) {
                Text(
                    text = badgeText,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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
            destructive -> colors.destructiveContainer.copy(alpha = 0.92f)
            emphasized -> colors.primaryContainer.copy(alpha = 0.78f)
            else -> colors.primaryContainer.copy(alpha = 0.60f)
        },
        border = BorderStroke(
            1.dp,
            when {
                !enabled -> colors.dividerSoft.copy(alpha = 0.46f)
                destructive -> colors.destructive.copy(alpha = 0.28f)
                emphasized -> colors.glassStroke.copy(alpha = 0.88f)
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
                destructive -> colors.onDestructiveContainer
                emphasized -> colors.titleAccent
                else -> colors.textSecondary
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
