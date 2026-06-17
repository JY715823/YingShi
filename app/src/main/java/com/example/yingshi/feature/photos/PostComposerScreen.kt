package com.example.yingshi.feature.photos

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.yingshi.data.model.CreatePostPayload
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun CreatePostScreen(
    route: CreatePostRoute,
    onBack: () -> Unit,
    onCreated: (PostDetailPlaceholderRoute) -> Unit,
    onSubmittedToBackground: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mode = RepositoryProvider.currentMode
    val seedState by produceState(
        initialValue = CreatePostUiState(
            isLoading = true,
            initialMediaItems = route.initialMediaItems,
        ),
        route,
        mode,
        AuthSessionManager.isLoggedIn,
    ) {
        value = loadCreatePostUiState(route)
    }
    val mediaKey = buildString {
        append(route.initialMediaItems.joinToString(separator = "|") { it.id })
        append("::")
        append(route.initialAppMediaIds.joinToString(separator = "|"))
    }
    var initialized by rememberSaveable(route.source, mediaKey) { mutableStateOf(false) }
    var isSubmitting by rememberSaveable(route.source, mediaKey) { mutableStateOf(false) }
    var title by rememberSaveable(route.source, mediaKey) { mutableStateOf("") }
    var summary by rememberSaveable(route.source, mediaKey) { mutableStateOf("") }
    var selectedAlbumIds by rememberSaveable(route.source, mediaKey) { mutableStateOf(emptyList<String>()) }
    val collaboratorDirectory = rememberCollaboratorDirectorySnapshot()
    val availableCollaboratorIds = remember(collaboratorDirectory) {
        collaboratorDirectory.all.mapTo(linkedSetOf()) { it.userId }
    }
    var selectedParticipantUserIds by rememberSaveable(route.source, mediaKey) { mutableStateOf(emptySet<String>()) }
    var selectedCoverMediaId by rememberSaveable(route.source, mediaKey) {
        mutableStateOf(
            route.initialMediaItems.firstOrNull()?.id
                ?: route.initialAppMediaItems.firstOrNull()?.mediaId
                ?: route.initialAppMediaIds.firstOrNull(),
        )
    }
    var selectedSystemMediaItems by remember(route.source, mediaKey) {
        mutableStateOf(route.initialMediaItems.distinctBy { it.id })
    }
    var selectedAppMediaItems by remember(route.source, mediaKey) {
        mutableStateOf(route.initialAppMediaItems.distinctBy { it.mediaId })
    }
    var hydratedInitialAppMediaIds by remember(route.source, mediaKey) { mutableStateOf(false) }
    var showPostMediaList by remember(route.source, mediaKey) { mutableStateOf(false) }
    var showAlbumDirectory by remember(route.source, mediaKey) { mutableStateOf(false) }
    var showPhotoFeedPicker by remember(route.source, mediaKey) { mutableStateOf(false) }
    var showTimeEditorSheet by remember(route.source, mediaKey) { mutableStateOf(false) }
    var showDiscardConfirm by rememberSaveable(route.source, mediaKey) { mutableStateOf(false) }
    var showClearAllConfirm by rememberSaveable(route.source, mediaKey) { mutableStateOf(false) }
    var displayTimeMillis by rememberSaveable(route.source, mediaKey) { mutableStateOf(System.currentTimeMillis()) }
    var localMessage by rememberSaveable(route.source, mediaKey) { mutableStateOf<String?>(null) }
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val availableAppMediaItems by produceState(
        initialValue = emptyList<CreatePostAppMediaItem>(),
        mode,
        AuthSessionManager.isLoggedIn,
    ) {
        value = loadCreatePostAppMediaItems()
    }
    val selectedAppMediaIds = selectedAppMediaItems.map { it.mediaId }
    val selectedSystemMediaIds = selectedSystemMediaItems.map { it.id }
    val selectedMediaIds = selectedSystemMediaIds + selectedAppMediaIds
    val normalizedSelectedParticipantUserIds = remember(selectedParticipantUserIds, availableCollaboratorIds) {
        normalizeOwnedCollaboratorSelection(
            selectedUserIds = selectedParticipantUserIds,
            allUserIds = availableCollaboratorIds,
            fallbackUserId = collaboratorDirectory.currentUser?.userId,
        )
    }
    val resolvedCoverMediaId = selectedCoverMediaId?.takeIf { selectedMediaIds.contains(it) }
        ?: selectedSystemMediaIds.firstOrNull()
        ?: selectedAppMediaIds.firstOrNull()
    val postMediaListItems = selectedSystemMediaItems.map(SystemMediaItem::toPostMediaListItem) +
        selectedAppMediaItems.map(CreatePostAppMediaItem::toPostMediaListItem)
    val selectedAlbumTitles = seedState.albums
        .filter { selectedAlbumIds.contains(it.id) }
        .map { it.title }
    val hasUnsavedChanges = title.isNotBlank() ||
        summary.isNotBlank() ||
        selectedSystemMediaItems.isNotEmpty() ||
        selectedAppMediaItems.isNotEmpty()
    val publishButtonText = when {
        isSubmitting -> "创建中..."
        selectedSystemMediaItems.isNotEmpty() -> "上传并创建相册"
        else -> "创建相册"
    }

    fun handleClose() {
        if (isSubmitting) return
        if (hasUnsavedChanges) {
            showDiscardConfirm = true
        } else {
            onBack()
        }
    }

    if (showPostMediaList) {
        PostMediaListScreen(
            initialItems = postMediaListItems,
            initialCoverMediaId = resolvedCoverMediaId,
            allowEmpty = true,
            onCancel = { showPostMediaList = false },
            onConfirm = { updatedItems, updatedCoverId ->
                val updatedIds = updatedItems.map { it.id }
                val systemById = selectedSystemMediaItems.associateBy { it.id }
                val appById = selectedAppMediaItems.associateBy { it.mediaId }
                selectedSystemMediaItems = updatedIds.mapNotNull(systemById::get)
                selectedAppMediaItems = updatedIds.mapNotNull(appById::get)
                selectedCoverMediaId = updatedCoverId
            },
            modifier = modifier,
            mode = PostMediaListMode.AUTO_APPLY,
        )
        return
    }

    if (showAlbumDirectory && seedState.albums.isNotEmpty()) {
        AlbumDirectoryDialog(
            albums = seedState.albums,
            selectedAlbumId = selectedAlbumIds.firstOrNull().orEmpty(),
            onDismiss = { showAlbumDirectory = false },
            onCreateLargeAlbum = {
                showAlbumDirectory = false
                localMessage = "请先返回相册页新建大相册。"
            },
            onSelectAlbum = { albumId ->
                selectedAlbumIds = listOf(albumId)
                showAlbumDirectory = false
            },
        )
    }

    if (showPhotoFeedPicker) {
        AppPhotoFeedPickerScreen(
            title = "选择媒体",
            confirmLabel = "加入新相册",
            initialSelectedMediaIds = selectedAppMediaIds.toSet(),
            onBack = { showPhotoFeedPicker = false },
            onConfirm = { items ->
                val nextItems = items.distinctBy { it.mediaId }
                selectedAppMediaItems = nextItems
                val remainingIds = selectedSystemMediaIds + nextItems.map { it.mediaId }
                if (selectedCoverMediaId != null && selectedCoverMediaId !in remainingIds) {
                    selectedCoverMediaId = remainingIds.firstOrNull()
                }
                showPhotoFeedPicker = false
            },
            modifier = modifier,
        )
        return
    }

    LaunchedEffect(availableAppMediaItems, route.initialAppMediaIds, hydratedInitialAppMediaIds) {
        if (hydratedInitialAppMediaIds) return@LaunchedEffect
        if (route.initialAppMediaIds.isEmpty()) {
            hydratedInitialAppMediaIds = true
            return@LaunchedEffect
        }
        val routeInitialItems = route.initialAppMediaIds
            .distinct()
            .mapNotNull { mediaId ->
                route.initialAppMediaItems.firstOrNull { it.mediaId == mediaId }
                    ?: availableAppMediaItems.firstOrNull { it.mediaId == mediaId }
            }
        if (routeInitialItems.isEmpty() && availableAppMediaItems.isEmpty()) return@LaunchedEffect
        selectedAppMediaItems = (selectedAppMediaItems + routeInitialItems).distinctBy { it.mediaId }
        if (selectedCoverMediaId == null) {
            selectedCoverMediaId = routeInitialItems.firstOrNull()?.mediaId ?: route.initialAppMediaIds.firstOrNull()
        }
        hydratedInitialAppMediaIds = true
    }

    LaunchedEffect(availableCollaboratorIds) {
        if (availableCollaboratorIds.isEmpty()) {
            selectedParticipantUserIds = emptySet()
            return@LaunchedEffect
        }
        selectedParticipantUserIds = normalizeOwnedCollaboratorSelection(
            selectedUserIds = selectedParticipantUserIds,
            allUserIds = availableCollaboratorIds,
            fallbackUserId = collaboratorDirectory.currentUser?.userId,
        )
    }

    LaunchedEffect(seedState) {
        if (initialized || seedState.isLoading) return@LaunchedEffect
        title = seedState.title
        summary = seedState.summary
        selectedAlbumIds = when {
            route.initialAlbumId != null && seedState.albums.any { it.id == route.initialAlbumId } -> listOf(route.initialAlbumId)
            else -> seedState.selectedAlbumIds
        }
        selectedParticipantUserIds = normalizeOwnedCollaboratorSelection(
            selectedUserIds = seedState.participantUserIds.toSet(),
            allUserIds = availableCollaboratorIds,
            fallbackUserId = collaboratorDirectory.currentUser?.userId,
        )
        if (selectedCoverMediaId == null) {
            selectedCoverMediaId = seedState.selectedCoverSourceMediaId
        }
        displayTimeMillis = seedState.displayTimeMillis
        if (selectedMediaIds.isNotEmpty() && selectedCoverMediaId == null) {
            selectedCoverMediaId = selectedMediaIds.first()
        }
        initialized = true
    }

    BackHandler(onBack = ::handleClose)

    fun toggleAlbum(albumId: String) {
        selectedAlbumIds = if (selectedAlbumIds.contains(albumId)) {
            emptyList()
        } else {
            listOf(albumId)
        }
    }

    fun submitDraft() {
        localMessage = null
        if (selectedAlbumIds.isEmpty()) {
            localMessage = "请至少选择一个相册。"
            return
        }
        val draft = CreatePostDraft(
            title = title.trim(),
            summary = summary.trim(),
            displayTimeMillis = displayTimeMillis,
            albumIds = selectedAlbumIds,
        participantUserIds = normalizedSelectedParticipantUserIds.toList(),
            coverSourceMediaId = resolvedCoverMediaId,
        )
        if (selectedSystemMediaItems.isNotEmpty()) {
            if (mode == RepositoryMode.REAL) {
                val queuedCount = LocalSystemMediaBridgeRepository.enqueueCreatePostUpload(
                    context = context,
                    mediaItems = selectedSystemMediaItems,
                    draft = draft,
                    additionalAppMediaIds = selectedAppMediaIds,
                    additionalAppCoverMediaId = resolvedCoverMediaId,
                )
                if (queuedCount > 0) {
                    Toast.makeText(context, "已加入上传队列，完成后会创建新小相册。", Toast.LENGTH_SHORT).show()
                    onSubmittedToBackground()
                } else {
                    localMessage = "当前没有可处理的媒体。"
                }
            } else {
                val createdPost = LocalSystemMediaBridgeRepository.createPostFromSystemMediaDraft(
                    draft = draft,
                    mediaItems = selectedSystemMediaItems,
                    additionalAppMediaItems = selectedAppMediaIds.mapNotNull(FakePhotoFeedRepository::findPhotoFeedItem),
                )
                if (createdPost == null) {
                    localMessage = "本地新小相册创建失败，请稍后重试。"
                } else {
                    onCreated(FakeAlbumRepository.toPostDetailRoute(createdPost))
                }
            }
            return
        }

        if (selectedAppMediaItems.isNotEmpty()) {
            if (mode == RepositoryMode.REAL) {
                scope.launch {
                    isSubmitting = true
                    val result = RepositoryProvider.postRepository.createPost(
                        CreatePostPayload(
                            title = draft.title.ifBlank { "新小相册" },
                            summary = draft.summary,
                            participantUserIds = draft.participantUserIds,
                            displayTimeMillis = draft.displayTimeMillis,
                            albumId = draft.requireAlbumId(),
                            initialMediaIds = selectedAppMediaIds,
                            coverMediaId = resolvedCoverMediaId,
                        ),
                    )
                    isSubmitting = false
                    when (result) {
                        is ApiResult.Success -> {
                            notifyRealBackendPostChanged(postIds = setOf(result.data.postId))
                            onCreated(
                                result.data.toPostDetailPlaceholderRoute(
                                    selectedAlbumId = selectedAlbumIds.first(),
                                ).copy(
                                    highlightMediaIds = selectedAppMediaIds.distinct(),
                                    focusMediaId = selectedAppMediaIds.firstOrNull(),
                                ),
                            )
                        }
                        is ApiResult.Error -> {
                            localMessage = result.toBackendUiMessage("创建小相册失败，请稍后重试。")
                        }
                        ApiResult.Loading -> Unit
                    }
                }
            } else {
                val selectedItems = selectedAppMediaIds.mapNotNull(FakePhotoFeedRepository::findPhotoFeedItem)
                val createdPost = FakeAlbumRepository.createConfiguredLocalPostFromPhotoFeedItems(
                    draft = draft,
                    mediaItems = selectedItems,
                )
                if (createdPost == null) {
                    localMessage = "本地新小相册创建失败，请稍后重试。"
                } else {
                    onCreated(
                        FakeAlbumRepository.toPostDetailRoute(createdPost).copy(
                            highlightMediaIds = selectedAppMediaIds.distinct(),
                            focusMediaId = selectedAppMediaIds.firstOrNull(),
                        ),
                    )
                }
            }
            return
        }

        scope.launch {
            isSubmitting = true
            val result = RepositoryProvider.postRepository.createPost(
                CreatePostPayload(
                    title = draft.title.ifBlank { "新小相册" },
                    summary = draft.summary,
                    participantUserIds = draft.participantUserIds,
                    displayTimeMillis = draft.displayTimeMillis,
                    albumId = draft.requireAlbumId(),
                    coverMediaId = null,
                ),
            )
            isSubmitting = false
            when (result) {
                is ApiResult.Success -> {
                    if (mode == RepositoryMode.REAL) {
                        notifyRealBackendPostChanged(postIds = setOf(result.data.postId))
                    }
                    onCreated(
                        result.data.toPostDetailPlaceholderRoute(
                            selectedAlbumId = selectedAlbumIds.first(),
                        ),
                    )
                }
                is ApiResult.Error -> {
                    localMessage = result.toBackendUiMessage("创建小相册失败，请稍后重试。")
                }
                ApiResult.Loading -> Unit
            }
        }
    }

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
                seedState.isLoading -> {
                    BackendLoadingCard(
                        text = "正在准备新增小相册表单…",
                        fillWidth = true,
                    )
                }
                seedState.tokenMissing -> {
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
                    seedState.errorMessage?.let { message ->
                        BackendInlineNotice(
                            text = message,
                            emphasized = true,
                        )
                    }

                    CreatePostMediaPreviewSection(
                        items = postMediaListItems,
                        coverMediaId = resolvedCoverMediaId,
                        onAddMedia = { showPhotoFeedPicker = true },
                        onClearAll = {
                            if (postMediaListItems.isEmpty()) {
                                localMessage = "当前还没有可清空的媒体"
                            } else {
                                showClearAllConfirm = true
                            }
                        },
                        onRemoveMedia = { mediaId ->
                            selectedSystemMediaItems = selectedSystemMediaItems.filterNot { it.id == mediaId }
                            selectedAppMediaItems = selectedAppMediaItems.filterNot { it.mediaId == mediaId }
                            val remainingIds = selectedSystemMediaItems.map { it.id } + selectedAppMediaItems.map { it.mediaId }
                            if (selectedCoverMediaId == mediaId) {
                                selectedCoverMediaId = remainingIds.firstOrNull()
                            }
                            localMessage = "已移出 1 项媒体"
                        },
                        onOpenAll = { showPostMediaList = true },
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
                            onValueChange = { title = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isSubmitting,
                            label = { Text("标题") },
                            placeholder = { Text("输入标题") },
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        OutlinedTextField(
                            value = summary,
                            onValueChange = { summary = it },
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
                                    localMessage = "至少保留 1 位所属"
                                } else {
                                    selectedParticipantUserIds = nextOwnedSelection
                                    localMessage = "已更新所属"
                                }
                            },
                        )
                    }

                    CreatePostSection(
                        title = "选择所属大相册",
                    ) {
                        if (seedState.albums.isEmpty()) {
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
                                    seedState.albums.forEach { album ->
                                        SelectableAlbumChip(
                                            title = album.title,
                                            selected = selectedAlbumIds.contains(album.id),
                                            onClick = { toggleAlbum(album.id) },
                                        )
                                    }
                                }
                                AlbumIconAction(
                                    icon = Icons.Rounded.Menu,
                                    contentDescription = "全部大相册",
                                    containerColor = colors.primaryContainer.copy(alpha = 0.78f),
                                    contentColor = colors.titleAccent,
                                    onClick = { showAlbumDirectory = true },
                                )
                            }
                        }
                    }
                    CreatePostSection(title = "时间") {
                        SmallAlbumTimeEditRow(
                            label = "小相册时间",
                            value = formatCreatePostTime(displayTimeMillis),
                            enabled = !isSubmitting,
                            onClick = { showTimeEditorSheet = true },
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
                            onClick = ::submitDraft,
                            modifier = Modifier.weight(1f),
                            enabled = !isSubmitting && !seedState.tokenMissing,
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
            onDismissRequest = { showDiscardConfirm = false },
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
                        showDiscardConfirm = false
                        onBack()
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(
                    text = "继续编辑",
                    emphasized = true,
                    onClick = { showDiscardConfirm = false },
                )
            },
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
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
                        showClearAllConfirm = false
                        selectedSystemMediaItems = emptyList()
                        selectedAppMediaItems = emptyList()
                        selectedCoverMediaId = null
                        localMessage = "已清空待选媒体"
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(
                    text = "取消",
                    onClick = { showClearAllConfirm = false },
                )
            },
        )
    }

    if (showTimeEditorSheet) {
        ViewerTimeEditorSheet(
            initialTimeMillis = displayTimeMillis,
            onDismiss = { showTimeEditorSheet = false },
            onConfirm = { nextTimeMillis ->
                displayTimeMillis = nextTimeMillis
                showTimeEditorSheet = false
                localMessage = "已更新小相册时间"
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
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(YingShiThemeTokens.radius.capsule)),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (selected) {
            colors.primaryContainer.copy(alpha = 0.72f)
        } else {
            colors.sectionBackground.copy(alpha = 0.72f)
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                colors.glassStroke.copy(alpha = 0.82f)
            } else {
                colors.dividerSoft.copy(alpha = 0.68f)
            },
        ),
        onClick = onClick,
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.titleAccent else colors.textSecondary,
        )
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
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(28.dp),
                            ) {
                                AlbumIconAction(
                                    icon = Icons.Default.Close,
                                    contentDescription = "移出媒体",
                                    containerColor = Color.Black.copy(alpha = 0.58f),
                                    contentColor = Color.White,
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
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }
                    if (rowIndex == 1 && previewItems.size < 6) {
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

private suspend fun loadCreatePostUiState(
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

private suspend fun loadCreatePostAppMediaItems(): List<CreatePostAppMediaItem> {
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
