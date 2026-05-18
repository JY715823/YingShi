package com.example.yingshi.data.remote.dto

data class LoginRequestDto(
    val account: String,
    val password: String,
)

data class LoginResponseDto(
    val userId: String,
    val account: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val libraryId: String,
    val libraryDisplayName: String? = null,
    val partner: PartnerProfileDto? = null,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpireAtMillis: Long,
    val refreshTokenExpireAtMillis: Long,
)

data class RefreshTokenRequestDto(
    val refreshToken: String,
)

data class RefreshTokenResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpireAtMillis: Long,
    val refreshTokenExpireAtMillis: Long,
)

data class LogoutRequestDto(
    val refreshToken: String,
)

data class LogoutResponseDto(
    val success: Boolean,
)

data class CurrentUserDto(
    val userId: String,
    val account: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val libraryId: String,
    val libraryDisplayName: String? = null,
    val partner: PartnerProfileDto? = null,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
)

data class PartnerProfileDto(
    val userId: String,
    val account: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
)

data class UpdateProfileRequestDto(
    val displayName: String,
    val bio: String? = null,
)

data class AuthErrorDto(
    val code: String,
    val message: String,
)
