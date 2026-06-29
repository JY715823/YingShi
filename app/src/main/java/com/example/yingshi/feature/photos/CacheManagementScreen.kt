package com.example.yingshi.feature.photos

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoLibrary
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.cache.AppReadCacheStore
import com.example.yingshi.data.cache.ReadCacheSummary
import com.example.yingshi.ui.components.YingShiBackdropVariant
import com.example.yingshi.ui.components.YingShiMistBackground
import com.example.yingshi.ui.components.YingShiMistCard
import com.example.yingshi.ui.components.YingShiNotice
import com.example.yingshi.ui.components.YingShiNoticeHost
import com.example.yingshi.ui.components.YingShiNoticeTone
import com.example.yingshi.ui.components.yingShiHapticClickable
import com.example.yingshi.ui.components.yingShiRouteReveal
import com.example.yingshi.ui.components.yingShiSoftReveal
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

    YingShiMistBackground(
        modifier = modifier.fillMaxSize(),
        showWaves = true,
        variant = YingShiBackdropVariant.PHOTOS,
    ) {
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

            // ── Overview ──
            YingShiMistCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .yingShiRouteReveal(),
            ) {
                Column(
                    modifier = Modifier.padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Text(
                        text = "当前缓存概览",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.titleAccent,
                    )
                    Text(
                        text = "只统计映世自己的缓存目录，不会扫描或删除系统相册源文件。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    CacheSummaryBlock(summary = currentSummary)
                }
            }

            // ── Cache breakdown ──
            YingShiMistCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .yingShiRouteReveal(),
            ) {
                Column(
                    modifier = Modifier.padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Text(
                        text = "缓存分类",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.titleAccent,
                    )
                    Text(
                        text = "按缩略图、封面、原图和视频片段分类。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
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
            }

            // ── Offline entry cache ──
            YingShiMistCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .yingShiRouteReveal(),
            ) {
                Column(
                    modifier = Modifier.padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Text(
                        text = "离线入口缓存",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.titleAccent,
                    )
                    Text(
                        text = "Me、照片流、相册目录、通知和回收站会把最近一次成功读取的结果写成 JSON 文件，按服务地址和账号隔离保存。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
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
            }

            // ── Cleanup actions ──
            YingShiMistCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .yingShiRouteReveal(),
            ) {
                Column(
                    modifier = Modifier.padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Text(
                        text = "清理入口",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.titleAccent,
                    )
                    Text(
                        text = "清理后需要重新加载对应媒体；离线入口缓存清理后，断网时将不再显示旧内容。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    CacheActionCard(
                        title = "清理缩略图 / 视频封面",
                        subtitle = "当前约 ${currentSummary?.thumbnailCoverSizeLabel ?: "统计中"}",
                        description = "清理 Coil 图片缓存和本地视频封面文件。",
                        icon = Icons.Rounded.Image,
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
                    CacheActionCard(
                        title = "清理原图 / 原视频缓存",
                        subtitle = "当前约 ${currentSummary?.originalMediaSizeLabel ?: "统计中"}",
                        description = "只清理 App 产生的原图状态和远程视频缓存片段。",
                        icon = Icons.Rounded.PhotoLibrary,
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
                    CacheActionCard(
                        title = "清理离线入口缓存",
                        subtitle = "当前约 ${readCacheSummary?.let { formatReadCacheSize(context, it.totalBytes) } ?: "统计中"}",
                        description = "会移除 Me、照片流、相册目录、通知和回收站的持久化读缓存。",
                        icon = Icons.Rounded.CloudOff,
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

                    // ── Danger action: clear all ──
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .yingShiHapticClickable(
                                shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
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
                            ),
                        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
                        color = colors.memoryContainer.copy(alpha = 0.72f),
                        border = BorderStroke(1.dp, colors.memoryAccent.copy(alpha = 0.36f)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.md, vertical = spacing.md),
                            horizontalArrangement = Arrangement.spacedBy(spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(end = spacing.xs)
                                    .clip(CircleShape)
                                    .background(colors.memoryAccent.copy(alpha = 0.18f))
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteSweep,
                                    contentDescription = null,
                                    tint = colors.memoryAccent,
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                            ) {
                                Text(
                                    text = "清除全部缓存",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.onMemoryContainer,
                                )
                                Text(
                                    text = "一次性清理缩略图、封面、原图状态和远程视频缓存片段。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onMemoryContainer.copy(alpha = 0.72f),
                                )
                            }
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
private fun CacheTopBar(
    title: String,
    onBack: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .yingShiSoftReveal(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(onClick = onBack)
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
private fun CacheSummaryBlock(
    summary: RealMediaCacheSummary?,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors

    Surface(
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
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
private fun CacheActionCard(
    title: String,
    subtitle: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .yingShiHapticClickable(shape = RoundedCornerShape(radius.lg), onClick = onClick),
        shape = RoundedCornerShape(radius.lg),
        color = colors.softGreenContainer.copy(alpha = 0.50f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.48f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.primaryContainer.copy(alpha = 0.50f))
                    .padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.titleAccent,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = colors.softGreenAction,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun CircleIconButton(
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.yingShiHapticClickable(shape = CircleShape, pressedScale = 0.94f, onClick = onClick),
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
