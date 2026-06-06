package com.example.yingshi.feature.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
    cacheKey: String? = null,
): State<VideoPosterState> {
    val context = LocalContext.current
    val resolvedCacheKey = videoPosterCacheKey(url, cacheKey)
    val cachedPoster = rememberCachedVideoPosterModel(context, url, resolvedCacheKey)
    return produceState(
        initialValue = VideoPosterState(
            model = cachedPoster,
            isLoading = !url.isNullOrBlank() && cachedPoster == null,
        ),
        key1 = context,
        key2 = url,
        key3 = VideoPosterRequestKey(accessToken, resolvedCacheKey),
    ) {
        if (url.isNullOrBlank()) {
            value = VideoPosterState()
            return@produceState
        }

        rememberCachedVideoPosterModel(context, url, resolvedCacheKey)?.let { cachedPosterModel ->
            value = VideoPosterState(model = cachedPosterModel)
            return@produceState
        }

        val posterFile = ensureVideoPosterFile(
            context = context,
            url = url,
            accessToken = accessToken,
            cacheKey = resolvedCacheKey,
        )
        value = if (posterFile != null) {
            VideoPosterState(model = rememberCachedVideoPosterModel(context, url, resolvedCacheKey) ?: posterFile)
        } else {
            VideoPosterState(hasError = true)
        }
    }
}

private fun rememberCachedVideoPosterModel(
    context: Context,
    url: String?,
    cacheKey: String?,
): Any? {
    if (url.isNullOrBlank()) return null
    val resolvedCacheKey = videoPosterCacheKey(url, cacheKey) ?: return null
    videoPosterMemoryCache[resolvedCacheKey]?.let { return it }
    val file = videoPosterFile(context, resolvedCacheKey).takeIf { it.exists() && it.length() > 0L } ?: return null
    val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    if (bitmap != null) {
        videoPosterMemoryCache[resolvedCacheKey] = bitmap
        return bitmap
    }
    return file
}

internal suspend fun prefetchVideoPoster(
    context: Context,
    url: String,
    accessToken: String?,
    cacheKey: String? = null,
) {
    ensureVideoPosterFile(
        context = context,
        url = url,
        accessToken = accessToken,
        cacheKey = videoPosterCacheKey(url, cacheKey),
    )
}

private suspend fun ensureVideoPosterFile(
    context: Context,
    url: String,
    accessToken: String?,
    cacheKey: String?,
): File? = withContext(Dispatchers.IO) {
    val resolvedCacheKey = videoPosterCacheKey(url, cacheKey) ?: return@withContext null
    val targetFile = videoPosterFile(context, resolvedCacheKey)
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
    cacheKey: String,
): File {
    val cacheDirectory = context.cacheDir.resolve("video-posters")
    val key = sha256(cacheKey)
    return cacheDirectory.resolve("$key.jpg")
}

private fun videoPosterCacheKey(
    url: String?,
    cacheKey: String?,
): String? {
    if (url.isNullOrBlank()) return null
    return cacheKey?.trim()?.ifBlank { null } ?: "video-poster:${stableMediaCacheUrlKey(url)}"
}

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return buildString(digest.size * 2) {
        digest.forEach { byte -> append("%02x".format(byte)) }
    }
}

private val videoPosterLocks = ConcurrentHashMap<String, Any>()
private val videoPosterMemoryCache = ConcurrentHashMap<String, Bitmap>()

internal fun clearVideoPosterMemoryCache() {
    videoPosterMemoryCache.values.forEach { bitmap ->
        if (!bitmap.isRecycled) {
            runCatching { bitmap.recycle() }
        }
    }
    videoPosterMemoryCache.clear()
    videoPosterLocks.clear()
}

private data class VideoPosterRequestKey(
    val accessToken: String?,
    val cacheKey: String?,
)
