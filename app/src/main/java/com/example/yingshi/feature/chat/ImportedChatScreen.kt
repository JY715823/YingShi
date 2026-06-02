package com.example.yingshi.feature.chat

import android.app.Application
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.South
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.example.yingshi.feature.chat.data.ChatReadingAnchor
import com.example.yingshi.feature.chat.data.ImportedChatDetail
import com.example.yingshi.feature.chat.data.ImportedChatImportInfo
import com.example.yingshi.feature.chat.data.ImportedChatSummary
import com.example.yingshi.feature.chat.data.ImportedChatType
import com.example.yingshi.feature.chat.data.ImportedMessageSearchResult
import com.example.yingshi.feature.chat.data.ImportedMessageSegment
import com.example.yingshi.feature.chat.data.ImportedParticipant
import com.example.yingshi.feature.chat.data.ImportedRenderableMessage
import com.example.yingshi.feature.chat.data.ImportedResource
import com.example.yingshi.feature.chat.data.ImportedResourceDetectedFormat
import com.example.yingshi.feature.chat.data.ImportedResourceRenderKind
import com.example.yingshi.feature.chat.data.ImportedResourceType
import com.example.yingshi.feature.chat.data.ImportedViewerMode
import com.example.yingshi.feature.chat.data.buildImportedMessagePresentation
import com.example.yingshi.feature.chat.data.buildImportedSearchPreviewSnippet
import com.example.yingshi.feature.chat.data.effectiveRenderKind
import com.example.yingshi.feature.chat.data.isDirectlyPlayableAudio
import com.example.yingshi.feature.chat.data.isImageLikeResource
import com.example.yingshi.feature.chat.data.isMediaViewerResource
import com.example.yingshi.feature.chat.data.isSilkAudio
import com.example.yingshi.feature.chat.data.resolveImportedFaceLabel
import com.example.yingshi.feature.chat.data.summarizeImportedUnsupportedMessage
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.xxin.silkdecoder.SilkDecoder

private val ChatListBackground = Color(0xFFF5F7FB)
private val ChatDetailBackground = Color(0xFFEDE9DF)
private val SelfBubbleColor = Color(0xFFB7EB8F)
private val OtherBubbleColor = Color.White
private val SystemBubbleColor = Color(0xFFF6F0D8)
private val HighlightColor = Color(0xFFFFF0B3)
private val ViewerScrim = Color(0xFF050608)
private val DateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
private val DateTimeFormatterFull: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)

private data class ChatViewerMediaItem(
    val stableKey: String,
    val messageLocalId: Long,
    val viewerMode: ImportedViewerMode,
    val type: ImportedResourceType,
    val path: String,
    val mimeType: String?,
    val senderName: String,
    val timestamp: Long,
    val width: Int?,
    val height: Int?,
    val isAnimatedImage: Boolean,
    val supportsOriginal: Boolean,
    val displayName: String,
)

private data class PdfPreviewPayload(
    val path: String,
    val title: String,
)

private data class RestoreScrollAnchor(
    val messageLocalId: Long,
    val scrollOffset: Int,
)

private enum class ChatOriginalLoadState {
    NotLoaded,
    Loading,
    Loaded,
    Failed,
}

private data class ChatVideoPosterState(
    val model: Any? = null,
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
)

private sealed interface ChatInlineSegment {
    data class Text(
        val text: String,
    ) : ChatInlineSegment

    data class Face(
        val faceId: String,
        val faceName: String,
        val fallbackLabel: String? = null,
    ) : ChatInlineSegment
}

private data class InlineMessagePayload(
    val text: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>,
)

private data class MessageActionOption(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit,
)

private enum class EdgePullDirection {
    TOP,
    BOTTOM,
}

private const val MinChatViewerScale = 1f
private const val MaxChatViewerScale = 4f
private const val ChatViewerResetScale = 1.02f

private class ChatViewerZoomState {
    var scale by mutableFloatStateOf(MinChatViewerScale)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    val isZoomed: Boolean
        get() = scale > ChatViewerResetScale

    fun reset() {
        scale = MinChatViewerScale
        offset = Offset.Zero
    }

    fun applyTransform(
        zoomChange: Float,
        panChange: Offset,
        containerSize: IntSize,
    ) {
        val nextScale = (scale * zoomChange).coerceIn(MinChatViewerScale, MaxChatViewerScale)
        if (nextScale <= ChatViewerResetScale) {
            reset()
            return
        }
        scale = nextScale
        val maxX = ((containerSize.width * nextScale - containerSize.width) / 2f).coerceAtLeast(0f)
        val maxY = ((containerSize.height * nextScale - containerSize.height) / 2f).coerceAtLeast(0f)
        offset = Offset(
            x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
            y = (offset.y + panChange.y).coerceIn(-maxY, maxY),
        )
    }
}

private sealed interface ChatTimelineItem {
    data class DayHeader(val key: String, val date: LocalDate) : ChatTimelineItem
    data class TimeHint(val key: String, val timestamp: Long) : ChatTimelineItem
    data class MessageRow(
        val key: String,
        val message: ImportedRenderableMessage,
        val participant: ImportedParticipant?,
    ) : ChatTimelineItem
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportedChatScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: ImportedChatViewModel = viewModel(
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
            ChatImportForegroundService.start(
                context = context,
                uri = uri,
                expectedChatId = uiState.activeManagedChatId,
            )
            viewModel.importFromZip(uri, uiState.activeManagedChatId)
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        if (uiState.selectedChatId == null) {
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
                        Toast.makeText(context, "无法打开文件选择器。", Toast.LENGTH_SHORT).show()
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
                                openImportedFile(context, resource.localFilePath, resource.resolvedMimeType)
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
                        openImportedFile(context, resource.localFilePath, resource.resolvedMimeType)
                    }
                },
            )
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
                                    openImportedFile(context, resource.localFilePath, resource.resolvedMimeType)
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
                            openImportedFile(context, resource.localFilePath, resource.resolvedMimeType)
                        }
                    },
                    onCopyText = { text ->
                        clipboardManager.setText(AnnotatedString(text))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    },
                    onShareFile = { resource ->
                        shareImportedFile(context, resource.localFilePath, resource.resolvedMimeType)
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
                        TextButton(
                            onClick = option.onClick,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(option.icon, contentDescription = null)
                                Text(option.label)
                            }
                        }
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
                                Toast.makeText(context, "无法打开文件选择器。", Toast.LENGTH_SHORT).show()
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
                            " 是否继续删除“$targetName”？",
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.deleteImportedChat(info.chatId) }) {
                        Text("删除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::hideDeleteConfirm) {
                        Text("取消")
                    }
                },
            )
        }
    }
}

@Composable
private fun ImportedChatListScreen(
    chats: List<ImportedChatSummary>,
    uiState: ImportedChatUiState,
    onBack: () -> Unit,
    onImportClick: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onManageChat: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChatListBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "聊天记录查看器",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "导入 QCE ZIP 后会先在本机解压合并，再同步保存可用记录。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        FilledTonalButton(
            onClick = onImportClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("导入 QCE ZIP")
        }

        if (uiState.isImporting) {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Text(
                        text = uiState.importProgress?.message ?: "正在导入聊天记录",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val progress = uiState.importProgress
                    if (progress == null || progress.total <= 0) {
                        CircularProgressIndicator()
                    } else {
                        LinearProgressIndicator(
                            progress = { progress.fraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "${progress.current} / ${progress.total}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (chats.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Text(
                        text = "还没有导入聊天记录",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "请选择 QCE 导出的流式 JSONL ZIP。包内至少需要包含 manifest.json、chunks/*.jsonl，资源和 avatars.json 会自动识别。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = onImportClick,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("选择 ZIP 并导入")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                contentPadding = PaddingValues(bottom = spacing.xl),
            ) {
                items(
                    items = chats,
                    key = { it.chatId },
                ) { chat ->
                    ImportedChatSummaryCard(
                        chat = chat,
                        onClick = { onOpenChat(chat.chatId) },
                        onLongPress = { onManageChat(chat.chatId) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImportedChatSummaryCard(
    chat: ImportedChatSummary,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val qFaceCatalog = rememberQFaceCatalog()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            ),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarBadge(
                name = chat.displayName,
                avatarLocalPath = chat.avatarLocalPath,
                size = 52.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = chat.displayName.ifBlank { "未命名会话" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatListTime(chat.lastMessageAtMillis),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                InlineMessageText(
                    segments = remember(chat.lastMessagePreview, qFaceCatalog) {
                        buildReplyPreviewInlineSegments(
                            previewText = chat.lastMessagePreview.ifBlank { messageTypeLabel(chat.lastMessageType) },
                            qFaceCatalog = qFaceCatalog,
                        )
                    },
                    qFaceCatalog = qFaceCatalog,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    emojiScaleEm = 1.18f,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChatTypeTag(chat.chatType)
                    Text(
                        text = "${chat.messageCount} 条消息",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "上次导入 ${formatRelativeTime(chat.lastImportedAtMillis)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

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
    val spacing = YingShiThemeTokens.spacing
    val qFaceCatalog = rememberQFaceCatalog()
    val listState = rememberLazyListState()
    val participants = remember(detail?.participants) {
        detail?.participants.orEmpty()
    }
    val timelineItems = remember(messages, participants) {
        buildTimelineItems(messages, participants)
    }
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
        }
        onConsumePendingJump()
        onClearJumpContext()
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
            .background(ChatDetailBackground)
            .statusBarsPadding(),
    ) {
        ImportedChatTopBar(
            detail = detail,
            onBack = { onBack(currentVisibleAnchor()) },
            onManage = onShowChatManagement,
        )

        ChatSearchBar(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChange,
            onClear = onClearSearch,
            onPickDate = {
                showDatePicker(context) { date ->
                    onJumpToDate(date)
                }
            },
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
                                val totalGestureDx = change.position.x - startPosition.x
                                val absDy = abs(accumulatedDy)
                                val absDx = abs(totalGestureDx)
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    onJumpToReply = { onJumpToReply(item.message) },
                                    onLongPress = {
                                        onShowMessageActions(
                                            MessageActionPayload(
                                                message = item.message,
                                                resource = null,
                                            ),
                                        )
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
                    FloatingActionButton(
                        onClick = onReturnToPreviousAnchor,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "返回刚才位置")
                    }
                }
                if (uiState.showBackToLatest) {
                    FloatingActionButton(
                        onClick = {
                            onRememberReturnAnchor(currentVisibleAnchor())
                            onJumpToLatest()
                        },
                    ) {
                        Icon(Icons.Default.South, contentDescription = "回到最新")
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            currentVisibleAnchor()?.let(onSaveReadingAnchor)
        }
    }
}

@Composable
private fun ImportedChatTopBar(
    detail: ImportedChatDetail?,
    onBack: () -> Unit,
    onManage: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.sm, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            AvatarBadge(
                name = detail?.displayName.orEmpty(),
                avatarLocalPath = resolveTitleAvatar(detail),
                size = 40.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail?.displayName?.ifBlank { "聊天记录" } ?: "聊天记录",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when (detail?.chatType) {
                        ImportedChatType.GROUP -> "${detail.participants.size} 位参与者"
                        ImportedChatType.PRIVATE -> "${detail?.messageCount ?: 0} 条消息"
                        else -> "本地会话"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onManage) {
                Icon(Icons.Default.Description, contentDescription = "会话管理")
            }
        }
    }
}

@Composable
private fun ChatSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onPickDate: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (value.isNotBlank()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Close, contentDescription = "清空搜索")
                    }
                }
            },
            placeholder = {
                Text("搜索消息、回复、卡片摘要")
            },
        )
        FilledTonalButton(onClick = onPickDate) {
            Icon(Icons.Default.CalendarToday, contentDescription = null)
        }
    }
}

@Composable
private fun SearchResultsPanel(
    results: List<ImportedMessageSearchResult>,
    query: String,
    currentIndex: Int,
    onResultClick: (Int, ImportedMessageSearchResult) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val qFaceCatalog = rememberQFaceCatalog()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        if (results.isEmpty()) {
            Text(
                text = "没有找到匹配结果。",
                modifier = Modifier.padding(spacing.md),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(
                modifier = Modifier.padding(vertical = spacing.xs),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                results.take(6).forEachIndexed { index, result ->
                    val snippet = remember(result.fullPreviewText, query) {
                        buildImportedSearchPreviewSnippet(
                            previewText = result.fullPreviewText,
                            query = query,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (index == currentIndex) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
                                RoundedCornerShape(16.dp),
                            )
                            .clickable { onResultClick(index, result) }
                            .padding(horizontal = spacing.md, vertical = spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = result.senderDisplayName,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                text = formatTimelineTime(result.timestamp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        InlineMessageText(
                            segments = remember(snippet.previewText, qFaceCatalog) {
                                buildReplyPreviewInlineSegments(
                                    previewText = snippet.previewText,
                                    qFaceCatalog = qFaceCatalog,
                                )
                            },
                            qFaceCatalog = qFaceCatalog,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            emojiScaleEm = 1.18f,
                            highlightQuery = query,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchNavigationBar(
    resultCount: Int,
    currentIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = if (resultCount <= 0) {
                    "0 条命中"
                } else {
                    "${(currentIndex.coerceAtLeast(0) + 1).coerceAtMost(resultCount)} / $resultCount"
                },
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onPrevious,
                enabled = resultCount > 0 && currentIndex > 0,
            ) {
                Text("上一条")
            }
            TextButton(
                onClick = onNext,
                enabled = resultCount > 0 && currentIndex in 0 until resultCount - 1,
            ) {
                Text("下一条")
            }
        }
    }
}

@Composable
private fun ChatManagementSheet(
    chat: ImportedChatSummary,
    onReimport: () -> Unit,
    onShowImportInfo: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = YingShiThemeTokens.spacing.md, vertical = YingShiThemeTokens.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
    ) {
        Text(
            text = chat.displayName.ifBlank { "会话管理" },
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "${chat.messageCount} 条消息 · 上次导入 ${formatRelativeTime(chat.lastImportedAtMillis)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ManagementActionButton(
            label = "再次导入",
            icon = Icons.Default.Download,
            onClick = onReimport,
        )
        ManagementActionButton(
            label = "查看导入信息",
            icon = Icons.Default.Description,
            onClick = onShowImportInfo,
        )
        ManagementActionButton(
            label = "删除会话",
            icon = Icons.Default.Close,
            onClick = onDelete,
        )
    }
}

@Composable
private fun ManagementActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null)
            Text(label)
        }
    }
}

@Composable
private fun ImportInfoDialog(
    info: ImportedChatImportInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ImportInfoLine("来源文件", info.sourceFileName ?: "未知")
                ImportInfoLine("最近导入", formatAbsoluteDateTime(info.lastImportedAtMillis))
                ImportInfoLine("当前消息数", "${info.messageCount} 条")
                ImportInfoLine("最近新增", "${info.lastImportAddedMessageCount} 条")
                ImportInfoLine("最近合并", "${info.lastImportMergedMessageCount} 条")
                ImportInfoLine("最近资源复制", "${info.lastImportResourceCount} 个")
                ImportInfoLine("最近头像复制", "${info.lastImportAvatarCount} 个")
                ImportInfoLine("本地占用", formatStorageSize(context, info.storageBytes))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        },
    )
}

@Composable
private fun ImportInfoLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DayHeaderRow(date: LocalDate) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color.White.copy(alpha = 0.8f),
        ) {
            Text(
                text = date.format(DateFormatter),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TimeHintRow(timestamp: Long) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatTimelineTime(timestamp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatMessageBubble(
    detail: ImportedChatDetail?,
    renderableMessage: ImportedRenderableMessage,
    participant: ImportedParticipant?,
    qFaceCatalog: QFaceCatalog?,
    highlighted: Boolean,
    highlightQuery: String,
    audioPlayer: ChatAudioPlayerState,
    onJumpToReply: () -> Unit,
    onLongPress: () -> Unit,
    onOpenResource: (ImportedResource) -> Unit,
    onOpenFile: (ImportedResource) -> Unit,
    onLongPressResource: (ImportedResource) -> Unit,
) {
    val message = renderableMessage.message
    val spacing = YingShiThemeTokens.spacing
    val isGroupIncoming = detail?.chatType == ImportedChatType.GROUP && !message.isSelf
    val bubbleColor = when {
        message.system || message.recalled -> SystemBubbleColor
        message.isSelf -> SelfBubbleColor
        else -> OtherBubbleColor
    }
    val horizontalArrangement = if (message.isSelf) Arrangement.End else Arrangement.Start
    val replySegment = remember(renderableMessage) {
        renderableMessage.segments.filterIsInstance<ImportedMessageSegment.Reply>().firstOrNull()
    }
    val replyPreviewText = replySegment?.content ?: message.replyPreviewText
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.Top,
    ) {
        if (!message.isSelf) {
            AvatarBadge(
                name = participant?.displayName ?: message.senderDisplayName,
                avatarLocalPath = participant?.avatarLocalPath,
                size = 38.dp,
            )
            Spacer(modifier = Modifier.width(spacing.xs))
        }

        Column(
            horizontalAlignment = if (message.isSelf) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isGroupIncoming) {
                Text(
                    text = participant?.displayName ?: message.senderDisplayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress,
                ),
                shape = RoundedCornerShape(
                    topStart = if (message.isSelf) 18.dp else 6.dp,
                    topEnd = if (message.isSelf) 6.dp else 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp,
                ),
                color = if (highlighted) HighlightColor else bubbleColor,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!replyPreviewText.isNullOrBlank() || !message.replyToSourceMessageId.isNullOrBlank() || !message.replyReferenceMessageId.isNullOrBlank()) {
                        ReplyPreviewCard(
                            previewText = replyPreviewText.orEmpty().ifBlank { "查看原消息" },
                            senderName = replySegment?.senderName ?: message.replyReferenceSenderName,
                            qFaceCatalog = qFaceCatalog,
                            highlightQuery = highlightQuery,
                            onClick = onJumpToReply,
                        )
                    }
                    MessageContent(
                        renderableMessage = renderableMessage,
                        qFaceCatalog = qFaceCatalog,
                        highlightQuery = highlightQuery,
                        audioPlayer = audioPlayer,
                        onOpenResource = onOpenResource,
                        onOpenFile = onOpenFile,
                        onLongPressResource = onLongPressResource,
                    )
                }
            }
        }

        if (message.isSelf) {
            Spacer(modifier = Modifier.width(spacing.xs))
            AvatarBadge(
                name = participant?.displayName ?: message.senderDisplayName,
                avatarLocalPath = participant?.avatarLocalPath,
                size = 38.dp,
            )
        }
    }
}

@Composable
private fun ReplyPreviewCard(
    previewText: String,
    senderName: String?,
    qFaceCatalog: QFaceCatalog?,
    highlightQuery: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = Color.Black.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            senderName?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            InlineMessageText(
                segments = remember(previewText, qFaceCatalog) {
                    buildReplyPreviewInlineSegments(previewText, qFaceCatalog)
                },
                qFaceCatalog = qFaceCatalog,
                textStyle = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                emojiScaleEm = 1.24f,
                highlightQuery = highlightQuery,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MessageContent(
    renderableMessage: ImportedRenderableMessage,
    qFaceCatalog: QFaceCatalog?,
    highlightQuery: String,
    audioPlayer: ChatAudioPlayerState,
    onOpenResource: (ImportedResource) -> Unit,
    onOpenFile: (ImportedResource) -> Unit,
    onLongPressResource: (ImportedResource) -> Unit,
) {
    val message = renderableMessage.message
    when {
        message.system -> {
            Text(
                text = message.text.ifBlank { "系统消息" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        message.recalled -> {
            Text(
                text = "${message.senderDisplayName} 撤回了一条消息",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> RenderMessageSegments(
            renderableMessage = renderableMessage,
            qFaceCatalog = qFaceCatalog,
            highlightQuery = highlightQuery,
            audioPlayer = audioPlayer,
            onOpenResource = onOpenResource,
            onOpenFile = onOpenFile,
            onLongPressResource = onLongPressResource,
        )
    }
}

@Composable
private fun RenderMessageSegments(
    renderableMessage: ImportedRenderableMessage,
    qFaceCatalog: QFaceCatalog?,
    highlightQuery: String,
    audioPlayer: ChatAudioPlayerState,
    onOpenResource: (ImportedResource) -> Unit,
    onOpenFile: (ImportedResource) -> Unit,
    onLongPressResource: (ImportedResource) -> Unit,
) {
    val segments = renderableMessage.segments.filterNot { it is ImportedMessageSegment.Reply }
    val inlineSegments = mutableListOf<ImportedMessageSegment>()
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        segments.forEach { segment ->
            when (segment) {
                is ImportedMessageSegment.Text,
                is ImportedMessageSegment.Face,
                -> inlineSegments += segment

                is ImportedMessageSegment.Resource -> {
                    if (inlineSegments.isNotEmpty()) {
                        InlineMessageSegments(
                            segments = inlineSegments.toList(),
                            qFaceCatalog = qFaceCatalog,
                            highlightQuery = highlightQuery,
                        )
                        inlineSegments.clear()
                    }
                    when (segment.resource.renderKind) {
                        ImportedResourceRenderKind.IMAGE,
                        ImportedResourceRenderKind.VIDEO,
                        ImportedResourceRenderKind.STICKER,
                        -> MediaResourceCard(
                            resource = segment.resource,
                            onClick = { onOpenResource(segment.resource) },
                            onLongPress = { onLongPressResource(segment.resource) },
                        )

                        ImportedResourceRenderKind.AUDIO -> AudioMessageCard(
                            resource = segment.resource,
                            audioPlayer = audioPlayer,
                            onLongPress = { onLongPressResource(segment.resource) },
                        )

                        ImportedResourceRenderKind.FILE -> FileMessageCard(
                            resource = segment.resource,
                            onClick = { onOpenFile(segment.resource) },
                            onLongPress = { onLongPressResource(segment.resource) },
                        )

                        ImportedResourceRenderKind.UNKNOWN -> UnknownMessageCard(
                            text = segment.label ?: "未知资源",
                        )
                    }
                }

                is ImportedMessageSegment.JsonCard -> {
                    if (inlineSegments.isNotEmpty()) {
                        InlineMessageSegments(
                            segments = inlineSegments.toList(),
                            qFaceCatalog = qFaceCatalog,
                            highlightQuery = highlightQuery,
                        )
                        inlineSegments.clear()
                    }
                    JsonCardMessage(
                        title = segment.title,
                        summary = segment.summary,
                    )
                }

                is ImportedMessageSegment.CallRecord -> {
                    if (inlineSegments.isNotEmpty()) {
                        InlineMessageSegments(
                            segments = inlineSegments.toList(),
                            qFaceCatalog = qFaceCatalog,
                        )
                        inlineSegments.clear()
                    }
                    CallSummaryMessage(segment.summary)
                }

                is ImportedMessageSegment.Unknown -> {
                    if (inlineSegments.isNotEmpty()) {
                        InlineMessageSegments(
                            segments = inlineSegments.toList(),
                            qFaceCatalog = qFaceCatalog,
                        )
                        inlineSegments.clear()
                    }
                    UnknownMessageCard(
                        text = summarizeUnknownMessageLabel(
                            type = segment.type,
                            rawJson = segment.rawJson,
                        ),
                    )
                }

                is ImportedMessageSegment.Reply -> Unit
            }
        }
        if (inlineSegments.isNotEmpty()) {
            InlineMessageSegments(
                segments = inlineSegments.toList(),
                qFaceCatalog = qFaceCatalog,
                highlightQuery = highlightQuery,
            )
            inlineSegments.clear()
        }
        if (segments.isEmpty() && renderableMessage.message.text.isNotBlank()) {
            InlineMessageSegments(
                segments = listOf(ImportedMessageSegment.Text(renderableMessage.message.text)),
                qFaceCatalog = qFaceCatalog,
                highlightQuery = highlightQuery,
            )
        }
    }
}

@Composable
private fun InlineMessageSegments(
    segments: List<ImportedMessageSegment>,
    qFaceCatalog: QFaceCatalog?,
    highlightQuery: String = "",
) {
    InlineMessageText(
        segments = remember(segments) {
            segments.mapNotNull { segment ->
                when (segment) {
                    is ImportedMessageSegment.Text -> ChatInlineSegment.Text(segment.text)
                    is ImportedMessageSegment.Face -> ChatInlineSegment.Face(
                        faceId = segment.faceId,
                        faceName = segment.faceName,
                        fallbackLabel = resolveImportedFaceLabel(
                            faceId = segment.faceId,
                            faceName = segment.faceName,
                            qFaceCatalog = qFaceCatalog,
                        ),
                    )
                    else -> null
                }
            }
        },
        qFaceCatalog = qFaceCatalog,
        textStyle = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        emojiScaleEm = 1.45f,
        highlightQuery = highlightQuery,
    )
}

@Composable
private fun InlineMessageText(
    segments: List<ChatInlineSegment>,
    qFaceCatalog: QFaceCatalog?,
    textStyle: TextStyle,
    color: Color,
    emojiScaleEm: Float,
    highlightQuery: String = "",
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val payload = remember(segments, qFaceCatalog, emojiScaleEm) {
        buildInlineMessagePayload(
            segments = segments,
            qFaceCatalog = qFaceCatalog,
            emojiScaleEm = emojiScaleEm,
        )
    }
    Text(
        text = remember(payload.text, highlightQuery) {
            highlightAnnotatedText(payload.text, highlightQuery)
        },
        inlineContent = payload.inlineContent,
        style = textStyle,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
    )
}

private fun buildReplyPreviewInlineSegments(
    previewText: String,
    qFaceCatalog: QFaceCatalog?,
): List<ChatInlineSegment> {
    if (previewText.isBlank()) return emptyList()
    val trimmed = previewText.trim()
    val catalog = qFaceCatalog ?: return if (looksLikeStandaloneFaceToken(trimmed)) {
        listOf(ChatInlineSegment.Face(faceId = "", faceName = trimmed))
    } else {
        listOf(ChatInlineSegment.Text(previewText))
    }
    val segments = catalog.tokenize(previewText).map { token ->
        when (token) {
            is QFaceTextToken.Text -> ChatInlineSegment.Text(token.text)
            is QFaceTextToken.Emoji -> ChatInlineSegment.Face(
                faceId = token.resolved.asset?.emojiId.orEmpty(),
                faceName = token.resolved.asset?.rawName ?: token.resolved.label,
                fallbackLabel = token.resolved.label,
            )
        }
    }
    if (segments.size == 1 &&
        segments.first() is ChatInlineSegment.Text &&
        looksLikeStandaloneFaceToken(trimmed)
    ) {
        return listOf(ChatInlineSegment.Face(faceId = "", faceName = trimmed))
    }
    return segments
}

private fun buildInlineMessagePayload(
    segments: List<ChatInlineSegment>,
    qFaceCatalog: QFaceCatalog?,
    emojiScaleEm: Float,
): InlineMessagePayload {
    val builder = AnnotatedString.Builder()
    val inlineContent = linkedMapOf<String, InlineTextContent>()
    segments.forEachIndexed { index, segment ->
        when (segment) {
            is ChatInlineSegment.Text -> builder.append(segment.text)
            is ChatInlineSegment.Face -> {
                val faceResolved = qFaceCatalog?.resolveDetailed(
                    faceId = segment.faceId,
                    faceName = segment.faceName,
                )
                val faceAsset = faceResolved?.asset
                val fallbackLabel = segment.fallbackLabel ?: faceResolved?.label ?: resolveImportedFaceLabel(
                    faceId = segment.faceId,
                    faceName = segment.faceName,
                    qFaceCatalog = qFaceCatalog,
                )
                if (faceAsset == null) {
                    builder.append(faceFallbackText(fallbackLabel))
                } else {
                    val key = "face-$index-${faceAsset.emojiId}"
                    builder.appendInlineContent(key, alternateText = faceFallbackText(fallbackLabel))
                    inlineContent[key] = InlineTextContent(
                        placeholder = Placeholder(
                            width = emojiScaleEm.em,
                            height = emojiScaleEm.em,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                        ),
                        ) {
                            AsyncImage(
                                model = faceAsset.assetUri,
                                contentDescription = fallbackLabel,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 1.dp),
                                contentScale = ContentScale.Fit,
                            )
                    }
                }
            }
        }
    }
    return InlineMessagePayload(
        text = builder.toAnnotatedString(),
        inlineContent = inlineContent,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaResourceCard(
    resource: ImportedResource,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val displayKind = resource.effectiveRenderKind()
    val size = remember(resource.width, resource.height, displayKind) {
        calculateMediaDisplaySize(resource)
    }
    val previewSizePx = remember(size, density) {
        with(density) {
            maxOf(size.width.roundToPx(), size.height.roundToPx()).coerceAtLeast(96.dp.roundToPx())
        }
    }
    val background = Color.Black.copy(alpha = 0.06f)
    val contentScale = if (resource.isAnimatedImage) ContentScale.Fit else ContentScale.Crop
    val previewRequest = remember(
        context,
        resource.localFilePath,
        previewSizePx,
        displayKind,
    ) {
        resource.takeIf { displayKind == ImportedResourceRenderKind.IMAGE }
            ?.let { buildImportedPreviewImageRequest(context, it.localFilePath, previewSizePx) }
    }
    val videoPosterState = if (displayKind == ImportedResourceRenderKind.VIDEO) {
        rememberImportedVideoPosterState(resource.localFilePath)
    } else {
        ChatVideoPosterState()
    }
    val previewPainter = rememberAsyncImagePainter(model = previewRequest)
    val previewState = previewPainter.state
    Box(
        modifier = Modifier
            .width(size.width)
            .height(size.height)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (displayKind) {
            ImportedResourceRenderKind.IMAGE -> {
                if (previewState is AsyncImagePainter.State.Error) {
                    MissingMediaPlaceholder(
                        label = if (resource.isAnimatedImage) "动图当前不可用" else "图片当前不可用",
                        icon = Icons.Default.Image,
                        dark = false,
                    )
                } else {
                    Image(
                        painter = previewPainter,
                        contentDescription = resource.originalFileName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = contentScale,
                    )
                }
            }

            ImportedResourceRenderKind.VIDEO -> {
                when (val posterModel = videoPosterState.model) {
                    is Bitmap -> Image(
                        bitmap = posterModel.asImageBitmap(),
                        contentDescription = resource.originalFileName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )

                    null -> Unit

                    else -> AsyncImage(
                        model = posterModel,
                        contentDescription = resource.originalFileName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            else -> Unit
        }
        when (displayKind) {
            ImportedResourceRenderKind.VIDEO -> {
                if (videoPosterState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                if (videoPosterState.hasError && videoPosterState.model == null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = Color.Black.copy(alpha = 0.34f),
                    ) {
                        Text(
                            text = "视频封面暂不可用",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.92f),
                        )
                    }
                }
            }

            ImportedResourceRenderKind.IMAGE -> {
                if (!resource.isAnimatedImage) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp),
                    )
                }
            }

            else -> Unit
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioMessageCard(
    resource: ImportedResource,
    audioPlayer: ChatAudioPlayerState,
    onLongPress: () -> Unit,
) {
    val statusText = when {
        audioPlayer.isDecoding(resource.localFilePath) -> "正在解码语音"
        audioPlayer.isPreparing(resource.localFilePath) -> "正在加载语音"
        audioPlayer.isPlaying(resource.localFilePath) -> "正在播放语音"
        audioPlayer.hasError(resource.localFilePath) -> "播放失败，点按重试"
        else -> "语音消息"
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { audioPlayer.toggle(resource) },
                onLongClick = onLongPress,
            ),
        color = Color.Black.copy(alpha = 0.06f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                if (audioPlayer.isDecoding(resource.localFilePath) || audioPlayer.isPreparing(resource.localFilePath)) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(10.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = buildString {
                        append(resource.durationSeconds?.let(::formatDurationSeconds) ?: "点击播放")
                        if (resource.isSilkAudio()) {
                            append(" · SILK")
                        } else if (resource.detectedFormat == ImportedResourceDetectedFormat.AMR) {
                            append(" · AMR")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileMessageCard(
    resource: ImportedResource,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current
    val fileLabel = normalizeFileTypeLabel(resource)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            ),
        color = Color.Black.copy(alpha = 0.06f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isPdfResource(resource)) Icons.Default.PictureAsPdf else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = resource.originalFileName ?: resource.storedFileName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(fileLabel)
                        resource.sizeBytes?.let {
                            append(" · ")
                            append(android.text.format.Formatter.formatShortFileSize(context, it))
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun JsonCardMessage(
    title: String?,
    summary: String?,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.06f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title ?: "分享卡片",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = summary ?: "没有更多摘要信息",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CallSummaryMessage(
    summary: String?,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.06f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = summary ?: "通话记录",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun UnknownMessageCard(
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.06f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AvatarBadge(
    name: String,
    avatarLocalPath: String?,
    size: Dp,
) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = Color(0xFFDDE6F3),
    ) {
        if (!avatarLocalPath.isNullOrBlank() && File(avatarLocalPath).exists()) {
            AsyncImage(
                model = File(avatarLocalPath),
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.trim().take(1).ifBlank { "聊" },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF476079),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ChatTypeTag(type: ImportedChatType) {
    val label = when (type) {
        ImportedChatType.PRIVATE -> "私聊"
        ImportedChatType.GROUP -> "群聊"
        ImportedChatType.UNKNOWN -> "未知"
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
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
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
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
                )
            } else {
                CircularProgressIndicator(
                    progress = { (pullPx / thresholdPx).coerceIn(0f, 1f) },
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(
                text = if (isLoading) "正在加载..." else if (pullPx >= thresholdPx) readyLabel else idleLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImportedChatMediaViewer(
    items: List<ChatViewerMediaItem>,
    initialIndex: Int,
    viewerMode: ImportedViewerMode,
    onDismiss: () -> Unit,
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size },
    )
    val originalLoadStates = remember(items) { mutableStateMapOf<String, ChatOriginalLoadState>() }
    val spacing = YingShiThemeTokens.spacing
    val background = ViewerScrim
    val foreground = Color.White
    val currentItem = items.getOrNull(pagerState.currentPage)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                when (item.type) {
                    ImportedResourceType.IMAGE -> ChatViewerImageCanvas(
                        item = item,
                        originalLoadState = originalLoadStates[item.stableKey] ?: ChatOriginalLoadState.NotLoaded,
                        onOriginalLoadStateChange = { stableKey, nextState ->
                            originalLoadStates[stableKey] = nextState
                        },
                        modifier = Modifier.fillMaxSize(),
                        background = background,
                    )

                    ImportedResourceType.VIDEO -> LocalVideoPlayer(
                        path = item.path,
                        mimeType = item.mimeType,
                    )

                    else -> Unit
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = spacing.sm, vertical = spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = foreground)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${items.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = foreground,
                )
                Text(
                    text = formatTimelineTime(items[pagerState.currentPage].timestamp),
                    style = MaterialTheme.typography.labelMedium,
                    color = foreground.copy(alpha = 0.72f),
                )
            }
        }

        currentItem?.takeIf { it.supportsOriginal }?.let { item ->
            ViewerCapsuleButton(
                text = (originalLoadStates[item.stableKey] ?: ChatOriginalLoadState.NotLoaded).actionLabel(),
                emphasized = (originalLoadStates[item.stableKey] ?: ChatOriginalLoadState.NotLoaded) == ChatOriginalLoadState.Loaded,
                enabled = (originalLoadStates[item.stableKey] ?: ChatOriginalLoadState.NotLoaded) != ChatOriginalLoadState.Loading,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 20.dp),
                onClick = {
                    val currentState = originalLoadStates[item.stableKey] ?: ChatOriginalLoadState.NotLoaded
                    if (currentState == ChatOriginalLoadState.Loading || currentState == ChatOriginalLoadState.Loaded) {
                        return@ViewerCapsuleButton
                    }
                    originalLoadStates[item.stableKey] = ChatOriginalLoadState.Loading
                },
            )
        }
    }
}

@Composable
private fun ChatViewerImageCanvas(
    item: ChatViewerMediaItem,
    originalLoadState: ChatOriginalLoadState,
    onOriginalLoadStateChange: (String, ChatOriginalLoadState) -> Unit,
    modifier: Modifier = Modifier,
    background: Color,
) {
    val context = LocalContext.current
    val displayMetrics = context.resources.displayMetrics
    val previewSizePx = remember(displayMetrics.widthPixels, displayMetrics.heightPixels) {
        maxOf(displayMetrics.widthPixels, displayMetrics.heightPixels).coerceAtLeast(720)
    }
    val previewRequest = remember(context, item.path, previewSizePx) {
        buildImportedPreviewImageRequest(
            absolutePath = item.path,
            requestSizePx = previewSizePx,
            context = context,
        )
    }
    val shouldRequestOriginal = originalLoadState == ChatOriginalLoadState.Loading ||
        originalLoadState == ChatOriginalLoadState.Loaded
    val originalRequest = remember(context, item.path, shouldRequestOriginal) {
        if (shouldRequestOriginal) {
            buildImportedOriginalImageRequest(
                absolutePath = item.path,
                context = context,
            )
        } else {
            null
        }
    }
    val previewPainter = rememberAsyncImagePainter(model = previewRequest)
    val originalPainter = rememberAsyncImagePainter(model = originalRequest)
    val previewState = previewPainter.state
    val originalState = originalPainter.state
    val showOriginal = originalLoadState == ChatOriginalLoadState.Loaded &&
        originalState is AsyncImagePainter.State.Success
    val showPreview = previewState !is AsyncImagePainter.State.Error && !showOriginal
    val isTallImage = remember(item.width, item.height) {
        val width = item.width?.toFloat()?.takeIf { it > 0f } ?: 0f
        val height = item.height?.toFloat()?.takeIf { it > 0f } ?: 0f
        width > 0f && height / width >= 2.2f
    }

    LaunchedEffect(item.stableKey, originalLoadState, originalState) {
        when {
            originalLoadState == ChatOriginalLoadState.Loading &&
                originalState is AsyncImagePainter.State.Success -> {
                onOriginalLoadStateChange(item.stableKey, ChatOriginalLoadState.Loaded)
            }

            originalLoadState == ChatOriginalLoadState.Loading &&
                originalState is AsyncImagePainter.State.Error -> {
                onOriginalLoadStateChange(item.stableKey, ChatOriginalLoadState.Failed)
            }
        }
    }

    Box(
        modifier = modifier.background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (showPreview) {
            ZoomablePainterImage(
                key = item.stableKey,
                painter = previewPainter,
                modifier = Modifier.fillMaxSize(),
                background = background,
                alignment = if (isTallImage) Alignment.TopCenter else Alignment.Center,
            )
        }

        if (showOriginal) {
            ZoomablePainterImage(
                key = item.stableKey,
                painter = originalPainter,
                modifier = Modifier.fillMaxSize(),
                background = background,
                alignment = if (isTallImage) Alignment.TopCenter else Alignment.Center,
            )
        }

        if (previewState is AsyncImagePainter.State.Loading && !showOriginal) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White.copy(alpha = 0.90f),
                strokeWidth = 2.dp,
            )
        }

        if (!showPreview && !showOriginal) {
            MissingMediaPlaceholder(
                label = "图片当前不可用",
                icon = Icons.Default.Image,
                dark = true,
            )
        }
    }
}

@Composable
private fun MissingMediaPlaceholder(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    dark: Boolean,
) {
    val background = if (dark) Color.Black.copy(alpha = 0.34f) else Color.Black.copy(alpha = 0.06f)
    val contentColor = if (dark) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = background,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun ZoomablePainterImage(
    key: String,
    painter: Painter,
    modifier: Modifier = Modifier,
    background: Color,
    alignment: Alignment = Alignment.Center,
) {
    val zoomState = remember(key) { ChatViewerZoomState() }
    Image(
        painter = painter,
        contentDescription = null,
        modifier = modifier
            .background(background)
            .graphicsLayer {
                scaleX = zoomState.scale
                scaleY = zoomState.scale
                translationX = zoomState.offset.x
                translationY = zoomState.offset.y
            }
            .chatViewerZoomGesture(zoomState),
        alignment = alignment,
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun LocalVideoPlayer(
    path: String,
    mimeType: String?,
) {
    val context = LocalContext.current
    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(
                MediaItem.Builder()
                    .setUri(Uri.fromFile(File(path)))
                    .setMimeType(mimeType)
                    .build(),
            )
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = true
                this.player = player
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = {
            it.player = player
        },
    )
}

private fun ChatOriginalLoadState.actionLabel(): String {
    return when (this) {
        ChatOriginalLoadState.NotLoaded -> "加载原图"
        ChatOriginalLoadState.Loading -> "原图加载中"
        ChatOriginalLoadState.Loaded -> "已加载原图"
        ChatOriginalLoadState.Failed -> "重试原图"
    }
}

@Composable
private fun ViewerCapsuleButton(
    text: String,
    emphasized: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = if (emphasized) {
            Color.White.copy(alpha = 0.18f)
        } else {
            Color.Black.copy(alpha = 0.42f)
        },
    ) {
        TextButton(
            enabled = enabled,
            onClick = onClick,
        ) {
            Text(
                text = text,
                color = if (enabled || emphasized) {
                    Color.White.copy(alpha = 0.95f)
                } else {
                    Color.White.copy(alpha = 0.62f)
                },
            )
        }
    }
}

private fun buildImportedPreviewImageRequest(
    context: Context,
    absolutePath: String,
    requestSizePx: Int,
): ImageRequest? {
    val file = File(absolutePath).takeIf { it.exists() && it.isFile } ?: return null
    val baseKey = importedLocalMediaCacheKey(file)
    return ImageRequest.Builder(context)
        .data(file)
        .memoryCacheKey("chat-preview:$baseKey:size:$requestSizePx")
        .diskCachePolicy(CachePolicy.DISABLED)
        .networkCachePolicy(CachePolicy.DISABLED)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .precision(Precision.INEXACT)
        .size(requestSizePx)
        .crossfade(false)
        .build()
}

private fun buildImportedOriginalImageRequest(
    context: Context,
    absolutePath: String,
): ImageRequest? {
    val file = File(absolutePath).takeIf { it.exists() && it.isFile } ?: return null
    val baseKey = importedLocalMediaCacheKey(file)
    return ImageRequest.Builder(context)
        .data(file)
        .memoryCacheKey("chat-original:$baseKey")
        .diskCachePolicy(CachePolicy.DISABLED)
        .networkCachePolicy(CachePolicy.DISABLED)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .precision(Precision.EXACT)
        .size(Size.ORIGINAL)
        .crossfade(false)
        .build()
}

@Composable
private fun rememberImportedVideoPosterState(
    absolutePath: String,
): ChatVideoPosterState {
    val context = LocalContext.current
    val cacheKey = remember(absolutePath) {
        importedLocalMediaCacheKey(File(absolutePath))
    }
    return produceState(
        initialValue = cachedImportedVideoPosterModel(context, cacheKey)?.let { cached ->
            ChatVideoPosterState(model = cached)
        } ?: ChatVideoPosterState(isLoading = absolutePath.isNotBlank()),
        key1 = context,
        key2 = absolutePath,
        key3 = cacheKey,
    ) {
        if (absolutePath.isBlank()) {
            value = ChatVideoPosterState()
            return@produceState
        }
        cachedImportedVideoPosterModel(context, cacheKey)?.let { cached ->
            value = ChatVideoPosterState(model = cached)
            return@produceState
        }
        val posterFile = ensureImportedVideoPosterFile(
            context = context,
            absolutePath = absolutePath,
            cacheKey = cacheKey,
        )
        value = if (posterFile != null) {
            ChatVideoPosterState(model = cachedImportedVideoPosterModel(context, cacheKey) ?: posterFile)
        } else {
            ChatVideoPosterState(hasError = true)
        }
    }.value
}

private fun cachedImportedVideoPosterModel(
    context: Context,
    cacheKey: String,
): Any? {
    importedVideoPosterMemoryCache[cacheKey]?.let { return it }
    val file = importedVideoPosterFile(context, cacheKey).takeIf { it.exists() && it.length() > 0L } ?: return null
    val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    if (bitmap != null) {
        importedVideoPosterMemoryCache[cacheKey] = bitmap
        return bitmap
    }
    return file
}

private suspend fun ensureImportedVideoPosterFile(
    context: Context,
    absolutePath: String,
    cacheKey: String,
): File? = withContext(Dispatchers.IO) {
    val targetFile = importedVideoPosterFile(context, cacheKey)
    if (targetFile.exists() && targetFile.length() > 0L) {
        return@withContext targetFile
    }
    val lock = importedVideoPosterLocks.getOrPut(targetFile.absolutePath) { Any() }
    synchronized(lock) {
        if (targetFile.exists() && targetFile.length() > 0L) {
            return@synchronized targetFile
        }
        targetFile.parentFile?.mkdirs()
        val bitmap = extractImportedVideoPosterBitmap(absolutePath) ?: return@synchronized null
        runCatching {
            FileOutputStream(targetFile).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 86, output)
            }
            targetFile.takeIf { it.exists() && it.length() > 0L }
        }.getOrNull().also {
            if (!bitmap.isRecycled) {
                runCatching { bitmap.recycle() }
            }
        }
    }
}

private fun extractImportedVideoPosterBitmap(
    absolutePath: String,
): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(absolutePath)
        retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.frameAtTime
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun importedVideoPosterFile(
    context: Context,
    cacheKey: String,
): File {
    return context.cacheDir.resolve("chat-video-posters").resolve("$cacheKey.jpg")
}

private fun importedLocalMediaCacheKey(file: File): String {
    val raw = buildString {
        append(file.absolutePath)
        append('#')
        append(file.length())
        append('#')
        append(file.lastModified())
    }
    return sha256(raw)
}

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return buildString(digest.size * 2) {
        digest.forEach { byte -> append("%02x".format(byte)) }
    }
}

private val importedVideoPosterLocks = ConcurrentHashMap<String, Any>()
private val importedVideoPosterMemoryCache = ConcurrentHashMap<String, Bitmap>()

private fun Modifier.chatViewerZoomGesture(
    zoomState: ChatViewerZoomState,
): Modifier = pointerInput(zoomState) {
    awaitEachGesture {
        while (true) {
            val event = awaitPointerEvent()
            val activeChanges = event.changes.filter { it.pressed }
            if (activeChanges.isEmpty()) break

            if (activeChanges.size >= 2) {
                val currentCentroid = activeChanges.chatViewerCentroid(usePrevious = false)
                val previousCentroid = activeChanges.chatViewerCentroid(usePrevious = true)
                val currentDistance = activeChanges.chatViewerAverageDistanceTo(
                    centroid = currentCentroid,
                    usePrevious = false,
                )
                val previousDistance = activeChanges.chatViewerAverageDistanceTo(
                    centroid = previousCentroid,
                    usePrevious = true,
                )
                val zoomChange = if (previousDistance > 0f) {
                    currentDistance / previousDistance
                } else {
                    MinChatViewerScale
                }
                zoomState.applyTransform(
                    zoomChange = zoomChange,
                    panChange = currentCentroid - previousCentroid,
                    containerSize = size,
                )
                activeChanges.forEach { it.consume() }
            } else if (zoomState.isZoomed) {
                val change = activeChanges.first()
                zoomState.applyTransform(
                    zoomChange = 1f,
                    panChange = change.positionChange(),
                    containerSize = size,
                )
                activeChanges.forEach { it.consume() }
            }
        }
    }
}

private fun List<androidx.compose.ui.input.pointer.PointerInputChange>.chatViewerCentroid(
    usePrevious: Boolean,
): Offset {
    val total = fold(Offset.Zero) { sum, change ->
        sum + if (usePrevious) change.previousPosition else change.position
    }
    return total / size.toFloat()
}

private fun List<androidx.compose.ui.input.pointer.PointerInputChange>.chatViewerAverageDistanceTo(
    centroid: Offset,
    usePrevious: Boolean,
): Float {
    return sumOf { change ->
        val position = if (usePrevious) change.previousPosition else change.position
        (position - centroid).getDistance().toDouble()
    }.toFloat() / size
}

private enum class ChatAudioPhase {
    IDLE,
    DECODING,
    PREPARING,
    PLAYING,
    ERROR,
}

private data class ResolvedAudioSource(
    val path: String,
    val mimeType: String?,
)

private class ChatAudioPlayerState(context: Context) {
    private val appContext = context.applicationContext
    private val player = ExoPlayer.Builder(appContext).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val decodeCacheDir = appContext.cacheDir.resolve("chat-audio-cache").apply { mkdirs() }
    private var prepareJob: Job? = null
    private var currentPath: String? by mutableStateOf(null)
    private var phase: ChatAudioPhase by mutableStateOf(ChatAudioPhase.IDLE)
    private var failedPath: String? by mutableStateOf(null)

    init {
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    phase = when (playbackState) {
                        Player.STATE_BUFFERING -> ChatAudioPhase.PREPARING
                        Player.STATE_READY -> if (player.isPlaying) ChatAudioPhase.PLAYING else phase
                        Player.STATE_ENDED -> ChatAudioPhase.IDLE
                        else -> if (player.isPlaying) ChatAudioPhase.PLAYING else phase
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        stop()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    phase = if (isPlaying) ChatAudioPhase.PLAYING else if (currentPath == null) ChatAudioPhase.IDLE else phase
                }

                override fun onPlayerError(error: PlaybackException) {
                    failedPath = currentPath
                    phase = ChatAudioPhase.ERROR
                }
            },
        )
    }

    fun isPlaying(path: String): Boolean = phase == ChatAudioPhase.PLAYING && currentPath == path

    fun isPreparing(path: String): Boolean = phase == ChatAudioPhase.PREPARING && currentPath == path

    fun isDecoding(path: String): Boolean = phase == ChatAudioPhase.DECODING && currentPath == path

    fun hasError(path: String): Boolean = failedPath == path

    fun toggle(resource: ImportedResource) {
        val path = resource.localFilePath
        if (currentPath == path && phase in setOf(ChatAudioPhase.DECODING, ChatAudioPhase.PREPARING, ChatAudioPhase.PLAYING)) {
            stop()
            return
        }
        prepareJob?.cancel()
        player.stop()
        player.clearMediaItems()
        failedPath = null
        currentPath = path
        phase = if (resource.isSilkAudio()) ChatAudioPhase.DECODING else ChatAudioPhase.PREPARING
        prepareJob = scope.launch {
            val resolved = runCatching {
                resolvePlaybackSource(resource)
            }.getOrNull()
            if (currentPath != path) return@launch
            if (resolved == null) {
                failedPath = path
                phase = ChatAudioPhase.ERROR
                return@launch
            }
            phase = ChatAudioPhase.PREPARING
            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(Uri.fromFile(File(resolved.path)))
                    .setMimeType(resolved.mimeType)
                    .build(),
            )
            player.prepare()
            player.playWhenReady = true
        }
    }

    fun release() {
        prepareJob?.cancel()
        scope.cancel()
        player.release()
    }

    private suspend fun resolvePlaybackSource(resource: ImportedResource): ResolvedAudioSource? {
        return when {
            resource.isSilkAudio() -> {
                val wavPath = decodeSilkToWav(resource) ?: return null
                ResolvedAudioSource(path = wavPath, mimeType = "audio/wav")
            }
            resource.isDirectlyPlayableAudio() -> {
                ResolvedAudioSource(
                    path = resource.localFilePath,
                    mimeType = resource.resolvedMimeType,
                )
            }
            else -> null
        }
    }

    private suspend fun decodeSilkToWav(resource: ImportedResource): String? {
        return withContext(Dispatchers.IO) {
            val stableName = resource.md5?.ifBlank { null }
                ?: buildString {
                    append(resource.resourceLocalId)
                    append('_')
                    append(resource.localFilePath.hashCode().toString().replace('-', 'n'))
                }
            val outputFile = decodeCacheDir.resolve("$stableName.wav")
            if (outputFile.exists() && outputFile.length() > 44L) {
                return@withContext outputFile.absolutePath
            }
            runCatching {
                outputFile.parentFile?.mkdirs()
                if (outputFile.exists()) {
                    outputFile.delete()
                }
                SilkDecoder.decodeToWav(resource.localFilePath, outputFile.absolutePath)
            }.getOrElse { false }.takeIf { it && outputFile.exists() && outputFile.length() > 44L }
                ?.let {
                    outputFile.absolutePath
                }
        }
    }

    private fun stop() {
        prepareJob?.cancel()
        prepareJob = null
        player.stop()
        player.clearMediaItems()
        currentPath = null
        phase = ChatAudioPhase.IDLE
    }
}

@Composable
private fun rememberChatAudioPlayer(): ChatAudioPlayerState {
    val context = LocalContext.current
    val player = remember { ChatAudioPlayerState(context) }
    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }
    return player
}

@Composable
private fun ImportedChatPdfPreview(
    payload: PdfPreviewPayload,
    onDismiss: () -> Unit,
) {
    val pageCount by produceState(initialValue = 0, payload.path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(File(payload.path), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        renderer.pageCount
                    }
                }
            }.getOrDefault(0)
        }
    }
    val spacing = YingShiThemeTokens.spacing
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101114)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.sm, vertical = spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = payload.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (pageCount > 0) "$pageCount 页 PDF" else "正在解析 PDF",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
                Spacer(modifier = Modifier.width(48.dp))
            }

            if (pageCount <= 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.sm),
                ) {
                    items((0 until pageCount).toList()) { pageIndex ->
                        PdfPageImage(
                            filePath = payload.path,
                            pageIndex = pageIndex,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageImage(
    filePath: String,
    pageIndex: Int,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, filePath, pageIndex) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        renderer.openPage(pageIndex).use { page ->
                            val width = (page.width * 1.6f).toInt().coerceAtLeast(page.width)
                            val height = (page.height * 1.6f).toInt().coerceAtLeast(page.height)
                            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            }.getOrNull()
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
    ) {
        if (bitmap == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "PDF 第 ${pageIndex + 1} 页",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
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
    messages.sortedBy { it.message.timestamp }.forEach { message ->
        val currentDate = Instant.ofEpochMilli(message.message.timestamp).atZone(zoneId).toLocalDate()
        if (lastDate != currentDate) {
            items += ChatTimelineItem.DayHeader(
                key = "day_$currentDate",
                date = currentDate,
            )
            lastDate = currentDate
            lastTimestamp = null
        }
        if (lastTimestamp == null || message.message.timestamp - lastTimestamp!! >= 5 * 60 * 1000L) {
            items += ChatTimelineItem.TimeHint(
                key = "time_${message.message.messageLocalId}",
                timestamp = message.message.timestamp,
            )
        }
        items += ChatTimelineItem.MessageRow(
            key = "msg_${message.message.messageLocalId}",
            message = message,
            participant = participantMap[
                participantStableKey(
                    message.message.senderUid,
                    message.message.senderUin,
                    message.message.senderDisplayName,
                )
            ],
        )
        lastTimestamp = message.message.timestamp
    }
    return items
}

private fun buildViewerItems(
    messages: List<ImportedRenderableMessage>,
    viewerMode: ImportedViewerMode,
): List<ChatViewerMediaItem> {
    return messages.flatMap { renderableMessage ->
        renderableMessage.message.resources
            .filter { resource ->
                when (viewerMode) {
                    ImportedViewerMode.MEDIA -> resource.isMediaViewerResource()

                    ImportedViewerMode.STICKER -> resource.isImageLikeResource()
                }
            }
            .map { resource ->
                ChatViewerMediaItem(
                    stableKey = viewerItemStableKey(renderableMessage.message.messageLocalId, resource),
                    messageLocalId = renderableMessage.message.messageLocalId,
                    viewerMode = viewerMode,
                    type = when (resource.effectiveRenderKind()) {
                        ImportedResourceRenderKind.VIDEO -> ImportedResourceType.VIDEO
                        else -> ImportedResourceType.IMAGE
                    },
                    path = resource.localFilePath,
                    mimeType = resource.resolvedMimeType,
                    senderName = renderableMessage.message.senderDisplayName,
                    timestamp = renderableMessage.message.timestamp,
                    width = resource.width,
                    height = resource.height,
                    isAnimatedImage = resource.isAnimatedImage,
                    supportsOriginal = resource.effectiveRenderKind() == ImportedResourceRenderKind.IMAGE,
                    displayName = resource.originalFileName ?: resource.storedFileName,
                )
            }
    }
}

private fun viewerItemStableKey(messageLocalId: Long, resource: ImportedResource): String {
    return "$messageLocalId:${resource.resourceLocalId}:${resource.storedRelativePath}"
}

private fun calculateMediaDisplaySize(resource: ImportedResource): DpSize {
    val width = resource.width?.toFloat()?.coerceAtLeast(1f) ?: 1f
    val height = resource.height?.toFloat()?.coerceAtLeast(1f) ?: 1f
    val ratio = width / height
    val kind = resource.effectiveRenderKind()
    val maxSide = when (kind) {
        ImportedResourceRenderKind.IMAGE -> 240f
        ImportedResourceRenderKind.VIDEO -> 240f
        else -> 240f
    }
    val minSide = when (kind) {
        ImportedResourceRenderKind.IMAGE -> 96f
        ImportedResourceRenderKind.VIDEO -> 96f
        else -> 96f
    }
    val result = if (ratio >= 1f) {
        val targetWidth = maxSide
        val targetHeight = (targetWidth / ratio).coerceAtLeast(minSide)
        targetWidth.dp to targetHeight.dp
    } else {
        val targetHeight = maxSide
        val targetWidth = (targetHeight * ratio).coerceAtLeast(minSide)
        targetWidth.dp to targetHeight.dp
    }
    return DpSize(result.first, result.second)
}

private fun resolveTitleAvatar(detail: ImportedChatDetail?): String? {
    if (detail == null) return null
    return when (detail.chatType) {
        ImportedChatType.PRIVATE -> detail.participants.firstOrNull { !it.isSelf }?.avatarLocalPath
        ImportedChatType.GROUP -> null
        ImportedChatType.UNKNOWN -> null
    }
}

private fun participantStableKey(uid: String?, uin: String?, displayName: String): String {
    return when {
        !uid.isNullOrBlank() -> "uid:$uid"
        !uin.isNullOrBlank() -> "uin:$uin"
        else -> "name:${displayName.trim().lowercase(Locale.ROOT)}"
    }
}

private fun messageTypeLabel(type: String): String {
    return when (type.lowercase(Locale.ROOT)) {
        "text" -> "文本消息"
        "image" -> "图片"
        "video" -> "视频"
        "audio" -> "语音"
        "file" -> "文件"
        "json" -> "分享卡片"
        else -> type.ifBlank { "消息" }
    }
}

private fun formatListTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

private fun formatRelativeTime(timestamp: Long): String {
    return formatListTime(timestamp)
}

private fun formatTimelineTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(TimeFormatter)
}

private fun formatAbsoluteDateTime(timestamp: Long): String {
    if (timestamp <= 0L) return "未知"
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatterFull)
}

private fun formatStorageSize(
    context: Context,
    bytes: Long,
): String {
    return android.text.format.Formatter.formatShortFileSize(context, bytes.coerceAtLeast(0L))
}

private fun formatDurationSeconds(seconds: Int): String {
    val minutes = seconds / 60
    val remain = seconds % 60
    return if (minutes > 0) {
        "${minutes}分${remain.toString().padStart(2, '0')}秒"
    } else {
        "${remain}秒"
    }
}

private fun showDatePicker(
    context: Context,
    onDateSelected: (LocalDate) -> Unit,
) {
    val calendar = Calendar.getInstance()
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onDateSelected(LocalDate.of(year, month + 1, dayOfMonth))
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH),
    ).show()
}

private fun isPdfResource(resource: ImportedResource): Boolean {
    return resource.resolvedMimeType.equals("application/pdf", ignoreCase = true) ||
        resource.mimeType.equals("application/pdf", ignoreCase = true) ||
        resource.storedFileName.endsWith(".pdf", ignoreCase = true) ||
        (resource.originalFileName?.endsWith(".pdf", ignoreCase = true) == true)
}

private fun normalizeFileTypeLabel(resource: ImportedResource): String {
    if (isPdfResource(resource)) return "PDF"
    val name = resource.originalFileName ?: resource.storedFileName
    val extension = name.substringAfterLast('.', "").trim().uppercase(Locale.ROOT)
    if (extension.isNotBlank()) return extension
    val mime = resource.resolvedMimeType ?: resource.mimeType ?: return "文件"
    return mime.substringAfterLast('/').ifBlank { "文件" }.uppercase(Locale.ROOT)
}

private fun findAnchorMessageId(
    timelineItems: List<ChatTimelineItem>,
    firstVisibleIndex: Int,
): Long? {
    return timelineItems.drop(firstVisibleIndex)
        .firstOrNull { it is ChatTimelineItem.MessageRow }
        ?.let { (it as ChatTimelineItem.MessageRow).message.message.messageLocalId }
}

private fun openImportedFile(
    context: Context,
    absolutePath: String,
    mimeType: String?,
) {
    val file = File(absolutePath)
    if (!file.exists()) {
        Toast.makeText(context, "文件不存在或已失效。", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "打开文件"))
    }.recoverCatching {
        intent.setDataAndType(uri, "*/*")
        context.startActivity(Intent.createChooser(intent, "打开文件"))
    }.onFailure {
        val message = if (it is ActivityNotFoundException) {
            "当前没有可打开这个文件的应用。"
        } else {
            "打开文件失败。"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

private fun shareImportedFile(
    context: Context,
    absolutePath: String,
    mimeType: String?,
) {
    val file = File(absolutePath)
    if (!file.exists()) {
        Toast.makeText(context, "文件不存在或已失效。", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType ?: "*/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "分享文件").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(context, "分享失败。", Toast.LENGTH_SHORT).show()
    }
}

private fun buildMessageActionOptions(
    payload: MessageActionPayload,
    audioPlayer: ChatAudioPlayerState,
    onDismiss: () -> Unit,
    onOpenResource: (ImportedResource, ImportedRenderableMessage) -> Unit,
    onOpenFile: (ImportedResource) -> Unit,
    onCopyText: (String) -> Unit,
    onShareFile: (ImportedResource) -> Unit,
    onToggleAudio: (ImportedResource, ChatAudioPlayerState) -> Unit,
): List<MessageActionOption> {
    val options = mutableListOf<MessageActionOption>()
    val resource = payload.resource
    if (resource == null) {
        val canCopyText = payload.message.segments.any {
            it is ImportedMessageSegment.Text || it is ImportedMessageSegment.Face || it is ImportedMessageSegment.Reply
        } || payload.message.message.text.isNotBlank()
        if (canCopyText) {
            val textContent = payload.message.message.text.takeIf { it.isNotBlank() } ?: buildReadableMessageSummary(payload.message)
            options += MessageActionOption(
                label = "复制文字",
                icon = Icons.Default.ContentCopy,
                onClick = {
                    onCopyText(textContent)
                    onDismiss()
                },
            )
        }
    } else {
        when (resource.effectiveRenderKind()) {
            ImportedResourceRenderKind.IMAGE,
            ImportedResourceRenderKind.STICKER,
            ImportedResourceRenderKind.VIDEO,
            -> options += MessageActionOption(
                label = "查看",
                icon = if (resource.effectiveRenderKind() == ImportedResourceRenderKind.VIDEO) Icons.Default.VideoLibrary else Icons.Default.Image,
                onClick = {
                    onOpenResource(resource, payload.message)
                    onDismiss()
                },
            )

            ImportedResourceRenderKind.FILE -> {
                options += MessageActionOption(
                    label = "打开",
                    icon = Icons.Default.Description,
                    onClick = {
                        onOpenFile(resource)
                        onDismiss()
                    },
                )
                if (isPdfResource(resource)) {
                    options += MessageActionOption(
                        label = "PDF 预览",
                        icon = Icons.Default.PictureAsPdf,
                        onClick = {
                            onOpenFile(resource)
                            onDismiss()
                        },
                    )
                }
            }

            ImportedResourceRenderKind.AUDIO -> {
                options += MessageActionOption(
                    label = if (audioPlayer.isPlaying(resource.localFilePath) || audioPlayer.isPreparing(resource.localFilePath) || audioPlayer.isDecoding(resource.localFilePath)) {
                        "停止"
                    } else {
                        "播放"
                    },
                    icon = Icons.Default.PlayArrow,
                    onClick = {
                        onToggleAudio(resource, audioPlayer)
                        onDismiss()
                    },
                )
            }

            ImportedResourceRenderKind.UNKNOWN -> Unit
        }

        if (resource.effectiveRenderKind() != ImportedResourceRenderKind.UNKNOWN) {
            options += MessageActionOption(
                label = "分享",
                icon = Icons.Default.FileUpload,
                onClick = {
                    onShareFile(resource)
                    onDismiss()
                },
            )
        }

        if (resource.effectiveRenderKind() == ImportedResourceRenderKind.FILE) {
            options += MessageActionOption(
                label = "复制文件名",
                icon = Icons.Default.ContentCopy,
                onClick = {
                    onCopyText(resource.originalFileName ?: resource.storedFileName)
                    onDismiss()
                },
            )
            options += MessageActionOption(
                label = "复制本地路径",
                icon = Icons.Default.ContentCopy,
                onClick = {
                    onCopyText(resource.localFilePath)
                    onDismiss()
                },
            )
        }
    }

    options += MessageActionOption(
        label = "复制摘要",
        icon = Icons.Default.ContentCopy,
        onClick = {
            onCopyText(buildReadableMessageSummary(payload.message))
            onDismiss()
        },
    )
    return options
}

private fun buildReadableMessageSummary(
    message: ImportedRenderableMessage,
): String {
    return message.segments.joinToString(separator = "") { segment ->
        when (segment) {
            is ImportedMessageSegment.Text -> segment.text
            is ImportedMessageSegment.Face -> "[${faceDisplayLabel(segment.faceName)}]"
            is ImportedMessageSegment.Resource -> when (segment.resource.effectiveRenderKind()) {
                ImportedResourceRenderKind.IMAGE,
                ImportedResourceRenderKind.STICKER,
                -> "[图片]"
                ImportedResourceRenderKind.VIDEO -> "[视频]"
                ImportedResourceRenderKind.AUDIO -> "[语音]"
                ImportedResourceRenderKind.FILE -> "[文件]"
                ImportedResourceRenderKind.UNKNOWN -> "[资源]"
            }
            is ImportedMessageSegment.Reply -> "[回复]"
            is ImportedMessageSegment.JsonCard -> segment.title ?: segment.summary ?: "分享卡片"
            is ImportedMessageSegment.CallRecord -> segment.summary ?: "通话记录"
            is ImportedMessageSegment.Unknown -> summarizeImportedUnsupportedMessage(segment.type, segment.rawJson)
        }
    }.ifBlank {
        message.message.text.ifBlank { "消息" }
    }
}

private fun highlightAnnotatedText(
    text: AnnotatedString,
    query: String,
): AnnotatedString {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) return text
    val base = text.text
    val lowerBase = base.lowercase(Locale.ROOT)
    val lowerQuery = normalizedQuery.lowercase(Locale.ROOT)
    val builder = AnnotatedString.Builder(text)
    var searchFrom = 0
    while (true) {
        val index = lowerBase.indexOf(lowerQuery, startIndex = searchFrom)
        if (index < 0) break
        builder.addStyle(
            style = androidx.compose.ui.text.SpanStyle(
                background = HighlightColor.copy(alpha = 0.58f),
            ),
            start = index,
            end = (index + normalizedQuery.length).coerceAtMost(base.length),
        )
        searchFrom = index + lowerQuery.length
    }
    return builder.toAnnotatedString()
}

@Composable
private fun rememberQFaceCatalog(): QFaceCatalog? {
    val appContext = LocalContext.current.applicationContext
    val catalog by produceState<QFaceCatalog?>(initialValue = null, appContext) {
        value = withContext(Dispatchers.IO) {
            QFaceCatalogStore.getOrLoad(appContext)
        }
    }
    return catalog
}

private fun faceDisplayLabel(faceName: String): String {
    return faceName.removePrefix("/").trim().ifBlank { "表情" }
}

private fun faceFallbackText(faceName: String): String {
    val label = faceName.removePrefix("/").trim().ifBlank { faceDisplayLabel(faceName) }
    return if (label != "表情") {
        "[$label]"
    } else {
        resolveFaceGlyph(label) ?: "☺"
    }
}

private fun looksLikeStandaloneFaceToken(text: String): Boolean {
    return text.startsWith("/") &&
        text.length in 2..12 &&
        text.none { it.isWhitespace() }
}

private fun summarizeUnknownMessageLabel(
    type: String,
    rawJson: String,
): String {
    return summarizeImportedUnsupportedMessage(type = type, rawJson = rawJson)
}

private fun resolveFaceGlyph(label: String): String? {
    return when {
        "微笑" in label -> "🙂"
        "撇嘴" in label -> "😒"
        "色" == label -> "😍"
        "发呆" in label -> "😳"
        "得意" in label -> "😏"
        "流泪" in label -> "😭"
        "害羞" in label -> "😊"
        "闭嘴" in label -> "🤐"
        "睡" == label -> "😴"
        "大哭" in label -> "😭"
        "尴尬" in label -> "😅"
        "发怒" in label -> "😠"
        "调皮" in label -> "😜"
        "呲牙" in label -> "😁"
        "惊讶" in label -> "😮"
        "难过" in label -> "😔"
        "酷" == label -> "😎"
        "冷汗" in label -> "😅"
        "抓狂" in label -> "😫"
        "吐" == label -> "🤮"
        "偷笑" in label -> "🤭"
        "可爱" in label -> "🥰"
        "白眼" in label -> "🙄"
        "傲慢" in label -> "😤"
        "饥饿" in label -> "😋"
        "困" == label -> "🥱"
        "惊恐" in label -> "😱"
        "流汗" in label -> "😓"
        "憨笑" in label -> "😄"
        "悠闲" in label -> "😌"
        "奋斗" in label -> "💪"
        "咒骂" in label -> "🤬"
        "疑问" in label -> "❓"
        "嘘" == label -> "🤫"
        "晕" == label -> "😵"
        "折磨" in label -> "😣"
        "衰" == label -> "🥲"
        "骷髅" in label -> "💀"
        "敲打" in label -> "👊"
        "再见" in label -> "👋"
        "擦汗" in label -> "😅"
        "抠鼻" in label -> "🤏"
        "鼓掌" in label -> "👏"
        "坏笑" in label -> "😏"
        "左哼哼" in label -> "😤"
        "右哼哼" in label -> "😤"
        "哈欠" in label -> "🥱"
        "鄙视" in label -> "😑"
        "委屈" in label -> "🥺"
        "快哭了" in label -> "🥹"
        "阴险" in label -> "😈"
        "亲亲" in label -> "😘"
        "吓" == label -> "😱"
        "可怜" in label -> "🥺"
        "菜刀" in label -> "🔪"
        "西瓜" in label -> "🍉"
        "啤酒" in label -> "🍺"
        "咖啡" in label -> "☕"
        "猪头" in label -> "🐷"
        "玫瑰" in label -> "🌹"
        "凋谢" in label -> "🥀"
        "爱心" in label -> "❤️"
        "心碎" in label -> "💔"
        "蛋糕" in label -> "🎂"
        "闪电" in label -> "⚡"
        "炸弹" in label -> "💣"
        "刀" == label -> "🔪"
        "足球" in label -> "⚽"
        "瓢虫" in label -> "🐞"
        "便便" in label -> "💩"
        "月亮" in label -> "🌙"
        "太阳" in label -> "☀️"
        "礼物" in label -> "🎁"
        "拥抱" in label -> "🫂"
        "强" == label -> "👍"
        "弱" == label -> "👎"
        "握手" in label -> "🤝"
        "胜利" in label -> "✌️"
        "抱拳" in label -> "🙏"
        "勾引" in label -> "👉"
        "拳头" in label -> "✊"
        "差劲" in label -> "👎"
        "爱你" in label -> "🤟"
        "NO" == label -> "🙅"
        "OK" == label -> "👌"
        "比心" in label -> "🫶"
        "大怨种" in label -> "😮‍💨"
        "敲敲" in label -> "🥺"
        else -> null
    }
}
