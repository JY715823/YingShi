package com.example.yingshi.feature.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yingshi.data.model.RemoteTrashDetail
import com.example.yingshi.data.model.RemoteTrashItem
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.auth.BackendAutoLoginManager
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
    val isMutating: Boolean = false,
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
        viewModelScope.launch {
            if (!AuthSessionManager.isLoggedIn) {
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

            val itemsResult = itemsDeferred.await()

            val itemError = (itemsResult as? ApiResult.Error)
                ?.toBackendUiMessage("读取回收站列表失败。")

            _uiState.value = RealTrashListUiState(
                isLoading = false,
                errorMessage = itemError,
                entries = (itemsResult as? ApiResult.Success)?.data.orEmpty()
                    .map { it.toTrashEntryUiModel() },
                pendingEntries = emptyList(),
                statusMessage = _uiState.value.statusMessage,
            )
        }
    }

    fun undoPendingCleanup(trashItemId: String, selectedType: TrashEntryType?) {
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
        viewModelScope.launch {
            if (!AuthSessionManager.isLoggedIn) {
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

    fun restore(onSuccess: (RemoteTrashItem) -> Unit) {
        mutateTrashItem("已恢复到正常列表。", onSuccess) {
            trashRepository.restoreTrashItem(route.entryId)
        }
    }

    fun restoreItem(trashItemId: String, onSuccess: (RemoteTrashItem) -> Unit) {
        mutateTrashItem("已恢复到正常列表。", onSuccess) {
            trashRepository.restoreTrashItem(trashItemId)
        }
    }

    fun remove(onSuccess: () -> Unit) {
        mutateTrashItem("已永久删除该回收站项目。", { onSuccess() }) {
            trashRepository.purgeTrashItem(route.entryId)
        }
    }

    fun removeItem(trashItemId: String, onSuccess: () -> Unit) {
        mutateTrashItem("已永久删除该回收站项目。", { onSuccess() }) {
            trashRepository.purgeTrashItem(trashItemId)
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
        TrashEntryType.POST_DELETED -> "smallAlbumDeleted"
        TrashEntryType.MEDIA_REMOVED -> "mediaRemoved"
        TrashEntryType.MEDIA_SYSTEM_DELETED -> "mediaSystemDeleted"
    }
}
