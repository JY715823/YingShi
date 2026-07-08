package com.example.yingshi.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yingshi.feature.chat.data.ImportedChatImportInfo
import com.example.yingshi.feature.chat.data.ImportedChatSummary
import com.example.yingshi.feature.chat.data.ImportedChatType
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens

// ── Chat list screen ──────────────────────────────────────────────────────────

@Composable
internal fun ImportedChatListScreen(
    chats: List<ImportedChatSummary>,
    uiState: ImportedChatUiState,
    onBack: () -> Unit,
    onImportClick: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onManageChat: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChatCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
            )
            Spacer(modifier = Modifier.width(spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "聊天记录",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
                Text(
                    text = if (chats.isEmpty()) "把旧对话放在这里" else "${chats.size} 个会话",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }

        ChatImportButton(
            onClick = onImportClick,
            modifier = Modifier.fillMaxWidth(),
        )

        if (uiState.isImporting) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(radius.lg),
                color = colors.raisedSurface.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.62f)),
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Text(
                        text = uiState.importProgress?.message ?: "正在导入聊天记录",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.titleAccent,
                    )
                    val progress = uiState.importProgress
                    if (progress == null || progress.total <= 0) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = colors.primaryActionPressed,
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { progress.fraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.primaryActionPressed,
                            trackColor = colors.sectionBackground.copy(alpha = 0.72f),
                        )
                        Text(
                            text = "${progress.current} / ${progress.total}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }

        if (chats.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(radius.lg),
                color = colors.sectionBackground.copy(alpha = 0.62f),
                border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.62f)),
            ) {
                Column(
                    modifier = Modifier.padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Text(
                        text = "还没有导入聊天记录",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.titleAccent,
                    )
                    Text(
                        text = "选择导出的聊天记录文件，导入后会保存在本机。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    ChatImportButton(
                        onClick = onImportClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
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

// ── Summary card ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImportedChatSummaryCard(
    chat: ImportedChatSummary,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val qFaceCatalog = rememberQFaceCatalog()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.lg))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            ),
        color = colors.raisedSurface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(radius.lg),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.56f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
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
                        color = colors.titleAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatListTime(chat.lastMessageAtMillis),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
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
                    color = colors.textSecondary,
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
                        color = colors.textSecondary,
                    )
                    Text(
                        text = "上次导入 ${formatRelativeTime(chat.lastImportedAtMillis)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

// ── Circle button ─────────────────────────────────────────────────────────────

@Composable
private fun ChatCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier
            .size(44.dp)
            .yingShiClickable(shape = CircleShape, pressedScale = 0.94f, onClick = onClick),
        shape = CircleShape,
        color = colors.sectionBackground.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colors.titleAccent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ── Import button ─────────────────────────────────────────────────────────────

@Composable
private fun ChatImportButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    Surface(
        modifier = modifier.yingShiClickable(shape = shape, pressedScale = 0.965f, onClick = onClick),
        shape = shape,
        color = colors.primaryContainer.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.74f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.FileUpload,
                contentDescription = null,
                tint = colors.titleAccent,
                modifier = Modifier.size(19.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "导入聊天记录",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
        }
    }
}

// ── Management sheet ──────────────────────────────────────────────────────────

@Composable
internal fun ChatManagementSheet(
    chat: ImportedChatSummary,
    onReimport: () -> Unit,
    onShowImportInfo: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = YingShiThemeTokens.spacing.md, vertical = YingShiThemeTokens.spacing.md),
        verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
    ) {
        Text(
            text = chat.displayName.ifBlank { "会话管理" },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.titleAccent,
        )
        Text(
            text = "${chat.messageCount} 条消息 · 上次导入 ${formatRelativeTime(chat.lastImportedAtMillis)}",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        ManagementActionButton(
            label = "再次导入",
            icon = Icons.Default.Download,
            onClick = onReimport,
        )
        ManagementActionButton(
            label = "会话详情",
            icon = Icons.Default.Description,
            onClick = onShowImportInfo,
        )
        ManagementActionButton(
            label = "删除会话",
            icon = Icons.Default.Close,
            danger = true,
            onClick = onDelete,
        )
    }
}

@Composable
private fun ManagementActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    ChatSheetActionButton(
        label = label,
        icon = icon,
        danger = danger,
        onClick = onClick,
    )
}

// ── Import info dialog ────────────────────────────────────────────────────────

@Composable
internal fun ImportInfoDialog(
    info: ImportedChatImportInfo,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("会话详情") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ImportInfoLine("来源文件", info.sourceFileName ?: "未知")
                ImportInfoLine("最近导入", formatAbsoluteDateTime(info.lastImportedAtMillis))
                ImportInfoLine("当前消息数", "${info.messageCount} 条")
                ImportInfoLine("最近新增", "${info.lastImportAddedMessageCount} 条")
                ImportInfoLine("最近合并", "${info.lastImportMergedMessageCount} 条")
                ImportInfoLine("媒体文件", "${info.lastImportResourceCount} 个")
                ImportInfoLine("头像文件", "${info.lastImportAvatarCount} 个")
                ImportInfoLine("本地占用", formatStorageSize(context, info.storageBytes))
            }
        },
        containerColor = YingShiThemeTokens.colors.raisedSurface,
        titleContentColor = YingShiThemeTokens.colors.titleAccent,
        textContentColor = YingShiThemeTokens.colors.textPrimary,
        confirmButton = {
            ChatDialogActionButton(text = "知道了", emphasized = true, onClick = onDismiss)
        },
    )
}

@Composable
private fun ImportInfoLine(
    label: String,
    value: String,
) {
    val colors = YingShiThemeTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
        )
    }
}

// ── Chat type tag ─────────────────────────────────────────────────────────────

@Composable
private fun ChatTypeTag(type: ImportedChatType) {
    val colors = YingShiThemeTokens.colors
    val label = when (type) {
        ImportedChatType.PRIVATE -> "私聊"
        ImportedChatType.GROUP -> "群聊"
        ImportedChatType.UNKNOWN -> "未知"
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = colors.primaryContainer.copy(alpha = 0.62f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = colors.titleAccent,
        )
    }
}
