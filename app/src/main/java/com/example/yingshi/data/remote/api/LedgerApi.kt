package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.LedgerSnapshotDto
import com.example.yingshi.data.remote.dto.LedgerSyncRequestDto
import com.example.yingshi.data.remote.dto.LedgerSyncResponseDto
import com.example.yingshi.data.remote.dto.UpsertLedgerSnapshotRequestDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

interface LedgerApi {
    @GET("api/ledger/snapshot")
    suspend fun getSnapshot(): ApiEnvelopeDto<LedgerSnapshotDto>

    @PUT("api/ledger/snapshot")
    suspend fun putSnapshot(
        @Body request: UpsertLedgerSnapshotRequestDto,
    ): ApiEnvelopeDto<LedgerSnapshotDto>

    @POST("api/ledger/sync")
    suspend fun syncLedger(
        @Body request: LedgerSyncRequestDto,
    ): ApiEnvelopeDto<LedgerSyncResponseDto>
}
