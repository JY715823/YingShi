package com.example.yingshi.data.remote.dto

data class TrashItemDto(
    val trashItemId: String,
    val itemType: String,
    val sourcePostId: String? = null,
    val sourceMediaId: String? = null,
    val title: String,
    val previewInfo: String,
    val deletedAtMillis: Long,
    val relatedPostIds: List<String> = emptyList(),
    val relatedMediaIds: List<String> = emptyList(),
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

data class RestoreRequestDto(
    val restoreRelations: Boolean = true,
    val operatorNote: String? = null,
)
