package com.example.yingshi.feature.photos

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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.auth.BackendAutoLoginManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.config.RemoteConfig
import com.example.yingshi.data.remote.config.RemoteServiceFactory
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.launch

data class BackendDiagnosticsRoute(
    val source: String = "settings",
)

@Composable
fun BackendDiagnosticsScreen(
    route: BackendDiagnosticsRoute,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val settings = BackendDebugConfig.settings
    val scope = rememberCoroutineScope()
    val autoLoginState by BackendAutoLoginManager.uiState.collectAsState()
    var baseUrlInput by rememberSaveable { mutableStateOf(settings.baseUrl) }
    var isRunning by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf("还没有执行操作。") }

    LaunchedEffect(settings.baseUrl) {
        if (baseUrlInput != settings.baseUrl) {
            baseUrlInput = settings.baseUrl
        }
    }

    val isBusy = isRunning || autoLoginState.inFlight

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        BackendDiagnosticsTopBar(
            source = route.source,
            onBack = onBack,
        )

        DiagnosticsSection(
            title = "后端地址",
            subtitle = "保存后会立即重建连接，并自动重试一次登录。",
        ) {
            OutlinedTextField(
                value = baseUrlInput,
                onValueChange = { baseUrlInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Base URL") },
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            isRunning = true
                            BackendDebugConfig.updateBaseUrl(baseUrlInput)
                            val outcome = BackendAutoLoginManager.loginDefault(
                                force = true,
                                reason = "save_base_url",
                            )
                            lastResult = outcome.message
                            isRunning = false
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("保存并重登")
                }

                OutlinedButton(
                    onClick = {
                        baseUrlInput = RemoteConfig.DEBUG_EMULATOR_BASE_URL
                    },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("模拟器")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                OutlinedButton(
                    onClick = {
                        baseUrlInput = RemoteConfig.DEBUG_DEVICE_LOOPBACK_BASE_URL
                    },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("本机")
                }

                OutlinedButton(
                    onClick = {
                        baseUrlInput = RemoteConfig.HTTPS_EXAMPLE_BASE_URL
                    },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("HTTPS示例")
                }
            }

            ValueCard(
                title = "当前生效地址",
                value = RemoteServiceFactory.currentBaseUrl(),
                note = "这是当前真正发请求用的地址，不会再挤在一行里。",
            )
        }

        DiagnosticsSection(
            title = "数据模式",
            subtitle = "离线模式只走本地假数据，真实模式才会访问后端。",
        ) {
            ModeRow(
                selectedMode = settings.repositoryMode,
                onSelected = { mode ->
                    BackendDebugConfig.updateRepositoryMode(mode)
                    lastResult = "数据模式已切换为 ${mode.displayLabel}"
                },
            )
            ValueCard(
                title = "当前模式",
                value = settings.repositoryMode.displayLabel,
                note = "切到真实后，页面会继续保留刚才的后端地址。",
            )
        }

        DiagnosticsSection(
            title = "登录状态",
            subtitle = "保存地址或网络恢复后，应用会自动再试一次登录。",
        ) {
            ValueCard(
                title = "状态",
                value = autoLoginState.phase.displayLabel,
                note = autoLoginState.message,
            )
            ValueCard(
                title = "最近触发",
                value = autoLoginState.lastReason.ifBlank { "未记录" },
                note = "应用启动和网络恢复都会自动触发一次重试。",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            isRunning = true
                            val outcome = BackendAutoLoginManager.loginDefault(
                                force = true,
                                reason = "manual_retry",
                            )
                            lastResult = outcome.message
                            isRunning = false
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("立即重试")
                }

                OutlinedButton(
                    onClick = {
                        AuthSessionManager.clearTokens()
                        BackendAutoLoginManager.markLoggedOut("已清除登录缓存")
                        lastResult = "已清除登录缓存。"
                    },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("清除登录")
                }
            }
        }

        DiagnosticsSection(
            title = "连通性",
            subtitle = "只保留一个最小 health 检查，不再放那些没用的测试入口。",
        ) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        isRunning = true
                        lastResult = runCatching {
                            healthSummary()
                        }.fold(
                            onSuccess = { "健康检查通过：$it" },
                            onFailure = { "健康检查失败：${it.localizedMessage ?: it::class.java.simpleName}" },
                        )
                        isRunning = false
                    }
                },
                enabled = !isBusy,
            ) {
                Text("检查健康")
            }
        }

        DiagnosticsSection(
            title = "最近结果",
            subtitle = "这里只显示最后一次动作的结果。",
        ) {
            ResultBlock(text = lastResult)
        }
    }
}

private suspend fun healthSummary(): String {
    val health = RemoteServiceFactory.healthApi.getHealth().data
    return "health=${health.status}, app=${health.application}"
}

@Composable
private fun BackendDiagnosticsTopBar(
    source: String,
    onBack: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(text = "‹", onClick = onBack)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "后端联调",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = source.toBackendDiagnosticsSourceLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiagnosticsSection(
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
private fun ValueCard(
    title: String,
    value: String,
    note: String,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectionContainer {
                Text(
                    text = value,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModeRow(
    selectedMode: RepositoryMode,
    onSelected: (RepositoryMode) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        RepositoryMode.entries.forEach { mode ->
            val selected = mode == selectedMode
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(radius.lg))
                    .clickable { onSelected(mode) },
                shape = RoundedCornerShape(radius.lg),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
                },
                border = BorderStroke(
                    1.dp,
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    },
                ),
            ) {
                Box(
                    modifier = Modifier.padding(vertical = spacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = mode.displayLabel,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultBlock(text: String) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        shape = RoundedCornerShape(radius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CircleIconButton(
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

private val RepositoryMode.displayLabel: String
    get() = when (this) {
        RepositoryMode.FAKE -> "离线"
        RepositoryMode.REAL -> "真实"
    }

private val com.example.yingshi.data.remote.auth.BackendAutoLoginPhase.displayLabel: String
    get() = when (this) {
        com.example.yingshi.data.remote.auth.BackendAutoLoginPhase.Idle -> "未登录"
        com.example.yingshi.data.remote.auth.BackendAutoLoginPhase.LoggingIn -> "登录中"
        com.example.yingshi.data.remote.auth.BackendAutoLoginPhase.Success -> "已登录"
        com.example.yingshi.data.remote.auth.BackendAutoLoginPhase.Failed -> "登录失败"
    }

private fun String.toBackendDiagnosticsSourceLabel(): String {
    return when (this) {
        "notification-center" -> "来源：通知中心"
        "notification-center-topbar" -> "来源：通知中心顶部"
        "photos-home" -> "来源：照片页"
        "my-page" -> "来源：我的页"
        "settings" -> "来源：设置"
        else -> "来源：$this"
    }
}

@Preview(showBackground = true)
@Composable
private fun BackendDiagnosticsScreenPreview() {
    YingShiTheme {
        BackendDiagnosticsScreen(
            route = BackendDiagnosticsRoute(),
            onBack = { },
        )
    }
}
