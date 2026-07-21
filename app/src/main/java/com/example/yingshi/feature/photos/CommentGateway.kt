package com.example.yingshi.feature.photos

import com.example.yingshi.data.model.CommentListState
import com.example.yingshi.data.model.RemoteComment
import com.example.yingshi.data.model.toCommentListState
import com.example.yingshi.data.repository.CommentRepository
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.data.remote.result.ApiResult

// Round 2 FR-3: FAKE-mode methods removed — FAKE screens now call FakeCommentRepository directly.
object CommentGateway {
    val repository: CommentRepository
        get() = RepositoryProvider.commentRepository

    suspend fun loadPostComments(
        postId: String,
        page: Int = 1,
        size: Int = 20,
    ): CommentListState {
        return repository.getPostComments(postId, page, size).toCommentListState()
    }

    suspend fun loadMediaComments(
        mediaId: String,
        page: Int = 1,
        size: Int = 20,
    ): CommentListState {
        return repository.getMediaComments(mediaId, page, size).toCommentListState()
    }

    suspend fun createPostCommentRemote(
        postId: String,
        content: String,
    ): ApiResult<RemoteComment> {
        return repository.createPostComment(postId, content)
    }

    suspend fun createMediaCommentRemote(
        mediaId: String,
        content: String,
    ): ApiResult<RemoteComment> {
        return repository.createMediaComment(mediaId, content)
    }

    suspend fun updateCommentRemote(
        commentId: String,
        content: String,
    ): ApiResult<RemoteComment> {
        return repository.updateComment(commentId, content)
    }

    suspend fun deleteCommentRemote(commentId: String): ApiResult<Unit> {
        return repository.deleteComment(commentId)
    }
}