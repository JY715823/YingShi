package com.example.yingshi.data.remote.config

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.yingshi.BuildConfig
import com.example.yingshi.data.remote.auth.AuthSessionManager

data class BackendDebugSettings(
    val baseUrl: String,
)

object BackendDebugConfig {
    private const val PREFS_NAME = "backend_debug_settings"
    private const val KEY_BASE_URL = "base_url"
    private const val LEGACY_CLOUDFLARE_HOST = "trycloudflare.com"

    private var appContext: Context? = null
    var sessionVersion by mutableIntStateOf(0)
        private set

    var settings by mutableStateOf(
        BackendDebugSettings(
            baseUrl = defaultBaseUrl(),
        ),
    )
        private set

    fun init(context: Context) {
        if (appContext != null) {
            return
        }
        appContext = context.applicationContext
        val preferences = preferences()
        val storedBaseUrl = preferences.getString(KEY_BASE_URL, defaultBaseUrl()) ?: defaultBaseUrl()
        val normalizedBaseUrl = normalizeBaseUrl(storedBaseUrl)
        val resolvedBaseUrl = if (shouldReplaceLegacyTunnelUrl(normalizedBaseUrl)) {
            val fallbackBaseUrl = defaultBaseUrl()
            preferences.edit().putString(KEY_BASE_URL, fallbackBaseUrl).apply()
            fallbackBaseUrl
        } else {
            normalizedBaseUrl
        }
        settings = BackendDebugSettings(
            baseUrl = resolvedBaseUrl,
        )
    }

    fun currentBaseUrl(): String = normalizeBaseUrl(settings.baseUrl)

    fun updateBaseUrl(rawBaseUrl: String) {
        val nextValue = normalizeBaseUrl(rawBaseUrl)
        if (nextValue == settings.baseUrl) {
            return
        }
        settings = settings.copy(baseUrl = nextValue)
        preferencesOrNull()?.edit()?.putString(KEY_BASE_URL, nextValue)?.apply()
        AuthSessionManager.clearAllAuthState()
        sessionVersion += 1
        RemoteServiceFactory.invalidate()
    }

    fun resetBaseUrlToDefault() {
        updateBaseUrl(defaultBaseUrl())
    }

    private fun preferences() = requireNotNull(appContext) {
        "BackendDebugConfig must be initialized before use."
    }.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun preferencesOrNull() = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun normalizeBaseUrl(rawValue: String): String {
        val trimmed = rawValue.trim().ifBlank { defaultBaseUrl() }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private fun shouldReplaceLegacyTunnelUrl(baseUrl: String): Boolean {
        return baseUrl.contains(LEGACY_CLOUDFLARE_HOST, ignoreCase = true)
    }

    private fun defaultBaseUrl(): String = BuildConfig.DEFAULT_API_BASE_URL
}
