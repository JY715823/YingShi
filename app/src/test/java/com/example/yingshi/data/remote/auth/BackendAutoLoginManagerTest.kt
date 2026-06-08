package com.example.yingshi.data.remote.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendAutoLoginManagerTest {

    @Test
    fun localAndPrivateBaseUrlsCanUseAutoLogin() {
        assertTrue(BackendAutoLoginManager.shouldAllowAutoLoginForBaseUrl("http://127.0.0.1:8080/"))
        assertTrue(BackendAutoLoginManager.shouldAllowAutoLoginForBaseUrl("http://10.0.2.2:8080/"))
        assertTrue(BackendAutoLoginManager.shouldAllowAutoLoginForBaseUrl("http://192.168.1.20:8080/"))
        assertTrue(BackendAutoLoginManager.shouldAllowAutoLoginForBaseUrl("http://10.106.3.193:8080/"))
    }

    @Test
    fun publicBaseUrlsRequireManualLogin() {
        assertFalse(BackendAutoLoginManager.shouldAllowAutoLoginForBaseUrl("https://api.example.com/"))
        assertFalse(BackendAutoLoginManager.shouldAllowAutoLoginForBaseUrl("https://demo.yingshi.app/"))
    }
}
