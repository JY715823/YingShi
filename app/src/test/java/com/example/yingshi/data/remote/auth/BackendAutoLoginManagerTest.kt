package com.example.yingshi.data.remote.auth

import com.example.yingshi.data.cache.OfflineAccessManager
import com.example.yingshi.data.model.AuthTokens
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackendAutoLoginManagerTest {

    @Before
    fun resetSessionState() {
        AuthSessionManager.clearTokensPreservingReadCache()
        OfflineAccessManager.clear()
        BackendAutoLoginManager.markLoggedOut("reset")
    }

    @Test
    fun noSessionRequiresManualEmailVerification() = runBlocking {
        val outcome = BackendAutoLoginManager.loginDefault(
            force = false,
            reason = "manual_retry",
        )

        assertFalse(outcome.success)
        assertTrue(outcome.message.contains("邮箱验证"))
        assertEquals(BackendAutoLoginPhase.Failed, BackendAutoLoginManager.uiState.value.phase)
    }

    @Test
    fun saveBaseUrlReasonUsesReverifyMessage() = runBlocking {
        val outcome = BackendAutoLoginManager.loginDefault(
            force = true,
            reason = "save_base_url",
        )

        assertFalse(outcome.success)
        assertTrue(outcome.message.contains("服务地址已更新"))
        assertTrue(outcome.message.contains("邮箱验证"))
    }

    @Test
    fun existingSessionReturnsSuccessWithoutCredentialReplay() = runBlocking {
        AuthSessionManager.saveTokens(
            AuthTokens(
                accessToken = "access-token",
                refreshToken = "refresh-token",
                accessTokenExpireAtMillis = System.currentTimeMillis() + 60_000L,
                refreshTokenExpireAtMillis = System.currentTimeMillis() + 10 * 60_000L,
            ),
        )

        val outcome = BackendAutoLoginManager.loginDefault(
            force = true,
            reason = "manual_retry",
        )

        assertTrue(outcome.success)
        assertTrue(outcome.message.contains("当前登录会话仍有效"))
        assertEquals(BackendAutoLoginPhase.Success, BackendAutoLoginManager.uiState.value.phase)
    }
}
