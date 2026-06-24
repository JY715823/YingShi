package com.example.yingshi.feature.life.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object PushNotificationChannels {
    const val SHARED_UPDATES_CHANNEL_ID = "yingshi_shared_updates_v2"

    fun ensureSharedUpdatesChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            SHARED_UPDATES_CHANNEL_ID,
            "映世提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "照片和生活模块的共享提醒"
        }
        manager.createNotificationChannel(channel)
    }
}
