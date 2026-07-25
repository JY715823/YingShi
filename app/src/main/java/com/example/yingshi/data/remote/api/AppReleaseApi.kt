package com.example.yingshi.data.remote.api

import com.example.yingshi.data.remote.dto.ApiEnvelopeDto
import com.example.yingshi.data.remote.dto.AppReleaseCheckDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * App 版本检查接口。
 *
 * 该接口无需登录（服务端 AppReleaseController 未加 @AuthRequired），
 * 因此 App 启动时即可调用。
 */
interface AppReleaseApi {

    @GET("api/app/release/check")
    suspend fun checkForUpdate(
        @Query("platform") platform: String = "android",
        @Query("versionCode") versionCode: Int,
    ): ApiEnvelopeDto<AppReleaseCheckDto>
}
