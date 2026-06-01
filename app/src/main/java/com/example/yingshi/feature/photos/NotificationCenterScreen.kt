package com.example.yingshi.feature.photos

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private data class NotificationCenterUiState(
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null,
    val notifications: List<NotificationCenterItemUiModel> = emptyList(),
)

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
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessionKey = realBackendSessionKey("notification-center-${route.source}")
    var selectedFilterName by rememberSaveable { mutableStateOf(NotificationCenterFilter.ALL.name) }
    var uiState by remember(sessionKey) {
        mutableStateOf(NotificationCenterUiState(isLoading = true))
    }
    val selectedFilter = NotificationCenterFilter.valueOf(selectedFilterName)
    val filteredNotifications = uiState.notifications.filterBy(selectedFilter)
    val unreadCount = uiState.notifications.count { !it.isRead }

    fun refresh(showLoading: Boolean = true) {
        coroutineScope.launch {
            uiState = uiState.copy(
                isLoading = showLoading,
                errorMessage = null,
            )
            when (val result = RepositoryProvider.notificationRepository.getNotifications(limit = 100)) {
                is ApiResult.Success -> {
                    uiState = NotificationCenterUiState(
                        isLoading = false,
                        notifications = result.data.map { it.toNotificationCenterItemUiModel() },
                    )
                }
                is ApiResult.Error -> {
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = result.toBackendUiMessage("读取通知失败，请稍后重试。"),
                    )
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    LaunchedEffect(sessionKey) {
        refresh(showLoading = true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        NotificationCenterTopBar(
            unreadCount = unreadCount,
            markAllReadEnabled = unreadCount > 0 && !uiState.isMutating,
            onBack = onBack,
            onMarkAllRead = {
                coroutineScope.launch {
                    uiState = uiState.copy(isMutating = true, errorMessage = null)
                    when (val result = RepositoryProvider.notificationRepository.markAllRead()) {
                        is ApiResult.Success -> {
                            uiState = uiState.copy(
                                isMutating = false,
                                notifications = uiState.notifications.map { item ->
                                    if (item.isRead) item else item.copy(isRead = true)
                                },
                            )
                            Toast.makeText(
                                context,
                                if (result.data.affectedCount > 0) {
                                    "已全部标记为已读"
                                } else {
                                    "当前没有新的未读通知"
                                },
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        is ApiResult.Error -> {
                            uiState = uiState.copy(
                                isMutating = false,
                                errorMessage = result.toBackendUiMessage("全部标记已读失败，请稍后重试。"),
                            )
                        }
                        ApiResult.Loading -> Unit
                    }
                }
            },
        )

        NotificationFilterRow(
            selectedFilter = selectedFilter,
            notifications = uiState.notifications,
            onFilterSelected = { selectedFilterName = it.name },
        )

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
                                    val targetItem = if (item.isRead) {
                                        item
                                    } else {
                                        when (val result = RepositoryProvider.notificationRepository.markRead(item.id)) {
                                            is ApiResult.Success -> {
                                                val updatedItem = result.data.toNotificationCenterItemUiModel()
                                                uiState = uiState.replaceNotification(updatedItem)
                                                updatedItem
                                            }
                                            is ApiResult.Error -> {
                                                uiState = uiState.copy(
                                                    errorMessage = result.toBackendUiMessage("标记通知已读失败。"),
                                                )
                                                uiState = uiState.replaceNotification(item.copy(isRead = true))
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
}

@Composable
private fun NotificationCenterTopBar(
    unreadCount: Int,
    markAllReadEnabled: Boolean,
    onBack: () -> Unit,
    onMarkAllRead: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NotificationCircleButton(text = "<", onClick = onBack)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "通知",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (unreadCount > 0) {
                    "还有 $unreadCount 条未读"
                } else {
                    "当前都已读完"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            enabled = markAllReadEnabled,
            onClick = onMarkAllRead,
        ) {
            Text(text = "全部已读")
        }
    }
}

@Composable
private fun NotificationFilterRow(
    selectedFilter: NotificationCenterFilter,
    notifications: List<NotificationCenterItemUiModel>,
    onFilterSelected: (NotificationCenterFilter) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        NotificationCenterFilter.entries.forEach { filter ->
            NotificationFilterChip(
                filter = filter,
                unreadCount = notifications.unreadCount(filter),
                selected = filter == selectedFilter,
                onClick = { onFilterSelected(filter) },
            )
        }
    }
}

@Composable
private fun NotificationFilterChip(
    filter: NotificationCenterFilter,
    unreadCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(radius.capsule))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(radius.capsule),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = filter.label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (unreadCount > 0) {
                Surface(
                    shape = RoundedCornerShape(radius.capsule),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    },
                ) {
                    Text(
                        text = unreadCount.toString(),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (selected) Color.White else MaterialTheme.colorScheme.primary,
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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.xl))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(radius.xl),
        color = if (item.isRead) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
        },
        border = BorderStroke(
            1.dp,
            if (item.isRead) {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = item.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = item.targetSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.84f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationTypeChip(
    type: NotificationCenterItemType,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        shape = RoundedCornerShape(radius.capsule),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
    ) {
        Text(
            text = type.label,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary,
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

    Surface(
        shape = RoundedCornerShape(radius.capsule),
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (emphasized) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
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

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.4.dp)
            Text(
                text = "正在读取通知…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                modifier = Modifier.align(Alignment.End),
                onClick = onAction,
            ) {
                Text(text = actionLabel)
            }
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

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = if (filter == NotificationCenterFilter.ALL) {
                    "当前还没有通知"
                } else {
                    "当前分类下还没有通知"
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "等评论、内容更新或回收站变更出现后，这里会自动展示。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotificationCircleButton(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
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
    return when (val targetType = filter.targetType) {
        null -> this
        else -> filter { it.type == targetType }
    }
}

private fun List<NotificationCenterItemUiModel>.unreadCount(
    filter: NotificationCenterFilter,
): Int {
    return filterBy(filter).count { !it.isRead }
}

private fun formatNotificationTime(timeMillis: Long): String {
    return SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
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
