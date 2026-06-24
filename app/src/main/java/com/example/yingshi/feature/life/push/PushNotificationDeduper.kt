package com.example.yingshi.feature.life.push

import android.content.Context

object PushNotificationDeduper {
    private const val PREFS_NAME = "yingshi_push_notification_deduper"
    private const val TTL_MILLIS = 24 * 60 * 60 * 1000L

    @Synchronized
    fun shouldShow(context: Context, key: String): Boolean {
        val normalized = key.trim().takeIf { it.isNotBlank() } ?: return true
        val now = System.currentTimeMillis()
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastShownAt = prefs.getLong(normalized, 0L)
        if (now - lastShownAt in 0 until TTL_MILLIS) {
            return false
        }
        prefs.edit()
            .putLong(normalized, now)
            .apply()
        return true
    }
}
