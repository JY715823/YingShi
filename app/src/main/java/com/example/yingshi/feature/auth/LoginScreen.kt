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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.yingshi.data.remote.connectivity.NetworkConnectivityMonitor
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
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val APP_NAME = "映世"
private const val LOGIN_SUBTITLE = "和你一起，把日常留住"
private const val LABEL_ACCOUNT = "账号"
private const val LABEL_PASSWORD = "密码"
private const val LABEL_ADVANCED = "服务连接"
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
    val networkState by NetworkConnectivityMonitor.state.collectAsState()
    val scope = rememberCoroutineScope()
    var account by rememberSaveable { mutableStateOf(BackendAutoLoginManager.DEFAULT_DEMO_ACCOUNT) }
    var password by rememberSaveable { mutableStateOf(BackendAutoLoginManager.DEFAULT_DEMO_PASSWORD) }
    var baseUrlInput by rememberSaveable(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var advancedMessage by remember { mutableStateOf<String?>(null) }
    var pendingLoginRequest by remember { mutableStateOf<LoginRequestDto?>(null) }
    var waitingForNetwork by remember { mutableStateOf(false) }
    var loginAttemptId by remember { mutableIntStateOf(0) }
    var loginJob by remember { mutableStateOf<Job?>(null) }

    fun finishLogin(session: RemoteLoginSession) {
        pendingLoginRequest = null
        waitingForNetwork = false
        loginJob = null
        isLoading = false
        errorMessage = null
        statusMessage = null
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

    fun launchQueuedLogin(attemptId: Int, request: LoginRequestDto) {
        if (attemptId != loginAttemptId || pendingLoginRequest != request) {
            return
        }
        if (!networkState.isConnected) {
            loginJob?.cancel()
            loginJob = null
            isLoading = true
            waitingForNetwork = true
            errorMessage = null
            statusMessage = "当前网络不可用，恢复后会自动登录。"
            return
        }
        waitingForNetwork = false
        errorMessage = null
        statusMessage = null
        isLoading = true
        val launchConnectivityVersion = NetworkConnectivityMonitor.currentState.changeVersion
        loginJob?.cancel()
        var launchedJob: Job? = null
        launchedJob = scope.launch {
            try {
                when (val loginResult = RepositoryProvider.authRepository.login(request)) {
                    is ApiResult.Success -> {
                        if (attemptId == loginAttemptId && pendingLoginRequest == request) {
                            finishLogin(loginResult.data)
                        }
                    }
                    is ApiResult.Error -> {
                        if (attemptId != loginAttemptId || pendingLoginRequest != request) {
                            return@launch
                        }
                        val connectivityChangedDuringRequest =
                            NetworkConnectivityMonitor.currentState.changeVersion != launchConnectivityVersion
                        val shouldWaitForReconnect =
                            loginResult.throwable is IOException &&
                                (
                                    !NetworkConnectivityMonitor.currentState.isConnected ||
                                        connectivityChangedDuringRequest
                                    )
                        if (shouldWaitForReconnect) {
                            isLoading = true
                            waitingForNetwork = true
                            errorMessage = null
                            statusMessage = if (NetworkConnectivityMonitor.currentState.isConnected) {
                                "网络已恢复，正在重新登录..."
                            } else {
                                "网络已断开，恢复后会自动登录。"
                            }
                        } else {
                            pendingLoginRequest = null
                            waitingForNetwork = false
                            isLoading = false
                            errorMessage = loginResult.message
                            statusMessage = null
                        }
                    }
                    ApiResult.Loading -> Unit
                }
            } catch (_: CancellationException) {
                Unit
            } catch (throwable: Throwable) {
                if (attemptId != loginAttemptId || pendingLoginRequest != request) {
                    return@launch
                }
                val connectivityChangedDuringRequest =
                    NetworkConnectivityMonitor.currentState.changeVersion != launchConnectivityVersion
                val shouldWaitForReconnect =
                    throwable is IOException &&
                        (
                            !NetworkConnectivityMonitor.currentState.isConnected ||
                                connectivityChangedDuringRequest
                            )
                if (shouldWaitForReconnect) {
                    isLoading = true
                    waitingForNetwork = true
                    errorMessage = null
                    statusMessage = if (NetworkConnectivityMonitor.currentState.isConnected) {
                        "网络已恢复，正在重新登录..."
                    } else {
                        "网络已断开，恢复后会自动登录。"
                    }
                } else {
                    pendingLoginRequest = null
                    waitingForNetwork = false
                    isLoading = false
                    errorMessage = throwable.message ?: ERROR_LOGIN_FAILED
                    statusMessage = null
                }
            } finally {
                if (loginJob === launchedJob) {
                    loginJob = null
                }
            }
        }
        loginJob = launchedJob
    }

    LaunchedEffect(Unit) {
        if (account.isBlank()) {
            account = BackendAutoLoginManager.DEFAULT_DEMO_ACCOUNT
        }
        if (password.isBlank()) {
            password = BackendAutoLoginManager.DEFAULT_DEMO_PASSWORD
        }
    }
    LaunchedEffect(settings.baseUrl) {
        if (baseUrlInput != settings.baseUrl) {
            baseUrlInput = settings.baseUrl
        }
    }
    LaunchedEffect(networkState.changeVersion, networkState.isConnected, pendingLoginRequest, waitingForNetwork) {
        val queuedRequest = pendingLoginRequest ?: return@LaunchedEffect
        if (!networkState.isConnected) {
            if (networkState.changeVersion > 0 && (isLoading || loginJob != null)) {
                loginJob?.cancel()
                loginJob = null
                isLoading = true
                waitingForNetwork = true
                errorMessage = null
                statusMessage = "网络已断开，恢复后会自动登录。"
            }
            return@LaunchedEffect
        }
        if ((waitingForNetwork || loginJob == null) && queuedRequest == pendingLoginRequest) {
            launchQueuedLogin(loginAttemptId, queuedRequest)
        }
    }

    YingShiMistBackground(modifier = modifier, showWaves = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 44.dp)
                .padding(top = 118.dp, bottom = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = APP_NAME,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.titleAccent,
            )

            Column(
                modifier = Modifier.padding(top = 52.dp),
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

                (errorMessage ?: statusMessage ?: sessionMessage)?.let { message ->
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
                    text = when {
                        waitingForNetwork -> "等待网络恢复"
                        isLoading -> "正在登录"
                        else -> ACTION_LOGIN
                    },
                    onClick = {
                        val request = LoginRequestDto(
                            account = account.trim(),
                            password = password,
                        )
                        loginAttemptId += 1
                        pendingLoginRequest = request
                        waitingForNetwork = false
                        errorMessage = null
                        statusMessage = null
                        launchQueuedLogin(loginAttemptId, request)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 38.dp),
                    enabled = !isLoading && account.isNotBlank() && password.isNotBlank(),
                    loading = isLoading,
                )
            }

            AdvancedRow(
                expanded = advancedExpanded,
                currentBaseUrl = settings.baseUrl,
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
                                .yingShiClickable(enabled = !isLoading, shape = RoundedCornerShape(radius.capsule)) {
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
        }
    }
}

@Composable
private fun AdvancedRow(
    expanded: Boolean,
    currentBaseUrl: String,
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
        color = colors.raisedSurface.copy(alpha = 0.74f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.10f))
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                tint = colors.titleAccent,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = LABEL_ADVANCED,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = currentBaseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
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
