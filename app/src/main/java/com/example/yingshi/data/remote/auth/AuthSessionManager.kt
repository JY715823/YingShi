package com.example.yingshi.data.remote.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.yingshi.data.model.AuthTokens
import com.example.yingshi.data.model.RemoteCurrentUser
import com.google.gson.Gson

interface TokenProvider {
    fun getAccessToken(): String?
    fun peekAccessToken(): String?
    val isLoggedIn: Boolean
}

interface TokenStore {
    fun getTokens(): AuthTokens?
    fun saveTokens(tokens: AuthTokens)
    fun clearTokens()
}

private class InMemoryTokenStore : TokenStore {
    private var tokens: AuthTokens? = null

    override fun getTokens(): AuthTokens? = tokens

    override fun saveTokens(tokens: AuthTokens) {
        this.tokens = tokens
    }

    override fun clearTokens() {
        tokens = null
    }
}

private class SharedPreferencesTokenStore(
    private val preferences: SharedPreferences,
) : TokenStore {
    override fun getTokens(): AuthTokens? {
        val accessToken = preferences.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        val refreshToken = preferences.getString(KEY_REFRESH_TOKEN, null).orEmpty()
        return AuthTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpireAtMillis = preferences.getLong(KEY_ACCESS_EXPIRES_AT, 0L),
            refreshTokenExpireAtMillis = preferences.getLong(KEY_REFRESH_EXPIRES_AT, 0L),
        )
    }

    override fun saveTokens(tokens: AuthTokens) {
        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, tokens.accessToken)
            .putString(KEY_REFRESH_TOKEN, tokens.refreshToken)
            .putLong(KEY_ACCESS_EXPIRES_AT, tokens.accessTokenExpireAtMillis)
            .putLong(KEY_REFRESH_EXPIRES_AT, tokens.refreshTokenExpireAtMillis)
            .apply()
    }

    override fun clearTokens() {
        preferences.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_ACCESS_EXPIRES_AT)
            .remove(KEY_REFRESH_EXPIRES_AT)
            .apply()
    }

    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_ACCESS_EXPIRES_AT = "access_token_expires_at"
        const val KEY_REFRESH_EXPIRES_AT = "refresh_token_expires_at"
    }
}

object AuthSessionManager : TokenProvider {
    private const val ACCESS_TOKEN_EXPIRY_SKEW_MILLIS = 30_000L
    private const val PREFS_NAME = "auth_session"
    private const val KEY_CURRENT_USER_SNAPSHOT = "current_user_snapshot_json"

    private var tokenStore: TokenStore = InMemoryTokenStore()
    private var sessionPreferences: SharedPreferences? = null
    private var currentUserSnapshotMemory: RemoteCurrentUser? = null
    private val gson = Gson()
    var sessionVersion by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        sessionPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        tokenStore = SharedPreferencesTokenStore(
            preferences = requireNotNull(sessionPreferences),
        )
        sessionVersion += 1
    }

    override fun getAccessToken(): String? {
        val tokens = tokenStore.getTokens() ?: return null
        val accessToken = tokens.accessToken.takeIf { it.isNotBlank() } ?: return null
        if (tokens.accessTokenExpireAtMillis <= System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY_SKEW_MILLIS) {
            AuthRefreshCoordinator.refreshBlocking()
            return tokenStore.getTokens()?.accessToken?.takeIf { it.isNotBlank() }
        }
        return accessToken
    }

    fun getRefreshToken(): String? = tokenStore.getTokens()?.refreshToken

    override fun peekAccessToken(): String? = tokenStore.getTokens()?.accessToken?.takeIf { it.isNotBlank() }

    fun peekRefreshToken(): String? = tokenStore.getTokens()?.refreshToken?.takeIf { it.isNotBlank() }

    fun peekTokens(): AuthTokens? = tokenStore.getTokens()

    fun saveTokens(tokens: AuthTokens) {
        tokenStore.saveTokens(tokens)
        sessionVersion += 1
    }

    fun getCurrentUserSnapshot(): RemoteCurrentUser? {
        val cachedJson = sessionPreferences?.getString(KEY_CURRENT_USER_SNAPSHOT, null)
        if (!cachedJson.isNullOrBlank()) {
            return runCatching {
                gson.fromJson(cachedJson, RemoteCurrentUser::class.java)
            }.getOrNull()?.also { currentUserSnapshotMemory = it }
        }
        return currentUserSnapshotMemory
    }

    fun saveCurrentUserSnapshot(user: RemoteCurrentUser) {
        currentUserSnapshotMemory = user
        sessionPreferences?.edit()
            ?.putString(KEY_CURRENT_USER_SNAPSHOT, gson.toJson(user))
            ?.apply()
    }

    fun clearCurrentUserSnapshot() {
        currentUserSnapshotMemory = null
        sessionPreferences?.edit()
            ?.remove(KEY_CURRENT_USER_SNAPSHOT)
            ?.apply()
    }

    fun clearTokens() {
        tokenStore.clearTokens()
        clearCurrentUserSnapshot()
        sessionVersion += 1
    }

    fun clearTokensIfAccessToken(accessToken: String?) {
        if (accessToken.isNullOrBlank() || tokenStore.getTokens()?.accessToken == accessToken) {
            clearTokens()
        }
    }

    override val isLoggedIn: Boolean
        get() {
            val tokens = tokenStore.getTokens() ?: return false
            return tokens.refreshToken.isNotBlank() &&
                tokens.refreshTokenExpireAtMillis > System.currentTimeMillis()
        }
}
