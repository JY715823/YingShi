package com.example.yingshi.feature.photos

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class PhotoThumbnailPalette(
    val start: Color,
    val end: Color,
    val accent: Color,
)

@Immutable
data class PhotoFeedSourceEntry(
    val mediaId: String,
    val mediaDisplayTimeMillis: Long,
    val postId: String?,
    val palette: PhotoThumbnailPalette,
    val aspectRatio: Float = 1f,
)

@Immutable
data class PhotoFeedItem(
    val mediaId: String,
    val mediaDisplayTimeMillis: Long,
    val displayYear: Int,
    val displayMonth: Int,
    val displayDay: Int,
    val commentCount: Int,
    val postIds: List<String>,
    val palette: PhotoThumbnailPalette,
    val aspectRatio: Float = 1f,
)

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

sealed interface PhotoFeedBlock {
    val key: String
}

data class PhotoFeedSectionHeader(
    override val key: String,
    val title: String,
) : PhotoFeedBlock

data class PhotoFeedDayHeader(
    override val key: String,
    val title: String,
) : PhotoFeedBlock

data class PhotoFeedGridRow(
    override val key: String,
    val items: List<PhotoFeedItem>,
) : PhotoFeedBlock
