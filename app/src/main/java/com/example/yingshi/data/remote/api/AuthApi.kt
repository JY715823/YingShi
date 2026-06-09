package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.config.RemoteConfig
import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.CurrentUserDto
import com.example.yingshi.data.remote.dto.LoginChallengeResponseDto
import com.example.yingshi.data.remote.dto.LoginRequestDto
import com.example.yingshi.data.remote.dto.LoginResponseDto
import com.example.yingshi.data.remote.dto.LogoutRequestDto
import com.example.yingshi.data.remote.dto.LogoutResponseDto
import com.example.yingshi.data.remote.dto.RememberedLoginRequestDto
import com.example.yingshi.data.remote.dto.RefreshTokenRequestDto
import com.example.yingshi.data.remote.dto.RefreshTokenResponseDto
import com.example.yingshi.data.remote.dto.ResendLoginChallengeRequestDto
import com.example.yingshi.data.remote.dto.UpdateProfileRequestDto
import com.example.yingshi.data.remote.dto.VerifyLoginChallengeRequestDto
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part

interface AuthApi {
    @Headers("${RemoteConfig.NO_AUTH_HEADER}: true")
    @POST("api/auth/login/challenge")
    suspend fun requestLoginChallenge(
        @Body request: LoginRequestDto,
    ): ApiEnvelopeDto<LoginChallengeResponseDto>

    @Headers("${RemoteConfig.NO_AUTH_HEADER}: true")
    @POST("api/auth/login/challenge/resend")
    suspend fun resendLoginChallenge(
        @Body request: ResendLoginChallengeRequestDto,
    ): ApiEnvelopeDto<LoginChallengeResponseDto>

    @Headers("${RemoteConfig.NO_AUTH_HEADER}: true")
    @POST("api/auth/login/verify")
    suspend fun verifyLoginChallenge(
        @Body request: VerifyLoginChallengeRequestDto,
    ): ApiEnvelopeDto<LoginResponseDto>

    @Headers("${RemoteConfig.NO_AUTH_HEADER}: true")
    @POST("api/auth/login/remembered")
    suspend fun loginWithRememberedDevice(
        @Body request: RememberedLoginRequestDto,
    ): ApiEnvelopeDto<LoginResponseDto>

    @Headers("${RemoteConfig.NO_AUTH_HEADER}: true")
    @POST("api/auth/refresh-token")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequestDto,
    ): ApiEnvelopeDto<RefreshTokenResponseDto>

    @POST("api/auth/logout")
    suspend fun logout(
        @Body request: LogoutRequestDto,
    ): ApiEnvelopeDto<LogoutResponseDto>

    @GET("api/auth/me")
    suspend fun getCurrentUser(): ApiEnvelopeDto<CurrentUserDto>

    @PATCH("api/auth/me/profile")
    suspend fun updateCurrentUserProfile(
        @Body request: UpdateProfileRequestDto,
    ): ApiEnvelopeDto<CurrentUserDto>

    @Multipart
    @POST("api/auth/me/avatar")
    suspend fun uploadCurrentUserAvatar(
        @Part file: MultipartBody.Part,
    ): ApiEnvelopeDto<CurrentUserDto>
}
