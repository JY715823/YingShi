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
import com.example.yingshi.data.model.RemotePostDetail
import com.example.yingshi.data.model.RemotePostMedia
import com.example.yingshi.data.model.RemotePostSummary
import com.example.yingshi.data.model.UpdateAlbumPayload
import com.example.yingshi.data.model.toCommentListState
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.auth.BackendSessionProbe
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.AlbumRepository
import com.example.yingshi.data.repository.AuthRepository
import com.example.yingshi.data.repository.CommentRepository
import com.example.yingshi.data.repository.PostRepository
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.sync.SyncModule
import com.example.yingshi.feature.sync.SyncVersionTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

private const val CommentNoticeVisibleMillis = 1800L

data class AlbumPageRealUiState(
    val isLoading: Boolean = false,
    val isPostsLoading: Boolean = false,
    val isPostsRefreshing: Boolean = false,
    val isAlbumMutating: Boolean = false,
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
    val hasMore: Boolean = false,
    val currentPage: Int = 1,
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
    private var cachedDirectorySnapshot: CachedAlbumDirectory? = null
    private var refreshVersion = 0
    private var postsLoadVersion = 0
    private val albumPostCardCacheByAlbumId = mutableMapOf<String, List<AlbumPostCardUiModel>>()
    private val pendingAlbumOverrides = mutableMapOf<String, PendingAlbumOverride>()

    init {
        refresh()
    }

    /**
     * 修复：refresh 完成后再 markRefreshed，避免 refresh 期间服务端版本又涨
     * 导致下次 poll 又 stale=true，形成闪烁循环。
     *
     * 根因 C 修复: 使用 markRefreshedFresh (suspend) 先拉取最新服务端版本再同步 local,
     * 彻底避免用陈旧快照导致下次 poll 又 stale=true 的循环。
     */
    fun refreshAndMarkRefreshed() {
        refreshJob?.cancel()
        val requestVersion = ++refreshVersion
        refreshJob = viewModelScope.launch {
            refreshInternal(requestVersion)
            // refresh 完成后标记已刷新，先拉最新 remote 再同步 local
            SyncVersionTracker.markRefreshedFresh(SyncModule.ALBUMS)
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        val requestVersion = ++refreshVersion
        refreshJob = viewModelScope.launch {
            refreshInternal(requestVersion)
            if (refreshVersion == requestVersion) {
                refreshJob = null
            }
        }
    }

    private suspend fun refreshInternal(requestVersion: Int) {
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
                return
            }
            val loginOutcome = BackendSessionProbe.probeSessionState(
                force = false,
                reason = "real_album_refresh",
            )
            if (!loginOutcome.success) {
                if (requestVersion != refreshVersion) return
                _uiState.value = AlbumPageRealUiState(
                    tokenMissing = true,
                    errorMessage = loginOutcome.message.ifBlank {
                        "需要先完成登录，请检查连接设置后再打开相册。"
                    },
                )
                return
            }
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                isOfflineReadOnly = false,
                tokenMissing = false,
                errorMessage = null,
                postsErrorMessage = null,
            )
        }
        when (val result = albumRepository.getAlbums()) {
            is ApiResult.Success -> {
                if (requestVersion != refreshVersion) return
                val cachedPostsByAlbumId = cachedDirectory?.postsByAlbumId.orEmpty()
                val cachedPreviewMediaByPostId = cachedDirectory.cachedPreviewMediaByPostId()
                val persistedAlbums = mergeRemoteAlbumsWithPendingOverrides(result.data)
                val albums = persistedAlbums.map { album -> album.toAlbumSummaryUiModel() }
                val selectedAlbumId = _uiState.value.selectedAlbumId
                    ?.takeIf { currentId -> albums.any { it.id == currentId } }
                    ?: albums.firstOrNull()?.id
                OfflineAccessManager.clear()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isPostsLoading = false,
                    isPostsRefreshing = false,
                    isOfflineReadOnly = false,
                    albums = albums,
                    selectedAlbumId = selectedAlbumId,
                    posts = selectedAlbumId?.let { resolvedAlbumId ->
                        cachedAlbumPostCards(resolvedAlbumId)
                            ?: cachedPostsByAlbumId[resolvedAlbumId]
                                .orEmpty()
                                .map { post ->
                                    post.toAlbumPostCardUiModelWithPreviewCache(
                                        selectedAlbumId = resolvedAlbumId,
                                        previewMediaByPostId = cachedPreviewMediaByPostId,
                                    )
                                }
                    }.orEmpty(),
                    statusMessage = _uiState.value.statusMessage.clearRecoveredNetworkStatus(),
                )
                withContext(Dispatchers.IO) {
                    persistAlbumDirectory(
                        albums = persistedAlbums,
                        postsByAlbumId = cachedPostsByAlbumId,
                    )
                }
                if (selectedAlbumId != null) {
                    loadAlbumPosts(
                        albumId = selectedAlbumId,
                        showBlockingIndicator = !cachedPostsByAlbumId.containsKey(selectedAlbumId),
                    )
                }
            }
            is ApiResult.Error -> {
                if (requestVersion != refreshVersion) return
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
                    isPostsRefreshing = false,
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
        viewModelScope.launch {
            val cachedDirectory = cachedDirectorySnapshot ?: withContext(Dispatchers.IO) { readCachedDirectory() }
            val cachedPostsByAlbumId = cachedDirectory?.postsByAlbumId.orEmpty()
            val cachedPreviewMediaByPostId = cachedDirectory.cachedPreviewMediaByPostId()
            val hasCachedPosts = cachedPostsByAlbumId.containsKey(albumId)
            val cachedPosts = cachedPostsByAlbumId[albumId].orEmpty()

            if (_uiState.value.isOfflineReadOnly) {
                _uiState.update {
                    it.copy(
                        selectedAlbumId = albumId,
                        posts = cachedAlbumPostCards(albumId)
                            ?: cachedPosts.map { post ->
                                post.toAlbumPostCardUiModelWithPreviewCache(
                                    selectedAlbumId = albumId,
                                    previewMediaByPostId = cachedPreviewMediaByPostId,
                                )
                            },
                        isPostsLoading = false,
                        isPostsRefreshing = false,
                        postsErrorMessage = if (hasCachedPosts) {
                            null
                        } else {
                            "当前离线，只能查看已缓存的小相册。"
                        },
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    selectedAlbumId = albumId,
                    posts = cachedAlbumPostCards(albumId)
                        ?: if (hasCachedPosts) {
                            cachedPosts.map { post ->
                                post.toAlbumPostCardUiModelWithPreviewCache(
                                    selectedAlbumId = albumId,
                                    previewMediaByPostId = cachedPreviewMediaByPostId,
                                )
                            }
                        } else {
                            emptyList()
                        },
                    isPostsLoading = false,
                    isPostsRefreshing = false,
                    postsErrorMessage = null,
                )
            }
            loadAlbumPosts(
                albumId = albumId,
                showBlockingIndicator = !hasCachedPosts,
            )
        }
    }

    fun renameAlbum(
        albumId: String,
        payload: UpdateAlbumPayload,
    ) {
        val normalizedTitle = payload.title.trim()
        val normalizedSubtitle = payload.subtitle.trim()
        if (normalizedTitle.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "大相册名称不能为空。") }
            return
        }
        if (_uiState.value.isOfflineReadOnly) {
            _uiState.update { it.copy(errorMessage = "缓存只读模式下不能重命名大相册。") }
            return
        }
        val targetAlbum = _uiState.value.albums.firstOrNull { it.id == albumId }
            ?: return
        val titleChanged = normalizedTitle != targetAlbum.title.trim()
        val descriptionChanged = normalizedSubtitle != targetAlbum.description.trim()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAlbumMutating = true,
                    errorMessage = null,
                    postsErrorMessage = null,
                )
            }
            when (
                val result = albumRepository.updateAlbum(
                    albumId,
                    UpdateAlbumPayload(
                        title = normalizedTitle,
                        subtitle = normalizedSubtitle,
                    ),
                )
            ) {
                is ApiResult.Success -> {
                    pendingAlbumOverrides[albumId] = PendingAlbumOverride(
                        title = normalizedTitle,
                        description = normalizedSubtitle,
                    )
                    val serverAlbum = result.data.toAlbumSummaryUiModel()
                    val updatedAlbum = serverAlbum.copy(
                        title = normalizedTitle,
                        description = normalizedSubtitle,
                    )
                    val nextAlbums = _uiState.value.albums.map { album ->
                        if (album.id == albumId) updatedAlbum else album
                    }
                    val nextPostsByAlbumId = cachedDirectorySnapshot?.postsByAlbumId.orEmpty()
                    val nextRemoteAlbums = cachedDirectorySnapshot?.albums
                        ?.map { album ->
                            if (album.albumId == albumId) {
                                result.data.copy(
                                    title = normalizedTitle,
                                    subtitle = normalizedSubtitle,
                                )
                            } else {
                                album
                            }
                        }
                        ?.takeIf { it.isNotEmpty() }
                        ?: buildCachedAlbumsFromUi(nextAlbums, nextPostsByAlbumId)
                    val successMessage = when {
                        titleChanged && descriptionChanged -> "已更新这个大相册的信息。"
                        titleChanged -> "已将大相册改名为「${updatedAlbum.title}」。"
                        descriptionChanged -> "已更新这个大相册的简介。"
                        else -> null
                    }
                    withContext(Dispatchers.IO) {
                        persistAlbumDirectory(
                            albums = nextRemoteAlbums,
                            postsByAlbumId = nextPostsByAlbumId,
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isAlbumMutating = false,
                            albums = nextAlbums,
                            statusMessage = successMessage,
                        )
                    }
                    notifyRealBackendAlbumsChanged()
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isAlbumMutating = false,
                            errorMessage = result.toBackendUiMessage("重命名大相册失败。"),
                        )
                    }
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { current ->
            val currentMessage = current.statusMessage
            if (currentMessage == null || !currentMessage.shouldAutoDismiss()) {
                current
            } else {
                current.copy(statusMessage = null)
            }
        }
    }

    fun moveSmallAlbums(
        targetAlbumId: String,
        smallAlbumIds: List<String>,
    ) {
        if (_uiState.value.isOfflineReadOnly) {
            _uiState.update { it.copy(errorMessage = "缓存只读模式下不能切换小相册所属大相册。") }
            return
        }
        if (smallAlbumIds.isEmpty()) return
        val sourceAlbumId = _uiState.value.selectedAlbumId ?: return
        if (sourceAlbumId == targetAlbumId) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAlbumMutating = true,
                    errorMessage = null,
                    postsErrorMessage = null,
                )
            }
            when (val result = albumRepository.moveSmallAlbums(targetAlbumId, smallAlbumIds)) {
                is ApiResult.Success -> {
                    val nextPostsByAlbumId = cachedDirectorySnapshot?.postsByAlbumId.orEmpty()
                        .mapValues { (albumId, posts) ->
                            if (albumId == sourceAlbumId) {
                                posts.filterNot { it.postId in smallAlbumIds }
                            } else {
                                posts
                            }
                        }
                    val nextRemoteAlbums = cachedDirectorySnapshot?.albums ?: emptyList()
                    withContext(Dispatchers.IO) {
                        persistAlbumDirectory(
                            albums = nextRemoteAlbums,
                            postsByAlbumId = nextPostsByAlbumId,
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isAlbumMutating = false,
                            posts = if (sourceAlbumId == it.selectedAlbumId) {
                                it.posts.filterNot { post -> post.id in smallAlbumIds }
                            } else {
                                it.posts
                            },
                            statusMessage = "已将 ${smallAlbumIds.size} 个小相册移动到目标大相册。",
                        )
                    }
                    notifyRealBackendContentChanged(postIds = smallAlbumIds.toSet())
                    if (sourceAlbumId == _uiState.value.selectedAlbumId) {
                        loadAlbumPosts(
                            albumId = sourceAlbumId,
                            showBlockingIndicator = false,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isAlbumMutating = false,
                            errorMessage = result.toBackendUiMessage("切换小相册所属大相册失败。"),
                        )
                    }
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun deleteAlbum(albumId: String) {
        if (_uiState.value.isOfflineReadOnly) {
            _uiState.update { it.copy(errorMessage = "缓存只读模式下不能删除大相册。") }
            return
        }
        pendingAlbumOverrides.remove(albumId)
        val targetAlbum = _uiState.value.albums.firstOrNull { it.id == albumId }
            ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAlbumMutating = true,
                    errorMessage = null,
                    postsErrorMessage = null,
                )
            }
            when (val result = albumRepository.deleteAlbum(albumId)) {
                is ApiResult.Success -> {
                    val currentState = _uiState.value
                    val nextAlbums = currentState.albums.filterNot { it.id == albumId }
                    val nextSelectedAlbumId = currentState.selectedAlbumId
                        ?.takeUnless { it == albumId }
                        ?.takeIf { currentId -> nextAlbums.any { it.id == currentId } }
                        ?: nextAlbums.firstOrNull()?.id
                    val nextPostsByAlbumId = cachedDirectorySnapshot
                        ?.postsByAlbumId
                        .orEmpty()
                        .filterKeys { key -> key != albumId }
                    val nextPreviewMediaByPostId = cachedDirectorySnapshot.cachedPreviewMediaByPostId()
                        .filterKeys { postId ->
                            nextPostsByAlbumId.values.any { posts -> posts.any { it.postId == postId } }
                        }
                    albumPostCardCacheByAlbumId.remove(albumId)
                    val nextRemoteAlbums = cachedDirectorySnapshot?.albums
                        ?.filterNot { album -> album.albumId == albumId }
                        ?.takeIf { it.isNotEmpty() || nextAlbums.isEmpty() }
                        ?: buildCachedAlbumsFromUi(nextAlbums, nextPostsByAlbumId)
                    val nextPosts = nextSelectedAlbumId?.let { resolvedAlbumId ->
                        cachedAlbumPostCards(resolvedAlbumId)
                            ?: nextPostsByAlbumId[resolvedAlbumId]
                                .orEmpty()
                                .map { post ->
                                    post.toAlbumPostCardUiModelWithPreviewCache(
                                        selectedAlbumId = resolvedAlbumId,
                                        previewMediaByPostId = nextPreviewMediaByPostId,
                                    )
                                }
                    }.orEmpty()
                    withContext(Dispatchers.IO) {
                        persistAlbumDirectory(
                            albums = nextRemoteAlbums,
                            postsByAlbumId = nextPostsByAlbumId,
                            previewMediaByPostId = nextPreviewMediaByPostId,
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isAlbumMutating = false,
                            albums = nextAlbums,
                            selectedAlbumId = nextSelectedAlbumId,
                            posts = nextPosts,
                            isPostsLoading = false,
                            isPostsRefreshing = false,
                            statusMessage = "已将大相册移入回收站，可在回收站整组恢复。",
                        )
                    }
                    notifyRealBackendContentChanged(postIds = result.data.relatedPostIds.toSet())
                    if (nextSelectedAlbumId != null) {
                        loadAlbumPosts(
                            albumId = nextSelectedAlbumId,
                            showBlockingIndicator = !nextPostsByAlbumId.containsKey(nextSelectedAlbumId),
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isAlbumMutating = false,
                            errorMessage = result.toBackendUiMessage("删除大相册失败。"),
                        )
                    }
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    private fun loadAlbumPosts(
        albumId: String,
        showBlockingIndicator: Boolean,
    ) {
        loadPostsJob?.cancel()
        loadCoverJob?.cancel()
        val requestVersion = ++postsLoadVersion
        loadPostsJob = viewModelScope.launch {
            val cachedPostsByAlbumId = cachedDirectorySnapshot?.postsByAlbumId.orEmpty()
            val cachedPreviewMediaByPostId = cachedDirectorySnapshot.cachedPreviewMediaByPostId()
            val hasCachedPosts = cachedPostsByAlbumId.containsKey(albumId)
            val cachedPosts = cachedPostsByAlbumId[albumId].orEmpty()
            if (_uiState.value.isOfflineReadOnly) {
                    _uiState.update {
                        it.copy(
                            isPostsLoading = false,
                            isPostsRefreshing = false,
                            posts = cachedAlbumPostCards(albumId)
                                ?: cachedPosts.map { post ->
                                    post.toAlbumPostCardUiModelWithPreviewCache(
                                        selectedAlbumId = albumId,
                                        previewMediaByPostId = cachedPreviewMediaByPostId,
                                    )
                                },
                            postsErrorMessage = if (hasCachedPosts) null else "当前离线，只能查看已缓存的小相册。",
                        )
                    }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isPostsLoading = showBlockingIndicator,
                    isPostsRefreshing = !showBlockingIndicator,
                    postsErrorMessage = null,
                )
            }
            when (val result = albumRepository.getAlbumPosts(albumId)) {
                is ApiResult.Success -> {
                    if (requestVersion != postsLoadVersion) return@launch
                    val posts = mergeAlbumPostCards(
                        albumId = albumId,
                        summaries = result.data,
                    )
                    val fallbackAlbums = _uiState.value.albums.map { album ->
                        RemoteAlbum(
                            albumId = album.id,
                            title = album.title,
                            subtitle = album.description,
                            coverMediaId = null,
                            smallAlbumCount = 0,
                            systemKey = album.systemKey,
                            includeInPhotoFeed = album.includeInPhotoFeed,
                        )
                    }
                    withContext(Dispatchers.IO) {
                        persistAlbumDirectory(
                            albums = readCachedAlbums().ifEmpty { fallbackAlbums },
                            postsByAlbumId = readCachedDirectory()?.postsByAlbumId.orEmpty() + (albumId to result.data),
                        )
                    }
                    rememberAlbumPostCards(albumId, posts)
                    OfflineAccessManager.clear()
                    _uiState.update {
                        it.copy(
                            isPostsLoading = false,
                            isPostsRefreshing = false,
                            isOfflineReadOnly = false,
                            posts = posts,
                            statusMessage = it.statusMessage.clearRecoveredNetworkStatus(),
                        )
                    }
                    prefetchAlbumPostCovers(
                        albumId = albumId,
                        summaries = result.data,
                    )
                }
                is ApiResult.Error -> {
                    if (requestVersion != postsLoadVersion) return@launch
                    if (hasCachedPosts && (OfflineAccessManager.state.isReadOnly || result.shouldFallbackToReadCache())) {
                        val message = result.offlineReadOnlyMessage()
                        OfflineAccessManager.enterReadOnly(message)
                        _uiState.update {
                            it.copy(
                                isPostsLoading = false,
                                isPostsRefreshing = false,
                                isOfflineReadOnly = true,
                                statusMessage = message,
                                posts = cachedAlbumPostCards(albumId)
                                    ?: cachedPosts.map { post ->
                                        post.toAlbumPostCardUiModelWithPreviewCache(
                                            selectedAlbumId = albumId,
                                            previewMediaByPostId = cachedPreviewMediaByPostId,
                                        )
                                    },
                                postsErrorMessage = null,
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isPostsLoading = false,
                                isPostsRefreshing = false,
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
            .filter { it.mediaCount > 0 }
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
            val nonEmptyPreviewMediaByPostId = previewMediaByPostId
                .filterValues { it.isNotEmpty() }
            if (nonEmptyPreviewMediaByPostId.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    persistAlbumDirectory(
                        albums = readCachedAlbums(),
                        postsByAlbumId = cachedDirectorySnapshot?.postsByAlbumId.orEmpty(),
                        previewMediaByPostId = cachedDirectorySnapshot.cachedPreviewMediaByPostId() +
                            nonEmptyPreviewMediaByPostId,
                    )
                }
            }

            _uiState.update { state ->
                val nextPosts = state.posts.map { post ->
                    val previewMedia = previewMediaByPostId[post.id].orEmpty()
                    val coverMedia = previewMedia.firstOrNull() ?: return@map post
                    val sourcePost = targetSummaries.firstOrNull { it.postId == post.id } ?: return@map post
                    sourcePost.toAlbumPostCardUiModel(
                        selectedAlbumId = albumId,
                        coverMedia = coverMedia,
                        previewMedia = previewMedia,
                    )
                }
                rememberAlbumPostCards(albumId, nextPosts)
                state.copy(posts = nextPosts)
            }
        }
    }

    private fun readCachedDirectory(): CachedAlbumDirectory? {
        val userId = AuthSessionManager.getCurrentUserSnapshot()?.userId ?: return null
        return AppReadCacheStore.readAlbumDirectory(userId)?.payload
            ?.also { cachedDirectorySnapshot = it }
    }

    private fun readCachedAlbums(): List<RemoteAlbum> {
        return readCachedDirectory()?.albums.orEmpty()
    }

    private fun persistAlbumDirectory(
        albums: List<RemoteAlbum>,
        postsByAlbumId: Map<String, List<RemotePostSummary>>,
        previewMediaByPostId: Map<String, List<RemotePostMedia>> = cachedDirectorySnapshot.cachedPreviewMediaByPostId(),
    ) {
        val userId = AuthSessionManager.getCurrentUserSnapshot()?.userId ?: return
        cachedDirectorySnapshot = CachedAlbumDirectory(
            albums = albums,
            postsByAlbumId = postsByAlbumId,
            previewMediaByPostId = previewMediaByPostId.takeIf { it.isNotEmpty() },
        )
        AppReadCacheStore.writeAlbumDirectory(
            userId = userId,
            payload = cachedDirectorySnapshot!!,
        )
    }

    private fun applyCachedDirectory(
        cachedDirectory: CachedAlbumDirectory,
        statusMessage: String? = null,
        isOfflineReadOnly: Boolean = false,
    ) {
        cachedDirectorySnapshot = cachedDirectory
        val selectedAlbumId = _uiState.value.selectedAlbumId
            ?.takeIf { currentId -> cachedDirectory.albums.any { it.albumId == currentId } }
            ?: cachedDirectory.albums.firstOrNull()?.albumId
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isPostsLoading = false,
            isPostsRefreshing = false,
            isOfflineReadOnly = isOfflineReadOnly,
            tokenMissing = false,
            errorMessage = null,
            postsErrorMessage = null,
            statusMessage = statusMessage,
            albums = cachedDirectory.albums.map(RemoteAlbum::toAlbumSummaryUiModel),
            selectedAlbumId = selectedAlbumId,
            posts = selectedAlbumId?.let { resolvedAlbumId ->
                val cachedPreviewMediaByPostId = cachedDirectory.cachedPreviewMediaByPostId()
                cachedAlbumPostCards(resolvedAlbumId)
                    ?: cachedDirectory.postsByAlbumId[resolvedAlbumId]
                        .orEmpty()
                        .map { post ->
                            post.toAlbumPostCardUiModelWithPreviewCache(
                                selectedAlbumId = resolvedAlbumId,
                                previewMediaByPostId = cachedPreviewMediaByPostId,
                            )
                        }
            }.orEmpty(),
        )
    }

    private fun cachedAlbumPostCards(albumId: String): List<AlbumPostCardUiModel>? {
        if (albumId.isBlank()) return null
        return albumPostCardCacheByAlbumId[albumId]
    }

    private fun rememberAlbumPostCards(
        albumId: String,
        posts: List<AlbumPostCardUiModel>,
    ) {
        if (albumId.isBlank()) return
        albumPostCardCacheByAlbumId[albumId] = posts
    }

    private fun mergeAlbumPostCards(
        albumId: String,
        summaries: List<RemotePostSummary>,
    ): List<AlbumPostCardUiModel> {
        val cachedCardsByPostId = cachedAlbumPostCards(albumId)
            .orEmpty()
            .associateBy { it.id }
        val cachedPreviewMediaByPostId = cachedDirectorySnapshot.cachedPreviewMediaByPostId()
        return summaries.map { summary ->
            val baseCard = summary.toAlbumPostCardUiModelWithPreviewCache(
                selectedAlbumId = albumId,
                previewMediaByPostId = cachedPreviewMediaByPostId,
            )
            val cachedCard = cachedCardsByPostId[summary.postId] ?: return@map baseCard
            baseCard.copy(
                coverMediaType = cachedCard.coverMediaType,
                coverAspectRatio = cachedCard.coverAspectRatio,
                coverMediaSource = cachedCard.coverMediaSource,
                previewMedia = cachedCard.previewMedia,
                coverRefreshNonce = cachedCard.coverRefreshNonce,
            )
        }
    }

    private fun mergeRemoteAlbumsWithPendingOverrides(
        remoteAlbums: List<RemoteAlbum>,
    ): List<RemoteAlbum> {
        if (pendingAlbumOverrides.isEmpty()) return remoteAlbums
        val remainingAlbumIds = remoteAlbums.mapTo(mutableSetOf()) { it.albumId }
        val mergedAlbums = remoteAlbums.map { remoteAlbum ->
            val pendingOverride = pendingAlbumOverrides[remoteAlbum.albumId] ?: return@map remoteAlbum
            val normalizedRemoteTitle = remoteAlbum.title.trim()
            val normalizedRemoteSubtitle = remoteAlbum.subtitle.trim()
            if (
                normalizedRemoteTitle == pendingOverride.title &&
                normalizedRemoteSubtitle == pendingOverride.description
            ) {
                pendingAlbumOverrides.remove(remoteAlbum.albumId)
                remoteAlbum
            } else {
                remoteAlbum.copy(
                    title = pendingOverride.title,
                    subtitle = pendingOverride.description,
                )
            }
        }
        pendingAlbumOverrides.keys.retainAll(remainingAlbumIds)
        return mergedAlbums
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

private data class PendingAlbumOverride(
    val title: String,
    val description: String,
)

private fun CachedAlbumDirectory?.cachedPreviewMediaByPostId(): Map<String, List<RemotePostMedia>> {
    return this?.previewMediaByPostId.orEmpty()
}

private fun RemotePostSummary.toAlbumPostCardUiModelWithPreviewCache(
    selectedAlbumId: String,
    previewMediaByPostId: Map<String, List<RemotePostMedia>>,
): AlbumPostCardUiModel {
    val previewMedia = previewMediaByPostId[postId]
        .orEmpty()
        .distinctBy { it.mediaId }
        .take(2)
    val coverMedia = previewMedia.firstOrNull { it.mediaId == coverMediaId }
        ?: previewMedia.firstOrNull()
    return toAlbumPostCardUiModel(
        selectedAlbumId = selectedAlbumId,
        coverMedia = coverMedia,
        previewMedia = previewMedia,
    )
}

private fun String.shouldAutoDismiss(): Boolean {
    return startsWith("已将大相册改名为「") ||
        this == "已更新这个大相册的简介。" ||
        this == "已更新这个大相册的信息。" ||
        this == "已将大相册移入回收站，可在回收站整组恢复。" ||
        endsWith("个小相册移动到目标大相册。")
}

private fun buildCachedAlbumsFromUi(
    albums: List<AlbumSummaryUiModel>,
    postsByAlbumId: Map<String, List<RemotePostSummary>>,
): List<RemoteAlbum> {
    return albums.map { album ->
        RemoteAlbum(
            albumId = album.id,
            title = album.title,
            subtitle = album.description,
            coverMediaId = null,
            smallAlbumCount = postsByAlbumId[album.id]?.size ?: 0,
            systemKey = album.systemKey,
            includeInPhotoFeed = album.includeInPhotoFeed,
        )
    }
}

private fun String?.clearRecoveredNetworkStatus(): String? {
    val normalized = this?.trim().orEmpty()
    if (normalized.isBlank()) return null
    return when {
        normalized.startsWith("网络已断开") -> null
        normalized.startsWith("网络已恢复") -> null
        else -> this
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

    private val commentThreadManager = CommentThreadManager(
        scope = viewModelScope,
        commentRepository = commentRepository,
        currentUserIdProvider = { _uiState.value.currentUserId },
        commentThreadsProvider = { _uiState.value.mediaComments },
        commentThreadsUpdater = { newThreads ->
            _uiState.update { it.copy(mediaComments = newThreads) }
        },
        onMutationSuccess = { mediaId ->
            notifyRealBackendCommentChanged(
                postIds = setOf(route.postId),
                mediaIds = setOf(mediaId),
            )
            SyncVersionTracker.markLocalMutation(SyncModule.ALBUMS)
            SyncVersionTracker.markLocalMutation(SyncModule.NOTIFICATIONS)
        },
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val cachedDetail = withContext(Dispatchers.IO) { readCachedPostDetail() }
            val cachedAlbumTitleById = withContext(Dispatchers.IO) { readCachedAlbumTitleMap() }
            if (cachedDetail != null && _uiState.value.detail == null) {
                try {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = null,
                        detail = cachedDetail.toPostDetailUiModel(cachedAlbumTitleById),
                        currentUserId = AuthSessionManager.getCurrentUserSnapshot()?.userId,
                    )
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "缓存数据解析失败，请尝试刷新。",
                    )
                }
            }
            if (!AuthSessionManager.isLoggedIn) {
                val loginOutcome = BackendSessionProbe.probeSessionState(
                    force = false,
                    reason = "real_post_detail_refresh",
                )
                if (!loginOutcome.success) {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            tokenMissing = state.detail == null,
                            errorMessage = loginOutcome.message.ifBlank {
                                "需要先完成登录，请检查连接设置后再打开小相册。"
                            },
                        )
                    }
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
                    withContext(Dispatchers.IO) {
                        writeCachedPostDetail(result.data)
                    }
                    try {
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
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "小相册数据解析失败：${e.message ?: "未知错误"}",
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = result.toBackendUiMessage("读取小相册详情失败。"),
                        )
                    }
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun retryPostComments() {
        loadPostComments()
    }

    fun loadMorePostComments() {
        val current = _uiState.value.postComments
        if (!current.hasMore || current.isLoading) return
        val nextPage = current.currentPage + 1
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    postComments = it.postComments.copy(isLoading = true, errorMessage = null),
                )
            }
            when (val result = commentRepository.getPostComments(route.postId, page = nextPage)) {
                is ApiResult.Success -> {
                    val newComments = result.data.comments
                        .filterNot { it.isDeleted }
                        .map { it.toCommentUiModel(_uiState.value.currentUserId) }
                    val existingIds = current.comments.map { it.id }.toSet()
                    val deduplicated = newComments.filterNot { it.id in existingIds }
                    _uiState.update { state ->
                        state.copy(
                            postComments = state.postComments.copy(
                                comments = state.postComments.comments + deduplicated,
                                isLoading = false,
                                hasMore = result.data.hasMore,
                                currentPage = result.data.page,
                            ),
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { state ->
                        state.copy(
                            postComments = state.postComments.copy(
                                isLoading = false,
                                errorMessage = result.toBackendUiMessage("加载更多评论失败。"),
                            ),
                        )
                    }
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun ensureMediaComments(mediaId: String) = commentThreadManager.ensureMediaComments(mediaId)

    fun retryMediaComments(mediaId: String) = commentThreadManager.retryMediaComments(mediaId)

    fun loadMoreMediaComments(mediaId: String) = commentThreadManager.loadMoreMediaComments(mediaId)

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
        targetMediaIds.forEach(commentThreadManager::loadMediaComments)
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
        NotificationCenterLocalStore.pushMediaCommentNotification(
            mediaId = mediaId,
            comment = normalized,
            postId = route.postId,
        )
        commentThreadManager.createMediaComment(mediaId, content)
    }

    fun updatePostComment(commentId: String, content: String) {
        val normalized = content.trim()
        if (normalized.isEmpty()) return
        mutatePostComments("评论已更新。") {
            commentRepository.updateComment(commentId, normalized)
        }
    }

    fun updateMediaComment(mediaId: String, commentId: String, content: String) {
        commentThreadManager.updateMediaComment(mediaId, commentId, content)
    }

    fun deletePostComment(commentId: String) {
        mutatePostComments("评论已删除。", deletedCommentId = commentId) {
            commentRepository.deleteComment(commentId)
        }
    }

    fun deleteMediaComment(mediaId: String, commentId: String) {
        commentThreadManager.deleteMediaComment(mediaId, commentId)
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
            clearPostCommentStatusLater(successMessage)
        }
    }

    private fun mutatePostComments(
        successMessage: String,
        deletedCommentId: String? = null,
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
                    notifyRealBackendCommentChanged(postIds = setOf(route.postId))
                    SyncVersionTracker.markLocalMutation(SyncModule.ALBUMS)
                    SyncVersionTracker.markLocalMutation(SyncModule.NOTIFICATIONS)
                    if (deletedCommentId != null) {
                        _uiState.update {
                            it.copy(
                                postComments = it.postComments.copy(
                                    comments = it.postComments.comments.filterNot { comment -> comment.id == deletedCommentId },
                                    isMutating = false,
                                    statusMessage = successMessage,
                                ),
                            )
                        }
                    }
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

    private fun clearPostCommentStatusLater(expectedMessage: String?) {
        if (expectedMessage == null) return
        viewModelScope.launch {
            delay(CommentNoticeVisibleMillis)
            _uiState.update {
                if (it.postComments.statusMessage == expectedMessage) {
                    it.copy(postComments = it.postComments.copy(statusMessage = null))
                } else {
                    it
                }
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
            else -> readCachedAlbumTitleMap()
        }
    }

    private fun readCachedPostDetail(): RemotePostDetail? {
        val userId = AuthSessionManager.getCurrentUserSnapshot()?.userId ?: return null
        return AppReadCacheStore.readPostDetail(userId = userId, postId = route.postId)?.payload
    }

    private fun writeCachedPostDetail(detail: RemotePostDetail) {
        val userId = AuthSessionManager.getCurrentUserSnapshot()?.userId ?: return
        AppReadCacheStore.writePostDetail(userId = userId, postId = detail.postId, detail = detail)
    }

    private fun readCachedAlbumTitleMap(): Map<String, String> {
        val userId = AuthSessionManager.getCurrentUserSnapshot()?.userId ?: return emptyMap()
        return AppReadCacheStore.readAlbumDirectory(userId)?.payload?.albums
            .orEmpty()
            .associate { it.albumId to it.title }
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
            comments = comments.filterNot { it.isDeleted }.map { it.toCommentUiModel(currentUserId) }.sortedByDescending { it.createdAtMillis },
            isLoading = false,
            isMutating = false,
            errorMessage = errorMessage,
            statusMessage = successMessage,
        )
    } else {
        RealCommentThreadUiState(
            comments = comments.filterNot { it.isDeleted }.map { it.toCommentUiModel(currentUserId) }.sortedByDescending { it.createdAtMillis },
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
