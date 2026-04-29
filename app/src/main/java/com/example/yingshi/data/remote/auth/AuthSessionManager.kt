package com.example.yingshi.data.remote.auth

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

class InMemoryTokenStore : TokenStore {
    private var tokens: AuthTokens? = null

    override fun getTokens(): AuthTokens? = tokens

    override fun saveTokens(tokens: AuthTokens) {
        this.tokens = tokens
    }

    override fun clearTokens() {
        tokens = null
    }
}

object AuthSessionManager : TokenProvider {
    private val tokenStore: TokenStore = InMemoryTokenStore()

    override fun getAccessToken(): String? = tokenStore.getTokens()?.accessToken

    fun getRefreshToken(): String? = tokenStore.getTokens()?.refreshToken

    fun saveTokens(tokens: AuthTokens) {
        tokenStore.saveTokens(tokens)
    }

    fun clearTokens() {
        tokenStore.clearTokens()
    }

    override val isLoggedIn: Boolean
        get() = !getAccessToken().isNullOrBlank()
}
