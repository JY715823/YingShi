package com.example.yingshi.feature.photos

import androidx.compose.runtime.Immutable

@Immutable
data class CreateSmallAlbumRoute(
    val source: String,
    val initialMediaItems: List<SystemMediaItem> = emptyList(),
    val initialAppMediaIds: List<String> = emptyList(),
    val initialAppMediaItems: List<CreateSmallAlbumAppMediaItem> = emptyList(),
)

@Immutable
data class CreateSmallAlbumAppMediaItem(
    val mediaId: String,
    val displayName: String,
    val mediaType: AppMediaType = AppMediaType.IMAGE,
    val palette: PhotoThumbnailPalette,
    val mediaSource: AppContentMediaSource? = null,
)

@Immutable
data class CreateSmallAlbumDraft(
    val title: String,
    val summary: String,
    val displayTimeMillis: Long,
    val albumIds: List<String>,
    val coverSourceMediaId: String? = null,
) {
    val albumId: String
        get() = albumIds.firstOrNull().orEmpty()
}

@Immutable
data class CreateSmallAlbumUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val tokenMissing: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val albums: List<LargeAlbumSummaryUiModel> = emptyList(),
    val title: String = "",
    val summary: String = "",
    val displayTimeMillis: Long = System.currentTimeMillis(),
    val selectedAlbumIds: List<String> = emptyList(),
    val initialMediaItems: List<SystemMediaItem> = emptyList(),
    val selectedCoverSourceMediaId: String? = null,
) {
    val hasInitialMedia: Boolean
        get() = initialMediaItems.isNotEmpty()

    val selectedAlbumId: String?
        get() = selectedAlbumIds.firstOrNull()
}

internal fun CreatePostDraft.requireAlbumId(): String = albumIds.firstOrNull().orEmpty()

internal fun PhotoFeedItem.toCreateSmallAlbumAppMediaItem(): CreateSmallAlbumAppMediaItem {
    return CreateSmallAlbumAppMediaItem(
        mediaId = mediaId,
        displayName = mediaId,
        mediaType = mediaType,
        palette = palette,
        mediaSource = mediaSource,
    )
}

internal fun CreateSmallAlbumUiState.toDraft(): CreateSmallAlbumDraft? {
    val resolvedAlbumId = selectedAlbumId ?: return null
    return CreateSmallAlbumDraft(
        title = title.trim(),
        summary = summary.trim(),
        displayTimeMillis = displayTimeMillis,
        albumIds = listOf(resolvedAlbumId),
        coverSourceMediaId = selectedCoverSourceMediaId,
    )
}

internal fun PhotoFeedItem.toCreatePostAppMediaItem(): CreatePostAppMediaItem {
    return toCreateSmallAlbumAppMediaItem()
}

typealias CreatePostRoute = CreateSmallAlbumRoute
typealias CreatePostAppMediaItem = CreateSmallAlbumAppMediaItem
typealias CreatePostDraft = CreateSmallAlbumDraft
typealias CreatePostUiState = CreateSmallAlbumUiState
