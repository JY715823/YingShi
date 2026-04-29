package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.PendingCleanupDto
import com.example.yingshi.data.remote.dto.RestoreRequestDto
import com.example.yingshi.data.remote.dto.TrashDetailDto
import com.example.yingshi.data.remote.dto.TrashItemDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TrashApi {
    @GET("v1/trash/items")
    suspend fun getTrashItems(
        @Query("type") type: String? = null,
        @Query("page") page: Int? = null,
        @Query("size") size: Int? = null,
    ): ApiEnvelopeDto<List<TrashItemDto>>

    @GET("v1/trash/items/{trashItemId}")
    suspend fun getTrashDetail(
        @Path("trashItemId") trashItemId: String,
    ): ApiEnvelopeDto<TrashDetailDto>

    @POST("v1/trash/items/{trashItemId}/restore")
    suspend fun restoreTrashItem(
        @Path("trashItemId") trashItemId: String,
        @Body request: RestoreRequestDto = RestoreRequestDto(),
    ): ApiEnvelopeDto<TrashItemDto>

    @POST("v1/trash/items/{trashItemId}/remove")
    suspend fun removeTrashItem(
        @Path("trashItemId") trashItemId: String,
    ): ApiEnvelopeDto<PendingCleanupDto>

    @POST("v1/trash/items/{trashItemId}/undo-remove")
    suspend fun undoRemoveTrashItem(
        @Path("trashItemId") trashItemId: String,
    ): ApiEnvelopeDto<TrashItemDto>

    @GET("v1/trash/pending-cleanup")
    suspend fun getPendingCleanupItems(): ApiEnvelopeDto<List<PendingCleanupDto>>

    @DELETE("v1/trash/items/{trashItemId}")
    suspend fun purgeTrashItem(
        @Path("trashItemId") trashItemId: String,
    ): ApiEnvelopeDto<Unit>
}
