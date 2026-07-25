package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshi.feature.sync.StaleBanner
import com.example.yingshi.feature.sync.SyncModule
import com.example.yingshi.feature.sync.SyncVersionTracker
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

object AlbumPageStateStore {
    var pendingSelectedAlbumId by mutableStateOf<String?>(null)
    var pendingUpdatedPostId by mutableStateOf<String?>(null)
}

private const val AlbumStatusMessageDismissDelayMillis = 2800L

@Composable
fun AlbumPageScreen(
    albums: List<AlbumSummaryUiModel>,
    posts: List<AlbumPostCardUiModel>,
    onOpenPost: (PostDetailPlaceholderRoute) -> Unit,
    onCreateLargeAlbum: () -> Unit,
    onCreateSmallAlbum: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    RealAlbumPageScreen(
        onOpenPost = onOpenPost,
        onCreateLargeAlbum = onCreateLargeAlbum,
        onCreateSmallAlbum = onCreateSmallAlbum,
        modifier = modifier,
    )
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
    // P1-3 诊断日志: 记录每次重组的触发原因
    android.util.Log.e("AlbumPageScreen", ">>> recompose: backendMutationVersion=${backendMutationEvent.version} affectsAlbums=${backendMutationEvent.affectsAlbums()} postsSize=${uiState.posts.size} isLoading=${uiState.isLoading}")
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
    // P1-3 根因修复: 不订阅整个 staleState, 改用 snapshotFlow 直接订阅 albumsStale 字段。
    // 之前订阅整个 staleState, lifeConsoleStale 变化 (life 操作 + LifeConsoleViewModel 重置)
    // 会触发 RealAlbumPageScreen 重组, 导致 LazyVerticalGrid 重新执行 (用户感知为"闪")。
    LaunchedEffect(Unit) {
        snapshotFlow { SyncVersionTracker.staleState.value.albumsStale }
            .distinctUntilChanged()
            .collect { currentlyStale ->
                if (currentlyStale) {
                    // 修复：等 refresh 完成后再 markRefreshed，避免 refresh 期间服务端版本又涨
                    // 导致下次 poll 又 stale=true，形成闪烁循环
                    viewModel.refreshAndMarkRefreshed()
                }
            }
    }
    ReconnectRefreshEffect(
        shouldRefresh = uiState.isOfflineReadOnly ||
            uiState.errorMessage != null ||
            uiState.postsErrorMessage != null ||
            uiState.isLoading ||
            uiState.isPostsLoading ||
            uiState.isPostsRefreshing,
        onReconnect = viewModel::refresh,
        onDisconnect = viewModel::handleConnectivityLost,
    )
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
    var showMoveDialog by remember { mutableStateOf<AlbumPostCardUiModel?>(null) }

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
    LaunchedEffect(uiState.statusMessage) {
        val targetMessage = uiState.statusMessage ?: return@LaunchedEffect
        delay(AlbumStatusMessageDismissDelayMillis)
        if (uiState.statusMessage == targetMessage) {
            viewModel.clearStatusMessage()
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
                    actionLabel = if (uiState.isOfflineReadOnly) "重试" else "新建大相册",
                    onAction = if (uiState.isOfflineReadOnly) {
                        viewModel::refresh
                    } else {
                        onCreateLargeAlbum
                    },
                )
            }

            else -> {
                StaleBanner(
                    module = SyncModule.ALBUMS,
                    onRefresh = {
                        viewModel.refresh()
                        SyncVersionTracker.markRefreshed(SyncModule.ALBUMS)
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                uiState.statusMessage?.let { statusMessage ->
                    BackendInlineNotice(
                        text = statusMessage,
                        emphasized = true,
                    )
                }
                uiState.errorMessage?.let { errorMessage ->
                    BackendInlineNotice(
                        text = errorMessage,
                        actionLabel = "重试",
                        onAction = viewModel::refresh,
                    )
                }
                AlbumSwitchSection(
                    albums = uiState.albums,
                    selectedAlbumId = uiState.selectedAlbumId.orEmpty(),
                    onSelectAlbum = viewModel::selectAlbum,
                    actionsEnabled = !uiState.isOfflineReadOnly,
                    isMutating = uiState.isAlbumMutating,
                    onCreateLargeAlbum = onCreateLargeAlbum,
                    onCreateSmallAlbum = { onCreateSmallAlbum(uiState.selectedAlbumId) },
                    onRenameAlbum = { album, payload ->
                        viewModel.renameAlbum(album.id, payload)
                    },
                    onDeleteAlbum = { album ->
                        viewModel.deleteAlbum(album.id)
                        SyncVersionTracker.markLocalMutation(SyncModule.ALBUMS)
                    },
                )

                when {
                    uiState.isPostsLoading && uiState.posts.isEmpty() -> {
                        AlbumPageLoadingCard(
                            text = "正在读取这个大相册里的小相册…",
                        )
                    }

                    uiState.postsErrorMessage != null && uiState.posts.isEmpty() -> {
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
                        AlbumEmptyStateCard(
                            selectedAlbumTitle = uiState.albums
                                .firstOrNull { album -> album.id == uiState.selectedAlbumId }
                                ?.title,
                            onCreateSmallAlbum = { onCreateSmallAlbum(uiState.selectedAlbumId) },
                            actionsEnabled = !uiState.isOfflineReadOnly,
                        )
                    }

                    else -> {
                        uiState.postsErrorMessage?.let { postsErrorMessage ->
                            BackendInlineNotice(
                                text = postsErrorMessage,
                                actionLabel = "重试",
                                onAction = {
                                    uiState.selectedAlbumId?.let(viewModel::selectAlbum)
                                },
                            )
                        }
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
                                        onLongPress = { showMoveDialog = post },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    showMoveDialog?.let { post ->
        MoveSmallAlbumTargetPickerDialog(
            smallAlbumTitle = post.title,
            albums = uiState.albums,
            currentAlbumId = uiState.selectedAlbumId.orEmpty(),
            isMutating = uiState.isAlbumMutating,
            onDismiss = { showMoveDialog = null },
            onSelectTarget = { targetAlbumId ->
                showMoveDialog = null
                viewModel.moveSmallAlbums(
                    targetAlbumId = targetAlbumId,
                    smallAlbumIds = listOf(post.id),
                )
                SyncVersionTracker.markLocalMutation(SyncModule.ALBUMS)
            },
        )
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
        color = colors.raisedSurface.copy(alpha = 0.40f),
        border = BorderStroke(
            width = 1.dp,
            color = colors.glassStroke.copy(alpha = 0.50f),
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
        color = colors.raisedSurface.copy(alpha = 0.40f),
        border = BorderStroke(
            width = 1.dp,
            color = colors.glassStroke.copy(alpha = 0.50f),
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
private fun AlbumEmptyStateCard(
    selectedAlbumTitle: String?,
    onCreateSmallAlbum: () -> Unit,
    actionsEnabled: Boolean = true,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = colors.raisedSurface.copy(alpha = 0.40f),
        border = BorderStroke(
            width = 1.dp,
            color = colors.glassStroke.copy(alpha = 0.50f),
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
            AlbumInlineActionButton(
                text = "新建小相册",
                enabled = actionsEnabled,
                onClick = onCreateSmallAlbum,
            )
        }
    }
}

private fun cardSpacing(density: AlbumGridDensity) = when (density) {
    AlbumGridDensity.COZY_2 -> 8.dp
    AlbumGridDensity.COZY_3 -> 6.dp
    AlbumGridDensity.COZY_4 -> 5.dp
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
