package com.example.yingshi.feature.life

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yingshi.data.model.RemotePendingCleanup
import com.example.yingshi.data.model.RemoteTrashItem
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.data.repository.TrashRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * P1-2: 今日痕迹回收站 ViewModel。
 *
 * - loadAll(): 并行加载 life trash 列表 + 24h 撤回中心列表。
 * - loadPendingCleanup(): 单独刷新 24h 撤回中心。
 * - restoreItem / moveItemOut / undoMoveOut / purgeItem: 操作单条 trashItem，成功后回调。
 *
 * life 媒体的 lifeCategory (PERSON/MEAL) 由服务端区分，本 ViewModel 在请求时不传 category，
 * 拉取所有 life trash 后由 UI 层按 category 过滤展示。
 */
class LifeTrashViewModel(
    private val trashRepository: TrashRepository = RepositoryProvider.trashRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LifeTrashUiState())
    val uiState: StateFlow<LifeTrashUiState> = _uiState.asStateFlow()

    fun loadAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            // 并行拉取 trash 列表 + pending cleanup
            val trashResult = trashRepository.getTrashItems(null)
            val pendingResult = trashRepository.getPendingCleanupItems()

            val newItems: List<RemoteTrashItem> = when (trashResult) {
                is ApiResult.Success -> trashResult.data
                is ApiResult.Error -> {
                    _uiState.update { it.copy(errorMessage = trashResult.message) }
                    _uiState.value.items
                }
                ApiResult.Loading -> _uiState.value.items
            }
            val newPending: List<RemotePendingCleanup> = when (pendingResult) {
                is ApiResult.Success -> pendingResult.data
                is ApiResult.Error -> {
                    // pending 失败不覆盖主错误，仅在无主错误时显示
                    if (_uiState.value.errorMessage == null) {
                        _uiState.update { it.copy(errorMessage = pendingResult.message) }
                    }
                    _uiState.value.pendingItems
                }
                ApiResult.Loading -> _uiState.value.pendingItems
            }
            _uiState.update {
                it.copy(
                    items = newItems,
                    pendingItems = newPending,
                    isLoading = false,
                    isLoadingPending = false,
                    errorMessage = if (trashResult is ApiResult.Error) trashResult.message else null,
                )
            }
        }
    }

    fun loadPendingCleanup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPending = true, errorMessage = null) }
            when (val result = trashRepository.getPendingCleanupItems()) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            pendingItems = result.data,
                            isLoadingPending = false,
                            errorMessage = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingPending = false,
                            errorMessage = result.message,
                        )
                    }
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun restoreItem(trashItemId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            when (val result = trashRepository.restoreTrashItem(trashItemId)) {
                is ApiResult.Success -> onSuccess()
                is ApiResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun moveItemOut(trashItemId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            when (val result = trashRepository.moveTrashItemOut(trashItemId)) {
                is ApiResult.Success -> onSuccess()
                is ApiResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun undoMoveOut(trashItemId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            when (val result = trashRepository.undoMoveTrashItemOut(trashItemId)) {
                is ApiResult.Success -> onSuccess()
                is ApiResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun purgeItem(trashItemId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            when (val result = trashRepository.purgeTrashItem(trashItemId)) {
                is ApiResult.Success -> onSuccess()
                is ApiResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LifeTrashViewModel() as T
                }
            }
        }
    }
}

data class LifeTrashUiState(
    val items: List<RemoteTrashItem> = emptyList(),
    val pendingItems: List<RemotePendingCleanup> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingPending: Boolean = false,
    val errorMessage: String? = null,
)
