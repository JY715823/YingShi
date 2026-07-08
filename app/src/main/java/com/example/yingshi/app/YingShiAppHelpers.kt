@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.yingshi.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.example.yingshi.feature.photos.LocalSystemMediaBridgeRepository
import com.example.yingshi.feature.photos.PhotoThumbnailPalette
import com.example.yingshi.feature.photos.PostDetailPlaceholderRoute
import com.example.yingshi.feature.photos.SystemMediaUploadTaskUiModel
import com.example.yingshi.feature.photos.TrashEntryType
import com.example.yingshi.feature.photos.parseTrashEntryTypeOrNull
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import com.example.yingshi.feature.photos.NotificationCenterItemType
import com.example.yingshi.feature.photos.NotificationCenterItemUiModel

// ---- Auth checking overlay ----

@Composable
internal fun AuthCheckingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(YingShiThemeTokens.spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.md),
        ) {
            CircularProgressIndicator()
            Text(
                text = "正在校验登录状态...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- Transfer toast messages ----

internal fun transferToastMessage(event: LocalSystemMediaBridgeRepository.OperationResultEvent): String {
    val shouldAutoOpenResult = event.shouldAutoOpenResult
    val totalCount = event.totalCount
    val successCount = event.successCount
    val failureCount = event.failureCount
    val cancelledCount = event.cancelledCount
    val succeeded = event.succeeded
    val operationType = event.operationType
    if (shouldAutoOpenResult && totalCount > successCount) {
        return ""
    }
    val hasUnfinishedItems = failureCount > 0 || cancelledCount > 0
    val isPartial = successCount > 0 && hasUnfinishedItems
    return when (operationType) {
        LocalSystemMediaBridgeRepository.OperationType.IMPORT_TO_APP -> when {
            isPartial -> "部分导入完成"
            succeeded && successCount > 0 -> "导入完成"
            cancelledCount > 0 && failureCount == 0 -> "导入已取消"
            else -> "导入失败，可重试"
        }
        LocalSystemMediaBridgeRepository.OperationType.CREATE_POST -> when {
            isPartial -> "小相册部分创建完成"
            succeeded && successCount > 0 -> "小相册创建完成"
            cancelledCount > 0 && failureCount == 0 -> "小相册创建已取消"
            else -> "小相册创建失败，可重试"
        }
        LocalSystemMediaBridgeRepository.OperationType.ADD_TO_EXISTING_POST -> when {
            isPartial -> "部分加入成功"
            succeeded && successCount > 0 -> "已加入小相册"
            cancelledCount > 0 && failureCount == 0 -> "加入已取消"
            else -> "加入失败，可重试"
        }
    }
}

// ---- Extension functions ----

internal fun SystemMediaUploadTaskUiModel.successfulResultMediaIdsInOperation(): List<String> {
    val taskOperationId = operationId
    val ids = LocalSystemMediaBridgeRepository.uploadTasks
        .filter { task -> task.operationId == taskOperationId }
        .mapNotNull { task -> task.resultMediaId?.takeIf { it.isNotBlank() } }
        .distinct()
    return ids.ifEmpty { resultMediaId?.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty() }
}

internal fun NotificationCenterItemUiModel.toNotificationPostRoute(): PostDetailPlaceholderRoute {
    val resolvedPostId = requireNotNull(postId)
    val syntheticAlbumId = "notification-entry"
    return PostDetailPlaceholderRoute(
        postId = resolvedPostId,
        albumId = syntheticAlbumId,
        albumIds = listOf(syntheticAlbumId),
        title = targetSummary.ifBlank { title },
        summary = body,
        postDisplayTimeMillis = createdAtMillis,
        mediaCount = if (mediaId.isNullOrBlank()) 0 else 1,
        coverPalette = NotificationPlaceholderPalette,
        entryNotice = "从通知进入",
        highlightMediaIds = mediaId?.let(::listOf).orEmpty(),
        focusMediaId = mediaId,
        autoOpenComment = type == NotificationCenterItemType.COMMENT,
    )
}

internal fun pushSmallAlbumFallbackRoute(postId: String): PostDetailPlaceholderRoute {
    val syntheticAlbumId = "push-entry"
    return PostDetailPlaceholderRoute(
        postId = postId,
        albumId = syntheticAlbumId,
        albumIds = listOf(syntheticAlbumId),
        title = "小相册",
        summary = "正在同步详情",
        postDisplayTimeMillis = System.currentTimeMillis(),
        mediaCount = 0,
        coverPalette = NotificationPlaceholderPalette,
        entryNotice = "从推送进入",
    )
}

internal fun String?.toTrashEntryTypeOrNull(): TrashEntryType? {
    return parseTrashEntryTypeOrNull(this)
}

// ---- Constants ----

internal val NotificationPlaceholderPalette = PhotoThumbnailPalette(
    start = Color(0xFFE9E2D8),
    end = Color(0xFFD7C6BB),
    accent = Color(0xFF8C6C59),
)

// ---- Utility ----

internal tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

// ---- Preview ----

@Preview(showBackground = true)
@Composable
private fun YingShiAppPreview() {
    YingShiTheme {
        YingShiApp()
    }
}
