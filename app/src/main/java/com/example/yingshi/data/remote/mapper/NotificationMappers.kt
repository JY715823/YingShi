package com.example.yingshi.data.remote.mapper

import com.example.yingshi.data.model.NotificationMarkAllReadResult
import com.example.yingshi.data.model.RemoteNotification
import com.example.yingshi.data.model.RemoteNotificationMediaItem
import com.example.yingshi.data.remote.dto.NotificationDto
import com.example.yingshi.data.remote.dto.NotificationMediaItemDto
import com.example.yingshi.data.remote.dto.NotificationMarkAllReadResponseDto

fun NotificationDto.toRemoteModel(): RemoteNotification {
    return RemoteNotification(
        notificationId = notificationId,
        type = type,
        module = module,
        category = category,
        title = title,
        body = body,
        createdAtMillis = createdAtMillis,
        isRead = isRead,
        actorUserId = actorUserId,
        actorDisplayName = actorDisplayName,
        actorAvatarUrl = actorAvatarUrl,
        actorIsCurrentUser = actorIsCurrentUser,
        groupId = groupId,
        operationId = operationId,
        groupItemCount = groupItemCount,
        mediaItems = mediaItems.map(NotificationMediaItemDto::toRemoteModel),
        targetRoute = targetRoute,
        targetSummary = targetSummary,
        targetType = targetType,
        smallAlbumId = smallAlbumId,
        mediaId = mediaId,
        trashItemId = trashItemId,
    )
}

fun NotificationMediaItemDto.toRemoteModel(): RemoteNotificationMediaItem {
    return RemoteNotificationMediaItem(
        mediaId = mediaId,
        mediaType = mediaType,
        mimeType = mimeType,
        previewUrl = previewUrl,
        thumbnailUrl = thumbnailUrl,
        coverUrl = coverUrl,
        mediaUrl = mediaUrl,
        videoUrl = videoUrl,
        displayTimeMillis = displayTimeMillis,
        durationMillis = durationMillis,
    )
}

fun NotificationMarkAllReadResponseDto.toRemoteModel(): NotificationMarkAllReadResult {
    return NotificationMarkAllReadResult(
        success = success,
        affectedCount = affectedCount,
    )
}
