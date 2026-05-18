package com.example.yingshi.feature.photos

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.view.TextureView
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.yingshi.data.remote.auth.AuthSessionManager
import kotlinx.coroutines.delay
import kotlin.math.max

internal data class InlineVideoPlaybackProgress(
    val positionMillis: Long = 0L,
    val durationMillis: Long? = null,
)

@Composable
internal fun AppContentInlineVideoPlayer(
    mediaSource: AppContentMediaSource?,
    mediaType: AppMediaType,
    playWhenReady: Boolean,
    modifier: Modifier = Modifier,
    onPlaybackProgressChange: (InlineVideoPlaybackProgress) -> Unit = {},
) {
    val videoUrl = remember(mediaSource, mediaType) {
        mediaSource.viewerVideoUrl(mediaType)
    }
    if (videoUrl.isNullOrBlank()) return
    val sessionVersion = AuthSessionManager.sessionVersion
    val accessToken = remember(sessionVersion) {
        AuthSessionManager.getAccessToken()?.takeIf { it.isNotBlank() }
    }
    val requestHeaders = remember(videoUrl, accessToken) {
        backendMediaRequestHeaders(videoUrl, accessToken)
    }

    InlineMutedRemoteVideoPlayer(
        videoUrl = videoUrl,
        requestHeaders = requestHeaders,
        playWhenReady = playWhenReady,
        modifier = modifier,
        onPlaybackProgressChange = onPlaybackProgressChange,
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun InlineMutedRemoteVideoPlayer(
    videoUrl: String,
    requestHeaders: Map<String, String>,
    playWhenReady: Boolean,
    modifier: Modifier = Modifier,
    onPlaybackProgressChange: (InlineVideoPlaybackProgress) -> Unit,
) {
    val context = LocalContext.current
    val textureViewRef = remember(videoUrl, requestHeaders) { mutableStateOf<TextureView?>(null) }
    var hasRenderedFirstFrame by remember(videoUrl, requestHeaders) { mutableStateOf(false) }
    var videoSize by remember(videoUrl, requestHeaders) { mutableStateOf(VideoSize.UNKNOWN) }
    val player = remember(videoUrl, requestHeaders) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            setAudioAttributes(
                Media3AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                false,
            )
            setMediaSource(
                ProgressiveMediaSource.Factory(
                    AppMediaVideoCache.dataSourceFactory(
                        context = context,
                        requestHeaders = requestHeaders,
                        connectTimeoutMs = 8_000,
                        readTimeoutMs = 8_000,
                    ),
                ).createMediaSource(MediaItem.fromUri(videoUrl)),
            )
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                onPlaybackProgressChange(player.toInlineVideoPlaybackProgress())
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onPlaybackProgressChange(player.toInlineVideoPlaybackProgress())
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = size
                textureViewRef.value?.applyCenterCropTransform(size.width, size.height)
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
                textureViewRef.value?.alpha = 1f
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            textureViewRef.value?.let { textureView ->
                player.clearVideoTextureView(textureView)
            }
            player.release()
        }
    }

    LaunchedEffect(player, playWhenReady) {
        if (playWhenReady) {
            player.playWhenReady = true
            player.play()
        } else {
            player.playWhenReady = false
            player.pause()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            onPlaybackProgressChange(player.toInlineVideoPlaybackProgress())
            delay(500)
        }
    }

    AndroidView(
        factory = { viewContext ->
            TextureView(viewContext).apply {
                isClickable = false
                isFocusable = false
                alpha = 0f
                textureViewRef.value = this
                player.setVideoTextureView(this)
                addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                    (view as? TextureView)?.applyCenterCropTransform(videoSize.width, videoSize.height)
                }
            }
        },
        modifier = modifier,
        update = { textureView ->
            textureViewRef.value = textureView
            textureView.alpha = if (hasRenderedFirstFrame) 1f else 0f
            player.setVideoTextureView(textureView)
            textureView.applyCenterCropTransform(videoSize.width, videoSize.height)
        },
    )
}

@Composable
internal fun SystemMediaInlineVideoPlayer(
    item: SystemMediaItem,
    playWhenReady: Boolean,
    modifier: Modifier = Modifier,
    onPlaybackProgressChange: (InlineVideoPlaybackProgress) -> Unit = {},
) {
    InlineMutedVideoPlayer(
        videoUri = item.uri,
        requestHeaders = emptyMap(),
        playWhenReady = playWhenReady,
        modifier = modifier,
        onPlaybackProgressChange = onPlaybackProgressChange,
    )
}

@Composable
private fun InlineMutedVideoPlayer(
    videoUri: Uri,
    requestHeaders: Map<String, String>,
    playWhenReady: Boolean,
    modifier: Modifier = Modifier,
    onPlaybackProgressChange: (InlineVideoPlaybackProgress) -> Unit,
) {
    val context = LocalContext.current
    val playWhenReadyState by rememberUpdatedState(playWhenReady)
    val textureViewRef = remember(videoUri) { mutableStateOf<TextureView?>(null) }
    val mediaPlayerRef = remember(videoUri) { mutableStateOf<MediaPlayer?>(null) }
    val surfaceRef = remember(videoUri) { mutableStateOf<Surface?>(null) }
    var isPrepared by remember(videoUri) { mutableStateOf(false) }
    var hasRenderedFirstFrame by remember(videoUri) { mutableStateOf(false) }

    fun releasePlayer() {
        mediaPlayerRef.value?.runCatching {
            if (isPlaying) pause()
            stop()
        }
        mediaPlayerRef.value?.release()
        mediaPlayerRef.value = null
        surfaceRef.value?.release()
        surfaceRef.value = null
        isPrepared = false
        hasRenderedFirstFrame = false
    }

    fun preparePlayer(context: Context, textureView: TextureView, surfaceTexture: SurfaceTexture) {
        releasePlayer()
        val surface = Surface(surfaceTexture)
        surfaceRef.value = surface
        val player = MediaPlayer()
        mediaPlayerRef.value = player
        runCatching {
            player.setSurface(surface)
            player.isLooping = true
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            player.setVolume(0f, 0f)
            val scheme = videoUri.scheme.orEmpty()
            if (scheme.startsWith("http", ignoreCase = true) && requestHeaders.isEmpty()) {
                player.setDataSource(videoUri.toString())
            } else if (requestHeaders.isEmpty()) {
                player.setDataSource(context, videoUri)
            } else {
                player.setDataSource(context, videoUri, requestHeaders)
            }
            player.setOnPreparedListener { preparedPlayer ->
                isPrepared = true
                textureView.applyCenterCropTransform(
                    videoWidth = preparedPlayer.videoWidth,
                    videoHeight = preparedPlayer.videoHeight,
                )
                onPlaybackProgressChange(preparedPlayer.toInlineVideoPlaybackProgress())
                if (playWhenReadyState) {
                    preparedPlayer.start()
                }
            }
            player.setOnVideoSizeChangedListener { _, width, height ->
                textureView.applyCenterCropTransform(width, height)
            }
            player.setOnErrorListener { _, _, _ ->
                isPrepared = false
                true
            }
            player.prepareAsync()
        }.onFailure {
            releasePlayer()
        }
    }

    DisposableEffect(videoUri) {
        onDispose {
            releasePlayer()
            textureViewRef.value = null
        }
    }

    LaunchedEffect(playWhenReady, isPrepared) {
        val player = mediaPlayerRef.value ?: return@LaunchedEffect
        if (playWhenReady && isPrepared) {
            if (!player.isPlaying) player.start()
        } else if (player.isPlaying) {
            player.pause()
        }
    }

    LaunchedEffect(videoUri, isPrepared) {
        while (isPrepared) {
            mediaPlayerRef.value?.let { player ->
                onPlaybackProgressChange(player.toInlineVideoPlaybackProgress())
            }
            delay(500)
        }
    }

    AndroidView(
        factory = { context ->
            TextureView(context).apply {
                isClickable = false
                isFocusable = false
                alpha = 0f
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        textureViewRef.value = this@apply
                        preparePlayer(context, this@apply, surface)
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                        mediaPlayerRef.value?.let { player ->
                            applyCenterCropTransform(player.videoWidth, player.videoHeight)
                        }
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        releasePlayer()
                        textureViewRef.value = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                        if (!hasRenderedFirstFrame) {
                            hasRenderedFirstFrame = true
                            this@apply.alpha = 1f
                        }
                    }
                }
            }
        },
        modifier = modifier,
        update = { textureView ->
            textureViewRef.value = textureView
            textureView.alpha = if (hasRenderedFirstFrame) 1f else 0f
            val surfaceTexture = textureView.surfaceTexture
            if (textureView.isAvailable && surfaceTexture != null && mediaPlayerRef.value == null) {
                preparePlayer(context, textureView, surfaceTexture)
            }
            mediaPlayerRef.value?.let { player ->
                textureView.applyCenterCropTransform(player.videoWidth, player.videoHeight)
                if (playWhenReady && isPrepared) {
                    if (!player.isPlaying) player.start()
                } else if (player.isPlaying) {
                    player.pause()
                }
            }
        },
    )
}

private fun MediaPlayer.toInlineVideoPlaybackProgress(): InlineVideoPlaybackProgress {
    return InlineVideoPlaybackProgress(
        positionMillis = runCatching { currentPosition.toLong().coerceAtLeast(0L) }.getOrDefault(0L),
        durationMillis = runCatching { duration.toLong().takeIf { it > 0L } }.getOrNull(),
    )
}

private fun ExoPlayer.toInlineVideoPlaybackProgress(): InlineVideoPlaybackProgress {
    return InlineVideoPlaybackProgress(
        positionMillis = currentPosition.coerceAtLeast(0L),
        durationMillis = duration.takeIf { it != C.TIME_UNSET && it > 0L },
    )
}

private fun TextureView.applyCenterCropTransform(
    videoWidth: Int,
    videoHeight: Int,
) {
    val viewWidth = width.toFloat().takeIf { it > 0f } ?: return
    val viewHeight = height.toFloat().takeIf { it > 0f } ?: return
    if (videoWidth <= 0 || videoHeight <= 0) return

    val scale = max(viewWidth / videoWidth.toFloat(), viewHeight / videoHeight.toFloat())
    val scaleX = videoWidth * scale / viewWidth
    val scaleY = videoHeight * scale / viewHeight
    setTransform(
        Matrix().apply {
            setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
        },
    )
}
