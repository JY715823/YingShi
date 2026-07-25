package com.example.yingshi.feature.me

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.model.RemotePartnerProfile
import com.example.yingshi.ui.components.YingShiBackdropVariant
import com.example.yingshi.ui.components.YingShiMistBackground
import com.example.yingshi.ui.components.yingShiHapticClickable
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.components.yingShiMemoryGlow
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens

private const val TEXT_USER_PENDING = "未加载用户"
private const val TEXT_EMAIL_PENDING = "未获取账号"
private const val TEXT_BIO_HINT = "进入个人主页后可以编辑昵称和简介。"
private const val TEXT_PARTNER_HINT = "一起把平常日子慢慢收进这座小小相册。"

@Composable
fun MyScreen(
    currentUser: RemoteCurrentUser?,
    isOfflineReadOnly: Boolean,
    isLoggingOut: Boolean,
    onOpenProfile: () -> Unit,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCacheManagement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val scrollState = rememberScrollState()

    YingShiMistBackground(
        modifier = modifier.fillMaxSize(),
        showWaves = false,
        variant = YingShiBackdropVariant.ME,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(top = 24.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "映世",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        color = colors.titleAccent,
                    )
                    Text(
                        text = "我的",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.titleAccent,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                SpaceIdentityCard(
                    currentUser = currentUser,
                    onOpenProfile = onOpenProfile,
                )
                PartnerCard(partner = currentUser?.partner)
                MyToolListCard(
                    onOpenSettings = onOpenSettings,
                    onOpenCacheManagement = onOpenCacheManagement,
                )
                AccountStatusCard(
                    currentUser = currentUser,
                    isOfflineReadOnly = isOfflineReadOnly,
                    isLoggingOut = isLoggingOut,
                    onLogout = onLogout,
                )
            }
        }
    }
}

@Composable
private fun SpaceIdentityCard(
    currentUser: RemoteCurrentUser?,
    onOpenProfile: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val displayName = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: TEXT_USER_PENDING
    val account = currentUser?.account?.takeIf { it.isNotBlank() } ?: TEXT_EMAIL_PENDING
    val intro = currentUser?.bio?.takeIf { it.isNotBlank() } ?: TEXT_BIO_HINT

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .yingShiClickable(shape = RoundedCornerShape(radius.xl), onClick = onOpenProfile),
        shape = RoundedCornerShape(radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.54f)),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GradientRingAvatar(
                    name = displayName,
                    avatarUrl = currentUser?.avatarUrl,
                    avatarSize = 88.dp,
                    modifier = Modifier.yingShiMemoryGlow(visible = true, warm = true),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                    )
                    Text(
                        text = account,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = intro,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .yingShiClickable(
                        shape = RoundedCornerShape(radius.capsule),
                        onClick = onOpenProfile,
                    ),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "查看个人主页",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.titleAccent,
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colors.titleAccent,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun PartnerCard(
    partner: RemotePartnerProfile?,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val context = LocalContext.current
    val displayName = partner?.displayName?.takeIf { it.isNotBlank() } ?: "另一半"
    val intro = if (partner == null) {
        "点击设置对方信息"
    } else {
        partner.bio?.takeIf { it.isNotBlank() } ?: TEXT_PARTNER_HINT
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .yingShiHapticClickable(enabled = partner == null) {
                Toast.makeText(context, "功能开发中，敬请期待", Toast.LENGTH_SHORT).show()
            },
        shape = RoundedCornerShape(radius.lg),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.52f)),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GradientRingAvatar(
                name = displayName,
                avatarUrl = partner?.avatarUrl,
                avatarSize = 50.dp,
                ringWidth = 2.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "另一半",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.memoryAccent,
                )
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                )
                Text(
                    text = intro,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AccountStatusCard(
    currentUser: RemoteCurrentUser?,
    isOfflineReadOnly: Boolean,
    isLoggingOut: Boolean,
    onLogout: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.lg),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.52f)),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = if (isOfflineReadOnly) {
                    colors.primaryContainer.copy(alpha = 0.88f)
                } else {
                    colors.memoryContainer.copy(alpha = 0.92f)
                },
            ) {
                Icon(
                    imageVector = if (isOfflineReadOnly) Icons.Rounded.Cached else Icons.Rounded.CloudDone,
                    contentDescription = null,
                    tint = if (isOfflineReadOnly) colors.titleAccent else colors.memoryAccent,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(22.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = if (isOfflineReadOnly) "缓存只读" else "已连接",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isOfflineReadOnly) colors.titleAccent else colors.memoryAccent,
                )
                Text(
                    text = currentUser?.account?.takeIf { it.isNotBlank() } ?: TEXT_EMAIL_PENDING,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isOfflineReadOnly) {
                    Text(
                        text = "当前显示上次缓存内容，恢复连接后会自动刷新。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .yingShiHapticClickable(
                        enabled = !isLoggingOut,
                        shape = RoundedCornerShape(radius.capsule),
                        onClick = onLogout,
                    ),
                shape = RoundedCornerShape(radius.capsule),
                color = colors.sectionBackground.copy(alpha = if (isLoggingOut) 0.48f else 0.82f),
                border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.82f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = if (isLoggingOut) "退出中" else "退出",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun MyToolListCard(
    onOpenSettings: () -> Unit,
    onOpenCacheManagement: () -> Unit,
) {
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.54f)),
        shadowElevation = 2.dp,
    ) {
        Column {
            MyToolRow(
                title = "设置",
                subtitle = "浏览偏好与空间设置",
                icon = Icons.Rounded.Settings,
                onClick = onOpenSettings,
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                color = colors.dividerSoft.copy(alpha = 0.72f),
            ) {
                Spacer(modifier = Modifier.fillMaxWidth().padding(top = 1.dp))
            }
            MyToolRow(
                title = "缓存管理",
                subtitle = "查看并清理媒体缓存和离线入口缓存",
                icon = Icons.Rounded.Cached,
                onClick = onOpenCacheManagement,
            )
        }
    }
}

@Composable
private fun MyToolRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .yingShiHapticClickable(
                shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
                onClick = onClick,
            )
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.titleAccent,
            modifier = Modifier.size(34.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = colors.titleAccent,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MyScreenPreview() {
    YingShiTheme {
        MyScreen(
            currentUser = RemoteCurrentUser(
                userId = "user_preview_a",
                account = "preview.a@yingshi.local",
                displayName = "映世小屋",
                avatarUrl = null,
                libraryId = "library_shared",
                libraryDisplayName = "我们的小空间",
                bio = "一起把平常日子慢慢收进这座小小相册。",
                partner = RemotePartnerProfile(
                    userId = "user_preview_b",
                    account = "preview.b@yingshi.local",
                    displayName = "另一半",
                    avatarUrl = null,
                    bio = "把生活里的闪光片段，也把安静和想念一起留下来。",
                ),
                createdAtMillis = 1760000000000L,
                updatedAtMillis = 1760000000000L,
            ),
            isOfflineReadOnly = false,
            isLoggingOut = false,
            onOpenProfile = {},
            onLogout = {},
            onOpenSettings = {},
            onOpenCacheManagement = {},
        )
    }
}
