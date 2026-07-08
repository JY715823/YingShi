package com.example.yingshi.feature.chat.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Streaming

interface ChatMediaApi {
    @Multipart
    @POST("api/chat/imported/media/upload")
    suspend fun uploadMedia(
        @Part file: MultipartBody.Part,
        @Part("chatStableKey") chatStableKey: RequestBody,
        @Part("md5") md5: RequestBody? = null,
    ): Map<String, String>

    @Streaming
    @GET("api/chat/imported/media/{key}")
    suspend fun downloadMedia(
        @Path("key", encoded = true) key: String,
    ): ResponseBody

    @HEAD("api/chat/imported/media/{key}")
    suspend fun checkMediaExists(
        @Path("key", encoded = true) key: String,
    ): Response<Unit>
}
