package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Menu
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yingshi.data.model.UpdateAlbumPayload
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
internal fun AlbumIconAction(
    text: String? = null,
    icon: ImageVector? = null,
    contentDescription: String,
    containerColor: Color? = null,
    contentColor: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val resolvedContentColor = contentColor ?: colors.titleAccent
    Surface(
        modifier = Modifier
            .size(46.dp)
            .yingShiClickable(
                enabled = enabled,
                shape = CircleShape,
                pressedScale = 0.94f,
                onClick = onClick,
            ),
        shape = CircleShape,
        color = if (enabled) {
            containerColor ?: colors.sectionBackground.copy(alpha = 0.82f)
        } else {
            colors.sectionBackground.copy(alpha = 0.58f)
        },
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.56f)),
        shadowElevation = if (enabled) 1.dp else 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = if (enabled) resolvedContentColor else colors.textSecondary.copy(alpha = 0.62f),
                    modifier = Modifier.size(25.dp),
                )
            } else {
                Text(
                    text = text.orEmpty(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (enabled) resolvedContentColor else colors.textSecondary.copy(alpha = 0.62f),
                )
            }
        }
    }
}

@Composable
internal fun AlbumSwitchSection(
    albums: List<AlbumSummaryUiModel>,
    selectedAlbumId: String,
    onSelectAlbum: (String) -> Unit,
    actionsEnabled: Boolean = true,
    isMutating: Boolean = false,
    onCreateLargeAlbum: () -> Unit,
    onCreateSmallAlbum: () -> Unit,
    onRenameAlbum: ((AlbumSummaryUiModel, UpdateAlbumPayload) -> Unit)? = null,
    onDeleteAlbum: ((AlbumSummaryUiModel) -> Unit)? = null,
) {
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    var showAlbumMenu by rememberSaveable { mutableStateOf(false) }
    val selectedAlbum = remember(albums, selectedAlbumId) {
        albums.firstOrNull { album -> album.id == selectedAlbumId }
    }
    val visibleAlbums = remember(albums, selectedAlbumId) {
        preferredVisibleAlbums(albums, selectedAlbumId)
    }

    YingShiToolSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.lg),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                visibleAlbums.forEach { album ->
                    AlbumSwitchChip(
                        album = album,
                        selected = album.id == selectedAlbumId,
                        modifier = Modifier
                            .widthIn(min = 92.dp, max = 124.dp),
                        onClick = { onSelectAlbum(album.id) },
                    )
                }
            }
            AlbumIconAction(
                icon = Icons.Rounded.Add,
                contentDescription = "新建小相册",
                containerColor = colors.softGreenContainer.copy(alpha = 0.92f),
                contentColor = colors.softGreenAction,
                enabled = actionsEnabled,
                onClick = onCreateSmallAlbum,
            )
            AlbumIconAction(
                icon = Icons.Rounded.Menu,
                contentDescription = "全部大相册",
                containerColor = colors.primaryContainer.copy(alpha = 0.74f),
                contentColor = colors.titleAccent,
                onClick = { showAlbumMenu = true },
            )
        }
    }

    if (showAlbumMenu) {
        AlbumDirectoryDialog(
            albums = albums,
            selectedAlbumId = selectedAlbumId,
            onDismiss = { showAlbumMenu = false },
            actionsEnabled = actionsEnabled,
            isMutating = isMutating,
            onCreateLargeAlbum = if (actionsEnabled) {
                {
                    showAlbumMenu = false
                    onCreateLargeAlbum()
                }
            } else {
                null
            },
            onSelectAlbum = { albumId ->
                showAlbumMenu = false
                onSelectAlbum(albumId)
            },
            onRenameSelectedAlbum = if (selectedAlbum != null && onRenameAlbum != null) {
                { payload ->
                    showAlbumMenu = false
                    onRenameAlbum(selectedAlbum, payload)
                }
            } else {
                null
            },
            onDeleteSelectedAlbum = if (selectedAlbum != null && onDeleteAlbum != null) {
                {
                    showAlbumMenu = false
                    onDeleteAlbum(selectedAlbum)
                }
            } else {
                null
            },
        )
    }
}

@Composable
internal fun AlbumSwitchChip(
    album: AlbumSummaryUiModel,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.md)
    val title = remember(album.title) {
        formatAlbumChipTitle(album.title)
    }

    Surface(
        modifier = modifier
            .yingShiClickable(shape = shape, onClick = onClick),
        shape = shape,
        color = if (selected) {
            colors.glassSurfaceBase
        } else {
            colors.raisedSurface.copy(alpha = 0.96f)
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) colors.glassStroke.copy(alpha = 0.86f) else colors.dividerSoft.copy(alpha = 0.50f),
        ),
        shadowElevation = if (selected) 2.dp else 0.dp,
    ) {
        Box {
            if (selected) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.titleAuraWarmGlow.copy(alpha = 0.32f),
                                colors.titleAuraCoolGlow.copy(alpha = 0.18f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.5f),
                            radius = size.minDimension * 0.85f,
                        ),
                        radius = size.minDimension * 0.85f,
                        center = Offset(size.width * 0.5f, size.height * 0.5f),
                    )
                }
            }
            Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 18.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Brush.linearGradient(listOf(album.accent.start, album.accent.end))),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = if (selected) colors.titleAccent else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

private fun formatAlbumChipTitle(rawTitle: String): String {
    val normalizedTitle = rawTitle.trim().ifBlank { "未命名相册" }
    return if (normalizedTitle.length > 4) {
        normalizedTitle.take(4) + "..."
    } else {
        normalizedTitle
    }
}

private fun preferredVisibleAlbums(
    albums: List<AlbumSummaryUiModel>,
    selectedAlbumId: String,
): List<AlbumSummaryUiModel> {
    if (albums.size <= 3) return albums
    val firstThree = albums.take(3)
    val selectedAlbum = albums.firstOrNull { it.id == selectedAlbumId }
    if (selectedAlbum == null || firstThree.any { it.id == selectedAlbumId }) {
        return firstThree
    }
    return listOf(selectedAlbum) + firstThree.take(2)
}
