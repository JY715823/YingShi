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
        Log.e(TAG, ">>> maybeShowLatest called: version=$remoteNotificationVersion")
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
                        Log.e(TAG, "Notification list is empty during fallback; will retry version=$remoteNotificationVersion")
                        return false
                    }
                    // 检查是否有比 lastVersion 更新的通知（即使被 actorIsCurrentUser/isRead 过滤了）
                    // 如果有，说明通知中心已经处理了这个版本，可以更新 lastVersion
                    // 如果没有，说明通知中心还没有收到新版本的数据（时序问题），需要重试
                    val hasNewerThanLastVersion = result.data.any { it.createdAtMillis > lastVersion }
                    if (hasNewerThanLastVersion) {
                        prefs.edit().putLong(KEY_LAST_REMOTE_VERSION, remoteNotificationVersion).apply()
                        Log.e(TAG, "No unread partner notification for fallback; version=$remoteNotificationVersion")
                        return true
                    }
                    Log.e(TAG, "No notification newer than lastVersion=$lastVersion, will retry version=$remoteNotificationVersion")
                    return false
                }
                val lastNotificationId = prefs.getString(KEY_LAST_NOTIFICATION_ID, null)
                if (target.notificationId == lastNotificationId) {
                    prefs.edit().putLong(KEY_LAST_REMOTE_VERSION, remoteNotificationVersion).apply()
                    return true
                }
                // P1-3 根因修复: life 路由 (life:bowel / life:trace) 完全跳过轮询回退。
                // life 通知由 SSE 推送负责, 不应通过轮询回退重复推送。
                // 之前 BUG:
                //   1. SSE 推送 notificationId = "life:bowel_added:timestamp"
                //   2. 通知中心 notificationId = "bowel:eventId"
                //   3. 两者不同导致 dedup 失效, 第二条通知用固定 ID 9001 替换第一条
                //   4. 通知中心 toBowelEventNotification 的 body 格式可能与 SSE 推送不同
                // 现在直接跳过 life 路由, 更新 lastVersion 避免反复查询。
                val targetRoute = target.targetRoute ?: ""
                if (targetRoute == "life:bowel" || targetRoute == "life:trace") {
                    Log.e(TAG, ">>> Fallback skipped: life route handled by SSE, route=$targetRoute id=${target.notificationId}")
                    prefs.edit()
                        .putLong(KEY_LAST_REMOTE_VERSION, remoteNotificationVersion)
                        .putString(KEY_LAST_NOTIFICATION_ID, target.notificationId)
                        .apply()
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
                Log.e(TAG, "Failed to fetch notifications for fallback: ${result.message}", result.throwable)
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
            // 携带 mediaId，让客户端能精准跳转到对应媒体的查看态
            // 之前 BUG: 缺少此字段导致回退通知无法精准跳转，总是跳到第一张
            mediaId?.takeIf { it.isNotBlank() }?.let { put("mediaId", it) }
        }
    }
}
