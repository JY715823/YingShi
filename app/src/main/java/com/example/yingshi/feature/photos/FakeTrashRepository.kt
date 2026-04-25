package com.example.yingshi.feature.photos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FakeTrashRepository {
    private data class PendingTrashRemoval(
        val entry: TrashEntryUiModel,
        val removedAtMillis: Long,
    )

    private val entries = mutableStateListOf<TrashEntryUiModel>()
    private val pendingRemovals = mutableStateListOf<PendingTrashRemoval>()
    private var latestSnackbarMessage by mutableStateOf<TrashSnackbarMessageUiModel?>(null)

    fun getEntries(type: TrashEntryType): List<TrashEntryUiModel> {
        return entries
            .filter { it.type == type }
            .sortedByDescending { it.deletedAtMillis }
    }

    fun getEntry(entryId: String): TrashEntryUiModel? {
        return entries.firstOrNull { it.id == entryId }
    }

    fun resolveDetailEntry(route: TrashDetailRoute): TrashEntryUiModel? {
        getEntry(route.entryId)?.let { return it }

        return entries.firstOrNull { entry ->
            entry.type == route.entryType &&
                entry.sourcePostId == route.sourcePostId &&
                entry.sourceMediaId == route.sourceMediaId
        }
    }

    fun getPendingCleanupEntries(): List<TrashPendingCleanupUiModel> {
        return pendingRemovals
            .sortedByDescending { it.removedAtMillis }
            .map { pending ->
                TrashPendingCleanupUiModel(
                    entry = pending.entry,
                    removedAtMillis = pending.removedAtMillis,
                )
            }
    }

    fun getSnackbarMessage(): TrashSnackbarMessageUiModel? = latestSnackbarMessage

    fun consumeSnackbarMessage(entryId: String) {
        if (latestSnackbarMessage?.entryId == entryId) {
            latestSnackbarMessage = null
        }
    }

    fun undoPendingRemoval(entryId: String): Boolean {
        val pending = pendingRemovals.firstOrNull { it.entry.id == entryId } ?: return false
        pendingRemovals.remove(pending)
        entries.add(0, pending.entry)
        consumeSnackbarMessage(entryId)
        return true
    }

    fun moveEntryOutOfTrash(entryId: String): Boolean {
        val entry = getEntry(entryId) ?: return false
        entries.remove(entry)
        pendingRemovals.add(
            0,
            PendingTrashRemoval(
                entry = entry,
                removedAtMillis = System.currentTimeMillis(),
            ),
        )
        latestSnackbarMessage = TrashSnackbarMessageUiModel(
            entryId = entryId,
            message = "已移出回收站（24h可撤销）",
        )
        return true
    }

    fun restoreEntry(entryId: String): TrashMutationResult {
        val entry = getEntry(entryId)
            ?: return TrashMutationResult(
                success = false,
                message = "该删除项不存在或已被移出回收站。",
            )

        val restored = when (entry.type) {
            TrashEntryType.POST_DELETED -> {
                entry.postSnapshot?.let(FakeAlbumRepository::restorePost) == true
            }

            TrashEntryType.MEDIA_REMOVED -> {
                val postId = entry.sourcePostId
                val mediaSnapshot = entry.mediaSnapshot
                if (postId == null || mediaSnapshot == null) {
                    false
                } else {
                    FakeAlbumRepository.restoreMediaToPost(
                        postId = postId,
                        mediaSnapshot = mediaSnapshot,
                    )
                }
            }

            TrashEntryType.MEDIA_SYSTEM_DELETED -> {
                val mediaId = entry.sourceMediaId ?: entry.mediaSnapshot?.mediaId
                if (mediaId == null) {
                    false
                } else {
                    FakePhotoFeedRepository.unhideMediaGlobally(listOf(mediaId))
                    FakePhotoFeedRepository.unhidePostsLocally(entry.relationSnapshots.map { it.postId })
                    FakeAlbumRepository.restoreMediaRelations(entry.relationSnapshots)
                    true
                }
            }
        }

        return if (restored) {
            entries.remove(entry)
            TrashMutationResult(
                success = true,
                message = when (entry.type) {
                    TrashEntryType.POST_DELETED -> "已恢复帖子删除。"
                    TrashEntryType.MEDIA_REMOVED -> "已恢复媒体与原帖关系。"
                    TrashEntryType.MEDIA_SYSTEM_DELETED -> "已恢复媒体本体和关联关系。"
                },
            )
        } else {
            TrashMutationResult(
                success = false,
                message = "当前对象已不存在，暂时无法恢复。",
            )
        }
    }

    fun recordDeletedPost(
        snapshot: TrashPostSnapshot,
        deletedAtMillis: Long = System.currentTimeMillis(),
    ) {
        removeDuplicatePostEntry(snapshot.post.id)
        entries.add(
            0,
            TrashEntryUiModel(
                id = "trash-post-${snapshot.post.id}-$deletedAtMillis",
                type = TrashEntryType.POST_DELETED,
                deletedAtMillis = deletedAtMillis,
                title = snapshot.post.title.ifBlank { "未命名帖子" },
                previewInfo = "删除于 ${formatTrashTime(deletedAtMillis)} · ${snapshot.mediaSnapshots.size} 张媒体 · ${snapshot.post.albumIds.size.coerceAtLeast(1)} 个所属相册",
                sourcePostId = snapshot.post.id,
                relatedMediaIds = snapshot.mediaSnapshots.map { it.mediaId },
                postSnapshot = snapshot,
                palette = snapshot.post.coverPalette,
            ),
        )
    }

    fun recordRemovedMedia(
        post: AlbumPostCardUiModel,
        mediaSnapshots: List<TrashMediaSnapshot>,
        deletedAtMillis: Long = System.currentTimeMillis(),
    ) {
        mediaSnapshots.forEach { media ->
            removeDuplicateMediaEntry(
                type = TrashEntryType.MEDIA_REMOVED,
                mediaId = media.mediaId,
                postId = post.id,
            )
            entries.add(
                0,
                TrashEntryUiModel(
                    id = "trash-removed-${post.id}-${media.mediaId}-$deletedAtMillis",
                    type = TrashEntryType.MEDIA_REMOVED,
                    deletedAtMillis = deletedAtMillis,
                    title = "从「${post.title.ifBlank { "当前帖子" }}」移除媒体",
                    previewInfo = "${formatTrashMediaLabel(media.displayTimeMillis)} · 媒体本体和评论仍保留",
                    sourcePostId = post.id,
                    sourceMediaId = media.mediaId,
                    relatedPostIds = listOf(post.id),
                    relatedMediaIds = listOf(media.mediaId),
                    mediaSnapshot = media,
                    relationSnapshots = listOf(
                        TrashPostRelationSnapshot(
                            postId = post.id,
                            postTitle = post.title.ifBlank { "当前帖子" },
                            mediaSnapshot = media,
                        ),
                    ),
                    palette = media.palette,
                ),
            )
        }
    }

    fun recordSystemDeletedMedia(
        mediaSnapshots: List<TrashMediaSnapshot>,
        relationSnapshotsByMediaId: Map<String, List<TrashPostRelationSnapshot>>,
        deletedAtMillis: Long = System.currentTimeMillis(),
    ) {
        mediaSnapshots.forEach { media ->
            removeDuplicateMediaEntry(
                type = TrashEntryType.MEDIA_SYSTEM_DELETED,
                mediaId = media.mediaId,
                postId = null,
            )
            val relations = relationSnapshotsByMediaId[media.mediaId].orEmpty()
            entries.add(
                0,
                TrashEntryUiModel(
                    id = "trash-system-${media.mediaId}-$deletedAtMillis",
                    type = TrashEntryType.MEDIA_SYSTEM_DELETED,
                    deletedAtMillis = deletedAtMillis,
                    title = media.sourcePostTitle?.let { "系统删除「$it」中的媒体" } ?: "系统删除媒体",
                    previewInfo = "${formatTrashMediaLabel(media.displayTimeMillis)} · 已从全局媒体流和相关帖子中本地隐藏",
                    sourcePostId = media.sourcePostId,
                    sourceMediaId = media.mediaId,
                    relatedPostIds = relations.map { it.postId }.distinct(),
                    relatedMediaIds = listOf(media.mediaId),
                    mediaSnapshot = media,
                    relationSnapshots = relations,
                    palette = media.palette,
                ),
            )
        }
    }

    private fun removeDuplicatePostEntry(postId: String) {
        entries.removeAll { entry ->
            entry.type == TrashEntryType.POST_DELETED && entry.sourcePostId == postId
        }
        pendingRemovals.removeAll { pending ->
            pending.entry.type == TrashEntryType.POST_DELETED && pending.entry.sourcePostId == postId
        }
    }

    private fun removeDuplicateMediaEntry(
        type: TrashEntryType,
        mediaId: String,
        postId: String?,
    ) {
        entries.removeAll { entry ->
            entry.type == type &&
                entry.sourceMediaId == mediaId &&
                entry.sourcePostId == postId
        }
        pendingRemovals.removeAll { pending ->
            pending.entry.type == type &&
                pending.entry.sourceMediaId == mediaId &&
                pending.entry.sourcePostId == postId
        }
    }

    private fun formatTrashTime(timeMillis: Long): String {
        return SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
    }

    private fun formatTrashMediaLabel(timeMillis: Long): String {
        return SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
    }
}
