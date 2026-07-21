package com.example.yingshi.feature.photos

import androidx.compose.runtime.Immutable

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

// --- 扩展函数：路由判断 ---

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
    return targetSummary == "设置" ||
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
        targetSummary == "设置" -> "缓存管理"
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
        targetSummary == "设置" -> "前往缓存管理"
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
        targetSummary == "设置" -> "这条通知对应的是本地清理与缓存管理入口。"
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