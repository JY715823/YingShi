package com.example.yingshi.feature.photos

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
