package com.example.yingshi.feature.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yingshi.data.model.CommentListState
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.model.toCommentListState
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.AlbumRepository
import com.example.yingshi.data.repository.AuthRepository
import com.example.yingshi.data.repository.CommentRepository
import com.example.yingshi.data.repository.PostRepository
import com.example.yingshi.data.repository.RepositoryProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

data class AlbumPageRealUiState(
    val isLoading: Boolean = false,
    val isPostsLoading: Boolean = false,
    val tokenMissing: Boolean = false,
    val errorMessage: String? = null,
    val postsErrorMessage: String? = null,
    val albums: List<AlbumSummaryUiModel> = emptyList(),
    val selectedAlbumId: String? = null,
    val posts: List<AlbumPostCardUiModel> = emptyList(),
)

data class RealCommentThreadUiState(
    val comments: List<CommentUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
) {
    val isEmpty: Boolean
        get() = !isLoading && comments.isEmpty() && errorMessage == null
}

data class PostDetailRealUiState(
    val isLoading: Boolean = false,
    val tokenMissing: Boolean = false,
    val errorMessage: String? = null,
    val detail: PostDetailUiModel? = null,
    val currentUserId: String? = null,
    val postComments: RealCommentThreadUiState = RealCommentThreadUiState(),
    val mediaComments: Map<String, RealCommentThreadUiState> = emptyMap(),
)

class AlbumPageRealViewModel(
    private val albumRepository: AlbumRepository = RepositoryProvider.albumRepository,
    private val postRepository: PostRepository = RepositoryProvider.postRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AlbumPageRealUiState(isLoading = true))
    val uiState: StateFlow<AlbumPageRealUiState> = _uiState.asStateFlow()

    private var loadPostsJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (!AuthSessionManager.isLoggedIn) {
            _uiState.value = AlbumPageRealUiState(
                tokenMissing = true,
                errorMessage = "请先到后端联调诊断页登录，再打开 REAL 相册页。",
            )
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    tokenMissing = false,
                    errorMessage = null,
                    postsErrorMessage = null,
                )
            }
            when (val result = albumRepository.getAlbums()) {
                is ApiResult.Success -> {
                    val albums = result.data.map { album -> album.toAlbumSummaryUiModel() }
                    val selectedAlbumId = _uiState.value.selectedAlbumId
                        ?.takeIf { currentId -> albums.any { it.id == currentId } }
                        ?: albums.firstOrNull()?.id
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        albums = albums,
                        selectedAlbumId = selectedAlbumId,
                        posts = emptyList(),
                    )
                    if (selectedAlbumId != null) {
                        loadAlbumPosts(selectedAlbumId)
                    }
                }
                is ApiResult.Error -> {
                    _uiState.value = AlbumPageRealUiState(
                        isLoading = false,
                        errorMessage = result.toUiMessage("读取后端相册失败。"),
                    )
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun selectAlbum(albumId: String) {
        if (_uiState.value.selectedAlbumId == albumId) {
            loadAlbumPosts(albumId)
            return
        }
        _uiState.update {
            it.copy(
                selectedAlbumId = albumId,
                posts = emptyList(),
                postsErrorMessage = null,
            )
        }
        loadAlbumPosts(albumId)
    }

    private fun loadAlbumPosts(albumId: String) {
        loadPostsJob?.cancel()
        loadPostsJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isPostsLoading = true,
                    postsErrorMessage = null,
                )
            }
            when (val result = albumRepository.getAlbumPosts(albumId)) {
                is ApiResult.Success -> {
                    val posts = supervisorScope {
                        val coverRequests = result.data.associate { summary ->
                            summary.postId to async {
                                when (val detailResult = postRepository.getPostDetail(summary.postId)) {
                                    is ApiResult.Success -> {
                                        val detail = detailResult.data
                                        detail.mediaItems.firstOrNull { it.mediaId == detail.coverMediaId }
                                            ?: detail.mediaItems.firstOrNull()
                                    }
                                    else -> null
                                }
                            }
                        }
                        result.data.map { post ->
                            post.toAlbumPostCardUiModel(
                                selectedAlbumId = albumId,
                                coverMedia = coverRequests[post.postId]?.await(),
                            )
                        }
                    }
                    _uiState.update {
                        it.copy(
                            isPostsLoading = false,
                            posts = posts,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isPostsLoading = false,
                            postsErrorMessage = result.toUiMessage("读取相册下帖子失败。"),
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
                    return AlbumPageRealViewModel() as T
                }
            }
        }
    }
}

class PostDetailRealViewModel(
    private val route: PostDetailPlaceholderRoute,
    private val postRepository: PostRepository = RepositoryProvider.postRepository,
    private val albumRepository: AlbumRepository = RepositoryProvider.albumRepository,
    private val commentRepository: CommentRepository = RepositoryProvider.commentRepository,
    private val authRepository: AuthRepository = RepositoryProvider.authRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PostDetailRealUiState(isLoading = true))
    val uiState: StateFlow<PostDetailRealUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (!AuthSessionManager.isLoggedIn) {
            _uiState.value = PostDetailRealUiState(
                tokenMissing = true,
                errorMessage = "请先到后端联调诊断页登录，再打开 REAL 帖子详情。",
            )
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    tokenMissing = false,
                    errorMessage = null,
                )
            }
            val currentUserDeferred = async { loadCurrentUser() }
            val albumMapDeferred = async { loadAlbumTitleMap() }

            when (val result = postRepository.getPostDetail(route.postId)) {
                is ApiResult.Success -> {
                    val currentUser = currentUserDeferred.await()
                    val albumTitleById = albumMapDeferred.await()
                    val detail = result.data.toPostDetailUiModel(albumTitleById)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        detail = detail,
                        currentUserId = currentUser?.userId,
                        postComments = _uiState.value.postComments.copy(
                            comments = emptyList(),
                            errorMessage = null,
                            statusMessage = null,
                        ),
                        mediaComments = emptyMap(),
                    )
                    loadPostComments()
                }
                is ApiResult.Error -> {
                    _uiState.value = PostDetailRealUiState(
                        isLoading = false,
                        errorMessage = result.toUiMessage("读取后端帖子详情失败。"),
                    )
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun retryPostComments() {
        loadPostComments()
    }

    fun ensureMediaComments(mediaId: String) {
        val current = _uiState.value.mediaComments[mediaId]
        if (current != null && (current.isLoading || current.comments.isNotEmpty())) return
        loadMediaComments(mediaId)
    }

    fun retryMediaComments(mediaId: String) {
        loadMediaComments(mediaId)
    }

    fun createPostComment(content: String) {
        val normalized = content.trim()
        if (normalized.isEmpty()) return
        mutatePostComments("评论已发送。") {
            commentRepository.createPostComment(route.postId, normalized)
        }
    }

    fun createMediaComment(mediaId: String, content: String) {
        val normalized = content.trim()
        if (normalized.isEmpty()) return
        mutateMediaComments(mediaId, "评论已发送。") {
            commentRepository.createMediaComment(mediaId, normalized)
        }
    }

    fun updatePostComment(commentId: String, content: String) {
        val normalized = content.trim()
        if (normalized.isEmpty()) return
        mutatePostComments("评论已更新。") {
            commentRepository.updateComment(commentId, normalized)
        }
    }

    fun updateMediaComment(mediaId: String, commentId: String, content: String) {
        val normalized = content.trim()
        if (normalized.isEmpty()) return
        mutateMediaComments(mediaId, "评论已更新。") {
            commentRepository.updateComment(commentId, normalized)
        }
    }

    fun deletePostComment(commentId: String) {
        mutatePostComments("评论已删除。") {
            commentRepository.deleteComment(commentId)
        }
    }

    fun deleteMediaComment(mediaId: String, commentId: String) {
        mutateMediaComments(mediaId, "评论已删除。") {
            commentRepository.deleteComment(commentId)
        }
    }

    private fun loadPostComments(successMessage: String? = null) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    postComments = it.postComments.copy(
                        isLoading = true,
                        isMutating = false,
                        errorMessage = null,
                        statusMessage = successMessage,
                    ),
                )
            }
            val commentState = commentRepository.getPostComments(route.postId).toCommentListState()
            _uiState.update { state ->
                state.copy(
                    postComments = commentState.toThreadUiState(
                        currentUserId = state.currentUserId,
                        successMessage = successMessage,
                    ),
                )
            }
        }
    }

    private fun loadMediaComments(
        mediaId: String,
        successMessage: String? = null,
    ) {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    mediaComments = state.mediaComments + (
                        mediaId to state.mediaComments[mediaId].orEmpty().copy(
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
                    mediaComments = state.mediaComments + (
                        mediaId to commentState.toThreadUiState(
                            currentUserId = state.currentUserId,
                            successMessage = successMessage,
                        )
                    ),
                )
            }
        }
    }

    private fun mutatePostComments(
        successMessage: String,
        block: suspend () -> ApiResult<*>,
    ) {
        if (!AuthSessionManager.isLoggedIn) {
            _uiState.update {
                it.copy(
                    postComments = it.postComments.copy(
                        errorMessage = "登录状态缺失，请先到联调诊断页重新登录。",
                    ),
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(postComments = it.postComments.copy(isMutating = true, errorMessage = null))
            }
            when (val result = block()) {
                is ApiResult.Success -> {
                    loadPostComments(successMessage)
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            postComments = it.postComments.copy(
                                isMutating = false,
                                errorMessage = result.toUiMessage("评论操作失败。"),
                            ),
                        )
                    }
                }
                ApiResult.Loading -> Unit
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
                    mediaComments = state.mediaComments + (
                        mediaId to state.mediaComments[mediaId].orEmpty().copy(
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
                    mediaComments = state.mediaComments + (
                        mediaId to state.mediaComments[mediaId].orEmpty().copy(
                            isMutating = true,
                            errorMessage = null,
                        )
                    ),
                )
            }
            when (val result = block()) {
                is ApiResult.Success -> {
                    RealBackendMutationBus.notifyChanged(
                        RealBackendMutationEvent(
                            scopes = setOf(
                                RealBackendRefreshScope.PHOTO_FEED,
                                RealBackendRefreshScope.MEDIA_MANAGEMENT,
                            ),
                            postIds = setOf(route.postId),
                            mediaIds = setOf(mediaId),
                        ),
                    )
                    loadMediaComments(mediaId, successMessage)
                }
                is ApiResult.Error -> {
                    _uiState.update { state ->
                        state.copy(
                            mediaComments = state.mediaComments + (
                                mediaId to state.mediaComments[mediaId].orEmpty().copy(
                                    isMutating = false,
                                    errorMessage = result.toUiMessage("评论操作失败。"),
                                )
                            ),
                        )
                    }
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    private suspend fun loadCurrentUser(): RemoteCurrentUser? {
        return when (val result = authRepository.getCurrentUser()) {
            is ApiResult.Success -> result.data
            else -> null
        }
    }

    private suspend fun loadAlbumTitleMap(): Map<String, String> {
        return when (val result = albumRepository.getAlbums()) {
            is ApiResult.Success -> result.data.associate { it.albumId to it.title }
            else -> emptyMap()
        }
    }

    companion object {
        fun factory(route: PostDetailPlaceholderRoute): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PostDetailRealViewModel(route = route) as T
                }
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
            comments = comments.map { it.toCommentUiModel(currentUserId) }.sortedByDescending { it.createdAtMillis },
            isLoading = false,
            isMutating = false,
            errorMessage = errorMessage,
            statusMessage = successMessage,
        )
    } else {
        RealCommentThreadUiState(
            comments = comments.map { it.toCommentUiModel(currentUserId) }.sortedByDescending { it.createdAtMillis },
            isLoading = isLoading,
            isMutating = false,
            errorMessage = null,
            statusMessage = successMessage,
        )
    }
}

private fun RealCommentThreadUiState?.orEmpty(): RealCommentThreadUiState {
    return this ?: RealCommentThreadUiState()
}

private fun ApiResult.Error.toUiMessage(fallback: String): String {
    val detail = throwable.toNetworkDetail()
    return when {
        !detail.isNullOrBlank() -> detail
        message.isNotBlank() -> message
        else -> fallback
    }
}

private fun Throwable?.toNetworkDetail(): String? {
    return when (this) {
        null -> null
        is HttpException -> when (code()) {
            401 -> "登录状态已失效，请先到联调诊断页重新登录。"
            403 -> "当前账号没有权限执行这个操作。"
            404 -> "后端资源不存在，可能已经被删除或恢复。"
            else -> "后端请求失败，HTTP ${code()}。"
        }
        is IOException -> message ?: "网络请求失败，请检查 baseUrl、同一 Wi-Fi 和服务端状态。"
        else -> message
    }
}
