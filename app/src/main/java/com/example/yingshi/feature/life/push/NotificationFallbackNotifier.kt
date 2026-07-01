package com.example.yingshi.feature.life.push

import android.content.Context
import android.util.Log
import com.example.yingshi.data.model.RemoteNotification
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.photos.SettingsRepository

object NotificationFallbackNotifier {
    private const val TAG = "NotificationFallback"
    private const val PREFS_NAME = "yingshi_notification_fallback"
    private const val KEY_LAST_REMOTE_VERSION = "last_remote_version"
    private const val KEY_LAST_NOTIFICATION_ID = "last_notification_id"

    suspend fun maybeShowLatest(context: Context, remoteNotificationVersion: Long): Boolean {
        if (remoteNotificationVersion <= 0L) return true
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastVersion = prefs.getLong(KEY_LAST_REMOTE_VERSION, 0L)
        if (remoteNotificationVersion <= lastVersion) {
            return true
        }

        return when (val result = RepositoryProvider.notificationRepository.getNotifications(limit = 30)) {
            is ApiResult.Success -> {
                val target = result.data
                    .asSequence()
                    .filter { it.createdAtMillis > lastVersion }
                    .filterNot { it.actorIsCurrentUser }
                    .filterNot { it.isRead }
                    .filter { SettingsRepository.isPushEnabled(it.module, it.category) }
                    .sortedByDescending { it.createdAtMillis }
                    .firstOrNull()
                if (target == null) {
                    if (result.data.isEmpty()) {
                        Log.d(TAG, "Notification list is empty during fallback; will retry version=$remoteNotificationVersion")
                        return false
                    }
                    prefs.edit().putLong(KEY_LAST_REMOTE_VERSION, remoteNotificationVersion).apply()
                    Log.d(TAG, "No unread partner notification for fallback; version=$remoteNotificationVersion")
                    return true
                }
                val lastNotificationId = prefs.getString(KEY_LAST_NOTIFICATION_ID, null)
                if (target.notificationId == lastNotificationId) {
                    prefs.edit().putLong(KEY_LAST_REMOTE_VERSION, remoteNotificationVersion).apply()
                    return true
                }
                val shown = PushNotificationPresenter.show(
                    context = appContext,
                    data = target.toPushData(remoteNotificationVersion),
                    source = "sync-fallback",
                )
                prefs.edit()
                    .putLong(KEY_LAST_REMOTE_VERSION, remoteNotificationVersion)
                    .putString(KEY_LAST_NOTIFICATION_ID, target.notificationId)
                    .apply()
                Log.e(TAG, ">>> Fallback notification checked: shown=$shown id=${target.notificationId} route=${target.targetRoute}")
                true
            }
            is ApiResult.Error -> {
                Log.w(TAG, "Failed to fetch notifications for fallback: ${result.message}", result.throwable)
                false
            }
            ApiResult.Loading -> false
        }
    }

    private fun RemoteNotification.toPushData(remoteNotificationVersion: Long): Map<String, String> {
        val safeRoute = targetRoute
            ?: mediaId?.let { "photos:media:$it" }
            ?: smallAlbumId?.let { "photos:small-album:$it" }
            ?: "photos"
        return buildMap {
            put("type", if (module == "life") "life_console.changed" else "photos.changed")
            put("event", if (module == "life") "life_console.changed" else "photos.changed")
            put("notificationId", notificationId)
            put("module", module.orEmpty().ifBlank { "photos" })
            put("category", category.orEmpty())
            actorUserId?.takeIf { it.isNotBlank() }?.let { put("actorUserId", it) }
            actorDisplayName?.takeIf { it.isNotBlank() }?.let { put("actorDisplayName", it) }
            operationId?.takeIf { it.isNotBlank() }?.let { put("operationId", it) }
            groupId?.takeIf { it.isNotBlank() }?.let { put("groupId", it) }
            put("title", title)
            put("body", body)
            put("targetRoute", safeRoute)
            put("occurredAtMillis", createdAtMillis.takeIf { it > 0L }?.toString() ?: remoteNotificationVersion.toString())
        }
    }
}
