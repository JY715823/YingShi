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
    val bio: String? = null,
    val createdAtMillis: Long? = null,
    val updatedAtMillis: Long? = null,
)

data class RemoteLoginSession(
    val userId: String,
    val account: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val libraryId: String,
    val libraryDisplayName: String?,
    val createdAtMillis: Long? = null,
    val updatedAtMillis: Long? = null,
    val tokens: AuthTokens,
)
