package com.example.yingshi.feature.photos

internal data class ViewerVideoPlaybackState(
    val mediaId: String? = null,
    val isPlaying: Boolean = false,
    val progressMillis: Long = 0L,
    val durationMillis: Long? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
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
        isLoading -> "视频加载中…"
        isPlaying -> "正在播放视频"
        else -> "视频已暂停"
    }
}

internal fun ViewerVideoPlaybackState.controlStatusLabel(): String {
    return when {
        errorMessage != null -> "播放失败"
        isLoading -> "加载中"
        isPlaying -> "播放中"
        else -> "已暂停"
    }
}

internal fun ViewerVideoPlaybackState.retryState(): ViewerVideoPlaybackState {
    return copy(
        isPlaying = false,
        progressMillis = 0L,
        isLoading = true,
        errorMessage = null,
    )
}
