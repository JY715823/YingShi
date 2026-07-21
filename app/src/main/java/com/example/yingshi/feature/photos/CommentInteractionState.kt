package com.example.yingshi.feature.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 评论交互状态 — 统一管理三处评论弹窗的交互状态。
 * 替代原本分散在 RealCommentThreadContent、FakeSmallAlbumCommentSheet、
 * PhotoViewerCommentSheet 中的 6-7 个独立 remember 变量。
 */
data class CommentInteractionState(
    val actionCommentId: String? = null,
    val editingCommentId: String? = null,
    val editingDraft: TextFieldValue = TextFieldValue(""),
    val selectedCommentId: String? = null,
    val pendingDeleteCommentId: String? = null,
    val selectedCommentValue: TextFieldValue = TextFieldValue(""),
    val showSelectedCommentNotice: Boolean = false,
)

/**
 * 记住评论交互状态，通过 [stateKey] 区分不同弹窗实例。
 *
 * 返回 [MutableState] 以便调用方通过 `by` 委托使用：
 * ```
 * var interactionState by rememberCommentInteractionState("key")
 * interactionState = interactionState.copy(actionCommentId = "123")
 * ```
 *
 * 使用 [remember] 而非 rememberSaveable，因为 CommentInteractionState 是自定义 data class
 * 且包含 TextFieldValue，无法被默认 Saver 自动序列化。交互状态属于临时 UI 状态，
 * 不需要跨进程死亡保留。
 *
 * @param stateKey 用于区分不同弹窗实例（当前仅用于语义清晰，不影响 remember 行为）
 * @param initialSelectedCommentId 初始高亮评论 ID（Viewer 定位用）
 */
@Composable
fun rememberCommentInteractionState(
    stateKey: String,
    initialSelectedCommentId: String? = null,
): MutableState<CommentInteractionState> {
    return remember(stateKey, initialSelectedCommentId) {
        mutableStateOf(
            CommentInteractionState(
                showSelectedCommentNotice = initialSelectedCommentId != null,
            )
        )
    }
}