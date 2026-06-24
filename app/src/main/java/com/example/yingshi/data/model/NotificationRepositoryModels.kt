package com.example.yingshi.data.model

data class RemoteNotification(
    val notificationId: String,
    val type: String,
    val module: String? = null,
    val category: String? = null,
    val title: String,
    val body: String,
    val createdAtMillis: Long,
    val isRead: Boolean,
    val actorUserId: String? = null,
    val actorDisplayName: String? = null,
    val actorAvatarUrl: String? = null,
    val actorIsCurrentUser: Boolean = false,
    val groupId: String? = null,
    val operationId: String? = null,
    val groupItemCount: Int? = null,
    val mediaItems: List<RemoteNotificationMediaItem> = emptyList(),
    val targetRoute: String? = null,
    val targetSummary: String? = null,
    val targetType: String? = null,
    val smallAlbumId: String? = null,
    val mediaId: String? = null,
    val trashItemId: String? = null,
) {
    val postId: String?
        get() = smallAlbumId
}

data class RemoteNotificationMediaItem(
    val mediaId: String,
    val mediaType: String? = null,
    val mimeType: String? = null,
    val previewUrl: String? = null,
    val thumbnailUrl: String? = null,
    val coverUrl: String? = null,
    val mediaUrl: String? = null,
    val videoUrl: String? = null,
    val displayTimeMillis: Long? = null,
    val durationMillis: Long? = null,
)

data class NotificationMarkAllReadResult(
    val success: Boolean,
    val affectedCount: Int,
)
