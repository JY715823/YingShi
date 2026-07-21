package com.example.yingshi.feature.photos

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.model.RemoteTrashDetail
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens

private class RealTrashViewerZoomState {
    var scale by mutableStateOf(MinRealTrashViewerScale)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    val isZoomed: Boolean
        get() = scale > RealTrashViewerResetScale

    fun reset() {
        scale = MinRealTrashViewerScale
        offset = Offset.Zero
    }

    fun applyTransform(
        zoomChange: Float,
        panChange: Offset,
        containerSize: IntSize,
        contentSize: IntSize,
    ) {
        if (containerSize.width <= 0 || containerSize.height <= 0) return
        val nextScale = (scale * zoomChange).coerceIn(MinRealTrashViewerScale, MaxRealTrashViewerScale)
        if (nextScale <= RealTrashViewerResetScale) {
            reset()
            return
        }
        scale = nextScale
        offset = clampOffset(
            value = offset + panChange,
            currentScale = nextScale,
            containerSize = containerSize,
            contentSize = contentSize,
        )
    }

    fun panBy(
        panChange: Offset,
        containerSize: IntSize,
        contentSize: IntSize,
    ) {
        if (!isZoomed || containerSize.width <= 0 || containerSize.height <= 0) return
        offset = clampOffset(
            value = offset + panChange,
            currentScale = scale,
            containerSize = containerSize,
            contentSize = contentSize,
        )
    }

    private fun clampOffset(
        value: Offset,
        currentScale: Float,
        containerSize: IntSize,
        contentSize: IntSize,
    ): Offset {
        val scaledWidth = contentSize.width * currentScale
        val scaledHeight = contentSize.height * currentScale
        val maxX = ((scaledWidth - containerSize.width) / 2f).coerceAtLeast(0f)
        val maxY = ((scaledHeight - containerSize.height) / 2f).coerceAtLeast(0f)
        return Offset(
            x = value.x.coerceIn(-maxX, maxX),
            y = value.y.coerceIn(-maxY, maxY),
        )
    }
}

private fun Modifier.realTrashViewerZoomGesture(
    zoomState: RealTrashViewerZoomState,
    contentSize: IntSize,
): Modifier = pointerInput(zoomState, contentSize) {
    awaitEachGesture {
        while (true) {
            val event = awaitPointerEvent()
            val activeChanges = event.changes.filter { it.pressed }
            if (activeChanges.isEmpty()) break

            if (activeChanges.size >= 2) {
                val currentCentroid = activeChanges.realTrashCentroid(usePrevious = false)
                val previousCentroid = activeChanges.realTrashCentroid(usePrevious = true)
                val currentDistance = activeChanges.realTrashAverageDistanceTo(
                    centroid = currentCentroid,
                    usePrevious = false,
                )
                val previousDistance = activeChanges.realTrashAverageDistanceTo(
                    centroid = previousCentroid,
                    usePrevious = true,
                )
                val zoomChange = if (previousDistance > 0f) {
                    currentDistance / previousDistance
                } else {
                    MinRealTrashViewerScale
                }
                zoomState.applyTransform(
                    zoomChange = zoomChange,
                    panChange = currentCentroid - previousCentroid,
                    containerSize = size,
                    contentSize = contentSize,
                )
                activeChanges.forEach { it.consume() }
            } else if (zoomState.isZoomed) {
                zoomState.panBy(
                    panChange = activeChanges.first().positionChange(),
                    containerSize = size,
                    contentSize = contentSize,
                )
                activeChanges.forEach { it.consume() }
            }
        }
    }
}

private fun List<PointerInputChange>.realTrashCentroid(usePrevious: Boolean): Offset {
    val total = fold(Offset.Zero) { sum, change ->
        sum + if (usePrevious) change.previousPosition else change.position
    }
    return total / size.toFloat()
}

private fun List<PointerInputChange>.realTrashAverageDistanceTo(
    centroid: Offset,
    usePrevious: Boolean,
): Float {
    return sumOf { change ->
        val position = if (usePrevious) change.previousPosition else change.position
        (position - centroid).getDistance().toDouble()
    }.toFloat() / size
}

@Composable
internal fun RealTrashMediaViewerDetailPagerContent(
    detail: RemoteTrashDetail,
    directory: CollaboratorDirectorySnapshot,
    entries: List<TrashEntryUiModel>,
    statusMessage: String?,
    errorMessage: String?,
    isMutating: Boolean,
    onBack: () -> Unit,
    onRestoreEntry: (TrashEntryUiModel) -> Unit,
    onRemoveEntry: (TrashEntryUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val accessToken = AuthSessionManager.peekAccessToken()
    val initialEntry = detail.item.toTrashEntryUiModel()
    val viewerEntries = remember(entries, initialEntry.id) {
        entries
            .filter { it.mediaSnapshot != null }
            .ifEmpty { listOf(initialEntry) }
            .distinctBy { it.businessIdentityKey() }
    }
    val initialPage = viewerEntries.indexOfFirst { it.id == initialEntry.id }
        .takeIf { it >= 0 } ?: 0
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { viewerEntries.size },
    )
    val currentEntry = viewerEntries[pagerState.currentPage.coerceIn(0, viewerEntries.lastIndex)]
    val currentMedia = currentEntry.mediaSnapshot
    val actorIdentity = resolveTrashActorIdentity(currentEntry, directory)
    val currentCommentMediaId = currentEntry.commentMediaId()
    val commentBindings = currentCommentMediaId?.let { rememberViewerCommentBindings(it) }
    val target = currentMedia?.let {
        RealOriginalMediaTarget(
            mediaId = it.mediaId,
            mediaType = it.mediaType,
            mediaSource = it.mediaSource,
        )
    }
    val originalLoadState = target?.let(RealOriginalLoadRepository::getState)
        ?: OriginalLoadState.NotLoaded
    var showPermanentDeleteConfirm by remember(currentEntry.id) {
        mutableStateOf(false)
    }
    var showRestoreConfirm by remember(currentEntry.id) {
        mutableStateOf(false)
    }
    var isImmersive by remember { mutableStateOf(false) }
    var showCommentPreview by remember(currentEntry.id) {
        mutableStateOf(false)
    }
    var videoPlaybackState by remember {
        mutableStateOf(ViewerVideoPlaybackState())
    }
    var videoControlsVisible by remember { mutableStateOf(true) }
    var videoControlsActivityNonce by remember { mutableIntStateOf(0) }
    val zoomState = remember { RealTrashViewerZoomState() }

    LaunchedEffect(currentEntry.id) {
        showCommentPreview = false
        videoPlaybackState = ViewerVideoPlaybackState(
            mediaId = currentMedia?.mediaId?.takeIf { currentMedia.mediaType == AppMediaType.VIDEO },
        )
        videoControlsVisible = true
        videoControlsActivityNonce += 1
        zoomState.reset()
    }
    LaunchedEffect(
        currentMedia?.mediaId,
        currentMedia?.mediaType,
        videoControlsVisible,
        videoControlsActivityNonce,
        videoPlaybackState.isPlaying,
        videoPlaybackState.isLoading,
        videoPlaybackState.errorMessage,
        videoPlaybackState.isCompleted,
    ) {
        if (currentMedia?.mediaType != AppMediaType.VIDEO || !videoControlsVisible) return@LaunchedEffect
        if (videoPlaybackState.isLoading || videoPlaybackState.errorMessage != null) return@LaunchedEffect
        kotlinx.coroutines.delay(2800)
        videoControlsVisible = false
    }
    BackHandler(enabled = !isImmersive && showCommentPreview) {
        showCommentPreview = false
    }
    BackHandler(enabled = !isImmersive && zoomState.isZoomed) {
        zoomState.reset()
    }
    BackHandler(enabled = isImmersive) {
        onBack()
    }
    ViewerStatusBarEffect(immersive = isImmersive)

    fun revealVideoControls() {
        videoControlsVisible = true
        videoControlsActivityNonce += 1
    }

    fun toggleImmersive() {
        val nextImmersive = !isImmersive
        applyViewerStatusBarVisibility(view, nextImmersive)
        isImmersive = nextImmersive
        if (nextImmersive) {
            showCommentPreview = false
            videoControlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(YingShiThemeTokens.colors.viewerBackground)
            .viewerSingleTapGesture { position, size ->
                if (currentMedia?.mediaType == AppMediaType.VIDEO) {
                    val topTapZonePx = with(density) { if (isImmersive) 0.dp.toPx() else 68.dp.toPx() }
                    val bottomTapZonePx = with(density) { if (isImmersive) 40.dp.toPx() else 104.dp.toPx() }
                    if (position.y <= topTapZonePx || position.y >= size.height - bottomTapZonePx) {
                        toggleImmersive()
                    }
                } else {
                    toggleImmersive()
                }
            },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = viewerEntries.size > 1 && !zoomState.isZoomed,
            key = { page -> viewerEntries[page].id },
        ) { page ->
            val pageMedia = viewerEntries[page].mediaSnapshot
            if (pageMedia == null) {
                RealTrashSectionCard(
                    title = "原媒体预览不可用",
                    body = MissingOriginalMediaMessage,
                )
            } else {
                val pageTarget = RealOriginalMediaTarget(
                    mediaId = pageMedia.mediaId,
                    mediaType = pageMedia.mediaType,
                    mediaSource = pageMedia.mediaSource,
                )
                TrashViewerMediaCanvas(
                    media = pageMedia,
                    originalLoadState = RealOriginalLoadRepository.getState(pageTarget),
                    onOriginalLoadStateChange = { state -> RealOriginalLoadRepository.setState(pageTarget, state) },
                    immersive = isImmersive,
                    zoomState = if (page == pagerState.currentPage && pageMedia.mediaType == AppMediaType.IMAGE) {
                        zoomState
                    } else {
                        null
                    },
                    videoPlaybackState = if (page == pagerState.currentPage) videoPlaybackState else null,
                    videoControlsVisible = page == pagerState.currentPage && videoControlsVisible,
                    onVideoAreaClick = {
                        if (videoControlsVisible) {
                            videoControlsVisible = false
                            videoControlsActivityNonce += 1
                        } else {
                            revealVideoControls()
                        }
                    },
                    onTogglePlayback = {
                        revealVideoControls()
                        val durationMillis = videoPlaybackState.durationMillis
                            ?: pageMedia.viewerVideoDurationMillis()
                        val shouldRestart = videoPlaybackState.isCompleted ||
                            (durationMillis > 0L && videoPlaybackState.progressMillis >= durationMillis)
                        videoPlaybackState = if (videoPlaybackState.errorMessage != null) {
                            videoPlaybackState.retryState().copy(mediaId = pageMedia.mediaId)
                        } else if (videoPlaybackState.isPlaying) {
                            videoPlaybackState.copy(isPlaying = false)
                        } else {
                            videoPlaybackState.copy(
                                mediaId = pageMedia.mediaId,
                                isPlaying = true,
                                progressMillis = if (shouldRestart) 0L else videoPlaybackState.progressMillis,
                                seekRequestMillis = if (shouldRestart) 0L else videoPlaybackState.seekRequestMillis,
                                seekRequestNonce = if (shouldRestart) {
                                    videoPlaybackState.seekRequestNonce + 1
                                } else {
                                    videoPlaybackState.seekRequestNonce
                                },
                                errorMessage = null,
                                isCompleted = false,
                            )
                        }
                    },
                    onSeekPlayback = { progressMillis ->
                        revealVideoControls()
                        val durationMillis = videoPlaybackState.durationMillis
                            ?: pageMedia.viewerVideoDurationMillis()
                        val targetMillis = progressMillis.coerceIn(0L, durationMillis.coerceAtLeast(0L))
                        videoPlaybackState = videoPlaybackState.copy(
                            mediaId = pageMedia.mediaId,
                            progressMillis = targetMillis,
                            seekRequestMillis = targetMillis,
                            seekRequestNonce = videoPlaybackState.seekRequestNonce + 1,
                            errorMessage = null,
                            isCompleted = false,
                        )
                    },
                    onVideoPlaybackStateChange = { mediaId, state ->
                        if (mediaId == currentMedia?.mediaId) {
                            videoPlaybackState = state
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (!isImmersive) {
            TrashViewerTopScrim(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(124.dp),
            )
        }

        if (!isImmersive) {
            TrashViewerBottomScrim(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(188.dp),
            )
        }

        if (!isImmersive) {
            RealTrashViewerTopBar(
                actorIdentity = actorIdentity,
                postTitle = currentEntry.takeIf { it.type == TrashEntryType.MEDIA_REMOVED }
                    ?.let(::realTrashGridPostTitle),
                isMutating = isMutating,
                canRestore = detail.canRestore,
                canRemove = detail.canMoveOutOfTrash,
                onBack = onBack,
                onRestore = { showRestoreConfirm = true },
                onRemove = { showPermanentDeleteConfirm = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 10.dp, top = 6.dp),
            )
        }

        if (!isImmersive) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(
                        end = YingShiThemeTokens.spacing.lg,
                        bottom = 0.dp,
                    ),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
            ) {
                statusMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = YingShiThemeTokens.colors.viewerTextSecondary.copy(alpha = 0.92f),
                    )
                }
                errorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = YingShiThemeTokens.colors.destructive.copy(alpha = 0.94f),
                    )
                }
            }
        }

        if (!isImmersive) {
            RealTrashViewerEdgeActions(
                commentCountLabel = commentBindings?.comments?.size?.toString() ?: "0",
                timeLabel = currentMedia?.displayTimeMillis?.let(::realFormatTrashEntryTime)
                    ?: realFormatTrashEntryTime(currentEntry.deletedAtMillis),
                originalActionLabel = if (target != null) {
                    originalLoadState.actionLabel()
                } else {
                    "原媒体不可用"
                },
                originalActionEnabled = target != null && originalLoadState != OriginalLoadState.Loading,
                originalActionEmphasized = target != null && originalLoadState == OriginalLoadState.Loaded,
                previewExpanded = showCommentPreview,
                onToggleComments = { showCommentPreview = !showCommentPreview },
                onOpenOriginal = {
                    if (target != null &&
                        originalLoadState != OriginalLoadState.Loaded &&
                        originalLoadState != OriginalLoadState.Loading
                    ) {
                        RealOriginalLoadRepository.requestOriginal(context, target, accessToken)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        start = YingShiThemeTokens.spacing.lg,
                        end = YingShiThemeTokens.spacing.lg,
                        bottom = 0.dp,
                    ),
            )
        }

        if (showCommentPreview && !isImmersive) {
            RealTrashViewerCommentPreview(
                comments = commentBindings?.comments.orEmpty(),
                isLoading = commentBindings?.isLoading == true,
                errorMessage = commentBindings?.errorMessage,
                onRetry = { commentBindings?.onRetry?.invoke() },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(
                        start = YingShiThemeTokens.spacing.lg,
                        bottom = 64.dp,
                    ),
            )
        }
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
                    enabled = !isMutating,
                    onClick = {
                        showRestoreConfirm = false
                        onRestoreEntry(currentEntry)
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { showRestoreConfirm = false })
            },
        )
    }

    if (showPermanentDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showPermanentDeleteConfirm = false },
            title = { Text("移出回收站？") },
            text = {
                Text(
                    "将把当前项目移到待清理。24 小时内可撤销，也可以在待清理页永久删除。",
                )
            },
            containerColor = YingShiThemeTokens.colors.raisedSurface,
            titleContentColor = YingShiThemeTokens.colors.titleAccent,
            textContentColor = YingShiThemeTokens.colors.textSecondary,
            confirmButton = {
                TrashDialogActionButton(
                    text = "移出回收站",
                    danger = true,
                    enabled = !isMutating,
                    onClick = {
                        showPermanentDeleteConfirm = false
                        onRemoveEntry(currentEntry)
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { showPermanentDeleteConfirm = false })
            },
        )
    }
}

@Composable
private fun TrashViewerTopScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    YingShiThemeTokens.colors.viewerBackground.copy(alpha = 0.34f),
                    YingShiThemeTokens.colors.viewerBackground.copy(alpha = 0.14f),
                    Color.Transparent,
                ),
            ),
        ),
    )
}

@Composable
private fun TrashViewerBottomScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    YingShiThemeTokens.colors.viewerBackground.copy(alpha = 0.12f),
                    YingShiThemeTokens.colors.viewerBackground.copy(alpha = 0.30f),
                ),
            ),
        ),
    )
}

@Composable
private fun TrashViewerMediaCanvas(
    media: TrashMediaSnapshot,
    originalLoadState: OriginalLoadState,
    onOriginalLoadStateChange: (OriginalLoadState) -> Unit,
    immersive: Boolean,
    zoomState: RealTrashViewerZoomState?,
    videoPlaybackState: ViewerVideoPlaybackState?,
    videoControlsVisible: Boolean,
    onVideoAreaClick: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekPlayback: (Long) -> Unit,
    onVideoPlaybackStateChange: (String, ViewerVideoPlaybackState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val density = LocalDensity.current
    val topPadding by animateDpAsState(
        targetValue = if (immersive) 0.dp else 68.dp,
        label = "trashViewerCanvasTopPadding",
    )
    val bottomPadding by animateDpAsState(
        targetValue = if (immersive) {
            if (media.mediaType == AppMediaType.VIDEO) 40.dp else 0.dp
        } else {
            104.dp
        },
        label = "trashViewerCanvasBottomPadding",
    )
    BoxWithConstraints(
        modifier = modifier.padding(top = topPadding, bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        val mediaAspectRatio = media.aspectRatio.coerceIn(0.05f, 20f)
        val fittedMediaWidth = if (maxHeight * mediaAspectRatio <= maxWidth) {
            maxHeight * mediaAspectRatio
        } else {
            maxWidth
        }
        val fittedMediaHeight = if (maxWidth / mediaAspectRatio <= maxHeight) {
            maxWidth / mediaAspectRatio
        } else {
            maxHeight
        }
        val canvasWidth = if (media.mediaType == AppMediaType.VIDEO) maxWidth else fittedMediaWidth
        val canvasHeight = if (media.mediaType == AppMediaType.VIDEO) maxHeight else fittedMediaHeight
        val contentSize = with(density) {
            IntSize(canvasWidth.roundToPx(), canvasHeight.roundToPx())
        }
        val zoomTransformModifier = if (zoomState != null) {
            Modifier.graphicsLayer {
                scaleX = zoomState.scale
                scaleY = zoomState.scale
                translationX = zoomState.offset.x
                translationY = zoomState.offset.y
            }
        } else {
            Modifier
        }
        val gestureModifier = if (zoomState != null) {
            Modifier.realTrashViewerZoomGesture(
                zoomState = zoomState,
                contentSize = contentSize,
            )
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier),
            contentAlignment = Alignment.Center,
        ) {
            if (media.mediaType == AppMediaType.VIDEO) {
                Box(
                    modifier = Modifier
                        .width(canvasWidth)
                        .height(canvasHeight)
                        .background(YingShiThemeTokens.colors.viewerBackground),
                ) {
                    val viewerMedia = remember(media) { media.toViewerPhotoFeedItem() }
                    ViewerVideoCanvas(
                        media = viewerMedia,
                        playbackState = videoPlaybackState,
                        isCurrent = videoPlaybackState?.mediaId == media.mediaId,
                        originalLoadState = originalLoadState,
                        onPlaybackStateChange = onVideoPlaybackStateChange,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (videoPlaybackState?.errorMessage == null) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable(
                                    interactionSource = remember(media.mediaId) {
                                        androidx.compose.foundation.interaction.MutableInteractionSource()
                                    },
                                    indication = androidx.compose.foundation.LocalIndication.current,
                                    onClick = onVideoAreaClick,
                                ),
                        )
                    }
                    if (videoControlsVisible && videoPlaybackState != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .clickable(onClick = onTogglePlayback),
                            shape = CircleShape,
                            color = YingShiThemeTokens.colors.viewerSurface.copy(
                                alpha = if (videoPlaybackState.isPlaying) 0.74f else 0.82f,
                            ),
                            border = BorderStroke(1.dp, YingShiThemeTokens.colors.viewerAccent.copy(alpha = 0.22f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .padding(22.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                VideoGlyph(
                                    state = if (videoPlaybackState.isPlaying) VideoGlyphState.PAUSE else VideoGlyphState.PLAY,
                                    tint = YingShiThemeTokens.colors.viewerText.copy(alpha = 0.92f),
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        ViewerVideoControls(
                            playbackState = videoPlaybackState,
                            durationMillis = videoPlaybackState.durationMillis
                                ?: media.viewerVideoDurationMillis(),
                            onTogglePlayback = onTogglePlayback,
                            onSeekPlayback = onSeekPlayback,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = spacing.lg, vertical = spacing.lg),
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .width(canvasWidth)
                        .height(canvasHeight)
                        .background(YingShiThemeTokens.colors.viewerBackground)
                        .then(zoomTransformModifier),
                ) {
                    AppContentMediaThumbnail(
                        mediaSource = media.mediaSource,
                        mediaType = media.mediaType,
                        palette = media.palette,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = realTrashViewerMediaContentDescription(media.mediaType),
                        contentScale = ContentScale.Fit,
                        requestSize = 1080,
                        showLoadingIndicator = true,
                        showStatusBadge = true,
                        showVideoPlayOverlay = false,
                        originalLoadState = originalLoadState,
                        onOriginalLoadStateChange = onOriginalLoadStateChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun RealTrashViewerTopBar(
    actorIdentity: CollaboratorIdentityUiModel?,
    postTitle: String?,
    isMutating: Boolean,
    canRestore: Boolean,
    canRemove: Boolean,
    onBack: () -> Unit,
    onRestore: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        // 返回键 - 独立 TopStart 对齐（与 PhotoViewer 一致）
        RealTrashViewerIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart),
        )

        // 小相册名 - 返回键右侧（如果有）
        postTitle?.takeIf { it.isNotBlank() }?.let { title ->
            RealTrashViewerMetaCapsule(
                text = title,
                emphasized = true,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 52.dp)
                    .widthIn(max = 180.dp),
            )
        }

        // 右侧按钮组
        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actorIdentity?.let {
                CollaboratorMarkerBadge(
                    identity = it,
                    size = 44.dp,
                )
            }
            if (canRestore) {
                RealTrashViewerIconButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "恢复",
                    enabled = !isMutating,
                    onClick = onRestore,
                )
            }
            if (canRemove) {
                RealTrashViewerIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "移出回收站",
                    destructive = true,
                    enabled = !isMutating,
                    onClick = onRemove,
                )
            }
        }
    }
}

@Composable
internal fun RealTrashViewerEdgeActions(
    commentCountLabel: String,
    timeLabel: String,
    originalActionLabel: String,
    originalActionEnabled: Boolean,
    originalActionEmphasized: Boolean,
    previewExpanded: Boolean,
    onToggleComments: () -> Unit,
    onOpenOriginal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        // 评论按钮（左侧）
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .yingShiClickable(
                    shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                    pressedScale = 0.96f,
                    onClick = onToggleComments,
                ),
            horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = YingShiThemeTokens.colors.viewerAccent.copy(alpha = if (previewExpanded) 0.24f else 0.14f),
                border = BorderStroke(1.dp, YingShiThemeTokens.colors.viewerOverlayBorder),
            ) {
                Text(
                    text = "评",
                    modifier = Modifier.padding(horizontal = YingShiThemeTokens.spacing.sm, vertical = YingShiThemeTokens.spacing.sm),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = YingShiThemeTokens.colors.viewerText.copy(alpha = 0.94f),
                )
            }
            if (commentCountLabel != "0") {
                RealTrashViewerCapsule(
                    text = commentCountLabel,
                    emphasized = true,
                    surfaceAlpha = if (previewExpanded) 0.18f else 0.14f,
                )
            }
        }

        // 时间标签（居中）
        Surface(
            modifier = Modifier.align(Alignment.Center),
            shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
            color = YingShiThemeTokens.colors.viewerSurface.copy(alpha = 0.82f),
            border = BorderStroke(1.dp, YingShiThemeTokens.colors.viewerAccent.copy(alpha = 0.18f)),
        ) {
            Text(
                text = timeLabel,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = YingShiThemeTokens.colors.viewerText.copy(alpha = 0.96f),
            )
        }

        // 原图按钮（右侧）
        RealTrashViewerCapsule(
            text = originalActionLabel,
            emphasized = originalActionEmphasized,
            enabled = originalActionEnabled,
            onClick = onOpenOriginal,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun RealTrashViewerTimeBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = YingShiThemeTokens.colors.viewerSurface.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, YingShiThemeTokens.colors.viewerOverlayBorder.copy(alpha = 0.25f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = YingShiThemeTokens.colors.viewerText.copy(alpha = 0.86f),
        )
    }
}

@Composable
internal fun RealTrashViewerIconButton(
    icon: ImageVector,
    contentDescription: String,
    destructive: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(46.dp)
            .yingShiClickable(shape = shape, pressedScale = 0.92f, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 按下时的微妙背景反馈
        Surface(
            shape = shape,
            color = if (destructive) {
                colors.destructiveContainer.copy(alpha = 0.12f)
            } else {
                colors.viewerSurface.copy(alpha = 0.08f)
            },
        ) {
            Box(modifier = Modifier.fillMaxSize())
        }
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (destructive) {
                colors.destructive.copy(alpha = if (enabled) 0.80f else 0.45f)
            } else {
                colors.viewerText.copy(alpha = if (enabled) 0.85f else 0.45f)
            },
            modifier = Modifier.size(23.dp),
        )
    }
}

@Composable
private fun RealTrashViewerCapsule(
    text: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    surfaceAlpha: Float = if (emphasized) 0.14f else 0.10f,
    contentAlpha: Float = 0.94f,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    Surface(
        modifier = modifier.then(
            if (onClick != null && enabled) {
                Modifier.yingShiClickable(shape = shape, pressedScale = 0.96f, onClick = onClick)
            } else {
                Modifier
            },
        ),
        shape = shape,
        color = if (destructive) {
            colors.destructiveContainer.copy(alpha = if (enabled) surfaceAlpha + 0.26f else 0.22f)
        } else {
            colors.viewerSurface.copy(alpha = if (enabled) surfaceAlpha + 0.26f else 0.22f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (destructive) {
                colors.destructive.copy(alpha = if (enabled) surfaceAlpha + 0.10f else 0.08f)
            } else {
                colors.viewerOverlayBorder.copy(alpha = if (enabled) surfaceAlpha + 0.10f else 0.08f)
            },
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = YingShiThemeTokens.spacing.sm, vertical = YingShiThemeTokens.spacing.xs),
            style = if (emphasized) {
                MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            } else {
                MaterialTheme.typography.labelLarge
            },
            color = colors.viewerText.copy(alpha = if (enabled) contentAlpha else 0.58f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun RealTrashViewerCommentPreview(
    comments: List<CommentUiModel>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            when {
                isLoading -> Text(
                    text = "加载中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.viewerTextSecondary.copy(alpha = 0.92f),
                )
                errorMessage != null -> Text(
                    text = errorMessage.ifBlank { "评论加载失败，点击重试" },
                    modifier = Modifier.clickable(onClick = onRetry),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.destructive.copy(alpha = 0.94f),
                )
                comments.isEmpty() -> Text(
                    text = "还没有评论",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.viewerTextSecondary.copy(alpha = 0.92f),
                )
                else -> comments.take(2).forEach { comment ->
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
}

@Composable
internal fun RealTrashDeletedMediaPlaceholder(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(YingShiThemeTokens.radius.lg))
            .background(YingShiThemeTokens.colors.viewerSurface.copy(alpha = 0.62f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "已删除",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = YingShiThemeTokens.colors.viewerTextSecondary,
        )
    }
}
