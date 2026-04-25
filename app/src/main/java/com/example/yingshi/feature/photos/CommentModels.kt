package com.example.yingshi.feature.photos

import androidx.compose.runtime.Immutable

enum class CommentTargetType {
    Post,
    Media,
}

@Immutable
data class CommentUiModel(
    val id: String,
    val targetType: CommentTargetType,
    val targetId: String,
    val author: String,
    val content: String,
    val createdAtMillis: Long,
    val isMine: Boolean = false,
    val isExpanded: Boolean = false,
)

