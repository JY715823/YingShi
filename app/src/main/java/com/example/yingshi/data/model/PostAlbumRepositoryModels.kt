package com.example.yingshi.data.model

data class RemotePostSummary(
    val postId: String,
    val title: String,
    val summary: String,
    val contributorLabel: String?,
    val creatorUserId: String? = null,
    val participantUserIds: List<String> = emptyList(),
    val displayTimeMillis: Long,
    val eventStartedAtMillis: Long? = null,
    val eventEndedAtMillis: Long? = null,
    val displayTimeSource: String? = null,
    val albumId: String,
    val coverMediaId: String?,
    val mediaCount: Int,
) {
    val smallAlbumId: String
        get() = postId

    val albumIds: List<String>
        get() = listOf(albumId)

    val selectedAlbumId: String
        get() = albumId
}

data class RemotePostMedia(
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
    val isCover: Boolean,
    val videoDurationMillis: Long?,
    val thumbnailUrl: String? = null,
    val mediaUrl: String? = null,
    val coverUrl: String? = null,
    val mimeType: String? = null,
    val createdAtMillis: Long? = null,
    val capturedAtMillis: Long? = null,
    val importedAtMillis: Long? = null,
    val displayTimeSource: String? = null,
    val uploadedByUserId: String? = null,
    val access: List<RemoteMediaAccess> = emptyList(),
)

data class RemotePostDetail(
    val postId: String,
    val title: String,
    val summary: String,
    val contributorLabel: String?,
    val creatorUserId: String? = null,
    val participantUserIds: List<String> = emptyList(),
    val displayTimeMillis: Long,
    val eventStartedAtMillis: Long? = null,
    val eventEndedAtMillis: Long? = null,
    val displayTimeSource: String? = null,
    val albumId: String,
    val coverMediaId: String?,
    val mediaItems: List<RemotePostMedia>,
) {
    val smallAlbumId: String
        get() = postId

    val albumIds: List<String>
        get() = listOf(albumId)

    val selectedAlbumId: String
        get() = albumId
}

data class CreatePostPayload(
    val title: String,
    val summary: String,
    val displayTimeMillis: Long,
    val eventStartedAtMillis: Long? = displayTimeMillis,
    val eventEndedAtMillis: Long? = null,
    val displayTimeSource: String? = "MANUAL",
    val albumId: String,
    val initialMediaIds: List<String> = emptyList(),
    val coverMediaId: String? = null,
) {
    val albumIds: List<String>
        get() = listOf(albumId)
}

data class UpdatePostBasicInfoPayload(
    val title: String,
    val summary: String,
    val displayTimeMillis: Long,
    val eventStartedAtMillis: Long? = displayTimeMillis,
    val eventEndedAtMillis: Long? = null,
    val displayTimeSource: String? = "MANUAL",
    val albumId: String,
) {
    val albumIds: List<String>
        get() = listOf(albumId)
}

data class UpdatePostAlbumsPayload(
    val albumId: String,
) {
    val albumIds: List<String>
        get() = listOf(albumId)
}

data class CreateAlbumPayload(
    val title: String,
    val subtitle: String = "",
)
