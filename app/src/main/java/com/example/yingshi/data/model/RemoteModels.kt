package com.example.yingshi.data.model

data class RemoteMedia(
    val mediaId: String,
    val mediaType: String,
    val previewUrl: String?,
    val originalUrl: String?,
    val videoUrl: String?,
    val width: Int?,
    val height: Int?,
    val aspectRatio: Float?,
    val displayTimeMillis: Long,
    val commentCount: Int,
    val postIds: List<String>,
)

data class RemoteAlbum(
    val albumId: String,
    val title: String,
    val subtitle: String,
    val coverMediaId: String?,
    val postCount: Int,
)

data class RemoteComment(
    val commentId: String,
    val targetType: String,
    val targetId: String,
    val authorId: String?,
    val authorName: String,
    val content: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long?,
    val isDeleted: Boolean,
)

data class RemoteTrashItem(
    val trashItemId: String,
    val itemType: String,
    val state: String?,
    val sourcePostId: String?,
    val sourceMediaId: String?,
    val title: String,
    val previewInfo: String,
    val deletedAtMillis: Long,
    val relatedPostIds: List<String>,
    val relatedMediaIds: List<String>,
)

data class RemoteUploadToken(
    val uploadId: String,
    val provider: String,
    val uploadUrl: String,
    val expireAtMillis: Long,
    val state: String,
)
