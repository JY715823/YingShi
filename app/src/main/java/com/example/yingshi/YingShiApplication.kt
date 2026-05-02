package com.example.yingshi

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class YingShiApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(false)
            .respectCacheHeaders(false)
            .memoryCache {
                MemoryCache.Builder(this@YingShiApplication)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil-media-cache"))
                    .maxSizePercent(0.08)
                    .build()
            }
            .build()
    }
}
