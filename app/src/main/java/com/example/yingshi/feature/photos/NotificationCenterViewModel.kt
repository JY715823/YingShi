package com.example.yingshi.feature.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yingshi.data.cache.AppReadCacheStore
import com.example.yingshi.data.cache.OfflineAccessManager
import com.example.yingshi.data.cache.OfflineReadOnlyDefaultMessage
import com.example.yingshi.data.model.RemoteNotification
import com.example.yingshi.data.model.RemoteNotificationMediaItem
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.components.YingShiNoticeTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// region Data classes

data class NotificationCenterUiState(
    val notifications: List<NotificationCenterItemUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null,
    val isOffline: Boolean = false,
    val isOfflineReadOnly: Boolean = false,
    val infoMessage: String? = null,
    val staleReason: String? = null,
    val isStale: Boolean = false,
    val userDisplayName: String? = null,
    val partnerDisplayName: String? = null,
    val partnerUserId: String? = null,
    val isEmpty: Boolean = true,
    val currentUserId: String? = null,
    val isLoadingInitial: Boolean = true,
    val isClearListConfirmVisible: Boolean = false,
    val notificationVersion: Long = 0L,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
) {
    val statusMessage: String? get() = infoMessage
}

data class NotificationResolvedPresentation(
    val title: String,
    val body: String,
    val targetSummary: String,
    val visual: NotificationVisual = NotificationVisual.None,
)

sealed interface NotificationVisual {
    data object None : NotificationVisual

    data class Media(
        val mediaSource: AppContentMediaSource?,
        val mediaType: AppMediaType,
        val palette: PhotoThumbnailPalette,
    ) : NotificationVisual

    data class SmallAlbum(
        val title: String,
        val metaLabel: String,
        val palette: PhotoThumbnailPalette,
        val previewMedia: List<AlbumPostPreviewMediaUiModel>,
    ) : NotificationVisual
}

// endregion

// region Notice event

data class NotificationCenterNotice(
    val message: String,
    val tone: YingShiNoticeTone = YingShiNoticeTone.INFO,
)

// endregion

class NotificationCenterViewModel : ViewModel() {

    // region Memory cache

    companion object {
        private val memoryUiStates = mutableMapOf<String, NotificationCenterUiState>()
        private val memoryPresentations = mutableMapOf<String, NotificationResolvedPresentation>()

        private const val PAGE_SIZE = 30

        fun readMemoryUiState(sessionKey: String): NotificationCenterUiState? = memoryUiStates[sessionKey]

        fun hasMemoryUiState(sessionKey: String): Boolean = memoryUiStates.containsKey(sessionKey)

        fun writeMemoryUiState(sessionKey: String, state: NotificationCenterUiState) {
            memoryUiStates[sessionKey] = state
        }

        fun readMemoryPresentation(notificationId: String): NotificationResolvedPresentation? =
            memoryPresentations[notificationId]

        fun writeMemoryPresentation(
            notificationId: String,
            presentation: NotificationResolvedPresentation,
        ) {
            memoryPresentations[notificationId] = presentation
        }
    }

    // endregion

    // region State flows

    private val _uiState = MutableStateFlow(NotificationCenterUiState(isLoading = true))
    val uiState: StateFlow<NotificationCenterUiState> = _uiState.asStateFlow()

    private val _selectedFilter = MutableStateFlow(NotificationCenterFilter.PHOTOS)
    val selectedFilter: StateFlow<NotificationCenterFilter> = _selectedFilter.asStateFlow()

    private val _selectedCategory = MutableStateFlow(NotificationCategoryFilter.ALL)
    val selectedCategory: StateFlow<NotificationCategoryFilter> = _selectedCategory.asStateFlow()

    private val _includeSelfActor = MutableStateFlow(false)
    val includeSelfActor: StateFlow<Boolean> = _includeSelfActor.asStateFlow()

    private val _includePartnerActor = MutableStateFlow(true)
    val includePartnerActor: StateFlow<Boolean> = _includePartnerActor.asStateFlow()

    private val _noticeEvent = MutableSharedFlow<NotificationCenterNotice>(extraBufferCapacity = 4)
    val noticeEvent: SharedFlow<NotificationCenterNotice> = _noticeEvent.asSharedFlow()

    // endregion

    // region Internal state

    private var sessionKey: String = ""
    private var currentUserId: String? = null
    private var refreshJob: Job? = null
    private var refreshVersion = 0
    private var currentCursor: String? = null
    private var hasMore: Boolean = true
    private var loadMoreJob: Job? = null

    // endregion

    // region Init

    fun init(sessionKey: String, source: String) {
        if (this.sessionKey == sessionKey) return
        this.sessionKey = sessionKey
        this.currentUserId = AuthSessionManager.getCurrentUserSnapshot()?.userId

        // Restore from memory cache
        val cachedMemoryState = readMemoryUiState(sessionKey)
        if (cachedMemoryState != null) {
            _uiState.value = cachedMemoryState
            return
        }

        // Load from disk cache
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            if (hasMemoryUiState(sessionKey)) return@launch
            val cachedRemoteNotifications = withContext(Dispatchers.IO) {
                AppReadCacheStore.readNotifications(userId)?.payload?.items
            }.orEmpty()
            if (cachedRemoteNotifications.isEmpty()) return@launch
            if (hasMemoryUiState(sessionKey)) return@launch
            val currentState = _uiState.value
            if (currentState.notifications.isNotEmpty() || currentState.errorMessage != null) return@launch
            updateUiState(
                cachedNotificationsUiState(
                    cachedList = cachedRemoteNotifications,
                    isOfflineReadOnly = OfflineAccessManager.state.isReadOnly,
                    statusMessage = if (OfflineAccessManager.state.isReadOnly) {
                        OfflineAccessManager.state.message ?: OfflineReadOnlyDefaultMessage
                    } else {
                        null
                    },
                ),
            )
        }
    }

    // endregion

    // region Refresh

    fun refresh() {
        refreshJob?.cancel()
        loadMoreJob?.cancel()
        val requestVersion = ++refreshVersion
        refreshJob = viewModelScope.launch {
            val userId = currentUserId
            val latestCachedRemoteNotifications = withContext(Dispatchers.IO) {
                userId?.let { AppReadCacheStore.readNotifications(it)?.payload?.items }
            }.orEmpty()
            updateUiState(
                _uiState.value.copy(
                    isLoading = _uiState.value.notifications.isEmpty(),
                    isRefreshing = _uiState.value.notifications.isNotEmpty(),
                    isOfflineReadOnly = false,
                    errorMessage = null,
                    infoMessage = null,
                ),
            )
            try {
                when (val result = RepositoryProvider.notificationRepository.getNotifications(
                    limit = PAGE_SIZE,
                    cursor = null,
                )) {
                    is ApiResult.Success -> {
                        if (requestVersion != refreshVersion) return@launch
                        if (userId != null) {
                            withContext(Dispatchers.IO) {
                                AppReadCacheStore.writeNotifications(
                                    userId = userId,
                                    notifications = result.data,
                                )
                            }
                        }
                        OfflineAccessManager.clear()
                        val remoteNotifications = result.data.map { it.toNotificationCenterItemUiModel() }
                        val hasMoreFlag = result.data.size >= PAGE_SIZE
                        val nextCursor = if (hasMoreFlag && remoteNotifications.isNotEmpty()) {
                            "${remoteNotifications.last().createdAtMillis}:${remoteNotifications.last().id}"
                        } else null
                        currentCursor = nextCursor
                        this@NotificationCenterViewModel.hasMore = hasMoreFlag
                        updateUiState(
                            NotificationCenterUiState(
                                isLoading = false,
                                isRefreshing = false,
                                isOfflineReadOnly = false,
                                notifications = mergeNotificationStreams(
                                    remoteNotifications = remoteNotifications,
                                    localNotifications = NotificationCenterLocalStore.getNotifications(),
                                ),
                                hasMore = hasMoreFlag,
                            ),
                        )
                    }

                    is ApiResult.Error -> {
                        if (requestVersion != refreshVersion) return@launch
                        if (latestCachedRemoteNotifications.isNotEmpty() &&
                            (OfflineAccessManager.state.isReadOnly || result.shouldFallbackToReadCache())
                        ) {
                            val message = result.offlineReadOnlyMessage()
                            OfflineAccessManager.enterReadOnly(message)
                            currentCursor = null
                            this@NotificationCenterViewModel.hasMore = false
                            updateUiState(
                                cachedNotificationsUiState(
                                    latestCachedRemoteNotifications,
                                    isOfflineReadOnly = true,
                                    statusMessage = message,
                                ).copy(hasMore = false),
                            )
                        } else {
                            updateUiState(
                                _uiState.value.copy(
                                    isLoading = false,
                                    isRefreshing = false,
                                    errorMessage = result.toBackendUiMessage("读取通知失败，请稍后重试。"),
                                ),
                            )
                        }
                    }

                    ApiResult.Loading -> Unit
                }
            } finally {
                // Always reset loading/refreshing state even if coroutine is cancelled
                if (refreshVersion == requestVersion) {
                    updateUiState(
                        _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                        ),
                    )
                    refreshJob = null
                }
            }
        }
    }

    fun handleConnectivityLost() {
        refreshJob?.cancel()
        refreshVersion += 1
        refreshJob = null
        viewModelScope.launch {
            val userId = currentUserId
            val latestCachedRemoteNotifications = withContext(Dispatchers.IO) {
                userId?.let { AppReadCacheStore.readNotifications(it)?.payload?.items }
            }.orEmpty()
            val message = "网络已断开，当前显示缓存内容，恢复连接后会自动刷新。"
            if (latestCachedRemoteNotifications.isNotEmpty()) {
                OfflineAccessManager.enterReadOnly(message)
                updateUiState(
                    cachedNotificationsUiState(
                        latestCachedRemoteNotifications,
                        isOfflineReadOnly = true,
                        statusMessage = message,
                    ),
                )
                return@launch
            }
            val currentState = _uiState.value
            val hasVisibleNotifications = currentState.notifications.isNotEmpty()
            if (hasVisibleNotifications) {
                OfflineAccessManager.enterReadOnly(message)
            }
            updateUiState(
                currentState.copy(
                    isLoading = false,
                    isMutating = false,
                    isOfflineReadOnly = hasVisibleNotifications,
                    errorMessage = if (hasVisibleNotifications) null else "当前无网络，恢复连接后会自动重试。",
                    infoMessage = if (hasVisibleNotifications) message else null,
                ),
            )
        }
    }

    // endregion

    // region Load more

    fun loadMore() {
        if (!hasMore || _uiState.value.isLoadingMore || _uiState.value.isRefreshing) return
        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            updateUiState(_uiState.value.copy(isLoadingMore = true))
            val cursor = currentCursor ?: run {
                updateUiState(_uiState.value.copy(isLoadingMore = false, hasMore = false))
                return@launch
            }
            try {
                when (val result = RepositoryProvider.notificationRepository.getNotifications(
                    limit = PAGE_SIZE,
                    cursor = cursor,
                )) {
                    is ApiResult.Success -> {
                        val newItems = result.data.map { it.toNotificationCenterItemUiModel() }
                        val hasMoreFlag = result.data.size >= PAGE_SIZE
                        val nextCursor = if (hasMoreFlag && newItems.isNotEmpty()) {
                            "${newItems.last().createdAtMillis}:${newItems.last().id}"
                        } else null
                        currentCursor = nextCursor
                        this@NotificationCenterViewModel.hasMore = hasMoreFlag
                        val merged = mergeNotificationStreams(
                            remoteNotifications = _uiState.value.notifications + newItems,
                            localNotifications = NotificationCenterLocalStore.getNotifications(),
                        )
                        updateUiState(
                            _uiState.value.copy(
                                notifications = merged,
                                isLoadingMore = false,
                                hasMore = hasMoreFlag,
                            ),
                        )
                        if (currentUserId != null) {
                            withContext(Dispatchers.IO) {
                                AppReadCacheStore.writeNotifications(
                                    userId = currentUserId!!,
                                    notifications = merged.map { it.toRemoteNotification() },
                                )
                            }
                        }
                    }

                    is ApiResult.Error -> {
                        updateUiState(
                            _uiState.value.copy(
                                isLoadingMore = false,
                                errorMessage = result.toBackendUiMessage("加载更多通知失败。"),
                            ),
                        )
                    }

                    ApiResult.Loading -> Unit
                }
            } finally {
                // Always reset isLoadingMore even if coroutine is cancelled
                if (_uiState.value.isLoadingMore) {
                    updateUiState(_uiState.value.copy(isLoadingMore = false))
                }
            }
        }
    }

    // endregion

    // region Mark read

    fun markRead(itemId: String) {
        if (_uiState.value.isOfflineReadOnly) return
        viewModelScope.launch {
            // Optimistic update: immediately mark as read in UI for instant feedback
            val optimisticNotifications = _uiState.value.notifications.map { item ->
                if (item.id == itemId) item.copy(isRead = true) else item
            }
            updateUiState(_uiState.value.copy(notifications = optimisticNotifications))

            if (NotificationCenterLocalStore.contains(itemId)) {
                NotificationCenterLocalStore.markRead(itemId)
                persistNotificationUiState(currentUserId, optimisticNotifications)
                return@launch
            }
            when (val result = RepositoryProvider.notificationRepository.markRead(itemId)) {
                is ApiResult.Success -> {
                    val updatedItem = result.data.toNotificationCenterItemUiModel()
                    val updatedNotifications = _uiState.value.notifications.map { item ->
                        if (item.id == updatedItem.id) updatedItem else item
                    }
                    updateUiState(_uiState.value.copy(notifications = updatedNotifications))
                    if (currentUserId != null) {
                        withContext(Dispatchers.IO) {
                            AppReadCacheStore.writeNotification(
                                userId = currentUserId!!,
                                notification = result.data,
                            )
                        }
                    }
                    persistNotificationUiState(currentUserId, updatedNotifications)
                }

                is ApiResult.Error -> {
                    // Rollback optimistic update: restore isRead to false
                    val rolledBack = _uiState.value.notifications.map { item ->
                        if (item.id == itemId) item.copy(isRead = false) else item
                    }
                    updateUiState(
                        _uiState.value.copy(
                            notifications = rolledBack,
                            errorMessage = result.toBackendUiMessage("标记通知已读失败。"),
                        ),
                    )
                }

                ApiResult.Loading -> { /* 保持乐观更新后的 isRead=true 状态 */ }
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            updateUiState(_uiState.value.copy(isMutating = true, errorMessage = null))
            when (val result = RepositoryProvider.notificationRepository.markAllRead()) {
                is ApiResult.Success -> {
                    NotificationCenterLocalStore.markAllRead()
                    val updatedState = _uiState.value.copy(
                        isMutating = false,
                        notifications = _uiState.value.notifications.map { item ->
                            if (item.isRead) item else item.copy(isRead = true)
                        },
                    )
                    updateUiState(updatedState)
                    persistNotificationUiState(currentUserId, updatedState.notifications)
                    emitNotice(
                        message = if (result.data.affectedCount > 0) {
                            "已全部标记为已读"
                        } else {
                            "当前没有新的未读通知"
                        },
                        tone = if (result.data.affectedCount > 0) {
                            YingShiNoticeTone.SUCCESS
                        } else {
                            YingShiNoticeTone.INFO
                        },
                    )
                }

                is ApiResult.Error -> {
                    updateUiState(
                        _uiState.value.copy(
                            isMutating = false,
                            errorMessage = result.toBackendUiMessage("全部标记已读失败，请稍后重试。"),
                        ),
                    )
                }

                ApiResult.Loading -> Unit
            }
        }
    }

    // endregion

    // region Clear list

    fun clearList(deleteIds: Set<String>) {
        if (deleteIds.isEmpty()) return
        NotificationCenterLocalStore.removeNotifications(deleteIds)
        updateUiState(
            _uiState.value.copy(
                notifications = _uiState.value.notifications.filterNot { it.id in deleteIds },
                errorMessage = null,
                infoMessage = null,
                isClearListConfirmVisible = false,
            ),
        )
        emitNotice(
            message = "已清空当前筛选结果",
            tone = YingShiNoticeTone.SUCCESS,
        )
    }

    // endregion

    // region Filter & actor toggles

    fun setFilter(filter: NotificationCenterFilter) {
        _selectedFilter.value = filter
    }

    fun setCategory(category: NotificationCategoryFilter) {
        _selectedCategory.value = category
    }

    fun toggleSelfActor() {
        _includeSelfActor.update { !it }
    }

    fun togglePartnerActor() {
        _includePartnerActor.update { !it }
    }

    // endregion

    // region Update notification item

    fun updateNotificationItem(updatedItem: NotificationCenterItemUiModel) {
        val updatedNotifications = _uiState.value.notifications.map { item ->
            if (item.id == updatedItem.id) updatedItem else item
        }
        updateUiState(_uiState.value.copy(notifications = updatedNotifications))
        viewModelScope.launch {
            persistNotificationUiState(currentUserId, updatedNotifications)
        }
    }

    // endregion

    // region Clear list confirm

    fun showClearListConfirm() {
        updateUiState(_uiState.value.copy(isClearListConfirmVisible = true))
    }

    fun dismissClearListConfirm() {
        updateUiState(_uiState.value.copy(isClearListConfirmVisible = false))
    }

    // endregion

    // region Internal helpers

    private fun updateUiState(nextState: NotificationCenterUiState) {
        _uiState.value = nextState
        if (!nextState.isLoading || nextState.notifications.isNotEmpty() || nextState.errorMessage != null) {
            writeMemoryUiState(sessionKey, nextState)
        }
    }

    private fun emitNotice(message: String, tone: YingShiNoticeTone = YingShiNoticeTone.INFO) {
        _noticeEvent.tryEmit(NotificationCenterNotice(message = message, tone = tone))
    }

    private fun cachedNotificationsUiState(
        cachedList: List<RemoteNotification>,
        isOfflineReadOnly: Boolean = false,
        statusMessage: String? = null,
    ): NotificationCenterUiState {
        return NotificationCenterUiState(
            isLoading = false,
            isOfflineReadOnly = isOfflineReadOnly,
            infoMessage = statusMessage,
            notifications = mergeNotificationStreams(
                remoteNotifications = cachedList.map { it.toNotificationCenterItemUiModel() },
                localNotifications = NotificationCenterLocalStore.getNotifications(),
            ),
        )
    }

    // endregion
}

// region Extension functions (moved from Screen)

private fun mergeNotificationStreams(
    remoteNotifications: List<NotificationCenterItemUiModel>,
    localNotifications: List<NotificationCenterItemUiModel>,
): List<NotificationCenterItemUiModel> {
    return (localNotifications + remoteNotifications)
        .distinctBy(NotificationCenterItemUiModel::id)
        .sortedByDescending(NotificationCenterItemUiModel::createdAtMillis)
}

private suspend fun persistNotificationUiState(
    userId: String?,
    notifications: List<NotificationCenterItemUiModel>,
) {
    if (userId.isNullOrBlank()) return
    withContext(Dispatchers.IO) {
        AppReadCacheStore.writeNotifications(
            userId = userId,
            notifications = notifications.map { it.toRemoteNotification() },
        )
    }
}

private fun NotificationCenterItemUiModel.toRemoteNotification(): RemoteNotification {
    return RemoteNotification(
        notificationId = id,
        type = type.apiValue,
        module = module,
        category = category,
        title = title,
        body = body,
        createdAtMillis = createdAtMillis,
        isRead = isRead,
        actorUserId = actorUserId,
        actorDisplayName = actorDisplayName,
        actorAvatarUrl = actorAvatarUrl,
        actorIsCurrentUser = actorIsCurrentUser,
        groupId = groupId,
        operationId = operationId,
        groupItemCount = groupItemCount,
        mediaItems = mediaItems.map { it.toRemoteNotificationMediaItem() },
        targetRoute = targetRoute,
        targetSummary = targetSummary,
        targetType = targetType,
        smallAlbumId = postId,
        mediaId = mediaId,
        trashItemId = trashItemId,
    )
}

private fun NotificationCenterMediaItemUiModel.toRemoteNotificationMediaItem(): RemoteNotificationMediaItem {
    return RemoteNotificationMediaItem(
        mediaId = mediaId,
        mediaType = mediaType.name.lowercase(Locale.US),
        mimeType = mimeType,
        previewUrl = mediaSource?.thumbnailUrl,
        thumbnailUrl = mediaSource?.thumbnailUrl,
        coverUrl = mediaSource?.coverUrl,
        mediaUrl = mediaSource?.mediaUrl ?: mediaSource?.originalUrl,
        videoUrl = mediaSource?.videoUrl,
        displayTimeMillis = displayTimeMillis,
        durationMillis = durationMillis,
    )
}

// endregion