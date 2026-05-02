package com.example.yingshi.feature.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yingshi.data.model.RemoteTrashDetail
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.data.repository.TrashRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RealTrashListUiState(
    val isLoading: Boolean = false,
    val tokenMissing: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val entries: List<TrashEntryUiModel> = emptyList(),
    val pendingEntries: List<TrashPendingCleanupUiModel> = emptyList(),
)

data class RealTrashDetailUiState(
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val tokenMissing: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val detail: RemoteTrashDetail? = null,
)

class RealTrashListViewModel(
    private val trashRepository: TrashRepository = RepositoryProvider.trashRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RealTrashListUiState(isLoading = true))
    val uiState: StateFlow<RealTrashListUiState> = _uiState.asStateFlow()

    fun refresh(selectedType: TrashEntryType?) {
        if (!AuthSessionManager.isLoggedIn) {
            _uiState.value = RealTrashListUiState(
                tokenMissing = true,
                errorMessage = "REAL 模式需要先登录，请到后端联调诊断页完成登录。",
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
            val itemsDeferred = async {
                trashRepository.getTrashItems(selectedType?.toApiItemType())
            }
            val pendingDeferred = async {
                trashRepository.getPendingCleanupItems()
            }

            val itemsResult = itemsDeferred.await()
            val pendingResult = pendingDeferred.await()

            val itemError = (itemsResult as? ApiResult.Error)
                ?.toBackendUiMessage("读取回收站列表失败。")
            val pendingError = (pendingResult as? ApiResult.Error)
                ?.toBackendUiMessage("读取待清理列表失败。")

            _uiState.value = RealTrashListUiState(
                isLoading = false,
                errorMessage = itemError ?: pendingError,
                entries = (itemsResult as? ApiResult.Success)?.data.orEmpty()
                    .map { it.toTrashEntryUiModel() },
                pendingEntries = (pendingResult as? ApiResult.Success)?.data.orEmpty()
                    .map { it.toTrashPendingCleanupUiModel() },
                statusMessage = _uiState.value.statusMessage,
            )
        }
    }

    fun undoPendingCleanup(trashItemId: String, selectedType: TrashEntryType?) {
        viewModelScope.launch {
            when (val result = trashRepository.undoMoveTrashItemOut(trashItemId)) {
                is ApiResult.Success -> {
                    RealBackendMutationBus.notifyChanged(
                        RealBackendMutationEvent(
                            scopes = setOf(
                                RealBackendRefreshScope.PHOTO_FEED,
                                RealBackendRefreshScope.ALBUMS,
                                RealBackendRefreshScope.POST_DETAIL,
                                RealBackendRefreshScope.MEDIA_MANAGEMENT,
                                RealBackendRefreshScope.TRASH,
                                RealBackendRefreshScope.SYSTEM_MEDIA_DESTINATIONS,
                            ),
                        ),
                    )
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

    companion object {
        fun factory(): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RealTrashListViewModel() as T
                }
            }
        }
    }
}

class RealTrashDetailViewModel(
    private val route: TrashDetailRoute,
    private val trashRepository: TrashRepository = RepositoryProvider.trashRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RealTrashDetailUiState(isLoading = true))
    val uiState: StateFlow<RealTrashDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (!AuthSessionManager.isLoggedIn) {
            _uiState.value = RealTrashDetailUiState(
                tokenMissing = true,
                errorMessage = "REAL 模式需要先登录，请到后端联调诊断页完成登录。",
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
            when (val result = trashRepository.getTrashDetail(route.entryId)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        detail = result.data,
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = RealTrashDetailUiState(
                        isLoading = false,
                        errorMessage = result.toBackendUiMessage("读取回收站详情失败。"),
                    )
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun restore(onSuccess: () -> Unit) {
        mutate("已恢复到正常列表。", onSuccess) {
            trashRepository.restoreTrashItem(route.entryId)
        }
    }

    fun remove(onSuccess: () -> Unit) {
        mutate("已移出回收站，进入 24 小时可撤销状态。", onSuccess) {
            trashRepository.moveTrashItemOut(route.entryId)
        }
    }

    fun undoRemove() {
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
                    RealBackendMutationBus.notifyChanged(
                        RealBackendMutationEvent(
                            scopes = setOf(
                                RealBackendRefreshScope.PHOTO_FEED,
                                RealBackendRefreshScope.ALBUMS,
                                RealBackendRefreshScope.POST_DETAIL,
                                RealBackendRefreshScope.MEDIA_MANAGEMENT,
                                RealBackendRefreshScope.TRASH,
                                RealBackendRefreshScope.SYSTEM_MEDIA_DESTINATIONS,
                            ),
                        ),
                    )
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
        TrashEntryType.POST_DELETED -> "postDeleted"
        TrashEntryType.MEDIA_REMOVED -> "mediaRemoved"
        TrashEntryType.MEDIA_SYSTEM_DELETED -> "mediaSystemDeleted"
    }
}
