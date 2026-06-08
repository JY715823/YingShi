package com.example.yingshi.feature.photos

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.yingshi.ui.components.YingShiMistBackground
import com.example.yingshi.ui.components.YingShiNotice
import com.example.yingshi.ui.components.YingShiNoticeHost
import com.example.yingshi.ui.components.YingShiNoticeTone
import com.example.yingshi.ui.components.YingShiStateLayer
import com.example.yingshi.ui.components.yingShiClickable
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
    val colors = YingShiThemeTokens.colors
    var noticeNonce by rememberSaveable { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<YingShiNotice?>(null) }
    fun showNotice(message: String, tone: YingShiNoticeTone = YingShiNoticeTone.INFO) {
        noticeNonce += 1
        notice = YingShiNotice(message = message, tone = tone, nonce = noticeNonce)
    }

    YingShiMistBackground(modifier = modifier, showWaves = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransferCircleButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "传输中心",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.titleAccent,
                    )
                    Text(
                        text = if (tasks.isEmpty()) {
                            "暂无传输任务"
                        } else {
                            "进行中 $runningGroups · 已完成 $completedGroups"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                if (completedGroups > 0) {
                    TransferActionPill(
                        text = "清理",
                        icon = Icons.Default.Delete,
                        onClick = { showClearCompletedDialog = true },
                    )
                } else {
                    Box(modifier = Modifier.size(40.dp))
                }
            }
            if (showClearCompletedDialog) {
                TransferClearRecordsDialog(
                    title = "清理已完成的传输记录？",
                    body = "只会从传输中心移除已结束的任务记录，不会删除已导入照片流的媒体、已创建的小相册，或已加入小相册里的媒体。",
                    onDismiss = { showClearCompletedDialog = false },
                    onConfirm = {
                        showClearCompletedDialog = false
                        operationGroups
                            .filter { group -> group.all { it.isTerminal } }
                            .flatten()
                            .forEach { task -> LocalSystemMediaBridgeRepository.dismissUploadTask(task.taskId) }
                        showNotice("已清理完成的传输记录", YingShiNoticeTone.SUCCESS)
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
                            onRetryOperation = { operationId ->
                                val retriedCount = LocalSystemMediaBridgeRepository
                                    .retryUploadOperation(context, operationId)
                                if (retriedCount > 0) {
                                    showNotice(
                                        if (retriedCount > 1) {
                                            "已重试 $retriedCount 项失败任务"
                                        } else {
                                            "已重新加入传输队列"
                                        },
                                        YingShiNoticeTone.SUCCESS,
                                    )
                                } else {
                                    showNotice("当前没有可重试的任务", YingShiNoticeTone.WARNING)
                                }
                            },
                            onCancelOperation = {
                                LocalSystemMediaBridgeRepository.cancelUploadOperation(it)
                                showNotice("已取消传输任务")
                            },
                            onClearTask = {
                                LocalSystemMediaBridgeRepository.dismissUploadTask(it)
                                showNotice("已移除传输记录")
                            },
                            onOpen = { onOpenTaskMedia(it) },
                        )
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
}

@Composable
private fun TransferEmptyState() {
    YingShiStateLayer(
        title = "没有传输任务",
        body = "导入照片流、新建小相册、加入小相册后的进度会显示在这里。",
        tone = YingShiNoticeTone.INFO,
    )
}

@Composable
private fun TransferOperationCard(
    tasks: List<SystemMediaUploadTaskUiModel>,
    onRetryOperation: (String) -> Unit,
    onCancelOperation: (String) -> Unit,
    onClearTask: (String) -> Unit,
    onOpen: (SystemMediaUploadTaskUiModel) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val primaryTask = tasks.first()
    val openTargetTask = tasks.openTargetTask()
    val canOpen = openTargetTask != null
    val retryableTasks = tasks.filter { it.canRetry }
    val runningTasks = tasks.filterNot { it.isTerminal }
    val allTerminal = tasks.all { it.isTerminal }
    val successCount = tasks.count { it.state == UploadState.SUCCESS }
    val failureCount = tasks.count { it.state == UploadState.FAILURE }
    val cancelledCount = tasks.count { it.state == UploadState.CANCELLED }
    val problemTasks = tasks
        .filter { it.state == UploadState.FAILURE || it.state == UploadState.CANCELLED || it.canRetry }
        .distinctBy { it.taskId }
    val totalCount = primaryTask.operationMediaCount.coerceAtLeast(tasks.size)
    val operationProgress = calculateTransferOperationProgressPercent(
        tasks = tasks,
        totalCount = totalCount,
    )
    val processedCount = calculateTransferOperationProcessedCount(
        tasks = tasks,
        totalCount = totalCount,
    )
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
                if (target != null) {
                    base.yingShiClickable(shape = RoundedCornerShape(radius.lg), onClick = { onOpen(target) })
                } else {
                    base
                }
            },
        shape = RoundedCornerShape(radius.lg),
        color = colors.raisedSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.62f)),
        shadowElevation = 0.dp,
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
                        color = colors.softGreenAction,
                    )
                    Text(
                        text = primaryTask.operationTitle ?: primaryTask.targetLabel,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.titleAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "媒体 $totalCount 项 · 成功 $successCount · 失败 $failureCount · 取消 $cancelledCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                    )
                    Text(
                        text = operationStateLabel(
                            tasks = tasks,
                            totalCount = totalCount,
                            processedCount = processedCount,
                            successCount = successCount,
                            failureCount = failureCount,
                            cancelledCount = cancelledCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 2,
                    )
                    LinearProgressIndicator(
                        progress = { operationProgress / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = colors.primaryActionPressed,
                        trackColor = colors.sectionBackground.copy(alpha = 0.72f),
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
                        color = colors.textSecondary,
                    )
                }
                if (problemTasks.isNotEmpty()) {
                    TransferActionPill(
                        text = if (showFailureDetails) "收起详情" else "查看失败",
                        onClick = { showFailureDetails = !showFailureDetails },
                        modifier = Modifier.align(Alignment.End),
                    )
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
                        text = "清理记录只会移除这条传输记录，不会删除已导入媒体或小相册内容。",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
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
                retryableTasks.firstOrNull()?.let { task ->
                    TransferActionPill(text = transferRetryActionLabel(retryableTasks.size), onClick = {
                        showFailureDetails = false
                        onRetryOperation(task.operationId)
                    })
                }
                runningTasks.firstOrNull()?.let { task ->
                    TransferActionPill(
                        text = if (runningTasks.size > 1) "取消全部" else "取消",
                        emphasized = false,
                        onClick = { onCancelOperation(task.operationId) },
                    )
                }
                if (allTerminal) {
                    openTargetTask?.let { target ->
                        TransferActionPill(
                            text = when (target.operationType) {
                                LocalSystemMediaBridgeRepository.OperationType.CREATE_POST -> "查看新小相册"
                                LocalSystemMediaBridgeRepository.OperationType.ADD_TO_EXISTING_POST -> "查看目标小相册"
                                LocalSystemMediaBridgeRepository.OperationType.IMPORT_TO_APP -> "查看照片"
                            },
                            onClick = { onOpen(target) },
                        )
                    }
                    TransferActionPill(
                        text = "清理",
                        emphasized = false,
                        onClick = { showClearGroupDialog = true },
                    )
                } else if (canOpen) {
                    openTargetTask?.let { target ->
                        TransferActionPill(text = "查看结果", onClick = { onOpen(target) })
                    }
                }
            }
        }
    }
    if (showClearGroupDialog) {
        TransferClearRecordsDialog(
            title = "清理这组传输记录？",
            body = "只会从传输中心移除本组记录，不会删除已导入照片流的媒体、已创建的小相册，或已加入小相册里的媒体。",
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
    val colors = YingShiThemeTokens.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.raisedSurface,
        titleContentColor = colors.titleAccent,
        textContentColor = colors.textSecondary,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        },
        text = {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TransferActionPill(text = "清理记录", onClick = onConfirm)
        },
        dismissButton = {
            TransferActionPill(text = "取消", emphasized = false, onClick = onDismiss)
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
    val colors = YingShiThemeTokens.colors
    val primaryTask = tasks.first()
    val resultTask = tasks.firstOrNull { it.resultPostRoute != null } ?: primaryTask

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.md),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.20f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = "失败与重试",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = failureRetryExplanation(
                    task = resultTask,
                    successCount = successCount,
                    failureCount = failureCount,
                    cancelledCount = cancelledCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
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
    val colors = YingShiThemeTokens.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(
            text = task.fileName,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.titleAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${task.mediaType.transferLabel()} · ${failureCurrentStateLabel(task)}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            maxLines = 1,
        )
        Text(
            text = failureReasonLabel(task),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TransferTaskLine(task: SystemMediaUploadTaskUiModel) {
    val colors = YingShiThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = task.fileName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = taskStateLabel(task),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun TransferTaskThumbnail(task: SystemMediaUploadTaskUiModel) {
    val context = LocalContext.current
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val previewUri = task.previewUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
    val backgroundColor = if (task.mediaType == SystemMediaType.VIDEO) {
        colors.sectionBackground.copy(alpha = 0.86f)
    } else {
        colors.sectionBackground.copy(alpha = 0.72f)
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
            PlaceholderBadge(isVideo = task.mediaType == SystemMediaType.VIDEO)
        }
    }
}

@Composable
private fun PlaceholderBadge(isVideo: Boolean) {
    val colors = YingShiThemeTokens.colors
    if (isVideo) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = colors.raisedSurface.copy(alpha = 0.72f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = colors.titleAccent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun VideoBadge() {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.size(28.dp),
        shape = CircleShape,
        color = colors.viewerBackground.copy(alpha = 0.38f),
        border = BorderStroke(1.dp, colors.viewerAccent.copy(alpha = 0.14f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = colors.viewerText,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun TransferCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier
            .size(40.dp)
            .semantics { this.contentDescription = contentDescription }
            .yingShiClickable(shape = CircleShape, pressedScale = 0.94f, onClick = onClick),
        shape = CircleShape,
        color = colors.sectionBackground.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.titleAccent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun TransferActionPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    emphasized: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    Surface(
        modifier = modifier
            .semantics { contentDescription = text }
            .yingShiClickable(enabled = enabled, shape = shape, pressedScale = 0.96f, onClick = onClick),
        shape = shape,
        color = when {
            !enabled -> colors.sectionBackground.copy(alpha = 0.50f)
            emphasized -> colors.primaryContainer.copy(alpha = 0.76f)
            else -> colors.sectionBackground.copy(alpha = 0.70f)
        },
        border = BorderStroke(
            1.dp,
            if (emphasized) colors.glassStroke.copy(alpha = 0.72f) else colors.dividerSoft.copy(alpha = 0.64f),
        ),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (emphasized) colors.titleAccent else colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (emphasized) colors.titleAccent else colors.textSecondary,
            )
        }
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
        LocalSystemMediaBridgeRepository.OperationType.IMPORT_TO_APP -> "导入照片流"
        LocalSystemMediaBridgeRepository.OperationType.CREATE_POST -> "新建小相册"
        LocalSystemMediaBridgeRepository.OperationType.ADD_TO_EXISTING_POST -> "加入已有小相册"
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
                "小相册已创建，成功项已保留；失败项可在传输中心重试，不会重复创建已成功内容。"
            } else {
                "成功项已保留；失败项可在传输中心重试，不会重复创建已成功内容。"
            }
        }
        LocalSystemMediaBridgeRepository.OperationType.ADD_TO_EXISTING_POST ->
            "目标小相册是「${task.operationTitle ?: task.targetLabel}」。成功项已保留；失败项可在传输中心重试，不会重复加入已成功内容。"
        LocalSystemMediaBridgeRepository.OperationType.IMPORT_TO_APP ->
            "成功项已进入照片流；失败项可在传输中心重试，不会重复导入已成功内容。"
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
    totalCount: Int,
    processedCount: Int,
    successCount: Int,
    failureCount: Int,
    cancelledCount: Int,
): String {
    val primaryTask = tasks.first()
    val targetLabel = primaryTask.operationTitle ?: primaryTask.targetLabel
    val countText = "成功 $successCount，失败 $failureCount，取消 $cancelledCount"
    val hasCancelled = cancelledCount > 0
    val hasFailure = failureCount > 0
    val hasSuccess = successCount > 0
    return when {
        tasks.any { it.state == UploadState.UPLOADING } && (hasFailure || hasCancelled) ->
            "正在重试失败项：已处理 ${processedCount}/${totalCount} 项"
        tasks.any { it.state == UploadState.UPLOADING } -> "正在处理 ${processedCount}/${totalCount} 项"
        tasks.any { it.state == UploadState.WAITING } && (hasFailure || hasCancelled) ->
            "正在重试失败项：等待处理 ${processedCount}/${totalCount} 项"
        tasks.any { it.state == UploadState.WAITING } -> "等待处理 ${processedCount}/${totalCount} 项"
        cancelledCount == tasks.size -> "已取消：未完成项不会继续处理，可清理传输记录。"
        hasCancelled && hasSuccess && !hasFailure -> "部分完成后取消：$countText。成功项已保留，未完成项已取消。"
        hasFailure || hasCancelled -> when (primaryTask.operationType) {
            LocalSystemMediaBridgeRepository.OperationType.IMPORT_TO_APP ->
                "部分导入完成：$countText。成功项已保留，失败或取消项可重试。"
            LocalSystemMediaBridgeRepository.OperationType.CREATE_POST -> {
                if (tasks.any { it.resultPostRoute != null }) {
                    "小相册已创建：$countText。成功项已保留，失败或取消项可重试。"
                } else {
                    "小相册未完整创建：$countText。失败或取消项可重试。"
                }
            }
            LocalSystemMediaBridgeRepository.OperationType.ADD_TO_EXISTING_POST ->
                "已加入「$targetLabel」：$countText。成功项已保留，失败或取消项可重试。"
        }
        successCount == tasks.size -> when (primaryTask.operationType) {
            LocalSystemMediaBridgeRepository.OperationType.IMPORT_TO_APP ->
                "导入完成：成功 $successCount 项，可查看照片"
            LocalSystemMediaBridgeRepository.OperationType.CREATE_POST ->
                "小相册创建完成：成功 $successCount 项，可查看新小相册"
            LocalSystemMediaBridgeRepository.OperationType.ADD_TO_EXISTING_POST ->
                "已加入「$targetLabel」：成功 $successCount 项，可查看目标小相册"
        }
        else -> "任务已更新"
    }
}

internal fun calculateTransferOperationProcessedCount(
    tasks: List<SystemMediaUploadTaskUiModel>,
    totalCount: Int,
): Int {
    val virtualCompletedCount = (totalCount - tasks.size).coerceAtLeast(0)
    return (virtualCompletedCount + tasks.count { it.isTerminal })
        .coerceIn(0, totalCount.coerceAtLeast(0))
}

internal fun calculateTransferOperationProgressPercent(
    tasks: List<SystemMediaUploadTaskUiModel>,
    totalCount: Int,
): Int {
    if (tasks.isEmpty()) return 0
    if (totalCount <= 0) return 0
    val virtualCompletedUnits = (totalCount - tasks.size).coerceAtLeast(0) * 100
    val taskUnits = tasks.sumOf { task ->
        if (task.isTerminal) {
            100
        } else {
            task.progressPercent.coerceIn(0, 100)
        }
    }
    return ((virtualCompletedUnits + taskUnits).toFloat() / totalCount)
        .toInt()
        .coerceIn(0, 100)
}

internal fun transferRetryActionLabel(retryableCount: Int): String {
    return if (retryableCount > 1) {
        "重试 $retryableCount 项"
    } else {
        "重试"
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
    YingShiTheme {
        TransferCenterScreen(
            route = TransferCenterRoute(),
            onBack = { },
            onOpenTaskMedia = { },
        )
    }
}
