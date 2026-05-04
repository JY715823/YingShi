package com.example.yingshi.feature.photos

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import coil.imageLoader
import coil.request.SuccessResult
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class RealOriginalMediaTarget(
    val mediaId: String,
    val mediaType: AppMediaType,
    val mediaSource: AppContentMediaSource?,
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
    private val statesByMediaId = mutableStateMapOf<String, OriginalLoadState>()

    fun getState(mediaId: String): OriginalLoadState {
        return statesByMediaId[mediaId] ?: OriginalLoadState.NotLoaded
    }

    fun setState(mediaId: String, state: OriginalLoadState) {
        statesByMediaId[mediaId] = state
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

    suspend fun loadOriginal(
        context: Context,
        target: RealOriginalMediaTarget,
        accessToken: String?,
    ): OriginalLoadState {
        val currentState = getState(target.mediaId)
        if (currentState == OriginalLoadState.Loaded || currentState == OriginalLoadState.Loading) {
            return currentState
        }

        val originalUrl = target.mediaSource.viewerOriginalMediaUrl(target.mediaType)
        if (originalUrl.isNullOrBlank()) {
            setState(target.mediaId, OriginalLoadState.Failed)
            return OriginalLoadState.Failed
        }

        setState(target.mediaId, OriginalLoadState.Loading)
        val loadSucceeded = when (target.mediaType) {
            AppMediaType.IMAGE -> loadOriginalImage(
                context = context,
                url = originalUrl,
                accessToken = accessToken,
            )

            AppMediaType.VIDEO -> probeOriginalMediaUrl(
                url = originalUrl,
                accessToken = accessToken,
            )
        }

        val nextState = if (loadSucceeded) {
            FakeMediaCacheRepository.markOriginalCached(target.mediaId)
            OriginalLoadState.Loaded
        } else {
            OriginalLoadState.Failed
        }
        setState(target.mediaId, nextState)
        return nextState
    }

    suspend fun loadAllOriginals(
        context: Context,
        targets: List<RealOriginalMediaTarget>,
        accessToken: String?,
    ): PostOriginalLoadSummary {
        targets.distinctBy { it.mediaId }.forEach { target ->
            if (getState(target.mediaId) != OriginalLoadState.Loaded) {
                loadOriginal(
                    context = context,
                    target = target,
                    accessToken = accessToken,
                )
            }
        }
        return getPostSummary(targets.map { it.mediaId })
    }

    private suspend fun loadOriginalImage(
        context: Context,
        url: String,
        accessToken: String?,
    ): Boolean {
        val request = backendMediaOriginalImageRequest(
            context = context,
            url = url,
            accessToken = accessToken,
            memoryCacheKey = sharedOriginalMemoryCacheKey(url),
        ) ?: return false
        return context.imageLoader.execute(request) is SuccessResult
    }

    private suspend fun probeOriginalMediaUrl(
        url: String,
        accessToken: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4_000
                readTimeout = 4_000
                backendMediaRequestHeaders(url, accessToken).forEach { (key, value) ->
                    setRequestProperty(key, value)
                }
                setRequestProperty("Range", "bytes=0-0")
            }
            try {
                connection.inputStream.use { it.read() }
                connection.responseCode in 200..299 ||
                    connection.responseCode == HttpURLConnection.HTTP_PARTIAL
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)
    }
}
