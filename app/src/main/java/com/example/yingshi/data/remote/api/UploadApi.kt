package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.CompleteUploadRequestDto
import com.example.yingshi.data.remote.dto.UploadTokenDto
import com.example.yingshi.data.remote.dto.UploadTokenRequestDto
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface UploadApi {
    @POST("v1/uploads/token")
    suspend fun requestUploadToken(
        @Body request: UploadTokenRequestDto,
    ): ApiEnvelopeDto<UploadTokenDto>

    @POST("v1/uploads/{uploadId}/complete")
    suspend fun completeUpload(
        @Path("uploadId") uploadId: String,
        @Body request: CompleteUploadRequestDto,
    ): ApiEnvelopeDto<Unit>
}
