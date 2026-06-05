package com.example.yingshi.feature.photos

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.components.yingShiClickable
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

private enum class NotificationCategoryFilter(
    val label: String,
) {
    ALL("全部分类"),
    COMMENT("评论"),
    CONTENT_UPDATE("内容更新"),
    DELETE_RESTORE("删除 / 恢复"),
    SYSTEM("系统"),
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
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessionKey = realBackendSessionKey("notification-center-${route.source}")
    var selectedFilterName by rememberSaveable { mutableStateOf(NotificationCenterFilter.PHOTOS.name) }
    var selectedCategoryName by rememberSaveable { mutableStateOf(NotificationCategoryFilter.ALL.name) }
    var uiState by remember(sessionKey) {
        mutableStateOf(NotificationCenterUiState(isLoading = true))
    }
    val selectedFilter = NotificationCenterFilter.valueOf(selectedFilterName)
    val selectedCategory = NotificationCategoryFilter.valueOf(selectedCategoryName)
    val filteredNotifications = uiState.notifications
        .filterBy(selectedFilter)
        .filterBy(selectedCategory)
    val unreadCount = filteredNotifications.count { !it.isRead }

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
            .background(colors.appBackground)
            .statusBarsPadding()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        NotificationCenterTopBar(
            selectedFilter = selectedFilter,
            selectedCategory = selectedCategory,
            notifications = uiState.notifications,
            unreadCount = unreadCount,
            markAllReadEnabled = unreadCount > 0 && !uiState.isMutating,
            deleteAllEnabled = filteredNotifications.isNotEmpty() && !uiState.isMutating,
            onBack = onBack,
            onFilterSelected = { selectedFilterName = it.name },
            onCategorySelected = { selectedCategoryName = it.name },
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
            onDeleteAll = {
                val deleteIds = filteredNotifications.map { it.id }.toSet()
                if (deleteIds.isEmpty()) return@NotificationCenterTopBar
                uiState = uiState.copy(
                    notifications = uiState.notifications.filterNot { it.id in deleteIds },
                    errorMessage = null,
                )
                Toast.makeText(context, "已删除当前模块通知", Toast.LENGTH_SHORT).show()
            },
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
    selectedFilter: NotificationCenterFilter,
    selectedCategory: NotificationCategoryFilter,
    notifications: List<NotificationCenterItemUiModel>,
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
    var categoryExpanded by rememberSaveable { mutableStateOf(false) }
    var moduleExpanded by rememberSaveable { mutableStateOf(false) }

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
                icon = Icons.Default.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "通知",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
                Text(
                    text = if (unreadCount > 0) {
                        "${selectedFilter.label} · ${selectedCategory.label} · $unreadCount 条未读"
                    } else {
                        "${selectedFilter.label} · ${selectedCategory.label} · 已读完"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NotificationTopIconButton(
                    icon = Icons.Default.Menu,
                    contentDescription = "通知分类",
                    selected = categoryExpanded,
                    onClick = {
                        categoryExpanded = !categoryExpanded
                        if (categoryExpanded) moduleExpanded = false
                    },
                )
                NotificationTopIconButton(
                    icon = if (moduleExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "模块选择",
                    selected = moduleExpanded,
                    onClick = {
                        moduleExpanded = !moduleExpanded
                        if (moduleExpanded) categoryExpanded = false
                    },
                )
            }
        }

        AnimatedVisibility(visible = categoryExpanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                NotificationCategoryFilter.entries.forEach { category ->
                    NotificationCategoryOption(
                        category = category,
                        selected = category == selectedCategory,
                        unreadCount = notifications
                            .filterBy(selectedFilter)
                            .filterBy(category)
                            .count { !it.isRead },
                        onClick = {
                            onCategorySelected(category)
                            categoryExpanded = false
                        },
                    )
                }
            }
        }

        AnimatedVisibility(visible = moduleExpanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                NotificationCenterFilter.entries.forEach { filter ->
                    NotificationModuleOption(
                        filter = filter,
                        selected = filter == selectedFilter,
                        unreadCount = notifications.unreadCount(filter),
                        onClick = {
                            onFilterSelected(filter)
                            moduleExpanded = false
                        },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NotificationActionPill(
                text = "一键已读",
                enabled = markAllReadEnabled,
                modifier = Modifier.weight(1f),
                onClick = onMarkAllRead,
            )
            NotificationActionPill(
                text = "一键删除",
                enabled = deleteAllEnabled,
                modifier = Modifier.weight(1f),
                onClick = onDeleteAll,
            )
        }
    }
}

@Composable
private fun NotificationTopIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = Modifier
            .size(42.dp)
            .yingShiClickable(shape = RoundedCornerShape(16.dp), pressedScale = 0.94f, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) colors.primaryContainer.copy(alpha = 0.92f) else colors.sectionBackground.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, if (selected) colors.glassStroke.copy(alpha = 0.86f) else colors.dividerSoft.copy(alpha = 0.70f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun NotificationCategoryOption(
    category: NotificationCategoryFilter,
    selected: Boolean,
    unreadCount: Int,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .yingShiClickable(
                shape = RoundedCornerShape(radius.lg),
                pressedScale = 0.98f,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(radius.lg),
        color = if (selected) colors.softGreenContainer.copy(alpha = 0.66f) else colors.raisedSurface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, if (selected) colors.softGreenAction.copy(alpha = 0.24f) else colors.dividerSoft.copy(alpha = 0.46f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = category.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = colors.titleAccent,
            )
            if (unreadCount > 0) {
                Surface(
                    shape = RoundedCornerShape(radius.capsule),
                    color = colors.memoryContainer.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, colors.memoryAccent.copy(alpha = 0.18f)),
                ) {
                    Text(
                        text = unreadCount.toString(),
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
private fun NotificationModuleOption(
    filter: NotificationCenterFilter,
    selected: Boolean,
    unreadCount: Int,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .yingShiClickable(
                shape = RoundedCornerShape(radius.lg),
                pressedScale = 0.98f,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(radius.lg),
        color = if (selected) colors.primaryContainer.copy(alpha = 0.62f) else colors.raisedSurface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, if (selected) colors.glassStroke.copy(alpha = 0.70f) else colors.dividerSoft.copy(alpha = 0.46f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = filter.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = colors.titleAccent,
            )
            if (unreadCount > 0) {
                Surface(
                    shape = RoundedCornerShape(radius.capsule),
                    color = colors.memoryContainer.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, colors.memoryAccent.copy(alpha = 0.18f)),
                ) {
                    Text(
                        text = unreadCount.toString(),
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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.xl))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(radius.xl),
        color = if (item.isRead) {
            colors.raisedSurface.copy(alpha = 0.94f)
        } else {
            colors.primaryContainer.copy(alpha = 0.58f)
        },
        border = BorderStroke(
            1.dp,
            if (item.isRead) {
                colors.dividerSoft.copy(alpha = 0.54f)
            } else {
                colors.glassStroke.copy(alpha = 0.74f)
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
                            .background(colors.softGreenAction),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.titleAccent,
                    )
                    Text(
                        text = item.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Text(
                        text = item.targetSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.isRead) {
                            colors.titleAccent.copy(alpha = 0.82f)
                        } else {
                            colors.softGreenAction
                        },
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
            colors.softGreenContainer
        } else {
            colors.sectionBackground.copy(alpha = 0.58f)
        },
        border = if (emphasized) {
            BorderStroke(1.dp, colors.softGreenAction.copy(alpha = 0.22f))
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
                colors.softGreenAction
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
            .size(42.dp)
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
        color = if (enabled) colors.softGreenContainer.copy(alpha = 0.90f) else colors.sectionBackground.copy(alpha = 0.52f),
        border = BorderStroke(1.dp, if (enabled) colors.softGreenAction.copy(alpha = 0.26f) else colors.dividerSoft.copy(alpha = 0.56f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) colors.softGreenAction else colors.textSecondary,
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

private fun List<NotificationCenterItemUiModel>.filterBy(
    category: NotificationCategoryFilter,
): List<NotificationCenterItemUiModel> {
    return if (category == NotificationCategoryFilter.ALL) {
        this
    } else {
        filter { it.type.name == category.name }
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
