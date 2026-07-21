package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.yingshi.data.model.UpdateAlbumPayload
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
internal fun AlbumDirectoryDialog(
    albums: List<AlbumSummaryUiModel>,
    selectedAlbumId: String,
    onDismiss: () -> Unit,
    actionsEnabled: Boolean = true,
    isMutating: Boolean = false,
    onCreateLargeAlbum: (() -> Unit)? = null,
    onSelectAlbum: (String) -> Unit,
    onRenameSelectedAlbum: ((UpdateAlbumPayload) -> Unit)? = null,
    onDeleteSelectedAlbum: (() -> Unit)? = null,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    var query by rememberSaveable { mutableStateOf("") }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val filteredAlbums = remember(albums, query) {
        val keyword = query.trim()
        if (keyword.isBlank()) {
            albums
        } else {
            albums.filter { album ->
                    album.title.contains(keyword, ignoreCase = true) ||
                    album.subtitle.contains(keyword, ignoreCase = true) ||
                    album.description.contains(keyword, ignoreCase = true)
            }
        }
    }
    val selectedAlbum = remember(albums, selectedAlbumId) {
        albums.firstOrNull { album -> album.id == selectedAlbumId }
    }
    var renameDraft by rememberSaveable(selectedAlbumId, selectedAlbum?.title) {
        mutableStateOf(selectedAlbum?.title.orEmpty())
    }
    var descriptionDraft by rememberSaveable(selectedAlbumId, selectedAlbum?.description) {
        mutableStateOf(selectedAlbum?.description.orEmpty())
    }
    val canManageSelectedAlbum = actionsEnabled &&
        !isMutating &&
        selectedAlbum != null

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
            shape = RoundedCornerShape(radius.lg),
            color = colors.glassSurfaceBase.copy(alpha = 0.82f),
            border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.60f)),
            shadowElevation = 8.dp,
        ) {
            Box(
                modifier = Modifier.background(
                    Brush.linearGradient(
                        colors = listOf(
                            colors.titleAccent.copy(alpha = 0.04f),
                            colors.glowWash.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                    ),
                ),
            ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "选择大相册",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AlbumSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        modifier = Modifier.weight(1f),
                    )
                    if (onCreateLargeAlbum != null) {
                        AlbumIconAction(
                            icon = Icons.Rounded.Add,
                            contentDescription = "新建大相册",
                            containerColor = colors.softGreenContainer.copy(alpha = 0.92f),
                            contentColor = colors.softGreenAction,
                            onClick = onCreateLargeAlbum,
                        )
                    }
                }
                selectedAlbum?.let { album ->
                    AlbumDirectoryManagementCard(
                        album = album,
                        actionsEnabled = actionsEnabled,
                        isMutating = isMutating,
                        onRename = if (onRenameSelectedAlbum != null && canManageSelectedAlbum) {
                            {
                                renameDraft = album.title
                                descriptionDraft = album.description
                                showRenameDialog = true
                            }
                        } else {
                            null
                        },
                        onDelete = if (onDeleteSelectedAlbum != null && canManageSelectedAlbum) {
                            { showDeleteDialog = true }
                        } else {
                            null
                        },
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 290.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 2.dp),
                ) {
                    items(
                        items = filteredAlbums,
                        key = { it.id },
                    ) { album ->
                        AlbumDirectoryRow(
                            album = album,
                            selected = album.id == selectedAlbumId,
                            onClick = { onSelectAlbum(album.id) },
                        )
                    }
                }
                if (filteredAlbums.isEmpty()) {
                    Text(
                        text = "没有找到相册",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }

    if (showRenameDialog && selectedAlbum != null && onRenameSelectedAlbum != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名大相册") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "会修改当前大相册标题和简介，小相册和媒体内容不会变化。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    OutlinedTextField(
                        value = renameDraft,
                        onValueChange = { renameDraft = it },
                        singleLine = true,
                        enabled = !isMutating,
                        label = { Text("大相册标题") },
                    )
                    OutlinedTextField(
                        value = descriptionDraft,
                        onValueChange = { descriptionDraft = it },
                        enabled = !isMutating,
                        minLines = 2,
                        maxLines = 4,
                        label = { Text("大相册简介") },
                    )
                }
            },
            containerColor = colors.raisedSurface,
            titleContentColor = colors.titleAccent,
            textContentColor = colors.textSecondary,
            confirmButton = {
                AlbumDialogActionButton(
                    text = if (isMutating) "保存中…" else "保存",
                    enabled = renameDraft.trim().isNotEmpty() && !isMutating,
                    onClick = {
                        showRenameDialog = false
                        onRenameSelectedAlbum(
                            UpdateAlbumPayload(
                                title = renameDraft.trim(),
                                subtitle = descriptionDraft.trim(),
                            ),
                        )
                    },
                )
            },
            dismissButton = {
                AlbumDialogActionButton(
                    text = "取消",
                    enabled = !isMutating,
                    onClick = { showRenameDialog = false },
                )
            },
        )
    }

    if (showDeleteDialog && selectedAlbum != null && onDeleteSelectedAlbum != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除这个大相册？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "会把「${selectedAlbum.title}」和里面的小相册一起移入回收站。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "媒体本体不会删除，可在回收站整册整组恢复。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            },
            containerColor = colors.raisedSurface,
            titleContentColor = colors.titleAccent,
            textContentColor = colors.textSecondary,
            confirmButton = {
                AlbumDialogActionButton(
                    text = if (isMutating) "删除中…" else "删除",
                    enabled = !isMutating,
                    danger = true,
                    onClick = {
                        showDeleteDialog = false
                        onDeleteSelectedAlbum()
                    },
                )
            },
            dismissButton = {
                AlbumDialogActionButton(
                    text = "取消",
                    enabled = !isMutating,
                    onClick = { showDeleteDialog = false },
                )
            },
        )
    }
    }
}

@Composable
private fun AlbumDirectoryManagementCard(
    album: AlbumSummaryUiModel,
    actionsEnabled: Boolean,
    isMutating: Boolean,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    Surface(
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = colors.glassSurfaceBase,
        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.70f)),
        shadowElevation = 3.dp,
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.titleAccent.copy(alpha = 0.06f),
                        colors.glowWash.copy(alpha = 0.20f),
                        Color.Transparent,
                    ),
                ),
            ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = spacing.md, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
                Text(
                    text = when {
                        !actionsEnabled -> "当前在缓存只读模式，只能浏览已缓存目录。"
                        album.description.isNotBlank() -> album.description
                        else -> "这个大相册还没有补充说明。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AlbumInlineActionButton(
                        text = if (isMutating) "处理中…" else "重命名",
                        enabled = onRename != null,
                        onClick = { onRename?.invoke() },
                    )
                    AlbumInlineActionButton(
                        text = "删除",
                        enabled = onDelete != null,
                        danger = true,
                        onClick = { onDelete?.invoke() },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.capsule),
        color = colors.sectionBackground.copy(alpha = 0.64f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.merge(
                    TextStyle(color = colors.textPrimary),
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isBlank()) {
                            Text(
                                text = "搜索大相册",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

@Composable
private fun AlbumDirectoryRow(
    album: AlbumSummaryUiModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .yingShiClickable(shape = RoundedCornerShape(radius.md), onClick = onClick),
        shape = RoundedCornerShape(radius.md),
        color = if (selected) colors.primaryContainer.copy(alpha = 0.54f) else colors.raisedSurface,
        border = BorderStroke(
            1.dp,
            if (selected) colors.glassStroke.copy(alpha = 0.82f) else colors.dividerSoft.copy(alpha = 0.54f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(album.accent.start, album.accent.end))),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(RoundedCornerShape(topStart = 7.dp))
                        .background(colors.raisedSurface.copy(alpha = 0.80f)),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            )
            {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = album.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = colors.titleAccent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
internal fun AlbumInlineActionButton(
    text: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    Surface(
        modifier = Modifier.yingShiClickable(
            enabled = enabled,
            shape = shape,
            pressedScale = 0.96f,
            onClick = onClick,
        ),
        shape = shape,
        color = if (enabled) {
            if (danger) {
                colors.destructiveContainer
            } else {
                colors.softGreenContainer.copy(alpha = 0.92f)
            }
        } else {
            colors.sectionBackground.copy(alpha = 0.64f)
        },
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) {
                if (danger) {
                    colors.destructive
                } else {
                    colors.softGreenAction
                }
            } else {
                colors.textSecondary.copy(alpha = 0.68f)
            },
        )
    }
}

@Composable
private fun AlbumDialogActionButton(
    text: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    AlbumInlineActionButton(
        text = text,
        enabled = enabled,
        danger = danger,
        onClick = onClick,
    )
}

@Composable
internal fun MoveSmallAlbumTargetPickerDialog(
    smallAlbumTitle: String,
    albums: List<AlbumSummaryUiModel>,
    currentAlbumId: String,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onSelectTarget: (String) -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val radius = YingShiThemeTokens.radius
    val targetAlbums = remember(albums, currentAlbumId) {
        albums.filterNot { it.id == currentAlbumId }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
            shape = RoundedCornerShape(radius.lg),
            color = colors.glowWash.copy(alpha = 0.98f),
            border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.74f)),
            shadowElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "将「$smallAlbumTitle」移动到",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
                if (targetAlbums.isEmpty()) {
                    Text(
                        text = "没有其他可选的大相册，请先新建一个大相册。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 2.dp),
                    ) {
                        items(
                            items = targetAlbums,
                            key = { it.id },
                        ) { album ->
                            AlbumDirectoryRow(
                                album = album,
                                selected = false,
                                onClick = {
                                    if (!isMutating) onSelectTarget(album.id)
                                },
                            )
                        }
                    }
                }
                AlbumInlineActionButton(
                    text = if (isMutating) "移动中…" else "取消",
                    enabled = !isMutating,
                    onClick = onDismiss,
                )
            }
        }
    }
}
