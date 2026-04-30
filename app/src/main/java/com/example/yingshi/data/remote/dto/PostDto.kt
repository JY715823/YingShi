package com.example.yingshi.data.remote.dto

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
    val mediaCount: Int = 0,
    val mediaItems: List<PostMediaDto> = emptyList(),
)

data class PostMediaDto(
    val sortOrder: Int = 0,
    val isCover: Boolean = false,
    val media: MediaDto,
)

data class CreatePostRequestDto(
    val title: String,
    val summary: String,
    val contributorLabel: String? = null,
    val displayTimeMillis: Long,
    val albumIds: List<String>,
    val initialMediaIds: List<String> = emptyList(),
    val coverMediaId: String? = null,
)

data class UpdatePostBasicInfoRequestDto(
    val title: String,
    val summary: String,
    val contributorLabel: String? = null,
    val displayTimeMillis: Long,
    val albumIds: List<String>,
)

data class SetPostCoverRequestDto(
    val coverMediaId: String,
)

data class UpdatePostMediaOrderRequestDto(
    val orderedMediaIds: List<String>,
)

data class AddPostMediaRequestDto(
    val mediaIds: List<String>,
    val coverMediaId: String? = null,
)
