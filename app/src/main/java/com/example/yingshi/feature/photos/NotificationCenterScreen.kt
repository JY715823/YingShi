package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.cache.AppReadCacheStore
import com.example.yingshi.data.cache.OfflineAccessManager
import com.example.yingshi.data.cache.OfflineReadOnlyDefaultMessage
import com.example.yingshi.data.model.RemoteNotification
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.model.RemotePostDetail
import com.example.yingshi.data.model.RemotePostMedia
import com.example.yingshi.data.model.RemoteTrashItem
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.components.YingShiNotice
import com.example.yingshi.ui.components.YingShiNoticeHost
import com.example.yingshi.ui.components.YingShiNoticeTone
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class NotificationCenterUiState(
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val isOfflineReadOnly: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val notifications: List<NotificationCenterItemUiModel> = emptyList(),
)

private data class NotificationResolvedPresentation(
    val title: String,
    val body: String,
    val targetSummary: String,
    val visual: NotificationVisual = NotificationVisual.None,
)

private sealed interface NotificationVisual {
    data object None : NotificationVisual

    data class Media(
        val mediaSource: AppContentMediaSource?,
        val mediaType: AppMediaType,
        val palette: PhotoThumbnailPalette,
    ) : NotificationVisual

    data class SmallAlbum(
        val title: String,
        val metaLabel: String,
        val palette: PhotoThumbnailPalette,
        val previewMedia: List<AlbumPostPreviewMediaUiModel>,
    ) : NotificationVisual
}

private object NotificationCenterMemoryCache {
    private val uiStates = mutableMapOf<String, NotificationCenterUiState>()
    private val presentations = mutableMapOf<String, NotificationResolvedPresentation>()

    fun readUiState(sessionKey: String): NotificationCenterUiState? = uiStates[sessionKey]

    fun hasUiState(sessionKey: String): Boolean = uiStates.containsKey(sessionKey)

    fun writeUiState(sessionKey: String, state: NotificationCenterUiState) {
        uiStates[sessionKey] = state
    }

    fun readPresentation(notificationId: String): NotificationResolvedPresentation? = presentations[notificationId]

    fun writePresentation(
        notificationId: String,
        presentation: NotificationResolvedPresentation,
    ) {
        presentations[notificationId] = presentation
    }
}

@Composable
fun NotificationCenterScreen(
    route: NotificationCenterRoute,
    onBack: () -> Unit,
    onOpenNotificationDetail: (NotificationDetailRoute) -> Unit,
    onOpenNotificationTarget: (NotificationCenterItemUiModel) -> Unit = { item ->
        onOpenNotificationDetail(
            NotificationDetailRoute(
                notificationId = item.id,
                source = "notification-center",
            ),
        )
    },
    onRouteSnapshotChange: (NotificationCenterRoute) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val coroutineScope = rememberCoroutineScope()
    val sessionKey = realBackendSessionKey("notification-center-${route.source}")
    val currentUserId = AuthSessionManager.getCurrentUserSnapshot()?.userId
    var selectedFilterName by remember(route.source) { mutableStateOf(route.selectedFilterName) }
    var selectedCategoryName by remember(route.source) { mutableStateOf(route.selectedCategoryName) }
    fun cachedNotificationsUiState(
        cachedList: List<RemoteNotification>,
        isOfflineReadOnly: Boolean = false,
        statusMessage: String? = null,
    ): NotificationCenterUiState {
        return NotificationCenterUiState(
            isLoading = false,
            isOfflineReadOnly = isOfflineReadOnly,
            statusMessage = statusMessage,
            notifications = mergeNotificationStreams(
                remoteNotifications = cachedList.map(RemoteNotification::toNotificationCenterItemUiModel),
                localNotifications = NotificationCenterLocalStore.getNotifications(),
            ),
        )
    }
    var uiState by remember(sessionKey) {
        mutableStateOf(
            NotificationCenterMemoryCache.readUiState(sessionKey)
                ?: NotificationCenterUiState(isLoading = true),
        )
    }
    var notice by remember { mutableStateOf<YingShiNotice?>(null) }
    var noticeNonce by remember { mutableIntStateOf(0) }
    var showClearListConfirm by remember(route.source) { mutableStateOf(false) }
    var refreshJob by remember(sessionKey) { mutableStateOf<Job?>(null) }
    var refreshVersion by remember(sessionKey) { mutableIntStateOf(0) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = route.firstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = route.firstVisibleItemScrollOffset,
    )
    val selectedFilter = NotificationCenterFilter.valueOf(selectedFilterName)
    val availableCategories = notificationCategoriesFor(selectedFilter)
    val selectedCategory = availableCategories.firstOrNull { it.name == selectedCategoryName }
        ?: NotificationCategoryFilter.ALL
    val filteredNotifications = uiState.notifications
        .filterBy(selectedFilter)
        .filterBy(selectedFilter, selectedCategory)
    val unreadCount = filteredNotifications.count { !it.isRead }
    val totalCount = filteredNotifications.size

    fun showNotice(
        message: String,
        tone: YingShiNoticeTone = YingShiNoticeTone.INFO,
    ) {
        noticeNonce += 1
        notice = YingShiNotice(message = message, tone = tone, nonce = noticeNonce)
    }

    fun updateUiState(nextState: NotificationCenterUiState) {
        uiState = nextState
        if (!nextState.isLoading || nextState.notifications.isNotEmpty() || nextState.errorMessage != null) {
            NotificationCenterMemoryCache.writeUiState(sessionKey, nextState)
        }
    }

    LaunchedEffect(sessionKey, currentUserId) {
        if (currentUserId == null || NotificationCenterMemoryCache.hasUiState(sessionKey)) return@LaunchedEffect
        val cachedRemoteNotifications = withContext(Dispatchers.IO) {
            AppReadCacheStore.readNotifications(currentUserId)?.payload?.items
        }.orEmpty()
        if (cachedRemoteNotifications.isEmpty()) return@LaunchedEffect
        if (NotificationCenterMemoryCache.hasUiState(sessionKey)) return@LaunchedEffect
        if (uiState.notifications.isNotEmpty() || uiState.errorMessage != null) return@LaunchedEffect
        updateUiState(
            cachedNotificationsUiState(
                cachedList = cachedRemoteNotifications,
                isOfflineReadOnly = OfflineAccessManager.state.isReadOnly,
                statusMessage = if (OfflineAccessManager.state.isReadOnly) {
                    OfflineAccessManager.state.message ?: OfflineReadOnlyDefaultMessage
                } else {
                    null
                },
            ),
        )
    }

    fun refresh(showLoading: Boolean = true) {
        refreshJob?.cancel()
        val requestVersion = ++refreshVersion
        refreshJob = coroutineScope.launch {
            val latestCachedRemoteNotifications = withContext(Dispatchers.IO) {
                currentUserId?.let { AppReadCacheStore.readNotifications(it)?.payload?.items }
            }.orEmpty()
            updateUiState(
                uiState.copy(
                    isLoading = showLoading && uiState.notifications.isEmpty(),
                    isOfflineReadOnly = false,
                    errorMessage = null,
                    statusMessage = null,
                ),
            )
            when (val result = RepositoryProvider.notificationRepository.getNotifications(limit = 100)) {
                is ApiResult.Success -> {
                    if (requestVersion != refreshVersion) return@launch
                    if (currentUserId != null) {
                        withContext(Dispatchers.IO) {
                            AppReadCacheStore.writeNotifications(
                                userId = currentUserId,
                                notifications = result.data,
                            )
                        }
                    }
                    OfflineAccessManager.clear()
                    updateUiState(
                        NotificationCenterUiState(
                            isLoading = false,
                            isOfflineReadOnly = false,
                            notifications = mergeNotificationStreams(
                                remoteNotifications = result.data.map { it.toNotificationCenterItemUiModel() },
                                localNotifications = NotificationCenterLocalStore.getNotifications(),
                            ),
                        ),
                    )
                }
                is ApiResult.Error -> {
                    if (requestVersion != refreshVersion) return@launch
                    if (latestCachedRemoteNotifications.isNotEmpty() &&
                        (OfflineAccessManager.state.isReadOnly || result.shouldFallbackToReadCache())
                    ) {
                        val message = result.offlineReadOnlyMessage()
                        OfflineAccessManager.enterReadOnly(message)
                        updateUiState(cachedNotificationsUiState(latestCachedRemoteNotifications, true, message))
                    } else {
                        updateUiState(
                            uiState.copy(
                                isLoading = false,
                                errorMessage = result.toBackendUiMessage("读取通知失败，请稍后重试。"),
                            ),
                        )
                    }
                }
                ApiResult.Loading -> Unit
            }
            if (refreshVersion == requestVersion) {
                refreshJob = null
            }
        }
    }

    fun handleConnectivityLost() {
        refreshJob?.cancel()
        refreshVersion += 1
        refreshJob = null
        coroutineScope.launch {
            val latestCachedRemoteNotifications = withContext(Dispatchers.IO) {
                currentUserId?.let { AppReadCacheStore.readNotifications(it)?.payload?.items }
            }.orEmpty()
            val message = "网络已断开，当前显示缓存内容，恢复连接后会自动刷新。"
            if (latestCachedRemoteNotifications.isNotEmpty()) {
                OfflineAccessManager.enterReadOnly(message)
                updateUiState(cachedNotificationsUiState(latestCachedRemoteNotifications, true, message))
                return@launch
            }
            val hasVisibleNotifications = uiState.notifications.isNotEmpty()
            if (hasVisibleNotifications) {
                OfflineAccessManager.enterReadOnly(message)
            }
            updateUiState(
                uiState.copy(
                    isLoading = false,
                    isMutating = false,
                    isOfflineReadOnly = hasVisibleNotifications,
                    errorMessage = if (hasVisibleNotifications) null else "当前无网络，恢复连接后会自动重试。",
                    statusMessage = if (hasVisibleNotifications) message else null,
                ),
            )
        }
    }

    LaunchedEffect(sessionKey) {
        if (!NotificationCenterMemoryCache.hasUiState(sessionKey)) {
            refresh(showLoading = true)
        }
    }
    ReconnectRefreshEffect(
        shouldRefresh = uiState.isOfflineReadOnly ||
            uiState.errorMessage != null ||
            (uiState.isLoading && uiState.notifications.isEmpty()),
        onReconnect = { refresh(showLoading = uiState.notifications.isEmpty()) },
        onDisconnect = ::handleConnectivityLost,
    )

    LaunchedEffect(selectedFilter, availableCategories) {
        if (availableCategories.none { it.name == selectedCategoryName }) {
            selectedCategoryName = NotificationCategoryFilter.ALL.name
        }
    }

    LaunchedEffect(route.source, selectedFilterName, selectedCategoryName, listState) {
        snapshotFlow {
            NotificationCenterRoute(
                source = route.source,
                selectedFilterName = selectedFilterName,
                selectedCategoryName = selectedCategoryName,
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
            )
        }.collect { snapshot ->
            onRouteSnapshotChange(snapshot)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.appBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            NotificationCenterTopBar(
                selectedFilter = selectedFilter,
                selectedCategory = selectedCategory,
                availableCategories = availableCategories,
                notifications = uiState.notifications,
                totalCount = totalCount,
                unreadCount = unreadCount,
                markAllReadEnabled = unreadCount > 0 && !uiState.isMutating && !uiState.isOfflineReadOnly,
                deleteAllEnabled = filteredNotifications.isNotEmpty() && !uiState.isMutating && !uiState.isOfflineReadOnly,
                onBack = onBack,
                onFilterSelected = { selectedFilterName = it.name },
                onCategorySelected = { selectedCategoryName = it.name },
                onMarkAllRead = {
                    if (uiState.isOfflineReadOnly) {
                        showNotice("当前为缓存只读，恢复连接后才能批量标记已读。", YingShiNoticeTone.WARNING)
                        return@NotificationCenterTopBar
                    }
                    coroutineScope.launch {
                        updateUiState(uiState.copy(isMutating = true, errorMessage = null))
                        when (val result = RepositoryProvider.notificationRepository.markAllRead()) {
                            is ApiResult.Success -> {
                                NotificationCenterLocalStore.markAllRead()
                                val updatedState = uiState.copy(
                                        isMutating = false,
                                        notifications = uiState.notifications.map { item ->
                                            if (item.isRead) item else item.copy(isRead = true)
                                        },
                                    )
                                updateUiState(updatedState)
                                persistNotificationUiState(currentUserId, updatedState.notifications)
                                showNotice(
                                    message = if (result.data.affectedCount > 0) {
                                        "已全部标记为已读"
                                    } else {
                                        "当前没有新的未读通知"
                                    },
                                    tone = if (result.data.affectedCount > 0) {
                                        YingShiNoticeTone.SUCCESS
                                    } else {
                                        YingShiNoticeTone.INFO
                                    },
                                )
                            }
                            is ApiResult.Error -> {
                                updateUiState(
                                    uiState.copy(
                                        isMutating = false,
                                        errorMessage = result.toBackendUiMessage("全部标记已读失败，请稍后重试。"),
                                    ),
                                )
                            }
                            ApiResult.Loading -> Unit
                        }
                    }
                },
                onDeleteAll = {
                    if (uiState.isOfflineReadOnly) {
                        showNotice("当前为缓存只读，恢复连接后才能清理通知列表。", YingShiNoticeTone.WARNING)
                        return@NotificationCenterTopBar
                    }
                    if (filteredNotifications.isNotEmpty()) {
                        showClearListConfirm = true
                    }
                },
            )

            uiState.statusMessage?.let { message ->
                NotificationCenterMessageCard(
                    message = message,
                    actionLabel = "重试",
                    onAction = { refresh(showLoading = false) },
                )
            }
            uiState.errorMessage?.let { message ->
                NotificationCenterMessageCard(
                    message = message,
                    actionLabel = "重试",
                    onAction = { refresh(showLoading = uiState.notifications.isEmpty()) },
                )
            }

            when {
                uiState.isLoading && uiState.notifications.isEmpty() -> {
                    NotificationCenterLoadingState(
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                filteredNotifications.isEmpty() -> {
                    NotificationCenterEmptyState(
                        filter = selectedFilter,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        items(
                            items = filteredNotifications,
                            key = NotificationCenterItemUiModel::id,
                        ) { item ->
                            NotificationCenterItemRow(
                                item = item,
                                onClick = {
                                    coroutineScope.launch {
                                        if (uiState.isOfflineReadOnly) {
                                            onOpenNotificationTarget(item)
                                            return@launch
                                        }
                                        val targetItem = if (item.isRead) {
                                            item
                                        } else if (NotificationCenterLocalStore.contains(item.id)) {
                                            NotificationCenterLocalStore.markRead(item.id)
                                            val updatedItem = item.copy(isRead = true)
                                            updateUiState(uiState.replaceNotification(updatedItem))
                                            updatedItem
                                        } else {
                                            when (val result = RepositoryProvider.notificationRepository.markRead(item.id)) {
                                                is ApiResult.Success -> {
                                                    val updatedItem = result.data.toNotificationCenterItemUiModel()
                                                    val updatedState = uiState.replaceNotification(updatedItem)
                                                    updateUiState(updatedState)
                                                    if (currentUserId != null) {
                                                        withContext(Dispatchers.IO) {
                                                            AppReadCacheStore.writeNotification(
                                                                userId = currentUserId,
                                                                notification = result.data,
                                                            )
                                                        }
                                                    }
                                                    persistNotificationUiState(currentUserId, updatedState.notifications)
                                                    updatedItem
                                                }
                                                is ApiResult.Error -> {
                                                    updateUiState(
                                                        uiState.copy(
                                                            errorMessage = result.toBackendUiMessage("标记通知已读失败。"),
                                                        ),
                                                    )
                                                    updateUiState(uiState.replaceNotification(item.copy(isRead = true)))
                                                    item.copy(isRead = true)
                                                }
                                                ApiResult.Loading -> item.copy(isRead = true)
                                            }
                                        }
                                        onOpenNotificationTarget(targetItem)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        YingShiNoticeHost(
            notice = notice,
            onExpired = { nonce ->
                if (notice?.nonce == nonce) {
                    notice = null
                }
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = spacing.md),
        )
    }

    if (showClearListConfirm) {
        AlertDialog(
            onDismissRequest = { showClearListConfirm = false },
            containerColor = colors.raisedSurface,
            titleContentColor = colors.titleAccent,
            textContentColor = colors.textSecondary,
            title = {
                Text(
                    text = "清空当前通知列表？",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            },
            text = {
                Text(
                    text = "只会把当前筛选结果从本地列表里移除，不会删除后端通知记录。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TrashDialogActionButton(
                    text = "继续清空",
                    emphasized = true,
                    onClick = {
                        val deleteIds = filteredNotifications.map(NotificationCenterItemUiModel::id).toSet()
                        showClearListConfirm = false
                        if (deleteIds.isNotEmpty()) {
                            NotificationCenterLocalStore.removeNotifications(deleteIds)
                            updateUiState(
                                uiState.copy(
                                    notifications = uiState.notifications.filterNot { it.id in deleteIds },
                                    errorMessage = null,
                                    statusMessage = null,
                                ),
                            )
                            showNotice(
                                message = "已清空当前筛选结果",
                                tone = YingShiNoticeTone.SUCCESS,
                            )
                        }
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { showClearListConfirm = false })
            },
        )
    }
}

@Composable
private fun NotificationCenterTopBar(
    selectedFilter: NotificationCenterFilter,
    selectedCategory: NotificationCategoryFilter,
    availableCategories: List<NotificationCategoryFilter>,
    notifications: List<NotificationCenterItemUiModel>,
    totalCount: Int,
    unreadCount: Int,
    markAllReadEnabled: Boolean,
    deleteAllEnabled: Boolean,
    onBack: () -> Unit,
    onFilterSelected: (NotificationCenterFilter) -> Unit,
    onCategorySelected: (NotificationCategoryFilter) -> Unit,
    onMarkAllRead: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors

    YingShiToolSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.md),
        highlighted = unreadCount > 0,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.Top,
            ) {
                NotificationIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "通知",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.titleAccent,
                        )
                        NotificationActionPill(
                            text = "一键已读",
                            enabled = markAllReadEnabled,
                            onClick = onMarkAllRead,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "共 $totalCount 条通知",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                            Text(
                                text = if (unreadCount > 0) "$unreadCount 条未读" else "全部已读",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (unreadCount > 0) {
                                    colors.memoryAccent
                                } else {
                                    colors.textSecondary
                                },
                            )
                        }
                        NotificationActionPill(
                            text = "清空",
                            enabled = deleteAllEnabled,
                            onClick = onDeleteAll,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NotificationFilterSectionLabel(text = "模块")
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    NotificationCenterFilter.entries.forEach { filter ->
                        NotificationFilterChip(
                            text = filter.label,
                            selected = filter == selectedFilter,
                            count = notifications.unreadCount(filter),
                            onClick = { onFilterSelected(filter) },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NotificationFilterSectionLabel(text = "分类")
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    availableCategories.forEach { category ->
                        NotificationFilterChip(
                            text = category.displayLabel(selectedFilter),
                            selected = category == selectedCategory,
                            count = notifications
                                .filterBy(selectedFilter)
                                .filterBy(selectedFilter, category)
                                .count { !it.isRead },
                            onClick = { onCategorySelected(category) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationFilterSectionLabel(
    text: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = YingShiThemeTokens.colors.textSecondary,
    )
}

@Composable
private fun NotificationFilterChip(
    text: String,
    selected: Boolean,
    count: Int,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = Modifier
            .yingShiClickable(
                shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                pressedScale = 0.97f,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (selected) {
            colors.primaryContainer.copy(alpha = 0.88f)
        } else {
            colors.sectionBackground.copy(alpha = 0.72f)
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                colors.glassStroke.copy(alpha = 0.78f)
            } else {
                colors.dividerSoft.copy(alpha = 0.64f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = colors.titleAccent,
            )
            if (count > 0) {
                Surface(
                    shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                    color = colors.memoryContainer.copy(alpha = 0.90f),
                    border = BorderStroke(1.dp, colors.memoryAccent.copy(alpha = 0.22f)),
                ) {
                    Text(
                        text = count.toString(),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onMemoryContainer,
                    )
                }
            }
        }
    }
}
@Composable
private fun NotificationCenterItemRow(
    item: NotificationCenterItemUiModel,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val presentation = rememberNotificationPresentation(item)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.xl))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(radius.xl),
        color = if (item.isRead) {
            colors.raisedSurface.copy(alpha = 0.94f)
        } else {
            colors.memoryWash.copy(alpha = 0.96f)
        },
        border = BorderStroke(
            1.dp,
            if (item.isRead) {
                colors.dividerSoft.copy(alpha = 0.54f)
            } else {
                colors.memoryAccent.copy(alpha = 0.24f)
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NotificationTypeChip(type = item.type)
                Text(
                    text = formatNotificationTime(item.createdAtMillis),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
                NotificationStatusBadge(
                    text = if (item.isRead) "已读" else "未读",
                    emphasized = !item.isRead,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                if (!item.isRead) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colors.memoryAccent),
                    )
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                    ) {
                        Text(
                            text = presentation.title,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.titleAccent,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = presentation.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = presentation.targetSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.isRead) {
                                colors.titleAccent.copy(alpha = 0.82f)
                            } else {
                                colors.memoryAccent
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (presentation.visual != NotificationVisual.None) {
                        NotificationVisualPane(
                            visual = presentation.visual,
                            modifier = Modifier
                                .width(96.dp)
                                .height(96.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationVisualPane(
    visual: NotificationVisual,
    modifier: Modifier = Modifier,
) {
    when (visual) {
        NotificationVisual.None -> Unit
        is NotificationVisual.Media -> NotificationMediaVisualPane(
            visual = visual,
            modifier = modifier,
        )
        is NotificationVisual.SmallAlbum -> NotificationSmallAlbumVisualPane(
            visual = visual,
            modifier = modifier,
        )
    }
}

@Composable
private fun NotificationMediaVisualPane(
    visual: NotificationVisual.Media,
    modifier: Modifier = Modifier,
) {
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius.lg),
        color = colors.sectionBackground.copy(alpha = 0.54f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.60f)),
    ) {
        AppContentMediaThumbnail(
            mediaSource = visual.mediaSource,
            mediaType = visual.mediaType,
            palette = visual.palette,
            modifier = Modifier.fillMaxSize(),
            contentDescription = if (visual.mediaType == AppMediaType.VIDEO) "通知视频预览" else "通知图片预览",
            requestSize = 360,
            showLoadingIndicator = true,
            showStatusBadge = true,
            showVideoPlayOverlay = false,
        )
    }
}

@Composable
private fun NotificationSmallAlbumVisualPane(
    visual: NotificationVisual.SmallAlbum,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val previewMedia = visual.previewMedia.distinctBy(AlbumPostPreviewMediaUiModel::id).take(2)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius.lg),
        color = colors.raisedSurface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.60f)),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (previewMedia.isNotEmpty()) {
                if (previewMedia.size == 1) {
                    NotificationSmallAlbumPreviewTile(
                        media = previewMedia.first(),
                        title = visual.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        NotificationSmallAlbumPreviewTile(
                            media = previewMedia[0],
                            title = visual.title,
                            modifier = Modifier
                                .weight(1.35f)
                                .fillMaxHeight(),
                        )
                        NotificationSmallAlbumPreviewTile(
                            media = previewMedia[1],
                            title = visual.title,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(colors.sectionBackground.copy(alpha = 0.50f)),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.sm, vertical = spacing.xs),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = visual.title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = visual.metaLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NotificationSmallAlbumPreviewTile(
    media: AlbumPostPreviewMediaUiModel,
    title: String,
    modifier: Modifier = Modifier,
) {
    AppContentMediaThumbnail(
        mediaSource = media.mediaSource,
        mediaType = media.mediaType,
        palette = media.palette,
        modifier = modifier,
        contentDescription = title,
        requestSize = 320,
        showLoadingIndicator = true,
        showStatusBadge = true,
        showVideoPlayOverlay = false,
    )
}

@Composable
private fun NotificationTypeChip(
    type: NotificationCenterItemType,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val containerColor = when (type) {
        NotificationCenterItemType.COMMENT -> colors.memoryContainer
        NotificationCenterItemType.CONTENT_UPDATE -> colors.primaryContainer
        NotificationCenterItemType.DELETE_RESTORE -> colors.softGreenContainer
        NotificationCenterItemType.SYSTEM -> colors.glowWash
    }

    Surface(
        shape = RoundedCornerShape(radius.capsule),
        color = containerColor.copy(alpha = 0.84f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.44f)),
    ) {
        Text(
            text = type.label,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.titleAccent,
        )
    }
}

@Composable
private fun NotificationStatusBadge(
    text: String,
    emphasized: Boolean,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        shape = RoundedCornerShape(radius.capsule),
        color = if (emphasized) {
            colors.memoryContainer
        } else {
            colors.sectionBackground.copy(alpha = 0.58f)
        },
        border = if (emphasized) {
            BorderStroke(1.dp, colors.memoryAccent.copy(alpha = 0.22f))
        } else {
            BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.42f))
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (emphasized) {
                colors.onMemoryContainer
            } else {
                colors.textSecondary
            },
        )
    }
}

@Composable
private fun NotificationCenterLoadingState(
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.54f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.4.dp,
                color = colors.primaryAction,
            )
            Text(
                text = "正在读取通知…",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun NotificationCenterMessageCard(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.54f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            NotificationActionPill(
                text = actionLabel,
                enabled = true,
                modifier = Modifier.align(Alignment.End),
                onClick = onAction,
            )
        }
    }
}

@Composable
private fun NotificationCenterEmptyState(
    filter: NotificationCenterFilter,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius.xl),
        color = colors.sectionBackground.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.54f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = "${filter.label}暂无通知",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            Text(
                text = "有新的照片或生活提醒时会出现在这里。",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun NotificationIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier
            .size(44.dp)
            .yingShiClickable(shape = CircleShape, pressedScale = 0.94f, onClick = onClick),
        shape = CircleShape,
        color = colors.sectionBackground.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colors.titleAccent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun NotificationActionPill(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier.yingShiClickable(
            enabled = enabled,
            shape = RoundedCornerShape(radius.capsule),
            pressedScale = 0.96f,
            onClick = onClick,
        ),
        shape = RoundedCornerShape(radius.capsule),
        color = if (enabled) colors.primaryContainer.copy(alpha = 0.90f) else colors.sectionBackground.copy(alpha = 0.52f),
        border = BorderStroke(1.dp, if (enabled) colors.glassStroke.copy(alpha = 0.30f) else colors.dividerSoft.copy(alpha = 0.56f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) colors.titleAccent else colors.textSecondary,
        )
    }
}

private fun NotificationCenterUiState.replaceNotification(
    updatedItem: NotificationCenterItemUiModel,
): NotificationCenterUiState {
    return copy(
        notifications = notifications.map { item ->
            if (item.id == updatedItem.id) updatedItem else item
        },
    )
}

private fun List<NotificationCenterItemUiModel>.filterBy(
    filter: NotificationCenterFilter,
): List<NotificationCenterItemUiModel> {
    return filter { it.matchesModule(filter) }
}

private fun mergeNotificationStreams(
    remoteNotifications: List<NotificationCenterItemUiModel>,
    localNotifications: List<NotificationCenterItemUiModel>,
): List<NotificationCenterItemUiModel> {
    return (localNotifications + remoteNotifications)
        .distinctBy(NotificationCenterItemUiModel::id)
        .sortedByDescending(NotificationCenterItemUiModel::createdAtMillis)
}

@Composable
private fun rememberNotificationPresentation(
    item: NotificationCenterItemUiModel,
): NotificationResolvedPresentation {
    val rawPresentation = remember(item.id, item.title, item.body, item.targetSummary) {
        item.toResolvedPresentation()
    }
    val cachedPresentation = NotificationCenterMemoryCache.readPresentation(item.id)
    val resolvedPresentation by produceState(
        initialValue = cachedPresentation ?: rawPresentation,
        item.id,
        item.title,
        item.body,
        item.targetSummary,
    ) {
        if (cachedPresentation == null) {
            val presentation = resolveNotificationPresentation(item)
            NotificationCenterMemoryCache.writePresentation(item.id, presentation)
            value = presentation
        }
    }
    return resolvedPresentation
}

private fun NotificationCenterItemUiModel.toResolvedPresentation(): NotificationResolvedPresentation {
    return NotificationResolvedPresentation(
        title = title.ifBlank { type.label },
        body = body.ifBlank { "点开查看通知详情" },
        targetSummary = targetSummary.withNotificationTargetFallback(),
    )
}

private suspend fun resolveNotificationPresentation(
    item: NotificationCenterItemUiModel,
): NotificationResolvedPresentation {
    val rawPresentation = item.toResolvedPresentation()
    val postVisual = item.postId?.let { postId ->
        loadNotificationPostVisual(postId = postId, focusMediaId = item.mediaId)
    }
    val trashVisual = item.trashItemId
        ?.takeIf { it.isNotBlank() }
        ?.let { trashItemId -> loadNotificationTrashVisual(trashItemId) }
    val visual = when {
        item.mediaId != null && postVisual?.mediaVisual != null -> postVisual.mediaVisual
        postVisual?.smallAlbumVisual != null -> postVisual.smallAlbumVisual
        trashVisual?.mediaVisual != null -> trashVisual.mediaVisual
        trashVisual?.smallAlbumVisual != null -> trashVisual.smallAlbumVisual
        else -> NotificationVisual.None
    }
    val resolvedTargetSummary = when {
        !item.targetSummary.shouldReplaceWithFriendlyCopy() -> item.targetSummary
        postVisual != null -> postVisual.title
        trashVisual != null -> trashVisual.targetSummary
        item.isLifeLedgerTarget() -> "记账"
        item.isLifeChatTarget() -> "聊天导入"
        item.isLifeConsoleTarget() -> "今日痕迹"
        item.targetType.equals("UPLOAD", ignoreCase = true) -> "传输中心"
        else -> item.targetSummary.withNotificationTargetFallback()
    }
    val resolvedTitle = if (rawPresentation.title.shouldReplaceWithFriendlyCopy()) {
        item.buildFriendlyTitle(
            resolvedTargetSummary = resolvedTargetSummary,
            postTitle = postVisual?.title,
        )
    } else {
        rawPresentation.title
    }
    val resolvedBody = if (rawPresentation.body.shouldReplaceWithFriendlyCopy()) {
        item.buildFriendlyBody(
            resolvedTargetSummary = resolvedTargetSummary,
            postTitle = postVisual?.title,
            rawBody = rawPresentation.body,
        )
    } else {
        rawPresentation.body
    }
    return NotificationResolvedPresentation(
        title = resolvedTitle,
        body = resolvedBody,
        targetSummary = resolvedTargetSummary.withNotificationTargetFallback(),
        visual = visual,
    )
}

private data class NotificationPostVisual(
    val title: String,
    val mediaVisual: NotificationVisual.Media?,
    val smallAlbumVisual: NotificationVisual.SmallAlbum?,
)

private data class NotificationTrashVisual(
    val targetSummary: String,
    val mediaVisual: NotificationVisual.Media?,
    val smallAlbumVisual: NotificationVisual.SmallAlbum?,
)

private suspend fun loadNotificationPostVisual(
    postId: String,
    focusMediaId: String?,
): NotificationPostVisual? {
    val detail = when (val result = RepositoryProvider.postRepository.getPostDetail(postId)) {
        is ApiResult.Success -> result.data
        else -> return null
    }
    val previewMedia = detail.mediaItems
        .distinctBy(RemotePostMedia::mediaId)
        .take(2)
        .map(RemotePostMedia::toNotificationPreviewMedia)
    val coverMedia = detail.mediaItems.firstOrNull { it.mediaId == focusMediaId }
        ?: detail.mediaItems.firstOrNull { it.isCover }
        ?: detail.mediaItems.firstOrNull { it.mediaId == detail.coverMediaId }
        ?: detail.mediaItems.firstOrNull()
    return NotificationPostVisual(
        title = detail.title.ifBlank { "小相册" },
        mediaVisual = coverMedia?.toNotificationMediaVisual(),
        smallAlbumVisual = NotificationVisual.SmallAlbum(
            title = detail.title.ifBlank { "小相册" },
            metaLabel = buildNotificationSmallAlbumMeta(detail),
            palette = realPaletteFor(detail.coverMediaId ?: coverMedia?.mediaId ?: detail.postId),
            previewMedia = previewMedia,
        ),
    )
}

private suspend fun loadNotificationTrashVisual(trashItemId: String): NotificationTrashVisual? {
    val item = when (val result = RepositoryProvider.trashRepository.getTrashDetail(trashItemId)) {
        is ApiResult.Success -> result.data.item
        else -> return null
    }
    val entry = item.toTrashEntryUiModel()
    val mediaVisual = entry.mediaSnapshot?.let { media ->
        NotificationVisual.Media(
            mediaSource = media.mediaSource
                ?: realTrashMediaSource(
                    mediaId = media.mediaId,
                    mediaType = media.mediaType,
                    width = media.width,
                    height = media.height,
                    durationMillis = media.videoDurationMillis,
                ),
            mediaType = media.mediaType,
            palette = media.palette,
        )
    }
    val smallAlbumVisual = if (
        entry.type == TrashEntryType.SMALL_ALBUM_DELETED ||
        entry.type == TrashEntryType.LARGE_ALBUM_DELETED
    ) {
        NotificationVisual.SmallAlbum(
            title = entry.title.ifBlank {
                if (entry.type == TrashEntryType.LARGE_ALBUM_DELETED) {
                    "回收站大相册"
                } else {
                    "回收站小相册"
                }
            },
            metaLabel = "${formatNotificationTime(entry.deletedAtMillis)} · ${entry.relatedMediaIds.size.takeIf { it > 0 } ?: 0} 项",
            palette = entry.palette,
            previewMedia = emptyList(),
        )
    } else {
        null
    }
    return NotificationTrashVisual(
        targetSummary = when {
            item.sourcePostId != null -> entry.title
            item.sourceMediaId != null -> "回收站"
            else -> "回收站"
        },
        mediaVisual = mediaVisual,
        smallAlbumVisual = smallAlbumVisual,
    )
}

private fun RemotePostMedia.toNotificationPreviewMedia(): AlbumPostPreviewMediaUiModel {
    val mediaType = resolveAppMediaType(
        rawType = mediaType,
        mimeType = mimeType,
        thumbnailUrl = thumbnailUrl ?: previewUrl,
        mediaUrl = mediaUrl,
        videoUrl = videoUrl,
        coverUrl = coverUrl,
        originalUrl = originalUrl,
    )
    return AlbumPostPreviewMediaUiModel(
        id = mediaId,
        palette = realPaletteFor(mediaId),
        mediaType = mediaType,
        aspectRatio = resolveAppContentAspectRatio(
            aspectRatio = aspectRatio,
            width = width,
            height = height,
            mediaType = mediaType,
        ),
        mediaSource = toAppContentMediaSource(),
    )
}

private fun RemotePostMedia.toNotificationMediaVisual(): NotificationVisual.Media {
    val resolvedType = resolveAppMediaType(
        rawType = mediaType,
        mimeType = mimeType,
        thumbnailUrl = thumbnailUrl ?: previewUrl,
        mediaUrl = mediaUrl,
        videoUrl = videoUrl,
        coverUrl = coverUrl,
        originalUrl = originalUrl,
    )
    return NotificationVisual.Media(
        mediaSource = toAppContentMediaSource(),
        mediaType = resolvedType,
        palette = realPaletteFor(mediaId),
    )
}

private fun buildNotificationSmallAlbumMeta(detail: RemotePostDetail): String {
    return "${formatNotificationTime(detail.displayTimeMillis)} · ${detail.mediaItems.size} 张"
}

private fun NotificationCenterItemUiModel.buildFriendlyTitle(
    resolvedTargetSummary: String,
    postTitle: String?,
): String {
    val targetLabel = postTitle?.takeIf { it.isNotBlank() } ?: resolvedTargetSummary.withNotificationTargetFallback()
    return when {
        isLifeLedgerTarget() -> "记账有新动态"
        isLifeChatTarget() -> "聊天导入有新动态"
        isLifeConsoleTarget() -> "今日痕迹有新更新"
        targetType.equals("UPLOAD", ignoreCase = true) -> "传输中心有新进度"
        type == NotificationCenterItemType.COMMENT -> "「$targetLabel」有新评论"
        type == NotificationCenterItemType.CONTENT_UPDATE && targetType.equals("ALBUM", ignoreCase = true) -> "相册目录有更新"
        type == NotificationCenterItemType.CONTENT_UPDATE -> "「$targetLabel」有内容更新"
        type == NotificationCenterItemType.DELETE_RESTORE -> "$targetLabel 有回收站变动"
        else -> if (targetLabel.isNotBlank()) "$targetLabel 有新提醒" else type.label
    }
}

private fun NotificationCenterItemUiModel.buildFriendlyBody(
    resolvedTargetSummary: String,
    postTitle: String?,
    rawBody: String,
): String {
    val targetLabel = postTitle?.takeIf { it.isNotBlank() } ?: resolvedTargetSummary.withNotificationTargetFallback()
    return when {
        isLifeLedgerTarget() -> "点开可直接查看对应的账本统计和最近变化。"
        isLifeChatTarget() -> "点开可查看最新导入的聊天内容。"
        isLifeConsoleTarget() -> "点开可查看今天新增的生活记录。"
        targetType.equals("UPLOAD", ignoreCase = true) -> "点开可查看当前文件传输状态。"
        type == NotificationCenterItemType.COMMENT -> "点开可直接回到 $targetLabel 查看评论上下文。"
        type == NotificationCenterItemType.DELETE_RESTORE -> "点开可查看回收站中的对应条目，并继续恢复或删除。"
        type == NotificationCenterItemType.CONTENT_UPDATE -> "点开可回到 $targetLabel 查看最新内容。"
        rawBody.isNotBlank() -> rawBody
        else -> "点开查看相关内容。"
    }
}

private fun String?.shouldReplaceWithFriendlyCopy(): Boolean {
    val value = this?.trim().orEmpty()
    if (value.isBlank()) return true
    if (value.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }) return false
    val looksFileName = value.contains('.') && value.any(Char::isDigit)
    val looksIdentifier = Regex("[A-Za-z0-9_-]{12,}").containsMatchIn(value)
    return looksFileName || looksIdentifier || value.all { it.code in 32..126 }
}

private fun String?.withNotificationTargetFallback(): String {
    return this?.takeIf { it.isNotBlank() } ?: "查看相关内容"
}

private fun List<NotificationCenterItemUiModel>.filterBy(
    filter: NotificationCenterFilter,
    category: NotificationCategoryFilter,
): List<NotificationCenterItemUiModel> {
    return if (category == NotificationCategoryFilter.ALL) {
        this
    } else {
        filter { it.matchesCategory(filter, category) }
    }
}

private fun List<NotificationCenterItemUiModel>.unreadCount(
    filter: NotificationCenterFilter,
): Int {
    return filterBy(filter).count { !it.isRead }
}

private fun NotificationCenterItemUiModel.matchesModule(
    filter: NotificationCenterFilter,
): Boolean {
    val haystack = listOfNotNull(targetType, targetSummary, title, body)
        .joinToString(separator = " ")
        .lowercase()
    val isLife = haystack.contains("life") ||
        haystack.contains("ledger") ||
        haystack.contains("chat") ||
        haystack.contains("账") ||
        haystack.contains("聊天") ||
        haystack.contains("痕迹")
    return when (filter) {
        NotificationCenterFilter.LIFE -> isLife
        NotificationCenterFilter.PHOTOS -> !isLife
    }
}

private fun NotificationCenterItemUiModel.matchesCategory(
    filter: NotificationCenterFilter,
    category: NotificationCategoryFilter,
): Boolean {
    return when (filter) {
        NotificationCenterFilter.PHOTOS -> when (category) {
            NotificationCategoryFilter.ALL -> true
            NotificationCategoryFilter.COMMENT -> type == NotificationCenterItemType.COMMENT
            NotificationCategoryFilter.CONTENT_UPDATE -> type == NotificationCenterItemType.CONTENT_UPDATE
            NotificationCategoryFilter.DELETE_RESTORE -> type == NotificationCenterItemType.DELETE_RESTORE
            NotificationCategoryFilter.SYSTEM -> type == NotificationCenterItemType.SYSTEM
            NotificationCategoryFilter.LEDGER,
            NotificationCategoryFilter.CHAT,
            NotificationCategoryFilter.TRACE,
            -> false
        }

        NotificationCenterFilter.LIFE -> when (category) {
            NotificationCategoryFilter.ALL -> true
            NotificationCategoryFilter.LEDGER -> isLifeLedgerTarget()
            NotificationCategoryFilter.CHAT -> isLifeChatTarget()
            NotificationCategoryFilter.TRACE -> isLifeConsoleTarget()
            NotificationCategoryFilter.SYSTEM -> {
                matchesModule(NotificationCenterFilter.LIFE) &&
                    !isLifeLedgerTarget() &&
                    !isLifeChatTarget() &&
                    !isLifeConsoleTarget()
            }
            NotificationCategoryFilter.COMMENT,
            NotificationCategoryFilter.CONTENT_UPDATE,
            NotificationCategoryFilter.DELETE_RESTORE,
            -> false
        }
    }
}

private fun notificationCategoriesFor(
    filter: NotificationCenterFilter,
): List<NotificationCategoryFilter> {
    return when (filter) {
        NotificationCenterFilter.PHOTOS -> listOf(
            NotificationCategoryFilter.ALL,
            NotificationCategoryFilter.COMMENT,
            NotificationCategoryFilter.CONTENT_UPDATE,
            NotificationCategoryFilter.DELETE_RESTORE,
            NotificationCategoryFilter.SYSTEM,
        )

        NotificationCenterFilter.LIFE -> listOf(
            NotificationCategoryFilter.ALL,
            NotificationCategoryFilter.LEDGER,
            NotificationCategoryFilter.CHAT,
            NotificationCategoryFilter.TRACE,
            NotificationCategoryFilter.SYSTEM,
        )
    }
}

private fun NotificationCategoryFilter.displayLabel(
    filter: NotificationCenterFilter,
): String {
    return when (filter) {
        NotificationCenterFilter.PHOTOS -> label
        NotificationCenterFilter.LIFE -> when (this) {
            NotificationCategoryFilter.CHAT -> "聊天导入"
            NotificationCategoryFilter.SYSTEM -> "同步提醒"
            else -> label
        }
    }
}

private fun formatNotificationTime(timeMillis: Long): String {
    return SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}

private suspend fun persistNotificationUiState(
    userId: String?,
    notifications: List<NotificationCenterItemUiModel>,
) {
    if (userId.isNullOrBlank()) return
    withContext(Dispatchers.IO) {
        AppReadCacheStore.writeNotifications(
            userId = userId,
            notifications = notifications.map(NotificationCenterItemUiModel::toRemoteNotification),
        )
    }
}

private fun NotificationCenterItemUiModel.toRemoteNotification(): RemoteNotification {
    return RemoteNotification(
        notificationId = id,
        type = type.apiValue,
        title = title,
        body = body,
        createdAtMillis = createdAtMillis,
        isRead = isRead,
        targetSummary = targetSummary,
        targetType = targetType,
        smallAlbumId = postId,
        mediaId = mediaId,
        trashItemId = trashItemId,
    )
}

@Preview(showBackground = true)
@Composable
private fun NotificationCenterScreenPreview() {
    YingShiTheme {
        NotificationCenterScreen(
            route = NotificationCenterRoute(),
            onBack = { },
            onOpenNotificationDetail = { },
        )
    }
}
