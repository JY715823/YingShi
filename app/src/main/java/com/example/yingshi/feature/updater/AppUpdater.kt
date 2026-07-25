package com.example.yingshi.feature.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * APK 下载与安装器。
 *
 * 设计要点：
 * - 使用独立 OkHttpClient 下载（避免和 RemoteServiceFactory 共享连接池，影响 API 调用）
 * - 下载到 `cacheDir/yingshi-update.apk`，无需申请外部存储权限
 * - 通过 FileProvider 生成 content:// URI 调起系统安装器（Android 7.0+ 必需）
 * - 下载进度通过前台通知展示
 * - 防重复下载：[isDownloading] 原子标记
 * - 支持下载完成自动弹安装界面（[autoPromptInstall]）
 */
object AppUpdater {

    private const val TAG = "AppUpdater"
    private const val APK_FILENAME = "yingshi-update.apk"
    private const val CHANNEL_ID = "yingshi_app_update"
    private const val NOTIFICATION_ID = 9527
    private const val ACTION_INSTALL_PROMPT = "com.example.yingshi.action.INSTALL_PROMPT"

    private val isDownloading = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null

    private val downloadClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * 启动 APK 下载。重复调用会被忽略（除非上一次已完成或失败）。
     *
     * @param context        任意 Context，内部取 applicationContext
     * @param downloadUrl    APK 下载地址（绝对 URL）
     * @param versionName    版本名（用于通知文案）
     * @param autoPromptInstall 下载完成后是否自动调起安装界面
     * @param onComplete     下载完成回调（在主线程），可在此跳转或提示用户
     */
    fun startDownload(
        context: Context,
        downloadUrl: String,
        versionName: String,
        autoPromptInstall: Boolean = true,
        onComplete: (() -> Unit)? = null,
    ) {
        if (!isDownloading.compareAndSet(false, true)) {
            Log.w(TAG, "startDownload: already downloading, ignored")
            return
        }
        val appContext = context.applicationContext
        ensureNotificationChannel(appContext)

        currentJob = scope.launch {
            val apkFile = File(appContext.cacheDir, APK_FILENAME)
            val progressNotifier = ProgressNotifier(appContext, versionName)
            progressNotifier.showStart()

            try {
                val resolvedUrl = resolveDownloadUrl(appContext, downloadUrl)
                Log.i(TAG, "startDownload: url=$resolvedUrl, target=${apkFile.absolutePath}")

                val request = Request.Builder().url(resolvedUrl).build()
                downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw RuntimeException("HTTP ${response.code} ${response.message}")
                    }
                    val body = response.body ?: throw RuntimeException("Empty response body")
                    val totalBytes = body.contentLength().takeIf { it > 0 } ?: -1L

                    // 删除旧 APK，避免 FileProvider 缓存问题
                    if (apkFile.exists()) apkFile.delete()

                    body.byteStream().use { input ->
                        apkFile.outputStream().use { output ->
                            val buffer = ByteArray(8 * 1024)
                            var downloaded = 0L
                            var lastNotifyAt = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                val now = System.currentTimeMillis()
                                // 限频更新通知（每 500ms 一次，避免抖动）
                                if (now - lastNotifyAt > 500) {
                                    progressNotifier.updateProgress(downloaded, totalBytes)
                                    lastNotifyAt = now
                                }
                            }
                        }
                    }
                }

                Log.i(TAG, "startDownload: completed, size=${apkFile.length()}")
                progressNotifier.showComplete()
                isDownloading.set(false)

                if (autoPromptInstall) {
                    withContext(Dispatchers.Main) {
                        promptInstall(appContext, apkFile)
                    }
                }
                withContext(Dispatchers.Main) {
                    onComplete?.invoke()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "startDownload: failed", t)
                progressNotifier.showFailed(t.message ?: "下载失败")
                isDownloading.set(false)
                // 失败时清理半成品文件
                runCatching { if (apkFile.exists()) apkFile.delete() }
            }
        }
    }

    /**
     * 调起系统 PackageInstaller 安装 APK。
     *
     * 需要 AndroidManifest 声明 REQUEST_INSTALL_PACKAGES 权限（Android 8.0+）。
     * 该权限是普通权限（install-time），不需要运行时申请。
     */
    fun promptInstall(context: Context, apkFile: File) {
        val appContext = context.applicationContext
        if (!apkFile.exists()) {
            Log.e(TAG, "promptInstall: apk not found: ${apkFile.absolutePath}")
            return
        }
        val authority = "${appContext.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(appContext, authority, apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            appContext.startActivity(intent)
            Log.i(TAG, "promptInstall: install intent launched")
        } catch (e: Exception) {
            Log.e(TAG, "promptInstall: failed to launch installer", e)
        }
    }

    /**
     * 把相对路径（如 /download/x.apk）拼接成绝对 URL。
     */
    private fun resolveDownloadUrl(context: Context, downloadUrl: String): String {
        if (downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://")) {
            return downloadUrl
        }
        val base = com.example.yingshi.data.remote.config.BackendDebugConfig.currentBaseUrl()
        return base.trimEnd('/') + "/" + downloadUrl.trimStart('/')
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "App 更新",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "应用更新下载进度"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * 下载进度通知封装。
     */
    private class ProgressNotifier(
        private val context: Context,
        private val versionName: String,
    ) {
        private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        fun showStart() {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("正在下载映世 v$versionName")
                .setContentText("准备中...")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setProgress(0, 0, true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }

        fun updateProgress(downloaded: Long, total: Long) {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("正在下载映世 v$versionName")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
            if (total > 0) {
                val percent = ((downloaded * 100) / total).toInt()
                builder.setContentText("$percent%")
                builder.setProgress(100, percent, false)
            } else {
                builder.setContentText(formatBytes(downloaded))
                builder.setProgress(0, 0, true)
            }
            manager.notify(NOTIFICATION_ID, builder.build())
        }

        fun showComplete() {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("下载完成")
                .setContentText("点击安装映世 v$versionName")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }

        fun showFailed(reason: String) {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("下载失败")
                .setContentText(reason)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }

        private fun formatBytes(bytes: Long): String {
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            return when {
                mb >= 1 -> String.format("%.1f MB", mb)
                kb >= 1 -> String.format("%.0f KB", kb)
                else -> "$bytes B"
            }
        }
    }
}
