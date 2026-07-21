package com.example.yingshi.feature.photos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.Icons as MaterialIcons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshi.feature.sync.SyncModule
import com.example.yingshi.feature.sync.SyncVersionTracker
import com.example.yingshi.ui.components.YingShiNotice
import com.example.yingshi.ui.components.YingShiNoticeHost
import com.example.yingshi.ui.components.YingShiNoticeTone
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens

@OptIn(ExperimentalMaterial3Api::class)
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
    val viewModel: NotificationCenterViewModel = viewModel()
    val sessionKey = realBackendSessionKey("notification-center-${route.source}")
    val syncStaleState by SyncVersionTracker.staleState.collectAsState()
    val notificationStale = syncStaleState.notificationsStale

    val uiState by viewModel.uiState.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val includeSelfActor by viewModel.includeSelfActor.collectAsState()
    val includePartnerActor by viewModel.includePartnerActor.collectAsState()

    var notice by remember { mutableStateOf<YingShiNotice?>(null) }
    var noticeNonce by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = route.firstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = route.firstVisibleItemScrollOffset,
    )
    val availableCategories = notificationCategoriesFor(selectedFilter)
    val filteredNotifications = uiState.notifications
        .filterByActor(includeSelf = includeSelfActor, includePartner = includePartnerActor)
        .filterBy(selectedFilter)
        .filterBy(selectedFilter, selectedCategory)
    val notificationSections = filteredNotifications.toNotificationDateSections()
    val unreadCount = filteredNotifications.count { !it.isRead }
    val totalCount = filteredNotifications.size

    fun showNotice(
        message: String,
        tone: YingShiNoticeTone = YingShiNoticeTone.INFO,
    ) {
        noticeNonce += 1
        notice = YingShiNotice(message = message, tone = tone, nonce = noticeNonce)
    }

    LaunchedEffect(Unit) {
        viewModel.noticeEvent.collect { event ->
            showNotice(event.message, event.tone)
        }
    }

    LaunchedEffect(sessionKey) {
        viewModel.init(sessionKey, route.source)
        if (!NotificationCenterViewModel.hasMemoryUiState(sessionKey)) {
            viewModel.refresh()
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { syncStaleState.notificationsStale }.collect { currentlyStale ->
            if (currentlyStale) {
                viewModel.refresh()
                SyncVersionTracker.markRefreshed(SyncModule.NOTIFICATIONS)
            }
        }
    }

    ReconnectRefreshEffect(
        shouldRefresh = uiState.isOfflineReadOnly ||
            uiState.errorMessage != null ||
            (uiState.isLoading && uiState.notifications.isEmpty()),
        onReconnect = { viewModel.refresh() },
        onDisconnect = { viewModel.handleConnectivityLost() },
    )

    LaunchedEffect(selectedFilter, availableCategories) {
        if (availableCategories.none { it.name == selectedCategory.name }) {
            viewModel.setCategory(NotificationCategoryFilter.ALL)
        }
    }
    LaunchedEffect(route.source, selectedFilter, selectedCategory, listState) {
        snapshotFlow {
            NotificationCenterRoute(
                source = route.source,
                selectedFilterName = selectedFilter.name,
                selectedCategoryName = selectedCategory.name,
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
            )
        }.collect { snapshot ->
            onRouteSnapshotChange(snapshot)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItem to totalItems
        }.collect { (lastVisible, total) ->
            if (total > 0 && lastVisible >= total - 3 && !uiState.isLoadingMore && uiState.hasMore) {
                viewModel.loadMore()
            }
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
            val motion = YingShiThemeTokens.motion

            AnimatedVisibility(
                visible = notificationStale,
                enter = fadeIn(animationSpec = tween(motion.noticeMillis, easing = motion.easing)) +
                    slideInVertically(animationSpec = tween(motion.noticeMillis, easing = motion.easing)) {
                        -it / 2
                    },
                exit = fadeOut(animationSpec = tween(motion.stateMillis, easing = motion.easing)) +
                    slideOutVertically(animationSpec = tween(motion.stateMillis, easing = motion.easing)) {
                        -it / 2
                    },
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .yingShiClickable(onClick = {
                            viewModel.refresh()
                            SyncVersionTracker.markRefreshed(SyncModule.NOTIFICATIONS)
                        }),
                    shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                    color = colors.primaryContainer.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, colors.primaryAction.copy(alpha = 0.18f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = MaterialIcons.Rounded.Refresh,
                            contentDescription = null,
                            tint = colors.titleAccent,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "有新通知，点击刷新",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.titleAccent,
                        )
                    }
                }
            }

            NotificationCenterTopBar(
                selectedFilter = selectedFilter,
                selectedCategory = selectedCategory,
                availableCategories = availableCategories,
                notifications = uiState.notifications,
                totalCount = totalCount,
                unreadCount = unreadCount,
                includeSelfActor = includeSelfActor,
                includePartnerActor = includePartnerActor,
                markAllReadEnabled = unreadCount > 0 && !uiState.isMutating && !uiState.isOfflineReadOnly,
                deleteAllEnabled = filteredNotifications.isNotEmpty() && !uiState.isMutating && !uiState.isOfflineReadOnly,
                onBack = onBack,
                onFilterSelected = { viewModel.setFilter(it) },
                onCategorySelected = { viewModel.setCategory(it) },
                onToggleSelfActor = { viewModel.toggleSelfActor() },
                onTogglePartnerActor = { viewModel.togglePartnerActor() },
                onMarkAllRead = {
                    if (uiState.isOfflineReadOnly) {
                        showNotice("当前为缓存只读，恢复连接后才能批量标记已读。", YingShiNoticeTone.WARNING)
                        return@NotificationCenterTopBar
                    }
                    viewModel.markAllRead()
                },
                onDeleteAll = {
                    if (uiState.isOfflineReadOnly) {
                        showNotice("当前为缓存只读，恢复连接后才能清理通知列表。", YingShiNoticeTone.WARNING)
                        return@NotificationCenterTopBar
                    }
                    if (filteredNotifications.isNotEmpty()) {
                        viewModel.showClearListConfirm()
                    }
                },
            )

            uiState.statusMessage?.let { message ->
                NotificationCenterMessageCard(
                    message = message,
                    actionLabel = "重试",
                    onAction = { viewModel.refresh() },
                )
            }
            uiState.errorMessage?.let { message ->
                NotificationCenterMessageCard(
                    message = message,
                    actionLabel = "重试",
                    onAction = { viewModel.refresh() },
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
                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = {
                            if (!uiState.isOfflineReadOnly) {
                                viewModel.refresh()
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            notificationSections.forEach { section ->
                                item(key = "date-${section.key}") {
                                    NotificationDateSectionHeader(title = section.title)
                                }
                                items(
                                    items = section.items,
                                    key = NotificationCenterItemUiModel::id,
                                ) { item ->
                                NotificationCenterItemRow(
                                    item = item,
                                    onMediaClick = { mediaItem ->
                                        val optimisticItem = item.copy(isRead = true, mediaId = mediaItem.mediaId)
                                        viewModel.updateNotificationItem(optimisticItem)
                                        if (!uiState.isOfflineReadOnly && !item.isRead) {
                                            viewModel.markRead(item.id)
                                        }
                                        onOpenNotificationTarget(optimisticItem)
                                    },
                                    onClick = {
                                        if (uiState.isOfflineReadOnly || item.isRead) {
                                            return@NotificationCenterItemRow
                                        }
                                        viewModel.markRead(item.id)
                                    },
                                )
                                }
                            }

                            if (uiState.isLoadingMore) {
                                item(key = "loading-more") {
                                    NotificationCenterLoadingState(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = spacing.md),
                                    )
                                }
                            }

                            if (!uiState.hasMore && notificationSections.isNotEmpty()) {
                                item(key = "no-more") {
                                    Text(
                                        text = "没有更多通知",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = spacing.md),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
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

    if (uiState.isClearListConfirmVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissClearListConfirm() },
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
                        viewModel.clearList(deleteIds)
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { viewModel.dismissClearListConfirm() })
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
    includeSelfActor: Boolean,
    includePartnerActor: Boolean,
    markAllReadEnabled: Boolean,
    deleteAllEnabled: Boolean,
    onBack: () -> Unit,
    onFilterSelected: (NotificationCenterFilter) -> Unit,
    onCategorySelected: (NotificationCategoryFilter) -> Unit,
    onToggleSelfActor: () -> Unit,
    onTogglePartnerActor: () -> Unit,
    onMarkAllRead: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    val actorFilteredNotifications = remember(notifications, includeSelfActor, includePartnerActor) {
        notifications.filterByActor(includeSelf = includeSelfActor, includePartner = includePartnerActor)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NotificationIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "通知",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
                Text(
                    text = if (unreadCount > 0) {
                        "共 $totalCount 条，$unreadCount 条未读"
                    } else {
                        "共 $totalCount 条，全部已读"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (unreadCount > 0) colors.memoryAccent else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            NotificationActionPill(
                text = "一键已读",
                enabled = markAllReadEnabled,
                onClick = onMarkAllRead,
            )
            NotificationIconButton(
                icon = Icons.Default.Delete,
                contentDescription = "清空当前筛选通知",
                enabled = deleteAllEnabled,
                danger = true,
                onClick = onDeleteAll,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NotificationActorChip(
                text = "对方",
                selected = includePartnerActor,
                onClick = onTogglePartnerActor,
            )
            NotificationActorChip(
                text = "自己",
                selected = includeSelfActor,
                onClick = onToggleSelfActor,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                        count = actorFilteredNotifications.unreadCount(filter),
                        onClick = { onFilterSelected(filter) },
                    )
                }
            }
            NotificationStatusBadge(
                text = selectedCategory.displayLabel(selectedFilter),
                emphasized = selectedCategory != NotificationCategoryFilter.ALL,
            )
            Box {
                NotificationIconButton(
                    icon = Icons.Default.Menu,
                    contentDescription = "通知分类",
                    onClick = { categoryMenuExpanded = true },
                )
                DropdownMenu(
                    expanded = categoryMenuExpanded,
                    onDismissRequest = { categoryMenuExpanded = false },
                ) {
                    availableCategories.forEach { category ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = category.displayLabel(selectedFilter),
                                    color = colors.titleAccent,
                                )
                            },
                            onClick = {
                                categoryMenuExpanded = false
                                onCategorySelected(category)
                            },
                        )
                    }
                }
            }
        }
    }
}