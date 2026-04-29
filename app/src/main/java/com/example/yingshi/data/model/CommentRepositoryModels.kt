package com.example.yingshi.data.model

import com.example.yingshi.data.remote.result.ApiResult

data class RemoteCommentPage(
    val comments: List<RemoteComment>,
    val page: Int = 1,
    val size: Int = 20,
    val hasMore: Boolean = false,
)

data class CommentListState(
    val comments: List<RemoteComment> = emptyList(),
    val isLoading: Boolean = false,
    val errorCode: String? = null,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean
        get() = !isLoading && errorCode == null && comments.isEmpty()
}

fun ApiResult<RemoteCommentPage>.toCommentListState(): CommentListState {
    return when (this) {
        is ApiResult.Loading -> CommentListState(isLoading = true)
        is ApiResult.Success -> CommentListState(comments = data.comments)
        is ApiResult.Error -> CommentListState(
            errorCode = code,
            errorMessage = message,
        )
    }
}
