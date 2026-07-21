package com.example.yingshi.feature.photos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 4: RealTrashShared.kt 纯函数单测。
 *
 * 覆盖：
 * - formatCountdownRemaining (11 用例)
 * - countdownSeverity (8 用例)
 * - realTrashEntrySourceLine (8 用例)
 * - pendingCleanupDeleteCopy (4 用例)
 * - toTrashStateLabel (12 用例)
 * - realTrashGridPostTitle (6 用例)
 * - previewMediaIds (6 用例)
 * - commentMediaId (5 用例)
 * - isRealMediaTrashType (4 用例)
 * - viewerVideoDurationMillis (2 用例)
 */
class RealTrashSharedLogicTest {

    // ==================== formatCountdownRemaining ====================

    @Test
    fun formatCountdownRemaining_zeroReturnsExpired() {
        assertEquals("已过期", formatCountdownRemaining(0L))
    }

    @Test
    fun formatCountdownRemaining_negativeReturnsExpired() {
        assertEquals("已过期", formatCountdownRemaining(-1_000L))
    }

    @Test
    fun formatCountdownRemaining_largeNegativeReturnsExpired() {
        assertEquals("已过期", formatCountdownRemaining(-60L * 60L * 1000L))
    }

    @Test
    fun formatCountdownRemaining_underOneMinuteReturnsAboutToExpire() {
        // 30 秒：不足 1 分钟
        assertEquals("即将过期", formatCountdownRemaining(30_000L))
    }

    @Test
    fun formatCountdownRemaining_exactlyOneSecondReturnsAboutToExpire() {
        assertEquals("即将过期", formatCountdownRemaining(1_000L))
    }

    @Test
    fun formatCountdownRemaining_oneMinuteReturnsMinutesFormat() {
        assertEquals("剩余 1m", formatCountdownRemaining(60_000L))
    }

    @Test
    fun formatCountdownRemaining_fiftyNineMinutesReturnsMinutesFormat() {
        assertEquals("剩余 59m", formatCountdownRemaining(59L * 60L * 1000L))
    }

    @Test
    fun formatCountdownRemaining_exactlyOneHourReturnsHoursFormat() {
        assertEquals("剩余 1h 0m", formatCountdownRemaining(60L * 60L * 1000L))
    }

    @Test
    fun formatCountdownRemaining_oneHourThirtyMinutesReturnsHoursAndMinutes() {
        assertEquals("剩余 1h 30m", formatCountdownRemaining(90L * 60L * 1000L))
    }

    @Test
    fun formatCountdownRemaining_23HoursReturnsHoursFormat() {
        assertEquals("剩余 23h 0m", formatCountdownRemaining(23L * 60L * 60L * 1000L))
    }

    @Test
    fun formatCountdownRemaining_largeValueStillFormatsHours() {
        // 99 小时 59 分钟
        val millis = (99L * 60L + 59L) * 60L * 1000L
        assertEquals("剩余 99h 59m", formatCountdownRemaining(millis))
    }

    // ==================== countdownSeverity ====================

    @Test
    fun countdownSeverity_zeroReturnsUrgent() {
        assertEquals(CountdownSeverity.URGENT, countdownSeverity(0L))
    }

    @Test
    fun countdownSeverity_negativeReturnsUrgent() {
        assertEquals(CountdownSeverity.URGENT, countdownSeverity(-1_000L))
    }

    @Test
    fun countdownSeverity_oneMillisecondBeforeOneHourIsUrgent() {
        // 1h - 1ms
        val oneHourMinusOne = 60L * 60L * 1000L - 1L
        assertEquals(CountdownSeverity.URGENT, countdownSeverity(oneHourMinusOne))
    }

    @Test
    fun countdownSeverity_exactlyOneHourIsUrgent() {
        // 1h 边界（<= 1h 为 URGENT）
        assertEquals(CountdownSeverity.URGENT, countdownSeverity(60L * 60L * 1000L))
    }

    @Test
    fun countdownSeverity_oneHourPlusOneIsWarning() {
        val oneHourPlusOne = 60L * 60L * 1000L + 1L
        assertEquals(CountdownSeverity.WARNING, countdownSeverity(oneHourPlusOne))
    }

    @Test
    fun countdownSeverity_fiveHoursIsWarning() {
        assertEquals(CountdownSeverity.WARNING, countdownSeverity(5L * 60L * 60L * 1000L))
    }

    @Test
    fun countdownSeverity_exactlySixHoursIsWarning() {
        // 6h 边界（<= 6h 为 WARNING）
        assertEquals(CountdownSeverity.WARNING, countdownSeverity(6L * 60L * 60L * 1000L))
    }

    @Test
    fun countdownSeverity_sixHoursPlusOneIsSafe() {
        val sixHourPlusOne = 6L * 60L * 60L * 1000L + 1L
        assertEquals(CountdownSeverity.SAFE, countdownSeverity(sixHourPlusOne))
    }

    // ==================== realTrashEntrySourceLine ====================

    @Test
    fun entrySourceLine_largeAlbumDeletedWithSnapshotUsesSnapshotCounts() {
        val entry = sampleTrashEntryUiModel(
            id = "e1",
            type = TrashEntryType.LARGE_ALBUM_DELETED,
        ).copy(
            relatedPostIds = listOf("p1", "p2"),
            relatedMediaIds = listOf("m1", "m2", "m3"),
        )
        val line = realTrashEntrySourceLine(entry)
        assertTrue(line.contains("整册删除"))
        assertTrue(line.contains("小相册 2 个"))
        assertTrue(line.contains("媒体 3 项"))
    }

    @Test
    fun entrySourceLine_largeAlbumDeletedWithoutSnapshotFallsBackToRelated() {
        val entry = sampleTrashEntryUiModel(
            id = "e1",
            type = TrashEntryType.LARGE_ALBUM_DELETED,
            relatedPostIds = listOf("p1"),
            relatedMediaIds = listOf("m1"),
        )
        val line = realTrashEntrySourceLine(entry)
        assertTrue(line.contains("整册删除"))
        assertTrue(line.contains("小相册 1 个"))
        assertTrue(line.contains("媒体 1 项"))
    }

    @Test
    fun entrySourceLine_smallAlbumDeletedUsesRelatedMediaCount() {
        val entry = sampleTrashEntryUiModel(
            id = "e1",
            type = TrashEntryType.SMALL_ALBUM_DELETED,
            relatedMediaIds = listOf("m1", "m2"),
        )
        val line = realTrashEntrySourceLine(entry)
        assertTrue(line.contains("小相册删除"))
        assertTrue(line.contains("媒体 2 项"))
    }

    @Test
    fun entrySourceLine_smallAlbumDeletedWithZeroMediaShowsZero() {
        val entry = sampleTrashEntryUiModel(
            id = "e1",
            type = TrashEntryType.SMALL_ALBUM_DELETED,
            relatedMediaIds = emptyList(),
        )
        val line = realTrashEntrySourceLine(entry)
        assertTrue(line.contains("媒体 0 项"))
    }

    @Test
    fun entrySourceLine_mediaRemovedWithSourcePostIdShowsSource() {
        val entry = sampleTrashEntryUiModel(
            id = "e1",
            type = TrashEntryType.MEDIA_REMOVED,
            sourcePostId = "post-abc",
        )
        val line = realTrashEntrySourceLine(entry)
        assertTrue(line.contains("从小相册移除"))
        assertTrue(line.contains("来源 post-abc"))
    }

    @Test
    fun entrySourceLine_mediaRemovedWithoutSourcePostIdFallsBackToRelated() {
        val entry = sampleTrashEntryUiModel(
            id = "e1",
            type = TrashEntryType.MEDIA_REMOVED,
            sourcePostId = null,
            relatedPostIds = listOf("first-post"),
        )
        val line = realTrashEntrySourceLine(entry)
        assertTrue(line.contains("来源 first-post"))
    }

    @Test
    fun entrySourceLine_mediaRemovedWithoutAnyPostShowsCurrentAlbum() {
        val entry = sampleTrashEntryUiModel(
            id = "e1",
            type = TrashEntryType.MEDIA_REMOVED,
            sourcePostId = null,
            relatedPostIds = emptyList(),
        )
        val line = realTrashEntrySourceLine(entry)
        assertTrue(line.contains("来源 当前小相册"))
    }

    @Test
    fun entrySourceLine_mediaSystemDeletedCountsRelatedPosts() {
        val entry = sampleTrashEntryUiModel(
            id = "e1",
            type = TrashEntryType.MEDIA_SYSTEM_DELETED,
            relatedPostIds = listOf("p1", "p2", "p3"),
        )
        val line = realTrashEntrySourceLine(entry)
        assertTrue(line.contains("媒体删除"))
        assertTrue(line.contains("影响小相册 3 个"))
    }

    // ==================== pendingCleanupDeleteCopy ====================

    @Test
    fun pendingCleanupDeleteCopy_mediaSystemDeletedMentionsFiles() {
        val entry = sampleTrashEntryUiModel(type = TrashEntryType.MEDIA_SYSTEM_DELETED)
        val copy = pendingCleanupDeleteCopy(entry)
        assertTrue(copy.contains("原文件"))
        assertTrue(copy.contains("预览文件"))
    }

    @Test
    fun pendingCleanupDeleteCopy_largeAlbumDeletedPreservesMedia() {
        val entry = sampleTrashEntryUiModel(type = TrashEntryType.LARGE_ALBUM_DELETED)
        val copy = pendingCleanupDeleteCopy(entry)
        assertTrue(copy.contains("大相册"))
        assertTrue(copy.contains("媒体本体保留"))
    }

    @Test
    fun pendingCleanupDeleteCopy_smallAlbumDeletedPreservesMedia() {
        val entry = sampleTrashEntryUiModel(type = TrashEntryType.SMALL_ALBUM_DELETED)
        val copy = pendingCleanupDeleteCopy(entry)
        assertTrue(copy.contains("小相册结构"))
        assertTrue(copy.contains("媒体本体保留"))
    }

    @Test
    fun pendingCleanupDeleteCopy_mediaRemovedOnlyDeletesRelation() {
        val entry = sampleTrashEntryUiModel(type = TrashEntryType.MEDIA_REMOVED)
        val copy = pendingCleanupDeleteCopy(entry)
        assertTrue(copy.contains("关系删除"))
        assertTrue(copy.contains("媒体本体保留"))
    }

    // ==================== toTrashStateLabel ====================

    @Test
    fun trashStateLabel_nullReturnsInTrash() {
        assertEquals("在回收站", null.toTrashStateLabel())
    }

    @Test
    fun trashStateLabel_emptyStringReturnsInTrash() {
        assertEquals("在回收站", "".toTrashStateLabel())
    }

    @Test
    fun trashStateLabel_blankStringReturnsInTrash() {
        assertEquals("在回收站", "   ".toTrashStateLabel())
    }

    @Test
    fun trashStateLabel_intrashReturnsInTrash() {
        assertEquals("在回收站", "intrash".toTrashStateLabel())
    }

    @Test
    fun trashStateLabel_inTrashCamelCaseReturnsInTrash() {
        assertEquals("在回收站", "inTrash".toTrashStateLabel())
    }

    @Test
    fun trashStateLabel_inUnderscoreTrashReturnsInTrash() {
        assertEquals("在回收站", "in_trash".toTrashStateLabel())
    }

    @Test
    fun trashStateLabel_pendingcleanupReturnsPending() {
        assertEquals("待清理", "pendingcleanup".toTrashStateLabel())
    }

    @Test
    fun trashStateLabel_pendingUnderscoreReturnsPending() {
        assertEquals("待清理", "pending_cleanup".toTrashStateLabel())
    }

    @Test
    fun trashStateLabel_restoredReturnsRestored() {
        assertEquals("已恢复", "restored".toTrashStateLabel())
    }

    @Test
    fun trashStateLabel_purgedReturnsDeleted() {
        assertEquals("已删除", "purged".toTrashStateLabel())
    }

    @Test
    fun trashStateLabel_deletedReturnsDeleted() {
        assertEquals("已删除", "deleted".toTrashStateLabel())
    }

    @Test
    fun trashStateLabel_unknownValueDefaultsToInTrash() {
        assertEquals("在回收站", "unknown_state".toTrashStateLabel())
    }

    // ==================== realTrashGridPostTitle ====================

    @Test
    fun gridPostTitle_usesMediaSnapshotSourcePostTitleWhenPresent() {
        val entry = sampleTrashEntryUiModel(
            mediaSnapshot = sampleTrashMediaSnapshot(sourcePostTitle = "媒体来源小相册标题"),
        ).copy(title = "标题A", sourcePostId = "post-A")
        assertEquals("媒体来源小相册标题", realTrashGridPostTitle(entry))
    }

    @Test
    fun gridPostTitle_fallsBackToEntryTitleWhenSnapshotTitleBlank() {
        val entry = sampleTrashEntryUiModel(
            mediaSnapshot = sampleTrashMediaSnapshot(sourcePostTitle = ""),
        ).copy(title = "标题B", sourcePostId = "post-B")
        assertEquals("标题B", realTrashGridPostTitle(entry))
    }

    @Test
    fun gridPostTitle_fallsBackToEntryTitleWhenSnapshotAbsent() {
        val entry = sampleTrashEntryUiModel(
            mediaSnapshot = null,
        ).copy(title = "标题C", sourcePostId = "post-C")
        assertEquals("标题C", realTrashGridPostTitle(entry))
    }

    @Test
    fun gridPostTitle_fallsBackToSourcePostIdWhenTitleBlank() {
        val entry = sampleTrashEntryUiModel(
            mediaSnapshot = null,
        ).copy(title = "", sourcePostId = "post-D")
        assertEquals("post-D", realTrashGridPostTitle(entry))
    }

    @Test
    fun gridPostTitle_returnsDefaultWhenAllBlank() {
        val entry = sampleTrashEntryUiModel(
            mediaSnapshot = null,
        ).copy(title = "", sourcePostId = "")
        assertEquals("来源小相册", realTrashGridPostTitle(entry))
    }

    @Test
    fun gridPostTitle_returnsDefaultWhenAllNull() {
        val entry = sampleTrashEntryUiModel(
            mediaSnapshot = null,
        ).copy(title = "", sourcePostId = null)
        assertEquals("来源小相册", realTrashGridPostTitle(entry))
    }

    // ==================== previewMediaIds ====================

    @Test
    fun previewMediaIds_returnsEmptyWhenAllAbsent() {
        val entry = sampleTrashEntryUiModel(
            commentTargetMediaId = null,
            sourceMediaId = null,
            relatedMediaIds = emptyList(),
        )
        assertTrue(entry.previewMediaIds().isEmpty())
    }

    @Test
    fun previewMediaIds_includesCommentTargetFirst() {
        val entry = sampleTrashEntryUiModel(
            commentTargetMediaId = "c1",
            sourceMediaId = "s1",
            relatedMediaIds = listOf("r1"),
        )
        assertEquals(listOf("c1", "s1", "r1"), entry.previewMediaIds())
    }

    @Test
    fun previewMediaIds_skipsBlankValues() {
        val entry = sampleTrashEntryUiModel(
            commentTargetMediaId = "",
            sourceMediaId = "s1",
            relatedMediaIds = listOf("", "r1", "  "),
        )
        assertEquals(listOf("s1", "r1"), entry.previewMediaIds())
    }

    @Test
    fun previewMediaIds_deduplicatesRepeatedIds() {
        val entry = sampleTrashEntryUiModel(
            commentTargetMediaId = "m1",
            sourceMediaId = "m1",
            relatedMediaIds = listOf("m1", "m2"),
        )
        assertEquals(listOf("m1", "m2"), entry.previewMediaIds())
    }

    @Test
    fun previewMediaIds_returnsOnlyRelatedWhenOthersNull() {
        val entry = sampleTrashEntryUiModel(
            commentTargetMediaId = null,
            sourceMediaId = null,
            relatedMediaIds = listOf("m1", "m2", "m3"),
        )
        assertEquals(listOf("m1", "m2", "m3"), entry.previewMediaIds())
    }

    @Test
    fun previewMediaIds_returnsCommentTargetAloneWhenOnlyPresent() {
        val entry = sampleTrashEntryUiModel(
            commentTargetMediaId = "c1",
            sourceMediaId = null,
            relatedMediaIds = emptyList(),
        )
        assertEquals(listOf("c1"), entry.previewMediaIds())
    }

    // ==================== commentMediaId ====================

    @Test
    fun commentMediaId_returnsCommentTargetWhenPresent() {
        val entry = sampleTrashEntryUiModel(
            commentTargetMediaId = "c1",
            sourceMediaId = "s1",
        )
        assertEquals("c1", entry.commentMediaId())
    }

    @Test
    fun commentMediaId_fallsBackToSourceMediaId() {
        val entry = sampleTrashEntryUiModel(
            commentTargetMediaId = null,
            sourceMediaId = "s1",
        )
        assertEquals("s1", entry.commentMediaId())
    }

    @Test
    fun commentMediaId_fallsBackToMediaSnapshotMediaId() {
        val entry = sampleTrashEntryUiModel(
            commentTargetMediaId = null,
            sourceMediaId = null,
            mediaSnapshot = sampleTrashMediaSnapshot(mediaId = "snap-1"),
        )
        assertEquals("snap-1", entry.commentMediaId())
    }

    @Test
    fun commentMediaId_fallsBackToFirstRelatedMediaId() {
        val entry = sampleTrashEntryUiModel(
            commentTargetMediaId = null,
            sourceMediaId = null,
            mediaSnapshot = null,
            relatedMediaIds = listOf("r1", "r2"),
        )
        assertEquals("r1", entry.commentMediaId())
    }

    @Test
    fun commentMediaId_returnsNullWhenAllAbsent() {
        val entry = sampleTrashEntryUiModel(
            commentTargetMediaId = null,
            sourceMediaId = null,
            mediaSnapshot = null,
            relatedMediaIds = emptyList(),
        )
        assertEquals(null, entry.commentMediaId())
    }

    // ==================== isRealMediaTrashType ====================

    @Test
    fun isRealMediaTrashType_mediaSystemDeletedReturnsTrue() {
        assertTrue(TrashEntryType.MEDIA_SYSTEM_DELETED.isRealMediaTrashType())
    }

    @Test
    fun isRealMediaTrashType_mediaRemovedReturnsTrue() {
        assertTrue(TrashEntryType.MEDIA_REMOVED.isRealMediaTrashType())
    }

    @Test
    fun isRealMediaTrashType_largeAlbumDeletedReturnsFalse() {
        assertEquals(false, TrashEntryType.LARGE_ALBUM_DELETED.isRealMediaTrashType())
    }

    @Test
    fun isRealMediaTrashType_smallAlbumDeletedReturnsFalse() {
        assertEquals(false, TrashEntryType.SMALL_ALBUM_DELETED.isRealMediaTrashType())
    }

    // ==================== viewerVideoDurationMillis ====================

    @Test
    fun viewerVideoDurationMillis_returnsActualWhenPresent() {
        val snapshot = sampleTrashMediaSnapshot(
            mediaType = AppMediaType.VIDEO,
            videoDurationMillis = 45_000L,
        )
        assertEquals(45_000L, snapshot.viewerVideoDurationMillis())
    }

    @Test
    fun viewerVideoDurationMillis_returnsDefaultWhenNull() {
        val snapshot = sampleTrashMediaSnapshot(
            mediaType = AppMediaType.VIDEO,
            videoDurationMillis = null,
        )
        assertEquals(18_000L, snapshot.viewerVideoDurationMillis())
    }
}
