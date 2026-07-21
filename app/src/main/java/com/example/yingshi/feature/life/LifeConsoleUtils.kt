package com.example.yingshi.feature.life

import com.example.yingshi.data.model.RemoteMedia
import java.time.Instant
import java.time.ZoneId

/** 生活模块统一时区常量 */
const val LIFE_CONSOLE_ZONE_ID = "Asia/Shanghai"

/**
 * 共享工具函数：消除 LifeConsoleWidgetStore / LifeConsoleWidgetProvider /
 * LifeMediaQuickViewerActivity / LifeConsoleScreen 之间的重复代码。
 */
fun RemoteMedia.isVideo(): Boolean {
    return mediaType.equals("video", ignoreCase = true) ||
        mimeType?.startsWith("video/", ignoreCase = true) == true ||
        !videoUrl.isNullOrBlank()
}

fun looksLikeVideoUrl(url: String): Boolean {
    val lower = url.substringBefore('?').lowercase()
    return listOf(".mp4", ".mov", ".m4v", ".webm", ".avi", ".mkv").any(lower::endsWith)
}

fun firstUsableImageUrl(vararg urls: String?): String? {
    return urls.firstOrNull { url ->
        val normalized = url?.trim()
        !normalized.isNullOrBlank() && !looksLikeVideoUrl(normalized)
    }?.trim()
}

fun formatTime(timeMillis: Long, zoneId: String = LIFE_CONSOLE_ZONE_ID): String {
    val time = Instant.ofEpochMilli(timeMillis).atZone(ZoneId.of(zoneId))
    return String.format("%02d:%02d", time.hour, time.minute)
}

/**
 * Round 7: 完整日期时间格式化 (历史页媒体卡片/大便事件展示)
 * 返回 "yyyy-MM-dd HH:mm" 格式。
 */
fun formatFullTime(timeMillis: Long, zoneId: String = LIFE_CONSOLE_ZONE_ID): String {
    val time = Instant.ofEpochMilli(timeMillis).atZone(ZoneId.of(zoneId))
    return String.format(
        "%04d-%02d-%02d %02d:%02d",
        time.year,
        time.monthValue,
        time.dayOfMonth,
        time.hour,
        time.minute,
    )
}
