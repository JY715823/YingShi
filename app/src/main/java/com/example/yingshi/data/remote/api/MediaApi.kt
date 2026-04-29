package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.MediaDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MediaApi {
    @GET("v1/media/feed")
    suspend fun getMediaFeed(
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("cursor") cursor: String? = null,
        @Query("albumId") albumId: String? = null,
        @Query("updatedAfter") updatedAfter: Long? = null,
    ): ApiEnvelopeDto<List<MediaDto>>

    @GET("v1/media/{mediaId}")
    suspend fun getMediaDetail(
        @Path("mediaId") mediaId: String,
    ): ApiEnvelopeDto<MediaDto>
}
