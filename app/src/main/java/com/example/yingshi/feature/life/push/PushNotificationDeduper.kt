package com.example.yingshi.feature.life.push

import android.content.Context
import android.util.Log

object PushNotificationDeduper {
    private const val TAG = "PushDeduper"
    private const val PREFS_NAME = "yingshi_push_notification_deduper"

    /**
     * Cross-path dedup window (ms).
     * When FCM and sync-fallback deliver the SAME notification (same notificationId),
     * only the first one to arrive should show. 2 minutes covers poll latency + API
     * round-trip with comfortable margin.
     */
    private const val CROSS_PATH_WINDOW_MS = 2 * 60 * 1000L

    // ── Cross-path dedup: per (route + notificationId) ──────────────────
    //
    // PREVIOUS BUG: The claim key was just the route (e.g. "life:trace").
    // Since person_media_added, meal_media_added, bowel_added ALL map to the
    // same route, the 5-minute window blocked ALL subsequent notifications to
    // that route — not just duplicates. This was the primary cause of
    // "notification instability" reported by the user.
    //
    // FIX: Include the notificationId in the claim key. Different events have
    // different notificationIds, so they are NOT blocked by each other. Only
    // the exact same notification delivered via both FCM and sync-fallback is
    // deduplicated.

    /**
     * Attempt to claim the notification slot for a given route + notificationId.
     *
     * @return true if the notification should be shown (slot was available),
     *         false if this exact notification was already shown via the other
     *         delivery path (FCM vs sync-fallback).
     */
    @Synchronized
    fun claimRouteNotification(context: Context, targetRoute: String, notificationId: String): Boolean {
        if (targetRoute.isBlank()) return true
        // Use notificationId to distinguish different events going to the same route.
        // If notificationId is blank, fall back to route-only key (legacy behavior).
        val key = if (notificationId.isNotBlank()) {
            "route_claim:$targetRoute|$notificationId"
        } else {
            "route_claim:$targetRoute"
        }
        val now = System.currentTimeMillis()
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val claimedAt = prefs.getLong(key, 0L)
        if (claimedAt > 0L && (now - claimedAt) < CROSS_PATH_WINDOW_MS) {
            Log.d(TAG, "claimRoute: BLOCKED key=$key (claimed ${now - claimedAt}ms ago)")
            return false
        }
        prefs.edit()
            .putLong(key, now)
            .apply()
        Log.d(TAG, "claimRoute: CLAIMED key=$key")
        return true
    }

    /**
     * Check whether a specific notification was recently claimed.
     * Does NOT record a claim — use claimRouteNotification() for that.
     */
    fun wasNotificationRecentlyClaimed(context: Context, targetRoute: String, notificationId: String): Boolean {
        if (targetRoute.isBlank()) return false
        val key = if (notificationId.isNotBlank()) {
            "route_claim:$targetRoute|$notificationId"
        } else {
            "route_claim:$targetRoute"
        }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val claimedAt = prefs.getLong(key, 0L)
        return claimedAt > 0L && (System.currentTimeMillis() - claimedAt) < CROSS_PATH_WINDOW_MS
    }
}
