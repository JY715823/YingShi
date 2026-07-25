package com.example.yingshi.feature.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yingshi.data.cache.AppReadCacheStore
import com.example.yingshi.data.cache.CachedPhotoFeed
import com.example.yingshi.data.cache.OfflineAccessManager
import com.example.yingshi.data.cache.OfflineReadOnlyDefaultMessage
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.auth.BackendSessionProbe
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.MediaRepository
import com.example.yingshi.data.repository.RepositoryProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RealPhotoFeedUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isSilentlyRefreshing: Boolean = false,
    val loadMoreErrorMessage: String? = null,
    val isDeleting: Boolean = false,
    val isOfflineReadOnly: Boolean = false,
    val tokenMissing: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val feedItems: List<PhotoFeedItem> = emptyList(),
)

class RealPhotoFeedViewModel(
    private val mediaRepository: MediaRepository = RepositoryProvider.mediaRepository,
) : ViewModel() {
    private val pageSize = 60
    private var nextCursor: String? = null
    private var loadMoreInFlight = false
    private var loadMoreBlockedByError = false
    private var refreshJob: Job? = null
    private var refreshCompletion: CompletableDeferred<Boolean>? = null
    private var refreshVersion = 0
    private val _uiState = MutableStateFlow(RealPhotoFeedUiState(isLoading = true))
    val uiState: StateFlow<RealPhotoFeedUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        startRefresh()
    }

    suspend fun refreshAndAwait(): Boolean {
        return startRefresh().await()
    }

    private fun startRefresh(): CompletableDeferred<Boolean> {
        refreshJob?.cancel()
        refreshCompletion?.complete(false)
        val completion = CompletableDeferred<Boolean>()
        refreshCompletion = completion
        val requestVersion = ++refreshVersion
        refreshJob = viewModelScope.launch {
            val cachedFeed = withContext(Dispatchers.IO) { readCachedFeed() }
            if (cachedFeed != null && _uiState.value.feedItems.isEmpty()) {
                applyCachedFeed(cachedFeed)
            }
            if (!AuthSessionManager.isLoggedIn) {
                if (cachedFeed != null && OfflineAccessManager.state.isReadOnly) {
                    applyCachedFeed(
                        cachedFeed = cachedFeed,
                        statusMessage = OfflineAccessManager.state.message ?: OfflineReadOnlyDefaultMessage,
                        isOfflineReadOnly = true,
                    )
                    completion.complete(false)
                    return@launch
                }
                val loginOutcome = BackendSessionProbe.probeSessionState(
                    force = false,
                    reason = "real_photo_feed_refresh",
                )
                if (!loginOutcome.success) {
                    if (requestVersion != refreshVersion) return@launch
                    _uiState.value = RealPhotoFeedUiState(
                        tokenMissing = true,
                        errorMessage = loginOutcome.message.ifBlank {
                            "需要先完成登录，请检查连接设置后重试。"
                        },
                    )
                    completion.complete(false)
                    return@launch
                }
            }

            val minLoadedItemCount = _uiState.value.feedItems.size.coerceAtLeast(pageSize)
            val showBlockingLoading = _uiState.value.feedItems.isEmpty()
            nextCursor = null
            loadMoreInFlight = false
            loadMoreBlockedByError = false
            _uiState.update {
                val nextOfflineReadOnly = if (showBlockingLoading) false else it.isOfflineReadOnly
                val nextHasMore = if (showBlockingLoading) false else it.hasMore
                val nextStatusMessage = if (showBlockingLoading) null else it.statusMessage
                it.copy(
                    isLoading = showBlockingLoading,
                    isSilentlyRefreshing = !showBlockingLoading,
                    isLoadingMore = false,
                    loadMoreErrorMessage = null,
                    isOfflineReadOnly = nextOfflineReadOnly,
                    tokenMissing = false,
                    hasMore = nextHasMore,
                    errorMessage = null,
                    statusMessage = nextStatusMessage,
                )
            }
            when (val result = loadRefreshPages(minLoadedItemCount = minLoadedItemCount)) {
                is ApiResult.Success -> {
                    if (requestVersion != refreshVersion) return@launch
                    nextCursor = result.data.nextCursor
                    withContext(Dispatchers.IO) {
                        persistFeed(result.data)
                    }
                    OfflineAccessManager.clear()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSilentlyRefreshing = false,
                            isOfflineReadOnly = false,
                            errorMessage = null,
                            statusMessage = null,
                            feedItems = result.data.items.map { item -> item.toPhotoFeedItem() },
                            hasMore = result.data.hasMore,
                            loadMoreErrorMessage = null,
                        )
                    }
                    completion.complete(true)
                }
                is ApiResult.Error -> {
                    if (requestVersion != refreshVersion) return@launch
                    if (cachedFeed != null && (OfflineAccessManager.state.isReadOnly || result.shouldFallbackToReadCache())) {
                        val message = result.offlineReadOnlyMessage()
                        OfflineAccessManager.enterReadOnly(message)
                        applyCachedFeed(
                            cachedFeed = cachedFeed,
                            statusMessage = message,
                            isOfflineReadOnly = true,
                        )
                        completion.complete(false)
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isSilentlyRefreshing = false,
                                isOfflineReadOnly = false,
                                errorMessage = result.toBackendUiMessage("读取照片流失败。"),
                            )
                        }
                        completion.complete(false)
                    }
                }
                ApiResult.Loading -> {
                    _uiState.update { it.copy(isSilentlyRefreshing = false) }
                    completion.complete(false)
                }
            }
            if (refreshVersion == requestVersion) {
                refreshJob = null
                refreshCompletion = null
            }
        }
        return completion
    }

    fun handleConnectivityLost() {
        refreshJob?.cancel()
        refreshVersion += 1
        refreshJob = null
        viewModelScope.launch {
            val cachedFeed = withContext(Dispatchers.IO) { readCachedFeed() }
            val message = "网络已断开，当前显示缓存内容，恢复连接后会自动刷新。"
            if (cachedFeed != null) {
                OfflineAccessManager.enterReadOnly(message)
                applyCachedFeed(
                    cachedFeed = cachedFeed,
                    statusMessage = message,
                    isOfflineReadOnly = true,
                )
                return@launch
            }
            val hasVisibleItems = _uiState.value.feedItems.isNotEmpty()
            if (hasVisibleItems) {
                OfflineAccessManager.enterReadOnly(message)
            }
            nextCursor = null
            loadMoreInFlight = false
            loadMoreBlockedByError = false
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    isSilentlyRefreshing = false,
                    loadMoreErrorMessage = null,
                    isOfflineReadOnly = hasVisibleItems,
                    tokenMissing = false,
                    hasMore = false,
                    errorMessage = if (hasVisibleItems) null else "当前无网络，恢复连接后会自动重试。",
                    statusMessage = if (hasVisibleItems) message else null,
                )
            }
        }
    }

    fun loadNextPage() {
        val cursor = nextCursor ?: return
        if (_uiState.value.isOfflineReadOnly) return
        if (loadMoreInFlight || _uiState.value.isLoading || _uiState.value.isLoadingMore) return
        if (loadMoreBlockedByError) return
        if (!AuthSessionManager.isLoggedIn) return

        loadMoreInFlight = true
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingMore = true,
                    loadMoreErrorMessage = null,
                    errorMessage = null,
                    statusMessage = null,
                )
            }
            when (val result = mediaRepository.getMediaFeedPage(cursor = cursor, pageSize = pageSize)) {
                is ApiResult.Success -> {
                    loadMoreBlockedByError = false
                    nextCursor = result.data.nextCursor
                    val currentState = _uiState.value
                    val nextItems = result.data.items.map { it.toPhotoFeedItem() }
                    val nextState = currentState.copy(
                        isLoadingMore = false,
                        loadMoreErrorMessage = null,
                        hasMore = result.data.hasMore,
                        feedItems = (currentState.feedItems + nextItems).distinctBy { it.mediaId },
                    )
                    withContext(Dispatchers.IO) {
                        persistFeed(
                            RefreshPageBundle(
                                items = nextState.feedItems.map(PhotoFeedItem::toCachedRemoteMedia),
                                nextCursor = result.data.nextCursor,
                                hasMore = result.data.hasMore,
                            ),
                        )
                    }
                    _uiState.value = nextState
                }
                is ApiResult.Error -> {
                    loadMoreBlockedByError = true
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            loadMoreErrorMessage = result.toBackendUiMessage("加载更多照片失败。"),
                        )
                    }
                }
                ApiResult.Loading -> Unit
            }
            loadMoreInFlight = false
        }
    }

    private suspend fun loadRefreshPages(minLoadedItemCount: Int): ApiResult<RefreshPageBundle> {
        val firstPage = when (val result = mediaRepository.getMediaFeedPage(pageSize = pageSize)) {
            is ApiResult.Success -> result.data
            is ApiResult.Error -> return ApiResult.Error(
                code = result.code,
                message = result.message,
                throwable = result.throwable,
            )
            ApiResult.Loading -> return ApiResult.Loading
        }

        val items = firstPage.items.toMutableList()
        var cursor = firstPage.nextCursor
        var hasMore = firstPage.hasMore
        while (hasMore && cursor != null && items.size < minLoadedItemCount) {
            when (val result = mediaRepository.getMediaFeedPage(cursor = cursor, pageSize = pageSize)) {
                is ApiResult.Success -> {
                    val page = result.data
                    items += page.items
                    cursor = page.nextCursor
                    hasMore = page.hasMore
                }
                is ApiResult.Error -> return ApiResult.Error(
                    code = result.code,
                    message = result.message,
                    throwable = result.throwable,
                )
                ApiResult.Loading -> return ApiResult.Loading
            }
        }

        return ApiResult.Success(
            RefreshPageBundle(
                items = items.distinctBy { it.mediaId },
                nextCursor = cursor,
                hasMore = hasMore,
            ),
        )
    }

    fun retryLoadNextPage() {
        loadMoreBlockedByError = false
        _uiState.update {
            it.copy(loadMoreErrorMessage = null)
        }
        loadNextPage()
    }

    fun clearStatusMessage(expectedMessage: String? = null) {
        _uiState.update { state ->
            if (expectedMessage != null && state.statusMessage != expectedMessage) {
                state
            } else if (state.statusMessage == null) {
                state
            } else {
                state.copy(statusMessage = null)
            }
        }
    }

    fun deleteSelectedMedia(
        mediaIds: Set<String>,
        onCompleted: (deletedIds: Set<String>) -> Unit = {},
    ) {
        val normalizedIds = mediaIds.toList().distinct()
        if (normalizedIds.isEmpty()) return
        if (_uiState.value.isOfflineReadOnly) {
            _uiState.update {
                it.copy(errorMessage = "缓存只读模式下不能删除媒体。")
            }
            return
        }
        if (!AuthSessionManager.isLoggedIn) {
            _uiState.update {
                it.copy(errorMessage = "登录状态缺失，请重新登录。")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDeleting = true,
                    errorMessage = null,
                    statusMessage = null,
                )
            }
            val deletedIds = linkedSetOf<String>()
            var firstFailure: String? = null
            val fallbackActorUserId = currentCollaboratorActorUserId()

            normalizedIds.forEach { mediaId ->
                when (val result = mediaRepository.systemDeleteMedia(mediaId)) {
                    is ApiResult.Success -> {
                        deletedIds += mediaId
                        TrashActorHintStore.record(
                            item = result.data,
                            fallbackActorUserId = fallbackActorUserId,
                        )
                    }
                    is ApiResult.Error -> {
                        if (firstFailure == null) {
                            firstFailure = result.toBackendUiMessage("删除媒体失败。")
                        }
                    }
                    ApiResult.Loading -> Unit
                }
            }

            _uiState.update {
                it.copy(
                    isDeleting = false,
                    errorMessage = firstFailure,
                    feedItems = if (deletedIds.isEmpty()) {
                        it.feedItems
                    } else {
                        it.feedItems.filterNot { item -> deletedIds.contains(item.mediaId) }
                    },
                    statusMessage = when {
                        deletedIds.isNotEmpty() && firstFailure == null ->
                            "已删除 ${deletedIds.size} 项媒体，并写入回收站。"
                        deletedIds.isNotEmpty() ->
                            "已删除 ${deletedIds.size} 项媒体，但仍有部分失败。"
                        else -> null
                    },
                )
            }
            if (deletedIds.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    persistCurrentFeedSnapshot()
                    deletedIds.forEach { mediaId ->
                        LocalSystemMediaBridgeRepository.forgetImportStatusByAppMediaId(mediaId)
                    }
                    invalidateSystemMediaMetadataCache(clearDisk = true)
                }
                notifyRealBackendContentChangedWithoutPhotoFeed(mediaIds = deletedIds)
            }
            onCompleted(deletedIds)
        }
    }

    private fun persistCurrentFeedSnapshot() {
        val currentState = _uiState.value
        persistFeed(
            RefreshPageBundle(
                items = currentState.feedItems.map(PhotoFeedItem::toCachedRemoteMedia),
                nextCursor = nextCursor,
                hasMore = currentState.hasMore,
            ),
        )
    }

    private fun readCachedFeed(): CachedPhotoFeed? {
        val userId = AuthSessionManager.getCurrentUserSnapshot()?.userId ?: return null
        return AppReadCacheStore.readPhotoFeed(userId)?.payload
    }

    private fun persistFeed(bundle: RefreshPageBundle) {
        val userId = AuthSessionManager.getCurrentUserSnapshot()?.userId ?: return
        AppReadCacheStore.writePhotoFeed(
            userId = userId,
            payload = CachedPhotoFeed(
                items = bundle.items,
                nextCursor = bundle.nextCursor,
                hasMore = bundle.hasMore,
            ),
        )
    }

    private fun applyCachedFeed(
        cachedFeed: CachedPhotoFeed,
        statusMessage: String? = null,
        isOfflineReadOnly: Boolean = false,
    ) {
        nextCursor = if (isOfflineReadOnly) null else cachedFeed.nextCursor
        _uiState.update {
            it.copy(
                isLoading = false,
                isLoadingMore = false,
                isSilentlyRefreshing = false,
                isOfflineReadOnly = isOfflineReadOnly,
                tokenMissing = false,
                hasMore = if (isOfflineReadOnly) false else cachedFeed.hasMore,
                errorMessage = null,
                statusMessage = statusMessage,
                feedItems = cachedFeed.items.map(RemoteMedia::toPhotoFeedItem),
            )
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RealPhotoFeedViewModel() as T
                }
            }
        }
    }
}

private data class RefreshPageBundle(
    val items: List<RemoteMedia>,
    val nextCursor: String?,
    val hasMore: Boolean,
)

private fun PhotoFeedItem.toCachedRemoteMedia(): RemoteMedia {
    val source = mediaSource
    return RemoteMedia(
        mediaId = mediaId,
        mediaType = mediaType.name,
        previewUrl = source?.thumbnailUrl,
        originalUrl = source?.originalUrl,
        videoUrl = source?.videoUrl,
        width = width,
        height = height,
        aspectRatio = aspectRatio,
        displayTimeMillis = mediaDisplayTimeMillis,
        commentCount = commentCount,
        smallAlbumIds = smallAlbumIds,
        thumbnailUrl = source?.thumbnailUrl,
        mediaUrl = source?.mediaUrl,
        coverUrl = source?.coverUrl,
        mimeType = source?.mimeType,
        durationMillis = videoDurationMillis ?: source?.durationMillis,
        createdAtMillis = source?.createdAtMillis ?: mediaDisplayTimeMillis,
        capturedAtMillis = capturedAtMillis,
        importedAtMillis = importedAtMillis,
        displayTimeSource = displayTimeSource,
        uploadedByUserId = uploadedByUserId,
    )
}
