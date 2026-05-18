package com.example.yingshi.feature.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yingshi.data.model.UpdatePostBasicInfoPayload
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.auth.BackendAutoLoginManager
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
    val mediaItems: List<ManagedPostMediaUiModel> = emptyList(),
    val coverMediaId: String? = null,
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
    private var initialMediaIds: List<String> = emptyList()
    private var initialCoverMediaId: String? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            if (!AuthSessionManager.isLoggedIn) {
                val loginOutcome = BackendAutoLoginManager.loginDefault(
                    force = false,
                    reason = "real_gear_edit_refresh",
                )
                if (!loginOutcome.success) {
                    _uiState.value = RealGearEditUiState(
                        tokenMissing = true,
                        errorMessage = loginOutcome.message.ifBlank {
                            "REAL 模式需要先登录，请到后端联调页检查后端地址。"
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
                    val mediaItems = detailResult.data.toManagedPostMediaUiModels()
                    val coverMediaId = mediaItems.firstOrNull { it.isCover }?.id
                        ?: mediaItems.firstOrNull()?.id
                    initialDraft = draft
                    initialMediaIds = mediaItems.map { it.id }
                    initialCoverMediaId = coverMediaId
                    _uiState.value = RealGearEditUiState(
                        isLoading = false,
                        albums = albums,
                        title = draft.title,
                        summary = draft.summary,
                        displayTimeMillis = draft.postDisplayTimeMillis,
                        selectedAlbumIds = draft.albumIds,
                        mediaItems = mediaItems,
                        coverMediaId = coverMediaId,
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
            state.copy(title = value).recalculate(initialDraft, initialMediaIds, initialCoverMediaId)
        }
    }

    fun updateSummary(value: String) {
        _uiState.update { state ->
            state.copy(summary = value).recalculate(initialDraft, initialMediaIds, initialCoverMediaId)
        }
    }

    fun shiftDisplayTime(deltaMillis: Long) {
        _uiState.update { state ->
            state.copy(displayTimeMillis = state.displayTimeMillis + deltaMillis)
                .recalculate(initialDraft, initialMediaIds, initialCoverMediaId)
        }
    }

    fun setDisplayTimeNow() {
        _uiState.update { state ->
            state.copy(displayTimeMillis = System.currentTimeMillis())
                .recalculate(initialDraft, initialMediaIds, initialCoverMediaId)
        }
    }

    fun toggleAlbum(albumId: String) {
        _uiState.update { state ->
            val updatedAlbumIds = if (state.selectedAlbumIds.contains(albumId)) {
                state.selectedAlbumIds.filterNot { it == albumId }
            } else {
                state.selectedAlbumIds + albumId
            }
            state.copy(selectedAlbumIds = updatedAlbumIds)
                .recalculate(initialDraft, initialMediaIds, initialCoverMediaId)
        }
    }

    internal fun updateMediaDraft(
        items: List<PostMediaListItem>,
        coverMediaId: String?,
    ) {
        val currentById = _uiState.value.mediaItems.associateBy { it.id }
        val updatedItems = items.mapNotNull { item -> currentById[item.id] }
        val safeCoverId = coverMediaId?.takeIf { id -> updatedItems.any { it.id == id } }
            ?: updatedItems.firstOrNull()?.id
        _uiState.update { state ->
            state.copy(
                mediaItems = updatedItems.map { media -> media.copy(isCover = media.id == safeCoverId) },
                coverMediaId = safeCoverId,
            ).recalculate(initialDraft, initialMediaIds, initialCoverMediaId)
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
        val finalMediaIds = snapshot.mediaItems.map { it.id }
        val finalCoverId = snapshot.coverMediaId?.takeIf { finalMediaIds.contains(it) }
            ?: snapshot.mediaItems.firstOrNull()?.id
        if (snapshot.mediaItems.isNotEmpty() && finalCoverId == null) {
            _uiState.update {
                it.copy(errorMessage = "封面媒体已失效，请重新选择封面后再保存。")
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
                    val savedDraft = EditablePostDraft(
                        postId = route.postId,
                        title = snapshot.title.trim(),
                        summary = snapshot.summary.trim(),
                        postDisplayTimeMillis = snapshot.displayTimeMillis,
                        albumIds = snapshot.selectedAlbumIds,
                    )
                    var committedMediaIds = initialMediaIds
                    var committedCoverMediaId = initialCoverMediaId
                    var firstFailure: String? = null
                    var mediaChanged = false
                    val removedIds = initialMediaIds.filterNot { finalMediaIds.contains(it) }
                    removedIds.forEach { mediaId ->
                        when (
                            val deleteResult = RepositoryProvider.mediaRepository.deleteMediaFromPost(
                                postId = route.postId,
                                mediaId = mediaId,
                                deleteMode = "directory",
                            )
                        ) {
                            is ApiResult.Success -> {
                                mediaChanged = true
                                committedMediaIds = committedMediaIds.filterNot { it == mediaId }
                            }
                            is ApiResult.Error -> if (firstFailure == null) {
                                firstFailure = deleteResult.toBackendUiMessage("移除媒体失败。")
                            }
                            ApiResult.Loading -> Unit
                        }
                    }

                    if (firstFailure == null && finalMediaIds.isNotEmpty()) {
                        val orderChanged = finalMediaIds != initialMediaIds.filter { finalMediaIds.contains(it) }
                        if (orderChanged) {
                            when (val orderResult = postRepository.updatePostMediaOrder(route.postId, finalMediaIds)) {
                                is ApiResult.Success -> {
                                    mediaChanged = true
                                    committedMediaIds = finalMediaIds
                                }
                                is ApiResult.Error -> firstFailure = orderResult.toBackendUiMessage("保存媒体顺序失败。")
                                ApiResult.Loading -> Unit
                            }
                        }
                    }

                    if (firstFailure == null && !finalCoverId.isNullOrBlank() && finalCoverId != initialCoverMediaId) {
                        when (val coverResult = postRepository.setPostCover(route.postId, finalCoverId)) {
                            is ApiResult.Success -> {
                                mediaChanged = true
                                committedCoverMediaId = finalCoverId
                            }
                            is ApiResult.Error -> firstFailure = coverResult.toBackendUiMessage("设置封面失败。")
                            ApiResult.Loading -> Unit
                        }
                    }

                    if (firstFailure != null) {
                        initialDraft = savedDraft
                        initialMediaIds = committedMediaIds
                        initialCoverMediaId = committedCoverMediaId?.takeIf { committedMediaIds.contains(it) }
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                errorMessage = firstFailure,
                            ).recalculate(initialDraft, initialMediaIds, initialCoverMediaId)
                        }
                        return@launch
                    }

                    initialDraft = savedDraft
                    initialMediaIds = committedMediaIds
                    initialCoverMediaId = committedCoverMediaId?.takeIf { committedMediaIds.contains(it) }
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            hasChanges = false,
                            statusMessage = "帖子信息已保存。",
                        )
                    }
                    notifyRealBackendPostChanged(
                        postIds = setOf(route.postId),
                    )
                    if (mediaChanged) {
                        notifyRealBackendContentChanged(
                            postIds = setOf(route.postId),
                            mediaIds = (removedIds + finalMediaIds).toSet(),
                        )
                    }
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
                    notifyRealBackendContentChanged(
                        postIds = setOf(route.postId),
                    )
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
        viewModelScope.launch {
            if (!AuthSessionManager.isLoggedIn) {
                val loginOutcome = BackendAutoLoginManager.loginDefault(
                    force = false,
                    reason = "real_media_management_refresh",
                )
                if (!loginOutcome.success) {
                    _uiState.value = RealMediaManagementUiState(
                        tokenMissing = true,
                        errorMessage = loginOutcome.message.ifBlank {
                            "REAL 模式需要先登录，请到后端联调页检查后端地址。"
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
                notifyRealBackendContentChanged(
                    postIds = setOf(route.postId),
                    mediaIds = normalizedIds.toSet(),
                )
                refresh()
            }
        }
    }

    fun deleteCurrentPost(onSuccess: () -> Unit) {
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
            when (val result = postRepository.deletePost(route.postId)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            statusMessage = "帖子已移入回收站。",
                        )
                    }
                    notifyRealBackendContentChanged(
                        postIds = setOf(route.postId),
                    )
                    onSuccess()
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = result.toBackendUiMessage("删除帖子失败。"),
                        )
                    }
                }
                ApiResult.Loading -> Unit
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
                    notifyRealBackendPostChanged(
                        postIds = setOf(route.postId),
                    )
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

private fun RealGearEditUiState.recalculate(
    initialDraft: EditablePostDraft?,
    initialMediaIds: List<String> = mediaItems.map { it.id },
    initialCoverMediaId: String? = coverMediaId,
): RealGearEditUiState {
    val currentSnapshot = EditablePostDraft(
        postId = initialDraft?.postId.orEmpty(),
        title = title,
        summary = summary,
        postDisplayTimeMillis = displayTimeMillis,
        albumIds = selectedAlbumIds,
    )
    val currentMediaIds = mediaItems.map { it.id }
    return copy(
        hasChanges = initialDraft != null &&
            (currentSnapshot != initialDraft ||
                currentMediaIds != initialMediaIds ||
                coverMediaId != initialCoverMediaId),
    )
}
