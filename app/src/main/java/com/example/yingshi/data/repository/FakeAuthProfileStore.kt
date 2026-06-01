package com.example.yingshi.data.repository

import com.example.yingshi.data.model.AuthTokens
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.model.RemoteLoginSession
import com.example.yingshi.data.model.RemotePartnerProfile
import com.example.yingshi.data.remote.auth.AuthSessionManager

private const val DEFAULT_FAKE_ACCOUNT = "fake@yingshi.local"
private const val DEMO_A_ACCOUNT = "demo.a@yingshi.local"
private const val DEMO_B_ACCOUNT = "demo.b@yingshi.local"
private const val FAKE_LIBRARY_ID = "fake-library-001"
private const val FAKE_LIBRARY_NAME = "\u6211\u4eec\u7684\u5c0f\u7a7a\u95f4"
private const val FAKE_BIO_DEFAULT = "\u672c\u5730\u6f14\u793a\u8d44\u6599\uff0c\u4e24\u4e2a\u4eba\u9ed8\u8ba4\u5171\u7528\u540c\u4e00\u5ea7\u5c0f\u5c0f\u76f8\u518c\u3002"
private const val FAKE_DISPLAY_NAME_DEFAULT = "\u672c\u5730\u5360\u4f4d\u8d26\u53f7"
private const val FAKE_DYNAMIC_BIO = "FAKE \u6a21\u5f0f\u4e2a\u4eba\u8d44\u6599\u5360\u4f4d\u5185\u5bb9\u3002"

private val profilesByAccount: MutableMap<String, RemoteCurrentUser> = linkedMapOf(
    DEMO_A_ACCOUNT to RemoteCurrentUser(
        userId = "fake-user-001",
        account = DEMO_A_ACCOUNT,
        displayName = "\u6620\u4e16\u5c0f\u5c4b",
        avatarUrl = null,
        libraryId = FAKE_LIBRARY_ID,
        libraryDisplayName = FAKE_LIBRARY_NAME,
        bio = "\u4e00\u8d77\u628a\u5e73\u5e38\u65e5\u5b50\u6162\u6162\u6536\u8fdb\u8fd9\u5ea7\u5c0f\u5c0f\u76f8\u518c\u3002",
        partner = RemotePartnerProfile(
            userId = "fake-user-002",
            account = DEMO_B_ACCOUNT,
            displayName = "\u53e6\u4e00\u534a",
            avatarUrl = null,
            bio = "\u628a\u751f\u6d3b\u91cc\u7684\u95ea\u5149\u7247\u6bb5\uff0c\u4e5f\u628a\u5b89\u9759\u548c\u60f3\u5ff5\u4e00\u8d77\u7559\u4e0b\u6765\u3002",
        ),
        createdAtMillis = 1760000000000L,
        updatedAtMillis = 1760000000000L,
    ),
    DEMO_B_ACCOUNT to RemoteCurrentUser(
        userId = "fake-user-002",
        account = DEMO_B_ACCOUNT,
        displayName = "\u53e6\u4e00\u534a",
        avatarUrl = null,
        libraryId = FAKE_LIBRARY_ID,
        libraryDisplayName = FAKE_LIBRARY_NAME,
        bio = "\u628a\u751f\u6d3b\u91cc\u7684\u95ea\u5149\u7247\u6bb5\uff0c\u4e5f\u628a\u5b89\u9759\u548c\u60f3\u5ff5\u4e00\u8d77\u7559\u4e0b\u6765\u3002",
        partner = RemotePartnerProfile(
            userId = "fake-user-001",
            account = DEMO_A_ACCOUNT,
            displayName = "\u6620\u4e16\u5c0f\u5c4b",
            avatarUrl = null,
            bio = "\u4e00\u8d77\u628a\u5e73\u5e38\u65e5\u5b50\u6162\u6162\u6536\u8fdb\u8fd9\u5ea7\u5c0f\u5c0f\u76f8\u518c\u3002",
        ),
        createdAtMillis = 1760000000000L,
        updatedAtMillis = 1760000000000L,
    ),
)

private var activeAccount: String? = null

@Synchronized
fun fakeAuthLoginProfile(account: String): RemoteCurrentUser {
    val normalizedAccount = account.ifBlank { DEFAULT_FAKE_ACCOUNT }
    val profile = profilesByAccount.getOrPut(normalizedAccount) {
        createProfile(
            userId = "fake-user-${normalizedAccount.hashCode().toString(16)}",
            account = normalizedAccount,
            displayName = normalizedAccount.substringBefore('@').ifBlank { FAKE_DISPLAY_NAME_DEFAULT },
            bio = FAKE_DYNAMIC_BIO,
        )
    }
    activeAccount = profile.account
    return profile
}

@Synchronized
fun fakeAuthCurrentProfile(): RemoteCurrentUser? {
    val account = activeAccount ?: if (AuthSessionManager.isLoggedIn) DEFAULT_FAKE_ACCOUNT else return null
    return profilesByAccount[account] ?: fakeAuthLoginProfile(account)
}

@Synchronized
fun fakeAuthUpdateProfile(displayName: String, bio: String?): RemoteCurrentUser? {
    if (!AuthSessionManager.isLoggedIn) {
        return null
    }
    val account = activeAccount ?: DEFAULT_FAKE_ACCOUNT
    val currentProfile = profilesByAccount[account] ?: fakeAuthLoginProfile(account)
    val updatedProfile = currentProfile.copy(
        displayName = displayName.trim().ifBlank { currentProfile.displayName },
        bio = bio?.trim()?.takeIf { it.isNotBlank() },
        updatedAtMillis = System.currentTimeMillis(),
    )
    profilesByAccount[account] = updatedProfile
    activeAccount = account
    return updatedProfile
}

@Synchronized
fun fakeAuthUpdateAvatar(avatarUrl: String?): RemoteCurrentUser? {
    if (!AuthSessionManager.isLoggedIn) {
        return null
    }
    val account = activeAccount ?: DEFAULT_FAKE_ACCOUNT
    val currentProfile = profilesByAccount[account] ?: fakeAuthLoginProfile(account)
    val updatedProfile = currentProfile.copy(
        avatarUrl = avatarUrl?.trim()?.takeIf { it.isNotBlank() },
        updatedAtMillis = System.currentTimeMillis(),
    )
    profilesByAccount[account] = updatedProfile
    activeAccount = account
    return updatedProfile
}

@Synchronized
fun fakeAuthLogout() {
    activeAccount = null
}

fun RemoteCurrentUser.toFakeLoginSession(): RemoteLoginSession {
    return RemoteLoginSession(
        userId = userId,
        account = account,
        displayName = displayName,
        avatarUrl = avatarUrl,
        bio = bio,
        libraryId = libraryId,
        libraryDisplayName = libraryDisplayName,
        partner = partner,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        tokens = AuthTokens(
            accessToken = "fake-access-token",
            refreshToken = "fake-refresh-token",
            accessTokenExpireAtMillis = System.currentTimeMillis() + 60 * 60 * 1000L,
            refreshTokenExpireAtMillis = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L,
        ),
    )
}

private fun createProfile(
    userId: String,
    account: String,
    displayName: String,
    bio: String,
): RemoteCurrentUser {
    val now = System.currentTimeMillis()
    return RemoteCurrentUser(
        userId = userId,
        account = account,
        displayName = displayName,
        avatarUrl = null,
        libraryId = FAKE_LIBRARY_ID,
        libraryDisplayName = FAKE_LIBRARY_NAME,
        bio = bio,
        partner = null,
        createdAtMillis = now - 86_400_000L,
        updatedAtMillis = now,
    )
}
