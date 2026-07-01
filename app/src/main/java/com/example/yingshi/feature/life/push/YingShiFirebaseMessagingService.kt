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
        val hasNotification = message.notification != null
        val dataSize = message.data.size
        // Use Log.e so this ALWAYS appears in logcat (Log.d may be filtered on some devices)
        Log.e(TAG, ">>> onMessageReceived: eventType=$eventType, hasNotification=$hasNotification, dataSize=$dataSize, dataKeys=${message.data.keys.toList()}")
        if (eventType == LIFE_CONSOLE_CHANGED) {
            Log.e(TAG, "Received life console change push; refreshing widgets.")
            LifeConsoleWidgetProvider.refreshAll(applicationContext)
        }
        SyncVersionTracker.requestImmediatePoll()
        // NOTE: Removed blanket 60s FCM suppression after local mutations.
        // The server's targetTokensFor() already excludes the actor's own tokens,
        // and PushNotificationPresenter.isActorCurrentUser() provides a per-notification
        // safety net. The old suppression was too aggressive — it blocked ALL incoming
        // FCMs (including legitimate partner notifications) for 60s after any local action.
        val shown = PushNotificationPresenter.show(applicationContext, message.data, source = "fcm")
        Log.e(TAG, ">>> onMessageReceived result: shown=$shown")
    }

    private companion object {
        const val TAG = "YingShiFirebaseMsg"
        const val LIFE_CONSOLE_CHANGED = "life_console.changed"
    }
}
