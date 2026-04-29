package com.example.yingshi.data.model

data class RemotePostSummary(
    val postId: String,
    val title: String,
    val summary: String,
    val contributorLabel: String?,
    val displayTimeMillis: Long,
    val albumIds: List<String>,
    val coverMediaId: String?,
    val mediaCount: Int,
)

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
)

data class RemotePostDetail(
    val postId: String,
    val title: String,
    val summary: String,
    val contributorLabel: String?,
    val displayTimeMillis: Long,
    val albumIds: List<String>,
    val coverMediaId: String?,
    val mediaItems: List<RemotePostMedia>,
)

data class CreatePostPayload(
    val title: String,
    val summary: String,
    val displayTimeMillis: Long,
    val albumIds: List<String>,
    val initialMediaIds: List<String> = emptyList(),
)

data class UpdatePostBasicInfoPayload(
    val title: String,
    val summary: String,
    val displayTimeMillis: Long,
    val albumIds: List<String>,
)

data class UpdatePostAlbumsPayload(
    val albumIds: List<String>,
)
