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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshi.data.model.RemoteTrashDetail
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.ui.theme.YingShiThemeTokens

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
            Text(if (isMutating) "处理中…" else "恢复当前分类")
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

private fun realTrashMediaSource(mediaId: String): AppContentMediaSource {
    val baseUrl = BackendDebugConfig.currentBaseUrl().trimEnd('/')
    val previewUrl = "$baseUrl/api/media/files/$mediaId?variant=preview"
    val originalUrl = "$baseUrl/api/media/files/$mediaId"
    return AppContentMediaSource(
        thumbnailUrl = previewUrl,
        mediaUrl = originalUrl,
        originalUrl = originalUrl,
    )
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
