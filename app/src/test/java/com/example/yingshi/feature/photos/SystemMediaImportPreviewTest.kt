package com.example.yingshi.feature.photos

import android.net.Uri
import android.net.UriTestDouble
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemMediaImportPreviewTest {

    @Test
    fun alreadyImportedItemsAreReportedAsDuplicates() {
        val existing = sampleSystemMediaItem(
            id = "image-101",
            mediaStoreId = 101L,
        )
        val fresh = sampleSystemMediaItem(
            id = "image-202",
            mediaStoreId = 202L,
        )

        val preview = buildSystemMediaImportPreview(
            mediaItems = listOf(existing, fresh),
            preference = MediaTimePreference.CAPTURED_FIRST,
            importedAtBaseMillis = 1_780_800_000_000L,
            knownAppMediaIdForSource = { item ->
                if (item.mediaStoreId == 101L) "media-existing" else null
            },
        )

        assertEquals(2, preview.requestedCount)
        assertEquals(1, preview.importableCount)
        assertEquals("image-202", preview.importableItems.single().id)
        assertEquals(1, preview.duplicateCount)
        assertEquals(
            SystemMediaImportDuplicateReason.ALREADY_IMPORTED,
            preview.duplicateItems.single().reason,
        )
    }

    @Test
    fun duplicateSelectionKeepsOnlyOneImportableItem() {
        val first = sampleSystemMediaItem(
            id = "image-301",
            mediaStoreId = 301L,
        )
        val duplicate = sampleSystemMediaItem(
            id = "image-301-copy",
            mediaStoreId = 301L,
        )

        val preview = buildSystemMediaImportPreview(
            mediaItems = listOf(first, duplicate),
            preference = MediaTimePreference.CAPTURED_FIRST,
            importedAtBaseMillis = 1_780_800_000_000L,
            knownAppMediaIdForSource = { null },
        )

        assertEquals(1, preview.importableCount)
        assertEquals("image-301", preview.importableItems.single().id)
        assertEquals(1, preview.duplicateCount)
        assertEquals(
            SystemMediaImportDuplicateReason.DUPLICATE_IN_SELECTION,
            preview.duplicateItems.single().reason,
        )
    }

    @Test
    fun capturedFirstWarnsWhenTimeFallsBack() {
        val fileFallback = sampleSystemMediaItem(
            id = "image-file-time",
            mediaStoreId = 401L,
            capturedAtMillis = null,
            fileModifiedAtMillis = 1_780_710_000_000L,
        )
        val importedFallback = sampleSystemMediaItem(
            id = "image-import-time",
            mediaStoreId = 402L,
            capturedAtMillis = null,
            fileModifiedAtMillis = null,
        )

        val preview = buildSystemMediaImportPreview(
            mediaItems = listOf(fileFallback, importedFallback),
            preference = MediaTimePreference.CAPTURED_FIRST,
            importedAtBaseMillis = 1_780_800_000_000L,
            knownAppMediaIdForSource = { null },
        )

        assertEquals(2, preview.importableCount)
        assertEquals(2, preview.timeNoticeCount)
        assertEquals(
            listOf(
                SystemMediaImportTimeNoticeKind.FILE_TIME_FALLBACK,
                SystemMediaImportTimeNoticeKind.IMPORTED_TIME_FALLBACK,
            ),
            preview.timeNoticeItems.map(SystemMediaImportTimeNoticeItem::kind),
        )
    }

    @Test
    fun largeGapBetweenCapturedAndFileTimeShowsReminder() {
        val preview = buildSystemMediaImportPreview(
            mediaItems = listOf(
                sampleSystemMediaItem(
                    id = "image-gap",
                    mediaStoreId = 501L,
                    capturedAtMillis = 1_780_000_000_000L,
                    fileModifiedAtMillis = 1_780_700_000_000L,
                ),
            ),
            preference = MediaTimePreference.CAPTURED_FIRST,
            importedAtBaseMillis = 1_780_800_000_000L,
            knownAppMediaIdForSource = { null },
        )

        assertEquals(1, preview.importableCount)
        assertEquals(DisplayTimeSourceOriginal, preview.importableItems.single().displayTimeSource)
        assertEquals(1, preview.timeNoticeCount)
        assertEquals(
            SystemMediaImportTimeNoticeKind.CAPTURED_FILE_TIME_GAP,
            preview.timeNoticeItems.single().kind,
        )
        assertTrue(preview.timeNoticeItems.single().message.contains("拍摄时间和文件时间相差"))
    }

    @Test
    fun importedFirstUsesImportedTimeWithoutWarnings() {
        val preview = buildSystemMediaImportPreview(
            mediaItems = listOf(
                sampleSystemMediaItem(
                    id = "image-imported-first",
                    mediaStoreId = 601L,
                    capturedAtMillis = null,
                    fileModifiedAtMillis = 1_780_700_000_000L,
                ),
            ),
            preference = MediaTimePreference.IMPORTED_FIRST,
            importedAtBaseMillis = 1_780_800_000_000L,
            knownAppMediaIdForSource = { null },
        )

        assertTrue(preview.hasImportableItems)
        assertEquals(DisplayTimeSourceImported, preview.importableItems.single().displayTimeSource)
        assertFalse(preview.timeNoticeItems.isNotEmpty())
    }

    private fun sampleSystemMediaItem(
        id: String,
        mediaStoreId: Long,
        capturedAtMillis: Long? = 1_780_600_000_000L,
        fileModifiedAtMillis: Long? = 1_780_601_000_000L,
    ): SystemMediaItem {
        return SystemMediaItem(
            id = id,
            mediaStoreId = mediaStoreId,
            uri = UriTestDouble("content://media/external/images/media/$mediaStoreId"),
            type = SystemMediaType.IMAGE,
            mimeType = "image/jpeg",
            displayName = "$id.jpg",
            bucketName = "Camera",
            displayTimeMillis = capturedAtMillis ?: fileModifiedAtMillis ?: 1_780_800_000_000L,
            capturedAtMillis = capturedAtMillis,
            fileModifiedAtMillis = fileModifiedAtMillis,
            displayTimeSource = capturedAtMillis?.let { DisplayTimeSourceOriginal } ?: DisplayTimeSourceImported,
            displayYear = 2026,
            displayMonth = 6,
            displayDay = 7,
            width = 1080,
            height = 1440,
            aspectRatio = 0.75f,
            palette = PhotoThumbnailPalette(
                start = Color(0xFF112233),
                end = Color(0xFF223344),
                accent = Color(0xFF335577),
            ),
            linkedPostIds = emptyList(),
            sizeBytes = 2_048_000L,
        )
    }
}
