package com.example.yingshi.feature.life.push

import android.content.Context
import android.util.Log
import com.example.yingshi.data.remote.auth.AuthRefreshCoordinator
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.auth.AuthInterceptor
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.connectivity.NetworkConnectivityMonitor
import com.example.yingshi.feature.sync.SyncVersionTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * SSE (Server-Sent Events) 连接管理器。
 *
 * 替代 FCM（在中国大陆被 GFW 封锁）实现实时推送。
 * 通过 OkHttp 长连接读取服务端推送的事件，解析后调用 PushNotificationPresenter.show()。
 *
 * 核心机制：
 * - 长连接：OkHttp + readTimeout(0) 保持 TCP 连接不断开
 * - 心跳：服务端每 30s 发送 :heartbeat 注释行，客户端 90s 未收到则强制重连
 * - 重连：指数退避（3s → 6s → 12s → 24s → 60s 上限），连接成功后重置
 * - 鉴权：Bearer JWT token，401 时通过 AuthRefreshCoordinator 自动刷新
 * - 排除 actor：服务端 sendToPartners() 排除 actor 自己的连接
 */
object SseConnectionManager {
    private const val TAG = "SseConnectionManager"
    private const val HEARTBEAT_TIMEOUT_MS = 90_000L // 90s 无心跳 → 强制重连
    private const val WATCHDOG_INTERVAL_MS = 10_000L // 每 10s 检查一次心跳和登录状态
    private const val RECONNECT_BASE_DELAY_MS = 3_000L
    private const val RECONNECT_MAX_DELAY_MS = 60_000L
    private const val PRECONDITION_RETRY_DELAY_MS = 2_000L // 未登录/无网络时 2s 后重试

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connectionJob: Job? = null

    @Volatile private var currentCall: okhttp3.Call? = null
    @Volatile private var isConnected = false
    @Volatile private var lastHeartbeatAt = 0L
    @Volatile private var backoffMs = RECONNECT_BASE_DELAY_MS
    @Volatile private var appContext: Context? = null
    @Volatile private var lastEventId: String? = null // R3-DIST-003: Track last SSE event ID for replay

    private val sseClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // SSE 长连接：不设读超时
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS) // 不设 call 超时
            .retryOnConnectionFailure(true)
            .addInterceptor(AuthInterceptor(AuthSessionManager))
            .authenticator(AuthRefreshCoordinator.createAuthenticator())
            .build()
    }

    fun start(context: Context) {
        if (connectionJob?.isActive == true) {
            Log.d(TAG, "start: already running, skip")
            return
        }
        appContext = context.applicationContext
        Log.i(TAG, "start: launching SSE connection loop")
        connectionJob = scope.launch {
            while (isActive) {
                try {
                    if (!canConnect()) {
                        delay(PRECONDITION_RETRY_DELAY_MS)
                        continue
                    }
                    connectAndRead()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "SSE connection loop error: ${e.javaClass.simpleName}: ${e.message}")
                }
                isConnected = false
                if (!isActive) break
                Log.d(TAG, "SSE disconnected, reconnecting in ${backoffMs}ms")
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(RECONNECT_MAX_DELAY_MS)
            }
        }
    }

    fun stop() {
        Log.i(TAG, "stop: cancelling SSE connection")
        connectionJob?.cancel()
        connectionJob = null
        currentCall?.cancel()
        currentCall = null
        isConnected = false
        lastEventId = null // Prevent cross-user event replay on account switch
    }

    /**
     * 返回 SSE 连接是否活跃。
     * 供 SseConnectionManager 的 watchdog 周期性检查，断开时触发重连。
     */
    fun isConnected(): Boolean = isConnected && connectionJob?.isActive == true

    private fun canConnect(): Boolean {
        if (!NetworkConnectivityMonitor.currentState.isConnected) return false
        if (!AuthSessionManager.isLoggedIn) return false
        if (AuthSessionManager.peekAccessToken().isNullOrBlank()) return false
        return true
    }

    private suspend fun connectAndRead() {
        val ctx = appContext ?: return
        val baseUrl = BackendDebugConfig.currentBaseUrl()
        val sseUrl = baseUrl + "api/sse/subscribe"
        val token = AuthSessionManager.peekAccessToken() ?: run {
            Log.w(TAG, "connectAndRead: no access token, skipping")
            return
        }

        val requestBuilder = Request.Builder()
            .url(sseUrl)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .header("Connection", "keep-alive")

        // R3-DIST-003: Carry Last-Event-ID for server-side replay of missed events
        lastEventId?.let { id ->
            requestBuilder.header("Last-Event-ID", id)
        }

        val request = requestBuilder.build()

        val call = sseClient.newCall(request)
        currentCall = call
        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "SSE connect failed: HTTP ${response.code} ${response.message}")
                response.close()
                return
            }
            val body = response.body ?: run {
                Log.w(TAG, "SSE connect: response body is null")
                response.close()
                return
            }
            Log.i(TAG, "SSE connected: HTTP ${response.code}")
            isConnected = true
            backoffMs = RECONNECT_BASE_DELAY_MS // 连接成功，重置退避
            lastHeartbeatAt = System.currentTimeMillis()

            // 看门狗协程：检查心跳超时和登录状态
            val watchdogJob = scope.launch {
                while (isActive && isConnected) {
                    delay(WATCHDOG_INTERVAL_MS)
                    if (!AuthSessionManager.isLoggedIn) {
                        Log.i(TAG, "Watchdog: user logged out, closing SSE connection")
                        currentCall?.cancel()
                        break
                    }
                    if (System.currentTimeMillis() - lastHeartbeatAt > HEARTBEAT_TIMEOUT_MS) {
                        Log.w(TAG, "Watchdog: heartbeat timeout (>${HEARTBEAT_TIMEOUT_MS}ms), forcing reconnect")
                        currentCall?.cancel()
                        break
                    }
                }
            }

            try {
                val source = body.source()
                var currentEvent = ""
                var currentData = StringBuilder()

                while (coroutineContext.isActive) {
                    val line = try {
                        source.readUtf8Line() ?: break
                    } catch (e: Exception) {
                        Log.w(TAG, "SSE read error: ${e.javaClass.simpleName}: ${e.message}")
                        break
                    }
                    when {
                        line.startsWith(":") -> {
                            // 心跳注释行
                            lastHeartbeatAt = System.currentTimeMillis()
                        }
                        line.startsWith("id:") -> {
                            // R3-DIST-003: Capture event ID for Last-Event-ID replay
                            lastEventId = line.removePrefix("id:").trim()
                        }
                        line.startsWith("event:") -> {
                            currentEvent = line.removePrefix("event:").trim()
                        }
                        line.startsWith("data:") -> {
                            currentData.append(line.removePrefix("data:").trim())
                        }
                        line.isEmpty() -> {
                            // 空行 = 事件结束，分发事件
                            if (currentData.isNotEmpty()) {
                                dispatchEvent(ctx, currentEvent, currentData.toString())
                            }
                            currentEvent = ""
                            currentData = StringBuilder()
                        }
                    }
                }
            } finally {
                watchdogJob.cancel()
            }
        } finally {
            currentCall = null
            isConnected = false
        }
    }

    private fun dispatchEvent(context: Context, event: String, data: String) {
        lastHeartbeatAt = System.currentTimeMillis()
        Log.d(TAG, ">>> SSE event received: event=$event dataLength=${data.length}")
        if (event == "hello") {
            Log.i(TAG, "SSE hello received, connection established")
            return
        }
        val map = parseSseData(data) ?: return
        // 通过 PushNotificationPresenter 显示通知
        val shown = PushNotificationPresenter.show(context, map, source = "sse")
        Log.d(TAG, ">>> SSE notification result: shown=$shown event=$event")
        // 按模块区分后续处理：
        // - life 事件：SSE 已直接显示通知，只需刷新 widget，不触发轮询
        //   （避免轮询更新 stale 状态导致照片流被误刷新）
        // - photo 事件：触发轮询以更新 stale 状态，让照片流刷新
        val module = map["module"].orEmpty()
        if (module == "life") {
            com.example.yingshi.feature.life.widget.LifeConsoleWidgetProvider.refreshAll(context)
        } else {
            SyncVersionTracker.requestImmediatePoll()
        }
    }

    private fun parseSseData(data: String): Map<String, String>? {
        return try {
            val json = JSONObject(data)
            val map = mutableMapOf<String, String>()
            json.keys().forEach { key ->
                json.optString(key).let { value ->
                    if (value.isNotEmpty()) map[key] = value
                }
            }
            map
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse SSE data as JSON: ${e.message}, raw=$data")
            null
        }
    }
}
