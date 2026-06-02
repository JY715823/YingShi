package com.example.yingshi.feature.chat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.chat.data.ChatImportProgress
import com.example.yingshi.feature.chat.data.ChatReadingAnchor
import com.example.yingshi.feature.chat.data.ImportedChatDetail
import com.example.yingshi.feature.chat.data.ImportedChatImportInfo
import com.example.yingshi.feature.chat.data.ImportedChatRepository
import com.example.yingshi.feature.chat.data.ImportedChatSummary
import com.example.yingshi.feature.chat.data.ImportedMessageSearchResult
import com.example.yingshi.feature.chat.data.ImportedMessageWindow
import com.example.yingshi.feature.chat.data.ImportedRenderableMessage
import com.example.yingshi.feature.chat.data.MessageWindowAnchor
import com.example.yingshi.feature.chat.data.NoOpChatSyncBridge
import com.example.yingshi.feature.chat.data.RemoteChatSyncBridge
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ImportedChatUiState(
    val chats: List<ImportedChatSummary> = emptyList(),
    val selectedChatId: Long? = null,
    val searchQuery: String = "",
    val isImporting: Boolean = false,
    val importProgress: ChatImportProgress? = null,
    val isLoadingChatWindow: Boolean = false,
    val isLoadingOlderPage: Boolean = false,
    val isLoadingNewerPage: Boolean = false,
    val hasOlderPage: Boolean = false,
    val hasNewerPage: Boolean = false,
    val pendingJumpMessageLocalId: Long? = null,
    val pendingJumpScrollOffset: Int = 0,
    val highlightedMessageLocalId: Long? = null,
    val showBackToLatest: Boolean = false,
    val returnAnchorAvailable: Boolean = false,
    val activeHighlightQuery: String = "",
    val activeJumpContext: ChatJumpSource? = null,
    val currentSearchResultIndex: Int = -1,
    val activeManagedChatId: Long? = null,
    val importInfoDialog: ImportedChatImportInfo? = null,
    val deleteConfirmInfo: ImportedChatImportInfo? = null,
    val pendingMessageAction: MessageActionPayload? = null,
    val message: String? = null,
)

private data class ImportUiState(
    val selectedChatId: Long?,
    val searchQuery: String,
    val isImporting: Boolean,
    val importProgress: ChatImportProgress?,
    val message: String?,
)

private data class ChatWindowUiState(
    val isLoadingChatWindow: Boolean,
    val isLoadingOlderPage: Boolean,
    val isLoadingNewerPage: Boolean,
    val window: ImportedMessageWindow,
    val pendingJumpMessageLocalId: Long?,
    val pendingJumpScrollOffset: Int,
    val activeJumpContext: ChatJumpSource?,
)

private data class ChatWindowLoadingState(
    val isLoadingChatWindow: Boolean,
    val isLoadingOlderPage: Boolean,
    val isLoadingNewerPage: Boolean,
    val window: ImportedMessageWindow,
)

private data class ChatPresentationState(
    val highlightedMessageLocalId: Long?,
    val showBackToLatest: Boolean,
    val returnAnchorAvailable: Boolean,
    val activeHighlightQuery: String,
    val currentSearchResultIndex: Int,
    val activeManagedChatId: Long?,
    val importInfoDialog: ImportedChatImportInfo?,
    val deleteConfirmInfo: ImportedChatImportInfo?,
    val pendingMessageAction: MessageActionPayload?,
)

enum class ChatJumpSource {
    SEARCH,
    DATE,
    REPLY,
    LATEST,
    RESTORE,
}

enum class MessageActionType {
    COPY_TEXT,
    COPY_SUMMARY,
    OPEN,
    VIEW,
    SHARE,
    AUDIO_TOGGLE,
}

data class MessageActionPayload(
    val message: ImportedRenderableMessage,
    val resource: com.example.yingshi.feature.chat.data.ImportedResource?,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ImportedChatViewModel(
    application: Application,
    private val repository: ImportedChatRepository = ImportedChatRepository(application),
) : AndroidViewModel(application) {

    init {
        viewModelScope.launch {
            runCatching {
                repository.hydrateFromRemoteIfNeeded()
            }
        }
        viewModelScope.launch {
            runCatching {
                repository.ensurePresentationMaintenance()
            }
        }
        viewModelScope.launch {
            ChatImportRuntime.state.collect { runtimeState ->
                isImporting.value = runtimeState.isRunning
                importProgress.value = runtimeState.progress
                runtimeState.error?.let {
                    message.value = it
                    ChatImportRuntime.clearTerminalState()
                }
                runtimeState.message?.let { summary ->
                    val importedChatId = runtimeState.importedChatId
                    if (importedChatId != null) {
                        selectedChatId.value = importedChatId
                        pendingJumpMessageLocalId.value = null
                        pendingJumpScrollOffset.value = 0
                        highlightedMessageLocalId.value = null
                        searchQuery.value = ""
                        activeHighlightQuery.value = ""
                        activeJumpContext.value = ChatJumpSource.LATEST
                        currentSearchResultIndex.value = -1
                        activeManagedChatId.value = null
                        importInfoDialog.value = null
                        deleteConfirmInfo.value = null
                        returnAnchor.value = null
                        showBackToLatest.value = false
                        loadLatestWindow(importedChatId)
                    }
                    message.value = summary
                    ChatImportRuntime.clearTerminalState()
                }
            }
        }
    }

    private val selectedChatId = MutableStateFlow<Long?>(null)
    private val searchQuery = MutableStateFlow("")
    private val isImporting = MutableStateFlow(false)
    private val importProgress = MutableStateFlow<ChatImportProgress?>(null)
    private val isLoadingChatWindow = MutableStateFlow(false)
    private val isLoadingOlderPage = MutableStateFlow(false)
    private val isLoadingNewerPage = MutableStateFlow(false)
    private val pendingJumpMessageLocalId = MutableStateFlow<Long?>(null)
    private val pendingJumpScrollOffset = MutableStateFlow(0)
    private val highlightedMessageLocalId = MutableStateFlow<Long?>(null)
    private val showBackToLatest = MutableStateFlow(false)
    private val returnAnchor = MutableStateFlow<ChatReadingAnchor?>(null)
    private val activeHighlightQuery = MutableStateFlow("")
    private val activeJumpContext = MutableStateFlow<ChatJumpSource?>(null)
    private val currentSearchResultIndex = MutableStateFlow(-1)
    private val activeManagedChatId = MutableStateFlow<Long?>(null)
    private val importInfoDialog = MutableStateFlow<ImportedChatImportInfo?>(null)
    private val deleteConfirmInfo = MutableStateFlow<ImportedChatImportInfo?>(null)
    private val pendingMessageAction = MutableStateFlow<MessageActionPayload?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val selectedChatWindow = MutableStateFlow(
        ImportedMessageWindow(
            items = emptyList(),
            startAnchor = null,
            endAnchor = null,
            hasOlder = false,
            hasNewer = false,
        ),
    )

    val chats: StateFlow<List<ImportedChatSummary>> = repository.observeChatSummaries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val selectedChatDetail: StateFlow<ImportedChatDetail?> = selectedChatId
        .flatMapLatest { chatId ->
            if (chatId == null) flowOf(null) else repository.observeChatDetail(chatId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val selectedChatMessages: StateFlow<List<ImportedRenderableMessage>> = selectedChatWindow
        .combine(selectedChatId) { window, chatId ->
            if (chatId == null) emptyList() else window.items
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val searchResults: StateFlow<List<ImportedMessageSearchResult>> = combine(
        selectedChatId,
        searchQuery,
    ) { chatId, query ->
        chatId to query
    }.flatMapLatest { (chatId, query) ->
        if (chatId == null || query.isBlank()) {
            flowOf(emptyList())
        } else {
            repository.searchChatMessages(chatId, query)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val importUiState = combine(
        selectedChatId,
        searchQuery,
        isImporting,
        importProgress,
        message,
    ) { selectedChatIdValue,
        searchQueryValue,
        isImportingValue,
        importProgressValue,
        messageValue,
        ->
        ImportUiState(
            selectedChatId = selectedChatIdValue,
            searchQuery = searchQueryValue,
            isImporting = isImportingValue,
            importProgress = importProgressValue,
            message = messageValue,
        )
    }

    private val chatWindowUiState = combine(
        combine(
            isLoadingChatWindow,
            isLoadingOlderPage,
            isLoadingNewerPage,
            selectedChatWindow,
        ) { isLoadingChatWindowValue, isLoadingOlderPageValue, isLoadingNewerPageValue, selectedChatWindowValue ->
            ChatWindowLoadingState(
                isLoadingChatWindow = isLoadingChatWindowValue,
                isLoadingOlderPage = isLoadingOlderPageValue,
                isLoadingNewerPage = isLoadingNewerPageValue,
                window = selectedChatWindowValue,
            )
        },
        combine(
            pendingJumpMessageLocalId,
            pendingJumpScrollOffset,
            activeJumpContext,
        ) { pendingJumpValue, pendingJumpScrollOffsetValue, jumpContextValue ->
            ChatWindowUiState(
                isLoadingChatWindow = false,
                isLoadingOlderPage = false,
                isLoadingNewerPage = false,
                window = ImportedMessageWindow(emptyList(), null, null, false, false),
                pendingJumpMessageLocalId = pendingJumpValue,
                pendingJumpScrollOffset = pendingJumpScrollOffsetValue,
                activeJumpContext = jumpContextValue,
            )
        },
    ) { loadingState, jumpState ->
        ChatWindowUiState(
            isLoadingChatWindow = loadingState.isLoadingChatWindow,
            isLoadingOlderPage = loadingState.isLoadingOlderPage,
            isLoadingNewerPage = loadingState.isLoadingNewerPage,
            window = loadingState.window,
            pendingJumpMessageLocalId = jumpState.pendingJumpMessageLocalId,
            pendingJumpScrollOffset = jumpState.pendingJumpScrollOffset,
            activeJumpContext = jumpState.activeJumpContext,
        )
    }

    private val chatPresentationState = combine(
        highlightedMessageLocalId,
        showBackToLatest,
        returnAnchor,
        activeHighlightQuery,
        currentSearchResultIndex,
        activeManagedChatId,
        importInfoDialog,
        deleteConfirmInfo,
        pendingMessageAction,
    ) { values ->
        val highlightedValue = values[0] as Long?
        val showBackToLatestValue = values[1] as Boolean
        val returnAnchorValue = values[2] as ChatReadingAnchor?
        val highlightQueryValue = values[3] as String
        val currentSearchResultIndexValue = values[4] as Int
        val activeManagedChatIdValue = values[5] as Long?
        val importInfoDialogValue = values[6] as ImportedChatImportInfo?
        val deleteConfirmInfoValue = values[7] as ImportedChatImportInfo?
        val pendingActionValue = values[8] as MessageActionPayload?
        ChatPresentationState(
            highlightedMessageLocalId = highlightedValue,
            showBackToLatest = showBackToLatestValue,
            returnAnchorAvailable = returnAnchorValue != null,
            activeHighlightQuery = highlightQueryValue,
            currentSearchResultIndex = currentSearchResultIndexValue,
            activeManagedChatId = activeManagedChatIdValue,
            importInfoDialog = importInfoDialogValue,
            deleteConfirmInfo = deleteConfirmInfoValue,
            pendingMessageAction = pendingActionValue,
        )
    }

    val uiState: StateFlow<ImportedChatUiState> = combine(
        chats,
        importUiState,
        chatWindowUiState,
        chatPresentationState,
    ) { chatsValue, importState, windowState, presentationState ->
        ImportedChatUiState(
            chats = chatsValue,
            selectedChatId = importState.selectedChatId,
            searchQuery = importState.searchQuery,
            isImporting = importState.isImporting,
            importProgress = importState.importProgress,
            isLoadingChatWindow = windowState.isLoadingChatWindow,
            isLoadingOlderPage = windowState.isLoadingOlderPage,
            isLoadingNewerPage = windowState.isLoadingNewerPage,
            hasOlderPage = windowState.window.hasOlder,
            hasNewerPage = windowState.window.hasNewer,
            pendingJumpMessageLocalId = windowState.pendingJumpMessageLocalId,
            pendingJumpScrollOffset = windowState.pendingJumpScrollOffset,
            highlightedMessageLocalId = presentationState.highlightedMessageLocalId,
            showBackToLatest = presentationState.showBackToLatest,
            returnAnchorAvailable = presentationState.returnAnchorAvailable,
            activeHighlightQuery = presentationState.activeHighlightQuery,
            activeJumpContext = windowState.activeJumpContext,
            currentSearchResultIndex = presentationState.currentSearchResultIndex,
            activeManagedChatId = presentationState.activeManagedChatId,
            importInfoDialog = presentationState.importInfoDialog,
            deleteConfirmInfo = presentationState.deleteConfirmInfo,
            pendingMessageAction = presentationState.pendingMessageAction,
            message = importState.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ImportedChatUiState(),
    )

    fun openChat(chatId: Long) {
        selectedChatId.value = chatId
        pendingJumpMessageLocalId.value = null
        pendingJumpScrollOffset.value = 0
        highlightedMessageLocalId.value = null
        searchQuery.value = ""
        activeHighlightQuery.value = ""
        activeJumpContext.value = null
        currentSearchResultIndex.value = -1
        activeManagedChatId.value = null
        importInfoDialog.value = null
        deleteConfirmInfo.value = null
        pendingMessageAction.value = null
        showBackToLatest.value = false
        loadInitialWindow(chatId)
    }

    fun closeChat(anchor: ChatReadingAnchor? = null, persistAnchor: Boolean = true) {
        if (persistAnchor) {
            persistCurrentReadingAnchor(anchor)
        }
        selectedChatId.value = null
        pendingJumpMessageLocalId.value = null
        pendingJumpScrollOffset.value = 0
        highlightedMessageLocalId.value = null
        searchQuery.value = ""
        activeHighlightQuery.value = ""
        activeJumpContext.value = null
        currentSearchResultIndex.value = -1
        activeManagedChatId.value = null
        importInfoDialog.value = null
        deleteConfirmInfo.value = null
        pendingMessageAction.value = null
        showBackToLatest.value = false
        returnAnchor.value = null
        selectedChatWindow.value = ImportedMessageWindow(
            items = emptyList(),
            startAnchor = null,
            endAnchor = null,
            hasOlder = false,
            hasNewer = false,
        )
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
        currentSearchResultIndex.value = if (query.isBlank()) -1 else 0
    }

    fun clearSearch() {
        searchQuery.value = ""
        activeHighlightQuery.value = ""
        currentSearchResultIndex.value = -1
    }

    fun importFromZip(uri: Uri, expectedChatId: Long? = null) {
        if (isImporting.value) return
        isImporting.value = true
        importProgress.value = ChatImportProgress(
            stage = "prepare",
            message = "准备导入聊天记录",
            current = 0,
            total = 1,
        )
        message.value = null
    }

    fun loadOlderPage() {
        val chatId = selectedChatId.value ?: return
        val anchor = selectedChatWindow.value.startAnchor ?: return
        if (isLoadingOlderPage.value || !selectedChatWindow.value.hasOlder) return
        viewModelScope.launch {
            isLoadingOlderPage.value = true
            runCatching {
                val older = repository.loadOlderMessages(chatId, anchor)
                if (older.isNotEmpty()) {
                    val merged = older + selectedChatWindow.value.items
                    val newStart = merged.first().message.toAnchor()
                    val currentEnd = merged.lastOrNull()?.message?.toAnchor()
                    selectedChatWindow.value = selectedChatWindow.value.copy(
                        items = merged.distinctBy { it.message.messageLocalId },
                        startAnchor = newStart,
                        endAnchor = currentEnd,
                        hasOlder = repository.hasOlderMessages(chatId, newStart),
                        hasNewer = currentEnd?.let { repository.hasNewerMessages(chatId, it) } ?: false,
                    )
                } else {
                    selectedChatWindow.value = selectedChatWindow.value.copy(hasOlder = false)
                }
            }
            isLoadingOlderPage.value = false
        }
    }

    fun loadNewerPage() {
        val chatId = selectedChatId.value ?: return
        val anchor = selectedChatWindow.value.endAnchor ?: return
        if (isLoadingNewerPage.value || !selectedChatWindow.value.hasNewer) return
        viewModelScope.launch {
            isLoadingNewerPage.value = true
            runCatching {
                val newer = repository.loadNewerMessages(chatId, anchor)
                if (newer.isNotEmpty()) {
                    val merged = (selectedChatWindow.value.items + newer).distinctBy { it.message.messageLocalId }
                    val newStart = merged.firstOrNull()?.message?.toAnchor()
                    val newEnd = merged.lastOrNull()?.message?.toAnchor()
                    selectedChatWindow.value = selectedChatWindow.value.copy(
                        items = merged,
                        startAnchor = newStart,
                        endAnchor = newEnd,
                        hasOlder = newStart?.let { repository.hasOlderMessages(chatId, it) } ?: false,
                        hasNewer = newEnd?.let { repository.hasNewerMessages(chatId, it) } ?: false,
                    )
                } else {
                    selectedChatWindow.value = selectedChatWindow.value.copy(hasNewer = false)
                }
            }
            isLoadingNewerPage.value = false
        }
    }

    fun jumpToDate(date: LocalDate) {
        val chatId = selectedChatId.value ?: return
        viewModelScope.launch {
            val messageLocalId = repository.jumpToDate(chatId, date)
            if (messageLocalId == null) {
                message.value = "这一天附近没有找到消息。"
            } else {
                jumpToMessage(messageLocalId, ChatJumpSource.DATE)
            }
        }
    }

    fun jumpToReply(renderableMessage: ImportedRenderableMessage) {
        val chatId = selectedChatId.value ?: return
        viewModelScope.launch {
            val replySegment = renderableMessage.segments
                .filterIsInstance<com.example.yingshi.feature.chat.data.ImportedMessageSegment.Reply>()
                .firstOrNull()
            val target = repository.resolveReplyTarget(
                chatId = chatId,
                sourceMessageId = replySegment?.sourceMessageId ?: renderableMessage.message.replyToSourceMessageId,
                replyReferenceMessageId = replySegment?.referencedMessageId ?: renderableMessage.message.replyReferenceMessageId,
                replyReferenceSenderUin = replySegment?.senderUin ?: renderableMessage.message.replyReferenceSenderUin,
                replyReferenceTimestampSeconds = replySegment?.timestampSeconds ?: renderableMessage.message.replyReferenceTimestampSeconds,
                replyReferenceContent = replySegment?.content ?: renderableMessage.message.replyReferenceContent,
            )
            if (target == null) {
                message.value = "没能定位到这条被回复的消息，可能这部分内容没有导入完整。"
            } else {
                jumpToMessage(target, ChatJumpSource.REPLY)
            }
        }
    }

    fun jumpToMessage(messageLocalId: Long, source: ChatJumpSource = ChatJumpSource.SEARCH) {
        val chatId = selectedChatId.value ?: return
        viewModelScope.launch {
            isLoadingChatWindow.value = true
            runCatching {
                selectedChatWindow.value = repository.loadWindowAroundMessage(chatId, messageLocalId)
                pendingJumpMessageLocalId.value = messageLocalId
                pendingJumpScrollOffset.value = 0
                highlightedMessageLocalId.value = messageLocalId
                activeJumpContext.value = source
                activeHighlightQuery.value = if (source == ChatJumpSource.SEARCH) {
                    searchQuery.value.trim()
                } else {
                    activeHighlightQuery.value
                }
                if (source == ChatJumpSource.SEARCH && currentSearchResultIndex.value < 0) {
                    currentSearchResultIndex.value = 0
                }
            }.onFailure {
                message.value = "跳转失败，请稍后再试。"
            }
            isLoadingChatWindow.value = false
        }
    }

    fun consumePendingJumpTarget() {
        pendingJumpMessageLocalId.value = null
        pendingJumpScrollOffset.value = 0
    }

    fun clearJumpContext() {
        activeJumpContext.value = null
    }

    fun clearHighlight() {
        highlightedMessageLocalId.value = null
    }

    fun setBackToLatestVisible(visible: Boolean) {
        showBackToLatest.value = visible
    }

    fun saveReadingAnchor(anchor: ChatReadingAnchor) {
        val chatId = selectedChatId.value ?: return
        viewModelScope.launch {
            repository.saveReadingAnchor(chatId, anchor)
        }
    }

    fun jumpToLatest() {
        val chatId = selectedChatId.value ?: return
        viewModelScope.launch {
            showBackToLatest.value = false
            activeHighlightQuery.value = ""
            activeJumpContext.value = ChatJumpSource.LATEST
            isLoadingChatWindow.value = true
            runCatching {
                selectedChatWindow.value = repository.loadLatestMessageWindow(chatId)
            }.onFailure {
                message.value = "回到最新失败。"
            }
            isLoadingChatWindow.value = false
        }
    }

    fun returnToPreviousAnchor() {
        val chatId = selectedChatId.value ?: return
        val anchor = returnAnchor.value ?: return
        viewModelScope.launch {
            isLoadingChatWindow.value = true
            runCatching {
                selectedChatWindow.value = repository.loadWindowAroundMessage(chatId, anchor.messageLocalId)
                pendingJumpMessageLocalId.value = anchor.messageLocalId
                pendingJumpScrollOffset.value = anchor.scrollOffset
                highlightedMessageLocalId.value = anchor.messageLocalId
                activeJumpContext.value = ChatJumpSource.RESTORE
                activeHighlightQuery.value = ""
            }.onFailure {
                message.value = "恢复刚才位置失败。"
            }
            isLoadingChatWindow.value = false
            returnAnchor.value = null
            showBackToLatest.value = false
        }
    }

    fun clearReturnAnchor() {
        returnAnchor.value = null
    }

    fun rememberReturnAnchor(anchor: ChatReadingAnchor?) {
        returnAnchor.value = anchor
    }

    fun showMessageActions(payload: MessageActionPayload) {
        pendingMessageAction.value = payload
    }

    fun clearMessageActions() {
        pendingMessageAction.value = null
    }

    fun showChatManagement(chatId: Long) {
        activeManagedChatId.value = chatId
    }

    fun hideChatManagement() {
        activeManagedChatId.value = null
    }

    fun showDeleteConfirm(chatId: Long) {
        viewModelScope.launch {
            activeManagedChatId.value = null
            deleteConfirmInfo.value = repository.getImportedChatImportInfo(chatId)
        }
    }

    fun hideDeleteConfirm() {
        deleteConfirmInfo.value = null
    }

    fun loadImportInfo(chatId: Long) {
        viewModelScope.launch {
            importInfoDialog.value = repository.getImportedChatImportInfo(chatId)
            activeManagedChatId.value = null
        }
    }

    fun clearImportInfoDialog() {
        importInfoDialog.value = null
    }

    fun deleteImportedChat(chatId: Long) {
        viewModelScope.launch {
            val deleted = repository.deleteImportedChat(chatId)
            deleteConfirmInfo.value = null
            activeManagedChatId.value = null
            if (!deleted) {
                message.value = "删除失败，会话可能已经不存在。"
                return@launch
            }
            if (selectedChatId.value == chatId) {
                closeChat(persistAnchor = false)
            }
            message.value = "会话已删除。"
        }
    }

    fun jumpToSearchResult(results: List<ImportedMessageSearchResult>, index: Int) {
        if (results.isEmpty()) return
        val resolvedIndex = index.coerceIn(0, results.lastIndex)
        currentSearchResultIndex.value = resolvedIndex
        jumpToMessage(results[resolvedIndex].messageLocalId, ChatJumpSource.SEARCH)
    }

    fun jumpToNextSearchResult(results: List<ImportedMessageSearchResult>) {
        if (results.isEmpty()) return
        val nextIndex = (currentSearchResultIndex.value + 1).coerceAtMost(results.lastIndex)
        if (nextIndex == currentSearchResultIndex.value) return
        jumpToSearchResult(results, nextIndex)
    }

    fun jumpToPreviousSearchResult(results: List<ImportedMessageSearchResult>) {
        if (results.isEmpty()) return
        val previousIndex = if (currentSearchResultIndex.value < 0) 0 else (currentSearchResultIndex.value - 1).coerceAtLeast(0)
        if (previousIndex == currentSearchResultIndex.value) return
        jumpToSearchResult(results, previousIndex)
    }

    fun consumeMessage() {
        message.value = null
    }

    private fun persistCurrentReadingAnchor(anchor: ChatReadingAnchor?) {
        val chatId = selectedChatId.value ?: return
        val resolvedAnchor = anchor ?: selectedChatWindow.value.endAnchor?.let { endAnchor ->
            ChatReadingAnchor(
                messageLocalId = endAnchor.messageLocalId,
                scrollOffset = 0,
                savedAtMillis = System.currentTimeMillis(),
            )
        } ?: return
        viewModelScope.launch {
            repository.saveReadingAnchor(
                chatId = chatId,
                anchor = resolvedAnchor,
            )
        }
    }

    private fun loadInitialWindow(chatId: Long) {
        viewModelScope.launch {
            isLoadingChatWindow.value = true
            val restored = runCatching {
                repository.loadReadingAnchor(chatId)
            }.getOrNull()
            if (restored != null) {
                runCatching {
                    selectedChatWindow.value = repository.loadWindowAroundMessage(chatId, restored.messageLocalId)
                    pendingJumpMessageLocalId.value = restored.messageLocalId
                    pendingJumpScrollOffset.value = restored.scrollOffset
                    highlightedMessageLocalId.value = restored.messageLocalId
                    activeJumpContext.value = ChatJumpSource.RESTORE
                }.onFailure {
                    runCatching {
                        selectedChatWindow.value = repository.loadLatestMessageWindow(chatId)
                    }.onFailure {
                        selectedChatWindow.value = ImportedMessageWindow(
                            items = emptyList(),
                            startAnchor = null,
                            endAnchor = null,
                            hasOlder = false,
                            hasNewer = false,
                        )
                        message.value = "加载聊天记录失败。"
                    }
                }
            } else {
                runCatching {
                    selectedChatWindow.value = repository.loadLatestMessageWindow(chatId)
                }.onFailure {
                    selectedChatWindow.value = ImportedMessageWindow(
                        items = emptyList(),
                        startAnchor = null,
                        endAnchor = null,
                        hasOlder = false,
                        hasNewer = false,
                    )
                    message.value = "加载聊天记录失败。"
                }
            }
            isLoadingChatWindow.value = false
        }
    }

    private fun loadLatestWindow(chatId: Long) {
        viewModelScope.launch {
            isLoadingChatWindow.value = true
            runCatching {
                selectedChatWindow.value = repository.loadLatestMessageWindow(chatId)
                pendingJumpMessageLocalId.value = null
                pendingJumpScrollOffset.value = 0
            }.onFailure {
                selectedChatWindow.value = ImportedMessageWindow(
                    items = emptyList(),
                    startAnchor = null,
                    endAnchor = null,
                    hasOlder = false,
                    hasNewer = false,
                )
                message.value = "加载聊天记录失败。"
            }
            isLoadingChatWindow.value = false
        }
    }

    private fun com.example.yingshi.feature.chat.data.ImportedMessage.toAnchor(): MessageWindowAnchor {
        return MessageWindowAnchor(
            timestamp = timestamp,
            messageLocalId = messageLocalId,
        )
    }

    companion object {
        private fun createRepository(application: Application): ImportedChatRepository {
            val syncBridge = if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
                RemoteChatSyncBridge()
            } else {
                NoOpChatSyncBridge
            }
            return ImportedChatRepository(
                appContext = application,
                syncBridge = syncBridge,
            )
        }

        fun factory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ImportedChatViewModel(
                        application = application,
                        repository = createRepository(application),
                    ) as T
                }
            }
        }
    }
}
