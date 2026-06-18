package com.example.yingshi.feature.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SystemMediaPreviewFrameTimesUs = listOf(
    500_000L,
    1_000_000L,
    2_000_000L,
    5_000_000L,
)
private val SystemMediaThumbnailSize = Size(640, 640)
private const val SystemMediaOverviewThumbnailMaxEdge = 160
private const val SystemMediaOverviewThumbnailCacheMaxBytes = 24 * 1024 * 1024
private const val SystemMediaImageThumbnailCacheMaxBytes = 64 * 1024 * 1024

@Composable
internal fun rememberSystemMediaThumbnail(
    context: Context,
    uri: Uri,
    targetSizePx: Int,
): Bitmap? {
    val appContext = remember(context) { context.applicationContext }
    val cacheKey = remember(uri) { uri.toString() }
    val requestedSize = targetSizePx.coerceAtLeast(64)
    return produceState<Bitmap?>(
        initialValue = SystemMediaBitmapThumbnailCache.getAny(cacheKey),
        key1 = appContext,
        key2 = uri,
        key3 = requestedSize,
    ) {
        val cached = SystemMediaBitmapThumbnailCache.get(cacheKey, requestedSize)
        if (cached != null) {
            value = cached
            return@produceState
        }
        value = SystemMediaBitmapThumbnailCache.getAny(cacheKey)
        val loaded = withContext(Dispatchers.IO) {
            loadSystemMediaThumbnail(appContext, uri, requestedSize)
        }
        if (loaded != null) {
            SystemMediaBitmapThumbnailCache.put(cacheKey, loaded)
            value = loaded
        }
    }.value
}

@Composable
internal fun rememberCachedSystemMediaThumbnail(
    uri: Uri,
    targetSizePx: Int,
): Bitmap? {
    val cacheKey = remember(uri) { uri.toString() }
    val requestedSize = targetSizePx.coerceAtLeast(64)
    return remember(cacheKey, requestedSize) {
        SystemMediaBitmapThumbnailCache.get(cacheKey, requestedSize)
            ?: SystemMediaBitmapThumbnailCache.getAny(cacheKey)
    }
}

internal suspend fun prefetchSystemMediaThumbnail(
    context: Context,
    uri: Uri,
    targetSizePx: Int,
) {
    val appContext = context.applicationContext
    val cacheKey = uri.toString()
    val requestedSize = targetSizePx.coerceAtLeast(64)
    if (SystemMediaBitmapThumbnailCache.get(cacheKey, requestedSize) != null) return
    if (!SystemMediaBitmapThumbnailCache.markInFlight(cacheKey, requestedSize)) return
    try {
        val loaded = withContext(Dispatchers.IO) {
            loadSystemMediaThumbnail(appContext, uri, requestedSize)
        }
        if (loaded != null) {
            SystemMediaBitmapThumbnailCache.put(cacheKey, loaded)
        }
    } finally {
        SystemMediaBitmapThumbnailCache.clearInFlight(cacheKey, requestedSize)
    }
}

@Composable
internal fun rememberSystemVideoThumbnail(
    context: Context,
    uri: Uri,
): Bitmap? {
    val appContext = remember(context) { context.applicationContext }
    return produceState<Bitmap?>(
        initialValue = SystemMediaVideoThumbnailCache.bitmaps[uri.toString()],
        key1 = appContext,
        key2 = uri,
    ) {
        SystemMediaVideoThumbnailCache.bitmaps[uri.toString()]?.let { cached ->
            value = cached
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            loadSystemVideoFrame(appContext, uri)?.also { bitmap ->
                SystemMediaVideoThumbnailCache.bitmaps[uri.toString()] = bitmap
            }
        }
    }.value
}

private fun loadSystemMediaThumbnail(
    context: Context,
    uri: Uri,
    targetSizePx: Int,
): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching {
            context.contentResolver.loadThumbnail(uri, Size(targetSizePx, targetSizePx), null)
        }.getOrNull()?.fitSystemThumbnail(targetSizePx)?.let { return it }
    }
    decodeSampledBitmap(context, uri, targetSizePx)?.fitSystemThumbnail(targetSizePx)?.let { return it }
    return loadSystemVideoFrame(context, uri)?.fitSystemThumbnail(targetSizePx)
}

private fun decodeSampledBitmap(
    context: Context,
    uri: Uri,
    targetSizePx: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val sampleOptions = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            targetSizePx = targetSizePx,
        )
    }
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, sampleOptions)
        }
    }.getOrNull()
}

private fun calculateInSampleSize(
    width: Int,
    height: Int,
    targetSizePx: Int,
): Int {
    var sampleSize = 1
    var halfWidth = width / 2
    var halfHeight = height / 2
    while (halfWidth / sampleSize >= targetSizePx && halfHeight / sampleSize >= targetSizePx) {
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}

internal fun loadSystemVideoFrame(
    context: Context,
    uri: Uri,
): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val thumbnail = runCatching {
            context.contentResolver.loadThumbnail(uri, SystemMediaThumbnailSize, null)
        }.getOrNull()
        if (thumbnail != null) return thumbnail
    }

    val retriever = MediaMetadataRetriever()
    val bitmap = runCatching {
        retriever.setDataSource(context, uri)
        SystemMediaPreviewFrameTimesUs.firstNotNullOfOrNull { timeUs ->
            retriever.getFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            )
        } ?: retriever.frameAtTime
    }.getOrNull()
    runCatching { retriever.release() }
    if (bitmap != null) return bitmap

    return null
}

internal suspend fun prefetchSystemVideoThumbnail(
    context: Context,
    uri: Uri,
) {
    val key = uri.toString()
    if (SystemMediaVideoThumbnailCache.bitmaps.containsKey(key)) return
    withContext(Dispatchers.IO) {
        if (SystemMediaVideoThumbnailCache.bitmaps.containsKey(key)) return@withContext
        loadSystemVideoFrame(context.applicationContext, uri)?.let { bitmap ->
            SystemMediaVideoThumbnailCache.bitmaps[key] = bitmap
        }
    }
}

internal fun resolveSystemVideoDimensions(
    context: Context,
    uri: Uri,
): Pair<Int?, Int?> {
    val retriever = MediaMetadataRetriever()
    return runCatching {
        retriever.setDataSource(context, uri)
        val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull()
        val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull()
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull()
            ?: 0
        if (rotation == 90 || rotation == 270) {
            rawHeight to rawWidth
        } else {
            rawWidth to rawHeight
        }
    }.getOrElse {
        null to null
    }.also {
        runCatching { retriever.release() }
    }
}

internal fun Bitmap.toComposeBitmap() = asImageBitmap()

private object SystemMediaVideoThumbnailCache {
    val bitmaps = ConcurrentHashMap<String, Bitmap>()
}

private object SystemMediaBitmapThumbnailCache {
    private val overviewCache = object : LruCache<String, CachedBitmap>(SystemMediaOverviewThumbnailCacheMaxBytes) {
        override fun sizeOf(key: String, value: CachedBitmap): Int {
            return value.bitmap.byteCount
        }

        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: CachedBitmap,
            newValue: CachedBitmap?,
        ) {
            if (newValue == null && latestOverviewBySource[oldValue.sourceKey] === oldValue) {
                latestOverviewBySource.remove(oldValue.sourceKey)
            }
        }
    }
    private val regularCache = object : LruCache<String, CachedBitmap>(SystemMediaImageThumbnailCacheMaxBytes) {
        override fun sizeOf(key: String, value: CachedBitmap): Int {
            return value.bitmap.byteCount
        }

        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: CachedBitmap,
            newValue: CachedBitmap?,
        ) {
            if (newValue == null && latestRegularBySource[oldValue.sourceKey] === oldValue) {
                latestRegularBySource.remove(oldValue.sourceKey)
            }
        }
    }
    private val latestOverviewBySource = ConcurrentHashMap<String, CachedBitmap>()
    private val latestRegularBySource = ConcurrentHashMap<String, CachedBitmap>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    @Synchronized
    fun getAny(key: String): Bitmap? {
        return latestOverviewBySource[key]?.bitmap
            ?: latestRegularBySource[key]?.bitmap
    }

    @Synchronized
    fun get(key: String, requestedSize: Int): Bitmap? {
        return cacheFor(requestedSize).get(cacheKey(key, requestedSize))?.bitmap
            ?: latestOverviewBySource[key]?.takeIf { it.edgeSize >= requestedSize }?.bitmap
            ?: latestRegularBySource[key]?.takeIf { it.edgeSize >= requestedSize }?.bitmap
            ?: latestOverviewBySource[key]?.bitmap
            ?: latestRegularBySource[key]?.bitmap
    }

    @Synchronized
    fun put(key: String, bitmap: Bitmap) {
        val edgeSize = maxOf(bitmap.width, bitmap.height)
        val cached = CachedBitmap(sourceKey = key, bitmap = bitmap, edgeSize = edgeSize)
        if (edgeSize <= SystemMediaOverviewThumbnailMaxEdge) {
            overviewCache.put(cacheKey(key, edgeSize), cached)
            latestOverviewBySource[key] = cached
        } else {
            regularCache.put(cacheKey(key, edgeSize), cached)
            latestRegularBySource[key] = cached
        }
    }

    fun markInFlight(key: String, requestedSize: Int): Boolean {
        return inFlight.add("$key@$requestedSize")
    }

    fun clearInFlight(key: String, requestedSize: Int) {
        inFlight.remove("$key@$requestedSize")
    }

    private fun cacheFor(size: Int): LruCache<String, CachedBitmap> {
        return if (size <= SystemMediaOverviewThumbnailMaxEdge) overviewCache else regularCache
    }

    private fun cacheKey(key: String, edgeSize: Int): String = "$key@$edgeSize"
}

private data class CachedBitmap(
    val sourceKey: String,
    val bitmap: Bitmap,
    val edgeSize: Int,
)

private fun Bitmap.fitSystemThumbnail(targetSizePx: Int): Bitmap {
    val maxEdge = maxOf(width, height)
    if (maxEdge <= targetSizePx) return this
    val scale = targetSizePx.toFloat() / maxEdge.toFloat()
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
}
