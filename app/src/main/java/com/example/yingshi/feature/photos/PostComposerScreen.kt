package com.example.yingshi.feature.photos

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val resolvedCoverMediaId = selectedCoverMediaId?.takeIf { selectedMediaIds.contains(it) }
        ?: selectedSystemMediaIds.firstOrNull()
        ?: selectedAppMediaIds.firstOrNull()
    val postMediaListItems = selectedSystemMediaItems.map(SystemMediaItem::toPostMediaListItem) +
        selectedAppMediaItems.map(CreatePostAppMediaItem::toPostMediaListItem)
    val selectedAlbumTitles = seedState.albums
        .filter { selectedAlbumIds.contains(it.id) }
        .map { it.title }
    val coverStatusLabel = createPostCoverLabel(
        items = postMediaListItems,
        coverMediaId = resolvedCoverMediaId,
    )
    val publishButtonText = when {
        isSubmitting -> "发布中..."
        selectedSystemMediaItems.isNotEmpty() -> "上传并发布"
        else -> "发布记忆"
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
                showPostMediaList = false
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

    LaunchedEffect(seedState) {
        if (initialized || seedState.isLoading) return@LaunchedEffect
        title = seedState.title
        summary = seedState.summary
        selectedAlbumIds = seedState.selectedAlbumIds
        if (selectedCoverMediaId == null) {
            selectedCoverMediaId = seedState.selectedCoverSourceMediaId
        }
        displayTimeMillis = seedState.displayTimeMillis
        if (selectedMediaIds.isNotEmpty() && selectedCoverMediaId == null) {
            selectedCoverMediaId = selectedMediaIds.first()
        }
        initialized = true
    }

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
                onBack = onBack,
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
                    CreatePostMemoryHeader(
                        mediaCount = postMediaListItems.size,
                        albumTitles = selectedAlbumTitles,
                        coverLabel = coverStatusLabel,
                    )

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
                        onOpenAll = { showPostMediaList = true },
                    )

                    CreatePostSection(
                        title = "写下这条记忆",
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isSubmitting,
                            label = { Text("标题") },
                            placeholder = { Text("给这条记忆起个名字") },
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        OutlinedTextField(
                            value = summary,
                            onValueChange = { summary = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            enabled = !isSubmitting,
                            label = { Text("简介 / 摘要") },
                            placeholder = { Text("写一点背景、感受或想留给以后看的话") },
                        )
                    }

                    CreatePostSection(
                        title = "选择所属大相册",
                        subtitle = if (selectedAlbumIds.isEmpty()) {
                            "请选择一个父大相册后再发布。"
                        } else {
                            "当前父大相册：${selectedAlbumTitles.firstOrNull() ?: "未选择"}"
                        },
                    ) {
                        if (seedState.albums.isEmpty()) {
                            BackendInlineNotice(text = "当前没有可选相册。")
                        } else {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
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
                        }
                    }

                    CreatePostPublishSummary(
                        mediaCount = postMediaListItems.size,
                        coverLabel = coverStatusLabel,
                        albumTitles = selectedAlbumTitles,
                        displayTimeMillis = displayTimeMillis,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CreatePostActionButton(
                            text = "取消",
                            onClick = onBack,
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
                text = "写一条记忆",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            Text(
                text = if (mediaCount > 0) {
                    "整理 $mediaCount 项媒体"
                } else {
                    "先写内容，之后可继续补媒体"
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun CreatePostMemoryHeader(
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
                text = "准备创建",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CreatePostInfoChip(text = "媒体 $mediaCount 项")
                CreatePostInfoChip(text = "封面：$coverLabel")
                CreatePostInfoChip(
                    text = if (albumTitles.isEmpty()) {
                        "未选择相册"
                    } else {
                        "相册：${albumTitles.take(2).joinToString("、")}${if (albumTitles.size > 2) "等" else ""}"
                    },
                )
            }
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
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
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
private fun CreatePostInfoChip(
    text: String,
) {
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
private fun SelectableAlbumChip(
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
private fun CreatePostMediaPreviewSection(
    items: List<PostMediaListItem>,
    coverMediaId: String?,
    onOpenAll: () -> Unit,
) {
    CreatePostSection(
        title = "媒体",
        subtitle = if (items.isEmpty()) "当前没有预选媒体。" else null,
    ) {
        val colors = YingShiThemeTokens.colors
        if (items.isEmpty()) {
            BackendInlineNotice(text = "当前没有媒体，创建后可在小相册设置中继续管理。")
            return@CreatePostSection
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "已选 ${items.size} 项 · ${createPostCoverLabel(items, coverMediaId)}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            CreatePostActionButton(text = "全部", onClick = onOpenAll)
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
private fun CreatePostPublishSummary(
    mediaCount: Int,
    coverLabel: String,
    albumTitles: List<String>,
    displayTimeMillis: Long,
) {
    CreatePostSection(title = "发布信息") {
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
                CreatePostSummaryRow(label = "媒体", value = if (mediaCount > 0) "$mediaCount 项" else "无媒体")
                CreatePostSummaryRow(label = "封面", value = coverLabel)
                CreatePostSummaryRow(
                    label = "相册",
                    value = albumTitles.ifEmpty { listOf("未选择") }.joinToString("、"),
                )
                CreatePostSummaryRow(label = "时间", value = formatCreatePostTime(displayTimeMillis))
            }
        }
    }
}

@Composable
private fun CreatePostSummaryRow(
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
    if (RepositoryProvider.currentMode == RepositoryMode.FAKE) {
        val albums = FakeAlbumRepository.getAlbums()
        return CreatePostUiState(
            isLoading = false,
            albums = albums,
            title = "",
            summary = "",
            displayTimeMillis = defaultDisplayTime,
            selectedAlbumIds = albums.firstOrNull()?.id?.let(::listOf).orEmpty(),
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
            selectedCoverSourceMediaId = defaultCoverId,
        )
    }

    return when (val result = RepositoryProvider.albumRepository.getAlbums()) {
        is ApiResult.Success -> {
            val albums = result.data.map { it.toAlbumSummaryUiModel() }
            CreatePostUiState(
                isLoading = false,
                albums = albums,
                displayTimeMillis = defaultDisplayTime,
                selectedAlbumIds = albums.firstOrNull()?.id?.let(::listOf).orEmpty(),
                initialMediaItems = initialItems,
                selectedCoverSourceMediaId = defaultCoverId,
            )
        }
        is ApiResult.Error -> {
            CreatePostUiState(
                isLoading = false,
                errorMessage = result.toBackendUiMessage("读取大相册失败，当前无法创建小相册。"),
                displayTimeMillis = defaultDisplayTime,
                initialMediaItems = initialItems,
                selectedCoverSourceMediaId = defaultCoverId,
            )
        }
        ApiResult.Loading -> CreatePostUiState(
            isLoading = true,
            displayTimeMillis = defaultDisplayTime,
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
