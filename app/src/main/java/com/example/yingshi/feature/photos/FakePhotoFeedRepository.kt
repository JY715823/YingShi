package com.example.yingshi.feature.photos

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import java.util.Calendar
import java.util.Locale

object FakePhotoFeedRepository {
    private val hiddenMediaIds = mutableStateListOf<String>()
    private val hiddenPostIds = mutableStateListOf<String>()
    private val importedEntries = mutableStateListOf<PhotoFeedSourceEntry>()
    private val baseSourceEntriesByMediaId: Map<String, List<PhotoFeedSourceEntry>> by lazy {
        fakeSourceEntries().groupBy { it.mediaId }
    }

    fun getPhotoFeed(): List<PhotoFeedItem> {
        return sourceEntriesByMediaId()
            .values
            .asSequence()
            .mapNotNull { entries ->
                val latestEntry = entries.maxBy { it.mediaDisplayTimeMillis }
                if (hiddenMediaIds.contains(latestEntry.mediaId)) {
                    return@mapNotNull null
                }
                val parts = dateParts(latestEntry.mediaDisplayTimeMillis)

                PhotoFeedItem(
                    mediaId = latestEntry.mediaId,
                    mediaDisplayTimeMillis = latestEntry.mediaDisplayTimeMillis,
                    displayYear = parts.year,
                    displayMonth = parts.month,
                    displayDay = parts.day,
                    commentCount = placeholderCommentCount(latestEntry.mediaId),
                    postIds = entries
                        .mapNotNull { it.postId }
                        .filterNot { hiddenPostIds.contains(it) }
                        .distinct(),
                    palette = latestEntry.palette,
                    aspectRatio = latestEntry.aspectRatio,
                    width = latestEntry.width,
                    height = latestEntry.height,
                )
            }
            .toList()
            .sortedByDescending { it.mediaDisplayTimeMillis }
    }

    fun importSystemMediaToFeed(
        mediaItems: List<SystemMediaItem>,
        postId: String,
    ) {
        mediaItems
            .distinctBy { it.id }
            .forEach { item ->
                val duplicate = importedEntries.any { entry ->
                    entry.mediaId == item.id && entry.postId == postId
                }
                if (!duplicate) {
                    importedEntries.add(
                        PhotoFeedSourceEntry(
                            mediaId = item.id,
                            mediaDisplayTimeMillis = item.displayTimeMillis,
                            postId = postId,
                            palette = item.palette,
                            aspectRatio = item.aspectRatio,
                            width = item.width,
                            height = item.height,
                        ),
                    )
                }
            }
    }

    fun hideMediaGlobally(mediaIds: Collection<String>) {
        mediaIds.forEach { mediaId ->
            if (!hiddenMediaIds.contains(mediaId)) {
                hiddenMediaIds.add(mediaId)
            }
        }
    }

    fun unhideMediaGlobally(mediaIds: Collection<String>) {
        hiddenMediaIds.removeAll { mediaId -> mediaIds.contains(mediaId) }
    }

    fun hidePostsLocally(postIds: Collection<String>) {
        postIds.forEach { postId ->
            if (!hiddenPostIds.contains(postId)) {
                hiddenPostIds.add(postId)
            }
        }
    }

    fun unhidePostsLocally(postIds: Collection<String>) {
        hiddenPostIds.removeAll { postId -> postIds.contains(postId) }
    }

    fun isMediaHidden(mediaId: String): Boolean = hiddenMediaIds.contains(mediaId)

    fun currentHiddenMediaIds(): Set<String> = hiddenMediaIds.toSet()

    private fun sourceEntriesByMediaId(): Map<String, List<PhotoFeedSourceEntry>> {
        if (importedEntries.isEmpty()) {
            return baseSourceEntriesByMediaId
        }
        return (baseSourceEntriesByMediaId.values.flatten() + importedEntries)
            .groupBy { it.mediaId }
    }

    private fun placeholderCommentCount(mediaId: String): Int {
        return FakeCommentRepository.mediaCommentCount(mediaId)
    }

    private fun fakeSourceEntries(): List<PhotoFeedSourceEntry> {
        return buildList {
            feedSeeds.forEach { seed ->
                val postIds = seed.postIds.ifEmpty { listOf(null) }
                postIds.forEach { postId ->
                    add(
                        PhotoFeedSourceEntry(
                            mediaId = seed.mediaId,
                            mediaDisplayTimeMillis = millisOf(
                                year = seed.year,
                                month = seed.month,
                                day = seed.day,
                                hour = seed.hour,
                                minute = seed.minute,
                            ),
                            postId = postId,
                            palette = seed.palette,
                            aspectRatio = seed.aspectRatio,
                            width = seed.width,
                            height = seed.height,
                        ),
                    )
                }
            }
        }
    }

    private data class FeedSeed(
        val mediaId: String,
        val year: Int,
        val month: Int,
        val day: Int,
        val hour: Int,
        val minute: Int,
        val postIds: List<String>,
        val palette: PhotoThumbnailPalette,
        val aspectRatio: Float = 1f,
        val width: Int? = null,
        val height: Int? = null,
    )

    private data class DateParts(
        val year: Int,
        val month: Int,
        val day: Int,
    )

    private fun dateParts(timeMillis: Long): DateParts {
        val calendar = Calendar.getInstance(Locale.CHINA).apply {
            timeInMillis = timeMillis
        }
        return DateParts(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            day = calendar.get(Calendar.DAY_OF_MONTH),
        )
    }

    private fun millisOf(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long {
        return Calendar.getInstance(Locale.CHINA).run {
            clear()
            set(year, month - 1, day, hour, minute, 0)
            timeInMillis
        }
    }

    private val blueMist = PhotoThumbnailPalette(
        start = Color(0xFFB8D8F8),
        end = Color(0xFF7EA6DF),
        accent = Color(0xFFE8F2FF),
    )
    private val dawnPeach = PhotoThumbnailPalette(
        start = Color(0xFFF5D2C3),
        end = Color(0xFFE7A08D),
        accent = Color(0xFFFFF0E8),
    )
    private val grassHaze = PhotoThumbnailPalette(
        start = Color(0xFFCFE5B9),
        end = Color(0xFF84B38A),
        accent = Color(0xFFEFF8E1),
    )
    private val twilightLavender = PhotoThumbnailPalette(
        start = Color(0xFFD8D0F2),
        end = Color(0xFF8FA0D8),
        accent = Color(0xFFF0EDFF),
    )
    private val teaBrown = PhotoThumbnailPalette(
        start = Color(0xFFE7CFB4),
        end = Color(0xFFB98B63),
        accent = Color(0xFFF7E8D4),
    )
    private val rainSlate = PhotoThumbnailPalette(
        start = Color(0xFFC5D1DA),
        end = Color(0xFF8095A7),
        accent = Color(0xFFE7F0F6),
    )

    private val feedSeeds = listOf(
        FeedSeed("media-2026-04-20-a", 2026, 4, 20, 10, 6, emptyList(), rainSlate, aspectRatio = 0.8f),
        FeedSeed("media-2026-04-18-a", 2026, 4, 18, 21, 12, listOf("post-night-walk", "post-april-window"), blueMist),
        FeedSeed("media-2026-04-18-b", 2026, 4, 18, 20, 44, listOf("post-night-walk"), twilightLavender),
        FeedSeed(
            "media-2026-04-18-c",
            2026,
            4,
            18,
            18,
            16,
            listOf("post-april-window"),
            teaBrown,
            aspectRatio = 0.92f,
            width = 1080,
            height = 3240,
        ),
        FeedSeed("media-2026-04-12-a", 2026, 4, 12, 16, 25, listOf("post-sunday-brunch"), dawnPeach),
        FeedSeed("media-2026-04-12-b", 2026, 4, 12, 15, 10, listOf("post-sunday-brunch", "post-flower-table"), grassHaze),
        FeedSeed("media-2026-04-03-a", 2026, 4, 3, 9, 48, listOf("post-morning-metro"), rainSlate),
        FeedSeed("media-2026-03-30-a", 2026, 3, 30, 22, 8, listOf("post-late-return"), blueMist),
        FeedSeed("media-2026-03-30-b", 2026, 3, 30, 21, 46, listOf("post-late-return", "post-river-night"), rainSlate),
        FeedSeed("media-2026-03-14-a", 2026, 3, 14, 14, 36, listOf("post-white-shirt-day"), teaBrown),
        FeedSeed("media-2026-03-14-b", 2026, 3, 14, 12, 5, listOf("post-white-shirt-day"), dawnPeach),
        FeedSeed("media-2026-03-05-a", 2026, 3, 5, 8, 32, listOf("post-quiet-commute"), rainSlate),
        FeedSeed("media-2026-02-25-a", 2026, 2, 25, 19, 28, listOf("post-lantern-night"), twilightLavender),
        FeedSeed("media-2026-02-25-b", 2026, 2, 25, 18, 53, listOf("post-lantern-night"), blueMist),
        FeedSeed("media-2026-02-11-a", 2026, 2, 11, 13, 7, listOf("post-noodle-lunch", "post-snow-light"), dawnPeach),
        FeedSeed("media-2026-02-11-b", 2026, 2, 11, 12, 40, listOf("post-noodle-lunch"), teaBrown),
        FeedSeed("media-2026-01-01-a", 2026, 1, 1, 0, 14, listOf("post-new-year"), twilightLavender),
        FeedSeed("media-2026-01-01-b", 2026, 1, 1, 0, 5, listOf("post-new-year", "post-fireworks"), blueMist),
        FeedSeed("media-2025-12-31-a", 2025, 12, 31, 23, 31, listOf("post-year-end"), rainSlate),
        FeedSeed("media-2025-12-24-a", 2025, 12, 24, 20, 22, listOf("post-christmas-eve", "post-home-lights"), dawnPeach),
        FeedSeed("media-2025-12-24-b", 2025, 12, 24, 19, 55, listOf("post-home-lights"), teaBrown),
        FeedSeed("media-2025-11-02-a", 2025, 11, 2, 17, 45, listOf("post-autumn-park"), grassHaze),
        FeedSeed(
            "media-2025-11-02-b",
            2025,
            11,
            2,
            16,
            16,
            listOf("post-autumn-park", "post-long-road"),
            blueMist,
            aspectRatio = 0.88f,
            width = 1242,
            height = 3600,
        ),
        FeedSeed("media-2025-10-19-a", 2025, 10, 19, 10, 2, listOf("post-quiet-sunday"), rainSlate),
        FeedSeed("media-2025-08-08-a", 2025, 8, 8, 19, 14, listOf("post-summer-rain"), blueMist),
        FeedSeed("media-2025-08-08-b", 2025, 8, 8, 18, 51, listOf("post-summer-rain", "post-balcony-wind"), grassHaze),
        FeedSeed("media-2025-07-01-a", 2025, 7, 1, 7, 28, listOf("post-july-start"), dawnPeach),
        FeedSeed("media-2025-05-20-a", 2025, 5, 20, 21, 9, listOf("post-may-memory", "post-letter-night"), twilightLavender),
        FeedSeed("media-2025-05-20-b", 2025, 5, 20, 20, 42, listOf("post-may-memory"), teaBrown),
        FeedSeed("media-2025-03-09-a", 2025, 3, 9, 11, 34, listOf("post-market-day"), grassHaze),
        FeedSeed("media-2024-10-03-a", 2024, 10, 3, 15, 50, listOf("post-october-trip"), rainSlate),
        FeedSeed("media-2024-10-03-b", 2024, 10, 3, 15, 12, listOf("post-october-trip", "post-bridge-light"), blueMist),
        FeedSeed("media-2024-09-21-a", 2024, 9, 21, 22, 11, listOf("post-rooftop-night"), twilightLavender),
        FeedSeed(
            "media-2024-09-21-b",
            2024,
            9,
            21,
            21,
            37,
            listOf("post-rooftop-night"),
            rainSlate,
            aspectRatio = 0.9f,
            width = 1170,
            height = 3280,
        ),
        FeedSeed("media-2024-06-15-a", 2024, 6, 15, 14, 18, listOf("post-lake-afternoon", "post-june-sun"), grassHaze),
        FeedSeed("media-2024-06-15-b", 2024, 6, 15, 13, 55, listOf("post-lake-afternoon"), blueMist),
        FeedSeed("media-2024-04-02-a", 2024, 4, 2, 9, 14, listOf("post-early-spring"), dawnPeach),
        FeedSeed("media-2024-01-01-a", 2024, 1, 1, 8, 20, listOf("post-first-sunrise"), teaBrown),
        FeedSeed("media-2024-01-01-b", 2024, 1, 1, 7, 58, listOf("post-first-sunrise", "post-new-calendar"), blueMist),
    )
}
