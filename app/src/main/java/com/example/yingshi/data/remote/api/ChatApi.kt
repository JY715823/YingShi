package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.ChatSnapshotDto
import com.example.yingshi.data.remote.dto.UpsertChatSnapshotRequestDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

interface ChatApi {
    @GET("api/chat/snapshot")
    suspend fun getSnapshot(): ApiEnvelopeDto<ChatSnapshotDto>

    @PUT("api/chat/snapshot")
    suspend fun putSnapshot(
        @Body request: UpsertChatSnapshotRequestDto,
    ): ApiEnvelopeDto<ChatSnapshotDto>
}
