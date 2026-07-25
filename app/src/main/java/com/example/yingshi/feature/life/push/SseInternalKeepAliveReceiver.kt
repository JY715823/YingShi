package com.example.yingshi.feature.life.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.connectivity.NetworkConnectivityMonitor

/**
 * R3-AND-002: 内部 SSE 保活广播接收器。
 *
 * 只接收应用内部自定义广播 ACTION_SSE_KEEPALIVE（由 AlarmManager 调度）。
 * exported=false，外部应用无法发送此广播触发 SSE 连接操作。
 *
 * 与 SseKeepAliveReceiver 拆分：
 * - SseKeepAliveReceiver: exported=true, 只处理系统广播 (BOOT_COMPLETED, MY_PACKAGE_REPLACED)
 * - SseInternalKeepAliveReceiver: exported=false, 只处理内部保活广播
 */
class SseInternalKeepAliveReceiver : BroadcastReceiver() {
    private val TAG = "SseInternalKeepAlive"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != SseKeepAliveReceiver.ACTION_SSE_KEEPALIVE) {
            Log.w(TAG, "Ignoring unexpected action: $action")
            return
        }

        Log.d(TAG, ">>> onReceive: action=$action")

        val appContext = context.applicationContext
        AuthSessionManager.init(appContext)
        BackendDebugConfig.init(appContext)
        NetworkConnectivityMonitor.init(appContext)

        // 周期性保活：检查 SSE 连接状态，断开则重启
        val connected = SseConnectionManager.isConnected()
        Log.d(TAG, "Keepalive tick: SSE connected=$connected")
        if (!connected) {
            Log.d(TAG, "Keepalive: SSE disconnected, restarting connection")
            SseConnectionManager.start(appContext)
        }
        // 调度下一次保活广播
        SseKeepAliveReceiver.scheduleNextKeepAlive(appContext)
    }
}
