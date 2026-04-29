package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.TrashItemDto
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
        @Query("pageSize") pageSize: Int? = null,
        @Query("cursor") cursor: String? = null,
    ): ApiEnvelopeDto<List<TrashItemDto>>

    @POST("v1/trash/items/{trashItemId}/restore")
    suspend fun restoreTrashItem(
        @Path("trashItemId") trashItemId: String,
    ): ApiEnvelopeDto<Unit>

    @DELETE("v1/trash/items/{trashItemId}")
    suspend fun deleteTrashItem(
        @Path("trashItemId") trashItemId: String,
    ): ApiEnvelopeDto<Unit>
}
