package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.MediaDto
import com.example.yingshi.data.remote.dto.TrashItemDto
import okhttp3.ResponseBody
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MediaApi {
    @GET("api/media/feed")
    suspend fun getMediaFeed(
    ): ApiEnvelopeDto<List<MediaDto>>

    @GET("api/media/files/{mediaId}")
    suspend fun getMediaFile(
        @Path("mediaId") mediaId: String,
    ): ResponseBody

    @DELETE("api/posts/{postId}/media/{mediaId}")
    suspend fun deleteMediaFromPost(
        @Path("postId") postId: String,
        @Path("mediaId") mediaId: String,
        @Query("deleteMode") deleteMode: String,
    ): ApiEnvelopeDto<TrashItemDto>

    @DELETE("api/media/{mediaId}")
    suspend fun deleteMediaFromSystem(
        @Path("mediaId") mediaId: String,
    ): ApiEnvelopeDto<TrashItemDto>
}
