package com.example.yingshi.data.repository

import com.example.yingshi.data.model.AuthTokens
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.model.RemoteLoginSession
import com.example.yingshi.data.remote.auth.AuthSessionManager

private const val DEFAULT_FAKE_ACCOUNT = "fake@yingshi.local"
private const val DEMO_A_ACCOUNT = "demo.a@yingshi.local"
private const val DEMO_B_ACCOUNT = "demo.b@yingshi.local"
private const val FAKE_LIBRARY_ID = "fake-library-001"
private const val FAKE_LIBRARY_NAME = "\u6620\u4e16\u672c\u5730\u5360\u4f4d\u7a7a\u95f4"
private const val FAKE_BIO_DEFAULT = "\u672c\u5730\u6f14\u793a\u7b80\u4ecb\uff0c\u53ef\u7f16\u8f91\u5e76\u5728\u5f53\u524d\u4f1a\u8bdd\u5185\u4fdd\u7559\u3002"
private const val FAKE_DISPLAY_NAME_DEFAULT = "\u672c\u5730\u5360\u4f4d\u8d26\u53f7"
private const val FAKE_DYNAMIC_BIO = "FAKE \u6a21\u5f0f\u4e2a\u4eba\u8d44\u6599\u5360\u4f4d\u5185\u5bb9\u3002"

private val profilesByAccount: MutableMap<String, RemoteCurrentUser> = linkedMapOf(
    DEMO_A_ACCOUNT to RemoteCurrentUser(
        userId = "fake-user-001",
        account = DEMO_A_ACCOUNT,
        displayName = "Demo A",
        avatarUrl = null,
        libraryId = FAKE_LIBRARY_ID,
        libraryDisplayName = FAKE_LIBRARY_NAME,
        bio = FAKE_BIO_DEFAULT,
        createdAtMillis = 1760000000000L,
        updatedAtMillis = 1760000000000L,
    ),
    DEMO_B_ACCOUNT to RemoteCurrentUser(
        userId = "fake-user-002",
        account = DEMO_B_ACCOUNT,
        displayName = "Demo B",
        avatarUrl = null,
        libraryId = FAKE_LIBRARY_ID,
        libraryDisplayName = FAKE_LIBRARY_NAME,
        bio = FAKE_BIO_DEFAULT,
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
        createdAtMillis = now - 86_400_000L,
        updatedAtMillis = now,
    )
}
