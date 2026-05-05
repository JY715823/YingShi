package com.example.yingshi.feature.photos

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.yingshi.data.model.UploadState
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
fun TransferCenterScreen(
    route: TransferCenterRoute,
    onBack: () -> Unit,
    onOpenTaskMedia: (SystemMediaUploadTaskUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val context = LocalContext.current
    val tasks = LocalSystemMediaBridgeRepository.uploadTasks
    val orderedTasks = tasks.asReversed()
    val completedTasks = orderedTasks.count { it.isTerminal }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransferCenterHeaderButton(
                text = "返回",
                onClick = onBack,
            )
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "传输中心",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = if (tasks.isEmpty()) "暂无传输任务" else "进行中 ${tasks.count { !it.isTerminal }} · 已完成 $completedTasks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val hasCompletedTasks = completedTasks > 0
            if (hasCompletedTasks) {
                TransferCenterHeaderButton(
                    text = "清空",
                    onClick = {
                        orderedTasks.filter { it.isTerminal }.forEach { task ->
                            LocalSystemMediaBridgeRepository.dismissUploadTask(task.taskId)
                        }
                    },
                )
            } else {
                Box(modifier = Modifier.size(72.dp))
            }
        }

        if (tasks.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
            ) {
                Column(
                    modifier = Modifier.padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Text(
                        text = "没有传输任务",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "上传、导入、发帖和加入帖子后的任务会显示在这里。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                items(
                    items = orderedTasks,
                    key = SystemMediaUploadTaskUiModel::taskId,
                ) { task ->
                    TransferTaskCard(
                        task = task,
                        onRetry = { LocalSystemMediaBridgeRepository.retryUploadTask(context, task.taskId) },
                        onCancel = { LocalSystemMediaBridgeRepository.cancelUploadTask(task.taskId) },
                        onClear = { LocalSystemMediaBridgeRepository.dismissUploadTask(task.taskId) },
                        onOpen = { onOpenTaskMedia(task) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferTaskCard(
    task: SystemMediaUploadTaskUiModel,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    onOpen: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val canOpen = task.state == UploadState.SUCCESS && !task.resultMediaId.isNullOrBlank()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .let { base ->
                if (canOpen) {
                    base.clickable(onClick = onOpen)
                } else {
                    base
                }
            },
        shape = RoundedCornerShape(radius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(spacing.md),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransferTaskThumbnail(task = task)

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    Text(
                        text = task.fileName,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = "${task.targetLabel} · ${taskStateLabel(task)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    if (task.resultMediaId != null) {
                        Text(
                            text = "媒体 ID：${task.resultMediaId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (task.progressPercent.coerceIn(0, 100)) / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (task.canRetry) {
                    TextButton(onClick = onRetry) {
                        Text("重试")
                    }
                }
                if (!task.isTerminal) {
                    TextButton(onClick = onCancel) {
                        Text("取消")
                    }
                } else {
                    if (canOpen) {
                        TextButton(onClick = onOpen) {
                            Text("查看")
                        }
                    }
                    TextButton(onClick = onClear) {
                        Text("清理")
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferTaskThumbnail(task: SystemMediaUploadTaskUiModel) {
    val context = LocalContext.current
    val radius = YingShiThemeTokens.radius
    val previewUri = task.previewUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
    val backgroundColor = if (task.mediaType == SystemMediaType.VIDEO) {
        Color(0xFF52627A)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(radius.md))
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        if (previewUri != null && task.mediaType == SystemMediaType.IMAGE) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(previewUri)
                    .precision(Precision.EXACT)
                    .crossfade(true)
                    .build(),
                contentDescription = task.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else if (previewUri != null && task.mediaType == SystemMediaType.VIDEO) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(previewUri)
                    .precision(Precision.EXACT)
                    .crossfade(true)
                    .build(),
                contentDescription = task.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            VideoBadge()
        } else {
            PlaceholderBadge(task = task)
        }

        if (task.mediaType == SystemMediaType.VIDEO && previewUri == null) {
            VideoBadge()
        }
    }
}

@Composable
private fun PlaceholderBadge(task: SystemMediaUploadTaskUiModel) {
    Text(
        text = if (task.mediaType == SystemMediaType.VIDEO) "视频" else "图片",
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = Color.White,
    )
}

@Composable
private fun VideoBadge() {
    Surface(
        modifier = Modifier.size(28.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.38f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "▶",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun TransferCenterHeaderButton(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .semantics { contentDescription = text }
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun taskStateLabel(task: SystemMediaUploadTaskUiModel): String {
    if (!task.statusMessage.isNullOrBlank()) return task.statusMessage
    return when (task.state) {
        UploadState.WAITING -> "等待上传"
        UploadState.UPLOADING -> "正在上传 ${task.progressPercent}%"
        UploadState.SUCCESS -> "上传成功"
        UploadState.FAILURE -> task.errorMessage ?: "上传失败"
        UploadState.CANCELLED -> "已取消"
    }
}

@Preview(showBackground = true)
@Composable
private fun TransferCenterScreenPreview() {
    YingShiTheme(darkTheme = true) {
        TransferCenterScreen(
            route = TransferCenterRoute(),
            onBack = { },
            onOpenTaskMedia = { },
        )
    }
}
