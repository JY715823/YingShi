package com.example.yingshi.feature.life.push

import android.util.Log
import com.example.yingshi.feature.life.widget.LifeConsoleWidgetProvider
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class YingShiFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushTokenRegistrar.registerToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val eventType = message.data["type"] ?: message.data["event"]
        if (eventType == LIFE_CONSOLE_CHANGED) {
            Log.d(TAG, "Received life console change push; refreshing widgets.")
            LifeConsoleWidgetProvider.refreshAll(applicationContext)
        }
    }

    private companion object {
        const val TAG = "YingShiFirebaseMsg"
        const val LIFE_CONSOLE_CHANGED = "life_console.changed"
    }
}
