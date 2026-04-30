package com.example.yingshi.feature.photos

import androidx.compose.ui.graphics.Color
import com.example.yingshi.data.model.RemoteAlbum
import com.example.yingshi.data.model.RemotePostDetail
import com.example.yingshi.data.model.RemotePostMedia
import com.example.yingshi.data.model.RemotePostSummary

fun RemoteAlbum.toAlbumSummaryUiModel(): AlbumSummaryUiModel {
    val palette = realPaletteFor(albumId)
    return AlbumSummaryUiModel(
        id = albumId,
        title = title,
        subtitle = subtitle.ifBlank { "$postCount posts" },
        accent = palette,
    )
}

fun RemotePostSummary.toAlbumPostCardUiModel(
    selectedAlbumId: String,
): AlbumPostCardUiModel {
    val palette = realPaletteFor(coverMediaId ?: postId)
    return AlbumPostCardUiModel(
        id = postId,
        albumId = selectedAlbumId,
        albumIds = albumIds.ifEmpty { listOf(selectedAlbumId) },
        title = title,
        summary = summary.ifBlank { contributorLabel.orEmpty().ifBlank { "No summary yet." } },
        postDisplayTimeMillis = displayTimeMillis,
        mediaCount = mediaCount,
        coverPalette = palette,
        coverMediaType = AppMediaType.IMAGE,
        coverAspectRatio = 1f,
    )
}

fun RemotePostSummary.toPostDetailPlaceholderRoute(
    selectedAlbumId: String,
): PostDetailPlaceholderRoute {
    val palette = realPaletteFor(coverMediaId ?: postId)
    return PostDetailPlaceholderRoute(
        postId = postId,
        albumId = selectedAlbumId,
        albumIds = albumIds.ifEmpty { listOf(selectedAlbumId) },
        title = title,
        summary = summary.ifBlank { contributorLabel.orEmpty().ifBlank { "No summary yet." } },
        postDisplayTimeMillis = displayTimeMillis,
        mediaCount = mediaCount,
        coverPalette = palette,
        coverMediaType = AppMediaType.IMAGE,
        coverAspectRatio = 1f,
    )
}

fun RemotePostDetail.toPostDetailUiModel(
    albumTitleById: Map<String, String>,
): PostDetailUiModel {
    return PostDetailUiModel(
        postId = postId,
        title = title,
        summary = summary.ifBlank { "No summary yet." },
        contributorLabel = contributorLabel.orEmpty().ifBlank { "Shared memory" },
        postDisplayTimeMillis = displayTimeMillis,
        albumIds = albumIds,
        albumChips = albumIds.map { albumId -> albumTitleById[albumId] ?: albumId },
        mediaItems = mediaItems.map(RemotePostMedia::toPostDetailMediaUiModel),
        comments = emptyList(),
    )
}

fun RemotePostMedia.toPostDetailMediaUiModel(): PostDetailMediaUiModel {
    val mediaKind = when (mediaType.lowercase()) {
        "video" -> AppMediaType.VIDEO
        else -> AppMediaType.IMAGE
    }
    return PostDetailMediaUiModel(
        id = mediaId,
        displayTimeMillis = displayTimeMillis,
        commentCount = commentCount,
        palette = realPaletteFor(mediaId),
        mediaType = mediaKind,
        aspectRatio = (aspectRatio ?: 1f).coerceIn(0.56f, 1.8f),
        width = width,
        height = height,
        videoDurationMillis = videoDurationMillis,
    )
}

private fun realPaletteFor(key: String): PhotoThumbnailPalette {
    val palettes = listOf(
        PhotoThumbnailPalette(
            start = Color(0xFFB8D8F8),
            end = Color(0xFF7EA6DF),
            accent = Color(0xFFE8F2FF),
        ),
        PhotoThumbnailPalette(
            start = Color(0xFFF5D2C3),
            end = Color(0xFFE7A08D),
            accent = Color(0xFFFFF0E8),
        ),
        PhotoThumbnailPalette(
            start = Color(0xFFCFE5B9),
            end = Color(0xFF84B38A),
            accent = Color(0xFFEFF8E1),
        ),
        PhotoThumbnailPalette(
            start = Color(0xFFD8D0F2),
            end = Color(0xFF8FA0D8),
            accent = Color(0xFFF0EDFF),
        ),
        PhotoThumbnailPalette(
            start = Color(0xFFE7CFB4),
            end = Color(0xFFB98B63),
            accent = Color(0xFFF7E8D4),
        ),
        PhotoThumbnailPalette(
            start = Color(0xFFC5D1DA),
            end = Color(0xFF8095A7),
            accent = Color(0xFFE7F0F6),
        ),
    )
    return palettes[key.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) } % palettes.size]
}
