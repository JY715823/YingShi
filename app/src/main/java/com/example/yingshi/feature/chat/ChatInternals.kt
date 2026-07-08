package com.example.yingshi.feature.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.example.yingshi.feature.chat.data.ImportedMessageSegment
import com.example.yingshi.feature.chat.data.ImportedRenderableMessage
import com.example.yingshi.feature.chat.data.ImportedResource
import com.example.yingshi.feature.chat.data.ImportedResourceRenderKind
import com.example.yingshi.feature.chat.data.ImportedResourceType
import com.example.yingshi.feature.chat.data.ImportedViewerMode
import com.example.yingshi.feature.chat.data.effectiveRenderKind
import com.example.yingshi.feature.chat.data.isImageLikeResource
import com.example.yingshi.feature.chat.data.isMediaViewerResource
import com.example.yingshi.feature.chat.data.resolveImportedFaceLabel
import com.example.yingshi.feature.chat.data.summarizeImportedUnsupportedMessage
import java.io.File
import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

// ── Formatters ────────────────────────────────────────────────────────────────

internal val DateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
internal val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
internal val DateTimeFormatterFull: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)

// ── Shared data types ─────────────────────────────────────────────────────────

internal data class ChatViewerMediaItem(
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

internal data class PdfPreviewPayload(
    val path: String,
    val title: String,
)

internal sealed interface ChatInlineSegment {
    data class Text(
        val text: String,
    ) : ChatInlineSegment

    data class Face(
        val faceId: String,
        val faceName: String,
        val fallbackLabel: String? = null,
    ) : ChatInlineSegment
}

internal data class InlineMessagePayload(
    val text: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>,
)

internal data class MessageActionOption(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

// ── Inline text builders ──────────────────────────────────────────────────────

internal fun buildReplyPreviewInlineSegments(
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

internal fun buildInlineMessagePayload(
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
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxSize()
                                .padding(horizontal = 1.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
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

// ── File operations ───────────────────────────────────────────────────────────

internal fun openImportedFile(
    context: Context,
    absolutePath: String,
    mimeType: String?,
): String? {
    val file = File(absolutePath)
    if (!file.exists()) {
        return "文件不存在或已失效。"
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
        return if (it is ActivityNotFoundException) {
            "当前没有可打开这个文件的应用。"
        } else {
            "打开文件失败。"
        }
    }
    return null
}

internal fun shareImportedFile(
    context: Context,
    absolutePath: String,
    mimeType: String?,
): String? {
    val file = File(absolutePath)
    if (!file.exists()) {
        return "文件不存在或已失效。"
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
        return "分享失败。"
    }
    return null
}

// ── Message action builder ────────────────────────────────────────────────────

internal fun buildMessageActionOptions(
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

internal fun buildReadableMessageSummary(
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

// ── Text highlight ────────────────────────────────────────────────────────────

internal fun highlightAnnotatedText(
    text: AnnotatedString,
    query: String,
    backgroundColor: Color,
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
                background = backgroundColor,
            ),
            start = index,
            end = (index + normalizedQuery.length).coerceAtMost(base.length),
        )
        searchFrom = index + lowerQuery.length
    }
    return builder.toAnnotatedString()
}

// ── QFace utilities ───────────────────────────────────────────────────────────

@Composable
internal fun rememberQFaceCatalog(): QFaceCatalog? {
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val catalog by produceState<QFaceCatalog?>(initialValue = null, appContext) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            QFaceCatalogStore.getOrLoad(appContext)
        }
    }
    return catalog
}

internal fun faceDisplayLabel(faceName: String): String {
    return faceName.removePrefix("/").trim().ifBlank { "表情" }
}

internal fun faceFallbackText(faceName: String): String {
    val label = faceName.removePrefix("/").trim().ifBlank { faceDisplayLabel(faceName) }
    return if (label != "表情") {
        "[$label]"
    } else {
        resolveFaceGlyph(label) ?: "☺"
    }
}

internal fun looksLikeStandaloneFaceToken(text: String): Boolean {
    return text.startsWith("/") &&
        text.length in 2..12 &&
        text.none { it.isWhitespace() }
}

internal fun summarizeUnknownMessageLabel(
    type: String,
    rawJson: String,
): String {
    return summarizeImportedUnsupportedMessage(type = type, rawJson = rawJson)
}

internal fun resolveFaceGlyph(label: String): String? {
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

// ── Viewer item builders ──────────────────────────────────────────────────────

internal fun buildViewerItems(
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

internal fun viewerItemStableKey(messageLocalId: Long, resource: ImportedResource): String {
    return "$messageLocalId:${resource.resourceLocalId}:${resource.storedRelativePath}"
}

internal fun calculateMediaDisplaySize(resource: ImportedResource): androidx.compose.ui.unit.DpSize {
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
    return androidx.compose.ui.unit.DpSize(result.first, result.second)
}

internal fun resolveTitleAvatar(detail: com.example.yingshi.feature.chat.data.ImportedChatDetail?): String? {
    if (detail == null) return null
    return when (detail.chatType) {
        com.example.yingshi.feature.chat.data.ImportedChatType.PRIVATE -> detail.participants.firstOrNull { !it.isSelf }?.avatarLocalPath
        com.example.yingshi.feature.chat.data.ImportedChatType.GROUP -> null
        com.example.yingshi.feature.chat.data.ImportedChatType.UNKNOWN -> null
    }
}

internal fun participantStableKey(uid: String?, uin: String?, displayName: String): String {
    return when {
        !uid.isNullOrBlank() -> "uid:$uid"
        !uin.isNullOrBlank() -> "uin:$uin"
        else -> "name:${displayName.trim().lowercase(Locale.ROOT)}"
    }
}

internal fun messageTypeLabel(type: String): String {
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

// ── Time / storage formatters ─────────────────────────────────────────────────

internal fun formatListTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return android.text.format.DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        android.text.format.DateUtils.MINUTE_IN_MILLIS,
        android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

internal fun formatRelativeTime(timestamp: Long): String {
    return formatListTime(timestamp)
}

internal fun formatTimelineTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(TimeFormatter)
}

internal fun formatAbsoluteDateTime(timestamp: Long): String {
    if (timestamp <= 0L) return "未知"
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatterFull)
}

internal fun formatStorageSize(
    context: Context,
    bytes: Long,
): String {
    return android.text.format.Formatter.formatShortFileSize(context, bytes.coerceAtLeast(0L))
}

internal fun formatDurationSeconds(seconds: Int): String {
    val minutes = seconds / 60
    val remain = seconds % 60
    return if (minutes > 0) {
        "${minutes}分${remain.toString().padStart(2, '0')}秒"
    } else {
        "${remain}秒"
    }
}

// ── Resource helpers ──────────────────────────────────────────────────────────

internal fun isPdfResource(resource: ImportedResource): Boolean {
    return resource.resolvedMimeType.equals("application/pdf", ignoreCase = true) ||
        resource.mimeType.equals("application/pdf", ignoreCase = true) ||
        resource.storedFileName.endsWith(".pdf", ignoreCase = true) ||
        (resource.originalFileName?.endsWith(".pdf", ignoreCase = true) == true)
}

internal fun normalizeFileTypeLabel(resource: ImportedResource): String {
    if (isPdfResource(resource)) return "PDF"
    val name = resource.originalFileName ?: resource.storedFileName
    val extension = name.substringAfterLast('.', "").trim().uppercase(Locale.ROOT)
    if (extension.isNotBlank()) return extension
    val mime = resource.resolvedMimeType ?: resource.mimeType ?: return "文件"
    return mime.substringAfterLast('/').ifBlank { "文件" }.uppercase(Locale.ROOT)
}

// ── Shared media placeholders & image request builders ────────────────────────

@Composable
internal fun MissingMediaPlaceholder(
    label: String,
    icon: ImageVector,
    dark: Boolean,
) {
    val colors = com.example.yingshi.ui.theme.YingShiThemeTokens.colors
    val background = if (dark) {
        colors.viewerSurface.copy(alpha = 0.76f)
    } else {
        colors.sectionBackground.copy(alpha = 0.88f)
    }
    val contentColor = if (dark) colors.viewerText else colors.textSecondary
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

internal fun buildImportedPreviewImageRequest(
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

internal fun buildImportedOriginalImageRequest(
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

internal fun importedLocalMediaCacheKey(file: File): String {
    val raw = buildString {
        append(file.absolutePath)
        append('#')
        append(file.length())
        append('#')
        append(file.lastModified())
    }
    return sha256(raw)
}

internal fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return buildString(digest.size * 2) {
        digest.forEach { byte -> append("%02x".format(byte)) }
    }
}
