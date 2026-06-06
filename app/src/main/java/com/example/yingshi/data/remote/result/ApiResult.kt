package com.example.yingshi.data.remote.result

import com.google.gson.JsonParser
import retrofit2.HttpException
import java.util.Collections
import java.util.WeakHashMap

sealed interface ApiResult<out T> {
    data object Loading : ApiResult<Nothing>

    data class Success<T>(
        val data: T,
    ) : ApiResult<T>

    class Error(
        code: String? = null,
        message: String,
        val throwable: Throwable? = null,
    ) : ApiResult<Nothing> {
        private val fallbackCode = code
        private val fallbackMessage = message

        val code: String?
            get() = throwable?.backendErrorCode() ?: fallbackCode

        val message: String
            get() = throwable?.backendErrorMessage() ?: fallbackMessage
    }
}

typealias NetworkResult<T> = ApiResult<T>

fun ApiResult.Error.httpStatusCode(): Int? = (throwable as? HttpException)?.code()

fun ApiResult.Error.isUnauthorized(): Boolean {
    val effectiveCode = throwable?.backendErrorCode() ?: code
    return effectiveCode == "AUTH_UNAUTHORIZED" ||
        effectiveCode == "AUTH_SESSION_INVALID" ||
        effectiveCode == "AUTH_TOKEN_EXPIRED" ||
        httpStatusCode() == 401
}

data class BackendErrorEnvelope(
    val code: String?,
    val message: String?,
)

private val backendErrorEnvelopeCache =
    Collections.synchronizedMap(WeakHashMap<Throwable, BackendErrorEnvelope?>())

fun Throwable.backendErrorCode(): String? = backendErrorEnvelope()?.code

fun Throwable.backendErrorMessage(): String? = backendErrorEnvelope()?.message

fun Throwable.backendErrorEnvelope(): BackendErrorEnvelope? {
    if (this !is HttpException) return null
    synchronized(backendErrorEnvelopeCache) {
        if (backendErrorEnvelopeCache.containsKey(this)) {
            return backendErrorEnvelopeCache[this]
        }
        val parsed = parseBackendErrorEnvelope()
        backendErrorEnvelopeCache[this] = parsed
        return parsed
    }
}

private fun HttpException.parseBackendErrorEnvelope(): BackendErrorEnvelope? {
    val rawBody = runCatching {
        response()?.errorBody()?.string()
    }.getOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        val root = JsonParser.parseString(rawBody).asJsonObject
        val error = root.getAsJsonObject("error") ?: return@runCatching null
        BackendErrorEnvelope(
            code = error.get("code")?.takeIf { !it.isJsonNull }?.asString?.trim()?.takeIf { it.isNotBlank() },
            message = error.get("message")?.takeIf { !it.isJsonNull }?.asString?.trim()?.takeIf { it.isNotBlank() },
        )
    }.getOrNull()
}
