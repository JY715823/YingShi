package com.example.yingshi.feature.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yingshi.data.model.UpdatePostBasicInfoPayload
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.AlbumRepository
import com.example.yingshi.data.repository.MediaRepository
import com.example.yingshi.data.repository.PostRepository
import com.example.yingshi.data.repository.RepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RealGearEditUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val tokenMissing: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val albums: List<AlbumSummaryUiModel> = emptyList(),
    val title: String = "",
    val summary: String = "",
    val displayTimeMillis: Long = System.currentTimeMillis(),
    val selectedAlbumIds: List<String> = emptyList(),
    val draftLoaded: Boolean = false,
    val hasChanges: Boolean = false,
)

data class RealMediaManagementUiState(
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val tokenMissing: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val postTitle: String = "",
    val mediaItems: List<ManagedPostMediaUiModel> = emptyList(),
)

class RealGearEditViewModel(
    private val route: GearEditRoute,
    private val postRepository: PostRepository = RepositoryProvider.postRepository,
    private val albumRepository: AlbumRepository = RepositoryProvider.albumRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RealGearEditUiState(isLoading = true))
    val uiState: StateFlow<RealGearEditUiState> = _uiState.asStateFlow()

    private var initialDraft: EditablePostDraft? = null

    init {
        refresh()
    }

    fun refresh() {
        if (!AuthSessionManager.isLoggedIn) {
            _uiState.value = RealGearEditUiState(
                tokenMissing = true,
                errorMessage = "REAL 模式需要先登录，才能编辑后端帖子。",
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

            val albumsResult = albumRepository.getAlbums()
            val detailResult = postRepository.getPostDetail(route.postId)
            val albums = (albumsResult as? ApiResult.Success)
                ?.data
                .orEmpty()
                .map { it.toAlbumSummaryUiModel() }

            when (detailResult) {
                is ApiResult.Success -> {
                    val draft = detailResult.data.toEditablePostDraft()
                    initialDraft = draft
                    _uiState.value = RealGearEditUiState(
                        isLoading = false,
                        albums = albums,
                        title = draft.title,
                        summary = draft.summary,
                        displayTimeMillis = draft.postDisplayTimeMillis,
                        selectedAlbumIds = draft.albumIds,
                        draftLoaded = true,
                        hasChanges = false,
                        errorMessage = (albumsResult as? ApiResult.Error)
                            ?.toBackendUiMessage("读取相册失败，暂时无法切换所属相册。"),
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = RealGearEditUiState(
                        isLoading = false,
                        albums = albums,
                        errorMessage = detailResult.toBackendUiMessage("读取后端帖子失败。"),
                    )
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun updateTitle(value: String) {
        _uiState.update { state ->
            state.copy(title = value).recalculate(initialDraft)
        }
    }

    fun updateSummary(value: String) {
        _uiState.update { state ->
            state.copy(summary = value).recalculate(initialDraft)
        }
    }

    fun shiftDisplayTime(deltaMillis: Long) {
        _uiState.update { state ->
            state.copy(displayTimeMillis = state.displayTimeMillis + deltaMillis).recalculate(initialDraft)
        }
    }

    fun setDisplayTimeNow() {
        _uiState.update { state ->
            state.copy(displayTimeMillis = System.currentTimeMillis()).recalculate(initialDraft)
        }
    }

    fun toggleAlbum(albumId: String) {
        _uiState.update { state ->
            val updatedAlbumIds = if (state.selectedAlbumIds.contains(albumId)) {
                state.selectedAlbumIds.filterNot { it == albumId }
            } else {
                state.selectedAlbumIds + albumId
            }
            state.copy(selectedAlbumIds = updatedAlbumIds).recalculate(initialDraft)
        }
    }

    fun save(onSuccess: () -> Unit) {
        if (!AuthSessionManager.isLoggedIn) {
            _uiState.update {
                it.copy(errorMessage = "登录状态已失效，请先重新登录。")
            }
            return
        }

        val snapshot = _uiState.value
        if (snapshot.selectedAlbumIds.isEmpty()) {
            _uiState.update {
                it.copy(errorMessage = "至少需要选择一个所属相册。")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    errorMessage = null,
                    statusMessage = null,
                )
            }
            when (
                val result = postRepository.updatePostBasicInfo(
                    postId = route.postId,
                    payload = UpdatePostBasicInfoPayload(
                        title = snapshot.title.trim(),
                        summary = snapshot.summary.trim(),
                        displayTimeMillis = snapshot.displayTimeMillis,
                        albumIds = snapshot.selectedAlbumIds,
                    ),
                )
            ) {
                is ApiResult.Success -> {
                    initialDraft = EditablePostDraft(
                        postId = route.postId,
                        title = snapshot.title.trim(),
                        summary = snapshot.summary.trim(),
                        postDisplayTimeMillis = snapshot.displayTimeMillis,
                        albumIds = snapshot.selectedAlbumIds,
                    )
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            hasChanges = false,
                            statusMessage = "帖子信息已保存。",
                        )
                    }
                    RealBackendMutationBus.notifyChanged()
                    onSuccess()
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = result.toBackendUiMessage("保存帖子信息失败。"),
                        )
                    }
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun deletePost(onSuccess: () -> Unit) {
        if (!AuthSessionManager.isLoggedIn) {
            _uiState.update {
                it.copy(errorMessage = "登录状态已失效，请先重新登录。")
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
            when (val result = postRepository.deletePost(route.postId)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            statusMessage = "帖子已移入回收站。",
                        )
                    }
                    RealBackendMutationBus.notifyChanged()
                    onSuccess()
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            errorMessage = result.toBackendUiMessage("删除帖子失败。"),
                        )
                    }
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    companion object {
        fun factory(route: GearEditRoute): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RealGearEditViewModel(route = route) as T
                }
            }
        }
    }
}

class RealMediaManagementViewModel(
    private val route: MediaManagementRoute,
    private val postRepository: PostRepository = RepositoryProvider.postRepository,
    private val mediaRepository: MediaRepository = RepositoryProvider.mediaRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RealMediaManagementUiState(isLoading = true))
    val uiState: StateFlow<RealMediaManagementUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (!AuthSessionManager.isLoggedIn) {
            _uiState.value = RealMediaManagementUiState(
                tokenMissing = true,
                errorMessage = "REAL 模式需要先登录，才能管理后端帖子媒体。",
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
            when (val result = postRepository.getPostDetail(route.postId)) {
                is ApiResult.Success -> {
                    _uiState.value = RealMediaManagementUiState(
                        isLoading = false,
                        postTitle = result.data.title,
                        mediaItems = result.data.toManagedPostMediaUiModels(),
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = RealMediaManagementUiState(
                        isLoading = false,
                        errorMessage = result.toBackendUiMessage("读取媒体管理数据失败。"),
                    )
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun setCover(mediaId: String) {
        mutate(
            successMessage = "封面已更新。",
            successRefresh = true,
        ) {
            postRepository.setPostCover(route.postId, mediaId)
        }
    }

    fun saveMediaOrder(orderedMediaIds: List<String>) {
        mutate(
            successMessage = "媒体顺序已保存。",
            successRefresh = true,
        ) {
            postRepository.updatePostMediaOrder(route.postId, orderedMediaIds)
        }
    }

    fun deleteMedia(
        mediaIds: List<String>,
        deleteMode: String,
    ) {
        val normalizedIds = mediaIds.distinct()
        if (normalizedIds.isEmpty()) return
        if (!AuthSessionManager.isLoggedIn) {
            _uiState.update {
                it.copy(errorMessage = "登录状态已失效，请先重新登录。")
            }
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
            var firstFailure: String? = null
            normalizedIds.forEach { mediaId ->
                when (
                    val result = mediaRepository.deleteMediaFromPost(
                        postId = route.postId,
                        mediaId = mediaId,
                        deleteMode = deleteMode,
                    )
                ) {
                    is ApiResult.Success -> successCount += 1
                    is ApiResult.Error -> {
                        if (firstFailure == null) {
                            firstFailure = result.toBackendUiMessage("删除媒体失败。")
                        }
                    }
                    ApiResult.Loading -> Unit
                }
            }

            val statusMessage = when {
                successCount == 0 -> null
                deleteMode.equals("system", ignoreCase = true) && firstFailure == null ->
                    "媒体已系统删除并进入回收站。"
                deleteMode.equals("system", ignoreCase = true) ->
                    "部分媒体已系统删除，但仍有失败项。"
                firstFailure == null -> "媒体已从当前帖子移除。"
                else -> "部分媒体已移除，但仍有失败项。"
            }

            _uiState.update {
                it.copy(
                    isMutating = false,
                    errorMessage = firstFailure,
                    statusMessage = statusMessage,
                )
            }
            if (successCount > 0) {
                RealBackendMutationBus.notifyChanged()
                refresh()
            }
        }
    }

    private fun mutate(
        successMessage: String,
        successRefresh: Boolean,
        block: suspend () -> ApiResult<*>,
    ) {
        if (!AuthSessionManager.isLoggedIn) {
            _uiState.update {
                it.copy(errorMessage = "登录状态已失效，请先重新登录。")
            }
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
            when (val result = block()) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            statusMessage = successMessage,
                        )
                    }
                    RealBackendMutationBus.notifyChanged()
                    if (successRefresh) {
                        refresh()
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = result.toBackendUiMessage("后端操作失败。"),
                        )
                    }
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    companion object {
        fun factory(route: MediaManagementRoute): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RealMediaManagementViewModel(route = route) as T
                }
            }
        }
    }
}

private fun RealGearEditUiState.recalculate(initialDraft: EditablePostDraft?): RealGearEditUiState {
    val currentSnapshot = EditablePostDraft(
        postId = initialDraft?.postId.orEmpty(),
        title = title,
        summary = summary,
        postDisplayTimeMillis = displayTimeMillis,
        albumIds = selectedAlbumIds,
    )
    return copy(
        hasChanges = initialDraft != null && currentSnapshot != initialDraft,
    )
}
