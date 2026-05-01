package com.example.yingshi.feature.photos

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.provider.MediaStore

internal fun supportsSystemMediaTrashRequest(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
}

internal fun systemMediaTrashUnsupportedMessage(): String {
    return "当前 Android 版本不支持系统媒体回收站确认流程。"
}

internal fun createSystemMediaTrashRequest(
    context: Context,
    mediaItems: List<SystemMediaItem>,
): Result<PendingIntent> {
    if (!supportsSystemMediaTrashRequest()) {
        return Result.failure(IllegalStateException(systemMediaTrashUnsupportedMessage()))
    }
    if (mediaItems.isEmpty()) {
        return Result.failure(IllegalArgumentException("当前没有可移到系统回收站的媒体。"))
    }
    return runCatching {
        MediaStore.createTrashRequest(
            context.contentResolver,
            mediaItems.map { it.uri },
            true,
        )
    }
}
