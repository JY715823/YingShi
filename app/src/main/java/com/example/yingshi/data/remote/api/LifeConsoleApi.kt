package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.LifeConsoleBowelMutationResponseDto
import com.example.yingshi.data.remote.dto.LifeConsoleMediaRequestDto
import com.example.yingshi.data.remote.dto.LifeConsoleTodayDto
import com.example.yingshi.data.remote.dto.RegisterPushTokenRequestDto
import com.example.yingshi.data.remote.dto.RegisterPushTokenResponseDto
import com.example.yingshi.data.remote.dto.TrashItemDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface LifeConsoleApi {
    @GET("api/life-console/today")
    suspend fun getToday(
        @Query("date") date: String? = null,
        @Query("zoneId") zoneId: String? = null,
    ): ApiEnvelopeDto<LifeConsoleTodayDto>

    @POST("api/life-console/media")
    suspend fun addMedia(
        @Body request: LifeConsoleMediaRequestDto,
    ): ApiEnvelopeDto<LifeConsoleTodayDto>

    @DELETE("api/life-console/media/{mediaId}")
    suspend fun deleteMedia(
        @Path("mediaId") mediaId: String,
        @Query("category") category: String,
    ): ApiEnvelopeDto<TrashItemDto>

    @POST("api/life-console/bowel-events")
    suspend fun addBowelEvent(): ApiEnvelopeDto<LifeConsoleBowelMutationResponseDto>

    @DELETE("api/life-console/bowel-events/latest")
    suspend fun deleteLatestBowelEvent(): ApiEnvelopeDto<LifeConsoleBowelMutationResponseDto>

    @POST("api/push/device-tokens")
    suspend fun registerPushToken(
        @Body request: RegisterPushTokenRequestDto,
    ): ApiEnvelopeDto<RegisterPushTokenResponseDto>
}
