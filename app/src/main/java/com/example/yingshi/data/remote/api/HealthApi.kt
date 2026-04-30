package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import retrofit2.http.GET

data class HealthStatusDto(
    val status: String,
    val application: String,
)

interface HealthApi {
    @GET("api/health")
    suspend fun getHealth(): ApiEnvelopeDto<HealthStatusDto>
}
