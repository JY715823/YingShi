package com.example.yingshi.feature.photos

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yingshi.data.model.CreatePostPayload
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.AlbumRepository
import com.example.yingshi.data.repository.PostRepository
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PostComposerUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val initialized: Boolean = false,
    val tokenMissing: Boolean = false,
    val errorMessage: String? = null,
    val localMessage: String? = null,
    val title: String = "",
    val summary: String = "",
    val displayTimeMillis: Long = System.currentTimeMillis(),
    val selectedAlbumIds: List<String> = emptyList(),
    val participantUserIds: Set<String> = emptySet(),
    val selectedSystemMediaItems: List<SystemMediaItem> = emptyList(),
    val selectedAppMediaItems: List<CreatePostAppMediaItem> = emptyList(),
    val selectedCoverMediaId: String? = null,
    val collaboratorDirectory: CollaboratorDirectorySnapshot = CollaboratorDirectorySnapshot(currentUser = null, partner = null),
    val availableCollaboratorIds: Set<String> = emptySet(),
    val availableAppMediaItems: List<CreatePostAppMediaItem> = emptyList(),
    val albums: List<AlbumSummaryUiModel> = emptyList(),
    val hydratedInitialAppMediaIds: Boolean = false,
    val showPostMediaList: Boolean = false,
    val showAlbumDirectory: Boolean = false,
    val showPhotoFeedPicker: Boolean = false,
    val showTimeEditorSheet: Boolean = false,
    val showDiscardConfirm: Boolean = false,
    val showClearAllConfirm: Boolean = false,
) {
    val selectedAppMediaIds: List<String>
        get() = selectedAppMediaItems.map { it.mediaId }

    val selectedSystemMediaIds: List<String>
        get() = selectedSystemMediaItems.map { it.id }

    val selectedMediaIds: List<String>
        get() = selectedSystemMediaIds + selectedAppMediaIds

    val normalizedSelectedParticipantUserIds: Set<String>
        get() = normalizeOwnedCollaboratorSelection(
            selectedUserIds = participantUserIds,
            allUserIds = availableCollaboratorIds,
            fallbackUserId = collaboratorDirectory.currentUser?.userId,
        )

    val resolvedCoverMediaId: String?
        get() = selectedCoverMediaId?.takeIf { selectedMediaIds.contains(it) }
            ?: selectedSystemMediaIds.firstOrNull()
            ?: selectedAppMediaIds.firstOrNull()

    val postMediaListItems: List<PostMediaListItem>
        get() = selectedSystemMediaItems.map(SystemMediaItem::toPostMediaListItem) +
            selectedAppMediaItems.map(CreatePostAppMediaItem::toPostMediaListItem)

    val selectedAlbumTitles: List<String>
        get() = albums.filter { selectedAlbumIds.contains(it.id) }.map { it.title }

    val hasUnsavedChanges: Boolean
        get() = title.isNotBlank() ||
            summary.isNotBlank() ||
            selectedSystemMediaItems.isNotEmpty() ||
            selectedAppMediaItems.isNotEmpty()

    val publishButtonText: String
        get() = when {
            isSubmitting -> "创建中..."
            selectedSystemMediaItems.isNotEmpty() -> "上传并创建相册"
            else -> "创建相册"
        }
}

class PostComposerViewModel(
    private val route: CreatePostRoute,
    private val postRepository: PostRepository = RepositoryProvider.postRepository,
    private val albumRepository: AlbumRepository = RepositoryProvider.albumRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PostComposerUiState(
            selectedSystemMediaItems = route.initialMediaItems.distinctBy { it.id },
            selectedAppMediaItems = route.initialAppMediaItems.distinctBy { it.mediaId },
            selectedCoverMediaId = route.initialMediaItems.firstOrNull()?.id
                ?: route.initialAppMediaItems.firstOrNull()?.mediaId
                ?: route.initialAppMediaIds.firstOrNull(),
            collaboratorDirectory = CollaboratorDirectorySnapshot(currentUser = null, partner = null),
        ),
    )
    val uiState: StateFlow<PostComposerUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // 加载 collaboratorDirectory
            val collaboratorDirectory = CollaboratorDirectoryStore.snapshot()
            val availableCollaboratorIds = collaboratorDirectory.all.mapTo(linkedSetOf()) { it.userId }

            // 加载 seedState
            val seedState = loadCreatePostUiState(route)

            // 加载 availableAppMediaItems
            val availableAppMediaItems = loadCreatePostAppMediaItems()

            _uiState.update { current ->
                val initialAlbumIds = when {
                    route.initialAlbumId != null && seedState.albums.any { it.id == route.initialAlbumId } ->
                        listOf(route.initialAlbumId)
                    else -> seedState.selectedAlbumIds
                }
                val initialParticipantUserIds = normalizeOwnedCollaboratorSelection(
                    selectedUserIds = seedState.participantUserIds.toSet(),
                    allUserIds = availableCollaboratorIds,
                    fallbackUserId = collaboratorDirectory.currentUser?.userId,
                )
                val initialCoverMediaId = current.selectedCoverMediaId
                    ?: seedState.selectedCoverSourceMediaId
                    ?: current.selectedMediaIds.firstOrNull()
                current.copy(
                    isLoading = false,
                    tokenMissing = seedState.tokenMissing,
                    errorMessage = seedState.errorMessage,
                    albums = seedState.albums,
                    collaboratorDirectory = collaboratorDirectory,
                    availableCollaboratorIds = availableCollaboratorIds,
                    availableAppMediaItems = availableAppMediaItems,
                    title = seedState.title,
                    summary = seedState.summary,
                    displayTimeMillis = seedState.displayTimeMillis,
                    selectedAlbumIds = initialAlbumIds,
                    participantUserIds = initialParticipantUserIds,
                    selectedCoverMediaId = initialCoverMediaId,
                    initialized = !seedState.isLoading,
                )
            }
        }
    }

    fun hydrateInitialAppMediaIds() {
        val current = _uiState.value
        if (current.hydratedInitialAppMediaIds) return
        if (route.initialAppMediaIds.isEmpty()) {
            _uiState.update { it.copy(hydratedInitialAppMediaIds = true) }
            return
        }
        val routeInitialItems = route.initialAppMediaIds
            .distinct()
            .mapNotNull { mediaId ->
                route.initialAppMediaItems.firstOrNull { it.mediaId == mediaId }
                    ?: current.availableAppMediaItems.firstOrNull { it.mediaId == mediaId }
            }
        if (routeInitialItems.isEmpty() && current.availableAppMediaItems.isEmpty()) return
        _uiState.update { state ->
            val nextAppMediaItems = (state.selectedAppMediaItems + routeInitialItems).distinctBy { it.mediaId }
            val nextCoverMediaId = state.selectedCoverMediaId
                ?: routeInitialItems.firstOrNull()?.mediaId
                ?: route.initialAppMediaIds.firstOrNull()
            state.copy(
                selectedAppMediaItems = nextAppMediaItems,
                selectedCoverMediaId = nextCoverMediaId,
                hydratedInitialAppMediaIds = true,
            )
        }
    }

    fun normalizeParticipants() {
        _uiState.update { state ->
            if (state.availableCollaboratorIds.isEmpty()) {
                state.copy(participantUserIds = emptySet())
            } else {
                state.copy(
                    participantUserIds = normalizeOwnedCollaboratorSelection(
                        selectedUserIds = state.participantUserIds,
                        allUserIds = state.availableCollaboratorIds,
                        fallbackUserId = state.collaboratorDirectory.currentUser?.userId,
                    ),
                )
            }
        }
    }

    fun updateTitle(value: String) {
        _uiState.update { it.copy(title = value) }
    }

    fun updateSummary(value: String) {
        _uiState.update { it.copy(summary = value) }
    }

    fun toggleAlbum(albumId: String) {
        _uiState.update { state ->
            val updatedAlbumIds = if (state.selectedAlbumIds.contains(albumId)) {
                emptyList()
            } else {
                listOf(albumId)
            }
            state.copy(selectedAlbumIds = updatedAlbumIds)
        }
    }

    fun updateParticipantUserIds(userIds: Set<String>) {
        _uiState.update { it.copy(participantUserIds = userIds) }
    }

    fun updateDisplayTime(timeMillis: Long) {
        _uiState.update { it.copy(displayTimeMillis = timeMillis) }
    }

    fun shiftDisplayTime(deltaMillis: Long) {
        _uiState.update { it.copy(displayTimeMillis = it.displayTimeMillis + deltaMillis) }
    }

    fun setDisplayTimeNow() {
        _uiState.update { it.copy(displayTimeMillis = System.currentTimeMillis()) }
    }

    fun updateMediaDraft(items: List<PostMediaListItem>, coverMediaId: String?) {
        _uiState.update { state ->
            val updatedIds = items.map { it.id }
            val systemById = state.selectedSystemMediaItems.associateBy { it.id }
            val appById = state.selectedAppMediaItems.associateBy { it.mediaId }
            state.copy(
                selectedSystemMediaItems = updatedIds.mapNotNull(systemById::get),
                selectedAppMediaItems = updatedIds.mapNotNull(appById::get),
                selectedCoverMediaId = coverMediaId,
            )
        }
    }

    fun addAppMediaItems(items: List<CreatePostAppMediaItem>) {
        _uiState.update { state ->
            val nextItems = items.distinctBy { it.mediaId }
            val remainingIds = state.selectedSystemMediaIds + nextItems.map { it.mediaId }
            val nextCoverMediaId = if (state.selectedCoverMediaId != null && state.selectedCoverMediaId !in remainingIds) {
                remainingIds.firstOrNull()
            } else {
                state.selectedCoverMediaId
            }
            state.copy(
                selectedAppMediaItems = nextItems,
                selectedCoverMediaId = nextCoverMediaId,
            )
        }
    }

    fun removeMedia(mediaId: String) {
        _uiState.update { state ->
            val nextSystemItems = state.selectedSystemMediaItems.filterNot { it.id == mediaId }
            val nextAppItems = state.selectedAppMediaItems.filterNot { it.mediaId == mediaId }
            val remainingIds = nextSystemItems.map { it.id } + nextAppItems.map { it.mediaId }
            val nextCoverMediaId = if (state.selectedCoverMediaId == mediaId) {
                remainingIds.firstOrNull()
            } else {
                state.selectedCoverMediaId
            }
            state.copy(
                selectedSystemMediaItems = nextSystemItems,
                selectedAppMediaItems = nextAppItems,
                selectedCoverMediaId = nextCoverMediaId,
                localMessage = "已移出 1 项媒体",
            )
        }
    }

    fun clearAllMedia() {
        _uiState.update { state ->
            state.copy(
                selectedSystemMediaItems = emptyList(),
                selectedAppMediaItems = emptyList(),
                selectedCoverMediaId = null,
                localMessage = "已清空待选媒体",
            )
        }
    }

    fun setShowPostMediaList(value: Boolean) {
        _uiState.update { it.copy(showPostMediaList = value) }
    }

    fun setShowAlbumDirectory(value: Boolean) {
        _uiState.update { it.copy(showAlbumDirectory = value) }
    }

    fun setShowPhotoFeedPicker(value: Boolean) {
        _uiState.update { it.copy(showPhotoFeedPicker = value) }
    }

    fun setShowTimeEditorSheet(value: Boolean) {
        _uiState.update { it.copy(showTimeEditorSheet = value) }
    }

    fun setShowDiscardConfirm(value: Boolean) {
        _uiState.update { it.copy(showDiscardConfirm = value) }
    }

    fun setShowClearAllConfirm(value: Boolean) {
        _uiState.update { it.copy(showClearAllConfirm = value) }
    }

    fun setLocalMessage(value: String?) {
        _uiState.update { it.copy(localMessage = value) }
    }

    fun clearLocalMessage() {
        _uiState.update { state ->
            if (state.localMessage == null) state else state.copy(localMessage = null)
        }
    }

    fun submitDraft(
        context: Context,
        onSuccess: (PostDetailPlaceholderRoute) -> Unit,
        onSubmittedToBackground: () -> Unit,
    ) {
        val state = _uiState.value
        if (state.selectedAlbumIds.isEmpty()) {
            _uiState.update { it.copy(localMessage = "请至少选择一个相册。") }
            return
        }
        val mode = RepositoryProvider.currentMode
        val draft = CreatePostDraft(
            title = state.title.trim(),
            summary = state.summary.trim(),
            displayTimeMillis = state.displayTimeMillis,
            albumIds = state.selectedAlbumIds,
            participantUserIds = state.normalizedSelectedParticipantUserIds.toList(),
            coverSourceMediaId = state.resolvedCoverMediaId,
        )

        // 分支 1: 系统媒体非空
        if (state.selectedSystemMediaItems.isNotEmpty()) {
            if (mode == RepositoryMode.REAL) {
                val queuedCount = LocalSystemMediaBridgeRepository.enqueueCreatePostUpload(
                    context = context,
                    mediaItems = state.selectedSystemMediaItems,
                    draft = draft,
                    additionalAppMediaIds = state.selectedAppMediaIds,
                    additionalAppCoverMediaId = state.resolvedCoverMediaId,
                )
                if (queuedCount > 0) {
                    Toast.makeText(
                        context,
                        if (state.selectedSystemMediaItems.all { it.isImportedToApp }) {
                            "正在复用已导入媒体创建小相册。"
                        } else {
                            "已加入上传队列，完成后会创建新小相册。"
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                    onSubmittedToBackground()
                } else {
                    _uiState.update { it.copy(localMessage = "当前媒体已在目标位置或暂时不可处理，请刷新后重试。") }
                }
            } else {
                val createdPost = LocalSystemMediaBridgeRepository.createPostFromSystemMediaDraft(
                    draft = draft,
                    mediaItems = state.selectedSystemMediaItems,
                    additionalAppMediaItems = state.selectedAppMediaIds.mapNotNull(FakePhotoFeedRepository::findPhotoFeedItem),
                )
                if (createdPost == null) {
                    _uiState.update { it.copy(localMessage = "本地新小相册创建失败，请稍后重试。") }
                } else {
                    onSuccess(FakeAlbumRepository.toPostDetailRoute(createdPost))
                }
            }
            return
        }

        // 分支 2: App 媒体非空
        if (state.selectedAppMediaItems.isNotEmpty()) {
            if (mode == RepositoryMode.REAL) {
                viewModelScope.launch {
                    _uiState.update { it.copy(isSubmitting = true) }
                    val result = postRepository.createPost(
                        CreatePostPayload(
                            title = draft.title.ifBlank { "新小相册" },
                            summary = draft.summary,
                            participantUserIds = draft.participantUserIds,
                            displayTimeMillis = draft.displayTimeMillis,
                            albumId = draft.requireAlbumId(),
                            initialMediaIds = state.selectedAppMediaIds,
                            coverMediaId = state.resolvedCoverMediaId,
                        ),
                    )
                    _uiState.update { it.copy(isSubmitting = false) }
                    when (result) {
                        is ApiResult.Success -> {
                            notifyRealBackendPostChanged(postIds = setOf(result.data.postId))
                            onSuccess(
                                result.data.toPostDetailPlaceholderRoute(
                                    selectedAlbumId = state.selectedAlbumIds.first(),
                                ).copy(
                                    highlightMediaIds = state.selectedAppMediaIds.distinct(),
                                    focusMediaId = state.selectedAppMediaIds.firstOrNull(),
                                ),
                            )
                        }
                        is ApiResult.Error -> {
                            _uiState.update { it.copy(localMessage = result.toBackendUiMessage("创建小相册失败，请稍后重试。")) }
                        }
                        ApiResult.Loading -> Unit
                    }
                }
            } else {
                val selectedItems = state.selectedAppMediaIds.mapNotNull(FakePhotoFeedRepository::findPhotoFeedItem)
                val createdPost = FakeAlbumRepository.createConfiguredLocalPostFromPhotoFeedItems(
                    draft = draft,
                    mediaItems = selectedItems,
                )
                if (createdPost == null) {
                    _uiState.update { it.copy(localMessage = "本地新小相册创建失败，请稍后重试。") }
                } else {
                    onSuccess(
                        FakeAlbumRepository.toPostDetailRoute(createdPost).copy(
                            highlightMediaIds = state.selectedAppMediaIds.distinct(),
                            focusMediaId = state.selectedAppMediaIds.firstOrNull(),
                        ),
                    )
                }
            }
            return
        }

        // 分支 3: 无媒体
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val result = postRepository.createPost(
                CreatePostPayload(
                    title = draft.title.ifBlank { "新小相册" },
                    summary = draft.summary,
                    participantUserIds = draft.participantUserIds,
                    displayTimeMillis = draft.displayTimeMillis,
                    albumId = draft.requireAlbumId(),
                    coverMediaId = null,
                ),
            )
            _uiState.update { it.copy(isSubmitting = false) }
            when (result) {
                is ApiResult.Success -> {
                    if (mode == RepositoryMode.REAL) {
                        notifyRealBackendPostChanged(postIds = setOf(result.data.postId))
                    }
                    onSuccess(
                        result.data.toPostDetailPlaceholderRoute(
                            selectedAlbumId = state.selectedAlbumIds.first(),
                        ),
                    )
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(localMessage = result.toBackendUiMessage("创建小相册失败，请稍后重试。")) }
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    companion object {
        fun factory(route: CreatePostRoute): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PostComposerViewModel(route = route) as T
                }
            }
        }
    }
}
