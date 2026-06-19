package com.example.yingshi.feature.photos

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
internal object AppMediaVideoCache {
    private const val CacheDirectoryName = "app-video-cache"
    private const val MaxCacheBytes = 768L * 1024L * 1024L

    private val lock = Any()
    private var simpleCache: SimpleCache? = null
    private var databaseProvider: StandaloneDatabaseProvider? = null

    fun dataSourceFactory(
        context: Context,
        requestHeaders: Map<String, String>,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): DataSource.Factory {
        val appContext = context.applicationContext
        val upstreamHttpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(connectTimeoutMs)
            .setReadTimeoutMs(readTimeoutMs)
            .setDefaultRequestProperties(requestHeaders)
        val upstreamFactory = DefaultDataSource.Factory(appContext, upstreamHttpFactory)
        return CacheDataSource.Factory()
            .setCache(cache(appContext))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun directory(context: Context): File {
        return context.applicationContext.cacheDir.resolve(CacheDirectoryName)
    }

    fun clear(context: Context): Boolean {
        val appContext = context.applicationContext
        synchronized(lock) {
            runCatching { simpleCache?.release() }
            simpleCache = null
            databaseProvider = null
        }
        return directory(appContext).deleteContentsSafely()
    }

    fun cache(context: Context): SimpleCache {
        val appContext = context.applicationContext
        synchronized(lock) {
            simpleCache?.let { return it }
            val provider = databaseProvider ?: StandaloneDatabaseProvider(appContext).also {
                databaseProvider = it
            }
            return SimpleCache(
                directory(appContext).apply { mkdirs() },
                LeastRecentlyUsedCacheEvictor(MaxCacheBytes),
                provider,
            ).also {
                simpleCache = it
            }
        }
    }
}
