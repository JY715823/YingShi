package com.example.yingshi.data.remote.mapper

import com.example.yingshi.data.model.RemoteAlbum
import com.example.yingshi.data.model.RemoteComment
import com.example.yingshi.data.model.RemoteCommentPage
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.model.RemotePost
import com.example.yingshi.data.model.RemoteTrashItem
import com.example.yingshi.data.model.RemoteUploadToken
import com.example.yingshi.data.remote.dto.AlbumDto
import com.example.yingshi.data.remote.dto.CommentDto
import com.example.yingshi.data.remote.dto.CommentListResponseDto
import com.example.yingshi.data.remote.dto.MediaDto
import com.example.yingshi.data.remote.dto.PostDto
import com.example.yingshi.data.remote.dto.TrashItemDto
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

fun PostDto.toRemoteModel(): RemotePost {
    return RemotePost(
        postId = postId,
        title = title,
        summary = summary,
        contributorLabel = contributorLabel,
        displayTimeMillis = displayTimeMillis,
        albumIds = albumIds,
        coverMediaId = coverMediaId,
        mediaItems = mediaItems.map(MediaDto::toRemoteModel),
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
