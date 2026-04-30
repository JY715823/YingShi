package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.AddPostMediaRequestDto
import com.example.yingshi.data.remote.dto.CreatePostRequestDto
import com.example.yingshi.data.remote.dto.PostDetailDto
import com.example.yingshi.data.remote.dto.SetPostCoverRequestDto
import com.example.yingshi.data.remote.dto.TrashItemDto
import com.example.yingshi.data.remote.dto.UpdatePostBasicInfoRequestDto
import com.example.yingshi.data.remote.dto.UpdatePostMediaOrderRequestDto
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.DELETE
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Path

interface PostApi {
    @GET("api/posts/{postId}")
    suspend fun getPostDetail(
        @Path("postId") postId: String,
    ): ApiEnvelopeDto<PostDetailDto>

    @POST("api/posts")
    suspend fun createPost(
        @Body request: CreatePostRequestDto,
    ): ApiEnvelopeDto<PostDetailDto>

    @PATCH("api/posts/{postId}")
    suspend fun updatePostBasicInfo(
        @Path("postId") postId: String,
        @Body request: UpdatePostBasicInfoRequestDto,
    ): ApiEnvelopeDto<PostDetailDto>

    @PATCH("api/posts/{postId}/cover")
    suspend fun setPostCover(
        @Path("postId") postId: String,
        @Body request: SetPostCoverRequestDto,
    ): ApiEnvelopeDto<PostDetailDto>

    @PATCH("api/posts/{postId}/media-order")
    suspend fun updatePostMediaOrder(
        @Path("postId") postId: String,
        @Body request: UpdatePostMediaOrderRequestDto,
    ): ApiEnvelopeDto<PostDetailDto>

    @POST("api/posts/{postId}/media")
    suspend fun addMediaToPost(
        @Path("postId") postId: String,
        @Body request: AddPostMediaRequestDto,
    ): ApiEnvelopeDto<PostDetailDto>

    @DELETE("api/posts/{postId}")
    suspend fun deletePost(
        @Path("postId") postId: String,
    ): ApiEnvelopeDto<TrashItemDto>
}
