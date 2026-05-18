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
    val thumbnailUrl: String? = null,
    val mediaUrl: String? = null,
    val coverUrl: String? = null,
    val mimeType: String? = null,
    val durationMillis: Long? = null,
    val createdAtMillis: Long? = null,
    val capturedAtMillis: Long? = null,
    val importedAtMillis: Long? = null,
    val displayTimeSource: String? = null,
)

data class RemoteMediaFeedPage(
    val items: List<RemoteMedia>,
    val nextCursor: String?,
    val hasMore: Boolean,
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
    val commentTargetMediaId: String?,
    val title: String,
    val previewInfo: String,
    val deletedAtMillis: Long,
    val relatedPostIds: List<String>,
    val relatedMediaIds: List<String>,
    val sourceMediaType: String? = null,
    val sourceMediaWidth: Int? = null,
    val sourceMediaHeight: Int? = null,
    val sourceMediaAspectRatio: Float? = null,
    val sourceMediaDurationMillis: Long? = null,
    val sourceMediaMimeType: String? = null,
)

data class RemoteUploadToken(
    val uploadId: String,
    val provider: String,
    val uploadUrl: String,
    val expireAtMillis: Long,
    val state: String,
)
