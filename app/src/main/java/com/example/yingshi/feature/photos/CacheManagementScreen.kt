package com.example.yingshi.feature.photos

import android.text.format.Formatter
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.cache.AppReadCacheStore
import com.example.yingshi.data.cache.ReadCacheSummary
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.components.YingShiMistBackground
import com.example.yingshi.ui.components.YingShiNotice
import com.example.yingshi.ui.components.YingShiNoticeHost
import com.example.yingshi.ui.components.YingShiNoticeTone
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
    val colors = YingShiThemeTokens.colors
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var refreshVersion by rememberSaveable { mutableIntStateOf(0) }
    var noticeNonce by rememberSaveable { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<YingShiNotice?>(null) }
    fun showNotice(message: String, tone: YingShiNoticeTone = YingShiNoticeTone.INFO) {
        noticeNonce += 1
        notice = YingShiNotice(message = message, tone = tone, nonce = noticeNonce)
    }
    val summary by produceState<RealMediaCacheSummary?>(
        initialValue = null,
        context,
        refreshVersion,
    ) {
        value = withContext(Dispatchers.IO) {
            MediaCacheRepository.getSummary(context)
        }
    }
    val readCacheSummary by produceState<ReadCacheSummary?>(
        initialValue = null,
        refreshVersion,
    ) {
        value = withContext(Dispatchers.IO) {
            AppReadCacheStore.summary()
        }
    }
    val currentSummary = summary

    YingShiMistBackground(modifier = modifier, showWaves = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            CacheTopBar(
                title = "缓存管理",
                onBack = onBack,
            )

            CacheSection(
                title = "当前缓存概览",
                subtitle = "只统计映世自己的缓存目录，不会扫描或删除系统相册源文件。",
            ) {
                CacheSummaryBlock(summary = currentSummary)
            }

            CacheSection(
                title = "缓存分类",
                subtitle = "按缩略图、封面、原图和视频片段分类。",
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
                title = "离线入口缓存",
                subtitle = "Me、照片流、相册目录、通知和回收站会把最近一次成功读取的结果写成 JSON 文件，按服务地址和账号隔离保存。",
            ) {
                CacheInfoRow(
                    title = "缓存文件",
                    value = readCacheSummary?.let { "${it.fileCount} 份" } ?: "统计中",
                )
                CacheInfoRow(
                    title = "占用空间",
                    value = readCacheSummary?.let { formatReadCacheSize(context, it.totalBytes) } ?: "统计中",
                )
                CacheInfoRow(
                    title = "最近更新",
                    value = readCacheSummary?.lastUpdatedAtMillis?.let(::formatCacheUpdatedAt)
                        ?: "还没有离线入口缓存",
                )
            }

            CacheSection(
                title = "清理入口",
                subtitle = "清理后需要重新加载对应媒体；离线入口缓存清理后，断网时将不再显示旧内容。",
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
                            showNotice(
                                if (ok) "已清理缩略图和视频封面缓存。" else "部分缓存清理失败，已保留可继续使用的文件。",
                                if (ok) YingShiNoticeTone.SUCCESS else YingShiNoticeTone.WARNING,
                            )
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
                            showNotice(
                                if (ok) "已清理原图和原视频缓存。" else "部分原媒体缓存清理失败，已保留可继续使用的文件。",
                                if (ok) YingShiNoticeTone.SUCCESS else YingShiNoticeTone.WARNING,
                            )
                        }
                    },
                )
                CacheActionRow(
                    title = "清理离线入口缓存",
                    subtitle = "当前约 ${readCacheSummary?.let { formatReadCacheSize(context, it.totalBytes) } ?: "统计中"}。会移除 Me、照片流、相册目录、通知和回收站的持久化读缓存。",
                    onClick = {
                        coroutineScope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                AppReadCacheStore.clearProtectedData()
                            }
                            refreshVersion += 1
                            showNotice(
                                if (ok) "已清理离线入口缓存。" else "部分离线入口缓存清理失败，请稍后重试。",
                                if (ok) YingShiNoticeTone.SUCCESS else YingShiNoticeTone.WARNING,
                            )
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
                            showNotice(
                                if (ok) "已清理全部媒体缓存。" else "部分媒体缓存清理失败，已保留可继续使用的文件。",
                                if (ok) YingShiNoticeTone.SUCCESS else YingShiNoticeTone.WARNING,
                            )
                        }
                    },
                )
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
private fun CacheTopBar(
    title: String,
    onBack: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CacheCircleButton(onClick = onBack)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
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
    val colors = YingShiThemeTokens.colors

    Surface(
        shape = RoundedCornerShape(radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.58f)),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
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
    val colors = YingShiThemeTokens.colors

    Surface(
        shape = RoundedCornerShape(radius.lg),
        color = colors.primaryContainer.copy(alpha = 0.36f),
        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.36f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = summary?.totalSizeLabel ?: "统计中",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            Text(
                text = summary?.let {
                    "缩略图 / 封面 ${it.thumbnailCoverSizeLabel} · 原图 / 原视频 ${it.originalMediaSizeLabel}"
                } ?: "正在扫描 App 缓存目录",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Text(
                text = summary?.let {
                    "已登记 ${it.registeredPreviewCount} 项预览 · ${it.registeredOriginalCount} 项原图 · ${it.registeredVideoCount} 项视频状态"
                } ?: " ",
                style = MaterialTheme.typography.labelLarge,
                color = colors.softGreenAction,
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
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.lg),
        color = colors.sectionBackground.copy(alpha = 0.46f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.44f)),
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
                color = colors.textPrimary,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = colors.softGreenAction,
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
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .yingShiClickable(shape = RoundedCornerShape(radius.lg), onClick = onClick),
        shape = RoundedCornerShape(radius.lg),
        color = if (danger) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.58f)
        } else {
            colors.softGreenContainer.copy(alpha = 0.50f)
        },
        border = BorderStroke(
            1.dp,
            if (danger) MaterialTheme.colorScheme.error.copy(alpha = 0.26f) else colors.dividerSoft.copy(alpha = 0.48f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (danger) MaterialTheme.colorScheme.onErrorContainer else colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun CacheCircleButton(
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.yingShiClickable(shape = CircleShape, pressedScale = 0.94f, onClick = onClick),
        shape = CircleShape,
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.66f)),
    ) {
        Box(
            modifier = Modifier.padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = colors.titleAccent,
            )
        }
    }
}

private fun formatReadCacheSize(
    context: android.content.Context,
    bytes: Long,
): String {
    return if (bytes <= 0L) "0 B" else Formatter.formatShortFileSize(context, bytes)
}

private fun formatCacheUpdatedAt(timeMillis: Long): String {
    return java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA)
        .format(java.util.Date(timeMillis))
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
