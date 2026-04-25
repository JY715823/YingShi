package com.example.yingshi.feature.photos

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.example.yingshi.ui.theme.YingShiThemeTokens

private const val DefaultVisibleCommentCount = 10

@Composable
fun CommentInputBar(
    stateKey: String,
    placeholder: String,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    darkMode: Boolean = false,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    var value by rememberSaveable(stateKey) { mutableStateOf("") }
    val sendEnabled = value.trim().isNotEmpty()
    val containerColor = if (darkMode) {
        Color.White.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
    }
    val textColor = if (darkMode) {
        Color.White.copy(alpha = 0.88f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val placeholderColor = if (darkMode) {
        Color.White.copy(alpha = 0.42f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(radius.lg),
            color = containerColor,
        ) {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                cursorBrush = SolidColor(textColor),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isBlank()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = placeholderColor,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }

        TextButton(
            enabled = sendEnabled,
            onClick = {
                val trimmed = value.trim()
                if (trimmed.isNotEmpty()) {
                    onSend(trimmed)
                    value = ""
                }
            },
        ) {
            Text(
                text = "发送",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (sendEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    placeholderColor
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommentListItem(
    comment: CommentUiModel,
    timeLabel: String,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    darkMode: Boolean = false,
    highlighted: Boolean = false,
    selectedForCopy: Boolean = false,
    isEditing: Boolean = false,
    editingValue: String = "",
    onEditingValueChange: (String) -> Unit = {},
    onSaveEdit: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onCopySelection: (() -> Unit)? = null,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val textColor = if (darkMode) Color.White.copy(alpha = 0.86f) else MaterialTheme.colorScheme.onSurface
    val metaColor = if (darkMode) Color.White.copy(alpha = 0.58f) else MaterialTheme.colorScheme.onSurfaceVariant
    val activeBackground = when {
        selectedForCopy && darkMode -> Color.White.copy(alpha = 0.10f)
        selectedForCopy -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        highlighted && darkMode -> Color.White.copy(alpha = 0.08f)
        highlighted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else -> Color.Transparent
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.md))
            .combinedClickable(
                onClick = {},
                onLongClick = onLongPress,
            )
            .background(activeBackground)
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = "${comment.author} · $timeLabel",
            style = MaterialTheme.typography.labelMedium,
            color = metaColor,
        )

        if (isEditing) {
            Surface(
                shape = RoundedCornerShape(radius.md),
                color = if (darkMode) {
                    Color.White.copy(alpha = 0.08f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
                },
            ) {
                BasicTextField(
                    value = editingValue,
                    onValueChange = onEditingValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.md, vertical = spacing.sm),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                    cursorBrush = SolidColor(textColor),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancelEdit) {
                    Text(text = "取消", color = metaColor)
                }
                TextButton(
                    enabled = editingValue.trim().isNotEmpty(),
                    onClick = onSaveEdit,
                ) {
                    Text(
                        text = "保存",
                        color = if (editingValue.trim().isNotEmpty()) {
                            if (darkMode) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.primary
                        } else {
                            metaColor
                        },
                    )
                }
            }
        } else {
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )

            if (selectedForCopy && onCopySelection != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "已选中当前评论全文",
                        style = MaterialTheme.typography.labelSmall,
                        color = metaColor,
                    )
                    TextButton(onClick = onCopySelection) {
                        Text(
                            text = "复制",
                            color = if (darkMode) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentActionMenuSheet(
    comment: CommentUiModel,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
    darkMode: Boolean = false,
) {
    val spacing = YingShiThemeTokens.spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val containerColor = if (darkMode) Color(0xFF07111C) else MaterialTheme.colorScheme.surface
    val contentColor = if (darkMode) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSurface
    val hintColor = if (darkMode) Color.White.copy(alpha = 0.58f) else MaterialTheme.colorScheme.onSurfaceVariant

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = "评论操作",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor,
            )
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodySmall,
                color = hintColor,
            )
            CommentActionButton(label = "编辑", onClick = onEdit, darkMode = darkMode)
            CommentActionButton(label = "删除", onClick = onDelete, darkMode = darkMode)
            CommentActionButton(label = "选择", onClick = onSelect, darkMode = darkMode)
        }
    }
}

@Composable
private fun CommentActionButton(
    label: String,
    onClick: () -> Unit,
    darkMode: Boolean,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val containerColor = if (darkMode) {
        Color.White.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f)
    }
    val textColor = if (darkMode) {
        Color.White.copy(alpha = 0.92f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.lg))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(radius.lg),
        color = containerColor,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = textColor,
        )
    }
}

@Composable
fun rememberCommentCopyHandler(): (String) -> Unit {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    return { text ->
        clipboardManager.setText(AnnotatedString(text))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }
}

fun List<CommentUiModel>.visibleComments(expanded: Boolean): List<CommentUiModel> {
    return if (expanded) {
        this
    } else {
        take(DefaultVisibleCommentCount)
    }
}

fun List<CommentUiModel>.hasHiddenComments(expanded: Boolean): Boolean {
    return !expanded && size > DefaultVisibleCommentCount
}

fun List<CommentUiModel>.canCollapseComments(expanded: Boolean): Boolean {
    return expanded && size > DefaultVisibleCommentCount
}
