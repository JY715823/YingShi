package com.example.yingshi.data.model

data class RemoteMediaAccess(
    val variant: String,
    val url: String?,
    val signedUrl: String?,
    val expiresAtMillis: Long?,
    val cacheKey: String?,
    val revision: String?,
) {
    val requestUrl: String?
        get() = url?.takeIf { it.isNotBlank() } ?: signedUrl?.takeIf { it.isNotBlank() }
}

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
    val smallAlbumIds: List<String>,
    val thumbnailUrl: String? = null,
    val mediaUrl: String? = null,
    val coverUrl: String? = null,
    val mimeType: String? = null,
    val durationMillis: Long? = null,
    val createdAtMillis: Long? = null,
    val capturedAtMillis: Long? = null,
    val importedAtMillis: Long? = null,
    val displayTimeSource: String? = null,
    val recordOwnerUserId: String? = null,
    val uploadedByUserId: String? = null,
    val access: List<RemoteMediaAccess> = emptyList(),
) {
    val postIds: List<String>
        get() = smallAlbumIds
}

data class RemoteMediaFeedPage(
    val items: List<RemoteMedia>,
    val nextCursor: String?,
    val hasMore: Boolean,
)

data class RemoteMediaImportStatus(
    val sourceFingerprint: String,
    val mediaId: String,
    val smallAlbumIds: List<String>,
)

data class RemoteAlbum(
    val albumId: String,
    val title: String,
    val subtitle: String,
    val coverMediaId: String?,
    val smallAlbumCount: Int,
    val systemKey: String? = null,
    val includeInPhotoFeed: Boolean = true,
) {
    val postCount: Int
        get() = smallAlbumCount

    val isSystemManaged: Boolean
        get() = !systemKey.isNullOrBlank()
}

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
    val actorUserId: String? = null,
    val sourceSmallAlbumId: String?,
    val sourceMediaId: String?,
    val commentTargetMediaId: String?,
    val title: String,
    val previewInfo: String,
    val deletedAtMillis: Long,
    val relatedSmallAlbumIds: List<String>,
    val relatedMediaIds: List<String>,
    val sourceMediaType: String? = null,
    val sourceMediaWidth: Int? = null,
    val sourceMediaHeight: Int? = null,
    val sourceMediaAspectRatio: Float? = null,
    val sourceMediaDurationMillis: Long? = null,
    val sourceMediaMimeType: String? = null,
){
    val sourcePostId: String?
        get() = sourceSmallAlbumId

    val relatedPostIds: List<String>
        get() = relatedSmallAlbumIds
}

data class RemoteUploadToken(
    val uploadId: String,
    val provider: String,
    val uploadUrl: String,
    val expireAtMillis: Long,
    val state: String,
    val uploadMethod: String = "multipart",
    val objectKey: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val confirmUrl: String? = null,
)
