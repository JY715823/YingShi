package com.example.yingshi.data.remote.mapper

import com.example.yingshi.data.model.NotificationMarkAllReadResult
import com.example.yingshi.data.model.RemoteNotification
import com.example.yingshi.data.remote.dto.NotificationDto
import com.example.yingshi.data.remote.dto.NotificationMarkAllReadResponseDto

fun NotificationDto.toRemoteModel(): RemoteNotification {
    return RemoteNotification(
        notificationId = notificationId,
        type = type,
        title = title,
        body = body,
        createdAtMillis = createdAtMillis,
        isRead = isRead,
        targetSummary = targetSummary,
        targetType = targetType,
        smallAlbumId = smallAlbumId,
        mediaId = mediaId,
        trashItemId = trashItemId,
    )
}

fun NotificationMarkAllReadResponseDto.toRemoteModel(): NotificationMarkAllReadResult {
    return NotificationMarkAllReadResult(
        success = success,
        affectedCount = affectedCount,
    )
}
