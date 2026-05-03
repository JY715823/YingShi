package com.example.yingshi.feature.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yingshi.data.model.CommentListState
import com.example.yingshi.data.model.toCommentListState
import com.example.yingshi.data.repository.AuthRepository
import com.example.yingshi.data.repository.CommentRepository
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.result.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RealViewerCommentUiState(
    val currentUserId: String? = null,
    val commentThreads: Map<String, RealCommentThreadUiState> = emptyMap(),
)

class RealViewerCommentViewModel(
    private val commentRepository: CommentRepository = RepositoryProvider.commentRepository,
    private val authRepository: AuthRepository = RepositoryProvider.authRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RealViewerCommentUiState())
    val uiState: StateFlow<RealViewerCommentUiState> = _uiState.asStateFlow()

    init {
        loadCurrentUser()
    }

    fun ensureMediaComments(mediaId: String) {
        val existing = _uiState.value.commentThreads[mediaId]
        if (existing != null && (existing.isLoading || existing.comments.isNotEmpty())) return
        loadMediaComments(mediaId)
    }

    fun retryMediaComments(mediaId: String) {
        loadMediaComments(mediaId)
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
        mutateMediaComments(mediaId, "评论已删除。") {
            commentRepository.deleteComment(commentId)
        }
    }

    private fun loadCurrentUser() {
        if (!AuthSessionManager.isLoggedIn) return
        viewModelScope.launch {
            val currentUser = when (val result = authRepository.getCurrentUser()) {
                is ApiResult.Success -> result.data
                else -> null
            }
            _uiState.update { it.copy(currentUserId = currentUser?.userId) }
        }
    }

    private fun loadMediaComments(mediaId: String, successMessage: String? = null) {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    commentThreads = state.commentThreads + (
                        mediaId to state.commentThreads[mediaId].orEmpty().copy(
                            isLoading = true,
                            isMutating = false,
                            errorMessage = null,
                            statusMessage = successMessage,
                        )
                    ),
                )
            }
            val commentState = commentRepository.getMediaComments(mediaId).toCommentListState()
            _uiState.update { state ->
                state.copy(
                    commentThreads = state.commentThreads + (
                        mediaId to commentState.toViewerThreadUiState(
                            currentUserId = state.currentUserId,
                            successMessage = successMessage,
                        )
                    ),
                )
            }
        }
    }

    private fun mutateMediaComments(
        mediaId: String,
        successMessage: String,
        block: suspend () -> ApiResult<*>,
    ) {
        if (!AuthSessionManager.isLoggedIn) {
            _uiState.update { state ->
                state.copy(
                    commentThreads = state.commentThreads + (
                        mediaId to state.commentThreads[mediaId].orEmpty().copy(
                            errorMessage = "登录状态缺失，请先到联调诊断页重新登录。",
                        )
                    ),
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    commentThreads = state.commentThreads + (
                        mediaId to state.commentThreads[mediaId].orEmpty().copy(
                            isMutating = true,
                            errorMessage = null,
                        )
                    ),
                )
            }
            when (val result = block()) {
                is ApiResult.Success -> {
                    notifyRealBackendCommentChanged(
                        mediaIds = setOf(mediaId),
                    )
                    loadMediaComments(mediaId, successMessage)
                }
                is ApiResult.Error -> {
                    _uiState.update { state ->
                        state.copy(
                            commentThreads = state.commentThreads + (
                                mediaId to state.commentThreads[mediaId].orEmpty().copy(
                                    isMutating = false,
                                    errorMessage = result.toBackendUiMessage("媒体评论操作失败。"),
                                )
                            ),
                        )
                    }
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RealViewerCommentViewModel() as T
                }
            }
        }
    }
}

private fun CommentListState.toViewerThreadUiState(
    currentUserId: String?,
    successMessage: String?,
): RealCommentThreadUiState {
    val mappedComments = comments
        .map { it.toCommentUiModel(currentUserId) }
        .sortedByDescending { it.createdAtMillis }
    return RealCommentThreadUiState(
        comments = mappedComments,
        isLoading = isLoading,
        isMutating = false,
        errorMessage = errorMessage,
        statusMessage = successMessage,
    )
}

private fun RealCommentThreadUiState?.orEmpty(): RealCommentThreadUiState {
    return this ?: RealCommentThreadUiState()
}
