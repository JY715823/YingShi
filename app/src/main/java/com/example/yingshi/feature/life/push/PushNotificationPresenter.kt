package com.example.yingshi.feature.life.push

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.yingshi.MainActivity
import com.example.yingshi.R
import com.example.yingshi.app.AppNavigationRequests
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.feature.life.LifePushDispatchActivity
import com.example.yingshi.feature.photos.SettingsRepository
import com.example.yingshi.feature.sync.SyncVersionTracker

object PushNotificationPresenter {
    private const val TAG = "PushNotificationPresenter"

    // 固定通知 ID：新通知替换旧通知，防止多条通知累积被 MIUI 自动分组
    // （分组通知不振动、不显示悬浮卡片）
    private const val LIFE_TRACE_NOTIFICATION_ID = 9001
    private const val SHARED_UPDATES_NOTIFICATION_ID = 9002

    fun show(context: Context, data: Map<String, String>, source: String): Boolean {
        val appContext = context.applicationContext
        val route = data["targetRoute"].orEmpty()
        val category = data["category"].orEmpty()
        val module = data["module"].orEmpty()

        // ── CRITICAL: use Log.e so the message always appears in logcat ──
        // 打印完整 data 用于排查推送内容问题（特别是 body/reason 字段）
        Log.e(TAG, ">>> show() called from=$source module=$module category=$category route=$route " +
            "title=${data["title"]} body=${data["body"]} reason=${data["reason"]} " +
            "mediaId=${data["mediaId"]} notificationId=${data["notificationId"]} " +
            "actorUserId=${data["actorUserId"]} occurredAtMillis=${data["occurredAtMillis"]}")

        if (!canPostNotifications(appContext)) {
            Log.e(TAG, "BLOCKED: cannot post notifications (permission/channel disabled)")
            return false
        }
        if (!SettingsRepository.isPushEnabled(module, category)) {
            Log.e(TAG, "Skip from $source: preference disabled module=$module, category=$category")
            return false
        }
        if (data.isActorCurrentUser()) {
            Log.e(TAG, "Skip from $source: actor is current user.")
            return false
        }

        // ── Cross-path dedup: per (route + notificationId) ────────────────
        // Different events have different notificationIds, so they pass through
        // even if they share the same route. Only the exact same notification
        // delivered via both SSE and FCM is deduplicated (2-min window).
        //
        // P1-3 修复: life 路由使用原始 notificationId 做去重 (不再用 route-only key)。
        // SSE 和 FCM 的 notificationId 相同 (如 "life:bowel_added:timestamp"),
        // 可以正确去重同一事件的 SSE+FCM 重复推送。
        // 通知中心的轮询回退已由 NotificationFallbackNotifier 完全跳过 life 路由,
        // 不存在通知中心 notificationId 与 SSE 不同导致 dedup 失效的问题。
        // 之前用 route-only key 会导致同一 route 的不同事件 (如两次大便) 被误拦截。
        val rawNotificationId = data["notificationId"].orEmpty()
        if (route.isNotBlank() && !PushNotificationDeduper.claimRouteNotification(appContext, route, rawNotificationId)) {
            Log.e(TAG, "BLOCKED: duplicate notification route=$route notifId=$rawNotificationId source=$source")
            return false
        }

        PushNotificationChannels.ensureChannels(appContext)
        val notificationId = data.stableNotificationIntId(appContext)
        val title = data["title"].orEmpty().ifBlank { "映世有新提醒" }
        val body = data["body"].orEmpty().ifBlank { "对方刚更新了共享空间。" }
        Log.e(TAG, "POSTING notification from=$source id=$notificationId route=$route title=$title")

        // Route life:trace and life:bowel to vibrating channel, everything else to general channel
        val isLifeRoute = route == "life:trace" || route == "life:bowel"
        val channelId = if (isLifeRoute) {
            PushNotificationChannels.LIFE_TRACE_CHANNEL_ID
        } else {
            PushNotificationChannels.SHARED_UPDATES_CHANNEL_ID
        }

        // Diagnostic: verify channel configuration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = nm.getNotificationChannel(channelId)
            Log.e(TAG, "CHANNEL: id=$channelId importance=${ch?.importance} vibrate=${ch?.shouldVibrate()} sound=${ch?.sound}")
        }

        val contentPI = contentIntent(appContext, data)

        // ── Heads-up + vibration strategy for OEM ROMs (MIUI, ColorOS, etc.) ──
        //
        // setFullScreenIntent() is REMOVED — it was intended to force MIUI/EMUI
        // to show heads-up banners, but it actually suppresses them on MIUI.
        // setFullScreenIntent is designed for incoming-call-style full-screen
        // notifications, not heads-up. On MIUI, it causes the notification to be
        // treated as a full-screen alert, showing only a status bar icon without
        // the heads-up popup card.
        //
        // Instead, we use the standard heads-up recipe:
        //   1. Channel IMPORTANCE_HIGH (set in PushNotificationChannels)
        //   2. setPriority(PRIORITY_HIGH) + setCategory(CATEGORY_MESSAGE)
        //   3. setDefaults(DEFAULT_ALL) + setVibrate() — MIUI requires explicit
        //      defaults flag on the builder to trigger vibration + heads-up.
        //      Without setDefaults(), MIUI ignores the channel's vibration
        //      settings entirely.
        //
        // When screen is on → heads-up banner. When screen is off → notification
        // appears on lock screen / ambient display.
        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0L, 180L, 80L, 180L))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentPI)
            .build()
        NotificationManagerCompat.from(appContext).notify(notificationId, notification)
        Log.e(TAG, "POSTED notification from=$source id=$notificationId channel=$channelId")
        return true
    }

    private fun contentIntent(context: Context, data: Map<String, String>): PendingIntent {
        val route = data["targetRoute"].orEmpty()
        val category = data["category"].orEmpty()
        val mediaId = route.substringAfter("photos:media:", missingDelimiterValue = "").takeIf { it.isNotBlank() }
        val postId = route.substringAfter("photos:small-album:", missingDelimiterValue = "").takeIf { it.isNotBlank() }
        val autoOpenComment = category.equals("comment", ignoreCase = true)

        // 大便通知：点击后只取消通知，不跳转任何页面
        // 之前 BUG: 大便通知携带 mediaId（来自 latestMediaId），但大便模块没有媒体，
        // LifePushDispatchActivity.resolveMediaWithSlot 按 mediaId 反查 slot 失败，
        // fallback 到 category match 返回人物/吃饭的最新媒体，造成"跳转到错误页面"
        if (route == "life:bowel") {
            val dismissIntent = Intent(context, NotificationDismissReceiver::class.java).apply {
                action = NotificationDismissReceiver.ACTION_DISMISS
                putExtra(NotificationDismissReceiver.EXTRA_NOTIFICATION_ID, data.stableNotificationIntId(context))
            }
            return PendingIntent.getBroadcast(
                context,
                (data["occurredAtMillis"]?.toLongOrNull() ?: System.currentTimeMillis()).hashCode(),
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val isLifeRoute = route == "life:trace"

        val intent = if (isLifeRoute) {
            // life 推送可能携带 mediaId 字段（服务端在 addMedia/deleteMedia 时填充），
            // 用于精准跳转到对应媒体的查看态，而不是总跳到第一张
            val lifeMediaId = data["mediaId"]?.takeIf { it.isNotBlank() }
            LifePushDispatchActivity.intent(context, route, category, lifeMediaId)
        } else {
            val action = when {
                route.startsWith("photos:small-album:") -> AppNavigationRequests.ACTION_OPEN_SMALL_ALBUM
                route.startsWith("photos") -> AppNavigationRequests.ACTION_OPEN_PHOTO_FEED
                else -> AppNavigationRequests.ACTION_OPEN_LIFE_CONSOLE
            }
            Intent(context, MainActivity::class.java).apply {
                this.action = action
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("targetRoute", route)
                putExtra("category", category)
                mediaId?.let { putExtra(AppNavigationRequests.EXTRA_PHOTO_FEED_MEDIA_ID, it) }
                postId?.let { putExtra(AppNavigationRequests.EXTRA_SMALL_ALBUM_ID, it) }
                putExtra(AppNavigationRequests.EXTRA_AUTO_OPEN_COMMENT, autoOpenComment)
            }
        }
        return PendingIntent.getActivity(
            context,
            (data["occurredAtMillis"]?.toLongOrNull() ?: System.currentTimeMillis()).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.e(TAG, "Notifications are disabled for app.")
            return false
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "POST_NOTIFICATIONS permission not granted.")
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            for (channelId in listOf(PushNotificationChannels.SHARED_UPDATES_CHANNEL_ID, PushNotificationChannels.LIFE_TRACE_CHANNEL_ID)) {
                val channel = manager.getNotificationChannel(channelId)
                if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                    Log.e(TAG, "Notification channel is disabled: $channelId")
                    return false
                }
            }
        }
        return true
    }

    private fun Map<String, String>.dedupeKey(context: Context): String {
        this["notificationId"]?.takeIf { it.isNotBlank() }?.let { return "notification:$it" }
        this["operationId"]?.takeIf { it.isNotBlank() }?.let { return "operation:$it:${this["category"].orEmpty()}" }
        this["groupId"]?.takeIf { it.isNotBlank() }?.let { return "group:$it:${this["category"].orEmpty()}" }
        return listOf(
            this["category"].orEmpty(),
            this["targetRoute"].orEmpty(),
            this["occurredAtMillis"].orEmpty(),
            this["title"].orEmpty(),
            this["body"].orEmpty(),
        ).joinToString("|")
    }

    private fun Map<String, String>.stableNotificationIntId(context: Context): Int {
        val route = this["targetRoute"].orEmpty()
        // life trace / bowel 使用固定 ID，新通知替换旧通知
        if (route == "life:trace" || route == "life:bowel") {
            return LIFE_TRACE_NOTIFICATION_ID
        }
        // 其他通知也使用固定 ID，防止累积分组
        return SHARED_UPDATES_NOTIFICATION_ID
    }

    private fun Map<String, String>.isActorCurrentUser(): Boolean {
        val actorUserId = this["actorUserId"]?.trim()?.takeIf { it.isNotBlank() } ?: return false
        val currentUserId = AuthSessionManager.getCurrentUserSnapshot()?.userId?.trim()?.takeIf { it.isNotBlank() }
            ?: return false
        return actorUserId == currentUserId
    }
}
