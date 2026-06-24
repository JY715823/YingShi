package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import retrofit2.http.GET

data class SyncVersionsDto(
    val photoFeedVersion: Long,
    val albumsVersion: Long,
    val trashVersion: Long,
    val notificationVersion: Long = 0L,
    val lifeConsoleVersion: Long,
    val serverTimeMillis: Long,
)

interface SyncApi {
    @GET("api/sync/versions")
    suspend fun getVersions(): ApiEnvelopeDto<SyncVersionsDto>
}
