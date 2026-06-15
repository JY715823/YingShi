package com.example.yingshi.feature.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yingshi.data.cache.AppReadCacheStore
import com.example.yingshi.data.cache.CachedTrashList
import com.example.yingshi.data.cache.OfflineAccessManager
import com.example.yingshi.data.cache.OfflineReadOnlyDefaultMessage
import com.example.yingshi.data.model.RemoteTrashDetail
import com.example.yingshi.data.model.RemoteTrashItem
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.auth.BackendAutoLoginManager
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.data.repository.TrashRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RealTrashListUiState(
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val isOfflineReadOnly: Boolean = false,
    val tokenMissing: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val entries: List<TrashEntryUiModel> = emptyList(),
    val pendingEntries: List<TrashPendingCleanupUiModel> = emptyList(),
)

data class RealTrashDetailUiState(
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val isOfflineReadOnly: Boolean = false,
    val tokenMissing: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val detail: RemoteTrashDetail? = null,
)

private object RealTrashMemoryCache {
    private val listStates = mutableMapOf<String, RealTrashListUiState>()
    private val detailStates = mutableMapOf<String, RealTrashDetailUiState>()

    fun listKey(selectedType: TrashEntryType?): String = selectedType?.name ?: "ALL"

    fun readListState(selectedType: TrashEntryType?): RealTrashListUiState? = listStates[listKey(selectedType)]

    fun hasListState(selectedType: TrashEntryType?): Boolean = listStates.containsKey(listKey(selectedType))

    fun writeListState(
        selectedType: TrashEntryType?,
        state: RealTrashListUiState,
    ) {
        listStates[listKey(selectedType)] = state.copy(isLoading = false, isMutating = false)
    }

    fun readDetailState(entryId: String): RealTrashDetailUiState? = detailStates[entryId]

    fun writeDetailState(
        entryId: String,
        state: RealTrashDetailUiState,
    ) {
        detailStates[entryId] = state.copy(isLoading = false, isMutating = false)
    }
}

class RealTrashListViewModel(
    initialSelectedType: TrashEntryType? = null,
    private val trashRepository: TrashRepository = RepositoryProvider.trashRepository,
) : ViewModel() {
    private var activeListKey = RealTrashMemoryCache.listKey(initialSelectedType)
    private val _uiState = MutableStateFlow(
        RealTrashMemoryCache.readListState(initialSelectedType) ?: RealTrashListUiState(isLoading = true),
    )
    val uiState: StateFlow<RealTrashListUiState> = _uiState.asStateFlow()

    fun refresh(selectedType: TrashEntryType?) {
        viewModelScope.launch {
            val nextListKey = RealTrashMemoryCache.listKey(selectedType)
            val cachedState = RealTrashMemoryCache.readListState(selectedType)
            val cachedList = withContext(Dispatchers.IO) { readCachedList(selectedType) }
            val hasCachedState = RealTrashMemoryCache.hasListState(selectedType)
            if (nextListKey != activeListKey) {
                activeListKey = nextListKey
                _uiState.value = cachedState
                    ?: cachedList?.toUiState()
                    ?: RealTrashListUiState(isLoading = true)
            } else if (cachedState != null && _uiState.value.entries.isEmpty()) {
                _uiState.value = cachedState
            } else if (cachedState == null && cachedList != null && _uiState.value.entries.isEmpty()) {
                _uiState.value = cachedList.toUiState()
            }
            if (!AuthSessionManager.isLoggedIn) {
                if (cachedList != null && OfflineAccessManager.state.isReadOnly) {
                    _uiState.value = cachedList.toUiState(
                        isOfflineReadOnly = true,
                        statusMessage = OfflineAccessManager.state.message ?: OfflineReadOnlyDefaultMessage,
                    )
                    return@launch
                }
                val loginOutcome = BackendAutoLoginManager.loginDefault(
                    force = false,
                    reason = "real_trash_list_refresh",
                )
                if (!loginOutcome.success) {
                    _uiState.value = RealTrashListUiState(
                        tokenMissing = true,
                        errorMessage = loginOutcome.message.ifBlank {
                            "需要先完成登录，请检查连接设置后重试。"
                        },
                    )
                    return@launch
                }
            }

            val shouldShowLoading = _uiState.value.entries.isEmpty() && !hasCachedState && cachedList == null
            _uiState.update {
                it.copy(
                    isLoading = shouldShowLoading,
                    isOfflineReadOnly = false,
                    tokenMissing = false,
                    errorMessage = null,
                )
            }
            val itemsDeferred = async {
                trashRepository.getTrashItems(selectedType?.toApiItemType())
            }

            val itemsResult = itemsDeferred.await()

            val rawError = itemsResult as? ApiResult.Error
            val itemError = rawError?.toBackendUiMessage("读取回收站列表失败。")
            val successItems = (itemsResult as? ApiResult.Success)?.data.orEmpty()
            if (successItems.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    persistTrashList(selectedType, successItems)
                }
                OfflineAccessManager.clear()
            }
            if (rawError != null) {
                if (cachedList != null && (OfflineAccessManager.state.isReadOnly || rawError.shouldFallbackToReadCache())) {
                    val message = rawError.offlineReadOnlyMessage()
                    OfflineAccessManager.enterReadOnly(message)
                    _uiState.value = cachedList.toUiState(
                        isOfflineReadOnly = true,
                        statusMessage = message,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isOfflineReadOnly = false,
                        tokenMissing = false,
                        errorMessage = itemError,
                    )
                }
                return@launch
            }
            successItems.forEach(TrashActorHintStore::record)

            val deduplicatedEntries = successItems
                .map { it.toTrashEntryUiModel() }
                .distinctBy { it.businessIdentityKey() }

            val nextState = RealTrashListUiState(
                isLoading = false,
                isOfflineReadOnly = false,
                errorMessage = itemError,
                entries = deduplicatedEntries,
                pendingEntries = emptyList(),
                statusMessage = _uiState.value.statusMessage,
            )
            _uiState.value = nextState
            RealTrashMemoryCache.writeListState(selectedType, nextState)
        }
    }

    fun undoPendingCleanup(trashItemId: String, selectedType: TrashEntryType?) {
        if (_uiState.value.isOfflineReadOnly) {
            _uiState.update { it.copy(errorMessage = "缓存只读模式下不能撤销回收站操作。") }
            return
        }
        viewModelScope.launch {
            when (val result = trashRepository.undoMoveTrashItemOut(trashItemId)) {
                is ApiResult.Success -> {
                    notifyRealBackendContentChanged()
                    refresh(selectedType)
                    _uiState.update { it.copy(statusMessage = "已撤销移出回收站。") }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(errorMessage = result.toBackendUiMessage("撤销失败。"))
                    }
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun showSelectionMessage(message: String) {
        _uiState.update {
            it.copy(
                statusMessage = message,
                errorMessage = null,
            )
        }
    }

    fun restoreEntries(
        entries: List<TrashEntryUiModel>,
        selectedType: TrashEntryType?,
        onFirstRestoredMediaIds: (List<String>) -> Unit,
    ) {
        if (entries.isEmpty()) return
        if (_uiState.value.isOfflineReadOnly) {
            _uiState.update { it.copy(errorMessage = "缓存只读模式下不能恢复回收站内容。") }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isMutating = true,
                    errorMessage = null,
                    statusMessage = null,
                )
            }
            var successCount = 0
            var failureCount = 0
            var firstRestoredMediaIds: List<String> = emptyList()
            entries.forEach { entry ->
                when (val result = trashRepository.restoreTrashItem(entry.id)) {
                    is ApiResult.Success -> {
                        successCount += 1
                        if (firstRestoredMediaIds.isEmpty()) {
                            firstRestoredMediaIds = result.data.toTrashEntryUiModel().restoreTargetMediaIds()
                                .ifEmpty { entry.restoreTargetMediaIds() }
                        }
                    }
                    is ApiResult.Error -> {
                        failureCount += 1
                    }
                    ApiResult.Loading -> Unit
                }
            }
            if (successCount > 0) {
                notifyRealBackendContentChanged()
            }
            refresh(selectedType)
            _uiState.update {
                it.copy(
                    isMutating = false,
                    statusMessage = when {
                        successCount > 0 && failureCount > 0 -> "批量恢复完成：成功 $successCount 项，失败 $failureCount 项。失败项已保留。"
                        successCount > 0 -> "已恢复 $successCount 项。"
                        else -> null
                    },
                    errorMessage = if (successCount == 0 && failureCount > 0) {
                        "批量恢复失败，回收站条目已保留。"
                    } else {
                        it.errorMessage
                    },
                )
            }
            if (firstRestoredMediaIds.isNotEmpty()) {
                onFirstRestoredMediaIds(firstRestoredMediaIds)
            }
        }
    }

    fun purgeEntries(
        entries: List<TrashEntryUiModel>,
        selectedType: TrashEntryType?,
    ) {
        if (entries.isEmpty()) return
        if (_uiState.value.isOfflineReadOnly) {
            _uiState.update { it.copy(errorMessage = "缓存只读模式下不能删除回收站内容。") }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isMutating = true,
                    errorMessage = null,
                    statusMessage = null,
                )
            }
            var successCount = 0
            var failureCount = 0
            entries.forEach { entry ->
                when (trashRepository.purgeTrashItem(entry.id)) {
                    is ApiResult.Success -> {
                        successCount += 1
                    }
                    is ApiResult.Error -> {
                        failureCount += 1
                    }
                    ApiResult.Loading -> Unit
                }
            }
            if (successCount > 0) {
                notifyRealBackendContentChanged()
            }
            refresh(selectedType)
            _uiState.update {
                it.copy(
                    isMutating = false,
                    statusMessage = when {
                        successCount > 0 && failureCount > 0 -> "清空当前分类完成：成功 $successCount 项，失败 $failureCount 项。失败项已保留。"
                        successCount > 0 -> "已清空当前分类 $successCount 项。"
                        else -> null
                    },
                    errorMessage = if (successCount == 0 && failureCount > 0) {
                        "清空当前分类失败，条目已保留。"
                    } else {
                        it.errorMessage
                    },
                )
            }
        }
    }

    companion object {
        fun factory(initialSelectedType: TrashEntryType? = null): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RealTrashListViewModel(initialSelectedType = initialSelectedType) as T
                }
            }
        }
    }
}

class RealTrashDetailViewModel(
    private val route: TrashDetailRoute,
    private val trashRepository: TrashRepository = RepositoryProvider.trashRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        RealTrashMemoryCache.readDetailState(route.entryId) ?: RealTrashDetailUiState(isLoading = true),
    )
    val uiState: StateFlow<RealTrashDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val cachedDetail = withContext(Dispatchers.IO) { readCachedDetail(route.entryId) }
            if (!AuthSessionManager.isLoggedIn) {
                if (cachedDetail != null && OfflineAccessManager.state.isReadOnly) {
                    _uiState.value = cachedDetail.toUiState(
                        isOfflineReadOnly = true,
                        statusMessage = OfflineAccessManager.state.message ?: OfflineReadOnlyDefaultMessage,
                    )
                    return@launch
                }
                val loginOutcome = BackendAutoLoginManager.loginDefault(
                    force = false,
                    reason = "real_trash_detail_refresh",
                )
                if (!loginOutcome.success) {
                    _uiState.value = RealTrashDetailUiState(
                        tokenMissing = true,
                        errorMessage = loginOutcome.message.ifBlank {
                            "需要先完成登录，请检查连接设置后重试。"
                        },
                    )
                    return@launch
                }
            }

            val shouldShowLoading = _uiState.value.detail == null
            _uiState.update {
                it.copy(
                    isLoading = shouldShowLoading,
                    isOfflineReadOnly = false,
                    tokenMissing = false,
                    errorMessage = null,
                )
            }
            when (val result = trashRepository.getTrashDetail(route.entryId)) {
                is ApiResult.Success -> {
                    TrashActorHintStore.record(result.data.item)
                    val nextState = _uiState.value.copy(
                        isLoading = false,
                        isOfflineReadOnly = false,
                        detail = result.data,
                    )
                    _uiState.value = nextState
                    RealTrashMemoryCache.writeDetailState(route.entryId, nextState)
                    withContext(Dispatchers.IO) {
                        persistTrashDetail(route.entryId, result.data)
                    }
                    OfflineAccessManager.clear()
                }
                is ApiResult.Error -> {
                    if (cachedDetail != null && (OfflineAccessManager.state.isReadOnly || result.shouldFallbackToReadCache())) {
                        val message = result.offlineReadOnlyMessage()
                        OfflineAccessManager.enterReadOnly(message)
                        _uiState.value = cachedDetail.toUiState(
                            isOfflineReadOnly = true,
                            statusMessage = message,
                        )
                    } else if (_uiState.value.detail != null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = result.toBackendUiMessage("读取回收站详情失败。"),
                            )
                        }
                    } else {
                        _uiState.value = RealTrashDetailUiState(
                            isLoading = false,
                            errorMessage = result.toBackendUiMessage("读取回收站详情失败。"),
                        )
                    }
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun restore(onSuccess: (RemoteTrashItem) -> Unit) {
        if (_uiState.value.isOfflineReadOnly) {
            _uiState.update { it.copy(errorMessage = "缓存只读模式下不能恢复回收站内容。") }
            return
        }
        mutateTrashItem("已恢复到正常列表。", onSuccess) {
            trashRepository.restoreTrashItem(route.entryId)
        }
    }

    fun restoreItem(trashItemId: String, onSuccess: (RemoteTrashItem) -> Unit) {
        if (_uiState.value.isOfflineReadOnly) {
            _uiState.update { it.copy(errorMessage = "缓存只读模式下不能恢复回收站内容。") }
            return
        }
        mutateTrashItem("已恢复到正常列表。", onSuccess) {
            trashRepository.restoreTrashItem(trashItemId)
        }
    }

    fun remove(onSuccess: () -> Unit) {
        if (_uiState.value.isOfflineReadOnly) {
            _uiState.update { it.copy(errorMessage = "缓存只读模式下不能删除回收站内容。") }
            return
        }
        mutateTrashItem("已永久删除该回收站项目。", { onSuccess() }) {
            trashRepository.purgeTrashItem(route.entryId)
        }
    }

    fun removeItem(trashItemId: String, onSuccess: () -> Unit) {
        if (_uiState.value.isOfflineReadOnly) {
            _uiState.update { it.copy(errorMessage = "缓存只读模式下不能删除回收站内容。") }
            return
        }
        mutateTrashItem("已永久删除该回收站项目。", { onSuccess() }) {
            trashRepository.purgeTrashItem(trashItemId)
        }
    }

    fun undoRemove() {
        if (_uiState.value.isOfflineReadOnly) {
            _uiState.update { it.copy(errorMessage = "缓存只读模式下不能撤销回收站操作。") }
            return
        }
        mutate("已撤销移出回收站。") {
            trashRepository.undoMoveTrashItemOut(route.entryId)
        }
    }

    private fun mutate(
        successMessage: String,
        onSuccess: (() -> Unit)? = null,
        block: suspend () -> ApiResult<*>,
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isMutating = true,
                    errorMessage = null,
                )
            }
            when (val result = block()) {
                is ApiResult.Success -> {
                    notifyRealBackendContentChanged()
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            statusMessage = successMessage,
                        )
                    }
                    if (onSuccess != null) {
                        onSuccess()
                    } else {
                        refresh()
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = result.toBackendUiMessage("回收站操作失败。"),
                        )
                    }
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    private fun mutateTrashItem(
        successMessage: String,
        onSuccess: ((RemoteTrashItem) -> Unit)? = null,
        block: suspend () -> ApiResult<RemoteTrashItem>,
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isMutating = true,
                    errorMessage = null,
                )
            }
            when (val result = block()) {
                is ApiResult.Success -> {
                    notifyRealBackendContentChanged()
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            statusMessage = successMessage,
                        )
                    }
                    if (onSuccess != null) {
                        onSuccess(result.data)
                    } else {
                        refresh()
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = result.toBackendUiMessage("回收站操作失败。"),
                        )
                    }
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    companion object {
        fun factory(route: TrashDetailRoute): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RealTrashDetailViewModel(route = route) as T
                }
            }
        }
    }
}

private fun TrashEntryType.toApiItemType(): String {
    return when (this) {
        TrashEntryType.LARGE_ALBUM_DELETED -> "largeAlbumDeleted"
        TrashEntryType.SMALL_ALBUM_DELETED -> "smallAlbumDeleted"
        TrashEntryType.MEDIA_REMOVED -> "mediaRemoved"
        TrashEntryType.MEDIA_SYSTEM_DELETED -> "mediaSystemDeleted"
    }
}

private fun RealTrashListViewModel.readCachedList(selectedType: TrashEntryType?): CachedTrashList? {
    val userId = AuthSessionManager.getCurrentUserSnapshot()?.userId ?: return null
    return AppReadCacheStore.readTrashList(userId, selectedType?.name)?.payload
}

private fun RealTrashListViewModel.persistTrashList(
    selectedType: TrashEntryType?,
    items: List<RemoteTrashItem>,
) {
    val userId = AuthSessionManager.getCurrentUserSnapshot()?.userId ?: return
    AppReadCacheStore.writeTrashList(
        userId = userId,
        selectedType = selectedType?.name,
        items = items,
    )
}

private fun CachedTrashList.toUiState(
    isOfflineReadOnly: Boolean = false,
    statusMessage: String? = null,
): RealTrashListUiState {
    val entries = items
        .map { it.toTrashEntryUiModel() }
        .distinctBy { it.businessIdentityKey() }
    return RealTrashListUiState(
        isLoading = false,
        isOfflineReadOnly = isOfflineReadOnly,
        statusMessage = statusMessage,
        entries = entries,
        pendingEntries = emptyList(),
    )
}

private fun RealTrashDetailViewModel.readCachedDetail(entryId: String): RemoteTrashDetail? {
    val userId = AuthSessionManager.getCurrentUserSnapshot()?.userId ?: return null
    return AppReadCacheStore.readTrashDetail(userId, entryId)?.payload
}

private fun RealTrashDetailViewModel.persistTrashDetail(
    entryId: String,
    detail: RemoteTrashDetail,
) {
    val userId = AuthSessionManager.getCurrentUserSnapshot()?.userId ?: return
    AppReadCacheStore.writeTrashDetail(
        userId = userId,
        entryId = entryId,
        detail = detail,
    )
}

private fun RemoteTrashDetail.toUiState(
    isOfflineReadOnly: Boolean = false,
    statusMessage: String? = null,
): RealTrashDetailUiState {
    return RealTrashDetailUiState(
        isLoading = false,
        isOfflineReadOnly = isOfflineReadOnly,
        statusMessage = statusMessage,
        detail = this,
    )
}
