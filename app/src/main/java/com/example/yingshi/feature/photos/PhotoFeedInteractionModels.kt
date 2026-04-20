package com.example.yingshi.feature.photos

import androidx.compose.runtime.Immutable

@Immutable
data class PhotoFeedSelectionState(
    val selectedMediaIds: Set<String> = emptySet(),
) {
    val isInSelectionMode: Boolean
        get() = selectedMediaIds.isNotEmpty()

    val selectedCount: Int
        get() = selectedMediaIds.size

    fun contains(mediaId: String): Boolean = selectedMediaIds.contains(mediaId)

    fun enterWith(mediaId: String): PhotoFeedSelectionState {
        return copy(selectedMediaIds = setOf(mediaId))
    }

    fun toggle(mediaId: String): PhotoFeedSelectionState {
        return if (selectedMediaIds.contains(mediaId)) {
            copy(selectedMediaIds = selectedMediaIds - mediaId)
        } else {
            copy(selectedMediaIds = selectedMediaIds + mediaId)
        }
    }

    fun clear(): PhotoFeedSelectionState = PhotoFeedSelectionState()
}

@Immutable
data class PhotoFeedScrubberAnchor(
    val blockKey: String,
    val itemIndex: Int,
    val label: String,
)

@Immutable
data class PhotoViewerPlaceholderRoute(
    val mediaId: String,
    val mediaPosition: Int,
    val mediaCount: Int,
    val densityLabel: String,
)
