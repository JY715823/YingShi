package com.example.yingshi.data.remote.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.yingshi.data.model.AuthTokens

interface TokenProvider {
    fun getAccessToken(): String?
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

    private var tokenStore: TokenStore = InMemoryTokenStore()
    var sessionVersion by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        tokenStore = SharedPreferencesTokenStore(
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        )
        sessionVersion += 1
    }

    override fun getAccessToken(): String? {
        val tokens = tokenStore.getTokens() ?: return null
        val accessToken = tokens.accessToken.takeIf { it.isNotBlank() } ?: return null
        if (tokens.accessTokenExpireAtMillis <= System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY_SKEW_MILLIS) {
            clearTokens()
            return null
        }
        return accessToken
    }

    fun getRefreshToken(): String? = tokenStore.getTokens()?.refreshToken

    fun saveTokens(tokens: AuthTokens) {
        tokenStore.saveTokens(tokens)
        sessionVersion += 1
    }

    fun clearTokens() {
        tokenStore.clearTokens()
        sessionVersion += 1
    }

    fun clearTokensIfAccessToken(accessToken: String?) {
        if (accessToken.isNullOrBlank() || tokenStore.getTokens()?.accessToken == accessToken) {
            clearTokens()
        }
    }

    override val isLoggedIn: Boolean
        get() = getAccessToken() != null
}
