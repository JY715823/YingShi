package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.cache.AppReadCacheStore
import com.example.yingshi.data.cache.OfflineAccessManager
import com.example.yingshi.data.cache.OfflineReadOnlyDefaultMessage
import com.example.yingshi.data.model.RemoteNotification
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class NotificationDetailUiState(
    val isLoading: Boolean = false,
    val isOfflineReadOnly: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val item: NotificationCenterItemUiModel? = null,
)

@Composable
fun NotificationDetailScreen(
    route: NotificationDetailRoute,
    onBack: () -> Unit,
    onOpenTarget: (NotificationCenterItemUiModel) -> Unit = { },
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val coroutineScope = rememberCoroutineScope()
    val sessionKey = realBackendSessionKey("notification-detail-${route.notificationId}")
    val currentUserId = AuthSessionManager.getCurrentUserSnapshot()?.userId
    var cachedItem by remember(sessionKey, currentUserId, route.notificationId) {
        mutableStateOf<NotificationCenterItemUiModel?>(null)
    }
    var uiState by remember(sessionKey, route.notificationId) {
        mutableStateOf(
            NotificationDetailUiState(isLoading = true),
        )
    }
    var refreshJob by remember(sessionKey, route.notificationId) { mutableStateOf<Job?>(null) }
    var refreshVersion by remember(sessionKey, route.notificationId) { mutableIntStateOf(0) }

    LaunchedEffect(sessionKey, currentUserId, route.notificationId) {
        val resolvedCachedItem = withContext(Dispatchers.IO) {
            currentUserId?.let { userId ->
                AppReadCacheStore.readNotification(userId, route.notificationId)?.payload
                    ?.toNotificationCenterItemUiModel()
            }
        }
        cachedItem = resolvedCachedItem
        if (resolvedCachedItem != null && uiState.item == null) {
            uiState = NotificationDetailUiState(
                isLoading = false,
                isOfflineReadOnly = OfflineAccessManager.state.isReadOnly,
                statusMessage = if (OfflineAccessManager.state.isReadOnly) {
                    OfflineAccessManager.state.message ?: OfflineReadOnlyDefaultMessage
                } else {
                    null
                },
                item = resolvedCachedItem,
            )
        }
    }

    fun refresh(markRead: Boolean) {
        refreshJob?.cancel()
        val requestVersion = ++refreshVersion
        refreshJob = coroutineScope.launch {
            val latestCachedItem = withContext(Dispatchers.IO) {
                currentUserId?.let { userId ->
                    AppReadCacheStore.readNotification(userId, route.notificationId)?.payload
                        ?.toNotificationCenterItemUiModel()
                }
            } ?: cachedItem
            if (!AuthSessionManager.isLoggedIn && latestCachedItem != null && OfflineAccessManager.state.isReadOnly) {
                if (requestVersion != refreshVersion) return@launch
                uiState = NotificationDetailUiState(
                    isLoading = false,
                    isOfflineReadOnly = true,
                    statusMessage = OfflineAccessManager.state.message ?: OfflineReadOnlyDefaultMessage,
                    item = latestCachedItem,
                )
                return@launch
            }
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            when (val result = RepositoryProvider.notificationRepository.getNotification(route.notificationId)) {
                is ApiResult.Success -> {
                    if (requestVersion != refreshVersion) return@launch
                    val loadedItem = result.data.toNotificationCenterItemUiModel()
                    if (currentUserId != null) {
                        withContext(Dispatchers.IO) {
                            AppReadCacheStore.writeNotification(
                                userId = currentUserId,
                                notification = result.data,
                            )
                        }
                    }
                    cachedItem = loadedItem
                    OfflineAccessManager.clear()
                    uiState = NotificationDetailUiState(
                        isLoading = false,
                        isOfflineReadOnly = false,
                        item = loadedItem,
                    )
                    if (markRead && !loadedItem.isRead && !uiState.isOfflineReadOnly) {
                        when (val readResult = RepositoryProvider.notificationRepository.markRead(route.notificationId)) {
                            is ApiResult.Success -> {
                                if (requestVersion != refreshVersion) return@launch
                                if (currentUserId != null) {
                                    withContext(Dispatchers.IO) {
                                        AppReadCacheStore.writeNotification(
                                            userId = currentUserId,
                                            notification = readResult.data,
                                        )
                                    }
                                }
                                cachedItem = readResult.data.toNotificationCenterItemUiModel()
                                uiState = uiState.copy(
                                    item = cachedItem,
                                )
                            }
                            is ApiResult.Error -> {
                                if (requestVersion != refreshVersion) return@launch
                                uiState = uiState.copy(
                                    errorMessage = readResult.toBackendUiMessage("标记通知已读失败。"),
                                )
                            }
                            ApiResult.Loading -> Unit
                        }
                    }
                }
                is ApiResult.Error -> {
                    if (requestVersion != refreshVersion) return@launch
                    if (latestCachedItem != null && (OfflineAccessManager.state.isReadOnly || result.shouldFallbackToReadCache())) {
                        val message = result.offlineReadOnlyMessage()
                        OfflineAccessManager.enterReadOnly(message)
                        uiState = NotificationDetailUiState(
                            isLoading = false,
                            isOfflineReadOnly = true,
                            statusMessage = message,
                            item = latestCachedItem,
                        )
                    } else {
                        uiState = NotificationDetailUiState(
                            isLoading = false,
                            errorMessage = result.toBackendUiMessage("读取通知详情失败，请稍后重试。"),
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
            val latestCachedItem = withContext(Dispatchers.IO) {
                currentUserId?.let { userId ->
                    AppReadCacheStore.readNotification(userId, route.notificationId)?.payload
                        ?.toNotificationCenterItemUiModel()
                }
            } ?: uiState.item ?: cachedItem
            val message = "网络已断开，当前显示缓存内容，恢复连接后会自动刷新。"
            if (latestCachedItem != null) {
                OfflineAccessManager.enterReadOnly(message)
                uiState = NotificationDetailUiState(
                    isLoading = false,
                    isOfflineReadOnly = true,
                    statusMessage = message,
                    item = latestCachedItem,
                )
                return@launch
            }
            val hasVisibleItem = uiState.item != null
            if (hasVisibleItem) {
                OfflineAccessManager.enterReadOnly(message)
            }
            uiState = uiState.copy(
                isLoading = false,
                isOfflineReadOnly = hasVisibleItem,
                errorMessage = if (hasVisibleItem) null else "当前无网络，恢复连接后会自动重试。",
                statusMessage = if (hasVisibleItem) message else null,
            )
        }
    }

    LaunchedEffect(sessionKey, route.notificationId) {
        refresh(markRead = true)
    }
    ReconnectRefreshEffect(
        shouldRefresh = uiState.isOfflineReadOnly ||
            uiState.errorMessage != null ||
            (uiState.isLoading && uiState.item == null),
        onReconnect = { refresh(markRead = uiState.item?.isRead != true) },
        onDisconnect = ::handleConnectivityLost,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        NotificationDetailTopBar(
            route = route,
            onBack = onBack,
        )

        when {
            uiState.isLoading && uiState.item == null -> {
                NotificationDetailLoadingState()
            }

            uiState.item != null -> {
                val item = requireNotNull(uiState.item)
                uiState.statusMessage?.let { message ->
                    NotificationDetailMessageCard(
                        message = message,
                        actionLabel = "重试",
                        onAction = { refresh(markRead = false) },
                    )
                }
                NotificationDetailPrimaryCard(item = item)
                NotificationDetailTargetCard(
                    item = item,
                    sourceLabel = route.source.toNotificationDetailSourceLabel(),
                    onOpenTarget = { onOpenTarget(item) },
                )
                uiState.errorMessage?.let { message ->
                    NotificationDetailMessageCard(
                        message = message,
                        actionLabel = "重试",
                        onAction = { refresh(markRead = false) },
                    )
                }
            }

            uiState.errorMessage != null -> {
                NotificationDetailMessageCard(
                    message = uiState.errorMessage.orEmpty(),
                    actionLabel = "重试",
                    onAction = { refresh(markRead = true) },
                )
            }

            else -> {
                NotificationDetailEmptyState()
            }
        }
    }
}

@Composable
private fun NotificationDetailTopBar(
    route: NotificationDetailRoute,
    onBack: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NotificationDetailCircleButton(onClick = onBack)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "通知详情",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            Text(
                text = route.source.toNotificationDetailSourceLabel(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun NotificationDetailPrimaryCard(
    item: NotificationCenterItemUiModel,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        shape = RoundedCornerShape(radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.58f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NotificationDetailPill(
                    text = item.type.label,
                    selected = true,
                )
                NotificationDetailPill(
                    text = if (item.isRead) "已读" else "未读",
                    warm = !item.isRead,
                )
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                NotificationDetailMetaRow(
                    label = "相关位置",
                    value = item.targetSummary,
                )
                NotificationDetailMetaRow(
                    label = "打开后会进入",
                    value = item.notificationTargetLabel(),
                )
                NotificationDetailMetaRow(
                    label = "收到时间",
                    value = formatNotificationDetailTime(item.createdAtMillis),
                )
            }
        }
    }
}

@Composable
private fun NotificationDetailTargetCard(
    item: NotificationCenterItemUiModel,
    sourceLabel: String,
    onOpenTarget: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        shape = RoundedCornerShape(radius.xl),
        color = colors.sectionBackground.copy(alpha = 0.44f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.58f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = "下一步",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            Text(
                text = item.notificationTargetDescription(),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            NotificationDetailMetaRow(
                label = "通知来自",
                value = sourceLabel,
            )
            if (item.hasNotificationTargetAction()) {
                NotificationDetailActionChip(
                    text = item.notificationActionLabel(),
                    modifier = Modifier.align(Alignment.End),
                    onClick = onOpenTarget,
                )
            }
        }
    }
}

@Composable
private fun NotificationDetailPill(
    text: String,
    selected: Boolean = false,
    warm: Boolean = false,
) {
    val colors = YingShiThemeTokens.colors
    val radius = YingShiThemeTokens.radius
    Surface(
        shape = RoundedCornerShape(radius.capsule),
        color = when {
            warm -> colors.memoryContainer.copy(alpha = 0.92f)
            selected -> colors.primaryContainer.copy(alpha = 0.78f)
            else -> colors.sectionBackground.copy(alpha = 0.74f)
        },
        border = BorderStroke(
            1.dp,
            when {
                warm -> colors.memoryAccent.copy(alpha = 0.20f)
                selected -> colors.glassStroke.copy(alpha = 0.42f)
                else -> colors.dividerSoft.copy(alpha = 0.52f)
            },
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = YingShiThemeTokens.spacing.sm, vertical = YingShiThemeTokens.spacing.xs),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (warm) colors.memoryAccent else colors.titleAccent,
        )
    }
}

@Composable
private fun NotificationDetailMetaRow(
    label: String,
    value: String,
) {
    val colors = YingShiThemeTokens.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun NotificationDetailLoadingState() {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
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
                text = "正在读取通知详情…",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun NotificationDetailMessageCard(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        shape = RoundedCornerShape(radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.54f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            NotificationDetailActionChip(
                text = actionLabel,
                modifier = Modifier.align(Alignment.End),
                onClick = onAction,
            )
        }
    }
}

@Composable
private fun NotificationDetailEmptyState() {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        shape = RoundedCornerShape(radius.xl),
        color = colors.sectionBackground.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.54f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = "这条通知当前不可用",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            Text(
                text = "通知可能已经被替换，或者当前会话里已经找不到对应记录。",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun NotificationDetailCircleButton(
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = colors.sectionBackground.copy(alpha = 0.80f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
    ) {
        Box(
            modifier = Modifier.padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = colors.titleAccent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun NotificationDetailActionChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(radius.capsule),
        color = colors.softGreenContainer.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, colors.softGreenAction.copy(alpha = 0.24f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.softGreenAction,
        )
    }
}

private fun formatNotificationDetailTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}

private fun String.toNotificationDetailSourceLabel(): String {
    return when (this) {
        "notification-center" -> "通知中心"
        "photos-top-bar" -> "照片页顶部"
        "photos-home" -> "照片页"
        else -> "通知详情"
    }
}

@Preview(showBackground = true)
@Composable
private fun NotificationDetailScreenPreview() {
    YingShiTheme {
        NotificationDetailScreen(
            route = NotificationDetailRoute(notificationId = "notice-comment-1"),
            onBack = { },
        )
    }
}
