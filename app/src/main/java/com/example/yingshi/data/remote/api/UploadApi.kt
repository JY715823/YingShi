package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.ConfirmUploadRequestDto
import com.example.yingshi.data.remote.dto.CreateUploadTokenRequestDto
import com.example.yingshi.data.remote.dto.UploadTaskDto
import com.example.yingshi.data.remote.dto.UploadTokenDto
import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface UploadApi {
    @POST("v1/uploads/token")
    suspend fun createUploadToken(
        @Body request: CreateUploadTokenRequestDto,
    ): ApiEnvelopeDto<UploadTokenDto>

    @POST("v1/uploads/{uploadId}/confirm")
    suspend fun confirmUpload(
        @Path("uploadId") uploadId: String,
        @Body request: ConfirmUploadRequestDto,
    ): ApiEnvelopeDto<UploadTaskDto>

    @POST("v1/uploads/{uploadId}/cancel")
    suspend fun cancelUpload(
        @Path("uploadId") uploadId: String,
    ): ApiEnvelopeDto<UploadTaskDto>

    @GET("v1/uploads/{uploadId}")
    suspend fun getUploadTask(
        @Path("uploadId") uploadId: String,
    ): ApiEnvelopeDto<UploadTaskDto>
}
