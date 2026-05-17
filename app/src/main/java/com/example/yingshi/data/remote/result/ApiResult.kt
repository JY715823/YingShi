package com.example.yingshi.data.remote.result

import retrofit2.HttpException

sealed interface ApiResult<out T> {
    data object Loading : ApiResult<Nothing>

    data class Success<T>(
        val data: T,
    ) : ApiResult<T>

    data class Error(
        val code: String? = null,
        val message: String,
        val throwable: Throwable? = null,
    ) : ApiResult<Nothing>
}

typealias NetworkResult<T> = ApiResult<T>

fun ApiResult.Error.httpStatusCode(): Int? = (throwable as? HttpException)?.code()

fun ApiResult.Error.isUnauthorized(): Boolean {
    return code == "AUTH_UNAUTHORIZED" || httpStatusCode() == 401
}
