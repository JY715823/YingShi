package com.example.yingshi.feature.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yingshi.data.model.CreateAlbumPayload
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.components.YingShiNotice
import com.example.yingshi.ui.components.YingShiNoticeTone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── UI State data classes ──────────────────────────────────────────────

data class PhotosRootDialogState(
    val showDeleteConfirm: Boolean = false,
    val showAddToPostDialog: Boolean = false,
    val showCreateAlbumDialog: Boolean = false,
    val addToPostDialogMessage: String? = null,
)

data class CreateAlbumDraft(
    val title: String = "",
    val subtitle: String = "",
    val errorMessage: String? = null,
    val isCreating: Boolean = false,
)

data class TrashUiState(
    val showPendingCleanup: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedEntryIds: List<String> = emptyList(),
    val selectedTypeName: String = "全部",
)

// ── Parameter groups (FR-7) ────────────────────────────────────────────

data class PhotosRootTrashParams(
    val selectedTypeName: String = "全部",
)

data class PhotosRootSelectionParams(
    val onAddedMediaToPost: (Any?) -> Unit = {},
    val photoFeedScrollTrigger: Long = 0L,
    val photoSelectionClearTrigger: Long = 0L,
)

// ── ViewModel ──────────────────────────────────────────────────────────

class PhotosRootViewModel : ViewModel() {

    // Dialog visibility
    private val _dialogState = MutableStateFlow(PhotosRootDialogState())
    val dialogState: StateFlow<PhotosRootDialogState> = _dialogState.asStateFlow()

    // Create album form
    private val _createAlbumDraft = MutableStateFlow(CreateAlbumDraft())
    val createAlbumDraft: StateFlow<CreateAlbumDraft> = _createAlbumDraft.asStateFlow()

    // Trash UI state
    private val _trashUiState = MutableStateFlow(TrashUiState())
    val trashUiState: StateFlow<TrashUiState> = _trashUiState.asStateFlow()

    // Notice
    private val _notice = MutableStateFlow<YingShiNotice?>(null)
    val notice: StateFlow<YingShiNotice?> = _notice.asStateFlow()
    private var noticeNonce = 0

    // Share guard
    private val _photoShareInFlight = MutableStateFlow(false)
    val photoShareInFlight: StateFlow<Boolean> = _photoShareInFlight.asStateFlow()

    // Inline video auto-play
    private val _inlineVideoAutoPlayEnabled = MutableStateFlow(false)
    val inlineVideoAutoPlayEnabled: StateFlow<Boolean> = _inlineVideoAutoPlayEnabled.asStateFlow()

    // Trash selection exit nonce (triggers BackHandler in Composable)
    private val _trashSelectionExitNonce = MutableStateFlow(0)
    val trashSelectionExitNonce: StateFlow<Int> = _trashSelectionExitNonce.asStateFlow()

    // ── Dialog actions ─────────────────────────────────────────────────

    fun updateDialogState(newState: PhotosRootDialogState) {
        _dialogState.value = newState
    }

    // ── Create album actions ───────────────────────────────────────────

    fun updateCreateAlbumDraft(newDraft: CreateAlbumDraft) {
        _createAlbumDraft.value = newDraft
    }

    fun resetCreateAlbumDraft() {
        _createAlbumDraft.value = CreateAlbumDraft()
    }

    fun createAlbum() {
        val draft = _createAlbumDraft.value
        if (draft.title.trim().isEmpty()) {
            _createAlbumDraft.value = draft.copy(errorMessage = "请先写一个大相册标题。")
            return
        }
        _createAlbumDraft.value = draft.copy(errorMessage = null, isCreating = true)
        viewModelScope.launch {
            when (
                val result = RepositoryProvider.albumRepository.createAlbum(
                    CreateAlbumPayload(
                        title = draft.title.trim(),
                        subtitle = draft.subtitle.trim(),
                    ),
                )
            ) {
                is ApiResult.Success -> {
                    AlbumPageStateStore.pendingSelectedAlbumId = result.data.albumId
                    if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
                        notifyRealBackendAlbumsChanged()
                    }
                    _createAlbumDraft.value = CreateAlbumDraft()
                    _dialogState.value = _dialogState.value.copy(showCreateAlbumDialog = false)
                    showNotice("已创建大相册", YingShiNoticeTone.SUCCESS)
                }

                is ApiResult.Error -> {
                    _createAlbumDraft.value = _createAlbumDraft.value.copy(
                        isCreating = false,
                        errorMessage = result.toBackendUiMessage("创建大相册失败，请稍后重试。"),
                    )
                }

                ApiResult.Loading -> Unit
            }
        }
    }

    // ── Trash actions ──────────────────────────────────────────────────

    fun updateTrashUiState(newState: TrashUiState) {
        _trashUiState.value = newState
    }

    fun exitTrashSelection() {
        _trashUiState.value = _trashUiState.value.copy(
            selectionMode = false,
            selectedEntryIds = emptyList(),
        )
        _trashSelectionExitNonce.value = _trashSelectionExitNonce.value + 1
    }

    fun initTrashTypeName(name: String) {
        if (_trashUiState.value.selectedTypeName == "全部") {
            _trashUiState.value = _trashUiState.value.copy(selectedTypeName = name)
        }
    }

    // ── Notice ─────────────────────────────────────────────────────────

    fun showNotice(
        message: String,
        tone: YingShiNoticeTone = YingShiNoticeTone.INFO,
    ) {
        noticeNonce += 1
        _notice.value = YingShiNotice(message = message, tone = tone, nonce = noticeNonce)
    }

    fun clearNotice(nonce: Int) {
        if (_notice.value?.nonce == nonce) {
            _notice.value = null
        }
    }

    // ── Share guard ────────────────────────────────────────────────────

    fun setShareInFlight(inFlight: Boolean) {
        _photoShareInFlight.value = inFlight
    }

    // ── Video auto-play ────────────────────────────────────────────────

    fun setInlineVideoAutoPlay(enabled: Boolean) {
        _inlineVideoAutoPlayEnabled.value = enabled
    }

    // ── Full reset (session change) ────────────────────────────────────

    fun resetAll() {
        _dialogState.value = PhotosRootDialogState()
        _createAlbumDraft.value = CreateAlbumDraft()
    }
}
