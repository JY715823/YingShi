package com.example.yingshi.feature.photos

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class PostOriginalLoadSummary(
    val totalCount: Int,
    val loadedCount: Int,
    val loadingCount: Int,
    val failedCount: Int,
) {
    val allLoaded: Boolean
        get() = totalCount > 0 && loadedCount == totalCount

    val hasLoading: Boolean
        get() = loadingCount > 0

    val buttonLabel: String
        get() = when {
            totalCount <= 0 -> "加载全帖原图"
            hasLoading -> "加载中... $loadedCount/$totalCount"
            allLoaded -> "已加载全帖原图"
            failedCount > 0 -> "加载失败，重试"
            else -> "加载全帖原图"
        }
}

object FakeOriginalLoadRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val statesByMediaId = mutableStateMapOf<String, OriginalLoadState>()
    private val activeJobs = mutableMapOf<String, Job>()

    fun getState(mediaId: String): OriginalLoadState {
        return statesByMediaId[mediaId] ?: OriginalLoadState.NotLoaded
    }

    fun loadOriginal(mediaId: String) {
        when (getState(mediaId)) {
            OriginalLoadState.Loaded,
            OriginalLoadState.Loading,
            -> return

            OriginalLoadState.NotLoaded,
            OriginalLoadState.Failed,
            -> startFakeLoad(mediaId)
        }
    }

    fun retryOriginal(mediaId: String) {
        startFakeLoad(mediaId)
    }

    fun clearOriginal(mediaId: String) {
        activeJobs.remove(mediaId)?.cancel()
        statesByMediaId[mediaId] = OriginalLoadState.NotLoaded
    }

    fun loadAllOriginals(mediaIds: List<String>) {
        mediaIds
            .distinct()
            .forEach { mediaId ->
                if (getState(mediaId) != OriginalLoadState.Loaded) {
                    startFakeLoad(mediaId)
                }
            }
    }

    fun getPostSummary(mediaIds: List<String>): PostOriginalLoadSummary {
        val distinctIds = mediaIds.distinct()
        val states = distinctIds.map(::getState)
        return PostOriginalLoadSummary(
            totalCount = distinctIds.size,
            loadedCount = states.count { it == OriginalLoadState.Loaded },
            loadingCount = states.count { it == OriginalLoadState.Loading },
            failedCount = states.count { it == OriginalLoadState.Failed },
        )
    }

    private fun startFakeLoad(mediaId: String) {
        activeJobs.remove(mediaId)?.cancel()
        statesByMediaId[mediaId] = OriginalLoadState.Loading
        activeJobs[mediaId] = scope.launch {
            delay(900)
            statesByMediaId[mediaId] = OriginalLoadState.Loaded
            FakeMediaCacheRepository.markOriginalCached(mediaId)
            activeJobs.remove(mediaId)
        }
    }
}
