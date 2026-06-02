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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private data class NotificationDetailUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val item: NotificationCenterItemUiModel? = null,
)

@Composable
fun NotificationDetailScreen(
    route: NotificationDetailRoute,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val coroutineScope = rememberCoroutineScope()
    val sessionKey = realBackendSessionKey("notification-detail-${route.notificationId}")
    var uiState by remember(sessionKey, route.notificationId) {
        mutableStateOf(NotificationDetailUiState(isLoading = true))
    }

    fun refresh(markRead: Boolean) {
        coroutineScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            when (val result = RepositoryProvider.notificationRepository.getNotification(route.notificationId)) {
                is ApiResult.Success -> {
                    val loadedItem = result.data.toNotificationCenterItemUiModel()
                    uiState = NotificationDetailUiState(
                        isLoading = false,
                        item = loadedItem,
                    )
                    if (markRead && !loadedItem.isRead) {
                        when (val readResult = RepositoryProvider.notificationRepository.markRead(route.notificationId)) {
                            is ApiResult.Success -> {
                                uiState = uiState.copy(
                                    item = readResult.data.toNotificationCenterItemUiModel(),
                                )
                            }
                            is ApiResult.Error -> {
                                uiState = uiState.copy(
                                    errorMessage = readResult.toBackendUiMessage("标记通知已读失败。"),
                                )
                            }
                            ApiResult.Loading -> Unit
                        }
                    }
                }
                is ApiResult.Error -> {
                    uiState = NotificationDetailUiState(
                        isLoading = false,
                        errorMessage = result.toBackendUiMessage("读取通知详情失败，请稍后重试。"),
                    )
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    LaunchedEffect(sessionKey, route.notificationId) {
        refresh(markRead = true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        NotificationDetailTopBar(onBack = onBack)

        when {
            uiState.isLoading && uiState.item == null -> {
                NotificationDetailLoadingState()
            }

            uiState.item != null -> {
                NotificationDetailPrimaryCard(item = requireNotNull(uiState.item))
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(radius.capsule),
                    color = colors.primaryContainer.copy(alpha = 0.58f),
                    border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.42f)),
                ) {
                    Text(
                        text = item.type.label,
                        modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.titleAccent,
                    )
                }
                Text(
                    text = formatNotificationDetailTime(item.createdAtMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
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
        }
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
