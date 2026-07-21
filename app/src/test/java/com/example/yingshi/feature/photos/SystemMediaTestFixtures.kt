package com.example.yingshi.feature.photos

import android.net.UriTestDouble
import androidx.compose.ui.graphics.Color

/**
 * 测试专用 SystemMediaItem 工厂。
 *
 * 所有参数都有默认值，调用方按需覆盖。
 * 默认构造一个 2026-06-07 拍摄的 Camera 相册图片。
 */
internal fun sampleSystemMediaItem(
    id: String,
    mediaStoreId: Long,
    type: SystemMediaType = SystemMediaType.IMAGE,
    mimeType: String = if (type == SystemMediaType.VIDEO) "video/mp4" else "image/jpeg",
    displayName: String = if (type == SystemMediaType.VIDEO) "$id.mp4" else "$id.jpg",
    bucketName: String? = "Camera",
    capturedAtMillis: Long? = 1_780_600_000_000L,
    fileModifiedAtMillis: Long? = 1_780_601_000_000L,
    importedAppMediaId: String? = null,
    width: Int = 1080,
    height: Int = 1440,
    videoDurationMillis: Long? = if (type == SystemMediaType.VIDEO) 5_000L else null,
    sizeBytes: Long? = 2_048_000L,
): SystemMediaItem {
    val displayTimeMillis = capturedAtMillis ?: fileModifiedAtMillis ?: 1_780_800_000_000L
    return SystemMediaItem(
        id = id,
        mediaStoreId = mediaStoreId,
        uri = UriTestDouble("content://media/external/${if (type == SystemMediaType.VIDEO) "video" else "images"}/media/$mediaStoreId"),
        type = type,
        mimeType = mimeType,
        displayName = displayName,
        bucketName = bucketName,
        displayTimeMillis = displayTimeMillis,
        capturedAtMillis = capturedAtMillis,
        fileModifiedAtMillis = fileModifiedAtMillis,
        displayTimeSource = capturedAtMillis?.let { DisplayTimeSourceOriginal } ?: DisplayTimeSourceImported,
        displayYear = 2026,
        displayMonth = 6,
        displayDay = 7,
        width = width,
        height = height,
        aspectRatio = if (height > 0) width.toFloat() / height.toFloat() else 0.75f,
        palette = PhotoThumbnailPalette(
            start = Color(0xFF112233),
            end = Color(0xFF223344),
            accent = Color(0xFF335577),
        ),
        importedAppMediaId = importedAppMediaId,
        linkedPostIds = emptyList(),
        videoDurationMillis = videoDurationMillis,
        sizeBytes = sizeBytes,
    )
}

/**
 * 构造一个 SystemMediaAlbum 测试实例。
 */
internal fun sampleSystemMediaAlbum(
    bucketName: String? = "Camera",
    displayName: String = bucketName ?: "未分组",
    mediaCount: Int = 1,
): SystemMediaAlbum {
    return SystemMediaAlbum(
        bucketName = bucketName,
        displayName = displayName,
        mediaCount = mediaCount,
        coverUri = null,
        coverMediaType = SystemMediaType.IMAGE,
    )
}
