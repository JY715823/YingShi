package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.AlbumDto
import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.PostSummaryDto
import retrofit2.http.GET
import retrofit2.http.Path

interface AlbumApi {
    @GET("api/albums")
    suspend fun getAlbums(): ApiEnvelopeDto<List<AlbumDto>>

    @GET("api/albums/{albumId}/posts")
    suspend fun getAlbumPosts(
        @Path("albumId") albumId: String,
    ): ApiEnvelopeDto<List<PostSummaryDto>>
}
