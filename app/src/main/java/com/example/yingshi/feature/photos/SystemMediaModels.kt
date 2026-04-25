package com.example.yingshi.feature.photos

import android.net.Uri
import androidx.compose.runtime.Immutable

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

enum class SystemMediaType(
    val label: String,
) {
    IMAGE("图片"),
    VIDEO("视频"),
}

@Immutable
data class SystemMediaItem(
    val id: String,
    val mediaStoreId: Long,
    val uri: Uri,
    val type: SystemMediaType,
    val mimeType: String,
    val displayName: String,
    val bucketName: String?,
    val displayTimeMillis: Long,
    val displayYear: Int,
    val displayMonth: Int,
    val displayDay: Int,
    val width: Int?,
    val height: Int?,
    val aspectRatio: Float,
    val palette: PhotoThumbnailPalette,
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

@Immutable
data class SystemMediaUiState(
    val isLoading: Boolean = true,
    val selectedFilter: SystemMediaFilter = SystemMediaFilter.ALL,
    val allItems: List<SystemMediaItem> = emptyList(),
    val filteredItems: List<SystemMediaItem> = emptyList(),
    val errorMessage: String? = null,
) {
    val hasError: Boolean
        get() = !errorMessage.isNullOrBlank()
}
