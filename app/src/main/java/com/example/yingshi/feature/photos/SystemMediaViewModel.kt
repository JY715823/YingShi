package com.example.yingshi.feature.photos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SystemMediaViewModel(
    application: Application,
    initialFilter: SystemMediaFilter = SystemMediaFilter.ALL,
    private val repository: SystemMediaRepository = LocalSystemMediaRepository(application),
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        SystemMediaUiState(
            isLoading = false,
            selectedFilter = initialFilter,
        ),
    )
    val uiState: StateFlow<SystemMediaUiState> = _uiState.asStateFlow()
    private var queriedItems: List<SystemMediaItem> = emptyList()

    init {
        val cachedItems = repository.peekCachedMedia()
        if (cachedItems.isNullOrEmpty()) {
            refresh()
        } else {
            queriedItems = cachedItems
            publishState(
                rawItems = cachedItems,
                selectedFilter = initialFilter,
                isLoading = false,
                errorMessage = null,
            )
        }
    }

    fun ensureLoaded() {
        if (queriedItems.isEmpty() && !_uiState.value.isLoading) {
            refresh()
        }
    }

    fun refresh(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val showLoading = _uiState.value.allItems.isEmpty()
            if (showLoading) {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    errorMessage = null,
                )
            }

            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadMedia(forceRefresh = forceRefresh)
                }
            }.onSuccess { items ->
                queriedItems = items
                publishState(
                    rawItems = queriedItems,
                    selectedFilter = _uiState.value.selectedFilter,
                    isLoading = false,
                    errorMessage = null,
                )
            }.onFailure { throwable ->
                if (_uiState.value.allItems.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        allItems = emptyList(),
                        filteredItems = emptyList(),
                        errorMessage = throwable.toSystemMediaMessage(),
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = throwable.toSystemMediaMessage(),
                    )
                }
            }
        }
    }

    fun onFilterSelected(filter: SystemMediaFilter) {
        publishState(
            rawItems = queriedItems,
            selectedFilter = filter,
            isLoading = false,
            errorMessage = _uiState.value.errorMessage,
        )
    }

    fun refreshLocalState() {
        if (_uiState.value.hasError && queriedItems.isEmpty()) return
        publishState(
            rawItems = queriedItems,
            selectedFilter = _uiState.value.selectedFilter,
            isLoading = false,
            errorMessage = null,
        )
    }

    fun handleBridgeMutation(
        event: LocalSystemMediaBridgeRepository.MutationEvent,
    ) {
        if (event.version <= 0) return
        when (event.kind) {
            LocalSystemMediaBridgeRepository.MutationKind.MEDIA_STORE_CHANGED -> {
                refresh(forceRefresh = true)
            }
            LocalSystemMediaBridgeRepository.MutationKind.OVERLAY_ONLY -> {
                refreshLocalState()
            }
        }
    }

    private fun publishState(
        rawItems: List<SystemMediaItem>,
        selectedFilter: SystemMediaFilter,
        isLoading: Boolean,
        errorMessage: String?,
    ) {
        val visibleItems = LocalSystemMediaBridgeRepository.applyOverlay(rawItems)
        _uiState.value = SystemMediaUiState(
            isLoading = isLoading,
            selectedFilter = selectedFilter,
            allItems = visibleItems,
            filteredItems = visibleItems.applyFilter(selectedFilter),
            errorMessage = errorMessage,
        )
    }

    companion object {
        fun factory(
            application: Application,
            initialFilter: SystemMediaFilter = SystemMediaFilter.ALL,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SystemMediaViewModel(
                        application = application,
                        initialFilter = initialFilter,
                    ) as T
                }
            }
        }
    }
}

private fun List<SystemMediaItem>.applyFilter(filter: SystemMediaFilter): List<SystemMediaItem> {
    return when (filter) {
        SystemMediaFilter.ALL -> this
        SystemMediaFilter.CAMERA -> filter { item ->
            val bucket = item.bucketName.orEmpty().lowercase()
            bucket.contains("camera") || bucket.contains("dcim")
        }
        SystemMediaFilter.SCREENSHOT -> filter { item ->
            val bucket = item.bucketName.orEmpty().lowercase()
            val displayName = item.displayName.lowercase()
            bucket.contains("screenshot") || displayName.contains("screenshot")
        }
        SystemMediaFilter.VIDEO -> filter { it.type == SystemMediaType.VIDEO }
        SystemMediaFilter.POSTED -> filter { it.linkedPostIds.isNotEmpty() }
        SystemMediaFilter.UNPOSTED -> filter { it.linkedPostIds.isEmpty() }
    }
}

private fun Throwable.toSystemMediaMessage(): String {
    return if (this is SecurityException) {
        "无法读取本地媒体。请先确认已经授予图片和视频权限。"
    } else {
        "读取本地媒体失败，请稍后重试。"
    }
}
