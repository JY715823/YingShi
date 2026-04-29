package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.AlbumDto
import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.PostDto
import com.example.yingshi.data.remote.dto.PostSummaryDto
import com.example.yingshi.data.remote.dto.UpdatePostAlbumsRequestDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface AlbumApi {
    @GET("v1/albums")
    suspend fun getAlbums(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
    ): ApiEnvelopeDto<List<AlbumDto>>

    @GET("v1/albums/{albumId}/posts")
    suspend fun getAlbumPosts(
        @Path("albumId") albumId: String,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
    ): ApiEnvelopeDto<List<PostSummaryDto>>

    @PATCH("v1/posts/{postId}/albums")
    suspend fun updatePostAlbums(
        @Path("postId") postId: String,
        @Body request: UpdatePostAlbumsRequestDto,
    ): ApiEnvelopeDto<PostDto>
}
