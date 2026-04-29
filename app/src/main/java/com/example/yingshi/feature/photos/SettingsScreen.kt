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
import androidx.compose.material3.TextButton
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
            subtitle = "当前只保留壳层分组，后续接共享空间、成员和账号相关设置。",
        ) {
            SettingsEntryRow(
                title = "共享空间信息",
                subtitle = "占位入口，后续承接空间名称、成员状态和身份信息。",
                onClick = {
                    Toast.makeText(context, "账号与空间设置仍为占位", Toast.LENGTH_SHORT).show()
                },
            )
        }

        SettingsSection(
            title = "浏览偏好",
            subtitle = "为后续照片流密度、查看偏好和通知呈现习惯预留位置。",
        ) {
            SettingsEntryRow(
                title = "媒体浏览偏好",
                subtitle = "占位入口，后续接入列表密度、查看手势和默认显示偏好。",
                onClick = {
                    Toast.makeText(context, "浏览偏好仍为占位", Toast.LENGTH_SHORT).show()
                },
            )
            SettingsEntryRow(
                title = "通知展示偏好",
                subtitle = "占位入口，后续接入静默、分组和提醒强度设置。",
                onClick = {
                    Toast.makeText(context, "通知偏好仍为占位", Toast.LENGTH_SHORT).show()
                },
            )
        }

        SettingsSection(
            title = "缓存与存储",
            subtitle = "这里接入 Stage 9.4 的全局缓存清理占位页，当前仍不扫描真实磁盘。",
        ) {
            SettingsSummaryBlock(
                title = "当前 fake 缓存总量",
                value = cacheSummary.totalSizeLabel,
                detail = "已登记 ${cacheSummary.mediaCount} 个 app 内容媒体",
            )
            SettingsEntryRow(
                title = "打开缓存管理",
                subtitle = "进入预览 / 原图 / 视频缓存的全局清理占位页。",
                onClick = {
                    onOpenCacheManagement(CacheManagementRoute(source = "settings"))
                },
            )
        }

        SettingsSection(
            title = "权限状态",
            subtitle = "当前只预留说明位，不做真实权限申请或系统跳转编排。",
        ) {
            SettingsEntryRow(
                title = "媒体与通知权限",
                subtitle = "占位入口，后续接入权限状态读取与引导。",
                onClick = {
                    Toast.makeText(context, "权限状态仍为占位", Toast.LENGTH_SHORT).show()
                },
            )
        }

        SettingsSection(
            title = "关于与诊断",
            subtitle = "当前只保留后续版本、日志、诊断信息的结构位置。",
        ) {
            SettingsEntryRow(
                title = "版本与构建信息",
                subtitle = "占位入口，后续接入版本号、构建号和环境说明。",
                onClick = {
                    Toast.makeText(context, "关于信息仍为占位", Toast.LENGTH_SHORT).show()
                },
            )
            SettingsEntryRow(
                title = "诊断与导出",
                subtitle = "占位入口，后续接入日志、调试和问题反馈相关能力。",
                onClick = {
                    Toast.makeText(context, "诊断入口仍为占位", Toast.LENGTH_SHORT).show()
                },
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
                text = "入口来源：$source",
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
