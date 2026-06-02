package com.example.yingshi

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.feature.life.push.PushTokenRegistrar

class YingShiApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        AuthSessionManager.init(applicationContext)
        BackendDebugConfig.init(applicationContext)
        PushTokenRegistrar.registerCurrentTokenIfPossible(applicationContext)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
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
                MemoryCache.Builder(this@YingShiApplication)
                    .maxSizePercent(0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil-media-cache"))
                    .maxSizePercent(0.12)
                    .build()
            }
            .build()
    }
}
