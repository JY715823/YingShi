package com.example.yingshi.feature.photos

import kotlin.math.abs

internal data class ViewerVideoPlaybackState(
    val mediaId: String? = null,
    val isPlaying: Boolean = false,
    val progressMillis: Long = 0L,
    val durationMillis: Long? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isCompleted: Boolean = false,
    val seekRequestMillis: Long? = null,
    val seekRequestNonce: Int = 0,
    val retryRequestNonce: Int = 0,
    val pendingSeekTargetMillis: Long? = null,
)

internal enum class ViewerImageFailureReason(
    val message: String,
) {
    NONE(message = ""),
    MISSING_URL(message = "暂无可用图片"),
    PREVIEW_FAILED(message = "图片加载失败"),
    ORIGINAL_FAILED(message = "原图加载失败，已保留预览"),
}

internal fun ViewerVideoPlaybackState.primaryStatusLabel(
    missingUrl: Boolean = false,
): String {
    return when {
        missingUrl -> "暂无可播放的视频地址"
        errorMessage != null -> errorMessage
        isLoading -> "视频加载中"
        isCompleted -> "视频播放结束"
        isPlaying -> "正在播放视频"
        else -> "视频已暂停"
    }
}

internal fun ViewerVideoPlaybackState.controlStatusLabel(): String {
    return when {
        errorMessage != null -> "播放失败"
        isLoading -> "加载中"
        isCompleted -> "播放结束"
        isPlaying -> "播放中"
        else -> "已暂停"
    }
}

internal fun ViewerVideoPlaybackState.retryState(): ViewerVideoPlaybackState {
    return copy(
        isPlaying = true,
        progressMillis = 0L,
        isLoading = true,
        errorMessage = null,
        isCompleted = false,
        seekRequestMillis = 0L,
        seekRequestNonce = seekRequestNonce + 1,
        retryRequestNonce = retryRequestNonce + 1,
        pendingSeekTargetMillis = 0L,
    )
}

internal fun ViewerVideoPlaybackState.mergePendingSeekDisplay(
    previous: ViewerVideoPlaybackState?,
): ViewerVideoPlaybackState {
    val pendingTarget = pendingSeekTargetMillis ?: previous?.pendingSeekTargetMillis
    if (pendingTarget == null) return this
    val normalizedTarget = pendingTarget.coerceAtLeast(0L)
    val isSettled = !isLoading && abs(progressMillis - normalizedTarget) <= 650L
    return if (isSettled) {
        copy(pendingSeekTargetMillis = null)
    } else {
        copy(
            progressMillis = normalizedTarget,
            durationMillis = durationMillis ?: previous?.durationMillis,
            pendingSeekTargetMillis = normalizedTarget,
        )
    }
}
