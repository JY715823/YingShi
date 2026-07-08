package com.example.yingshi.feature.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.yingshi.feature.chat.data.ImportedChatDetail
import com.example.yingshi.feature.chat.data.ImportedChatType
import com.example.yingshi.feature.chat.data.ImportedMessageSegment
import com.example.yingshi.feature.chat.data.ImportedParticipant
import com.example.yingshi.feature.chat.data.ImportedRenderableMessage
import com.example.yingshi.feature.chat.data.ImportedResource
import com.example.yingshi.feature.chat.data.ImportedResourceRenderKind
import com.example.yingshi.feature.chat.data.effectiveRenderKind
import com.example.yingshi.feature.chat.data.resolveImportedFaceLabel
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.io.File

// ── Group nickname color palette ──────────────────────────────────────────────

private val GroupNicknameColors = listOf(
    Color(0xFFE57373),
    Color(0xFF64B5F6),
    Color(0xFF81C784),
    Color(0xFFFFB74D),
    Color(0xFFBA68C8),
    Color(0xFF4DB6AC),
    Color(0xFFF06292),
    Color(0xFF7986CB),
)

// ── Message bubble ────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatMessageBubble(
    detail: ImportedChatDetail?,
    renderableMessage: ImportedRenderableMessage,
    participant: ImportedParticipant?,
    qFaceCatalog: QFaceCatalog?,
    highlighted: Boolean,
    highlightQuery: String,
    audioPlayer: ChatAudioPlayerState,
    isConsecutive: Boolean = false,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onJumpToReply: () -> Unit,
    onLongPress: () -> Unit,
    onTap: (() -> Unit)? = null,
    onOpenResource: (ImportedResource) -> Unit,
    onOpenFile: (ImportedResource) -> Unit,
    onLongPressResource: (ImportedResource) -> Unit,
) {
    val message = renderableMessage.message
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val isGroupIncoming = detail?.chatType == ImportedChatType.GROUP && !message.isSelf
    val bubbleColor = when {
        message.system || message.recalled -> colors.memoryWash
        message.isSelf -> colors.softGreenContainer
        else -> colors.raisedSurface
    }
    val highlightColor = colors.primaryContainer.copy(alpha = 0.88f)
    // #5 Search highlight pulse animation
    val animatedBubbleColor by animateColorAsState(
        targetValue = if (highlighted) highlightColor else bubbleColor,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 2400),
        label = "highlightPulse",
    )
    val horizontalArrangement = if (message.isSelf) Arrangement.End else Arrangement.Start
    val replySegment = remember(renderableMessage) {
        renderableMessage.segments.filterIsInstance<ImportedMessageSegment.Reply>().firstOrNull()
    }
    val replyPreviewText = replySegment?.content ?: message.replyPreviewText
    val tailColor = if (message.isSelf) colors.softGreenContainer else colors.raisedSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isConsecutive && !message.isSelf) {
                    Modifier.padding(start = 46.dp)
                } else {
                    Modifier
                }
            ),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.Top,
    ) {
        if (!message.isSelf && !isConsecutive) {
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
            if (isGroupIncoming && !isConsecutive) {
                // #2 Colored nicknames in group chat
                val nicknameColor = remember(participant) {
                    val key = participant?.let { participantStableKey(it.uid, it.uin, it.displayName) }
                        ?: message.senderDisplayName
                    GroupNicknameColors[key.hashCode().mod(GroupNicknameColors.size).let { if (it < 0) it + GroupNicknameColors.size else it }]
                }
                Text(
                    text = participant?.displayName ?: message.senderDisplayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = nicknameColor,
                )
            }
            Surface(
                modifier = Modifier
                    .then(
                        if (!message.system && !message.recalled) {
                            // #4 Bubble tail
                            Modifier.drawBehind {
                                val tailW = 8.dp.toPx()
                                val tailH = 10.dp.toPx()
                                val path = Path()
                                if (message.isSelf) {
                                    val startX = size.width
                                    val startY = size.height - 20.dp.toPx()
                                    path.moveTo(startX, startY)
                                    path.cubicTo(
                                        startX + tailW * 0.6f, startY + tailH * 0.2f,
                                        startX + tailW, startY + tailH * 0.6f,
                                        startX + tailW * 0.3f, startY + tailH,
                                    )
                                    path.cubicTo(
                                        startX + tailW * 0.1f, startY + tailH * 0.7f,
                                        startX, startY + tailH * 0.4f,
                                        startX, startY,
                                    )
                                } else {
                                    val startX = 0f
                                    val startY = size.height - 20.dp.toPx()
                                    path.moveTo(startX, startY)
                                    path.cubicTo(
                                        startX - tailW * 0.6f, startY + tailH * 0.2f,
                                        startX - tailW, startY + tailH * 0.6f,
                                        startX - tailW * 0.3f, startY + tailH,
                                    )
                                    path.cubicTo(
                                        startX - tailW * 0.1f, startY + tailH * 0.7f,
                                        startX, startY + tailH * 0.4f,
                                        startX, startY,
                                    )
                                }
                                path.close()
                                drawPath(path, color = tailColor)
                            }
                        } else {
                            Modifier
                        }
                    )
                    .combinedClickable(
                        onClick = {
                            if (selectionMode) {
                                onTap?.invoke()
                            } else {
                                // default no-op click
                            }
                        },
                        onLongClick = onLongPress,
                    ),
                shape = RoundedCornerShape(
                    topStart = if (message.isSelf) 18.dp else 6.dp,
                    topEnd = if (message.isSelf) 6.dp else 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp,
                ),
                color = if (highlighted) animatedBubbleColor else bubbleColor,
                // #1 Bubble shadow
                shadowElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .then(
                            if (selectionMode && isSelected) {
                                Modifier.drawBehind {
                                    drawRect(
                                        color = colors.primaryContainer.copy(alpha = 0.25f),
                                        size = size,
                                    )
                                }
                            } else {
                                Modifier
                            }
                        ),
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

// ── Reply preview card ────────────────────────────────────────────────────────

@Composable
private fun ReplyPreviewCard(
    previewText: String,
    senderName: String?,
    qFaceCatalog: QFaceCatalog?,
    highlightQuery: String,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = colors.sectionBackground.copy(alpha = 0.58f),
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
                    color = colors.titleAccent,
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
                color = colors.textSecondary,
                emojiScaleEm = 1.24f,
                highlightQuery = highlightQuery,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Message content router ────────────────────────────────────────────────────

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
    val colors = YingShiThemeTokens.colors
    when {
        message.system -> {
            Text(
                text = message.text.ifBlank { "系统消息" },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }

        message.recalled -> {
            Text(
                text = "${message.senderDisplayName} 撤回了一条消息",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
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

// ── Segment renderer ──────────────────────────────────────────────────────────

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

// ── Inline segments ───────────────────────────────────────────────────────────

@Composable
private fun InlineMessageSegments(
    segments: List<ImportedMessageSegment>,
    qFaceCatalog: QFaceCatalog?,
    highlightQuery: String = "",
) {
    val colors = YingShiThemeTokens.colors
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
        color = colors.textPrimary,
        emojiScaleEm = 1.45f,
        highlightQuery = highlightQuery,
    )
}

@Composable
internal fun InlineMessageText(
    segments: List<ChatInlineSegment>,
    qFaceCatalog: QFaceCatalog?,
    textStyle: TextStyle,
    color: Color,
    emojiScaleEm: Float,
    highlightQuery: String = "",
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val highlightBackground = YingShiThemeTokens.colors.primaryContainer.copy(alpha = 0.78f)
    val payload = remember(segments, qFaceCatalog, emojiScaleEm) {
        buildInlineMessagePayload(
            segments = segments,
            qFaceCatalog = qFaceCatalog,
            emojiScaleEm = emojiScaleEm,
        )
    }
    androidx.compose.material3.Text(
        text = remember(payload.text, highlightQuery, highlightBackground) {
            highlightAnnotatedText(
                text = payload.text,
                query = highlightQuery,
                backgroundColor = highlightBackground,
            )
        },
        inlineContent = payload.inlineContent,
        style = textStyle,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
    )
}

// ── Avatar badge ──────────────────────────────────────────────────────────────

@Composable
internal fun AvatarBadge(
    name: String,
    avatarLocalPath: String?,
    size: Dp,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = colors.sectionBackground.copy(alpha = 0.96f),
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
                    color = colors.titleAccent,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
