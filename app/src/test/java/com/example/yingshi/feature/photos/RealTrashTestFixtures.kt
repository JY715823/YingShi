package com.example.yingshi.feature.photos

import androidx.compose.ui.graphics.Color
import com.example.yingshi.data.model.RemotePendingCleanup
import com.example.yingshi.data.model.RemoteTrashDetail
import com.example.yingshi.data.model.RemoteTrashItem

/**
 * Round 4 测试夹具：回收站模块数据工厂。
 *
 * 所有参数都有默认值，调用方按需覆盖。默认构造一个 MEDIA_SYSTEM_DELETED 类型的回收站条目。
 */
internal fun sampleRemoteTrashItem(
    trashItemId: String = "trash-item-1",
    itemType: String = "MEDIA_SYSTEM_DELETED",
    state: String? = "IN_TRASH",
    actorUserId: String? = null,
    sourceSmallAlbumId: String? = null,
    sourceMediaId: String? = "media-1",
    commentTargetMediaId: String? = null,
    title: String = "测试回收站条目",
    previewInfo: String = "预览信息",
    deletedAtMillis: Long = 1_780_000_000_000L,
    relatedSmallAlbumIds: List<String> = emptyList(),
    relatedMediaIds: List<String> = listOf("media-1"),
    sourceMediaType: String? = null,
    sourceMediaWidth: Int? = null,
    sourceMediaHeight: Int? = null,
    sourceMediaAspectRatio: Float? = null,
    sourceMediaDurationMillis: Long? = null,
    sourceMediaMimeType: String? = null,
): RemoteTrashItem = RemoteTrashItem(
    trashItemId = trashItemId,
    itemType = itemType,
    state = state,
    actorUserId = actorUserId,
    sourceSmallAlbumId = sourceSmallAlbumId,
    sourceMediaId = sourceMediaId,
    commentTargetMediaId = commentTargetMediaId,
    title = title,
    previewInfo = previewInfo,
    deletedAtMillis = deletedAtMillis,
    relatedSmallAlbumIds = relatedSmallAlbumIds,
    relatedMediaIds = relatedMediaIds,
    sourceMediaType = sourceMediaType,
    sourceMediaWidth = sourceMediaWidth,
    sourceMediaHeight = sourceMediaHeight,
    sourceMediaAspectRatio = sourceMediaAspectRatio,
    sourceMediaDurationMillis = sourceMediaDurationMillis,
    sourceMediaMimeType = sourceMediaMimeType,
)

internal fun sampleRemoteTrashDetail(
    item: RemoteTrashItem = sampleRemoteTrashItem(),
    canRestore: Boolean = true,
    canMoveOutOfTrash: Boolean = true,
    pendingCleanup: RemotePendingCleanup? = null,
): RemoteTrashDetail = RemoteTrashDetail(
    item = item,
    canRestore = canRestore,
    canMoveOutOfTrash = canMoveOutOfTrash,
    pendingCleanup = pendingCleanup,
)

internal fun sampleRemotePendingCleanup(
    trashItemId: String = "pending-1",
    removedAtMillis: Long = 1_780_000_000_000L,
    undoDeadlineMillis: Long = removedAtMillis + 24L * 60L * 60L * 1000L,
    item: RemoteTrashItem = sampleRemoteTrashItem(trashItemId = trashItemId),
): RemotePendingCleanup = RemotePendingCleanup(
    trashItemId = trashItemId,
    removedAtMillis = removedAtMillis,
    undoDeadlineMillis = undoDeadlineMillis,
    item = item,
)

internal val samplePalette: PhotoThumbnailPalette = PhotoThumbnailPalette(
    start = Color(0xFF112233),
    end = Color(0xFF223344),
    accent = Color(0xFF335577),
)

internal fun sampleTrashMediaSnapshot(
    mediaId: String = "media-1",
    displayTimeMillis: Long = 1_780_000_000_000L,
    mediaType: AppMediaType = AppMediaType.IMAGE,
    aspectRatio: Float = 0.75f,
    width: Int? = 1080,
    height: Int? = 1440,
    videoDurationMillis: Long? = null,
    sourcePostId: String? = null,
    sourcePostTitle: String? = null,
    isCover: Boolean = false,
): TrashMediaSnapshot = TrashMediaSnapshot(
    mediaId = mediaId,
    displayTimeMillis = displayTimeMillis,
    palette = samplePalette,
    mediaType = mediaType,
    aspectRatio = aspectRatio,
    width = width,
    height = height,
    videoDurationMillis = videoDurationMillis,
    mediaSource = null,
    isCover = isCover,
    sourcePostId = sourcePostId,
    sourcePostTitle = sourcePostTitle,
)

internal fun sampleTrashEntryUiModel(
    id: String = "entry-1",
    type: TrashEntryType = TrashEntryType.MEDIA_SYSTEM_DELETED,
    deletedAtMillis: Long = 1_780_000_000_000L,
    title: String = "测试条目",
    previewInfo: String = "预览信息",
    actorUserId: String? = null,
    sourcePostId: String? = null,
    sourceMediaId: String? = null,
    commentTargetMediaId: String? = null,
    relatedPostIds: List<String> = emptyList(),
    relatedMediaIds: List<String> = emptyList(),
    albumSnapshot: TrashAlbumSnapshot? = null,
    postSnapshot: TrashPostSnapshot? = null,
    mediaSnapshot: TrashMediaSnapshot? = null,
    relationSnapshots: List<TrashPostRelationSnapshot> = emptyList(),
): TrashEntryUiModel = TrashEntryUiModel(
    id = id,
    type = type,
    deletedAtMillis = deletedAtMillis,
    title = title,
    previewInfo = previewInfo,
    actorUserId = actorUserId,
    sourcePostId = sourcePostId,
    sourceMediaId = sourceMediaId,
    commentTargetMediaId = commentTargetMediaId,
    relatedPostIds = relatedPostIds,
    relatedMediaIds = relatedMediaIds,
    albumSnapshot = albumSnapshot,
    postSnapshot = postSnapshot,
    mediaSnapshot = mediaSnapshot,
    relationSnapshots = relationSnapshots,
    palette = samplePalette,
)

internal fun sampleTrashPendingCleanupUiModel(
    entry: TrashEntryUiModel = sampleTrashEntryUiModel(),
    removedAtMillis: Long = 1_780_000_000_000L,
    undoDeadlineMillis: Long = removedAtMillis + 24L * 60L * 60L * 1000L,
): TrashPendingCleanupUiModel = TrashPendingCleanupUiModel(
    entry = entry,
    removedAtMillis = removedAtMillis,
    undoDeadlineMillis = undoDeadlineMillis,
)
