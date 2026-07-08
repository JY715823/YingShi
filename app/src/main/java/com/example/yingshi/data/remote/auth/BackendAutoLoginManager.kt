package com.example.yingshi.data.remote.auth

import com.example.yingshi.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BackendAutoLoginPhase {
    Idle,
    LoggingIn,
    Success,
    Failed,
}

data class BackendAutoLoginUiState(
    val phase: BackendAutoLoginPhase = BackendAutoLoginPhase.Idle,
    val message: String = "尚未尝试会话恢复",
    val account: String = BuildConfig.DEFAULT_PRIMARY_ACCOUNT,
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

// Default values from BuildConfig — email addresses available in all build types,
// password only in debug (empty string in release).
val DEFAULT_PRIMARY_ACCOUNT: String get() = BuildConfig.DEFAULT_PRIMARY_ACCOUNT
val DEFAULT_SECONDARY_ACCOUNT: String get() = BuildConfig.DEFAULT_SECONDARY_ACCOUNT
val DEFAULT_TEMP_PASSWORD: String get() = BuildConfig.DEFAULT_TEMP_PASSWORD

object BackendAutoLoginManager {
    val DEFAULT_DEMO_ACCOUNT: String get() = BuildConfig.DEFAULT_PRIMARY_ACCOUNT
    val DEFAULT_DEMO_PASSWORD: String get() = BuildConfig.DEFAULT_TEMP_PASSWORD
    val SECONDARY_DEMO_ACCOUNT: String get() = BuildConfig.DEFAULT_SECONDARY_ACCOUNT

    private val state = MutableStateFlow(BackendAutoLoginUiState(account = DEFAULT_PRIMARY_ACCOUNT))

    val uiState = state.asStateFlow()

    suspend fun loginDefault(
        force: Boolean = false,
        reason: String = "app_start",
    ): BackendAutoLoginOutcome {
        val startedAt = System.currentTimeMillis()
        if (AuthSessionManager.isLoggedIn) {
            val snapshot = AuthSessionManager.getCurrentUserSnapshot()
            val successMessage = if (snapshot?.displayName.isNullOrBlank()) {
                "当前登录会话仍有效。"
            } else {
                "当前登录会话仍有效：${snapshot?.displayName}"
            }
            state.value = BackendAutoLoginUiState(
                phase = BackendAutoLoginPhase.Success,
                message = successMessage,
                account = snapshot?.account ?: DEFAULT_PRIMARY_ACCOUNT,
                lastReason = reason,
                lastAttemptAtMillis = startedAt,
            )
            return BackendAutoLoginOutcome(
                success = true,
                message = successMessage,
                displayName = snapshot?.displayName,
            )
        }

        val message = when (reason) {
            "save_base_url" -> "服务地址已更新，请重新登录。"
            else -> "当前没有可自动恢复的登录会话，请重新登录。"
        }
        state.value = BackendAutoLoginUiState(
            phase = BackendAutoLoginPhase.Failed,
            message = message,
            account = AuthSessionManager.getCurrentUserSnapshot()?.account ?: DEFAULT_PRIMARY_ACCOUNT,
            lastReason = reason,
            lastAttemptAtMillis = startedAt,
        )
        return BackendAutoLoginOutcome(
            success = false,
            message = message,
        )
    }

    fun markLoggedOut(message: String = "已清除登录") {
        state.value = state.value.copy(
            phase = BackendAutoLoginPhase.Idle,
            message = message,
            lastReason = "manual_logout",
            lastAttemptAtMillis = System.currentTimeMillis(),
        )
    }
}
