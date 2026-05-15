package com.example.yingshi.feature.photos

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
            modifier = modifier,
        )
        return
    }

    val spacing = YingShiThemeTokens.spacing
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
    val safeCoverMediaId = coverMediaId?.takeIf { id -> mediaItems.any { it.id == id } }
        ?: mediaItems.firstOrNull()?.id
    val selectedAlbumTitles = albums
        .filter { selectedAlbumIds.contains(it.id) }
        .map { it.title }
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
            if (!FakeAlbumRepository.updatePostMediaOrder(route.postId, finalIds)) {
                isSaving = false
                localMessage = "保存媒体顺序失败，请重试。"
                return
            }
            safeCoverMediaId?.let { coverId ->
                if (!FakeAlbumRepository.setPostCover(route.postId, coverId)) {
                    isSaving = false
                    localMessage = "设置封面失败，请重新选择封面后重试。"
                    return
                }
            }
        }
        isSaving = false
        onPostUpdated(route.postId, selectedAlbumIds.firstOrNull())
        Toast.makeText(context, "帖子已保存", Toast.LENGTH_SHORT).show()
        onBack()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        GearEditTopBar(
            onCancel = handleClose,
            onSave = ::saveDraft,
            saveEnabled = !isSaving,
        )

        GearEditMemoryHeader(
            mediaCount = mediaItems.size,
            albumTitles = selectedAlbumTitles,
            coverLabel = gearEditCoverLabel(mediaItems, safeCoverMediaId),
        )

        localMessage?.let { message ->
            BackendInlineNotice(text = message, emphasized = true)
        }

        GearEditMediaPreviewSection(
            items = mediaItems,
            coverMediaId = safeCoverMediaId,
            onOpenAll = { showPostMediaList = true },
        )

        GearEditTextSection(
            title = title,
            summary = summary,
            enabled = !isSaving,
            onTitleChange = { title = it },
            onSummaryChange = { summary = it },
        )

        GearEditSection(
            title = "时间设置",
            subtitle = "沿用新建帖子流程的发布时间检查，保存后同步到详情。",
        ) {
            Text(
                text = formatGearEditTime(displayTimeMillis),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                GearEditChip(text = "-1小时") { displayTimeMillis -= 60 * 60 * 1000L }
                GearEditChip(text = "+1小时") { displayTimeMillis += 60 * 60 * 1000L }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                GearEditChip(text = "-1天") { displayTimeMillis -= 24 * 60 * 60 * 1000L }
                GearEditChip(text = "+1天") { displayTimeMillis += 24 * 60 * 60 * 1000L }
                GearEditChip(text = "设为现在") { displayTimeMillis = System.currentTimeMillis() }
            }
        }

        GearEditSection(
            title = "放进相册",
            subtitle = if (selectedAlbumIds.isEmpty()) {
                "至少选择一个相册后才能保存。"
            } else {
                "已选择 ${selectedAlbumIds.size} 个相册。"
            },
        ) {
            if (selectedAlbumIds.isEmpty()) {
                Text(
                    text = "请至少选择一个相册后再保存。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            AlbumSelectionFlow(
                albums = albums,
                selectedAlbumIds = selectedAlbumIds,
                onToggleAlbum = { albumId ->
                    if (selectedAlbumIds.contains(albumId)) {
                        selectedAlbumIds.remove(albumId)
                    } else {
                        selectedAlbumIds.add(albumId)
                    }
                },
            )
        }

        GearEditPublishSummary(
            mediaCount = mediaItems.size,
            coverLabel = gearEditCoverLabel(mediaItems, safeCoverMediaId),
            albumTitles = selectedAlbumTitles,
            displayTimeMillis = displayTimeMillis,
        )

        GearEditSaveRow(
            isSaving = isSaving,
            onCancel = handleClose,
            onSave = ::saveDraft,
        )

        GearEditSection(
            title = "危险操作",
            subtitle = "删除帖子会进入回收站流程，媒体列表调整请在上方同一套媒体列表里完成。",
        ) {
            GearEditEntryRow(
                title = "删除整个帖子",
                subtitle = "本轮支持“仅删帖子”以及“删帖子并系统删其中媒体”的本地版本。",
                danger = true,
                onClick = {
                    showDeletePostDialog = true
                },
            )
        }
    }

    if (showDeletePostDialog) {
        AlertDialog(
            onDismissRequest = { showDeletePostDialog = false },
            title = { Text("删除整个帖子") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
                ) {
                    Text("选择只删除帖子，或在删除帖子时把其中媒体一起记入“媒体系统删”。")
                    if (systemDeleteImpact.sharedMediaCount > 0) {
                        Text(
                            text = "其中有 ${systemDeleteImpact.sharedMediaCount} 张媒体同时属于其他帖子，会带来 ${systemDeleteImpact.affectedOtherPostCount} 个其他帖子的全局影响。",
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (systemDeleteImpact.mediaCount > 0) {
                        Text("当前帖子共有 ${systemDeleteImpact.mediaCount} 张媒体会进入本地系统删流程。")
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs)) {
                    TextButton(
                        onClick = {
                            showDeletePostDialog = false
                            onDeleteCurrentPost(route.postId, false)
                        },
                    ) {
                        Text("仅删帖子")
                    }
                    TextButton(
                        onClick = {
                            showDeletePostDialog = false
                            onDeleteCurrentPost(route.postId, true)
                        },
                    ) {
                        Text("删帖并系统删媒体")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePostDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun RealGearEditScreen(
    route: GearEditRoute,
    onBack: () -> Unit,
    onPostUpdated: (postId: String, albumId: String?) -> Unit,
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
    val mediaItems = uiState.mediaItems.map(ManagedPostMediaUiModel::toPostMediaListItem)
    val safeCoverMediaId = uiState.coverMediaId?.takeIf { id -> mediaItems.any { it.id == id } }
        ?: mediaItems.firstOrNull()?.id
    val selectedAlbumTitles = uiState.albums
        .filter { uiState.selectedAlbumIds.contains(it.id) }
        .map { it.title }

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
                .background(MaterialTheme.colorScheme.background)
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
                text = "正在读取后端帖子编辑信息…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
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
            GearEditSection(
                title = "保存状态",
                subtitle = errorMessage,
            ) {}
        }

        uiState.statusMessage?.let { statusMessage ->
            GearEditSection(
                title = "操作结果",
                subtitle = statusMessage,
            ) {}
        }

        GearEditMemoryHeader(
            mediaCount = mediaItems.size,
            albumTitles = selectedAlbumTitles,
            coverLabel = gearEditCoverLabel(mediaItems, safeCoverMediaId),
        )

        GearEditMediaPreviewSection(
            items = mediaItems,
            coverMediaId = safeCoverMediaId,
            onOpenAll = { showPostMediaList = true },
        )

        GearEditTextSection(
            title = uiState.title,
            summary = uiState.summary,
            enabled = !uiState.isSaving,
            onTitleChange = viewModel::updateTitle,
            onSummaryChange = viewModel::updateSummary,
        )

        GearEditSection(
            title = "时间设置",
            subtitle = "沿用新建帖子流程的发布时间检查，保存后同步到后端。",
        ) {
            Text(
                text = formatGearEditTime(uiState.displayTimeMillis),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                GearEditChip(text = "-1小时") { viewModel.shiftDisplayTime(-(60 * 60 * 1000L)) }
                GearEditChip(text = "+1小时") { viewModel.shiftDisplayTime(60 * 60 * 1000L) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                GearEditChip(text = "-1天") { viewModel.shiftDisplayTime(-(24 * 60 * 60 * 1000L)) }
                GearEditChip(text = "+1天") { viewModel.shiftDisplayTime(24 * 60 * 60 * 1000L) }
                GearEditChip(text = "设为现在") { viewModel.setDisplayTimeNow() }
            }
        }

        GearEditSection(
            title = "放进相册",
            subtitle = if (uiState.selectedAlbumIds.isEmpty()) {
                "至少选择一个相册后才能保存。"
            } else {
                "已选择 ${uiState.selectedAlbumIds.size} 个相册。"
            },
        ) {
            if (uiState.selectedAlbumIds.isEmpty()) {
                Text(
                    text = "请至少选择一个相册后再保存。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            AlbumSelectionFlow(
                albums = uiState.albums,
                selectedAlbumIds = uiState.selectedAlbumIds,
                onToggleAlbum = viewModel::toggleAlbum,
            )
        }

        GearEditPublishSummary(
            mediaCount = mediaItems.size,
            coverLabel = gearEditCoverLabel(mediaItems, safeCoverMediaId),
            albumTitles = selectedAlbumTitles,
            displayTimeMillis = uiState.displayTimeMillis,
        )

        GearEditSaveRow(
            isSaving = uiState.isSaving,
            onCancel = handleClose,
            onSave = {
                viewModel.save(
                    onSuccess = {
                        onPostUpdated(route.postId, uiState.selectedAlbumIds.firstOrNull())
                        onBack()
                    },
                )
            },
        )

        GearEditSection(
            title = "危险操作",
            subtitle = "删除帖子会进入回收站流程，媒体列表调整请在上方同一套媒体列表里完成。",
        ) {
            GearEditEntryRow(
                title = "删除整个帖子",
                subtitle = "REAL 模式下会把帖子移入后端回收站。",
                danger = true,
                onClick = { showDeletePostDialog = true },
            )
        }
    }

    if (showDeletePostDialog) {
        AlertDialog(
            onDismissRequest = { showDeletePostDialog = false },
            title = { Text("删除整个帖子") },
            text = {
                Text("确认后会把当前帖子移入后端回收站，帖子详情、相册页和回收站会同步刷新。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeletePostDialog = false
                        viewModel.deletePost(onSuccess = onBack)
                    },
                    enabled = !uiState.isDeleting,
                ) {
                    Text(if (uiState.isDeleting) "处理中…" else "确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePostDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun GearEditTopBar(
    onCancel: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel) {
            Text("取消")
        }
        Text(
            text = "帖子编辑",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        TextButton(onClick = onSave, enabled = saveEnabled) {
            Text("保存")
        }
    }
}

@Composable
private fun GearEditMemoryHeader(
    mediaCount: Int,
    albumTitles: List<String>,
    coverLabel: String,
) {
    val spacing = YingShiThemeTokens.spacing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = "编辑这条记忆",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "标题、简介、相册、封面和媒体顺序会在保存后一起刷新到帖子详情。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        title = "写下这条记忆",
        subtitle = "标题用于列表识别，简介可以保留当时的心情或补充说明。",
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = enabled,
            label = { Text("标题") },
            placeholder = { Text("给这条记忆起个名字") },
        )
        Spacer(modifier = Modifier.size(4.dp))
        OutlinedTextField(
            value = summary,
            onValueChange = onSummaryChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            enabled = enabled,
            label = { Text("简介 / 摘要") },
            placeholder = { Text("写一点背景、感受或想留给以后看的话") },
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
            "当前帖子没有媒体。"
        } else {
            "预览前 4 项；排序、封面和移除都在全部列表中管理。"
        },
    ) {
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenAll) {
                Text("全部")
            }
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
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)),
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
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
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
    GearEditSection(title = "保存前检查", subtitle = "确认后会刷新帖子详情和相册卡片。") {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
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
        TextButton(
            onClick = onCancel,
            enabled = !isSaving,
        ) {
            Text("取消")
        }
        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f),
            enabled = !isSaving,
            shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(if (isSaving) "保存中…" else "保存帖子")
        }
    }
}

@Composable
private fun GearEditInfoChip(text: String) {
    Surface(
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GearEditSummaryRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun AlbumSelectionFlow(
    albums: List<AlbumSummaryUiModel>,
    selectedAlbumIds: List<String>,
    onToggleAlbum: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
    ) {
        buildGearEditAlbumRows(albums).forEach { rowAlbums ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
            ) {
                rowAlbums.forEach { album ->
                    SelectableGearEditChip(
                        text = album.title,
                        selected = selectedAlbumIds.contains(album.id),
                        onClick = { onToggleAlbum(album.id) },
                    )
                }
            }
        }
    }
}

private fun buildGearEditAlbumRows(albums: List<AlbumSummaryUiModel>): List<List<AlbumSummaryUiModel>> {
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

@Composable
private fun GearEditChip(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = YingShiThemeTokens.spacing.sm,
                vertical = YingShiThemeTokens.spacing.xs,
            ),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SelectableGearEditChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
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
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = if (danger) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.06f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        GearEditTopBar(onCancel = onBack, onSave = onBack)
        Text(
            text = "当前帖子不存在，暂时无法进入 Gear Edit。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
