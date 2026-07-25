package com.example.yingshi

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.yingshi.app.AppNavigationRequests
import com.example.yingshi.app.YingShiApp
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.feature.life.push.PushTokenRegistrar
import com.example.yingshi.feature.life.push.SseConnectionManager
import com.example.yingshi.feature.updater.AppUpdateChecker
import com.example.yingshi.ui.theme.YingShiTheme

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "sse_battery_guide"
        private const val KEY_GUIDE_SHOWN = "battery_guide_shown"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthSessionManager.init(applicationContext)
        BackendDebugConfig.init(applicationContext)
        Log.d(TAG, "onCreate: intent action=${intent?.action}, extras=${intent?.extras?.keySet()?.toList()}")
        handleLaunchIntent(intent)
        enableEdgeToEdge()
        // 确保 SSE 连接启动（Application.onCreate 可能因 MIUI 前台服务限制失败，
        // Activity 前台时再次启动以确保连接恢复）
        SseConnectionManager.start(applicationContext)
        // 引导用户关闭电池优化，避免 MIUI/Doze 在 App 退到后台后限制 SSE 保活闹钟
        maybePromptBatteryOptimization()
        // 启动时检查 App 版本更新（异步，不阻塞 UI）
        // 网络失败静默忽略，不影响 App 正常启动
        AppUpdateChecker.checkForUpdate(this)
        setContent {
            YingShiTheme {
                YingShiApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d(TAG, "onNewIntent: action=${intent.action}, extras=${intent.extras?.keySet()?.toList()}")
        handleLaunchIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        PushTokenRegistrar.registerCurrentTokenIfPossible(applicationContext)
        // App 回到前台时主动恢复 SSE 连接（清后台后 SSE 已断开，
        // 回到前台立即恢复，避免等待 2 分钟保活广播）
        SseConnectionManager.start(applicationContext)
        // 再次检查电池优化（用户可能从系统设置返回但未授权）
        maybePromptBatteryOptimization()
    }

    /**
     * 引导用户关闭电池优化。
     *
     * 背景：MIUI/Android Doze 模式会限制后台 SSE 保活闹钟触发频率，
     * 导致 App 清后台后收不到通知。引导用户将 App 加入电池优化白名单
     * 可让 AlarmManager.setExactAndAllowWhileIdle 在 Doze 下也能正常触发，
     * 同时避免 MIUI 后台清理机制杀死 App 进程。
     *
     * 仅引导一次（通过 SharedPreferences 记录），用户拒绝后不再打扰。
     * 用户后续可在系统设置中手动开启。
     */
    private fun maybePromptBatteryOptimization() {
        val pm = getSystemService(POWER_SERVICE) as? PowerManager ?: return
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_GUIDE_SHOWN, false)) return
        prefs.edit().putBoolean(KEY_GUIDE_SHOWN, true).apply()
        Log.e(TAG, "maybePromptBatteryOptimization: prompting user to disable battery optimization")
        AlertDialog.Builder(this)
            .setTitle("关闭电池优化以保障通知推送")
            .setMessage("为保障 App 退到后台后仍能及时收到通知推送，需要关闭本应用的电池优化。\n\n请在接下来的系统弹窗中选择\"允许\"。")
            .setPositiveButton("去设置") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "maybePromptBatteryOptimization: direct request failed, fallback to settings: ${e.message}")
                    try {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    } catch (e2: Exception) {
                        Log.e(TAG, "maybePromptBatteryOptimization: fallback also failed: ${e2.message}")
                    }
                }
            }
            .setNegativeButton("稍后再说", null)
            .setCancelable(false)
            .show()
    }

    private fun handleLaunchIntent(intent: Intent?) {
        val targetRoute = intent?.getStringExtra("targetRoute").orEmpty()
        val category = intent?.getStringExtra("category").orEmpty()
        val shouldAutoOpenComment = category.equals("comment", ignoreCase = true) ||
            intent?.getBooleanExtra(AppNavigationRequests.EXTRA_AUTO_OPEN_COMMENT, false) == true
        Log.d(TAG, "handleLaunchIntent: targetRoute=$targetRoute, category=$category, shouldAutoOpenComment=$shouldAutoOpenComment, action=${intent?.action}")
        if (targetRoute.isNotBlank()) {
            when {
                targetRoute.startsWith("photos:media:", ignoreCase = true) -> {
                    val mediaId = targetRoute.substringAfter("photos:media:").takeIf { it.isNotBlank() }
                    Log.d(TAG, "handleLaunchIntent: opening photo feed with media=$mediaId, autoOpenViewer=$shouldAutoOpenComment, autoOpenComment=$shouldAutoOpenComment")
                    AppNavigationRequests.requestPhotoFeed(
                        mediaId = mediaId,
                        autoOpenViewer = shouldAutoOpenComment,
                        autoOpenComment = shouldAutoOpenComment,
                    )
                    return
                }
                targetRoute.startsWith("photos:small-album:", ignoreCase = true) -> {
                    val postId = targetRoute.substringAfter("photos:small-album:").takeIf { it.isNotBlank() }
                    Log.d(TAG, "handleLaunchIntent: opening small album=$postId, autoOpenComment=$shouldAutoOpenComment")
                    if (postId != null) {
                        AppNavigationRequests.requestSmallAlbum(
                            postId = postId,
                            autoOpenComment = shouldAutoOpenComment,
                        )
                    } else {
                        AppNavigationRequests.requestPhotoFeed()
                    }
                    return
                }
                targetRoute.startsWith("photos", ignoreCase = true) -> {
                    Log.d(TAG, "handleLaunchIntent: opening photo feed (general)")
                    AppNavigationRequests.requestPhotoFeed()
                    return
                }
                targetRoute == "life:bowel" || targetRoute == "life:trace" -> {
                    Log.d(TAG, "handleLaunchIntent: opening life console")
                    AppNavigationRequests.requestLifeConsole()
                    return
                }
            }
        }
        when (intent?.action) {
            AppNavigationRequests.ACTION_OPEN_LIFE_CONSOLE -> {
                Log.d(TAG, "handleLaunchIntent: action OPEN_LIFE_CONSOLE")
                AppNavigationRequests.requestLifeConsole(
                    slotKey = intent.getStringExtra(AppNavigationRequests.EXTRA_LIFE_CONSOLE_SLOT_KEY),
                    mediaId = intent.getStringExtra(AppNavigationRequests.EXTRA_LIFE_CONSOLE_MEDIA_ID),
                )
            }
            AppNavigationRequests.ACTION_OPEN_LEDGER -> {
                Log.d(TAG, "handleLaunchIntent: action OPEN_LEDGER")
                AppNavigationRequests.requestLedger()
            }
            AppNavigationRequests.ACTION_OPEN_LEDGER_ADD -> {
                Log.d(TAG, "handleLaunchIntent: action OPEN_LEDGER_ADD")
                AppNavigationRequests.requestLedgerAdd()
            }
            AppNavigationRequests.ACTION_OPEN_PHOTO_FEED -> {
                val mediaId = intent.getStringExtra(AppNavigationRequests.EXTRA_PHOTO_FEED_MEDIA_ID)
                    ?: intent.mediaIdFromTargetRoute()
                Log.d(TAG, "handleLaunchIntent: action OPEN_PHOTO_FEED, mediaId=$mediaId, autoOpenComment=$shouldAutoOpenComment")
                AppNavigationRequests.requestPhotoFeed(
                    mediaId = mediaId,
                    autoOpenViewer = shouldAutoOpenComment,
                    autoOpenComment = shouldAutoOpenComment,
                )
            }
            AppNavigationRequests.ACTION_OPEN_SMALL_ALBUM -> {
                val postId = intent.getStringExtra(AppNavigationRequests.EXTRA_SMALL_ALBUM_ID)
                    ?: intent.smallAlbumIdFromTargetRoute()
                val autoOpenComment = intent.getBooleanExtra(AppNavigationRequests.EXTRA_AUTO_OPEN_COMMENT, false)
                Log.d(TAG, "handleLaunchIntent: action OPEN_SMALL_ALBUM, postId=$postId, autoOpenComment=$autoOpenComment")
                if (!postId.isNullOrBlank()) {
                    AppNavigationRequests.requestSmallAlbum(
                        postId = postId,
                        autoOpenComment = autoOpenComment,
                    )
                } else {
                    AppNavigationRequests.requestPhotoFeed()
                }
            }
            else -> {
                Log.d(TAG, "handleLaunchIntent: no matching route or action, ignoring")
            }
        }
    }

    private fun Intent.mediaIdFromTargetRoute(): String? {
        return getStringExtra("targetRoute")
            ?.substringAfter("photos:media:", missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
    }

    private fun Intent.smallAlbumIdFromTargetRoute(): String? {
        return getStringExtra("targetRoute")
            ?.substringAfter("photos:small-album:", missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
    }
}
