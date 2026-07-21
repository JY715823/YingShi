package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.AddSmallAlbumMediaRequestDto
import com.example.yingshi.data.remote.dto.CreateSmallAlbumRequestDto
import com.example.yingshi.data.remote.dto.SmallAlbumDetailDto
import com.example.yingshi.data.remote.dto.SmallAlbumSummaryDto
import com.example.yingshi.data.remote.dto.SetSmallAlbumCoverRequestDto
import com.example.yingshi.data.remote.dto.TrashItemDto
import com.example.yingshi.data.remote.dto.UpdateSmallAlbumBasicInfoRequestDto
import com.example.yingshi.data.remote.dto.UpdateSmallAlbumMediaBatchRequestDto
import com.example.yingshi.data.remote.dto.UpdateSmallAlbumMediaOrderRequestDto
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.DELETE
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Path

interface SmallAlbumApi {
    @GET("api/small-albums")
    suspend fun getPosts(): ApiEnvelopeDto<List<SmallAlbumSummaryDto>>

    @GET("api/small-albums/{smallAlbumId}")
    suspend fun getPostDetail(
        @Path("smallAlbumId") smallAlbumId: String,
    ): ApiEnvelopeDto<SmallAlbumDetailDto>

    @POST("api/small-albums")
    suspend fun createPost(
        @Body request: CreateSmallAlbumRequestDto,
    ): ApiEnvelopeDto<SmallAlbumDetailDto>

    @PATCH("api/small-albums/{smallAlbumId}")
    suspend fun updatePostBasicInfo(
        @Path("smallAlbumId") smallAlbumId: String,
        @Body request: UpdateSmallAlbumBasicInfoRequestDto,
    ): ApiEnvelopeDto<SmallAlbumDetailDto>

    @PATCH("api/small-albums/{smallAlbumId}/cover")
    suspend fun setPostCover(
        @Path("smallAlbumId") smallAlbumId: String,
        @Body request: SetSmallAlbumCoverRequestDto,
    ): ApiEnvelopeDto<SmallAlbumDetailDto>

    @PATCH("api/small-albums/{smallAlbumId}/media-order")
    suspend fun updatePostMediaOrder(
        @Path("smallAlbumId") smallAlbumId: String,
        @Body request: UpdateSmallAlbumMediaOrderRequestDto,
    ): ApiEnvelopeDto<SmallAlbumDetailDto>

    @PATCH("api/small-albums/{smallAlbumId}/media-batch")
    suspend fun updatePostMediaBatch(
        @Path("smallAlbumId") smallAlbumId: String,
        @Body request: UpdateSmallAlbumMediaBatchRequestDto,
    ): ApiEnvelopeDto<SmallAlbumDetailDto>

    @POST("api/small-albums/{smallAlbumId}/media")
    suspend fun addMediaToPost(
        @Path("smallAlbumId") smallAlbumId: String,
        @Body request: AddSmallAlbumMediaRequestDto,
    ): ApiEnvelopeDto<SmallAlbumDetailDto>

    @DELETE("api/small-albums/{smallAlbumId}")
    suspend fun deleteSmallAlbum(
        @Path("smallAlbumId") smallAlbumId: String,
    ): ApiEnvelopeDto<TrashItemDto>
}

