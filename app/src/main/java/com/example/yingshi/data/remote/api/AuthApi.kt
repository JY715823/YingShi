package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.config.RemoteConfig
import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.CurrentUserDto
import com.example.yingshi.data.remote.dto.LoginRequestDto
import com.example.yingshi.data.remote.dto.LoginResponseDto
import com.example.yingshi.data.remote.dto.LogoutRequestDto
import com.example.yingshi.data.remote.dto.LogoutResponseDto
import com.example.yingshi.data.remote.dto.RefreshTokenRequestDto
import com.example.yingshi.data.remote.dto.RefreshTokenResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

interface AuthApi {
    @Headers("${RemoteConfig.NO_AUTH_HEADER}: true")
    @POST("v1/auth/login")
    suspend fun login(
        @Body request: LoginRequestDto,
    ): ApiEnvelopeDto<LoginResponseDto>

    @Headers("${RemoteConfig.NO_AUTH_HEADER}: true")
    @POST("v1/auth/refresh-token")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequestDto,
    ): ApiEnvelopeDto<RefreshTokenResponseDto>

    @POST("v1/auth/logout")
    suspend fun logout(
        @Body request: LogoutRequestDto,
    ): ApiEnvelopeDto<LogoutResponseDto>

    @GET("v1/auth/me")
    suspend fun getCurrentUser(): ApiEnvelopeDto<CurrentUserDto>
}
