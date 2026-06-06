package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.model.UploadState
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
fun SystemMediaUploadTaskPanel(
    tasks: List<SystemMediaUploadTaskUiModel>,
    onCancelTask: (String) -> Unit,
    onDismissTask: (String) -> Unit,
    onRetryTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tasks.isEmpty()) return
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = "正在导入",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            tasks.takeLast(3).reversed().forEach { task ->
                val statusColor = when (task.state) {
                    UploadState.WAITING -> colors.textSecondary
                    UploadState.UPLOADING -> colors.titleAccent
                    UploadState.SUCCESS -> colors.softGreenAction
                    UploadState.FAILURE -> MaterialTheme.colorScheme.error
                    UploadState.CANCELLED -> colors.textSecondary
                }
                val trackColor = if (task.state == UploadState.FAILURE) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.62f)
                } else {
                    colors.sectionBackground.copy(alpha = 0.88f)
                }
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                        ) {
                            Text(
                                text = "${task.targetLabel} · ${task.fileName}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = colors.textPrimary,
                            )
                            Text(
                                text = taskStateLabel(task),
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (task.canRetry) {
                                UploadTaskActionButton(text = "重试", onClick = { onRetryTask(task.taskId) })
                            }
                            if (task.isTerminal) {
                                UploadTaskActionButton(text = "收起", onClick = { onDismissTask(task.taskId) })
                            } else {
                                UploadTaskActionButton(text = "取消", onClick = { onCancelTask(task.taskId) })
                            }
                        }
                    }
                    LinearProgressIndicator(
                        progress = { task.progressPercent.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = statusColor,
                        trackColor = trackColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun UploadTaskActionButton(
    text: String,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    Surface(
        modifier = Modifier.yingShiClickable(
            shape = shape,
            pressedScale = 0.96f,
            onClick = onClick,
        ),
        shape = shape,
        color = colors.primaryContainer.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.72f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.titleAccent,
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
