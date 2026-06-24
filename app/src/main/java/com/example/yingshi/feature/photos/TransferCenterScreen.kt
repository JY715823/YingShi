package com.example.yingshi.feature.photos

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.model.UploadState
import com.example.yingshi.ui.components.YingShiMistBackground
import com.example.yingshi.ui.components.YingShiNotice
import com.example.yingshi.ui.components.YingShiNoticeHost
import com.example.yingshi.ui.components.YingShiNoticeTone
import com.example.yingshi.ui.components.YingShiStateLayer
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class TransferCenterCategory(
    val label: String,
    val emptyTitle: String,
    val emptyBody: String,
) {
    ALL("全部", "没有传输任务", "导入照片流、新建小相册、加入小相册后的进度会显示在这里。"),
    RUNNING("进行中", "没有进行中的传输", "新的上传任务会显示在这里。"),
    RETRYABLE("失败/可重试", "没有需要处理的失败项", "失败或暂停的媒体会保留继续入口。"),
    COMPLETED("已完成", "没有已完成记录", "成功导入或创建后的记录会显示在这里。"),
    CANCELLED("已取消", "没有已取消记录", "暂停或取消的任务会显示在这里。"),
    IMPORT_TO_APP("导入照片流", "没有照片流导入记录", "从系统媒体或底部加号导入 App 的任务会显示在这里。"),
    CREATE_POST("新建小相册", "没有新建小相册记录", "从系统媒体创建小相册的任务会显示在这里。"),
    ADD_TO_EXISTING_POST("加入小相册", "没有加入小相册记录", "追加到已有小相册的任务会显示在这里。"),
}

private object TransferCenterStateStore {
    var selectedCategory: TransferCenterCategory = TransferCenterCategory.ALL
    var firstVisibleItemIndex: Int = 0
    var firstVisibleItemScrollOffset: Int = 0
    val collapsedOperationIds: MutableSet<String> = linkedSetOf()
    val manuallyExpandedOperationIds: MutableSet<String> = linkedSetOf()
    var entranceNonce: Int = 0
}

private data class TransferDateSection(
    val key: String,
    val title: String,
    val groups: List<List<SystemMediaUploadTaskUiModel>>,
)

@Composable
fun TransferCenterScreen(
    route: TransferCenterRoute,
    onBack: () -> Unit,
    onOpenTaskMedia: (SystemMediaUploadTaskUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val context = LocalContext.current
    var selectedCategory by rememberSaveable { mutableStateOf(TransferCenterStateStore.selectedCategory) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    val tasks = LocalSystemMediaBridgeRepository.uploadTasks
    val operationGroups = tasks
        .groupBy { it.operationId }
        .values
        .map { it.sortedByDescending(SystemMediaUploadTaskUiModel::transferSortMillis) }
        .sortedByDescending { group -> group.maxOf { it.transferSortMillis() } }
        .filter { group -> group.matchesTransferCategory(selectedCategory) }
    val dateSections = remember(operationGroups) {
        operationGroups.toTransferDateSections()
    }
    val completedGroups = operationGroups.count { group -> group.all { it.isTerminal && !it.canRetry } }
    val runningGroups = operationGroups.size - completedGroups
    var showClearCompletedDialog by rememberSaveable { mutableStateOf(false) }
    val colors = YingShiThemeTokens.colors
    var noticeNonce by rememberSaveable { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<YingShiNotice?>(null) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = TransferCenterStateStore.firstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = TransferCenterStateStore.firstVisibleItemScrollOffset,
    )
    fun showNotice(message: String, tone: YingShiNoticeTone = YingShiNoticeTone.INFO) {
        noticeNonce += 1
        notice = YingShiNotice(message = message, tone = tone, nonce = noticeNonce)
    }
    LaunchedEffect(Unit) {
        TransferCenterStateStore.entranceNonce += 1
        TransferCenterStateStore.manuallyExpandedOperationIds.clear()
        TransferCenterStateStore.collapsedOperationIds.clear()
        LocalSystemMediaBridgeRepository.warmPersistentTransferCenter(context)
        tasks
            .groupBy { it.operationId }
            .values
            .filter { group -> group.all { it.isTerminal } }
            .forEach { group -> TransferCenterStateStore.collapsedOperationIds += group.first().operationId }
    }
    LaunchedEffect(operationGroups.map { group -> group.first().operationId to group.all { it.isTerminal } }) {
        operationGroups
            .filter { group ->
                group.all { it.isTerminal } &&
                    group.first().operationId !in TransferCenterStateStore.manuallyExpandedOperationIds
            }
            .forEach { group -> TransferCenterStateStore.collapsedOperationIds += group.first().operationId }
    }
    LaunchedEffect(selectedCategory) {
        TransferCenterStateStore.selectedCategory = selectedCategory
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            TransferCenterStateStore.firstVisibleItemIndex = index
            TransferCenterStateStore.firstVisibleItemScrollOffset = offset
        }
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
                if (operationGroups.isNotEmpty()) {
                    TransferActionPill(
                        text = "清空",
                        icon = Icons.Default.Delete,
                        onClick = { showClearCompletedDialog = true },
                    )
                }
                Box {
                    TransferCircleButton(
                        icon = Icons.Default.Menu,
                        contentDescription = "分类",
                        onClick = { showCategoryMenu = true },
                    )
                    DropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = { showCategoryMenu = false },
                    ) {
                        TransferCenterCategory.entries.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.label) },
                                onClick = {
                                    selectedCategory = category
                                    showCategoryMenu = false
                                },
                            )
                        }
                    }
                }
            }
            if (showClearCompletedDialog) {
                TransferClearRecordsDialog(
                    title = "清空「${selectedCategory.label}」记录？",
                    body = "只会从传输中心移除当前分类里的可见记录，不会删除已导入照片流的媒体、已创建的小相册，或已加入小相册里的媒体。",
                    onDismiss = { showClearCompletedDialog = false },
                    onConfirm = {
                        showClearCompletedDialog = false
                        operationGroups.flatten().forEach { task ->
                            if (task.canCancel) {
                                LocalSystemMediaBridgeRepository.cancelUploadTask(task.taskId)
                            }
                            LocalSystemMediaBridgeRepository.dismissUploadTask(task.taskId)
                        }
                        showNotice("已清空当前分类记录", YingShiNoticeTone.SUCCESS)
                    },
                )
            }

            if (operationGroups.isEmpty()) {
                TransferEmptyState(selectedCategory)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    dateSections.forEach { section ->
                        item(key = "header-${section.key}") {
                            TransferDateHeader(title = section.title)
                        }
                        items(
                            items = section.groups,
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
                                onPauseOperation = {
                                    LocalSystemMediaBridgeRepository.pauseUploadOperation(it)
                                    showNotice("已暂停，可稍后重试")
                                },
                                onCancelOperation = {
                                    LocalSystemMediaBridgeRepository.cancelUploadOperation(it)
                                    showNotice("已取消传输任务")
                                },
                                onPauseTask = {
                                    LocalSystemMediaBridgeRepository.pauseUploadTask(it)
                                    showNotice("已暂停，可稍后重试")
                                },
                                onCancelTask = {
                                    LocalSystemMediaBridgeRepository.cancelUploadTask(it)
                                    showNotice("已取消该媒体")
                                },
                                onRetryTask = {
                                    if (LocalSystemMediaBridgeRepository.retryUploadTask(context, it)) {
                                        showNotice("已重新加入传输队列", YingShiNoticeTone.SUCCESS)
                                    } else {
                                        showNotice("当前任务无法重试", YingShiNoticeTone.WARNING)
                                    }
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
private fun TransferDateHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(top = YingShiThemeTokens.spacing.xs),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = YingShiThemeTokens.colors.titleAccent,
    )
}

@Composable
private fun TransferEmptyState(category: TransferCenterCategory) {
    YingShiStateLayer(
        title = category.emptyTitle,
        body = category.emptyBody,
        tone = YingShiNoticeTone.INFO,
    )
}

@Composable
private fun TransferOperationCard(
    tasks: List<SystemMediaUploadTaskUiModel>,
    onRetryOperation: (String) -> Unit,
    onPauseOperation: (String) -> Unit,
    onCancelOperation: (String) -> Unit,
    onPauseTask: (String) -> Unit,
    onCancelTask: (String) -> Unit,
    onRetryTask: (String) -> Unit,
    onClearTask: (String) -> Unit,
    onOpen: (SystemMediaUploadTaskUiModel) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val primaryTask = tasks.first()
    val retryableTasks = tasks.filter { it.canRetry }
    val runningTasks = tasks.filter { it.canPause }
    val cancellableTasks = tasks.filter { it.canCancel }
    val allTerminal = tasks.all { it.isTerminal }
    val entranceNonce = TransferCenterStateStore.entranceNonce
    var isCollapsed by remember(primaryTask.operationId, allTerminal, entranceNonce) {
        mutableStateOf(
            allTerminal &&
                primaryTask.operationId in TransferCenterStateStore.collapsedOperationIds &&
                primaryTask.operationId !in TransferCenterStateStore.manuallyExpandedOperationIds,
        )
    }
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
        modifier = Modifier.fillMaxWidth(),
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
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = primaryTask.operationTitle
                                ?.takeIf { it.isNotBlank() }
                                ?: primaryTask.operationType.label(),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.titleAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatTransferTime(primaryTask.transferSortMillis()),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.softGreenAction,
                            maxLines = 1,
                        )
                    }
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

            if (!isCollapsed) {
                Column(
                    modifier = Modifier.padding(horizontal = spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    tasks.chunked(3).forEach { rowTasks ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        ) {
                            rowTasks.forEach { task ->
                                TransferMediaTile(
                                    task = task,
                                    modifier = Modifier.weight(1f),
                                    onOpen = { onOpen(task) },
                                    onPause = { onPauseTask(task.taskId) },
                                    onCancel = { onCancelTask(task.taskId) },
                                    onRetry = { onRetryTask(task.taskId) },
                                )
                            }
                            repeat(3 - rowTasks.size) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
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
            } else {
                Row(
                    modifier = Modifier.padding(horizontal = spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TransferTaskThumbnailStack(tasks = tasks)
                    Text(
                        text = "已折叠 ${tasks.size} 项媒体，展开后可查看每项缩略图、进度和控制。",
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
                        text = if (runningTasks.size > 1) "暂停全部" else "暂停",
                        emphasized = false,
                        onClick = { onPauseOperation(task.operationId) },
                    )
                }
                cancellableTasks.firstOrNull()?.let { task ->
                    TransferActionPill(
                        text = if (cancellableTasks.size > 1) "取消全部" else "取消",
                        emphasized = false,
                        onClick = { onCancelOperation(task.operationId) },
                    )
                }
                if (allTerminal) {
                    TransferActionPill(
                        text = if (isCollapsed) "展开" else "折叠",
                        emphasized = false,
                        onClick = {
                            isCollapsed = !isCollapsed
                            if (isCollapsed) {
                                TransferCenterStateStore.collapsedOperationIds += primaryTask.operationId
                                TransferCenterStateStore.manuallyExpandedOperationIds -= primaryTask.operationId
                            } else {
                                TransferCenterStateStore.collapsedOperationIds -= primaryTask.operationId
                                TransferCenterStateStore.manuallyExpandedOperationIds += primaryTask.operationId
                            }
                        },
                    )
                    TransferActionPill(
                        text = "清理",
                        emphasized = false,
                        onClick = { showClearGroupDialog = true },
                    )
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
            text = "${task.mediaType.transferLabel()} · ${failureCurrentStateLabel(task)}",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.titleAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
private fun TransferMediaTile(
    task: SystemMediaUploadTaskUiModel,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val canOpen = task.state == UploadState.SUCCESS && !task.resultMediaId.isNullOrBlank()
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(YingShiThemeTokens.radius.md))
            .background(colors.sectionBackground.copy(alpha = 0.72f))
            .yingShiClickable(
                enabled = canOpen,
                shape = RoundedCornerShape(YingShiThemeTokens.radius.md),
                onClick = onOpen,
            ),
    ) {
        TransferTaskThumbnail(
            task = task,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(46.dp)
                .background(colors.viewerBackground.copy(alpha = 0.32f)),
        )
        Text(
            text = "${task.progressPercent.coerceIn(0, 100)}%",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = spacing.xs, bottom = spacing.xs),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.viewerText,
            maxLines = 1,
        )
        Text(
            text = compactTaskStateLabel(task),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(spacing.xs),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.viewerText,
            maxLines = 1,
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = spacing.xs, bottom = spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when {
                task.canPause -> {
                    TransferIconActionButton(icon = Icons.Default.Pause, contentDescription = "暂停", onClick = onPause)
                    TransferIconActionButton(icon = Icons.Default.Close, contentDescription = "取消", onClick = onCancel)
                }
                task.canRetry || task.state == UploadState.FAILURE -> {
                    TransferIconActionButton(icon = Icons.Default.Refresh, contentDescription = "重试", onClick = onRetry)
                    if (task.canCancel) {
                        TransferIconActionButton(icon = Icons.Default.Close, contentDescription = "取消", onClick = onCancel)
                    }
                }
            }
        }
        LinearProgressIndicator(
            progress = { task.progressPercent.coerceIn(0, 100) / 100f },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(3.dp),
            color = when (task.state) {
                UploadState.FAILURE -> MaterialTheme.colorScheme.error
                UploadState.CANCELLED -> colors.textSecondary
                else -> colors.primaryActionPressed
            },
            trackColor = Color.Transparent,
        )
    }
}

@Composable
private fun TransferTaskThumbnail(
    task: SystemMediaUploadTaskUiModel,
    modifier: Modifier = Modifier.size(72.dp),
) {
    val context = LocalContext.current
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val sessionVersion = AuthSessionManager.sessionVersion
    val accessToken = remember(sessionVersion) {
        AuthSessionManager.peekAccessToken()?.takeIf { it.isNotBlank() }
    }
    val thumbnailUrl = remember(task.thumbnailUrl) {
        resolveBackendMediaUrl(task.thumbnailUrl)
    }
    val previewUri = task.previewUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
    val networkRequest = remember(context, thumbnailUrl, accessToken) {
        if (!thumbnailUrl.isNullOrBlank()) {
            backendMediaImageRequest(
                context = context,
                url = thumbnailUrl,
                accessToken = accessToken,
                memoryCacheKey = sharedPreviewMemoryCacheKey(thumbnailUrl),
                diskCacheKey = stableMediaCacheUrlKey(thumbnailUrl),
                size = 512,
            )
        } else {
            null
        }
    }
    val localThumbnail = if (previewUri != null) {
        rememberSystemMediaThumbnail(
            context = context,
            uri = previewUri,
            targetSizePx = 256,
        )
    } else {
        null
    }
    val backgroundColor = if (task.mediaType == SystemMediaType.VIDEO) {
        colors.sectionBackground.copy(alpha = 0.86f)
    } else {
        colors.sectionBackground.copy(alpha = 0.72f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius.md))
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        when {
            localThumbnail != null -> {
                Image(
                    bitmap = localThumbnail.asImageBitmap(),
                    contentDescription = task.fileName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                if (task.mediaType == SystemMediaType.VIDEO) {
                    VideoBadge()
                }
            }
            networkRequest != null -> {
                AsyncImage(
                    model = networkRequest,
                    contentDescription = task.fileName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                if (task.mediaType == SystemMediaType.VIDEO) {
                    VideoBadge()
                }
            }
            else -> {
                PlaceholderBadge(isVideo = task.mediaType == SystemMediaType.VIDEO)
            }
        }
    }
}

@Composable
private fun TransferIconActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier
            .size(24.dp)
            .semantics { this.contentDescription = contentDescription }
            .yingShiClickable(shape = CircleShape, pressedScale = 0.90f, onClick = onClick),
        shape = CircleShape,
        color = colors.viewerBackground.copy(alpha = 0.52f),
        border = BorderStroke(1.dp, colors.viewerAccent.copy(alpha = 0.12f)),
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.viewerText,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun TransferTaskThumbnailStack(tasks: List<SystemMediaUploadTaskUiModel>) {
    Box(modifier = Modifier.size(82.dp)) {
        tasks.take(3).reversed().forEachIndexed { reversedIndex, task ->
            val depth = 2 - reversedIndex
            Box(
                modifier = Modifier
                    .padding(start = (depth * 5).dp, top = (depth * 5).dp)
                    .size(72.dp)
                    .background(Color.Transparent),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(YingShiThemeTokens.radius.md),
                    color = YingShiThemeTokens.colors.sectionBackground.copy(alpha = 0.38f + depth * 0.16f),
                    border = BorderStroke(1.dp, YingShiThemeTokens.colors.dividerSoft.copy(alpha = 0.36f)),
                    shadowElevation = 0.dp,
                ) {
                    Box {
                        TransferTaskThumbnail(task = task)
                    }
                }
            }
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

private fun List<SystemMediaUploadTaskUiModel>.matchesTransferCategory(
    category: TransferCenterCategory,
): Boolean {
    if (isEmpty()) return false
    return when (category) {
        TransferCenterCategory.ALL -> true
        TransferCenterCategory.RUNNING -> any { it.canPause }
        TransferCenterCategory.RETRYABLE -> any { it.canRetry || it.state == UploadState.FAILURE }
        TransferCenterCategory.COMPLETED -> all { it.state == UploadState.SUCCESS }
        TransferCenterCategory.CANCELLED -> any { it.state == UploadState.CANCELLED }
        TransferCenterCategory.IMPORT_TO_APP -> first().operationType ==
            LocalSystemMediaBridgeRepository.OperationType.IMPORT_TO_APP
        TransferCenterCategory.CREATE_POST -> first().operationType ==
            LocalSystemMediaBridgeRepository.OperationType.CREATE_POST
        TransferCenterCategory.ADD_TO_EXISTING_POST -> first().operationType ==
            LocalSystemMediaBridgeRepository.OperationType.ADD_TO_EXISTING_POST
    }
}

private fun List<List<SystemMediaUploadTaskUiModel>>.toTransferDateSections(): List<TransferDateSection> {
    return groupBy { group -> transferDateKey(group.maxOf { it.transferSortMillis() }) }
        .map { (key, groups) ->
            TransferDateSection(
                key = key,
                title = transferDateTitle(groups.maxOf { group -> group.maxOf { it.transferSortMillis() } }),
                groups = groups.sortedByDescending { group -> group.maxOf { it.transferSortMillis() } },
            )
        }
        .sortedByDescending { section -> section.groups.maxOf { group -> group.maxOf { it.transferSortMillis() } } }
}

internal fun SystemMediaUploadTaskUiModel.transferSortMillis(): Long {
    return updatedAtMillis.takeIf { it > 0L }
        ?: completedAtMillis?.takeIf { it > 0L }
        ?: createdAtMillis
}

private fun transferDateKey(timeMillis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(timeMillis))
}

private fun transferDateTitle(timeMillis: Long): String {
    val target = Calendar.getInstance(Locale.CHINA).apply { timeInMillis = timeMillis }
    val today = Calendar.getInstance(Locale.CHINA)
    val yesterday = Calendar.getInstance(Locale.CHINA).apply {
        add(Calendar.DAY_OF_YEAR, -1)
    }
    return when {
        target.sameCalendarDay(today) -> "今天"
        target.sameCalendarDay(yesterday) -> "昨天"
        else -> SimpleDateFormat("M月d日", Locale.CHINA).format(Date(timeMillis))
    }
}

private fun Calendar.sameCalendarDay(other: Calendar): Boolean {
    return get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
        get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
}

private fun formatTransferTime(timeMillis: Long): String {
    return SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timeMillis))
}

internal fun calculateTransferOperationProcessedCount(
    tasks: List<SystemMediaUploadTaskUiModel>,
    totalCount: Int,
): Int {
    val virtualCompletedCount = (totalCount - tasks.size).coerceAtLeast(0)
    return (virtualCompletedCount + tasks.count { it.isTerminal && !it.canRetry })
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
        if (task.isTerminal && !task.canRetry) {
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

private fun compactTaskStateLabel(task: SystemMediaUploadTaskUiModel): String {
    return when (task.state) {
        UploadState.WAITING -> "等待"
        UploadState.UPLOADING -> "上传中"
        UploadState.SUCCESS -> "完成"
        UploadState.FAILURE -> "失败"
        UploadState.CANCELLED -> if (task.canRetry) "暂停" else "取消"
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
