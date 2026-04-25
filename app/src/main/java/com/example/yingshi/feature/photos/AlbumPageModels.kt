package com.example.yingshi.feature.photos

import androidx.compose.runtime.Immutable

@Immutable
data class AlbumSummaryUiModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val accent: PhotoThumbnailPalette,
)

@Immutable
data class AlbumPostCardUiModel(
    val id: String,
    val albumId: String,
    val albumIds: List<String> = listOf(albumId),
    val title: String,
    val summary: String,
    val postDisplayTimeMillis: Long,
    val mediaCount: Int,
    val coverPalette: PhotoThumbnailPalette,
    val coverAspectRatio: Float = 1f,
)

@Immutable
data class PostDetailUiModel(
    val postId: String,
    val title: String,
    val summary: String,
    val contributorLabel: String,
    val postDisplayTimeMillis: Long,
    val albumIds: List<String>,
    val albumChips: List<String>,
    val mediaItems: List<PostDetailMediaUiModel>,
    val comments: List<CommentUiModel>,
)

@Immutable
data class PostDetailMediaUiModel(
    val id: String,
    val displayTimeMillis: Long,
    val commentCount: Int,
    val palette: PhotoThumbnailPalette,
    val aspectRatio: Float = 1f,
)

enum class AlbumGridDensity(
    val columns: Int,
    val label: String,
) {
    COZY_2(columns = 2, label = "2列"),
    COZY_3(columns = 3, label = "3列"),
    COZY_4(columns = 4, label = "4列"),
}

@Immutable
data class PostDetailPlaceholderRoute(
    val postId: String,
    val albumId: String,
    val albumIds: List<String> = listOf(albumId),
    val title: String,
    val summary: String,
    val postDisplayTimeMillis: Long,
    val mediaCount: Int,
    val coverPalette: PhotoThumbnailPalette,
    val coverAspectRatio: Float = 1f,
)

@Immutable
data class GearEditRoute(
    val postId: String,
)

@Immutable
data class MediaManagementRoute(
    val postId: String,
)

@Immutable
data class EditablePostDraft(
    val postId: String,
    val title: String,
    val summary: String,
    val postDisplayTimeMillis: Long,
    val albumIds: List<String>,
)

@Immutable
data class ManagedPostMediaUiModel(
    val id: String,
    val displayTimeMillis: Long,
    val commentCount: Int,
    val palette: PhotoThumbnailPalette,
    val aspectRatio: Float,
    val isCover: Boolean,
)
