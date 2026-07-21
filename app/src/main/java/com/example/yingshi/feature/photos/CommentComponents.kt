package com.example.yingshi.feature.photos

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.yingshi.ui.components.rememberYingShiMotionEnabled
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
    requestFocusOnShow: Boolean = false,
    elevated: Boolean = false,
    value: TextFieldValue = TextFieldValue(""),
    onValueChange: (TextFieldValue) -> Unit = {},
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val sendEnabled = value.text.trim().isNotEmpty()
    val containerColor = when {
        darkMode && elevated -> colors.viewerSurface.copy(alpha = 0.92f)
        darkMode -> colors.viewerSurface.copy(alpha = 0.72f)
        elevated -> colors.raisedSurface.copy(alpha = 0.94f)
        else -> colors.sectionBackground.copy(alpha = 0.62f)
    }
    val textColor = if (darkMode) {
        colors.viewerText.copy(alpha = 0.88f)
    } else {
        colors.textPrimary
    }
    val placeholderColor = if (darkMode) {
        colors.viewerTextSecondary.copy(alpha = 0.86f)
    } else {
        colors.textSecondary
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(stateKey, requestFocusOnShow) {
        if (requestFocusOnShow) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
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
            border = BorderStroke(
                1.dp,
                when {
                    darkMode && elevated -> colors.viewerAccent.copy(alpha = 0.28f)
                    darkMode -> colors.viewerAccent.copy(alpha = 0.14f)
                    elevated -> colors.goldAccent.copy(alpha = 0.20f)
                    else -> colors.dividerSoft.copy(alpha = 0.68f)
                },
            ),
            shadowElevation = if (elevated) 2.dp else 0.dp,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = textColor,
                    lineHeight = 20.sp,
                ),
                cursorBrush = SolidColor(textColor),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.text.isBlank()) {
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

        CommentActionButton(
            text = "发送",
            enabled = sendEnabled,
            onClick = {
                val trimmed = value.text.trim()
                if (trimmed.isNotEmpty()) {
                    onSend(trimmed)
                    onValueChange(TextFieldValue(""))
                }
            },
            darkMode = darkMode,
            emphasized = true,
            elevated = elevated,
        )
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
    confirmingDelete: Boolean = false,
    isEditing: Boolean = false,
    editingValue: TextFieldValue = TextFieldValue(comment.content),
    onEditingValueChange: (TextFieldValue) -> Unit = {},
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
    val colors = YingShiThemeTokens.colors
    val textColor = if (darkMode) colors.viewerText.copy(alpha = 0.90f) else colors.textPrimary
    val metaColor = if (darkMode) colors.viewerTextSecondary.copy(alpha = 0.90f) else colors.textSecondary
    val authorColor = if (darkMode) {
        colors.viewerAccent.copy(alpha = 0.96f)
    } else {
        colors.goldAccent.copy(alpha = 0.96f)
    }
    val editFocusRequester = remember(comment.id) { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val motionEnabled = rememberYingShiMotionEnabled()
    val activeBackground = when {
        selectionMode && darkMode -> colors.viewerAccent.copy(alpha = 0.14f)
        selectionMode -> colors.primaryContainer.copy(alpha = 0.42f)
        confirmingDelete && darkMode -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f)
        confirmingDelete -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.40f)
        highlighted && darkMode -> colors.viewerAccent.copy(alpha = 0.12f)
        highlighted -> colors.memoryWash.copy(alpha = 0.96f)
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

    LaunchedEffect(comment.id, isEditing) {
        if (isEditing) {
            editFocusRequester.requestFocus()
            keyboardController?.show()
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = comment.author,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = authorColor,
                )
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = metaColor,
                )
            }

            when {
                isEditing -> {
                    Surface(
                        shape = RoundedCornerShape(radius.md),
                        color = if (darkMode) {
                            colors.viewerSurface.copy(alpha = 0.72f)
                        } else {
                            colors.sectionBackground.copy(alpha = 0.62f)
                        },
                    ) {
                        BasicTextField(
                            value = editingValue,
                            onValueChange = onEditingValueChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(editFocusRequester)
                                .padding(horizontal = spacing.md, vertical = spacing.sm),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = textColor,
                                lineHeight = 20.sp,
                            ),
                            cursorBrush = SolidColor(textColor),
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CommentActionButton(
                            text = "取消",
                            darkMode = darkMode,
                            onClick = onCancelEdit,
                        )
                        CommentActionButton(
                            text = "保存",
                            enabled = editingValue.text.trim().isNotEmpty(),
                            onClick = onSaveEdit,
                            darkMode = darkMode,
                            emphasized = true,
                        )
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
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            lineHeight = 20.sp,
                        ),
                        color = textColor,
                    )
                }
            }
        }

        val menuTransition = remember { MutableTransitionState(false) }
        menuTransition.targetState = showInlineActionMenu && menuPositionProvider != null

        if (menuTransition.currentState || menuTransition.targetState) {
            Popup(
                popupPositionProvider = menuPositionProvider!!,
                onDismissRequest = { onClick?.invoke() },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                ),
            ) {
                AnimatedVisibility(
                    visibleState = menuTransition,
                    enter = if (motionEnabled) fadeIn() + scaleIn() else EnterTransition.None,
                    exit = if (motionEnabled) fadeOut() + scaleOut() else ExitTransition.None,
                ) {
                    CommentInlineActionMenu(
                        darkMode = darkMode,
                        onCopy = onCopyFull,
                        onSelect = onSelectText,
                        onEdit = onEdit,
                        onDelete = onDelete,
                        confirmingDelete = confirmingDelete,
                    )
                }
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
    confirmingDelete: Boolean,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val containerColor = if (darkMode) {
        colors.viewerSurface.copy(alpha = 0.94f)
    } else {
        colors.raisedSurface.copy(alpha = 0.96f)
    }
    val borderColor = if (darkMode) {
        colors.viewerAccent.copy(alpha = 0.14f)
    } else {
        colors.dividerSoft.copy(alpha = 0.62f)
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
            InlineActionMenuItem(
                label = if (confirmingDelete) "确认删除" else "删除",
                darkMode = darkMode,
                danger = true,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun InlineActionMenuItem(
    label: String,
    darkMode: Boolean,
    danger: Boolean = false,
    onClick: (() -> Unit)?,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val textColor = if (danger) {
        if (darkMode) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.error
    } else if (darkMode) {
        colors.viewerText.copy(alpha = 0.92f)
    } else {
        colors.textPrimary
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
    val colors = YingShiThemeTokens.colors
    val textColor = if (darkMode) colors.viewerText.copy(alpha = 0.88f) else colors.textPrimary
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val selectedText = value.selectedTextOrNull()

    LaunchedEffect(value.text) {
        focusRequester.requestFocus()
        keyboardController?.hide()
    }

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
        readOnly = true,
        keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
        cursorBrush = SolidColor(textColor),
    )

    if (selectedText != null && onCopySelection != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            CommentActionButton(
                text = "复制",
                darkMode = darkMode,
                emphasized = true,
                onClick = onCopySelection,
            )
        }
    }
}

@Composable
private fun CommentActionButton(
    text: String,
    enabled: Boolean = true,
    darkMode: Boolean = false,
    emphasized: Boolean = false,
    elevated: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    val containerColor = when {
        darkMode && emphasized && elevated -> colors.viewerAccent.copy(alpha = 0.34f)
        darkMode && emphasized -> colors.viewerAccent.copy(alpha = 0.24f)
        darkMode -> colors.viewerSurface.copy(alpha = 0.72f)
        !enabled -> colors.sectionBackground.copy(alpha = 0.46f)
        emphasized -> colors.primaryContainer.copy(alpha = 0.86f)
        else -> colors.sectionBackground.copy(alpha = 0.72f)
    }
    val contentColor = when {
        !enabled && darkMode -> colors.viewerText.copy(alpha = 0.36f)
        !enabled -> colors.textSecondary.copy(alpha = 0.55f)
        darkMode -> colors.viewerText.copy(alpha = 0.92f)
        emphasized -> colors.titleAccent
        else -> colors.textSecondary
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = containerColor,
        border = BorderStroke(
            1.dp,
            if (darkMode) colors.viewerAccent.copy(alpha = 0.14f) else colors.dividerSoft.copy(alpha = 0.68f),
        ),
        shadowElevation = if (elevated && emphasized && enabled) 2.dp else 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor,
        )
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

fun endOfCommentEditValue(text: String): TextFieldValue {
    return TextFieldValue(text = text, selection = TextRange(text.length))
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

// region RealCommentThread (migrated from SmallAlbumCommentSheets.kt)

@Composable
internal fun RealCommentThreadCard(
    title: String,
    subtitle: String,
    stateKeyPrefix: String,
    emptyText: String,
    state: RealCommentThreadUiState,
    onRetry: () -> Unit,
    onCreateComment: (String) -> Unit,
    onUpdateComment: (String, String) -> Unit,
    onDeleteComment: (String) -> Unit,
    onLoadMore: (() -> Unit)? = null,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.68f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            RealCommentThreadContent(
                state = state,
                stateKeyPrefix = stateKeyPrefix,
                emptyText = emptyText,
                onRetry = onRetry,
                onCreateComment = onCreateComment,
                onUpdateComment = onUpdateComment,
                onDeleteComment = onDeleteComment,
                onLoadMore = onLoadMore,
            )
        }
    }
}

@Composable
internal fun RealCommentThreadContent(
    state: RealCommentThreadUiState,
    stateKeyPrefix: String,
    emptyText: String,
    onRetry: () -> Unit,
    onCreateComment: (String) -> Unit,
    onUpdateComment: (String, String) -> Unit,
    onDeleteComment: (String) -> Unit,
    showInput: Boolean = true,
    inputPlaceholder: String = "写一条评论",
    onLoadMore: (() -> Unit)? = null,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val motion = YingShiThemeTokens.motion
    val motionEnabled = rememberYingShiMotionEnabled()
    val copyComment = rememberCommentCopyHandler()
    var expanded by rememberSaveable(stateKeyPrefix) { mutableStateOf(false) }
    var interactionState by rememberCommentInteractionState(stateKeyPrefix)
    val visibleComments = state.comments.visibleComments(expanded)

    BackHandler(enabled = interactionState.selectedCommentId != null) {
        interactionState = interactionState.copy(
            selectedCommentId = null,
            selectedCommentValue = TextFieldValue(""),
        )
    }
    BackHandler(enabled = interactionState.actionCommentId != null) {
        interactionState = interactionState.copy(actionCommentId = null)
    }

    if (state.errorMessage != null) {
        PostInlineNotice(
            text = state.errorMessage,
            actionLabel = "重试",
            onAction = onRetry,
        )
    }
    if (state.isLoading && visibleComments.isNotEmpty()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Text(
                text = "评论同步中…",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }

    when {
        state.isLoading && visibleComments.isEmpty() -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = "正在读取评论…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }

        visibleComments.isEmpty() -> {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }

        else -> {
            visibleComments.forEach { comment ->
                CommentListItem(
                    comment = comment,
                    timeLabel = formatPostTime(comment.createdAtMillis),
                    onLongPress = {
                        interactionState = interactionState.copy(
                            selectedCommentId = null,
                            selectedCommentValue = TextFieldValue(""),
                            editingCommentId = null,
                            editingDraft = TextFieldValue(""),
                            pendingDeleteCommentId = null,
                            actionCommentId = comment.id,
                        )
                    },
                    onClick = {
                        interactionState = interactionState.copy(
                            selectedCommentId = if (interactionState.selectedCommentId != null) null else interactionState.selectedCommentId,
                            selectedCommentValue = if (interactionState.selectedCommentId != null) TextFieldValue("") else interactionState.selectedCommentValue,
                            pendingDeleteCommentId = null,
                            actionCommentId = null,
                        )
                    },
                    showInlineActionMenu = interactionState.actionCommentId == comment.id &&
                        interactionState.selectedCommentId != comment.id &&
                        interactionState.editingCommentId != comment.id,
                    onCopyFull = {
                        copyComment(comment.content)
                        interactionState = interactionState.copy(actionCommentId = null)
                    },
                    onSelectText = {
                        interactionState = interactionState.copy(
                            selectedCommentId = comment.id,
                            selectedCommentValue = fullCommentSelectionValue(comment.content),
                            editingCommentId = null,
                            editingDraft = TextFieldValue(""),
                            pendingDeleteCommentId = null,
                            actionCommentId = null,
                        )
                    },
                    onEdit = {
                        interactionState = interactionState.copy(
                            editingCommentId = comment.id,
                            editingDraft = endOfCommentEditValue(comment.content),
                            selectedCommentId = null,
                            selectedCommentValue = TextFieldValue(""),
                            pendingDeleteCommentId = null,
                            actionCommentId = null,
                        )
                    },
                    onDelete = {
                        if (interactionState.pendingDeleteCommentId == comment.id) {
                            onDeleteComment(comment.id)
                            interactionState = interactionState.copy(
                                selectedCommentId = if (interactionState.selectedCommentId == comment.id) null else interactionState.selectedCommentId,
                                selectedCommentValue = if (interactionState.selectedCommentId == comment.id) TextFieldValue("") else interactionState.selectedCommentValue,
                                editingCommentId = if (interactionState.editingCommentId == comment.id) null else interactionState.editingCommentId,
                                editingDraft = if (interactionState.editingCommentId == comment.id) TextFieldValue("") else interactionState.editingDraft,
                                pendingDeleteCommentId = null,
                                actionCommentId = null,
                            )
                        } else {
                            interactionState = interactionState.copy(
                                pendingDeleteCommentId = comment.id,
                                actionCommentId = comment.id,
                            )
                        }
                    },
                    confirmingDelete = interactionState.pendingDeleteCommentId == comment.id,
                    isEditing = interactionState.editingCommentId == comment.id,
                    editingValue = if (interactionState.editingCommentId == comment.id) interactionState.editingDraft else endOfCommentEditValue(comment.content),
                    onEditingValueChange = { interactionState = interactionState.copy(editingDraft = it) },
                    onSaveEdit = {
                        onUpdateComment(comment.id, interactionState.editingDraft.text)
                        interactionState = interactionState.copy(
                            editingCommentId = null,
                            editingDraft = TextFieldValue(""),
                            pendingDeleteCommentId = null,
                            actionCommentId = null,
                        )
                    },
                    onCancelEdit = {
                        interactionState = interactionState.copy(
                            editingCommentId = null,
                            editingDraft = TextFieldValue(""),
                        )
                    },
                    selectionMode = interactionState.selectedCommentId == comment.id,
                    selectionFieldValue = if (interactionState.selectedCommentId == comment.id) {
                        interactionState.selectedCommentValue
                    } else {
                        TextFieldValue(comment.content)
                    },
                    onSelectionFieldValueChange = {
                        interactionState = interactionState.copy(selectedCommentValue = it)
                    },
                    onCopySelection = if (interactionState.selectedCommentId == comment.id) {
                        {
                            interactionState.selectedCommentValue.selectedTextOrNull()?.let(copyComment)
                            interactionState = interactionState.copy(
                                selectedCommentId = null,
                                selectedCommentValue = TextFieldValue(""),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }

    if (state.comments.hasHiddenComments(expanded)) {
        PostActionChip(text = "展开更多", onClick = { expanded = true })
    }
    if (state.comments.canCollapseComments(expanded)) {
        PostActionChip(text = "收起", onClick = { expanded = false })
    }
    if (state.hasMore && onLoadMore != null) {
        if (state.isLoading) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(
                    text = "加载更多评论…",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        } else {
            PostActionChip(text = "加载更多评论", onClick = onLoadMore)
        }
    }
    if (state.isMutating) {
        Text(
            text = "正在提交…",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
    }
    if (showInput) {
        var inputValue by remember { mutableStateOf(TextFieldValue("")) }
        CommentInputBar(
            stateKey = "$stateKeyPrefix-input",
            placeholder = inputPlaceholder,
            elevated = true,
            onSend = onCreateComment,
            value = inputValue,
            onValueChange = { inputValue = it },
        )
    }
}

// endregion

