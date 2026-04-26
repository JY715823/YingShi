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
data class PhotoViewerRoute(
    val mediaItems: List<PhotoFeedItem>,
    val initialIndex: Int,
    val sourceLabel: String,
    val showPostSegments: Boolean = false,
)

@Immutable
data class PhotoViewerOverlayUiModel(
    val commentCountLabel: String,
    val timeLabel: String,
    val originalLoadState: OriginalLoadState,
    val relatedPostsLabel: String?,
    val relatedPosts: List<ViewerRelatedPostUiModel>,
    val previewComments: List<CommentUiModel>,
)

enum class OriginalLoadState(
    val label: String,
) {
    NotLoaded(label = "加载原图"),
    Loading(label = "加载中..."),
    Loaded(label = "已加载原图"),
    Failed(label = "加载失败，重试"),
}

@Immutable
data class ViewerRelatedPostUiModel(
    val id: String,
    val title: String,
    val subtitle: String,
)
