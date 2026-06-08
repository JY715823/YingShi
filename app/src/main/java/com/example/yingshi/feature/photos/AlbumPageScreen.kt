package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import com.example.yingshi.ui.components.yingShiClickable
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AlbumPageStateStore {
    var pendingSelectedAlbumId by mutableStateOf<String?>(null)
    var pendingUpdatedPostId by mutableStateOf<String?>(null)
}

@Composable
fun AlbumPageScreen(
    albums: List<AlbumSummaryUiModel>,
    posts: List<AlbumPostCardUiModel>,
    onOpenPost: (PostDetailPlaceholderRoute) -> Unit,
    onCreateLargeAlbum: () -> Unit,
    onCreateSmallAlbum: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
        RealAlbumPageScreen(
            onOpenPost = onOpenPost,
            onCreateLargeAlbum = onCreateLargeAlbum,
            onCreateSmallAlbum = onCreateSmallAlbum,
            modifier = modifier,
        )
        return
    }

    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val settingsState = SettingsRepository.getSettingsState()
    var selectedAlbumId by rememberSaveable(albums) {
        mutableStateOf(albums.firstOrNull()?.id.orEmpty())
    }
    val pendingSelectedAlbumId = AlbumPageStateStore.pendingSelectedAlbumId
    var densityName by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    LaunchedEffect(Unit) {
        if (densityName == null) {
            densityName = settingsState.defaultAlbumGridDensity.name
        }
    }
    LaunchedEffect(pendingSelectedAlbumId, albums) {
        val targetAlbumId = pendingSelectedAlbumId ?: return@LaunchedEffect
        if (albums.any { it.id == targetAlbumId }) {
            selectedAlbumId = targetAlbumId
            AlbumPageStateStore.pendingSelectedAlbumId = null
        }
    }
    val gridDensity = AlbumGridDensity.valueOf(
        densityName ?: settingsState.defaultAlbumGridDensity.name,
    )
    val gridState = rememberLazyGridState()
    val filteredPosts = posts.filter { it.albumId == selectedAlbumId }
    val pendingUpdatedPostId = AlbumPageStateStore.pendingUpdatedPostId
    var recentlyUpdatedPostId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingUpdatedPostId, filteredPosts) {
        val targetPostId = pendingUpdatedPostId ?: return@LaunchedEffect
        val targetIndex = filteredPosts.indexOfFirst { it.id == targetPostId }
        if (targetIndex >= 0) {
            recentlyUpdatedPostId = targetPostId
            AlbumPageStateStore.pendingUpdatedPostId = null
            gridState.scrollToItem(targetIndex)
        }
    }
    LaunchedEffect(recentlyUpdatedPostId) {
        val targetPostId = recentlyUpdatedPostId ?: return@LaunchedEffect
        delay(4500)
        if (recentlyUpdatedPostId == targetPostId) {
            recentlyUpdatedPostId = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AlbumSwitchSection(
            albums = albums,
            selectedAlbumId = selectedAlbumId,
            onSelectAlbum = { selectedAlbumId = it },
            onCreateLargeAlbum = onCreateLargeAlbum,
            onCreateSmallAlbum = { onCreateSmallAlbum(selectedAlbumId.ifBlank { null }) },
        )

        if (filteredPosts.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
                color = colors.sectionBackground.copy(alpha = 0.58f),
                border = BorderStroke(
                    width = 1.dp,
                    color = colors.dividerSoft.copy(alpha = 0.62f),
                ),
            ) {
                Text(
                    text = "这个大相册里还没有小相册。",
                    modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.lg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = YingShiThemeTokens.colors.textSecondary,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .discreteZoomLevelGesture(
                        enabled = true,
                        levels = AlbumGridDensity.entries.toList(),
                        currentLevel = gridDensity,
                        onLevelChange = { densityName = it.name },
                    ),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridDensity.columns),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(cardSpacing(gridDensity)),
                    verticalArrangement = Arrangement.spacedBy(cardSpacing(gridDensity)),
                    contentPadding = PaddingValues(bottom = spacing.lg),
                ) {
                    items(
                        items = filteredPosts,
                        key = { it.id },
                        contentType = { "album-post-${gridDensity.columns}" },
                    ) { post ->
                        AlbumPostCard(
                            post = post,
                            density = gridDensity,
                            isRecentlyUpdated = post.id == recentlyUpdatedPostId,
                            onClick = { onOpenPost(FakeAlbumRepository.toPostDetailRoute(post)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RealAlbumPageScreen(
    onOpenPost: (PostDetailPlaceholderRoute) -> Unit,
    onCreateLargeAlbum: () -> Unit,
    onCreateSmallAlbum: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessionKey = realBackendSessionKey("real-album-page")
    val viewModel: AlbumPageRealViewModel = viewModel(
        key = sessionKey,
        factory = AlbumPageRealViewModel.factory(),
    )
    val uiState by viewModel.uiState.collectAsState()
    val backendMutationEvent by RealBackendMutationBus.latestEvent.collectAsState()
    val pendingSelectedAlbumId = AlbumPageStateStore.pendingSelectedAlbumId
    val pendingUpdatedPostId = AlbumPageStateStore.pendingUpdatedPostId
    val spacing = YingShiThemeTokens.spacing
    val settingsState = SettingsRepository.getSettingsState()
    var densityName by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    LaunchedEffect(Unit) {
        if (densityName == null) {
            densityName = settingsState.defaultAlbumGridDensity.name
        }
    }
    LaunchedEffect(backendMutationEvent.version) {
        if (backendMutationEvent.version > 0 && backendMutationEvent.affectsAlbums()) {
            viewModel.refresh()
        }
    }
    LaunchedEffect(pendingSelectedAlbumId, uiState.albums) {
        val targetAlbumId = pendingSelectedAlbumId ?: return@LaunchedEffect
        if (uiState.albums.any { it.id == targetAlbumId }) {
            viewModel.selectAlbum(targetAlbumId)
            AlbumPageStateStore.pendingSelectedAlbumId = null
        }
    }
    val gridDensity = AlbumGridDensity.valueOf(
        densityName ?: settingsState.defaultAlbumGridDensity.name,
    )
    val gridState = rememberLazyGridState()
    var recentlyUpdatedPostId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingUpdatedPostId, uiState.posts) {
        val targetPostId = pendingUpdatedPostId ?: return@LaunchedEffect
        val targetIndex = uiState.posts.indexOfFirst { it.id == targetPostId }
        if (targetIndex >= 0) {
            recentlyUpdatedPostId = targetPostId
            AlbumPageStateStore.pendingUpdatedPostId = null
            gridState.scrollToItem(targetIndex)
        }
    }
    LaunchedEffect(recentlyUpdatedPostId) {
        val targetPostId = recentlyUpdatedPostId ?: return@LaunchedEffect
        delay(4500)
        if (recentlyUpdatedPostId == targetPostId) {
            recentlyUpdatedPostId = null
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            uiState.tokenMissing -> {
                AlbumPageNoticeCard(
                    text = uiState.errorMessage
                        ?: "请先在连接设置完成登录。",
                    actionLabel = "重试",
                    onAction = viewModel::refresh,
                )
            }

            uiState.isLoading && uiState.albums.isEmpty() -> {
                AlbumPageLoadingCard()
            }

            uiState.errorMessage != null && uiState.albums.isEmpty() -> {
                val errorMessage = uiState.errorMessage ?: "读取相册失败。"
                AlbumPageNoticeCard(
                    text = errorMessage,
                    actionLabel = "重试",
                    onAction = viewModel::refresh,
                )
            }

            uiState.albums.isEmpty() -> {
                AlbumPageNoticeCard(
                    text = "当前账号还没有可用相册。",
                    actionLabel = "重试",
                    onAction = viewModel::refresh,
                )
            }

            else -> {
                AlbumSwitchSection(
                    albums = uiState.albums,
                    selectedAlbumId = uiState.selectedAlbumId.orEmpty(),
                    onSelectAlbum = viewModel::selectAlbum,
                    onCreateLargeAlbum = onCreateLargeAlbum,
                    onCreateSmallAlbum = { onCreateSmallAlbum(uiState.selectedAlbumId) },
                )

                when {
                    uiState.isPostsLoading -> {
                        AlbumPageLoadingCard(
                            text = "正在读取这个大相册里的小相册…",
                        )
                    }

                    uiState.postsErrorMessage != null -> {
                        val postsErrorMessage = uiState.postsErrorMessage
                            ?: "读取这个大相册下的小相册失败。"
                        AlbumPageNoticeCard(
                            text = postsErrorMessage,
                            actionLabel = "重试",
                            onAction = {
                                uiState.selectedAlbumId?.let(viewModel::selectAlbum)
                            },
                        )
                    }

                    uiState.posts.isEmpty() -> {
                        AlbumPageNoticeCard(
                            text = "这个大相册里还没有小相册。",
                        )
                    }

                    else -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .discreteZoomLevelGesture(
                                    enabled = true,
                                    levels = AlbumGridDensity.entries.toList(),
                                    currentLevel = gridDensity,
                                    onLevelChange = { densityName = it.name },
                                ),
                        ) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(gridDensity.columns),
                                state = gridState,
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(cardSpacing(gridDensity)),
                                verticalArrangement = Arrangement.spacedBy(cardSpacing(gridDensity)),
                                contentPadding = PaddingValues(bottom = spacing.lg),
                            ) {
                                items(
                                    items = uiState.posts,
                                    key = { it.id },
                                    contentType = { "album-post-${gridDensity.columns}" },
                                ) { post ->
                                    AlbumPostCard(
                                        post = post,
                                        density = gridDensity,
                                        isRecentlyUpdated = post.id == recentlyUpdatedPostId,
                                        onClick = {
                                            val selectedAlbumId = uiState.selectedAlbumId ?: post.albumId
                                            onOpenPost(
                                                PostDetailPlaceholderRoute(
                                                    postId = post.id,
                                                    albumId = selectedAlbumId,
                                                    albumIds = post.albumIds,
                                                    title = post.title,
                                                    summary = post.summary,
                                                    postDisplayTimeMillis = post.postDisplayTimeMillis,
                                                    mediaCount = post.mediaCount,
                                                    coverPalette = post.coverPalette,
                                                    coverMediaType = post.coverMediaType,
                                                    coverAspectRatio = post.coverAspectRatio,
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumPageLoadingCard(
    text: String = "正在读取相册…",
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = colors.sectionBackground.copy(alpha = 0.58f),
        border = BorderStroke(
            width = 1.dp,
            color = colors.dividerSoft.copy(alpha = 0.62f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = colors.primaryAction,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun AlbumPageNoticeCard(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = colors.sectionBackground.copy(alpha = 0.58f),
        border = BorderStroke(
            width = 1.dp,
            color = colors.dividerSoft.copy(alpha = 0.62f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            if (actionLabel != null && onAction != null) {
                AlbumInlineActionButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

@Composable
internal fun AlbumIconAction(
    text: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    contentDescription: String,
    containerColor: Color? = null,
    contentColor: Color? = null,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val resolvedContentColor = contentColor ?: colors.titleAccent
    Surface(
        modifier = Modifier
            .size(46.dp)
            .yingShiClickable(shape = CircleShape, pressedScale = 0.94f, onClick = onClick),
        shape = CircleShape,
        color = containerColor ?: colors.sectionBackground.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.56f)),
        shadowElevation = 1.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = resolvedContentColor,
                    modifier = Modifier.size(25.dp),
                )
            } else {
                Text(
                    text = text.orEmpty(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = resolvedContentColor,
                )
            }
        }
    }
}

@Composable
private fun AlbumEmptyStateCard(
    selectedAlbumTitle: String?,
    onCreateSmallAlbum: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = colors.sectionBackground.copy(alpha = 0.62f),
        border = BorderStroke(
            width = 1.dp,
            color = colors.dividerSoft.copy(alpha = 0.62f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = if (selectedAlbumTitle.isNullOrBlank()) {
                    "先选一个大相册"
                } else {
                    "这个大相册里还没有小相册。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            AlbumInlineActionButton(text = "新建小相册", onClick = onCreateSmallAlbum)
        }
    }
}

@Composable
private fun AlbumSwitchSection(
    albums: List<AlbumSummaryUiModel>,
    selectedAlbumId: String,
    onSelectAlbum: (String) -> Unit,
    onCreateLargeAlbum: () -> Unit,
    onCreateSmallAlbum: () -> Unit,
) {
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    var showAlbumMenu by rememberSaveable { mutableStateOf(false) }
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
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                visibleAlbums.forEach { album ->
                    AlbumSwitchChip(
                        album = album,
                        selected = album.id == selectedAlbumId,
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 0.dp),
                        onClick = { onSelectAlbum(album.id) },
                    )
                }
                repeat((3 - visibleAlbums.size).coerceAtLeast(0)) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            AlbumIconAction(
                icon = Icons.Rounded.Add,
                contentDescription = "新建小相册",
                containerColor = colors.softGreenContainer.copy(alpha = 0.92f),
                contentColor = colors.softGreenAction,
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
            onCreateLargeAlbum = {
                showAlbumMenu = false
                onCreateLargeAlbum()
            },
            onSelectAlbum = { albumId ->
                showAlbumMenu = false
                onSelectAlbum(albumId)
            },
        )
    }
}

@Composable
internal fun AlbumDirectoryDialog(
    albums: List<AlbumSummaryUiModel>,
    selectedAlbumId: String,
    onDismiss: () -> Unit,
    onCreateLargeAlbum: (() -> Unit)? = null,
    onSelectAlbum: (String) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    var query by rememberSaveable { mutableStateOf("") }
    val filteredAlbums = remember(albums, query) {
        val keyword = query.trim()
        if (keyword.isBlank()) {
            albums
        } else {
            albums.filter { album ->
                album.title.contains(keyword, ignoreCase = true) ||
                    album.subtitle.contains(keyword, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp),
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
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 330.dp),
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
private fun AlbumSwitchChip(
    album: AlbumSummaryUiModel,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(16.dp)
    val title = remember(album.title) {
        album.title.trim().ifBlank { "未命名相册" }
    }

    Surface(
        modifier = modifier
            .yingShiClickable(shape = shape, onClick = onClick),
        shape = shape,
        color = if (selected) {
            colors.selectedPillBg.copy(alpha = 0.68f)
        } else {
            colors.raisedSurface.copy(alpha = 0.96f)
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) colors.glassStroke.copy(alpha = 0.86f) else colors.dividerSoft.copy(alpha = 0.50f),
        ),
        shadowElevation = 0.dp,
    ) {
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
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = if (selected) colors.titleAccent else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AlbumInlineActionButton(
    text: String,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    Surface(
        modifier = Modifier.yingShiClickable(shape = shape, pressedScale = 0.96f, onClick = onClick),
        shape = shape,
        color = colors.softGreenContainer.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.softGreenAction,
        )
    }
}

@Composable
private fun AlbumGridDensitySwitcher(
    selectedDensity: AlbumGridDensity,
    onDensitySelected: (AlbumGridDensity) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        shape = RoundedCornerShape(radius.md),
        color = colors.sectionBackground.copy(alpha = 0.52f),
        border = BorderStroke(
            width = 1.dp,
            color = colors.dividerSoft.copy(alpha = 0.62f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.sm, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            AlbumGridDensity.entries.forEach { density ->
                val selected = density == selectedDensity
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(radius.capsule))
                        .clickable { onDensitySelected(density) },
                    shape = RoundedCornerShape(radius.capsule),
                    color = if (selected) {
                        colors.primaryContainer.copy(alpha = 0.70f)
                    } else {
                        Color.Transparent
                    },
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = density.label,
                            style = if (selected) {
                                MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                            } else {
                                MaterialTheme.typography.labelMedium
                            },
                            color = if (selected) {
                                colors.titleAccent
                            } else {
                                colors.textSecondary
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumPostCard(
    post: AlbumPostCardUiModel,
    density: AlbumGridDensity,
    isRecentlyUpdated: Boolean = false,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val innerPadding = when (density) {
        AlbumGridDensity.COZY_2 -> spacing.md
        AlbumGridDensity.COZY_3 -> spacing.sm
        AlbumGridDensity.COZY_4 -> spacing.xs
    }
    val summaryMaxLines = if (density == AlbumGridDensity.COZY_2) 2 else 1
    val summary = post.summary.meaningfulPostSummaryOrNull()
    val mediaCountLabel = when (post.mediaCount) {
        0 -> "暂无媒体"
        1 -> "1 张"
        else -> "${post.mediaCount} 张"
    }
    val titleStyle = if (density == AlbumGridDensity.COZY_4) {
        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    } else {
        MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
    }
    val coverAspectRatio = when (density) {
        AlbumGridDensity.COZY_2 -> 1.26f
        AlbumGridDensity.COZY_3 -> 1.18f
        AlbumGridDensity.COZY_4 -> 1.08f
    }
    val previewMedia = remember(post) {
        val resolved = if (post.previewMedia.size >= 2 || RepositoryProvider.currentMode == RepositoryMode.REAL) {
            post.previewMedia
        } else {
            FakeAlbumRepository.getPostDetail(FakeAlbumRepository.toPostDetailRoute(post))
                .mediaItems
                .map(PostDetailMediaUiModel::toAlbumPostPreviewMediaUiModel)
        }
        resolved
            .ifEmpty {
                listOf(
                    AlbumPostPreviewMediaUiModel(
                        id = "${post.id}-cover",
                        palette = post.coverPalette,
                        mediaType = post.coverMediaType,
                        aspectRatio = post.coverAspectRatio,
                        mediaSource = post.coverMediaSource,
                    ),
                )
            }
            .distinctBy { it.id }
            .take(2)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .yingShiClickable(shape = RoundedCornerShape(radius.lg), onClick = onClick),
        shape = RoundedCornerShape(radius.lg),
        color = colors.raisedSurface.copy(alpha = 0.96f),
        border = BorderStroke(
            width = if (isRecentlyUpdated) 1.5.dp else 1.dp,
            color = if (isRecentlyUpdated) {
                colors.memoryAccent.copy(alpha = 0.28f)
            } else {
                colors.dividerSoft.copy(alpha = 0.54f)
            },
        ),
        shadowElevation = 1.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(coverAspectRatio)
            ) {
                if (previewMedia.size >= 2) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        AlbumPostPreviewThumbnail(
                            media = previewMedia[0],
                            contentDescription = post.title,
                            modifier = Modifier
                                .weight(1.45f)
                                .fillMaxSize(),
                        )
                        AlbumPostPreviewThumbnail(
                            media = previewMedia[1],
                            contentDescription = post.title,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                        )
                    }
                } else {
                    AlbumPostPreviewThumbnail(
                        media = previewMedia.first(),
                        contentDescription = post.title,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                YingShiMediaFrame(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(topStart = radius.lg, topEnd = radius.lg),
                    memoryActive = isRecentlyUpdated,
                    topScrimAlpha = 0.12f,
                    bottomGlowAlpha = 0.20f,
                )

                if (previewMedia.firstOrNull()?.mediaType == AppMediaType.VIDEO) {
                    VideoMediaMarker(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = innerPadding, start = innerPadding),
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    colors.viewerBackground.copy(alpha = 0.52f),
                                ),
                            ),
                        )
                        .padding(horizontal = innerPadding, vertical = innerPadding),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        Text(
                            text = post.title,
                            style = titleStyle,
                            color = colors.viewerText.copy(alpha = 0.96f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formatAlbumPostTime(post.postDisplayTimeMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.viewerText.copy(alpha = 0.78f),
                                maxLines = 1,
                            )
                            Text(
                                text = mediaCountLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.glowWash,
                                maxLines = 1,
                            )
                        }
                    }
                }

                if (isRecentlyUpdated) {
                    YingShiMemoryBadge(
                        text = "刚更新",
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = innerPadding,
                                top = if (previewMedia.firstOrNull()?.mediaType == AppMediaType.VIDEO) {
                                    innerPadding + 28.dp
                                } else {
                                    innerPadding
                                },
                            ),
                        compact = density == AlbumGridDensity.COZY_4,
                    )
                }
            }

            if (summary != null) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = innerPadding,
                        vertical = if (density == AlbumGridDensity.COZY_4) spacing.xs else innerPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (density == AlbumGridDensity.COZY_4) 2.dp else spacing.xxs),
                ) {
                    Text(
                        text = "简介",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.goldAccent.copy(alpha = 0.86f),
                        maxLines = 1,
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.textSecondary,
                        maxLines = summaryMaxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumPostPreviewThumbnail(
    media: AlbumPostPreviewMediaUiModel,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    AppContentMediaThumbnail(
        mediaSource = media.mediaSource,
        mediaType = media.mediaType,
        palette = media.palette,
        modifier = modifier,
        contentDescription = contentDescription,
        requestSize = 384,
        showLoadingIndicator = false,
    )
}

@Composable
fun PostDetailPlaceholderScreen(
    route: PostDetailPlaceholderRoute,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                shape = CircleShape,
                color = colors.sectionBackground.copy(alpha = 0.80f),
                border = BorderStroke(
                    width = 1.dp,
                    color = colors.dividerSoft.copy(alpha = 0.72f),
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "<",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.titleAccent,
                    )
                }
            }

            Text(
                text = "小相册详情",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(radius.xl),
            color = colors.raisedSurface.copy(alpha = 0.96f),
            border = BorderStroke(
                width = 1.dp,
                color = colors.dividerSoft.copy(alpha = 0.62f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(route.coverAspectRatio.coerceIn(0.94f, 1.10f))
                        .clip(RoundedCornerShape(radius.lg))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(route.coverPalette.start, route.coverPalette.end),
                            ),
                        ),
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Text(
                        text = route.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.titleAccent,
                    )
                    Text(
                        text = route.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DetailMetaCapsule(text = formatAlbumPostTime(route.postDisplayTimeMillis))
                    DetailMetaCapsule(text = "${route.mediaCount} 张媒体")
                }

            }
        }
    }
}

@Composable
private fun DetailMetaCapsule(text: String) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        shape = RoundedCornerShape(radius.capsule),
        color = colors.sectionBackground.copy(alpha = 0.70f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.62f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
    }
}

private fun cardSpacing(density: AlbumGridDensity) = when (density) {
    AlbumGridDensity.COZY_2 -> 8.dp
    AlbumGridDensity.COZY_3 -> 6.dp
    AlbumGridDensity.COZY_4 -> 5.dp
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

private fun formatAlbumPostTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(timeMillis))
}

@Preview(showBackground = true)
@Composable
private fun AlbumPageScreenPreview() {
    YingShiTheme {
        AlbumPageScreen(
            albums = FakeAlbumRepository.getAlbums(),
            posts = FakeAlbumRepository.getPosts(),
            onOpenPost = { },
            onCreateLargeAlbum = { },
            onCreateSmallAlbum = { },
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PostDetailPlaceholderScreenPreview() {
    YingShiTheme {
        PostDetailPlaceholderScreen(
            route = FakeAlbumRepository.toPostDetailRoute(FakeAlbumRepository.getPosts().first()),
            onBack = { },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
