package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.DeleteMediaRequestDto
import com.example.yingshi.data.remote.dto.MediaDto
import com.example.yingshi.data.remote.dto.TrashItemDto
import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.HTTP
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

    @HTTP(method = "DELETE", path = "v1/posts/{postId}/media", hasBody = true)
    suspend fun deleteMediaFromPost(
        @Path("postId") postId: String,
        @Body request: DeleteMediaRequestDto,
    ): ApiEnvelopeDto<List<TrashItemDto>>

    @HTTP(method = "DELETE", path = "v1/media", hasBody = true)
    suspend fun deleteMediaFromSystem(
        @Body request: DeleteMediaRequestDto,
    ): ApiEnvelopeDto<List<TrashItemDto>>
}
