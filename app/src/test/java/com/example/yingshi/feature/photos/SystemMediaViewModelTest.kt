package com.example.yingshi.feature.photos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemMediaViewModelTest {

    // ==================== ALL filter ====================

    @Test
    fun allFilterReturnsAllItems() {
        val items = listOf(
            sampleSystemMediaItem(id = "img-1", mediaStoreId = 101L),
            sampleSystemMediaItem(id = "img-2", mediaStoreId = 102L),
            sampleSystemMediaItem(id = "img-3", mediaStoreId = 103L),
        )

        val result = items.applyFilter(SystemMediaFilter.ALL, album = null)

        assertEquals(3, result.size)
        assertEquals(listOf("img-1", "img-2", "img-3"), result.map { it.id })
    }

    // ==================== CAMERA filter ====================

    @Test
    fun cameraFilterMatchesCameraBucket() {
        val items = listOf(
            sampleSystemMediaItem(id = "cam", mediaStoreId = 1L, bucketName = "Camera"),
            sampleSystemMediaItem(id = "dl", mediaStoreId = 2L, bucketName = "Downloads"),
        )

        val result = items.applyFilter(SystemMediaFilter.CAMERA, album = null)

        assertEquals(1, result.size)
        assertEquals("cam", result.single().id)
    }

    @Test
    fun cameraFilterMatchesDcimBucket() {
        val items = listOf(
            sampleSystemMediaItem(id = "dcim", mediaStoreId = 1L, bucketName = "DCIM"),
            sampleSystemMediaItem(id = "dl", mediaStoreId = 2L, bucketName = "Downloads"),
        )

        val result = items.applyFilter(SystemMediaFilter.CAMERA, album = null)

        assertEquals(1, result.size)
        assertEquals("dcim", result.single().id)
    }

    @Test
    fun cameraFilterExcludesNonCameraBucket() {
        val items = listOf(
            sampleSystemMediaItem(id = "screenshots", mediaStoreId = 1L, bucketName = "Screenshots"),
            sampleSystemMediaItem(id = "downloads", mediaStoreId = 2L, bucketName = "Downloads"),
            sampleSystemMediaItem(id = "whatsapp", mediaStoreId = 3L, bucketName = "WhatsApp Images"),
        )

        val result = items.applyFilter(SystemMediaFilter.CAMERA, album = null)

        assertTrue(result.isEmpty())
    }

    @Test
    fun cameraFilterHandlesNullBucketName() {
        val items = listOf(
            sampleSystemMediaItem(id = "null-bucket", mediaStoreId = 1L, bucketName = null),
            sampleSystemMediaItem(id = "cam", mediaStoreId = 2L, bucketName = "Camera"),
        )

        val result = items.applyFilter(SystemMediaFilter.CAMERA, album = null)

        assertEquals(1, result.size)
        assertEquals("cam", result.single().id)
    }

    @Test
    fun cameraFilterIsCaseInsensitive() {
        val items = listOf(
            sampleSystemMediaItem(id = "lower", mediaStoreId = 1L, bucketName = "camera"),
            sampleSystemMediaItem(id = "upper", mediaStoreId = 2L, bucketName = "CAMERA"),
            sampleSystemMediaItem(id = "mixed", mediaStoreId = 3L, bucketName = "CaMeRa"),
            sampleSystemMediaItem(id = "dcim-lower", mediaStoreId = 4L, bucketName = "dcim"),
            sampleSystemMediaItem(id = "dcim-upper", mediaStoreId = 5L, bucketName = "DCIM"),
        )

        val result = items.applyFilter(SystemMediaFilter.CAMERA, album = null)

        assertEquals(5, result.size)
    }

    // ==================== SCREENSHOT filter ====================

    @Test
    fun screenshotFilterMatchesBucketName() {
        val items = listOf(
            sampleSystemMediaItem(id = "shot", mediaStoreId = 1L, bucketName = "Screenshots"),
            sampleSystemMediaItem(id = "cam", mediaStoreId = 2L, bucketName = "Camera"),
        )

        val result = items.applyFilter(SystemMediaFilter.SCREENSHOT, album = null)

        assertEquals(1, result.size)
        assertEquals("shot", result.single().id)
    }

    @Test
    fun screenshotFilterMatchesDisplayName() {
        val items = listOf(
            sampleSystemMediaItem(
                id = "screenshot-file",
                mediaStoreId = 1L,
                bucketName = "Downloads",
                displayName = "Screenshot_2026.jpg",
            ),
            sampleSystemMediaItem(id = "cam", mediaStoreId = 2L, bucketName = "Camera"),
        )

        val result = items.applyFilter(SystemMediaFilter.SCREENSHOT, album = null)

        assertEquals(1, result.size)
        assertEquals("screenshot-file", result.single().id)
    }

    @Test
    fun screenshotFilterExcludesNonScreenshot() {
        val items = listOf(
            sampleSystemMediaItem(id = "cam", mediaStoreId = 1L, bucketName = "Camera", displayName = "IMG_001.jpg"),
            sampleSystemMediaItem(id = "dl", mediaStoreId = 2L, bucketName = "Downloads", displayName = "document.pdf"),
        )

        val result = items.applyFilter(SystemMediaFilter.SCREENSHOT, album = null)

        assertTrue(result.isEmpty())
    }

    // ==================== VIDEO filter ====================

    @Test
    fun videoFilterReturnsOnlyVideos() {
        val items = listOf(
            sampleSystemMediaItem(id = "vid", mediaStoreId = 1L, type = SystemMediaType.VIDEO),
            sampleSystemMediaItem(id = "img", mediaStoreId = 2L, type = SystemMediaType.IMAGE),
        )

        val result = items.applyFilter(SystemMediaFilter.VIDEO, album = null)

        assertEquals(1, result.size)
        assertEquals("vid", result.single().id)
    }

    @Test
    fun videoFilterExcludesImages() {
        val items = listOf(
            sampleSystemMediaItem(id = "img1", mediaStoreId = 1L, type = SystemMediaType.IMAGE),
            sampleSystemMediaItem(id = "img2", mediaStoreId = 2L, type = SystemMediaType.IMAGE),
        )

        val result = items.applyFilter(SystemMediaFilter.VIDEO, album = null)

        assertTrue(result.isEmpty())
    }

    // ==================== IMPORTED / UNIMPORTED filters ====================

    @Test
    fun importedFilterReturnsOnlyImported() {
        val items = listOf(
            sampleSystemMediaItem(id = "imported", mediaStoreId = 1L, importedAppMediaId = "media-001"),
            sampleSystemMediaItem(id = "fresh", mediaStoreId = 2L, importedAppMediaId = null),
        )

        val result = items.applyFilter(SystemMediaFilter.IMPORTED, album = null)

        assertEquals(1, result.size)
        assertEquals("imported", result.single().id)
    }

    @Test
    fun unimportedFilterReturnsOnlyUnimported() {
        val items = listOf(
            sampleSystemMediaItem(id = "imported", mediaStoreId = 1L, importedAppMediaId = "media-001"),
            sampleSystemMediaItem(id = "fresh", mediaStoreId = 2L, importedAppMediaId = null),
        )

        val result = items.applyFilter(SystemMediaFilter.UNIMPORTED, album = null)

        assertEquals(1, result.size)
        assertEquals("fresh", result.single().id)
    }

    // ==================== album 筛选 ====================

    @Test
    fun albumFilterNarrowsToSpecificBucket() {
        val items = listOf(
            sampleSystemMediaItem(id = "cam-1", mediaStoreId = 1L, bucketName = "Camera"),
            sampleSystemMediaItem(id = "dl-1", mediaStoreId = 2L, bucketName = "Downloads"),
            sampleSystemMediaItem(id = "cam-2", mediaStoreId = 3L, bucketName = "Camera"),
        )
        val album = sampleSystemMediaAlbum(bucketName = "Camera", displayName = "Camera")

        val result = items.applyFilter(SystemMediaFilter.ALL, album = album)

        assertEquals(2, result.size)
        assertEquals(listOf("cam-1", "cam-2"), result.map { it.id })
    }

    @Test
    fun albumNullSkipsAlbumFiltering() {
        val items = listOf(
            sampleSystemMediaItem(id = "cam", mediaStoreId = 1L, bucketName = "Camera"),
            sampleSystemMediaItem(id = "dl", mediaStoreId = 2L, bucketName = "Downloads"),
        )

        val result = items.applyFilter(SystemMediaFilter.ALL, album = null)

        assertEquals(2, result.size)
    }

    @Test
    fun albumPlusFilterIsAndCombination() {
        val items = listOf(
            sampleSystemMediaItem(id = "cam-img", mediaStoreId = 1L, bucketName = "Camera", type = SystemMediaType.IMAGE),
            sampleSystemMediaItem(id = "cam-vid", mediaStoreId = 2L, bucketName = "Camera", type = SystemMediaType.VIDEO),
            sampleSystemMediaItem(id = "dl-vid", mediaStoreId = 3L, bucketName = "Downloads", type = SystemMediaType.VIDEO),
        )
        val album = sampleSystemMediaAlbum(bucketName = "Camera")

        val result = items.applyFilter(SystemMediaFilter.VIDEO, album = album)

        assertEquals(1, result.size)
        assertEquals("cam-vid", result.single().id)
    }

    @Test
    fun albumPlusCameraCombination() {
        val items = listOf(
            sampleSystemMediaItem(id = "cam-1", mediaStoreId = 1L, bucketName = "Camera"),
            sampleSystemMediaItem(id = "dcim-1", mediaStoreId = 2L, bucketName = "DCIM"),
            sampleSystemMediaItem(id = "dl-1", mediaStoreId = 3L, bucketName = "Downloads"),
        )
        val album = sampleSystemMediaAlbum(bucketName = "Camera")

        val result = items.applyFilter(SystemMediaFilter.CAMERA, album = album)

        assertEquals(1, result.size)
        assertEquals("cam-1", result.single().id)
    }

    // ==================== 边界场景 ====================

    @Test
    fun emptyListReturnsEmpty() {
        val result = emptyList<SystemMediaItem>().applyFilter(SystemMediaFilter.ALL, album = null)

        assertTrue(result.isEmpty())
    }
}
