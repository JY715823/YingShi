package com.example.yingshi.feature.photos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.components.rememberYingShiMotionEnabled
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.components.yingShiSoftReveal
import com.example.yingshi.ui.theme.YingShiThemeTokens
import com.example.yingshi.ui.theme.YingShiViewerAccent
import com.example.yingshi.ui.theme.YingShiViewerBackground
import com.example.yingshi.ui.theme.YingShiViewerOverlayBorder
import com.example.yingshi.ui.theme.YingShiViewerOverlayEdgeGlow
import com.example.yingshi.ui.theme.YingShiViewerSurface
import com.example.yingshi.ui.theme.YingShiViewerText

internal val ViewerNightTop = YingShiViewerSurface
internal val ViewerNightBottom = YingShiViewerBackground
internal val ViewerNightMiddle = YingShiViewerSurface.copy(alpha = 0.92f)
internal val ViewerSurface = YingShiViewerText
internal val ViewerAccent = YingShiViewerAccent
internal val ViewerOverlayEdgeGlow = YingShiViewerOverlayEdgeGlow
internal val ViewerOverlayBorder = YingShiViewerOverlayBorder

internal object ViewerLayoutTuning {
    val topBarStartInset = 4.dp
    val topBarEndInset = 10.dp
    val topBarTopInset = 6.dp
    val backButtonTouchSize = 44.dp
    val canvasHorizontalPadding = 0.dp
    val canvasTopPadding = 68.dp
    val canvasBottomPadding = 104.dp
    val immersiveCanvasTopPadding = 0.dp
    val immersiveCanvasBottomPadding = 0.dp
    val immersiveVideoVerticalTapZone = 76.dp
    val immersiveVideoBottomExitZone = 40.dp
    const val commentPreviewWidthFraction = 0.70f
    val commentPreviewMaxWidth = 288.dp
    val commentPreviewHeight = 172.dp
    val photoFlowEdgeActionsBottomPadding = 0.dp
    val inPostEdgeActionsBottomPadding = 2.dp
    val postSegmentBottomOffset = 2.dp
    const val commentSheetHeightFraction = 0.66f
    const val relatedPostsSheetHeightFraction = 0.42f
    const val zoomedOverlayAlpha = 0.42f
    const val previewCommentsMaxCount = 10
}

internal data class ViewerCommentPanelState(
    val selectedCommentId: String? = null,
)

internal data class ViewerNotice(
    val mediaId: String,
    val message: String,
    val emphasized: Boolean = false,
    val nonce: Int,
)

@Composable
internal fun ViewerTopScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ViewerNightBottom.copy(alpha = 0.62f),
                        ViewerNightBottom.copy(alpha = 0.24f),
                        Color.Transparent,
                    ),
                ),
            )
            .drawBehind {
                drawLine(
                    color = ViewerOverlayEdgeGlow,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            },
    )
}

@Composable
internal fun ViewerBottomScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        ViewerNightBottom.copy(alpha = 0.20f),
                        ViewerNightBottom.copy(alpha = 0.58f),
                    ),
                ),
            )
            .drawBehind {
                drawLine(
                    color = ViewerOverlayEdgeGlow,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            },
    )
}

@Composable
internal fun ViewerNoticeHost(
    notice: ViewerNotice?,
    currentMediaId: String,
    onExpired: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = YingShiThemeTokens.motion
    val spacing = YingShiThemeTokens.spacing
    val motionEnabled = rememberYingShiMotionEnabled()
    val visibleNotice = notice?.takeIf { it.mediaId == currentMediaId }

    LaunchedEffect(visibleNotice?.nonce) {
        val activeNotice = visibleNotice ?: return@LaunchedEffect
        kotlinx.coroutines.delay(motion.viewerNoticeVisibleMillis.toLong())
        onExpired(activeNotice.nonce)
    }

    AnimatedVisibility(
        visible = visibleNotice != null,
        enter = fadeIn(tween(if (motionEnabled) motion.viewerNoticeMillis else 0, easing = motion.easing)) +
            slideInVertically(
                animationSpec = tween(if (motionEnabled) motion.viewerNoticeMillis else 0, easing = motion.easing),
                initialOffsetY = { -it / 5 },
            ),
        exit = fadeOut(tween(if (motionEnabled) motion.stateMillis else 0, easing = motion.easing)) +
            slideOutVertically(
                animationSpec = tween(if (motionEnabled) motion.stateMillis else 0, easing = motion.easing),
                targetOffsetY = { -it / 6 },
            ),
        modifier = modifier,
    ) {
        val activeNotice = visibleNotice ?: return@AnimatedVisibility
        Surface(
            modifier = Modifier
                .yingShiSoftReveal(visible = true, motionEnabled = motionEnabled)
                .widthIn(max = 320.dp),
            shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
            color = ViewerNightTop.copy(alpha = if (activeNotice.emphasized) 0.82f else 0.76f),
            border = BorderStroke(
                width = 1.dp,
                color = ViewerOverlayBorder.copy(alpha = if (activeNotice.emphasized) 0.40f else 0.26f),
            ),
        ) {
            Text(
                text = activeNotice.message,
                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (activeNotice.emphasized) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = ViewerSurface.copy(alpha = 0.94f),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun ViewerAtmosphereLayer(modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(ViewerNightBottom)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ViewerAccent.copy(alpha = 0.16f),
                            ViewerNightMiddle.copy(alpha = 0.16f),
                            Color.Transparent,
                        ),
                        center = Offset(0f, 0f),
                        radius = 980f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ViewerAccent.copy(alpha = 0.10f),
                            ViewerNightTop.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                        center = Offset(1200f, 2200f),
                        radius = 860f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ViewerNightTop.copy(alpha = 0.12f),
                            Color.Transparent,
                            ViewerNightBottom.copy(alpha = 0.34f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
internal fun PhotoViewerTopBar(
    onBack: () -> Unit,
    uploaderIdentity: CollaboratorIdentityUiModel?,
    onShare: () -> Unit,
    onEditTime: () -> Unit,
    onDelete: () -> Unit,
    onOpenRelatedPosts: () -> Unit,
    locationLabel: String?,
    onOpenLocation: () -> Unit,
    modifier: Modifier = Modifier,
    overlayAlpha: Float = 1f,
) {
    // Round 8 第十六轮: TopBar 重做
    // 第一行: 返回键 (左) + 上传者徽章 + 分享 + 相册图标 + 垃圾桶图标 (右)
    // 第二行: 地点胶囊 (右对齐, 最大宽度 220dp, 省略, 点击跳地图)
    Column(
        modifier = modifier.alpha(overlayAlpha),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            val topButtonShape = CircleShape
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .widthIn(max = 230.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .size(ViewerLayoutTuning.backButtonTouchSize)
                        .yingShiClickable(shape = topButtonShape, pressedScale = 0.94f, onClick = onBack),
                    shape = topButtonShape,
                    color = ViewerNightTop.copy(alpha = 0.56f),
                    border = BorderStroke(1.dp, ViewerAccent.copy(alpha = 0.18f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = ViewerSurface.copy(alpha = 0.94f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                uploaderIdentity?.let { identity ->
                    CollaboratorMarkerBadge(
                        identity = identity,
                        size = 44.dp,
                    )
                }
                ViewerIconCircle(
                    icon = Icons.Default.IosShare,
                    contentDescription = "分享",
                    onClick = onShare,
                )
                ViewerIconCircle(
                    icon = Icons.Filled.PhotoAlbum,
                    contentDescription = "所属小相册",
                    onClick = onOpenRelatedPosts,
                )
                ViewerIconCircle(
                    icon = Icons.Filled.Delete,
                    contentDescription = "删除",
                    onClick = onDelete,
                )
            }
        }

        // Round 8 第十八轮: 第二行地点胶囊 (右对齐). 即使无地点也显示"添加地点"占位,
        // 点击跳地图页选择, 选择后服务端回填 locationLabel. 这样用户能主动补全地点.
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            ViewerLocationCapsule(
                text = locationLabel?.takeIf { it.isNotBlank() } ?: "添加地点",
                onClick = onOpenLocation,
                placeholder = locationLabel.isNullOrBlank(),
            )
        }
    }
}

@Composable
private fun ViewerLocationCapsule(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: Boolean = false,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val shape = RoundedCornerShape(radius.capsule)
    // Round 8 第十九轮: 地点胶囊加长加高, 容纳更长地点名, 视觉更平衡.
    // widthIn 220→280, vertical padding xs(8dp)→10dp, 图标 14→16dp.
    Surface(
        modifier = modifier
            .widthIn(max = 280.dp)
            .yingShiClickable(shape = shape, pressedScale = 0.96f, onClick = onClick),
        shape = shape,
        color = ViewerNightTop.copy(alpha = if (placeholder) 0.40f else 0.56f),
        border = BorderStroke(1.dp, ViewerOverlayBorder.copy(alpha = if (placeholder) 0.50f else 1f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Icon(
                imageVector = if (placeholder) Icons.Filled.Add else Icons.Filled.LocationOn,
                contentDescription = null,
                tint = if (placeholder) ViewerAccent.copy(alpha = 0.88f) else ViewerAccent.copy(alpha = 0.94f),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (placeholder) FontWeight.Normal else FontWeight.Medium,
                ),
                color = ViewerSurface.copy(alpha = if (placeholder) 0.72f else 0.94f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ViewerTimeBadge(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val radius = YingShiThemeTokens.radius
    val shape = RoundedCornerShape(radius.capsule)
    Surface(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.yingShiClickable(shape = shape, pressedScale = 0.96f, onClick = onClick)
            } else {
                Modifier
            },
        )
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            ViewerNightTop.copy(alpha = 0.82f),
                            ViewerNightTop.copy(alpha = 0.55f),
                        ),
                    ),
                )
            },
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, ViewerOverlayBorder),
        shadowElevation = 4.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = ViewerSurface.copy(alpha = 0.96f),
        )
    }
}

@Composable
private fun ViewerIconCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val shape = CircleShape
    // Round 8 第十八轮: 去掉 drawBehind { drawRect(...) }, 它画的是矩形背景会超出 CircleShape
    // 边界形成难看的矩形边框. 改用 Surface color 直接设置圆形背景.
    Surface(
        modifier = Modifier
            .size(46.dp)
            .yingShiClickable(shape = shape, pressedScale = 0.94f, onClick = onClick),
        shape = shape,
        color = ViewerNightTop.copy(alpha = 0.56f),
        border = BorderStroke(1.dp, ViewerOverlayBorder),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = ViewerSurface.copy(alpha = 0.90f),
                modifier = Modifier.size(23.dp),
            )
        }
    }
}

@Composable
internal fun PhotoViewerEdgeActions(
    overlayUiModel: PhotoViewerOverlayUiModel,
    originalActionLabel: String,
    timeLabel: String,
    showCommentPreview: Boolean,
    onOpenComments: () -> Unit,
    onEditTime: () -> Unit,
    onOpenOriginal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    // Round 8 第十八轮: 改回单行布局, 时间不挤占评论/加载原图.
    //   [评论]  [时间居中]  [加载原图]  三端对齐
    Box(modifier = modifier.fillMaxWidth()) {
        ViewerCommentEntry(
            commentCountLabel = overlayUiModel.commentCountLabel,
            previewExpanded = showCommentPreview,
            onClick = onOpenComments,
            modifier = Modifier.align(Alignment.CenterStart),
        )

        ViewerTimeBadge(
            text = timeLabel,
            onClick = onEditTime,
            modifier = Modifier.align(Alignment.Center),
        )

        if (overlayUiModel.showOriginalAction) {
            ViewerCapsule(
                text = originalActionLabel,
                emphasized = overlayUiModel.originalLoadState == OriginalLoadState.Loaded,
                enabled = overlayUiModel.originalLoadState != OriginalLoadState.Loading,
                onClick = onOpenOriginal,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        } else {
            ViewerCapsule(
                text = "原图已保存",
                emphasized = false,
                surfaceAlpha = 0.08f,
                contentAlpha = 0.78f,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun ViewerCommentEntry(
    commentCountLabel: String,
    previewExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Row(
        modifier = modifier
            .yingShiClickable(
                shape = RoundedCornerShape(radius.capsule),
                pressedScale = 0.96f,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier,
            shape = CircleShape,
            color = ViewerAccent.copy(alpha = if (previewExpanded) 0.24f else 0.14f),
            border = BorderStroke(1.dp, ViewerOverlayBorder),
        ) {
            Text(
                text = "评",
                modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.sm),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ViewerSurface.copy(alpha = 0.94f),
            )
        }

        if (commentCountLabel != "0") {
            ViewerCapsule(
                text = commentCountLabel,
                emphasized = true,
                surfaceAlpha = if (previewExpanded) 0.18f else 0.14f,
            )
        }
    }
}

@Composable
private fun ViewerCapsule(
    text: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    surfaceAlpha: Float = if (emphasized) 0.14f else 0.10f,
    contentAlpha: Float = 0.94f,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val shape = RoundedCornerShape(radius.capsule)

    Surface(
        modifier = modifier.then(
            if (onClick != null && enabled) {
                Modifier.yingShiClickable(shape = shape, pressedScale = 0.96f, onClick = onClick)
            } else {
                Modifier
            },
        ),
        shape = shape,
        color = ViewerNightTop.copy(alpha = if (enabled) surfaceAlpha + 0.26f else 0.22f),
        border = BorderStroke(
            width = 1.dp,
            color = ViewerOverlayBorder.copy(alpha = if (enabled) surfaceAlpha + 0.10f else 0.08f),
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = if (emphasized) {
                MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            } else {
                MaterialTheme.typography.labelLarge
            },
            color = ViewerSurface.copy(alpha = if (enabled) contentAlpha else 0.58f),
        )
    }
}

@Composable
internal fun ViewerCommentPreviewLayer(
    comments: List<CommentUiModel>,
    onOpenComment: (String) -> Unit,
    onAddComment: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val shape = RoundedCornerShape(radius.lg)

    Surface(
        modifier = modifier
            .fillMaxWidth(ViewerLayoutTuning.commentPreviewWidthFraction)
            .widthIn(max = ViewerLayoutTuning.commentPreviewMaxWidth)
            .height(ViewerLayoutTuning.commentPreviewHeight),
        shape = shape,
        color = ViewerNightTop.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, ViewerOverlayBorder),
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ViewerAccent.copy(alpha = 0.14f),
                            Color.Transparent,
                        ),
                        center = Offset(0f, 0f),
                        radius = 360f,
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.md, vertical = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "媒体评论",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = ViewerSurface.copy(alpha = 0.90f),
                    )
                    ViewerCapsule(
                        text = "添加评论",
                        emphasized = false,
                        surfaceAlpha = 0.12f,
                        contentAlpha = 0.88f,
                        onClick = onAddComment,
                    )
                }
                if (comments.isEmpty()) {
                    Text(
                        text = "当前媒体还没有评论",
                        modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.xs),
                        style = MaterialTheme.typography.bodySmall,
                        color = ViewerSurface.copy(alpha = 0.64f),
                    )
                } else {
                    comments.forEach { comment ->
                        Text(
                            text = "${comment.author}：${comment.content}",
                            modifier = Modifier
                                .clip(RoundedCornerShape(radius.sm))
                                .clickable { onOpenComment(comment.id) }
                                .padding(horizontal = spacing.xs, vertical = spacing.xs),
                            style = MaterialTheme.typography.bodySmall,
                            color = ViewerSurface.copy(alpha = 0.88f),
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ViewerSheetActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        color = if (emphasized) {
            ViewerSurface.copy(alpha = 0.16f)
        } else {
            ViewerSurface.copy(alpha = 0.08f)
        },
        border = BorderStroke(1.dp, ViewerSurface.copy(alpha = if (emphasized) 0.18f else 0.10f)),
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) ViewerSurface.copy(alpha = 0.90f) else ViewerSurface.copy(alpha = 0.38f),
        )
    }
}

@Composable
internal fun ViewerDeleteConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val dialogColors = YingShiThemeTokens.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogColors.raisedSurface,
        titleContentColor = dialogColors.titleAccent,
        textContentColor = dialogColors.textSecondary,
        title = {
            Text(
                text = "删除当前媒体到回收站？",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        },
        text = {
            Text(
                text = "当前媒体会从照片流消失，并影响所有引用它的小相册。删除后会进入映世回收站，可以在回收站中恢复。",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TrashDialogActionButton(
                text = "删除到回收站",
                danger = true,
                onClick = onConfirm,
            )
        },
        dismissButton = {
            TrashDialogActionButton(text = "取消", onClick = onDismiss)
        },
    )
}
