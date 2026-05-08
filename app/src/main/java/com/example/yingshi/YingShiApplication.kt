package com.example.yingshi

import android.app.Application
import android.widget.Toast
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.yingshi.data.model.AuthTokens
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.RemoteServiceFactory
import com.example.yingshi.data.remote.dto.LoginRequestDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class YingShiApplication : Application(), ImageLoaderFactory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        autoLogin()
    }

    private fun autoLogin() {
        appScope.launch {
            try {
                val response = RemoteServiceFactory.authApi.login(
                    LoginRequestDto(
                        account = "demo.a@yingshi.local",
                        password = "demo123456",
                    )
                )
                val body = response.data
                AuthSessionManager.saveTokens(
                    AuthTokens(
                        accessToken = body.accessToken,
                        refreshToken = body.refreshToken,
                        accessTokenExpireAtMillis = body.accessTokenExpireAtMillis,
                        refreshTokenExpireAtMillis = body.refreshTokenExpireAtMillis,
                    )
                )
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(
                        this@YingShiApplication,
                        "自动登录失败，请到设置 → 后端联调中手动登录。",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
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
