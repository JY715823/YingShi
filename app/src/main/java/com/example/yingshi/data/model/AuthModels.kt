package com.example.yingshi.data.model

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpireAtMillis: Long,
    val refreshTokenExpireAtMillis: Long,
)

data class RemoteCurrentUser(
    val userId: String,
    val account: String,
    val displayName: String,
    val avatarUrl: String?,
    val libraryId: String,
    val libraryDisplayName: String?,
)

data class RemoteLoginSession(
    val userId: String,
    val account: String,
    val displayName: String,
    val libraryId: String,
    val libraryDisplayName: String?,
    val tokens: AuthTokens,
)
