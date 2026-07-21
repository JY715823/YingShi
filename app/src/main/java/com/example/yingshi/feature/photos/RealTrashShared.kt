package com.example.yingshi.feature.photos

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yingshi.data.model.RemoteTrashDetail
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

internal const val MissingOriginalMediaMessage = "没有找到可查看的原媒体。"
internal const val EmptyTrashPreviewMessage = "没有更多可显示的内容。"
internal const val MinRealTrashViewerScale = 1f
internal const val MaxRealTrashViewerScale = 4f
internal const val RealTrashViewerResetScale = 1.02f

@Composable
internal fun RealTrashViewerMetaCapsule(
    text: String,
    emphasized: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    Box(modifier = modifier) {
        Surface(
            shape = shape,
            color = colors.viewerSurface.copy(alpha = 0.06f),
        ) {
            Box(modifier = Modifier.fillMaxSize())
        }
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (emphasized) {
                colors.viewerAccent
            } else {
                colors.viewerText.copy(alpha = 0.92f)
            },
        )
    }
}

/**
 * 浅色背景图标按钮 — 用于回收站列表/详情等浅色页面
 * 极简设计：无可见边框/阴影，只有图标 + 微妙的按压反馈
 */
@Composable
internal fun RealTrashLightIconButton(
    icon: ImageVector,
    contentDescription: String,
    destructive: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(44.dp)
            .yingShiClickable(
                shape = shape,
                pressedScale = 0.92f,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 按下时的微妙背景反馈
        Surface(
            shape = shape,
            color = if (destructive) {
                colors.destructiveContainer.copy(alpha = 0.08f)
            } else {
                colors.sectionBackground.copy(alpha = 0.06f)
            },
        ) {
            Box(modifier = Modifier.fillMaxSize())
        }
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (destructive) {
                colors.destructive.copy(alpha = if (enabled) 0.75f else 0.40f)
            } else {
                colors.textSecondary.copy(alpha = if (enabled) 0.85f else 0.45f)
            },
            modifier = Modifier.size(22.dp),
        )
    }
}

internal fun realTrashViewerMediaContentDescription(mediaType: AppMediaType): String {
    return when (mediaType) {
        AppMediaType.VIDEO -> "回收站视频"
        AppMediaType.IMAGE -> "回收站照片"
    }
}

internal fun TrashEntryUiModel.previewMediaIds(): List<String> {
    return buildList {
        commentTargetMediaId?.takeIf { it.isNotBlank() }?.let(::add)
        sourceMediaId?.takeIf { it.isNotBlank() }?.let(::add)
        addAll(relatedMediaIds.filter { it.isNotBlank() })
    }.distinct()
}

internal fun TrashEntryUiModel.commentMediaId(): String? {
    return commentTargetMediaId?.takeIf { it.isNotBlank() }
        ?: sourceMediaId?.takeIf { it.isNotBlank() }
        ?: mediaSnapshot?.mediaId?.takeIf { it.isNotBlank() }
        ?: relatedMediaIds.firstOrNull { it.isNotBlank() }
}

internal fun TrashEntryUiModel.toTrashPostDetailUiModel(mediaIds: List<String>): PostDetailUiModel {
    val postId = sourcePostId?.takeIf { it.isNotBlank() } ?: id
    return PostDetailUiModel(
        postId = postId,
        title = title.ifBlank { "回收站小相册" },
        summary = previewInfo.ifBlank { EmptyTrashPreviewMessage },
        contributorLabel = "回收站小相册",
        postDisplayTimeMillis = deletedAtMillis,
        albumIds = relatedPostIds.ifEmpty { listOf(postId) },
        albumChips = listOf("已删除", "媒体 ${mediaIds.size} 项"),
        mediaItems = mediaIds.map { mediaId ->
            PostDetailMediaUiModel(
                id = mediaId,
                displayTimeMillis = deletedAtMillis,
                commentCount = 0,
                palette = realPaletteFor(mediaId),
                mediaType = AppMediaType.IMAGE,
                aspectRatio = 1f,
                displayTimeSource = DisplayTimeSourceImported,
                mediaSource = realTrashMediaSource(mediaId),
            )
        },
        comments = emptyList(),
    )
}

internal fun TrashEntryUiModel.toTrashPostDetailUiModelFromRemote(detail: RemoteTrashDetail): PostDetailUiModel {
    val postId = sourcePostId?.takeIf { it.isNotBlank() } ?: id
    val relatedMediaIds = detail.item.relatedMediaIds
        .filter { it.isNotBlank() }
        .ifEmpty { previewMediaIds() }
    return PostDetailUiModel(
        postId = postId,
        title = title.ifBlank { "回收站小相册" },
        summary = previewInfo.ifBlank { EmptyTrashPreviewMessage },
        contributorLabel = "回收站小相册",
        postDisplayTimeMillis = deletedAtMillis,
        albumIds = detail.item.relatedPostIds.ifEmpty { listOf(postId) },
        albumChips = listOf("已删除", "媒体 ${relatedMediaIds.size} 项"),
        mediaItems = relatedMediaIds.map { mediaId ->
            PostDetailMediaUiModel(
                id = mediaId,
                displayTimeMillis = deletedAtMillis,
                commentCount = 0,
                palette = realPaletteFor(mediaId),
                mediaType = AppMediaType.IMAGE,
                aspectRatio = 1f,
                displayTimeSource = DisplayTimeSourceImported,
                mediaSource = realTrashMediaSource(mediaId),
            )
        },
        comments = emptyList(),
    )
}

internal fun realTrashEntrySourceLine(entry: TrashEntryUiModel): String {
    return when (entry.type) {
        TrashEntryType.LARGE_ALBUM_DELETED -> {
            val postCount = entry.albumSnapshot?.postSnapshots?.size ?: entry.relatedPostIds.size
            val mediaCount = entry.albumSnapshot?.postSnapshots?.sumOf { it.mediaSnapshots.size }
                ?: entry.relatedMediaIds.size
            "整册删除 · 小相册 $postCount 个 · 媒体 $mediaCount 项"
        }
        TrashEntryType.SMALL_ALBUM_DELETED -> {
            val mediaCount = entry.relatedMediaIds.size
            "小相册删除 · 媒体 $mediaCount 项"
        }
        TrashEntryType.MEDIA_REMOVED -> {
            val source = entry.sourcePostId ?: entry.relatedPostIds.firstOrNull() ?: "当前小相册"
            "从小相册移除 · 来源 $source"
        }
        TrashEntryType.MEDIA_SYSTEM_DELETED -> {
            val postCount = entry.relatedPostIds.size
            "媒体删除 · 影响小相册 $postCount 个"
        }
    }
}

internal fun pendingCleanupDeleteCopy(entry: TrashEntryUiModel): String {
    return when (entry.type) {
        TrashEntryType.MEDIA_SYSTEM_DELETED -> "永久删除后会清理媒体记录、评论、关系以及原文件和预览文件。"
        TrashEntryType.LARGE_ALBUM_DELETED -> "永久删除后会清理大相册、小相册结构、评论和关系；媒体本体保留。"
        TrashEntryType.SMALL_ALBUM_DELETED -> "永久删除后会清理小相册结构、评论和关系；媒体本体保留。"
        TrashEntryType.MEDIA_REMOVED -> "永久删除后只确认小相册与媒体的关系删除；媒体本体保留。"
    }
}

internal fun realTrashMediaSource(
    mediaId: String,
    mediaType: AppMediaType = AppMediaType.IMAGE,
    width: Int? = null,
    height: Int? = null,
    durationMillis: Long? = null,
    mimeType: String? = null,
): AppContentMediaSource {
    val baseUrl = BackendDebugConfig.currentBaseUrl().trimEnd('/')
    val previewUrl = "$baseUrl/api/media/files/$mediaId?variant=preview"
    val originalUrl = "$baseUrl/api/media/files/$mediaId"
    val coverUrl = "$baseUrl/api/media/files/$mediaId?variant=cover"
    return AppContentMediaSource(
        thumbnailUrl = previewUrl,
        mediaUrl = originalUrl,
        originalUrl = originalUrl,
        videoUrl = if (mediaType == AppMediaType.VIDEO) originalUrl else null,
        coverUrl = if (mediaType == AppMediaType.VIDEO) coverUrl else previewUrl,
        mimeType = mimeType,
        width = width,
        height = height,
        durationMillis = durationMillis,
    )
}

internal fun TrashEntryType.isRealMediaTrashType(): Boolean {
    return this == TrashEntryType.MEDIA_SYSTEM_DELETED || this == TrashEntryType.MEDIA_REMOVED
}

internal fun realTrashDaysSince(timeMillis: Long): Long {
    val now = System.currentTimeMillis()
    if (timeMillis <= 0L || now <= timeMillis) return 0L
    return TimeUnit.MILLISECONDS.toDays(now - timeMillis)
}

internal fun realFormatTrashEntryTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}

internal fun realTrashGridPostTitle(entry: TrashEntryUiModel): String {
    return entry.mediaSnapshot?.sourcePostTitle?.takeIf { it.isNotBlank() }
        ?: entry.title?.takeIf { it.isNotBlank() }
        ?: entry.sourcePostId?.takeIf { it.isNotBlank() }
        ?: "来源小相册"
}

internal fun TrashMediaSnapshot.toViewerPhotoFeedItem(): PhotoFeedItem {
    val date = java.util.Calendar.getInstance(Locale.CHINA).apply {
        timeInMillis = displayTimeMillis
    }
    return PhotoFeedItem(
        mediaId = mediaId,
        mediaDisplayTimeMillis = displayTimeMillis,
        displayYear = date.get(java.util.Calendar.YEAR),
        displayMonth = date.get(java.util.Calendar.MONTH) + 1,
        displayDay = date.get(java.util.Calendar.DAY_OF_MONTH),
        commentCount = 0,
        smallAlbumIds = sourcePostId?.let(::listOf).orEmpty(),
        palette = palette,
        mediaType = mediaType,
        aspectRatio = aspectRatio,
        width = width,
        height = height,
        videoDurationMillis = videoDurationMillis,
        displayTimeSource = DisplayTimeSourceImported,
        mediaSource = mediaSource,
    )
}

internal fun TrashMediaSnapshot.viewerVideoDurationMillis(): Long {
    return videoDurationMillis ?: 18_000L
}

internal fun String?.toTrashStateLabel(): String {
    return when (this?.trim()?.lowercase(Locale.ROOT)) {
        null, "", "intrash", "in_trash" -> "在回收站"
        "pendingcleanup", "pending_cleanup" -> "待清理"
        "restored" -> "已恢复"
        "purged", "deleted" -> "已删除"
        else -> "在回收站"
    }
}

@Composable
internal fun RealTrashSectionCard(
    title: String,
    body: String,
    emphasized: Boolean = false,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = if (emphasized) {
            colors.primaryContainer.copy(alpha = 0.62f)
        } else {
            colors.raisedSurface.copy(alpha = 0.94f)
        },
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
    ) {
        Column(
            modifier = Modifier.padding(YingShiThemeTokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
internal fun RealTrashCenteredEmptyState(
    text: String = "当前分类为空",
) {
    val colors = YingShiThemeTokens.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.md),
        ) {
            Box(
                modifier = Modifier.size(72.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = colors.viewerAccent.copy(alpha = 0.36f),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.textSecondary.copy(alpha = 0.46f),
            )
        }
    }
}

@Composable
internal fun RealTrashEntryPreview(
    entry: TrashEntryUiModel,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val media = entry.mediaSnapshot
    val mediaId = media?.mediaId ?: entry.previewMediaIds().firstOrNull()
    if (mediaId == null) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
            color = colors.sectionBackground.copy(alpha = 0.58f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = entry.type.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
        }
        return
    }
    AppContentMediaThumbnail(
        mediaSource = media?.mediaSource ?: realTrashMediaSource(
            mediaId = mediaId,
            mediaType = media?.mediaType ?: AppMediaType.IMAGE,
            width = media?.width,
            height = media?.height,
            durationMillis = media?.videoDurationMillis,
        ),
        mediaType = media?.mediaType ?: AppMediaType.IMAGE,
        palette = realPaletteFor(mediaId),
        modifier = modifier,
        requestSize = 720,
        showLoadingIndicator = true,
        showStatusBadge = true,
        showVideoPlayOverlay = false,
    )
}

@Composable
internal fun RealTrashSelectionOverlay(
    selected: Boolean,
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Box(
        modifier = modifier
            .size(46.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomEnd,
    ) {
        AppMediaSelectionBadge(
            selected = selected,
            modifier = Modifier.padding(end = 2.dp, bottom = 2.dp),
        )
    }
}

@Composable
internal fun RealTrashDaysBadge(
    days: Long,
    modifier: Modifier = Modifier,
) {
    val danger = days > 25
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (danger) {
            colors.destructive.copy(alpha = 0.88f)
        } else {
            colors.viewerBackground.copy(alpha = 0.36f)
        },
        border = BorderStroke(
            1.dp,
            if (danger) {
                colors.destructive.copy(alpha = 0.24f)
            } else {
                colors.viewerAccent.copy(alpha = 0.14f)
            },
        ),
    ) {
        Text(
            text = "${days.coerceAtLeast(0)}天",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.viewerText,
        )
    }
}

@Composable
internal fun RealTrashPostTitleChip(text: String) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .drawBehind {
                drawRect(Brush.linearGradient(listOf(colors.glowWash.copy(alpha = 0.18f), Color.Transparent)))
            },
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = colors.viewerSurface.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, colors.viewerAccent.copy(alpha = 0.34f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = colors.titleAccent,
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun RealTrashPostGridCard(
    entry: TrashEntryUiModel,
    selected: Boolean,
    selectionMode: Boolean,
    actorIdentity: CollaboratorIdentityUiModel? = null,
    showActorBadge: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val mediaCount = entry.relatedMediaIds.size
    val coverMediaId = entry.previewMediaIds().firstOrNull()
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(Brush.linearGradient(listOf(colors.glowWash.copy(alpha = 0.18f), Color.Transparent)))
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.viewerOverlayBorder),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.12f)
                    .clip(RoundedCornerShape(YingShiThemeTokens.radius.md)),
            ) {
                if (coverMediaId.isNullOrBlank()) {
                    RealTrashDeletedMediaPlaceholder(
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    AppContentMediaThumbnail(
                        mediaSource = realTrashMediaSource(coverMediaId),
                        mediaType = AppMediaType.IMAGE,
                        palette = realPaletteFor(coverMediaId),
                        modifier = Modifier.matchParentSize(),
                        requestSize = 384,
                        showLoadingIndicator = true,
                        showStatusBadge = true,
                    )
                }
                RealTrashDaysBadge(
                    days = realTrashDaysSince(entry.deletedAtMillis),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
                RealTrashViewerMetaCapsule(
                    text = "${mediaCount.coerceAtLeast(0)}项媒体",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                )
                RealTrashSelectionOverlay(
                    selected = selected,
                    visible = selectionMode,
                    onClick = onClick,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
                if (showActorBadge && actorIdentity != null) {
                    CollaboratorMarkerBadge(
                        identity = actorIdentity,
                        size = 22.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = if (selectionMode) 30.dp else 6.dp, bottom = 6.dp),
                    )
                }
            }
            Text(
                text = entry.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
            )
            Text(
                text = entry.previewInfo,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Text(
                text = realFormatTrashEntryTime(entry.deletedAtMillis),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary.copy(alpha = 0.82f),
            )
        }
    }
}

// ==================== Round 3 新增 Composable ====================

@Composable
internal fun RealTrashCategoryHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = YingShiThemeTokens.spacing.md, vertical = YingShiThemeTokens.spacing.xs),
    ) {
        Crossfade(
            targetState = title,
            animationSpec = tween(YingShiThemeTokens.motion.sectionMillis, easing = YingShiThemeTokens.motion.easing),
            label = "trash-category-title",
        ) { currentTitle ->
            Text(
                text = currentTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

internal enum class CountdownSeverity { URGENT, WARNING, SAFE }

internal fun countdownSeverity(remainingMillis: Long): CountdownSeverity {
    val oneHourMillis = 1L * 60L * 60L * 1000L
    val sixHourMillis = 6L * 60L * 60L * 1000L
    return when {
        remainingMillis <= oneHourMillis -> CountdownSeverity.URGENT
        remainingMillis <= sixHourMillis -> CountdownSeverity.WARNING
        else -> CountdownSeverity.SAFE
    }
}

@Composable
internal fun RealTrashCountdownBadge(
    remainingMillis: Long,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val severity = countdownSeverity(remainingMillis)

    val gradientColors = when (severity) {
        CountdownSeverity.URGENT -> listOf(colors.memoryContainer, colors.memoryWash)
        CountdownSeverity.WARNING -> listOf(colors.goldAccent.copy(alpha = 0.20f), colors.glowWash)
        CountdownSeverity.SAFE -> listOf(colors.softGreenContainer, colors.glowWash)
    }
    val borderColor = when (severity) {
        CountdownSeverity.URGENT -> colors.destructive.copy(alpha = 0.44f)
        CountdownSeverity.WARNING -> colors.goldAccent.copy(alpha = 0.34f)
        CountdownSeverity.SAFE -> colors.memoryAccent.copy(alpha = 0.30f)
    }
    val textColor = if (severity == CountdownSeverity.URGENT) colors.destructive else colors.titleAccent

    Surface(
        modifier = modifier
            .drawBehind {
                drawRect(Brush.linearGradient(gradientColors))
            },
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = Color.Transparent,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = textColor,
            )
            Text(
                text = formatCountdownRemaining(remainingMillis),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = textColor,
                maxLines = 1,
            )
        }
    }
}

internal fun formatCountdownRemaining(remainingMillis: Long): String {
    if (remainingMillis <= 0L) return "已过期"
    val hours = TimeUnit.MILLISECONDS.toHours(remainingMillis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis) - hours * 60L
    return if (hours >= 1) {
        "剩余 ${hours}h ${minutes}m"
    } else if (minutes >= 1) {
        "剩余 ${minutes}m"
    } else {
        "即将过期"
    }
}

@Composable
internal fun RealTrashLargeAlbumChildList(
    postSnapshots: List<TrashPostSnapshot>,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        postSnapshots.forEach { snapshot ->
            val post = snapshot.post
            val coverMediaId = snapshot.mediaSnapshots.firstOrNull()?.mediaId
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(YingShiThemeTokens.radius.md),
                color = colors.raisedSurface.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, colors.viewerOverlayBorder),
                shadowElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    val coverPalette = post.coverPalette
                    if (coverMediaId != null) {
                        AppContentMediaThumbnail(
                            mediaSource = realTrashMediaSource(coverMediaId),
                            mediaType = post.coverMediaType,
                            palette = coverPalette,
                            modifier = Modifier.size(56.dp),
                            requestSize = 256,
                            showStatusBadge = false,
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(56.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = colors.textSecondary.copy(alpha = 0.46f),
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = post.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.titleAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${post.mediaCount} 项媒体",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RealTrashDetailTopBar(
    title: String,
    onBack: () -> Unit,
    onRestore: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    isMutating: Boolean = false,
    isOfflineReadOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val actionsEnabled = !isMutating && !isOfflineReadOnly
    Row(
        modifier = modifier.fillMaxWidth(),
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
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
            color = colors.titleAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onRestore != null) {
            PostIconButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "恢复",
                onClick = onRestore,
                enabled = actionsEnabled,
            )
        }
        if (onRemove != null) {
            // 删除按钮 - 使用 destructive 样式
            val shape = CircleShape
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .yingShiClickable(shape = shape, pressedScale = 0.94f, enabled = actionsEnabled, onClick = onRemove),
                shape = shape,
                color = colors.destructiveContainer.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, colors.destructive.copy(alpha = 0.22f)),
                shadowElevation = 1.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "移出回收站",
                        tint = colors.destructive,
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun RealTrashOfflineBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.md),
        color = colors.memoryContainer.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, colors.memoryAccent.copy(alpha = 0.22f)),
    ) {
        Row(
            modifier = Modifier.padding(YingShiThemeTokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colors.memoryAccent,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onMemoryContainer,
            )
        }
    }
}
