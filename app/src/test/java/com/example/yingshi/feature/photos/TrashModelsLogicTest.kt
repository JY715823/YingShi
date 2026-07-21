package com.example.yingshi.feature.photos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Round 4: TrashModels.kt helper 函数单测。
 *
 * 覆盖：
 * - parseTrashEntryTypeOrNull (8 用例)
 * - parseTrashEntryTypeOrDefault (3 用例)
 * - restoreTargetMediaIds (2 用例)
 * - businessIdentityKey (5 用例)
 */
class TrashModelsLogicTest {

    // ==================== parseTrashEntryTypeOrNull ====================

    @Test
    fun parseOrNull_nullReturnsNull() {
        assertNull(parseTrashEntryTypeOrNull(null))
    }

    @Test
    fun parseOrNull_emptyReturnsNull() {
        assertNull(parseTrashEntryTypeOrNull(""))
    }

    @Test
    fun parseOrNull_blankReturnsNull() {
        assertNull(parseTrashEntryTypeOrNull("   "))
    }

    @Test
    fun parseOrNull_largeAlbumDeletedMatches() {
        assertEquals(
            TrashEntryType.LARGE_ALBUM_DELETED,
            parseTrashEntryTypeOrNull("largeAlbumDeleted"),
        )
    }

    @Test
    fun parseOrNull_largeAlbumDeletedIsCaseInsensitive() {
        assertEquals(
            TrashEntryType.LARGE_ALBUM_DELETED,
            parseTrashEntryTypeOrNull("LARGEALBUMDELETED"),
        )
    }

    @Test
    fun parseOrNull_postDeletedAliasMapsToSmallAlbum() {
        // POST_DELETED 别名映射到 SMALL_ALBUM_DELETED
        assertEquals(
            TrashEntryType.SMALL_ALBUM_DELETED,
            parseTrashEntryTypeOrNull("POST_DELETED"),
        )
    }

    @Test
    fun parseOrNull_unknownStringFallsBackToEnumNameMatch() {
        // 未知字符串最后尝试 enum 名匹配
        assertEquals(
            TrashEntryType.MEDIA_REMOVED,
            parseTrashEntryTypeOrNull("MEDIA_REMOVED"),
        )
    }

    @Test
    fun parseOrNull_trimsWhitespaceBeforeMatching() {
        assertEquals(
            TrashEntryType.MEDIA_SYSTEM_DELETED,
            parseTrashEntryTypeOrNull("  mediaSystemDeleted  "),
        )
    }

    // ==================== parseTrashEntryTypeOrDefault ====================

    @Test
    fun parseOrDefault_returnsParsedWhenValid() {
        assertEquals(
            TrashEntryType.LARGE_ALBUM_DELETED,
            parseTrashEntryTypeOrDefault("largeAlbumDeleted", TrashEntryType.MEDIA_REMOVED),
        )
    }

    @Test
    fun parseOrDefault_returnsDefaultWhenNull() {
        assertEquals(
            TrashEntryType.MEDIA_SYSTEM_DELETED,
            parseTrashEntryTypeOrDefault(null, TrashEntryType.MEDIA_SYSTEM_DELETED),
        )
    }

    @Test
    fun parseOrDefault_returnsDefaultWhenInvalid() {
        assertEquals(
            TrashEntryType.SMALL_ALBUM_DELETED,
            parseTrashEntryTypeOrDefault("not_valid", TrashEntryType.SMALL_ALBUM_DELETED),
        )
    }

    // ==================== restoreTargetMediaIds ====================

    @Test
    fun restoreTargetMediaIds_collectsAllSourcesAndDeduplicates() {
        val entry = sampleTrashEntryUiModel(
            sourceMediaId = "src-1",
            mediaSnapshot = sampleTrashMediaSnapshot(mediaId = "snap-1"),
        ).copy(
            relatedMediaIds = listOf("rel-1", "src-1"), // src-1 应去重
        )
        val ids = entry.restoreTargetMediaIds()
        assertEquals(listOf("src-1", "snap-1", "rel-1"), ids)
    }

    @Test
    fun restoreTargetMediaIds_returnsEmptyWhenAllAbsent() {
        val entry = sampleTrashEntryUiModel(
            sourceMediaId = null,
            mediaSnapshot = null,
        ).copy(
            relatedMediaIds = emptyList(),
        )
        assertEquals(emptyList<String>(), entry.restoreTargetMediaIds())
    }

    // ==================== businessIdentityKey ====================

    @Test
    fun businessIdentityKey_isDeterministicForSameEntry() {
        val entry = sampleTrashEntryUiModel(
            id = "x1",
            type = TrashEntryType.MEDIA_SYSTEM_DELETED,
            sourcePostId = "p1",
            sourceMediaId = "m1",
            title = "标题",
            previewInfo = "预览",
            deletedAtMillis = 1_700_000_000_000L,
            relatedPostIds = listOf("p1", "p2"),
            relatedMediaIds = listOf("m1", "m2"),
        )
        assertEquals(entry.businessIdentityKey(), entry.businessIdentityKey())
    }

    @Test
    fun businessIdentityKey_differsByType() {
        val base = sampleTrashEntryUiModel(type = TrashEntryType.MEDIA_SYSTEM_DELETED)
        val other = base.copy(type = TrashEntryType.MEDIA_REMOVED)
        assertEquals(false, base.businessIdentityKey() == other.businessIdentityKey())
    }

    @Test
    fun businessIdentityKey_differsBySourceMediaId() {
        val base = sampleTrashEntryUiModel(sourceMediaId = "m1")
        val other = base.copy(sourceMediaId = "m2")
        assertEquals(false, base.businessIdentityKey() == other.businessIdentityKey())
    }

    @Test
    fun businessIdentityKey_differsByRelatedPostOrderIsNormalized() {
        // relatedPostIds 排序后 join，顺序不影响 key
        val base = sampleTrashEntryUiModel().copy(relatedPostIds = listOf("p1", "p2"))
        val reordered = base.copy(relatedPostIds = listOf("p2", "p1"))
        assertEquals(base.businessIdentityKey(), reordered.businessIdentityKey())
    }

    @Test
    fun businessIdentityKey_differsByDeletedAtMillis() {
        val base = sampleTrashEntryUiModel(deletedAtMillis = 1_700_000_000_000L)
        val other = base.copy(deletedAtMillis = 1_800_000_000_000L)
        assertEquals(false, base.businessIdentityKey() == other.businessIdentityKey())
    }
}
