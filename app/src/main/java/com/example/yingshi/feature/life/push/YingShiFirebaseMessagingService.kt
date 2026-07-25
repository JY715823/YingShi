package com.example.yingshi.feature.life.push

import android.util.Log
import com.example.yingshi.feature.life.widget.LifeConsoleWidgetProvider
import com.example.yingshi.feature.sync.SyncVersionTracker
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class YingShiFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.e(TAG, ">>> onNewToken: token prefix=${token.take(12)}")
        PushTokenRegistrar.registerToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val eventType = message.data["type"] ?: message.data["event"]
        val module = message.data["module"].orEmpty()
        val hasNotification = message.notification != null
        val dataSize = message.data.size
        // Use Log.e so this ALWAYS appears in logcat (Log.d may be filtered on some devices)
        Log.e(TAG, ">>> onMessageReceived: eventType=$eventType, module=$module, hasNotification=$hasNotification, dataSize=$dataSize, dataKeys=${message.data.keys.toList()}")
        if (eventType == LIFE_CONSOLE_CHANGED) {
            Log.e(TAG, "Received life console change push; refreshing widgets.")
            LifeConsoleWidgetProvider.refreshAll(applicationContext)
        }
        // 按模块区分后续处理（与 SseConnectionManager 路径对齐）：
        // - life 事件：SSE/FCM 已直接显示通知 + 刷新 widget，不触发轮询
        //   （避免轮询更新 stale 状态导致照片流被误刷新；服务端 photoFeedVersion
        //   已通过 DomainNotLife 排除 life domain 媒体，理论上不会触发照片流 stale，
        //   但保险起见仍然跳过轮询）
        // - photo 事件：触发轮询以更新 stale 状态，让照片流刷新
        if (module != MODULE_LIFE) {
            SyncVersionTracker.requestImmediatePoll()
        } else {
            Log.e(TAG, "Skipping requestImmediatePoll for life event (module=life)")
        }
        val shown = PushNotificationPresenter.show(applicationContext, message.data, source = "fcm")
        Log.e(TAG, ">>> onMessageReceived result: shown=$shown")
    }

    private companion object {
        const val TAG = "YingShiFirebaseMsg"
        const val LIFE_CONSOLE_CHANGED = "life_console.changed"
        const val MODULE_LIFE = "life"
    }
}
