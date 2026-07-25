package com.example.yingshi.feature.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

data class ViewerCommentBindings(
    val comments: List<CommentUiModel>,
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val onRetry: (() -> Unit)? = null,
    val onCreateComment: (String) -> Unit,
    val onUpdateComment: (String, String) -> Unit,
    val onDeleteComment: (String) -> Unit,
)

@Composable
fun rememberViewerCommentBindings(
    mediaId: String,
): ViewerCommentBindings {
    val normalizedMediaId = mediaId.trim()
    val sessionKey = realBackendSessionKey("real-viewer-comments")
    val viewModel: RealViewerCommentViewModel = viewModel(
        key = sessionKey,
        factory = RealViewerCommentViewModel.factory(),
    )
    val uiState by viewModel.uiState.collectAsState()
    val threadState = uiState.commentThreads[normalizedMediaId] ?: RealCommentThreadUiState(isLoading = true)
    LaunchedEffect(normalizedMediaId) {
        if (normalizedMediaId.isNotBlank()) {
            viewModel.ensureMediaComments(normalizedMediaId)
        }
    }
    return ViewerCommentBindings(
        comments = threadState.comments,
        isLoading = threadState.isLoading,
        isMutating = threadState.isMutating,
        errorMessage = threadState.errorMessage,
        statusMessage = threadState.statusMessage,
        onRetry = { viewModel.retryMediaComments(normalizedMediaId) },
        onCreateComment = { content -> viewModel.createMediaComment(normalizedMediaId, content) },
        onUpdateComment = { commentId, content -> viewModel.updateMediaComment(normalizedMediaId, commentId, content) },
        onDeleteComment = { commentId -> viewModel.deleteMediaComment(normalizedMediaId, commentId) },
    )
}
