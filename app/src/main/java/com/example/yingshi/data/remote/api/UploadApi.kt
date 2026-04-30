package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.CreateUploadTokenRequestDto
import com.example.yingshi.data.remote.dto.UploadCompleteResponseDto
import com.example.yingshi.data.remote.dto.UploadTokenDto
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
}
