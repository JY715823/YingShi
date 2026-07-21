package com.example.yingshi.feature.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.example.yingshi.data.model.RemotePostDetail
import com.example.yingshi.data.model.RemotePostMedia
import com.example.yingshi.data.model.RemoteTrashItem
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// region Presentation-state helpers

@Composable
internal fun rememberNotificationPresentation(
    item: NotificationCenterItemUiModel,
): NotificationResolvedPresentation {
    val rawPresentation = remember(item.id, item.title, item.body, item.targetSummary) {
        item.toResolvedPresentation()
    }
    val cachedPresentation = NotificationCenterViewModel.readMemoryPresentation(item.id)
    val resolvedPresentation by produceState(
        initialValue = cachedPresentation ?: rawPresentation,
        item.id,
        item.title,
        item.body,
        item.targetSummary,
    ) {
        if (cachedPresentation == null) {
            val presentation = resolveNotificationPresentation(item)
            NotificationCenterViewModel.writeMemoryPresentation(item.id, presentation)
            value = presentation
        }
    }
    return resolvedPresentation
}

internal fun NotificationCenterItemUiModel.toResolvedPresentation(): NotificationResolvedPresentation {
    return NotificationResolvedPresentation(
        title = title.ifBlank { type.label },
        body = body.ifBlank { "点开查看通知详情" },
        targetSummary = targetSummary.withNotificationTargetFallback(),
    )
}

internal suspend fun resolveNotificationPresentation(
    item: NotificationCenterItemUiModel,
): NotificationResolvedPresentation {
    val rawPresentation = item.toResolvedPresentation()
    val postVisual = item.postId?.let { postId ->
        loadNotificationPostVisual(postId = postId, focusMediaId = item.mediaId)
    }
    val trashVisual = item.trashItemId
        ?.takeIf { it.isNotBlank() }
        ?.let { trashItemId -> loadNotificationTrashVisual(trashItemId) }
    val visual = when {
        item.mediaId != null && postVisual?.mediaVisual != null -> postVisual.mediaVisual
        postVisual?.smallAlbumVisual != null -> postVisual.smallAlbumVisual
        trashVisual?.mediaVisual != null -> trashVisual.mediaVisual
        trashVisual?.smallAlbumVisual != null -> trashVisual.smallAlbumVisual
        else -> NotificationVisual.None
    }
    val resolvedTargetSummary = when {
        !item.targetSummary.shouldReplaceWithFriendlyCopy() -> item.targetSummary
        postVisual != null -> postVisual.title
        trashVisual != null -> trashVisual.targetSummary
        item.isLifeLedgerTarget() -> "记账"
        item.isLifeChatTarget() -> "聊天导入"
        item.isLifeConsoleTarget() -> "今日痕迹"
        item.targetType.equals("UPLOAD", ignoreCase = true) -> "传输中心"
        else -> item.targetSummary.withNotificationTargetFallback()
    }
    val resolvedTitle = if (rawPresentation.title.shouldReplaceWithFriendlyCopy()) {
        item.buildFriendlyTitle(
            resolvedTargetSummary = resolvedTargetSummary,
            postTitle = postVisual?.title,
        )
    } else {
        rawPresentation.title
    }
    val resolvedBody = if (rawPresentation.body.shouldReplaceWithFriendlyCopy()) {
        item.buildFriendlyBody(
            resolvedTargetSummary = resolvedTargetSummary,
            postTitle = postVisual?.title,
            rawBody = rawPresentation.body,
        )
    } else {
        rawPresentation.body
    }
    return NotificationResolvedPresentation(
        title = resolvedTitle,
        body = resolvedBody,
        targetSummary = resolvedTargetSummary.withNotificationTargetFallback(),
        visual = visual,
    )
}

// endregion

// region Remote detail loading

internal data class NotificationPostVisual(
    val title: String,
    val mediaVisual: NotificationVisual.Media?,
    val smallAlbumVisual: NotificationVisual.SmallAlbum?,
)

internal data class NotificationTrashVisual(
    val targetSummary: String,
    val mediaVisual: NotificationVisual.Media?,
    val smallAlbumVisual: NotificationVisual.SmallAlbum?,
)

internal suspend fun loadNotificationPostVisual(
    postId: String,
    focusMediaId: String?,
): NotificationPostVisual? {
    val detail = when (val result = RepositoryProvider.postRepository.getPostDetail(postId)) {
        is ApiResult.Success -> result.data
        else -> return null
    }
    val previewMedia = detail.mediaItems
        .distinctBy(RemotePostMedia::mediaId)
        .take(2)
        .map(RemotePostMedia::toNotificationPreviewMedia)
    val coverMedia = detail.mediaItems.firstOrNull { it.mediaId == focusMediaId }
        ?: detail.mediaItems.firstOrNull { it.isCover }
        ?: detail.mediaItems.firstOrNull { it.mediaId == detail.coverMediaId }
        ?: detail.mediaItems.firstOrNull()
    return NotificationPostVisual(
        title = detail.title.ifBlank { "小相册" },
        mediaVisual = coverMedia?.toNotificationMediaVisual(),
        smallAlbumVisual = NotificationVisual.SmallAlbum(
            title = detail.title.ifBlank { "小相册" },
            metaLabel = buildNotificationSmallAlbumMeta(detail),
            palette = realPaletteFor(detail.coverMediaId ?: coverMedia?.mediaId ?: detail.postId),
            previewMedia = previewMedia,
        ),
    )
}

internal suspend fun loadNotificationTrashVisual(trashItemId: String): NotificationTrashVisual? {
    val item = when (val result = RepositoryProvider.trashRepository.getTrashDetail(trashItemId)) {
        is ApiResult.Success -> result.data.item
        else -> return null
    }
    val entry = item.toTrashEntryUiModel()
    val mediaVisual = entry.mediaSnapshot?.let { media ->
        NotificationVisual.Media(
            mediaSource = media.mediaSource
                ?: realTrashMediaSource(
                    mediaId = media.mediaId,
                    mediaType = media.mediaType,
                    width = media.width,
                    height = media.height,
                    durationMillis = media.videoDurationMillis,
                ),
            mediaType = media.mediaType,
            palette = media.palette,
        )
    }
    val smallAlbumVisual = if (
        entry.type == TrashEntryType.SMALL_ALBUM_DELETED ||
        entry.type == TrashEntryType.LARGE_ALBUM_DELETED
    ) {
        NotificationVisual.SmallAlbum(
            title = entry.title.ifBlank {
                if (entry.type == TrashEntryType.LARGE_ALBUM_DELETED) {
                    "回收站大相册"
                } else {
                    "回收站小相册"
                }
            },
            metaLabel = "${formatNotificationTime(entry.deletedAtMillis)} · ${entry.relatedMediaIds.size.takeIf { it > 0 } ?: 0} 项",
            palette = entry.palette,
            previewMedia = emptyList(),
        )
    } else {
        null
    }
    return NotificationTrashVisual(
        targetSummary = when {
            item.sourcePostId != null -> entry.title
            item.sourceMediaId != null -> "回收站"
            else -> "回收站"
        },
        mediaVisual = mediaVisual,
        smallAlbumVisual = smallAlbumVisual,
    )
}

internal fun RemotePostMedia.toNotificationPreviewMedia(): AlbumPostPreviewMediaUiModel {
    val mediaType = resolveAppMediaType(
        rawType = mediaType,
        mimeType = mimeType,
        thumbnailUrl = thumbnailUrl ?: previewUrl,
        mediaUrl = mediaUrl,
        videoUrl = videoUrl,
        coverUrl = coverUrl,
        originalUrl = originalUrl,
    )
    return AlbumPostPreviewMediaUiModel(
        id = mediaId,
        palette = realPaletteFor(mediaId),
        mediaType = mediaType,
        aspectRatio = resolveAppContentAspectRatio(
            aspectRatio = aspectRatio,
            width = width,
            height = height,
            mediaType = mediaType,
        ),
        mediaSource = toAppContentMediaSource(),
    )
}

internal fun RemotePostMedia.toNotificationMediaVisual(): NotificationVisual.Media {
    val resolvedType = resolveAppMediaType(
        rawType = mediaType,
        mimeType = mimeType,
        thumbnailUrl = thumbnailUrl ?: previewUrl,
        mediaUrl = mediaUrl,
        videoUrl = videoUrl,
        coverUrl = coverUrl,
        originalUrl = originalUrl,
    )
    return NotificationVisual.Media(
        mediaSource = toAppContentMediaSource(),
        mediaType = resolvedType,
        palette = realPaletteFor(mediaId),
    )
}

internal fun buildNotificationSmallAlbumMeta(detail: RemotePostDetail): String {
    return "${formatNotificationTime(detail.displayTimeMillis)} · ${detail.mediaItems.size} 张"
}

// endregion

// region Friendly copy building

internal fun NotificationCenterItemUiModel.buildFriendlyTitle(
    resolvedTargetSummary: String,
    postTitle: String?,
): String {
    val targetLabel = postTitle?.takeIf { it.isNotBlank() } ?: resolvedTargetSummary.withNotificationTargetFallback()
    return when {
        isLifeLedgerTarget() -> "记账有新动态"
        isLifeChatTarget() -> "聊天导入有新动态"
        isLifeConsoleTarget() -> "今日痕迹有新更新"
        targetType.equals("UPLOAD", ignoreCase = true) -> "传输中心有新进度"
        type == NotificationCenterItemType.COMMENT -> "「$targetLabel」有新评论"
        type == NotificationCenterItemType.CONTENT_UPDATE && targetType.equals("ALBUM", ignoreCase = true) -> "相册目录有更新"
        type == NotificationCenterItemType.CONTENT_UPDATE -> "「$targetLabel」有内容更新"
        type == NotificationCenterItemType.DELETE_RESTORE -> "$targetLabel 有回收站变动"
        else -> if (targetLabel.isNotBlank()) "$targetLabel 有新提醒" else type.label
    }
}

internal fun NotificationCenterItemUiModel.buildFriendlyBody(
    resolvedTargetSummary: String,
    postTitle: String?,
    rawBody: String,
): String {
    val targetLabel = postTitle?.takeIf { it.isNotBlank() } ?: resolvedTargetSummary.withNotificationTargetFallback()
    return when {
        isLifeLedgerTarget() -> "点开可直接查看对应的账本统计和最近变化。"
        isLifeChatTarget() -> "点开可查看最新导入的聊天内容。"
        isLifeConsoleTarget() -> "点开可查看今天新增的生活记录。"
        targetType.equals("UPLOAD", ignoreCase = true) -> "点开可查看当前文件传输状态。"
        type == NotificationCenterItemType.COMMENT -> "点开可直接回到 $targetLabel 查看评论上下文。"
        type == NotificationCenterItemType.DELETE_RESTORE -> "点开可查看回收站中的对应条目，并继续恢复或删除。"
        type == NotificationCenterItemType.CONTENT_UPDATE -> "点开可回到 $targetLabel 查看最新内容。"
        rawBody.isNotBlank() -> rawBody
        else -> "点开查看相关内容。"
    }
}

internal fun String?.shouldReplaceWithFriendlyCopy(): Boolean {
    val value = this?.trim().orEmpty()
    if (value.isBlank()) return true
    // If text contains any Han (Chinese) characters, it's already human-readable — keep it
    if (value.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }) return false
    val looksFileName = value.contains('.') && value.any(Char::isDigit)
    val looksIdentifier = Regex("[A-Za-z0-9_-]{12,}").containsMatchIn(value)
    return looksFileName || looksIdentifier || value.all { it.code in 32..126 }
}

internal fun String?.withNotificationTargetFallback(): String {
    return this?.takeIf { it.isNotBlank() } ?: "查看相关内容"
}

// endregion

// region Filtering & grouping

internal fun List<NotificationCenterItemUiModel>.filterBy(
    filter: NotificationCenterFilter,
): List<NotificationCenterItemUiModel> {
    return filter { it.matchesModule(filter) }
}

internal fun List<NotificationCenterItemUiModel>.filterBy(
    filter: NotificationCenterFilter,
    category: NotificationCategoryFilter,
): List<NotificationCenterItemUiModel> {
    return if (category == NotificationCategoryFilter.ALL) {
        this
    } else {
        filter { it.matchesCategory(filter, category) }
    }
}

internal fun List<NotificationCenterItemUiModel>.filterByActor(
    includeSelf: Boolean,
    includePartner: Boolean,
): List<NotificationCenterItemUiModel> {
    if (includeSelf && includePartner) return this
    if (!includeSelf && !includePartner) return emptyList()
    return filter { item ->
        if (item.actorIsCurrentUser) includeSelf else includePartner
    }
}

internal data class NotificationDateSection(
    val key: String,
    val title: String,
    val items: List<NotificationCenterItemUiModel>,
)

internal fun List<NotificationCenterItemUiModel>.toNotificationDateSections(): List<NotificationDateSection> {
    return groupBy { item -> notificationDateKey(item.createdAtMillis) }
        .map { (key, items) ->
            val sortedItems = items.sortedByDescending(NotificationCenterItemUiModel::createdAtMillis)
            NotificationDateSection(
                key = key,
                title = notificationDateTitle(sortedItems.first().createdAtMillis),
                items = sortedItems,
            )
        }
        .sortedByDescending { section -> section.items.firstOrNull()?.createdAtMillis ?: 0L }
}

internal fun List<NotificationCenterItemUiModel>.unreadCount(
    filter: NotificationCenterFilter,
): Int {
    return filterBy(filter).count { !it.isRead }
}

internal fun NotificationCenterItemUiModel.matchesModule(
    filter: NotificationCenterFilter,
): Boolean {
    if (module.equals("life", ignoreCase = true)) {
        return filter == NotificationCenterFilter.LIFE
    }
    if (module.equals("photos", ignoreCase = true)) {
        return filter == NotificationCenterFilter.PHOTOS
    }
    val haystack = listOfNotNull(targetType, targetSummary, title, body)
        .joinToString(separator = " ")
        .lowercase()
    val isLife = haystack.contains("life") ||
        haystack.contains("ledger") ||
        haystack.contains("chat") ||
        haystack.contains("账") ||
        haystack.contains("聊天") ||
        haystack.contains("痕迹")
    return when (filter) {
        NotificationCenterFilter.LIFE -> isLife
        NotificationCenterFilter.PHOTOS -> !isLife
    }
}

internal fun NotificationCenterItemUiModel.matchesCategory(
    filter: NotificationCenterFilter,
    category: NotificationCategoryFilter,
): Boolean {
    return when (filter) {
        NotificationCenterFilter.PHOTOS -> when (category) {
            NotificationCategoryFilter.ALL -> true
            NotificationCategoryFilter.COMMENT -> this.category.equals("comment", ignoreCase = true) || type == NotificationCenterItemType.COMMENT
            NotificationCategoryFilter.CONTENT_UPDATE -> this.category.equals("content_update", ignoreCase = true) || type == NotificationCenterItemType.CONTENT_UPDATE
            NotificationCategoryFilter.DELETE_RESTORE -> this.category.equals("delete", ignoreCase = true) || type == NotificationCenterItemType.DELETE_RESTORE
            NotificationCategoryFilter.SYSTEM -> this.category.equals("system", ignoreCase = true) || type == NotificationCenterItemType.SYSTEM
            NotificationCategoryFilter.LEDGER,
            NotificationCategoryFilter.CHAT,
            NotificationCategoryFilter.TRACE,
            -> false
        }

        NotificationCenterFilter.LIFE -> when (category) {
            NotificationCategoryFilter.ALL -> true
            NotificationCategoryFilter.LEDGER -> this.category.equals("ledger", ignoreCase = true) || isLifeLedgerTarget()
            NotificationCategoryFilter.CHAT -> this.category.equals("chat", ignoreCase = true) || isLifeChatTarget()
            NotificationCategoryFilter.TRACE -> this.category.equals("trace", ignoreCase = true) || isLifeConsoleTarget()
            NotificationCategoryFilter.SYSTEM -> {
                this.category.equals("system", ignoreCase = true) ||
                    matchesModule(NotificationCenterFilter.LIFE) &&
                    !isLifeLedgerTarget() &&
                    !isLifeChatTarget() &&
                    !isLifeConsoleTarget()
            }
            NotificationCategoryFilter.COMMENT,
            NotificationCategoryFilter.CONTENT_UPDATE,
            NotificationCategoryFilter.DELETE_RESTORE,
            -> false
        }
    }
}

internal fun notificationCategoriesFor(
    filter: NotificationCenterFilter,
): List<NotificationCategoryFilter> {
    return when (filter) {
        NotificationCenterFilter.PHOTOS -> listOf(
            NotificationCategoryFilter.ALL,
            NotificationCategoryFilter.CONTENT_UPDATE,
            NotificationCategoryFilter.COMMENT,
            NotificationCategoryFilter.DELETE_RESTORE,
        )

        NotificationCenterFilter.LIFE -> listOf(
            NotificationCategoryFilter.ALL,
            NotificationCategoryFilter.TRACE,
            NotificationCategoryFilter.LEDGER,
            NotificationCategoryFilter.CHAT,
        )
    }
}

internal fun NotificationCategoryFilter.displayLabel(
    filter: NotificationCenterFilter,
): String {
    return when (filter) {
        NotificationCenterFilter.PHOTOS -> label
        NotificationCenterFilter.LIFE -> when (this) {
            NotificationCategoryFilter.CHAT -> "聊天导入"
            else -> label
        }
    }
}

// endregion

// region Time formatting

internal fun formatNotificationTime(timeMillis: Long): String {
    return SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}

internal fun notificationDateKey(timeMillis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(timeMillis))
}

internal fun notificationDateTitle(timeMillis: Long): String {
    return SimpleDateFormat("M月d日", Locale.CHINA).format(Date(timeMillis))
}

// endregion