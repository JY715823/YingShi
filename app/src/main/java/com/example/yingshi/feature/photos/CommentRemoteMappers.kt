package com.example.yingshi.feature.photos

import com.example.yingshi.data.model.RemoteComment

fun RemoteComment.toCommentUiModel(
    currentUserId: String? = null,
): CommentUiModel {
    return CommentUiModel(
        id = commentId,
        targetType = when (targetType.lowercase()) {
            "media" -> CommentTargetType.Media
            else -> CommentTargetType.Post
        },
        targetId = targetId,
        author = authorName,
        content = content,
        createdAtMillis = createdAtMillis,
        isMine = !currentUserId.isNullOrBlank() && currentUserId == authorId,
    )
}
