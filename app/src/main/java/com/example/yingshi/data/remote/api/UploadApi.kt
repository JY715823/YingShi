package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.ConfirmUploadRequestDto
import com.example.yingshi.data.remote.dto.CreateUploadTokenRequestDto
import com.example.yingshi.data.remote.dto.UploadCompleteResponseDto
import com.example.yingshi.data.remote.dto.UploadTaskDto
import com.example.yingshi.data.remote.dto.UploadTokenDto
import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Part
import okhttp3.MultipartBody

interface UploadApi {
    @POST("api/uploads/token")
    suspend fun createUploadToken(
        @Body request: CreateUploadTokenRequestDto,
    ): ApiEnvelopeDto<UploadTokenDto>

    @Multipart
    @POST("api/uploads/{uploadId}/file")
    suspend fun uploadFile(
        @Path("uploadId") uploadId: String,
        @Part file: MultipartBody.Part,
    ): ApiEnvelopeDto<UploadCompleteResponseDto>

    @GET("api/uploads/{uploadId}")
    suspend fun getUploadTask(
        @Path("uploadId") uploadId: String,
    ): ApiEnvelopeDto<UploadTaskDto>

    @POST("api/uploads/{uploadId}/confirm")
    suspend fun confirmUpload(
        @Path("uploadId") uploadId: String,
        @Body request: ConfirmUploadRequestDto,
    ): ApiEnvelopeDto<UploadTaskDto>

    @POST("api/uploads/{uploadId}/cancel")
    suspend fun cancelUpload(
        @Path("uploadId") uploadId: String,
    ): ApiEnvelopeDto<UploadTaskDto>
}
