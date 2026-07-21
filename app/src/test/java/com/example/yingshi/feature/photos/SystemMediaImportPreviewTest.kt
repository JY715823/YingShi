package com.example.yingshi.feature.photos

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

    @Test
    fun mixedBatchClassifiesAllThreeStatesCorrectly() {
        val alreadyImported = sampleSystemMediaItem(
            id = "img-imported",
            mediaStoreId = 701L,
        )
        val fresh = sampleSystemMediaItem(
            id = "img-fresh",
            mediaStoreId = 702L,
        )
        val duplicateOfFresh = sampleSystemMediaItem(
            id = "img-dup",
            mediaStoreId = 702L,
        )

        val preview = buildSystemMediaImportPreview(
            mediaItems = listOf(alreadyImported, fresh, duplicateOfFresh),
            preference = MediaTimePreference.CAPTURED_FIRST,
            importedAtBaseMillis = 1_780_800_000_000L,
            knownAppMediaIdForSource = { item ->
                if (item.mediaStoreId == 701L) "media-existing" else null
            },
        )

        assertEquals(3, preview.requestedCount)
        assertEquals(1, preview.importableCount)
        assertEquals(2, preview.duplicateCount)
        assertEquals(
            listOf(SystemMediaImportDuplicateReason.ALREADY_IMPORTED, SystemMediaImportDuplicateReason.DUPLICATE_IN_SELECTION),
            preview.duplicateItems.map { it.reason },
        )
        assertEquals(3, preview.importableCount + preview.duplicateCount)
    }

    @Test
    fun allItemsInAlreadyImportedGroupAreMarked() {
        val first = sampleSystemMediaItem(
            id = "img-a",
            mediaStoreId = 801L,
        )
        val second = sampleSystemMediaItem(
            id = "img-b",
            mediaStoreId = 801L,
        )

        val preview = buildSystemMediaImportPreview(
            mediaItems = listOf(first, second),
            preference = MediaTimePreference.CAPTURED_FIRST,
            importedAtBaseMillis = 1_780_800_000_000L,
            knownAppMediaIdForSource = { _ -> "media-existing" },
        )

        assertEquals(0, preview.importableCount)
        assertEquals(2, preview.duplicateCount)
        assertEquals(
            listOf(
                SystemMediaImportDuplicateReason.ALREADY_IMPORTED,
                SystemMediaImportDuplicateReason.ALREADY_IMPORTED,
            ),
            preview.duplicateItems.map { it.reason },
        )
    }

    @Test
    fun multipleIndependentGroupsKeepOrder() {
        val first = sampleSystemMediaItem(
            id = "img-1",
            mediaStoreId = 901L,
        )
        val second = sampleSystemMediaItem(
            id = "img-2",
            mediaStoreId = 902L,
        )

        val preview = buildSystemMediaImportPreview(
            mediaItems = listOf(first, second),
            preference = MediaTimePreference.CAPTURED_FIRST,
            importedAtBaseMillis = 1_780_800_000_000L,
            knownAppMediaIdForSource = { null },
        )

        assertEquals(2, preview.importableCount)
        assertEquals(0, preview.duplicateCount)
        assertEquals(listOf("img-1", "img-2"), preview.importableItems.map { it.id })
    }

    @Test
    fun emptyInputReturnsEmptyPreview() {
        val preview = buildSystemMediaImportPreview(
            mediaItems = emptyList(),
            preference = MediaTimePreference.CAPTURED_FIRST,
            importedAtBaseMillis = 1_780_800_000_000L,
            knownAppMediaIdForSource = { null },
        )

        assertEquals(0, preview.requestedCount)
        assertEquals(0, preview.importableCount)
        assertEquals(0, preview.duplicateCount)
        assertFalse(preview.hasImportableItems)
    }

    @Test
    fun allAlreadyImportedReturnsNoImportableItems() {
        val first = sampleSystemMediaItem(
            id = "img-1",
            mediaStoreId = 1001L,
        )
        val second = sampleSystemMediaItem(
            id = "img-2",
            mediaStoreId = 1002L,
        )

        val preview = buildSystemMediaImportPreview(
            mediaItems = listOf(first, second),
            preference = MediaTimePreference.CAPTURED_FIRST,
            importedAtBaseMillis = 1_780_800_000_000L,
            knownAppMediaIdForSource = { _ -> "media-existing" },
        )

        assertFalse(preview.hasImportableItems)
        assertEquals(0, preview.importableCount)
        assertEquals(2, preview.duplicateCount)
    }

    @Test
    fun pickedItemUsesMetadataKeyPath() {
        val first = sampleSystemMediaItem(
            id = "picked-abc-123",
            mediaStoreId = 0L,
            displayName = "vacation.jpg",
            sizeBytes = 1_000_000L,
        )
        val duplicate = sampleSystemMediaItem(
            id = "picked-xyz-456",
            mediaStoreId = 1L,
            displayName = "vacation.jpg",
            sizeBytes = 1_000_000L,
        )

        val preview = buildSystemMediaImportPreview(
            mediaItems = listOf(first, duplicate),
            preference = MediaTimePreference.CAPTURED_FIRST,
            importedAtBaseMillis = 1_780_800_000_000L,
            knownAppMediaIdForSource = { null },
        )

        assertEquals(1, preview.importableCount)
        assertEquals(1, preview.duplicateCount)
        assertEquals(
            SystemMediaImportDuplicateReason.DUPLICATE_IN_SELECTION,
            preview.duplicateItems.single().reason,
        )
    }

    @Test
    fun requestedCountReflectsOriginalInputSize() {
        val items = (1..5).map { idx ->
            sampleSystemMediaItem(
                id = "img-$idx",
                mediaStoreId = 1100L + idx,
            )
        }

        val preview = buildSystemMediaImportPreview(
            mediaItems = items,
            preference = MediaTimePreference.CAPTURED_FIRST,
            importedAtBaseMillis = 1_780_800_000_000L,
            knownAppMediaIdForSource = { null },
        )

        assertEquals(5, preview.requestedCount)
        assertEquals(5, preview.importableCount)
        assertEquals(0, preview.duplicateCount)
    }
}
