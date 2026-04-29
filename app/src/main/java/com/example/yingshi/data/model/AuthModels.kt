package com.example.yingshi.data.model

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpireAtMillis: Long,
    val refreshTokenExpireAtMillis: Long,
)

data class RemoteCurrentUser(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val spaceId: String,
    val spaceDisplayName: String?,
)

data class RemoteLoginSession(
    val userId: String,
    val displayName: String,
    val spaceId: String,
    val tokens: AuthTokens,
)
