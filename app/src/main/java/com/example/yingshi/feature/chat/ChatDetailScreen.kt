package com.example.yingshi.feature.chat

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.South
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yingshi.feature.chat.data.ChatReadingAnchor
import com.example.yingshi.feature.chat.data.ImportedChatDetail
import com.example.yingshi.feature.chat.data.ImportedChatImportInfo
import com.example.yingshi.feature.chat.data.ImportedChatSummary
import com.example.yingshi.feature.chat.data.ImportedMessageSearchResult
import com.example.yingshi.feature.chat.data.ImportedParticipant
import com.example.yingshi.feature.chat.data.ImportedRenderableMessage
import com.example.yingshi.feature.chat.data.ImportedResource
import com.example.yingshi.feature.chat.data.ImportedResourceRenderKind
import com.example.yingshi.feature.chat.data.ImportedViewerMode
import com.example.yingshi.feature.chat.data.buildImportedMessagePresentation
import com.example.yingshi.feature.chat.data.effectiveRenderKind
import com.example.yingshi.feature.chat.data.isImageLikeResource
import com.example.yingshi.feature.chat.data.isMediaViewerResource
import com.example.yingshi.ui.components.YingShiNotice
import com.example.yingshi.ui.components.YingShiNoticeHost
import com.example.yingshi.ui.components.YingShiNoticeTone
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Local types ───────────────────────────────────────────────────────────────

private data class RestoreScrollAnchor(
    val messageLocalId: Long,
    val scrollOffset: Int,
)

private enum class EdgePullDirection {
    TOP,
    BOTTOM,
}

private sealed interface ChatTimelineItem {
    data class DayHeader(val key: String, val date: LocalDate) : ChatTimelineItem
    data class TimeHint(val key: String, val timestamp: Long) : ChatTimelineItem
    data class MessageRow(
        val key: String,
        val message: ImportedRenderableMessage,
        val participant: ImportedParticipant?,
        val isConsecutive: Boolean = false,
    ) : ChatTimelineItem
}

// ── Main entry point ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportedChatScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: ImportedChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = ImportedChatViewModel.factory(LocalContext.current.applicationContext as Application),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val chats by viewModel.chats.collectAsState()
    val chatDetail by viewModel.selectedChatDetail.collectAsState()
    val messages by viewModel.selectedChatMessages.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val sharedAudioPlayer = rememberChatAudioPlayer()
    var mediaViewerItems by remember { mutableStateOf<List<ChatViewerMediaItem>>(emptyList()) }
    var mediaViewerInitialIndex by remember { mutableIntStateOf(0) }
    var mediaViewerMode by remember { mutableStateOf(ImportedViewerMode.MEDIA) }
    var mediaViewerVisible by remember { mutableStateOf(false) }
    var pdfPreview by remember { mutableStateOf<PdfPreviewPayload?>(null) }
    var notice by remember { mutableStateOf<YingShiNotice?>(null) }
    var noticeNonce by remember { mutableIntStateOf(0) }

    fun showNotice(
        message: String,
        tone: YingShiNoticeTone = YingShiNoticeTone.INFO,
    ) {
        noticeNonce += 1
        notice = YingShiNotice(message = message, tone = tone, nonce = noticeNonce)
    }

    BackHandler(enabled = mediaViewerVisible) {
        mediaViewerVisible = false
    }
    BackHandler(enabled = pdfPreview != null) {
        pdfPreview = null
    }
    BackHandler(enabled = !mediaViewerVisible && pdfPreview == null && uiState.selectedChatId != null) {
        viewModel.closeChat()
    }
    BackHandler(enabled = !mediaViewerVisible && pdfPreview == null && uiState.selectedChatId == null) {
        onBack()
    }

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importFromZip(uri, uiState.activeManagedChatId)
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            showNotice(message)
            viewModel.consumeMessage()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        // #9 Page transition animation
        AnimatedContent(
            targetState = uiState.selectedChatId,
            transitionSpec = {
                (slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                ) + fadeIn()) togetherWith androidx.compose.animation.fadeOut(animationSpec = tween(200))
            },
            label = "chatPageTransition",
        ) { selectedChatId ->
            if (selectedChatId == null) {
                ImportedChatListScreen(
                    modifier = Modifier.fillMaxSize(),
                    chats = chats,
                    uiState = uiState,
                    onBack = onBack,
                    onImportClick = {
                        runCatching {
                            zipPickerLauncher.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/octet-stream",
                                ),
                            )
                        }.onFailure {
                            showNotice("无法打开文件选择器。", YingShiNoticeTone.WARNING)
                        }
                    },
                    onOpenChat = viewModel::openChat,
                    onManageChat = viewModel::showChatManagement,
                )
            } else {
                ImportedChatDetailScreen(
                    modifier = Modifier.fillMaxSize(),
                    uiState = uiState,
                    detail = chatDetail,
                    messages = messages,
                    searchResults = searchResults,
                    audioPlayer = sharedAudioPlayer,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onClearSearch = viewModel::clearSearch,
                    onJumpToDate = viewModel::jumpToDate,
                    onJumpToReply = viewModel::jumpToReply,
                    onJumpToMessage = viewModel::jumpToMessage,
                    onJumpToSearchResult = { index -> viewModel.jumpToSearchResult(searchResults, index) },
                    onJumpToPreviousSearchResult = { viewModel.jumpToPreviousSearchResult(searchResults) },
                    onJumpToNextSearchResult = { viewModel.jumpToNextSearchResult(searchResults) },
                    onConsumePendingJump = viewModel::consumePendingJumpTarget,
                    onClearJumpContext = viewModel::clearJumpContext,
                    onClearHighlight = viewModel::clearHighlight,
                    onLoadOlderPage = viewModel::loadOlderPage,
                    onLoadNewerPage = viewModel::loadNewerPage,
                    onRememberReturnAnchor = viewModel::rememberReturnAnchor,
                    onSaveReadingAnchor = viewModel::saveReadingAnchor,
                    onJumpToLatest = viewModel::jumpToLatest,
                    onReturnToPreviousAnchor = viewModel::returnToPreviousAnchor,
                    onBack = { anchor -> viewModel.closeChat(anchor) },
                    onSetBackToLatestVisible = viewModel::setBackToLatestVisible,
                    onShowMessageActions = viewModel::showMessageActions,
                    onShowChatManagement = { chatDetail?.chatId?.let(viewModel::showChatManagement) },
                    onOpenResource = { resource, message ->
                        when (resource.effectiveRenderKind()) {
                            ImportedResourceRenderKind.IMAGE,
                            ImportedResourceRenderKind.STICKER,
                            ImportedResourceRenderKind.VIDEO,
                            -> {
                                val viewerItems = buildViewerItems(messages, ImportedViewerMode.MEDIA)
                                val clickedKey = viewerItemStableKey(message.message.messageLocalId, resource)
                                mediaViewerMode = ImportedViewerMode.MEDIA
                                mediaViewerInitialIndex = viewerItems.indexOfFirst { it.stableKey == clickedKey }
                                    .coerceAtLeast(0)
                                mediaViewerItems = viewerItems
                                mediaViewerVisible = viewerItems.isNotEmpty()
                            }

                            ImportedResourceRenderKind.FILE -> {
                                if (isPdfResource(resource)) {
                                    pdfPreview = PdfPreviewPayload(
                                        path = resource.localFilePath,
                                        title = resource.originalFileName ?: resource.storedFileName,
                                    )
                                } else {
                                    openImportedFile(
                                        context = context,
                                        absolutePath = resource.localFilePath,
                                        mimeType = resource.resolvedMimeType,
                                    )?.let { showNotice(it, YingShiNoticeTone.WARNING) }
                                }
                            }

                            ImportedResourceRenderKind.AUDIO,
                            ImportedResourceRenderKind.UNKNOWN,
                            -> Unit
                        }
                    },
                    onOpenFile = { resource ->
                        if (isPdfResource(resource)) {
                            pdfPreview = PdfPreviewPayload(
                                path = resource.localFilePath,
                                title = resource.originalFileName ?: resource.storedFileName,
                            )
                        } else {
                            openImportedFile(
                                context = context,
                                absolutePath = resource.localFilePath,
                                mimeType = resource.resolvedMimeType,
                            )?.let { showNotice(it, YingShiNoticeTone.WARNING) }
                        }
                    },
                )
            }
        }

        pdfPreview?.let { payload ->
            ImportedChatPdfPreview(
                payload = payload,
                onDismiss = { pdfPreview = null },
            )
        }

        if (mediaViewerVisible) {
            ImportedChatMediaViewer(
                items = mediaViewerItems,
                initialIndex = mediaViewerInitialIndex,
                viewerMode = mediaViewerMode,
                onDismiss = { mediaViewerVisible = false },
            )
        }

        uiState.pendingMessageAction?.let { payload ->
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val options = remember(payload, messages) {
                buildMessageActionOptions(
                    payload = payload,
                    audioPlayer = sharedAudioPlayer,
                    onDismiss = viewModel::clearMessageActions,
                    onOpenResource = { resource, message ->
                        when (resource.effectiveRenderKind()) {
                            ImportedResourceRenderKind.IMAGE,
                            ImportedResourceRenderKind.STICKER,
                            ImportedResourceRenderKind.VIDEO,
                            -> {
                                val viewerItems = buildViewerItems(messages, ImportedViewerMode.MEDIA)
                                val clickedKey = viewerItemStableKey(message.message.messageLocalId, resource)
                                mediaViewerMode = ImportedViewerMode.MEDIA
                                mediaViewerInitialIndex = viewerItems.indexOfFirst { it.stableKey == clickedKey }.coerceAtLeast(0)
                                mediaViewerItems = viewerItems
                                mediaViewerVisible = viewerItems.isNotEmpty()
                            }

                            ImportedResourceRenderKind.FILE -> {
                                if (isPdfResource(resource)) {
                                    pdfPreview = PdfPreviewPayload(
                                        path = resource.localFilePath,
                                        title = resource.originalFileName ?: resource.storedFileName,
                                    )
                                } else {
                                    openImportedFile(
                                        context = context,
                                        absolutePath = resource.localFilePath,
                                        mimeType = resource.resolvedMimeType,
                                    )?.let { showNotice(it, YingShiNoticeTone.WARNING) }
                                }
                            }

                            ImportedResourceRenderKind.AUDIO,
                            ImportedResourceRenderKind.UNKNOWN,
                            -> Unit
                        }
                    },
                    onOpenFile = { resource ->
                        if (isPdfResource(resource)) {
                            pdfPreview = PdfPreviewPayload(
                                path = resource.localFilePath,
                                title = resource.originalFileName ?: resource.storedFileName,
                            )
                        } else {
                            openImportedFile(
                                context = context,
                                absolutePath = resource.localFilePath,
                                mimeType = resource.resolvedMimeType,
                            )?.let { showNotice(it, YingShiNoticeTone.WARNING) }
                        }
                    },
                    onCopyText = { text ->
                        clipboardManager.setText(AnnotatedString(text))
                        showNotice("已复制", YingShiNoticeTone.SUCCESS)
                    },
                    onShareFile = { resource ->
                        shareImportedFile(
                            context = context,
                            absolutePath = resource.localFilePath,
                            mimeType = resource.resolvedMimeType,
                        )?.let { showNotice(it, YingShiNoticeTone.WARNING) }
                    },
                    onToggleAudio = { resource, audioPlayerState ->
                        audioPlayerState.toggle(resource)
                    },
                )
            }
            ModalBottomSheet(
                onDismissRequest = viewModel::clearMessageActions,
                sheetState = sheetState,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = YingShiThemeTokens.spacing.md, vertical = YingShiThemeTokens.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
                ) {
                    Text(
                        text = "消息操作",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    options.forEach { option ->
                        ChatSheetActionButton(
                            label = option.label,
                            icon = option.icon,
                            onClick = option.onClick,
                        )
                    }
                }
            }
        }

        uiState.activeManagedChatId?.let { managedChatId ->
            val managedChat = chats.firstOrNull { it.chatId == managedChatId }
            if (managedChat != null) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = viewModel::hideChatManagement,
                    sheetState = sheetState,
                ) {
                    ChatManagementSheet(
                        chat = managedChat,
                        onReimport = {
                            viewModel.hideChatManagement()
                            runCatching {
                                zipPickerLauncher.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/x-zip-compressed",
                                        "application/octet-stream",
                                    ),
                                )
                            }.onFailure {
                                showNotice("无法打开文件选择器。", YingShiNoticeTone.WARNING)
                            }
                        },
                        onShowImportInfo = { viewModel.loadImportInfo(managedChat.chatId) },
                        onDelete = { viewModel.showDeleteConfirm(managedChat.chatId) },
                    )
                }
            }
        }

        uiState.importInfoDialog?.let { info ->
            ImportInfoDialog(
                info = info,
                onDismiss = viewModel::clearImportInfoDialog,
            )
        }

        uiState.deleteConfirmInfo?.let { info ->
            val targetName = info.displayName.ifBlank { "这个会话" }
            AlertDialog(
                onDismissRequest = viewModel::hideDeleteConfirm,
                title = { Text("删除会话") },
                text = {
                    Text(
                        "删除后会移除这个会话的消息、资源、头像和阅读位置，且无法恢复。" +
                            " 当前本地占用 ${formatStorageSize(context, info.storageBytes)}。" +
                            " 是否继续删除\u201C$targetName\u201D？",
                    )
                },
                containerColor = YingShiThemeTokens.colors.raisedSurface,
                titleContentColor = YingShiThemeTokens.colors.titleAccent,
                textContentColor = YingShiThemeTokens.colors.textSecondary,
                confirmButton = {
                    ChatDialogActionButton(
                        text = "删除",
                        danger = true,
                        onClick = { viewModel.deleteImportedChat(info.chatId) },
                    )
                },
                dismissButton = {
                    ChatDialogActionButton(text = "取消", onClick = viewModel::hideDeleteConfirm)
                },
            )
        }

        YingShiNoticeHost(
            notice = notice,
            modifier = Modifier.align(Alignment.TopCenter),
            onExpired = {
                if (notice?.nonce == it) {
                    notice = null
                }
            },
        )
    }
}

// ── Chat detail screen ────────────────────────────────────────────────────────

@Composable
private fun ImportedChatDetailScreen(
    uiState: ImportedChatUiState,
    detail: ImportedChatDetail?,
    messages: List<ImportedRenderableMessage>,
    searchResults: List<ImportedMessageSearchResult>,
    audioPlayer: ChatAudioPlayerState,
    onBack: (ChatReadingAnchor?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onJumpToDate: (LocalDate) -> Unit,
    onJumpToReply: (ImportedRenderableMessage) -> Unit,
    onJumpToMessage: (Long) -> Unit,
    onJumpToSearchResult: (Int) -> Unit,
    onJumpToPreviousSearchResult: () -> Unit,
    onJumpToNextSearchResult: () -> Unit,
    onConsumePendingJump: () -> Unit,
    onClearJumpContext: () -> Unit,
    onClearHighlight: () -> Unit,
    onLoadOlderPage: () -> Unit,
    onLoadNewerPage: () -> Unit,
    onRememberReturnAnchor: (ChatReadingAnchor?) -> Unit,
    onSaveReadingAnchor: (ChatReadingAnchor) -> Unit,
    onJumpToLatest: () -> Unit,
    onReturnToPreviousAnchor: () -> Unit,
    onSetBackToLatestVisible: (Boolean) -> Unit,
    onShowMessageActions: (MessageActionPayload) -> Unit,
    onShowChatManagement: () -> Unit,
    onOpenResource: (ImportedResource, ImportedRenderableMessage) -> Unit,
    onOpenFile: (ImportedResource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val qFaceCatalog = rememberQFaceCatalog()
    val listState = rememberLazyListState()
    val participants = remember(detail?.participants) {
        detail?.participants.orEmpty()
    }
    val timelineItems = remember(messages, participants) {
        buildTimelineItems(messages, participants)
    }
    // #7 Date picker state
    var showDatePickerDialog by remember { mutableStateOf(false) }
    // #11 Multi-select mode state
    var selectionMode by remember { mutableStateOf(false) }
    var selectedMessageIds by remember { mutableStateOf(setOf<Long>()) }
    fun currentVisibleAnchor(): ChatReadingAnchor? {
        val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            timelineItems.getOrNull(info.index) is ChatTimelineItem.MessageRow
        } ?: return null
        val row = timelineItems.getOrNull(visible.index) as? ChatTimelineItem.MessageRow ?: return null
        return ChatReadingAnchor(
            messageLocalId = row.message.message.messageLocalId,
            scrollOffset = listState.firstVisibleItemScrollOffset,
            savedAtMillis = System.currentTimeMillis(),
        )
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val thresholdPx = with(density) { 72.dp.toPx() }
    val maxPullPx = thresholdPx * 1.6f
    var pullOffsetTargetPx by remember { mutableFloatStateOf(0f) }
    var isPullDragging by remember { mutableStateOf(false) }
    val pullOffsetPx by animateFloatAsState(
        targetValue = pullOffsetTargetPx,
        animationSpec = if (isPullDragging) {
            snap()
        } else {
            spring(
                dampingRatio = 0.82f,
                stiffness = 420f,
            )
        },
        label = "chatPullOffset",
    )
    var pendingRestore by remember { mutableStateOf<RestoreScrollAnchor?>(null) }
    fun settlePullGesture() {
        val finalOffset = pullOffsetTargetPx
        if (finalOffset >= thresholdPx && !uiState.isLoadingOlderPage && uiState.hasOlderPage) {
            findAnchorMessageId(timelineItems, listState.firstVisibleItemIndex)?.let { anchorId ->
                pendingRestore = RestoreScrollAnchor(
                    messageLocalId = anchorId,
                    scrollOffset = listState.firstVisibleItemScrollOffset,
                )
            }
            onLoadOlderPage()
        } else if (finalOffset <= -thresholdPx && !uiState.isLoadingNewerPage && uiState.hasNewerPage) {
            onLoadNewerPage()
        }
        isPullDragging = false
        pullOffsetTargetPx = 0f
    }
    val topPullPx = pullOffsetPx.coerceAtLeast(0f)
    val bottomPullPx = (-pullOffsetPx).coerceAtLeast(0f)

    LaunchedEffect(messages.size, uiState.isLoadingOlderPage, timelineItems) {
        val restore = pendingRestore ?: return@LaunchedEffect
        if (uiState.isLoadingOlderPage) return@LaunchedEffect
        val index = timelineItems.indexOfFirst { item ->
            item is ChatTimelineItem.MessageRow && item.message.message.messageLocalId == restore.messageLocalId
        }
        if (index >= 0) {
            listState.scrollToItem(index, restore.scrollOffset)
        }
        pendingRestore = null
    }

    LaunchedEffect(uiState.pendingJumpMessageLocalId, timelineItems) {
        val targetMessageId = uiState.pendingJumpMessageLocalId ?: return@LaunchedEffect
        val index = timelineItems.indexOfFirst { item ->
            item is ChatTimelineItem.MessageRow && item.message.message.messageLocalId == targetMessageId
        }
        if (index >= 0) {
            listState.scrollToItem(index.coerceAtLeast(0), uiState.pendingJumpScrollOffset)
            onConsumePendingJump()
            onClearJumpContext()
        }
    }

    LaunchedEffect(uiState.highlightedMessageLocalId) {
        if (uiState.highlightedMessageLocalId != null) {
            delay(2_400)
            onClearHighlight()
        }
    }

    LaunchedEffect(listState, uiState.hasNewerPage, messages.size) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val isNearBottom = lastVisible >= timelineItems.lastIndex - 2
            uiState.hasNewerPage || !isNearBottom
        }.collect { shouldShow ->
            onSetBackToLatestVisible(shouldShow && messages.isNotEmpty())
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .statusBarsPadding(),
    ) {
        // #11 Multi-select top bar
        if (selectionMode) {
            Surface(
                color = colors.raisedSurface.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.62f)),
                shadowElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.md, vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    ChatIconActionButton(
                        icon = Icons.Default.Close,
                        contentDescription = "退出多选",
                        onClick = {
                            selectionMode = false
                            selectedMessageIds = emptySet()
                        },
                    )
                    Text(
                        text = "已选择 ${selectedMessageIds.size} 条",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.titleAccent,
                        modifier = Modifier.weight(1f),
                    )
                    if (selectedMessageIds.isNotEmpty()) {
                        ChatIconActionButton(
                            icon = Icons.Default.ContentCopy,
                            contentDescription = "复制",
                            onClick = {
                                val selectedTexts = messages
                                    .filter { it.message.messageLocalId in selectedMessageIds }
                                    .sortedBy { it.message.timestamp }
                                    .joinToString("\n") { it.message.text.ifBlank { buildReadableMessageSummary(it) } }
                                clipboardManager.setText(AnnotatedString(selectedTexts))
                                selectionMode = false
                                selectedMessageIds = emptySet()
                            },
                            emphasized = true,
                        )
                    }
                }
            }
        } else {
            ImportedChatTopBar(
                detail = detail,
                onBack = { onBack(currentVisibleAnchor()) },
                onManage = onShowChatManagement,
            )
        }

        ChatSearchBar(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChange,
            onClear = onClearSearch,
            onPickDate = { showDatePickerDialog = true },
        )

        if (uiState.searchQuery.isNotBlank()) {
            SearchNavigationBar(
                resultCount = searchResults.size,
                currentIndex = uiState.currentSearchResultIndex,
                onPrevious = {
                    if (searchResults.isNotEmpty() && !uiState.returnAnchorAvailable) {
                        onRememberReturnAnchor(currentVisibleAnchor())
                    }
                    onJumpToPreviousSearchResult()
                },
                onNext = {
                    if (searchResults.isNotEmpty() && !uiState.returnAnchorAvailable) {
                        onRememberReturnAnchor(currentVisibleAnchor())
                    }
                    onJumpToNextSearchResult()
                },
            )
            SearchResultsPanel(
                results = searchResults,
                query = uiState.searchQuery,
                currentIndex = uiState.currentSearchResultIndex,
                onResultClick = { index, _ ->
                    if (!uiState.returnAnchorAvailable) {
                        onRememberReturnAnchor(currentVisibleAnchor())
                    }
                    onJumpToSearchResult(index)
                },
            )
        }

        Box(
            modifier = Modifier.fillMaxSize()
                // #8 Chat background texture - dot pattern
                .drawDotPattern(colors.appBackground)
                .pointerInput(
                    messages.size,
                    uiState.hasOlderPage,
                    uiState.hasNewerPage,
                    uiState.isLoadingOlderPage,
                    uiState.isLoadingNewerPage,
                    thresholdPx,
                    maxPullPx,
                    viewConfiguration.touchSlop,
                ) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val pointerId = down.id
                        val startPosition = down.position
                        isPullDragging = true
                        pullOffsetTargetPx = pullOffsetPx
                        var activeDirection: EdgePullDirection? = null
                        var accumulatedDy = 0f
                        val touchSlop = viewConfiguration.touchSlop

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == pointerId }
                                ?: event.changes.firstOrNull()
                                ?: continue

                            if (!change.pressed) {
                                if (pullOffsetTargetPx.absoluteValue > 0f) {
                                    settlePullGesture()
                                } else {
                                    isPullDragging = false
                                }
                                break
                            }

                            val deltaY = change.positionChange().y
                            if (deltaY == 0f && activeDirection == null) continue

                            if (activeDirection == null) {
                                accumulatedDy += deltaY
                                val totalgestureDx = change.position.x - startPosition.x
                                val absDy = kotlin.math.abs(accumulatedDy)
                                val absDx = kotlin.math.abs(totalgestureDx)
                                if (absDy < touchSlop || absDy <= absDx * 1.05f) {
                                    continue
                                }

                                val canStartTopPull = accumulatedDy > 0f &&
                                    !listState.canScrollBackward &&
                                    !uiState.isLoadingOlderPage &&
                                    messages.isNotEmpty()
                                val canStartBottomPull = accumulatedDy < 0f &&
                                    !listState.canScrollForward &&
                                    !uiState.isLoadingNewerPage &&
                                    messages.isNotEmpty()

                                activeDirection = when {
                                    canStartTopPull -> EdgePullDirection.TOP
                                    canStartBottomPull -> EdgePullDirection.BOTTOM
                                    else -> null
                                }

                                if (activeDirection == null) {
                                    if (absDx > absDy * 1.1f) {
                                        isPullDragging = false
                                        break
                                    }
                                    continue
                                }
                            }

                            val direction = activeDirection ?: continue
                            val previous = pullOffsetTargetPx
                            val next = when (direction) {
                                EdgePullDirection.TOP -> {
                                    (previous + deltaY * if (deltaY > 0f) 0.5f else 1f)
                                        .coerceIn(0f, maxPullPx)
                                }

                                EdgePullDirection.BOTTOM -> {
                                    (previous + deltaY * if (deltaY < 0f) 0.5f else 1f)
                                        .coerceIn(-maxPullPx, 0f)
                                }
                            }

                            if (next != previous) {
                                pullOffsetTargetPx = next
                                event.changes.forEach { it.consume() }
                            }

                            if (next == 0f) {
                                val inwardRelease = when (direction) {
                                    EdgePullDirection.TOP -> deltaY < 0f
                                    EdgePullDirection.BOTTOM -> deltaY > 0f
                                }
                                if (inwardRelease) {
                                    activeDirection = null
                                    accumulatedDy = 0f
                                }
                            }
                        }
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = pullOffsetPx
                    },
            ) {
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (uiState.isLoadingChatWindow) {
                            CircularProgressIndicator()
                        } else {
                            Text(
                                text = if (detail == null) "正在加载会话..." else "这个会话里还没有消息。",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textSecondary,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = spacing.md,
                            end = spacing.md,
                            top = spacing.sm,
                            bottom = spacing.xl,
                        ),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        itemsIndexed(
                            items = timelineItems,
                            key = { _, item ->
                                when (item) {
                                    is ChatTimelineItem.DayHeader -> item.key
                                    is ChatTimelineItem.TimeHint -> item.key
                                    is ChatTimelineItem.MessageRow -> item.key
                                }
                            },
                        ) { _, item ->
                            when (item) {
                                is ChatTimelineItem.DayHeader -> DayHeaderRow(item.date)
                                is ChatTimelineItem.TimeHint -> TimeHintRow(item.timestamp)
                                is ChatTimelineItem.MessageRow -> ChatMessageBubble(
                                    detail = detail,
                                    renderableMessage = item.message,
                                    participant = item.participant,
                                    qFaceCatalog = qFaceCatalog,
                                    highlighted = uiState.highlightedMessageLocalId == item.message.message.messageLocalId,
                                    highlightQuery = uiState.activeHighlightQuery,
                                    audioPlayer = audioPlayer,
                                    isConsecutive = item.isConsecutive,
                                    selectionMode = selectionMode,
                                    isSelected = item.message.message.messageLocalId in selectedMessageIds,
                                    onJumpToReply = { onJumpToReply(item.message) },
                                    onLongPress = {
                                        if (!selectionMode) {
                                            selectionMode = true
                                            selectedMessageIds = setOf(item.message.message.messageLocalId)
                                        } else {
                                            onShowMessageActions(
                                                MessageActionPayload(
                                                    message = item.message,
                                                    resource = null,
                                                ),
                                            )
                                        }
                                    },
                                    onTap = {
                                        val id = item.message.message.messageLocalId
                                        selectedMessageIds = if (id in selectedMessageIds) {
                                            selectedMessageIds - id
                                        } else {
                                            selectedMessageIds + id
                                        }
                                        if (selectedMessageIds.isEmpty()) selectionMode = false
                                    },
                                    onOpenResource = { resource -> onOpenResource(resource, item.message) },
                                    onOpenFile = onOpenFile,
                                    onLongPressResource = { resource ->
                                        onShowMessageActions(
                                            MessageActionPayload(
                                                message = item.message,
                                                resource = resource,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }

            EdgePullIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .graphicsLayer {
                        translationY = pullOffsetPx
                    },
                pullPx = topPullPx,
                thresholdPx = thresholdPx,
                isLoading = uiState.isLoadingOlderPage,
                readyLabel = "松开加载上一页",
                idleLabel = if (uiState.hasOlderPage) "下拉加载上一页" else "已经到最早了",
            )
            EdgePullIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .graphicsLayer {
                        translationY = pullOffsetPx
                    },
                pullPx = bottomPullPx,
                thresholdPx = thresholdPx,
                isLoading = uiState.isLoadingNewerPage,
                readyLabel = "松开加载下一页",
                idleLabel = if (uiState.hasNewerPage) "上拉加载下一页" else "已经到最新了",
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                if (uiState.returnAnchorAvailable) {
                    ChatIconActionButton(
                        icon = Icons.Default.KeyboardArrowDown,
                        contentDescription = "返回刚才位置",
                        emphasized = false,
                        onClick = onReturnToPreviousAnchor,
                    )
                }
                if (uiState.showBackToLatest) {
                    ChatIconActionButton(
                        icon = Icons.Default.South,
                        contentDescription = "回到最新",
                        emphasized = true,
                        onClick = {
                            onRememberReturnAnchor(currentVisibleAnchor())
                            onJumpToLatest()
                        },
                    )
                }
            }
        }
    }

    // #7 Material 3 DatePicker dialog
    if (showDatePickerDialog) {
        showDatePicker(
            onDateSelected = { date -> onJumpToDate(date) },
            onDismiss = { showDatePickerDialog = false },
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            currentVisibleAnchor()?.let(onSaveReadingAnchor)
        }
    }
}

// ── Timeline helpers ──────────────────────────────────────────────────────────

@Composable
private fun DayHeaderRow(date: LocalDate) {
    val colors = YingShiThemeTokens.colors
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = colors.raisedSurface.copy(alpha = 0.82f),
            border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.54f)),
        ) {
            Text(
                text = date.format(DateFormatter),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun TimeHintRow(timestamp: Long) {
    val colors = YingShiThemeTokens.colors
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatTimelineTime(timestamp),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun EdgePullIndicator(
    modifier: Modifier = Modifier,
    pullPx: Float,
    thresholdPx: Float,
    isLoading: Boolean,
    readyLabel: String,
    idleLabel: String,
) {
    val visible = isLoading || pullPx > 0f
    if (!visible) return
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.64f)),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = colors.titleAccent,
                )
            } else {
                CircularProgressIndicator(
                    progress = { (pullPx / thresholdPx).coerceIn(0f, 1f) },
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = colors.titleAccent,
                    trackColor = colors.sectionBackground.copy(alpha = 0.72f),
                )
            }
            Text(
                text = if (isLoading) "正在加载" else if (pullPx >= thresholdPx) readyLabel else idleLabel,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
        }
    }
}

private fun buildTimelineItems(
    messages: List<ImportedRenderableMessage>,
    participants: List<ImportedParticipant>,
): List<ChatTimelineItem> {
    val participantMap = participants.associateBy { participantStableKey(it.uid, it.uin, it.displayName) }
    val zoneId = ZoneId.systemDefault()
    val items = mutableListOf<ChatTimelineItem>()
    var lastDate: LocalDate? = null
    var lastTimestamp: Long? = null
    var lastSenderKey: String? = null
    messages.sortedBy { it.message.timestamp }.forEach { message ->
        val currentDate = Instant.ofEpochMilli(message.message.timestamp).atZone(zoneId).toLocalDate()
        if (lastDate != currentDate) {
            items += ChatTimelineItem.DayHeader(
                key = "day_$currentDate",
                date = currentDate,
            )
            lastDate = currentDate
            lastTimestamp = null
            lastSenderKey = null
        }
        val previousTimestamp = lastTimestamp
        if (previousTimestamp == null || message.message.timestamp - previousTimestamp >= 5 * 60 * 1000L) {
            items += ChatTimelineItem.TimeHint(
                key = "time_${message.message.messageLocalId}",
                timestamp = message.message.timestamp,
            )
            lastSenderKey = null
        }
        val currentSenderKey = participantStableKey(
            message.message.senderUid,
            message.message.senderUin,
            message.message.senderDisplayName,
        )
        // #3 Consecutive message merge: same sender and timestamp diff < 5 minutes
        val isConsecutive = lastSenderKey != null &&
            lastSenderKey == currentSenderKey &&
            lastTimestamp != null &&
            (message.message.timestamp - lastTimestamp!!) < 5 * 60 * 1000L
        items += ChatTimelineItem.MessageRow(
            key = "msg_${message.message.messageLocalId}",
            message = message,
            participant = participantMap[currentSenderKey],
            isConsecutive = isConsecutive,
        )
        lastTimestamp = message.message.timestamp
        lastSenderKey = currentSenderKey
    }
    return items
}

// #7 Material 3 DatePicker
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun showDatePicker(
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onDateSelected(
                            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        )
                    }
                    onDismiss()
                },
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

private fun findAnchorMessageId(
    timelineItems: List<ChatTimelineItem>,
    firstVisibleIndex: Int,
): Long? {
    return timelineItems.drop(firstVisibleIndex)
        .firstOrNull { it is ChatTimelineItem.MessageRow }
        ?.let { (it as ChatTimelineItem.MessageRow).message.message.messageLocalId }
}

// #8 Chat background dot pattern texture
private fun Modifier.drawDotPattern(baseColor: Color): Modifier = this.then(
    Modifier.drawBehind {
        val dotColor = baseColor.copy(alpha = 1f).let { c ->
            // 3% darker than appBackground
            Color(
                red = (c.red * 255f * 0.97f).coerceIn(0f, 255f) / 255f,
                green = (c.green * 255f * 0.97f).coerceIn(0f, 255f) / 255f,
                blue = (c.blue * 255f * 0.97f).coerceIn(0f, 255f) / 255f,
                alpha = c.alpha,
            )
        }
        val dotRadius = 1.dp.toPx()
        val spacingPx = 16.dp.toPx()
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                drawCircle(
                    color = dotColor,
                    radius = dotRadius,
                    center = Offset(x, y),
                )
                x += spacingPx
            }
            y += spacingPx
        }
    }
)
