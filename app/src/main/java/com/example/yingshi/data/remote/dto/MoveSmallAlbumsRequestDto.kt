package com.example.yingshi.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class MoveSmallAlbumsRequestDto(
    val smallAlbumIds: List<String>,
)
