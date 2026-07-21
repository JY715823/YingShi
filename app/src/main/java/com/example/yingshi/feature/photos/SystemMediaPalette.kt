package com.example.yingshi.feature.photos

import androidx.compose.ui.graphics.Color

internal val systemMediaPalettes: List<PhotoThumbnailPalette> = listOf(
    PhotoThumbnailPalette(start = Color(0xFFB8D8F8), end = Color(0xFF7EA6DF), accent = Color(0xFFE8F2FF)),
    PhotoThumbnailPalette(start = Color(0xFFF5D2C3), end = Color(0xFFE7A08D), accent = Color(0xFFFFF0E8)),
    PhotoThumbnailPalette(start = Color(0xFFCFE5B9), end = Color(0xFF84B38A), accent = Color(0xFFEFF8E1)),
    PhotoThumbnailPalette(start = Color(0xFFD8D0F2), end = Color(0xFF8FA0D8), accent = Color(0xFFF0EDFF)),
    PhotoThumbnailPalette(start = Color(0xFFE7CFB4), end = Color(0xFFB98B63), accent = Color(0xFFF7E8D4)),
    PhotoThumbnailPalette(start = Color(0xFFC5D1DA), end = Color(0xFF8095A7), accent = Color(0xFFE7F0F6)),
)

internal fun paletteForSystemMediaId(mediaStoreId: Long): PhotoThumbnailPalette {
    return systemMediaPalettes[(mediaStoreId % systemMediaPalettes.size).toInt()]
}

internal fun paletteForPickedMedia(index: Int, type: SystemMediaType): PhotoThumbnailPalette {
    val videoOffset = if (type == SystemMediaType.VIDEO) 1 else 0
    return systemMediaPalettes[(index + videoOffset) % systemMediaPalettes.size]
}
