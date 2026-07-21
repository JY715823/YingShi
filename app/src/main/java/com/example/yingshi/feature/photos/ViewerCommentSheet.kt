package com.example.yingshi.feature.photos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.theme.YingShiThemeTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoViewerCommentSheet(
    mediaId: String,
    comments: List<CommentUiModel>,
    selectedCommentId: String?,
    autoFocusInput: Boolean,
    onDismiss: () -> Unit,
    isLoading: Boolean = false,
    isMutating: Boolean = false,
    errorMessage: String? = null,
    statusMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    onCreateComment: (String) -> Unit,
    onUpdateComment: (String, String) -> Unit,
    onDeleteComment: (String) -> Unit,
    onShowNotice: (String, Boolean) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val copyComment = rememberCommentCopyHandler()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var expanded by rememberSaveable(mediaId) { mutableStateOf(false) }
    var interactionState by rememberCommentInteractionState(mediaId, selectedCommentId)
    val visibleComments = comments.visibleComments(expanded)

    LaunchedEffect(mediaId, selectedCommentId) {
        if (selectedCommentId == null) {
            interactionState = interactionState.copy(showSelectedCommentNotice = false)
        } else {
            interactionState = interactionState.copy(showSelectedCommentNotice = true)
            kotlinx.coroutines.delay(2400L)
            interactionState = interactionState.copy(showSelectedCommentNotice = false)
        }
    }

    BackHandler(enabled = interactionState.selectedCommentId != null) {
        interactionState = interactionState.copy(
            selectedCommentId = null,
            selectedCommentValue = TextFieldValue(""),
        )
    }
    BackHandler(enabled = interactionState.actionCommentId != null) {
        interactionState = interactionState.copy(actionCommentId = null)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ViewerNightTop,
        contentColor = ViewerSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(ViewerLayoutTuning.commentSheetHeightFraction)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ViewerNightTop.copy(alpha = 0.98f),
                            ViewerNightTop.copy(alpha = 0.94f),
                            ViewerNightBottom.copy(alpha = 0.98f),
                        ),
                    ),
                )
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 6.dp, height = 24.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(ViewerAccent.copy(alpha = 0.82f)),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "媒体评论",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = ViewerSurface.copy(alpha = 0.96f),
                    )
                    Text(
                        text = if (comments.isEmpty()) "给这一帧留一句" else "${comments.size} 条留言",
                        style = MaterialTheme.typography.labelMedium,
                        color = ViewerSurface.copy(alpha = 0.58f),
                    )
                }
            }
            if (interactionState.showSelectedCommentNotice) {
                Text(
                    text = "已定位到这条评论",
                    style = MaterialTheme.typography.labelMedium,
                    color = ViewerAccent.copy(alpha = 0.82f),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                if (errorMessage != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ViewerSurface.copy(alpha = 0.82f),
                        )
                        if (onRetry != null) {
                            ViewerSheetActionButton(text = "重试", emphasized = true, onClick = onRetry)
                        }
                    }
                }
                if (isLoading) {
                    Text(
                        text = "正在读取媒体评论…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ViewerSurface.copy(alpha = 0.68f),
                    )
                } else if (visibleComments.isEmpty()) {
                    Text(
                        text = "还没有留言，给这段记忆留一句。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ViewerSurface.copy(alpha = 0.76f),
                    )
                } else {
                    visibleComments.forEach { comment ->
                        CommentListItem(
                            comment = comment,
                            timeLabel = formatViewerTime(comment.createdAtMillis),
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
                            darkMode = true,
                            highlighted = comment.id == selectedCommentId,
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
                                    onShowNotice("评论已删除", true)
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
                                onShowNotice("评论已更新", true)
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
                if (comments.hasHiddenComments(expanded)) {
                    ViewerSheetActionButton(text = "展开更多评论", onClick = { expanded = true })
                }
                if (comments.canCollapseComments(expanded)) {
                    ViewerSheetActionButton(text = "收起到最新 10 条", onClick = { expanded = false })
                }
                if (isMutating) {
                    Text(
                        text = "正在提交评论操作…",
                        style = MaterialTheme.typography.labelMedium,
                        color = ViewerSurface.copy(alpha = 0.72f),
                    )
                }
            }
            var inputValue by remember { mutableStateOf(TextFieldValue("")) }
            CommentInputBar(
                stateKey = "media-comment-input-$mediaId",
                placeholder = "写一条媒体评论",
                darkMode = true,
                elevated = true,
                requestFocusOnShow = autoFocusInput,
                onSend = { content ->
                    onCreateComment(content)
                    expanded = false
                },
                value = inputValue,
                onValueChange = { inputValue = it },
            )
        }
    }
}
