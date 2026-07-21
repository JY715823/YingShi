package com.example.yingshi.feature.photos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
internal fun FakeSmallAlbumCommentSheet(
    postId: String,
    onClose: () -> Unit,
    onShowNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val copyComment = rememberCommentCopyHandler()
    val comments = FakeCommentRepository.getPostComments(postId)
    var expanded by rememberSaveable(postId) { mutableStateOf(false) }
    var interactionState by rememberCommentInteractionState(postId)
    val visibleComments = comments.visibleComments(expanded)

    BackHandler(enabled = interactionState.selectedCommentId != null) {
        interactionState = interactionState.copy(
            selectedCommentId = null,
            selectedCommentValue = TextFieldValue(""),
        )
    }
    BackHandler(enabled = interactionState.actionCommentId != null) {
        interactionState = interactionState.copy(actionCommentId = null)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.64f)
            .navigationBarsPadding(),
        shape = RoundedCornerShape(radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, colors.goldAccent.copy(alpha = 0.22f)),
        shadowElevation = 8.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.memoryWash.copy(alpha = 0.72f),
                            colors.raisedSurface.copy(alpha = 0.98f),
                            colors.glowWash.copy(alpha = 0.36f),
                        ),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 6.dp, height = 24.dp)
                            .clip(RoundedCornerShape(radius.capsule))
                            .background(colors.goldAccent.copy(alpha = 0.78f)),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "小相册评论",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.titleAccent,
                        )
                        Text(
                            text = if (comments.isEmpty()) "给这段记忆留一句" else "${comments.size} 条留言",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                    }
                    PostActionChip(text = "关闭", onClick = onClose)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    if (visibleComments.isEmpty()) {
                        Text(
                            text = "还没有留言，给这段记忆留一句。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    } else {
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
                                FakeCommentRepository.deletePostComment(postId, comment.id)
                                interactionState = interactionState.copy(
                                    selectedCommentId = if (interactionState.selectedCommentId == comment.id) null else interactionState.selectedCommentId,
                                    selectedCommentValue = if (interactionState.selectedCommentId == comment.id) TextFieldValue("") else interactionState.selectedCommentValue,
                                    editingCommentId = if (interactionState.editingCommentId == comment.id) null else interactionState.editingCommentId,
                                    editingDraft = if (interactionState.editingCommentId == comment.id) TextFieldValue("") else interactionState.editingDraft,
                                    pendingDeleteCommentId = null,
                                    actionCommentId = null,
                                )
                                onShowNotice("评论已删除")
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
                            FakeCommentRepository.updatePostComment(
                                postId = postId,
                                commentId = comment.id,
                                content = interactionState.editingDraft.text,
                            )
                            interactionState = interactionState.copy(
                                editingCommentId = null,
                                editingDraft = TextFieldValue(""),
                                pendingDeleteCommentId = null,
                                actionCommentId = null,
                            )
                            onShowNotice("评论已更新")
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
                    PostActionChip(text = "展开更多评论", onClick = { expanded = true })
                }
                if (comments.canCollapseComments(expanded)) {
                    PostActionChip(text = "收起到最新 10 条", onClick = { expanded = false })
                }
            }
                var inputValue by remember { mutableStateOf(TextFieldValue("")) }
                CommentInputBar(
                    stateKey = "post-comment-input-$postId",
                    placeholder = "写一条小相册评论",
                    modifier = Modifier.imePadding(),
                    elevated = true,
                    onSend = { content ->
                        FakeCommentRepository.addPostComment(postId, content)
                        expanded = false
                    },
                    value = inputValue,
                    onValueChange = { inputValue = it },
                )
            }
        }
    }
}

@Composable
internal fun RealSmallAlbumCommentSheet(
    smallAlbumId: String,
    state: RealCommentThreadUiState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onCreateComment: (String) -> Unit,
    onUpdateComment: (String, String) -> Unit,
    onDeleteComment: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLoadMore: (() -> Unit)? = null,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.64f)
            .navigationBarsPadding(),
        shape = RoundedCornerShape(radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, colors.goldAccent.copy(alpha = 0.22f)),
        shadowElevation = 8.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.memoryWash.copy(alpha = 0.72f),
                            colors.raisedSurface.copy(alpha = 0.98f),
                            colors.glowWash.copy(alpha = 0.36f),
                        ),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 6.dp, height = 24.dp)
                            .clip(RoundedCornerShape(radius.capsule))
                            .background(colors.goldAccent.copy(alpha = 0.78f)),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "小相册评论",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.titleAccent,
                        )
                        Text(
                            text = if (state.comments.isEmpty()) "给这段记忆留一句" else "${state.comments.size} 条留言",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                    }
                    PostActionChip(text = "关闭", onClick = onClose)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    RealCommentThreadContent(
                        state = state,
                        stateKeyPrefix = "real-small-album-comment-$smallAlbumId",
                        emptyText = "还没有留言，给这段记忆留一句。",
                        onRetry = onRetry,
                        onCreateComment = onCreateComment,
                        onUpdateComment = onUpdateComment,
                        onDeleteComment = onDeleteComment,
                        showInput = false,
                        onLoadMore = onLoadMore,
                    )
                }
                var inputValue by remember { mutableStateOf(TextFieldValue("")) }
                CommentInputBar(
                    stateKey = "real-small-album-comment-$smallAlbumId-input",
                    placeholder = "写一条小相册评论",
                    modifier = Modifier.imePadding(),
                    elevated = true,
                    onSend = onCreateComment,
                    value = inputValue,
                    onValueChange = { inputValue = it },
                )
            }
        }
    }
}