package com.example.yingshi.data.remote.dto

data class MediaDto(
    val mediaId: String,
    val mediaType: String? = null,
    val type: String? = null,
    val url: String? = null,
    val mediaUrl: String? = null,
    val previewUrl: String? = null,
    val thumbnailUrl: String? = null,
    val originalUrl: String? = null,
    val videoUrl: String? = null,
    val coverUrl: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val aspectRatio: Float? = null,
    val durationMillis: Long? = null,
    val duration: Long? = null,
    val displayTimeMillis: Long = 0L,
    val createdAtMillis: Long? = null,
    val postIds: List<String> = emptyList(),
)
