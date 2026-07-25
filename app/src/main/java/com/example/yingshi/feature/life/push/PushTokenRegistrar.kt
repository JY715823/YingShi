package com.example.yingshi.feature.life.push

import android.content.Context
import android.util.Log
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.photos.SettingsRepository
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

data class PushTokenDiagnosticState(
    val status: String = "待注册",
    val detail: String = "打开 App 或点击重试后，会尝试把本机注册为当前账号的推送设备。",
    val retryCount: Int = 0,
)

object PushTokenRegistrar {
    private const val TAG = "PushTokenRegistrar"
    private const val PLATFORM_ANDROID = "android"
    private const val MAX_RETRY_COUNT = 5
    private const val BASE_RETRY_DELAY_MS = 5000L
    private const val DEDUP_PREFS_NAME = "push_token_refresh"
    private const val DEDUP_KEY_LAST_TOKEN = "last_token"
    private const val DEDUP_KEY_LAST_REPORT_AT = "last_report_at"
    private const val DEDUP_WINDOW_MS = 5L * 60 * 1000 // 5 minutes
    private const val TOKEN_FETCH_TIMEOUT_SEC = 30L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _diagnosticState = MutableStateFlow(PushTokenDiagnosticState())
    val diagnosticState: StateFlow<PushTokenDiagnosticState> = _diagnosticState.asStateFlow()
    private var currentRetryCount = 0
    private var isRegistering = false

    fun registerCurrentTokenIfPossible(context: Context, forceRetry: Boolean = false) {
        val appContext = context.applicationContext
        if (isRegistering && !forceRetry) {
            Log.e(TAG, "Skip registration: already in progress")
            return
        }
        registrationBlockReason(appContext)?.let { reason ->
            Log.e(TAG, "BLOCKED: $reason")
            _diagnosticState.value = PushTokenDiagnosticState(
                status = "未注册",
                detail = reason,
                retryCount = currentRetryCount,
            )
            return
        }
        if (forceRetry) {
            currentRetryCount = 0
        }
        isRegistering = true
        _diagnosticState.value = PushTokenDiagnosticState(
            status = "读取中",
            detail = "正在向 Firebase 读取本机 FCM token（30秒超时）。",
            retryCount = currentRetryCount,
        )
        Log.e(TAG, "Fetching FCM token (forceRetry=$forceRetry, retryCount=$currentRetryCount)")
        scope.launch {
            try {
                val token = Tasks.await(
                    FirebaseMessaging.getInstance().token,
                    TOKEN_FETCH_TIMEOUT_SEC,
                    TimeUnit.SECONDS,
                )
                Log.e(TAG, "FCM token obtained: prefix=${token.take(12)}")
                registerToken(appContext, token)
            } catch (e: TimeoutException) {
                Log.e(TAG, "FCM token fetch TIMED OUT after ${TOKEN_FETCH_TIMEOUT_SEC}s", e)
                isRegistering = false
                _diagnosticState.value = PushTokenDiagnosticState(
                    status = "超时",
                    detail = "读取 FCM token 超时（${TOKEN_FETCH_TIMEOUT_SEC}秒）。请确认 Google Play 服务正常运行。",
                    retryCount = currentRetryCount,
                )
                scheduleRetry(appContext)
            } catch (e: Exception) {
                if (!isActive) return@launch
                Log.e(TAG, "FCM token fetch FAILED: ${e.javaClass.simpleName} - ${e.message}", e)
                isRegistering = false
                _diagnosticState.value = PushTokenDiagnosticState(
                    status = "无 token",
                    detail = "无法读取 FCM token：${e.localizedMessage ?: e.javaClass.simpleName}。",
                    retryCount = currentRetryCount,
                )
                scheduleRetry(appContext)
            }
        }
    }

    fun registerToken(context: Context, token: String) {
        val appContext = context.applicationContext
        val trimmedToken = token.trim()
        if (trimmedToken.isBlank()) {
            isRegistering = false
            _diagnosticState.value = PushTokenDiagnosticState(
                status = "无 token",
                detail = "Firebase 返回了空 token，这台设备暂时不能接收 FCM 推送。",
                retryCount = currentRetryCount,
            )
            return
        }
        registrationBlockReason(appContext)?.let { reason ->
            isRegistering = false
            _diagnosticState.value = PushTokenDiagnosticState(
                status = "未注册",
                detail = reason,
                retryCount = currentRetryCount,
            )
            return
        }
        // De-dup: skip network call if same token was reported within 5 minutes
        val prefs = appContext.getSharedPreferences(DEDUP_PREFS_NAME, Context.MODE_PRIVATE)
        val lastToken = prefs.getString(DEDUP_KEY_LAST_TOKEN, null)
        val lastReportAt = prefs.getLong(DEDUP_KEY_LAST_REPORT_AT, 0L)
        val now = System.currentTimeMillis()
        if (trimmedToken == lastToken && now - lastReportAt < DEDUP_WINDOW_MS) {
            isRegistering = false
            _diagnosticState.value = PushTokenDiagnosticState(
                status = "已注册",
                detail = "本机 token 已是最新。",
                retryCount = 0,
            )
            Log.e(TAG, "Skip registration: token unchanged and reported ${now - lastReportAt}ms ago")
            return
        }
        val account = AuthSessionManager.getCurrentUserSnapshot()?.account
            ?: AuthSessionManager.getLastSignedInAccount()
            ?: "当前账号"
        _diagnosticState.value = PushTokenDiagnosticState(
            status = "注册中",
            detail = "正在把本机 token 注册到 $account。",
            retryCount = currentRetryCount,
        )
        Log.e(TAG, "Registering token to server for account=$account")

        scope.launch {
            when (
                val result = RepositoryProvider.lifeConsoleRepository.registerPushToken(
                    platform = PLATFORM_ANDROID,
                    token = trimmedToken,
                )
            ) {
                is ApiResult.Success -> {
                    SettingsRepository.refreshPushDiagnosticsFromRemote()
                    currentRetryCount = 0
                    isRegistering = false
                    prefs.edit()
                        .putString(DEDUP_KEY_LAST_TOKEN, trimmedToken)
                        .putLong(DEDUP_KEY_LAST_REPORT_AT, System.currentTimeMillis())
                        .apply()
                    _diagnosticState.value = PushTokenDiagnosticState(
                        status = "已注册",
                        detail = "本机已注册为 $account 的推送设备。token 前缀：${trimmedToken.take(12)}",
                        retryCount = 0,
                    )
                    Log.e(TAG, "Registered Firebase Messaging token successfully.")
                }
                is ApiResult.Error -> {
                    isRegistering = false
                    _diagnosticState.value = PushTokenDiagnosticState(
                        status = "注册失败",
                        detail = "后端拒绝或网络失败：${result.message}",
                        retryCount = currentRetryCount,
                    )
                    Log.e(TAG, "Register push token failed: ${result.message}", result.throwable)
                    scheduleRetry(appContext)
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    private fun scheduleRetry(context: Context) {
        if (currentRetryCount >= MAX_RETRY_COUNT) {
            Log.e(TAG, "Max retry count ($MAX_RETRY_COUNT) reached, stopping retry")
            return
        }
        currentRetryCount++
        val delayMs = BASE_RETRY_DELAY_MS * (1 shl (currentRetryCount - 1))
        Log.e(TAG, "Scheduling retry #$currentRetryCount after ${delayMs}ms")
        _diagnosticState.value = _diagnosticState.value.copy(
            detail = "${_diagnosticState.value.detail}（将在 ${delayMs / 1000} 秒后第 $currentRetryCount 次重试）",
            retryCount = currentRetryCount,
        )
        scope.launch {
            delay(delayMs)
            val currentUser = AuthSessionManager.peekTokens()?.accessToken
            if (currentUser.isNullOrBlank()) {
                Log.e(TAG, "Retry skipped: no valid login state")
                isRegistering = false
                return@launch
            }
            Log.e(TAG, "Executing retry #$currentRetryCount")
            registerCurrentTokenIfPossible(context, forceRetry = false)
        }
    }

    private fun registrationBlockReason(context: Context): String? {
        if (AuthSessionManager.peekTokens()?.accessToken.isNullOrBlank()) {
            return "当前没有有效登录态，请先登录后再注册推送设备。"
        }
        if (!runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)) {
            return "Firebase 未初始化，请确认当前安装包包含 google-services.json。"
        }
        return null
    }
}
