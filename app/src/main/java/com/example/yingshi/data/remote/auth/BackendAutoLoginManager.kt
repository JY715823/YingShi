package com.example.yingshi.data.remote.auth

import com.example.yingshi.data.model.AuthTokens
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.config.RemoteConfig
import com.example.yingshi.data.remote.config.RemoteServiceFactory
import com.example.yingshi.data.remote.dto.LoginRequestDto
import com.example.yingshi.data.remote.mapper.toRemoteModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

enum class BackendAutoLoginPhase {
    Idle,
    LoggingIn,
    Success,
    Failed,
}

data class BackendAutoLoginUiState(
    val phase: BackendAutoLoginPhase = BackendAutoLoginPhase.Idle,
    val message: String = "尚未尝试自动登录",
    val account: String = "demo.a@yingshi.local",
    val lastReason: String = "",
    val lastAttemptAtMillis: Long? = null,
) {
    val inFlight: Boolean
        get() = phase == BackendAutoLoginPhase.LoggingIn
}

data class BackendAutoLoginOutcome(
    val success: Boolean,
    val message: String,
    val displayName: String? = null,
)

object BackendAutoLoginManager {
    const val DEFAULT_DEMO_ACCOUNT = "demo.a@yingshi.local"
    const val DEFAULT_DEMO_PASSWORD = "demo123456"

    private val loginLock = AtomicBoolean(false)
    private val state = MutableStateFlow(BackendAutoLoginUiState(account = DEFAULT_DEMO_ACCOUNT))

    val uiState = state.asStateFlow()

    suspend fun loginDefault(
        force: Boolean = false,
        reason: String = "app_start",
    ): BackendAutoLoginOutcome {
        if (reason.startsWith("real_") && !shouldAllowAutoLoginForBaseUrl(BackendDebugConfig.currentBaseUrl())) {
            val message = "需要先完成登录，请检查连接设置后重试。"
            state.value = state.value.copy(
                phase = BackendAutoLoginPhase.Failed,
                message = message,
                lastReason = reason,
                lastAttemptAtMillis = System.currentTimeMillis(),
            )
            return BackendAutoLoginOutcome(
                success = false,
                message = message,
            )
        }
        return loginWithCredentials(
            account = DEFAULT_DEMO_ACCOUNT,
            password = DEFAULT_DEMO_PASSWORD,
            force = force,
            reason = reason,
        )
    }

    suspend fun loginWithCredentials(
        account: String,
        password: String,
        force: Boolean = true,
        reason: String = "manual",
    ): BackendAutoLoginOutcome {
        val normalizedAccount = account.trim()
        if (normalizedAccount.isBlank()) {
            return BackendAutoLoginOutcome(
                success = false,
                message = "账号不能为空。",
            )
        }

        if (!force && AuthSessionManager.isLoggedIn) {
            val message = "当前已经登录，无需重复登录。"
            state.value = state.value.copy(
                phase = BackendAutoLoginPhase.Success,
                message = message,
                account = normalizedAccount,
                lastReason = reason,
                lastAttemptAtMillis = System.currentTimeMillis(),
            )
            return BackendAutoLoginOutcome(
                success = true,
                message = message,
            )
        }

        if (!loginLock.compareAndSet(false, true)) {
            return BackendAutoLoginOutcome(
                success = false,
                message = "正在登录中，请稍后再试。",
            )
        }

        val startedAt = System.currentTimeMillis()
        state.value = state.value.copy(
            phase = BackendAutoLoginPhase.LoggingIn,
            message = "正在登录中…",
            account = normalizedAccount,
            lastReason = reason,
            lastAttemptAtMillis = startedAt,
        )

        return try {
            val response = RemoteServiceFactory.authApi.login(
                LoginRequestDto(
                    account = normalizedAccount,
                    password = password,
                ),
            ).data

            AuthSessionManager.saveTokens(
                AuthTokens(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken,
                    accessTokenExpireAtMillis = response.accessTokenExpireAtMillis,
                    refreshTokenExpireAtMillis = response.refreshTokenExpireAtMillis,
                ),
            )
            AuthSessionManager.saveCurrentUserSnapshot(
                RemoteCurrentUser(
                    userId = response.userId,
                    account = response.account,
                    displayName = response.displayName,
                    avatarUrl = response.avatarUrl,
                    libraryId = response.libraryId,
                    libraryDisplayName = response.libraryDisplayName,
                    bio = response.bio,
                    partner = response.partner?.toRemoteModel(),
                    createdAtMillis = response.createdAtMillis,
                    updatedAtMillis = response.updatedAtMillis,
                ),
            )

            val successMessage = "登录成功：${response.displayName}"
            state.value = BackendAutoLoginUiState(
                phase = BackendAutoLoginPhase.Success,
                message = successMessage,
                account = normalizedAccount,
                lastReason = reason,
                lastAttemptAtMillis = startedAt,
            )
            BackendAutoLoginOutcome(
                success = true,
                message = successMessage,
                displayName = response.displayName,
            )
        } catch (e: Exception) {
            val errorMessage = e.localizedMessage?.takeIf { it.isNotBlank() }
                ?: e::class.java.simpleName
            val failureMessage = "登录失败：$errorMessage"
            state.value = BackendAutoLoginUiState(
                phase = BackendAutoLoginPhase.Failed,
                message = failureMessage,
                account = normalizedAccount,
                lastReason = reason,
                lastAttemptAtMillis = startedAt,
            )
            BackendAutoLoginOutcome(
                success = false,
                message = failureMessage,
            )
        } finally {
            loginLock.set(false)
        }
    }

    fun markLoggedOut(message: String = "已清除登录") {
        state.value = state.value.copy(
            phase = BackendAutoLoginPhase.Idle,
            message = message,
            lastReason = "manual_logout",
            lastAttemptAtMillis = System.currentTimeMillis(),
        )
    }

    internal fun shouldAllowAutoLoginForBaseUrl(baseUrl: String): Boolean {
        val normalized = baseUrl.trim()
        if (normalized.isBlank()) return false
        val host = runCatching { URI(normalized).host?.trim()?.lowercase() }.getOrNull()
            ?: return false
        if (host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2") {
            return true
        }
        if (host.endsWith(".local") || host.endsWith(".localhost")) {
            return true
        }
        if (host.startsWith("10.")) {
            return true
        }
        if (host.startsWith("192.168.")) {
            return true
        }
        if (host.startsWith("172.")) {
            val secondSegment = host.split('.').getOrNull(1)?.toIntOrNull()
            if (secondSegment != null && secondSegment in 16..31) {
                return true
            }
        }
        return normalized == RemoteConfig.DEBUG_EMULATOR_BASE_URL ||
            normalized == RemoteConfig.DEBUG_DEVICE_LOOPBACK_BASE_URL
    }
}
