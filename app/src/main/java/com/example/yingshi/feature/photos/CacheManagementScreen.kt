package com.example.yingshi.feature.photos

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
fun CacheManagementScreen(
    route: CacheManagementRoute,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val summary = FakeMediaCacheRepository.getGlobalSummary()
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        CacheTopBar(
            title = "缓存管理",
            subtitle = "来源：${route.source.toCacheSourceLabel()}",
            onBack = onBack,
        )

        CacheSection(
            title = "当前缓存总量",
            subtitle = "当前只处理 app 内容区 fake 缓存状态，不扫描真实磁盘，也不影响系统媒体工具区。",
        ) {
            CacheSummaryBlock(summary = summary)
        }

        CacheSection(
            title = "全局清理入口",
            subtitle = "这轮只做本地状态变化，为后续真实原图 / 视频 / OSS 缓存清理预留结构。",
        ) {
            CacheActionRow(
                title = "清理全部预览缓存",
                subtitle = "当前可清理 ${summary.previewCachedCount} 项预览缓存状态。",
                onClick = {
                    FakeMediaCacheRepository.clearAllPreviewCaches()
                    Toast.makeText(context, "已清理全部预览缓存状态", Toast.LENGTH_SHORT).show()
                },
            )
            CacheActionRow(
                title = "清理全部原图缓存",
                subtitle = "当前可清理 ${summary.originalCachedCount} 项原图缓存，并重置原图加载状态。",
                onClick = {
                    FakeMediaCacheRepository.clearAllOriginalCaches()
                    Toast.makeText(context, "已清理全部原图缓存状态", Toast.LENGTH_SHORT).show()
                },
            )
            CacheActionRow(
                title = "清理全部视频缓存",
                subtitle = "当前可清理 ${summary.videoCachedCount} 项视频缓存状态。",
                onClick = {
                    FakeMediaCacheRepository.clearAllVideoCaches()
                    Toast.makeText(context, "已清理全部视频缓存状态", Toast.LENGTH_SHORT).show()
                },
            )
            CacheActionRow(
                title = "清理全部缓存",
                subtitle = "一次性清理预览 / 原图 / 视频全部 fake 缓存状态。",
                danger = true,
                onClick = {
                    FakeMediaCacheRepository.clearAllCaches()
                    Toast.makeText(context, "已清理全部缓存状态", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

@Composable
private fun CacheTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CacheCircleButton(text = "<", onClick = onBack)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CacheSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        shape = RoundedCornerShape(radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
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
            content()
        }
    }
}

@Composable
private fun CacheSummaryBlock(
    summary: AppMediaCacheSummary,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        shape = RoundedCornerShape(radius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = summary.totalSizeLabel,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "已登记 ${summary.mediaCount} 个 app 内容媒体",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "预览 ${summary.previewCachedCount} · 原图 ${summary.originalCachedCount} · 视频 ${summary.videoCachedCount}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CacheActionRow(
    title: String,
    subtitle: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(radius.lg),
        color = if (danger) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.06f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CacheCircleButton(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun String.toCacheSourceLabel(): String {
    return when (this) {
        "settings" -> "设置页"
        "settings-storage" -> "设置页 / 缓存与存储"
        "viewer-settings" -> "Viewer 设置入口"
        else -> this
    }
}

@Preview(showBackground = true)
@Composable
private fun CacheManagementScreenPreview() {
    YingShiTheme {
        CacheManagementScreen(
            route = CacheManagementRoute(),
            onBack = { },
        )
    }
}
