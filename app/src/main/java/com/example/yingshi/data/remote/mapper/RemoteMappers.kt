package com.example.yingshi.data.remote.mapper

import com.example.yingshi.data.model.RemoteAlbum
import com.example.yingshi.data.model.RemoteComment
import com.example.yingshi.data.model.RemoteCommentPage
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.model.RemotePostDetail
import com.example.yingshi.data.model.RemotePostMedia
import com.example.yingshi.data.model.RemotePostSummary
import com.example.yingshi.data.model.RemotePendingCleanup
import com.example.yingshi.data.model.RemoteTrashDetail
import com.example.yingshi.data.model.RemoteTrashItem
import com.example.yingshi.data.model.RemoteUploadToken
import com.example.yingshi.data.model.RemoteUploadTask
import com.example.yingshi.data.model.UploadState
import com.example.yingshi.data.remote.dto.AlbumDto
import com.example.yingshi.data.remote.dto.CommentDto
import com.example.yingshi.data.remote.dto.CommentListResponseDto
import com.example.yingshi.data.remote.dto.MediaDto
import com.example.yingshi.data.remote.dto.PostDetailDto
import com.example.yingshi.data.remote.dto.PostMediaDto
import com.example.yingshi.data.remote.dto.PostSummaryDto
import com.example.yingshi.data.remote.dto.PendingCleanupDto
import com.example.yingshi.data.remote.dto.TrashDetailDto
import com.example.yingshi.data.remote.dto.TrashItemDto
import com.example.yingshi.data.remote.dto.UploadCompleteResponseDto
import com.example.yingshi.data.remote.dto.UploadTaskDto
import com.example.yingshi.data.remote.dto.UploadTokenDto

fun MediaDto.toRemoteModel(): RemoteMedia {
    val normalizedDisplayTime = displayTimeMillis.takeIf { it > 0L } ?: createdAtMillis ?: 0L
    return RemoteMedia(
        mediaId = mediaId,
        mediaType = mediaType?.ifBlank { type.orEmpty() }.orEmpty().ifBlank { "image" },
        previewUrl = previewUrl ?: thumbnailUrl,
        originalUrl = originalUrl,
        videoUrl = videoUrl,
        width = width,
        height = height,
        aspectRatio = aspectRatio,
        displayTimeMillis = normalizedDisplayTime,
        commentCount = 0,
        postIds = postIds,
        thumbnailUrl = thumbnailUrl ?: previewUrl,
        mediaUrl = mediaUrl ?: url,
        coverUrl = coverUrl,
        mimeType = mimeType,
        durationMillis = durationMillis ?: duration,
        createdAtMillis = createdAtMillis,
    )
}

fun AlbumDto.toRemoteModel(): RemoteAlbum {
    return RemoteAlbum(
        albumId = albumId,
        title = title,
        subtitle = subtitle,
        coverMediaId = coverMediaId,
        postCount = postCount,
    )
}

fun PostSummaryDto.toRemoteSummary(): RemotePostSummary {
    return RemotePostSummary(
        postId = postId,
        title = title,
        summary = summary,
        contributorLabel = contributorLabel,
        displayTimeMillis = displayTimeMillis,
        albumIds = albumIds,
        coverMediaId = coverMediaId,
        mediaCount = mediaCount,
    )
}

fun PostMediaDto.toRemotePostMedia(): RemotePostMedia {
    val normalizedDisplayTime = media.displayTimeMillis.takeIf { it > 0L } ?: media.createdAtMillis ?: 0L
    return RemotePostMedia(
        mediaId = media.mediaId,
        mediaType = media.mediaType?.ifBlank { media.type.orEmpty() }.orEmpty().ifBlank { "image" },
        previewUrl = media.previewUrl ?: media.thumbnailUrl,
        originalUrl = media.originalUrl,
        videoUrl = media.videoUrl,
        width = media.width,
        height = media.height,
        aspectRatio = media.aspectRatio,
        displayTimeMillis = normalizedDisplayTime,
        commentCount = 0,
        isCover = isCover,
        videoDurationMillis = media.durationMillis ?: media.duration,
        thumbnailUrl = media.thumbnailUrl ?: media.previewUrl,
        mediaUrl = media.mediaUrl ?: media.url,
        coverUrl = media.coverUrl,
        mimeType = media.mimeType,
        createdAtMillis = media.createdAtMillis,
    )
}

fun PostDetailDto.toRemoteDetail(): RemotePostDetail {
    return RemotePostDetail(
        postId = postId,
        title = title,
        summary = summary,
        contributorLabel = contributorLabel,
        displayTimeMillis = displayTimeMillis,
        albumIds = albumIds,
        coverMediaId = coverMediaId,
        mediaItems = mediaItems.map(PostMediaDto::toRemotePostMedia),
    )
}

fun PostDetailDto.toRemoteSummary(): RemotePostSummary {
    return RemotePostSummary(
        postId = postId,
        title = title,
        summary = summary,
        contributorLabel = contributorLabel,
        displayTimeMillis = displayTimeMillis,
        albumIds = albumIds,
        coverMediaId = coverMediaId,
        mediaCount = mediaCount,
    )
}

fun CommentDto.toRemoteModel(): RemoteComment {
    return RemoteComment(
        commentId = commentId,
        targetType = targetType,
        targetId = postId ?: mediaId.orEmpty(),
        authorId = authorId,
        authorName = authorName,
        content = content.orEmpty(),
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        isDeleted = isDeleted,
    )
}

fun CommentListResponseDto.toRemotePage(): RemoteCommentPage {
    return RemoteCommentPage(
        comments = comments.map(CommentDto::toRemoteModel),
        page = page,
        size = size,
        hasMore = hasMore,
    )
}

fun TrashItemDto.toRemoteModel(): RemoteTrashItem {
    return RemoteTrashItem(
        trashItemId = trashItemId,
        itemType = itemType,
        state = state,
        sourcePostId = sourcePostId,
        sourceMediaId = sourceMediaId,
        title = title,
        previewInfo = previewInfo,
        deletedAtMillis = deletedAtMillis,
        relatedPostIds = relatedPostIds,
        relatedMediaIds = relatedMediaIds,
    )
}

fun PendingCleanupDto.toRemoteModel(): RemotePendingCleanup {
    return RemotePendingCleanup(
        trashItemId = trashItemId,
        removedAtMillis = removedAtMillis,
        undoDeadlineMillis = undoDeadlineMillis,
        item = item.toRemoteModel(),
    )
}

fun TrashDetailDto.toRemoteDetail(): RemoteTrashDetail {
    return RemoteTrashDetail(
        item = item.toRemoteModel(),
        canRestore = canRestore,
        canMoveOutOfTrash = canMoveOutOfTrash,
        pendingCleanup = pendingCleanup?.toRemoteModel(),
    )
}

fun UploadTokenDto.toRemoteModel(): RemoteUploadToken {
    return RemoteUploadToken(
        uploadId = uploadId,
        provider = provider,
        uploadUrl = uploadUrl,
        expireAtMillis = expireAtMillis,
        state = state,
    )
}

fun UploadCompleteResponseDto.toRemoteModel(): RemoteUploadTask {
    return RemoteUploadTask(
        uploadId = uploadId,
        fileName = media.mediaId,
        mediaType = media.mediaType?.ifBlank { media.type.orEmpty() }.orEmpty().ifBlank { "image" },
        objectKey = media.url,
        state = state.toUploadState(),
        progressPercent = if (state.equals("success", ignoreCase = true)) 100 else 0,
        errorMessage = null,
    )
}

fun UploadTaskDto.toRemoteModel(): RemoteUploadTask {
    return RemoteUploadTask(
        uploadId = uploadId,
        fileName = fileName,
        mediaType = mediaType,
        objectKey = objectKey,
        state = state.toUploadState(),
        progressPercent = progressPercent,
        errorMessage = errorMessage,
    )
}

private fun String.toUploadState(): UploadState {
    return when (lowercase()) {
        "waiting" -> UploadState.WAITING
        "uploading" -> UploadState.UPLOADING
        "success" -> UploadState.SUCCESS
        "failure" -> UploadState.FAILURE
        "cancelled" -> UploadState.CANCELLED
        else -> UploadState.FAILURE
    }
}
