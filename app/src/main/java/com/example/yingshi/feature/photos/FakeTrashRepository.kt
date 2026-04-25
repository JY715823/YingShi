package com.example.yingshi.feature.photos

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FakeTrashRepository {
    private val entries = mutableStateListOf<TrashEntryUiModel>()

    fun getEntries(type: TrashEntryType): List<TrashEntryUiModel> {
        return entries
            .filter { it.type == type }
            .sortedByDescending { it.deletedAtMillis }
    }

    fun recordDeletedPost(
        post: AlbumPostCardUiModel,
        mediaIds: List<String>,
        deletedAtMillis: Long = System.currentTimeMillis(),
    ) {
        entries.add(
            0,
            TrashEntryUiModel(
                id = "trash-post-${post.id}-$deletedAtMillis",
                type = TrashEntryType.POST_DELETED,
                deletedAtMillis = deletedAtMillis,
                title = post.title.ifBlank { "未命名帖子" },
                previewInfo = "删除于 ${formatTrashTime(deletedAtMillis)} · ${mediaIds.size} 张媒体 · ${post.albumIds.size.coerceAtLeast(1)} 个所属相册",
                sourcePostId = post.id,
                relatedMediaIds = mediaIds,
                palette = post.coverPalette,
            ),
        )
    }

    fun recordRemovedMedia(
        post: AlbumPostCardUiModel,
        mediaSnapshots: List<TrashMediaSnapshot>,
        deletedAtMillis: Long = System.currentTimeMillis(),
    ) {
        mediaSnapshots.forEach { media ->
            entries.add(
                0,
                TrashEntryUiModel(
                    id = "trash-removed-${post.id}-${media.mediaId}-$deletedAtMillis",
                    type = TrashEntryType.MEDIA_REMOVED,
                    deletedAtMillis = deletedAtMillis,
                    title = "从《${post.title.ifBlank { "当前帖子" }}》移除媒体",
                    previewInfo = "${formatTrashMediaLabel(media.displayTimeMillis)} · 媒体本体和评论仍保留",
                    sourcePostId = post.id,
                    sourceMediaId = media.mediaId,
                    relatedPostIds = listOf(post.id),
                    relatedMediaIds = listOf(media.mediaId),
                    palette = media.palette,
                ),
            )
        }
    }

    fun recordSystemDeletedMedia(
        mediaSnapshots: List<TrashMediaSnapshot>,
        deletedAtMillis: Long = System.currentTimeMillis(),
    ) {
        mediaSnapshots.forEach { media ->
            entries.add(
                0,
                TrashEntryUiModel(
                    id = "trash-system-${media.mediaId}-$deletedAtMillis",
                    type = TrashEntryType.MEDIA_SYSTEM_DELETED,
                    deletedAtMillis = deletedAtMillis,
                    title = media.sourcePostTitle?.let { "系统删除《$it》中的媒体" } ?: "系统删除媒体",
                    previewInfo = "${formatTrashMediaLabel(media.displayTimeMillis)} · 已从全局媒体流和相关帖子中本地隐藏",
                    sourcePostId = media.sourcePostId,
                    sourceMediaId = media.mediaId,
                    relatedPostIds = media.sourcePostId?.let(::listOf).orEmpty(),
                    relatedMediaIds = listOf(media.mediaId),
                    palette = media.palette,
                ),
            )
        }
    }

    private fun formatTrashTime(timeMillis: Long): String {
        return SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
    }

    private fun formatTrashMediaLabel(timeMillis: Long): String {
        return SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
    }
}
