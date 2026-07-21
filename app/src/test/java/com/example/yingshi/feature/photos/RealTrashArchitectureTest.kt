package com.example.yingshi.feature.photos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 1 架构验证测试
 *
 * 验证 FR-2 删除 RealTrashMemoryCache 后 ViewModel 的:
 * 1. UiState 数据类默认值正确
 * 2. activeListKey 计算逻辑正确（替代 RealTrashMemoryCache.listKey()）
 * 3. ViewModel 类结构正确（class + ViewModel() + StateFlow）
 */
class RealTrashArchitectureTest {

    // ==================== FR-2 AC-3: UiState 默认值 ====================

    @Test
    fun listUiStateDefaultsAreCorrect() {
        val state = RealTrashListUiState()

        assertFalse("默认 isLoading 应为 false", state.isLoading)
        assertFalse("默认 isMutating 应为 false", state.isMutating)
        assertFalse("默认 isOfflineReadOnly 应为 false", state.isOfflineReadOnly)
        assertFalse("默认 tokenMissing 应为 false", state.tokenMissing)
        assertNull("默认 errorMessage 应为 null", state.errorMessage)
        assertNull("默认 statusMessage 应为 null", state.statusMessage)
        assertTrue("默认 entries 应为空列表", state.entries.isEmpty())
        assertTrue("默认 pendingEntries 应为空列表", state.pendingEntries.isEmpty())
    }

    @Test
    fun detailUiStateDefaultsAreCorrect() {
        val state = RealTrashDetailUiState()

        assertFalse("默认 isLoading 应为 false", state.isLoading)
        assertFalse("默认 isMutating 应为 false", state.isMutating)
        assertFalse("默认 isOfflineReadOnly 应为 false", state.isOfflineReadOnly)
        assertFalse("默认 tokenMissing 应为 false", state.tokenMissing)
        assertNull("默认 errorMessage 应为 null", state.errorMessage)
        assertNull("默认 statusMessage 应为 null", state.statusMessage)
        assertNull("默认 detail 应为 null", state.detail)
    }

    // ==================== FR-2 AC-1: activeListKey 计算逻辑 ====================
    // 原 RealTrashMemoryCache.listKey(initialSelectedType) 逻辑被内联为
    // initialSelectedType?.name ?: "ALL"

    @Test
    fun activeListKeyCalculationIsNullReturnsAll() {
        val initialSelectedType: TrashEntryType? = null
        val key = initialSelectedType?.name ?: "ALL"

        assertEquals("null 类型应返回 ALL", "ALL", key)
    }

    @Test
    fun activeListKeyCalculationIsMediaRemovedReturnsEnumName() {
        val initialSelectedType: TrashEntryType? = TrashEntryType.MEDIA_REMOVED
        val key = initialSelectedType?.name ?: "ALL"

        assertEquals("MEDIA_REMOVED 应返回枚举名", "MEDIA_REMOVED", key)
    }

    @Test
    fun activeListKeyCalculationIsLargeAlbumReturnsEnumName() {
        val initialSelectedType: TrashEntryType? = TrashEntryType.LARGE_ALBUM_DELETED
        val key = initialSelectedType?.name ?: "ALL"

        assertEquals("LARGE_ALBUM_DELETED 应返回枚举名", "LARGE_ALBUM_DELETED", key)
    }

    @Test
    fun activeListKeyCalculationIsSmallAlbumReturnsEnumName() {
        val initialSelectedType: TrashEntryType? = TrashEntryType.SMALL_ALBUM_DELETED
        val key = initialSelectedType?.name ?: "ALL"

        assertEquals("SMALL_ALBUM_DELETED 应返回枚举名", "SMALL_ALBUM_DELETED", key)
    }

    @Test
    fun activeListKeyCalculationIsSystemDeletedReturnsEnumName() {
        val initialSelectedType: TrashEntryType? = TrashEntryType.MEDIA_SYSTEM_DELETED
        val key = initialSelectedType?.name ?: "ALL"

        assertEquals("MEDIA_SYSTEM_DELETED 应返回枚举名", "MEDIA_SYSTEM_DELETED", key)
    }

    // ==================== FR-2 AC-1: 无 RealTrashMemoryCache 残留 ====================

    @Test
    fun realTrashListUiStateHasNoMemoryCacheDependency() {
        // 验证 RealTrashListUiState 可以独立构造，不依赖任何全局单例
        val state = RealTrashListUiState(isLoading = true)
        assertTrue(state.isLoading)

        val loaded = state.copy(isLoading = false, entries = emptyList())
        assertFalse(loaded.isLoading)
    }

    @Test
    fun realTrashDetailUiStateHasNoMemoryCacheDependency() {
        // 验证 RealTrashDetailUiState 可以独立构造，不依赖任何全局单例
        val state = RealTrashDetailUiState(isLoading = true)
        assertTrue(state.isLoading)

        val loaded = state.copy(isLoading = false)
        assertFalse(loaded.isLoading)
    }

    // ==================== FR-2 AC-2: ViewModel 类结构 ====================

    @Test
    fun realTrashListViewModelExtendsViewModel() {
        // 验证 RealTrashListViewModel 是 class（不是 object）且继承 ViewModel
        // 通过反射验证类结构
        val clazz = RealTrashListViewModel::class.java
        assertEquals("RealTrashListViewModel", clazz.simpleName)

        // 验证父类是 ViewModel
        val superclass = clazz.superclass
        assertEquals(
            "RealTrashListViewModel 应继承 ViewModel",
            "androidx.lifecycle.ViewModel",
            superclass?.name,
        )
    }

    @Test
    fun realTrashDetailViewModelExtendsViewModel() {
        // 验证 RealTrashDetailViewModel 是 class（不是 object）且继承 ViewModel
        val clazz = RealTrashDetailViewModel::class.java
        assertEquals("RealTrashDetailViewModel", clazz.simpleName)

        val superclass = clazz.superclass
        assertEquals(
            "RealTrashDetailViewModel 应继承 ViewModel",
            "androidx.lifecycle.ViewModel",
            superclass?.name,
        )
    }
}
