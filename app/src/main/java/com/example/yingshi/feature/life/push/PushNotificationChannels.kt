package com.example.yingshi.feature.life.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log

object PushNotificationChannels {
    /** Vibrating channel for general notifications (photos, system, etc.) — v9: vibration enabled for all */
    const val SHARED_UPDATES_CHANNEL_ID = "yingshi_shared_updates_heads_up_v9"

    /** Vibrating channel for life trace notifications (人物痕迹 / 吃饭 / 排便) */
    const val LIFE_TRACE_CHANNEL_ID = "yingshi_life_trace_v9"

    // Historical channel IDs that should be cleaned up on upgrade
    private val DEPRECATED_CHANNEL_IDS = listOf(
        "yingshi_shared_updates_heads_up_v8",
        "yingshi_life_trace_v8",
    )

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // --- General channel (vibration enabled, heads-up) ---
        manager.getNotificationChannel(SHARED_UPDATES_CHANNEL_ID)?.let { existing ->
            if (existing.importance < NotificationManager.IMPORTANCE_HIGH) {
                Log.e("PushChannels", "General channel importance too low (${existing.importance}), recreating.")
                manager.deleteNotificationChannel(SHARED_UPDATES_CHANNEL_ID)
            }
        }
        val generalChannel = NotificationChannel(
            SHARED_UPDATES_CHANNEL_ID,
            "映世提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "照片和生活模块的共享提醒"
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 180L, 80L, 180L)
            enableLights(true)
            setShowBadge(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        manager.createNotificationChannel(generalChannel)

        // --- Life trace channel (with vibration, heads-up) ---
        manager.getNotificationChannel(LIFE_TRACE_CHANNEL_ID)?.let { existing ->
            if (
                existing.importance < NotificationManager.IMPORTANCE_HIGH ||
                !existing.shouldVibrate()
            ) {
                Log.e("PushChannels", "Trace channel config wrong (importance=${existing.importance}, vibrate=${existing.shouldVibrate()}), recreating.")
                manager.deleteNotificationChannel(LIFE_TRACE_CHANNEL_ID)
            }
        }
        val traceChannel = NotificationChannel(
            LIFE_TRACE_CHANNEL_ID,
            "今日痕迹提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "人物痕迹和吃饭的振动提醒"
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 180L, 80L, 180L)
            enableLights(true)
            setShowBadge(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        manager.createNotificationChannel(traceChannel)

        // --- Cleanup deprecated channels (v8 and earlier) ---
        DEPRECATED_CHANNEL_IDS.forEach { oldId ->
            manager.getNotificationChannel(oldId)?.let {
                Log.e("PushChannels", "Cleaning up deprecated channel: $oldId")
                manager.deleteNotificationChannel(oldId)
            }
        }

        // Diagnostic: verify channels after creation — use Log.e for visibility
        val general = manager.getNotificationChannel(SHARED_UPDATES_CHANNEL_ID)
        val trace = manager.getNotificationChannel(LIFE_TRACE_CHANNEL_ID)
        Log.e("PushChannels", ">>> General: importance=${general?.importance}, vibrate=${general?.shouldVibrate()}, sound=${general?.sound}")
        Log.e("PushChannels", ">>> Trace: importance=${trace?.importance}, vibrate=${trace?.shouldVibrate()}, pattern=${trace?.vibrationPattern?.toList()}, sound=${trace?.sound}")
    }
}
