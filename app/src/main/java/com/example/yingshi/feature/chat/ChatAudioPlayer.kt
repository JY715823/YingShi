package com.example.yingshi.feature.chat

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.yingshi.feature.chat.data.ImportedResource
import com.example.yingshi.feature.chat.data.isDirectlyPlayableAudio
import com.example.yingshi.feature.chat.data.isSilkAudio
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.xxin.silkdecoder.SilkDecoder

// ── Audio phase ──

internal enum class ChatAudioPhase {
    IDLE,
    DECODING,
    PREPARING,
    PLAYING,
    ERROR,
}

// ── Resolved source ──

internal data class ResolvedAudioSource(
    val path: String,
    val mimeType: String?,
)

// ── Player state ──

internal class ChatAudioPlayerState(context: Context) {
    private val appContext = context.applicationContext
    private val player = ExoPlayer.Builder(appContext).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val decodeCacheDir = appContext.cacheDir.resolve("chat-audio-cache").apply { mkdirs() }
    private var prepareJob: Job? = null
    private var currentPath: String? by mutableStateOf(null)
    private var phase: ChatAudioPhase by mutableStateOf(ChatAudioPhase.IDLE)
    private var failedPath: String? by mutableStateOf(null)

    init {
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    phase = when (playbackState) {
                        Player.STATE_BUFFERING -> ChatAudioPhase.PREPARING
                        Player.STATE_READY -> if (player.isPlaying) ChatAudioPhase.PLAYING else phase
                        Player.STATE_ENDED -> ChatAudioPhase.IDLE
                        else -> if (player.isPlaying) ChatAudioPhase.PLAYING else phase
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        stop()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    phase = if (isPlaying) ChatAudioPhase.PLAYING else if (currentPath == null) ChatAudioPhase.IDLE else phase
                }

                override fun onPlayerError(error: PlaybackException) {
                    failedPath = currentPath
                    phase = ChatAudioPhase.ERROR
                }
            },
        )
    }

    fun isPlaying(path: String): Boolean = phase == ChatAudioPhase.PLAYING && currentPath == path

    fun isPreparing(path: String): Boolean = phase == ChatAudioPhase.PREPARING && currentPath == path

    fun isDecoding(path: String): Boolean = phase == ChatAudioPhase.DECODING && currentPath == path

    fun hasError(path: String): Boolean = failedPath == path

    fun toggle(resource: ImportedResource) {
        val path = resource.localFilePath
        if (currentPath == path && phase in setOf(ChatAudioPhase.DECODING, ChatAudioPhase.PREPARING, ChatAudioPhase.PLAYING)) {
            stop()
            return
        }
        prepareJob?.cancel()
        player.stop()
        player.clearMediaItems()
        failedPath = null
        currentPath = path
        phase = if (resource.isSilkAudio()) ChatAudioPhase.DECODING else ChatAudioPhase.PREPARING
        prepareJob = scope.launch {
            val resolved = runCatching {
                resolvePlaybackSource(resource)
            }.getOrNull()
            if (currentPath != path) return@launch
            if (resolved == null) {
                failedPath = path
                phase = ChatAudioPhase.ERROR
                return@launch
            }
            phase = ChatAudioPhase.PREPARING
            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(Uri.fromFile(File(resolved.path)))
                    .setMimeType(resolved.mimeType)
                    .build(),
            )
            player.prepare()
            player.playWhenReady = true
        }
    }

    fun release() {
        prepareJob?.cancel()
        scope.cancel()
        player.release()
    }

    private suspend fun resolvePlaybackSource(resource: ImportedResource): ResolvedAudioSource? {
        return when {
            resource.isSilkAudio() -> {
                val wavPath = decodeSilkToWav(resource) ?: return null
                ResolvedAudioSource(path = wavPath, mimeType = "audio/wav")
            }
            resource.isDirectlyPlayableAudio() -> {
                ResolvedAudioSource(
                    path = resource.localFilePath,
                    mimeType = resource.resolvedMimeType,
                )
            }
            else -> null
        }
    }

    private suspend fun decodeSilkToWav(resource: ImportedResource): String? {
        return withContext(Dispatchers.IO) {
            val stableName = resource.md5?.ifBlank { null }
                ?: buildString {
                    append(resource.resourceLocalId)
                    append('_')
                    append(resource.localFilePath.hashCode().toString().replace('-', 'n'))
                }
            val outputFile = decodeCacheDir.resolve("$stableName.wav")
            if (outputFile.exists() && outputFile.length() > 44L) {
                return@withContext outputFile.absolutePath
            }
            runCatching {
                outputFile.parentFile?.mkdirs()
                if (outputFile.exists()) {
                    outputFile.delete()
                }
                SilkDecoder.decodeToWav(resource.localFilePath, outputFile.absolutePath)
            }.getOrElse { false }.takeIf { it && outputFile.exists() && outputFile.length() > 44L }
                ?.let {
                    outputFile.absolutePath
                }
        }
    }

    private fun stop() {
        prepareJob?.cancel()
        prepareJob = null
        player.stop()
        player.clearMediaItems()
        currentPath = null
        phase = ChatAudioPhase.IDLE
    }
}

// ── Composable entry ──

@Composable
internal fun rememberChatAudioPlayer(): ChatAudioPlayerState {
    val context = LocalContext.current
    val player = remember { ChatAudioPlayerState(context) }
    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }
    return player
}
