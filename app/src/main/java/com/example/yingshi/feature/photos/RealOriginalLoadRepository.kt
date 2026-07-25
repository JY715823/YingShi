package com.example.yingshi.feature.photos

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import coil.imageLoader
import coil.memory.MemoryCache
import com.example.yingshi.data.remote.connectivity.NetworkConnectivityMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import coil.request.SuccessResult
import java.util.Collections

internal data class RealOriginalMediaTarget(
    val mediaId: String,
    val mediaType: AppMediaType,
    val mediaSource: AppContentMediaSource?,
)

internal data class RealOriginalLoadAllResult(
    val successCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
)

private data class RealOriginalRequestKey(
    val mediaId: String,
    val originalUrl: String,
    val originalCacheKey: String?,
)

private data class PendingOriginalRequest(
    val context: Context,
    val target: RealOriginalMediaTarget,
    val accessToken: String?,
)

internal fun PhotoFeedItem.toRealOriginalMediaTarget(): RealOriginalMediaTarget {
    return RealOriginalMediaTarget(
        mediaId = mediaId,
        mediaType = mediaType,
        mediaSource = mediaSource,
    )
}

internal fun PostDetailMediaUiModel.toRealOriginalMediaTarget(): RealOriginalMediaTarget {
    return RealOriginalMediaTarget(
        mediaId = id,
        mediaType = mediaType,
        mediaSource = mediaSource,
    )
}

internal object RealOriginalLoadRepository {
    private val scope = CoroutineScope(SupervisorJob())
    private val statesByRequestKey = mutableStateMapOf<RealOriginalRequestKey, OriginalLoadState>()
    private val activeJobs = Collections.synchronizedMap(mutableMapOf<RealOriginalRequestKey, Job>())
    private val pendingRequests = Collections.synchronizedMap(mutableMapOf<RealOriginalRequestKey, PendingOriginalRequest>())
    private val requestVersions = Collections.synchronizedMap(mutableMapOf<RealOriginalRequestKey, Int>())

    init {
        scope.launch {
            NetworkConnectivityMonitor.state.collectLatest { state ->
                if (state.isConnected) {
                    retryPendingRequests()
                } else {
                    pauseActiveRequests()
                }
            }
        }
    }

    fun getState(target: RealOriginalMediaTarget): OriginalLoadState {
        val key = target.requestKey() ?: return OriginalLoadState.NotLoaded
        return statesByRequestKey[key] ?: OriginalLoadState.NotLoaded
    }

    fun setState(target: RealOriginalMediaTarget, state: OriginalLoadState) {
        val key = target.requestKey()
        if (key == null) {
            clearOriginal(target.mediaId)
            return
        }
        if (state == OriginalLoadState.NotLoaded) {
            statesByRequestKey.remove(key)
            pendingRequests.remove(key)
            requestVersions.remove(key)
            return
        }
        statesByRequestKey[key] = state
        if (state == OriginalLoadState.Loaded) {
            pendingRequests.remove(key)
            // R3-APP-001: Original cached state tracked by RealOriginalLoadRepository itself
        }
    }

    fun requestOriginal(
        context: Context,
        target: RealOriginalMediaTarget,
        accessToken: String?,
    ): Boolean {
        val key = target.requestKey()
        if (key == null) {
            clearOriginal(target.mediaId)
            return false
        }
        val currentState = getState(target)
        if (currentState == OriginalLoadState.Loaded) {
            return true
        }
        pendingRequests[key] = PendingOriginalRequest(
            context = context.applicationContext,
            target = target,
            accessToken = accessToken,
        )
        statesByRequestKey[key] = OriginalLoadState.Loading
        if (NetworkConnectivityMonitor.currentState.isConnected) {
            launchOriginalRequest(
                key = key,
                context = context.applicationContext,
                target = target,
                accessToken = accessToken,
            )
        }
        return true
    }

    fun clearOriginal(mediaId: String) {
        synchronized(activeJobs) {
            activeJobs.entries.removeAll { (key, job) ->
                if (key.mediaId == mediaId) {
                    job.cancel()
                    true
                } else {
                    false
                }
            }
        }
        statesByRequestKey.keys
            .filter { it.mediaId == mediaId }
            .forEach { key ->
                statesByRequestKey.remove(key)
                pendingRequests.remove(key)
                requestVersions.remove(key)
            }
    }

    fun clearAllOriginals() {
        synchronized(activeJobs) {
            activeJobs.values.forEach { it.cancel() }
            activeJobs.clear()
        }
        statesByRequestKey.clear()
        pendingRequests.clear()
        requestVersions.clear()
    }

    fun clearCachedOriginalFiles(context: Context): Boolean {
        val keys = statesByRequestKey.keys.toList()
        var ok = true
        keys.forEach { key ->
            val diskKey = key.originalCacheKey ?: sharedOriginalDiskCacheKey(key.originalUrl)
            val memoryKey = key.originalCacheKey ?: sharedOriginalMemoryCacheKey(key.originalUrl)
            ok = runCatching {
                context.imageLoader.diskCache?.remove(diskKey)
                context.imageLoader.memoryCache?.remove(MemoryCache.Key(memoryKey))
                true
            }.getOrDefault(false) && ok
        }
        clearAllOriginals()
        return ok
    }

    fun getPostSummaryForTargets(targets: List<RealOriginalMediaTarget>): PostOriginalLoadSummary {
        val loadableTargets = targets
            .filter { it.requestKey() != null }
            .distinctBy { it.mediaId }
        val states = loadableTargets.map { getState(it) }
        return PostOriginalLoadSummary(
            totalCount = loadableTargets.size,
            loadedCount = states.count { it == OriginalLoadState.Loaded },
            loadingCount = states.count { it == OriginalLoadState.Loading },
            failedCount = states.count { it == OriginalLoadState.Failed },
        )
    }

    suspend fun loadOriginal(
        context: Context,
        target: RealOriginalMediaTarget,
        accessToken: String?,
    ): OriginalLoadState {
        if (target.mediaType != AppMediaType.IMAGE) {
            clearOriginal(target.mediaId)
            return OriginalLoadState.NotLoaded
        }
        val key = target.requestKey()
        if (key == null) {
            withContext(Dispatchers.Main.immediate) {
                setState(target, OriginalLoadState.NotLoaded)
            }
            return OriginalLoadState.NotLoaded
        }

        withContext(Dispatchers.Main.immediate) {
            setState(target, OriginalLoadState.Loading)
        }
        val nextState = performOriginalLoad(
            context = context,
            target = target,
            accessToken = accessToken,
        )
        withContext(Dispatchers.Main.immediate) {
            setState(target, nextState)
        }
        Log.d("RealOriginalLoadRepo", "finish original load url=${key.originalUrl} state=$nextState")
        return nextState
    }

    suspend fun loadAllOriginals(
        context: Context,
        targets: List<RealOriginalMediaTarget>,
        accessToken: String?,
    ): RealOriginalLoadAllResult {
        var successCount = 0
        var skippedCount = 0
        var failedCount = 0
        targets.distinctBy { it.mediaId }.forEach { target ->
            if (target.mediaType != AppMediaType.IMAGE || target.requestKey() == null) {
                setState(target, OriginalLoadState.NotLoaded)
                skippedCount += 1
                return@forEach
            }
            val result = if (getState(target) == OriginalLoadState.Loaded) {
                OriginalLoadState.Loaded
            } else {
                loadOriginal(context, target, accessToken)
            }
            when (result) {
                OriginalLoadState.Loaded -> successCount += 1
                OriginalLoadState.Failed -> failedCount += 1
                else -> skippedCount += 1
            }
        }
        return RealOriginalLoadAllResult(
            successCount = successCount,
            skippedCount = skippedCount,
            failedCount = failedCount,
        )
    }

    private suspend fun performOriginalLoad(
        context: Context,
        target: RealOriginalMediaTarget,
        accessToken: String?,
    ): OriginalLoadState {
        val key = target.requestKey() ?: return OriginalLoadState.NotLoaded
        Log.d("RealOriginalLoadRepo", "perform original load url=${key.originalUrl}")
        return try {
            val loadSucceeded = withTimeout(15_000L) {
                loadOriginalImage(
                    context = context,
                    url = key.originalUrl,
                    cacheKey = key.originalCacheKey,
                    accessToken = accessToken,
                )
            }
            if (loadSucceeded) {
                OriginalLoadState.Loaded
            } else {
                OriginalLoadState.Failed
            }
        } catch (throwable: Throwable) {
            Log.e(
                "RealOriginalLoadRepo",
                "original load timed out or crashed url=${key.originalUrl}",
                throwable,
            )
            OriginalLoadState.Failed
        }
    }

    private suspend fun loadOriginalImage(
        context: Context,
        url: String,
        cacheKey: String?,
        accessToken: String?,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            Log.d("RealOriginalLoadRepo", "build original request url=$url")
            val request = backendMediaOriginalImageRequest(
                context = context,
                url = url,
                accessToken = accessToken,
                memoryCacheKey = cacheKey ?: sharedOriginalMemoryCacheKey(url),
                diskCacheKey = cacheKey,
            ) ?: return@withContext false
            Log.d("RealOriginalLoadRepo", "execute original request url=$url")
            when (val result = context.imageLoader.execute(request)) {
                is SuccessResult -> true
                else -> {
                    Log.w(
                        "RealOriginalLoadRepo",
                        "original load failed url=$url result=${result::class.java.simpleName}",
                        (result as? coil.request.ErrorResult)?.throwable,
                    )
                    false
                }
            }
        }
    }

    private fun launchOriginalRequest(
        key: RealOriginalRequestKey,
        context: Context,
        target: RealOriginalMediaTarget,
        accessToken: String?,
    ) {
        val requestVersion = nextRequestVersion(key)
        val launchConnectivityVersion = NetworkConnectivityMonitor.currentState.changeVersion
        synchronized(activeJobs) {
            activeJobs[key]?.cancel()
            activeJobs[key] = scope.launch(Dispatchers.IO) {
                var shouldRemovePending = false
                var retryAfterCompletion = false
                try {
                    Log.d("RealOriginalLoadRepo", "start original load url=${key.originalUrl}")
                    val nextState = try {
                        Log.d("RealOriginalLoadRepo", "begin original request body url=${key.originalUrl}")
                        performOriginalLoad(
                            context = context,
                            target = target,
                            accessToken = accessToken,
                        )
                    } catch (throwable: Throwable) {
                        Log.e(
                            "RealOriginalLoadRepo",
                            "original load crashed url=${key.originalUrl}",
                            throwable,
                        )
                        OriginalLoadState.Failed
                    }
                    withContext(Dispatchers.Main.immediate) {
                        if (requestVersions[key] != requestVersion) {
                            return@withContext
                        }
                        val connectivityState = NetworkConnectivityMonitor.currentState
                        val connectivityChangedDuringRequest =
                            connectivityState.changeVersion != launchConnectivityVersion
                        if (
                            nextState == OriginalLoadState.Failed &&
                            (
                                !connectivityState.isConnected ||
                                    connectivityChangedDuringRequest
                                )
                        ) {
                            statesByRequestKey[key] = OriginalLoadState.Loading
                            retryAfterCompletion = connectivityState.isConnected
                            return@withContext
                        }
                        shouldRemovePending = nextState != OriginalLoadState.Loading
                        setState(target, nextState)
                    }
                    Log.d("RealOriginalLoadRepo", "finish original load url=${key.originalUrl} state=$nextState")
                } catch (throwable: Throwable) {
                    Log.e(
                        "RealOriginalLoadRepo",
                        "original load crashed url=${key.originalUrl}",
                        throwable,
                    )
                    withContext(Dispatchers.Main.immediate) {
                        if (requestVersions[key] != requestVersion) {
                            return@withContext
                        }
                        val connectivityState = NetworkConnectivityMonitor.currentState
                        val connectivityChangedDuringRequest =
                            connectivityState.changeVersion != launchConnectivityVersion
                        if (
                            !connectivityState.isConnected ||
                            connectivityChangedDuringRequest
                        ) {
                            statesByRequestKey[key] = OriginalLoadState.Loading
                            retryAfterCompletion = connectivityState.isConnected
                            return@withContext
                        }
                        shouldRemovePending = true
                        setState(target, OriginalLoadState.Failed)
                    }
                } finally {
                    synchronized(activeJobs) {
                        activeJobs.remove(key)
                    }
                    if (shouldRemovePending) {
                        pendingRequests.remove(key)
                    }
                    if (retryAfterCompletion && pendingRequests.containsKey(key)) {
                        launchOriginalRequest(
                            key = key,
                            context = context,
                            target = target,
                            accessToken = accessToken,
                        )
                    }
                }
            }
        }
    }

    private suspend fun pauseActiveRequests() {
        val jobsToCancel = synchronized(activeJobs) {
            activeJobs
                .filterKeys { key -> statesByRequestKey[key] == OriginalLoadState.Loading }
                .map { (key, job) ->
                    nextRequestVersion(key)
                    key to job
                }
        }
        jobsToCancel.forEach { (_, job) ->
            runCatching { job.cancelAndJoin() }
        }
        synchronized(activeJobs) {
            jobsToCancel.forEach { (key, _) ->
                activeJobs.remove(key)
                if (pendingRequests.containsKey(key)) {
                    statesByRequestKey[key] = OriginalLoadState.Loading
                }
            }
        }
    }

    private fun retryPendingRequests() {
        val pending = synchronized(pendingRequests) {
            pendingRequests.entries.toList()
        }
        pending.forEach { (key, request) ->
            if (statesByRequestKey[key] != OriginalLoadState.Loaded) {
                statesByRequestKey[key] = OriginalLoadState.Loading
                launchOriginalRequest(
                    key = key,
                    context = request.context,
                    target = request.target,
                    accessToken = request.accessToken,
                )
            }
        }
    }

    private fun nextRequestVersion(key: RealOriginalRequestKey): Int {
        return synchronized(requestVersions) {
            val nextVersion = (requestVersions[key] ?: 0) + 1
            requestVersions[key] = nextVersion
            nextVersion
        }
    }

}

private fun RealOriginalMediaTarget.requestKey(): RealOriginalRequestKey? {
    val originalUrl = loadableOriginalUrl() ?: return null
    return RealOriginalRequestKey(
        mediaId = mediaId,
        originalUrl = originalUrl,
        originalCacheKey = mediaSource.viewerOriginalImageCacheKey(mediaType),
    )
}

private fun RealOriginalMediaTarget.loadableOriginalUrl(): String? {
    if (mediaType != AppMediaType.IMAGE) return null
    return mediaSource.viewerOriginalImageUrl(mediaType)
}
