package com.example.yingshi.data.remote.auth

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
    val account: String = DEFAULT_PRIMARY_ACCOUNT,
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

const val DEFAULT_PRIMARY_ACCOUNT = "1085060329@qq.com"
const val DEFAULT_SECONDARY_ACCOUNT = "2926315047@qq.com"
const val DEFAULT_TEMP_PASSWORD = "123456"

object BackendAutoLoginManager {
    const val DEFAULT_DEMO_ACCOUNT = DEFAULT_PRIMARY_ACCOUNT
    const val DEFAULT_DEMO_PASSWORD = DEFAULT_TEMP_PASSWORD
    const val SECONDARY_DEMO_ACCOUNT = DEFAULT_SECONDARY_ACCOUNT

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
