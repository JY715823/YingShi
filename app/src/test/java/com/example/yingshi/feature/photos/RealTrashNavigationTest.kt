package com.example.yingshi.feature.photos

import com.example.yingshi.feature.sync.SyncModule
import com.example.yingshi.feature.sync.SyncStaleState
import com.example.yingshi.navigation.PhotosTopDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 2 导航与同步状态测试
 *
 * FR-6: 验证 PhotosTopDestination 枚举映射（TRASH Tab 可正确恢复）
 * FR-9: 验证 pendingEntries 在 UiState 中的默认值与保留逻辑
 * FR-10: 验证 SyncModule.SYSTEM_MEDIA 存在且 SyncStaleState 正确处理
 */
class RealTrashNavigationTest {

    // ==================== FR-6: PhotosTopDestination 枚举 ====================

    @Test
    fun trashDestinationIsAtExpectedIndex() {
        assertEquals(0, PhotosTopDestination.PHOTOS.ordinal)
        assertEquals(1, PhotosTopDestination.ALBUMS.ordinal)
        assertEquals(2, PhotosTopDestination.TRASH.ordinal)
    }

    @Test
    fun trashDestinationNameMatchesEnumName() {
        assertEquals("TRASH", PhotosTopDestination.TRASH.name)
    }

    @Test
    fun entriesListContainsAllThreeDestinations() {
        val entries = PhotosTopDestination.entries
        assertEquals(3, entries.size)
        assertEquals(PhotosTopDestination.PHOTOS, entries[0])
        assertEquals(PhotosTopDestination.ALBUMS, entries[1])
        assertEquals(PhotosTopDestination.TRASH, entries[2])
    }

    @Test
    fun pagingIndexMapsToCorrectDestination() {
        // YingShiApp.kt LaunchedEffect uses PhotosTopDestination.entries[pagerState.currentPage]
        // Ensure TRASH (index 2) is the last tab so back navigation from trash detail restores to TRASH
        val pageName = PhotosTopDestination.entries[2].name
        assertEquals("TRASH", pageName)
    }

    // ==================== FR-9: pendingEntries 保留逻辑 ====================

    @Test
    fun listUiStatePendingEntriesDefaultsToEmpty() {
        val state = RealTrashListUiState()
        assertTrue("默认 pendingEntries 应为空列表", state.pendingEntries.isEmpty())
    }

    @Test
    fun listUiStateCopyPreservesPendingEntries() {
        val original = RealTrashListUiState(
            isLoading = true,
            errorMessage = null,
        )
        val copied = original.copy(isLoading = false, errorMessage = "error")
        // copy() should not lose pendingEntries (even if empty by default)
        assertEquals(original.pendingEntries, copied.pendingEntries)
    }

    // ==================== FR-10: SyncModule.SYSTEM_MEDIA ====================

    @Test
    fun systemMediaModuleExistsInEnum() {
        val modules = SyncModule.entries
        assertTrue("SyncModule 应包含 SYSTEM_MEDIA", modules.any { it.name == "SYSTEM_MEDIA" })
    }

    @Test
    fun staleStateSystemMediaDefaultsToFalse() {
        val state = SyncStaleState()
        assertFalse("默认 systemMediaStale 应为 false", state.systemMediaStale)
    }

    @Test
    fun staleStateIsStaleReturnsCorrectValueForSystemMedia() {
        val staleState = SyncStaleState(systemMediaStale = true)
        assertTrue("systemMediaStale=true 时 isStale(SYSTEM_MEDIA) 应返回 true",
            staleState.isStale(SyncModule.SYSTEM_MEDIA))
    }

    @Test
    fun staleStateIsStaleReturnsFalseForSystemMediaWhenNotStale() {
        val staleState = SyncStaleState(systemMediaStale = false)
        assertFalse("systemMediaStale=false 时 isStale(SYSTEM_MEDIA) 应返回 false",
            staleState.isStale(SyncModule.SYSTEM_MEDIA))
    }

    @Test
    fun staleStateCopyCanClearSystemMediaStale() {
        val stale = SyncStaleState(systemMediaStale = true)
        val cleared = stale.copy(systemMediaStale = false)
        assertFalse("copy(systemMediaStale=false) 后应清除", cleared.systemMediaStale)
    }
}
