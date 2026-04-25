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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlin.math.roundToInt

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
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    darkMode: Boolean = false,
    highlighted: Boolean = false,
    showInlineActionMenu: Boolean = false,
    onCopyFull: (() -> Unit)? = null,
    onSelectText: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    isEditing: Boolean = false,
    editingValue: String = "",
    onEditingValueChange: (String) -> Unit = {},
    onSaveEdit: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
    selectionMode: Boolean = false,
    selectionFieldValue: TextFieldValue = TextFieldValue(comment.content),
    onSelectionFieldValueChange: (TextFieldValue) -> Unit = {},
    onCopySelection: (() -> Unit)? = null,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val density = LocalDensity.current
    val textColor = if (darkMode) Color.White.copy(alpha = 0.86f) else MaterialTheme.colorScheme.onSurface
    val metaColor = if (darkMode) Color.White.copy(alpha = 0.58f) else MaterialTheme.colorScheme.onSurfaceVariant
    val activeBackground = when {
        selectionMode && darkMode -> Color.White.copy(alpha = 0.10f)
        selectionMode -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        highlighted && darkMode -> Color.White.copy(alpha = 0.08f)
        highlighted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    var itemBounds by remember(comment.id) { mutableStateOf<IntRect?>(null) }
    val menuPositionProvider = remember(itemBounds, density) {
        itemBounds?.let { anchorBounds ->
            CommentInlineMenuPositionProvider(
                anchorBounds = anchorBounds,
                horizontalMarginPx = with(density) { 12.dp.roundToPx() },
                verticalMarginPx = with(density) { 8.dp.roundToPx() },
            )
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(radius.md))
                .then(
                    if (selectionMode) {
                        Modifier
                    } else {
                        Modifier.combinedClickable(
                            onClick = { onClick?.invoke() },
                            onLongClick = onLongPress,
                        )
                    },
                )
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    itemBounds = IntRect(
                        left = bounds.left.roundToInt(),
                        top = bounds.top.roundToInt(),
                        right = bounds.right.roundToInt(),
                        bottom = bounds.bottom.roundToInt(),
                    )
                }
                .background(activeBackground)
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = "${comment.author} · $timeLabel",
                style = MaterialTheme.typography.labelMedium,
                color = metaColor,
            )

            when {
                isEditing -> {
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
                }

                selectionMode -> {
                    CommentSelectableText(
                        value = selectionFieldValue,
                        darkMode = darkMode,
                        onValueChange = onSelectionFieldValueChange,
                        onCopySelection = onCopySelection,
                    )
                }

                else -> {
                    Text(
                        text = comment.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                    )
                }
            }
        }

        if (showInlineActionMenu && menuPositionProvider != null) {
            Popup(
                popupPositionProvider = menuPositionProvider,
                onDismissRequest = { onClick?.invoke() },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                ),
            ) {
                CommentInlineActionMenu(
                    darkMode = darkMode,
                    onCopy = onCopyFull,
                    onSelect = onSelectText,
                    onEdit = onEdit,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun CommentInlineActionMenu(
    darkMode: Boolean,
    onCopy: (() -> Unit)?,
    onSelect: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val containerColor = if (darkMode) {
        Color.Black.copy(alpha = 0.76f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    }
    val borderColor = if (darkMode) {
        Color.White.copy(alpha = 0.10f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
    }

    Surface(
        shape = RoundedCornerShape(radius.lg),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            InlineActionMenuItem(label = "复制", darkMode = darkMode, onClick = onCopy)
            InlineActionMenuItem(label = "选择", darkMode = darkMode, onClick = onSelect)
            InlineActionMenuItem(label = "编辑", darkMode = darkMode, onClick = onEdit)
            InlineActionMenuItem(label = "删除", darkMode = darkMode, onClick = onDelete)
        }
    }
}

@Composable
private fun InlineActionMenuItem(
    label: String,
    darkMode: Boolean,
    onClick: (() -> Unit)?,
) {
    val spacing = YingShiThemeTokens.spacing
    val textColor = if (darkMode) {
        Color.White.copy(alpha = 0.92f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(YingShiThemeTokens.radius.md))
            .clickable(enabled = onClick != null) {
                onClick?.invoke()
            }
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
        color = textColor,
    )
}

@Composable
private fun CommentSelectableText(
    value: TextFieldValue,
    darkMode: Boolean,
    onValueChange: (TextFieldValue) -> Unit,
    onCopySelection: (() -> Unit)?,
) {
    val textColor = if (darkMode) Color.White.copy(alpha = 0.88f) else MaterialTheme.colorScheme.onSurface
    val focusRequester = FocusRequester()
    val selectedText = value.selectedTextOrNull()

    LaunchedEffect(value.text) {
        focusRequester.requestFocus()
    }

    CompositionLocalProvider(LocalTextToolbar provides DisabledTextToolbar) {
        BasicTextField(
            value = value,
            onValueChange = { nextValue ->
                if (nextValue.text == value.text) {
                    onValueChange(nextValue)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            readOnly = false,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
            cursorBrush = SolidColor(textColor),
        )
    }

    if (selectedText != null && onCopySelection != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCopySelection) {
                Text(
                    text = "复制",
                    color = if (darkMode) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private class CommentInlineMenuPositionProvider(
    private val anchorBounds: IntRect,
    private val horizontalMarginPx: Int,
    private val verticalMarginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val preferredAboveY = this.anchorBounds.top - popupContentSize.height - verticalMarginPx
        val fallbackBelowY = this.anchorBounds.bottom + verticalMarginPx
        val clampedY = when {
            preferredAboveY >= verticalMarginPx -> preferredAboveY
            else -> fallbackBelowY.coerceAtMost(
                windowSize.height - popupContentSize.height - verticalMarginPx,
            )
        }.coerceAtLeast(verticalMarginPx)

        val preferredRightX = this.anchorBounds.right - popupContentSize.width
        val fallbackLeftX = this.anchorBounds.left
        val maxX = (windowSize.width - popupContentSize.width - horizontalMarginPx)
            .coerceAtLeast(horizontalMarginPx)
        val clampedX = when {
            preferredRightX >= horizontalMarginPx -> preferredRightX
            else -> fallbackLeftX
        }.coerceIn(horizontalMarginPx, maxX)

        return IntOffset(
            x = clampedX,
            y = clampedY,
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

fun fullCommentSelectionValue(text: String): TextFieldValue {
    return TextFieldValue(text = text, selection = TextRange(0, text.length))
}

fun TextFieldValue.selectedTextOrNull(): String? {
    val start = selection.start.coerceIn(0, text.length)
    val end = selection.end.coerceIn(0, text.length)
    if (start == end) return null
    return text.substring(minOf(start, end), maxOf(start, end))
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

private object DisabledTextToolbar : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden

    override fun hide() = Unit

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = Unit
}
