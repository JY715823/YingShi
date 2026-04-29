package com.example.yingshi.data.remote.dto

data class CommentDto(
    val commentId: String,
    val targetType: String,
    val targetId: String,
    val authorName: String,
    val content: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long? = null,
    val isDeleted: Boolean = false,
)

data class CommentListResponseDto(
    val comments: List<CommentDto>,
)

data class CreateCommentRequestDto(
    val content: String,
)

data class UpdateCommentRequestDto(
    val content: String,
)
