package com.example.yingshi.data.remote.dto

data class MediaDto(
    val mediaId: String,
    val mediaType: String,
    val previewUrl: String? = null,
    val originalUrl: String? = null,
    val videoUrl: String? = null,
    val coverUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val aspectRatio: Float? = null,
    val displayTimeMillis: Long,
    val commentCount: Int = 0,
    val postIds: List<String> = emptyList(),
)

data class DeleteMediaRequestDto(
    val mediaIds: List<String>,
    val deleteScope: String,
    val sourcePostId: String? = null,
    val moveToTrash: Boolean = true,
    val operatorNote: String? = null,
)
