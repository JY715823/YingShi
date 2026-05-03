package com.example.yingshi.feature.photos

import androidx.compose.runtime.Immutable

@Immutable
data class CreatePostRoute(
    val source: String,
    val initialMediaItems: List<SystemMediaItem> = emptyList(),
)

@Immutable
data class CreatePostDraft(
    val title: String,
    val summary: String,
    val displayTimeMillis: Long,
    val albumIds: List<String>,
    val coverSourceMediaId: String? = null,
    val locationLabel: String? = null,
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

internal fun CreatePostUiState.toDraft(): CreatePostDraft {
    return CreatePostDraft(
        title = title.trim(),
        summary = summary.trim(),
        displayTimeMillis = displayTimeMillis,
        albumIds = selectedAlbumIds,
        coverSourceMediaId = selectedCoverSourceMediaId,
        locationLabel = null,
    )
}
