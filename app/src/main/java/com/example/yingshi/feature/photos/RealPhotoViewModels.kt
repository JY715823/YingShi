package com.example.yingshi.feature.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yingshi.data.cache.AppReadCacheStore
import com.example.yingshi.data.cache.CachedAlbumDirectory
import com.example.yingshi.data.cache.OfflineAccessManager
import com.example.yingshi.data.cache.OfflineReadOnlyDefaultMessage
import com.example.yingshi.data.model.CommentListState
import com.example.yingshi.data.model.RemoteAlbum
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.model.RemotePostSummary
import com.example.yingshi.data.model.toCommentListState
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.auth.BackendAutoLoginManager
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.AlbumRepository
import com.example.yingshi.data.repository.AuthRepository
import com.example.yingshi.data.repository.CommentRepository
import com.example.yingshi.data.repository.PostRepository
import com.example.yingshi.data.repository.RepositoryProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

data class AlbumPageRealUiState(
    val isLoading: Boolean = false,
    val isPostsLoading: Boolean = false,
    val isOfflineReadOnly: Boolean = false,
    val tokenMissing: Boolean = false,
    val errorMessage: String? = null,
    val postsErrorMessage: String? = null,
    val statusMessage: String? = null,
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

    private var refreshJob: Job? = null
    private var loadPostsJob: Job? = null
    private var loadCoverJob: Job? = null
    private var refreshVersion = 0
    private var postsLoadVersion = 0

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        val requestVersion = ++refreshVersion
        refreshJob = viewModelScope.launch {
            val cachedDirectory = withContext(Dispatchers.IO) { readCachedDirectory() }
            if (cachedDirectory != null && _uiState.value.albums.isEmpty()) {
                applyCachedDirectory(cachedDirectory)
            }
            if (!AuthSessionManager.isLoggedIn) {
                if (cachedDirectory != null && OfflineAccessManager.state.isReadOnly) {
                    applyCachedDirectory(
                        cachedDirectory = cachedDirectory,
                        statusMessage = OfflineAccessManager.state.message ?: OfflineReadOnlyDefaultMessage,
                        isOfflineReadOnly = true,
                    )
                    return@launch
                }
                val loginOutcome = BackendAutoLoginManager.loginDefault(
                    force = false,
                    reason = "real_album_refresh",
                )
                if (!loginOutcome.success) {
                    if (requestVersion != refreshVersion) return@launch
                    _uiState.value = AlbumPageRealUiState(
                        tokenMissing = true,
                        errorMessage = loginOutcome.message.ifBlank {
                            "需要先完成登录，请检查连接设置后再打开相册。"
                        },
                    )
                    return@launch
                }
            }

            _uiState.update {
                it.copy(
                    isLoading = true,
                    isOfflineReadOnly = false,
                    tokenMissing = false,
                    errorMessage = null,
                    postsErrorMessage = null,
                    statusMessage = null,
                )
            }
            when (val result = albumRepository.getAlbums()) {
                is ApiResult.Success -> {
                    if (requestVersion != refreshVersion) return@launch
                    val cachedPostsByAlbumId = cachedDirectory?.postsByAlbumId.orEmpty()
                    val albums = result.data.map { album -> album.toAlbumSummaryUiModel() }
                    val selectedAlbumId = _uiState.value.selectedAlbumId
                        ?.takeIf { currentId -> albums.any { it.id == currentId } }
                        ?: albums.firstOrNull()?.id
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isOfflineReadOnly = false,
                        albums = albums,
                        selectedAlbumId = selectedAlbumId,
                        posts = cachedPostsByAlbumId[selectedAlbumId]
                            .orEmpty()
                            .map { post -> post.toAlbumPostCardUiModel(selectedAlbumId = selectedAlbumId.orEmpty()) },
                    )
                    withContext(Dispatchers.IO) {
                        persistAlbumDirectory(
                            albums = result.data,
                            postsByAlbumId = cachedPostsByAlbumId,
                        )
                    }
                    OfflineAccessManager.clear()
                    if (selectedAlbumId != null) {
                        loadAlbumPosts(selectedAlbumId)
                    }
                }
                is ApiResult.Error -> {
                    if (requestVersion != refreshVersion) return@launch
                    if (cachedDirectory != null && (OfflineAccessManager.state.isReadOnly || result.shouldFallbackToReadCache())) {
                        val message = result.offlineReadOnlyMessage()
                        OfflineAccessManager.enterReadOnly(message)
                        applyCachedDirectory(
                            cachedDirectory = cachedDirectory,
                            statusMessage = message,
                            isOfflineReadOnly = true,
                        )
                    } else {
                        _uiState.value = AlbumPageRealUiState(
                            isLoading = false,
                            errorMessage = result.toBackendUiMessage("读取相册失败。"),
                        )
                    }
                }
                ApiResult.Loading -> Unit
            }
            if (refreshVersion == requestVersion) {
                refreshJob = null
            }
        }
    }

    fun handleConnectivityLost() {
        refreshJob?.cancel()
        loadPostsJob?.cancel()
        loadCoverJob?.cancel()
        refreshVersion += 1
        postsLoadVersion += 1
        refreshJob = null
        loadPostsJob = null
        loadCoverJob = null
        viewModelScope.launch {
            val cachedDirectory = withContext(Dispatchers.IO) { readCachedDirectory() }
            val message = "网络已断开，当前显示缓存内容，恢复连接后会自动刷新。"
            if (cachedDirectory != null) {
                OfflineAccessManager.enterReadOnly(message)
                applyCachedDirectory(
                    cachedDirectory = cachedDirectory,
                    statusMessage = message,
                    isOfflineReadOnly = true,
                )
                return@launch
            }
            val hasVisibleContent = _uiState.value.albums.isNotEmpty() || _uiState.value.posts.isNotEmpty()
            if (hasVisibleContent) {
                OfflineAccessManager.enterReadOnly(message)
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isPostsLoading = false,
                    isOfflineReadOnly = hasVisibleContent,
                    tokenMissing = false,
                    errorMessage = if (hasVisibleContent) null else "当前无网络，恢复连接后会自动重试。",
                    postsErrorMessage = null,
                    statusMessage = if (hasVisibleContent) message else null,
                )
            }
        }
    }

    fun selectAlbum(albumId: String) {
        if (_uiState.value.isOfflineReadOnly) {
            viewModelScope.launch {
                val cachedPosts = withContext(Dispatchers.IO) {
                    readCachedDirectory()?.postsByAlbumId?.get(albumId).orEmpty()
                }
                _uiState.update {
                    it.copy(
                        selectedAlbumId = albumId,
                        posts = cachedPosts.map { post -> post.toAlbumPostCardUiModel(selectedAlbumId = albumId) },
                        postsErrorMessage = if (cachedPosts.isEmpty()) {
                            "当前离线，只能查看已缓存的小相册。"
                        } else {
                            null
                        },
                    )
                }
            }
            return
        }
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
        loadCoverJob?.cancel()
        val requestVersion = ++postsLoadVersion
        loadPostsJob = viewModelScope.launch {
            if (_uiState.value.isOfflineReadOnly) {
                val cachedPosts = withContext(Dispatchers.IO) {
                    readCachedDirectory()?.postsByAlbumId?.get(albumId).orEmpty()
                }
                _uiState.update {
                    it.copy(
                        isPostsLoading = false,
                        posts = cachedPosts.map { post -> post.toAlbumPostCardUiModel(selectedAlbumId = albumId) },
                        postsErrorMessage = if (cachedPosts.isEmpty()) "当前离线，只能查看已缓存的小相册。" else null,
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isPostsLoading = true,
                    postsErrorMessage = null,
                )
            }
            when (val result = albumRepository.getAlbumPosts(albumId)) {
                is ApiResult.Success -> {
                    if (requestVersion != postsLoadVersion) return@launch
                    val posts = result.data.map { post ->
                        post.toAlbumPostCardUiModel(selectedAlbumId = albumId)
                    }
                    val fallbackAlbums = _uiState.value.albums.map { album ->
                        RemoteAlbum(
                            albumId = album.id,
                            title = album.title,
                            subtitle = album.subtitle,
                            coverMediaId = null,
                            smallAlbumCount = 0,
                        )
                    }
                    withContext(Dispatchers.IO) {
                        persistAlbumDirectory(
                            albums = readCachedAlbums().ifEmpty { fallbackAlbums },
                            postsByAlbumId = readCachedDirectory()?.postsByAlbumId.orEmpty() + (albumId to result.data),
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isPostsLoading = false,
                            isOfflineReadOnly = false,
                            posts = posts,
                        )
                    }
                    prefetchAlbumPostCovers(
                        albumId = albumId,
                        summaries = result.data,
                    )
                }
                is ApiResult.Error -> {
                    if (requestVersion != postsLoadVersion) return@launch
                    val cachedPosts = withContext(Dispatchers.IO) {
                        readCachedDirectory()?.postsByAlbumId?.get(albumId).orEmpty()
                    }
                    if (cachedPosts.isNotEmpty() && (OfflineAccessManager.state.isReadOnly || result.shouldFallbackToReadCache())) {
                        val message = result.offlineReadOnlyMessage()
                        OfflineAccessManager.enterReadOnly(message)
                        _uiState.update {
                            it.copy(
                                isPostsLoading = false,
                                isOfflineReadOnly = true,
                                statusMessage = message,
                                posts = cachedPosts.map { post ->
                                    post.toAlbumPostCardUiModel(selectedAlbumId = albumId)
                                },
                                postsErrorMessage = null,
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isPostsLoading = false,
                                postsErrorMessage = result.toBackendUiMessage("读取大相册下的小相册失败。"),
                            )
                        }
                    }
                }
                ApiResult.Loading -> Unit
            }
            if (postsLoadVersion == requestVersion) {
                loadPostsJob = null
            }
        }
    }

    private fun prefetchAlbumPostCovers(
        albumId: String,
        summaries: List<RemotePostSummary>,
    ) {
        val targetSummaries = summaries
            .asSequence()
            .filter { it.coverMediaId != null }
            .take(6)
            .toList()
        if (targetSummaries.isEmpty()) return

        loadCoverJob?.cancel()
        loadCoverJob = viewModelScope.launch {
            val previewMediaByPostId = supervisorScope {
                targetSummaries.associate { summary ->
                    summary.postId to async {
                        when (val detailResult = postRepository.getPostDetail(summary.postId)) {
                            is ApiResult.Success -> {
                                val detail = detailResult.data
                                val coverMedia = detail.mediaItems.firstOrNull { it.mediaId == detail.coverMediaId }
                                    ?: detail.mediaItems.firstOrNull()
                                listOfNotNull(coverMedia)
                                    .plus(detail.mediaItems)
                                    .distinctBy { it.mediaId }
                                    .take(2)
                            }
                            else -> emptyList()
                        }
                    }
                }.mapValues { (_, deferred) -> deferred.await() }
            }

            if (_uiState.value.selectedAlbumId != albumId) return@launch

            _uiState.update { state ->
                state.copy(
                    posts = state.posts.map { post ->
                        val previewMedia = previewMediaByPostId[post.id].orEmpty()
                        val coverMedia = previewMedia.firstOrNull() ?: return@map post
                        val sourcePost = targetSummaries.firstOrNull { it.postId == post.id } ?: return@map post
                        sourcePost.toAlbumPostCardUiModel(
                            selectedAlbumId = albumId,
                            coverMedia = coverMedia,
                            previewMedia = previewMedia,
                        )
                    },
                )
            }
        }
    }

    private fun readCachedDirectory(): CachedAlbumDirectory? {
        val userId = AuthSessionManager.getCurrentUserSnapshot()?.userId ?: return null
        return AppReadCacheStore.readAlbumDirectory(userId)?.payload
    }

    private fun readCachedAlbums(): List<RemoteAlbum> {
        return readCachedDirectory()?.albums.orEmpty()
    }

    private fun persistAlbumDirectory(
        albums: List<RemoteAlbum>,
        postsByAlbumId: Map<String, List<RemotePostSummary>>,
    ) {
        val userId = AuthSessionManager.getCurrentUserSnapshot()?.userId ?: return
        AppReadCacheStore.writeAlbumDirectory(
            userId = userId,
            payload = CachedAlbumDirectory(
                albums = albums,
                postsByAlbumId = postsByAlbumId,
            ),
        )
    }

    private fun applyCachedDirectory(
        cachedDirectory: CachedAlbumDirectory,
        statusMessage: String? = null,
        isOfflineReadOnly: Boolean = false,
    ) {
        val selectedAlbumId = _uiState.value.selectedAlbumId
            ?.takeIf { currentId -> cachedDirectory.albums.any { it.albumId == currentId } }
            ?: cachedDirectory.albums.firstOrNull()?.albumId
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isPostsLoading = false,
            isOfflineReadOnly = isOfflineReadOnly,
            tokenMissing = false,
            errorMessage = null,
            postsErrorMessage = null,
            statusMessage = statusMessage,
            albums = cachedDirectory.albums.map(RemoteAlbum::toAlbumSummaryUiModel),
            selectedAlbumId = selectedAlbumId,
            posts = cachedDirectory.postsByAlbumId[selectedAlbumId]
                .orEmpty()
                .map { post -> post.toAlbumPostCardUiModel(selectedAlbumId = selectedAlbumId.orEmpty()) },
        )
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
        viewModelScope.launch {
            if (!AuthSessionManager.isLoggedIn) {
                val loginOutcome = BackendAutoLoginManager.loginDefault(
                    force = false,
                    reason = "real_post_detail_refresh",
                )
                if (!loginOutcome.success) {
                    _uiState.value = PostDetailRealUiState(
                        tokenMissing = true,
                        errorMessage = loginOutcome.message.ifBlank {
                            "需要先完成登录，请检查连接设置后再打开小相册。"
                        },
                    )
                    return@launch
                }
            }

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
                        errorMessage = result.toBackendUiMessage("读取小相册详情失败。"),
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

    fun handleExternalCommentMutation(event: RealBackendMutationEvent) {
        if (!_uiState.value.detailMediaIds().let { mediaIds ->
                event.isCommentMutationForPostDetail(route.postId, mediaIds)
            }
        ) {
            return
        }

        if (event.postIds.contains(route.postId) && _uiState.value.postComments.comments.isNotEmpty()) {
            loadPostComments()
        }

        val loadedMediaIds = _uiState.value.mediaComments.keys
        val targetMediaIds = if (event.mediaIds.isEmpty()) {
            loadedMediaIds
        } else {
            loadedMediaIds.intersect(event.mediaIds)
        }
        targetMediaIds.forEach(::loadMediaComments)
    }

    fun createPostComment(content: String) {
        val normalized = content.trim()
        if (normalized.isEmpty()) return
        mutatePostComments(
            successMessage = "评论已发送。",
            onSuccess = {
                NotificationCenterLocalStore.pushPostCommentNotification(
                    postId = route.postId,
                    comment = normalized,
                )
            },
        ) {
            commentRepository.createPostComment(route.postId, normalized)
        }
    }

    fun createMediaComment(mediaId: String, content: String) {
        val normalized = content.trim()
        if (normalized.isEmpty()) return
        mutateMediaComments(
            mediaId = mediaId,
            successMessage = "评论已发送。",
            onSuccess = {
                NotificationCenterLocalStore.pushMediaCommentNotification(
                    mediaId = mediaId,
                    comment = normalized,
                    postId = route.postId,
                )
            },
        ) {
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
        onSuccess: (() -> Unit)? = null,
        block: suspend () -> ApiResult<*>,
    ) {
        if (!AuthSessionManager.isLoggedIn) {
            _uiState.update {
                it.copy(
                    postComments = it.postComments.copy(
                        errorMessage = "登录状态缺失，请重新登录。",
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
                    onSuccess?.invoke()
                    loadPostComments(successMessage)
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            postComments = it.postComments.copy(
                                isMutating = false,
                                errorMessage = result.toBackendUiMessage("评论操作失败。"),
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
        onSuccess: (() -> Unit)? = null,
        block: suspend () -> ApiResult<*>,
    ) {
        if (!AuthSessionManager.isLoggedIn) {
            _uiState.update { state ->
                state.copy(
                    mediaComments = state.mediaComments + (
                        mediaId to state.mediaComments[mediaId].orEmpty().copy(
                            errorMessage = "登录状态缺失，请重新登录。",
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
                    onSuccess?.invoke()
                    notifyRealBackendCommentChanged(
                        postIds = setOf(route.postId),
                        mediaIds = setOf(mediaId),
                    )
                    loadMediaComments(mediaId, successMessage)
                }
                is ApiResult.Error -> {
                    _uiState.update { state ->
                        state.copy(
                            mediaComments = state.mediaComments + (
                                mediaId to state.mediaComments[mediaId].orEmpty().copy(
                                    isMutating = false,
                                    errorMessage = result.toBackendUiMessage("评论操作失败。"),
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

private fun PostDetailRealUiState.detailMediaIds(): List<String> {
    return detail?.mediaItems?.map { it.id }.orEmpty()
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
            401 -> "登录状态已失效，请重新登录。"
            403 -> "当前账号没有权限执行这个操作。"
            404 -> "资源不存在，可能已经被删除或恢复。"
            else -> "同步请求失败，HTTP ${code()}。"
        }
        is IOException -> message ?: "网络请求失败，请检查服务地址、网络和服务状态。"
        else -> message
    }
}
