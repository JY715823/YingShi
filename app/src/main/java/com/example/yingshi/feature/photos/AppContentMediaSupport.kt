package com.example.yingshi.feature.photos

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.model.RemotePostMedia
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.config.RemoteConfig
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import java.util.Locale

@Immutable
data class AppContentMediaSource(
    val thumbnailUrl: String? = null,
    val originalUrl: String? = null,
    val mediaUrl: String? = null,
    val videoUrl: String? = null,
    val coverUrl: String? = null,
    val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationMillis: Long? = null,
    val createdAtMillis: Long? = null,
)

internal fun RemoteMedia.toAppContentMediaSource(): AppContentMediaSource {
    return AppContentMediaSource(
        thumbnailUrl = resolveBackendMediaUrl(thumbnailUrl ?: previewUrl),
        originalUrl = resolveBackendMediaUrl(originalUrl),
        mediaUrl = resolveBackendMediaUrl(mediaUrl),
        videoUrl = resolveBackendMediaUrl(videoUrl),
        coverUrl = resolveBackendMediaUrl(coverUrl),
        mimeType = mimeType?.trim()?.ifBlank { null },
        width = width,
        height = height,
        durationMillis = durationMillis,
        createdAtMillis = createdAtMillis ?: displayTimeMillis,
    )
}

internal fun RemotePostMedia.toAppContentMediaSource(): AppContentMediaSource {
    return AppContentMediaSource(
        thumbnailUrl = resolveBackendMediaUrl(thumbnailUrl ?: previewUrl),
        originalUrl = resolveBackendMediaUrl(originalUrl),
        mediaUrl = resolveBackendMediaUrl(mediaUrl),
        videoUrl = resolveBackendMediaUrl(videoUrl),
        coverUrl = resolveBackendMediaUrl(coverUrl),
        mimeType = mimeType?.trim()?.ifBlank { null },
        width = width,
        height = height,
        durationMillis = videoDurationMillis,
        createdAtMillis = createdAtMillis ?: displayTimeMillis,
    )
}

internal fun resolveAppMediaType(
    rawType: String?,
    mimeType: String?,
    thumbnailUrl: String?,
    mediaUrl: String?,
    videoUrl: String?,
    coverUrl: String?,
    originalUrl: String?,
): AppMediaType {
    val normalizedType = rawType?.trim()?.lowercase(Locale.ROOT)
    if (normalizedType == "video") return AppMediaType.VIDEO
    if (normalizedType == "image") return AppMediaType.IMAGE

    val normalizedMimeType = mimeType?.trim()?.lowercase(Locale.ROOT)
    if (normalizedMimeType?.startsWith("video/") == true) return AppMediaType.VIDEO
    if (normalizedMimeType?.startsWith("image/") == true) return AppMediaType.IMAGE

    if (!videoUrl.isNullOrBlank()) return AppMediaType.VIDEO

    val candidateUrls = listOf(thumbnailUrl, coverUrl, mediaUrl, originalUrl)
    if (candidateUrls.any(::looksLikeVideoUrl)) return AppMediaType.VIDEO
    if (candidateUrls.any(::looksLikeImageUrl)) return AppMediaType.IMAGE

    return AppMediaType.IMAGE
}

internal fun resolveAppContentAspectRatio(
    aspectRatio: Float?,
    width: Int?,
    height: Int?,
    mediaType: AppMediaType,
): Float {
    if (width != null && height != null && width > 0 && height > 0) {
        return (width.toFloat() / height.toFloat()).coerceIn(0.15f, 6f)
    }
    if (aspectRatio != null && aspectRatio > 0f) {
        return aspectRatio.coerceIn(0.15f, 6f)
    }
    return if (mediaType == AppMediaType.VIDEO) 1.33f else 1f
}

internal fun AppContentMediaSource?.thumbnailModelUrl(
    mediaType: AppMediaType,
): String? {
    if (this == null) return null
    return when (mediaType) {
        AppMediaType.IMAGE -> firstNotBlank(
            thumbnailUrl,
            mediaUrl,
            originalUrl,
            coverUrl,
        )
        AppMediaType.VIDEO -> firstNotBlank(
            thumbnailUrl.takeIf { canUseAsVideoPoster(thumbnailUrl, mimeType) },
            coverUrl.takeIf { canUseAsVideoPoster(coverUrl, mimeType) },
            mediaUrl.takeIf { canUseAsVideoPoster(mediaUrl, mimeType) },
            originalUrl.takeIf { canUseAsVideoPoster(originalUrl, mimeType) },
            videoUrl,
            mediaUrl,
        )
    }
}

internal fun AppContentMediaSource?.viewerPreviewImageUrl(
    mediaType: AppMediaType,
): String? {
    if (mediaType != AppMediaType.IMAGE || this == null) return null
    return firstNotBlank(
        thumbnailUrl,
        mediaUrl,
        originalUrl,
    )
}

internal fun AppContentMediaSource?.viewerOriginalImageUrl(
    mediaType: AppMediaType,
): String? {
    if (mediaType != AppMediaType.IMAGE || this == null) return null
    val previewUrl = viewerPreviewImageUrl(mediaType)
    val originalCandidate = originalUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return originalCandidate.takeUnless { candidate ->
        !previewUrl.isNullOrBlank() && candidate.equals(previewUrl, ignoreCase = true)
    }
}

internal fun AppContentMediaSource?.hasMeaningfulViewerOriginal(
    mediaType: AppMediaType,
): Boolean {
    return viewerOriginalImageUrl(mediaType) != null
}

internal fun AppContentMediaSource?.viewerVideoUrl(
    mediaType: AppMediaType,
): String? {
    if (mediaType != AppMediaType.VIDEO || this == null) return null
    return firstNotBlank(
        videoUrl,
        mediaUrl,
        originalUrl,
    )
}

private fun canUseAsVideoPoster(
    url: String?,
    mimeType: String?,
): Boolean {
    if (url.isNullOrBlank()) return false
    val normalizedMimeType = mimeType?.trim()?.lowercase(Locale.ROOT)
    if (normalizedMimeType?.startsWith("image/") == true) return true
    return looksLikeImageUrl(url)
}

internal fun looksLikeVideoSource(
    url: String?,
    mimeType: String?,
): Boolean {
    if (mimeType?.startsWith("video/", ignoreCase = true) == true) return true
    return looksLikeVideoUrl(url)
}

internal fun backendMediaRequestHeaders(
    url: String?,
    accessToken: String?,
): Map<String, String> {
    if (url.isNullOrBlank() || accessToken.isNullOrBlank()) return emptyMap()
    return if (url.startsWith("http", ignoreCase = true)) {
        mapOf("Authorization" to "${RemoteConfig.AUTH_SCHEME} $accessToken")
    } else {
        emptyMap()
    }
}

internal fun backendMediaImageRequest(
    context: Context,
    url: String?,
    accessToken: String?,
    memoryCacheKey: String? = url?.let(::sharedPreviewMemoryCacheKey),
    size: Int? = null,
): ImageRequest? {
    if (url.isNullOrBlank()) return null
    return ImageRequest.Builder(context).apply {
        data(url)
        memoryCacheKey?.let(::memoryCacheKey)
        diskCacheKey(sharedMediaDiskCacheKey(url))
        networkCachePolicy(CachePolicy.ENABLED)
        diskCachePolicy(CachePolicy.ENABLED)
        memoryCachePolicy(CachePolicy.ENABLED)
        precision(Precision.INEXACT)
        crossfade(false)
        size?.let(::size)
        backendMediaRequestHeaders(url, accessToken).forEach(::addHeader)
    }.build()
}

internal fun sharedPreviewMemoryCacheKey(url: String): String = "media:$url"

internal fun sharedOriginalMemoryCacheKey(url: String): String = "original:$url"

internal fun sharedMediaDiskCacheKey(url: String): String = "media:$url"

private fun resolveBackendMediaUrl(rawUrl: String?): String? {
    val normalized = rawUrl?.trim().orEmpty()
    if (normalized.isEmpty()) return null
    return runCatching {
        val parsed = Uri.parse(normalized)
        if (!parsed.scheme.isNullOrBlank()) {
            normalized
        } else {
            val baseUrl = BackendDebugConfig.currentBaseUrl().trimEnd('/')
            val relativePath = normalized.trimStart('/')
            "$baseUrl/$relativePath"
        }
    }.getOrNull()
}

private fun firstNotBlank(vararg values: String?): String? {
    return values.firstOrNull { !it.isNullOrBlank() }?.trim()
}

internal fun looksLikeImageUrl(url: String?): Boolean {
    return url.hasFileExtension(
        "jpg",
        "jpeg",
        "png",
        "webp",
        "gif",
        "bmp",
        "heic",
        "heif",
        "avif",
    )
}

internal fun looksLikeVideoUrl(url: String?): Boolean {
    return url.hasFileExtension(
        "mp4",
        "mov",
        "m4v",
        "webm",
        "3gp",
        "mkv",
        "avi",
    )
}

private fun String?.hasFileExtension(vararg expectedExtensions: String): Boolean {
    if (this.isNullOrBlank()) return false
    val normalized = this.substringBefore('?').substringBefore('#')
    val extension = normalized.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase(Locale.ROOT)
    return extension.isNotBlank() && expectedExtensions.contains(extension)
}
