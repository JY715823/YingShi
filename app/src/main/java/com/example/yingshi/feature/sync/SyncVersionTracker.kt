package com.example.yingshi.feature.sync

import android.content.Context
import android.util.Log
import com.example.yingshi.data.remote.api.SyncVersionsDto
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.RemoteServiceFactory
import com.example.yingshi.data.remote.connectivity.NetworkConnectivityMonitor
import com.example.yingshi.feature.life.push.NotificationFallbackNotifier
import com.example.yingshi.feature.life.widget.LifeConsoleWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SyncModule {
    PHOTO_FEED,
    ALBUMS,
    TRASH,
    NOTIFICATIONS,
    LIFE_CONSOLE,
    SYSTEM_MEDIA,
}

data class SyncStaleState(
    val photoFeedStale: Boolean = false,
    val albumsStale: Boolean = false,
    val trashStale: Boolean = false,
    val notificationsStale: Boolean = false,
    val lifeConsoleStale: Boolean = false,
    val systemMediaStale: Boolean = false,
) {
    fun isStale(module: SyncModule): Boolean = when (module) {
        SyncModule.PHOTO_FEED -> photoFeedStale
        SyncModule.ALBUMS -> albumsStale
        SyncModule.TRASH -> trashStale
        SyncModule.NOTIFICATIONS -> notificationsStale
        SyncModule.LIFE_CONSOLE -> lifeConsoleStale
        SyncModule.SYSTEM_MEDIA -> systemMediaStale
    }
}

object SyncVersionTracker {
    private const val TAG = "SyncVersionTracker"

    private val _staleState = MutableStateFlow(SyncStaleState())
    val staleState: StateFlow<SyncStaleState> = _staleState.asStateFlow()

    @Volatile
    private var localVersions: SyncVersionsDto? = null

    @Volatile
    private var _latestRemoteVersions: SyncVersionsDto? = null
    @Volatile
    private var lastFallbackNotificationVersion: Long = 0L

    private var scope: CoroutineScope? = null
    private var pollingJob: Job? = null
    private val pollMutex = Mutex()
    @Volatile
    private var pendingImmediatePoll = false
    @Volatile
    private var lastLocalMutationFcmSuppressAt: Long = 0L
    @Volatile
    private var appInForeground = true
    private var appContext: Context? = null

    private val pendingAutoClearModules = mutableMapOf<SyncModule, Long>()

    fun init(context: Context) {
        appContext = context.applicationContext
        Log.d(TAG, "init: context set")
    }

    fun startPolling() {
        if (scope != null) {
            Log.d(TAG, "startPolling: already running, skip")
            return
        }
        Log.d(TAG, "startPolling: creating scope and launching poll loop")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        pollingJob = scope?.launch {
            Log.d(TAG, "poll loop: started")
            var cycle = 0
            while (true) {
                cycle++
                val connected = NetworkConnectivityMonitor.currentState.isConnected
                val loggedIn = AuthSessionManager.isLoggedIn
                if (cycle <= 2 || cycle % 10 == 0) {
                    Log.d(TAG, "poll loop cycle=$cycle connected=$connected loggedIn=$loggedIn")
                }
                if (connected && loggedIn) {
                    pollMutex.withLock {
                        pollVersions()
                    }
                }
                delay(nextPollDelayMillis())
                pendingImmediatePoll = false
            }
        }
    }

    fun setAppInForeground(inForeground: Boolean) {
        appInForeground = inForeground
        if (inForeground) {
            // 根因 B 修复: 若 absorb 窗口内有 life 操作, 先让 absorb 优先发挥作用,
            // 避免立即 poll 时 notificationsStale=true (因 NOTIFICATIONS local 版本尚未同步)
            // 引发通知中心反复刷新和照片流页面的 Composable 重组开销
            val hasAbsorbing = synchronized(pendingAutoClearModules) {
                pendingAutoClearModules.values.any { it > System.currentTimeMillis() }
            }
            if (hasAbsorbing) {
                Log.d(TAG, "setAppInForeground(true): absorb window active, deferring poll")
                // 不立即 poll, 下次常规 poll 会自动处理 (前台 3s 间隔)
            } else {
                requestImmediatePoll()
            }
        }
    }

    fun stopPolling() {
        Log.d(TAG, "stopPolling: cancelling")
        pollingJob?.cancel()
        pollingJob = null
        scope?.cancel()
        scope = null
    }

    /**
     * 标记某模块已刷新, 同步 local 版本到上次 poll 的快照。
     *
     * 用于非协程上下文 (如 StaleBanner 的 onRefresh 回调)。
     * 协程上下文请优先使用 [markRefreshedFresh], 后者会先拉取最新服务端版本避免陈旧快照导致 stale 循环。
     */
    fun markRefreshed(module: SyncModule) {
        val remote = _latestRemoteVersions
        if (remote == null) {
            Log.w(TAG, "markRefreshed($module): no remote versions yet, skip")
            return
        }
        Log.d(TAG, "markRefreshed($module): updating local version to snapshot")
        updateLocalVersionFor(module, remote)
        _staleState.update { current ->
            when (module) {
                SyncModule.PHOTO_FEED -> current.copy(photoFeedStale = false)
                SyncModule.ALBUMS -> current.copy(albumsStale = false)
                SyncModule.TRASH -> current.copy(trashStale = false)
                SyncModule.NOTIFICATIONS -> current.copy(notificationsStale = false)
                SyncModule.LIFE_CONSOLE -> current.copy(lifeConsoleStale = false)
                SyncModule.SYSTEM_MEDIA -> current.copy(systemMediaStale = false)
            }
        }
    }

    /**
     * 标记某模块已刷新, 同步 local 版本到最新服务端版本。
     *
     * 根因 C 修复: 先拉取最新服务端版本 (而不是用 _latestRemoteVersions 陈旧快照),
     * 再同步 local。避免 refresh 期间服务端版本又涨导致下次 poll 又 stale=true 的循环。
     *
     * 调用方必须在协程内调用。
     */
    suspend fun markRefreshedFresh(module: SyncModule) {
        val freshRemote = fetchRemoteVersions()
        if (freshRemote != null) {
            _latestRemoteVersions = freshRemote
            Log.d(TAG, "markRefreshedFresh($module): fetched fresh remote, syncing local to latest")
            updateLocalVersionFor(module, freshRemote)
        } else {
            val remote = _latestRemoteVersions
            if (remote == null) {
                Log.w(TAG, "markRefreshedFresh($module): no remote versions yet, skip")
                return
            }
            Log.d(TAG, "markRefreshedFresh($module): fetch failed, using stale snapshot")
            updateLocalVersionFor(module, remote)
        }
        _staleState.update { current ->
            when (module) {
                SyncModule.PHOTO_FEED -> current.copy(photoFeedStale = false)
                SyncModule.ALBUMS -> current.copy(albumsStale = false)
                SyncModule.TRASH -> current.copy(trashStale = false)
                SyncModule.NOTIFICATIONS -> current.copy(notificationsStale = false)
                SyncModule.LIFE_CONSOLE -> current.copy(lifeConsoleStale = false)
                SyncModule.SYSTEM_MEDIA -> current.copy(systemMediaStale = false)
            }
        }
    }

    fun markLocalMutation(module: SyncModule) {
        Log.e(TAG, "markLocalMutation($module) appInForeground=$appInForeground")
        if (module == SyncModule.SYSTEM_MEDIA) {
            // SYSTEM_MEDIA has no remote version; set stale directly so the SystemMediaViewModel refreshes.
            _staleState.update { it.copy(systemMediaStale = true) }
            return
        }
        synchronized(pendingAutoClearModules) {
            pendingAutoClearModules[module] = System.currentTimeMillis() + LOCAL_MUTATION_ABSORB_MILLIS
            // P1-3 根因修复: notificationVersion 不再包含 lifeConsoleVersion,
            // life 操作不会让 notificationVersion 涨, 无需 absorb NOTIFICATIONS。
        }
        lastLocalMutationFcmSuppressAt = System.currentTimeMillis()
        // LIFE_CONSOLE 本地操作不触发立即轮询：
        // 1. life 操作是本地操作，UI 已直接更新，不需要轮询来刷新
        // 2. life 操作的 SSE/FCM 推送只发给对方设备，不发给操作者自己
        // 3. 之前 BUG: markLocalMutation(LIFE_CONSOLE) → requestImmediatePoll → pollVersions
        //    会计算 photoFeedStale，若服务端 photoFeedVersion 有任何变化（即便已排除 life domain），
        //    仍可能误判 stale，导致照片流被 life 操作触发刷新
        // 4. pendingAutoClearModules 吸收窗口会在下次常规轮询时生效（前台 3s，后台 30s）
        if (module == SyncModule.LIFE_CONSOLE) {
            Log.e(TAG, "markLocalMutation(LIFE_CONSOLE): skip requestImmediatePoll to avoid photo feed refresh")
            return
        }
        requestImmediatePoll()
    }

    /**
     * Returns true if a local mutation happened within the FCM suppression window.
     * Used to suppress self-notifications: when the user uploads/deletes locally,
     * the server may send an FCM back to the same user — this prevents showing it.
     */
    fun shouldSuppressLocalMutationFcm(): Boolean {
        val ts = lastLocalMutationFcmSuppressAt
        if (ts <= 0L) return false
        return (System.currentTimeMillis() - ts) < LOCAL_MUTATION_SUPPRESS_FCM_MILLIS
    }

    fun reset() {
        Log.d(TAG, "reset: clearing all state")
        localVersions = null
        _latestRemoteVersions = null
        lastFallbackNotificationVersion = 0L
        _staleState.value = SyncStaleState()
    }

    fun requestImmediatePoll() {
        // P1-3 诊断日志: 简化日志, 不再打印完整堆栈 (堆栈日志太吵, 淹没关键日志)
        Log.e(TAG, "requestImmediatePoll: requested")
        pendingImmediatePoll = true
        if (scope == null) {
            startPolling()
        }
        val activeScope = scope ?: return
        activeScope.launch {
            if (NetworkConnectivityMonitor.currentState.isConnected && AuthSessionManager.isLoggedIn) {
                pollMutex.withLock {
                    pollVersions()
                }
            }
        }
    }

    private fun updateLocalVersionFor(module: SyncModule, remote: SyncVersionsDto) {
        localVersions = localVersions?.let { local ->
            when (module) {
                SyncModule.PHOTO_FEED -> local.copy(photoFeedVersion = remote.photoFeedVersion)
                SyncModule.ALBUMS -> local.copy(albumsVersion = remote.albumsVersion)
                SyncModule.TRASH -> local.copy(trashVersion = remote.trashVersion)
                SyncModule.NOTIFICATIONS -> local.copy(notificationVersion = remote.effectiveNotificationVersion())
                SyncModule.LIFE_CONSOLE -> local.copy(lifeConsoleVersion = remote.lifeConsoleVersion)
                SyncModule.SYSTEM_MEDIA -> local // no remote version; stale flag is managed directly
            }
        } ?: remote
    }

    private suspend fun pollVersions() {
        val remote = fetchRemoteVersions()
        if (remote == null) {
            Log.e(TAG, "pollVersions: fetchRemoteVersions returned null")
            return
        }
        _latestRemoteVersions = remote
        val local = localVersions
        if (local == null) {
            Log.d(TAG, "pollVersions: first poll, initializing local=${remote.photoFeedVersion}")
            val notificationHandled = maybeShowFallbackNotification(remote)
            localVersions = if (notificationHandled) {
                remote
            } else {
                remote.copy(notificationVersion = pendingNotificationVersion(remote))
            }
            return
        }

        val now = System.currentTimeMillis()
        // absorb 窗口修复：
        // 之前的 bug：removeAll { it.value < now } 只移除已过期的 entry，
        // 正在生效（30s 内）的 entry 不会被加入 autoClear，
        // 导致 absorb 期内 updateLocalVersionFor 不执行，
        // 服务端版本回声（本地操作→服务端版本涨→poll 检测到 remote>local）触发 stale=true，
        // 造成照片流/相册页被本地操作"回声"刷新。
        // 修复：absorb 期内的模块也同步 local 到 remote，避免回声触发 stale。
        val absorbActiveModules = synchronized(pendingAutoClearModules) {
            val active = pendingAutoClearModules.keys.toSet()
            // 移除已过期的
            pendingAutoClearModules.entries.removeAll { it.value < now }
            active
        }
        if (absorbActiveModules.isNotEmpty()) {
            Log.d(TAG, "pollVersions: absorbing modules=$absorbActiveModules (syncing local to remote to avoid echo stale)")
        }
        for (module in absorbActiveModules) {
            updateLocalVersionFor(module, remote)
        }

        val updatedLocal = localVersions
        if (updatedLocal == null) {
            Log.w(TAG, "pollVersions: updatedLocal is null after autoClear, skip")
            return
        }
        val newStale = SyncStaleState(
            photoFeedStale = remote.photoFeedVersion > updatedLocal.photoFeedVersion,
            albumsStale = remote.albumsVersion > updatedLocal.albumsVersion,
            trashStale = remote.trashVersion > updatedLocal.trashVersion,
            notificationsStale = remote.effectiveNotificationVersion() > updatedLocal.effectiveNotificationVersion(),
            lifeConsoleStale = remote.lifeConsoleVersion > updatedLocal.lifeConsoleVersion,
            systemMediaStale = _staleState.value.systemMediaStale,
        )
        val previous = _staleState.value
        val ctx = appContext
        if (newStale.notificationsStale) {
            maybeShowFallbackNotification(remote)
        }
        if (newStale != previous) {
            Log.e(TAG, "pollVersions: stale state changed prev=$previous new=$newStale")
            Log.e(TAG, "pollVersions: remote(photo=${remote.photoFeedVersion} albums=${remote.albumsVersion} trash=${remote.trashVersion} notifications=${remote.effectiveNotificationVersion()} life=${remote.lifeConsoleVersion})")
            Log.e(TAG, "pollVersions: local(photo=${updatedLocal.photoFeedVersion} albums=${updatedLocal.albumsVersion} trash=${updatedLocal.trashVersion} notifications=${updatedLocal.effectiveNotificationVersion()} life=${updatedLocal.lifeConsoleVersion})")
            _staleState.value = newStale
            if (ctx != null && newStale.lifeConsoleStale && !previous.lifeConsoleStale) {
                Log.e(TAG, "pollVersions: lifeConsole became stale, refreshing widgets")
                LifeConsoleWidgetProvider.refreshAll(ctx)
            }
        }
    }

    private suspend fun maybeShowFallbackNotification(remote: SyncVersionsDto): Boolean {
        val ctx = appContext ?: return true
        val remoteNotificationVersion = remote.effectiveNotificationVersion()
        if (remoteNotificationVersion <= lastFallbackNotificationVersion) return true
        Log.e(TAG, "pollVersions: notification version advanced, checking fallback notification")
        if (!appInForeground) {
            delay(BACKGROUND_FALLBACK_GRACE_MS)
        }
        Log.e(TAG, "pollVersions: calling NotificationFallbackNotifier.maybeShowLatest")
        val handled = NotificationFallbackNotifier.maybeShowLatest(ctx, remoteNotificationVersion)
        Log.e(TAG, "pollVersions: fallback result handled=$handled")
        if (handled) {
            lastFallbackNotificationVersion = remoteNotificationVersion
        }
        return handled
    }

    private fun pendingNotificationVersion(remote: SyncVersionsDto): Long {
        val remoteNotificationVersion = remote.effectiveNotificationVersion()
        return if (remoteNotificationVersion > 1L) {
            remoteNotificationVersion - 1L
        } else {
            0L
        }
    }

    private suspend fun fetchRemoteVersions(): SyncVersionsDto? {
        return runCatching {
            RemoteServiceFactory.syncApi.getVersions().data
        }.onSuccess { dto ->
            Log.e(TAG, "fetchRemoteVersions: success photo=${dto.photoFeedVersion} albums=${dto.albumsVersion} notifications=${dto.effectiveNotificationVersion()} life=${dto.lifeConsoleVersion}")
        }.onFailure { e ->
            Log.e(TAG, "fetchRemoteVersions: failed — ${e.javaClass.simpleName}: ${e.message}")
        }.getOrNull()
    }

    private fun nextPollDelayMillis(): Long {
        if (pendingImmediatePoll) return 200L
        return if (appInForeground) FOREGROUND_POLL_INTERVAL_MS else BACKGROUND_POLL_INTERVAL_MS
    }

    // P1-3 根因修复: 前台 poll 间隔从 8s 调整为 15s。
    // 8s 仍然太频繁, 每次 poll 都是网络请求, 且 photos SSE 事件触发的 requestImmediatePoll
    // 会在 200ms 后执行 pollVersions, 频繁的 poll 增加 staleState 重建几率。
    // 15s 在实时性和性能之间取得平衡, SSE 推送仍能保证关键事件的即时性。
    private const val FOREGROUND_POLL_INTERVAL_MS = 15000L
    private const val BACKGROUND_POLL_INTERVAL_MS = 30000L
    private const val BACKGROUND_FALLBACK_GRACE_MS = 2500L
    private const val LOCAL_MUTATION_ABSORB_MILLIS = 30_000L
    private const val LOCAL_MUTATION_SUPPRESS_FCM_MILLIS = 60_000L
}

private fun SyncVersionsDto.effectiveNotificationVersion(): Long {
    // P1-3 根因修复: 不再把 lifeConsoleVersion 纳入 fallback 计算。
    // 服务端 notificationVersion 已不再包含 lifeConsoleVersion, 客户端 fallback 也需对齐。
    // life 通知完全由 SSE 推送负责, 不依赖 notificationVersion 触发轮询回退,
    // 避免 life 操作触发 notificationsStale=true → 通知重复推送 + 照片流被间接刷新。
    return notificationVersion.takeIf { it > 0L } ?: maxOf(
        photoFeedVersion,
        albumsVersion,
        trashVersion,
    )
}
