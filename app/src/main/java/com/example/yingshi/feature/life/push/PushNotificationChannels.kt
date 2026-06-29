package com.example.yingshi.feature.life.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

object PushNotificationChannels {
    const val SHARED_UPDATES_CHANNEL_ID = "yingshi_shared_updates_heads_up_v4"

    fun ensureSharedUpdatesChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.getNotificationChannel(SHARED_UPDATES_CHANNEL_ID)?.let { existing ->
            if (
                existing.importance < NotificationManager.IMPORTANCE_HIGH ||
                !existing.shouldVibrate()
            ) {
                manager.deleteNotificationChannel(SHARED_UPDATES_CHANNEL_ID)
            } else {
                return
            }
        }
        val channel = NotificationChannel(
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
        manager.createNotificationChannel(channel)
    }
}
