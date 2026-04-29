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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.model.UploadState
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
fun SystemMediaUploadTaskPanel(
    tasks: List<SystemMediaUploadTaskUiModel>,
    onCancelTask: (String) -> Unit,
    onDismissTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tasks.isEmpty()) return
    val spacing = YingShiThemeTokens.spacing

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = "上传占位任务",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            tasks.takeLast(3).reversed().forEach { task ->
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                        ) {
                            Text(
                                text = "${task.targetLabel} · ${task.fileName}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = taskStateLabel(task),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (task.isTerminal) {
                            TextButton(onClick = { onDismissTask(task.taskId) }) {
                                Text("关闭")
                            }
                        } else {
                            TextButton(onClick = { onCancelTask(task.taskId) }) {
                                Text("取消")
                            }
                        }
                    }
                    LinearProgressIndicator(
                        progress = { task.progressPercent.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun taskStateLabel(task: SystemMediaUploadTaskUiModel): String {
    return when (task.state) {
        UploadState.WAITING -> "等待上传"
        UploadState.UPLOADING -> "上传中 ${task.progressPercent}%"
        UploadState.SUCCESS -> "上传成功，已进入 app 内容"
        UploadState.FAILURE -> task.errorMessage ?: "上传失败"
        UploadState.CANCELLED -> "已取消"
    }
}
