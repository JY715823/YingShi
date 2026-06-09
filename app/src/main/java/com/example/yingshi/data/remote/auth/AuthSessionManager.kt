package com.example.yingshi.data.remote.auth

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.yingshi.data.cache.AppReadCacheStore
import com.example.yingshi.data.cache.OfflineAccessManager
import com.example.yingshi.data.model.AuthTokens
import com.example.yingshi.data.model.RemoteCurrentUser
import java.util.Locale
import java.util.UUID

private fun SharedPreferences.Editor.persist() {
    if (!commit()) {
        apply()
    }
}

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
            .persist()
    }

    override fun clearTokens() {
        preferences.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_ACCESS_EXPIRES_AT)
            .remove(KEY_REFRESH_EXPIRES_AT)
            .persist()
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
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_LAST_SIGNED_IN_ACCOUNT = "last_signed_in_account"
    private const val KEY_REMEMBERED_LOGIN_PREFIX = "remembered_login_token_"
    private const val KEY_REMEMBERED_LOGIN_EXPIRE_PREFIX = "remembered_login_expire_"

    private var tokenStore: TokenStore = InMemoryTokenStore()
    private var sessionPreferences: SharedPreferences? = null
    private var currentUserSnapshot: RemoteCurrentUser? = null
    private var installDeviceId: String = UUID.randomUUID().toString()
    var sessionVersion by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        sessionPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        AppReadCacheStore.init(context.applicationContext)
        tokenStore = SharedPreferencesTokenStore(
            preferences = requireNotNull(sessionPreferences),
        )
        installDeviceId = sessionPreferences
            ?.getString(KEY_DEVICE_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().also { deviceId ->
                sessionPreferences?.edit()?.putString(KEY_DEVICE_ID, deviceId)?.persist()
            }
        currentUserSnapshot = AppReadCacheStore.readCurrentUser()?.payload
        sessionVersion += 1
    }

    override fun getAccessToken(): String? {
        val tokens = tokenStore.getTokens() ?: return null
        val accessToken = tokens.accessToken.takeIf { it.isNotBlank() } ?: return null
        if (tokens.accessTokenExpireAtMillis <= System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY_SKEW_MILLIS) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                return accessToken
            }
            AuthRefreshCoordinator.refreshBlocking()
            return tokenStore.getTokens()?.accessToken?.takeIf { it.isNotBlank() }
        }
        return accessToken
    }

    fun getRefreshToken(): String? = tokenStore.getTokens()?.refreshToken

    override fun peekAccessToken(): String? = tokenStore.getTokens()?.accessToken?.takeIf { it.isNotBlank() }

    fun peekRefreshToken(): String? = tokenStore.getTokens()?.refreshToken?.takeIf { it.isNotBlank() }

    fun peekTokens(): AuthTokens? = tokenStore.getTokens()

    fun getDeviceId(): String = installDeviceId

    fun saveTokens(tokens: AuthTokens) {
        tokenStore.saveTokens(tokens)
        sessionVersion += 1
    }

    fun getCurrentUserSnapshot(): RemoteCurrentUser? {
        return currentUserSnapshot
    }

    fun getLastSignedInAccount(): String? {
        return sessionPreferences
            ?.getString(KEY_LAST_SIGNED_IN_ACCOUNT, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun saveCurrentUserSnapshot(user: RemoteCurrentUser) {
        currentUserSnapshot = user
        AppReadCacheStore.writeCurrentUser(user)
        sessionPreferences?.edit()?.putString(KEY_LAST_SIGNED_IN_ACCOUNT, user.account.trim())?.persist()
    }

    fun clearCurrentUserSnapshot() {
        currentUserSnapshot = null
        AppReadCacheStore.clearCurrentUser()
    }

    fun clearTokens() {
        tokenStore.clearTokens()
        currentUserSnapshot = null
        AppReadCacheStore.clearProtectedData()
        OfflineAccessManager.clear()
        sessionVersion += 1
    }

    fun clearTokensPreservingReadCache() {
        tokenStore.clearTokens()
        sessionVersion += 1
    }

    fun clearTokensIfAccessToken(accessToken: String?) {
        if (accessToken.isNullOrBlank() || tokenStore.getTokens()?.accessToken == accessToken) {
            clearTokensPreservingReadCache()
        }
    }

    fun saveRememberedLogin(
        account: String,
        token: String?,
        expireAtMillis: Long?,
    ) {
        val preferences = sessionPreferences ?: return
        val normalizedAccount = normalizeAccountKey(account) ?: return
        val tokenKey = KEY_REMEMBERED_LOGIN_PREFIX + normalizedAccount
        val expireKey = KEY_REMEMBERED_LOGIN_EXPIRE_PREFIX + normalizedAccount
        if (token.isNullOrBlank() || expireAtMillis == null || expireAtMillis <= System.currentTimeMillis()) {
            preferences.edit()
                .remove(tokenKey)
                .remove(expireKey)
                .persist()
            return
        }
        preferences.edit()
            .putString(tokenKey, token)
            .putLong(expireKey, expireAtMillis)
            .persist()
    }

    fun getRememberedLoginToken(account: String): String? {
        val preferences = sessionPreferences ?: return null
        val normalizedAccount = normalizeAccountKey(account) ?: return null
        val tokenKey = KEY_REMEMBERED_LOGIN_PREFIX + normalizedAccount
        val expireKey = KEY_REMEMBERED_LOGIN_EXPIRE_PREFIX + normalizedAccount
        val token = preferences.getString(tokenKey, null)?.takeIf { it.isNotBlank() } ?: return null
        val expireAtMillis = preferences.getLong(expireKey, 0L)
        if (expireAtMillis <= System.currentTimeMillis()) {
            preferences.edit()
                .remove(tokenKey)
                .remove(expireKey)
                .persist()
            return null
        }
        return token
    }

    fun clearRememberedLogin(account: String) {
        val preferences = sessionPreferences ?: return
        val normalizedAccount = normalizeAccountKey(account) ?: return
        preferences.edit()
            .remove(KEY_REMEMBERED_LOGIN_PREFIX + normalizedAccount)
            .remove(KEY_REMEMBERED_LOGIN_EXPIRE_PREFIX + normalizedAccount)
            .persist()
    }

    fun clearAllRememberedLogins() {
        val preferences = sessionPreferences ?: return
        val editor = preferences.edit()
        preferences.all.keys
            .filter {
                it.startsWith(KEY_REMEMBERED_LOGIN_PREFIX) ||
                    it.startsWith(KEY_REMEMBERED_LOGIN_EXPIRE_PREFIX)
            }
            .forEach(editor::remove)
        editor.persist()
    }

    fun clearAllAuthState() {
        clearTokens()
        clearAllRememberedLogins()
    }

    override val isLoggedIn: Boolean
        get() {
            val tokens = tokenStore.getTokens() ?: return false
            return tokens.refreshToken.isNotBlank() &&
                tokens.refreshTokenExpireAtMillis > System.currentTimeMillis()
        }

    private fun normalizeAccountKey(account: String): String? {
        return account.trim()
            .takeIf { it.isNotBlank() }
            ?.lowercase(Locale.ROOT)
    }

}
