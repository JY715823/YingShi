package com.example.yingshi.data.remote.dto

data class PostDto(
    val postId: String,
    val title: String,
    val summary: String,
    val contributorLabel: String? = null,
    val displayTimeMillis: Long,
    val albumIds: List<String> = emptyList(),
    val coverMediaId: String? = null,
    val mediaCount: Int = 0,
)

data class PostSummaryDto(
    val postId: String,
    val title: String,
    val summary: String,
    val contributorLabel: String? = null,
    val displayTimeMillis: Long,
    val albumIds: List<String> = emptyList(),
    val coverMediaId: String? = null,
    val mediaCount: Int = 0,
)

data class PostDetailDto(
    val postId: String,
    val title: String,
    val summary: String,
    val contributorLabel: String? = null,
    val displayTimeMillis: Long,
    val albumIds: List<String> = emptyList(),
    val coverMediaId: String? = null,
    val mediaItems: List<PostMediaDto> = emptyList(),
)

data class PostMediaDto(
    val mediaId: String,
    val mediaType: String,
    val previewUrl: String? = null,
    val originalUrl: String? = null,
    val videoUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val aspectRatio: Float? = null,
    val displayTimeMillis: Long,
    val commentCount: Int = 0,
    val isCover: Boolean = false,
    val videoDurationMillis: Long? = null,
)

data class CreatePostRequestDto(
    val title: String,
    val summary: String,
    val displayTimeMillis: Long,
    val albumIds: List<String>,
    val initialMediaIds: List<String> = emptyList(),
)

data class UpdatePostBasicInfoRequestDto(
    val title: String,
    val summary: String,
    val displayTimeMillis: Long,
    val albumIds: List<String>,
)

data class SetPostCoverRequestDto(
    val coverMediaId: String,
)

data class UpdatePostMediaOrderRequestDto(
    val orderedMediaIds: List<String>,
)

data class UpdatePostAlbumsRequestDto(
    val albumIds: List<String>,
)
