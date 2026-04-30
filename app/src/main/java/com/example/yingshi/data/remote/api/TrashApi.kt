package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.PendingCleanupDto
import com.example.yingshi.data.remote.dto.TrashDetailDto
import com.example.yingshi.data.remote.dto.TrashItemDto
import com.example.yingshi.data.remote.dto.TrashPageResponseDto
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TrashApi {
    @GET("api/trash/items")
    suspend fun getTrashItems(
        @Query("itemType") itemType: String? = null,
        @Query("page") page: Int? = null,
        @Query("size") size: Int? = null,
    ): ApiEnvelopeDto<TrashPageResponseDto>

    @GET("api/trash/items/{trashItemId}")
    suspend fun getTrashDetail(
        @Path("trashItemId") trashItemId: String,
    ): ApiEnvelopeDto<TrashDetailDto>

    @POST("api/trash/items/{trashItemId}/restore")
    suspend fun restoreTrashItem(
        @Path("trashItemId") trashItemId: String,
    ): ApiEnvelopeDto<TrashItemDto>

    @POST("api/trash/items/{trashItemId}/remove")
    suspend fun removeTrashItem(
        @Path("trashItemId") trashItemId: String,
    ): ApiEnvelopeDto<PendingCleanupDto>

    @POST("api/trash/items/{trashItemId}/undo-remove")
    suspend fun undoRemoveTrashItem(
        @Path("trashItemId") trashItemId: String,
    ): ApiEnvelopeDto<TrashItemDto>

    @GET("api/trash/pending-cleanup")
    suspend fun getPendingCleanupItems(): ApiEnvelopeDto<List<PendingCleanupDto>>
}
