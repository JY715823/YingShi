package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.CommentDto
import com.example.yingshi.data.remote.dto.CommentListResponseDto
import com.example.yingshi.data.remote.dto.CreateCommentRequestDto
import com.example.yingshi.data.remote.dto.UpdateCommentRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CommentApi {
    @GET("v1/posts/{postId}/comments")
    suspend fun getPostComments(
        @Path("postId") postId: String,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
    ): ApiEnvelopeDto<CommentListResponseDto>

    @GET("v1/media/{mediaId}/comments")
    suspend fun getMediaComments(
        @Path("mediaId") mediaId: String,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
    ): ApiEnvelopeDto<CommentListResponseDto>

    @POST("v1/posts/{postId}/comments")
    suspend fun createPostComment(
        @Path("postId") postId: String,
        @Body request: CreateCommentRequestDto,
    ): ApiEnvelopeDto<CommentDto>

    @POST("v1/media/{mediaId}/comments")
    suspend fun createMediaComment(
        @Path("mediaId") mediaId: String,
        @Body request: CreateCommentRequestDto,
    ): ApiEnvelopeDto<CommentDto>

    @PATCH("v1/comments/{commentId}")
    suspend fun updateComment(
        @Path("commentId") commentId: String,
        @Body request: UpdateCommentRequestDto,
    ): ApiEnvelopeDto<CommentDto>

    @DELETE("v1/comments/{commentId}")
    suspend fun deleteComment(
        @Path("commentId") commentId: String,
    ): ApiEnvelopeDto<Unit>
}
