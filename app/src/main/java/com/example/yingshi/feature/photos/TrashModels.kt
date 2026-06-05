package com.example.yingshi.feature.photos

import androidx.compose.runtime.Immutable

enum class TrashEntryType(
    val label: String,
    val summary: String,
) {
    POST_DELETED(
        label = "小相册删除",
        summary = "恢复小相册本体、小相册评论和小相册与媒体关系。",
    ),
    MEDIA_REMOVED(
        label = "媒体移除",
        summary = "只恢复当前小相册与该媒体的关系，不影响媒体本体和媒体评论。",
    ),
    MEDIA_SYSTEM_DELETED(
        label = "媒体删除",
        summary = "恢复媒体本体、被清除的小相册关系和媒体评论入口。",
    ),
}

val TrashCategoryMenuTypes: List<TrashEntryType> = listOf(
    TrashEntryType.MEDIA_SYSTEM_DELETED,
    TrashEntryType.POST_DELETED,
    TrashEntryType.MEDIA_REMOVED,
)

@Immutable
data class TrashEntryUiModel(
    val id: String,
    val type: TrashEntryType,
    val deletedAtMillis: Long,
    val title: String,
    val previewInfo: String,
    val actorUserId: String? = null,
    val sourcePostId: String? = null,
    val sourceMediaId: String? = null,
    val commentTargetMediaId: String? = null,
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
    val mediaType: AppMediaType = AppMediaType.IMAGE,
    val aspectRatio: Float,
    val width: Int? = null,
    val height: Int? = null,
    val videoDurationMillis: Long? = null,
    val mediaSource: AppContentMediaSource? = null,
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

@Immutable
data class TrashPendingCleanupRoute(
    val source: String = "trash-page",
)

fun TrashEntryUiModel.restoreTargetMediaIds(): List<String> {
    return buildList {
        sourceMediaId?.takeIf { it.isNotBlank() }?.let(::add)
        mediaSnapshot?.mediaId?.takeIf { it.isNotBlank() }?.let(::add)
        postSnapshot?.mediaSnapshots
            ?.map { it.mediaId }
            ?.filter { it.isNotBlank() }
            ?.let(::addAll)
        relationSnapshots
            .map { it.mediaSnapshot.mediaId }
            .filter { it.isNotBlank() }
            .let(::addAll)
        addAll(relatedMediaIds.filter { it.isNotBlank() })
    }.distinct()
}
