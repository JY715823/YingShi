package com.example.yingshi.data.remote.auth

import com.example.yingshi.data.remote.config.RemoteConfig
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val tokenProvider: TokenProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val skipAuth = original.header(RemoteConfig.NO_AUTH_HEADER) == "true"
        val builder = original.newBuilder()
            .removeHeader(RemoteConfig.NO_AUTH_HEADER)

        if (!skipAuth) {
            tokenProvider.getAccessToken()?.takeIf { it.isNotBlank() }?.let { accessToken ->
                builder.header(
                    "Authorization",
                    "${RemoteConfig.AUTH_SCHEME} $accessToken",
                )
            }
        }

        return chain.proceed(builder.build())
    }
}
