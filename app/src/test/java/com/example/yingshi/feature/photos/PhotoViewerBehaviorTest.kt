package com.example.yingshi.feature.photos

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoViewerBehaviorTest {

    @Test
    fun longImageReadingUsesDimensionsThreshold() {
        val item = samplePhotoFeedItem(
            width = 1000,
            height = 2200,
            aspectRatio = 1000f / 2200f,
        )

        assertTrue(item.shouldUseLongImageReading())
    }

    @Test
    fun longImageReadingFallsBackToAspectRatio() {
        val item = samplePhotoFeedItem(
            width = null,
            height = null,
            aspectRatio = 0.4f,
        )

        assertTrue(item.shouldUseLongImageReading())
    }

    @Test
    fun regularPortraitImageDoesNotUseLongImageReading() {
        val item = samplePhotoFeedItem(
            width = 1000,
            height = 1800,
            aspectRatio = 1000f / 1800f,
        )

        assertFalse(item.shouldUseLongImageReading())
    }

    @Test
    fun displayTimeLabelIncludesSource() {
        val timeMillis = 1_780_817_400_000L
        val label = buildMediaDisplayTimeLabel(
            timeMillis = timeMillis,
            source = DisplayTimeSourceFileModified,
        )

        assertEquals("文件时间", label.substringBefore(" · "))
        assertEquals(formatMediaDisplayTime(timeMillis), label.substringAfter(" · "))
    }

    private fun samplePhotoFeedItem(
        width: Int?,
        height: Int?,
        aspectRatio: Float,
    ): PhotoFeedItem {
        return PhotoFeedItem(
            mediaId = "media-sample",
            mediaDisplayTimeMillis = 1_780_800_000_000L,
            displayYear = 2026,
            displayMonth = 6,
            displayDay = 7,
            commentCount = 0,
            smallAlbumIds = emptyList(),
            palette = PhotoThumbnailPalette(
                start = Color(0xFF112233),
                end = Color(0xFF223344),
                accent = Color(0xFF335577),
            ),
            mediaType = AppMediaType.IMAGE,
            aspectRatio = aspectRatio,
            width = width,
            height = height,
        )
    }
}
