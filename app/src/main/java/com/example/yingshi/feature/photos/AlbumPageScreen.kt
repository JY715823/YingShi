package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlbumPageScreen(
    albums: List<AlbumSummaryUiModel>,
    posts: List<AlbumPostCardUiModel>,
    onOpenPost: (PostDetailPlaceholderRoute) -> Unit,
    onManageAlbums: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    var selectedAlbumId by rememberSaveable(albums) {
        mutableStateOf(albums.firstOrNull()?.id.orEmpty())
    }
    var densityName by rememberSaveable {
        mutableStateOf(AlbumGridDensity.COZY_2.name)
    }
    val gridDensity = AlbumGridDensity.valueOf(densityName)
    val gridState = rememberLazyGridState()
    val selectedAlbum = remember(albums, selectedAlbumId) {
        albums.firstOrNull { it.id == selectedAlbumId } ?: albums.firstOrNull()
    }
    val filteredPosts = posts.filter { it.albumIds.contains(selectedAlbumId) }
    val chipRows = remember(albums) { buildAlbumChipRows(albums) }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = "相册目录",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = selectedAlbum?.subtitle ?: "按相册整理帖子记录，入口更柔和，也更像纪念册目录。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AlbumManageButton(onClick = onManageAlbums)
        }

        AlbumSwitchSection(
            rows = chipRows,
            selectedAlbumId = selectedAlbumId,
            onSelectAlbum = { selectedAlbumId = it },
        )

        AlbumGridDensitySwitcher(
            selectedDensity = gridDensity,
            onDensitySelected = { densityName = it.name },
        )

        if (filteredPosts.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
                ),
            ) {
                Text(
                    text = "这个相册里暂时还没有帖子占位。",
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xl),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .discreteZoomLevelGesture(
                        enabled = true,
                        levels = AlbumGridDensity.entries.toList(),
                        currentLevel = gridDensity,
                        onLevelChange = { densityName = it.name },
                    ),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridDensity.columns),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(cardSpacing(gridDensity)),
                    verticalArrangement = Arrangement.spacedBy(cardSpacing(gridDensity)),
                    contentPadding = PaddingValues(bottom = spacing.lg),
                ) {
                    items(filteredPosts, key = { it.id }) { post ->
                        AlbumPostCard(
                            post = post,
                            density = gridDensity,
                            onClick = { onOpenPost(FakeAlbumRepository.toPostDetailRoute(post)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumSwitchSection(
    rows: List<List<AlbumSummaryUiModel>>,
    selectedAlbumId: String,
    onSelectAlbum: (String) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing

    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        rows.forEach { rowAlbums ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rowAlbums.forEach { album ->
                    AlbumSwitchChip(
                        album = album,
                        selected = album.id == selectedAlbumId,
                        onClick = { onSelectAlbum(album.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumSwitchChip(
    album: AlbumSummaryUiModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)

    Surface(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = if (selected) {
            album.accent.start.copy(alpha = 0.24f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                album.accent.accent.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
            },
        ),
    ) {
        Text(
            text = album.title,
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun AlbumManageButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text = "管理")
    }
}

@Composable
private fun AlbumGridDensitySwitcher(
    selectedDensity: AlbumGridDensity,
    onDensitySelected: (AlbumGridDensity) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        shape = RoundedCornerShape(radius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.sm, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            AlbumGridDensity.entries.forEach { density ->
                val selected = density == selectedDensity
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(radius.capsule))
                        .clickable { onDensitySelected(density) },
                    shape = RoundedCornerShape(radius.capsule),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    } else {
                        Color.Transparent
                    },
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
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
private fun AlbumPostCard(
    post: AlbumPostCardUiModel,
    density: AlbumGridDensity,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val innerPadding = when (density) {
        AlbumGridDensity.COZY_2 -> spacing.md
        AlbumGridDensity.COZY_3 -> spacing.sm
        AlbumGridDensity.COZY_4 -> spacing.xs
    }
    val summaryMaxLines = if (density == AlbumGridDensity.COZY_2) 2 else 1
    val titleStyle = if (density == AlbumGridDensity.COZY_4) {
        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    } else {
        MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    }
    val coverAspectRatio = when (density) {
        AlbumGridDensity.COZY_2 -> post.coverAspectRatio.coerceIn(0.92f, 1.08f)
        AlbumGridDensity.COZY_3 -> post.coverAspectRatio.coerceIn(0.98f, 1.10f)
        AlbumGridDensity.COZY_4 -> post.coverAspectRatio.coerceIn(1.02f, 1.14f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.lg))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(radius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(coverAspectRatio)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(post.coverPalette.start, post.coverPalette.end),
                        ),
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = innerPadding, end = innerPadding)
                        .size(if (density == AlbumGridDensity.COZY_4) 32.dp else 52.dp)
                        .clip(CircleShape)
                        .background(post.coverPalette.accent.copy(alpha = 0.14f)),
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = innerPadding, bottom = innerPadding)
                        .fillMaxWidth(if (density == AlbumGridDensity.COZY_4) 0.42f else 0.48f)
                        .height(if (density == AlbumGridDensity.COZY_4) 18.dp else 26.dp)
                        .clip(RoundedCornerShape(radius.capsule))
                        .background(Color.White.copy(alpha = 0.11f)),
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = spacing.xs, bottom = spacing.xs),
                    shape = RoundedCornerShape(radius.capsule),
                    color = Color.Black.copy(alpha = 0.20f),
                ) {
                    Text(
                        text = "${post.mediaCount} 张",
                        modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = innerPadding, vertical = innerPadding),
                verticalArrangement = Arrangement.spacedBy(if (density == AlbumGridDensity.COZY_4) spacing.xxs else spacing.xs),
            ) {
                Text(
                    text = post.title,
                    style = titleStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = post.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = summaryMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatAlbumPostTime(post.postDisplayTimeMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun PostDetailPlaceholderScreen(
    route: PostDetailPlaceholderRoute,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "<",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Text(
                text = "帖子详情",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(radius.xl),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
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
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = route.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DetailMetaCapsule(text = formatAlbumPostTime(route.postDisplayTimeMillis))
                    DetailMetaCapsule(text = "${route.mediaCount} 张媒体")
                }

                Surface(
                    shape = RoundedCornerShape(radius.lg),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        Text(
                            text = "Stage 4.1 先打通到这里",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "后续帖子详情页会在这里接入媒体序列、帖子信息区和帖子评论区。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailMetaCapsule(text: String) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        shape = RoundedCornerShape(radius.capsule),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun cardSpacing(density: AlbumGridDensity) = when (density) {
    AlbumGridDensity.COZY_2 -> 14.dp
    AlbumGridDensity.COZY_3 -> 10.dp
    AlbumGridDensity.COZY_4 -> 8.dp
}

private fun buildAlbumChipRows(albums: List<AlbumSummaryUiModel>): List<List<AlbumSummaryUiModel>> {
    if (albums.isEmpty()) return emptyList()
    if (albums.size == 1) return listOf(albums)

    val firstRow = mutableListOf<AlbumSummaryUiModel>()
    val secondRow = mutableListOf<AlbumSummaryUiModel>()
    albums.forEachIndexed { index, album ->
        if (index % 2 == 0) {
            firstRow += album
        } else {
            secondRow += album
        }
    }
    return listOf(firstRow, secondRow).filter { it.isNotEmpty() }
}

private fun formatAlbumPostTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(timeMillis))
}

@Preview(showBackground = true)
@Composable
private fun AlbumPageScreenPreview() {
    YingShiTheme {
        AlbumPageScreen(
            albums = FakeAlbumRepository.getAlbums(),
            posts = FakeAlbumRepository.getPosts(),
            onOpenPost = { },
            onManageAlbums = { },
            modifier = Modifier.padding(16.dp),
        )
    }
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
