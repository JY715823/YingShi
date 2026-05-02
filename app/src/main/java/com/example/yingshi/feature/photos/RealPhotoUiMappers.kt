package com.example.yingshi.feature.photos

import androidx.compose.ui.graphics.Color
import com.example.yingshi.data.model.RemoteAlbum
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.model.RemotePendingCleanup
import com.example.yingshi.data.model.RemotePostDetail
import com.example.yingshi.data.model.RemotePostMedia
import com.example.yingshi.data.model.RemotePostSummary
import com.example.yingshi.data.model.RemoteTrashItem
import java.util.Calendar

fun RemoteAlbum.toAlbumSummaryUiModel(): AlbumSummaryUiModel {
    val palette = realPaletteFor(albumId)
    return AlbumSummaryUiModel(
        id = albumId,
        title = title,
        subtitle = subtitle.ifBlank { "共 $postCount 个帖子" },
        accent = palette,
    )
}

fun RemotePostSummary.toAlbumPostCardUiModel(
    selectedAlbumId: String,
    coverMedia: RemotePostMedia? = null,
): AlbumPostCardUiModel {
    val palette = realPaletteFor(coverMediaId ?: postId)
    val resolvedCoverType = coverMedia?.toResolvedAppMediaType() ?: AppMediaType.IMAGE
    return AlbumPostCardUiModel(
        id = postId,
        albumId = selectedAlbumId,
        albumIds = albumIds.ifEmpty { listOf(selectedAlbumId) },
        title = title,
        summary = summary.ifBlank { contributorLabel.orEmpty().ifBlank { "还没有简介" } },
        postDisplayTimeMillis = displayTimeMillis,
        mediaCount = mediaCount,
        coverPalette = palette,
        coverMediaType = resolvedCoverType,
        coverAspectRatio = coverMedia?.toResolvedAspectRatio(resolvedCoverType) ?: 1f,
        coverMediaSource = coverMedia?.toAppContentMediaSource(),
    )
}

fun RemotePostSummary.toPostDetailPlaceholderRoute(
    selectedAlbumId: String,
): PostDetailPlaceholderRoute {
    val palette = realPaletteFor(coverMediaId ?: postId)
    return PostDetailPlaceholderRoute(
        postId = postId,
        albumId = selectedAlbumId,
        albumIds = albumIds.ifEmpty { listOf(selectedAlbumId) },
        title = title,
        summary = summary.ifBlank { contributorLabel.orEmpty().ifBlank { "还没有简介" } },
        postDisplayTimeMillis = displayTimeMillis,
        mediaCount = mediaCount,
        coverPalette = palette,
        coverMediaType = AppMediaType.IMAGE,
        coverAspectRatio = 1f,
    )
}

fun RemotePostDetail.toPostDetailUiModel(
    albumTitleById: Map<String, String>,
): PostDetailUiModel {
    return PostDetailUiModel(
        postId = postId,
        title = title,
        summary = summary.ifBlank { "还没有简介" },
        contributorLabel = contributorLabel.orEmpty().ifBlank { "共享记录" },
        postDisplayTimeMillis = displayTimeMillis,
        albumIds = albumIds,
        albumChips = albumIds.map { albumId -> albumTitleById[albumId] ?: albumId },
        mediaItems = mediaItems.map(RemotePostMedia::toPostDetailMediaUiModel),
        comments = emptyList(),
    )
}

fun RemotePostDetail.toEditablePostDraft(): EditablePostDraft {
    return EditablePostDraft(
        postId = postId,
        title = title,
        summary = summary,
        postDisplayTimeMillis = displayTimeMillis,
        albumIds = albumIds,
    )
}

fun RemotePostDetail.toManagedPostMediaUiModels(): List<ManagedPostMediaUiModel> {
    return mediaItems.map { media ->
        val mediaKind = media.toResolvedAppMediaType()
        ManagedPostMediaUiModel(
            id = media.mediaId,
            displayTimeMillis = media.displayTimeMillis,
            commentCount = media.commentCount,
            palette = realPaletteFor(media.mediaId),
            mediaType = mediaKind,
            aspectRatio = media.toResolvedAspectRatio(mediaKind),
            isCover = media.isCover,
            videoDurationMillis = media.videoDurationMillis,
            mediaSource = media.toAppContentMediaSource(),
        )
    }
}

fun RemoteMedia.toPhotoFeedItem(): PhotoFeedItem {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = displayTimeMillis
    }
    val mediaKind = toResolvedAppMediaType()
    return PhotoFeedItem(
        mediaId = mediaId,
        mediaDisplayTimeMillis = displayTimeMillis,
        displayYear = calendar.get(Calendar.YEAR),
        displayMonth = calendar.get(Calendar.MONTH) + 1,
        displayDay = calendar.get(Calendar.DAY_OF_MONTH),
        commentCount = commentCount,
        postIds = postIds,
        palette = realPaletteFor(mediaId),
        mediaType = mediaKind,
        aspectRatio = toResolvedAspectRatio(mediaKind),
        width = width,
        height = height,
        videoDurationMillis = durationMillis,
        mediaSource = toAppContentMediaSource(),
    )
}

fun RemoteTrashItem.toTrashEntryUiModel(): TrashEntryUiModel {
    return TrashEntryUiModel(
        id = trashItemId,
        type = toTrashEntryType(),
        deletedAtMillis = deletedAtMillis,
        title = title.ifBlank { defaultTrashTitle() },
        previewInfo = previewInfo.ifBlank { defaultTrashPreview() },
        sourcePostId = sourcePostId,
        sourceMediaId = sourceMediaId,
        relatedPostIds = relatedPostIds,
        relatedMediaIds = relatedMediaIds,
        palette = realPaletteFor(sourceMediaId ?: sourcePostId ?: trashItemId),
    )
}

fun RemotePendingCleanup.toTrashPendingCleanupUiModel(): TrashPendingCleanupUiModel {
    return TrashPendingCleanupUiModel(
        entry = item.toTrashEntryUiModel(),
        removedAtMillis = removedAtMillis,
    )
}

fun RemotePostMedia.toPostDetailMediaUiModel(): PostDetailMediaUiModel {
    val mediaKind = toResolvedAppMediaType()
    return PostDetailMediaUiModel(
        id = mediaId,
        displayTimeMillis = displayTimeMillis,
        commentCount = commentCount,
        palette = realPaletteFor(mediaId),
        mediaType = mediaKind,
        aspectRatio = toResolvedAspectRatio(mediaKind),
        width = width,
        height = height,
        videoDurationMillis = videoDurationMillis,
        mediaSource = toAppContentMediaSource(),
    )
}

internal fun realPaletteFor(key: String): PhotoThumbnailPalette {
    val palettes = listOf(
        PhotoThumbnailPalette(
            start = Color(0xFFB8D8F8),
            end = Color(0xFF7EA6DF),
            accent = Color(0xFFE8F2FF),
        ),
        PhotoThumbnailPalette(
            start = Color(0xFFF5D2C3),
            end = Color(0xFFE7A08D),
            accent = Color(0xFFFFF0E8),
        ),
        PhotoThumbnailPalette(
            start = Color(0xFFCFE5B9),
            end = Color(0xFF84B38A),
            accent = Color(0xFFEFF8E1),
        ),
        PhotoThumbnailPalette(
            start = Color(0xFFD8D0F2),
            end = Color(0xFF8FA0D8),
            accent = Color(0xFFF0EDFF),
        ),
        PhotoThumbnailPalette(
            start = Color(0xFFE7CFB4),
            end = Color(0xFFB98B63),
            accent = Color(0xFFF7E8D4),
        ),
        PhotoThumbnailPalette(
            start = Color(0xFFC5D1DA),
            end = Color(0xFF8095A7),
            accent = Color(0xFFE7F0F6),
        ),
    )
    return palettes[key.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) } % palettes.size]
}

private fun RemoteMedia.toResolvedAppMediaType(): AppMediaType {
    return resolveAppMediaType(
        rawType = mediaType,
        mimeType = mimeType,
        thumbnailUrl = thumbnailUrl ?: previewUrl,
        mediaUrl = mediaUrl,
        videoUrl = videoUrl,
        coverUrl = coverUrl,
        originalUrl = originalUrl,
    )
}

private fun RemotePostMedia.toResolvedAppMediaType(): AppMediaType {
    return resolveAppMediaType(
        rawType = mediaType,
        mimeType = mimeType,
        thumbnailUrl = thumbnailUrl ?: previewUrl,
        mediaUrl = mediaUrl,
        videoUrl = videoUrl,
        coverUrl = coverUrl,
        originalUrl = originalUrl,
    )
}

private fun RemoteMedia.toResolvedAspectRatio(mediaType: AppMediaType): Float {
    return resolveAppContentAspectRatio(
        aspectRatio = aspectRatio,
        width = width,
        height = height,
        mediaType = mediaType,
    )
}

private fun RemotePostMedia.toResolvedAspectRatio(mediaType: AppMediaType): Float {
    return resolveAppContentAspectRatio(
        aspectRatio = aspectRatio,
        width = width,
        height = height,
        mediaType = mediaType,
    )
}

private fun RemoteTrashItem.toTrashEntryType(): TrashEntryType {
    return when (itemType) {
        "mediaRemoved" -> TrashEntryType.MEDIA_REMOVED
        "mediaSystemDeleted" -> TrashEntryType.MEDIA_SYSTEM_DELETED
        else -> TrashEntryType.POST_DELETED
    }
}

private fun RemoteTrashItem.defaultTrashTitle(): String {
    return when (toTrashEntryType()) {
        TrashEntryType.POST_DELETED -> "已删除帖子"
        TrashEntryType.MEDIA_REMOVED -> "已移出媒体"
        TrashEntryType.MEDIA_SYSTEM_DELETED -> "已删除媒体"
    }
}

private fun RemoteTrashItem.defaultTrashPreview(): String {
    return when (toTrashEntryType()) {
        TrashEntryType.POST_DELETED -> "帖子已移入回收站"
        TrashEntryType.MEDIA_REMOVED -> "媒体已从帖子中移出"
        TrashEntryType.MEDIA_SYSTEM_DELETED -> "媒体已从空间中删除"
    }
}
