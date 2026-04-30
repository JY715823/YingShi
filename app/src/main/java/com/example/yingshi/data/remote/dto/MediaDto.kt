package com.example.yingshi.data.remote.dto

data class MediaDto(
    val mediaId: String,
    val mediaType: String,
    val url: String? = null,
    val previewUrl: String? = null,
    val originalUrl: String? = null,
    val videoUrl: String? = null,
    val coverUrl: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val aspectRatio: Float? = null,
    val durationMillis: Long? = null,
    val displayTimeMillis: Long,
    val postIds: List<String> = emptyList(),
)
