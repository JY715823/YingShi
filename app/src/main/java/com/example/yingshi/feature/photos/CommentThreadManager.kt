package com.example.yingshi.feature.photos

import com.example.yingshi.data.model.CommentListState
import com.example.yingshi.data.model.toCommentListState
import com.example.yingshi.data.repository.CommentRepository
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.result.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class CommentThreadManager(
    private val scope: CoroutineScope,
    private val commentRepository: CommentRepository,
    private val currentUserIdProvider: () -> String?,
    private val commentThreadsProvider: () -> Map<String, RealCommentThreadUiState>,
    private val commentThreadsUpdater: (Map<String, RealCommentThreadUiState>) -> Unit,
    private val onMutationSuccess: ((mediaId: String) -> Unit)? = null,
) {
    companion object {
        private const val CommentNoticeVisibleMillis = 1800L
    }

    fun ensureMediaComments(mediaId: String) {
        val existing = commentThreadsProvider()[mediaId]
        if (existing == null || (existing.isLoading && existing.comments.isEmpty())) {
            loadMediaComments(mediaId)
        }
    }

    fun loadMediaComments(mediaId: String, successMessage: String? = null) {
        scope.launch {
            commentThreadsUpdater(
                commentThreadsProvider() + (
                    mediaId to commentThreadsProvider()[mediaId].orEmpty().copy(
                        isLoading = true,
                        isMutating = false,
                        errorMessage = null,
                        statusMessage = successMessage,
                    )
                ),
            )
            val commentState = commentRepository.getMediaComments(mediaId).toCommentListState()
            commentThreadsUpdater(
                commentThreadsProvider() + (
                    mediaId to commentState.toThreadUiState(
                        currentUserId = currentUserIdProvider(),
                        successMessage = successMessage,
                    )
                ),
            )
            clearMediaCommentStatusLater(mediaId, successMessage)
        }
    }

    fun loadMoreMediaComments(mediaId: String) {
        val current = commentThreadsProvider()[mediaId] ?: return
        if (!current.hasMore || current.isLoading) return
        val nextPage = current.currentPage + 1
        scope.launch {
            commentThreadsUpdater(
                commentThreadsProvider() + (
                    mediaId to current.copy(isLoading = true, errorMessage = null)
                ),
            )
            when (val result = commentRepository.getMediaComments(mediaId, page = nextPage)) {
                is ApiResult.Success -> {
                    val newComments = result.data.comments
                        .filterNot { it.isDeleted }
                        .map { it.toCommentUiModel(currentUserIdProvider()) }
                    val existingIds = current.comments.map { it.id }.toSet()
                    val deduplicated = newComments.filterNot { it.id in existingIds }
                    commentThreadsUpdater(
                        commentThreadsProvider() + (
                            mediaId to current.copy(
                                comments = current.comments + deduplicated,
                                isLoading = false,
                                hasMore = result.data.hasMore,
                                currentPage = result.data.page,
                            )
                        ),
                    )
                }
                is ApiResult.Error -> {
                    commentThreadsUpdater(
                        commentThreadsProvider() + (
                            mediaId to current.copy(
                                isLoading = false,
                                errorMessage = result.toBackendUiMessage("加载更多评论失败。"),
                            )
                        ),
                    )
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun createMediaComment(mediaId: String, content: String) {
        val normalized = content.trim()
        if (normalized.isEmpty()) return
        mutateMediaComments(mediaId, "评论已发送。") {
            commentRepository.createMediaComment(mediaId, normalized)
        }
    }

    fun updateMediaComment(mediaId: String, commentId: String, content: String) {
        val normalized = content.trim()
        if (normalized.isEmpty()) return
        mutateMediaComments(mediaId, "评论已更新。") {
            commentRepository.updateComment(commentId, normalized)
        }
    }

    fun deleteMediaComment(mediaId: String, commentId: String) {
        mutateMediaComments(mediaId, "评论已删除。", deletedCommentId = commentId) {
            commentRepository.deleteComment(commentId)
        }
    }

    fun retryMediaComments(mediaId: String) {
        loadMediaComments(mediaId)
    }

    private fun mutateMediaComments(
        mediaId: String,
        successMessage: String,
        deletedCommentId: String? = null,
        block: suspend () -> ApiResult<*>,
    ) {
        if (!AuthSessionManager.isLoggedIn) {
            commentThreadsUpdater(
                commentThreadsProvider() + (
                    mediaId to commentThreadsProvider()[mediaId].orEmpty().copy(
                        errorMessage = "登录状态缺失，请重新登录。",
                    )
                ),
            )
            return
        }
        scope.launch {
            commentThreadsUpdater(
                commentThreadsProvider() + (
                    mediaId to commentThreadsProvider()[mediaId].orEmpty().copy(
                        isMutating = true,
                        errorMessage = null,
                    )
                ),
            )
            when (val result = block()) {
                is ApiResult.Success -> {
                    onMutationSuccess?.invoke(mediaId)
                    if (deletedCommentId != null) {
                        commentThreadsUpdater(
                            commentThreadsProvider() + (
                                mediaId to commentThreadsProvider()[mediaId].orEmpty().copy(
                                    comments = commentThreadsProvider()[mediaId].orEmpty().comments
                                        .filterNot { comment -> comment.id == deletedCommentId },
                                    isMutating = false,
                                    statusMessage = successMessage,
                                )
                            ),
                        )
                    }
                    loadMediaComments(mediaId, successMessage)
                }
                is ApiResult.Error -> {
                    commentThreadsUpdater(
                        commentThreadsProvider() + (
                            mediaId to commentThreadsProvider()[mediaId].orEmpty().copy(
                                isMutating = false,
                                errorMessage = result.toBackendUiMessage("媒体评论操作失败。"),
                            )
                        ),
                    )
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    private fun clearMediaCommentStatusLater(mediaId: String, expectedMessage: String?) {
        if (expectedMessage == null) return
        scope.launch {
            delay(CommentNoticeVisibleMillis)
            val current = commentThreadsProvider()[mediaId] ?: return@launch
            if (current.statusMessage == expectedMessage) {
                commentThreadsUpdater(
                    commentThreadsProvider() + (mediaId to current.copy(statusMessage = null))
                )
            }
        }
    }
}

private fun CommentListState.toThreadUiState(
    currentUserId: String?,
    successMessage: String?,
): RealCommentThreadUiState {
    return if (errorMessage != null) {
        RealCommentThreadUiState(
            comments = comments.filterNot { it.isDeleted }.map { it.toCommentUiModel(currentUserId) }
                .sortedByDescending { it.createdAtMillis },
            isLoading = false,
            isMutating = false,
            errorMessage = errorMessage,
            statusMessage = successMessage,
            hasMore = hasMore,
            currentPage = currentPage,
        )
    } else {
        RealCommentThreadUiState(
            comments = comments.filterNot { it.isDeleted }.map { it.toCommentUiModel(currentUserId) }
                .sortedByDescending { it.createdAtMillis },
            isLoading = isLoading,
            isMutating = false,
            errorMessage = null,
            statusMessage = successMessage,
            hasMore = hasMore,
            currentPage = currentPage,
        )
    }
}

private fun RealCommentThreadUiState?.orEmpty(): RealCommentThreadUiState {
    return this ?: RealCommentThreadUiState()
}