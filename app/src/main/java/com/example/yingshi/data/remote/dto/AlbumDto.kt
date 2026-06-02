package com.example.yingshi.data.remote.dto

data class AlbumDto(
    val albumId: String,
    val title: String,
    val subtitle: String,
    val coverMediaId: String? = null,
    val systemKey: String? = null,
    val includeInPhotoFeed: Boolean = true,
    val smallAlbumCount: Int = 0,
) {
    val postCount: Int
        get() = smallAlbumCount
}

data class CreateAlbumRequestDto(
    val title: String,
    val subtitle: String = "",
)
