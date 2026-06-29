package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.cache.OfflineAccessManager
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.auth.BackendAutoLoginManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.config.RemoteServiceFactory
import com.example.yingshi.ui.components.YingShiBackdropVariant
import com.example.yingshi.ui.components.YingShiMistBackground
import com.example.yingshi.ui.components.YingShiMistCard
import com.example.yingshi.ui.components.YingShiPrimaryMistButton
import com.example.yingshi.ui.components.YingShiStateLayer
import com.example.yingshi.ui.components.YingShiNoticeTone
import com.example.yingshi.ui.components.YingShiTextField
import com.example.yingshi.ui.components.yingShiHapticClickable
import com.example.yingshi.ui.components.yingShiRouteReveal
import com.example.yingshi.ui.components.yingShiSoftReveal
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
    val colors = YingShiThemeTokens.colors
    val settings = BackendDebugConfig.settings
    val scope = rememberCoroutineScope()
    val autoLoginState by BackendAutoLoginManager.uiState.collectAsState()
    val offlineAccessState = OfflineAccessManager.state
    var baseUrlInput by rememberSaveable { mutableStateOf(settings.baseUrl) }
    var isRunning by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf("还没有执行操作。") }

    LaunchedEffect(settings.baseUrl) {
        if (baseUrlInput != settings.baseUrl) {
            baseUrlInput = settings.baseUrl
        }
    }

    val isBusy = isRunning || autoLoginState.inFlight

    val connectionTone = when (autoLoginState.phase) {
        com.example.yingshi.data.remote.auth.BackendAutoLoginPhase.Success -> YingShiNoticeTone.SUCCESS
        com.example.yingshi.data.remote.auth.BackendAutoLoginPhase.Failed -> YingShiNoticeTone.WARNING
        com.example.yingshi.data.remote.auth.BackendAutoLoginPhase.LoggingIn -> YingShiNoticeTone.INFO
        else -> YingShiNoticeTone.WARNING
    }

    YingShiMistBackground(
        modifier = modifier.fillMaxSize(),
        showWaves = true,
        variant = YingShiBackdropVariant.LIFE,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            BackendDiagnosticsTopBar(
                onBack = onBack,
            )

            // ── Server URL ──
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
                        text = "服务地址",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.titleAccent,
                    )
                    Text(
                        text = "保存后会清除当前会话，并回到重新登录的状态。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    YingShiTextField(
                        value = baseUrlInput,
                        onValueChange = { baseUrlInput = it },
                        placeholder = "输入服务地址",
                        icon = Icons.Rounded.Link,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    YingShiPrimaryMistButton(
                        text = "保存地址",
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
                    )
                    DiagnosticsValueCard(
                        title = "当前服务地址",
                        value = RemoteServiceFactory.currentBaseUrl(),
                        note = "用于同步照片、通知和生活记录。",
                    )
                }
            }

            // ── Connection status ──
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
                        text = "连接状态",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.titleAccent,
                    )
                    Text(
                        text = "网络恢复后，若会话仍有效会自动恢复；若会话已失效，会保留缓存并提示重新登录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    YingShiStateLayer(
                        title = "状态",
                        body = autoLoginState.message,
                        tone = connectionTone,
                        actionLabel = autoLoginState.phase.displayLabel,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DiagnosticsValueCard(
                        title = "离线兜底",
                        value = if (offlineAccessState.isReadOnly) "缓存只读中" else "实时连接优先",
                        note = offlineAccessState.message ?: "Me、照片流、相册目录、通知和回收站支持持久化读缓存。",
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        YingShiPrimaryMistButton(
                            text = "检查会话",
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
                        )

                        DiagnosticsSecondaryButton(
                            text = "退出连接",
                            onClick = {
                                AuthSessionManager.clearTokens()
                                BackendAutoLoginManager.markLoggedOut("已退出当前连接。")
                                lastResult = "已退出当前连接。"
                            },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ── Operation result ──
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
                        text = "操作结果",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.titleAccent,
                    )
                    Text(
                        text = "最近一次连接结果。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    ResultBlock(text = lastResult)
                }
            }
        }
    }
}

@Composable
private fun BackendDiagnosticsTopBar(
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
                text = "连接设置",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
        }
    }
}

@Composable
private fun DiagnosticsValueCard(
    title: String,
    value: String,
    note: String,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.lg),
        color = colors.sectionBackground.copy(alpha = 0.44f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.44f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            SelectionContainer {
                Text(
                    text = value,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                )
            }
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ResultBlock(text: String) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        shape = RoundedCornerShape(radius.lg),
        color = colors.softGreenContainer.copy(alpha = 0.46f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.44f)),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun DiagnosticsSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    Surface(
        modifier = modifier.yingShiHapticClickable(
            enabled = enabled,
            shape = shape,
            pressedScale = 0.96f,
            onClick = onClick,
        ),
        shape = shape,
        color = colors.sectionBackground.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.66f)),
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (enabled) colors.textSecondary else colors.textSecondary.copy(alpha = 0.56f),
            )
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
