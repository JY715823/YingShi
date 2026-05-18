package com.example.yingshi.feature.photos

import androidx.compose.runtime.Immutable

@Immutable
data class CreatePostRoute(
    val source: String,
    val initialMediaItems: List<SystemMediaItem> = emptyList(),
    val initialAppMediaIds: List<String> = emptyList(),
    val initialAppMediaItems: List<CreatePostAppMediaItem> = emptyList(),
)

@Immutable
data class CreatePostAppMediaItem(
    val mediaId: String,
    val displayName: String,
    val mediaType: AppMediaType = AppMediaType.IMAGE,
    val palette: PhotoThumbnailPalette,
    val mediaSource: AppContentMediaSource? = null,
)

@Immutable
data class CreatePostDraft(
    val title: String,
    val summary: String,
    val displayTimeMillis: Long,
    val albumIds: List<String>,
    val coverSourceMediaId: String? = null,
)

@Immutable
data class CreatePostUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val tokenMissing: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val albums: List<AlbumSummaryUiModel> = emptyList(),
    val title: String = "",
    val summary: String = "",
    val displayTimeMillis: Long = System.currentTimeMillis(),
    val selectedAlbumIds: List<String> = emptyList(),
    val initialMediaItems: List<SystemMediaItem> = emptyList(),
    val selectedCoverSourceMediaId: String? = null,
) {
    val hasInitialMedia: Boolean
        get() = initialMediaItems.isNotEmpty()
}

internal fun PhotoFeedItem.toCreatePostAppMediaItem(): CreatePostAppMediaItem {
    return CreatePostAppMediaItem(
        mediaId = mediaId,
        displayName = mediaId,
        mediaType = mediaType,
        palette = palette,
        mediaSource = mediaSource,
    )
}

internal fun CreatePostUiState.toDraft(): CreatePostDraft {
    return CreatePostDraft(
        title = title.trim(),
        summary = summary.trim(),
        displayTimeMillis = displayTimeMillis,
        albumIds = selectedAlbumIds,
        coverSourceMediaId = selectedCoverSourceMediaId,
    )
}
