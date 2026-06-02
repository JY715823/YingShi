package com.example.yingshi.feature.auth

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.model.RemoteLoginSession
import com.example.yingshi.data.remote.auth.BackendAutoLoginManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.dto.LoginRequestDto
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.components.YingShiMistBackground
import com.example.yingshi.ui.components.YingShiMistCard
import com.example.yingshi.ui.components.YingShiPrimaryMistButton
import com.example.yingshi.ui.components.YingShiTextField
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.launch

private const val APP_NAME = "映世"
private const val LOGIN_SUBTITLE = "和你一起，把日常留住"
private const val LABEL_ACCOUNT = "账号"
private const val LABEL_PASSWORD = "密码"
private const val LABEL_ADVANCED = "连接设置"
private const val LABEL_BACKEND_ADDRESS = "服务地址"
private const val ACTION_LOGIN = "登录"
private const val ACTION_SAVE_ADDRESS = "保存地址"
private const val ERROR_LOGIN_FAILED = "登录失败，请重试。"

@Composable
fun LoginScreen(
    sessionMessage: String? = null,
    onLoginSuccess: (RemoteCurrentUser) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val settings = BackendDebugConfig.settings
    val scope = rememberCoroutineScope()
    var account by rememberSaveable { mutableStateOf(BackendAutoLoginManager.DEFAULT_DEMO_ACCOUNT) }
    var password by rememberSaveable { mutableStateOf(BackendAutoLoginManager.DEFAULT_DEMO_PASSWORD) }
    var baseUrlInput by rememberSaveable(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var advancedMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (account.isBlank()) {
            account = BackendAutoLoginManager.DEFAULT_DEMO_ACCOUNT
        }
        if (password.isBlank()) {
            password = BackendAutoLoginManager.DEFAULT_DEMO_PASSWORD
        }
    }

    YingShiMistBackground(modifier = modifier, showWaves = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 44.dp)
                .padding(top = 156.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = APP_NAME,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.titleAccent,
                )
            }

            Column(
                modifier = Modifier.padding(top = 64.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                YingShiTextField(
                    value = account,
                    onValueChange = {
                        account = it
                        errorMessage = null
                    },
                    placeholder = LABEL_ACCOUNT,
                    icon = Icons.Rounded.Person,
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                YingShiTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    placeholder = LABEL_PASSWORD,
                    icon = Icons.Rounded.Lock,
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                AdvancedRow(
                    expanded = advancedExpanded,
                    onClick = {
                        advancedExpanded = !advancedExpanded
                        advancedMessage = null
                    },
                )

                if (advancedExpanded) {
                    YingShiMistCard(
                        shape = RoundedCornerShape(radius.lg),
                        color = colors.raisedSurface.copy(alpha = 0.76f),
                    ) {
                        Column(
                            modifier = Modifier.padding(spacing.md),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            YingShiTextField(
                                value = baseUrlInput,
                                onValueChange = {
                                    baseUrlInput = it
                                    advancedMessage = null
                                },
                                placeholder = LABEL_BACKEND_ADDRESS,
                                icon = Icons.Rounded.Settings,
                                enabled = !isLoading,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isLoading) {
                                        BackendDebugConfig.updateBaseUrl(baseUrlInput)
                                        baseUrlInput = BackendDebugConfig.currentBaseUrl()
                                        advancedMessage = "地址已保存"
                                    },
                                shape = RoundedCornerShape(radius.capsule),
                                color = colors.sectionBackground.copy(alpha = 0.84f),
                                border = BorderStroke(1.dp, colors.dividerSoft),
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = spacing.sm),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = ACTION_SAVE_ADDRESS,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.titleAccent,
                                    )
                                }
                            }
                            advancedMessage?.let { message ->
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.titleAccent,
                                )
                            }
                        }
                    }
                }

                (errorMessage ?: sessionMessage)?.let { message ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(radius.lg),
                        color = if (errorMessage != null) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.88f)
                        } else {
                            colors.memoryWash.copy(alpha = 0.90f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (errorMessage != null) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.24f)
                            } else {
                                colors.memoryAccent.copy(alpha = 0.16f)
                            },
                        ),
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (errorMessage != null) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                colors.onMemoryContainer
                            },
                        )
                    }
                }

                YingShiPrimaryMistButton(
                    text = ACTION_LOGIN,
                    onClick = {
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            try {
                                when (
                                    val loginResult = RepositoryProvider.authRepository.login(
                                        LoginRequestDto(
                                            account = account.trim(),
                                            password = password,
                                        ),
                                    )
                                ) {
                                    is ApiResult.Success -> {
                                        val session: RemoteLoginSession = loginResult.data
                                        onLoginSuccess(
                                            RemoteCurrentUser(
                                                userId = session.userId,
                                                account = session.account,
                                                displayName = session.displayName,
                                                avatarUrl = session.avatarUrl,
                                                libraryId = session.libraryId,
                                                libraryDisplayName = session.libraryDisplayName,
                                                bio = session.bio,
                                                partner = session.partner,
                                                createdAtMillis = session.createdAtMillis,
                                                updatedAtMillis = session.updatedAtMillis,
                                            ),
                                        )
                                    }
                                    is ApiResult.Error -> {
                                        errorMessage = loginResult.message
                                    }
                                    ApiResult.Loading -> Unit
                                }
                            } catch (throwable: Throwable) {
                                errorMessage = throwable.message ?: ERROR_LOGIN_FAILED
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 38.dp),
                    enabled = !isLoading && account.isNotBlank() && password.isNotBlank(),
                    loading = isLoading,
                )
                Text(
                    text = LOGIN_SUBTITLE,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun AdvancedRow(
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val radius = YingShiThemeTokens.radius
    val spacing = YingShiThemeTokens.spacing

    YingShiMistCard(
        modifier = Modifier
            .fillMaxWidth()
            .yingShiClickable(shape = RoundedCornerShape(radius.lg), onClick = onClick),
        shape = RoundedCornerShape(radius.lg),
        color = colors.raisedSurface.copy(alpha = 0.70f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.18f))
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                tint = colors.titleAccent,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = LABEL_ADVANCED,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textSecondary,
            )
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = colors.titleAccent,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    YingShiTheme {
        LoginScreen(onLoginSuccess = {})
    }
}
