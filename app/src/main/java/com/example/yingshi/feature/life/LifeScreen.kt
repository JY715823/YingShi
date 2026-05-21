package com.example.yingshi.feature.life

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.example.yingshi.ui.components.ShellPage
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
fun LifeScreen(
    modifier: Modifier = Modifier,
    onOpenLedger: () -> Unit = {},
    onOpenChatViewer: () -> Unit = {},
) {
    ShellPage(
        title = "生活",
        summary = "日常工具从记账开始，先把收支、预算和资产做成真正可用的生活入口。",
        modifier = modifier,
        content = {
            LifeEntryCard(
                title = "记账",
                summary = "时光序式月账本、快速记一笔、资产、预算、统计和回收站。",
                onClick = onOpenLedger,
            )
            LifeEntryCard(
                title = "纪念日",
                summary = "后续生活模块入口，当前先保留轻量位置。",
                onClick = {},
            )
            LifeEntryCard(
                title = "聊天记录查看器",
                summary = "导入 QCE ZIP，合并多会话，本地离线浏览 QQ 风格聊天记录。",
                onClick = onOpenChatViewer,
            )
        },
    )
}

@Composable
private fun LifeEntryCard(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12),
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LifeScreenPreview() {
    YingShiTheme {
        LifeScreen()
    }
}
