package com.example.yingshi.data.remote.dto

data class AlbumDto(
    val albumId: String,
    val title: String,
    val subtitle: String,
    val coverMediaId: String? = null,
    val smallAlbumCount: Int = 0,
) {
    val postCount: Int
        get() = smallAlbumCount
}
