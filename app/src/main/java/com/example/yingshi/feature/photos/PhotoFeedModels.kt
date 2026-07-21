package com.example.yingshi.feature.photos

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class PhotoThumbnailPalette(
    val start: Color,
    val end: Color,
    val accent: Color,
)

enum class AppMediaType {
    IMAGE,
    VIDEO,
}

@Immutable
data class PhotoFeedSourceEntry(
    val mediaId: String,
    val mediaDisplayTimeMillis: Long,
    val smallAlbumId: String?,
    val uploadedByUserId: String? = null,
    val palette: PhotoThumbnailPalette,
    val mediaType: AppMediaType = AppMediaType.IMAGE,
    val aspectRatio: Float = 1f,
    val width: Int? = null,
    val height: Int? = null,
    val videoDurationMillis: Long? = null,
    val capturedAtMillis: Long? = mediaDisplayTimeMillis,
    val importedAtMillis: Long? = null,
    val displayTimeSource: String? = DisplayTimeSourceOriginal,
    val mediaSource: AppContentMediaSource? = null,
) {
    val postId: String?
        get() = smallAlbumId
}

@Immutable
data class PhotoFeedItem(
    val mediaId: String,
    val mediaDisplayTimeMillis: Long,
    val displayYear: Int,
    val displayMonth: Int,
    val displayDay: Int,
    val commentCount: Int,
    val smallAlbumIds: List<String>,
    val uploadedByUserId: String? = null,
    val palette: PhotoThumbnailPalette,
    val mediaType: AppMediaType = AppMediaType.IMAGE,
    val aspectRatio: Float = 1f,
    val width: Int? = null,
    val height: Int? = null,
    val videoDurationMillis: Long? = null,
    val capturedAtMillis: Long? = null,
    val importedAtMillis: Long? = null,
    val displayTimeSource: String? = null,
    val mediaSource: AppContentMediaSource? = null,
    // Round 8 第十五轮: 拍摄地点 (来自 EXIF GPS, 服务端高德逆地理编码生成)
    val locationLabel: String? = null,
    // Round 8 第十六轮: 地点经纬度 (GCJ-02), 用于点击地点胶囊跳地图页修改
    val locationLat: Double? = null,
    val locationLng: Double? = null,
) {
    val postIds: List<String>
        get() = smallAlbumIds
}

enum class PhotoFeedDensity(
    val columns: Int,
    val label: String,
) {
    COMFORT_2(columns = 2, label = "2列"),
    COMFORT_3(columns = 3, label = "3列"),
    DENSE_4(columns = 4, label = "4列"),
    OVERVIEW_8(columns = 8, label = "8列"),
    OVERVIEW_16(columns = 16, label = "16列"),
}

enum class PhotoFeedPresentation {
    MAIN_STREAM,
    EMBEDDED,
}

enum class PhotoFeedTimeGranularity {
    YEAR,
    MONTH,
    DAY,
}

sealed interface PhotoFeedBlock {
    val key: String
}

data class PhotoFeedSectionHeader(
    override val key: String,
    val title: String,
    val granularity: PhotoFeedTimeGranularity,
    val year: Int? = null,
    val month: Int? = null,
    val anchorTimeMillis: Long? = null,
) : PhotoFeedBlock

data class PhotoFeedDayHeader(
    override val key: String,
    val title: String,
    val year: Int,
    val month: Int,
    val day: Int,
    val scrubberLabel: String = title,
    val anchorTimeMillis: Long? = null,
) : PhotoFeedBlock

data class PhotoFeedGridRow(
    override val key: String,
    val items: List<PhotoFeedItem>,
) : PhotoFeedBlock

data class PhotoFeedTimeBucketHeader(
    override val key: String,
    val title: String,
    val scrubberLabel: String,
    val bucketHours: Int,
    val anchorTimeMillis: Long,
    val currentCount: Int = 0,
    val partnerCount: Int = 0,
) : PhotoFeedBlock

data class PhotoFeedCollaboratorHeader(
    override val key: String,
    val identity: CollaboratorIdentityUiModel,
) : PhotoFeedBlock

data class PhotoFeedCollaboratorDivider(
    override val key: String,
) : PhotoFeedBlock
