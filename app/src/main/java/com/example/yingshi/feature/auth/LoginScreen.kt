package com.example.yingshi.feature.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.example.yingshi.BuildConfig
import com.example.yingshi.data.remote.connectivity.NetworkConnectivityMonitor
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.dto.LoginRequestDto
import com.example.yingshi.data.remote.dto.RememberedLoginRequestDto
import com.example.yingshi.data.remote.dto.ResendLoginChallengeRequestDto
import com.example.yingshi.data.remote.dto.VerifyLoginChallengeRequestDto
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.components.YingShiAuroraBackdrop
import com.example.yingshi.ui.components.YingShiBackdropVariant
import com.example.yingshi.ui.components.YingShiPrimaryMistButton
import com.example.yingshi.ui.components.YingShiTextField
import com.example.yingshi.ui.components.rememberYingShiMotionEnabled
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
    val motionEnabled = rememberYingShiMotionEnabled()
    val settings = BackendDebugConfig.settings
    val networkState by NetworkConnectivityMonitor.state.collectAsState()
    val scope = rememberCoroutineScope()

    var account by rememberSaveable {
        mutableStateOf(AuthSessionManager.getLastSignedInAccount() ?: BuildConfig.DEFAULT_PRIMARY_ACCOUNT)
    }
    var password by rememberSaveable { mutableStateOf(BuildConfig.DEFAULT_TEMP_PASSWORD) }
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
    var lockedUntilMillis by rememberSaveable { mutableLongStateOf(0L) }
    var lockedMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val resendRemainingMillis = (challengeState?.resendAvailableAtMillis ?: 0L) - nowMillis
    val resendRemainingSeconds = (resendRemainingMillis.coerceAtLeast(0L) + 999L) / 1000L
    val challengeExpired = challengeState?.let { nowMillis >= it.expireAtMillis } ?: false
    val isRememberedLoginPending = pendingAction is PendingLoginAction.RememberedLogin && challengeState == null
    val isAccountLocked = lockedUntilMillis > nowMillis
    val currentMessage = lockedMessage ?: errorMessage ?: statusMessage ?: sessionMessage

    fun finishLogin(session: RemoteLoginSession) {
        pendingAction = null
        waitingForNetwork = false
        actionJob = null
        isLoading = false
        errorMessage = null
        statusMessage = null
        verificationCode = ""
        challengeState = null
        lockedUntilMillis = 0L
        lockedMessage = null
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
        if (effectiveCode == "AUTH_ACCOUNT_LOCKED") {
            lockedUntilMillis = System.currentTimeMillis() + 15 * 60 * 1000L
            lockedMessage = "账号已临时锁定，请 15 分钟后再试"
            errorMessage = null
        } else {
            lockedUntilMillis = 0L
            lockedMessage = null
            errorMessage = result.message
        }
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
        lockedUntilMillis = 0L
        lockedMessage = null
        launchPendingAction(actionNonce, action)
    }

    LaunchedEffect(settings.baseUrl) {
        if (baseUrlInput != settings.baseUrl) {
            baseUrlInput = settings.baseUrl
        }
    }

    LaunchedEffect(challengeState?.challengeId, lockedUntilMillis) {
        while (challengeState != null || lockedUntilMillis > nowMillis) {
            nowMillis = System.currentTimeMillis()
            if (lockedUntilMillis <= nowMillis && lockedMessage != null) {
                lockedMessage = null
            }
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

    YingShiAuroraBackdrop(
        modifier = modifier.fillMaxSize(),
        variant = YingShiBackdropVariant.AUTH,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
        ) {
            Spacer(modifier = Modifier.weight(0.5f, fill = true))

            // Brand header — outside the card
            BrandHeader()

            Spacer(modifier = Modifier.height(28.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
                        ambientColor = colors.titleAccent.copy(alpha = 0.04f),
                        spotColor = colors.titleAccent.copy(alpha = 0.06f),
                    )
                    .shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
                        ambientColor = colors.glassStroke.copy(alpha = 0.08f),
                        spotColor = Color.Transparent,
                    ),
                shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
                color = colors.raisedSurface.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.50f)),
            ) {
                Box(
                    modifier = Modifier.background(
                        Brush.horizontalGradient(
                            listOf(
                                colors.glowWash.copy(alpha = 0.10f),
                                colors.raisedSurface.copy(alpha = 0.20f),
                                Color.Transparent,
                            ),
                        ),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(YingShiThemeTokens.spacing.xl),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        PresetAccountRow(
                            selectedAccount = account,
                            onSelect = { selected ->
                                account = selected
                                password = BuildConfig.DEFAULT_TEMP_PASSWORD
                                errorMessage = null
                                lockedUntilMillis = 0L
                                lockedMessage = null
                            },
                            motionEnabled = motionEnabled,
                        )

                        YingShiTextField(
                            value = account,
                            onValueChange = {
                                account = it
                                errorMessage = null
                                lockedUntilMillis = 0L
                                lockedMessage = null
                            },
                            placeholder = LABEL_ACCOUNT,
                            icon = Icons.Rounded.AlternateEmail,
                            enabled = !isLoading && challengeState == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        AnimatedContent(
                            targetState = challengeState != null,
                            transitionSpec = {
                                fadeIn(tween(if (motionEnabled) 250 else 0)) togetherWith
                                    fadeOut(tween(if (motionEnabled) 200 else 0))
                            },
                            label = "authFieldSwitch",
                        ) { isChallenge ->
                            if (!isChallenge) {
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
                        }

                        AnimatedVisibility(
                            visible = currentMessage != null,
                            enter = fadeIn(tween(if (motionEnabled) 250 else 0)),
                            exit = fadeOut(tween(if (motionEnabled) 200 else 0)),
                        ) {
                            AuthMessageBlock(
                                message = currentMessage,
                                isError = errorMessage != null,
                            )
                        }

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
                                account.isNotBlank() && password.isNotBlank() && !isLoading && !isAccountLocked
                            } else {
                                verificationCode.length >= 6 && !isLoading && !isAccountLocked
                            },
                            loading = isLoading,
                        )

                        // Divider with "或"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(0.5.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color.Transparent, colors.dividerSoft.copy(alpha = 0.50f)),
                                        ),
                                    ),
                            )
                            Text(
                                text = "或",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary.copy(alpha = 0.50f),
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(0.5.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(colors.dividerSoft.copy(alpha = 0.50f), Color.Transparent),
                                        ),
                                    ),
                            )
                        }

                        // Quick login button
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .yingShiClickable(shape = RoundedCornerShape(14.dp)) {
                                    val normalizedAccount = account.trim()
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
                                                fallbackRequest = LoginRequestDto(
                                                    account = normalizedAccount,
                                                    password = password,
                                                ),
                                            ),
                                        )
                                    }
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = colors.raisedSurface.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.45f)),
                        ) {
                            Text(
                                text = "同设备免验证重登",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 13.dp),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                color = colors.textSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }

                        AnimatedVisibility(
                            visible = challengeState != null,
                            enter = fadeIn(tween(if (motionEnabled) 250 else 0)) + slideInVertically(
                                animationSpec = tween(if (motionEnabled) 250 else 0),
                                initialOffsetY = { it / 4 },
                            ),
                            exit = fadeOut(tween(if (motionEnabled) 200 else 0)),
                        ) {
                            LoginInlineActions(
                                challengeExpired = challengeExpired,
                                resendRemainingSeconds = resendRemainingSeconds,
                                isLoading = isLoading,
                                isAccountLocked = isAccountLocked,
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Decorative gold line above
        Box(
            modifier = Modifier
                .widthIn(max = 40.dp)
                .fillMaxWidth()
                .height(1.5.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, colors.goldAccent.copy(alpha = 0.40f), Color.Transparent),
                    ),
                ),
        )
        Text(
            text = APP_NAME,
            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.titleAccent,
        )
        Text(
            text = "我们的共同空间",
            style = MaterialTheme.typography.titleSmall,
            color = colors.goldAccent.copy(alpha = 0.78f),
        )
        // Decorative subtle line below
        Box(
            modifier = Modifier
                .widthIn(max = 24.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, colors.dividerSoft, Color.Transparent),
                    ),
                ),
        )
    }
}

@Composable
private fun PresetAccountRow(
    selectedAccount: String,
    onSelect: (String) -> Unit,
    motionEnabled: Boolean = true,
) {
    val colors = YingShiThemeTokens.colors
    // Round 8 第十轮: 改用 Row 替代 FlowRow, 避免 experimental API 在不同 compose-foundation
    // 版本间的 NoSuchMethodError 崩溃 (这里只有2个账号, 不需要自动换行).
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        listOf(
            BuildConfig.DEFAULT_PRIMARY_ACCOUNT to "账号 A",
            BuildConfig.DEFAULT_SECONDARY_ACCOUNT to "账号 B",
        ).forEach { (account, label) ->
            val selected = selectedAccount.trim().equals(account, ignoreCase = true)
            val pillBg by animateColorAsState(
                targetValue = if (selected) colors.primaryContainer.copy(alpha = 0.15f) else colors.raisedSurface.copy(alpha = 0.45f),
                animationSpec = tween(if (motionEnabled) 300 else 0),
                label = "pillBg",
            )
            val pillBorder by animateColorAsState(
                targetValue = if (selected) colors.glassStroke.copy(alpha = 0.70f) else colors.dividerSoft.copy(alpha = 0.40f),
                animationSpec = tween(if (motionEnabled) 300 else 0),
                label = "pillBorder",
            )
            val labelColor by animateColorAsState(
                targetValue = if (selected) colors.titleAccent else colors.textSecondary,
                animationSpec = tween(if (motionEnabled) 200 else 0),
                label = "labelColor",
            )
            val accountColor by animateColorAsState(
                targetValue = if (selected) colors.titleAccent.copy(alpha = 0.88f) else colors.textSecondary.copy(alpha = 0.88f),
                animationSpec = tween(if (motionEnabled) 200 else 0),
                label = "accountColor",
            )
            val indicatorAlpha by animateColorAsState(
                targetValue = if (selected) colors.goldAccent.copy(alpha = 0.50f) else Color.Transparent,
                animationSpec = tween(if (motionEnabled) 300 else 0),
                label = "indicatorAlpha",
            )
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .yingShiClickable(shape = RoundedCornerShape(14.dp)) {
                        onSelect(account)
                    },
                shape = RoundedCornerShape(14.dp),
                color = pillBg,
                border = BorderStroke(1.5.dp, pillBorder),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = labelColor,
                    )
                    Text(
                        text = account,
                        style = MaterialTheme.typography.bodySmall,
                        color = accountColor,
                    )
                    // Gold indicator line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                            .height(2.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, indicatorAlpha, Color.Transparent),
                                ),
                            ),
                    )
                }
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
    isAccountLocked: Boolean,
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
                enabled = !isLoading && !isAccountLocked && (challengeExpired || resendRemainingSeconds <= 0L),
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
            tint = colors.textSecondary.copy(alpha = 0.72f),
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "连接设置",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
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
