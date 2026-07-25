package com.example.yingshi.feature.life.push

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.connectivity.NetworkConnectivityMonitor

/**
 * SSE 保活广播接收器。
 *
 * 监听系统广播和自定义定时广播，在以下场景下重启 SSE 连接：
 * - BOOT_COMPLETED: 设备开机后恢复 SSE 连接
 * - MY_PACKAGE_REPLACED: App 升级后恢复 SSE 连接
 * - ACTION_SSE_KEEPALIVE: 自定义定时广播，周期性检查并恢复 SSE 连接
 *
 * 核心保活机制：
 * 使用 AlarmManager 定时发送自定义广播（而非启动服务），避免 MIUI 后台启动
 * 前台服务限制。即使 App 进程被杀、前台服务无法启动，AlarmManager 仍会定时
 * 唤醒 App 进程（通过广播），重新启动 SseConnectionManager。
 *
 * 注意：CONNECTIVITY_CHANGE 在 Android 7.0+ 不再支持 manifest 注册，
 * 网络变化监听由 NetworkConnectivityMonitor（运行时注册）处理，
 * 仅在 App 进程存活时有效。
 */
class SseKeepAliveReceiver : BroadcastReceiver() {
    private val TAG = "SseKeepAliveReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, ">>> onReceive: action=$action")

        // 初始化必要的单例（如果 App 进程刚被创建，Application.onCreate 已处理；
        // 但如果 Receiver 通过 registerReceiver 运行时注册，可能需要手动初始化）
        val appContext = context.applicationContext
        AuthSessionManager.init(appContext)
        BackendDebugConfig.init(appContext)
        NetworkConnectivityMonitor.init(appContext)

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // 设备开机或 App 升级后，启动 SSE 连接
                Log.i(TAG, "Starting SSE connection after $action")
                SseConnectionManager.start(appContext)
                // 调度周期性保活广播
                scheduleNextKeepAlive(appContext)
            }
            // R3-AND-002: Custom keepalive action moved to SseInternalKeepAliveReceiver
            // This receiver only handles system broadcasts
        }
    }

    companion object {
        const val ACTION_SSE_KEEPALIVE = "com.example.yingshi.action.SSE_KEEPALIVE"
        // 保活间隔：2 分钟（之前 5 分钟太长，App 被杀后恢复连接慢）
        // MIUI 上 Doze 模式下 setAndAllowWhileIdle 会有 9 分钟节流，但前台/熄屏非 Doze 时可正常触发
        private const val KEEPALIVE_INTERVAL_MS = 2 * 60 * 1000L
        private const val KEEPALIVE_REQUEST_CODE = 9101

        /**
         * 调度下一次保活广播。
         * 优先使用 setExactAndAllowWhileIdle（精确闹钟，Doze 下也能触发），
         * Android 12+ 需要 SCHEDULE_EXACT_ALARM 权限，已在 manifest 声明。
         * 失败时 fallback 到 setAndAllowWhileIdle（不精确，但 Doze 下可触发），
         * 再失败 fallback 到 set（前台时正常触发）。
         */
        fun scheduleNextKeepAlive(context: Context) {
            val intent = Intent(context, SseInternalKeepAliveReceiver::class.java).apply {
                action = ACTION_SSE_KEEPALIVE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                KEEPALIVE_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerAt = SystemClock.elapsedRealtime() + KEEPALIVE_INTERVAL_MS
            // 优先尝试 setExactAndAllowWhileIdle（最可靠，Doze 下也能触发）
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pendingIntent,
                    )
                    Log.d("SseKeepAliveReceiver", "scheduleNextKeepAlive: setExactAndAllowWhileIdle scheduled in ${KEEPALIVE_INTERVAL_MS}ms")
                } else {
                    alarmManager.setExact(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pendingIntent,
                    )
                    Log.d("SseKeepAliveReceiver", "scheduleNextKeepAlive: setExact scheduled in ${KEEPALIVE_INTERVAL_MS}ms")
                }
            } catch (e: SecurityException) {
                // Android 12+ SCHEDULE_EXACT_ALARM 权限被拒绝，fallback 到 setAndAllowWhileIdle
                Log.w("SseKeepAliveReceiver", "scheduleNextKeepAlive: setExactAndAllowWhileIdle failed (SecurityException), fallback to setAndAllowWhileIdle: ${e.message}")
                try {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pendingIntent,
                    )
                } catch (e2: SecurityException) {
                    Log.w("SseKeepAliveReceiver", "scheduleNextKeepAlive: setAndAllowWhileIdle also failed, fallback to set: ${e2.message}")
                    try {
                        alarmManager.set(
                            AlarmManager.ELAPSED_REALTIME,
                            triggerAt,
                            pendingIntent,
                        )
                    } catch (e3: Exception) {
                        Log.w("SseKeepAliveReceiver", "scheduleNextKeepAlive: all alarm scheduling failed: ${e3.message}")
                    }
                }
            }
        }

        /**
         * 取消保活广播调度。
         */
        fun cancelKeepAlive(context: Context) {
            val intent = Intent(context, SseInternalKeepAliveReceiver::class.java).apply {
                action = ACTION_SSE_KEEPALIVE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                KEEPALIVE_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pendingIntent != null) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(pendingIntent)
                Log.d("SseKeepAliveReceiver", "cancelKeepAlive: cancelled")
            }
        }
    }
}
