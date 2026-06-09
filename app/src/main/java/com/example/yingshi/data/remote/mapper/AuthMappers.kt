package com.example.yingshi.data.remote.mapper

import com.example.yingshi.data.model.AuthTokens
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.model.RemoteLoginChallenge
import com.example.yingshi.data.model.RemoteLoginSession
import com.example.yingshi.data.model.RemotePartnerProfile
import com.example.yingshi.data.remote.dto.CurrentUserDto
import com.example.yingshi.data.remote.dto.LoginChallengeResponseDto
import com.example.yingshi.data.remote.dto.LoginResponseDto
import com.example.yingshi.data.remote.dto.PartnerProfileDto
import com.example.yingshi.data.remote.dto.RefreshTokenResponseDto

fun LoginChallengeResponseDto.toRemoteModel(): RemoteLoginChallenge {
    return RemoteLoginChallenge(
        challengeId = challengeId,
        maskedEmail = maskedEmail,
        expireAtMillis = expireAtMillis,
        resendAvailableAtMillis = resendAvailableAtMillis,
    )
}

fun LoginResponseDto.toRemoteModel(): RemoteLoginSession {
    return RemoteLoginSession(
        userId = userId,
        account = account,
        displayName = displayName,
        avatarUrl = avatarUrl,
        bio = bio,
        libraryId = libraryId,
        libraryDisplayName = libraryDisplayName,
        partner = partner?.toRemoteModel(),
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        rememberedLoginToken = rememberedLoginToken,
        rememberedLoginExpireAtMillis = rememberedLoginExpireAtMillis,
        tokens = AuthTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpireAtMillis = accessTokenExpireAtMillis,
            refreshTokenExpireAtMillis = refreshTokenExpireAtMillis,
        ),
    )
}

fun RefreshTokenResponseDto.toRemoteModel(): AuthTokens {
    return AuthTokens(
        accessToken = accessToken,
        refreshToken = refreshToken,
        accessTokenExpireAtMillis = accessTokenExpireAtMillis,
        refreshTokenExpireAtMillis = refreshTokenExpireAtMillis,
    )
}

fun CurrentUserDto.toRemoteModel(): RemoteCurrentUser {
    return RemoteCurrentUser(
        userId = userId,
        account = account,
        displayName = displayName,
        avatarUrl = avatarUrl,
        bio = bio,
        libraryId = libraryId,
        libraryDisplayName = libraryDisplayName,
        partner = partner?.toRemoteModel(),
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )
}

fun PartnerProfileDto.toRemoteModel(): RemotePartnerProfile {
    return RemotePartnerProfile(
        userId = userId,
        account = account,
        displayName = displayName,
        avatarUrl = avatarUrl,
        bio = bio,
    )
}
