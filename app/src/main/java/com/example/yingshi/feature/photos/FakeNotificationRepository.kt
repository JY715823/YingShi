package com.example.yingshi.feature.photos

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf
import com.example.yingshi.data.model.RemoteNotification
import com.example.yingshi.data.model.RemoteNotificationMediaItem

@Immutable
data class NotificationCenterRoute(
    val source: String = "photos-bell",
    val selectedFilterName: String = NotificationCenterFilter.PHOTOS.name,
    val selectedCategoryName: String = NotificationCategoryFilter.ALL.name,
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0,
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
    val mediaItems: List<NotificationCenterMediaItemUiModel> = emptyList(),
    val targetRoute: String? = null,
    val targetSummary: String,
    val targetType: String? = null,
    val postId: String? = null,
    val mediaId: String? = null,
    val trashItemId: String? = null,
)

@Immutable
data class NotificationCenterMediaItemUiModel(
    val mediaId: String,
    val mediaType: AppMediaType = AppMediaType.IMAGE,
    val mimeType: String? = null,
    val mediaSource: AppContentMediaSource? = null,
    val displayTimeMillis: Long? = null,
    val durationMillis: Long? = null,
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
) {
    PHOTOS("照片模块"),
    LIFE("生活模块"),
}

enum class NotificationCategoryFilter(
    val label: String,
) {
    ALL("全部分类"),
    COMMENT("评论"),
    CONTENT_UPDATE("内容更新"),
    DELETE_RESTORE("删除 / 恢复"),
    SYSTEM("系统"),
    LEDGER("记账"),
    CHAT("聊天记录"),
    TRACE("今日痕迹"),
}

object NotificationCenterLocalStore {
    private val notifications = mutableStateListOf<NotificationCenterItemUiModel>()

    fun getNotifications(): List<NotificationCenterItemUiModel> = notifications

    fun contains(notificationId: String): Boolean {
        return notifications.any { it.id == notificationId }
    }

    fun push(notification: NotificationCenterItemUiModel) {
        notifications.removeAll { it.id == notification.id }
        notifications.add(index = 0, element = notification)
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

    fun removeNotifications(notificationIds: Set<String>) {
        if (notificationIds.isEmpty()) return
        notifications.removeAll { it.id in notificationIds }
    }

    fun pushPostCommentNotification(
        postId: String,
        comment: String,
    ) {
        val post = FakeAlbumRepository.getPost(postId)
        val postTitle = post?.title?.takeIf { it.isNotBlank() } ?: "当前小相册"
        push(
            NotificationCenterItemUiModel(
                id = nextLocalNotificationId("post-comment"),
                type = NotificationCenterItemType.COMMENT,
                title = "小相册评论有新内容",
                body = "“${comment.notificationPreview()}”已加到「$postTitle」里。",
                createdAtMillis = System.currentTimeMillis(),
                isRead = false,
                targetSummary = postTitle,
                targetType = "POST",
                postId = postId,
            ),
        )
    }

    fun pushMediaCommentNotification(
        mediaId: String,
        comment: String,
        postId: String? = null,
    ) {
        val post = postId?.let(FakeAlbumRepository::getPost)
            ?: FakeAlbumRepository.findFirstPostByMediaId(mediaId)
        val postTitle = post?.title?.takeIf { it.isNotBlank() } ?: "当前媒体"
        push(
            NotificationCenterItemUiModel(
                id = nextLocalNotificationId("media-comment"),
                type = NotificationCenterItemType.COMMENT,
                title = "媒体评论有新内容",
                body = "“${comment.notificationPreview()}”已加到当前媒体评论里。",
                createdAtMillis = System.currentTimeMillis(),
                isRead = false,
                targetSummary = postTitle,
                targetType = "POST",
                postId = post?.id ?: postId,
                mediaId = mediaId,
            ),
        )
    }

    private var localNotificationSequence = 0L

    private fun nextLocalNotificationId(prefix: String): String {
        localNotificationSequence += 1
        return "notice-local-$prefix-${System.currentTimeMillis()}-$localNotificationSequence"
    }
}

object FakeNotificationRepository {
    private var localNotificationSequence = 0L
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
        NotificationCenterItemUiModel(
            id = "notice-life-ledger-1",
            type = NotificationCenterItemType.SYSTEM,
            title = "记账有新变动",
            body = "本月账本新增了一笔生活支出。",
            createdAtMillis = 1_777_372_000_000L,
            isRead = false,
            targetSummary = "记账",
            targetType = "LIFE_LEDGER",
        ),
        NotificationCenterItemUiModel(
            id = "notice-life-trace-1",
            type = NotificationCenterItemType.CONTENT_UPDATE,
            title = "今日痕迹已更新",
            body = "今日痕迹里有新的照片记录。",
            createdAtMillis = 1_777_368_400_000L,
            isRead = true,
            targetSummary = "今日痕迹",
            targetType = "LIFE_CONSOLE",
        ),
    )

    fun getNotifications(): List<NotificationCenterItemUiModel> = notifications

    fun getNotifications(filter: NotificationCenterFilter): List<NotificationCenterItemUiModel> {
        return notifications.filter { it.matchesNotificationCenterFilter(filter) }
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

    fun pushPostCommentNotification(
        postId: String,
        comment: String,
    ) {
        val post = FakeAlbumRepository.getPost(postId)
        val postTitle = post?.title?.takeIf { it.isNotBlank() } ?: "当前小相册"
        prependNotification(
            NotificationCenterItemUiModel(
                id = nextLocalNotificationId("post-comment"),
                type = NotificationCenterItemType.COMMENT,
                title = "小相册评论有新内容",
                body = "“${comment.notificationPreview()}”已加到「$postTitle」里。",
                createdAtMillis = System.currentTimeMillis(),
                isRead = false,
                targetSummary = postTitle,
                targetType = "POST",
                postId = postId,
            ),
        )
    }

    fun pushMediaCommentNotification(
        mediaId: String,
        comment: String,
    ) {
        val post = FakeAlbumRepository.findFirstPostByMediaId(mediaId)
        val postTitle = post?.title?.takeIf { it.isNotBlank() } ?: "当前媒体"
        prependNotification(
            NotificationCenterItemUiModel(
                id = nextLocalNotificationId("media-comment"),
                type = NotificationCenterItemType.COMMENT,
                title = "媒体评论有新内容",
                body = "“${comment.notificationPreview()}”已加到当前媒体评论里。",
                createdAtMillis = System.currentTimeMillis(),
                isRead = false,
                targetSummary = postTitle,
                targetType = "POST",
                postId = post?.id,
                mediaId = mediaId,
            ),
        )
    }

    private fun prependNotification(item: NotificationCenterItemUiModel) {
        notifications.add(index = 0, element = item)
    }

    private fun nextLocalNotificationId(prefix: String): String {
        localNotificationSequence += 1
        return "notice-$prefix-${System.currentTimeMillis()}-$localNotificationSequence"
    }
}

private fun String.notificationPreview(): String {
    return trim().replace('\n', ' ').take(22).ifBlank { "新评论" }
}

fun RemoteNotification.toNotificationCenterItemUiModel(): NotificationCenterItemUiModel {
    return NotificationCenterItemUiModel(
        id = notificationId,
        type = NotificationCenterItemType.fromApiValue(type),
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
        mediaItems = mediaItems.map(RemoteNotificationMediaItem::toNotificationCenterMediaItemUiModel),
        targetRoute = targetRoute,
        targetSummary = targetSummary.orNotificationTargetSummary(),
        targetType = targetType,
        postId = postId,
        mediaId = mediaId,
        trashItemId = trashItemId,
    )
}

private fun RemoteNotificationMediaItem.toNotificationCenterMediaItemUiModel(): NotificationCenterMediaItemUiModel {
    val resolvedType = resolveAppMediaType(
        rawType = mediaType,
        mimeType = mimeType,
        thumbnailUrl = thumbnailUrl ?: previewUrl,
        mediaUrl = mediaUrl,
        videoUrl = videoUrl,
        coverUrl = coverUrl,
        originalUrl = null,
    )
    return NotificationCenterMediaItemUiModel(
        mediaId = mediaId,
        mediaType = resolvedType,
        mimeType = mimeType,
        mediaSource = AppContentMediaSource(
            thumbnailUrl = resolveBackendMediaUrl(thumbnailUrl ?: previewUrl),
            originalUrl = resolveBackendMediaUrl(mediaUrl),
            mediaUrl = resolveBackendMediaUrl(mediaUrl),
            videoUrl = resolveBackendMediaUrl(videoUrl),
            coverUrl = resolveBackendMediaUrl(coverUrl),
            mimeType = mimeType,
            durationMillis = durationMillis,
        ),
        displayTimeMillis = displayTimeMillis,
        durationMillis = durationMillis,
    )
}

private fun String?.orNotificationTargetSummary(): String {
    return this?.takeIf { it.isNotBlank() } ?: "查看相关内容"
}

private fun NotificationCenterItemUiModel.matchesNotificationCenterFilter(
    filter: NotificationCenterFilter,
): Boolean {
    return when (filter) {
        NotificationCenterFilter.LIFE -> belongsToLifeModule()
        NotificationCenterFilter.PHOTOS -> !belongsToLifeModule()
    }
}

fun NotificationCenterItemUiModel.belongsToLifeModule(): Boolean {
    if (module.equals("life", ignoreCase = true)) return true
    if (module.equals("photos", ignoreCase = true)) return false
    val text = listOfNotNull(targetType, targetSummary, title, body)
        .joinToString(separator = " ")
        .lowercase()
    return text.contains("life") ||
        text.contains("ledger") ||
        text.contains("chat") ||
        text.contains("账") ||
        text.contains("聊天") ||
        text.contains("痕迹")
}

fun NotificationCenterItemUiModel.isLifeLedgerTarget(): Boolean {
    return targetType.equals("LIFE_LEDGER", ignoreCase = true) ||
        targetSummary.contains("记账") ||
        title.contains("记账")
}

fun NotificationCenterItemUiModel.isLifeConsoleTarget(): Boolean {
    return targetType.equals("LIFE_CONSOLE", ignoreCase = true) ||
        targetSummary.contains("痕迹") ||
        title.contains("痕迹")
}

fun NotificationCenterItemUiModel.isLifeChatTarget(): Boolean {
    return targetType.equals("LIFE_CHAT", ignoreCase = true) ||
        targetSummary.contains("聊天") ||
        title.contains("聊天")
}

fun NotificationCenterItemUiModel.hasNotificationTargetAction(): Boolean {
    return id == "notice-cache-1" ||
        !postId.isNullOrBlank() ||
        !trashItemId.isNullOrBlank() ||
        type == NotificationCenterItemType.DELETE_RESTORE ||
        targetType.equals("UPLOAD", ignoreCase = true) ||
        isLifeLedgerTarget() ||
        isLifeConsoleTarget() ||
        isLifeChatTarget() ||
        type == NotificationCenterItemType.CONTENT_UPDATE
}

fun NotificationCenterItemUiModel.notificationTargetLabel(): String {
    return when {
        id == "notice-cache-1" -> "缓存管理"
        !postId.isNullOrBlank() -> "小相册"
        !trashItemId.isNullOrBlank() || type == NotificationCenterItemType.DELETE_RESTORE -> "回收站"
        targetType.equals("UPLOAD", ignoreCase = true) -> "传输中心"
        isLifeLedgerTarget() -> "记账"
        isLifeConsoleTarget() -> "今日痕迹"
        isLifeChatTarget() -> "聊天记录"
        type == NotificationCenterItemType.CONTENT_UPDATE -> "相册目录"
        else -> "通知说明"
    }
}

fun NotificationCenterItemUiModel.notificationActionLabel(): String {
    return when {
        id == "notice-cache-1" -> "前往缓存管理"
        !postId.isNullOrBlank() -> "查看小相册"
        !trashItemId.isNullOrBlank() || type == NotificationCenterItemType.DELETE_RESTORE -> "打开回收站"
        targetType.equals("UPLOAD", ignoreCase = true) -> "查看传输"
        isLifeLedgerTarget() -> "打开记账"
        isLifeConsoleTarget() -> "查看今日痕迹"
        isLifeChatTarget() -> "打开聊天记录"
        type == NotificationCenterItemType.CONTENT_UPDATE -> "查看相册目录"
        else -> "返回通知列表"
    }
}

fun NotificationCenterItemUiModel.notificationTargetDescription(): String {
    return when {
        id == "notice-cache-1" -> "这条通知对应的是本地清理与缓存管理入口。"
        !postId.isNullOrBlank() && !mediaId.isNullOrBlank() ->
            "会直接带你回到相关小相册，并高亮当前媒体。"
        !postId.isNullOrBlank() -> "会回到相关小相册，继续查看更新内容。"
        !trashItemId.isNullOrBlank() -> "会打开对应的回收站条目，继续恢复或删除。"
        type == NotificationCenterItemType.DELETE_RESTORE -> "会回到回收站，继续处理删除与恢复记录。"
        targetType.equals("UPLOAD", ignoreCase = true) -> "会打开传输中心，继续查看导入结果。"
        isLifeLedgerTarget() -> "会进入生活里的记账页，继续查看本月变化。"
        isLifeConsoleTarget() -> "会进入今日痕迹，继续查看当天更新。"
        isLifeChatTarget() -> "会进入聊天记录查看器。"
        type == NotificationCenterItemType.CONTENT_UPDATE -> "会回到相册目录，继续查看内容更新。"
        else -> "这条通知主要用于补充说明当前发生的变动。"
    }
}
