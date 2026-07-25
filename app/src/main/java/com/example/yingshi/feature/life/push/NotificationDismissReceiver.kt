package com.example.yingshi.feature.life.push

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 用于"点击通知只取消通知、不跳转任何页面"的场景。
 *
 * 典型用例：大便通知。之前大便通知携带 mediaId（来自 latestMediaId），
 * LifePushDispatchActivity 按 mediaId 反查 slot 失败后 fallback 到
 * category match，返回人物/吃饭的最新媒体，造成跳转到错误页面。
 *
 * 现在改为：大便通知点击 → 发送 broadcast → 本 Receiver 取消通知 → 无任何跳转。
 */
class NotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_DISMISS) return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        Log.e(TAG, "onReceive: dismissing notification id=$notificationId")
        if (notificationId >= 0) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notificationId)
        }
    }

    companion object {
        private const val TAG = "NotificationDismissReceiver"
        const val ACTION_DISMISS = "com.example.yingshi.action.NOTIFICATION_DISMISS"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
