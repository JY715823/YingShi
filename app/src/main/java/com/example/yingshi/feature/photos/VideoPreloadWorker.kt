package com.example.yingshi.feature.photos

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.yingshi.data.remote.config.RemoteServiceFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class VideoPreloadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val feed = RemoteServiceFactory.mediaApi.getMediaFeed(pageSize = MAX_FEED_ITEMS)
            val videos = feed.data.orEmpty().filter {
                it.mediaType.equals("video", ignoreCase = true)
            }.take(MAX_VIDEOS)
            if (videos.isEmpty()) return@withContext Result.success()

            val cache = AppMediaVideoCache.cache(applicationContext)

            for (media in videos) {
                if (!isActive || isStopped) break
                val access = media.access.orEmpty().firstOrNull {
                    it.variant.equals("video", ignoreCase = true)
                } ?: continue

                val url = resolveUrl(access) ?: continue
                val cacheKey = access.cacheKey?.trim()?.takeIf { it.isNotBlank() }
                    ?: sharedVideoDiskCacheKey(url)

                val dataSourceFactory = AppMediaVideoCache.dataSourceFactory(
                    context = applicationContext,
                    requestHeaders = emptyMap(),
                    connectTimeoutMs = 12_000,
                    readTimeoutMs = 25_000,
                )

                try {
                    val dataSource = dataSourceFactory.createDataSource()
                    val dataSpec = DataSpec.Builder()
                        .setUri(Uri.parse(url))
                        .setKey(cacheKey)
                        .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
                        .build()
                    dataSource.open(dataSpec)

                    val buffer = ByteArray(32 * 1024)
                    var bytesRead = 0L
                    while (isActive && bytesRead < MAX_PRELOAD_BYTES_PER_VIDEO) {
                        val read = dataSource.read(buffer, 0, buffer.size)
                        if (read == C.RESULT_END_OF_INPUT) break
                        bytesRead += read
                    }
                    dataSource.close()
                } catch (_: CancellationException) {
                    break
                } catch (_: Exception) {
                    continue
                }
            }

            Result.success()
        } catch (_: CancellationException) {
            Result.retry()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    private fun resolveUrl(access: com.example.yingshi.data.remote.dto.MediaAccessDto): String? {
        val signed = access.signedUrl?.takeIf { it.isNotBlank() }
        if (signed != null && !isExpired(access.expiresAtMillis)) return signed
        return access.url?.takeIf { it.isNotBlank() } ?: signed
    }

    private fun isExpired(expiresAtMillis: Long?): Boolean {
        val expires = expiresAtMillis ?: return false
        return System.currentTimeMillis() > expires
    }

    companion object {
        private const val MAX_FEED_ITEMS = 15
        private const val MAX_VIDEOS = 3
        private const val MAX_PRELOAD_BYTES_PER_VIDEO = 6L * 1024L * 1024L
        private const val MAX_RETRIES = 3
        private const val PERIODIC_WORK_NAME = "video-preload-periodic"
        private const val ONE_TIME_WORK_NAME = "video-preload-one-time"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<VideoPreloadWorker>(
                12, TimeUnit.HOURS,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun scheduleOneTime(context: Context) {
            if (!isUnmetered(context)) return

            val request = OneTimeWorkRequestBuilder<VideoPreloadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        private fun isUnmetered(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
    }
}
