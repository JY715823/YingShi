package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun SystemMediaImportPreviewDialog(
    preview: SystemMediaImportPreview,
    timePreferenceLabel: String,
    onDismiss: () -> Unit,
    onConfirmImport: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val importableCount = preview.importableCount
    val importableImageCount = preview.importableItems.count { it.type == SystemMediaType.IMAGE }
    val importableVideoCount = preview.importableItems.count { it.type == SystemMediaType.VIDEO }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "导入前检查",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Surface(
                    shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
                    color = colors.sectionBackground.copy(alpha = 0.76f),
                    border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.58f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.md, vertical = spacing.md),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        SystemMediaImportPreviewSummaryPill(
                            text = "图片 $importableImageCount",
                            emphasized = importableImageCount > 0,
                            modifier = Modifier.weight(1f),
                        )
                        SystemMediaImportPreviewSummaryPill(
                            text = "视频 $importableVideoCount",
                            emphasized = importableVideoCount > 0,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (importableCount <= 0) {
                    Text(
                        text = "当前没有可导入的媒体。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                if (preview.duplicateCount > 0) {
                    Text(
                        text = "已自动跳过 ${preview.duplicateCount} 个已导入或重复选择的媒体。",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.titleAccent,
                    )
                }
                if (preview.timeNoticeCount > 0) {
                    Text(
                        text = "${preview.timeNoticeCount} 个媒体使用“$timePreferenceLabel”规则重新确定时间。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        },
        confirmButton = {
            ImportPreviewActionChip(
                text = "确定",
                emphasized = importableCount > 0,
                onClick = if (importableCount > 0) onConfirmImport else onDismiss,
            )
        },
        dismissButton = {
            ImportPreviewActionChip(
                text = "取消",
                emphasized = false,
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun SystemMediaImportPreviewSummaryPill(
    text: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (emphasized) {
            colors.primaryContainer.copy(alpha = 0.9f)
        } else {
            colors.sectionBackground.copy(alpha = 0.7f)
        },
        border = BorderStroke(
            1.dp,
            if (emphasized) colors.glassStroke.copy(alpha = 0.72f) else colors.dividerSoft.copy(alpha = 0.62f),
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.titleAccent,
        )
    }
}

@Composable
private fun ImportPreviewActionChip(
    text: String,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.yingShiClickable(
            shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
            pressedScale = 0.96f,
            onClick = onClick,
        ),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (emphasized) {
            colors.primaryContainer.copy(alpha = 0.88f)
        } else {
            colors.sectionBackground.copy(alpha = 0.72f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (emphasized) {
                colors.glassStroke.copy(alpha = 0.72f)
            } else {
                colors.dividerSoft.copy(alpha = 0.62f)
            },
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.titleAccent,
        )
    }
}
