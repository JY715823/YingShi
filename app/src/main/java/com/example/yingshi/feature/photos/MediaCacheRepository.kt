package com.example.yingshi.feature.photos

import android.content.Context
import androidx.compose.runtime.Immutable
import coil.imageLoader
import java.io.File
import java.util.Locale

@Immutable
internal data class RealMediaCacheSummary(
    val thumbnailCoverBytes: Long,
    val originalMediaBytes: Long,
    val totalBytes: Long,
    val thumbnailCoverSizeLabel: String,
    val originalMediaSizeLabel: String,
    val totalSizeLabel: String,
    val registeredMediaCount: Int,
    val registeredPreviewCount: Int,
    val registeredOriginalCount: Int,
    val registeredVideoCount: Int,
)

internal object MediaCacheRepository {
    fun getSummary(context: Context): RealMediaCacheSummary {
        val appContext = context.applicationContext
        val thumbnailCoverBytes = directorySize(appContext.cacheDir.resolve("coil-media-cache")) +
            directorySize(appContext.cacheDir.resolve("video-posters"))
        val originalMediaBytes = directorySize(AppMediaVideoCache.directory(appContext))
        // R3-APP-001: Removed FakeMediaCacheRepository.getGlobalSummary() dependency.
        // Registered counts are set to 0; real cache size is still computed from disk.
        val totalBytes = thumbnailCoverBytes + originalMediaBytes
        return RealMediaCacheSummary(
            thumbnailCoverBytes = thumbnailCoverBytes,
            originalMediaBytes = originalMediaBytes,
            totalBytes = totalBytes,
            thumbnailCoverSizeLabel = formatBytes(thumbnailCoverBytes),
            originalMediaSizeLabel = formatBytes(originalMediaBytes),
            totalSizeLabel = formatBytes(totalBytes),
            registeredMediaCount = 0,
            registeredPreviewCount = 0,
            registeredOriginalCount = 0,
            registeredVideoCount = 0,
        )
    }

    fun clearThumbnailAndCoverCache(context: Context): Boolean {
        val appContext = context.applicationContext
        val imageLoader = appContext.imageLoader
        runCatching { imageLoader.memoryCache?.clear() }
        val coilCleared = runCatching {
            imageLoader.diskCache?.clear()
            true
        }.getOrElse {
            appContext.cacheDir.resolve("coil-media-cache").deleteContentsSafely()
        }
        val postersCleared = appContext.cacheDir.resolve("video-posters").deleteContentsSafely()
        clearVideoPosterMemoryCache()
        // R3-APP-001: Removed FakeMediaCacheRepository.clearAllPreviewCaches()
        RealOriginalLoadRepository.clearAllOriginals()
        return coilCleared && postersCleared
    }

    fun clearOriginalMediaCache(context: Context): Boolean {
        val appContext = context.applicationContext
        runCatching { appContext.imageLoader.memoryCache?.clear() }
        val originalsCleared = RealOriginalLoadRepository.clearCachedOriginalFiles(appContext)
        val videoCleared = AppMediaVideoCache.clear(appContext)
        // R3-APP-001: Removed FakeMediaCacheRepository.clearAllOriginalCaches() and clearAllVideoCaches()
        return originalsCleared && videoCleared
    }

    fun clearAllMediaCaches(context: Context): Boolean {
        val previewCleared = clearThumbnailAndCoverCache(context)
        val originalCleared = clearOriginalMediaCache(context)
        return previewCleared && originalCleared
    }
}

internal fun File.deleteContentsSafely(): Boolean {
    if (!exists()) return true
    if (!isDirectory) return runCatching { delete() }.getOrDefault(false)
    var ok = true
    listFiles().orEmpty().forEach { child ->
        ok = child.deleteRecursivelySafely() && ok
    }
    return ok
}

private fun File.deleteRecursivelySafely(): Boolean {
    if (!exists()) return true
    var ok = true
    if (isDirectory) {
        listFiles().orEmpty().forEach { child ->
            ok = child.deleteRecursivelySafely() && ok
        }
    }
    return runCatching { delete() }.getOrDefault(false) && ok
}

private fun directorySize(directory: File): Long {
    if (!directory.exists()) return 0L
    if (!directory.isDirectory) return directory.length().coerceAtLeast(0L)
    var total = 0L
    val pending = ArrayDeque<File>()
    pending.add(directory)
    while (pending.isNotEmpty()) {
        val file = pending.removeFirst()
        if (file.isDirectory) {
            file.listFiles().orEmpty().forEach(pending::add)
        } else {
            total += file.length().coerceAtLeast(0L)
        }
    }
    return total
}

internal fun formatCacheBytes(bytes: Long): String = formatBytes(bytes)

private fun formatBytes(bytes: Long): String {
    val kb = 1024L
    val mb = kb * 1024L
    val gb = mb * 1024L
    return when {
        bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes.toDouble() / gb.toDouble())
        bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / mb.toDouble())
        bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes.toDouble() / kb.toDouble())
        else -> "$bytes B"
    }
}
