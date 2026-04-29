package com.example.yingshi.feature.photos

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf

@Immutable
data class NotificationCenterRoute(
    val source: String = "photos-bell",
)

@Immutable
data class NotificationCenterItemUiModel(
    val id: String,
    val type: NotificationCenterItemType,
    val title: String,
    val body: String,
    val createdAtMillis: Long,
    val isRead: Boolean,
)

enum class NotificationCenterItemType(
    val label: String,
) {
    COMMENT("评论"),
    CONTENT_UPDATE("内容更新"),
    DELETE_RESTORE("删除 / 恢复"),
    SYSTEM("系统"),
}

object FakeNotificationRepository {
    private val notifications = mutableStateListOf(
        NotificationCenterItemUiModel(
            id = "notice-comment-1",
            type = NotificationCenterItemType.COMMENT,
            title = "你收到一条媒体评论",
            body = "“这张的光好温柔。” 已添加到当前媒体评论里。",
            createdAtMillis = 1_777_412_800_000L,
            isRead = false,
        ),
        NotificationCenterItemUiModel(
            id = "notice-post-update-1",
            type = NotificationCenterItemType.CONTENT_UPDATE,
            title = "帖子内容有更新",
            body = "“四月窗边” 的标题和简介刚刚被本地修改。",
            createdAtMillis = 1_777_409_200_000L,
            isRead = false,
        ),
        NotificationCenterItemUiModel(
            id = "notice-trash-1",
            type = NotificationCenterItemType.DELETE_RESTORE,
            title = "有内容进入回收站",
            body = "1 条帖子删除记录和 2 个媒体删除快照已写入本地回收站。",
            createdAtMillis = 1_777_401_000_000L,
            isRead = true,
        ),
        NotificationCenterItemUiModel(
            id = "notice-restore-1",
            type = NotificationCenterItemType.DELETE_RESTORE,
            title = "恢复入口仍在保留期内",
            body = "最近一次移出回收站的内容仍保留 24h 可撤销语义。",
            createdAtMillis = 1_777_393_600_000L,
            isRead = true,
        ),
        NotificationCenterItemUiModel(
            id = "notice-cache-1",
            type = NotificationCenterItemType.SYSTEM,
            title = "缓存管理壳层已接入",
            body = "设置页将可进入全局缓存清理占位页，但当前仍不扫描真实磁盘。",
            createdAtMillis = 1_777_386_400_000L,
            isRead = false,
        ),
        NotificationCenterItemUiModel(
            id = "notice-viewer-video-1",
            type = NotificationCenterItemType.CONTENT_UPDATE,
            title = "Viewer 视频壳层可用",
            body = "照片流 Viewer 与帖子内 Viewer 现在都能识别视频媒体。",
            createdAtMillis = 1_777_379_200_000L,
            isRead = true,
        ),
    )

    fun getNotifications(): List<NotificationCenterItemUiModel> = notifications

    fun unreadCount(): Int = notifications.count { !it.isRead }

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
