package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.MediaImportStatusDto
import com.example.yingshi.data.remote.dto.MediaImportStatusRequestDto
import com.example.yingshi.data.remote.dto.MediaDto
import com.example.yingshi.data.remote.dto.TrashItemDto
import okhttp3.ResponseBody
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Path
import retrofit2.http.Query

interface MediaApi {
    @GET("api/media/feed")
    suspend fun getMediaFeed(
        @Query("cursor") cursor: String? = null,
        @Query("pageSize") pageSize: Int? = null,
    ): ApiEnvelopeDto<List<MediaDto>>

    @POST("api/media/import-status")
    suspend fun getImportStatus(
        @Body request: MediaImportStatusRequestDto,
    ): ApiEnvelopeDto<List<MediaImportStatusDto>>

    @GET("api/media/files/{mediaId}")
    suspend fun getMediaFile(
        @Path("mediaId") mediaId: String,
    ): ResponseBody

    @DELETE("api/small-albums/{smallAlbumId}/media/{mediaId}")
    suspend fun deleteMediaFromPost(
        @Path("smallAlbumId") smallAlbumId: String,
        @Path("mediaId") mediaId: String,
        @Query("deleteMode") deleteMode: String,
    ): ApiEnvelopeDto<TrashItemDto>

    @DELETE("api/media/{mediaId}")
    suspend fun deleteMediaFromSystem(
        @Path("mediaId") mediaId: String,
    ): ApiEnvelopeDto<TrashItemDto>
}
