package com.example.yingshi.data.remote.dto

data class CommentDto(
    val commentId: String,
    val targetType: String,
    val postId: String? = null,
    val mediaId: String? = null,
    val authorId: String? = null,
    val authorName: String,
    val content: String? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long? = null,
    val isDeleted: Boolean = false,
)

data class CommentListResponseDto(
    val comments: List<CommentDto>,
    val page: Int = 1,
    val size: Int = 10,
    val totalElements: Long = 0,
    val hasMore: Boolean = false,
)

data class CreateCommentRequestDto(
    val content: String,
)

data class UpdateCommentRequestDto(
    val content: String,
)
