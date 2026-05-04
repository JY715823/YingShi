package com.example.yingshi.feature.photos

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateMapOf

@Immutable
data class AppMediaCacheState(
    val mediaId: String,
    val mediaType: AppMediaType,
    val previewCached: Boolean,
    val originalCached: Boolean,
    val videoCached: Boolean,
    val cacheSizeLabel: String,
)

@Immutable
data class AppMediaCacheSummary(
    val mediaCount: Int,
    val previewCachedCount: Int,
    val originalCachedCount: Int,
    val videoCachedCount: Int,
    val totalBytes: Long,
    val totalSizeLabel: String,
)

@Immutable
data class CacheManagementRoute(
    val source: String = "viewer-settings",
)

private data class MutableMediaCacheFlags(
    val previewCached: Boolean,
    val originalCached: Boolean,
    val videoCached: Boolean,
)

object FakeMediaCacheRepository {
    private val mediaTypeByMediaId = mutableStateMapOf<String, AppMediaType>()
    private val flagsByMediaId = mutableStateMapOf<String, MutableMediaCacheFlags>()

    fun registerMedia(
        mediaId: String,
        mediaType: AppMediaType,
    ) {
        mediaTypeByMediaId[mediaId] = mediaType
        flagsByMediaId.putIfAbsent(
            mediaId,
            MutableMediaCacheFlags(
                previewCached = true,
                originalCached = false,
                videoCached = mediaType == AppMediaType.VIDEO,
            ),
        )
    }

    fun getState(
        mediaId: String,
        mediaType: AppMediaType,
    ): AppMediaCacheState {
        registerMedia(mediaId = mediaId, mediaType = mediaType)
        val flags = flagsByMediaId.getValue(mediaId)
        return AppMediaCacheState(
            mediaId = mediaId,
            mediaType = mediaType,
            previewCached = flags.previewCached,
            originalCached = flags.originalCached,
            videoCached = flags.videoCached,
            cacheSizeLabel = formatBytes(cacheSizeBytes(mediaType, flags)),
        )
    }

    fun getSummary(mediaIds: List<String>): AppMediaCacheSummary {
        val distinctIds = mediaIds.distinct().filter { mediaTypeByMediaId.containsKey(it) }
        val states = distinctIds.mapNotNull { mediaId ->
            mediaTypeByMediaId[mediaId]?.let { mediaType ->
                getState(mediaId = mediaId, mediaType = mediaType)
            }
        }
        val totalBytes = states.sumOf { cacheSizeBytes(it.mediaType, it.toFlags()) }
        return AppMediaCacheSummary(
            mediaCount = states.size,
            previewCachedCount = states.count { it.previewCached },
            originalCachedCount = states.count { it.originalCached },
            videoCachedCount = states.count { it.videoCached },
            totalBytes = totalBytes,
            totalSizeLabel = formatBytes(totalBytes),
        )
    }

    fun getGlobalSummary(): AppMediaCacheSummary {
        return getSummary(mediaTypeByMediaId.keys.toList())
    }

    fun markOriginalCached(mediaId: String) {
        val mediaType = mediaTypeByMediaId[mediaId] ?: return
        val current = getState(mediaId = mediaId, mediaType = mediaType)
        flagsByMediaId[mediaId] = current.toFlags().copy(originalCached = true)
    }

    fun clearPreviewCache(mediaId: String) {
        updateFlags(mediaId) { it.copy(previewCached = false) }
    }

    fun clearOriginalCache(mediaId: String) {
        updateFlags(mediaId) { it.copy(originalCached = false) }
        FakeOriginalLoadRepository.clearOriginal(mediaId)
        RealOriginalLoadRepository.clearOriginal(mediaId)
    }

    fun clearVideoCache(mediaId: String) {
        updateFlags(mediaId) { flags ->
            if ((mediaTypeByMediaId[mediaId] ?: AppMediaType.IMAGE) == AppMediaType.VIDEO) {
                flags.copy(videoCached = false)
            } else {
                flags
            }
        }
    }

    fun clearPostOriginalCaches(mediaIds: List<String>) {
        mediaIds.distinct().forEach(::clearOriginalCache)
    }

    fun clearPostVideoCaches(mediaIds: List<String>) {
        mediaIds.distinct().forEach(::clearVideoCache)
    }

    fun clearAllPreviewCaches() {
        flagsByMediaId.keys.toList().forEach(::clearPreviewCache)
    }

    fun clearAllOriginalCaches() {
        flagsByMediaId.keys.toList().forEach(::clearOriginalCache)
        RealOriginalLoadRepository.clearAllOriginals()
    }

    fun clearAllVideoCaches() {
        flagsByMediaId.keys.toList().forEach(::clearVideoCache)
    }

    fun clearAllCaches() {
        clearAllPreviewCaches()
        clearAllOriginalCaches()
        clearAllVideoCaches()
    }

    private fun updateFlags(
        mediaId: String,
        transform: (MutableMediaCacheFlags) -> MutableMediaCacheFlags,
    ) {
        val mediaType = mediaTypeByMediaId[mediaId] ?: return
        val current = flagsByMediaId[mediaId] ?: run {
            registerMedia(mediaId = mediaId, mediaType = mediaType)
            flagsByMediaId.getValue(mediaId)
        }
        flagsByMediaId[mediaId] = transform(current)
    }

    private fun cacheSizeBytes(
        mediaType: AppMediaType,
        flags: MutableMediaCacheFlags,
    ): Long {
        var total = 0L
        if (flags.previewCached) total += 420L * 1024L
        if (flags.originalCached) {
            total += if (mediaType == AppMediaType.VIDEO) {
                0L
            } else {
                6L * 1024L * 1024L
            }
        }
        if (flags.videoCached && mediaType == AppMediaType.VIDEO) {
            total += 18L * 1024L * 1024L
        }
        return total
    }

    private fun AppMediaCacheState.toFlags(): MutableMediaCacheFlags {
        return MutableMediaCacheFlags(
            previewCached = previewCached,
            originalCached = originalCached,
            videoCached = videoCached,
        )
    }

    private fun formatBytes(bytes: Long): String {
        val kb = 1024L
        val mb = kb * 1024L
        return when {
            bytes >= mb -> String.format("%.1f MB", bytes.toDouble() / mb.toDouble())
            bytes >= kb -> String.format("%.0f KB", bytes.toDouble() / kb.toDouble())
            else -> "$bytes B"
        }
    }
}
