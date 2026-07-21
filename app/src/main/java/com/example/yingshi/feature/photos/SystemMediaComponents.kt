package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
internal fun SystemMediaActionChip(
    text: String,
    emphasized: Boolean,
    danger: Boolean = false,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier.yingShiClickable(
            shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
            pressedScale = 0.96f,
            onClick = onClick,
        ),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (danger) {
            colors.destructiveContainer.copy(alpha = 0.90f)
        } else if (emphasized) {
            colors.primaryContainer.copy(alpha = 0.88f)
        } else {
            colors.sectionBackground.copy(alpha = 0.72f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (danger) {
                colors.destructive.copy(alpha = 0.24f)
            } else if (emphasized) {
                colors.glassStroke.copy(alpha = 0.72f)
            } else {
                colors.dividerSoft.copy(alpha = 0.62f)
            },
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .then(if (compact) Modifier else Modifier.fillMaxWidth())
                .padding(horizontal = if (compact) 12.dp else 14.dp, vertical = if (compact) 7.dp else 10.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
            color = if (danger) {
                MaterialTheme.colorScheme.onErrorContainer
            } else if (emphasized) {
                colors.titleAccent
            } else {
                colors.titleAccent
            },
        )
    }
}

@Composable
internal fun SystemMediaIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier
            .size(44.dp)
            .yingShiClickable(shape = CircleShape, pressedScale = 0.94f, onClick = onClick),
        shape = CircleShape,
        color = colors.sectionBackground.copy(alpha = 0.80f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colors.titleAccent,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

@Composable
internal fun SystemMediaTopBar(
    selectedFilter: SystemMediaFilter,
    selectionMode: Boolean,
    selectedCount: Int,
    totalCount: Int = 0,
    selectedAlbum: SystemMediaAlbum? = null,
    onBack: () -> Unit,
    onFilterSelected: (SystemMediaFilter) -> Unit,
    onRefresh: () -> Unit,
    onOpenAlbums: () -> Unit = {},
    isManualRefreshing: Boolean = false,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    var filterMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        YingShiToolSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
            contentPadding = PaddingValues(horizontal = spacing.sm, vertical = spacing.sm),
            highlighted = selectionMode,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SystemMediaIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack,
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "系统媒体",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.titleAccent,
                        )
                        Text(
                            text = if (selectionMode) {
                                "长按与滑动选择媒体"
                            } else {
                                val albumPart = selectedAlbum?.displayName?.let { " · $it" } ?: ""
                                "共 $totalCount 项$albumPart"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = colors.textSecondary,
                        )
                    }

                    if (isManualRefreshing) {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape,
                            color = colors.sectionBackground.copy(alpha = 0.80f),
                            border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(21.dp),
                                    strokeWidth = 2.dp,
                                    color = colors.primaryAction,
                                )
                            }
                        }
                    } else {
                        SystemMediaIconButton(
                            icon = Icons.Default.Refresh,
                            contentDescription = "刷新媒体",
                            onClick = onRefresh,
                        )
                    }
                    SystemMediaIconButton(
                        icon = Icons.Filled.PhotoLibrary,
                        contentDescription = "系统相册",
                        onClick = onOpenAlbums,
                    )
                    Box {
                        SystemMediaIconButton(
                            icon = Icons.Default.Menu,
                            contentDescription = "媒体分类",
                            onClick = { filterMenuExpanded = true },
                        )
                        DropdownMenu(
                            expanded = filterMenuExpanded,
                            onDismissRequest = { filterMenuExpanded = false },
                        ) {
                            SystemMediaFilter.entries.forEach { filter ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = filter.label,
                                            fontWeight = if (filter == selectedFilter) FontWeight.SemiBold else FontWeight.Medium,
                                        )
                                    },
                                    onClick = {
                                        onFilterSelected(filter)
                                        filterMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selectionMode) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
                color = colors.primaryContainer.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.70f)),
            ) {
                Text(
                    text = if (selectedCount > 0) "已选 $selectedCount 项" else "请选择媒体",
                    modifier = Modifier.padding(horizontal = spacing.md, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
internal fun SystemMediaAtmosphereLayer(
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.primaryContainer.copy(alpha = 0.18f),
                            colors.appBackground.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                        center = Offset(80f, 120f),
                        radius = 920f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.memoryAccent.copy(alpha = 0.14f),
                            colors.sectionBackground.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                        center = Offset(1120f, 1880f),
                        radius = 980f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.raisedSurface.copy(alpha = 0.06f),
                            Color.Transparent,
                            colors.viewerBackground.copy(alpha = 0.08f),
                        ),
                    ),
                ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SystemMediaAlbumSheet(
    albums: List<SystemMediaAlbum>,
    selectedAlbum: SystemMediaAlbum?,
    onDismiss: () -> Unit,
    onAlbumSelected: (SystemMediaAlbum?) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.raisedSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = "系统相册",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                item(key = "album-all") {
                    SystemMediaAlbumItem(
                        displayName = "全部",
                        mediaCount = albums.sumOf { it.mediaCount },
                        coverUri = null,
                        isSelected = selectedAlbum == null,
                        onClick = { onAlbumSelected(null) },
                    )
                }
                items(
                    items = albums,
                    key = { it.bucketName ?: "ungrouped" },
                ) { album ->
                    SystemMediaAlbumItem(
                        displayName = album.displayName,
                        mediaCount = album.mediaCount,
                        coverUri = album.coverUri,
                        isSelected = selectedAlbum?.bucketName == album.bucketName,
                        onClick = { onAlbumSelected(album) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemMediaAlbumItem(
    displayName: String,
    mediaCount: Int,
    coverUri: android.net.Uri?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val colors = YingShiThemeTokens.colors
    val coverThumbnail = coverUri?.let { uri ->
        rememberSystemMediaThumbnail(
            context = context,
            uri = uri,
            targetSizePx = 256,
        )
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(YingShiThemeTokens.radius.lg))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = colors.sectionBackground.copy(alpha = 0.50f),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) {
                colors.primaryContainer.copy(alpha = 0.78f)
            } else {
                colors.dividerSoft.copy(alpha = 0.62f)
            },
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (coverThumbnail != null) {
                Image(
                    bitmap = coverThumbnail.toComposeBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    colors.primaryContainer.copy(alpha = 0.22f),
                                    colors.viewerBackground.copy(alpha = 0.18f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        tint = colors.titleAccent.copy(alpha = 0.50f),
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                colors.viewerBackground.copy(alpha = 0.72f),
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.viewerText,
                    maxLines = 1,
                )
                Text(
                    text = "$mediaCount 项",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.viewerTextSecondary,
                )
            }
        }
    }
}
