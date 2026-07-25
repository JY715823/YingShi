package com.example.yingshi.feature.life.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import android.util.Log

object PushNotificationChannels {
    /**
     * v12: 强制重建通道，解决 MIUI 上用户手动降低重要程度后无法恢复的问题。
     *
     * Android 通道"锁定"机制：当用户在系统设置中手动修改通道的重要程度后，
     * 系统会"锁定"该通道的设置。即使 App 删除并重建同名通道，系统也会恢复
     * 用户之前设置的（降低后的）重要程度。唯一解决方法是使用新的通道 ID。
     *
     * v11 通道可能已被部分用户手动降低，导致无振动、无悬浮通知。
     * v12 使用全新 ID，确保所有设置恢复为 App 指定的默认值。
     */
    const val SHARED_UPDATES_CHANNEL_ID = "yingshi_shared_updates_heads_up_v12"
    const val LIFE_TRACE_CHANNEL_ID = "yingshi_life_trace_v12"

    // 历史通道 ID，升级时清理
    private val DEPRECATED_CHANNEL_IDS = listOf(
        "yingshi_shared_updates_heads_up_v8",
        "yingshi_life_trace_v8",
        "yingshi_shared_updates_heads_up_v9",
        "yingshi_life_trace_v9",
        "yingshi_shared_updates_heads_up_v10",
        "yingshi_life_trace_v10",
        "yingshi_shared_updates_heads_up_v11",
        "yingshi_life_trace_v11",
    )

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isMiui = isMiuiDevice()

        // --- 通用通道（振动 + 悬浮通知） ---
        manager.getNotificationChannel(SHARED_UPDATES_CHANNEL_ID)?.let { existing ->
            if (needsRecreation(existing)) {
                Log.e("PushChannels", "General channel config wrong, recreating: importance=${existing.importance}, vibrate=${existing.shouldVibrate()}, sound=${existing.sound}")
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
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        manager.createNotificationChannel(generalChannel)

        // --- 今日痕迹通道（振动 + 悬浮通知） ---
        manager.getNotificationChannel(LIFE_TRACE_CHANNEL_ID)?.let { existing ->
            if (needsRecreation(existing)) {
                Log.e("PushChannels", "Trace channel config wrong, recreating: importance=${existing.importance}, vibrate=${existing.shouldVibrate()}, sound=${existing.sound}")
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
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        manager.createNotificationChannel(traceChannel)

        // --- 清理废弃通道 ---
        DEPRECATED_CHANNEL_IDS.forEach { oldId ->
            manager.getNotificationChannel(oldId)?.let {
                Log.e("PushChannels", "Cleaning up deprecated channel: $oldId")
                manager.deleteNotificationChannel(oldId)
            }
        }

        // --- 诊断日志：验证通道最终配置 ---
        val general = manager.getNotificationChannel(SHARED_UPDATES_CHANNEL_ID)
        val trace = manager.getNotificationChannel(LIFE_TRACE_CHANNEL_ID)
        Log.e("PushChannels", ">>> Device: MIUI=$isMiui, manufacturer=${Build.MANUFACTURER}, model=${Build.MODEL}")
        Log.e("PushChannels", ">>> General: id=${general?.id}, importance=${general?.importance}, vibrate=${general?.shouldVibrate()}, pattern=${general?.vibrationPattern?.toList()}, sound=${general?.sound}, lockscreen=${general?.lockscreenVisibility}")
        Log.e("PushChannels", ">>> Trace: id=${trace?.id}, importance=${trace?.importance}, vibrate=${trace?.shouldVibrate()}, pattern=${trace?.vibrationPattern?.toList()}, sound=${trace?.sound}, lockscreen=${trace?.lockscreenVisibility}")

        // 检测通道重要程度是否被系统/用户降低
        if (general != null && general.importance < NotificationManager.IMPORTANCE_HIGH) {
            Log.e("PushChannels", "!!! WARNING: General channel importance=${general.importance} < IMPORTANCE_HIGH(${NotificationManager.IMPORTANCE_HIGH}). " +
                "Heads-up and vibration will NOT work. User must enable in system settings.")
        }
        if (trace != null && trace.importance < NotificationManager.IMPORTANCE_HIGH) {
            Log.e("PushChannels", "!!! WARNING: Trace channel importance=${trace.importance} < IMPORTANCE_HIGH(${NotificationManager.IMPORTANCE_HIGH}). " +
                "Heads-up and vibration will NOT work. User must enable in system settings.")
        }
    }

    /**
     * 判断通道是否需要重建。
     *
     * MIUI 上 getLockscreenVisibility() 始终返回 -1000（非标准值），
     * 不能作为重建依据，否则通道会被反复删除重建，触发 Android 通道锁定机制
     * （恢复用户之前降低的设置），反而导致震动/悬浮失效。
     *
     * 只检查真正影响震动/悬浮的关键设置：importance、vibrate、sound。
     */
    private fun needsRecreation(channel: NotificationChannel): Boolean {
        return channel.importance < NotificationManager.IMPORTANCE_HIGH ||
            !channel.shouldVibrate() ||
            channel.sound == null
    }

    /**
     * 检测是否为 MIUI 设备。
     * 小米和红米的 Build.MANUFACTURER 均为 "Xiaomi"。
     */
    private fun isMiuiDevice(): Boolean {
        return Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)
    }

    /**
     * 打开 App 的通知设置页面。
     * 用户可在此检查/启用：
     * - 通道重要程度（需为"高"才能悬浮通知）
     * - MIUI 特有的"悬浮通知"开关
     * - 振动权限
     */
    fun openNotificationSettings(context: Context) {
        runCatching {
            val intent = android.content.Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure { e ->
            Log.e("PushChannels", "Failed to open notification settings: ${e.message}")
        }
    }
}
