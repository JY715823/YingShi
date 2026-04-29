package com.example.yingshi.feature.photos

import com.example.yingshi.data.model.CommentListState
import com.example.yingshi.data.model.RemoteComment
import com.example.yingshi.data.model.toCommentListState
import com.example.yingshi.data.repository.CommentRepository
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.data.remote.result.ApiResult

object CommentGateway {
    val repository: CommentRepository
        get() = RepositoryProvider.commentRepository

    fun getPostComments(postId: String): List<CommentUiModel> {
        return FakeCommentRepository.getPostComments(postId)
    }

    fun getMediaComments(mediaId: String): List<CommentUiModel> {
        return FakeCommentRepository.getMediaComments(mediaId)
    }

    fun addPostComment(postId: String, content: String) {
        FakeCommentRepository.addPostComment(postId, content)
    }

    fun addMediaComment(mediaId: String, content: String) {
        FakeCommentRepository.addMediaComment(mediaId, content)
    }

    fun updatePostComment(postId: String, commentId: String, content: String) {
        FakeCommentRepository.updatePostComment(postId, commentId, content)
    }

    fun updateMediaComment(mediaId: String, commentId: String, content: String) {
        FakeCommentRepository.updateMediaComment(mediaId, commentId, content)
    }

    fun deletePostComment(postId: String, commentId: String) {
        FakeCommentRepository.deletePostComment(postId, commentId)
    }

    fun deleteMediaComment(mediaId: String, commentId: String) {
        FakeCommentRepository.deleteMediaComment(mediaId, commentId)
    }

    fun mediaCommentCount(mediaId: String): Int = FakeCommentRepository.mediaCommentCount(mediaId)

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
