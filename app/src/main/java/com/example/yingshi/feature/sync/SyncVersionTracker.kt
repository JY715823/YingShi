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
}

data class SyncStaleState(
    val photoFeedStale: Boolean = false,
    val albumsStale: Boolean = false,
    val trashStale: Boolean = false,
    val notificationsStale: Boolean = false,
    val lifeConsoleStale: Boolean = false,
) {
    fun isStale(module: SyncModule): Boolean = when (module) {
        SyncModule.PHOTO_FEED -> photoFeedStale
        SyncModule.ALBUMS -> albumsStale
        SyncModule.TRASH -> trashStale
        SyncModule.NOTIFICATIONS -> notificationsStale
        SyncModule.LIFE_CONSOLE -> lifeConsoleStale
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
            requestImmediatePoll()
        }
    }

    fun stopPolling() {
        Log.d(TAG, "stopPolling: cancelling")
        pollingJob?.cancel()
        pollingJob = null
        scope?.cancel()
        scope = null
    }

    fun markRefreshed(module: SyncModule) {
        val remote = _latestRemoteVersions
        if (remote == null) {
            Log.w(TAG, "markRefreshed($module): no remote versions yet, skip")
            return
        }
        Log.d(TAG, "markRefreshed($module): updating local version to remote=${remote.photoFeedVersion}")
        updateLocalVersionFor(module, remote)
        _staleState.update { current ->
            when (module) {
                SyncModule.PHOTO_FEED -> current.copy(photoFeedStale = false)
                SyncModule.ALBUMS -> current.copy(albumsStale = false)
                SyncModule.TRASH -> current.copy(trashStale = false)
                SyncModule.NOTIFICATIONS -> current.copy(notificationsStale = false)
                SyncModule.LIFE_CONSOLE -> current.copy(lifeConsoleStale = false)
            }
        }
    }

    fun markLocalMutation(module: SyncModule) {
        Log.d(TAG, "markLocalMutation($module)")
        synchronized(pendingAutoClearModules) {
            pendingAutoClearModules[module] = System.currentTimeMillis() + LOCAL_MUTATION_ABSORB_MILLIS
        }
        requestImmediatePoll()
    }

    fun reset() {
        Log.d(TAG, "reset: clearing all state")
        localVersions = null
        _latestRemoteVersions = null
        lastFallbackNotificationVersion = 0L
        _staleState.value = SyncStaleState()
    }

    fun requestImmediatePoll() {
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
            }
        } ?: remote
    }

    private suspend fun pollVersions() {
        val remote = fetchRemoteVersions()
        if (remote == null) {
            Log.w(TAG, "pollVersions: fetchRemoteVersions returned null")
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
        val autoClear = synchronized(pendingAutoClearModules) {
            pendingAutoClearModules.entries.removeAll { it.value < now }
            pendingAutoClearModules.keys.toSet()
        }
        if (autoClear.isNotEmpty()) {
            Log.d(TAG, "pollVersions: auto-clearing modules=$autoClear")
        }
        for (module in autoClear) {
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
        )
        val previous = _staleState.value
        val ctx = appContext
        if (newStale.notificationsStale) {
            maybeShowFallbackNotification(remote)
        }
        if (newStale != previous) {
            Log.d(TAG, "pollVersions: stale state changed prev=$previous new=$newStale")
            Log.d(TAG, "pollVersions: remote(photo=${remote.photoFeedVersion} albums=${remote.albumsVersion} trash=${remote.trashVersion} notifications=${remote.effectiveNotificationVersion()} life=${remote.lifeConsoleVersion})")
            Log.d(TAG, "pollVersions: local(photo=${updatedLocal.photoFeedVersion} albums=${updatedLocal.albumsVersion} trash=${updatedLocal.trashVersion} notifications=${updatedLocal.effectiveNotificationVersion()} life=${updatedLocal.lifeConsoleVersion})")
            _staleState.value = newStale
            if (ctx != null && newStale.lifeConsoleStale && !previous.lifeConsoleStale) {
                Log.d(TAG, "pollVersions: lifeConsole became stale, refreshing widgets")
                LifeConsoleWidgetProvider.refreshAll(ctx)
            }
        }
    }

    private suspend fun maybeShowFallbackNotification(remote: SyncVersionsDto): Boolean {
        val ctx = appContext ?: return true
        val remoteNotificationVersion = remote.effectiveNotificationVersion()
        if (remoteNotificationVersion <= lastFallbackNotificationVersion) return true
        Log.d(TAG, "pollVersions: notification version advanced, checking fallback notification")
        if (!appInForeground) {
            delay(BACKGROUND_FALLBACK_GRACE_MS)
        }
        val handled = NotificationFallbackNotifier.maybeShowLatest(ctx, remoteNotificationVersion)
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
            Log.v(TAG, "fetchRemoteVersions: success photo=${dto.photoFeedVersion} albums=${dto.albumsVersion} notifications=${dto.effectiveNotificationVersion()}")
        }.onFailure { e ->
            Log.e(TAG, "fetchRemoteVersions: failed — ${e.javaClass.simpleName}: ${e.message}")
        }.getOrNull()
    }

    private fun nextPollDelayMillis(): Long {
        if (pendingImmediatePoll) return 200L
        return if (appInForeground) FOREGROUND_POLL_INTERVAL_MS else BACKGROUND_POLL_INTERVAL_MS
    }

    private const val FOREGROUND_POLL_INTERVAL_MS = 3000L
    private const val BACKGROUND_POLL_INTERVAL_MS = 30000L
    private const val BACKGROUND_FALLBACK_GRACE_MS = 2500L
    private const val LOCAL_MUTATION_ABSORB_MILLIS = 30_000L
}

private fun SyncVersionsDto.effectiveNotificationVersion(): Long {
    return notificationVersion.takeIf { it > 0L } ?: maxOf(
        photoFeedVersion,
        albumsVersion,
        trashVersion,
        lifeConsoleVersion,
    )
}
