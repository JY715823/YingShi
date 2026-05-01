package com.example.yingshi.data.remote.config

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.yingshi.BuildConfig
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.repository.RepositoryMode

data class BackendDebugSettings(
    val baseUrl: String,
    val repositoryMode: RepositoryMode,
)

object BackendDebugConfig {
    private const val PREFS_NAME = "backend_debug_settings"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_REPOSITORY_MODE = "repository_mode"

    private var appContext: Context? = null
    var sessionVersion by mutableIntStateOf(0)
        private set

    var settings by mutableStateOf(
        BackendDebugSettings(
            baseUrl = defaultBaseUrl(),
            repositoryMode = defaultRepositoryMode(),
        ),
    )
        private set

    fun init(context: Context) {
        if (appContext != null) {
            return
        }
        appContext = context.applicationContext
        val preferences = preferences()
        settings = BackendDebugSettings(
            baseUrl = normalizeBaseUrl(
                preferences.getString(KEY_BASE_URL, defaultBaseUrl()) ?: defaultBaseUrl(),
            ),
            repositoryMode = parseRepositoryMode(
                preferences.getString(KEY_REPOSITORY_MODE, defaultRepositoryMode().name),
            ),
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
        AuthSessionManager.clearTokens()
        sessionVersion += 1
        RemoteServiceFactory.invalidate()
    }

    fun updateRepositoryMode(mode: RepositoryMode) {
        if (mode == settings.repositoryMode) {
            return
        }
        settings = settings.copy(repositoryMode = mode)
        preferencesOrNull()?.edit()?.putString(KEY_REPOSITORY_MODE, mode.name)?.apply()
        sessionVersion += 1
    }

    fun resetBaseUrlToDefault() {
        updateBaseUrl(defaultBaseUrl())
    }

    private fun preferences() = requireNotNull(appContext) {
        "BackendDebugConfig must be initialized before use."
    }.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun preferencesOrNull() = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun parseRepositoryMode(rawValue: String?): RepositoryMode {
        return runCatching {
            RepositoryMode.valueOf(rawValue.orEmpty())
        }.getOrElse {
            defaultRepositoryMode()
        }
    }

    private fun normalizeBaseUrl(rawValue: String): String {
        val trimmed = rawValue.trim().ifBlank { defaultBaseUrl() }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private fun defaultBaseUrl(): String = BuildConfig.DEFAULT_API_BASE_URL

    private fun defaultRepositoryMode(): RepositoryMode {
        return runCatching {
            RepositoryMode.valueOf(BuildConfig.DEFAULT_REPOSITORY_MODE)
        }.getOrElse {
            RepositoryMode.FAKE
        }
    }
}
