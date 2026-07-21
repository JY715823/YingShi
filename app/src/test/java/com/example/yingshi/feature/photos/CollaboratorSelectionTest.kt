package com.example.yingshi.feature.photos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 4: CollaboratorIdentity.kt 选择 helper 函数单测。
 *
 * 覆盖：
 * - defaultCollaboratorSelection (4 用例)
 * - normalizeCollaboratorSelectionKeepingEmpty (5 用例)
 * - isAllCollaboratorsSelected (6 用例)
 * - toggleCollaboratorSelectionKeepingEmpty (4 用例)
 * - initialOrNormalizedCollaboratorSelection (3 用例)
 */
class CollaboratorSelectionTest {

    // ==================== defaultCollaboratorSelection ====================

    @Test
    fun defaultSelection_returnsAllNonBlankUserIds() {
        val all = setOf("u1", "u2", "u3")
        assertEquals(all, defaultCollaboratorSelection(all))
    }

    @Test
    fun defaultSelection_filtersBlankUserIds() {
        val all = setOf("u1", "", "  ", "u2")
        assertEquals(setOf("u1", "u2"), defaultCollaboratorSelection(all))
    }

    @Test
    fun defaultSelection_returnsEmptyForEmptyInput() {
        assertTrue(defaultCollaboratorSelection(emptySet()).isEmpty())
    }

    @Test
    fun defaultSelection_returnsOnlyNonBlankWhenAllBlank() {
        val all = setOf("", "  ")
        assertTrue(defaultCollaboratorSelection(all).isEmpty())
    }

    // ==================== normalizeCollaboratorSelectionKeepingEmpty ====================

    @Test
    fun normalizeKeepingEmpty_returnsEmptyWhenAllIsEmpty() {
        assertTrue(normalizeCollaboratorSelectionKeepingEmpty(setOf("u1"), emptySet()).isEmpty())
    }

    @Test
    fun normalizeKeepingEmpty_filtersOutIdsNotInAll() {
        val all = setOf("u1", "u2")
        val selected = setOf("u1", "u3") // u3 不在 all 中
        assertEquals(setOf("u1"), normalizeCollaboratorSelectionKeepingEmpty(selected, all))
    }

    @Test
    fun normalizeKeepingEmpty_preservesAllValidSelections() {
        val all = setOf("u1", "u2", "u3")
        val selected = setOf("u1", "u2")
        assertEquals(setOf("u1", "u2"), normalizeCollaboratorSelectionKeepingEmpty(selected, all))
    }

    @Test
    fun normalizeKeepingEmpty_returnsEmptyWhenSelectionEmpty() {
        val all = setOf("u1", "u2")
        assertTrue(normalizeCollaboratorSelectionKeepingEmpty(emptySet(), all).isEmpty())
    }

    @Test
    fun normalizeKeepingEmpty_returnsAllWhenAllSelected() {
        val all = setOf("u1", "u2")
        assertEquals(all, normalizeCollaboratorSelectionKeepingEmpty(all, all))
    }

    // ==================== isAllCollaboratorsSelected ====================

    @Test
    fun isAllSelected_returnsFalseWhenAllEmpty() {
        assertFalse(isAllCollaboratorsSelected(setOf("u1"), emptySet()))
    }

    @Test
    fun isAllSelected_returnsTrueWhenAllSelected() {
        val all = setOf("u1", "u2")
        assertTrue(isAllCollaboratorsSelected(all, all))
    }

    @Test
    fun isAllSelected_returnsFalseWhenPartialSelection() {
        val all = setOf("u1", "u2")
        assertFalse(isAllCollaboratorsSelected(setOf("u1"), all))
    }

    @Test
    fun isAllSelected_returnsFalseWhenEmptySelection() {
        val all = setOf("u1", "u2")
        assertFalse(isAllCollaboratorsSelected(emptySet(), all))
    }

    @Test
    fun isAllSelected_returnsFalseWhenSelectionContainsExtraIds() {
        // 选中集合包含 all 之外 id 但不全是 all 的情况
        val all = setOf("u1", "u2")
        val selected = setOf("u1", "u3") // u3 不在 all，归一化后只剩 u1
        assertFalse(isAllCollaboratorsSelected(selected, all))
    }

    @Test
    fun isAllSelected_returnsTrueForSingleUserAllSelected() {
        val all = setOf("u1")
        assertTrue(isAllCollaboratorsSelected(setOf("u1"), all))
    }

    // ==================== toggleCollaboratorSelectionKeepingEmpty ====================

    @Test
    fun toggleKeepingEmpty_addsUserIdWhenNotSelected() {
        val all = setOf("u1", "u2")
        val result = toggleCollaboratorSelectionKeepingEmpty(setOf("u1"), "u2", all)
        assertEquals(setOf("u1", "u2"), result)
    }

    @Test
    fun toggleKeepingEmpty_removesUserIdWhenSelected() {
        val all = setOf("u1", "u2")
        val result = toggleCollaboratorSelectionKeepingEmpty(setOf("u1", "u2"), "u2", all)
        assertEquals(setOf("u1"), result)
    }

    @Test
    fun toggleKeepingEmpty_filtersInvalidIdsWhenToggledIdNotInAll() {
        val all = setOf("u1", "u2")
        val selected = setOf("u1", "u3") // u3 不在 all
        val result = toggleCollaboratorSelectionKeepingEmpty(selected, "u_invalid", all)
        // 切换的 id 不在 all，仅过滤已有选择
        assertEquals(setOf("u1"), result)
    }

    @Test
    fun toggleKeepingEmpty_togglingAddsToEmptySelection() {
        val all = setOf("u1", "u2")
        val result = toggleCollaboratorSelectionKeepingEmpty(emptySet(), "u1", all)
        assertEquals(setOf("u1"), result)
    }

    // ==================== initialOrNormalizedCollaboratorSelection ====================

    @Test
    fun initialOrNormalized_returnsDefaultWhenNotInitialized() {
        val all = setOf("u1", "u2")
        assertEquals(all, initialOrNormalizedCollaboratorSelection(emptySet(), all, initialized = false))
    }

    @Test
    fun initialOrNormalized_returnsNormalizedWhenInitialized() {
        val all = setOf("u1", "u2", "u3")
        val selected = setOf("u1", "u_invalid")
        val result = initialOrNormalizedCollaboratorSelection(selected, all, initialized = true)
        // 归一化后只剩 u1
        assertEquals(setOf("u1"), result)
    }

    @Test
    fun initialOrNormalized_returnsEmptyForEmptyAllWhenInitialized() {
        val result = initialOrNormalizedCollaboratorSelection(setOf("u1"), emptySet(), initialized = true)
        assertTrue(result.isEmpty())
    }
}
