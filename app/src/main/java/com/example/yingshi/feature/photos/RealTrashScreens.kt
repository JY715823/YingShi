package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshi.data.model.RemoteTrashDetail
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun RealTrashPageScreen(
    modifier: Modifier = Modifier,
    selectedTypeName: String = TrashEntryType.MEDIA_SYSTEM_DELETED.name,
    onSelectedTypeNameChange: (String) -> Unit = { },
    showPendingCleanup: Boolean = false,
    onShowPendingCleanupChange: (Boolean) -> Unit = { },
    onOpenTrashDetail: (TrashDetailRoute) -> Unit = { },
    onRestoreTargetMediaIds: (List<String>) -> Unit = { },
) {
    val sessionKey = realBackendSessionKey("real-trash-list")
    val viewModel: RealTrashListViewModel = viewModel(
        key = sessionKey,
        factory = RealTrashListViewModel.factory(),
    )
    val uiState by viewModel.uiState.collectAsState()
    val backendMutationEvent by RealBackendMutationBus.latestEvent.collectAsState()
    val selectedType = TrashEntryType.valueOf(selectedTypeName)
    val spacing = YingShiThemeTokens.spacing
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTypeName) {
        viewModel.refresh(selectedType)
    }
    LaunchedEffect(backendMutationEvent.version) {
        if (backendMutationEvent.version > 0 && backendMutationEvent.affectsTrash()) {
            viewModel.refresh(selectedType)
        }
    }

    if (selectedType.isRealMediaTrashType()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                RealTrashCategoryActionRow(
                    selectedType = selectedType,
                    entryCount = uiState.entries.size,
                    menuExpanded = showCategoryMenu,
                    isMutating = uiState.isMutating,
                    onMenuExpandedChange = { showCategoryMenu = it },
                    onTypeSelected = { onSelectedTypeNameChange(it.name) },
                    onRestoreCurrent = {
                        viewModel.restoreEntries(
                            entries = uiState.entries,
                            selectedType = selectedType,
                            onFirstRestoredMediaIds = onRestoreTargetMediaIds,
                        )
                    },
                    onRequestClearCurrent = { showClearConfirm = true },
                )
            }
            uiState.statusMessage?.let { message ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RealTrashSectionCard(title = "操作结果", body = message, emphasized = true)
                }
            }
            uiState.errorMessage?.let { message ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RealTrashSectionCard(title = "请求失败", body = message)
                }
            }
            when {
                uiState.isLoading && uiState.entries.isEmpty() -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        RealTrashSectionCard(title = "读取中", body = "正在从后端读取回收站列表…")
                    }
                }
                uiState.entries.isEmpty() -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        RealTrashSectionCard(title = "当前分类为空", body = "这一类回收站项目还没有内容。")
                    }
                }
                else -> {
                    realTrashMonthGroups(uiState.entries).forEach { group ->
                        item(
                            key = "real-trash-month-${group.key}",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            RealTrashGridMonthHeader(title = group.title)
                        }
                        gridItems(
                            items = group.entries,
                            key = { it.id },
                        ) { entry ->
                            RealTrashMediaGridCell(
                                entry = entry,
                                showPostTitle = selectedType == TrashEntryType.MEDIA_REMOVED,
                                onClick = {
                                    onOpenTrashDetail(TrashDetailRoute(entryId = entry.id))
                                },
                            )
                        }
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item {
                RealTrashCategoryActionRow(
                    selectedType = selectedType,
                    entryCount = uiState.entries.size,
                    menuExpanded = showCategoryMenu,
                    isMutating = uiState.isMutating,
                    onMenuExpandedChange = { showCategoryMenu = it },
                    onTypeSelected = { onSelectedTypeNameChange(it.name) },
                    onRestoreCurrent = {
                        viewModel.restoreEntries(
                            entries = uiState.entries,
                            selectedType = selectedType,
                            onFirstRestoredMediaIds = onRestoreTargetMediaIds,
                        )
                    },
                    onRequestClearCurrent = { showClearConfirm = true },
                )
            }

            if (uiState.statusMessage != null) {
                item {
                    RealTrashSectionCard(
                        title = "操作结果",
                        body = uiState.statusMessage ?: "",
                        emphasized = true,
                    )
                }
            }

            if (uiState.errorMessage != null) {
                item {
                    RealTrashSectionCard(
                        title = "请求失败",
                        body = uiState.errorMessage ?: "",
                    )
                }
            }

            when {
                uiState.isLoading && uiState.entries.isEmpty() -> {
                    item {
                        RealTrashSectionCard(
                            title = "读取中",
                            body = "正在从后端读取回收站列表…",
                        )
                    }
                }
                uiState.entries.isEmpty() -> {
                    item {
                        RealTrashSectionCard(
                            title = "当前分类为空",
                            body = "这一类回收站项目还没有内容，可以先在 REAL 照片流里删除一项媒体试试。",
                        )
                    }
                }
                else -> {
                    items(
                        items = uiState.entries,
                        key = { it.id },
                    ) { entry ->
                        RealTrashEntryRow(
                            entry = entry,
                            onClick = {
                                onOpenTrashDetail(
                                    TrashDetailRoute(entryId = entry.id),
                                )
                            },
                            trailing = {
                                Text(
                                    text = "查看",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空当前分类？") },
            text = {
                Text(
                    "将永久删除当前「${selectedType.label}」分类中的 ${uiState.entries.size} 项。媒体删除类会删除对应 Server local-storage 文件；帖子删除、媒体移除不会误删仍被其他地方引用的媒体文件。",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = uiState.entries.isNotEmpty() && !uiState.isMutating,
                    onClick = {
                        showClearConfirm = false
                        viewModel.purgeEntries(
                            entries = uiState.entries,
                            selectedType = selectedType,
                        )
                    },
                ) {
                    Text("清空当前分类")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun RealTrashEntryRow(
    entry: TrashEntryUiModel,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val spacing = YingShiThemeTokens.spacing
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier.padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RealTrashEntryPreview(
                entry = entry,
                modifier = Modifier
                    .weight(0.26f)
                    .aspectRatio(1f),
            )
            Column(
                modifier = Modifier.weight(0.74f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = entry.type.label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = entry.previewInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = realTrashEntrySourceLine(entry),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun RealTrashCategoryActionRow(
    selectedType: TrashEntryType,
    entryCount: Int,
    menuExpanded: Boolean,
    isMutating: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onTypeSelected: (TrashEntryType) -> Unit,
    onRestoreCurrent: () -> Unit,
    onRequestClearCurrent: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            RealTrashIconActionButton(
                text = "☰",
                enabled = !isMutating,
                onClick = { onMenuExpandedChange(true) },
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { onMenuExpandedChange(false) },
            ) {
                TrashCategoryMenuTypes.forEach { type ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (type == selectedType) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        androidx.compose.foundation.shape.RoundedCornerShape(
                                            YingShiThemeTokens.radius.md,
                                        ),
                                    )
                                    .padding(horizontal = spacing.xs, vertical = spacing.xxs),
                                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = type.label,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (type == selectedType) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Medium
                                        },
                                    ),
                                    color = if (type == selectedType) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                if (type == selectedType) {
                                    Text(
                                        text = "✓",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        },
                        onClick = {
                            onMenuExpandedChange(false)
                            onTypeSelected(type)
                        },
                    )
                }
            }
        }
        Box(modifier = Modifier.weight(1f))
        TextButton(
            enabled = entryCount > 0 && !isMutating,
            onClick = onRestoreCurrent,
        ) {
            Text(if (isMutating) "…" else "↩")
        }
        RealTrashIconActionButton(
            text = "🗑",
            enabled = entryCount > 0 && !isMutating,
            onClick = onRequestClearCurrent,
        )
    }
}

@Composable
private fun RealTrashIconActionButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (enabled) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f)
            },
        )
    }
}

@Composable
private fun RealTrashEntryPreview(
    entry: TrashEntryUiModel,
    modifier: Modifier = Modifier,
) {
    val mediaId = entry.previewMediaIds().firstOrNull()
    if (mediaId == null) {
        Surface(
            modifier = modifier,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.lg),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = entry.type.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    AppContentMediaThumbnail(
        mediaSource = realTrashMediaSource(mediaId),
        mediaType = AppMediaType.IMAGE,
        palette = realPaletteFor(mediaId),
        modifier = modifier,
        requestSize = 256,
        showLoadingIndicator = true,
        showStatusBadge = true,
    )
}

@Composable
private fun RealTrashGridMonthHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun RealTrashMediaGridCell(
    entry: TrashEntryUiModel,
    showPostTitle: Boolean,
    onClick: () -> Unit,
) {
    val media = entry.mediaSnapshot
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(4.dp)),
        ) {
            RealTrashEntryPreview(
                entry = entry,
                modifier = Modifier.matchParentSize(),
            )
            RealTrashDaysBadge(
                days = realTrashDaysSince(entry.deletedAtMillis),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 5.dp, end = 5.dp),
            )
            if (media?.mediaType == AppMediaType.VIDEO) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 5.dp, bottom = 5.dp),
                    shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                    color = Color.Black.copy(alpha = 0.38f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VideoGlyph(
                            state = VideoGlyphState.PLAY,
                            tint = Color.White.copy(alpha = 0.94f),
                            modifier = Modifier.size(9.dp),
                        )
                        Text(
                            text = formatVideoDurationLabel(media.videoDurationMillis) ?: "视频",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White.copy(alpha = 0.94f),
                        )
                    }
                }
            }
        }
        if (showPostTitle) {
            Text(
                text = realTrashGridPostTitle(entry),
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RealTrashDaysBadge(
    days: Long,
    modifier: Modifier = Modifier,
) {
    val danger = days > 25
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (danger) Color(0xFFE5484D).copy(alpha = 0.88f) else Color.Black.copy(alpha = 0.36f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    ) {
        Text(
            text = "${days.coerceAtLeast(0)}天",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
    }
}

private fun TrashEntryUiModel.previewMediaIds(): List<String> {
    return buildList {
        sourceMediaId?.takeIf { it.isNotBlank() }?.let(::add)
        addAll(relatedMediaIds.filter { it.isNotBlank() })
    }.distinct()
}

private fun realTrashEntrySourceLine(entry: TrashEntryUiModel): String {
    return when (entry.type) {
        TrashEntryType.POST_DELETED -> {
            val mediaCount = entry.relatedMediaIds.size
            "帖子删除 · 媒体 $mediaCount 项"
        }
        TrashEntryType.MEDIA_REMOVED -> {
            val source = entry.sourcePostId ?: entry.relatedPostIds.firstOrNull() ?: "当前帖子"
            "从帖子移除 · 来源 $source"
        }
        TrashEntryType.MEDIA_SYSTEM_DELETED -> {
            val postCount = entry.relatedPostIds.size
            "媒体删除 · 影响帖子 $postCount 个"
        }
    }
}

@Composable
fun RealTrashDetailScreen(
    route: TrashDetailRoute,
    onBack: () -> Unit,
    onEntryRemoved: () -> Unit,
    onEntryRestored: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessionKey = realBackendSessionKey("real-trash-detail-${route.entryId}")
    val viewModel: RealTrashDetailViewModel = viewModel(
        key = sessionKey,
        factory = RealTrashDetailViewModel.factory(route),
    )
    val uiState by viewModel.uiState.collectAsState()
    val backendMutationEvent by RealBackendMutationBus.latestEvent.collectAsState()
    val detail = uiState.detail
    val spacing = YingShiThemeTokens.spacing

    LaunchedEffect(backendMutationEvent.version) {
        if (backendMutationEvent.version > 0 && backendMutationEvent.affectsTrash()) {
            viewModel.refresh()
        }
    }

    val mediaDetailEntry = detail?.item?.toTrashEntryUiModel()
    if (mediaDetailEntry?.type?.isRealMediaTrashType() == true) {
        RealTrashMediaViewerDetailContent(
            detail = detail,
            statusMessage = uiState.statusMessage,
            errorMessage = uiState.errorMessage,
            isMutating = uiState.isMutating,
            onBack = onBack,
            onRestore = {
                viewModel.restore { restoredItem ->
                    val mediaIds = restoredItem.toTrashEntryUiModel().restoreTargetMediaIds()
                    onEntryRestored(mediaIds)
                }
            },
            onRemove = { viewModel.remove(onEntryRemoved) },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("返回")
            }
            Text(
                text = "回收站详情",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        when {
            uiState.tokenMissing -> {
                RealTrashSectionCard(
                    title = "REAL 模式需要登录",
                    body = uiState.errorMessage ?: "请先到联调诊断页完成登录。",
                )
            }
            uiState.isLoading && detail == null -> {
                RealTrashSectionCard(
                    title = "读取中",
                    body = "正在从后端读取回收站详情…",
                )
            }
            uiState.errorMessage != null && detail == null -> {
                RealTrashSectionCard(
                    title = "读取失败",
                    body = uiState.errorMessage ?: "暂时无法读取回收站详情。",
                )
            }
            detail == null -> {
                RealTrashSectionCard(
                    title = "详情不可用",
                    body = "这个回收站项目可能已经被恢复或移出。",
                )
            }
            else -> {
                RealTrashDetailContent(
                    detail = detail,
                    statusMessage = uiState.statusMessage,
                    errorMessage = uiState.errorMessage,
                    isMutating = uiState.isMutating,
                    onRestore = {
                        viewModel.restore { restoredItem ->
                            val mediaIds = restoredItem.toTrashEntryUiModel().restoreTargetMediaIds()
                            onEntryRestored(mediaIds)
                        }
                    },
                    onRemove = { viewModel.remove(onEntryRemoved) },
                    onUndoRemove = viewModel::undoRemove,
                )
            }
        }
    }
}

@Composable
private fun RealTrashMediaViewerDetailContent(
    detail: RemoteTrashDetail,
    statusMessage: String?,
    errorMessage: String?,
    isMutating: Boolean,
    onBack: () -> Unit,
    onRestore: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val item = detail.item
    val entry = item.toTrashEntryUiModel()
    val media = entry.mediaSnapshot
    var showPermanentDeleteConfirm by remember(item.trashItemId) {
        mutableStateOf(false)
    }
    val target = media?.let {
        RealOriginalMediaTarget(
            mediaId = it.mediaId,
            mediaType = it.mediaType,
            mediaSource = it.mediaSource,
        )
    }
    val originalLoadState = target?.let(RealOriginalLoadRepository::getState)
        ?: OriginalLoadState.NotLoaded
    val accessToken = AuthSessionManager.getAccessToken()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07111F)),
    ) {
        if (media == null) {
            RealTrashSectionCard(
                title = "原媒体预览不可用",
                body = "当前删除项没有返回 sourceMediaId 或 relatedMediaIds，无法定位原媒体文件。",
            )
        } else {
            AppContentMediaThumbnail(
                mediaSource = media.mediaSource,
                mediaType = media.mediaType,
                palette = media.palette,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .aspectRatio(media.aspectRatio.coerceIn(0.45f, 2.2f)),
                contentDescription = media.mediaId,
                contentScale = ContentScale.Fit,
                requestSize = 1080,
                showLoadingIndicator = true,
                showStatusBadge = true,
                showVideoPlayOverlay = media.mediaType == AppMediaType.VIDEO,
                originalLoadState = originalLoadState,
                onOriginalLoadStateChange = { state ->
                    target?.let { RealOriginalLoadRepository.setState(it, state) }
                },
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = YingShiThemeTokens.spacing.lg, vertical = YingShiThemeTokens.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RealTrashViewerOverlayButton(text = "<", onClick = onBack)
            Box(modifier = Modifier.weight(1f))
            if (detail.canRestore) {
                RealTrashViewerOverlayButton(
                    text = if (isMutating) "…" else "↩",
                    onClick = onRestore,
                )
            }
            if (detail.canMoveOutOfTrash) {
                RealTrashViewerOverlayButton(
                    text = if (isMutating) "…" else "🗑",
                    destructive = true,
                    onClick = { showPermanentDeleteConfirm = true },
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(YingShiThemeTokens.spacing.lg),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
        ) {
            if (target != null) {
                RealTrashViewerOverlayButton(
                    text = originalLoadState.actionLabel(),
                    onClick = {
                        if (originalLoadState != OriginalLoadState.Loaded &&
                            originalLoadState != OriginalLoadState.Loading
                        ) {
                            RealOriginalLoadRepository.requestOriginal(context, target, accessToken)
                        }
                    },
                )
            }
            if (entry.type == TrashEntryType.MEDIA_REMOVED) {
                Surface(
                    shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                    color = Color.Black.copy(alpha = 0.38f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                ) {
                    Text(
                        text = realTrashGridPostTitle(entry),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }
            }
            statusMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.86f),
                )
            }
            errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFB4AB),
                )
            }
        }
    }

    if (showPermanentDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showPermanentDeleteConfirm = false },
            title = { Text("永久删除该回收站项目？") },
            text = {
                Text(
                    "确认后会删除回收站记录。媒体删除项还会删除 Server local-storage 中该媒体明确归属的原文件、preview-v2 和 cover 文件，无法恢复。",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isMutating,
                    onClick = {
                        showPermanentDeleteConfirm = false
                        onRemove()
                    },
                ) {
                    Text("永久删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermanentDeleteConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun RealTrashViewerOverlayButton(
    text: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(YingShiThemeTokens.radius.capsule))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (destructive) {
            Color(0xFFE5484D).copy(alpha = 0.88f)
        } else {
            Color.Black.copy(alpha = 0.38f)
        },
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White.copy(alpha = 0.94f),
        )
    }
}

@Composable
private fun RealTrashDetailContent(
    detail: RemoteTrashDetail,
    statusMessage: String?,
    errorMessage: String?,
    isMutating: Boolean,
    onRestore: () -> Unit,
    onRemove: () -> Unit,
    onUndoRemove: () -> Unit,
) {
    val item = detail.item
    val spacing = YingShiThemeTokens.spacing
    var showPermanentDeleteConfirm by remember(item.trashItemId) {
        mutableStateOf(false)
    }

    if (statusMessage != null) {
        RealTrashSectionCard(
            title = "操作结果",
            body = statusMessage,
            emphasized = true,
        )
    }
    if (errorMessage != null) {
        RealTrashSectionCard(
            title = "操作失败",
            body = errorMessage,
        )
    }

    RealTrashSectionCard(
        title = item.title.ifBlank { "回收站项目" },
        body = item.previewInfo.ifBlank { "后端没有返回额外说明。" },
    )

    RealTrashDeletedPreview(item = item)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = "类型：${item.toTrashEntryUiModel().type.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "状态：${item.state ?: "inTrash"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.sourcePostId != null) {
                Text(
                    text = "来源帖子：${item.sourcePostId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.sourceMediaId != null) {
                Text(
                    text = "来源媒体：${item.sourceMediaId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.relatedPostIds.isNotEmpty()) {
                Text(
                    text = "关联帖子：${item.relatedPostIds.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.relatedMediaIds.isNotEmpty()) {
                Text(
                    text = "关联媒体：${item.relatedMediaIds.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                if (detail.canRestore) {
                    TextButton(
                        enabled = !isMutating,
                        onClick = onRestore,
                    ) {
                        Text(if (isMutating) "处理中…" else "恢复")
                    }
                }
                if (detail.canMoveOutOfTrash) {
                    TextButton(
                        enabled = !isMutating,
                        onClick = { showPermanentDeleteConfirm = true },
                    ) {
                        Text(if (isMutating) "处理中…" else "永久删除")
                    }
                }
                if (detail.pendingCleanup != null) {
                    // Pending cleanup is no longer exposed in the client UI.
                }
            }
        }
    }

    if (showPermanentDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showPermanentDeleteConfirm = false },
            title = { Text("永久删除该回收站项目？") },
            text = {
                Text(
                    "确认后会删除回收站记录。媒体删除项还会删除 Server local-storage 中该媒体明确归属的原文件、preview-v2 和 cover 文件，无法恢复。",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isMutating,
                    onClick = {
                        showPermanentDeleteConfirm = false
                        onRemove()
                    },
                ) {
                    Text("永久删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermanentDeleteConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun RealTrashDeletedPreview(
    item: com.example.yingshi.data.model.RemoteTrashItem,
) {
    val mediaIds = buildList {
        item.sourceMediaId?.takeIf { it.isNotBlank() }?.let(::add)
        addAll(item.relatedMediaIds.filter { it.isNotBlank() })
    }.distinct()
    val type = item.toTrashEntryUiModel().type

    when {
        mediaIds.isNotEmpty() -> {
            RealTrashMediaStrip(
                title = when (type) {
                    TrashEntryType.POST_DELETED -> "原帖子媒体"
                    TrashEntryType.MEDIA_REMOVED -> "被移除的媒体"
                    TrashEntryType.MEDIA_SYSTEM_DELETED -> "被删除的媒体"
                },
                mediaIds = mediaIds,
            )
        }

        type == TrashEntryType.POST_DELETED -> {
            RealTrashSectionCard(
                title = "原帖子内容",
                body = "当前后端删除项没有返回媒体快照，只能展示帖子标题和说明；后续可扩展更完整的帖子快照契约。",
            )
        }

        else -> {
            RealTrashSectionCard(
                title = "原媒体预览不可用",
                body = "当前删除项没有返回 sourceMediaId 或 relatedMediaIds，无法定位原媒体文件。",
            )
        }
    }
}

@Composable
private fun RealTrashMediaStrip(
    title: String,
    mediaIds: List<String>,
) {
    val spacing = YingShiThemeTokens.spacing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            mediaIds.chunked(3).forEach { rowIds ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    rowIds.forEach { mediaId ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                        ) {
                            AppContentMediaThumbnail(
                                mediaSource = realTrashMediaSource(mediaId),
                                mediaType = AppMediaType.IMAGE,
                                palette = realPaletteFor(mediaId),
                                modifier = Modifier.fillMaxSize(),
                                contentDescription = mediaId,
                                requestSize = 384,
                                showLoadingIndicator = true,
                                showStatusBadge = true,
                            )
                        }
                    }
                    repeat(3 - rowIds.size) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                        )
                    }
                }
            }
        }
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

private fun TrashEntryType.isRealMediaTrashType(): Boolean {
    return this == TrashEntryType.MEDIA_SYSTEM_DELETED || this == TrashEntryType.MEDIA_REMOVED
}

private data class RealTrashMonthGroup(
    val key: String,
    val title: String,
    val entries: List<TrashEntryUiModel>,
)

private fun realTrashMonthGroups(entries: List<TrashEntryUiModel>): List<RealTrashMonthGroup> {
    val formatter = SimpleDateFormat("yyyy年M月", Locale.CHINA)
    val keyFormatter = SimpleDateFormat("yyyy-MM", Locale.CHINA)
    return entries
        .sortedByDescending { it.deletedAtMillis }
        .groupBy { keyFormatter.format(Date(it.deletedAtMillis)) }
        .map { (key, groupEntries) ->
            RealTrashMonthGroup(
                key = key,
                title = formatter.format(Date(groupEntries.first().deletedAtMillis)),
                entries = groupEntries,
            )
        }
}

private fun realTrashDaysSince(timeMillis: Long): Long {
    val now = System.currentTimeMillis()
    if (timeMillis <= 0L || now <= timeMillis) return 0L
    return TimeUnit.MILLISECONDS.toDays(now - timeMillis)
}

private fun realTrashGridPostTitle(entry: TrashEntryUiModel): String {
    return entry.mediaSnapshot?.sourcePostTitle
        ?: entry.title
        ?: entry.sourcePostId
        ?: "来源帖子"
}

@Composable
private fun RealTrashSectionCard(
    title: String,
    body: String,
    emphasized: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(YingShiThemeTokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (emphasized) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
