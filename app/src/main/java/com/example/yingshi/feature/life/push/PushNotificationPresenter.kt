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

    fun show(context: Context, data: Map<String, String>, source: String): Boolean {
        val appContext = context.applicationContext
        val route = data["targetRoute"].orEmpty()
        val category = data["category"].orEmpty()
        val module = data["module"].orEmpty()

        // ── CRITICAL: use Log.e so the message always appears in logcat ──
        Log.e(TAG, ">>> show() called from=$source module=$module category=$category route=$route")

        if (!canPostNotifications(appContext)) {
            Log.e(TAG, "BLOCKED: cannot post notifications (permission/channel disabled)")
            return false
        }
        if (!SettingsRepository.isPushEnabled(module, category)) {
            Log.d(TAG, "Skip from $source: preference disabled module=$module, category=$category")
            return false
        }
        if (data.isActorCurrentUser()) {
            Log.d(TAG, "Skip from $source: actor is current user.")
            return false
        }

        // ── Cross-path dedup: per (route + notificationId) ────────────────
        // Different events have different notificationIds, so they pass through
        // even if they share the same route. Only the exact same notification
        // delivered via both FCM and sync-fallback is deduplicated (2-min window).
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

        // NOTE: Do NOT use setDefaults() or setVibrate() — they conflict with
        // channel settings on OEM ROMs (MIUI, ColorOS, etc.), causing vibration
        // failure and heads-up suppression. All sound/vibration is configured
        // at the channel level only (see PushNotificationChannels).
        //
        // setFullScreenIntent() is used for life routes to force MIUI to show
        // heads-up banners. MIUI suppresses IMPORTANCE_HIGH heads-up unless the
        // app is manually whitelisted or the notification uses fullScreenIntent.
        // When screen is on → shows as heads-up banner. When screen is off →
        // launches the dispatch activity directly (good UX: user sees the photo).
        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .apply {
                if (isLifeRoute) {
                    setFullScreenIntent(contentPI, true)
                }
            }
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

        val isLifeRoute = route == "life:trace" || route == "life:bowel"

        val intent = if (isLifeRoute) {
            LifePushDispatchActivity.intent(context, route, category)
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
        this["notificationId"]?.takeIf { it.isNotBlank() }?.let { return it.hashCode() }
        this["operationId"]?.takeIf { it.isNotBlank() }?.let { return "operation:$it:${this["category"].orEmpty()}".hashCode() }
        this["groupId"]?.takeIf { it.isNotBlank() }?.let { return "group:$it:${this["category"].orEmpty()}".hashCode() }
        return (this["occurredAtMillis"]?.toLongOrNull() ?: System.currentTimeMillis()).hashCode()
    }

    private fun Map<String, String>.isActorCurrentUser(): Boolean {
        val actorUserId = this["actorUserId"]?.trim()?.takeIf { it.isNotBlank() } ?: return false
        val currentUserId = AuthSessionManager.getCurrentUserSnapshot()?.userId?.trim()?.takeIf { it.isNotBlank() }
            ?: return false
        return actorUserId == currentUserId
    }
}
