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

private const val TITLE_MY = "\u6211\u7684"
private const val SUMMARY_MY = "\u8d26\u53f7\u3001\u73af\u5883\u548c\u672c\u5730\u5de5\u5177\u90fd\u653e\u5728\u8fd9\u91cc\u3002"
private const val TEXT_USER_PENDING = "\u672a\u52a0\u8f7d\u7528\u6237"
private const val TEXT_EMAIL_PENDING = "\u672a\u83b7\u53d6\u90ae\u7bb1"
private const val TEXT_BIO_HINT = "\u8fdb\u5165\u4e2a\u4eba\u4e3b\u9875\u540e\u53ef\u4ee5\u7f16\u8f91\u6635\u79f0\u548c\u7b80\u4ecb\u3002"
private const val LABEL_BIO = "\u7b80\u4ecb"
private const val TEXT_OPEN_PROFILE = "\u70b9\u51fb\u67e5\u770b\u4e2a\u4eba\u4e3b\u9875"
private const val LABEL_ENV = "\u5f53\u524d\u73af\u5883"
private const val ACTION_LOGOUT = "\u9000\u51fa\u767b\u5f55"
private const val ACTION_LOGOUT_LOADING = "\u9000\u51fa\u4e2d..."
private const val ENTRY_SETTINGS = "\u8bbe\u7f6e"
private const val ENTRY_SETTINGS_SUMMARY = "\u67e5\u770b\u5f53\u524d\u504f\u597d\u548c\u540e\u7aef\u8bca\u65ad\u5165\u53e3\u3002"
private const val ENTRY_CACHE = "\u7f13\u5b58\u7ba1\u7406"
private const val ENTRY_CACHE_SUMMARY = "\u67e5\u770b\u5e76\u6e05\u7406\u672c\u5730\u7f13\u5b58\u5360\u7528\u3002"

@Composable
fun MyScreen(
    currentUser: RemoteCurrentUser?,
    repositoryMode: RepositoryMode,
    baseUrl: String,
    isLoggingOut: Boolean,
    onOpenProfile: () -> Unit,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCacheManagement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing

    ShellPage(
        title = TITLE_MY,
        summary = SUMMARY_MY,
        modifier = modifier.fillMaxSize(),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                ProfileCard(
                    currentUser = currentUser,
                    onClick = onOpenProfile,
                )
                EnvironmentCard(
                    repositoryMode = repositoryMode,
                    baseUrl = baseUrl,
                    isLoggingOut = isLoggingOut,
                    onLogout = onLogout,
                )
                MyEntryRow(
                    title = ENTRY_SETTINGS,
                    subtitle = ENTRY_SETTINGS_SUMMARY,
                    onClick = onOpenSettings,
                )
                MyEntryRow(
                    title = ENTRY_CACHE,
                    subtitle = ENTRY_CACHE_SUMMARY,
                    onClick = onOpenCacheManagement,
                )
            }
        },
    )
}

@Composable
private fun ProfileCard(
    currentUser: RemoteCurrentUser?,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val displayName = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: TEXT_USER_PENDING
    val email = currentUser?.account?.takeIf { it.isNotBlank() } ?: TEXT_EMAIL_PENDING
    val intro = currentUser?.bio?.takeIf { it.isNotBlank() } ?: TEXT_BIO_HINT

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                        text = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "Y",
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

            InfoLine(label = LABEL_BIO, value = intro)
            Text(
                text = TEXT_OPEN_PROFILE,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EnvironmentCard(
    repositoryMode: RepositoryMode,
    baseUrl: String,
    isLoggingOut: Boolean,
    onLogout: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

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
            InfoLine(label = LABEL_ENV, value = repositoryMode.name)
            InfoLine(label = "baseUrl", value = baseUrl)

            Button(
                onClick = onLogout,
                enabled = !isLoggingOut,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isLoggingOut) ACTION_LOGOUT_LOADING else ACTION_LOGOUT)
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
                bio = "\u6e29\u67d4\u8bb0\u5f55\u65e5\u5e38\uff0c\u548c\u53e6\u4e00\u534a\u5171\u4eab\u8fd9\u5ea7\u5c0f\u5c0f\u76f8\u518c\u3002",
                createdAtMillis = 1760000000000L,
                updatedAtMillis = 1760000000000L,
            ),
            repositoryMode = RepositoryMode.REAL,
            baseUrl = "http://10.0.2.2:8080/",
            isLoggingOut = false,
            onOpenProfile = {},
            onLogout = {},
            onOpenSettings = {},
            onOpenCacheManagement = {},
        )
    }
}
