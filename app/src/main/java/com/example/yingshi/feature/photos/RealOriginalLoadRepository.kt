package com.example.yingshi.feature.photos

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import coil.imageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
            return
        }
        statesByRequestKey[key] = state
        if (state == OriginalLoadState.Loaded) {
            FakeMediaCacheRepository.markOriginalCached(target.mediaId)
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
        if (currentState != OriginalLoadState.Loaded && currentState != OriginalLoadState.Loading) {
            statesByRequestKey[key] = OriginalLoadState.Loading
            synchronized(activeJobs) {
                activeJobs[key]?.cancel()
                activeJobs[key] = scope.launch(Dispatchers.IO) {
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
                            setState(target, OriginalLoadState.Failed)
                        }
                    } finally {
                        synchronized(activeJobs) {
                            activeJobs.remove(key)
                        }
                    }
                }
            }
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
            .forEach(statesByRequestKey::remove)
    }

    fun clearAllOriginals() {
        synchronized(activeJobs) {
            activeJobs.values.forEach { it.cancel() }
            activeJobs.clear()
        }
        statesByRequestKey.clear()
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
        accessToken: String?,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            Log.d("RealOriginalLoadRepo", "build original request url=$url")
            val request = backendMediaOriginalImageRequest(
                context = context,
                url = url,
                accessToken = accessToken,
                memoryCacheKey = sharedOriginalMemoryCacheKey(url),
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

}

private fun RealOriginalMediaTarget.requestKey(): RealOriginalRequestKey? {
    val originalUrl = loadableOriginalUrl() ?: return null
    return RealOriginalRequestKey(
        mediaId = mediaId,
        originalUrl = originalUrl,
    )
}

private fun RealOriginalMediaTarget.loadableOriginalUrl(): String? {
    if (mediaType != AppMediaType.IMAGE) return null
    return mediaSource.viewerOriginalImageUrl(mediaType)
}
