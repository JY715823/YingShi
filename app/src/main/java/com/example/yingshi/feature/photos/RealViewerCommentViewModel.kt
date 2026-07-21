package com.example.yingshi.feature.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

    private val commentThreadManager = CommentThreadManager(
        scope = viewModelScope,
        commentRepository = RepositoryProvider.commentRepository,
        currentUserIdProvider = { _uiState.value.currentUserId },
        commentThreadsProvider = { _uiState.value.commentThreads },
        commentThreadsUpdater = { newThreads ->
            _uiState.update { it.copy(commentThreads = newThreads) }
        },
        onMutationSuccess = { mediaId ->
            notifyRealBackendCommentChanged(
                postIds = getRelatedPostIds(),
                mediaIds = setOf(mediaId),
            )
        },
    )

    init {
        loadCurrentUser()
    }

    fun ensureMediaComments(mediaId: String) = commentThreadManager.ensureMediaComments(mediaId)

    fun loadMediaComments(mediaId: String) = commentThreadManager.loadMediaComments(mediaId)

    fun createMediaComment(mediaId: String, content: String) = commentThreadManager.createMediaComment(mediaId, content)

    fun updateMediaComment(mediaId: String, commentId: String, content: String) = commentThreadManager.updateMediaComment(mediaId, commentId, content)

    fun deleteMediaComment(mediaId: String, commentId: String) = commentThreadManager.deleteMediaComment(mediaId, commentId)

    fun retryMediaComments(mediaId: String) = commentThreadManager.retryMediaComments(mediaId)

    fun loadMoreMediaComments(mediaId: String) = commentThreadManager.loadMoreMediaComments(mediaId)

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

    private fun getRelatedPostIds(): Set<String> = emptySet()

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