package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun AlbumPostCard(
    post: AlbumPostCardUiModel,
    density: AlbumGridDensity,
    isRecentlyUpdated: Boolean = false,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val innerPadding = when (density) {
        AlbumGridDensity.COZY_2 -> spacing.md
        AlbumGridDensity.COZY_3 -> spacing.sm
        AlbumGridDensity.COZY_4 -> spacing.xs
    }
    val summaryMaxLines = if (density == AlbumGridDensity.COZY_2) 2 else 1
    val summary = post.summary.meaningfulPostSummaryOrNull()
    val mediaCountLabel = when (post.mediaCount) {
        0 -> "暂无媒体"
        1 -> "1 张"
        else -> "${post.mediaCount} 张"
    }
    val titleStyle = if (density == AlbumGridDensity.COZY_4) {
        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    } else {
        MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
    }
    val coverAspectRatio = when (density) {
        AlbumGridDensity.COZY_2 -> 1.26f
        AlbumGridDensity.COZY_3 -> 1.18f
        AlbumGridDensity.COZY_4 -> 1.08f
    }
    val previewMedia = remember(
        post.id,
        post.coverRefreshNonce,
        post.coverMediaType,
        post.coverAspectRatio,
        post.coverMediaSource,
        post.previewMedia,
    ) {
        val resolved = post.previewMedia
        resolved
            .ifEmpty {
                listOf(
                    AlbumPostPreviewMediaUiModel(
                        id = "${post.id}-cover",
                        palette = post.coverPalette,
                        mediaType = post.coverMediaType,
                        aspectRatio = post.coverAspectRatio,
                        mediaSource = post.coverMediaSource,
                        refreshKey = "album-cover:${post.id}:${post.coverRefreshNonce}:0",
                    ),
                )
            }
            .distinctBy { it.id }
            .take(2)
            .mapIndexed { index, media ->
                val stableRefreshKey = media.refreshKey
                    ?: media.mediaSource?.thumbnailModelCacheKey(media.mediaType)
                    ?: media.mediaSource?.thumbnailModelUrl(media.mediaType)
                    ?: "album-preview:${post.id}:$index"
                media.copy(refreshKey = stableRefreshKey)
            }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .yingShiClickable(
                shape = RoundedCornerShape(radius.lg),
                onClick = onClick,
                onLongClick = onLongPress,
            ),
        shape = RoundedCornerShape(radius.lg),
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = colors.glassStroke.copy(alpha = 0.22f),
        ),
        shadowElevation = 0.dp,
    ) {
        Box {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(coverAspectRatio)
            ) {
                if (previewMedia.size >= 2) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        AlbumPostPreviewThumbnail(
                            media = previewMedia[0],
                            contentDescription = post.title,
                            modifier = Modifier
                                .weight(1.45f)
                                .fillMaxSize(),
                        )
                        AlbumPostPreviewThumbnail(
                            media = previewMedia[1],
                            contentDescription = post.title,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                        )
                    }
                } else {
                    AlbumPostPreviewThumbnail(
                        media = previewMedia.first(),
                        contentDescription = post.title,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                YingShiMediaFrame(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(topStart = radius.lg, topEnd = radius.lg),
                    memoryActive = isRecentlyUpdated,
                    topScrimAlpha = 0.12f,
                    bottomGlowAlpha = 0.20f,
                )

                if (previewMedia.firstOrNull()?.mediaType == AppMediaType.VIDEO) {
                    VideoMediaMarker(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = innerPadding, start = innerPadding),
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.70f),
                                ),
                            ),
                        )
                        .padding(horizontal = innerPadding, vertical = innerPadding),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = post.title,
                            style = titleStyle.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formatAlbumPostTime(post.postDisplayTimeMillis),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White.copy(alpha = 0.78f),
                                maxLines = 1,
                            )
                            Text(
                                text = mediaCountLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFE8C547),
                                maxLines = 1,
                            )
                        }
                    }
                }

                if (isRecentlyUpdated) {
                    YingShiMemoryBadge(
                        text = "刚更新",
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = innerPadding,
                                top = if (previewMedia.firstOrNull()?.mediaType == AppMediaType.VIDEO) {
                                    innerPadding + 28.dp
                                } else {
                                    innerPadding
                                },
                            ),
                        compact = density == AlbumGridDensity.COZY_4,
                    )
                }
            }

            if (summary != null) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.dividerSoft.copy(alpha = 0.20f)),
                )
                Column(
                    modifier = Modifier.padding(
                        horizontal = innerPadding,
                        vertical = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "简介",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                        ),
                        color = colors.goldAccent.copy(alpha = 0.70f),
                        maxLines = 1,
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            lineHeight = 16.sp,
                        ),
                        color = colors.textPrimary.copy(alpha = 0.85f),
                        maxLines = summaryMaxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun AlbumPostPreviewThumbnail(
    media: AlbumPostPreviewMediaUiModel,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    AppContentMediaThumbnail(
        mediaSource = media.mediaSource.withRefreshKey(media.refreshKey),
        mediaType = media.mediaType,
        palette = media.palette,
        modifier = modifier,
        contentDescription = contentDescription,
        requestSize = 384,
        showLoadingIndicator = false,
    )
}

@Composable
fun PostDetailPlaceholderScreen(
    route: PostDetailPlaceholderRoute,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                shape = CircleShape,
                color = colors.sectionBackground.copy(alpha = 0.80f),
                border = BorderStroke(
                    width = 1.dp,
                    color = colors.dividerSoft.copy(alpha = 0.72f),
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "<",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.titleAccent,
                    )
                }
            }

            Text(
                text = "小相册详情",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(radius.xl),
            color = colors.raisedSurface.copy(alpha = 0.96f),
            border = BorderStroke(
                width = 1.dp,
                color = colors.dividerSoft.copy(alpha = 0.62f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(route.coverAspectRatio.coerceIn(0.94f, 1.10f))
                        .clip(RoundedCornerShape(radius.lg))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(route.coverPalette.start, route.coverPalette.end),
                            ),
                        ),
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Text(
                        text = route.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.titleAccent,
                    )
                    Text(
                        text = route.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DetailMetaCapsule(text = formatAlbumPostTime(route.postDisplayTimeMillis))
                    DetailMetaCapsule(text = "${route.mediaCount} 张媒体")
                }

            }
        }
    }
}

@Composable
private fun DetailMetaCapsule(text: String) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        shape = RoundedCornerShape(radius.capsule),
        color = colors.sectionBackground.copy(alpha = 0.70f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.62f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
    }
}

private fun formatAlbumPostTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(timeMillis))
}

@Preview(showBackground = true)
@Composable
private fun PostDetailPlaceholderScreenPreview() {
    YingShiTheme {
        PostDetailPlaceholderScreen(
            route = FakeAlbumRepository.toPostDetailRoute(FakeAlbumRepository.getPosts().first()),
            onBack = { },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
