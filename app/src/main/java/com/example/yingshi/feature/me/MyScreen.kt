package com.example.yingshi.feature.me

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.components.ShellPage
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
fun MyScreen(
    onOpenSettings: () -> Unit,
    onOpenCacheManagement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing

    ShellPage(
        title = "我的",
        summary = "把设置和缓存管理收口到一个更安静的工具页里，不再散落在通知、帖子详情或 Viewer 中。",
        modifier = modifier.fillMaxSize(),
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                MyEntryRow(
                    title = "设置",
                    subtitle = "管理浏览偏好、后端联调诊断和当前阶段的工具项。",
                    onClick = onOpenSettings,
                )
                MyEntryRow(
                    title = "缓存管理",
                    subtitle = "查看预览、原图、视频缓存占位，并执行本地清理。",
                    onClick = onOpenCacheManagement,
                )
            }
        },
    )
}

@Composable
private fun MyEntryRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MyScreenPreview() {
    YingShiTheme {
        MyScreen(
            onOpenSettings = {},
            onOpenCacheManagement = {},
        )
    }
}
