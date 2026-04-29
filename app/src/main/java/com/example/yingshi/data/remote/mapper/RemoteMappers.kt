package com.example.yingshi.data.remote.mapper

import com.example.yingshi.data.model.RemoteAlbum
import com.example.yingshi.data.model.RemoteComment
import com.example.yingshi.data.model.RemoteCommentPage
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.model.RemotePostDetail
import com.example.yingshi.data.model.RemotePostMedia
import com.example.yingshi.data.model.RemotePostSummary
import com.example.yingshi.data.model.RemoteTrashItem
import com.example.yingshi.data.model.RemoteUploadToken
import com.example.yingshi.data.model.RemoteUploadTask
import com.example.yingshi.data.model.UploadState
import com.example.yingshi.data.remote.dto.AlbumDto
import com.example.yingshi.data.remote.dto.CommentDto
import com.example.yingshi.data.remote.dto.CommentListResponseDto
import com.example.yingshi.data.remote.dto.MediaDto
import com.example.yingshi.data.remote.dto.PostDetailDto
import com.example.yingshi.data.remote.dto.PostDto
import com.example.yingshi.data.remote.dto.PostMediaDto
import com.example.yingshi.data.remote.dto.PostSummaryDto
import com.example.yingshi.data.remote.dto.TrashItemDto
import com.example.yingshi.data.remote.dto.UploadTaskDto
import com.example.yingshi.data.remote.dto.UploadTokenDto

fun MediaDto.toRemoteModel(): RemoteMedia {
    return RemoteMedia(
        mediaId = mediaId,
        mediaType = mediaType,
        previewUrl = previewUrl,
        originalUrl = originalUrl,
        videoUrl = videoUrl,
        width = width,
        height = height,
        aspectRatio = aspectRatio,
        displayTimeMillis = displayTimeMillis,
        commentCount = commentCount,
        postIds = postIds,
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

fun PostDto.toRemoteSummary(): RemotePostSummary {
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
    return RemotePostMedia(
        mediaId = mediaId,
        mediaType = mediaType,
        previewUrl = previewUrl,
        originalUrl = originalUrl,
        videoUrl = videoUrl,
        width = width,
        height = height,
        aspectRatio = aspectRatio,
        displayTimeMillis = displayTimeMillis,
        commentCount = commentCount,
        isCover = isCover,
        videoDurationMillis = videoDurationMillis,
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

fun CommentDto.toRemoteModel(): RemoteComment {
    return RemoteComment(
        commentId = commentId,
        targetType = targetType,
        targetId = targetId,
        authorName = authorName,
        content = content,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        isDeleted = isDeleted,
    )
}

fun CommentListResponseDto.toRemotePage(
    page: Int = 1,
    size: Int = comments.size,
    hasMore: Boolean = false,
): RemoteCommentPage {
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
        sourcePostId = sourcePostId,
        sourceMediaId = sourceMediaId,
        title = title,
        previewInfo = previewInfo,
        deletedAtMillis = deletedAtMillis,
        relatedPostIds = relatedPostIds,
        relatedMediaIds = relatedMediaIds,
    )
}

fun UploadTokenDto.toRemoteModel(): RemoteUploadToken {
    return RemoteUploadToken(
        uploadId = uploadId,
        provider = provider,
        bucket = bucket,
        objectKey = objectKey,
        uploadUrl = uploadUrl,
        expireAtMillis = expireAtMillis,
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
