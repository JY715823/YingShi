package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
internal fun PostDetailLoadingState(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PostDetailInfoState(
        title = "正在读取小相册详情",
        message = "正在读取小相册详情和评论…",
        onBack = onBack,
        modifier = modifier,
        loading = true,
    )
}

@Composable
internal fun PostDetailInfoState(
    title: String,
    message: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    loading: Boolean = false,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .statusBarsPadding()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        SmallAlbumDetailTopBar(
            title = title,
            onBack = onBack,
            onShareAll = {},
            onEdit = {},
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
            color = colors.raisedSurface.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
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
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = colors.primaryAction,
                    )
                }
                if (actionLabel != null && onAction != null) {
                    PostDetailActionButton(text = actionLabel, onClick = onAction)
                }
            }
        }
    }
}

@Composable
internal fun PostInlineNotice(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = colors.sectionBackground.copy(alpha = 0.64f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.56f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            if (actionLabel != null && onAction != null) {
                PostDetailActionButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

@Composable
internal fun PostDetailActionButton(
    text: String,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    Surface(
        modifier = Modifier.yingShiClickable(shape = shape, pressedScale = 0.96f, onClick = onClick),
        shape = shape,
        color = colors.primaryContainer.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.74f)),
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.titleAccent,
        )
    }
}

@Composable
internal fun PostDetailMissingState(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .statusBarsPadding()
            .padding(
                horizontal = YingShiThemeTokens.spacing.lg,
                vertical = YingShiThemeTokens.spacing.md,
            ),
        verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.md),
    ) {
        SmallAlbumDetailTopBar(
            title = "小相册",
            onBack = onBack,
            onShareAll = {},
            onEdit = {},
        )
        Text(
            text = "当前小相册没有可展示的媒体，可能已经被删除、被移出关系，或仍处于系统删除状态。",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
        )
    }
}

@Composable
internal fun SmallAlbumInfoSurface(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape,
    contentPadding: PaddingValues,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier,
        shape = shape,
        color = colors.raisedSurface.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.78f)),
        shadowElevation = 1.dp,
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.glowWash.copy(alpha = 0.28f),
                            colors.memoryWash.copy(alpha = 0.16f),
                            Color.Transparent,
                        ),
                    ),
                )
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
internal fun SmallAlbumDetailTopBar(
    title: String,
    ownershipAvatars: List<CollaboratorIdentityUiModel> = emptyList(),
    onBack: () -> Unit,
    onShareAll: () -> Unit,
    onEdit: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PostIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "返回",
            onClick = onBack,
            buttonSize = 44.dp,
            iconSize = 22.dp,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                color = colors.titleAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (ownershipAvatars.isNotEmpty()) {
                CollaboratorAvatarStack(
                    identities = ownershipAvatars,
                    avatarSize = 24.dp,
                )
            }
        }
        PostIconButton(icon = Icons.Default.IosShare, contentDescription = "分享整个小相册", onClick = onShareAll)
        PostIconButton(icon = Icons.Rounded.Edit, contentDescription = "整理", onClick = onEdit)
    }
}

@Composable
internal fun PostIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    buttonSize: Dp = 48.dp,
    iconSize: Dp = 25.dp,
) {
    val colors = YingShiThemeTokens.colors
    val shape = CircleShape
    Surface(
        modifier = modifier
            .size(buttonSize)
            .yingShiClickable(shape = shape, pressedScale = 0.94f, enabled = enabled, onClick = onClick),
        shape = shape,
        color = if (emphasized) {
            colors.primaryContainer.copy(alpha = 0.92f)
        } else {
            colors.raisedSurface.copy(alpha = if (enabled) 0.94f else 0.50f)
        },
        border = BorderStroke(
            1.dp,
            if (emphasized) {
                colors.glassStroke.copy(alpha = if (enabled) 0.82f else 0.40f)
            } else {
                colors.dividerSoft.copy(alpha = if (enabled) 0.72f else 0.36f)
            },
        ),
        shadowElevation = if (emphasized) 3.dp else 1.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colors.titleAccent.copy(alpha = if (enabled) 1f else 0.50f),
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
internal fun SmallAlbumInfoSection(
    detail: PostDetailUiModel,
    originalSummary: PostOriginalLoadSummary,
    onOpenComments: () -> Unit,
    onLoadAllOriginals: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    var summaryExpanded by rememberSaveable(detail.postId, detail.summary) { mutableStateOf(false) }
    val summary = detail.summary.meaningfulPostSummaryOrNull()
    val albumPalette = remember(detail.albumIds) {
        resolveLargeAlbumPalette(detail.albumIds.firstOrNull().orEmpty())
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        SmallAlbumPrimaryMetaRow(
            timeLabel = formatPostTime(detail.postDisplayTimeMillis),
            mediaCountLabel = "${detail.mediaItems.size} 张",
        )
        if (summary != null) {
            SmallAlbumSummaryText(
                summary = summary,
                expanded = summaryExpanded,
                onToggleExpanded = { summaryExpanded = !summaryExpanded },
                modifier = Modifier.padding(horizontal = spacing.xs, vertical = 2.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                SmallAlbumBelongChip(
                    text = detail.albumChips.firstOrNull().orEmpty(),
                    palette = albumPalette,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PostActionChip(
                    text = originalSummary.buttonLabel,
                    onClick = onLoadAllOriginals,
                    containerColor = colors.primaryContainer.copy(alpha = 0.70f),
                )
                PostIconButton(
                    icon = Icons.Rounded.ChatBubbleOutline,
                    contentDescription = if (detail.comments.isEmpty()) "打开评论" else "打开评论，当前 ${detail.comments.size} 条",
                    onClick = onOpenComments,
                    buttonSize = 40.dp,
                    iconSize = 20.dp,
                )
            }
        }
    }
}

@Composable
internal fun SmallAlbumPrimaryMetaRow(
    timeLabel: String,
    mediaCountLabel: String,
) {
    val spacing = YingShiThemeTokens.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YingShiStatusPill(text = timeLabel)
        YingShiStatusPill(text = mediaCountLabel)
    }
}

@Composable
internal fun SmallAlbumBelongChip(
    text: String,
    palette: PhotoThumbnailPalette?,
) {
    if (text.isBlank()) return
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val accentColor = remember(palette, colors.primaryActionPressed) {
        palette?.let { resolvedPalette ->
            listOf(resolvedPalette.end, resolvedPalette.accent, resolvedPalette.start)
                .minByOrNull { it.luminance() }
        } ?: colors.primaryActionPressed
    }
    val barStartColor = palette?.start ?: colors.primaryContainer
    val emphasizedColor = remember(accentColor, colors.titleAccent) {
        if (accentColor.luminance() > 0.62f) {
            lerp(accentColor, colors.titleAccent, 0.32f)
        } else {
            lerp(accentColor, colors.titleAccent, 0.12f)
        }
    }
    val containerColor = remember(barStartColor, colors.raisedSurface) {
        lerp(colors.raisedSurface, barStartColor, 0.22f)
    }
    val borderColor = remember(emphasizedColor) {
        emphasizedColor.copy(alpha = 0.34f)
    }

    Surface(
        shape = RoundedCornerShape(radius.capsule),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 5.dp, height = 16.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(barStartColor, emphasizedColor),
                        ),
                    ),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                ),
                color = emphasizedColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun resolveLargeAlbumPalette(albumId: String): PhotoThumbnailPalette? {
    if (albumId.isBlank()) return null
    return realPaletteFor(albumId)
}

@Composable
internal fun PostInfoSection(
    detail: PostDetailUiModel,
    originalSummary: PostOriginalLoadSummary,
    onOpenComments: () -> Unit,
    onLoadAllOriginals: () -> Unit,
) {
    SmallAlbumInfoSection(
        detail = detail,
        originalSummary = originalSummary,
        onOpenComments = onOpenComments,
        onLoadAllOriginals = onLoadAllOriginals,
    )
}

@Composable
internal fun PostActionChip(
    text: String,
    onClick: () -> Unit,
    containerColor: Color = YingShiThemeTokens.colors.primaryContainer.copy(alpha = 0.42f),
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(radius.capsule)

    Surface(
        modifier = Modifier
            .yingShiClickable(shape = shape, pressedScale = 0.96f, onClick = onClick),
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.62f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.titleAccent,
        )
    }
}

@Composable
internal fun PostMetaCapsule(
    text: String,
    containerColor: Color = YingShiThemeTokens.colors.softGreenContainer.copy(alpha = 0.58f),
    contentColor: Color = YingShiThemeTokens.colors.textSecondary,
    borderColor: Color? = null,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelMedium,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        shape = RoundedCornerShape(radius.capsule),
        color = containerColor,
        border = borderColor?.let { BorderStroke(1.dp, it) },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = textStyle,
            color = contentColor,
        )
    }
}

@Composable
internal fun SmallAlbumSummaryText(
    summary: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp,
    )
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val canExpand = remember(summary, maxWidthPx, textStyle) {
            maxWidthPx > 0 && summaryExceedsTwoLines(
                summary = summary,
                textMeasurer = textMeasurer,
                textStyle = textStyle,
                maxWidthPx = maxWidthPx,
            )
        }
        val annotatedText = remember(summary, expanded, canExpand, maxWidthPx, textStyle) {
            buildSmallAlbumSummaryAnnotatedString(
                summary = summary,
                expanded = expanded,
                canExpand = canExpand,
                textMeasurer = textMeasurer,
                textStyle = textStyle,
                maxWidthPx = maxWidthPx,
                actionColor = colors.memoryAccent,
            )
        }
        Text(
            text = annotatedText,
            modifier = if (canExpand) Modifier.clickable(onClick = onToggleExpanded) else Modifier,
            style = textStyle,
            color = colors.titleAccent.copy(alpha = 0.94f),
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Clip,
        )
    }
}

internal fun summaryExceedsTwoLines(
    summary: String,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Int,
): Boolean {
    if (maxWidthPx <= 0) return false
    return textMeasurer.measure(
        text = summary,
        style = textStyle,
        constraints = Constraints(maxWidth = maxWidthPx),
        maxLines = 2,
    ).hasVisualOverflow
}

internal fun buildSmallAlbumSummaryAnnotatedString(
    summary: String,
    expanded: Boolean,
    canExpand: Boolean,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Int,
    actionColor: Color,
): androidx.compose.ui.text.AnnotatedString {
    val actionLabel = if (expanded) " 收起" else " 展开"
    val actionStyle = SpanStyle(
        color = actionColor,
        fontWeight = FontWeight.SemiBold,
    )
    if (!canExpand || maxWidthPx <= 0) {
        return buildAnnotatedString { append(summary) }
    }
    if (expanded) {
        return buildAnnotatedString {
            append(summary)
            withStyle(actionStyle) {
                append(actionLabel)
            }
        }
    }

    val suffix = "…$actionLabel"
    var low = 0
    var high = summary.length
    var best = 0
    while (low <= high) {
        val middle = (low + high) / 2
        val candidate = summary.take(middle).trimForSummaryPreview() + suffix
        val fits = !textMeasurer.measure(
            text = candidate,
            style = textStyle,
            constraints = Constraints(maxWidth = maxWidthPx),
            maxLines = 2,
        ).hasVisualOverflow
        if (fits) {
            best = middle
            low = middle + 1
        } else {
            high = middle - 1
        }
    }
    val preview = summary.take(best).trimForSummaryPreview().ifBlank { summary.take(1) }
    return buildAnnotatedString {
        append(preview)
        append("…")
        withStyle(actionStyle) {
            append(actionLabel)
        }
    }
}

internal fun String.trimForSummaryPreview(): String {
    return trimEnd { character ->
        character == ' ' ||
            character == '\n' ||
            character == '，' ||
            character == '。' ||
            character == '、' ||
            character == ',' ||
            character == '.'
    }
}
