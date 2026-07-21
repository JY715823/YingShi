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
    val participantUserIds: List<String> = emptyList(),
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
                        participantUserIds = draft.participantUserIds,
                        mediaItems = mediaItems,
                        coverMediaId = coverMediaId,
                        draftLoaded = true,
                        hasChanges = false,
                        errorMessage = (albumsResult as? ApiResult.Error)
                            ?.toBackendUiMessage("读取相册失败，当前无法切换所属相册。"),
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = RealGearEditUiState(
                        isLoading = false,
                        albums = albums,
                        errorMessage = detailResult.toBackendUiMessage("读取小相册失败。"),
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

    fun updateDisplayTime(timeMillis: Long) {
        _uiState.update { state ->
            state.copy(displayTimeMillis = timeMillis)
                .recalculate(initialDraft, initialMediaIds, initialCoverMediaId)
        }
    }

    fun toggleAlbum(albumId: String) {
        _uiState.update { state ->
            val updatedAlbumIds = if (state.selectedAlbumIds.contains(albumId)) {
                emptyList()
            } else {
                listOf(albumId)
            }
            state.copy(selectedAlbumIds = updatedAlbumIds)
                .recalculate(initialDraft, initialMediaIds, initialCoverMediaId)
        }
    }

    fun updateParticipantUserIds(userIds: List<String>) {
        _uiState.update { state ->
            state.copy(participantUserIds = userIds.distinct())
                .recalculate(initialDraft, initialMediaIds, initialCoverMediaId)
        }
    }

    fun clearStatusMessage() {
        _uiState.update { state ->
            if (state.statusMessage == null) state else state.copy(statusMessage = null)
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

    fun syncExternalContent() {
        val snapshot = _uiState.value
        if (snapshot.isLoading || snapshot.tokenMissing || !snapshot.draftLoaded) return
        viewModelScope.launch {
            when (val result = postRepository.getPostDetail(route.postId)) {
                is ApiResult.Success -> {
                    val latestMediaItems = result.data.toManagedPostMediaUiModels()
                    val latestCoverMediaId = latestMediaItems.firstOrNull { it.isCover }?.id
                        ?: latestMediaItems.firstOrNull()?.id
                    val previousBaselineIds = initialMediaIds
                    val currentMediaIds = snapshot.mediaItems.map { it.id }
                    val localMediaDirty = currentMediaIds != previousBaselineIds
                    val localCoverDirty = snapshot.coverMediaId != initialCoverMediaId
                    val mergedMediaItems = if (!localMediaDirty && !localCoverDirty) {
                        latestMediaItems
                    } else {
                        mergeMediaDraftWithRemoteAdditions(
                            currentItems = snapshot.mediaItems,
                            latestItems = latestMediaItems,
                            previousBaselineIds = previousBaselineIds,
                            currentIdSelector = { it.id },
                            latestIdSelector = { it.id },
                            transformLatest = { it.copy(isCover = false) },
                        )
                    }
                    val nextCoverId = when {
                        localMediaDirty || localCoverDirty ->
                            snapshot.coverMediaId?.takeIf { id -> mergedMediaItems.any { it.id == id } }
                                ?: latestCoverMediaId?.takeIf { id -> mergedMediaItems.any { it.id == id } }
                                ?: mergedMediaItems.firstOrNull()?.id
                        else ->
                            latestCoverMediaId?.takeIf { id -> mergedMediaItems.any { it.id == id } }
                                ?: mergedMediaItems.firstOrNull()?.id
                    }
                    initialMediaIds = latestMediaItems.map { it.id }
                    initialCoverMediaId = latestCoverMediaId
                    _uiState.update { state ->
                        state.copy(
                            mediaItems = mergedMediaItems.map { media ->
                                media.copy(isCover = media.id == nextCoverId)
                            },
                            coverMediaId = nextCoverId,
                        ).recalculate(initialDraft, initialMediaIds, initialCoverMediaId)
                    }
                }
                is ApiResult.Error -> Unit
                ApiResult.Loading -> Unit
            }
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
                        participantUserIds = snapshot.participantUserIds,
                        displayTimeMillis = snapshot.displayTimeMillis,
                        albumId = snapshot.selectedAlbumIds.first(),
                    ),
                )
            ) {
                is ApiResult.Success -> {
                    val savedDraft = EditablePostDraft(
                        postId = route.postId,
                        title = snapshot.title.trim(),
                        summary = snapshot.summary.trim(),
                        postDisplayTimeMillis = snapshot.displayTimeMillis,
                        albumIds = listOf(snapshot.selectedAlbumIds.first()),
                        participantUserIds = snapshot.participantUserIds,
                    )
                    var committedMediaIds = initialMediaIds
                    var committedCoverMediaId = initialCoverMediaId
                    var firstFailure: String? = null
                    var mediaChanged = false
                    val removedIds = initialMediaIds.filterNot { finalMediaIds.contains(it) }
                    if (removedIds.isNotEmpty()) {
                        when (val batchResult = postRepository.updatePostMediaBatch(route.postId, removedIds)) {
                            is ApiResult.Success -> {
                                mediaChanged = true
                                committedMediaIds = committedMediaIds.filterNot { removedIds.contains(it) }
                                committedCoverMediaId = batchResult.data.coverMediaId
                            }
                            is ApiResult.Error -> firstFailure = batchResult.toBackendUiMessage("批量移除媒体失败。")
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

                    val previousDraft = initialDraft
                    val titleChanged = previousDraft?.title != snapshot.title.trim()
                    val summaryChanged = previousDraft?.summary != snapshot.summary.trim()
                    val ownershipChanged = previousDraft?.participantUserIds != snapshot.participantUserIds
                    initialDraft = savedDraft
                    initialMediaIds = committedMediaIds
                    initialCoverMediaId = committedCoverMediaId?.takeIf { committedMediaIds.contains(it) }
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            hasChanges = false,
                            statusMessage = when {
                                titleChanged && !summaryChanged -> "已更新标题。"
                                !titleChanged && summaryChanged -> "已更新简介。"
                                ownershipChanged && !titleChanged && !summaryChanged -> "已更新所属。"
                                else -> "小相册已保存。"
                            },
                        )
                    }
                    notifyRealBackendPostChanged(
                        postIds = setOf(route.postId),
                    )
                    if (mediaChanged) {
                        notifyRealBackendContentChangedWithoutPhotoFeed(
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
                            errorMessage = result.toBackendUiMessage("保存小相册信息失败。"),
                        )
                    }
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun deleteSmallAlbum(onSuccess: () -> Unit) {
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
            when (val result = postRepository.deleteSmallAlbum(route.postId)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            statusMessage = "小相册已移入回收站。",
                        )
                    }
                    notifyRealBackendContentChangedWithoutPhotoFeed(
                        postIds = setOf(route.postId),
                    )
                    onSuccess()
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            errorMessage = result.toBackendUiMessage("删除小相册失败。"),
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
                        smallAlbumId = route.postId,
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
                firstFailure == null -> "媒体已从当前小相册移除。"
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
                notifyRealBackendContentChangedWithoutPhotoFeed(
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
            when (val result = postRepository.deleteSmallAlbum(route.postId)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            statusMessage = "小相册已移入回收站。",
                        )
                    }
                    notifyRealBackendContentChangedWithoutPhotoFeed(
                        postIds = setOf(route.postId),
                    )
                    onSuccess()
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = result.toBackendUiMessage("删除小相册失败。"),
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
                            errorMessage = result.toBackendUiMessage("保存失败。"),
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
        participantUserIds = participantUserIds,
    )
    val currentMediaIds = mediaItems.map { it.id }
    return copy(
        hasChanges = initialDraft != null &&
            (currentSnapshot != initialDraft ||
                currentMediaIds != initialMediaIds ||
                coverMediaId != initialCoverMediaId),
    )
}

