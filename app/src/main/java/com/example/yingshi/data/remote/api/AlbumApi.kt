package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.AlbumDto
import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.CreateAlbumRequestDto
import com.example.yingshi.data.remote.dto.PostSummaryDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST

interface AlbumApi {
    @POST("api/albums")
    suspend fun createAlbum(
        @Body request: CreateAlbumRequestDto,
    ): ApiEnvelopeDto<AlbumDto>

    @GET("api/albums")
    suspend fun getAlbums(): ApiEnvelopeDto<List<AlbumDto>>

    @GET("api/albums/{albumId}/small-albums")
    suspend fun getAlbumPosts(
        @Path("albumId") albumId: String,
    ): ApiEnvelopeDto<List<PostSummaryDto>>
}
