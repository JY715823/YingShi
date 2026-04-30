package com.example.yingshi.feature.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yingshi.data.repository.MediaRepository
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.result.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RealPhotoFeedUiState(
    val isLoading: Boolean = false,
    val isDeleting: Boolean = false,
    val tokenMissing: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val feedItems: List<PhotoFeedItem> = emptyList(),
)

class RealPhotoFeedViewModel(
    private val mediaRepository: MediaRepository = RepositoryProvider.mediaRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RealPhotoFeedUiState(isLoading = true))
    val uiState: StateFlow<RealPhotoFeedUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (!AuthSessionManager.isLoggedIn) {
            _uiState.value = RealPhotoFeedUiState(
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
                    statusMessage = null,
                )
            }
            when (val result = mediaRepository.getMediaFeed()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        feedItems = result.data.map { it.toPhotoFeedItem() },
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = RealPhotoFeedUiState(
                        isLoading = false,
                        errorMessage = result.toBackendUiMessage("读取后端照片流失败。"),
                    )
                }
                ApiResult.Loading -> Unit
            }
        }
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
            var successCount = 0
            var firstFailure: String? = null

            normalizedIds.forEach { mediaId ->
                when (val result = mediaRepository.systemDeleteMedia(mediaId)) {
                    is ApiResult.Success -> successCount += 1
                    is ApiResult.Error -> {
                        if (firstFailure == null) {
                            firstFailure = result.toBackendUiMessage("删除媒体失败。")
                        }
                    }
                    ApiResult.Loading -> Unit
                }
            }

            refresh()
            _uiState.update {
                it.copy(
                    isDeleting = false,
                    errorMessage = firstFailure,
                    statusMessage = when {
                        successCount > 0 && firstFailure == null -> "已删除 $successCount 项媒体，并写入后端回收站。"
                        successCount > 0 -> "已删除 $successCount 项媒体，但仍有部分失败。"
                        else -> null
                    },
                )
            }
            if (successCount > 0) {
                RealBackendMutationBus.notifyChanged()
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
