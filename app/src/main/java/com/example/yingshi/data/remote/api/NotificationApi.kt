package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.NotificationDto
import com.example.yingshi.data.remote.dto.NotificationMarkAllReadResponseDto
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface NotificationApi {
    @GET("api/notifications")
    suspend fun getNotifications(
        @Query("limit") limit: Int? = null,
        @Query("cursor") cursor: String? = null,
    ): ApiEnvelopeDto<List<NotificationDto>>

    @GET("api/notifications/{notificationId}")
    suspend fun getNotification(
        @Path("notificationId") notificationId: String,
    ): ApiEnvelopeDto<NotificationDto>

    @POST("api/notifications/{notificationId}/read")
    suspend fun markRead(
        @Path("notificationId") notificationId: String,
    ): ApiEnvelopeDto<NotificationDto>

    @POST("api/notifications/read-all")
    suspend fun markAllRead(): ApiEnvelopeDto<NotificationMarkAllReadResponseDto>
}
