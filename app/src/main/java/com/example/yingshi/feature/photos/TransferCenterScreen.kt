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
import androidx.compose.ui.text.style.TextOverflow
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
    val operationGroups = tasks
        .groupBy { it.operationId }
        .values
        .map { it.sortedByDescending(SystemMediaUploadTaskUiModel::taskId) }
        .sortedByDescending { group -> group.maxOf { it.taskId } }
    val completedGroups = operationGroups.count { group -> group.all { it.isTerminal } }
    val runningGroups = operationGroups.size - completedGroups

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
            TransferCenterHeaderButton(text = "返回", onClick = onBack)
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "传输中心",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = if (tasks.isEmpty()) {
                        "暂无传输任务"
                    } else {
                        "进行中 $runningGroups · 已完成 $completedGroups"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (completedGroups > 0) {
                TransferCenterHeaderButton(
                    text = "清空",
                    onClick = {
                        operationGroups
                            .filter { group -> group.all { it.isTerminal } }
                            .flatten()
                            .forEach { task -> LocalSystemMediaBridgeRepository.dismissUploadTask(task.taskId) }
                    },
                )
            } else {
                Box(modifier = Modifier.size(72.dp))
            }
        }

        if (tasks.isEmpty()) {
            TransferEmptyState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                items(
                    items = operationGroups,
                    key = { group -> group.first().operationId },
                ) { group ->
                    TransferOperationCard(
                        tasks = group,
                        onRetryTask = { taskId -> LocalSystemMediaBridgeRepository.retryUploadTask(context, taskId) },
                        onCancelTask = LocalSystemMediaBridgeRepository::cancelUploadTask,
                        onClearTask = LocalSystemMediaBridgeRepository::dismissUploadTask,
                        onOpen = { onOpenTaskMedia(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferEmptyState() {
    val spacing = YingShiThemeTokens.spacing
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
                text = "导入 App、新建帖子、加入已有帖子后的进度和结果会显示在这里。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransferOperationCard(
    tasks: List<SystemMediaUploadTaskUiModel>,
    onRetryTask: (String) -> Unit,
    onCancelTask: (String) -> Unit,
    onClearTask: (String) -> Unit,
    onOpen: (SystemMediaUploadTaskUiModel) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val primaryTask = tasks.first()
    val canOpen = primaryTask.openTargetTask() != null
    val failedTasks = tasks.filter { it.canRetry }
    val runningTasks = tasks.filterNot { it.isTerminal }
    val allTerminal = tasks.all { it.isTerminal }
    val successCount = tasks.count { it.state == UploadState.SUCCESS }
    val failureCount = tasks.count { it.state == UploadState.FAILURE }
    val cancelledCount = tasks.count { it.state == UploadState.CANCELLED }
    val totalCount = primaryTask.operationMediaCount.coerceAtLeast(tasks.size)
    val averageProgress = if (tasks.isEmpty()) {
        0
    } else {
        tasks.map { it.progressPercent.coerceIn(0, 100) }.average().toInt()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .let { base ->
                val target = primaryTask.openTargetTask()
                if (target != null) base.clickable { onOpen(target) } else base
            },
        shape = RoundedCornerShape(radius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(
                modifier = Modifier.padding(spacing.md),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransferTaskThumbnail(task = primaryTask)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    Text(
                        text = primaryTask.operationType.label(),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = primaryTask.operationTitle ?: primaryTask.targetLabel,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "媒体 $totalCount 项 · 成功 $successCount · 失败 $failureCount · 取消 $cancelledCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        text = operationStateLabel(tasks),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    LinearProgressIndicator(
                        progress = { averageProgress / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                tasks.take(3).forEach { task ->
                    TransferTaskLine(task = task)
                }
                if (tasks.size > 3) {
                    Text(
                        text = "还有 ${tasks.size - 3} 项媒体任务",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                failedTasks.firstOrNull()?.let { task ->
                    TextButton(onClick = { onRetryTask(task.taskId) }) {
                        Text(if (failedTasks.size > 1) "重试失败项" else "重试")
                    }
                }
                runningTasks.firstOrNull()?.let { task ->
                    TextButton(onClick = { onCancelTask(task.taskId) }) {
                        Text(if (runningTasks.size > 1) "取消当前项" else "取消")
                    }
                }
                if (allTerminal) {
                    primaryTask.openTargetTask()?.let { target ->
                        TextButton(onClick = { onOpen(target) }) {
                            Text(when (target.operationType) {
                                LocalSystemMediaBridgeRepository.OperationType.CREATE_POST -> "查看新帖子"
                                LocalSystemMediaBridgeRepository.OperationType.ADD_TO_EXISTING_POST -> "查看目标帖子"
                                LocalSystemMediaBridgeRepository.OperationType.IMPORT_TO_APP -> "查看照片"
                            })
                        }
                    }
                    TextButton(onClick = { tasks.forEach { onClearTask(it.taskId) } }) {
                        Text("清理")
                    }
                } else if (canOpen) {
                    primaryTask.openTargetTask()?.let { target ->
                        TextButton(onClick = { onOpen(target) }) {
                            Text("查看结果")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferTaskLine(task: SystemMediaUploadTaskUiModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = task.fileName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = taskStateLabel(task),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
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
        if (previewUri != null) {
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
            if (task.mediaType == SystemMediaType.VIDEO) {
                VideoBadge()
            }
        } else {
            PlaceholderBadge(task = task)
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
                text = ">",
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

private fun SystemMediaUploadTaskUiModel.openTargetTask(): SystemMediaUploadTaskUiModel? {
    return when (operationType) {
        LocalSystemMediaBridgeRepository.OperationType.CREATE_POST,
        LocalSystemMediaBridgeRepository.OperationType.ADD_TO_EXISTING_POST,
        -> takeIf { resultPostRoute != null }
        LocalSystemMediaBridgeRepository.OperationType.IMPORT_TO_APP -> takeIf {
            state == UploadState.SUCCESS && !resultMediaId.isNullOrBlank()
        }
    }
}

private fun LocalSystemMediaBridgeRepository.OperationType.label(): String {
    return when (this) {
        LocalSystemMediaBridgeRepository.OperationType.IMPORT_TO_APP -> "导入 App"
        LocalSystemMediaBridgeRepository.OperationType.CREATE_POST -> "新建帖子"
        LocalSystemMediaBridgeRepository.OperationType.ADD_TO_EXISTING_POST -> "加入已有帖子"
    }
}

private fun operationStateLabel(tasks: List<SystemMediaUploadTaskUiModel>): String {
    val terminalCount = tasks.count { it.isTerminal }
    val successCount = tasks.count { it.state == UploadState.SUCCESS }
    val failureCount = tasks.count { it.state == UploadState.FAILURE }
    val cancelledCount = tasks.count { it.state == UploadState.CANCELLED }
    return when {
        tasks.any { it.state == UploadState.UPLOADING } -> "正在处理 ${terminalCount}/${tasks.size} 项"
        tasks.any { it.state == UploadState.WAITING } -> "等待处理 ${terminalCount}/${tasks.size} 项"
        failureCount > 0 || cancelledCount > 0 -> "部分完成：成功 $successCount，失败 $failureCount，取消 $cancelledCount。成功结果仍可查看，失败项可重试。"
        successCount == tasks.size -> "全部完成，可查看结果"
        else -> "任务已更新"
    }
}

private fun taskStateLabel(task: SystemMediaUploadTaskUiModel): String {
    if (!task.statusMessage.isNullOrBlank()) return task.statusMessage
    return when (task.state) {
        UploadState.WAITING -> "等待"
        UploadState.UPLOADING -> "${task.progressPercent}%"
        UploadState.SUCCESS -> "成功"
        UploadState.FAILURE -> task.errorMessage ?: "失败"
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
