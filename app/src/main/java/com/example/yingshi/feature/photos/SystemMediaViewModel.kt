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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    private var refreshVersion = 0
    private var contentObserver: SystemMediaContentObserver? = null

    // EXIF 更新攒批: 后台解析完一项就 emit, 5000 项会导致 5000 次 publishState 卡死主线程.
    // 攒批窗口内累积所有更新, 窗口结束统一 apply + 一次 publishState.
    private val exifUpdateBatch = mutableMapOf<Long, SystemMediaExifUpdate>()
    private var exifBatchJob: Job? = null

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
        observeExifUpdates()
    }

    private fun observeSyncStaleState() {
        viewModelScope.launch {
            SyncVersionTracker.staleState.collect { state ->
                if (state.systemMediaStale) {
                    refresh(forceRefresh = true)
                    SyncVersionTracker.markRefreshedFresh(SyncModule.SYSTEM_MEDIA)
                }
            }
        }
    }

    // P0-2: 监听后台 EXIF 解析完成事件, merge 到 queriedItems 对应 item 并刷新 UI.
    // 攒批策略: 后台 5000 项 EXIF 解析, 每项 emit 一次. 若每次都 publishState 会卡死主线程
    // (5000 × O(n) applyOverlay = O(n²) 工作量). 改为 300ms 窗口攒批, 期间累积到 map,
    // 窗口结束统一 apply + 一次 publishState. 5000 项 → ~10 次 publishState.
    // 防重入: 若 refresh/backgroundRefresh 正在进行, 跳过 (refresh 会重新加载全部数据覆盖).
    private fun observeExifUpdates() {
        viewModelScope.launch {
            SystemMediaExifUpdateBus.events.collect { update ->
                exifUpdateBatch[update.mediaStoreId] = update
                exifBatchJob?.cancel()
                exifBatchJob = launch {
                    delay(EXIF_BATCH_WINDOW_MS)
                    applyBatchedExifUpdates()
                }
            }
        }
    }

    private fun applyBatchedExifUpdates() {
        if (exifUpdateBatch.isEmpty()) return
        // 防重入: refresh 进行中跳过, refresh 会用最新数据覆盖 queriedItems
        if (refreshJob?.isActive == true || backgroundRefreshJob?.isActive == true) {
            exifUpdateBatch.clear()
            return
        }
        val batch = exifUpdateBatch.values.toList()
        exifUpdateBatch.clear()
        if (queriedItems.isEmpty()) return
        val mutable = queriedItems.toMutableList()
        var anyChanged = false
        for (update in batch) {
            val index = mutable.indexOfFirst { it.mediaStoreId == update.mediaStoreId }
            if (index == -1) continue
            val original = mutable[index]
            // 时间和地点都没变才跳过, 避免 locationLabel 更新被吞
            if (original.displayTimeMillis == update.displayTimeMillis &&
                original.displayTimeSource == update.displayTimeSource &&
                original.locationLabel == update.locationLabel &&
                original.latitude == update.latitude &&
                original.longitude == update.longitude) continue
            mutable[index] = original.copy(
                displayTimeMillis = update.displayTimeMillis,
                capturedAtMillis = update.capturedAtMillis,
                fileModifiedAtMillis = update.fileModifiedAtMillis,
                displayTimeSource = update.displayTimeSource,
                displayYear = update.displayYear,
                displayMonth = update.displayMonth,
                displayDay = update.displayDay,
                locationLabel = update.locationLabel,
                latitude = update.latitude,
                longitude = update.longitude,
            )
            anyChanged = true
        }
        if (!anyChanged) return
        queriedItems = mutable
        publishState(
            rawItems = queriedItems,
            selectedFilter = _uiState.value.selectedFilter,
            isLoading = false,
            errorMessage = _uiState.value.errorMessage,
        )
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
            startBackgroundRefresh(useLocalOverlayOnly = true)
        }
    }

    internal fun startBackgroundRefresh(useLocalOverlayOnly: Boolean = false) {
        if (backgroundRefreshJob?.isActive == true) return
        backgroundRefreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBackgroundRefreshing = true)
            try {
                val items = withContext(Dispatchers.IO) {
                    repository.loadMedia(
                        forceRefresh = true,
                        useLocalOverlayOnly = useLocalOverlayOnly,
                    )
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
        // 变更1: cancel+restart 替代 pendingForceRefresh 排队, 避免 do-while 重复迭代.
        // 此前手动刷新期间 observeSyncStaleState/syncStaleState 监听器二次触发 pendingForceRefresh=true,
        // 导致 do-while 跑 2 轮完整网络路径 (4s). 改为 cancel+restart 后只跑 1 轮 (2s).
        refreshJob?.cancel()
        val requestVersion = ++refreshVersion
        refreshJob = viewModelScope.launch {
            // 变更3: 先缓存后网络两阶段. 手动刷新时先立即显示缓存, 再走网络.
            // 参考 RealPhotoFeedViewModel.startRefresh() L72-75 的 applyCachedFeed 先行策略.
            if (forceRefresh) {
                val cachedItems = withContext(Dispatchers.IO) {
                    repository.peekCachedMedia(maxAgeMillis = 0L)
                }
                if (cachedItems != null && cachedItems.isNotEmpty() && _uiState.value.allItems.isEmpty()) {
                    queriedItems = cachedItems
                    publishState(
                        rawItems = queriedItems,
                        selectedFilter = _uiState.value.selectedFilter,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            }
            val showLoading = _uiState.value.allItems.isEmpty()
            _uiState.value = _uiState.value.copy(
                isLoading = if (showLoading) true else _uiState.value.isLoading,
                isRefreshing = true,
                errorMessage = null,
            )

            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadMedia(forceRefresh = forceRefresh)
                }
            }.onSuccess { items ->
                // 变更1: 版本守卫, 丢弃过期结果
                if (requestVersion != refreshVersion) return@launch
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
                if (requestVersion != refreshVersion) return@launch
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
            if (requestVersion == refreshVersion) {
                refreshJob = null
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
                // 本地隐藏/恢复操作, MediaStore 实际未变, 仅需重读 + 应用 overlay.
                startBackgroundRefresh(useLocalOverlayOnly = true)
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
        // EXIF 更新攒批窗口: 300ms 内累积的事件统一 apply, 避免主线程被 5000 次刷新卡死
        private const val EXIF_BATCH_WINDOW_MS = 300L

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
