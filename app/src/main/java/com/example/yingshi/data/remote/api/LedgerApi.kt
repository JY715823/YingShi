package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.LedgerSyncRequestDto
import com.example.yingshi.data.remote.dto.LedgerSyncResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

interface LedgerApi {
    @POST("api/ledger/sync")
    suspend fun syncLedger(
        @Body request: LedgerSyncRequestDto,
    ): ApiEnvelopeDto<LedgerSyncResponseDto>
}
