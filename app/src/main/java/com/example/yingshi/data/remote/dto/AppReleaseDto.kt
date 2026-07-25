package com.example.yingshi.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * App 版本检查响应 DTO。
 *
 * 服务端 [AppReleaseCheckResponse] 的客户端镜像。
 *
 * @param hasUpdate 是否有可用更新
 * @param latestVersionCode 服务端最新 versionCode
 * @param latestVersionName 服务端最新 versionName
 * @param downloadUrl APK 下载地址（可能是相对路径如 /download/x.apk，由调用方拼接 baseUrl）
 * @param updateDescription 更新说明
 * @param forceUpdate 是否强制更新
 */
data class AppReleaseCheckDto(
    @SerializedName("hasUpdate") val hasUpdate: Boolean = false,
    @SerializedName("latestVersionCode") val latestVersionCode: Int? = null,
    @SerializedName("latestVersionName") val latestVersionName: String? = null,
    @SerializedName("downloadUrl") val downloadUrl: String? = null,
    @SerializedName("updateDescription") val updateDescription: String? = null,
    @SerializedName("forceUpdate") val forceUpdate: Boolean = false,
)
