package com.example.yingshi.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.yingshi.feature.chat.data.ImportedChatDetail
import com.example.yingshi.feature.chat.data.ImportedChatType
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens

// ── Top bar ──

@Composable
internal fun ImportedChatTopBar(
    detail: ImportedChatDetail?,
    onBack: () -> Unit,
    onManage: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
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
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
            )
            AvatarBadge(
                name = detail?.displayName.orEmpty(),
                avatarLocalPath = resolveTitleAvatar(detail),
                size = 40.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail?.displayName?.ifBlank { "聊天记录" } ?: "聊天记录",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
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
                    color = colors.textSecondary,
                )
            }
            ChatIconActionButton(
                icon = Icons.Default.Description,
                contentDescription = "会话管理",
                onClick = onManage,
            )
        }
    }
}

// ── Icon action button (circle) ──

@Composable
internal fun ChatIconActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    emphasized: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = YingShiThemeTokens.colors
    val containerColor = when {
        !enabled -> colors.sectionBackground.copy(alpha = 0.54f)
        danger -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.88f)
        emphasized -> colors.primaryContainer.copy(alpha = 0.84f)
        else -> colors.raisedSurface.copy(alpha = 0.95f)
    }
    val contentColor = when {
        !enabled -> colors.textSecondary.copy(alpha = 0.58f)
        danger -> MaterialTheme.colorScheme.onErrorContainer
        else -> colors.titleAccent
    }
    val borderColor = if (danger) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
    } else {
        colors.dividerSoft.copy(alpha = 0.70f)
    }
    Surface(
        modifier = modifier
            .size(size)
            .yingShiClickable(
                enabled = enabled,
                shape = CircleShape,
                pressedScale = 0.94f,
                onClick = onClick,
            ),
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size((size.value * 0.46f).dp),
            )
        }
    }
}

// ── Dialog action button (capsule) ──

@Composable
internal fun ChatDialogActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    danger: Boolean = false,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    val containerColor = when {
        !enabled -> colors.sectionBackground.copy(alpha = 0.54f)
        danger -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.88f)
        emphasized -> colors.primaryContainer.copy(alpha = 0.82f)
        else -> colors.raisedSurface.copy(alpha = 0.96f)
    }
    val contentColor = when {
        !enabled -> colors.textSecondary.copy(alpha = 0.58f)
        danger -> MaterialTheme.colorScheme.onErrorContainer
        else -> colors.titleAccent
    }
    val borderColor = if (danger) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
    } else {
        colors.dividerSoft.copy(alpha = 0.68f)
    }
    Surface(
        modifier = modifier.yingShiClickable(
            enabled = enabled,
            shape = shape,
            pressedScale = 0.96f,
            onClick = onClick,
        ),
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor,
        )
    }
}

// ── Sheet action button (full-width row) ──

@Composable
internal fun ChatSheetActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    val colors = YingShiThemeTokens.colors
    val radius = YingShiThemeTokens.radius
    val spacing = YingShiThemeTokens.spacing
    val shape = RoundedCornerShape(radius.lg)
    val iconBg = if (danger) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.88f)
    } else {
        colors.primaryContainer.copy(alpha = 0.58f)
    }
    val contentColor = if (danger) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        colors.titleAccent
    }
    val containerColor = if (danger) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.54f)
    } else {
        colors.raisedSurface.copy(alpha = 0.94f)
    }
    val borderColor = if (danger) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.20f)
    } else {
        colors.dividerSoft.copy(alpha = 0.64f)
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .yingShiClickable(shape = shape, pressedScale = 0.97f, onClick = onClick),
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = iconBg) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(18.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor,
            )
        }
    }
}
