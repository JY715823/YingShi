package com.example.yingshi.feature.photos

import androidx.compose.runtime.Immutable

@Immutable
data class LargeAlbumSummaryUiModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val accent: PhotoThumbnailPalette,
)

@Immutable
data class SmallAlbumCardUiModel(
    val id: String,
    val albumId: String,
    val albumIds: List<String> = listOf(albumId),
    val title: String,
    val summary: String,
    val creatorUserId: String? = null,
    val participantUserIds: List<String> = emptyList(),
    val postDisplayTimeMillis: Long,
    val mediaCount: Int,
    val coverPalette: PhotoThumbnailPalette,
    val coverMediaType: AppMediaType = AppMediaType.IMAGE,
    val coverAspectRatio: Float = 1f,
    val coverMediaSource: AppContentMediaSource? = null,
) {
    val smallAlbumId: String
        get() = id

    val smallAlbumDisplayTimeMillis: Long
        get() = postDisplayTimeMillis
}

@Immutable
data class SmallAlbumDetailUiModel(
    val postId: String,
    val title: String,
    val summary: String,
    val contributorLabel: String,
    val creatorUserId: String? = null,
    val participantUserIds: List<String> = emptyList(),
    val postDisplayTimeMillis: Long,
    val albumIds: List<String>,
    val albumChips: List<String>,
    val mediaItems: List<SmallAlbumDetailMediaUiModel>,
    val comments: List<CommentUiModel>,
    val entryNotice: String? = null,
) {
    val smallAlbumId: String
        get() = postId

    val smallAlbumDisplayTimeMillis: Long
        get() = postDisplayTimeMillis

    val albumId: String
        get() = albumIds.firstOrNull().orEmpty()
}

@Immutable
data class SmallAlbumDetailMediaUiModel(
    val id: String,
    val displayTimeMillis: Long,
    val commentCount: Int,
    val uploadedByUserId: String? = null,
    val palette: PhotoThumbnailPalette,
    val mediaType: AppMediaType = AppMediaType.IMAGE,
    val aspectRatio: Float = 1f,
    val width: Int? = null,
    val height: Int? = null,
    val videoDurationMillis: Long? = null,
    val mediaSource: AppContentMediaSource? = null,
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
data class SmallAlbumDetailRoute(
    val postId: String,
    val albumId: String,
    val albumIds: List<String> = listOf(albumId),
    val title: String,
    val summary: String,
    val postDisplayTimeMillis: Long,
    val mediaCount: Int,
    val coverPalette: PhotoThumbnailPalette,
    val coverMediaType: AppMediaType = AppMediaType.IMAGE,
    val coverAspectRatio: Float = 1f,
    val entryNotice: String? = null,
    val highlightMediaIds: List<String> = emptyList(),
    val focusMediaId: String? = highlightMediaIds.firstOrNull(),
    val feedbackNonce: Int = 0,
) {
    val smallAlbumId: String
        get() = postId

    val smallAlbumDisplayTimeMillis: Long
        get() = postDisplayTimeMillis
}

@Immutable
data class GearEditRoute(
    val postId: String,
) {
    val smallAlbumId: String
        get() = postId
}

@Immutable
data class MediaManagementRoute(
    val postId: String,
) {
    val smallAlbumId: String
        get() = postId
}

@Immutable
data class EditableSmallAlbumDraft(
    val postId: String,
    val title: String,
    val summary: String,
    val postDisplayTimeMillis: Long,
    val albumIds: List<String>,
) {
    val smallAlbumId: String
        get() = postId

    val smallAlbumDisplayTimeMillis: Long
        get() = postDisplayTimeMillis

    val albumId: String
        get() = albumIds.firstOrNull().orEmpty()
}

@Immutable
data class ManagedSmallAlbumMediaUiModel(
    val id: String,
    val displayTimeMillis: Long,
    val commentCount: Int,
    val uploadedByUserId: String? = null,
    val palette: PhotoThumbnailPalette,
    val mediaType: AppMediaType = AppMediaType.IMAGE,
    val aspectRatio: Float,
    val isCover: Boolean,
    val videoDurationMillis: Long? = null,
    val mediaSource: AppContentMediaSource? = null,
)

typealias AlbumSummaryUiModel = LargeAlbumSummaryUiModel
typealias AlbumPostCardUiModel = SmallAlbumCardUiModel
typealias PostDetailUiModel = SmallAlbumDetailUiModel
typealias PostDetailMediaUiModel = SmallAlbumDetailMediaUiModel
typealias PostDetailPlaceholderRoute = SmallAlbumDetailRoute
typealias EditablePostDraft = EditableSmallAlbumDraft
typealias ManagedPostMediaUiModel = ManagedSmallAlbumMediaUiModel
