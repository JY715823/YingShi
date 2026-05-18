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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CacheManagementScreen(
    route: CacheManagementRoute,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var refreshVersion by rememberSaveable { mutableIntStateOf(0) }
    val summary by produceState<RealMediaCacheSummary?>(
        initialValue = null,
        context,
        refreshVersion,
    ) {
        value = withContext(Dispatchers.IO) {
            MediaCacheRepository.getSummary(context)
        }
    }
    val currentSummary = summary

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
            title = "当前缓存概览",
            subtitle = "只统计 App 自己的缓存目录；不会扫描或删除系统相册源文件，也不会影响后端文件。",
        ) {
            CacheSummaryBlock(summary = currentSummary)
        }

        CacheSection(
            title = "缓存分类",
            subtitle = "图片缩略图、视频封面主要在 Coil 图片缓存里；远程视频播放片段单独在 App 视频缓存里。",
        ) {
            CacheInfoRow(title = "媒体缓存总量", value = currentSummary?.totalSizeLabel ?: "统计中")
            CacheInfoRow(title = "缩略图 / 视频封面", value = currentSummary?.thumbnailCoverSizeLabel ?: "统计中")
            CacheInfoRow(title = "原图 / 原视频", value = currentSummary?.originalMediaSizeLabel ?: "统计中")
            CacheInfoRow(
                title = "已登记媒体状态",
                value = currentSummary?.let {
                    "${it.registeredMediaCount} 项"
                } ?: "统计中",
            )
        }

        CacheSection(
            title = "清理入口",
            subtitle = "清理只发生在 App 缓存目录和本地缓存状态里；清完后照片流会重新从后端加载 preview / cover。",
        ) {
            CacheActionRow(
                title = "清理缩略图 / 视频封面",
                subtitle = "当前约 ${currentSummary?.thumbnailCoverSizeLabel ?: "统计中"}。会清理 Coil 图片缓存和本地视频封面文件。",
                onClick = {
                    coroutineScope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            MediaCacheRepository.clearThumbnailAndCoverCache(context)
                        }
                        refreshVersion += 1
                        Toast.makeText(
                            context,
                            if (ok) "已清理缩略图和视频封面缓存。" else "部分缓存清理失败，已保留可继续使用的文件。",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
            CacheActionRow(
                title = "清理原图 / 原视频缓存",
                subtitle = "当前约 ${currentSummary?.originalMediaSizeLabel ?: "统计中"}。只清理 App 产生的原图状态和远程视频缓存片段。",
                onClick = {
                    coroutineScope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            MediaCacheRepository.clearOriginalMediaCache(context)
                        }
                        refreshVersion += 1
                        Toast.makeText(
                            context,
                            if (ok) "已清理原图和原视频缓存。" else "部分原媒体缓存清理失败，已保留可继续使用的文件。",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
            CacheActionRow(
                title = "清理全部缓存",
                subtitle = "一次性清理缩略图、封面、原图状态和远程视频缓存片段。",
                danger = true,
                onClick = {
                    coroutineScope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            MediaCacheRepository.clearAllMediaCaches(context)
                        }
                        refreshVersion += 1
                        Toast.makeText(
                            context,
                            if (ok) "已清理全部媒体缓存。" else "部分媒体缓存清理失败，已保留可继续使用的文件。",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
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
    summary: RealMediaCacheSummary?,
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
                text = summary?.totalSizeLabel ?: "统计中",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = summary?.let {
                    "缩略图 / 封面 ${it.thumbnailCoverSizeLabel} · 原图 / 原视频 ${it.originalMediaSizeLabel}"
                } ?: "正在扫描 App 缓存目录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = summary?.let {
                    "已登记 ${it.registeredPreviewCount} 项预览 · ${it.registeredOriginalCount} 项原图 · ${it.registeredVideoCount} 项视频状态"
                } ?: " ",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CacheInfoRow(
    title: String,
    value: String,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
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
        "my-page" -> "我的页"
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
