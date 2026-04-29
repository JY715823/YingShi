package com.example.yingshi.data.remote.dto

data class LoginRequestDto(
    val account: String,
    val password: String,
    val deviceName: String? = null,
    val clientVersion: String? = null,
)

data class LoginResponseDto(
    val userId: String,
    val displayName: String,
    val spaceId: String,
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
    val displayName: String,
    val avatarUrl: String? = null,
    val spaceId: String,
    val spaceDisplayName: String? = null,
)

data class AuthErrorDto(
    val code: String,
    val message: String,
)
