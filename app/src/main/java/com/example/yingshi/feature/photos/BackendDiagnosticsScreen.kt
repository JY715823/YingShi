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
import com.example.yingshi.data.remote.config.RemoteServiceFactory
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
            onBack = onBack,
        )

        DiagnosticsSection(
            title = "服务地址",
            subtitle = "保存后会重新连接当前账号。",
        ) {
            OutlinedTextField(
                value = baseUrlInput,
                onValueChange = { baseUrlInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("服务地址") },
                singleLine = true,
            )

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
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存并连接")
            }

            ValueCard(
                title = "当前服务地址",
                value = RemoteServiceFactory.currentBaseUrl(),
                note = "用于同步照片、通知和生活记录。",
            )
        }

        DiagnosticsSection(
            title = "登录状态",
            subtitle = "网络恢复后，应用会自动再试一次登录。",
        ) {
            ValueCard(
                title = "状态",
                value = autoLoginState.phase.displayLabel,
                note = autoLoginState.message,
            )
            ValueCard(
                title = "最近触发",
                value = autoLoginState.lastReason.ifBlank { "未记录" },
                note = "应用启动和网络恢复时会自动重试。",
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
                    Text("重新连接")
                }

                OutlinedButton(
                    onClick = {
                        AuthSessionManager.clearTokens()
                        BackendAutoLoginManager.markLoggedOut("已退出当前连接")
                        lastResult = "已退出当前连接。"
                    },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("退出连接")
                }
            }
        }

        DiagnosticsSection(
            title = "操作结果",
            subtitle = "这里只显示最后一次操作的结果。",
        ) {
            ResultBlock(text = lastResult)
        }
    }
}

@Composable
private fun BackendDiagnosticsTopBar(
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
                text = "连接设置",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
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

private val com.example.yingshi.data.remote.auth.BackendAutoLoginPhase.displayLabel: String
    get() = when (this) {
        com.example.yingshi.data.remote.auth.BackendAutoLoginPhase.Idle -> "未登录"
        com.example.yingshi.data.remote.auth.BackendAutoLoginPhase.LoggingIn -> "登录中"
        com.example.yingshi.data.remote.auth.BackendAutoLoginPhase.Success -> "已登录"
        com.example.yingshi.data.remote.auth.BackendAutoLoginPhase.Failed -> "登录失败"
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
