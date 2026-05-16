package com.example.yingshi.feature.me

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.ui.components.ShellPage
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
fun MyScreen(
    currentUser: RemoteCurrentUser?,
    repositoryMode: RepositoryMode,
    baseUrl: String,
    isLoggingOut: Boolean,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCacheManagement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing

    ShellPage(
        title = "我的",
        summary = "账号、环境和本地工具都放在这里。",
        modifier = modifier.fillMaxSize(),
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                ProfileCard(
                    currentUser = currentUser,
                    repositoryMode = repositoryMode,
                    baseUrl = baseUrl,
                    isLoggingOut = isLoggingOut,
                    onLogout = onLogout,
                )
                MyEntryRow(
                    title = "设置",
                    subtitle = "浏览偏好、后端联调诊断和当前阶段工具项。",
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
private fun ProfileCard(
    currentUser: RemoteCurrentUser?,
    repositoryMode: RepositoryMode,
    baseUrl: String,
    isLoggingOut: Boolean,
    onLogout: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val displayName = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "未加载用户"
    val email = currentUser?.account?.takeIf { it.isNotBlank() } ?: "未获取邮箱"
    val intro = currentUser?.libraryDisplayName?.takeIf { it.isNotBlank() }
        ?: "映世共享空间成员"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                ) {
                    Text(
                        text = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "映",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            InfoLine(label = "简介", value = intro)
            InfoLine(label = "环境", value = repositoryMode.name)
            InfoLine(label = "baseUrl", value = baseUrl)

            Button(
                onClick = onLogout,
                enabled = !isLoggingOut,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isLoggingOut) "退出中..." else "退出登录")
            }
        }
    }
}

@Composable
private fun InfoLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xxs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
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
            currentUser = RemoteCurrentUser(
                userId = "user_demo_a",
                account = "demo.a@yingshi.local",
                displayName = "Demo A",
                avatarUrl = null,
                libraryId = "library_shared",
                libraryDisplayName = "YingShi Shared Library",
            ),
            repositoryMode = RepositoryMode.REAL,
            baseUrl = "http://10.0.2.2:8080/",
            isLoggingOut = false,
            onLogout = {},
            onOpenSettings = {},
            onOpenCacheManagement = {},
        )
    }
}
