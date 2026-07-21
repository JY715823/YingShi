package com.example.yingshi.feature.photos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CreatePostScreen(
    route: CreatePostRoute,
    onBack: () -> Unit,
    onCreated: (PostDetailPlaceholderRoute) -> Unit,
    onSubmittedToBackground: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val viewModel: PostComposerViewModel = viewModel(
        key = "post-composer-${route.source}",
        factory = PostComposerViewModel.factory(route),
    )
    val uiState by viewModel.uiState.collectAsState()

    val collaboratorDirectory = uiState.collaboratorDirectory
    val availableCollaboratorIds = uiState.availableCollaboratorIds
    val normalizedSelectedParticipantUserIds = uiState.normalizedSelectedParticipantUserIds
    val resolvedCoverMediaId = uiState.resolvedCoverMediaId
    val postMediaListItems = uiState.postMediaListItems
    val selectedAppMediaIds = uiState.selectedAppMediaIds
    val selectedSystemMediaIds = uiState.selectedSystemMediaIds
    val isSubmitting = uiState.isSubmitting
    val title = uiState.title
    val summary = uiState.summary
    val selectedAlbumIds = uiState.selectedAlbumIds
    val displayTimeMillis = uiState.displayTimeMillis
    val localMessage = uiState.localMessage
    val showPostMediaList = uiState.showPostMediaList
    val showAlbumDirectory = uiState.showAlbumDirectory
    val showPhotoFeedPicker = uiState.showPhotoFeedPicker
    val showTimeEditorSheet = uiState.showTimeEditorSheet
    val showDiscardConfirm = uiState.showDiscardConfirm
    val showClearAllConfirm = uiState.showClearAllConfirm
    val hasUnsavedChanges = uiState.hasUnsavedChanges
    val publishButtonText = uiState.publishButtonText

    LaunchedEffect(uiState.availableAppMediaItems, route.initialAppMediaIds, uiState.hydratedInitialAppMediaIds) {
        if (uiState.hydratedInitialAppMediaIds) return@LaunchedEffect
        viewModel.hydrateInitialAppMediaIds()
    }

    LaunchedEffect(availableCollaboratorIds) {
        if (availableCollaboratorIds.isNotEmpty()) {
            viewModel.normalizeParticipants()
        }
    }

    fun handleClose() {
        if (isSubmitting) return
        if (hasUnsavedChanges) {
            viewModel.setShowDiscardConfirm(true)
        } else {
            onBack()
        }
    }

    if (showPostMediaList) {
        PostMediaListScreen(
            initialItems = postMediaListItems,
            initialCoverMediaId = resolvedCoverMediaId,
            allowEmpty = true,
            onCancel = { viewModel.setShowPostMediaList(false) },
            onConfirm = { updatedItems, updatedCoverId ->
                viewModel.updateMediaDraft(updatedItems, updatedCoverId)
            },
            modifier = modifier,
            mode = PostMediaListMode.AUTO_APPLY,
        )
        return
    }

    if (showAlbumDirectory && uiState.albums.isNotEmpty()) {
        AlbumDirectoryDialog(
            albums = uiState.albums,
            selectedAlbumId = selectedAlbumIds.firstOrNull().orEmpty(),
            onDismiss = { viewModel.setShowAlbumDirectory(false) },
            onCreateLargeAlbum = {
                viewModel.setShowAlbumDirectory(false)
                viewModel.setLocalMessage("请先返回相册页新建大相册。")
            },
            onSelectAlbum = { albumId ->
                viewModel.toggleAlbum(albumId)
                viewModel.setShowAlbumDirectory(false)
            },
        )
    }

    if (showPhotoFeedPicker) {
        AppPhotoFeedPickerScreen(
            title = "选择媒体",
            confirmLabel = "加入新相册",
            initialSelectedMediaIds = selectedAppMediaIds.toSet(),
            onBack = { viewModel.setShowPhotoFeedPicker(false) },
            onConfirm = { items ->
                viewModel.addAppMediaItems(items)
                viewModel.setShowPhotoFeedPicker(false)
            },
            modifier = modifier,
        )
        return
    }

    BackHandler(onBack = ::handleClose)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = colors.appBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg, vertical = spacing.md)
                .padding(bottom = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            CreatePostTopBar(
                mediaCount = postMediaListItems.size,
                onBack = ::handleClose,
            )

            when {
                uiState.isLoading -> {
                    BackendLoadingCard(
                        text = "正在准备新增小相册表单…",
                        fillWidth = true,
                    )
                }
                uiState.tokenMissing -> {
                    BackendNoticeCard(
                        title = "需要先登录",
                        text = "新增小相册前需要先连接服务，当前无法读取大相册。",
                        fillWidth = true,
                    )
                }
                else -> {
                    localMessage?.let { message ->
                        BackendInlineNotice(
                            text = message,
                            emphasized = true,
                        )
                    }
                    uiState.errorMessage?.let { message ->
                        BackendInlineNotice(
                            text = message,
                            emphasized = true,
                        )
                    }

                    CreatePostMediaPreviewSection(
                        items = postMediaListItems,
                        coverMediaId = resolvedCoverMediaId,
                        onAddMedia = { viewModel.setShowPhotoFeedPicker(true) },
                        onClearAll = {
                            if (postMediaListItems.isEmpty()) {
                                viewModel.setLocalMessage("当前还没有可清空的媒体")
                            } else {
                                viewModel.setShowClearAllConfirm(true)
                            }
                        },
                        onRemoveMedia = { mediaId ->
                            viewModel.removeMedia(mediaId)
                        },
                        onOpenAll = { viewModel.setShowPostMediaList(true) },
                        ownershipLabel = describeSelectedCollaborators(
                            directory = collaboratorDirectory,
                            selectedUserIds = normalizedSelectedParticipantUserIds,
                        ),
                    )

                    CreatePostSection(
                        title = "标题和简介",
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { viewModel.updateTitle(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isSubmitting,
                            label = { Text("标题") },
                            placeholder = { Text("输入标题") },
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        OutlinedTextField(
                            value = summary,
                            onValueChange = { viewModel.updateSummary(it) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            enabled = !isSubmitting,
                            label = { Text("简介") },
                            placeholder = { Text("输入简介") },
                        )
                    }

                    CreatePostSection(
                        title = "所属",
                        subtitle = "至少选择 1 位，可以同时勾选两位",
                    ) {
                        OwnershipSelectorRow(
                            directory = collaboratorDirectory,
                            selectedUserIds = normalizedSelectedParticipantUserIds,
                            onToggleUserId = { userId ->
                                val nextSelection = toggleCollaboratorSelectionKeepingEmpty(
                                    currentSelection = normalizedSelectedParticipantUserIds,
                                    toggledUserId = userId,
                                    allUserIds = availableCollaboratorIds,
                                )
                                val nextOwnedSelection = normalizeOwnedCollaboratorSelection(
                                    selectedUserIds = nextSelection,
                                    allUserIds = availableCollaboratorIds,
                                    fallbackUserId = collaboratorDirectory.currentUser?.userId,
                                )
                                if (nextOwnedSelection == normalizedSelectedParticipantUserIds &&
                                    normalizedSelectedParticipantUserIds.size == 1 &&
                                    userId in normalizedSelectedParticipantUserIds
                                ) {
                                    viewModel.setLocalMessage("至少保留 1 位所属")
                                } else {
                                    viewModel.updateParticipantUserIds(nextOwnedSelection)
                                    viewModel.setLocalMessage("已更新所属")
                                }
                            },
                        )
                    }

                    CreatePostSection(
                        title = "选择所属大相册",
                    ) {
                        if (uiState.albums.isEmpty()) {
                            BackendInlineNotice(text = "当前没有可选相册。")
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    uiState.albums.forEach { album ->
                                        SelectableAlbumChip(
                                            title = album.title,
                                            selected = selectedAlbumIds.contains(album.id),
                                            onClick = { viewModel.toggleAlbum(album.id) },
                                        )
                                    }
                                }
                                AlbumIconAction(
                                    icon = Icons.Rounded.Menu,
                                    contentDescription = "全部大相册",
                                    containerColor = colors.primaryContainer.copy(alpha = 0.78f),
                                    contentColor = colors.titleAccent,
                                    onClick = { viewModel.setShowAlbumDirectory(true) },
                                )
                            }
                        }
                    }
                    CreatePostSection(title = "时间") {
                        SmallAlbumTimeEditRow(
                            label = "小相册时间",
                            value = formatCreatePostTime(displayTimeMillis),
                            enabled = !isSubmitting,
                            onClick = { viewModel.setShowTimeEditorSheet(true) },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CreatePostActionButton(
                            text = "取消",
                            onClick = ::handleClose,
                            enabled = !isSubmitting,
                        )
                        CreatePostActionButton(
                            text = publishButtonText,
                            onClick = {
                                viewModel.setLocalMessage(null)
                                viewModel.submitDraft(
                                    context = context,
                                    onSuccess = onCreated,
                                    onSubmittedToBackground = onSubmittedToBackground,
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isSubmitting && !uiState.tokenMissing,
                            emphasized = true,
                        )
                    }
                }
            }
        }
    }

    if (showDiscardConfirm) {
        val dialogColors = YingShiThemeTokens.colors
        AlertDialog(
            onDismissRequest = { viewModel.setShowDiscardConfirm(false) },
            containerColor = dialogColors.raisedSurface,
            titleContentColor = dialogColors.titleAccent,
            textContentColor = dialogColors.textSecondary,
            title = {
                Text(
                    text = "放弃这次创建？",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            },
            text = {
                Text("已经选好的照片、标题和简介会一起丢失。")
            },
            confirmButton = {
                TrashDialogActionButton(
                    text = "放弃退出",
                    danger = true,
                    onClick = {
                        viewModel.setShowDiscardConfirm(false)
                        onBack()
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(
                    text = "继续编辑",
                    emphasized = true,
                    onClick = { viewModel.setShowDiscardConfirm(false) },
                )
            },
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowClearAllConfirm(false) },
            containerColor = colors.raisedSurface,
            titleContentColor = colors.titleAccent,
            textContentColor = colors.textSecondary,
            title = {
                Text(
                    text = "清空已选媒体",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            },
            text = {
                Text("确认后会移除当前已选的所有媒体，封面也会一起清空。")
            },
            confirmButton = {
                TrashDialogActionButton(
                    text = "确认清空",
                    danger = true,
                    onClick = {
                        viewModel.setShowClearAllConfirm(false)
                        viewModel.clearAllMedia()
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(
                    text = "取消",
                    onClick = { viewModel.setShowClearAllConfirm(false) },
                )
            },
        )
    }

    if (showTimeEditorSheet) {
        ViewerTimeEditorSheet(
            initialTimeMillis = displayTimeMillis,
            onDismiss = { viewModel.setShowTimeEditorSheet(false) },
            onConfirm = { nextTimeMillis ->
                viewModel.updateDisplayTime(nextTimeMillis)
                viewModel.setShowTimeEditorSheet(false)
                viewModel.setLocalMessage("已更新小相册时间")
            },
        )
    }
}

@Composable
private fun CreatePostTopBar(
    mediaCount: Int,
    onBack: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = colors.sectionBackground.copy(alpha = 0.78f),
            border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
            onClick = onBack,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "<",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "新建小相册",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
        }
    }
}

@Composable
private fun CreatePostSection(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.titleAccent,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
        content()
    }
}

@Composable
internal fun SelectableAlbumChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.md)
    Surface(
        modifier = Modifier.clip(shape),
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
        onClick = onClick,
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
                        .background(Brush.linearGradient(listOf(colors.titleAuraWarmGlow, colors.titleAuraCoolGlow))),
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

@Composable
internal fun SelectableInfoChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier,
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
internal fun CreatePostArrangementHeader(
    mediaCount: Int,
    coverLabel: String,
    ownershipLabel: String,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = colors.softGreenContainer.copy(alpha = 0.64f),
        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = "正在整理这条记忆",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectableInfoChip(text = if (mediaCount > 0) "媒体 $mediaCount 项" else "还没选媒体")
                SelectableInfoChip(text = "封面：$coverLabel")
            }
            SelectableInfoChip(text = "所属：$ownershipLabel")
        }
    }
}

@Composable
private fun CreatePostMediaPreviewSection(
    items: List<PostMediaListItem>,
    coverMediaId: String?,
    onAddMedia: () -> Unit,
    onClearAll: () -> Unit,
    onRemoveMedia: (String) -> Unit,
    onOpenAll: () -> Unit,
    ownershipLabel: String,
) {
    CreatePostSection(
        title = "媒体",
    ) {
        val colors = YingShiThemeTokens.colors
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (items.isEmpty()) "还没有选媒体" else "已选 ${items.size} 项",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            AlbumIconAction(
                icon = Icons.Rounded.Add,
                contentDescription = "添加媒体",
                containerColor = colors.softGreenContainer.copy(alpha = 0.92f),
                contentColor = colors.softGreenAction,
                onClick = onAddMedia,
            )
            CreatePostInlineAction(
                text = "清空",
                enabled = items.isNotEmpty(),
                danger = true,
                onClick = onClearAll,
            )
        }
        val previewItems = items.take(5)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            previewItems.chunked(3).forEachIndexed { rowIndex, rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rowItems.forEach { item ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
                            color = colors.glassSurfaceBase.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.52f)),
                            shadowElevation = 2.dp,
                        ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            PostMediaListThumbnail(
                                item = item,
                                modifier = Modifier.fillMaxSize(),
                                requestSize = 640,
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(28.dp),
                            ) {
                                AlbumIconAction(
                                    icon = Icons.Default.Close,
                                    contentDescription = "移出媒体",
                                    containerColor = colors.viewerBackground.copy(alpha = 0.72f),
                                    contentColor = colors.viewerText,
                                    onClick = { onRemoveMedia(item.id) },
                                )
                            }
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
                                        color = colors.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                        }
                    }
                    if (rowIndex == 1 && previewItems.size < 6) {
                        Surface(
                            onClick = onOpenAll,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
                            color = colors.glassSurfaceBase.copy(alpha = 0.48f),
                            border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.52f)),
                            shadowElevation = 2.dp,
                            contentColor = colors.textSecondary,
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "全部",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                    repeat((3 - rowItems.size - if (rowIndex == 1 && previewItems.size < 6) 1 else 0).coerceAtLeast(0)) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            if (previewItems.size <= 3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(YingShiThemeTokens.radius.lg))
                            .background(colors.sectionBackground.copy(alpha = 0.56f))
                            .clickable(onClick = onOpenAll),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "全部",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textSecondary,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OwnershipSelectorRow(
    directory: CollaboratorDirectorySnapshot,
    selectedUserIds: Set<String>,
    onToggleUserId: (String) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    if (directory.all.isEmpty()) {
        BackendInlineNotice(text = "当前没有可选所属账号。")
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        directory.all.forEach { identity ->
            CollaboratorFilterChip(
                identity = identity,
                selected = identity.userId in selectedUserIds,
                onClick = { onToggleUserId(identity.userId) },
                labelText = if (identity.isCurrentUser) "我" else identity.displayName,
            )
        }
    }
}

@Composable
internal fun CreatePostInlineAction(
    text: String,
    enabled: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    Surface(
        modifier = Modifier.clip(shape),
        shape = shape,
        color = if (danger) {
            colors.raisedSurface.copy(alpha = if (enabled) 0.92f else 0.46f)
        } else {
            colors.sectionBackground.copy(alpha = if (enabled) 0.78f else 0.46f)
        },
        border = BorderStroke(
            1.dp,
            if (danger) colors.memoryAccent.copy(alpha = 0.24f) else colors.dividerSoft.copy(alpha = 0.68f),
        ),
        onClick = { if (enabled) onClick() },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (danger) colors.memoryAccent else colors.textSecondary,
        )
    }
}

internal fun describeSelectedCollaborators(
    directory: CollaboratorDirectorySnapshot,
    selectedUserIds: Set<String>,
): String {
    val identities = directory.resolveOrdered(selectedUserIds)
    if (identities.isEmpty()) return "未设置"
    return identities.joinToString(" / ") { identity ->
        if (identity.isCurrentUser) "我" else identity.displayName
    }
}

@Composable
private fun CreatePostActionButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
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
            emphasized -> colors.primaryContainer.copy(alpha = 0.88f)
            else -> colors.sectionBackground.copy(alpha = 0.72f)
        },
        border = BorderStroke(
            1.dp,
            if (emphasized) colors.glassStroke.copy(alpha = 0.84f) else colors.dividerSoft.copy(alpha = 0.68f),
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
                    emphasized -> colors.titleAccent
                    else -> colors.textSecondary
                },
            )
        }
    }
}

private fun createPostCoverLabel(
    items: List<PostMediaListItem>,
    coverMediaId: String?,
): String {
    if (items.isEmpty()) return "无媒体"
    val index = items.indexOfFirst { it.id == coverMediaId }
    return if (index >= 0) {
        "第 ${index + 1} 项"
    } else {
        "未设置，发布时使用第 1 项"
    }
}

internal suspend fun loadCreatePostUiState(
    route: CreatePostRoute,
): CreatePostUiState {
    val initialItems = route.initialMediaItems
    val defaultDisplayTime = initialItems.maxOfOrNull { it.displayTimeMillis } ?: System.currentTimeMillis()
    val defaultCoverId = initialItems.firstOrNull()?.id
    val defaultParticipantUserIds = defaultCurrentCollaboratorUserIds().toList()
    if (RepositoryProvider.currentMode == RepositoryMode.FAKE) {
        val albums = FakeAlbumRepository.getAlbums()
        val defaultAlbumId = route.initialAlbumId?.takeIf { initialId -> albums.any { it.id == initialId } }
            ?: albums.firstOrNull()?.id
        return CreatePostUiState(
            isLoading = false,
            albums = albums,
            title = "",
            summary = "",
            displayTimeMillis = defaultDisplayTime,
            selectedAlbumIds = defaultAlbumId?.let(::listOf).orEmpty(),
            participantUserIds = defaultParticipantUserIds,
            initialMediaItems = initialItems,
            selectedCoverSourceMediaId = defaultCoverId,
        )
    }

    if (!AuthSessionManager.isLoggedIn) {
        return CreatePostUiState(
            isLoading = false,
            tokenMissing = true,
            initialMediaItems = initialItems,
            displayTimeMillis = defaultDisplayTime,
            participantUserIds = defaultParticipantUserIds,
            selectedCoverSourceMediaId = defaultCoverId,
        )
    }

    return when (val result = RepositoryProvider.albumRepository.getAlbums()) {
        is ApiResult.Success -> {
            val albums = result.data.map { it.toAlbumSummaryUiModel() }
            val defaultAlbumId = route.initialAlbumId?.takeIf { initialId -> albums.any { it.id == initialId } }
                ?: albums.firstOrNull()?.id
            CreatePostUiState(
                isLoading = false,
                albums = albums,
                displayTimeMillis = defaultDisplayTime,
                selectedAlbumIds = defaultAlbumId?.let(::listOf).orEmpty(),
                participantUserIds = defaultParticipantUserIds,
                initialMediaItems = initialItems,
                selectedCoverSourceMediaId = defaultCoverId,
            )
        }
        is ApiResult.Error -> {
            CreatePostUiState(
                isLoading = false,
                errorMessage = result.toBackendUiMessage("读取大相册失败，当前无法创建小相册。"),
                displayTimeMillis = defaultDisplayTime,
                participantUserIds = defaultParticipantUserIds,
                initialMediaItems = initialItems,
                selectedCoverSourceMediaId = defaultCoverId,
            )
        }
        ApiResult.Loading -> CreatePostUiState(
            isLoading = true,
            displayTimeMillis = defaultDisplayTime,
            participantUserIds = defaultParticipantUserIds,
            initialMediaItems = initialItems,
            selectedCoverSourceMediaId = defaultCoverId,
        )
    }
}

internal suspend fun loadCreatePostAppMediaItems(): List<CreatePostAppMediaItem> {
    if (RepositoryProvider.currentMode == RepositoryMode.FAKE) {
        return FakePhotoFeedRepository.getPhotoFeed().map(PhotoFeedItem::toCreatePostAppMediaItem)
    }
    if (!AuthSessionManager.isLoggedIn) return emptyList()
    return when (val result = RepositoryProvider.mediaRepository.getMediaFeedPage(pageSize = 80)) {
        is ApiResult.Success -> result.data.items.map { it.toPhotoFeedItem().toCreatePostAppMediaItem() }
        is ApiResult.Error,
        ApiResult.Loading -> emptyList()
    }
}

private fun formatCreatePostTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}
