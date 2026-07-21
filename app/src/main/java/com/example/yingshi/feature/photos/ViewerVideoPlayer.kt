package com.example.yingshi.feature.photos

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlin.math.abs

internal const val DefaultViewerVideoDurationMillis = 18_000L

@Composable
private fun ViewerVideoPosterFallback(
    message: String?,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing

    Box(
        modifier = modifier.background(ViewerNightBottom),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
            color = ViewerNightTop.copy(alpha = 0.68f),
            border = BorderStroke(1.dp, ViewerSurface.copy(alpha = 0.08f)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = CircleShape,
                    color = ViewerSurface.copy(alpha = 0.10f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        VideoGlyph(
                            state = VideoGlyphState.PLAY,
                            tint = ViewerSurface.copy(alpha = 0.88f),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (!message.isNullOrBlank()) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelLarge,
                        color = ViewerSurface.copy(alpha = 0.82f),
                    )
                }
            }
        }
    }
}

private enum class VideoUiState { PLAYING, LOADING, ERROR, COMPLETED }

@Composable
internal fun ViewerVideoErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.viewerSurface.copy(alpha = 0.54f))
                .border(
                    BorderStroke(1.dp, colors.viewerOverlayBorder),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            VideoGlyph(
                state = VideoGlyphState.ERROR,
                tint = colors.viewerAccent,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(modifier = Modifier.height(spacing.md))

        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.viewerText.copy(alpha = 0.90f),
            textAlign = TextAlign.Center,
            maxLines = 2,
        )

        Spacer(modifier = Modifier.height(spacing.lg))

        Surface(
            shape = RoundedCornerShape(radius.capsule),
            color = colors.viewerAccent.copy(alpha = 0.18f),
            border = BorderStroke(1.dp, colors.viewerOverlayBorder),
            modifier = Modifier.yingShiClickable(
                pressedScale = 0.94f,
                onClick = onRetry,
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = colors.viewerAccent,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "重试",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.viewerAccent,
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
internal fun ViewerVideoCanvas(
    media: PhotoFeedItem,
    playbackState: ViewerVideoPlaybackState?,
    isCurrent: Boolean,
    autoPauseOnMediaSwitch: Boolean = true,
    originalLoadState: OriginalLoadState,
    onPlaybackStateChange: (String, ViewerVideoPlaybackState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val motion = YingShiThemeTokens.motion
    val context = LocalContext.current
    val videoUrl = remember(media.mediaSource, media.mediaType) {
        media.mediaSource.viewerVideoUrl(media.mediaType)
    }
    val videoCacheKey = remember(media.mediaSource, media.mediaType) {
        media.mediaSource.viewerVideoCacheKey(media.mediaType)
    }
    val isPlaying = playbackState?.isPlaying == true
    val isLoading = playbackState?.isLoading == true
    val errorMessage = playbackState?.errorMessage
    val sessionVersion = AuthSessionManager.sessionVersion
    val accessToken = remember(sessionVersion) {
        AuthSessionManager.peekAccessToken()?.takeIf { it.isNotBlank() }
    }
    val posterImageUrl = remember(media.mediaSource, media.mediaType) {
        media.mediaSource.videoPosterImageUrl(media.mediaType)
    }
    val posterImageCacheKey = remember(media.mediaSource, media.mediaType) {
        media.mediaSource.videoPosterImageCacheKey(media.mediaType)
    }
    val posterImageDiskCacheKey = remember(media.mediaSource, media.mediaType) {
        media.mediaSource.videoPosterImageDiskCacheKey(media.mediaType)
    }
    val posterImageRequest = remember(context, posterImageUrl, posterImageCacheKey, posterImageDiskCacheKey, accessToken) {
        backendMediaImageRequest(
            context = context,
            url = posterImageUrl,
            accessToken = accessToken,
            memoryCacheKey = posterImageCacheKey ?: posterImageUrl?.let(::sharedPreviewMemoryCacheKey),
            diskCacheKey = posterImageDiskCacheKey,
            size = 1280,
        )
    }
    val posterImagePainter = rememberAsyncImagePainter(model = posterImageRequest)
    val posterImageState = posterImagePainter.state
    val fallbackPosterVideoUrl = if (posterImageUrl.isNullOrBlank() ||
        posterImageState is AsyncImagePainter.State.Error
    ) {
        videoUrl?.takeIf(::canExtractViewerPosterOnClient)
    } else {
        null
    }
    val videoPosterState = rememberVideoPosterState(
        url = fallbackPosterVideoUrl,
        accessToken = accessToken,
        cacheKey = videoCacheKey,
        diskCacheKey = media.mediaSource.viewerVideoDiskCacheKey(media.mediaType),
    ).value
    val extractedPosterPainter = rememberAsyncImagePainter(model = videoPosterState.model)
    val requestHeaders = remember(videoUrl, accessToken) {
        backendMediaRequestHeaders(videoUrl, accessToken)
    }
    var retryVersion by remember(media.mediaId) { mutableStateOf(0) }
    val retryRequestNonce = playbackState?.retryRequestNonce ?: 0
    var isPrepared by remember(media.mediaId, retryVersion, retryRequestNonce) { mutableStateOf(false) }
    var pendingSeekTargetMillis by remember(media.mediaId, retryVersion, retryRequestNonce) {
        mutableStateOf<Long?>(null)
    }
    val initialPositionMillis = playbackState?.progressMillis?.coerceAtLeast(0L) ?: 0L
    val shouldPreparePlayer = isCurrent &&
        !videoUrl.isNullOrBlank() &&
        (isPlaying || initialPositionMillis > 0L || retryRequestNonce > 0 || (playbackState?.seekRequestNonce ?: 0) > 0)
    val player = remember(media.mediaId, videoUrl, videoCacheKey, requestHeaders, retryVersion, retryRequestNonce, shouldPreparePlayer) {
        if (videoUrl.isNullOrBlank() || !shouldPreparePlayer) {
            null
        } else {
            ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(
                    Media3AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true,
                )
                setMediaSource(
                    ProgressiveMediaSource.Factory(
                        AppMediaVideoCache.dataSourceFactory(
                            context = context,
                            requestHeaders = requestHeaders,
                            connectTimeoutMs = 8_000,
                            readTimeoutMs = 8_000,
                        ),
                    ).createMediaSource(
                        MediaItem.Builder()
                            .setUri(videoUrl)
                            .setCustomCacheKey(videoCacheKey ?: sharedVideoDiskCacheKey(videoUrl))
                            .build(),
                    ),
                )
                if (initialPositionMillis > 0L) {
                    seekTo(initialPositionMillis)
                }
                prepare()
            }
        }
    }

    fun updatePlaybackState(transform: (ViewerVideoPlaybackState) -> ViewerVideoPlaybackState) {
        val current = playbackState ?: ViewerVideoPlaybackState(mediaId = media.mediaId)
        onPlaybackStateChange(
            media.mediaId,
            transform(current.copy(mediaId = media.mediaId)),
        )
    }

    LaunchedEffect(media.mediaId, videoUrl, autoPauseOnMediaSwitch) {
        if (!autoPauseOnMediaSwitch && playbackState?.mediaId == media.mediaId) {
            return@LaunchedEffect
        }
        if (videoUrl.isNullOrBlank()) {
            onPlaybackStateChange(
                media.mediaId,
                ViewerVideoPlaybackState(
                    mediaId = media.mediaId,
                    errorMessage = "视频 URL 为空",
                ),
            )
        } else {
            val currentProgressMillis = playbackState?.progressMillis?.coerceAtLeast(0L) ?: 0L
            onPlaybackStateChange(
                media.mediaId,
                ViewerVideoPlaybackState(
                    mediaId = media.mediaId,
                    isLoading = playbackState?.isPlaying == true,
                    progressMillis = currentProgressMillis,
                    durationMillis = media.viewerVideoDurationMillis(),
                    seekRequestMillis = playbackState?.seekRequestMillis,
                    seekRequestNonce = playbackState?.seekRequestNonce ?: 0,
                    pendingSeekTargetMillis = playbackState?.pendingSeekTargetMillis,
                ),
            )
        }
    }

    DisposableEffect(player) {
        if (player == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackStateValue: Int) {
                    when (playbackStateValue) {
                        Player.STATE_BUFFERING -> {
                            updatePlaybackState {
                                it.copy(
                                    isLoading = true,
                                    errorMessage = null,
                                )
                            }
                        }

                        Player.STATE_READY -> {
                            isPrepared = true
                            updatePlaybackState {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = null,
                                    isCompleted = false,
                                    durationMillis = player.viewerDurationMillis() ?: it.durationMillis,
                                )
                            }
                        }

                        Player.STATE_ENDED -> {
                            updatePlaybackState {
                                it.copy(
                                    isPlaying = false,
                                    isLoading = false,
                                    isCompleted = true,
                                    progressMillis = player.viewerDurationMillis() ?: it.progressMillis,
                                    durationMillis = player.viewerDurationMillis() ?: it.durationMillis,
                                )
                            }
                        }

                        else -> Unit
                    }
                }

                override fun onIsPlayingChanged(isPlayingValue: Boolean) {
                    updatePlaybackState {
                        it.copy(
                            isLoading = player.playbackState == Player.STATE_BUFFERING,
                            durationMillis = player.viewerDurationMillis() ?: it.durationMillis,
                        )
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    isPrepared = false
                    updatePlaybackState {
                        it.copy(
                            isPlaying = false,
                            isLoading = false,
                            errorMessage = "视频加载失败，请重试",
                            isCompleted = false,
                        )
                    }
                }
            }
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
                player.release()
            }
        }
    }

    DisposableEffect(isCurrent) {
        if (!isCurrent) {
            player?.pause()
            if (autoPauseOnMediaSwitch) {
                updatePlaybackState { it.copy(isPlaying = false) }
            }
        }
        onDispose {
        }
    }

    LaunchedEffect(isCurrent, isPlaying, errorMessage, videoUrl, retryVersion) {
        if (player == null) return@LaunchedEffect
        player.playWhenReady = isCurrent && isPlaying && errorMessage == null
        if (isCurrent && isPlaying && errorMessage == null) {
            player.play()
        } else {
            player.pause()
        }
        while (isCurrent && videoUrl != null && errorMessage == null) {
            if (isPrepared) {
                val rawProgressMillis = player.currentPosition.coerceAtLeast(0L)
                val pendingSeekMillis = pendingSeekTargetMillis
                val displayedProgressMillis = if (pendingSeekMillis != null) {
                    val distance = rawProgressMillis - pendingSeekMillis
                    if (distance in -500L..500L) {
                        pendingSeekTargetMillis = null
                        rawProgressMillis
                    } else {
                        pendingSeekMillis
                    }
                } else {
                    rawProgressMillis
                }
                updatePlaybackState {
                    it.copy(
                        progressMillis = displayedProgressMillis,
                        durationMillis = player.viewerDurationMillis() ?: it.durationMillis,
                        isLoading = player.playbackState == Player.STATE_BUFFERING,
                        pendingSeekTargetMillis = pendingSeekTargetMillis,
                    )
                }
            }
            kotlinx.coroutines.delay(300)
        }
    }

    LaunchedEffect(playbackState?.seekRequestNonce, player) {
        val targetMillis = playbackState?.seekRequestMillis ?: return@LaunchedEffect
        val normalizedTargetMillis = targetMillis.coerceAtLeast(0L)
        pendingSeekTargetMillis = normalizedTargetMillis
        player?.seekTo(normalizedTargetMillis)
    }
    val hasServerPosterImage = posterImageRequest != null &&
        posterImageState is AsyncImagePainter.State.Success
    val hasExtractedPosterImage = videoPosterState.model != null &&
        extractedPosterPainter.state !is AsyncImagePainter.State.Error
    val posterPainter = if (hasServerPosterImage) {
        posterImagePainter
    } else {
        extractedPosterPainter
    }
    val hasPosterImage = hasServerPosterImage || hasExtractedPosterImage
    val hasPendingSeek = playbackState?.pendingSeekTargetMillis != null ||
        (playbackState?.seekRequestMillis != null && playbackState.seekRequestNonce > 0 && isLoading)
    val shouldShowPoster = hasPosterImage &&
        (!isPrepared || hasPendingSeek || (playbackState?.progressMillis ?: 0L) <= 0L || errorMessage != null)

    Box(
        modifier = modifier
            .background(ViewerNightBottom),
    ) {
        if (!videoUrl.isNullOrBlank() && player != null) {
            key(retryVersion, retryRequestNonce) {
                AndroidView(
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            this.player = player
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { playerView ->
                        playerView.player = player
                        if (isCurrent && errorMessage == null && isPlaying) {
                            player.play()
                        } else {
                            player.pause()
                        }
                    },
                )
            }
        }

        if (shouldShowPoster) {
            Image(
                painter = posterPainter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else if (!isPrepared) {
            ViewerVideoPosterFallback(
                message = when {
                    videoUrl.isNullOrBlank() -> "暂无视频地址"
                    errorMessage != null -> "视频加载失败"
                    posterImageState is AsyncImagePainter.State.Loading || videoPosterState.isLoading || isLoading -> "视频准备中"
                    else -> null
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (posterImageState is AsyncImagePainter.State.Loading || videoPosterState.isLoading || isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp),
                color = ViewerSurface.copy(alpha = 0.88f),
                strokeWidth = 2.dp,
            )
        }

        val videoUiState = when {
            errorMessage != null && !videoUrl.isNullOrBlank() -> VideoUiState.ERROR
            !isPrepared || isLoading -> VideoUiState.LOADING
            playbackState?.isPlaying == true -> VideoUiState.PLAYING
            playbackState?.isCompleted == true -> VideoUiState.COMPLETED
            else -> VideoUiState.LOADING
        }
        AnimatedContent(
            targetState = videoUiState,
            transitionSpec = {
                fadeIn(tween(motion.stateMillis, easing = motion.easing)) togetherWith
                    fadeOut(tween(motion.stateMillis, easing = motion.easing))
            },
            label = "videoState",
        ) { state ->
            if (state == VideoUiState.ERROR) {
                ViewerVideoErrorState(
                    errorMessage = errorMessage ?: "视频加载失败",
                    onRetry = {
                        retryVersion += 1
                        isPrepared = false
                        updatePlaybackState { it.retryState() }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private fun canExtractViewerPosterOnClient(url: String): Boolean {
    val normalized = url.trim().lowercase()
    return normalized.startsWith("content://") ||
        normalized.startsWith("file://") ||
        normalized.startsWith("http://") ||
        normalized.startsWith("https://") ||
        normalized.startsWith("/")
}

@Composable
internal fun ViewerVideoControls(
    playbackState: ViewerVideoPlaybackState,
    durationMillis: Long,
    onTogglePlayback: () -> Unit,
    onSeekPlayback: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val safeDurationMillis = durationMillis.coerceAtLeast(0L)
    var draggedFraction by remember(playbackState.mediaId) { mutableStateOf<Float?>(null) }
    var releasedSeekMillis by remember(playbackState.mediaId) { mutableStateOf<Long?>(null) }
    val displayedProgressMillis = when {
        safeDurationMillis <= 0L -> 0L
        draggedFraction != null -> (draggedFraction!! * safeDurationMillis)
            .toLong()
            .coerceIn(0L, safeDurationMillis)
        releasedSeekMillis != null -> releasedSeekMillis!!.coerceIn(0L, safeDurationMillis)
        playbackState.pendingSeekTargetMillis != null -> playbackState.pendingSeekTargetMillis.coerceIn(0L, safeDurationMillis)
        else -> playbackState.progressMillis.coerceIn(0L, safeDurationMillis)
    }
    val displayedFraction = if (safeDurationMillis <= 0L) {
        0f
    } else {
        (displayedProgressMillis.toFloat() / safeDurationMillis.toFloat()).coerceIn(0f, 1f)
    }

    LaunchedEffect(
        releasedSeekMillis,
        playbackState.progressMillis,
        playbackState.pendingSeekTargetMillis,
        playbackState.isLoading,
    ) {
        val targetMillis = releasedSeekMillis ?: return@LaunchedEffect
        if (playbackState.pendingSeekTargetMillis == null &&
            !playbackState.isLoading &&
            abs(playbackState.progressMillis - targetMillis) <= 650L
        ) {
            releasedSeekMillis = null
        }
    }

    Surface(
        modifier = modifier.widthIn(min = 240.dp, max = 420.dp),
        shape = RoundedCornerShape(radius.xl),
        color = ViewerNightTop.copy(alpha = 0.76f),
        border = BorderStroke(1.dp, ViewerSurface.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(onClick = onTogglePlayback),
                    shape = CircleShape,
                    color = ViewerSurface.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, ViewerSurface.copy(alpha = 0.10f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        VideoGlyph(
                            state = if (playbackState.isPlaying) {
                                VideoGlyphState.PAUSE
                            } else {
                                VideoGlyphState.PLAY
                            },
                            tint = ViewerSurface.copy(alpha = 0.92f),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    Text(
                        text = "${formatVideoProgress(displayedProgressMillis)} / ${formatVideoProgress(safeDurationMillis)} · 剩余 ${formatVideoProgress((safeDurationMillis - displayedProgressMillis).coerceAtLeast(0L))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = ViewerSurface.copy(alpha = 0.68f),
                    )
                }
            }

            Slider(
                value = displayedFraction,
                onValueChange = { draggedFraction = it.coerceIn(0f, 1f) },
                onValueChangeFinished = {
                    val targetFraction = draggedFraction ?: displayedFraction
                    val targetMillis = if (safeDurationMillis <= 0L) {
                        0L
                    } else {
                        (targetFraction * safeDurationMillis).toLong().coerceIn(0L, safeDurationMillis)
                    }
                    releasedSeekMillis = targetMillis
                    draggedFraction = null
                    onSeekPlayback(targetMillis)
                },
                enabled = safeDurationMillis > 0L && playbackState.errorMessage == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
            )
        }
    }
}

internal fun PhotoFeedItem.viewerVideoDurationMillis(): Long {
    return videoDurationMillis ?: DefaultViewerVideoDurationMillis
}

private fun ExoPlayer.viewerDurationMillis(): Long? {
    return duration.takeIf { it != C.TIME_UNSET && it > 0L }
}

private fun formatVideoProgress(timeMillis: Long): String {
    val totalSeconds = (timeMillis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}
