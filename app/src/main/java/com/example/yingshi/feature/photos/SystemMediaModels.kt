package com.example.yingshi.feature.photos

import androidx.compose.runtime.Immutable

enum class SystemMediaPermissionState(
    val label: String,
    val description: String,
) {
    UNAUTHORIZED(
        label = "未授权",
        description = "当前只展示权限引导壳子。后续接真实权限后，这里会控制系统媒体读取范围。",
    ),
    PARTIAL(
        label = "部分授权",
        description = "用于占位 Android 的部分照片授权场景，先用 fake 数据模拟可见范围。",
    ),
    AUTHORIZED(
        label = "已授权",
        description = "当前以本地 fake system media 数据占位，后续再接真实 MediaStore。",
    ),
}

enum class SystemMediaFilter(
    val label: String,
) {
    ALL("全部"),
    CAMERA("相机"),
    SCREENSHOT("截图"),
    VIDEO("视频"),
    POSTED("已发帖"),
    UNPOSTED("未发帖"),
}

enum class SystemMediaKind(
    val label: String,
) {
    CAMERA("相机"),
    SCREENSHOT("截图"),
    VIDEO("视频"),
}

@Immutable
data class SystemMediaItem(
    val id: String,
    val displayTimeMillis: Long,
    val displayYear: Int,
    val displayMonth: Int,
    val displayDay: Int,
    val palette: PhotoThumbnailPalette,
    val aspectRatio: Float,
    val kind: SystemMediaKind,
    val linkedPostIds: List<String>,
)

@Immutable
data class SystemMediaRoute(
    val source: String = "photos-top-bar",
)

@Immutable
data class SystemMediaViewerRoute(
    val mediaItems: List<SystemMediaItem>,
    val initialIndex: Int,
)
