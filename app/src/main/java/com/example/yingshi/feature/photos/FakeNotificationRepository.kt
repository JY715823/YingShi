package com.example.yingshi.feature.photos

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf
import com.example.yingshi.data.model.RemoteNotification

@Immutable
data class NotificationCenterRoute(
    val source: String = "photos-bell",
)

@Immutable
data class NotificationDetailRoute(
    val notificationId: String,
    val source: String = "notification-center",
)

@Immutable
data class NotificationCenterItemUiModel(
    val id: String,
    val type: NotificationCenterItemType,
    val title: String,
    val body: String,
    val createdAtMillis: Long,
    val isRead: Boolean,
    val targetSummary: String,
    val targetType: String? = null,
    val postId: String? = null,
    val mediaId: String? = null,
    val trashItemId: String? = null,
)

enum class NotificationCenterItemType(
    val label: String,
    val apiValue: String,
) {
    COMMENT("评论", "comment"),
    CONTENT_UPDATE("内容更新", "content_update"),
    DELETE_RESTORE("删除 / 恢复", "delete_restore"),
    SYSTEM("系统", "system"),
    ;

    companion object {
        fun fromApiValue(value: String): NotificationCenterItemType {
            return when (value.lowercase()) {
                "comment",
                "comment_edit",
                "comment_delete" -> COMMENT
                "content_update" -> CONTENT_UPDATE
                "delete_restore" -> DELETE_RESTORE
                else -> SYSTEM
            }
        }
    }
}

enum class NotificationCenterFilter(
    val label: String,
    val targetType: NotificationCenterItemType?,
) {
    ALL("全部", null),
    COMMENT("评论", NotificationCenterItemType.COMMENT),
    CONTENT_UPDATE("内容更新", NotificationCenterItemType.CONTENT_UPDATE),
    DELETE_RESTORE("删除 / 恢复", NotificationCenterItemType.DELETE_RESTORE),
    SYSTEM("系统", NotificationCenterItemType.SYSTEM),
}

object FakeNotificationRepository {
    private val notifications = mutableStateListOf(
        NotificationCenterItemUiModel(
            id = "notice-comment-1",
            type = NotificationCenterItemType.COMMENT,
            title = "你收到一条媒体评论",
            body = "“这张的光好温柔。”已经追加到当前媒体评论里。",
            createdAtMillis = 1_777_412_800_000L,
            isRead = false,
            targetSummary = "夜晚散步",
            targetType = "POST",
            postId = "post-river-night",
        ),
        NotificationCenterItemUiModel(
            id = "notice-comment-2",
            type = NotificationCenterItemType.COMMENT,
            title = "小相册评论有新回复",
            body = "“夜晚散步”下有一条新的回复。",
            createdAtMillis = 1_777_411_600_000L,
            isRead = false,
            targetSummary = "夜晚散步",
            targetType = "POST",
            postId = "post-window-light",
        ),
        NotificationCenterItemUiModel(
            id = "notice-post-update-1",
            type = NotificationCenterItemType.CONTENT_UPDATE,
            title = "小相册内容有更新",
            body = "“四月窗边”的标题和简介刚刚被本地修改。",
            createdAtMillis = 1_777_409_200_000L,
            isRead = false,
            targetSummary = "四月窗边",
            targetType = "POST",
            postId = "post-window-light",
        ),
        NotificationCenterItemUiModel(
            id = "notice-album-update-1",
            type = NotificationCenterItemType.CONTENT_UPDATE,
            title = "相册目录有变动",
            body = "“周末餐桌”大相册下的小相册顺序已在本地重新整理。",
            createdAtMillis = 1_777_405_600_000L,
            isRead = true,
            targetSummary = "周末餐桌",
            targetType = "ALBUM",
        ),
        NotificationCenterItemUiModel(
            id = "notice-trash-1",
            type = NotificationCenterItemType.DELETE_RESTORE,
            title = "有内容进入回收站",
            body = "1 条小相册删除记录和 2 个媒体删除快照已写入本地回收站。",
            createdAtMillis = 1_777_401_000_000L,
            isRead = true,
            targetSummary = "回收站",
            targetType = "MEDIA_SYSTEM_DELETED",
        ),
        NotificationCenterItemUiModel(
            id = "notice-restore-1",
            type = NotificationCenterItemType.DELETE_RESTORE,
            title = "回收站恢复入口已更新",
            body = "现在可以在回收站按分类批量恢复或清空内容。",
            createdAtMillis = 1_777_393_600_000L,
            isRead = true,
            targetSummary = "回收站",
            targetType = "MEDIA_SYSTEM_DELETED",
        ),
        NotificationCenterItemUiModel(
            id = "notice-cache-1",
            type = NotificationCenterItemType.SYSTEM,
            title = "缓存可以清理",
            body = "缩略图缓存可在设置中清理，照片和相册内容不会被删除。",
            createdAtMillis = 1_777_386_400_000L,
            isRead = false,
            targetSummary = "设置",
            targetType = "SYSTEM",
        ),
        NotificationCenterItemUiModel(
            id = "notice-viewer-video-1",
            type = NotificationCenterItemType.SYSTEM,
            title = "视频可以直接查看",
            body = "照片流和小相册中的视频现在可以在查看器里播放。",
            createdAtMillis = 1_777_379_200_000L,
            isRead = true,
            targetSummary = "照片流",
            targetType = "SYSTEM",
        ),
    )

    fun getNotifications(): List<NotificationCenterItemUiModel> = notifications

    fun getNotifications(filter: NotificationCenterFilter): List<NotificationCenterItemUiModel> {
        return when (val targetType = filter.targetType) {
            null -> notifications
            else -> notifications.filter { it.type == targetType }
        }
    }

    fun getNotification(notificationId: String): NotificationCenterItemUiModel? {
        return notifications.firstOrNull { it.id == notificationId }
    }

    fun unreadCount(): Int = notifications.count { !it.isRead }

    fun unreadCount(filter: NotificationCenterFilter): Int {
        return getNotifications(filter).count { !it.isRead }
    }

    fun markRead(notificationId: String) {
        val index = notifications.indexOfFirst { it.id == notificationId }
        if (index < 0) return
        val current = notifications[index]
        if (current.isRead) return
        notifications[index] = current.copy(isRead = true)
    }

    fun markAllRead() {
        notifications.forEachIndexed { index, item ->
            if (!item.isRead) {
                notifications[index] = item.copy(isRead = true)
            }
        }
    }
}

fun RemoteNotification.toNotificationCenterItemUiModel(): NotificationCenterItemUiModel {
    return NotificationCenterItemUiModel(
        id = notificationId,
        type = NotificationCenterItemType.fromApiValue(type),
        title = title,
        body = body,
        createdAtMillis = createdAtMillis,
        isRead = isRead,
        targetSummary = targetSummary.orNotificationTargetSummary(),
        targetType = targetType,
        postId = postId,
        mediaId = mediaId,
        trashItemId = trashItemId,
    )
}

private fun String?.orNotificationTargetSummary(): String {
    return this?.takeIf { it.isNotBlank() } ?: "查看相关内容"
}
