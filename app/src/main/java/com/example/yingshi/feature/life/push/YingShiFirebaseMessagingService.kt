package com.example.yingshi.feature.life.push

import android.util.Log
import com.example.yingshi.feature.life.widget.LifeConsoleWidgetProvider
import com.example.yingshi.feature.sync.SyncVersionTracker
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class YingShiFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "onNewToken: token prefix=${token.take(12)}")
        PushTokenRegistrar.registerToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val eventType = message.data["type"] ?: message.data["event"]
        val hasNotification = message.notification != null
        val dataSize = message.data.size
        Log.d(TAG, "onMessageReceived: eventType=$eventType, hasNotification=$hasNotification, dataSize=$dataSize, dataKeys=${message.data.keys.toList()}")
        if (eventType == LIFE_CONSOLE_CHANGED) {
            Log.d(TAG, "Received life console change push; refreshing widgets.")
            LifeConsoleWidgetProvider.refreshAll(applicationContext)
        }
        SyncVersionTracker.requestImmediatePoll()
        val shown = PushNotificationPresenter.show(applicationContext, message.data, source = "fcm")
        Log.d(TAG, "onMessageReceived: notification shown=$shown")
    }

    private companion object {
        const val TAG = "YingShiFirebaseMsg"
        const val LIFE_CONSOLE_CHANGED = "life_console.changed"
    }
}
