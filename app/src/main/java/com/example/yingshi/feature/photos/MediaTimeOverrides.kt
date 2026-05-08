package com.example.yingshi.feature.photos

/**
 * Session-level cache for media display-time overrides.
 * Written by the viewer's time-editor and read by both FAKE and REAL photo-feed pipelines.
 */
object MediaTimeOverrides {
    private val map = linkedMapOf<String, Long>()

    fun put(mediaId: String, timeMillis: Long) {
        map[mediaId] = timeMillis
    }

    fun get(mediaId: String): Long? = map[mediaId]
}