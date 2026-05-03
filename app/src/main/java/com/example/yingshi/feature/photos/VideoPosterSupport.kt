package com.example.yingshi.feature.photos

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class VideoPosterState(
    val model: Any? = null,
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
)

@Composable
internal fun rememberVideoPosterState(
    url: String?,
    accessToken: String?,
): State<VideoPosterState> {
    val context = LocalContext.current
    return produceState(
        initialValue = VideoPosterState(
            isLoading = !url.isNullOrBlank(),
        ),
        key1 = context,
        key2 = url,
        key3 = accessToken,
    ) {
        if (url.isNullOrBlank()) {
            value = VideoPosterState()
            return@produceState
        }

        val posterFile = ensureVideoPosterFile(
            context = context,
            url = url,
            accessToken = accessToken,
        )
        value = if (posterFile != null) {
            VideoPosterState(model = posterFile)
        } else {
            VideoPosterState(hasError = true)
        }
    }
}

internal suspend fun prefetchVideoPoster(
    context: Context,
    url: String,
    accessToken: String?,
) {
    ensureVideoPosterFile(
        context = context,
        url = url,
        accessToken = accessToken,
    )
}

private suspend fun ensureVideoPosterFile(
    context: Context,
    url: String,
    accessToken: String?,
): File? = withContext(Dispatchers.IO) {
    val targetFile = videoPosterFile(context, url)
    if (targetFile.exists() && targetFile.length() > 0L) {
        return@withContext targetFile
    }

    val lock = videoPosterLocks.getOrPut(targetFile.absolutePath) { Any() }
    synchronized(lock) {
        if (targetFile.exists() && targetFile.length() > 0L) {
            return@synchronized targetFile
        }

        targetFile.parentFile?.mkdirs()
        val posterBitmap = extractVideoPosterBitmap(url, accessToken) ?: return@synchronized null
        return@synchronized runCatching {
            FileOutputStream(targetFile).use { outputStream ->
                posterBitmap.compress(Bitmap.CompressFormat.JPEG, 88, outputStream)
            }
            posterBitmap.recycle()
            targetFile.takeIf { it.exists() && it.length() > 0L }
        }.getOrNull()
    }
}

private fun extractVideoPosterBitmap(
    url: String,
    accessToken: String?,
): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        val headers = linkedMapOf<String, String>().apply {
            putAll(backendMediaRequestHeaders(url, accessToken))
        }
        retriever.setDataSource(url, headers)
        retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.frameAtTime
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun videoPosterFile(
    context: Context,
    url: String,
): File {
    val cacheDirectory = context.cacheDir.resolve("video-posters")
    val key = sha256(url)
    return cacheDirectory.resolve("$key.jpg")
}

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return buildString(digest.size * 2) {
        digest.forEach { byte -> append("%02x".format(byte)) }
    }
}

private val videoPosterLocks = ConcurrentHashMap<String, Any>()
