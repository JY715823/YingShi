package com.example.yingshi.feature.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.auth.BackendAutoLoginManager
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.MediaRepository
import com.example.yingshi.data.repository.RepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RealPhotoFeedUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreErrorMessage: String? = null,
    val isDeleting: Boolean = false,
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
    private val _uiState = MutableStateFlow(RealPhotoFeedUiState(isLoading = true))
    val uiState: StateFlow<RealPhotoFeedUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            if (!AuthSessionManager.isLoggedIn) {
                val loginOutcome = BackendAutoLoginManager.loginDefault(
                    force = false,
                    reason = "real_photo_feed_refresh",
                )
                if (!loginOutcome.success) {
                    _uiState.value = RealPhotoFeedUiState(
                        tokenMissing = true,
                        errorMessage = loginOutcome.message.ifBlank {
                            "REAL 模式需要先登录，请到后端联调页检查后端地址。"
                        },
                    )
                    return@launch
                }
            }

            val minLoadedItemCount = _uiState.value.feedItems.size.coerceAtLeast(pageSize)
            nextCursor = null
            loadMoreInFlight = false
            loadMoreBlockedByError = false
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isLoadingMore = false,
                    loadMoreErrorMessage = null,
                    tokenMissing = false,
                    hasMore = false,
                    errorMessage = null,
                    statusMessage = null,
                )
            }
            when (val result = loadRefreshPages(minLoadedItemCount = minLoadedItemCount)) {
                is ApiResult.Success -> {
                    nextCursor = result.data.nextCursor
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            feedItems = result.data.items.map { item -> item.toPhotoFeedItem() },
                            hasMore = result.data.hasMore,
                            loadMoreErrorMessage = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.toBackendUiMessage("读取后端照片流失败。"),
                        )
                    }
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun loadNextPage() {
        val cursor = nextCursor ?: return
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
                    _uiState.update { state ->
                        val nextItems = result.data.items.map { it.toPhotoFeedItem() }
                        state.copy(
                            isLoadingMore = false,
                            loadMoreErrorMessage = null,
                            hasMore = result.data.hasMore,
                            feedItems = (state.feedItems + nextItems).distinctBy { it.mediaId },
                        )
                    }
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

    fun deleteSelectedMedia(mediaIds: Set<String>) {
        val normalizedIds = mediaIds.toList().distinct()
        if (normalizedIds.isEmpty()) return
        if (!AuthSessionManager.isLoggedIn) {
            _uiState.update {
                it.copy(errorMessage = "登录状态缺失，请先到联调诊断页重新登录。")
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

            normalizedIds.forEach { mediaId ->
                when (val result = mediaRepository.systemDeleteMedia(mediaId)) {
                    is ApiResult.Success -> deletedIds += mediaId
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
                            "已删除 ${deletedIds.size} 项媒体，并写入后端回收站。"
                        deletedIds.isNotEmpty() ->
                            "已删除 ${deletedIds.size} 项媒体，但仍有部分失败。"
                        else -> null
                    },
                )
            }
            if (deletedIds.isNotEmpty()) {
                notifyRealBackendContentChanged(mediaIds = deletedIds)
            }
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
    val items: List<com.example.yingshi.data.model.RemoteMedia>,
    val nextCursor: String?,
    val hasMore: Boolean,
)
