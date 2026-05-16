package com.example.yingshi

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.yingshi.data.remote.auth.BackendAutoLoginManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class YingShiApplication : Application(), ImageLoaderFactory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        BackendDebugConfig.init(applicationContext)
        autoLogin()
        registerNetworkRetryCallback()
    }

    private fun autoLogin() {
        appScope.launch {
            BackendAutoLoginManager.loginDefault(force = true, reason = "app_start")
        }
    }

    private fun registerNetworkRetryCallback() {
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                appScope.launch {
                    BackendAutoLoginManager.loginDefault(force = false, reason = "network_available")
                }
            }
        })
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(false)
            .respectCacheHeaders(false)
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
