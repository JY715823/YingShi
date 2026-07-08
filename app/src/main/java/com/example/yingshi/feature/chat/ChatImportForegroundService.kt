package com.example.yingshi.feature.chat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.yingshi.MainActivity
import com.example.yingshi.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatImportForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockRenewalJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        observeJob = scope.launch {
            ChatImportRuntime.state.collectLatest { state ->
                if (state.isRunning) {
                    runCatching {
                        startForeground(NOTIFICATION_ID, buildNotification(state))
                    }.onFailure { throwable ->
                        Log.w(TAG, "Unable to promote chat import to foreground", throwable)
                    }
                } else if (state.result != null || state.error != null) {
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(NOTIFICATION_ID, buildNotification(state))
                    releaseWakeLock()
                    runCatching {
                        stopForeground(STOP_FOREGROUND_DETACH)
                    }
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_IMPORT) {
            val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
            val expectedChatId = intent.getLongExtra(EXTRA_EXPECTED_CHAT_ID, -1L).takeIf { it > 0L }
            if (uri != null) {
                runCatching {
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification(
                            ChatImportRuntimeState(
                                isRunning = true,
                                progress = null,
                            ),
                        ),
                    )
                }.onFailure { throwable ->
                    Log.w(TAG, "Unable to start chat import foreground notification", throwable)
                }
                acquireWakeLock()
                ChatImportRuntime.startImport(
                    context = applicationContext,
                    uri = uri,
                    expectedChatId = expectedChatId,
                )
            } else {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observeJob?.cancel()
        wakeLockRenewalJob?.cancel()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(state: ChatImportRuntimeState): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1001,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val progress = state.progress
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(
                when {
                    state.isRunning -> "聊天记录导入中"
                    state.error != null -> "聊天记录导入失败"
                    else -> "聊天记录导入完成"
                },
            )
            .setContentText(
                when {
                    state.isRunning -> progress?.message ?: "正在准备导入"
                    state.error != null -> state.error
                    else -> state.message ?: "导入完成"
                },
            )
            .setContentIntent(pendingIntent)
            .setOngoing(state.isRunning)
            .setOnlyAlertOnce(true)
            .setAutoCancel(!state.isRunning)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (progress != null && progress.total > 0 && state.isRunning) {
            builder.setProgress(progress.total, progress.current.coerceIn(0, progress.total), false)
                .setSubText("${progress.current} / ${progress.total}")
        } else if (state.isRunning) {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "聊天记录导入",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "用于显示大体积聊天记录导入进度"
        }
        manager.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YingShi:ChatImport").apply {
            setReferenceCounted(false)
            acquire(2 * 60 * 60 * 1000L)
        }
        wakeLockRenewalJob?.cancel()
        wakeLockRenewalJob = scope.launch {
            while (true) {
                delay(90 * 60 * 1000L) // 90 minutes
                wakeLock?.let { lock ->
                    if (lock.isHeld) lock.release()
                }
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YingShi:ChatImport").apply {
                    setReferenceCounted(false)
                    acquire(2 * 60 * 60 * 1000L)
                }
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLockRenewalJob?.cancel()
        wakeLockRenewalJob = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        const val ACTION_START_IMPORT = "com.example.yingshi.feature.chat.action.START_IMPORT"
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_EXPECTED_CHAT_ID = "extra_expected_chat_id"
        private const val CHANNEL_ID = "chat_imports"
        private const val NOTIFICATION_ID = 32001
        private const val TAG = "ChatImportService"

        fun start(
            context: Context,
            uri: Uri,
            expectedChatId: Long? = null,
        ) {
            val intent = ChatImportRuntime.buildStartIntent(context, uri, expectedChatId)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
