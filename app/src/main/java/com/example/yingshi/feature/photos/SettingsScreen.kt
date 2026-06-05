package com.example.yingshi.feature.photos

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens

data class SettingsRoute(
    val source: String = "my-page",
)

@Composable
fun SettingsScreen(
    route: SettingsRoute,
    onBack: () -> Unit,
    onOpenBackendDiagnostics: (BackendDiagnosticsRoute) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val settingsState = FakeSettingsRepository.getSettingsState()
    val viewerPreferences = settingsState.viewerPreferences
    val loginStatusValue = if (AuthSessionManager.isLoggedIn) {
        "已连接"
    } else {
        "未连接"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.appBackground)
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
            subtitle = "管理当前账号和两个人的共享空间。",
        ) {
            SettingsInfoRow(
                title = "共享空间",
                subtitle = "映世双人空间",
                value = "默认",
            )
            SettingsInfoRow(
                title = "登录状态",
                subtitle = "用于同步相册、通知和生活记录。",
                value = loginStatusValue,
            )
            SettingsEntryRow(
                title = "退出登录",
                subtitle = "清除当前账号会话",
                destructive = true,
                onClick = onLogout,
            )
        }

        SettingsSection(
            title = "浏览偏好",
            subtitle = "重新进入页面时使用这些默认偏好。",
        ) {
            SettingsChoiceRow(
                title = "照片页默认网格密度",
                subtitle = "支持 2 / 3 / 4 / 8 / 16 列。",
                options = PhotoFeedDensity.entries,
                selectedOption = settingsState.defaultPhotoFeedDensity,
                optionLabel = { it.label },
                onOptionSelected = { FakeSettingsRepository.updateDefaultPhotoFeedDensity(it) },
            )
            SettingsChoiceRow(
                title = "相册页默认列数",
                subtitle = "支持 2 / 3 / 4 列。",
                options = AlbumGridDensity.entries,
                selectedOption = settingsState.defaultAlbumGridDensity,
                optionLabel = { it.label },
                onOptionSelected = { FakeSettingsRepository.updateDefaultAlbumGridDensity(it) },
            )
        }

        SettingsSection(
            title = "查看器偏好",
            subtitle = "控制全屏看图和视频切换时的行为。",
        ) {
            SettingsInfoRow(
                title = "评论预览默认状态",
                subtitle = "当前固定为默认关闭，通过评论气泡再展开预览层。",
                value = "默认关闭",
            )
            SettingsSwitchRow(
                title = "缩放时弱化操作层",
                subtitle = "缩放后优先查看内容，恢复到适配屏幕后再把操作层完整显示回来。",
                checked = viewerPreferences.hideOverlaysWhenZoomed,
                onCheckedChange = { FakeSettingsRepository.updateHideViewerOverlaysWhenZoomed(it) },
            )
            SettingsSwitchRow(
                title = "切换媒体时自动暂停视频",
                subtitle = "避免视频播放状态串到下一张媒体上。",
                checked = viewerPreferences.autoPauseVideoOnMediaSwitch,
                onCheckedChange = { FakeSettingsRepository.updateAutoPauseVideoOnMediaSwitch(it) },
            )
        }

        SettingsSection(
            title = "权限状态",
            subtitle = "查看照片、通知和系统媒体相关状态。",
        ) {
            SettingsInfoRow(
                title = "系统媒体访问",
                subtitle = "系统媒体工具区会根据权限结果显示空态、错误态或授权提示。",
                value = "按运行时状态处理",
            )
            SettingsInfoRow(
                title = "通知权限",
                subtitle = "通知中心会显示评论、内容更新和回收站变更。",
                value = "可用",
            )
        }

        SettingsSection(
            title = "关于",
            subtitle = "应用信息与服务连接。",
        ) {
            SettingsInfoRow(
                title = "应用名称",
                subtitle = "双人私密相册与生活记录。",
                value = "映世",
            )
            SettingsEntryRow(
                title = "连接设置",
                subtitle = "查看服务地址、登录状态和同步模式。",
                onClick = { onOpenBackendDiagnostics(BackendDiagnosticsRoute(source = "settings")) },
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
    val colors = YingShiThemeTokens.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsCircleButton(onClick = onBack)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
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
    val colors = YingShiThemeTokens.colors

    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
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
                        .yingShiClickable(shape = RoundedCornerShape(radius.capsule), pressedScale = 0.96f) {
                            onOptionSelected(option)
                        },
                    shape = RoundedCornerShape(radius.capsule),
                    color = if (selected) {
                        colors.primaryContainer.copy(alpha = 0.68f)
                    } else {
                        colors.sectionBackground.copy(alpha = 0.50f)
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (selected) {
                            colors.glassStroke.copy(alpha = 0.58f)
                        } else {
                            colors.dividerSoft.copy(alpha = 0.56f)
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
                            colors.titleAccent
                        } else {
                            colors.textSecondary
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
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.lg),
        color = colors.sectionBackground.copy(alpha = 0.54f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.48f)),
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
                    color = colors.textPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.raisedSurface,
                    checkedTrackColor = colors.softGreenAction,
                    checkedBorderColor = colors.softGreenAction,
                    uncheckedThumbColor = colors.raisedSurface,
                    uncheckedTrackColor = colors.dividerSoft.copy(alpha = 0.72f),
                    uncheckedBorderColor = colors.dividerSoft,
                ),
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
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.lg),
        color = colors.sectionBackground.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.44f)),
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
                    color = colors.textPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = colors.softGreenAction,
            )
        }
    }
}

@Composable
private fun SettingsEntryRow(
    title: String,
    subtitle: String,
    destructive: Boolean = false,
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
        color = if (destructive) {
            colors.memoryContainer.copy(alpha = 0.70f)
        } else {
            colors.softGreenContainer.copy(alpha = 0.54f)
        },
        border = BorderStroke(
            1.dp,
            if (destructive) {
                colors.memoryAccent.copy(alpha = 0.18f)
            } else {
                colors.dividerSoft.copy(alpha = 0.48f)
            },
        ),
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
                    color = if (destructive) colors.memoryAccent else colors.textPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            Text(
                text = ">",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (destructive) colors.memoryAccent else colors.softGreenAction,
            )
        }
    }
}

@Composable
private fun SettingsCircleButton(
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

private fun String.toSettingsSourceLabel(): String {
    return when (this) {
        "notification-center" -> "通知中心"
        "notification-center-topbar" -> "通知中心顶部"
        "photos-home" -> "照片页"
        "my-page" -> "我的页"
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
            onOpenBackendDiagnostics = { },
            onLogout = { },
        )
    }
}
