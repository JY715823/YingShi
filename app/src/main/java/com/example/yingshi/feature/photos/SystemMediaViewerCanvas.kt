package com.example.yingshi.feature.photos

import android.widget.VideoView
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlin.math.min

internal const val SystemLongImageHeightWidthRatioThreshold = 3.8f

internal object SystemViewerLayoutTuning {
    val canvasHorizontalPadding = 0.dp
    val canvasTopPadding = 88.dp
    val canvasBottomPadding = 84.dp
    val immersiveCanvasTopPadding = 0.dp
    val immersiveCanvasBottomPadding = 0.dp
    val immersiveVideoBottomExitZone = 64.dp
}

@Composable
internal fun SystemMediaViewerCanvas(
    item: SystemMediaItem,
    isCurrent: Boolean,
    immersive: Boolean,
    zoomState: ViewerZoomState?,
) {
    var containerSize by remember(item.id) { mutableStateOf(IntSize.Zero) }
    var videoPlaybackState by remember(item.id) {
        mutableStateOf(ViewerVideoPlaybackState())
    }
    val colors = YingShiThemeTokens.colors
    val density = LocalDensity.current
    var videoRetryVersion by remember(item.id) { mutableStateOf(0) }
    var videoControlsVisible by remember(item.id) { mutableStateOf(true) }
    var videoControlsActivityNonce by remember(item.id) { mutableIntStateOf(0) }
    LaunchedEffect(
        item.id,
        item.type,
        videoControlsVisible,
        videoControlsActivityNonce,
        videoPlaybackState.isPlaying,
        videoPlaybackState.isLoading,
        videoPlaybackState.errorMessage,
        videoPlaybackState.isCompleted,
    ) {
        if (item.type != SystemMediaType.VIDEO || !videoControlsVisible) return@LaunchedEffect
        if (videoPlaybackState.isLoading || videoPlaybackState.errorMessage != null) return@LaunchedEffect
        kotlinx.coroutines.delay(2800)
        videoControlsVisible = false
    }
    fun revealVideoControls() {
        videoControlsVisible = true
        videoControlsActivityNonce += 1
    }
    fun toggleVideoControlsFromVideoArea() {
        videoControlsVisible = !videoControlsVisible
        videoControlsActivityNonce += 1
    }
    val transformModifier = if (zoomState != null) {
        Modifier
            .graphicsLayer(
                transformOrigin = TransformOrigin(0f, 0f),
                scaleX = zoomState.scale,
                scaleY = zoomState.scale,
                translationX = zoomState.offset.x,
                translationY = zoomState.offset.y,
            )
    } else {
        Modifier
    }
    val topPadding by animateDpAsState(
        targetValue = if (immersive) {
            SystemViewerLayoutTuning.immersiveCanvasTopPadding
        } else {
            SystemViewerLayoutTuning.canvasTopPadding
        },
        label = "systemViewerCanvasTopPadding",
    )
    val bottomPadding by animateDpAsState(
        targetValue = if (immersive) {
            if (item.type == SystemMediaType.VIDEO) {
                SystemViewerLayoutTuning.immersiveVideoBottomExitZone
            } else {
                SystemViewerLayoutTuning.immersiveCanvasBottomPadding
            }
        } else {
            SystemViewerLayoutTuning.canvasBottomPadding
        },
        label = "systemViewerCanvasBottomPadding",
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = SystemViewerLayoutTuning.canvasHorizontalPadding,
                top = topPadding,
                end = SystemViewerLayoutTuning.canvasHorizontalPadding,
                bottom = bottomPadding,
            )
            .onSizeChanged { containerSize = it },
        contentAlignment = Alignment.Center,
    ) {
        val mediaAspectRatio = item.systemViewerAspectRatio()
        val useLongImageReading = item.type == SystemMediaType.IMAGE &&
            item.shouldUseSystemLongImageReading()
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
        val canvasWidth = if (item.type == SystemMediaType.VIDEO || useLongImageReading) {
            maxWidth
        } else {
            fittedMediaWidth
        }
        val canvasHeight = if (item.type == SystemMediaType.VIDEO) {
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
        LaunchedEffect(item.id, useLongImageReading) {
            if (useLongImageReading) {
                longImageScrollState.scrollTo(0)
            }
        }
        val containerSizePx = with(density) {
            IntSize(maxWidth.roundToPx(), maxHeight.roundToPx())
        }
        val longImageMinimumScale = if (useLongImageReading &&
            contentSize.width > 0 &&
            contentSize.height > 0 &&
            containerSizePx.width > 0 &&
            containerSizePx.height > 0
        ) {
            min(
                containerSizePx.width.toFloat() / contentSize.width.toFloat(),
                containerSizePx.height.toFloat() / contentSize.height.toFloat(),
            ).coerceIn(0.05f, MinViewerScale)
        } else {
            MinViewerScale
        }
        val contentTopLeft = Offset(
            x = ((containerSizePx.width - contentSize.width) / 2f),
            y = if (useLongImageReading) {
                -longImageScrollState.value.toFloat()
            } else {
                ((containerSizePx.height - contentSize.height) / 2f)
            },
        )
        zoomState?.updateContentGeometry(
            contentSize = contentSize,
            contentTopLeft = contentTopLeft,
            minimumScale = longImageMinimumScale,
        )
        val gestureModifier = if (zoomState != null) {
            Modifier.systemViewerZoomGesture(
                zoomState = zoomState,
                contentSize = contentSize,
                contentTopLeft = contentTopLeft,
                onDoubleTap = { position, size ->
                    zoomState.toggleDoubleTap(
                        tapPosition = position,
                        containerSize = size,
                    )
                },
            )
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier),
            contentAlignment = if (useLongImageReading) Alignment.TopCenter else Alignment.Center,
        ) {
            when (item.type) {
                SystemMediaType.IMAGE -> {
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
                            AsyncImage(
                                model = item.uri,
                                contentDescription = item.displayName,
                                modifier = Modifier
                                    .width(canvasWidth)
                                    .height(canvasHeight)
                                    .then(transformModifier),
                                contentScale = ContentScale.FillWidth,
                            )
                        }
                    } else {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = item.displayName,
                            modifier = Modifier
                                .width(canvasWidth)
                                .height(canvasHeight)
                                .then(transformModifier),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }

                SystemMediaType.VIDEO -> {
                    Box(
                        modifier = Modifier
                            .width(canvasWidth)
                            .height(canvasHeight),
                    ) {
                        val revealInteractionSource = remember(item.id) { MutableInteractionSource() }
                        SystemMediaViewerVideoCanvas(
                            item = item,
                            isCurrent = isCurrent,
                            playbackState = videoPlaybackState,
                            retryVersion = videoRetryVersion,
                            onPlaybackStateChange = { videoPlaybackState = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .then(transformModifier),
                        )
                        if (videoPlaybackState.errorMessage == null) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable(
                                        interactionSource = revealInteractionSource,
                                        indication = androidx.compose.foundation.LocalIndication.current,
                                        onClick = { toggleVideoControlsFromVideoArea() },
                                    ),
                            )
                        }
                        if (videoControlsVisible) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .clickable {
                                        revealVideoControls()
                                        val durationMillis = videoPlaybackState.durationMillis ?: 0L
                                        val shouldRestart = videoPlaybackState.isCompleted ||
                                            durationMillis > 0L &&
                                            videoPlaybackState.progressMillis >= durationMillis
                                        videoPlaybackState = if (videoPlaybackState.errorMessage != null) {
                                            videoPlaybackState.retryState()
                                        } else if (videoPlaybackState.isPlaying) {
                                            videoPlaybackState.copy(isPlaying = false)
                                        } else {
                                            videoPlaybackState.copy(
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
                                shape = CircleShape,
                                color = colors.viewerSurface.copy(alpha = if (videoPlaybackState.isPlaying) 0.74f else 0.82f),
                                border = BorderStroke(1.dp, colors.viewerAccent.copy(alpha = 0.22f)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .padding(22.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    VideoGlyph(
                                        state = if (videoPlaybackState.isPlaying) VideoGlyphState.PAUSE else VideoGlyphState.PLAY,
                                        tint = colors.viewerText,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                        if (videoControlsVisible) {
                            SystemMediaVideoControls(
                            playbackState = videoPlaybackState,
                            onTogglePlayback = {
                                revealVideoControls()
                                val durationMillis = videoPlaybackState.durationMillis ?: 0L
                                val shouldRestart = videoPlaybackState.isCompleted ||
                                    durationMillis > 0L &&
                                    videoPlaybackState.progressMillis >= durationMillis
                                videoPlaybackState = if (videoPlaybackState.errorMessage != null) {
                                    videoPlaybackState.retryState()
                                } else if (videoPlaybackState.isPlaying) {
                                    videoPlaybackState.copy(isPlaying = false)
                                } else {
                                    videoPlaybackState.copy(
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
                                val durationMillis = (videoPlaybackState.durationMillis ?: 0L).coerceAtLeast(0L)
                                val targetMillis = progressMillis.coerceIn(0L, durationMillis)
                                videoPlaybackState = videoPlaybackState.copy(
                                    progressMillis = targetMillis,
                                    seekRequestMillis = targetMillis,
                                    seekRequestNonce = videoPlaybackState.seekRequestNonce + 1,
                                    errorMessage = null,
                                    isCompleted = false,
                                )
                            },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 16.dp, bottom = 18.dp, end = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SystemMediaViewerVideoCanvas(
    item: SystemMediaItem,
    isCurrent: Boolean,
    playbackState: ViewerVideoPlaybackState,
    retryVersion: Int,
    onPlaybackStateChange: (ViewerVideoPlaybackState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val videoViewRef = remember(item.id) { mutableStateOf<VideoView?>(null) }
    val videoThumbnail = rememberSystemVideoThumbnail(LocalContext.current, item.uri)
    var isPrepared by remember(item.id, retryVersion, playbackState.retryRequestNonce) { mutableStateOf(false) }

    LaunchedEffect(item.id, retryVersion) {
        onPlaybackStateChange(ViewerVideoPlaybackState(isLoading = true))
    }

    DisposableEffect(item.id, retryVersion, playbackState.retryRequestNonce) {
        onDispose {
            videoViewRef.value?.pause()
            videoViewRef.value?.stopPlayback()
            videoViewRef.value = null
        }
    }

    LaunchedEffect(playbackState.seekRequestNonce, isPrepared) {
        val targetMillis = playbackState.seekRequestMillis ?: return@LaunchedEffect
        val videoView = videoViewRef.value ?: return@LaunchedEffect
        if (isPrepared) {
            videoView.seekTo(targetMillis.toInt().coerceAtLeast(0))
        }
    }

    DisposableEffect(isCurrent) {
        if (!isCurrent) {
            videoViewRef.value?.pause()
            onPlaybackStateChange(playbackState.copy(isPlaying = false))
        }
        onDispose { }
    }

    LaunchedEffect(isCurrent, playbackState.isPlaying, playbackState.errorMessage) {
        while (isCurrent && playbackState.errorMessage == null) {
            val videoView = videoViewRef.value
            if (videoView != null && isPrepared) {
                onPlaybackStateChange(
                    playbackState.copy(
                        progressMillis = videoView.currentPosition.toLong().coerceAtLeast(0L),
                        durationMillis = videoView.duration.toLong().takeIf { it > 0 }
                            ?: playbackState.durationMillis,
                    ),
                )
            }
            kotlinx.coroutines.delay(300)
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        SystemMediaVideoPlaceholder(
            message = when {
                playbackState.errorMessage != null -> "视频加载失败"
                playbackState.isLoading -> "视频准备中"
                else -> "正在显示视频封面"
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (videoThumbnail != null) {
            Image(
                bitmap = videoThumbnail.toComposeBitmap(),
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        key(retryVersion, playbackState.retryRequestNonce) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setVideoURI(item.uri)
                    setOnPreparedListener { player ->
                        player.isLooping = false
                        isPrepared = true
                        onPlaybackStateChange(
                            playbackState.copy(
                                isLoading = false,
                                errorMessage = null,
                                isCompleted = false,
                                durationMillis = duration.toLong().takeIf { it > 0 },
                            ),
                        )
                        if (isCurrent && playbackState.isPlaying) {
                            start()
                        }
                    }
                    setOnErrorListener { _, _, _ ->
                        isPrepared = false
                        onPlaybackStateChange(
                            playbackState.copy(
                                isPlaying = false,
                                isLoading = false,
                                errorMessage = "视频加载失败，请重试",
                                isCompleted = false,
                            ),
                        )
                        true
                    }
                    setOnCompletionListener {
                        onPlaybackStateChange(
                            playbackState.copy(
                                isPlaying = false,
                                isCompleted = true,
                                progressMillis = duration.toLong().takeIf { it > 0 }
                                    ?: playbackState.progressMillis,
                            ),
                        )
                    }
                    videoViewRef.value = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { videoView ->
                videoViewRef.value = videoView
                videoView.alpha = if (playbackState.isPlaying) 1f else 0f
                if (isCurrent && playbackState.errorMessage == null) {
                    if (playbackState.isPlaying && isPrepared && !videoView.isPlaying) {
                        videoView.start()
                    } else if (!playbackState.isPlaying && videoView.isPlaying) {
                        videoView.pause()
                    }
                } else if (videoView.isPlaying) {
                    videoView.pause()
                }
            },
        )
        }

        if (playbackState.isLoading) {
            val colors = YingShiThemeTokens.colors
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp),
                color = colors.viewerAccent,
                strokeWidth = 2.dp,
            )
        }

        if (playbackState.errorMessage != null) {
            val colors = YingShiThemeTokens.colors
            Text(
                text = playbackState.errorMessage,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(
                        color = colors.viewerBackground.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.viewerText,
            )
        }
    }
}

internal fun SystemMediaItem.shouldUseSystemLongImageReading(): Boolean {
    if (type != SystemMediaType.IMAGE) return false
    val widthValue = width
    val heightValue = height
    if (widthValue != null && heightValue != null && widthValue > 0 && heightValue > 0) {
        return heightValue.toFloat() / widthValue.toFloat() >= SystemLongImageHeightWidthRatioThreshold
    }
    val normalizedAspectRatio = aspectRatio.takeIf { it > 0f } ?: return false
    return 1f / normalizedAspectRatio >= SystemLongImageHeightWidthRatioThreshold
}

internal fun SystemMediaItem.systemViewerAspectRatio(): Float {
    val widthValue = width
    val heightValue = height
    if (widthValue != null && heightValue != null && widthValue > 0 && heightValue > 0) {
        return (widthValue.toFloat() / heightValue.toFloat()).coerceIn(0.05f, 20f)
    }
    return aspectRatio.coerceIn(0.05f, 20f)
}

@Composable
internal fun SystemMediaViewerAtmosphereLayer(
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Box(modifier = modifier.background(colors.viewerBackground)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.viewerAccent.copy(alpha = 0.18f),
                            colors.viewerSurface.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                        center = Offset(0f, 0f),
                        radius = 980f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.primaryContainer.copy(alpha = 0.12f),
                            colors.viewerBackground.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                        center = Offset(1180f, 2140f),
                        radius = 920f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.viewerSurface.copy(alpha = 0.08f),
                            Color.Transparent,
                            colors.viewerBackground.copy(alpha = 0.34f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
internal fun SystemMediaVideoPlaceholder(
    message: String,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier,
        color = colors.viewerBackground,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = colors.viewerSurface.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, colors.viewerAccent.copy(alpha = 0.14f)),
                ) {
                    Box(
                        modifier = Modifier.padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        VideoGlyph(
                            state = VideoGlyphState.PLAY,
                            tint = colors.viewerText,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.viewerTextSecondary,
                )
            }
        }
    }
}
