package com.example.yingshi.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.model.RemoteLoginSession
import com.example.yingshi.data.remote.auth.BackendAutoLoginManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.config.RemoteServiceFactory
import com.example.yingshi.data.remote.dto.LoginRequestDto
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.launch

private const val APP_NAME = "\u6620\u4e16"
private const val LOGIN_SUBTITLE = "\u767b\u5f55\u540e\u8fdb\u5165\u4e24\u4e2a\u4eba\u7684\u76f8\u518c\u7a7a\u95f4\u3002"
private const val LABEL_EMAIL = "\u90ae\u7bb1"
private const val LABEL_PASSWORD = "\u5bc6\u7801"
private const val ACTION_LOGIN = "\u767b\u5f55"
private const val ACTION_FILL_DEMO = "\u586b\u5165 demo"
private const val ERROR_LOGIN_FAILED = "\u767b\u5f55\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5\u3002"
private const val LABEL_CURRENT_MODE = "\u5f53\u524d\u6a21\u5f0f\uff1a"
private const val TIP_REAL_PREFIX = "REAL \u6a21\u5f0f\u8bf7\u786e\u8ba4 baseUrl \u6307\u5411\u5f53\u524d\u540e\u7aef\uff1a"
private const val TIP_FAKE =
    "FAKE \u6a21\u5f0f\u4f1a\u4f7f\u7528\u672c\u5730\u5360\u4f4d\u8d26\u53f7\uff0c\u4e0d\u8bbf\u95ee\u540e\u7aef\u3002"

@Composable
fun LoginScreen(
    onLoginSuccess: (RemoteCurrentUser) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val settings = BackendDebugConfig.settings
    val scope = rememberCoroutineScope()
    var account by rememberSaveable { mutableStateOf(BackendAutoLoginManager.DEFAULT_DEMO_ACCOUNT) }
    var password by rememberSaveable { mutableStateOf(BackendAutoLoginManager.DEFAULT_DEMO_PASSWORD) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = APP_NAME,
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = LOGIN_SUBTITLE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Surface(
                shape = RoundedCornerShape(radius.xl),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
            ) {
                Column(
                    modifier = Modifier.padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    OutlinedTextField(
                        value = account,
                        onValueChange = { account = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(LABEL_EMAIL) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        enabled = !isLoading,
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(LABEL_PASSWORD) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !isLoading,
                    )

                    errorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Button(
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
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && account.isNotBlank() && password.isNotBlank(),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(vertical = 2.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(ACTION_LOGIN)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        OutlinedButton(
                            onClick = {
                                account = BackendAutoLoginManager.DEFAULT_DEMO_ACCOUNT
                                password = BackendAutoLoginManager.DEFAULT_DEMO_PASSWORD
                                errorMessage = null
                            },
                            enabled = !isLoading,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(ACTION_FILL_DEMO)
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(radius.lg),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    ) {
                        Text(
                            text = if (settings.repositoryMode == RepositoryMode.REAL) "R" else "F",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        Text(
                            text = LABEL_CURRENT_MODE + settings.repositoryMode.name,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (settings.repositoryMode == RepositoryMode.REAL) {
                                TIP_REAL_PREFIX + RemoteServiceFactory.currentBaseUrl()
                            } else {
                                TIP_FAKE
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
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
