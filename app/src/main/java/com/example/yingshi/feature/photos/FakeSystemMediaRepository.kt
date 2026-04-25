package com.example.yingshi.feature.photos

import androidx.compose.ui.graphics.Color
import java.util.Calendar
import java.util.Locale

object FakeSystemMediaRepository {
    fun getMedia(filter: SystemMediaFilter): List<SystemMediaItem> {
        val items = systemMediaSeeds
            .map { seed ->
                val displayTimeMillis = millisOf(
                    year = seed.year,
                    month = seed.month,
                    day = seed.day,
                    hour = seed.hour,
                    minute = seed.minute,
                )
                SystemMediaItem(
                    id = seed.id,
                    displayTimeMillis = displayTimeMillis,
                    displayYear = seed.year,
                    displayMonth = seed.month,
                    displayDay = seed.day,
                    palette = seed.palette,
                    aspectRatio = seed.aspectRatio,
                    kind = seed.kind,
                    linkedPostIds = seed.linkedPostIds,
                )
            }
            .sortedByDescending { it.displayTimeMillis }

        return when (filter) {
            SystemMediaFilter.ALL -> items
            SystemMediaFilter.CAMERA -> items.filter { it.kind == SystemMediaKind.CAMERA }
            SystemMediaFilter.SCREENSHOT -> items.filter { it.kind == SystemMediaKind.SCREENSHOT }
            SystemMediaFilter.VIDEO -> items.filter { it.kind == SystemMediaKind.VIDEO }
            SystemMediaFilter.POSTED -> items.filter { it.linkedPostIds.isNotEmpty() }
            SystemMediaFilter.UNPOSTED -> items.filter { it.linkedPostIds.isEmpty() }
        }
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

    private data class SystemMediaSeed(
        val id: String,
        val year: Int,
        val month: Int,
        val day: Int,
        val hour: Int,
        val minute: Int,
        val kind: SystemMediaKind,
        val linkedPostIds: List<String>,
        val palette: PhotoThumbnailPalette,
        val aspectRatio: Float,
    )

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

    private val systemMediaSeeds = listOf(
        SystemMediaSeed("system-2026-04-25-camera-a", 2026, 4, 25, 18, 45, SystemMediaKind.CAMERA, listOf("post-night-walk"), blueMist, 0.82f),
        SystemMediaSeed("system-2026-04-25-screenshot-a", 2026, 4, 25, 15, 22, SystemMediaKind.SCREENSHOT, emptyList(), rainSlate, 0.56f),
        SystemMediaSeed("system-2026-04-24-video-a", 2026, 4, 24, 21, 7, SystemMediaKind.VIDEO, listOf("post-april-window"), twilightLavender, 0.7f),
        SystemMediaSeed("system-2026-04-22-camera-a", 2026, 4, 22, 9, 18, SystemMediaKind.CAMERA, emptyList(), dawnPeach, 1.1f),
        SystemMediaSeed("system-2026-04-20-camera-a", 2026, 4, 20, 11, 3, SystemMediaKind.CAMERA, listOf("post-sunday-brunch"), grassHaze, 0.8f),
        SystemMediaSeed("system-2026-04-18-video-a", 2026, 4, 18, 22, 11, SystemMediaKind.VIDEO, emptyList(), teaBrown, 0.72f),
        SystemMediaSeed("system-2026-04-17-screenshot-a", 2026, 4, 17, 13, 40, SystemMediaKind.SCREENSHOT, emptyList(), blueMist, 0.56f),
        SystemMediaSeed("system-2026-04-15-camera-a", 2026, 4, 15, 8, 10, SystemMediaKind.CAMERA, listOf("post-morning-metro"), rainSlate, 1.05f),
        SystemMediaSeed("system-2026-04-12-camera-a", 2026, 4, 12, 16, 21, SystemMediaKind.CAMERA, listOf("post-flower-table"), dawnPeach, 0.9f),
        SystemMediaSeed("system-2026-04-10-screenshot-a", 2026, 4, 10, 20, 3, SystemMediaKind.SCREENSHOT, emptyList(), twilightLavender, 0.56f),
        SystemMediaSeed("system-2026-04-03-camera-a", 2026, 4, 3, 10, 2, SystemMediaKind.CAMERA, emptyList(), teaBrown, 1.2f),
        SystemMediaSeed("system-2026-03-30-video-a", 2026, 3, 30, 22, 31, SystemMediaKind.VIDEO, listOf("post-river-night"), blueMist, 0.7f),
        SystemMediaSeed("system-2026-03-14-camera-a", 2026, 3, 14, 14, 9, SystemMediaKind.CAMERA, listOf("post-white-shirt-day"), grassHaze, 0.84f),
        SystemMediaSeed("system-2026-02-25-camera-a", 2026, 2, 25, 19, 14, SystemMediaKind.CAMERA, emptyList(), rainSlate, 0.82f),
        SystemMediaSeed("system-2026-02-11-screenshot-a", 2026, 2, 11, 12, 58, SystemMediaKind.SCREENSHOT, listOf("post-noodle-lunch"), teaBrown, 0.56f),
        SystemMediaSeed("system-2026-01-01-camera-a", 2026, 1, 1, 0, 8, SystemMediaKind.CAMERA, listOf("post-new-year"), twilightLavender, 1.0f),
    )
}
