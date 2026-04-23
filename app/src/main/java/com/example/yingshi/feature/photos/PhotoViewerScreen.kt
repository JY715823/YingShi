package com.example.yingshi.feature.photos

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ViewerNightTop = Color(0xFF07111C)
private val ViewerNightBottom = Color(0xFF03070E)
private val ViewerNightMiddle = Color(0xFF102032)
private val ViewerSurface = Color(0xFFFFFFFF)

@Composable
fun PhotoViewerScreen(
    route: PhotoViewerRoute,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (route.mediaItems.isEmpty()) {
        EmptyPhotoViewerScreen(
            onBack = onBack,
            modifier = modifier,
        )
        return
    }

    val context = LocalContext.current
    val spacing = YingShiThemeTokens.spacing
    val initialPage = route.initialIndex.coerceIn(0, route.mediaItems.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { route.mediaItems.size },
    )
    val currentIndex by remember(route.mediaItems, pagerState) {
        derivedStateOf {
            pagerState.currentPage.coerceIn(0, route.mediaItems.lastIndex)
        }
    }
    val currentItem = route.mediaItems[currentIndex]
    val overlayUiModel = remember(route.sourceLabel, currentIndex, route.mediaItems.size, currentItem) {
        PhotoViewerOverlayUiModel(
            sourceLabel = route.sourceLabel,
            pageLabel = "${currentIndex + 1} / ${route.mediaItems.size}",
            commentCountLabel = currentItem.commentCount.toString(),
            timeLabel = formatViewerTime(currentItem.mediaDisplayTimeMillis),
            relatedPostsLabel = if (currentItem.postIds.isNotEmpty()) {
                if (currentItem.postIds.size > 1) {
                    "所属帖子 ${currentItem.postIds.size}"
                } else {
                    "所属帖子"
                }
            } else {
                null
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ViewerNightTop,
                        currentItem.palette.end.copy(alpha = 0.22f),
                        ViewerNightMiddle,
                        ViewerNightBottom,
                    ),
                ),
            ),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = route.mediaItems.size > 1,
            key = { page -> route.mediaItems[page].mediaId },
        ) { page ->
            PhotoViewerCanvas(
                media = route.mediaItems[page],
                modifier = Modifier.fillMaxSize(),
            )
        }

        ViewerTopScrim(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(148.dp),
        )

        ViewerBottomScrim(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(236.dp),
        )

        PhotoViewerTopBar(
            sourceLabel = overlayUiModel.sourceLabel,
            pageLabel = overlayUiModel.pageLabel,
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
        )

        PhotoViewerEdgeActions(
            overlayUiModel = overlayUiModel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = spacing.lg, vertical = spacing.lg),
            onOpenComments = {
                Toast.makeText(context, "评论系统将在后续阶段接入", Toast.LENGTH_SHORT).show()
            },
            onOpenOriginal = {
                Toast.makeText(context, "原图 / 原件加载将在后续阶段接入", Toast.LENGTH_SHORT).show()
            },
            onOpenRelatedPosts = {
                Toast.makeText(context, "所属帖子跳转将在后续阶段接入", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun ViewerTopScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.30f),
                    Color.Black.copy(alpha = 0.12f),
                    Color.Transparent,
                ),
            ),
        ),
    )
}

@Composable
private fun ViewerBottomScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.10f),
                    Color.Black.copy(alpha = 0.28f),
                ),
            ),
        ),
    )
}

@Composable
private fun EmptyPhotoViewerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(ViewerNightTop, ViewerNightBottom),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onBack) {
                Text(text = "返回", color = ViewerSurface.copy(alpha = 0.90f))
            }
            Text(
                text = "当前没有可查看的媒体",
                style = MaterialTheme.typography.titleMedium,
                color = ViewerSurface.copy(alpha = 0.92f),
            )
        }
    }
}

@Composable
private fun PhotoViewerTopBar(
    sourceLabel: String,
    pageLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Text(
                text = "返回",
                style = MaterialTheme.typography.titleMedium,
                color = ViewerSurface.copy(alpha = 0.92f),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ViewerCapsule(
                text = sourceLabel,
                emphasized = false,
            )
            ViewerCapsule(
                text = pageLabel,
                emphasized = true,
            )
        }
    }
}

@Composable
private fun PhotoViewerCanvas(
    media: PhotoFeedItem,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val canvasAspectRatio = media.aspectRatio.coerceIn(0.75f, 1.35f)

    Box(
        modifier = modifier.padding(horizontal = spacing.xl, vertical = spacing.xxl),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(canvasAspectRatio)
                .clip(RoundedCornerShape(radius.xl))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            media.palette.start.copy(alpha = 0.98f),
                            media.palette.end.copy(alpha = 0.90f),
                        ),
                    ),
                ),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = spacing.lg, end = spacing.lg)
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(media.palette.accent.copy(alpha = 0.20f)),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = spacing.lg, bottom = spacing.lg)
                    .fillMaxWidth(0.46f)
                    .aspectRatio(2.5f)
                    .clip(RoundedCornerShape(radius.capsule))
                    .background(media.palette.accent.copy(alpha = 0.14f)),
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.10f),
                                Color.Transparent,
                                ViewerNightTop.copy(alpha = 0.12f),
                            ),
                        ),
                    ),
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = spacing.md, bottom = spacing.md),
                shape = RoundedCornerShape(radius.capsule),
                color = ViewerNightTop.copy(alpha = 0.18f),
            ) {
                Text(
                    text = "媒体预览占位",
                    modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                    style = MaterialTheme.typography.labelMedium,
                    color = ViewerSurface.copy(alpha = 0.82f),
                )
            }
        }
    }
}

@Composable
private fun PhotoViewerEdgeActions(
    overlayUiModel: PhotoViewerOverlayUiModel,
    onOpenComments: () -> Unit,
    onOpenOriginal: () -> Unit,
    onOpenRelatedPosts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        ViewerCommentEntry(
            commentCountLabel = overlayUiModel.commentCountLabel,
            onClick = onOpenComments,
        )

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            ViewerCapsule(
                text = overlayUiModel.timeLabel,
                emphasized = false,
            )
            ViewerCapsule(
                text = "原图 / 原件",
                emphasized = false,
                onClick = onOpenOriginal,
            )
            overlayUiModel.relatedPostsLabel?.let { relatedPostsLabel ->
                ViewerCapsule(
                    text = relatedPostsLabel,
                    emphasized = false,
                    onClick = onOpenRelatedPosts,
                )
            }
        }
    }
}

@Composable
private fun ViewerCommentEntry(
    commentCountLabel: String,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(radius.capsule))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = ViewerSurface.copy(alpha = 0.12f),
        ) {
            Text(
                text = "评",
                modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.sm),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ViewerSurface.copy(alpha = 0.94f),
            )
        }

        ViewerCapsule(
            text = commentCountLabel,
            emphasized = true,
        )
    }
}

@Composable
private fun ViewerCapsule(
    text: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val shape = RoundedCornerShape(radius.capsule)

    Surface(
        modifier = modifier
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
        shape = shape,
        color = ViewerSurface.copy(alpha = if (emphasized) 0.14f else 0.10f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = ViewerSurface.copy(alpha = if (emphasized) 0.18f else 0.10f),
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
            color = ViewerSurface.copy(alpha = 0.94f),
        )
    }
}

private fun formatViewerTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}

@Preview(showBackground = true)
@Composable
private fun PhotoViewerScreenPreview() {
    YingShiTheme(darkTheme = true) {
        PhotoViewerScreen(
            route = PhotoViewerRoute(
                mediaItems = FakePhotoFeedRepository.getPhotoFeed(),
                initialIndex = 0,
                sourceLabel = "照片页全局媒体流",
                showPostSegments = false,
            ),
            onBack = { },
        )
    }
}
