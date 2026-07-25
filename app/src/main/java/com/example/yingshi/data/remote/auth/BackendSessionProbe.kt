package com.example.yingshi.data.remote.auth

import com.example.yingshi.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SessionProbePhase {
    Idle,
    LoggingIn,
    Success,
    Failed,
}

data class SessionProbeUiState(
    val phase: SessionProbePhase = SessionProbePhase.Idle,
    val message: String = "尚未尝试会话恢复",
    val account: String = BuildConfig.DEFAULT_PRIMARY_ACCOUNT,
    val lastReason: String = "",
    val lastAttemptAtMillis: Long? = null,
) {
    val inFlight: Boolean
        get() = phase == SessionProbePhase.LoggingIn
}

data class SessionProbeOutcome(
    val success: Boolean,
    val message: String,
    val displayName: String? = null,
)

// Top-level defaults are declared in BackendAutoLoginManager.kt (same package).

object BackendSessionProbe {
    val DEFAULT_DEMO_ACCOUNT: String get() = BuildConfig.DEFAULT_PRIMARY_ACCOUNT
    val DEFAULT_DEMO_PASSWORD: String get() = BuildConfig.DEFAULT_TEMP_PASSWORD
    val SECONDARY_DEMO_ACCOUNT: String get() = BuildConfig.DEFAULT_SECONDARY_ACCOUNT

    private val state = MutableStateFlow(SessionProbeUiState(account = DEFAULT_DEMO_ACCOUNT))

    val uiState = state.asStateFlow()

    suspend fun probeSessionState(
        force: Boolean = false,
        reason: String = "app_start",
    ): SessionProbeOutcome {
        val startedAt = System.currentTimeMillis()
        if (AuthSessionManager.isLoggedIn) {
            val snapshot = AuthSessionManager.getCurrentUserSnapshot()
            val successMessage = if (snapshot?.displayName.isNullOrBlank()) {
                "当前登录会话仍有效。"
            } else {
                "当前登录会话仍有效：${snapshot?.displayName}"
            }
            state.value = SessionProbeUiState(
                phase = SessionProbePhase.Success,
                message = successMessage,
                account = snapshot?.account ?: DEFAULT_DEMO_ACCOUNT,
                lastReason = reason,
                lastAttemptAtMillis = startedAt,
            )
            return SessionProbeOutcome(
                success = true,
                message = successMessage,
                displayName = snapshot?.displayName,
            )
        }

        val message = when (reason) {
            "save_base_url" -> "服务地址已更新，请重新登录。"
            else -> "当前没有可自动恢复的登录会话，请重新登录。"
        }
        state.value = SessionProbeUiState(
            phase = SessionProbePhase.Failed,
            message = message,
            account = AuthSessionManager.getCurrentUserSnapshot()?.account ?: DEFAULT_DEMO_ACCOUNT,
            lastReason = reason,
            lastAttemptAtMillis = startedAt,
        )
        return SessionProbeOutcome(
            success = false,
            message = message,
        )
    }

    fun markLoggedOut(message: String = "已清除登录") {
        state.value = state.value.copy(
            phase = SessionProbePhase.Idle,
            message = message,
            lastReason = "manual_logout",
            lastAttemptAtMillis = System.currentTimeMillis(),
        )
    }
}
