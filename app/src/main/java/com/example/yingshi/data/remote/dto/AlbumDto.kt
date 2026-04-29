package com.example.yingshi.data.remote.dto

data class AlbumDto(
    val albumId: String,
    val title: String,
    val subtitle: String,
    val coverMediaId: String? = null,
    val postCount: Int = 0,
)
