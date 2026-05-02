package com.example.yingshi.feature.photos

import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.result.ApiResult
import retrofit2.HttpException
import java.io.IOException

internal fun ApiResult.Error.toBackendUiMessage(fallback: String): String {
    val detail = throwable.toBackendNetworkDetail()
    return when {
        !detail.isNullOrBlank() -> detail
        message.isNotBlank() -> message
        else -> fallback
    }
}

internal fun Throwable?.toBackendNetworkDetail(): String? {
    return when (this) {
        null -> null
        is HttpException -> when (code()) {
            401 -> "登录状态已失效，请先到联调诊断页重新登录。"
            403 -> "当前账号没有权限执行这个操作。"
            404 -> "后端资源不存在，可能已经被删除或恢复。"
            else -> "后端请求失败，HTTP ${code()}。"
        }
        is IOException -> message ?: "网络请求失败，请检查 baseUrl、同一 Wi-Fi 和服务端状态。"
        else -> message
    }
}

internal fun realBackendSessionKey(scope: String): String {
    val settings = BackendDebugConfig.settings
    return buildString {
        append(scope)
        append('|')
        append(settings.repositoryMode.name)
        append('|')
        append(BackendDebugConfig.currentBaseUrl())
        append("|config=")
        append(BackendDebugConfig.sessionVersion)
        append("|token=")
        append(AuthSessionManager.sessionVersion)
    }
}

internal fun String?.orReadableBackendMessage(fallback: String): String {
    val value = this?.trim().orEmpty()
    if (value.isBlank()) return fallback
    val mojibakeMarkers = listOf(
        "銆?",
        "妯″紡",
        "璇诲彇",
        "鐧诲綍",
        "鍚庣",
        "鍥炴敹",
        "鍒犻櫎",
        "鍙栨秷",
        "闇€瑕?",
    )
    return if (mojibakeMarkers.any(value::contains)) fallback else value
}
