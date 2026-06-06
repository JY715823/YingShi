package com.example.yingshi.feature.photos

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GearEditScreen(
    route: GearEditRoute,
    onBack: () -> Unit,
    onPostUpdated: (postId: String, albumId: String?) -> Unit = { _, _ -> },
    onDeleteCurrentPost: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
        RealGearEditScreen(
            route = route,
            onBack = onBack,
            onPostUpdated = onPostUpdated,
            onDeleteCurrentPost = onDeleteCurrentPost,
            modifier = modifier,
        )
        return
    }

    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val context = LocalContext.current
    val post = remember(route.postId) { FakeAlbumRepository.getPost(route.postId) }
    val initialDraft = remember(route.postId) {
        FakeAlbumRepository.getEditablePostDraft(route.postId)
    }
    val initialMediaItems = remember(route.postId) {
        FakeAlbumRepository.getManagedPostMedia(route.postId).orEmpty()
    }
    val initialCoverId = remember(route.postId, initialMediaItems) {
        initialMediaItems.firstOrNull { it.isCover }?.id ?: initialMediaItems.firstOrNull()?.id
    }
    val systemDeleteImpact = remember(route.postId) {
        FakeAlbumRepository.getPostSystemDeleteImpact(route.postId)
    }

    if (initialDraft == null || post == null) {
        GearEditMissingState(
            onBack = onBack,
            modifier = modifier,
        )
        return
    }

    var title by remember(route.postId) { mutableStateOf(initialDraft.title) }
    var summary by remember(route.postId) { mutableStateOf(initialDraft.summary) }
    var displayTimeMillis by remember(route.postId) { mutableLongStateOf(initialDraft.postDisplayTimeMillis) }
    val selectedAlbumIds = remember(route.postId) {
        mutableStateListOf<String>().apply {
            addAll(initialDraft.albumIds)
        }
    }
    var mediaItems by remember(route.postId) {
        mutableStateOf(initialMediaItems.map(ManagedPostMediaUiModel::toPostMediaListItem))
    }
    var coverMediaId by remember(route.postId) { mutableStateOf(initialCoverId) }
    var showPostMediaList by remember(route.postId) { mutableStateOf(false) }
    var localMessage by rememberSaveable(route.postId) { mutableStateOf<String?>(null) }
    var isSaving by rememberSaveable(route.postId) { mutableStateOf(false) }
    val albums = remember { FakeAlbumRepository.getAlbums() }
    var showDeletePostDialog by rememberSaveable(route.postId) { mutableStateOf(false) }
    var showAlbumDirectory by rememberSaveable(route.postId) { mutableStateOf(false) }
    val safeCoverMediaId = coverMediaId?.takeIf { id -> mediaItems.any { it.id == id } }
        ?: mediaItems.firstOrNull()?.id
    val hasChanges = title != initialDraft.title ||
        summary != initialDraft.summary ||
        displayTimeMillis != initialDraft.postDisplayTimeMillis ||
        selectedAlbumIds.toList() != initialDraft.albumIds ||
        mediaItems.map { it.id } != initialMediaItems.map { it.id } ||
        safeCoverMediaId != initialCoverId

    val handleClose = {
        if (hasChanges) {
            Toast.makeText(context, "未保存修改已放弃", Toast.LENGTH_SHORT).show()
        }
        onBack()
    }

    BackHandler(onBack = handleClose)

    if (showPostMediaList) {
        PostMediaListScreen(
            initialItems = mediaItems,
            initialCoverMediaId = safeCoverMediaId,
            allowEmpty = false,
            onCancel = { showPostMediaList = false },
            onConfirm = { updatedItems, updatedCoverId ->
                mediaItems = updatedItems
                coverMediaId = updatedCoverId?.takeIf { id -> updatedItems.any { it.id == id } }
                    ?: updatedItems.firstOrNull()?.id
                localMessage = null
                showPostMediaList = false
            },
            modifier = modifier,
        )
        return
    }

    fun saveDraft() {
        localMessage = null
        if (selectedAlbumIds.isEmpty()) {
            localMessage = "请至少选择一个相册后再保存。"
            return
        }
        if (mediaItems.isNotEmpty() && safeCoverMediaId == null) {
            localMessage = "封面媒体已失效，请重新选择封面。"
            return
        }
        isSaving = true
        FakeAlbumRepository.updatePostBasicInfo(
            postId = route.postId,
            title = title.trim(),
            summary = summary.trim(),
            postDisplayTimeMillis = displayTimeMillis,
            albumIds = selectedAlbumIds.toList(),
        )
        val finalIds = mediaItems.map { it.id }
        val originalIds = initialMediaItems.map { it.id }
        val removedIds = (originalIds - finalIds.toSet()).toSet()
        if (removedIds.isNotEmpty()) {
            val selectedMediaSnapshots = FakeAlbumRepository.snapshotPostMedia(
                postId = route.postId,
                mediaIds = removedIds,
            )
            FakeAlbumRepository.applyMediaDelete(
                postId = route.postId,
                mediaIds = removedIds,
                semantic = FakeAlbumRepository.MediaDeleteSemantic.DIRECTORY_ONLY,
            )
            FakeTrashRepository.recordRemovedMedia(post, selectedMediaSnapshots)
        }
        if (finalIds.isNotEmpty()) {
            if (!FakeAlbumRepository.updatePostMediaOrder(route.postId, finalIds, touchUpdatedTime = false)) {
                isSaving = false
                localMessage = "保存媒体顺序失败，请重试。"
                return
            }
            safeCoverMediaId?.let { coverId ->
                if (!FakeAlbumRepository.setPostCover(route.postId, coverId, touchUpdatedTime = false)) {
                    isSaving = false
                    localMessage = "设置封面失败，请重新选择封面后重试。"
                    return
                }
            }
        }
        isSaving = false
        onPostUpdated(route.postId, selectedAlbumIds.firstOrNull())
        Toast.makeText(context, "小相册已保存", Toast.LENGTH_SHORT).show()
        onBack()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        GearEditTopBar(
            onCancel = handleClose,
            onSave = ::saveDraft,
            saveEnabled = !isSaving,
        )

        localMessage?.let { message ->
            BackendInlineNotice(text = message, emphasized = true)
        }

        GearEditTextSection(
            title = title,
            summary = summary,
            enabled = !isSaving,
            onTitleChange = { title = it },
            onSummaryChange = { summary = it },
        )

        GearEditSection(
            title = "选择所属大相册",
        ) {
            if (selectedAlbumIds.isEmpty()) {
                Text(
                    text = "请选择一个大相册",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            AlbumSelectionFlow(
                albums = albums,
                selectedAlbumIds = selectedAlbumIds,
                onToggleAlbum = { albumId ->
                    if (selectedAlbumIds.firstOrNull() != albumId) {
                        selectedAlbumIds.clear()
                        selectedAlbumIds.add(albumId)
                    }
                },
                trailing = {
                    AlbumIconAction(
                        icon = Icons.Rounded.Menu,
                        contentDescription = "更多大相册",
                        containerColor = colors.sectionBackground.copy(alpha = 0.88f),
                        contentColor = colors.titleAccent,
                        onClick = { showAlbumDirectory = true },
                    )
                },
            )
        }
        GearEditActionButton(
            text = if (isSaving) "保存中…" else "保存",
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving,
            emphasized = true,
            onClick = ::saveDraft,
        )
        Spacer(modifier = Modifier.weight(1f, fill = false))
        GearEditActionButton(
            text = "删除整个小相册",
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving,
            danger = true,
            onClick = { showDeletePostDialog = true },
        )
    }

    if (showAlbumDirectory) {
        AlbumDirectoryDialog(
            albums = albums,
            selectedAlbumId = selectedAlbumIds.firstOrNull().orEmpty(),
            onDismiss = { showAlbumDirectory = false },
            onSelectAlbum = { albumId ->
                showAlbumDirectory = false
                if (selectedAlbumIds.firstOrNull() != albumId) {
                    selectedAlbumIds.clear()
                    selectedAlbumIds.add(albumId)
                }
            },
        )
    }

    if (showDeletePostDialog) {
        AlertDialog(
            onDismissRequest = { showDeletePostDialog = false },
            containerColor = colors.raisedSurface,
            titleContentColor = colors.titleAccent,
            textContentColor = colors.textSecondary,
            title = {
                Text(
                    text = "删除整个小相册",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
                ) {
                    Text("选择只删除小相册，或在删除小相册时把其中媒体一起记入“媒体系统删”。")
                    if (systemDeleteImpact.sharedMediaCount > 0) {
                        Text(
                            text = "其中有 ${systemDeleteImpact.sharedMediaCount} 张媒体同时属于其他小相册，会带来 ${systemDeleteImpact.affectedOtherPostCount} 个其他小相册的全局影响。",
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (systemDeleteImpact.mediaCount > 0) {
                        Text("当前小相册共有 ${systemDeleteImpact.mediaCount} 张媒体会进入本地系统删流程。")
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs)) {
                    TrashDialogActionButton(
                        text = "仅删小相册",
                        onClick = {
                            showDeletePostDialog = false
                            onDeleteCurrentPost(route.postId, false)
                        },
                    )
                    TrashDialogActionButton(
                        text = "同时系统删媒体",
                        danger = true,
                        onClick = {
                            showDeletePostDialog = false
                            onDeleteCurrentPost(route.postId, true)
                        },
                    )
                }
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { showDeletePostDialog = false })
            },
        )
    }
}

@Composable
private fun RealGearEditScreen(
    route: GearEditRoute,
    onBack: () -> Unit,
    onPostUpdated: (postId: String, albumId: String?) -> Unit,
    onDeleteCurrentPost: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val context = LocalContext.current
    val sessionKey = realBackendSessionKey("real-gear-edit-${route.postId}")
    val viewModel: RealGearEditViewModel = viewModel(
        key = sessionKey,
        factory = RealGearEditViewModel.factory(route),
    )
    val uiState by viewModel.uiState.collectAsState()
    var showDeletePostDialog by rememberSaveable(route.postId) { mutableStateOf(false) }
    var showPostMediaList by remember(route.postId) { mutableStateOf(false) }
    var showAlbumDirectory by rememberSaveable(route.postId) { mutableStateOf(false) }
    val mediaItems = uiState.mediaItems.map(ManagedPostMediaUiModel::toPostMediaListItem)
    val safeCoverMediaId = uiState.coverMediaId?.takeIf { id -> mediaItems.any { it.id == id } }
        ?: mediaItems.firstOrNull()?.id

    val handleClose = {
        if (uiState.hasChanges) {
            Toast.makeText(context, "未保存修改已丢弃", Toast.LENGTH_SHORT).show()
        }
        onBack()
    }

    BackHandler(onBack = handleClose)

    if (showPostMediaList) {
        PostMediaListScreen(
            initialItems = mediaItems,
            initialCoverMediaId = safeCoverMediaId,
            allowEmpty = false,
            onCancel = { showPostMediaList = false },
            onConfirm = { updatedItems, updatedCoverId ->
                viewModel.updateMediaDraft(updatedItems, updatedCoverId)
                showPostMediaList = false
            },
            modifier = modifier,
        )
        return
    }

    if (uiState.isLoading && !uiState.draftLoaded) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(YingShiThemeTokens.colors.appBackground)
                .statusBarsPadding()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            GearEditTopBar(
                onCancel = handleClose,
                onSave = {},
                saveEnabled = false,
            )
            Text(
                text = "正在读取小相册编辑信息…",
                style = MaterialTheme.typography.bodyLarge,
                color = YingShiThemeTokens.colors.textSecondary,
            )
        }
        return
    }

    if (uiState.tokenMissing || (!uiState.draftLoaded && uiState.errorMessage != null)) {
        GearEditMissingState(
            onBack = onBack,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(YingShiThemeTokens.colors.appBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        GearEditTopBar(
            onCancel = handleClose,
            onSave = {
                viewModel.save(
                    onSuccess = {
                        onPostUpdated(route.postId, uiState.selectedAlbumIds.firstOrNull())
                        onBack()
                    },
                )
            },
            saveEnabled = !uiState.isSaving,
        )

        uiState.errorMessage?.let { errorMessage ->
            BackendInlineNotice(text = errorMessage, emphasized = true)
        }

        GearEditTextSection(
            title = uiState.title,
            summary = uiState.summary,
            enabled = !uiState.isSaving,
            onTitleChange = viewModel::updateTitle,
            onSummaryChange = viewModel::updateSummary,
        )

        GearEditSection(
            title = "选择所属大相册",
        ) {
            if (uiState.selectedAlbumIds.isEmpty()) {
                Text(
                    text = "请选择一个大相册",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            AlbumSelectionFlow(
                albums = uiState.albums,
                selectedAlbumIds = uiState.selectedAlbumIds,
                onToggleAlbum = viewModel::toggleAlbum,
                trailing = {
                    AlbumIconAction(
                        icon = Icons.Rounded.Menu,
                        contentDescription = "更多大相册",
                        containerColor = YingShiThemeTokens.colors.sectionBackground.copy(alpha = 0.88f),
                        contentColor = YingShiThemeTokens.colors.titleAccent,
                        onClick = { showAlbumDirectory = true },
                    )
                },
            )
        }
        GearEditActionButton(
            text = if (uiState.isSaving) "保存中…" else "保存",
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isSaving,
            emphasized = true,
            onClick = {
                viewModel.save(
                    onSuccess = {
                        onPostUpdated(route.postId, uiState.selectedAlbumIds.firstOrNull())
                        onBack()
                    },
                )
            },
        )
        Spacer(modifier = Modifier.weight(1f, fill = false))
        GearEditActionButton(
            text = "删除整个小相册",
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isSaving && !uiState.isDeleting,
            danger = true,
            onClick = { showDeletePostDialog = true },
        )
    }

    if (showAlbumDirectory) {
        AlbumDirectoryDialog(
            albums = uiState.albums,
            selectedAlbumId = uiState.selectedAlbumIds.firstOrNull().orEmpty(),
            onDismiss = { showAlbumDirectory = false },
            onSelectAlbum = { albumId ->
                showAlbumDirectory = false
                if (uiState.selectedAlbumIds.firstOrNull() != albumId) {
                    viewModel.toggleAlbum(albumId)
                }
            },
        )
    }

    if (showDeletePostDialog) {
        val colors = YingShiThemeTokens.colors
        AlertDialog(
            onDismissRequest = { showDeletePostDialog = false },
            containerColor = colors.raisedSurface,
            titleContentColor = colors.titleAccent,
            textContentColor = colors.textSecondary,
            title = {
                Text(
                    text = "删除整个小相册",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            },
            text = {
                Text("确认后会把当前小相册移入回收站，小相册详情、大相册页和回收站会同步刷新。")
            },
            confirmButton = {
                TrashDialogActionButton(
                    text = if (uiState.isDeleting) "处理中…" else "确认删除",
                    onClick = {
                        showDeletePostDialog = false
                        viewModel.deletePost(
                            onSuccess = {
                                onDeleteCurrentPost(route.postId, false)
                            },
                        )
                    },
                    enabled = !uiState.isDeleting,
                    danger = true,
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { showDeletePostDialog = false })
            },
        )
    }
}

@Composable
private fun GearEditTopBar(
    onCancel: () -> Unit,
    onSave: () -> Unit = {},
    saveEnabled: Boolean = true,
) {
    val colors = YingShiThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GearEditActionButton(text = "取消", onClick = onCancel)
        Text(
            text = "编辑小相册",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.titleAccent,
        )
        Spacer(modifier = Modifier.width(72.dp))
    }
}

@Composable
private fun GearEditMemoryHeader(
    mediaCount: Int,
    albumTitles: List<String>,
    coverLabel: String,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = colors.softGreenContainer.copy(alpha = 0.66f),
        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = "编辑这条记忆",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GearEditInfoChip(text = "媒体 $mediaCount 项")
                GearEditInfoChip(text = "封面：$coverLabel")
            }
            GearEditInfoChip(
                text = if (albumTitles.isEmpty()) {
                    "未选择相册"
                } else {
                    "相册：${albumTitles.take(2).joinToString("、")}${if (albumTitles.size > 2) "等" else ""}"
                },
            )
        }
    }
}

@Composable
private fun GearEditTextSection(
    title: String,
    summary: String,
    enabled: Boolean,
    onTitleChange: (String) -> Unit,
    onSummaryChange: (String) -> Unit,
) {
    GearEditSection(
        title = "编辑标题和简介",
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = enabled,
            label = { Text("标题") },
            placeholder = { Text("输入标题") },
        )
        Spacer(modifier = Modifier.size(4.dp))
        OutlinedTextField(
            value = summary,
            onValueChange = onSummaryChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            enabled = enabled,
            label = { Text("简介 / 摘要") },
            placeholder = { Text("输入简介") },
        )
    }
}

@Composable
private fun GearEditMediaPreviewSection(
    items: List<PostMediaListItem>,
    coverMediaId: String?,
    onOpenAll: () -> Unit,
) {
    GearEditSection(
        title = "媒体",
        subtitle = if (items.isEmpty()) {
            "当前小相册没有媒体。"
        } else {
            null
        },
    ) {
        val colors = YingShiThemeTokens.colors
        if (items.isEmpty()) {
            BackendInlineNotice(text = "当前没有可管理的媒体。")
            return@GearEditSection
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "已选 ${items.size} 项 · ${gearEditCoverLabel(items, coverMediaId)}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            GearEditActionButton(text = "全部", onClick = onOpenAll)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.take(4).chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rowItems.forEach { item ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(YingShiThemeTokens.radius.lg))
                                .background(colors.sectionBackground.copy(alpha = 0.62f)),
                        ) {
                            PostMediaListThumbnail(
                                item = item,
                                modifier = Modifier.fillMaxSize(),
                                requestSize = 640,
                            )
                            if (item.id == coverMediaId) {
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp),
                                    shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                                    color = colors.primaryAction.copy(alpha = 0.88f),
                                ) {
                                    Text(
                                        text = "封面",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun GearEditPublishSummary(
    mediaCount: Int,
    coverLabel: String,
    albumTitles: List<String>,
    displayTimeMillis: Long,
) {
    GearEditSection(title = "保存信息") {
        val colors = YingShiThemeTokens.colors
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
            color = colors.raisedSurface.copy(alpha = 0.90f),
            border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
        ) {
            Column(
                modifier = Modifier.padding(YingShiThemeTokens.spacing.md),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GearEditSummaryRow(label = "媒体", value = if (mediaCount > 0) "$mediaCount 项" else "无媒体")
                GearEditSummaryRow(label = "封面", value = coverLabel)
                GearEditSummaryRow(
                    label = "相册",
                    value = albumTitles.ifEmpty { listOf("未选择") }.joinToString("、"),
                )
                GearEditSummaryRow(label = "时间", value = formatGearEditTime(displayTimeMillis))
            }
        }
    }
}

@Composable
private fun GearEditSaveRow(
    isSaving: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GearEditActionButton(
            text = "取消",
            onClick = onCancel,
            enabled = !isSaving,
        )
        GearEditActionButton(
            text = if (isSaving) "保存中…" else "保存小相册",
            onClick = onSave,
            modifier = Modifier.weight(1f),
            enabled = !isSaving,
            emphasized = true,
        )
    }
}

@Composable
private fun GearEditInfoChip(text: String) {
    val colors = YingShiThemeTokens.colors
    Surface(
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = colors.raisedSurface.copy(alpha = 0.74f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.68f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun GearEditSummaryRow(
    label: String,
    value: String,
) {
    val colors = YingShiThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.titleAccent,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

private fun gearEditCoverLabel(
    items: List<PostMediaListItem>,
    coverMediaId: String?,
): String {
    if (items.isEmpty()) return "无媒体"
    val index = items.indexOfFirst { it.id == coverMediaId }
    return if (index >= 0) {
        "第 ${index + 1} 项"
    } else {
        "未设置，保存时使用第 1 项"
    }
}

@Composable
private fun GearEditSection(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlbumSelectionFlow(
    albums: List<AlbumSummaryUiModel>,
    selectedAlbumIds: List<String>,
    onToggleAlbum: (String) -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
        ) {
            albums.forEach { album ->
                SelectableGearEditChip(
                    text = album.title,
                    selected = selectedAlbumIds.contains(album.id),
                    onClick = { onToggleAlbum(album.id) },
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun GearEditChip(
    text: String,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = colors.primaryContainer.copy(alpha = 0.68f),
        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.70f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = YingShiThemeTokens.spacing.sm,
                vertical = YingShiThemeTokens.spacing.xs,
            ),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.titleAccent,
        )
    }
}

@Composable
private fun SelectableGearEditChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (selected) {
            colors.primaryContainer.copy(alpha = 0.72f)
        } else {
            colors.sectionBackground.copy(alpha = 0.72f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                colors.glassStroke.copy(alpha = 0.82f)
            } else {
                colors.dividerSoft.copy(alpha = 0.68f)
            },
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = YingShiThemeTokens.spacing.sm,
                vertical = YingShiThemeTokens.spacing.xs,
            ),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (selected) {
                colors.titleAccent
            } else {
                colors.textSecondary
            },
        )
    }
}

@Composable
private fun GearEditEntryRow(
    title: String,
    subtitle: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    if (title.contains("缓存") || title.contains("缂撳瓨")) {
        return
    }
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = if (danger) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.06f)
        } else {
            colors.sectionBackground.copy(alpha = 0.62f)
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (danger) MaterialTheme.colorScheme.error else colors.titleAccent,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun GearEditActionButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        color = when {
            !enabled -> colors.sectionBackground.copy(alpha = 0.46f)
            danger -> MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
            emphasized -> colors.primaryContainer.copy(alpha = 0.88f)
            else -> colors.sectionBackground.copy(alpha = 0.72f)
        },
        border = BorderStroke(
            1.dp,
            when {
                danger -> MaterialTheme.colorScheme.error.copy(alpha = 0.28f)
                emphasized -> colors.glassStroke.copy(alpha = 0.84f)
                else -> colors.dividerSoft.copy(alpha = 0.68f)
            },
        ),
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = when {
                    !enabled -> colors.textSecondary.copy(alpha = 0.58f)
                    danger -> MaterialTheme.colorScheme.error
                    emphasized -> colors.titleAccent
                    else -> colors.textSecondary
                },
            )
        }
    }
}

@Composable
private fun GearEditMissingState(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .statusBarsPadding()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        GearEditTopBar(onCancel = onBack, onSave = onBack)
        Text(
            text = "当前小相册不存在，无法编辑。",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
        )
    }
}

private fun formatGearEditTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}

@Preview(showBackground = true)
@Composable
private fun GearEditScreenPreview() {
    YingShiTheme {
        GearEditScreen(
            route = GearEditRoute(postId = "post-window-light"),
            onBack = {},
            onDeleteCurrentPost = { _, _ -> },
        )
    }
}
