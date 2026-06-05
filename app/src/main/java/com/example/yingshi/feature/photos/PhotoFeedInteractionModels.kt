package com.example.yingshi.feature.photos

import androidx.compose.runtime.Immutable

@Immutable
data class PhotoFeedSelectionState(
    val selectedMediaIds: Set<String> = emptySet(),
    val isInSelectionMode: Boolean = false,
) {
    val selectedCount: Int
        get() = selectedMediaIds.size

    fun contains(mediaId: String): Boolean = selectedMediaIds.contains(mediaId)

    fun enterWith(mediaId: String): PhotoFeedSelectionState {
        return copy(
            selectedMediaIds = setOf(mediaId),
            isInSelectionMode = true,
        )
    }

    fun toggle(mediaId: String): PhotoFeedSelectionState {
        return if (selectedMediaIds.contains(mediaId)) {
            copy(selectedMediaIds = selectedMediaIds - mediaId)
        } else {
            copy(selectedMediaIds = selectedMediaIds + mediaId)
        }
    }

    fun selectMultiple(ids: Set<String>): PhotoFeedSelectionState {
        return copy(selectedMediaIds = selectedMediaIds + ids)
    }

    fun deselectMultiple(ids: Set<String>): PhotoFeedSelectionState {
        return copy(selectedMediaIds = selectedMediaIds - ids)
    }

    fun selectAll(ids: Set<String>): PhotoFeedSelectionState {
        return copy(selectedMediaIds = ids)
    }

    fun clear(): PhotoFeedSelectionState = PhotoFeedSelectionState()

    fun visibleWithin(visibleMediaIds: Set<String>): PhotoFeedSelectionState {
        return copy(
            selectedMediaIds = selectedMediaIds.filterTo(linkedSetOf()) { it in visibleMediaIds },
        )
    }

    fun without(mediaIds: Set<String>): PhotoFeedSelectionState {
        if (mediaIds.isEmpty()) return this
        val remainingIds = selectedMediaIds - mediaIds
        return if (remainingIds.isEmpty()) {
            clear()
        } else {
            copy(selectedMediaIds = remainingIds)
        }
    }
}

@Immutable
data class SelectionNumberFlash(
    val number: Int,
    val nonce: Int,
)

@Immutable
data class PhotoFeedScrubberAnchor(
    val blockKey: String,
    val itemIndex: Int,
    val label: String,
    val timeMillis: Long,
)

@Immutable
data class PhotoFeedScrubberYearMarker(
    val year: Int,
    val progress: Float,
)

@Immutable
data class PhotoViewerRoute(
    val mediaItems: List<PhotoFeedItem>,
    val initialIndex: Int,
    val sourceLabel: String,
    val showSmallAlbumSegments: Boolean = false,
    val sourceSmallAlbumRoute: SmallAlbumDetailRoute? = null,
) {
    val showPostSegments: Boolean
        get() = showSmallAlbumSegments

    val sourcePostRoute: PostDetailPlaceholderRoute?
        get() = sourceSmallAlbumRoute
}

@Immutable
data class PhotoViewerOverlayUiModel(
    val commentCountLabel: String,
    val timeLabel: String,
    val pageLabel: String,
    val originalLoadState: OriginalLoadState,
    val showOriginalAction: Boolean,
    val relatedSmallAlbumsLabel: String?,
    val relatedSmallAlbums: List<ViewerRelatedSmallAlbumUiModel>,
    val previewComments: List<CommentUiModel>,
) {
    val relatedPostsLabel: String?
        get() = relatedSmallAlbumsLabel

    val relatedPosts: List<ViewerRelatedSmallAlbumUiModel>
        get() = relatedSmallAlbums
}

enum class OriginalLoadState {
    NotLoaded,
    Loading,
    Loaded,
    Failed,
}

internal fun OriginalLoadState.actionLabel(): String {
    return when (this) {
        OriginalLoadState.NotLoaded -> "加载原图"
        OriginalLoadState.Loading -> "原图加载中"
        OriginalLoadState.Loaded -> "已加载原图"
        OriginalLoadState.Failed -> "重试原图"
    }
}

@Immutable
data class ViewerRelatedSmallAlbumUiModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val route: SmallAlbumDetailRoute,
)

typealias ViewerRelatedPostUiModel = ViewerRelatedSmallAlbumUiModel
