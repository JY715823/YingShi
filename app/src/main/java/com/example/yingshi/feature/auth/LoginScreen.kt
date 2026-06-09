package com.example.yingshi.feature.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.model.RemoteLoginChallenge
import com.example.yingshi.data.model.RemoteLoginSession
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.auth.DEFAULT_PRIMARY_ACCOUNT
import com.example.yingshi.data.remote.auth.DEFAULT_SECONDARY_ACCOUNT
import com.example.yingshi.data.remote.auth.DEFAULT_TEMP_PASSWORD
import com.example.yingshi.data.remote.connectivity.NetworkConnectivityMonitor
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.dto.LoginRequestDto
import com.example.yingshi.data.remote.dto.RememberedLoginRequestDto
import com.example.yingshi.data.remote.dto.ResendLoginChallengeRequestDto
import com.example.yingshi.data.remote.dto.VerifyLoginChallengeRequestDto
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.components.YingShiPrimaryMistButton
import com.example.yingshi.ui.components.YingShiTextField
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val APP_NAME = "映世"
private const val LABEL_ACCOUNT = "用户名"
private const val LABEL_PASSWORD = "密码"
private const val LABEL_CODE = "验证码"
private const val LABEL_BACKEND_ADDRESS = "服务地址"

private data class ActiveLoginChallenge(
    val challengeId: String,
    val expireAtMillis: Long,
    val resendAvailableAtMillis: Long,
)

private sealed interface PendingLoginAction {
    data class RememberedLogin(
        val request: RememberedLoginRequestDto,
        val fallbackRequest: LoginRequestDto,
    ) : PendingLoginAction

    data class RequestChallenge(
        val request: LoginRequestDto,
    ) : PendingLoginAction

    data class ResendChallenge(
        val request: ResendLoginChallengeRequestDto,
    ) : PendingLoginAction

    data class VerifyChallenge(
        val request: VerifyLoginChallengeRequestDto,
    ) : PendingLoginAction
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    sessionMessage: String? = null,
    onLoginSuccess: (RemoteCurrentUser) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val settings = BackendDebugConfig.settings
    val networkState by NetworkConnectivityMonitor.state.collectAsState()
    val scope = rememberCoroutineScope()

    var account by rememberSaveable {
        mutableStateOf(AuthSessionManager.getLastSignedInAccount() ?: DEFAULT_PRIMARY_ACCOUNT)
    }
    var password by rememberSaveable { mutableStateOf(DEFAULT_TEMP_PASSWORD) }
    var verificationCode by rememberSaveable { mutableStateOf("") }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var baseUrlInput by rememberSaveable(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var showConnectionSheet by rememberSaveable { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<PendingLoginAction?>(null) }
    var waitingForNetwork by remember { mutableStateOf(false) }
    var actionNonce by remember { mutableIntStateOf(0) }
    var actionJob by remember { mutableStateOf<Job?>(null) }
    var challengeState by remember { mutableStateOf<ActiveLoginChallenge?>(null) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    val resendRemainingMillis = (challengeState?.resendAvailableAtMillis ?: 0L) - nowMillis
    val resendRemainingSeconds = (resendRemainingMillis.coerceAtLeast(0L) + 999L) / 1000L
    val challengeExpired = challengeState?.let { nowMillis >= it.expireAtMillis } ?: false
    val isRememberedLoginPending = pendingAction is PendingLoginAction.RememberedLogin && challengeState == null
    val currentMessage = errorMessage ?: statusMessage ?: sessionMessage

    fun finishLogin(session: RemoteLoginSession) {
        pendingAction = null
        waitingForNetwork = false
        actionJob = null
        isLoading = false
        errorMessage = null
        statusMessage = null
        verificationCode = ""
        challengeState = null
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

    fun applyChallenge(challenge: RemoteLoginChallenge) {
        challengeState = ActiveLoginChallenge(
            challengeId = challenge.challengeId,
            expireAtMillis = challenge.expireAtMillis,
            resendAvailableAtMillis = challenge.resendAvailableAtMillis,
        )
        verificationCode = ""
        nowMillis = System.currentTimeMillis()
    }

    fun clearChallenge() {
        challengeState = null
        verificationCode = ""
        nowMillis = System.currentTimeMillis()
    }

    fun handleActionError(result: ApiResult.Error) {
        val effectiveCode = result.code
        if (
            effectiveCode == "AUTH_LOGIN_CODE_EXPIRED" ||
            effectiveCode == "AUTH_LOGIN_CHALLENGE_INVALID"
        ) {
            clearChallenge()
        }
        errorMessage = result.message
        statusMessage = null
        pendingAction = null
        waitingForNetwork = false
        isLoading = false
    }

    fun launchPendingAction(expectedNonce: Int, action: PendingLoginAction) {
        if (expectedNonce != actionNonce || pendingAction != action) {
            return
        }
        if (!networkState.isConnected) {
            actionJob?.cancel()
            actionJob = null
            isLoading = true
            waitingForNetwork = true
            errorMessage = null
            statusMessage = when (action) {
                is PendingLoginAction.RememberedLogin -> "当前网络不可用，恢复后会自动继续登录。"
                is PendingLoginAction.RequestChallenge -> "当前网络不可用，恢复后会自动发送验证码。"
                is PendingLoginAction.ResendChallenge -> "当前网络不可用，恢复后会自动重发验证码。"
                is PendingLoginAction.VerifyChallenge -> "当前网络不可用，恢复后会自动继续登录。"
            }
            return
        }

        waitingForNetwork = false
        errorMessage = null
        statusMessage = null
        isLoading = true
        val launchConnectivityVersion = NetworkConnectivityMonitor.currentState.changeVersion
        actionJob?.cancel()
        var launchedJob: Job? = null
        launchedJob = scope.launch {
            try {
                when (action) {
                    is PendingLoginAction.RememberedLogin -> {
                        when (val result = RepositoryProvider.authRepository.loginWithRememberedDevice(action.request)) {
                            is ApiResult.Success -> {
                                if (expectedNonce == actionNonce && pendingAction == action) {
                                    finishLogin(result.data)
                                }
                            }
                            is ApiResult.Error -> {
                                if (expectedNonce == actionNonce && pendingAction == action) {
                                    val shouldWaitForReconnect =
                                        result.throwable is IOException &&
                                            (
                                                !NetworkConnectivityMonitor.currentState.isConnected ||
                                                    NetworkConnectivityMonitor.currentState.changeVersion != launchConnectivityVersion
                                                )
                                    if (shouldWaitForReconnect) {
                                        isLoading = true
                                        waitingForNetwork = true
                                        errorMessage = null
                                        statusMessage = if (NetworkConnectivityMonitor.currentState.isConnected) {
                                            "网络已恢复，正在继续登录..."
                                        } else {
                                            "网络已断开，恢复后会自动继续登录。"
                                        }
                                    } else if (
                                        result.code == "AUTH_REMEMBERED_LOGIN_INVALID" ||
                                            result.code == "AUTH_REMEMBERED_LOGIN_EXPIRED"
                                    ) {
                                        AuthSessionManager.clearRememberedLogin(action.request.account)
                                        statusMessage = if (result.code == "AUTH_REMEMBERED_LOGIN_EXPIRED") {
                                            "本机免验证已过期，正在重新发送验证码..."
                                        } else {
                                            "本机免验证已失效，正在重新发送验证码..."
                                        }
                                        actionNonce += 1
                                        val fallbackNonce = actionNonce
                                        val fallbackAction = PendingLoginAction.RequestChallenge(action.fallbackRequest)
                                        pendingAction = fallbackAction
                                        waitingForNetwork = false
                                        actionJob = null
                                        launchPendingAction(fallbackNonce, fallbackAction)
                                        return@launch
                                    } else {
                                        handleActionError(result)
                                    }
                                }
                            }
                            ApiResult.Loading -> Unit
                        }
                    }

                    is PendingLoginAction.RequestChallenge -> {
                        when (val result = RepositoryProvider.authRepository.requestLoginChallenge(action.request)) {
                            is ApiResult.Success -> {
                                if (expectedNonce == actionNonce && pendingAction == action) {
                                    applyChallenge(result.data)
                                    pendingAction = null
                                    isLoading = false
                                    statusMessage = "验证码已发送，请查看邮箱。"
                                }
                            }
                            is ApiResult.Error -> {
                                if (expectedNonce == actionNonce && pendingAction == action) {
                                    val shouldWaitForReconnect =
                                        result.throwable is IOException &&
                                            (
                                                !NetworkConnectivityMonitor.currentState.isConnected ||
                                                    NetworkConnectivityMonitor.currentState.changeVersion != launchConnectivityVersion
                                                )
                                    if (shouldWaitForReconnect) {
                                        isLoading = true
                                        waitingForNetwork = true
                                        errorMessage = null
                                        statusMessage = if (NetworkConnectivityMonitor.currentState.isConnected) {
                                            "网络已恢复，正在重新发送验证码..."
                                        } else {
                                            "网络已断开，恢复后会自动发送验证码。"
                                        }
                                    } else {
                                        handleActionError(result)
                                    }
                                }
                            }
                            ApiResult.Loading -> Unit
                        }
                    }

                    is PendingLoginAction.ResendChallenge -> {
                        when (val result = RepositoryProvider.authRepository.resendLoginChallenge(action.request)) {
                            is ApiResult.Success -> {
                                if (expectedNonce == actionNonce && pendingAction == action) {
                                    applyChallenge(result.data)
                                    pendingAction = null
                                    isLoading = false
                                    statusMessage = "验证码已重新发送，请查看邮箱。"
                                }
                            }
                            is ApiResult.Error -> {
                                if (expectedNonce == actionNonce && pendingAction == action) {
                                    val shouldWaitForReconnect =
                                        result.throwable is IOException &&
                                            (
                                                !NetworkConnectivityMonitor.currentState.isConnected ||
                                                    NetworkConnectivityMonitor.currentState.changeVersion != launchConnectivityVersion
                                                )
                                    if (shouldWaitForReconnect) {
                                        isLoading = true
                                        waitingForNetwork = true
                                        errorMessage = null
                                        statusMessage = if (NetworkConnectivityMonitor.currentState.isConnected) {
                                            "网络已恢复，正在重新发送验证码..."
                                        } else {
                                            "网络已断开，恢复后会自动重发验证码。"
                                        }
                                    } else {
                                        handleActionError(result)
                                    }
                                }
                            }
                            ApiResult.Loading -> Unit
                        }
                    }

                    is PendingLoginAction.VerifyChallenge -> {
                        when (val result = RepositoryProvider.authRepository.verifyLoginChallenge(action.request)) {
                            is ApiResult.Success -> {
                                if (expectedNonce == actionNonce && pendingAction == action) {
                                    finishLogin(result.data)
                                }
                            }
                            is ApiResult.Error -> {
                                if (expectedNonce == actionNonce && pendingAction == action) {
                                    val shouldWaitForReconnect =
                                        result.throwable is IOException &&
                                            (
                                                !NetworkConnectivityMonitor.currentState.isConnected ||
                                                    NetworkConnectivityMonitor.currentState.changeVersion != launchConnectivityVersion
                                                )
                                    if (shouldWaitForReconnect) {
                                        isLoading = true
                                        waitingForNetwork = true
                                        errorMessage = null
                                        statusMessage = if (NetworkConnectivityMonitor.currentState.isConnected) {
                                            "网络已恢复，正在继续登录..."
                                        } else {
                                            "网络已断开，恢复后会自动继续登录。"
                                        }
                                    } else {
                                        handleActionError(result)
                                    }
                                }
                            }
                            ApiResult.Loading -> Unit
                        }
                    }
                }
            } catch (_: CancellationException) {
                Unit
            } catch (throwable: Throwable) {
                if (expectedNonce != actionNonce || pendingAction != action) {
                    return@launch
                }
                val shouldWaitForReconnect =
                    throwable is IOException &&
                        (
                            !NetworkConnectivityMonitor.currentState.isConnected ||
                                NetworkConnectivityMonitor.currentState.changeVersion != launchConnectivityVersion
                            )
                if (shouldWaitForReconnect) {
                    isLoading = true
                    waitingForNetwork = true
                    errorMessage = null
                    statusMessage = "网络状态已变化，恢复后会自动继续当前登录动作。"
                } else {
                    pendingAction = null
                    waitingForNetwork = false
                    isLoading = false
                    errorMessage = throwable.message ?: "登录失败，请稍后重试。"
                    statusMessage = null
                }
            } finally {
                if (actionJob === launchedJob) {
                    actionJob = null
                }
            }
        }
        actionJob = launchedJob
    }

    fun queueAction(action: PendingLoginAction) {
        actionNonce += 1
        pendingAction = action
        waitingForNetwork = false
        errorMessage = null
        statusMessage = null
        launchPendingAction(actionNonce, action)
    }

    LaunchedEffect(settings.baseUrl) {
        if (baseUrlInput != settings.baseUrl) {
            baseUrlInput = settings.baseUrl
        }
    }

    LaunchedEffect(challengeState?.challengeId) {
        while (challengeState != null) {
            nowMillis = System.currentTimeMillis()
            delay(1000)
        }
    }

    LaunchedEffect(networkState.changeVersion, networkState.isConnected, pendingAction, waitingForNetwork) {
        val queuedAction = pendingAction ?: return@LaunchedEffect
        if (!networkState.isConnected) {
            if (networkState.changeVersion > 0 && (isLoading || actionJob != null)) {
                actionJob?.cancel()
                actionJob = null
                isLoading = true
                waitingForNetwork = true
                errorMessage = null
                statusMessage = "网络已断开，恢复后会自动继续当前登录动作。"
            }
            return@LaunchedEffect
        }
        if ((waitingForNetwork || actionJob == null) && queuedAction == pendingAction) {
            launchPendingAction(actionNonce, queuedAction)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF9FDFF),
                        Color(0xFFEAF8FF),
                        Color(0xFFE8F5EF),
                        Color(0xFFFFEEE2),
                        Color(0xFFF7FCFF),
                    ),
                ),
            ),
    ) {
        LoginBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
        ) {
            Spacer(modifier = Modifier.weight(0.72f, fill = true))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                BrandHeader()

                PresetAccountRow(
                    selectedAccount = account,
                    onSelect = { selected ->
                        account = selected
                        password = DEFAULT_TEMP_PASSWORD
                        errorMessage = null
                    },
                )

                YingShiTextField(
                    value = account,
                    onValueChange = {
                        account = it
                        errorMessage = null
                    },
                    placeholder = LABEL_ACCOUNT,
                    icon = Icons.Rounded.AlternateEmail,
                    enabled = !isLoading && challengeState == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (challengeState == null) {
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
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingContent = {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (isPasswordVisible) "隐藏密码" else "显示密码",
                                tint = colors.titleAccent,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .yingShiClickable(shape = CircleShape) {
                                        isPasswordVisible = !isPasswordVisible
                                    }
                                    .padding(4.dp)
                                    .size(22.dp),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    YingShiTextField(
                        value = verificationCode,
                        onValueChange = {
                            verificationCode = it.filter(Char::isDigit).take(6)
                            errorMessage = null
                        },
                        placeholder = LABEL_CODE,
                        icon = Icons.Rounded.Lock,
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                AuthMessageBlock(
                    message = currentMessage,
                    isError = errorMessage != null,
                )

                YingShiPrimaryMistButton(
                    text = when {
                        challengeState == null && waitingForNetwork && isRememberedLoginPending -> "等待网络恢复后登录"
                        challengeState == null && waitingForNetwork -> "等待网络恢复后发送"
                        challengeState == null && isLoading && isRememberedLoginPending -> "正在登录"
                        challengeState == null && isLoading -> "正在发送验证码"
                        challengeState == null -> "登录"
                        waitingForNetwork -> "等待网络恢复后登录"
                        isLoading -> "正在验证并登录"
                        else -> "完成登录"
                    },
                    onClick = {
                        if (challengeState == null) {
                            val normalizedAccount = account.trim()
                            val challengeRequest = LoginRequestDto(
                                account = normalizedAccount,
                                password = password,
                            )
                            val rememberedLoginToken = AuthSessionManager.getRememberedLoginToken(normalizedAccount)
                            if (rememberedLoginToken != null) {
                                queueAction(
                                    PendingLoginAction.RememberedLogin(
                                        request = RememberedLoginRequestDto(
                                            account = normalizedAccount,
                                            password = password,
                                            deviceId = AuthSessionManager.getDeviceId(),
                                            rememberedLoginToken = rememberedLoginToken,
                                        ),
                                        fallbackRequest = challengeRequest,
                                    ),
                                )
                            } else {
                                queueAction(
                                    PendingLoginAction.RequestChallenge(
                                        challengeRequest,
                                    ),
                                )
                            }
                        } else {
                            val activeChallenge = challengeState ?: return@YingShiPrimaryMistButton
                            queueAction(
                                PendingLoginAction.VerifyChallenge(
                                    VerifyLoginChallengeRequestDto(
                                        challengeId = activeChallenge.challengeId,
                                        code = verificationCode.trim(),
                                        deviceId = AuthSessionManager.getDeviceId(),
                                    ),
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = if (challengeState == null) {
                        account.isNotBlank() && password.isNotBlank() && !isLoading
                    } else {
                        verificationCode.length >= 6 && !isLoading
                    },
                    loading = isLoading,
                )

                if (challengeState != null) {
                    LoginInlineActions(
                        challengeExpired = challengeExpired,
                        resendRemainingSeconds = resendRemainingSeconds,
                        isLoading = isLoading,
                        onResend = {
                            val activeChallenge = challengeState ?: return@LoginInlineActions
                            if (challengeExpired) {
                                clearChallenge()
                                queueAction(
                                    PendingLoginAction.RequestChallenge(
                                        LoginRequestDto(
                                            account = account.trim(),
                                            password = password,
                                        ),
                                    ),
                                )
                            } else {
                                queueAction(
                                    PendingLoginAction.ResendChallenge(
                                        ResendLoginChallengeRequestDto(
                                            challengeId = activeChallenge.challengeId,
                                        ),
                                    ),
                                )
                            }
                        },
                        onBack = {
                            clearChallenge()
                            errorMessage = null
                            statusMessage = null
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f, fill = true))

            ConnectionEntryLink(
                onClick = { showConnectionSheet = true },
            )
        }
    }

    if (showConnectionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showConnectionSheet = false },
            containerColor = colors.raisedSurface.copy(alpha = 0.98f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "连接设置",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
                YingShiTextField(
                    value = baseUrlInput,
                    onValueChange = { baseUrlInput = it },
                    placeholder = LABEL_BACKEND_ADDRESS,
                    icon = Icons.Rounded.Settings,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
                YingShiPrimaryMistButton(
                    text = "保存地址",
                    onClick = {
                        BackendDebugConfig.updateBaseUrl(baseUrlInput)
                        baseUrlInput = BackendDebugConfig.currentBaseUrl()
                        clearChallenge()
                        errorMessage = null
                        statusMessage = "地址已保存，请重新登录。"
                        showConnectionSheet = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = baseUrlInput.isNotBlank() && !isLoading,
                )
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    val colors = YingShiThemeTokens.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = APP_NAME,
            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.titleAccent,
        )
    }
}

@Composable
private fun LoginBackdrop() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xD6FBFFFF), Color(0x36FBFFFF), Color.Transparent),
                center = Offset(width * 0.18f, height * 0.14f),
                radius = height * 0.40f,
            ),
            radius = height * 0.40f,
            center = Offset(width * 0.18f, height * 0.14f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xC2FFD8B8), Color(0x2BFFD8B8), Color.Transparent),
                center = Offset(width * 0.86f, height * 0.16f),
                radius = height * 0.30f,
            ),
            radius = height * 0.30f,
            center = Offset(width * 0.86f, height * 0.16f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xB8FFAFD7), Color(0x2EFFAFD7), Color.Transparent),
                center = Offset(width * 0.80f, height * 0.72f),
                radius = height * 0.34f,
            ),
            radius = height * 0.34f,
            center = Offset(width * 0.80f, height * 0.72f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xA261F2DF), Color(0x1D61F2DF), Color.Transparent),
                center = Offset(width * 0.16f, height * 0.82f),
                radius = height * 0.34f,
            ),
            radius = height * 0.34f,
            center = Offset(width * 0.16f, height * 0.82f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x9EFFFFFF), Color(0x28FFFFFF), Color.Transparent),
                center = Offset(width * 0.50f, height * 0.40f),
                radius = height * 0.26f,
            ),
            radius = height * 0.26f,
            center = Offset(width * 0.50f, height * 0.40f),
        )

        val upperAurora = Path().apply {
            moveTo(-width * 0.10f, height * 0.12f)
            cubicTo(
                width * 0.12f,
                height * 0.00f,
                width * 0.38f,
                height * 0.30f,
                width * 0.58f,
                height * 0.10f,
            )
            cubicTo(
                width * 0.82f,
                height * -0.02f,
                width * 0.96f,
                height * 0.20f,
                width * 1.10f,
                height * 0.02f,
            )
            lineTo(width * 1.10f, height * 0.20f)
            cubicTo(
                width * 0.90f,
                height * 0.34f,
                width * 0.62f,
                height * 0.18f,
                width * 0.34f,
                height * 0.34f,
            )
            cubicTo(
                width * 0.16f,
                height * 0.42f,
                width * 0.00f,
                height * 0.24f,
                -width * 0.10f,
                height * 0.28f,
            )
            close()
        }
        drawPath(
            path = upperAurora,
            brush = Brush.linearGradient(
                colors = listOf(Color(0x12FFFFFF), Color(0x76FFF4DF), Color(0x20BEEBFF), Color(0x10FFFFFF)),
                start = Offset(width * 0.04f, height * 0.04f),
                end = Offset(width * 0.94f, height * 0.28f),
            ),
        )

        val lowerAurora = Path().apply {
            moveTo(-width * 0.08f, height * 0.92f)
            cubicTo(
                width * 0.12f,
                height * 0.70f,
                width * 0.38f,
                height * 0.98f,
                width * 0.64f,
                height * 0.78f,
            )
            cubicTo(
                width * 0.84f,
                height * 0.60f,
                width * 0.96f,
                height * 0.88f,
                width * 1.08f,
                height * 0.62f,
            )
            lineTo(width * 1.08f, height * 0.82f)
            cubicTo(
                width * 0.86f,
                height * 1.00f,
                width * 0.60f,
                height * 0.84f,
                width * 0.28f,
                height * 1.04f,
            )
            cubicTo(
                width * 0.06f,
                height * 1.08f,
                -width * 0.04f,
                height * 0.96f,
                -width * 0.08f,
                height * 0.92f,
            )
            close()
        }
        drawPath(
            path = lowerAurora,
            brush = Brush.linearGradient(
                colors = listOf(Color(0x10FFFFFF), Color(0x5BCFFFF7), Color(0x55FFDBBC), Color(0x10FFFFFF)),
                start = Offset(width * 0.08f, height * 0.70f),
                end = Offset(width * 0.92f, height * 0.96f),
            ),
        )

        val glassSweep = Path().apply {
            moveTo(width * 0.08f, height * 0.44f)
            cubicTo(
                width * 0.26f,
                height * 0.30f,
                width * 0.46f,
                height * 0.62f,
                width * 0.66f,
                height * 0.44f,
            )
            cubicTo(
                width * 0.82f,
                height * 0.32f,
                width * 0.92f,
                height * 0.52f,
                width * 1.02f,
                height * 0.40f,
            )
        }
        drawPath(
            path = glassSweep,
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, Color(0xB7FFFFFF), Color(0x66D8FFF7), Color.Transparent),
            ),
            style = Stroke(width = 26f, cap = StrokeCap.Round),
        )

        val goldTrace = Path().apply {
            moveTo(width * 0.14f, height * 0.56f)
            cubicTo(
                width * 0.30f,
                height * 0.50f,
                width * 0.48f,
                height * 0.66f,
                width * 0.70f,
                height * 0.54f,
            )
            cubicTo(
                width * 0.84f,
                height * 0.46f,
                width * 0.92f,
                height * 0.54f,
                width * 1.00f,
                height * 0.48f,
            )
        }
        drawPath(
            path = goldTrace,
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, Color(0xD4F7CE83), Color.Transparent),
            ),
            style = Stroke(width = 5f, cap = StrokeCap.Round),
        )

        val petalVeil = Path().apply {
            moveTo(width * 0.54f, height * 0.16f)
            cubicTo(
                width * 0.66f,
                height * 0.26f,
                width * 0.72f,
                height * 0.42f,
                width * 0.62f,
                height * 0.54f,
            )
            cubicTo(
                width * 0.50f,
                height * 0.60f,
                width * 0.42f,
                height * 0.48f,
                width * 0.40f,
                height * 0.34f,
            )
            cubicTo(
                width * 0.42f,
                height * 0.22f,
                width * 0.46f,
                height * 0.16f,
                width * 0.54f,
                height * 0.16f,
            )
            close()
        }
        drawPath(
            path = petalVeil,
            brush = Brush.linearGradient(
                colors = listOf(Color(0x34FFFFFF), Color(0x12FFFFFF), Color(0x26FFC6E0)),
                start = Offset(width * 0.42f, height * 0.20f),
                end = Offset(width * 0.68f, height * 0.56f),
            ),
        )

        val lowerGlassBloom = Path().apply {
            moveTo(width * 0.12f, height * 0.66f)
            cubicTo(
                width * 0.22f,
                height * 0.58f,
                width * 0.36f,
                height * 0.72f,
                width * 0.34f,
                height * 0.88f,
            )
            cubicTo(
                width * 0.26f,
                height * 0.98f,
                width * 0.10f,
                height * 0.92f,
                width * 0.08f,
                height * 0.80f,
            )
            cubicTo(
                width * 0.08f,
                height * 0.72f,
                width * 0.08f,
                height * 0.68f,
                width * 0.12f,
                height * 0.66f,
            )
            close()
        }
        drawPath(
            path = lowerGlassBloom,
            brush = Brush.linearGradient(
                colors = listOf(Color(0x20FFFFFF), Color(0x16D2FFF0), Color(0x3AFFD5B8)),
                start = Offset(width * 0.08f, height * 0.64f),
                end = Offset(width * 0.34f, height * 0.92f),
            ),
        )

        repeat(24) { index ->
            val x = width * ((index * 29 + 17) % 100) / 100f
            val y = height * ((index * 13 + 23) % 100) / 100f
            drawCircle(
                color = if (index % 5 == 0) {
                    Color(0xFFFFF7E8).copy(alpha = 0.46f)
                } else {
                    Color.White.copy(alpha = if (index % 3 == 0) 0.34f else 0.18f)
                },
                radius = if (index % 4 == 0) 4.4f else 2.2f,
                center = Offset(x, y),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetAccountRow(
    selectedAccount: String,
    onSelect: (String) -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf(
            DEFAULT_PRIMARY_ACCOUNT to "账号 A",
            DEFAULT_SECONDARY_ACCOUNT to "账号 B",
        ).forEach { (account, label) ->
            val selected = selectedAccount.trim().equals(account, ignoreCase = true)
            Column(
                modifier = Modifier.yingShiClickable(shape = CircleShape) {
                    onSelect(account)
                },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) colors.titleAccent else colors.textSecondary,
                )
                Text(
                    text = account,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) colors.titleAccent.copy(alpha = 0.88f) else colors.textSecondary.copy(alpha = 0.88f),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .background(
                            if (selected) colors.titleAccent.copy(alpha = 0.55f) else Color.Transparent,
                        )
                        .height(2.dp),
                )
            }
        }
    }
}

@Composable
private fun AuthMessageBlock(
    message: String?,
    isError: Boolean,
) {
    if (message.isNullOrBlank()) {
        return
    }
    val colors = YingShiThemeTokens.colors
    Text(
        text = message,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else colors.titleAccent,
    )
}

@Composable
private fun LoginInlineActions(
    challengeExpired: Boolean,
    resendRemainingSeconds: Long,
    isLoading: Boolean,
    onResend: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onResend,
                enabled = !isLoading && (challengeExpired || resendRemainingSeconds <= 0L),
            ) {
                Text(
                    text = when {
                        challengeExpired -> "重新获取验证码"
                        resendRemainingSeconds > 0L -> "重发 ${resendRemainingSeconds}s"
                        else -> "重新发送验证码"
                    },
                    color = colors.titleAccent,
                )
            }
            TextButton(
                onClick = onBack,
                enabled = !isLoading,
            ) {
                Text(
                    text = "返回账号密码",
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ConnectionEntryLink(
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .yingShiClickable(shape = CircleShape, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Settings,
            contentDescription = null,
            tint = colors.titleAccent.copy(alpha = 0.88f),
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "连接设置",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.titleAccent,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    YingShiTheme {
        LoginScreen(onLoginSuccess = {})
    }
}
