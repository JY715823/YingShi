package com.example.yingshi.data.remote.auth

import com.example.yingshi.data.model.AuthTokens
import com.example.yingshi.data.remote.api.AuthApi
import com.example.yingshi.data.remote.dto.RefreshTokenRequestDto
import com.example.yingshi.data.remote.mapper.toRemoteModel
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.HttpException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object AuthRefreshCoordinator {
    private val refreshLock = ReentrantLock()
    private var authApiFactory: (() -> AuthApi)? = null

    fun registerAuthApiFactory(factory: () -> AuthApi) {
        authApiFactory = factory
    }

    fun createAuthenticator(): Authenticator {
        return Authenticator { _, response ->
            refreshAndRebuildRequest(response)
        }
    }

    fun refreshBlocking(): Boolean {
        return refreshTokens(expectedAccessToken = null) != null
    }

    fun refreshAndRebuildRequest(response: Response): Request? {
        if (response.priorResponseCount() >= 2) {
            return null
        }
        val sentAccessToken = response.request.header("Authorization")
            ?.removePrefix("Bearer")
            ?.trim()
        if (sentAccessToken.isNullOrBlank()) {
            return null
        }
        val refreshedTokens = refreshTokens(expectedAccessToken = sentAccessToken) ?: return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer ${refreshedTokens.accessToken}")
            .build()
    }

    private fun refreshTokens(expectedAccessToken: String?): AuthTokens? {
        val currentRefreshToken = AuthSessionManager.peekRefreshToken()?.takeIf { it.isNotBlank() } ?: return null
        refreshLock.withLock {
            val latestTokens = AuthSessionManager.peekTokens()
            if (latestTokens == null) {
                return null
            }
            if (
                !expectedAccessToken.isNullOrBlank() &&
                latestTokens.accessToken.isNotBlank() &&
                latestTokens.accessToken != expectedAccessToken
            ) {
                return latestTokens
            }

            val latestRefreshToken = latestTokens.refreshToken.takeIf { it.isNotBlank() } ?: return null
            if (latestRefreshToken != currentRefreshToken) {
                return AuthSessionManager.peekTokens()
            }

            val authApi = authApiFactory?.invoke() ?: return null
            return try {
                val refreshedTokens = runBlocking {
                    authApi.refreshToken(
                        RefreshTokenRequestDto(refreshToken = latestRefreshToken),
                    ).data.toRemoteModel()
                }
                AuthSessionManager.saveTokens(refreshedTokens)
                refreshedTokens
            } catch (exception: Exception) {
                if ((exception as? HttpException)?.code() == 401) {
                    AuthSessionManager.clearTokens()
                }
                null
            }
        }
    }
}

private fun Response.priorResponseCount(): Int {
    var count = 0
    var current: Response? = this.priorResponse
    while (current != null) {
        count += 1
        current = current.priorResponse
    }
    return count
}
