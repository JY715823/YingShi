package com.example.yingshi.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.feature.photos.AppContentMediaThumbnail
import com.example.yingshi.ui.components.rememberYingShiMotionEnabled
import com.example.yingshi.ui.components.yingShiHapticClickable
import com.example.yingshi.ui.components.yingShiMemoryGlow
import com.example.yingshi.ui.components.yingShiSoftReveal
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenPhotos: () -> Unit = {},
    onOpenLedger: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val uiState = rememberHomeUiState()
    val motionEnabled = rememberYingShiMotionEnabled()

    HomeBackdrop(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 22.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "映世",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                    modifier = Modifier.yingShiSoftReveal(motionEnabled = motionEnabled),
                )
                HomeNotificationBellButton(
                    unreadCount = uiState.unreadNotificationCount,
                    onClick = onOpenNotifications,
                )
            }

            if (uiState.isReadOnly) {
                HomeStatusPill(
                    text = "缓存只读",
                    modifier = Modifier.yingShiSoftReveal(motionEnabled = motionEnabled),
                )
            }

            HomeFoyerCard(
                uiState = uiState,
                modifier = Modifier.yingShiSoftReveal(motionEnabled = motionEnabled),
                onClick = onOpenPhotos,
            )

            HomeRecentPhotosCard(
                summary = uiState.recentPhotos,
                modifier = Modifier.yingShiSoftReveal(motionEnabled = motionEnabled),
                onClick = onOpenPhotos,
            )

            HomeLedgerCard(
                summary = uiState.ledger,
                modifier = Modifier.yingShiSoftReveal(motionEnabled = motionEnabled),
                onClick = onOpenLedger,
            )

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun HomeBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.appBackground,
                        colors.sectionBackground.copy(alpha = 0.94f),
                        colors.glowWash.copy(alpha = 0.98f),
                        colors.appBackground,
                    ),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xD6FBFFFF), Color(0x36FBFFFF), Color.Transparent),
                    center = Offset(width * 0.18f, height * 0.16f),
                    radius = height * 0.34f,
                ),
                radius = height * 0.34f,
                center = Offset(width * 0.18f, height * 0.16f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xB6FFE3C8), Color(0x2CFFE3C8), Color.Transparent),
                    center = Offset(width * 0.86f, height * 0.18f),
                    radius = height * 0.28f,
                ),
                radius = height * 0.28f,
                center = Offset(width * 0.86f, height * 0.18f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xA2FFD4E4), Color(0x1FFFD4E4), Color.Transparent),
                    center = Offset(width * 0.84f, height * 0.74f),
                    radius = height * 0.30f,
                ),
                radius = height * 0.30f,
                center = Offset(width * 0.84f, height * 0.74f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x8FCBF5F1), Color(0x18CBF5F1), Color.Transparent),
                    center = Offset(width * 0.14f, height * 0.78f),
                    radius = height * 0.30f,
                ),
                radius = height * 0.30f,
                center = Offset(width * 0.14f, height * 0.78f),
            )

            val upperAurora = Path().apply {
                moveTo(-width * 0.10f, height * 0.10f)
                cubicTo(
                    width * 0.10f,
                    height * 0.00f,
                    width * 0.34f,
                    height * 0.24f,
                    width * 0.58f,
                    height * 0.08f,
                )
                cubicTo(
                    width * 0.82f,
                    height * -0.02f,
                    width * 0.98f,
                    height * 0.20f,
                    width * 1.08f,
                    height * 0.02f,
                )
                lineTo(width * 1.08f, height * 0.18f)
                cubicTo(
                    width * 0.88f,
                    height * 0.30f,
                    width * 0.62f,
                    height * 0.14f,
                    width * 0.30f,
                    height * 0.30f,
                )
                cubicTo(
                    width * 0.12f,
                    height * 0.38f,
                    width * 0.00f,
                    height * 0.24f,
                    -width * 0.10f,
                    height * 0.24f,
                )
                close()
            }
            drawPath(
                path = upperAurora,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0x08FFFFFF), Color(0x76FFF6E8), Color(0x2CBEEBFF), Color(0x10FFFFFF)),
                    start = Offset(width * 0.06f, height * 0.04f),
                    end = Offset(width * 0.94f, height * 0.26f),
                ),
            )

            val lowerAurora = Path().apply {
                moveTo(-width * 0.06f, height * 0.90f)
                cubicTo(
                    width * 0.14f,
                    height * 0.70f,
                    width * 0.38f,
                    height * 0.98f,
                    width * 0.64f,
                    height * 0.78f,
                )
                cubicTo(
                    width * 0.84f,
                    height * 0.64f,
                    width * 0.96f,
                    height * 0.90f,
                    width * 1.06f,
                    height * 0.68f,
                )
                lineTo(width * 1.06f, height * 0.84f)
                cubicTo(
                    width * 0.86f,
                    height * 1.00f,
                    width * 0.58f,
                    height * 0.86f,
                    width * 0.24f,
                    height * 1.02f,
                )
                cubicTo(
                    width * 0.04f,
                    height * 1.06f,
                    -width * 0.02f,
                    height * 0.94f,
                    -width * 0.06f,
                    height * 0.90f,
                )
                close()
            }
            drawPath(
                path = lowerAurora,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0x10FFFFFF), Color(0x58CBFFF7), Color(0x44FFDBC1), Color(0x10FFFFFF)),
                    start = Offset(width * 0.08f, height * 0.72f),
                    end = Offset(width * 0.92f, height * 0.96f),
                ),
            )

            val glassSweep = Path().apply {
                moveTo(width * 0.08f, height * 0.46f)
                cubicTo(
                    width * 0.28f,
                    height * 0.30f,
                    width * 0.48f,
                    height * 0.62f,
                    width * 0.68f,
                    height * 0.42f,
                )
                cubicTo(
                    width * 0.84f,
                    height * 0.30f,
                    width * 0.94f,
                    height * 0.50f,
                    width * 1.02f,
                    height * 0.38f,
                )
            }
            drawPath(
                path = glassSweep,
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color(0x9EFFFFFF), Color(0x44D9FFF8), Color.Transparent),
                ),
                style = Stroke(width = 24f, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

@Composable
private fun HomeStatusPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = colors.raisedSurface.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.68f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.memoryAccent,
        )
    }
}

@Composable
private fun HomeFoyerCard(
    uiState: HomeUiState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val shape = RoundedCornerShape(radius.xl)
    val hasPhotos = uiState.recentPhotos.hasPhotos
    val headline = if (hasPhotos) {
        "${formatHomeRelativeTime(uiState.recentPhotos.latestPhotoAtMillis)} · ${uiState.recentPhotos.totalCount} 张回忆"
    } else {
        "把新的回忆收进来"
    }
    val supporting = if (hasPhotos) {
        "最近更新 ${formatHomeTimeStamp(uiState.recentPhotos.latestPhotoAtMillis)}"
    } else {
        "门厅会在有缓存后点亮最近照片"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.94f)
            .yingShiHapticClickable(shape = shape, pressedScale = 0.985f, onClick = onClick),
        shape = shape,
        color = colors.raisedSurface.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.48f)),
        shadowElevation = 3.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            colors.raisedSurface.copy(alpha = 0.28f),
                            colors.glowWash.copy(alpha = 0.22f),
                            colors.memoryWash.copy(alpha = 0.12f),
                        ),
                    ),
                ),
        ) {
            HomeFoyerCollage(
                summary = uiState.recentPhotos,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.20f),
                                Color.Transparent,
                                colors.viewerBackground.copy(alpha = 0.14f),
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                HomeStatusPill(text = uiState.spaceLabel)
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(
                        text = "最近照片",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.raisedSurface.copy(alpha = 0.96f),
                    )
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.raisedSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.raisedSurface.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeFoyerCollage(
    summary: HomeRecentPhotosSummary,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val tiles = summary.tiles
    Row(
        modifier = modifier.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier.weight(1.08f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HomePhotoPane(
                tile = tiles.getOrNull(0),
                fallbackBrush = Brush.linearGradient(
                    listOf(
                        colors.primaryContainer.copy(alpha = 0.72f),
                        colors.glowWash.copy(alpha = 0.88f),
                    ),
                ),
                modifier = Modifier.weight(1.32f),
            )
            Row(
                modifier = Modifier.weight(0.88f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HomePhotoPane(
                    tile = tiles.getOrNull(3),
                    fallbackBrush = Brush.linearGradient(
                        listOf(
                            colors.softGreenContainer.copy(alpha = 0.84f),
                            colors.sectionBackground.copy(alpha = 0.90f),
                        ),
                    ),
                    modifier = Modifier.weight(1f),
                )
                HomePhotoPane(
                    tile = tiles.getOrNull(4),
                    fallbackBrush = Brush.linearGradient(
                        listOf(
                            colors.memoryWash.copy(alpha = 0.86f),
                            colors.raisedSurface.copy(alpha = 0.92f),
                        ),
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Column(
            modifier = Modifier.weight(0.92f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HomePhotoPane(
                tile = tiles.getOrNull(1),
                fallbackBrush = Brush.linearGradient(
                    listOf(
                        colors.raisedSurface.copy(alpha = 0.94f),
                        colors.glowWash.copy(alpha = 0.72f),
                    ),
                ),
                modifier = Modifier.weight(0.92f),
            )
            HomePhotoPane(
                tile = tiles.getOrNull(2),
                fallbackBrush = Brush.linearGradient(
                    listOf(
                        colors.sectionBackground.copy(alpha = 0.90f),
                        colors.memoryWash.copy(alpha = 0.56f),
                    ),
                ),
                modifier = Modifier.weight(1.12f),
            )
        }
    }
}

@Composable
private fun HomePhotoPane(
    tile: HomePhotoTile?,
    fallbackBrush: Brush,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val radius = RoundedCornerShape(24.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = radius,
        color = Color.Transparent,
        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.26f)),
    ) {
        if (tile == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(fallbackBrush)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                colors.raisedSurface.copy(alpha = 0.24f),
                                Color.Transparent,
                            ),
                            center = Offset(0.22f, 0.18f),
                            radius = 900f,
                        ),
                    ),
            )
        } else {
            AppContentMediaThumbnail(
                mediaSource = tile.mediaSource,
                mediaType = tile.mediaType,
                palette = tile.palette,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                requestSize = 720,
                showLoadingIndicator = false,
                showStatusBadge = false,
                showVideoPlayOverlay = tile.mediaType != com.example.yingshi.feature.photos.AppMediaType.IMAGE,
            )
        }
    }
}

@Composable
private fun HomeRecentPhotosCard(
    summary: HomeRecentPhotosSummary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.xl)
    val headline = if (summary.hasPhotos) {
        "${formatHomeRelativeTime(summary.latestPhotoAtMillis)} · ${summary.totalCount} 张"
    } else {
        "还没有照片缓存"
    }
    val supporting = if (summary.hasPhotos) {
        "最近一组回忆 ${formatHomeTimeStamp(summary.latestPhotoAtMillis)}"
    } else {
        "进入照片页后，这里会带回最近回忆"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .yingShiHapticClickable(shape = shape, pressedScale = 0.988f, onClick = onClick),
        shape = shape,
        color = colors.raisedSurface.copy(alpha = 0.84f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.76f)),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            colors.raisedSurface.copy(alpha = 0.92f),
                            colors.glowWash.copy(alpha = 0.34f),
                            colors.memoryWash.copy(alpha = 0.22f),
                        ),
                    ),
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = "最近照片",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            HomeMiniPhotoStrip(summary = summary)
        }
    }
}

@Composable
private fun HomeMiniPhotoStrip(
    summary: HomeRecentPhotosSummary,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val tiles = summary.tiles.take(3)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (tiles.isEmpty()) {
            repeat(3) { index ->
                Surface(
                    modifier = Modifier.size(width = 36.dp, height = 52.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = if (index == 1) {
                        colors.glowWash.copy(alpha = 0.82f)
                    } else {
                        colors.sectionBackground.copy(alpha = 0.82f)
                    },
                    border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.56f)),
                ) {}
            }
        } else {
            tiles.forEach { tile ->
                Surface(
                    modifier = Modifier.size(width = 36.dp, height = 52.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.30f)),
                ) {
                    AppContentMediaThumbnail(
                        mediaSource = tile.mediaSource,
                        mediaType = tile.mediaType,
                        palette = tile.palette,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        requestSize = 256,
                        showLoadingIndicator = false,
                        showStatusBadge = false,
                        showVideoPlayOverlay = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeLedgerCard(
    summary: HomeLedgerSummary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.xl)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .yingShiHapticClickable(shape = shape, pressedScale = 0.988f, onClick = onClick),
        shape = shape,
        color = colors.raisedSurface.copy(alpha = 0.86f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            colors.raisedSurface.copy(alpha = 0.96f),
                            colors.softGreenContainer.copy(alpha = 0.28f),
                            colors.memoryWash.copy(alpha = 0.16f),
                        ),
                    ),
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(
                    text = "账本信号",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
                Text(
                    text = summary.bookName ?: "默认账本",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            if (summary.hasTransaction) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    HomeLedgerMetric(
                        title = "最近一笔",
                        headline = buildString {
                            append(summary.latestTransactionLabel ?: "--")
                            val amount = summary.latestTransactionAmountText
                            if (!amount.isNullOrBlank()) {
                                append(" · ")
                                append(amount)
                            }
                        },
                        supporting = formatHomeTimeStamp(summary.latestTransactionAtMillis),
                        modifier = Modifier.weight(1f),
                    )
                    HomeLedgerMetric(
                        title = "本月支出",
                        headline = summary.monthExpenseText ?: "¥0.00",
                        supporting = "当前自然月",
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Text(
                    text = if (summary.hasBook) {
                        "还没有账本记录，点这里直接去记一笔。"
                    } else {
                        "账本还没有准备好，点这里进入后会自动补齐。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun HomeLedgerMetric(
    title: String,
    headline: String,
    supporting: String,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = colors.raisedSurface.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.56f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textSecondary,
            )
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun HomeNotificationBellButton(
    unreadCount: Int,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val iconColor = colors.titleAccent

    Surface(
        modifier = Modifier
            .size(46.dp)
            .yingShiHapticClickable(shape = CircleShape, pressedScale = 0.94f, onClick = onClick)
            .yingShiMemoryGlow(visible = unreadCount > 0, warm = true),
        shape = CircleShape,
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
        shadowElevation = 2.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .padding(8.dp),
            ) {
                val stroke = Stroke(width = 2.6f, cap = StrokeCap.Round)
                drawArc(
                    color = iconColor,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    style = stroke,
                )
                drawLine(
                    color = iconColor,
                    start = center.copy(x = size.width * 0.22f, y = size.height * 0.66f),
                    end = center.copy(x = size.width * 0.78f, y = size.height * 0.66f),
                    strokeWidth = 2.6f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = iconColor,
                    start = center.copy(x = size.width * 0.50f, y = size.height * 0.10f),
                    end = center.copy(x = size.width * 0.50f, y = size.height * 0.20f),
                    strokeWidth = 2.6f,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = iconColor,
                    radius = 2.2f,
                    center = center.copy(y = size.height * 0.82f),
                )
            }
            if (unreadCount > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 1.dp, end = 1.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = colors.memoryAccent,
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.raisedSurface,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    YingShiTheme {
        HomeScreen()
    }
}
