package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.CreatePostRequestDto
import com.example.yingshi.data.remote.dto.PostDetailDto
import com.example.yingshi.data.remote.dto.PostDto
import com.example.yingshi.data.remote.dto.PostSummaryDto
import com.example.yingshi.data.remote.dto.SetPostCoverRequestDto
import com.example.yingshi.data.remote.dto.UpdatePostBasicInfoRequestDto
import com.example.yingshi.data.remote.dto.UpdatePostMediaOrderRequestDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PostApi {
    @GET("v1/posts")
    suspend fun getPosts(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
    ): ApiEnvelopeDto<List<PostSummaryDto>>

    @GET("v1/posts/{postId}")
    suspend fun getPostDetail(
        @Path("postId") postId: String,
    ): ApiEnvelopeDto<PostDetailDto>

    @POST("v1/posts")
    suspend fun createPost(
        @Body request: CreatePostRequestDto,
    ): ApiEnvelopeDto<PostDto>

    @PATCH("v1/posts/{postId}")
    suspend fun updatePostBasicInfo(
        @Path("postId") postId: String,
        @Body request: UpdatePostBasicInfoRequestDto,
    ): ApiEnvelopeDto<PostDto>

    @PATCH("v1/posts/{postId}/cover")
    suspend fun setPostCover(
        @Path("postId") postId: String,
        @Body request: SetPostCoverRequestDto,
    ): ApiEnvelopeDto<PostDetailDto>

    @PATCH("v1/posts/{postId}/media-order")
    suspend fun updatePostMediaOrder(
        @Path("postId") postId: String,
        @Body request: UpdatePostMediaOrderRequestDto,
    ): ApiEnvelopeDto<PostDetailDto>
}
