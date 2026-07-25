package com.example.yingshi

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.config.RemoteServiceFactory
import com.example.yingshi.data.remote.connectivity.NetworkConnectivityMonitor
import com.example.yingshi.feature.photos.LocalSystemMediaRepository
import com.example.yingshi.feature.photos.SettingsRepository
import com.example.yingshi.feature.photos.VideoPreloadWorker
import com.example.yingshi.feature.photos.hasSystemMediaReadAccess
import com.example.yingshi.feature.photos.preloadSystemMediaCache
import com.example.yingshi.feature.life.push.PushNotificationChannels
import com.example.yingshi.feature.life.push.PushTokenRefreshWorker
import com.example.yingshi.feature.life.push.PushTokenRegistrar
import com.example.yingshi.feature.life.push.SseConnectionManager
import com.example.yingshi.feature.life.push.SseKeepAliveReceiver
import com.example.yingshi.feature.sync.SyncVersionTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class YingShiApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        AuthSessionManager.init(applicationContext)
        BackendDebugConfig.init(applicationContext)
        NetworkConnectivityMonitor.init(applicationContext)
        SettingsRepository.init(applicationContext)
        PushNotificationChannels.ensureChannels(applicationContext)
        VideoPreloadWorker.schedulePeriodic(applicationContext)
        PushTokenRefreshWorker.schedulePeriodic(applicationContext)
        preloadSystemMediaCache(applicationContext)
        // P2-2: WorkManager 周期维护系统媒体缓存 (15min), 进程被杀后可恢复,
        // 确保用户下次进入页面 peekCachedMedia 命中 (<200ms 出列表)
        SystemMediaCacheRefreshWorker.schedulePeriodic(applicationContext)
        PushTokenRegistrar.registerCurrentTokenIfPossible(applicationContext)
        SyncVersionTracker.init(applicationContext)
        // 启动 SSE 连接管理器（App 在前台时立即工作）
        // 前台服务由 MainActivity.onCreate() 启动（MIUI 限制后台启动前台服务）
        // 前台服务启动后接管 SSE 保活，App 退到后台后 SSE 仍可持续
        SseConnectionManager.start(applicationContext)
        // 调度周期性保活广播作为兜底：
        // 即使前台服务被杀、App 进程被杀，AlarmManager 仍会定时唤醒 App 进程
        //（通过广播），重新启动 SseConnectionManager。
        SseKeepAliveReceiver.scheduleNextKeepAlive(applicationContext)
    }

    override fun newImageLoader(): ImageLoader {
        // P0 修复：接入专为图片加载优化的 OkHttpClient（maxRequestsPerHost=32、共享连接池、
        // AuthInterceptor + 401 自动刷新）。此前使用 Coil 默认 OkHttpClient（maxRequestsPerHost=5），
        // 在照片流高并发缩略图场景下请求严重排队，表现为"点击能打开但缩略图空白几秒才出图"。
        // 接入后并发能力提升 6.4 倍，且与 API 调用复用 TCP/TLS 连接，省去握手开销。
        return ImageLoader.Builder(this)
            .okHttpClient(RemoteServiceFactory.imageOkHttpClient)
            .crossfade(false)
            .respectCacheHeaders(false)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .memoryCache {
                // 缩略图场景下，内存缓存命中率直接决定"即时响应"体验。
                // 提升到 40%（此前 30%）：照片流通常可见 ~50 张缩略图，加上预取的 ~80 张，
                // 720px 缩略图单张约 200-400KB，总占用 ~50MB，30% 在低端机上可能被挤出。
                MemoryCache.Builder(this@YingShiApplication)
                    .maxSizePercent(0.40)
                    .build()
            }
            .diskCache {
                // 磁盘缓存提升到 20%（此前 12%）：缩略图不大，多缓存历史媒体，
                // 用户滑动到很久之前的照片时也能毫秒级出图，"不占空间"。
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil-media-cache"))
                    .maxSizePercent(0.20)
                    .build()
            }
            .build()
    }
}

// P2-2: WorkManager 周期缓存刷新, 模拟"准常驻"进程, 对标小米相册 gallery.db 持久化策略.
// 进程被杀后, WorkManager 会在系统调度下恢复运行, 维护 LocalSystemMediaQueryCache.memoryItems
// 和 SystemMediaExifCache (单例). 用户下次进入系统媒体页时, peekCachedMedia 命中, <200ms 出列表.
class SystemMediaCacheRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!isActive || isStopped) return@withContext Result.success()
        if (!hasSystemMediaReadAccess(applicationContext)) {
            return@withContext Result.success()
        }
        try {
            LocalSystemMediaRepository(applicationContext).loadMedia(
                forceRefresh = true,
                useLocalOverlayOnly = true,
            )
            Result.success()
        } catch (_: CancellationException) {
            Result.success()
        } catch (_: Exception) {
            Result.success()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "system-media-cache-refresh-periodic"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SystemMediaCacheRefreshWorker>(
                15, TimeUnit.MINUTES,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
