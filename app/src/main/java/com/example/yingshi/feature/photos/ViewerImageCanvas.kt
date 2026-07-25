package com.example.yingshi.feature.photos

import android.os.SystemClock
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.components.yingShiMediaEnterMotion
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlin.math.min

// A 2:1 cutoff catches long screenshots/receipts while ordinary 4:5 and
// 9:16 portraits remain in the regular viewer path.
internal const val LongImageHeightWidthRatioThreshold = 2.0f

internal fun Modifier.viewerZoomGesture(
    zoomState: ViewerZoomState,
    contentSize: IntSize,
    contentTopLeft: Offset,
): Modifier = pointerInput(zoomState, contentSize, contentTopLeft) {
    awaitEachGesture {
        while (true) {
            val event = awaitPointerEvent()
            val activeChanges = event.changes.filter { it.pressed }
            if (activeChanges.isEmpty()) break

            if (activeChanges.size >= 2) {
                val currentCentroid = activeChanges.centroid(usePrevious = false)
                val previousCentroid = activeChanges.centroid(usePrevious = true)
                val currentDistance = activeChanges.averageDistanceTo(currentCentroid, usePrevious = false)
                val previousDistance = activeChanges.averageDistanceTo(previousCentroid, usePrevious = true)
                val zoomChange = if (previousDistance > 0f) {
                    currentDistance / previousDistance
                } else {
                    MinViewerScale
                }

                zoomState.applyTransform(
                    zoomChange = zoomChange,
                    panChange = currentCentroid - previousCentroid,
                    focalPoint = currentCentroid,
                    containerSize = size,
                    contentSize = contentSize,
                    contentTopLeft = contentTopLeft,
                )
                activeChanges.forEach { it.consume() }
            } else if (zoomState.isZoomed) {
                val panChange = activeChanges.first().positionChange()
                if (panChange.getDistance() > viewConfiguration.touchSlop / 3f) {
                    zoomState.panBy(
                        panChange = panChange,
                        containerSize = size,
                        contentSize = contentSize,
                        contentTopLeft = contentTopLeft,
                    )
                    activeChanges.forEach { it.consume() }
                }
            }
        }
        zoomState.settleAfterGesture(
            containerSize = size,
            contentSize = contentSize,
            contentTopLeft = contentTopLeft,
        )
    }
}

internal fun Modifier.viewerSingleTapGesture(
    enabled: Boolean = true,
    onDoubleTap: ((Offset, IntSize) -> Unit)? = null,
    onTap: (Offset, IntSize) -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(onTap, onDoubleTap) {
        // 用 coroutineScope 包裹, 以便 launch 延迟触发 onTap.
        // 此前在每次抬手时立即触发 onTap, 导致双击会先触发单击(收起信息)再触发双击(缩放).
        // 修复: 抬手后延迟 ViewerFastDoubleTapWindowMillis 触发 onTap, 若期间出现第二次 tap 则取消并触发 onDoubleTap.
        coroutineScope {
            var pendingTapJob: Job? = null
            var lastTapUptimeMillis = 0L
            var lastTapPosition: Offset? = null
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = down.position
                var pointerCountExceeded = false
                var moved = false
                var consumed = down.isConsumed
                var upPosition = start
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.size > 1) pointerCountExceeded = true
                    event.changes.forEach { change ->
                        if (change.isConsumed) consumed = true
                        if ((change.position - start).getDistance() > viewConfiguration.touchSlop) {
                            moved = true
                        }
                        if (change.id == down.id && !change.pressed) {
                            upPosition = change.position
                        }
                    }
                    // 滑动/多指时取消 pending tap, 避免误触发单击.
                    if (moved || pointerCountExceeded) {
                        pendingTapJob?.cancel()
                        pendingTapJob = null
                        lastTapUptimeMillis = 0L
                        lastTapPosition = null
                    }
                    if (pressed.isEmpty()) {
                        if (!pointerCountExceeded && !moved && !consumed) {
                            val now = SystemClock.uptimeMillis()
                            val previousTapPosition = lastTapPosition
                            val doubleTapDistance = viewConfiguration.touchSlop * 8f
                            val isDoubleTap = onDoubleTap != null &&
                                previousTapPosition != null &&
                                now - lastTapUptimeMillis <= ViewerFastDoubleTapWindowMillis &&
                                (upPosition - previousTapPosition).getDistance() <= doubleTapDistance
                            if (isDoubleTap) {
                                pendingTapJob?.cancel()
                                lastTapUptimeMillis = 0L
                                lastTapPosition = null
                                onDoubleTap?.invoke(upPosition, size)
                            } else {
                                pendingTapJob?.cancel()
                                lastTapUptimeMillis = now
                                lastTapPosition = upPosition
                                val tapPosition = upPosition
                                val tapSize = size
                                pendingTapJob = launch {
                                    delay(ViewerFastDoubleTapWindowMillis)
                                    onTap(tapPosition, tapSize)
                                }
                            }
                        }
                        break
                    }
                }
            }
        }
    }
}

private fun List<PointerInputChange>.centroid(usePrevious: Boolean): Offset {
    val total = fold(Offset.Zero) { sum, change ->
        sum + if (usePrevious) change.previousPosition else change.position
    }
    return total / size.toFloat()
}

private fun List<PointerInputChange>.averageDistanceTo(
    centroid: Offset,
    usePrevious: Boolean,
): Float {
    return sumOf { change ->
        val position = if (usePrevious) change.previousPosition else change.position
        (position - centroid).getDistance().toDouble()
    }.toFloat() / size
}

@Composable
internal fun PhotoViewerCanvas(
    media: PhotoFeedItem,
    zoomState: ViewerZoomState?,
    videoPlaybackState: ViewerVideoPlaybackState?,
    originalLoadState: OriginalLoadState,
    originalLoadingLabel: String,
    overlaysVisible: Boolean,
    immersive: Boolean,
    videoControlsVisible: Boolean,
    onVideoAreaClick: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekPlayback: (Long) -> Unit,
    onVideoPlaybackStateChange: (String, ViewerVideoPlaybackState) -> Unit,
    onOriginalLoadStateChange: (String, OriginalLoadState) -> Unit,
    autoLongImageReading: Boolean,
    autoPauseVideoOnMediaSwitch: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val density = LocalDensity.current
    val isVideo = media.mediaType == AppMediaType.VIDEO
    val useLongImageReading = !isVideo &&
        autoLongImageReading &&
        media.shouldUseLongImageReading()
    val topPadding by animateDpAsState(
        targetValue = if (immersive) ViewerLayoutTuning.immersiveCanvasTopPadding else ViewerLayoutTuning.canvasTopPadding,
        label = "viewerCanvasTopPadding",
    )
    val bottomPadding by animateDpAsState(
        targetValue = if (immersive) {
            if (isVideo) ViewerLayoutTuning.immersiveVideoBottomExitZone else ViewerLayoutTuning.immersiveCanvasBottomPadding
        } else {
            ViewerLayoutTuning.canvasBottomPadding
        },
        label = "viewerCanvasBottomPadding",
    )

    BoxWithConstraints(
        modifier = modifier.padding(
            start = ViewerLayoutTuning.canvasHorizontalPadding,
            top = topPadding,
            end = ViewerLayoutTuning.canvasHorizontalPadding,
            bottom = bottomPadding,
        ),
        contentAlignment = Alignment.Center,
    ) {
        val mediaAspectRatio = media.viewerAspectRatio().coerceIn(0.05f, 20f)
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
        val canvasWidth = if (isVideo || useLongImageReading) maxWidth else fittedMediaWidth
        val canvasHeight = if (isVideo) {
            maxHeight
        } else if (useLongImageReading) {
            (maxWidth / mediaAspectRatio).coerceAtLeast(maxHeight)
        } else {
            fittedMediaHeight
        }
        val contentSize = with(density) {
            IntSize(canvasWidth.roundToPx(), canvasHeight.roundToPx())
        }
        val longImageScrollState = rememberScrollState()
        LaunchedEffect(media.mediaId, useLongImageReading) {
            if (useLongImageReading) {
                longImageScrollState.scrollTo(0)
            }
        }
        val containerSize = with(density) {
            IntSize(maxWidth.roundToPx(), maxHeight.roundToPx())
        }
        val longImageMinimumScale = if (useLongImageReading &&
            contentSize.width > 0 &&
            contentSize.height > 0 &&
            containerSize.width > 0 &&
            containerSize.height > 0
        ) {
            min(
                containerSize.width.toFloat() / contentSize.width.toFloat(),
                containerSize.height.toFloat() / contentSize.height.toFloat(),
            ).coerceIn(0.05f, MinViewerScale)
        } else {
            MinViewerScale
        }
        val contentTopLeft = Offset(
            x = ((containerSize.width - contentSize.width) / 2f),
            y = if (useLongImageReading) {
                -longImageScrollState.value.toFloat()
            } else {
                ((containerSize.height - contentSize.height) / 2f)
            },
        )
        zoomState?.updateContentGeometry(
            contentSize = contentSize,
            contentTopLeft = contentTopLeft,
            minimumScale = longImageMinimumScale,
        )
        val zoomTransformModifier = if (zoomState != null) {
            Modifier
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = zoomState.scale
                    scaleY = zoomState.scale
                    translationX = zoomState.offset.x
                    translationY = zoomState.offset.y
                }
        } else {
            Modifier
        }
        val gestureModifier = if (zoomState != null) {
            Modifier.viewerZoomGesture(
                zoomState = zoomState,
                contentSize = contentSize,
                contentTopLeft = contentTopLeft,
            )
        } else {
            Modifier
        }
        var mediaEnterActive by remember(media.mediaId, zoomState != null) { mutableStateOf(false) }
        LaunchedEffect(media.mediaId, zoomState != null) {
            mediaEnterActive = zoomState != null
        }
        val mediaEnterModifier = if (zoomState != null) {
            Modifier
                .yingShiMediaEnterMotion(active = mediaEnterActive)
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier),
            contentAlignment = if (useLongImageReading) Alignment.TopCenter else Alignment.Center,
        ) {
            if (useLongImageReading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(
                            state = longImageScrollState,
                            enabled = zoomState?.isZoomed != true,
                        ),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .width(canvasWidth)
                            .height(canvasHeight)
                            .then(mediaEnterModifier)
                            .then(zoomTransformModifier)
                            .background(ViewerNightBottom),
                    ) {
                        if (media.mediaSource != null) {
                            ViewerImageCanvas(
                                media = media,
                                originalLoadState = originalLoadState,
                                originalLoadingLabel = originalLoadingLabel,
                                onOriginalLoadStateChange = onOriginalLoadStateChange,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Surface(
                                modifier = Modifier.align(Alignment.Center),
                                shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                                color = ViewerNightTop.copy(alpha = 0.82f),
                            ) {
                                Text(
                                    text = "暂无可用媒体预览",
                                    modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ViewerSurface.copy(alpha = 0.82f),
                                )
                            }
                        }
                    }
                }
            } else if (isVideo) {
                Box(
                    modifier = Modifier
                        .width(canvasWidth)
                        .height(canvasHeight),
                ) {
                    val revealInteractionSource = remember(media.mediaId) { MutableInteractionSource() }
                    ViewerVideoCanvas(
                        media = media,
                        playbackState = videoPlaybackState,
                        isCurrent = zoomState != null,
                        autoPauseOnMediaSwitch = autoPauseVideoOnMediaSwitch,
                        originalLoadState = originalLoadState,
                        onPlaybackStateChange = onVideoPlaybackStateChange,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(mediaEnterModifier)
                            .then(zoomTransformModifier),
                    )
                    if (videoPlaybackState?.errorMessage == null) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable(
                                    interactionSource = revealInteractionSource,
                                    indication = LocalIndication.current,
                                    onClick = onVideoAreaClick,
                                ),
                        )
                    }
                    if (videoControlsVisible && videoPlaybackState != null) {
                        val playButtonShape = CircleShape
                        Surface(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .yingShiClickable(
                                    shape = playButtonShape,
                                    pressedScale = 0.94f,
                                    onClick = onTogglePlayback,
                                ),
                            shape = playButtonShape,
                            color = ViewerNightTop.copy(alpha = if (videoPlaybackState.isPlaying) 0.62f else 0.70f),
                            border = BorderStroke(1.dp, ViewerAccent.copy(alpha = 0.26f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .padding(22.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                VideoGlyph(
                                    state = if (videoPlaybackState.isPlaying) VideoGlyphState.PAUSE else VideoGlyphState.PLAY,
                                    tint = ViewerSurface.copy(alpha = 0.92f),
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                    if (videoControlsVisible && videoPlaybackState != null) {
                        val durationMillis = videoPlaybackState.durationMillis
                            ?: media.viewerVideoDurationMillis()
                        ViewerVideoControls(
                            playbackState = videoPlaybackState,
                            durationMillis = durationMillis,
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
                        .then(mediaEnterModifier)
                        .then(zoomTransformModifier)
                        .background(ViewerNightBottom),
                ) {
                    if (media.mediaSource != null) {
                        ViewerImageCanvas(
                            media = media,
                            originalLoadState = originalLoadState,
                            originalLoadingLabel = originalLoadingLabel,
                            onOriginalLoadStateChange = onOriginalLoadStateChange,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Surface(
                            modifier = Modifier.align(Alignment.Center),
                            shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                            color = ViewerNightTop.copy(alpha = 0.82f),
                        ) {
                            Text(
                                text = "暂无可用媒体预览",
                                modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                                style = MaterialTheme.typography.labelMedium,
                                color = ViewerSurface.copy(alpha = 0.82f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ViewerImageCanvas(
    media: PhotoFeedItem,
    originalLoadState: OriginalLoadState,
    originalLoadingLabel: String,
    onOriginalLoadStateChange: (String, OriginalLoadState) -> Unit,
    contentScale: ContentScale = ContentScale.Fit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val mediaSource = media.mediaSource
    val previewUrl = remember(mediaSource, media.mediaType) {
        mediaSource.viewerPreviewImageUrl(media.mediaType)
    }
    val previewCacheKey = remember(mediaSource, media.mediaType) {
        mediaSource.viewerPreviewImageCacheKey(media.mediaType)
    }
    val originalUrl = remember(mediaSource, media.mediaType) {
        mediaSource.viewerOriginalImageUrl(media.mediaType)
    }
    val originalCacheKey = remember(mediaSource, media.mediaType) {
        mediaSource.viewerOriginalImageCacheKey(media.mediaType)
    }
    val shouldRequestOriginal = originalLoadState == OriginalLoadState.Loaded
    val sessionVersion = AuthSessionManager.sessionVersion
    val accessToken = remember(sessionVersion) {
        AuthSessionManager.peekAccessToken()?.takeIf { it.isNotBlank() }
    }
    val previewRequest = remember(context, previewUrl, previewCacheKey, accessToken) {
        backendMediaImageRequest(
            context = context,
            url = previewUrl,
            accessToken = accessToken,
            memoryCacheKey = previewCacheKey ?: previewUrl?.let(::sharedPreviewMemoryCacheKey),
            diskCacheKey = previewCacheKey,
        )
    }
    val originalRequest = remember(context, originalUrl, originalCacheKey, shouldRequestOriginal, accessToken) {
        if (shouldRequestOriginal) {
            backendMediaOriginalImageRequest(
                context = context,
                url = originalUrl,
                accessToken = accessToken,
                memoryCacheKey = originalCacheKey ?: originalUrl?.let(::sharedOriginalMemoryCacheKey),
                diskCacheKey = originalCacheKey,
            )
        } else {
            null
        }
    }
    val previewPainter = rememberAsyncImagePainter(model = previewRequest)
    val originalPainter = rememberAsyncImagePainter(model = originalRequest)
    val previewState = previewPainter.state
    val originalState = originalPainter.state
    val showOriginal = originalLoadState == OriginalLoadState.Loaded &&
        originalState is AsyncImagePainter.State.Success
    val showPreview = previewRequest != null &&
        previewState !is AsyncImagePainter.State.Error &&
        !showOriginal
    val failureReason = when {
        previewUrl == null && originalUrl == null -> ViewerImageFailureReason.MISSING_URL
        showOriginal || showPreview -> ViewerImageFailureReason.NONE
        originalLoadState == OriginalLoadState.Failed -> ViewerImageFailureReason.ORIGINAL_FAILED
        previewRequest != null && previewState is AsyncImagePainter.State.Error -> ViewerImageFailureReason.PREVIEW_FAILED
        else -> ViewerImageFailureReason.NONE
    }

    Box(
        modifier = modifier.background(ViewerNightBottom),
        contentAlignment = Alignment.Center,
    ) {
        if (showPreview) {
            Image(
                painter = previewPainter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }

        if (showOriginal) {
            Image(
                painter = originalPainter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }

        if (previewRequest != null && previewState is AsyncImagePainter.State.Loading && !showOriginal) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = ViewerSurface.copy(alpha = 0.90f),
                strokeWidth = 2.dp,
            )
        }

        if (originalLoadState == OriginalLoadState.Loading) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = spacing.md),
                shape = RoundedCornerShape(radius.capsule),
                color = ViewerNightTop.copy(alpha = 0.84f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = ViewerSurface.copy(alpha = 0.88f),
                        strokeWidth = 1.5.dp,
                    )
                    Text(
                        text = originalLoadingLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = ViewerSurface.copy(alpha = 0.88f),
                    )
                }
            }
        }

        if (failureReason != ViewerImageFailureReason.NONE) {
            ViewerImageFallback(
                reason = failureReason,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun ViewerImageFallback(
    reason: ViewerImageFailureReason,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val label = reason.message.takeIf { it.isNotBlank() } ?: return

    Surface(
        modifier = modifier.padding(spacing.lg),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = ViewerNightTop.copy(alpha = 0.82f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = ViewerSurface.copy(alpha = 0.88f),
        )
    }
}

internal fun PhotoFeedItem.viewerAspectRatio(): Float {
    val widthValue = width
    val heightValue = height
    if (widthValue != null && heightValue != null && widthValue > 0 && heightValue > 0) {
        return widthValue.toFloat() / heightValue.toFloat()
    }
    return aspectRatio.coerceAtLeast(0.2f)
}

internal fun PhotoFeedItem.shouldUseLongImageReading(): Boolean {
    if (mediaType != AppMediaType.IMAGE) return false
    val widthValue = width
    val heightValue = height
    if (widthValue != null && heightValue != null && widthValue > 0 && heightValue > 0) {
        return heightValue.toFloat() / widthValue.toFloat() >= LongImageHeightWidthRatioThreshold
    }
    val normalizedAspectRatio = aspectRatio.takeIf { it > 0f } ?: return false
    return 1f / normalizedAspectRatio >= LongImageHeightWidthRatioThreshold
}
