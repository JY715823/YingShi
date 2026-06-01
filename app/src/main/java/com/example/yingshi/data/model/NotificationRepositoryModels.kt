package com.example.yingshi.data.model

data class RemoteNotification(
    val notificationId: String,
    val type: String,
    val title: String,
    val body: String,
    val createdAtMillis: Long,
    val isRead: Boolean,
    val targetSummary: String? = null,
    val targetType: String? = null,
    val smallAlbumId: String? = null,
    val mediaId: String? = null,
    val trashItemId: String? = null,
) {
    val postId: String?
        get() = smallAlbumId
}

data class NotificationMarkAllReadResult(
    val success: Boolean,
    val affectedCount: Int,
)
