package com.example.yingshi.feature.photos

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
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
    var selectedCoverSourceMediaId by rememberSaveable(route.source, mediaKey) { mutableStateOf<String?>(null) }
    var displayTimeMillis by rememberSaveable(route.source, mediaKey) { mutableStateOf(System.currentTimeMillis()) }
    var localMessage by rememberSaveable(route.source, mediaKey) { mutableStateOf<String?>(null) }
    val spacing = YingShiThemeTokens.spacing

    LaunchedEffect(seedState) {
        if (initialized || seedState.isLoading) return@LaunchedEffect
        title = seedState.title
        summary = seedState.summary
        selectedAlbumIds = seedState.selectedAlbumIds
        selectedCoverSourceMediaId = seedState.selectedCoverSourceMediaId
        displayTimeMillis = seedState.displayTimeMillis
        initialized = true
    }

    fun toggleAlbum(albumId: String) {
        selectedAlbumIds = if (selectedAlbumIds.contains(albumId)) {
            selectedAlbumIds.filterNot { it == albumId }
        } else {
            selectedAlbumIds + albumId
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
            coverSourceMediaId = selectedCoverSourceMediaId,
            locationLabel = null,
        )
        if (route.initialMediaItems.isNotEmpty()) {
            if (mode == RepositoryMode.REAL) {
                val queuedCount = LocalSystemMediaBridgeRepository.enqueueCreatePostUpload(
                    context = context,
                    mediaItems = route.initialMediaItems,
                    draft = draft,
                )
                if (queuedCount > 0) {
                    Toast.makeText(context, "已加入上传队列，完成后会创建新帖子。", Toast.LENGTH_SHORT).show()
                    onSubmittedToBackground()
                } else {
                    localMessage = "当前没有可处理的媒体。"
                }
            } else {
                val createdPost = LocalSystemMediaBridgeRepository.createPostFromSystemMediaDraft(
                    draft = draft,
                    mediaItems = route.initialMediaItems,
                )
                if (createdPost == null) {
                    localMessage = "本地新帖子创建失败，请稍后重试。"
                } else {
                    onCreated(FakeAlbumRepository.toPostDetailRoute(createdPost))
                }
            }
            return
        }

        if (route.initialAppMediaIds.isNotEmpty()) {
            if (mode == RepositoryMode.REAL) {
                scope.launch {
                    isSubmitting = true
                    val result = RepositoryProvider.postRepository.createPost(
                        CreatePostPayload(
                            title = draft.title.ifBlank { "新帖子" },
                            summary = draft.summary,
                            displayTimeMillis = draft.displayTimeMillis,
                            albumIds = draft.albumIds,
                            initialMediaIds = route.initialAppMediaIds,
                            coverMediaId = route.initialAppMediaIds.firstOrNull(),
                        ),
                    )
                    isSubmitting = false
                    when (result) {
                        is ApiResult.Success -> {
                            notifyRealBackendPostChanged(postIds = setOf(result.data.postId))
                            onCreated(
                                result.data.toPostDetailPlaceholderRoute(
                                    selectedAlbumId = selectedAlbumIds.first(),
                                ),
                            )
                        }
                        is ApiResult.Error -> {
                            localMessage = result.toBackendUiMessage("创建帖子失败，请稍后重试。")
                        }
                        ApiResult.Loading -> Unit
                    }
                }
            } else {
                val selectedItems = route.initialAppMediaIds
                    .mapNotNull(FakePhotoFeedRepository::findPhotoFeedItem)
                val createdPost = FakeAlbumRepository.createConfiguredLocalPostFromPhotoFeedItems(
                    draft = draft,
                    mediaItems = selectedItems,
                )
                if (createdPost == null) {
                    localMessage = "本地新帖子创建失败，请稍后重试。"
                } else {
                    onCreated(FakeAlbumRepository.toPostDetailRoute(createdPost))
                }
            }
            return
        }

        scope.launch {
            isSubmitting = true
            val result = RepositoryProvider.postRepository.createPost(
                CreatePostPayload(
                    title = draft.title.ifBlank { "新帖子" },
                    summary = draft.summary,
                    displayTimeMillis = draft.displayTimeMillis,
                    albumIds = draft.albumIds,
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
                    localMessage = result.toBackendUiMessage("创建帖子失败，请稍后重试。")
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            CreatePostTopBar(
                hasInitialMedia = route.initialMediaItems.isNotEmpty(),
                onBack = onBack,
            )

            when {
                seedState.isLoading -> {
                    BackendLoadingCard(
                        text = "正在准备新增帖子表单…",
                        fillWidth = true,
                    )
                }
                seedState.tokenMissing -> {
                    BackendNoticeCard(
                        title = "需要先登录",
                        text = "REAL 模式下新增帖子前需要先登录，当前无法读取后端相册。",
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

                    CreatePostSection(title = "标题") {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isSubmitting,
                            placeholder = { Text("输入帖子标题") },
                        )
                    }

                    CreatePostSection(title = "描述") {
                        OutlinedTextField(
                            value = summary,
                            onValueChange = { summary = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            enabled = !isSubmitting,
                            placeholder = { Text("补充这条帖子的说明") },
                        )
                    }

                    CreatePostSection(title = "相册归属") {
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

                    CreatePostSection(title = "时间") {
                        BackendInlineNotice(
                            text = formatCreatePostTime(displayTimeMillis),
                        )
                    }

                    CreatePostSection(title = "地点") {
                        BackendInlineNotice(
                            text = "本轮沿用现有能力，地点暂未接入独立编辑。",
                        )
                    }

                    if (route.initialMediaItems.isNotEmpty()) {
                        CreatePostSection(title = "初始媒体") {
                            Text(
                                text = "已选 ${route.initialMediaItems.size} 项，点击任一缩略图可设为封面。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                route.initialMediaItems.forEach { item ->
                                    SelectableCoverMediaCard(
                                        item = item,
                                        selected = item.id == selectedCoverSourceMediaId,
                                        onClick = { selectedCoverSourceMediaId = item.id },
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = onBack,
                            enabled = !isSubmitting,
                        ) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = ::submitDraft,
                            enabled = !isSubmitting && !seedState.tokenMissing,
                        ) {
                            Text(
                                if (isSubmitting) {
                                    "提交中…"
                                } else if (route.initialMediaItems.isNotEmpty()) {
                                    "开始创建"
                                } else {
                                    "创建帖子"
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatePostTopBar(
    hasInitialMedia: Boolean,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
            onClick = onBack,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "<",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "新增帖子",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (hasInitialMedia) {
                    "这次会先上传媒体，再创建新帖子。"
                } else {
                    "先创建帖子基础信息，后续可再补媒体。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreatePostSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

@Composable
private fun SelectableAlbumChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(YingShiThemeTokens.radius.capsule)),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
            },
        ),
        onClick = onClick,
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectableCoverMediaCard(
    item: SystemMediaItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val videoThumbnail = if (item.type == SystemMediaType.VIDEO) {
        rememberSystemVideoThumbnail(context, item.uri)
    } else {
        null
    }
    Column(
        modifier = Modifier.width(92.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(RoundedCornerShape(YingShiThemeTokens.radius.lg))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.TopEnd,
        ) {
            if (videoThumbnail != null) {
                Image(
                    bitmap = videoThumbnail.toComposeBitmap(),
                    contentDescription = item.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.uri)
                        .size(320)
                        .precision(Precision.INEXACT)
                        .crossfade(false)
                        .build(),
                    contentDescription = item.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            if (selected) {
                Surface(
                    modifier = Modifier.padding(6.dp),
                    shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                    color = MaterialTheme.colorScheme.primary,
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
        Text(
            text = item.displayName.ifBlank { item.id },
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                errorMessage = result.toBackendUiMessage("读取相册失败，暂时无法创建帖子。"),
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

private fun formatCreatePostTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}
