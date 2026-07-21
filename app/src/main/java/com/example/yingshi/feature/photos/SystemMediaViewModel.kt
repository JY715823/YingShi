package com.example.yingshi.feature.photos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yingshi.feature.sync.SyncModule
import com.example.yingshi.feature.sync.SyncVersionTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var refreshJob: Job? = null
    private var backgroundRefreshJob: Job? = null
    private var hasLoadedOnce = false
    private var pendingForceRefresh = false
    private var contentObserver: SystemMediaContentObserver? = null

    init {
        val cachedItems = repository.peekCachedMedia(maxAgeMillis = CACHE_VALIDITY_MILLIS)
        if (cachedItems.isNullOrEmpty()) {
            refresh()
        } else {
            queriedItems = cachedItems
            hasLoadedOnce = true
            publishState(
                rawItems = cachedItems,
                selectedFilter = initialFilter,
                isLoading = false,
                errorMessage = null,
            )
            startBackgroundRefresh()
        }
        registerContentObserver()
        loadAlbums()
        observeSyncStaleState()
    }

    private fun observeSyncStaleState() {
        viewModelScope.launch {
            SyncVersionTracker.staleState.collect { state ->
                if (state.systemMediaStale) {
                    refresh(forceRefresh = true)
                    SyncVersionTracker.markRefreshed(SyncModule.SYSTEM_MEDIA)
                }
            }
        }
    }

    private fun loadAlbums() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadAlbums()
                }
            }.onSuccess { albums ->
                _uiState.value = _uiState.value.copy(albums = albums)
            }
        }
    }

    private fun registerContentObserver() {
        contentObserver = SystemMediaContentObserver.create(getApplication()) {
            startBackgroundRefresh()
        }
    }

    private fun startBackgroundRefresh() {
        if (backgroundRefreshJob?.isActive == true) return
        backgroundRefreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBackgroundRefreshing = true)
            try {
                val items = withContext(Dispatchers.IO) {
                    repository.loadMedia(forceRefresh = true)
                }
                queriedItems = items
                hasLoadedOnce = true
                publishState(
                    rawItems = queriedItems,
                    selectedFilter = _uiState.value.selectedFilter,
                    isLoading = false,
                    errorMessage = null,
                )
                loadAlbums()
            } catch (_: Throwable) {
                // 后台刷新失败时不清空已有数据，仅重置状态
            } finally {
                _uiState.value = _uiState.value.copy(isBackgroundRefreshing = false)
            }
        }
    }

    fun ensureLoaded() {
        if ((queriedItems.isNotEmpty() || hasLoadedOnce) && !_uiState.value.hasError) {
            return
        }
        refresh()
    }

    fun refresh(forceRefresh: Boolean = false) {
        // 手动强制刷新时取消后台刷新，避免被阻塞
        if (forceRefresh && backgroundRefreshJob?.isActive == true) {
            backgroundRefreshJob?.cancel()
            backgroundRefreshJob = null
        }
        if (refreshJob?.isActive == true) {
            if (forceRefresh) {
                pendingForceRefresh = true
            }
            return
        }

        refreshJob = viewModelScope.launch {
            var shouldForceRefresh = forceRefresh || pendingForceRefresh
            pendingForceRefresh = false
            do {
                val currentForceRefresh = shouldForceRefresh
                shouldForceRefresh = false
                val showLoading = _uiState.value.allItems.isEmpty()
                _uiState.value = _uiState.value.copy(
                    isLoading = if (showLoading) true else _uiState.value.isLoading,
                    isRefreshing = true,
                    errorMessage = null,
                )

                runCatching {
                    withContext(Dispatchers.IO) {
                        repository.loadMedia(forceRefresh = currentForceRefresh)
                    }
                }.onSuccess { items ->
                    queriedItems = items
                    hasLoadedOnce = true
                    publishState(
                        rawItems = queriedItems,
                        selectedFilter = _uiState.value.selectedFilter,
                        isLoading = false,
                        errorMessage = null,
                    )
                    loadAlbums()
                }.onFailure { throwable ->
                    if (_uiState.value.allItems.isEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            allItems = emptyList(),
                            filteredItems = emptyList(),
                            errorMessage = throwable.toSystemMediaMessage(),
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = throwable.toSystemMediaMessage(),
                        )
                    }
                }
                if (pendingForceRefresh) {
                    shouldForceRefresh = true
                    pendingForceRefresh = false
                }
            } while (shouldForceRefresh)
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

    fun onAlbumSelected(album: SystemMediaAlbum?) {
        _uiState.value = _uiState.value.copy(selectedAlbum = album)
        publishState(
            rawItems = queriedItems,
            selectedFilter = _uiState.value.selectedFilter,
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
                startBackgroundRefresh()
            }
            LocalSystemMediaBridgeRepository.MutationKind.OVERLAY_ONLY -> {
                refreshLocalState()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        contentObserver?.unregister()
        contentObserver = null
    }

    private fun publishState(
        rawItems: List<SystemMediaItem>,
        selectedFilter: SystemMediaFilter,
        isLoading: Boolean,
        errorMessage: String?,
    ) {
        val visibleItems = LocalSystemMediaBridgeRepository.applyOverlay(rawItems)
        val isBackgroundRefreshing = _uiState.value.isBackgroundRefreshing
        val selectedAlbum = _uiState.value.selectedAlbum
        val albums = _uiState.value.albums
        _uiState.value = SystemMediaUiState(
            isLoading = isLoading,
            isRefreshing = false,
            isBackgroundRefreshing = isBackgroundRefreshing,
            selectedFilter = selectedFilter,
            selectedAlbum = selectedAlbum,
            albums = albums,
            allItems = visibleItems,
            filteredItems = visibleItems.applyFilter(selectedFilter, selectedAlbum),
            errorMessage = errorMessage,
        )
    }

    companion object {
        private const val CACHE_VALIDITY_MILLIS = 5L * 60L * 1000L

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

internal fun List<SystemMediaItem>.applyFilter(
    filter: SystemMediaFilter,
    album: SystemMediaAlbum?,
): List<SystemMediaItem> {
    val albumFiltered = if (album == null) {
        this
    } else {
        filter { it.bucketName == album.bucketName }
    }
    return when (filter) {
        SystemMediaFilter.ALL -> albumFiltered
        SystemMediaFilter.CAMERA -> albumFiltered.filter { item ->
            val bucket = item.bucketName.orEmpty().lowercase()
            bucket.contains("camera") || bucket.contains("dcim")
        }
        SystemMediaFilter.SCREENSHOT -> albumFiltered.filter { item ->
            val bucket = item.bucketName.orEmpty().lowercase()
            val displayName = item.displayName.lowercase()
            bucket.contains("screenshot") || displayName.contains("screenshot")
        }
        SystemMediaFilter.VIDEO -> albumFiltered.filter { it.type == SystemMediaType.VIDEO }
        SystemMediaFilter.IMPORTED -> albumFiltered.filter { it.isImportedToApp }
        SystemMediaFilter.UNIMPORTED -> albumFiltered.filter { !it.isImportedToApp }
    }
}

private fun Throwable.toSystemMediaMessage(): String {
    return if (this is SecurityException) {
        "无法读取本地媒体。请先确认已经授予图片和视频权限。"
    } else {
        "读取本地媒体失败，请稍后重试。"
    }
}
