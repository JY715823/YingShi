package com.example.yingshi.data.model

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpireAtMillis: Long,
    val refreshTokenExpireAtMillis: Long,
)

data class RemotePartnerProfile(
    val userId: String,
    val account: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
)

data class RemoteCurrentUser(
    val userId: String,
    val account: String,
    val displayName: String,
    val avatarUrl: String?,
    val libraryId: String,
    val libraryDisplayName: String?,
    val bio: String? = null,
    val partner: RemotePartnerProfile? = null,
    val createdAtMillis: Long? = null,
    val updatedAtMillis: Long? = null,
)

data class RemoteLoginChallenge(
    val challengeId: String,
    val maskedEmail: String,
    val expireAtMillis: Long,
    val resendAvailableAtMillis: Long,
)

data class RemoteLoginSession(
    val userId: String,
    val account: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val libraryId: String,
    val libraryDisplayName: String?,
    val partner: RemotePartnerProfile? = null,
    val createdAtMillis: Long? = null,
    val updatedAtMillis: Long? = null,
    val rememberedLoginToken: String? = null,
    val rememberedLoginExpireAtMillis: Long? = null,
    val tokens: AuthTokens,
)
