package com.example.yingshi.feature.photos

import androidx.compose.runtime.Immutable

enum class TrashEntryType(
    val label: String,
    val summary: String,
) {
    POST_DELETED(
        label = "帖子删除",
        summary = "恢复帖子本体、帖子评论和帖子与媒体关系。",
    ),
    MEDIA_REMOVED(
        label = "媒体移除",
        summary = "只恢复当前帖子与该媒体的关系，不影响媒体本体和媒体评论。",
    ),
    MEDIA_SYSTEM_DELETED(
        label = "媒体系统删",
        summary = "恢复媒体本体、被清除的帖子关系和媒体评论入口。",
    ),
}

@Immutable
data class TrashEntryUiModel(
    val id: String,
    val type: TrashEntryType,
    val deletedAtMillis: Long,
    val title: String,
    val previewInfo: String,
    val sourcePostId: String? = null,
    val sourceMediaId: String? = null,
    val relatedPostIds: List<String> = emptyList(),
    val relatedMediaIds: List<String> = emptyList(),
    val postSnapshot: TrashPostSnapshot? = null,
    val mediaSnapshot: TrashMediaSnapshot? = null,
    val relationSnapshots: List<TrashPostRelationSnapshot> = emptyList(),
    val palette: PhotoThumbnailPalette,
)

@Immutable
data class TrashPostSnapshot(
    val post: AlbumPostCardUiModel,
    val mediaSnapshots: List<TrashMediaSnapshot>,
)

@Immutable
data class TrashMediaSnapshot(
    val mediaId: String,
    val displayTimeMillis: Long,
    val palette: PhotoThumbnailPalette,
    val aspectRatio: Float,
    val isCover: Boolean = false,
    val sourcePostId: String? = null,
    val sourcePostTitle: String? = null,
)

@Immutable
data class TrashPostRelationSnapshot(
    val postId: String,
    val postTitle: String,
    val mediaSnapshot: TrashMediaSnapshot,
)

@Immutable
data class TrashPendingCleanupUiModel(
    val entry: TrashEntryUiModel,
    val removedAtMillis: Long,
)

@Immutable
data class TrashMutationResult(
    val success: Boolean,
    val message: String,
)

@Immutable
data class TrashSnackbarMessageUiModel(
    val entryId: String,
    val message: String,
)

@Immutable
data class TrashDetailRoute(
    val entryId: String,
    val entryType: TrashEntryType? = null,
    val sourcePostId: String? = null,
    val sourceMediaId: String? = null,
)
