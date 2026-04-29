package com.example.yingshi.data.remote.dto

data class AlbumDto(
    val albumId: String,
    val title: String,
    val subtitle: String,
    val coverMediaId: String? = null,
    val postCount: Int = 0,
)

data class PostDto(
    val postId: String,
    val title: String,
    val summary: String,
    val contributorLabel: String? = null,
    val displayTimeMillis: Long,
    val albumIds: List<String> = emptyList(),
    val coverMediaId: String? = null,
    val mediaItems: List<MediaDto> = emptyList(),
)
