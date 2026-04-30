package com.example.yingshi.feature.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider

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
    if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
        val viewModel: RealViewerCommentViewModel = viewModel(
            key = "real-viewer-comments",
            factory = RealViewerCommentViewModel.factory(),
        )
        val uiState by viewModel.uiState.collectAsState()
        val threadState = uiState.commentThreads[mediaId] ?: RealCommentThreadUiState(isLoading = true)
        LaunchedEffect(mediaId) {
            viewModel.ensureMediaComments(mediaId)
        }
        return ViewerCommentBindings(
            comments = threadState.comments,
            isLoading = threadState.isLoading,
            isMutating = threadState.isMutating,
            errorMessage = threadState.errorMessage,
            statusMessage = threadState.statusMessage,
            onRetry = { viewModel.retryMediaComments(mediaId) },
            onCreateComment = { content -> viewModel.createMediaComment(mediaId, content) },
            onUpdateComment = { commentId, content -> viewModel.updateMediaComment(mediaId, commentId, content) },
            onDeleteComment = { commentId -> viewModel.deleteMediaComment(mediaId, commentId) },
        )
    }

    val comments = CommentGateway.getMediaComments(mediaId)
    return ViewerCommentBindings(
        comments = comments,
        onCreateComment = { content -> CommentGateway.addMediaComment(mediaId, content) },
        onUpdateComment = { commentId, content ->
            CommentGateway.updateMediaComment(mediaId = mediaId, commentId = commentId, content = content)
        },
        onDeleteComment = { commentId -> CommentGateway.deleteMediaComment(mediaId, commentId) },
    )
}
