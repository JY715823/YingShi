package com.example.yingshi.data.remote.config

object RemoteConfig {
    const val DEBUG_EMULATOR_BASE_URL = "http://10.0.2.2:8080/"
    const val DEBUG_DEVICE_LOOPBACK_BASE_URL = "http://127.0.0.1:8080/"
    const val HTTPS_EXAMPLE_BASE_URL = "https://api.example.com/"
    const val RELEASE_BASE_URL = "https://release-api-url-not-configured.invalid/"
    const val AUTH_SCHEME = "Bearer"
    const val NO_AUTH_HEADER = "X-No-Auth"
}
