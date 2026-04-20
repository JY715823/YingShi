package com.example.yingshi.feature.photos

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
fun PhotoFeedScreen(modifier: Modifier = Modifier) {
    val spacing = YingShiThemeTokens.spacing
    val feedItems = remember { FakePhotoFeedRepository.getPhotoFeed() }
    var densityName by rememberSaveable { mutableStateOf(PhotoFeedDensity.COMFORT_3.name) }
    val density = PhotoFeedDensity.valueOf(densityName)
    val blocks = remember(feedItems, density) {
        buildPhotoFeedBlocks(
            items = feedItems,
            density = density,
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(sectionSpacing(density)),
        contentPadding = PaddingValues(
            top = spacing.xs,
            bottom = spacing.xxl,
        ),
    ) {
        item(key = "feed-summary") {
            PhotoFeedSummary(
                mediaCount = feedItems.size,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item(key = "density-switcher") {
            PhotoFeedDensitySwitcher(
                selectedDensity = density,
                onDensitySelected = { densityName = it.name },
            )
        }

        items(
            items = blocks,
            key = { it.key },
        ) { block ->
            when (block) {
                is PhotoFeedSectionHeader -> PhotoFeedSectionHeaderRow(title = block.title)
                is PhotoFeedDayHeader -> PhotoFeedDayHeaderRow(title = block.title)
                is PhotoFeedGridRow -> PhotoFeedGridRowContent(
                    row = block,
                    density = density,
                )
            }
        }
    }
}

@Composable
private fun PhotoFeedSummary(
    mediaCount: Int,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SummaryTag(text = "全局媒体流")
        SummaryTag(text = "最新优先")
        Text(
            text = "已去重 $mediaCount 项媒体",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryTag(text: String) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        shape = RoundedCornerShape(radius.capsule),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f),
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PhotoFeedDensitySwitcher(
    selectedDensity: PhotoFeedDensity,
    onDensitySelected: (PhotoFeedDensity) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        shape = RoundedCornerShape(radius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.sm, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            PhotoFeedDensity.entries.forEach { density ->
                val selected = density == selectedDensity
                val containerColor = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    Color.Transparent
                }

                TextButton(
                    onClick = { onDensitySelected(density) },
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(radius.capsule))
                            .background(containerColor)
                            .padding(horizontal = spacing.xs, vertical = spacing.xs),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = density.label,
                            style = if (selected) {
                                MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                            } else {
                                MaterialTheme.typography.labelMedium
                            },
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoFeedSectionHeaderRow(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun PhotoFeedDayHeaderRow(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PhotoFeedGridRowContent(
    row: PhotoFeedGridRow,
    density: PhotoFeedDensity,
) {
    val spacing = rowSpacing(density)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        row.items.forEach { item ->
            PhotoFeedCard(
                item = item,
                density = density,
                modifier = Modifier.weight(1f),
            )
        }

        repeat(density.columns - row.items.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoFeedCard(
    item: PhotoFeedItem,
    density: PhotoFeedDensity,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)
    val shape = RoundedCornerShape(cardRadius(density))
    val accentSize = when {
        density.columns <= 3 -> 28.dp
        density.columns <= 8 -> 16.dp
        else -> 10.dp
    }
    val ribbonHeight = when {
        density.columns <= 3 -> 24.dp
        density.columns <= 8 -> 12.dp
        else -> 6.dp
    }

    Box(
        modifier = modifier
            .aspectRatio(item.aspectRatio)
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(item.palette.start, item.palette.end),
                ),
            )
            .border(
                border = BorderStroke(1.dp, outlineColor),
                shape = shape,
            )
            .combinedClickable(
                onClick = {
                    Toast.makeText(context, "Viewer 将在后续阶段接入", Toast.LENGTH_SHORT).show()
                },
                onLongClick = {
                    Toast.makeText(context, "多选将在后续阶段接入", Toast.LENGTH_SHORT).show()
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.08f),
                        ),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(accentSize)
                .clip(CircleShape)
                .background(item.palette.accent.copy(alpha = 0.22f)),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .fillMaxWidth(0.58f)
                .height(ribbonHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(item.palette.accent.copy(alpha = 0.18f)),
        )
    }
}

private fun sectionSpacing(density: PhotoFeedDensity): Dp {
    return when {
        density.columns <= 4 -> 20.dp
        density.columns == 8 -> 16.dp
        else -> 12.dp
    }
}

private fun rowSpacing(density: PhotoFeedDensity): Dp {
    return when {
        density.columns <= 3 -> 8.dp
        density.columns <= 8 -> 4.dp
        else -> 2.dp
    }
}

private fun cardRadius(density: PhotoFeedDensity): Dp {
    return when {
        density.columns <= 3 -> 18.dp
        density.columns <= 8 -> 12.dp
        else -> 8.dp
    }
}

@Preview(showBackground = true)
@Composable
private fun PhotoFeedScreenPreview() {
    YingShiTheme {
        PhotoFeedScreen()
    }
}
