package com.example.yingshi.feature.photos

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens

data class SettingsRoute(
    val source: String = "notification-center",
)

@Composable
fun SettingsScreen(
    route: SettingsRoute,
    onBack: () -> Unit,
    onOpenCacheManagement: (CacheManagementRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val context = LocalContext.current
    val cacheSummary = FakeMediaCacheRepository.getGlobalSummary()
    val settingsState = FakeSettingsRepository.getSettingsState()
    val viewerPreferences = settingsState.viewerPreferences

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        SettingsTopBar(
            source = route.source,
            onBack = onBack,
        )

        SettingsSection(
            title = "账号与空间",
            subtitle = "先保留为工具页占位分组，后续再接共享空间、成员和账号体系。",
        ) {
            SettingsInfoRow(
                title = "共享空间",
                subtitle = "当前阶段仍是本地壳层，暂不接真实账号与双人空间。",
                value = "占位",
            )
            SettingsInfoRow(
                title = "身份与成员状态",
                subtitle = "后续在这里接入双方身份、邀请状态和空间归属信息。",
                value = "未接入",
            )
        }

        SettingsSection(
            title = "浏览偏好",
            subtitle = "用于首次进入或重置后的默认值，不会持续覆盖当前页面里临时切换的密度。",
        ) {
            SettingsChoiceRow(
                title = "照片页默认网格密度",
                subtitle = "支持 2 / 3 / 4 / 8 / 16 列，下一次重新进入照片页时优先使用。",
                options = PhotoFeedDensity.entries,
                selectedOption = settingsState.defaultPhotoFeedDensity,
                optionLabel = { it.label },
                onOptionSelected = { FakeSettingsRepository.updateDefaultPhotoFeedDensity(it) },
            )
            SettingsChoiceRow(
                title = "相册页默认列数",
                subtitle = "支持 2 / 3 / 4 列，下一次重新进入相册页时优先使用。",
                options = AlbumGridDensity.entries,
                selectedOption = settingsState.defaultAlbumGridDensity,
                optionLabel = { it.label },
                onOptionSelected = { FakeSettingsRepository.updateDefaultAlbumGridDensity(it) },
            )
        }

        SettingsSection(
            title = "Viewer 偏好",
            subtitle = "这轮先保存到当前 app 会话。能立即生效的会直接接进现有 Viewer，其余保留清晰占位说明。",
        ) {
            SettingsInfoRow(
                title = "评论预览默认关闭",
                subtitle = "当前仍固定沿用关闭策略，点击评论气泡后再展开预览，不单独提供开关。",
                value = "固定策略",
            )
            SettingsSwitchRow(
                title = "缩放时隐藏操作层",
                subtitle = if (viewerPreferences.hideOverlaysWhenZoomed) {
                    "已生效：缩放时会隐藏或弱化操作层，恢复到普通查看后再显示。"
                } else {
                    "已生效：缩放时继续保留操作层，方便对照评论、原图和所属帖子入口。"
                },
                checked = viewerPreferences.hideOverlaysWhenZoomed,
                onCheckedChange = { FakeSettingsRepository.updateHideViewerOverlaysWhenZoomed(it) },
            )
            SettingsSwitchRow(
                title = "视频切换时自动暂停",
                subtitle = if (viewerPreferences.autoPauseVideoOnMediaSwitch) {
                    "当前会话已保存为开启；现阶段 Viewer 仍固定按自动暂停策略处理。"
                } else {
                    "当前会话已保存为关闭；真实播放器接入前，Viewer 仍固定按自动暂停策略处理。"
                },
                checked = viewerPreferences.autoPauseVideoOnMediaSwitch,
                onCheckedChange = { FakeSettingsRepository.updateAutoPauseVideoOnMediaSwitch(it) },
            )
        }

        SettingsSection(
            title = "缓存与存储",
            subtitle = "这里正式承接 Stage 9.4 的全局缓存清理占位能力，当前只做 app 内容区本地 fake 状态变化。",
        ) {
            SettingsSummaryBlock(
                title = "当前缓存总量",
                value = cacheSummary.totalSizeLabel,
                detail = "已登记 ${cacheSummary.mediaCount} 个 app 内容媒体",
            )
            SettingsActionRow(
                title = "清理全部预览缓存",
                subtitle = "当前可清理 ${cacheSummary.previewCachedCount} 项预览缓存状态。",
                onClick = {
                    FakeMediaCacheRepository.clearAllPreviewCaches()
                    Toast.makeText(context, "已清理全部预览缓存状态", Toast.LENGTH_SHORT).show()
                },
            )
            SettingsActionRow(
                title = "清理全部原图缓存",
                subtitle = "当前可清理 ${cacheSummary.originalCachedCount} 项原图缓存，并重置原图加载状态。",
                onClick = {
                    FakeMediaCacheRepository.clearAllOriginalCaches()
                    Toast.makeText(context, "已清理全部原图缓存状态", Toast.LENGTH_SHORT).show()
                },
            )
            SettingsActionRow(
                title = "清理全部视频缓存",
                subtitle = "当前可清理 ${cacheSummary.videoCachedCount} 项视频缓存状态。",
                onClick = {
                    FakeMediaCacheRepository.clearAllVideoCaches()
                    Toast.makeText(context, "已清理全部视频缓存状态", Toast.LENGTH_SHORT).show()
                },
            )
            SettingsActionRow(
                title = "清理全部缓存",
                subtitle = "一次性清理预览 / 原图 / 视频全部 fake 缓存状态。",
                danger = true,
                onClick = {
                    FakeMediaCacheRepository.clearAllCaches()
                    Toast.makeText(context, "已清理全部缓存状态", Toast.LENGTH_SHORT).show()
                },
            )
            SettingsEntryRow(
                title = "打开完整缓存清理页",
                subtitle = "进入独立缓存管理页，继续沿用 Stage 9.4 的全局清理入口。",
                onClick = {
                    onOpenCacheManagement(CacheManagementRoute(source = "settings-storage"))
                },
            )
        }

        SettingsSection(
            title = "权限状态",
            subtitle = "当前只展示状态，不做真实权限申请流程。",
        ) {
            SettingsInfoRow(
                title = "系统媒体访问",
                subtitle = "系统媒体工具区按当前阶段假定为已具备全部媒体访问能力。",
                value = "已授权全部媒体",
            )
            SettingsInfoRow(
                title = "通知权限",
                subtitle = "通知中心仍是本地 fake 数据，后续再接真实权限读取与申请。",
                value = "占位",
            )
            SettingsInfoRow(
                title = "后台任务 / 下载权限",
                subtitle = "为后续原图下载、视频缓存和后台任务占位，当前未接真实系统能力。",
                value = "占位",
            )
        }

        SettingsSection(
            title = "关于与诊断",
            subtitle = "先把版本、构建和诊断位置定住，真实服务留到后续阶段。",
        ) {
            SettingsInfoRow(
                title = "App 名称",
                subtitle = "当前项目名称。",
                value = "映世",
            )
            SettingsInfoRow(
                title = "当前版本",
                subtitle = "当前先使用 Stage 10 占位版本文案，后续再接真实版本与渠道说明。",
                value = "1.0（占位）",
            )
            SettingsInfoRow(
                title = "构建信息",
                subtitle = "当前先给出轻量占位说明，不接真实诊断导出。",
                value = "debug / local shell（占位）",
            )
            SettingsInfoRow(
                title = "崩溃上报",
                subtitle = "后续再接真实崩溃上报服务。",
                value = "未接入",
            )
            SettingsInfoRow(
                title = "埋点统计",
                subtitle = "后续再接真实埋点与统计配置。",
                value = "未接入",
            )
        }
    }
}

@Composable
private fun SettingsTopBar(
    source: String,
    onBack: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsCircleButton(text = "<", onClick = onBack)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "入口来源：${source.toSettingsSourceLabel()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        shape = RoundedCornerShape(radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)),
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
private fun SettingsSummaryBlock(
    title: String,
    value: String,
    detail: String,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        shape = RoundedCornerShape(radius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun <T> SettingsChoiceRow(
    title: String,
    subtitle: String,
    options: List<T>,
    selectedOption: T,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            options.forEach { option ->
                val selected = option == selectedOption
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(radius.capsule))
                        .clickable { onOptionSelected(option) },
                    shape = RoundedCornerShape(radius.capsule),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)
                        },
                    ),
                ) {
                    Text(
                        text = optionLabel(option),
                        modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
                        style = if (selected) {
                            MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        } else {
                            MaterialTheme.typography.labelMedium
                        },
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
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
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@Composable
private fun SettingsInfoRow(
    title: String,
    subtitle: String,
    value: String,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SettingsActionRow(
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
            .clip(RoundedCornerShape(radius.lg))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(radius.lg),
        color = if (danger) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.06f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
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
            Text(
                text = "执行",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SettingsEntryRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.lg))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(radius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = ">",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsCircleButton(
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

private fun String.toSettingsSourceLabel(): String {
    return when (this) {
        "notification-center" -> "通知中心"
        "notification-center-topbar" -> "通知中心顶部"
        "photos-home" -> "照片页"
        else -> this
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    YingShiTheme {
        SettingsScreen(
            route = SettingsRoute(),
            onBack = { },
            onOpenCacheManagement = { },
        )
    }
}
