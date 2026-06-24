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
import com.example.yingshi.feature.photos.SettingsRepository

object PushNotificationPresenter {
    private const val TAG = "PushNotificationPresenter"
    private const val CHANNEL_ID = PushNotificationChannels.SHARED_UPDATES_CHANNEL_ID

    fun show(context: Context, data: Map<String, String>, source: String): Boolean {
        val appContext = context.applicationContext
        if (!canPostNotifications(appContext)) {
            Log.w(TAG, "Skip notification from $source: POST_NOTIFICATIONS is not granted.")
            return false
        }
        if (!SettingsRepository.isPushEnabled(data["module"], data["category"])) {
            Log.d(TAG, "Skip notification from $source: preference disabled module=${data["module"]}, category=${data["category"]}")
            return false
        }
        if (!PushNotificationDeduper.shouldShow(appContext, data.dedupeKey())) {
            Log.d(TAG, "Skip duplicate notification from $source: route=${data["targetRoute"]}, category=${data["category"]}")
            return false
        }
        PushNotificationChannels.ensureSharedUpdatesChannel(appContext)
        val notificationId = (data["occurredAtMillis"]?.toLongOrNull() ?: System.currentTimeMillis()).hashCode()
        val title = data["title"].orEmpty().ifBlank { "映世有新提醒" }
        val body = data["body"].orEmpty().ifBlank { "对方刚更新了共享空间。" }
        val route = data["targetRoute"].orEmpty()
        val category = data["category"].orEmpty()
        Log.d(TAG, "Showing notification from $source: id=$notificationId, route=$route, category=$category, mediaId=${route.substringAfter("photos:media:", "")}")
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(appContext, data))
            .build()
        NotificationManagerCompat.from(appContext).notify(notificationId, notification)
        Log.d(TAG, "Notification posted successfully from $source: id=$notificationId")
        return true
    }

    private fun contentIntent(context: Context, data: Map<String, String>): PendingIntent {
        val route = data["targetRoute"].orEmpty()
        val category = data["category"].orEmpty()
        val action = when {
            route.startsWith("photos:small-album:") -> AppNavigationRequests.ACTION_OPEN_SMALL_ALBUM
            route.startsWith("photos") -> AppNavigationRequests.ACTION_OPEN_PHOTO_FEED
            route == "life:bowel" -> AppNavigationRequests.ACTION_OPEN_LIFE_CONSOLE
            route == "life:trace" -> AppNavigationRequests.ACTION_OPEN_LIFE_CONSOLE
            else -> AppNavigationRequests.ACTION_OPEN_LIFE_CONSOLE
        }
        val mediaId = route.substringAfter("photos:media:", missingDelimiterValue = "").takeIf { it.isNotBlank() }
        val postId = route.substringAfter("photos:small-album:", missingDelimiterValue = "").takeIf { it.isNotBlank() }
        val autoOpenComment = category.equals("comment", ignoreCase = true)
        Log.d(TAG, "Building contentIntent: route=$route, action=$action, mediaId=$mediaId, postId=$postId, autoOpenComment=$autoOpenComment")
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("targetRoute", route)
            putExtra("category", category)
            mediaId?.let { putExtra(AppNavigationRequests.EXTRA_PHOTO_FEED_MEDIA_ID, it) }
            postId?.let { putExtra(AppNavigationRequests.EXTRA_SMALL_ALBUM_ID, it) }
            putExtra(AppNavigationRequests.EXTRA_AUTO_OPEN_COMMENT, autoOpenComment)
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
            Log.w(TAG, "Notifications are disabled for app.")
            return false
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = manager.getNotificationChannel(CHANNEL_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                Log.w(TAG, "Notification channel is disabled: $CHANNEL_ID")
                return false
            }
        }
        return true
    }

    private fun Map<String, String>.dedupeKey(): String {
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
}
