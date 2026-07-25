package com.example.yingshi.feature.photos

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.model.RemoteMediaAccess
import com.example.yingshi.data.model.RemotePostMedia
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.config.RemoteConfig
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
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
    val thumbnailCacheKey: String? = null,
    val originalCacheKey: String? = null,
    val mediaCacheKey: String? = null,
    val videoCacheKey: String? = null,
    val coverCacheKey: String? = null,
    val refreshKey: String? = null,
)

internal fun AppContentMediaSource?.withRefreshKey(refreshKey: String?): AppContentMediaSource? {
    val normalized = refreshKey?.trim()?.ifBlank { null }
    return this?.copy(refreshKey = normalized)
}

fun RemoteMedia.toAppContentMediaSource(): AppContentMediaSource {
    val previewAccess = access.mediaAccess("preview")
    val originalAccess = access.mediaAccess("original")
    val videoAccess = access.mediaAccess("video")
    val coverAccess = access.mediaAccess("cover")
    val resolvedMediaCacheKey = when {
        mediaType.equals("video", ignoreCase = true) -> videoAccess.stableCacheKey()
        else -> originalAccess.stableCacheKey()
    }
    return AppContentMediaSource(
        thumbnailUrl = resolveBackendMediaUrl(previewAccess?.requestUrl ?: thumbnailUrl ?: previewUrl),
        originalUrl = resolveBackendMediaUrl(originalAccess?.requestUrl ?: originalUrl),
        mediaUrl = resolveBackendMediaUrl(
            originalAccess?.requestUrl ?: videoAccess?.requestUrl ?: mediaUrl,
        ),
        videoUrl = resolveBackendMediaUrl(videoAccess?.requestUrl ?: videoUrl),
        coverUrl = resolveBackendMediaUrl(coverAccess?.requestUrl ?: coverUrl),
        mimeType = mimeType?.trim()?.ifBlank { null },
        width = width,
        height = height,
        durationMillis = durationMillis,
        createdAtMillis = createdAtMillis ?: displayTimeMillis,
        thumbnailCacheKey = previewAccess.stableCacheKey(),
        originalCacheKey = originalAccess.stableCacheKey(),
        mediaCacheKey = resolvedMediaCacheKey,
        videoCacheKey = videoAccess.stableCacheKey(),
        coverCacheKey = coverAccess.stableCacheKey(),
    )
}

internal fun RemotePostMedia.toAppContentMediaSource(): AppContentMediaSource {
    val previewAccess = access.mediaAccess("preview")
    val originalAccess = access.mediaAccess("original")
    val videoAccess = access.mediaAccess("video")
    val coverAccess = access.mediaAccess("cover")
    val resolvedMediaCacheKey = when {
        mediaType.equals("video", ignoreCase = true) -> videoAccess.stableCacheKey()
        else -> originalAccess.stableCacheKey()
    }
    return AppContentMediaSource(
        thumbnailUrl = resolveBackendMediaUrl(previewAccess?.requestUrl ?: thumbnailUrl ?: previewUrl),
        originalUrl = resolveBackendMediaUrl(originalAccess?.requestUrl ?: originalUrl),
        mediaUrl = resolveBackendMediaUrl(
            originalAccess?.requestUrl ?: videoAccess?.requestUrl ?: mediaUrl,
        ),
        videoUrl = resolveBackendMediaUrl(videoAccess?.requestUrl ?: videoUrl),
        coverUrl = resolveBackendMediaUrl(coverAccess?.requestUrl ?: coverUrl),
        mimeType = mimeType?.trim()?.ifBlank { null },
        width = width,
        height = height,
        durationMillis = videoDurationMillis,
        createdAtMillis = createdAtMillis ?: displayTimeMillis,
        thumbnailCacheKey = previewAccess.stableCacheKey(),
        originalCacheKey = originalAccess.stableCacheKey(),
        mediaCacheKey = resolvedMediaCacheKey,
        videoCacheKey = videoAccess.stableCacheKey(),
        coverCacheKey = coverAccess.stableCacheKey(),
    )
}

private fun String?.withRefreshKey(refreshKey: String?): String? {
    val normalizedRefreshKey = refreshKey?.trim()?.ifBlank { null } ?: return this?.trim()?.ifBlank { null }
    val normalizedBase = this?.trim()?.ifBlank { null }
    return buildString {
        if (!normalizedBase.isNullOrBlank()) {
            append(normalizedBase)
            append("|")
        }
        append("refresh:")
        append(normalizedRefreshKey)
    }
}

fun resolveAppMediaType(
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
        AppMediaType.VIDEO -> videoPosterImageUrl(mediaType)
    }
}

internal fun AppContentMediaSource?.thumbnailModelCacheKey(
    mediaType: AppMediaType,
): String? {
    if (this == null) return null
    return when (mediaType) {
        AppMediaType.IMAGE -> firstNotBlank(
            thumbnailCacheKey,
            mediaCacheKey,
            originalCacheKey,
            coverCacheKey,
        ).withRefreshKey(refreshKey)
        AppMediaType.VIDEO -> videoPosterImageCacheKey(mediaType).withRefreshKey(refreshKey)
    }
}

internal fun AppContentMediaSource?.thumbnailModelDiskCacheKey(
    mediaType: AppMediaType,
): String? {
    if (this == null) return null
    return when (mediaType) {
        AppMediaType.IMAGE -> firstNotBlank(
            thumbnailCacheKey,
            mediaCacheKey,
            originalCacheKey,
            coverCacheKey,
        )
        AppMediaType.VIDEO -> videoPosterImageDiskCacheKey(mediaType)
    }
}

internal fun AppContentMediaSource?.videoPosterImageUrl(
    mediaType: AppMediaType,
): String? {
    if (mediaType != AppMediaType.VIDEO || this == null) return null
    return firstNotBlank(
        coverUrl.takeIf { canUseAsVideoPoster(coverUrl, mimeType) },
        thumbnailUrl.takeIf { canUseAsVideoPoster(thumbnailUrl, mimeType) },
        mediaUrl.takeIf { canUseAsVideoPoster(mediaUrl, mimeType) },
        originalUrl.takeIf { canUseAsVideoPoster(originalUrl, mimeType) },
    )
}

internal fun AppContentMediaSource?.videoPosterImageCacheKey(
    mediaType: AppMediaType,
): String? {
    if (mediaType != AppMediaType.VIDEO || this == null) return null
    val posterImageUrl = videoPosterImageUrl(mediaType) ?: return null
    return when (posterImageUrl) {
        thumbnailUrl -> thumbnailCacheKey.withRefreshKey(refreshKey)
        coverUrl -> coverCacheKey.withRefreshKey(refreshKey)
        mediaUrl -> mediaCacheKey.withRefreshKey(refreshKey)
        originalUrl -> originalCacheKey.withRefreshKey(refreshKey)
        else -> null
    }
}

internal fun AppContentMediaSource?.videoPosterImageDiskCacheKey(
    mediaType: AppMediaType,
): String? {
    if (mediaType != AppMediaType.VIDEO || this == null) return null
    val posterImageUrl = videoPosterImageUrl(mediaType) ?: return null
    return when (posterImageUrl) {
        thumbnailUrl -> thumbnailCacheKey
        coverUrl -> coverCacheKey
        mediaUrl -> mediaCacheKey
        originalUrl -> originalCacheKey
        else -> null
    }
}

internal fun AppContentMediaSource?.videoPosterVideoUrl(
    mediaType: AppMediaType,
): String? {
    if (mediaType != AppMediaType.VIDEO || this == null) return null
    return firstNotBlank(
        videoUrl,
        mediaUrl.takeIf { looksLikeVideoUrl(it) },
        originalUrl.takeIf { looksLikeVideoUrl(it) },
        thumbnailUrl.takeIf { looksLikeVideoUrl(it) },
    )
}

internal fun AppContentMediaSource?.videoPosterVideoCacheKey(
    mediaType: AppMediaType,
): String? {
    if (mediaType != AppMediaType.VIDEO || this == null) return null
    val posterVideoUrl = videoPosterVideoUrl(mediaType) ?: return null
    return when (posterVideoUrl) {
        videoUrl -> videoCacheKey.withRefreshKey(refreshKey)
        mediaUrl -> mediaCacheKey.withRefreshKey(refreshKey)
        originalUrl -> originalCacheKey.withRefreshKey(refreshKey)
        thumbnailUrl -> thumbnailCacheKey.withRefreshKey(refreshKey)
        else -> null
    }
}

internal fun AppContentMediaSource?.videoPosterVideoDiskCacheKey(
    mediaType: AppMediaType,
): String? {
    if (mediaType != AppMediaType.VIDEO || this == null) return null
    val posterVideoUrl = videoPosterVideoUrl(mediaType) ?: return null
    return when (posterVideoUrl) {
        videoUrl -> videoCacheKey
        mediaUrl -> mediaCacheKey
        originalUrl -> originalCacheKey
        thumbnailUrl -> thumbnailCacheKey
        else -> null
    }
}

internal fun AppContentMediaSource?.viewerPreviewImageUrl(
    mediaType: AppMediaType,
): String? {
    if (mediaType != AppMediaType.IMAGE || this == null) return null
    return firstNotBlank(
        thumbnailUrl,
        mediaUrl,
    )
}

internal fun AppContentMediaSource?.viewerPreviewImageCacheKey(
    mediaType: AppMediaType,
): String? {
    if (mediaType != AppMediaType.IMAGE || this == null) return null
    return firstNotBlank(
        thumbnailCacheKey,
        mediaCacheKey,
    ).withRefreshKey(refreshKey)
}

internal fun AppContentMediaSource?.viewerOriginalImageUrl(
    mediaType: AppMediaType,
): String? {
    if (mediaType != AppMediaType.IMAGE || this == null) return null
    val previewUrl = viewerPreviewImageUrl(mediaType)
    return firstDistinctNotBlank(
        disallow = setOfNotNull(previewUrl),
        originalUrl,
        mediaUrl,
    )
}

internal fun AppContentMediaSource?.viewerOriginalImageCacheKey(
    mediaType: AppMediaType,
): String? {
    if (mediaType != AppMediaType.IMAGE || this == null) return null
    return firstNotBlank(
        originalCacheKey,
        mediaCacheKey,
    ).withRefreshKey(refreshKey)
}

internal fun AppContentMediaSource?.viewerOriginalImageDiskCacheKey(
    mediaType: AppMediaType,
): String? {
    if (mediaType != AppMediaType.IMAGE || this == null) return null
    return firstNotBlank(
        originalCacheKey,
        mediaCacheKey,
    )
}

internal fun AppContentMediaSource?.hasMeaningfulViewerOriginal(
    mediaType: AppMediaType,
): Boolean {
    return mediaType == AppMediaType.IMAGE && viewerOriginalImageUrl(mediaType) != null
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

internal fun AppContentMediaSource?.viewerVideoCacheKey(
    mediaType: AppMediaType,
): String? {
    if (mediaType != AppMediaType.VIDEO || this == null) return null
    return firstNotBlank(
        videoCacheKey,
        mediaCacheKey,
        originalCacheKey,
    ).withRefreshKey(refreshKey)
}

internal fun AppContentMediaSource?.viewerVideoDiskCacheKey(
    mediaType: AppMediaType,
): String? {
    if (mediaType != AppMediaType.VIDEO || this == null) return null
    return firstNotBlank(
        videoCacheKey,
        mediaCacheKey,
        originalCacheKey,
    )
}

internal fun SystemMediaItem.toAppContentMediaSource(): AppContentMediaSource {
    val uriString = uri.toString()
    return AppContentMediaSource(
        thumbnailUrl = uriString,
        originalUrl = uriString,
        mediaUrl = uriString,
        videoUrl = if (type == SystemMediaType.VIDEO) uriString else null,
        coverUrl = uriString,
        mimeType = mimeType,
        width = width,
        height = height,
        durationMillis = if (type == SystemMediaType.VIDEO) {
            videoDurationMillis
        } else {
            null
        },
        createdAtMillis = displayTimeMillis,
    )
}

private fun canUseAsVideoPoster(
    url: String?,
    mimeType: String?,
): Boolean {
    if (url.isNullOrBlank()) return false
    val normalizedMimeType = mimeType?.trim()?.lowercase(Locale.ROOT)
    if (normalizedMimeType?.startsWith("image/") == true) return true
    if (url.contains("variant=cover", ignoreCase = true) ||
        url.contains("variant=preview", ignoreCase = true)
    ) {
        return true
    }
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
    return if (isBackendApiUrl(url)) {
        mapOf("Authorization" to "${RemoteConfig.AUTH_SCHEME} $accessToken")
    } else {
        emptyMap()
    }
}

private fun isBackendApiUrl(url: String): Boolean {
    val normalizedUrl = url.trim().lowercase()
    if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
        return false
    }
    val baseUrl = BackendDebugConfig.currentBaseUrl().trim().lowercase()
    return normalizedUrl.startsWith(baseUrl)
}

internal fun backendMediaImageRequest(
    context: Context,
    url: String?,
    accessToken: String?,
    memoryCacheKey: String? = url?.let(::sharedPreviewMemoryCacheKey),
    placeholderMemoryCacheKey: String? = null,
    diskCacheKey: String? = null,
    size: Int? = null,
): ImageRequest? {
    if (url.isNullOrBlank()) return null
    return ImageRequest.Builder(context).apply {
        data(url)
        memoryCacheKey?.let(::memoryCacheKey)
        placeholderMemoryCacheKey?.let(::placeholderMemoryCacheKey)
        diskCacheKey(diskCacheKey?.takeIf { it.isNotBlank() } ?: sharedMediaDiskCacheKey(url))
        networkCachePolicy(CachePolicy.ENABLED)
        diskCachePolicy(CachePolicy.ENABLED)
        memoryCachePolicy(CachePolicy.ENABLED)
        precision(Precision.INEXACT)
        crossfade(false)
        size?.let(::size)
        backendMediaRequestHeaders(url, accessToken).forEach(::addHeader)
    }.build()
}

internal fun backendMediaOriginalImageRequest(
    context: Context,
    url: String?,
    accessToken: String?,
    memoryCacheKey: String? = url?.let(::sharedOriginalMemoryCacheKey),
    diskCacheKey: String? = null,
): ImageRequest? {
    if (url.isNullOrBlank()) return null
    return ImageRequest.Builder(context).apply {
        data(url)
        memoryCacheKey?.let(::memoryCacheKey)
        diskCacheKey(diskCacheKey?.takeIf { it.isNotBlank() } ?: sharedOriginalDiskCacheKey(url))
        networkCachePolicy(CachePolicy.ENABLED)
        diskCachePolicy(CachePolicy.ENABLED)
        memoryCachePolicy(CachePolicy.ENABLED)
        precision(Precision.EXACT)
        size(Size.ORIGINAL)
        crossfade(false)
        backendMediaRequestHeaders(url, accessToken).forEach(::addHeader)
    }.build()
}

internal fun sharedPreviewMemoryCacheKey(url: String): String = "media:${stableMediaCacheUrlKey(url)}"

internal fun sharedOriginalMemoryCacheKey(url: String): String = "original:${stableMediaCacheUrlKey(url)}"

internal fun sharedMediaDiskCacheKey(url: String): String = "media:${stableMediaCacheUrlKey(url)}"

internal fun sharedOriginalDiskCacheKey(url: String): String = "original:${stableMediaCacheUrlKey(url)}"

internal fun sharedVideoDiskCacheKey(url: String): String = "video:${stableMediaCacheUrlKey(url)}"

internal fun stableMediaCacheUrlKey(url: String): String {
    val normalized = url.trim()
    if (normalized.isBlank()) return normalized
    return runCatching {
        val parsed = java.net.URI(normalized)
        val path = parsed.rawPath?.takeIf { it.isNotBlank() } ?: parsed.path.orEmpty()
        val mediaFileMarker = "/api/media/files/"
        val markerIndex = path.indexOf(mediaFileMarker, ignoreCase = true)
        if (markerIndex >= 0) {
            val encodedMediaId = path.substring(markerIndex + mediaFileMarker.length)
                .substringBefore('/')
                .takeIf { it.isNotBlank() }
            if (!encodedMediaId.isNullOrBlank()) {
                val variant = rawQueryParameter(parsed.rawQuery, "variant")?.trim()?.ifBlank { null } ?: "original"
                return@runCatching "media:${decodeUrlComponent(encodedMediaId)}:$variant"
            }
        }

        val hasVolatileQuery = rawQueryParameterNames(parsed.rawQuery).any { name ->
            name.lowercase(Locale.ROOT) in VolatileSignedQueryParameterNames
        }
        if (hasVolatileQuery) {
            normalized.substringBefore('?').substringBefore('#')
        } else {
            normalized.substringBefore('#')
        }
    }.getOrElse {
        normalized.substringBefore('#')
    }
}

internal fun resolveBackendMediaUrl(rawUrl: String?): String? {
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

private fun List<RemoteMediaAccess>.mediaAccess(variant: String): RemoteMediaAccess? {
    return firstOrNull { it.variant.equals(variant, ignoreCase = true) }
}

private fun RemoteMediaAccess?.stableCacheKey(): String? {
    return this?.cacheKey?.trim()?.ifBlank { null }
}

private fun firstDistinctNotBlank(
    disallow: Set<String>,
    vararg values: String?,
): String? {
    val normalizedDisallow = disallow.map(::normalizeComparableMediaUrl).toSet()
    return values.asSequence()
        .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
        .firstOrNull { normalizeComparableMediaUrl(it) !in normalizedDisallow }
}

private fun normalizeComparableMediaUrl(value: String): String {
    return runCatching {
        val parsed = Uri.parse(value.trim())
        parsed.buildUpon().fragment(null).build().toString()
    }.getOrElse {
        value.trim().substringBefore('#')
    }
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

private fun rawQueryParameterNames(rawQuery: String?): Set<String> {
    if (rawQuery.isNullOrBlank()) return emptySet()
    return rawQuery.split('&')
        .asSequence()
        .map { it.substringBefore('=') }
        .filter { it.isNotBlank() }
        .map(::decodeUrlComponent)
        .toSet()
}

private fun rawQueryParameter(rawQuery: String?, name: String): String? {
    if (rawQuery.isNullOrBlank()) return null
    return rawQuery.split('&')
        .asSequence()
        .mapNotNull { part ->
            val rawName = part.substringBefore('=').takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (!decodeUrlComponent(rawName).equals(name, ignoreCase = true)) {
                return@mapNotNull null
            }
            decodeUrlComponent(part.substringAfter('=', missingDelimiterValue = ""))
        }
        .firstOrNull()
}

private fun decodeUrlComponent(value: String): String {
    return runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrElse {
        value
    }
}

private val VolatileSignedQueryParameterNames = setOf(
    "x-amz-algorithm",
    "x-amz-credential",
    "x-amz-date",
    "x-amz-expires",
    "x-amz-security-token",
    "x-amz-signature",
    "expires",
    "sign",
    "signature",
    "security-token",
    "t",
    "token",
    "q-ak",
    "q-header-list",
    "q-key-time",
    "q-sign-algorithm",
    "q-sign-time",
    "q-signature",
    "q-url-param-list",
)
