package com.example.yingshi.feature.photos

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.imageLoader
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.sync.SyncModule
import com.example.yingshi.feature.sync.SyncVersionTracker
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiViewerSurface
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun PrefetchViewerMediaAssets(
    items: List<PhotoFeedItem>,
    currentIndex: Int,
    accessToken: String?,
) {
    if (items.isEmpty()) return

    val context = LocalContext.current
    val targets = remember(items, currentIndex) {
        buildList {
            listOf(currentIndex - 1, currentIndex, currentIndex + 1)
                .distinct()
                .forEach { index ->
                    val item = items.getOrNull(index) ?: return@forEach
                    add(item)
                }
        }
    }

    LaunchedEffect(context, targets, accessToken) {
        val imageLoader = context.imageLoader
        targets.forEach { item ->
            if (item.mediaType == AppMediaType.VIDEO) {
                val posterImageCacheKey = item.mediaSource.videoPosterImageCacheKey(item.mediaType)
                val posterImageUrl = item.mediaSource.videoPosterImageUrl(item.mediaType)
                val posterImageDiskCacheKey = item.mediaSource.videoPosterImageDiskCacheKey(item.mediaType)
                if (posterImageUrl != null) {
                    backendMediaImageRequest(
                        context = context,
                        url = posterImageUrl,
                        accessToken = accessToken,
                        memoryCacheKey = posterImageCacheKey ?: sharedPreviewMemoryCacheKey(posterImageUrl),
                        diskCacheKey = posterImageDiskCacheKey,
                        size = 1280,
                    )?.let(imageLoader::enqueue)
                    return@forEach
                }
            } else {
                item.mediaSource?.viewerPreviewImageUrl(item.mediaType)?.let { previewUrl ->
                    val previewCacheKey = item.mediaSource.viewerPreviewImageCacheKey(item.mediaType)
                    backendMediaImageRequest(
                        context = context,
                        url = previewUrl,
                        accessToken = accessToken,
                        memoryCacheKey = previewCacheKey ?: sharedPreviewMemoryCacheKey(previewUrl),
                        diskCacheKey = previewCacheKey,
                        size = 1280,
                    )?.let(imageLoader::enqueue)
                }
            }
        }
    }
}

@Composable
internal fun EmptyPhotoViewerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ViewerNightBottom),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ViewerSheetActionButton(text = "返回", onClick = onBack)
            Text(
                text = "当前没有可查看的媒体",
                style = MaterialTheme.typography.titleMedium,
                color = ViewerSurface.copy(alpha = 0.92f),
            )
        }
    }
}

internal fun deleteFakeViewerMedia(item: PhotoFeedItem) {
    val selectedIds = setOf(item.mediaId)
    val outcome = FakeAlbumRepository.previewGlobalMediaDelete(selectedIds)
    val deletedPostSnapshots = outcome.deletedPostIds.mapNotNull(FakeAlbumRepository::snapshotPost)
    val relationSnapshotsByMediaId = FakeAlbumRepository.snapshotMediaRelations(selectedIds)

    FakeTrashRepository.recordSystemDeletedMedia(
        mediaSnapshots = listOf(
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
                sourcePostId = item.postIds.firstOrNull(),
                sourcePostTitle = item.postIds.firstOrNull()?.let(FakeAlbumRepository::getPost)?.title,
            ),
        ),
        relationSnapshotsByMediaId = relationSnapshotsByMediaId,
    )
    deletedPostSnapshots.forEach(FakeTrashRepository::recordDeletedPost)
    val appliedOutcome = FakeAlbumRepository.applyGlobalMediaDelete(selectedIds)
    FakeAlbumRepository.deletePostsLocally(appliedOutcome.deletedPostIds)
}

internal suspend fun deleteRealViewerMedia(mediaId: String): String? {
    if (!AuthSessionManager.isLoggedIn) {
        return "请先连接服务，再删除这项媒体。"
    }
    return when (val result = RepositoryProvider.mediaRepository.systemDeleteMedia(mediaId)) {
        is ApiResult.Success -> {
            TrashActorHintStore.record(
                item = result.data,
                fallbackActorUserId = currentCollaboratorActorUserId(),
            )
            // 立即清除本地导入 overlay, 避免刷新完成前 Viewer 仍显示"已导入".
            // 与 RealTrashViewModels 永久删除路径保持一致.
            LocalSystemMediaBridgeRepository.forgetImportStatusByAppMediaId(mediaId)
            invalidateSystemMediaMetadataCache(clearDisk = true)
            notifyRealBackendContentChanged(
                mediaIds = setOf(mediaId),
            )
            null
        }
        is ApiResult.Error -> result.toBackendUiMessage("删除真实媒体失败。")
        ApiResult.Loading -> null
    }
}

internal fun buildViewerTimeMillis(
    selectedDateMillis: Long,
    hour: Int,
    minute: Int,
): Long {
    return Calendar.getInstance(Locale.CHINA).run {
        timeInMillis = selectedDateMillis
        set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
        set(Calendar.MINUTE, minute.coerceIn(0, 59))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }
}

internal fun PhotoFeedItem.withViewerDisplayTime(timeMillis: Long): PhotoFeedItem {
    val calendar = Calendar.getInstance(Locale.CHINA).apply {
        timeInMillis = timeMillis
    }
    return copy(
        mediaDisplayTimeMillis = timeMillis,
        displayYear = calendar.get(Calendar.YEAR),
        displayMonth = calendar.get(Calendar.MONTH) + 1,
        displayDay = calendar.get(Calendar.DAY_OF_MONTH),
        displayTimeSource = DisplayTimeSourceManual,
    )
}

internal fun formatViewerTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}

internal fun shareCurrentMedia(
    context: Context,
    currentItem: PhotoFeedItem,
    coroutineScope: CoroutineScope,
    isShareInFlight: Boolean,
    setShareInFlight: (Boolean) -> Unit,
    showViewerNotice: (String, Boolean) -> Unit,
) {
    if (isShareInFlight) {
        showViewerNotice("正在准备分享文件…", false)
    } else {
        coroutineScope.launch {
            setShareInFlight(true)
            showViewerNotice("正在准备分享文件…", false)
            try {
                when (
                    val result = MediaShareManager.shareMedia(
                        context = context,
                        items = listOf(currentItem.toShareableMediaItem()),
                        packageBaseName = "映世-${currentItem.mediaId}",
                    )
                ) {
                    is MediaShareLaunchResult.Success -> {
                        showViewerNotice(result.toNoticeMessage(), true)
                    }
                    is MediaShareLaunchResult.Error -> {
                        showViewerNotice(result.message, false)
                    }
                }
            } finally {
                setShareInFlight(false)
            }
        }
    }
}

internal fun toggleVideoPlayback(
    videoPlaybackState: ViewerVideoPlaybackState,
    currentItem: PhotoFeedItem,
    setVideoPlaybackState: (ViewerVideoPlaybackState) -> Unit,
    revealVideoControls: () -> Unit,
) {
    revealVideoControls()
    val durationMillis = videoPlaybackState.durationMillis
        ?: currentItem.viewerVideoDurationMillis()
    val shouldRestart = videoPlaybackState.isCompleted ||
        (durationMillis > 0L && videoPlaybackState.progressMillis >= durationMillis)
    val nextState = if (videoPlaybackState.errorMessage != null) {
        videoPlaybackState.retryState().copy(mediaId = currentItem.mediaId)
    } else if (videoPlaybackState.isPlaying) {
        videoPlaybackState.copy(isPlaying = false)
    } else {
        videoPlaybackState.copy(
            mediaId = currentItem.mediaId,
            isPlaying = true,
            progressMillis = if (shouldRestart) 0L else videoPlaybackState.progressMillis,
            seekRequestMillis = if (shouldRestart) 0L else videoPlaybackState.seekRequestMillis,
            seekRequestNonce = if (shouldRestart) {
                videoPlaybackState.seekRequestNonce + 1
            } else {
                videoPlaybackState.seekRequestNonce
            },
            pendingSeekTargetMillis = if (shouldRestart) 0L else videoPlaybackState.pendingSeekTargetMillis,
            errorMessage = null,
            isCompleted = false,
        )
    }
    setVideoPlaybackState(nextState)
}

internal fun seekVideoPlayback(
    progressMillis: Long,
    videoPlaybackState: ViewerVideoPlaybackState,
    currentItem: PhotoFeedItem,
    setVideoPlaybackState: (ViewerVideoPlaybackState) -> Unit,
    revealVideoControls: () -> Unit,
) {
    revealVideoControls()
    val durationMillis = videoPlaybackState.durationMillis
        ?: currentItem.viewerVideoDurationMillis()
    val targetMillis = progressMillis.coerceIn(0L, durationMillis.coerceAtLeast(0L))
    val nextState = videoPlaybackState.copy(
        mediaId = currentItem.mediaId,
        progressMillis = targetMillis,
        seekRequestMillis = targetMillis,
        seekRequestNonce = videoPlaybackState.seekRequestNonce + 1,
        pendingSeekTargetMillis = targetMillis,
        errorMessage = null,
        isCompleted = false,
    )
    setVideoPlaybackState(nextState)
}

internal fun handleViewerTap(
    position: Offset,
    size: IntSize,
    currentItem: PhotoFeedItem,
    isImmersive: Boolean,
    density: Density,
    videoControlsVisible: Boolean,
    setVideoControlsVisible: (Boolean) -> Unit,
    toggleImmersive: () -> Unit,
    incrementVideoControlsActivityNonce: () -> Unit,
) {
    if (currentItem.mediaType == AppMediaType.VIDEO) {
        val topTapZonePx = with(density) {
            if (isImmersive) {
                ViewerLayoutTuning.immersiveCanvasTopPadding.toPx()
            } else {
                ViewerLayoutTuning.canvasTopPadding.toPx()
            }
        }
        val bottomTapZonePx = with(density) {
            if (isImmersive) {
                ViewerLayoutTuning.immersiveVideoBottomExitZone.toPx()
            } else {
                ViewerLayoutTuning.canvasBottomPadding.toPx()
            }
        }
        if (position.y <= topTapZonePx || position.y >= size.height - bottomTapZonePx) {
            toggleImmersive()
        } else {
            setVideoControlsVisible(!videoControlsVisible)
            incrementVideoControlsActivityNonce()
        }
    } else {
        toggleImmersive()
    }
}

internal fun handleViewerDoubleTap(
    position: Offset,
    size: IntSize,
    currentItem: PhotoFeedItem,
    zoomState: ViewerZoomState,
) {
    if (currentItem.mediaType == AppMediaType.IMAGE) {
        zoomState.toggleDoubleTap(
            tapPosition = position,
            containerSize = size,
        )
    }
}

internal fun applyTimeEdit(
    nextTimeMillis: Long,
    currentItem: PhotoFeedItem,
    viewerItems: List<PhotoFeedItem>,
    currentIndex: Int,
    coroutineScope: CoroutineScope,
    pagerState: PagerState,
    setShowTimeEditorSheet: (Boolean) -> Unit,
    setViewerItems: (List<PhotoFeedItem>) -> Unit,
    showViewerNotice: (String, Boolean) -> Unit,
) {
    setShowTimeEditorSheet(false)
    val currentMediaId = currentItem.mediaId
    // 即时反馈：先更新本地 viewer 顺序, 让用户看到改动
    val nextItems = viewerItems
        .map { item ->
            if (item.mediaId == currentMediaId) {
                item.withViewerDisplayTime(nextTimeMillis)
            } else {
                item
            }
        }
        .sortedByDescending { it.mediaDisplayTimeMillis }
    setViewerItems(nextItems)
    coroutineScope.launch {
        val nextIndex = nextItems.indexOfFirst { it.mediaId == currentMediaId }
            .takeIf { it >= 0 }
            ?: currentIndex.coerceIn(0, nextItems.lastIndex)
        pagerState.scrollToPage(nextIndex)
    }
    showViewerNotice("时间已修改", true)
    // 异步持久化到服务端 (替换原 MediaTimeOverrides 假持久化)
    if (!AuthSessionManager.isLoggedIn) {
        showViewerNotice("未登录, 修改仅在本地有效", false)
        return
    }
    coroutineScope.launch {
        when (val result = RepositoryProvider.mediaRepository.updateMediaTime(currentMediaId, nextTimeMillis)) {
            is ApiResult.Success -> {
                SyncVersionTracker.markLocalMutation(SyncModule.PHOTO_FEED)
                notifyRealBackendContentChanged(mediaIds = setOf(currentMediaId))
            }
            is ApiResult.Error -> {
                showViewerNotice(result.toBackendUiMessage("时间修改失败, 请重试"), false)
            }
            ApiResult.Loading -> Unit
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PhotoViewerScreenPreview() {
    YingShiTheme(darkTheme = true) {
        PhotoViewerScreen(
            route = PhotoViewerRoute(
                mediaItems = FakePhotoFeedRepository.getPhotoFeed(),
                initialIndex = 0,
                sourceLabel = "照片页全局媒体流",
                showSmallAlbumSegments = false,
            ),
            onBack = { },
        )
    }
}
