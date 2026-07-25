package com.example.yingshi.data.remote.dto

data class TrashItemDto(
    val trashItemId: String,
    val itemType: String,
    val state: String? = null,
    val actorUserId: String? = null,
    val sourceSmallAlbumId: String? = null,
    val sourceMediaId: String? = null,
    val commentTargetMediaId: String? = null,
    val title: String,
    val previewInfo: String,
    val deletedAtMillis: Long,
    val relatedSmallAlbumIds: List<String> = emptyList(),
    val relatedMediaIds: List<String> = emptyList(),
    val sourceMediaType: String? = null,
    val sourceMediaWidth: Int? = null,
    val sourceMediaHeight: Int? = null,
    val sourceMediaAspectRatio: Float? = null,
    val sourceMediaDurationMillis: Long? = null,
    val sourceMediaMimeType: String? = null,
    // P1-2 改造: life 回收站分类 (PERSON/MEAL/null); null 表示照片回收站
    val lifeCategory: String? = null,
) {
    val sourcePostId: String?
        get() = sourceSmallAlbumId

    val relatedPostIds: List<String>
        get() = relatedSmallAlbumIds
}

data class TrashPageResponseDto(
    val items: List<TrashItemDto> = emptyList(),
    val page: Int = 1,
    val size: Int = 10,
    val totalElements: Long = 0,
    val hasMore: Boolean = false,
)

data class TrashDetailDto(
    val item: TrashItemDto,
    val canRestore: Boolean = true,
    val canMoveOutOfTrash: Boolean = true,
    val pendingCleanup: PendingCleanupDto? = null,
)

data class PendingCleanupDto(
    val trashItemId: String,
    val removedAtMillis: Long,
    val undoDeadlineMillis: Long,
    val item: TrashItemDto,
)
