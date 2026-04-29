package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.AlbumDto
import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.PostDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface PostApi {
    @GET("v1/albums")
    suspend fun getAlbums(
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("cursor") cursor: String? = null,
    ): ApiEnvelopeDto<List<AlbumDto>>

    @GET("v1/posts")
    suspend fun getPosts(
        @Query("albumId") albumId: String? = null,
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("cursor") cursor: String? = null,
    ): ApiEnvelopeDto<List<PostDto>>

    @GET("v1/posts/{postId}")
    suspend fun getPostDetail(
        @Path("postId") postId: String,
    ): ApiEnvelopeDto<PostDto>
}
