package com.example.yingshi.feature.life

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yingshi.data.model.RemoteLifeConsoleHistory
import com.example.yingshi.data.model.RemoteLifeConsoleToday
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.life.widget.LifeConsoleWidgetProvider
import com.example.yingshi.feature.sync.SyncModule
import com.example.yingshi.feature.sync.SyncVersionTracker
import com.example.yingshi.ui.components.YingShiNotice
import com.example.yingshi.ui.components.YingShiNoticeTone
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class LifeConsoleUiState(
    val snapshot: RemoteLifeConsoleToday? = null,
    val history: RemoteLifeConsoleHistory? = null,
    val isLoading: Boolean = false,
    val isHistoryLoading: Boolean = false,
    val actionMessage: String? = null,
    val pendingUploadCategory: String? = null,
    val historyRange: LifeConsoleHistoryRange = LifeConsoleHistoryRange.ALL,
    val showHistoryPage: Boolean = false,
    val notice: YingShiNotice? = null,
    val pendingDeleteTarget: Pair<String, String>? = null,
    val contentVisible: Boolean = false,
    // FR-5: 离线缓存状态
    val offlineMode: Boolean = false,
    // FR-6: 错误分级
    val errorType: LifeConsoleErrorType? = null,
    val errorMessage: String? = null,
    // Round 7 阶段 5: 上传后滚动定位 — 一次性消费字段
    val lastUploadedMediaId: String? = null,
    val lastUploadedSlotKey: String? = null,
    // Round 7 阶段 7: 位置选择页目标 (media 或 bowel event)
    val pendingLocationUpdateTarget: LocationUpdateTarget? = null,
    // Round 8 第八轮: 正在异步获取定位的 mediaId 集合, UI 用它显示"正在定位中"
    val pendingLocationMediaIds: Set<String> = emptySet(),
    // Round 8 第十轮: 正在异步获取定位的 bowelEventId 集合, UI 用它显示"正在定位中"
    val pendingLocationBowelEventIds: Set<String> = emptySet(),
    // Round 8 第九轮: 大便事件正在添加 (同步获取 GPS 中), UI 显示 loading
    val isAddingBowel: Boolean = false,
    // Round 8 第九轮: 大便删除确认对话框
    val pendingBowelDelete: Boolean = false,
)

/**
 * Round 7 阶段 7: 位置选择页目标类型。
 * - Media: 调用 updateMediaLocation(mediaId, ...)
 * - Bowel: 调用 updateBowelEventLocation(eventId, ...)
 */
sealed class LocationUpdateTarget {
    abstract val initialLat: Double?
    abstract val initialLng: Double?
    abstract val initialLabel: String?

    data class Media(
        val mediaId: String,
        override val initialLat: Double?,
        override val initialLng: Double?,
        override val initialLabel: String?,
    ) : LocationUpdateTarget()

    data class Bowel(
        val eventId: String,
        override val initialLat: Double?,
        override val initialLng: Double?,
        override val initialLabel: String?,
    ) : LocationUpdateTarget()
}

// FR-6: 错误类型分级
enum class LifeConsoleErrorType {
    NETWORK,    // 无网络
    TIMEOUT,    // 超时
    SERVER,     // 服务端错误 (5xx)
    UNKNOWN,    // 未知
}

fun Throwable.toLifeConsoleErrorType(): LifeConsoleErrorType = when (this) {
    is UnknownHostException, is ConnectException -> LifeConsoleErrorType.NETWORK
    is SocketTimeoutException -> LifeConsoleErrorType.TIMEOUT
    is HttpException -> if (code() in 500..599) LifeConsoleErrorType.SERVER else LifeConsoleErrorType.UNKNOWN
    else -> LifeConsoleErrorType.UNKNOWN
}

class LifeConsoleViewModel(
    application: Application,
    private val zoneId: String = LIFE_CONSOLE_ZONE_ID,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LifeConsoleUiState())
    val uiState: StateFlow<LifeConsoleUiState> = _uiState.asStateFlow()

    private var noticeNonce = 0
    private var syncStaleJob: Job? = null
    private var crossMidnightJob: Job? = null

    private val appContext get() = getApplication<Application>().applicationContext

    init {
        loadToday()
        loadHistory()
        startSyncStaleObserver()
        startCrossMidnightTimer()
    }

    // ---- 通知 ----

    fun showNotice(message: String, tone: YingShiNoticeTone = YingShiNoticeTone.INFO) {
        noticeNonce += 1
        _uiState.update {
            it.copy(notice = YingShiNotice(message = message, tone = tone, nonce = noticeNonce))
        }
    }

    fun dismissNotice(nonce: Int) {
        if (_uiState.value.notice?.nonce == nonce) {
            _uiState.update { it.copy(notice = null) }
        }
    }

    // ---- 今日数据 ----

    fun loadToday() {
        val requestedDate = currentLifeConsoleDate(zoneId)
        if (_uiState.value.snapshot?.date != requestedDate) {
            _uiState.update { it.copy(snapshot = null) }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = RepositoryProvider.lifeConsoleRepository.getToday(
                date = requestedDate,
                zoneId = zoneId,
            )) {
                is ApiResult.Success -> {
                    val today = result.data
                    _uiState.update {
                        it.copy(
                            snapshot = today,
                            actionMessage = null,
                            errorType = null,
                            errorMessage = null,
                            offlineMode = result.isFromCache,
                            contentVisible = true,
                        )
                    }
                    LifeConsoleWidgetProvider.applySnapshot(appContext, today)
                    if (result.isFromCache) {
                        showNotice("正在使用离线数据", YingShiNoticeTone.INFO)
                    }
                }
                is ApiResult.Error -> {
                    val errorType = result.throwable?.toLifeConsoleErrorType() ?: LifeConsoleErrorType.UNKNOWN
                    _uiState.update {
                        it.copy(
                            actionMessage = result.message,
                            errorType = errorType,
                            errorMessage = result.message,
                        )
                    }
                }
                ApiResult.Loading -> Unit
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    // ---- 历史数据 ----

    fun loadHistory(limitDays: Int = _uiState.value.historyRange.limitDays) {
        val todayDate = currentLifeConsoleDate(zoneId)
        viewModelScope.launch {
            _uiState.update { it.copy(isHistoryLoading = true) }
            when (val result = RepositoryProvider.lifeConsoleRepository.getHistory(
                zoneId = zoneId,
                limitDays = limitDays,
            )) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            history = result.data,
                            actionMessage = null,
                            errorType = null,
                            errorMessage = null,
                            offlineMode = result.isFromCache,
                        )
                    }
                }
                is ApiResult.Error -> {
                    val errorType = result.throwable?.toLifeConsoleErrorType() ?: LifeConsoleErrorType.UNKNOWN
                    _uiState.update {
                        it.copy(
                            actionMessage = result.message,
                            errorType = errorType,
                            errorMessage = result.message,
                        )
                    }
                }
                ApiResult.Loading -> Unit
            }
            _uiState.update { it.copy(isHistoryLoading = false) }
        }
    }

    fun refreshHistory() {
        loadHistory(_uiState.value.historyRange.limitDays)
    }

    // ---- 历史范围 ----

    fun setHistoryRange(range: LifeConsoleHistoryRange) {
        _uiState.update { it.copy(historyRange = range) }
        loadHistory(limitDays = range.limitDays)
    }

    fun setShowHistoryPage(show: Boolean) {
        _uiState.update { it.copy(showHistoryPage = show) }
    }

    // ---- 上传 ----

    fun setPendingUploadCategory(category: String?) {
        _uiState.update { it.copy(pendingUploadCategory = category) }
    }

    fun onUploadResult(uris: List<android.net.Uri>, category: String, isFromCamera: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = LifeConsoleUploadBridge.uploadMedia(appContext, category, uris, isFromCamera)) {
                is ApiResult.Success -> {
                    // Round 7 阶段 5: 携带首个新上传的 mediaId + slotKey，驱动今日页滚动定位
                    val firstNewId = result.data.uploadedMediaIds.firstOrNull()
                    val newMediaIds = result.data.uploadedMediaIds
                    _uiState.update {
                        it.copy(
                            snapshot = result.data.snapshot,
                            actionMessage = null,
                            lastUploadedMediaId = firstNewId,
                            lastUploadedSlotKey = category,
                            // Round 8 第十四轮: 只有拍照上传才显示"正在定位中"占位
                            // 相册上传已读 EXIF GPS (有就有, 没有就没有), 不需要异步定位
                            pendingLocationMediaIds = if (isFromCamera) {
                                it.pendingLocationMediaIds + newMediaIds
                            } else {
                                it.pendingLocationMediaIds
                            },
                        )
                    }
                    LifeConsoleWidgetProvider.applySnapshot(appContext, result.data.snapshot)
                    showNotice("已上传到今日痕迹", YingShiNoticeTone.SUCCESS)
                    SyncVersionTracker.markLocalMutation(SyncModule.LIFE_CONSOLE)
                    loadHistory()
                    // Round 8 第十四轮: 只有拍照上传才异步获取当前 GPS 定位
                    // 相册上传已读 EXIF GPS, 不再触发实时定位
                    if (isFromCamera && newMediaIds.isNotEmpty()) {
                        launch { attachLocationToNewMedia(newMediaIds) }
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(actionMessage = result.message) }
                }
                ApiResult.Loading -> Unit
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Round 8 第八轮: 异步获取当前位置并更新到刚上传的媒体上.
     * 上传时不再同步获取定位 (会阻塞照片展示), 改为上传成功后异步获取.
     * UI 通过 pendingLocationMediaIds 显示"正在定位中"占位.
     */
    private suspend fun attachLocationToNewMedia(mediaIds: List<String>) {
        // Round 8 第十九轮: 优先读持续定位缓存 (0ms), 为 null 才 fallback 到一次性请求.
        val location = LocationHelper.currentLocationFast(appContext)
        // 不管成功失败都要清掉 pending 标记
        for (id in mediaIds) {
            viewModelScope.launch {
                if (location != null) {
                    when (val r = RepositoryProvider.lifeConsoleRepository.updateMediaLocation(
                        mediaId = id,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        locationLabel = null, // 服务端 Amap 反查
                    )) {
                        is ApiResult.Success -> {
                            _uiState.update {
                                it.copy(
                                    snapshot = r.data,
                                    pendingLocationMediaIds = it.pendingLocationMediaIds - id,
                                )
                            }
                            LifeConsoleWidgetProvider.applySnapshot(appContext, r.data)
                            SyncVersionTracker.markLocalMutation(SyncModule.LIFE_CONSOLE)
                        }
                        is ApiResult.Error -> {
                            _uiState.update {
                                it.copy(pendingLocationMediaIds = it.pendingLocationMediaIds - id)
                            }
                        }
                        ApiResult.Loading -> Unit
                    }
                } else {
                    _uiState.update {
                        it.copy(pendingLocationMediaIds = it.pendingLocationMediaIds - id)
                    }
                }
            }
        }
    }

    /**
     * Round 7 阶段 5: 滚动定位完成后由 Screen 调用，清空一次性消费字段，
     * 避免后续 recomposition 重复触发滚动。
     */
    fun consumeLastUploadedMediaId() {
        if (_uiState.value.lastUploadedMediaId != null || _uiState.value.lastUploadedSlotKey != null) {
            _uiState.update { it.copy(lastUploadedMediaId = null, lastUploadedSlotKey = null) }
        }
    }

    // ---- 位置更新 (Round 7 阶段 7) ----

    fun setPendingLocationUpdateTarget(target: LocationUpdateTarget?) {
        _uiState.update { it.copy(pendingLocationUpdateTarget = target) }
    }

    fun updateMediaLocation(mediaId: String, latitude: Double?, longitude: Double?, locationLabel: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = RepositoryProvider.lifeConsoleRepository.updateMediaLocation(
                mediaId = mediaId,
                latitude = latitude,
                longitude = longitude,
                locationLabel = locationLabel,
            )) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(snapshot = result.data, actionMessage = null) }
                    LifeConsoleWidgetProvider.applySnapshot(appContext, result.data)
                    showNotice("位置已更新", YingShiNoticeTone.SUCCESS)
                    SyncVersionTracker.markLocalMutation(SyncModule.LIFE_CONSOLE)
                    loadHistory()
                }
                is ApiResult.Error -> _uiState.update { it.copy(actionMessage = result.message) }
                ApiResult.Loading -> Unit
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun updateBowelEventLocation(eventId: String, latitude: Double?, longitude: Double?, locationLabel: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = RepositoryProvider.lifeConsoleRepository.updateBowelEventLocation(
                eventId = eventId,
                latitude = latitude,
                longitude = longitude,
                locationLabel = locationLabel,
            )) {
                is ApiResult.Success -> {
                    val current = _uiState.value.snapshot
                    if (current != null) {
                        val next = current.copy(bowel = result.data.bowel)
                        _uiState.update { it.copy(snapshot = next, actionMessage = null) }
                        LifeConsoleWidgetProvider.applySnapshot(appContext, next)
                    }
                    showNotice("位置已更新", YingShiNoticeTone.SUCCESS)
                    SyncVersionTracker.markLocalMutation(SyncModule.LIFE_CONSOLE)
                    loadHistory()
                }
                is ApiResult.Error -> _uiState.update { it.copy(actionMessage = result.message) }
                ApiResult.Loading -> Unit
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    // ---- 删除 ----

    fun setPendingDeleteTarget(mediaId: String, category: String) {
        _uiState.update { it.copy(pendingDeleteTarget = Pair(mediaId, category)) }
    }

    fun cancelDeleteMedia() {
        _uiState.update { it.copy(pendingDeleteTarget = null) }
    }

    fun confirmDeleteMedia() {
        val target = _uiState.value.pendingDeleteTarget ?: return
        val (mediaId, category) = target
        viewModelScope.launch {
            val current = _uiState.value.snapshot ?: return@launch
            _uiState.update { it.copy(isLoading = true, pendingDeleteTarget = null) }
            when (val result = RepositoryProvider.lifeConsoleRepository.deleteMedia(
                category = category,
                mediaId = mediaId,
            )) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(actionMessage = null) }
                    current.withoutMedia(mediaId)?.let { next ->
                        _uiState.update { it.copy(snapshot = next) }
                        LifeConsoleWidgetProvider.applySnapshot(appContext, next)
                    }
                    showNotice("已从今日痕迹移除", YingShiNoticeTone.SUCCESS)
                    SyncVersionTracker.markLocalMutation(SyncModule.LIFE_CONSOLE)
                    loadToday()
                    loadHistory()
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(actionMessage = result.message)
                }
                ApiResult.Loading -> Unit
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    // ---- 排便 ----

    fun addBowelEvent() {
        if (_uiState.value.isAddingBowel) return
        val restored = _uiState.value.snapshot ?: return
        // Round 8 第十轮: 改回异步 — 先创建事件 (无位置), UI 立即刷新显示新事件 + "正在定位中",
        // 然后异步获取 GPS 定位并更新到事件上. 用户要求"先加载刷新出来, 地点位置再显示正在加载".
        viewModelScope.launch {
            _uiState.update { it.copy(isAddingBowel = true) }
            // 先无位置创建事件, 让服务端记录并立即返回新 snapshot
            when (val result = RepositoryProvider.lifeConsoleRepository.addBowelEvent(
                zoneId = zoneId,
                latitude = null,
                longitude = null,
            )) {
                is ApiResult.Success -> {
                    val current = _uiState.value.snapshot
                    if (current != null) {
                        val next = current.copy(bowel = result.data.bowel)
                        _uiState.update { it.copy(snapshot = next) }
                        LifeConsoleWidgetProvider.applySnapshot(appContext, next)
                    }
                    SyncVersionTracker.markLocalMutation(SyncModule.LIFE_CONSOLE)
                    loadHistory()
                    // Round 8 第十轮: 异步获取 GPS 并更新到这条新事件, UI 显示"正在定位中"
                    val newEventId = result.data.eventId
                    if (newEventId != null) {
                        _uiState.update {
                            it.copy(pendingLocationBowelEventIds = it.pendingLocationBowelEventIds + newEventId)
                        }
                        launch { attachLocationToBowelEvent(newEventId) }
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(actionMessage = result.message) }
                }
                ApiResult.Loading -> Unit
            }
            _uiState.update { it.copy(isAddingBowel = false) }
        }
    }

    /**
     * Round 8 第十轮: 异步获取 GPS 定位并更新到大便事件上.
     * LocationHelper 现在返回 GCJ-02, 传给服务端准确.
     */
    private suspend fun attachLocationToBowelEvent(eventId: String) {
        // Round 8 第十九轮: 优先读持续定位缓存 (0ms), 为 null 才 fallback 到一次性请求.
        val location = LocationHelper.currentLocationFast(appContext)
        if (location == null) {
            _uiState.update {
                it.copy(pendingLocationBowelEventIds = it.pendingLocationBowelEventIds - eventId)
            }
            showNotice("GPS 未就绪, 请手动添加地点", YingShiNoticeTone.WARNING)
            return
        }
        when (val r = RepositoryProvider.lifeConsoleRepository.updateBowelEventLocation(
            eventId = eventId,
            latitude = location.latitude,
            longitude = location.longitude,
            locationLabel = null,
        )) {
            is ApiResult.Success -> {
                val current = _uiState.value.snapshot
                if (current != null) {
                    val next = current.copy(bowel = r.data.bowel)
                    _uiState.update {
                        it.copy(
                            snapshot = next,
                            pendingLocationBowelEventIds = it.pendingLocationBowelEventIds - eventId,
                        )
                    }
                    LifeConsoleWidgetProvider.applySnapshot(appContext, next)
                }
                SyncVersionTracker.markLocalMutation(SyncModule.LIFE_CONSOLE)
                loadHistory()
            }
            is ApiResult.Error -> {
                _uiState.update {
                    it.copy(pendingLocationBowelEventIds = it.pendingLocationBowelEventIds - eventId)
                }
            }
            ApiResult.Loading -> Unit
        }
    }

    // Round 8 第九轮: 大便删除确认对话框
    fun requestBowelDelete() {
        _uiState.update { it.copy(pendingBowelDelete = true) }
    }

    fun cancelBowelDelete() {
        _uiState.update { it.copy(pendingBowelDelete = false) }
    }

    fun removeBowelEvent() {
        val restored = _uiState.value.snapshot ?: return
        _uiState.update { it.copy(pendingBowelDelete = false) }
        val optimistic = restored.withOptimisticBowelDelta(delta = -1) ?: return
        _uiState.update { it.copy(snapshot = optimistic) }
        LifeConsoleWidgetProvider.applySnapshot(appContext, optimistic)
        viewModelScope.launch {
            when (val result = RepositoryProvider.lifeConsoleRepository.deleteLatestBowelEvent(zoneId)) {
                is ApiResult.Success -> {
                    val current = _uiState.value.snapshot
                    if (current != null) {
                        val next = current.copy(bowel = result.data.bowel)
                        _uiState.update { it.copy(snapshot = next) }
                        LifeConsoleWidgetProvider.applySnapshot(appContext, next)
                    }
                    SyncVersionTracker.markLocalMutation(SyncModule.LIFE_CONSOLE)
                    loadHistory()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(snapshot = restored) }
                    LifeConsoleWidgetProvider.applySnapshot(appContext, restored)
                    _uiState.update { it.copy(actionMessage = result.message) }
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    // ---- 生命周期 ----

    fun onResume() {
        loadToday()
        loadHistory()
    }

    // ---- 内部定时器 ----

    private fun startSyncStaleObserver() {
        syncStaleJob?.cancel()
        syncStaleJob = viewModelScope.launch {
            SyncVersionTracker.staleState.collect { stale ->
                if (stale.lifeConsoleStale) {
                    loadToday()
                    loadHistory()
                    SyncVersionTracker.markRefreshed(SyncModule.LIFE_CONSOLE)
                }
            }
        }
    }

    private fun startCrossMidnightTimer() {
        crossMidnightJob?.cancel()
        crossMidnightJob = viewModelScope.launch {
            while (true) {
                delay(millisUntilNextLifeConsoleRefresh(zoneId))
                loadToday()
                loadHistory()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        syncStaleJob?.cancel()
        crossMidnightJob?.cancel()
    }

    companion object {
        fun factory(application: Application, zoneId: String = LIFE_CONSOLE_ZONE_ID): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LifeConsoleViewModel(
                        application = application,
                        zoneId = zoneId,
                    ) as T
                }
            }
        }
    }
}