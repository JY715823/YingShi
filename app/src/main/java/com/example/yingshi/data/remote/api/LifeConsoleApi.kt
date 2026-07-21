package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.LifeConsoleBowelEventRequestDto
import com.example.yingshi.data.remote.dto.LifeConsoleBowelMutationResponseDto
import com.example.yingshi.data.remote.dto.LifeConsoleHistoryDto
import com.example.yingshi.data.remote.dto.LifeConsoleMediaRequestDto
import com.example.yingshi.data.remote.dto.LifeConsoleTodayDto
import com.example.yingshi.data.remote.dto.PushDiagnosticsResponseDto
import com.example.yingshi.data.remote.dto.PushPreferencesResponseDto
import com.example.yingshi.data.remote.dto.RegisterPushTokenRequestDto
import com.example.yingshi.data.remote.dto.RegisterPushTokenResponseDto
import com.example.yingshi.data.remote.dto.TrashItemDto
import com.example.yingshi.data.remote.dto.UpdateLocationRequestDto
import com.example.yingshi.data.remote.dto.UpdatePushPreferenceRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface LifeConsoleApi {
    @GET("api/life-console/today")
    suspend fun getToday(
        @Query("date") date: String? = null,
        @Query("zoneId") zoneId: String? = null,
    ): ApiEnvelopeDto<LifeConsoleTodayDto>

    @GET("api/life-console/history")
    suspend fun getHistory(
        @Query("zoneId") zoneId: String? = null,
        @Query("limitDays") limitDays: Int? = null,
    ): ApiEnvelopeDto<LifeConsoleHistoryDto>

    @POST("api/life-console/media")
    suspend fun addMedia(
        @Body request: LifeConsoleMediaRequestDto,
    ): ApiEnvelopeDto<LifeConsoleTodayDto>

    @DELETE("api/life-console/media/{mediaId}")
    suspend fun deleteMedia(
        @Path("mediaId") mediaId: String,
        @Query("category") category: String,
    ): ApiEnvelopeDto<TrashItemDto>

    // Round 7 阶段 7: PATCH 媒体位置
    @PATCH("api/life-console/media/{mediaId}/location")
    suspend fun updateMediaLocation(
        @Path("mediaId") mediaId: String,
        @Body request: UpdateLocationRequestDto,
    ): ApiEnvelopeDto<LifeConsoleTodayDto>

    // Round 7 阶段 7: PATCH 大便事件位置
    @PATCH("api/life-console/bowel-events/{eventId}/location")
    suspend fun updateBowelEventLocation(
        @Path("eventId") eventId: String,
        @Body request: UpdateLocationRequestDto,
    ): ApiEnvelopeDto<LifeConsoleBowelMutationResponseDto>

    @POST("api/life-console/bowel-events")
    suspend fun addBowelEvent(
        @Query("zoneId") zoneId: String? = null,
        @Body body: LifeConsoleBowelEventRequestDto,
    ): ApiEnvelopeDto<LifeConsoleBowelMutationResponseDto>

    @DELETE("api/life-console/bowel-events/latest")
    suspend fun deleteLatestBowelEvent(
        @Query("zoneId") zoneId: String? = null,
    ): ApiEnvelopeDto<LifeConsoleBowelMutationResponseDto>

    @POST("api/push/device-tokens")
    suspend fun registerPushToken(
        @Body request: RegisterPushTokenRequestDto,
    ): ApiEnvelopeDto<RegisterPushTokenResponseDto>

    @GET("api/push/preferences")
    suspend fun getPushPreferences(): ApiEnvelopeDto<PushPreferencesResponseDto>

    @GET("api/push/diagnostics")
    suspend fun getPushDiagnostics(): ApiEnvelopeDto<PushDiagnosticsResponseDto>

    @POST("api/push/preferences")
    suspend fun updatePushPreference(
        @Body request: UpdatePushPreferenceRequestDto,
    ): ApiEnvelopeDto<PushPreferencesResponseDto>
}
