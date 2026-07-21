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
    IMPORTED("已导入"),
    UNIMPORTED("未导入"),
}

@Immutable
data class SystemMediaAlbum(
    val bucketName: String?,
    val displayName: String,
    val mediaCount: Int,
    val coverUri: Uri?,
    val coverMediaType: SystemMediaType,
)

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
    val capturedAtMillis: Long? = null,
    val fileModifiedAtMillis: Long? = null,
    val displayTimeSource: String = DisplayTimeSourceImported,
    val displayYear: Int,
    val displayMonth: Int,
    val displayDay: Int,
    val width: Int?,
    val height: Int?,
    val aspectRatio: Float,
    val palette: PhotoThumbnailPalette,
    val importedAppMediaId: String? = null,
    val linkedSmallAlbumIds: List<String> = emptyList(),
    val linkedPostIds: List<String> = linkedSmallAlbumIds,
    val videoDurationMillis: Long? = null,
    val uploadedByUserId: String? = null,
    val sizeBytes: Long? = null,
) {
    val isImportedToApp: Boolean
        get() = !importedAppMediaId.isNullOrBlank()
}

@Immutable
data class SystemMediaRoute(
    val source: String = "photos-top-bar",
)

@Immutable
data class TransferCenterRoute(
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
    val isRefreshing: Boolean = false,
    val isBackgroundRefreshing: Boolean = false,
    val selectedFilter: SystemMediaFilter = SystemMediaFilter.ALL,
    val selectedAlbum: SystemMediaAlbum? = null,
    val albums: List<SystemMediaAlbum> = emptyList(),
    val allItems: List<SystemMediaItem> = emptyList(),
    val filteredItems: List<SystemMediaItem> = emptyList(),
    val errorMessage: String? = null,
) {
    val hasError: Boolean
        get() = !errorMessage.isNullOrBlank()
}
