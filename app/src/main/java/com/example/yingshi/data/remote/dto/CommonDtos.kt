package com.example.yingshi.data.remote.dto

data class ApiErrorDto(
    val code: String,
    val message: String,
    val details: String? = null,
)

data class PageDto(
    val page: Int = 1,
    val pageSize: Int = 20,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

data class ApiEnvelopeDto<T>(
    val requestId: String,
    val data: T,
    val page: PageDto? = null,
    val error: ApiErrorDto? = null,
)
