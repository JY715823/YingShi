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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    var showClearCompletedDialog by rememberSaveable { mutableStateOf(false) }

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
                    text = "清理完成记录",
                    onClick = { showClearCompletedDialog = true },
                )
            } else {
                Box(modifier = Modifier.size(72.dp))
            }
        }
        if (showClearCompletedDialog) {
            TransferClearRecordsDialog(
                title = "清理已完成的传输记录？",
                body = "只会从传输中心移除已结束的任务记录，不会删除已导入 App 的媒体、已创建的帖子，或已加入帖子里的媒体。",
                onDismiss = { showClearCompletedDialog = false },
                onConfirm = {
                    showClearCompletedDialog = false
                    operationGroups
                        .filter { group -> group.all { it.isTerminal } }
                        .flatten()
                        .forEach { task -> LocalSystemMediaBridgeRepository.dismissUploadTask(task.taskId) }
                },
            )
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
    val openTargetTask = tasks.openTargetTask()
    val canOpen = openTargetTask != null
    val failedTasks = tasks.filter { it.canRetry }
    val runningTasks = tasks.filterNot { it.isTerminal }
    val allTerminal = tasks.all { it.isTerminal }
    val successCount = tasks.count { it.state == UploadState.SUCCESS }
    val failureCount = tasks.count { it.state == UploadState.FAILURE }
    val cancelledCount = tasks.count { it.state == UploadState.CANCELLED }
    val problemTasks = tasks
        .filter { it.state == UploadState.FAILURE || it.state == UploadState.CANCELLED || it.canRetry }
        .distinctBy { it.taskId }
    val totalCount = primaryTask.operationMediaCount.coerceAtLeast(tasks.size)
    val averageProgress = if (tasks.isEmpty()) {
        0
    } else {
        tasks.map { it.progressPercent.coerceIn(0, 100) }.average().toInt()
    }
    var showFailureDetails by rememberSaveable(primaryTask.operationId) { mutableStateOf(false) }
    var showClearGroupDialog by rememberSaveable(primaryTask.operationId) { mutableStateOf(false) }

    LaunchedEffect(problemTasks.size, runningTasks.size) {
        if (showFailureDetails && (problemTasks.isEmpty() || runningTasks.isNotEmpty())) {
            showFailureDetails = false
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .let { base ->
                val target = openTargetTask
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
                        text = operationStateLabel(
                            tasks = tasks,
                            successCount = successCount,
                            failureCount = failureCount,
                            cancelledCount = cancelledCount,
                        ),
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
                if (problemTasks.isNotEmpty()) {
                    TextButton(
                        onClick = { showFailureDetails = !showFailureDetails },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(if (showFailureDetails) "收起失败详情" else "查看失败详情")
                    }
                }
                if (showFailureDetails && problemTasks.isNotEmpty()) {
                    TransferFailureDetails(
                        tasks = tasks,
                        problemTasks = problemTasks,
                        successCount = successCount,
                        failureCount = failureCount,
                        cancelledCount = cancelledCount,
                    )
                }
                if (allTerminal) {
                    Text(
                        text = "清理记录只会移除这条传输记录，不会删除已导入媒体或帖子内容。",
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
                    TextButton(onClick = {
                        showFailureDetails = false
                        onRetryTask(task.taskId)
                    }) {
                        Text(if (failedTasks.size > 1) "重试失败项" else "重试")
                    }
                }
                runningTasks.firstOrNull()?.let { task ->
                    TextButton(onClick = { onCancelTask(task.taskId) }) {
                        Text(if (runningTasks.size > 1) "取消当前项" else "取消")
                    }
                }
                if (allTerminal) {
                    openTargetTask?.let { target ->
                        TextButton(onClick = { onOpen(target) }) {
                            Text(when (target.operationType) {
                                LocalSystemMediaBridgeRepository.OperationType.CREATE_POST -> "查看新帖子"
                                LocalSystemMediaBridgeRepository.OperationType.ADD_TO_EXISTING_POST -> "查看目标帖子"
                                LocalSystemMediaBridgeRepository.OperationType.IMPORT_TO_APP -> "查看照片"
                            })
                        }
                    }
                    TextButton(onClick = { showClearGroupDialog = true }) {
                        Text("清理本组记录")
                    }
                } else if (canOpen) {
                    openTargetTask?.let { target ->
                        TextButton(onClick = { onOpen(target) }) {
                            Text("查看结果")
                        }
                    }
                }
            }
        }
    }
    if (showClearGroupDialog) {
        TransferClearRecordsDialog(
            title = "清理这组传输记录？",
            body = "只会从传输中心移除本组记录，不会删除已导入 App 的媒体、已创建的帖子，或已加入帖子里的媒体。",
            onDismiss = { showClearGroupDialog = false },
            onConfirm = {
                showClearGroupDialog = false
                tasks.forEach { onClearTask(it.taskId) }
            },
        )
    }
}

@Composable
private fun TransferClearRecordsDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("清理记录")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun TransferFailureDetails(
    tasks: List<SystemMediaUploadTaskUiModel>,
    problemTasks: List<SystemMediaUploadTaskUiModel>,
    successCount: Int,
    failureCount: Int,
    cancelledCount: Int,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val primaryTask = tasks.first()
    val resultTask = tasks.firstOrNull { it.resultPostRoute != null } ?: primaryTask

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = "失败与重试说明",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = failureRetryExplanation(
                    task = resultTask,
                    successCount = successCount,
                    failureCount = failureCount,
                    cancelledCount = cancelledCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            problemTasks.forEach { task ->
                TransferFailureDetailLine(task = task)
            }
        }
    }
}

@Composable
private fun TransferFailureDetailLine(task: SystemMediaUploadTaskUiModel) {
    val spacing = YingShiThemeTokens.spacing
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(
            text = task.fileName,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${task.mediaType.transferLabel()} · ${failureCurrentStateLabel(task)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = failureReasonLabel(task),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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

private fun List<SystemMediaUploadTaskUiModel>.openTargetTask(): SystemMediaUploadTaskUiModel? {
    val primaryTask = firstOrNull() ?: return null
    return when (primaryTask.operationType) {
        LocalSystemMediaBridgeRepository.OperationType.CREATE_POST,
        LocalSystemMediaBridgeRepository.OperationType.ADD_TO_EXISTING_POST,
        -> firstOrNull { it.resultPostRoute != null }
        LocalSystemMediaBridgeRepository.OperationType.IMPORT_TO_APP -> firstOrNull {
            it.state == UploadState.SUCCESS && !it.resultMediaId.isNullOrBlank()
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

private fun failureRetryExplanation(
    task: SystemMediaUploadTaskUiModel,
    successCount: Int,
    failureCount: Int,
    cancelledCount: Int,
): String {
    val countPrefix = "已成功 $successCount 项，失败 $failureCount 项，取消 $cancelledCount 项。"
    val actionText = when (task.operationType) {
        LocalSystemMediaBridgeRepository.OperationType.CREATE_POST -> {
            if (task.resultPostRoute != null) {
                "帖子已创建，成功项已保留；失败项可在传输中心重试，不会重复创建已成功内容。"
            } else {
                "成功项已保留；失败项可在传输中心重试，不会重复创建已成功内容。"
            }
        }
        LocalSystemMediaBridgeRepository.OperationType.ADD_TO_EXISTING_POST ->
            "目标帖子是「${task.operationTitle ?: task.targetLabel}」。成功项已保留；失败项可在传输中心重试，不会重复加入已成功内容。"
        LocalSystemMediaBridgeRepository.OperationType.IMPORT_TO_APP ->
            "成功项已进入 App；失败项可在传输中心重试，不会重复导入已成功内容。"
    }
    return countPrefix + actionText
}

private fun SystemMediaType.transferLabel(): String {
    return when (this) {
        SystemMediaType.IMAGE -> "图片"
        SystemMediaType.VIDEO -> "视频"
    }
}

private fun failureCurrentStateLabel(task: SystemMediaUploadTaskUiModel): String {
    return when (task.state) {
        UploadState.WAITING -> "等待重试"
        UploadState.UPLOADING -> "正在处理 ${task.progressPercent}%"
        UploadState.SUCCESS -> "已成功"
        UploadState.FAILURE -> "失败"
        UploadState.CANCELLED -> "已取消"
    }
}

private fun failureReasonLabel(task: SystemMediaUploadTaskUiModel): String {
    task.errorMessage?.takeIf { it.isNotBlank() }?.let { return it }
    task.statusMessage?.takeIf { it.isNotBlank() }?.let { return it }
    return when (task.state) {
        UploadState.CANCELLED -> "任务已取消，未继续上传。"
        UploadState.FAILURE -> "上传失败，可重试。"
        else -> "当前项需要重试。"
    }
}

private fun operationStateLabel(
    tasks: List<SystemMediaUploadTaskUiModel>,
    successCount: Int,
    failureCount: Int,
    cancelledCount: Int,
): String {
    val terminalCount = tasks.count { it.isTerminal }
    val primaryTask = tasks.first()
    val targetLabel = primaryTask.operationTitle ?: primaryTask.targetLabel
    val countText = "成功 $successCount，失败 $failureCount，取消 $cancelledCount"
    val hasCancelled = cancelledCount > 0
    val hasFailure = failureCount > 0
    val hasSuccess = successCount > 0
    return when {
        tasks.any { it.state == UploadState.UPLOADING } && (hasFailure || hasCancelled) ->
            "正在重试失败项：已处理 ${terminalCount}/${tasks.size} 项"
        tasks.any { it.state == UploadState.UPLOADING } -> "正在处理 ${terminalCount}/${tasks.size} 项"
        tasks.any { it.state == UploadState.WAITING } && (hasFailure || hasCancelled) ->
            "正在重试失败项：等待处理 ${terminalCount}/${tasks.size} 项"
        tasks.any { it.state == UploadState.WAITING } -> "等待处理 ${terminalCount}/${tasks.size} 项"
        cancelledCount == tasks.size -> "已取消：未完成项不会继续处理，可清理传输记录。"
        hasCancelled && hasSuccess && !hasFailure -> "部分完成后取消：$countText。成功项已保留，未完成项已取消。"
        hasFailure || hasCancelled -> when (primaryTask.operationType) {
            LocalSystemMediaBridgeRepository.OperationType.IMPORT_TO_APP ->
                "部分导入完成：$countText。成功项已保留，失败或取消项可重试。"
            LocalSystemMediaBridgeRepository.OperationType.CREATE_POST -> {
                if (tasks.any { it.resultPostRoute != null }) {
                    "帖子已创建：$countText。成功项已保留，失败或取消项可重试。"
                } else {
                    "帖子未完整创建：$countText。失败或取消项可重试。"
                }
            }
            LocalSystemMediaBridgeRepository.OperationType.ADD_TO_EXISTING_POST ->
                "已加入「$targetLabel」：$countText。成功项已保留，失败或取消项可重试。"
        }
        successCount == tasks.size -> when (primaryTask.operationType) {
            LocalSystemMediaBridgeRepository.OperationType.IMPORT_TO_APP ->
                "导入完成：成功 $successCount 项，可查看照片"
            LocalSystemMediaBridgeRepository.OperationType.CREATE_POST ->
                "帖子创建完成：成功 $successCount 项，可查看新帖子"
            LocalSystemMediaBridgeRepository.OperationType.ADD_TO_EXISTING_POST ->
                "已加入「$targetLabel」：成功 $successCount 项，可查看目标帖子"
        }
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
