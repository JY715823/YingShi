package com.example.yingshi.feature.updater

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.util.Log
import com.example.yingshi.BuildConfig
import com.example.yingshi.data.remote.config.RemoteServiceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App 启动时检查版本更新。
 *
 * 使用流程：
 * ```
 * // 在 MainActivity.onCreate() 中调用
 * AppUpdateChecker.checkForUpdate(this)
 * ```
 *
 * 行为说明：
 * - 异步检查，不阻塞 UI
 * - 有更新时弹 AlertDialog 提示用户
 * - 非强制更新：用户可点"稍后"忽略，24小时内不再弹同一版本
 * - 强制更新：只有"立即更新"按钮，用户必须更新才能继续
 * - "立即更新"调用 [AppUpdater] 下载 APK，完成后自动弹安装界面
 * - 网络错误静默忽略（不影响 App 正常启动）
 */
object AppUpdateChecker {

    private const val TAG = "AppUpdateChecker"
    private const val PREFS_NAME = "app_update_checker"
    private const val KEY_IGNORED_VERSION_CODE = "ignored_version_code"
    private const val KEY_LAST_PROMPT_TS = "last_prompt_ts"
    private const val PROMPT_COOLDOWN_MS = 24 * 60 * 60 * 1000L // 24 小时

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 启动时检查更新。在 Activity.onCreate() 调用即可。
     *
     * @param activity 用于弹窗的 Activity（必须是 Activity，不能是 ApplicationContext）
     */
    fun checkForUpdate(activity: Activity) {
        scope.launch {
            try {
                val api = RemoteServiceFactory.appReleaseApi
                val response = api.checkForUpdate(
                    platform = "android",
                    versionCode = BuildConfig.VERSION_CODE,
                )
                val data = response.data
                if (data == null || !data.hasUpdate) {
                    Log.d(TAG, "checkForUpdate: no update available, data=$data")
                    return@launch
                }

                // 强制更新：无视忽略记录，直接弹
                if (!data.forceUpdate && shouldSuppressPrompt(activity, data.latestVersionCode)) {
                    Log.d(TAG, "checkForUpdate: suppressed by user ignore or cooldown, version=${data.latestVersionCode}")
                    return@launch
                }

                Log.i(TAG, "checkForUpdate: update available! current=${BuildConfig.VERSION_CODE}, latest=${data.latestVersionCode} (${data.latestVersionName})")
                withContext(Dispatchers.Main) {
                    showUpdateDialog(activity, data)
                }
            } catch (t: Throwable) {
                // 启动检查失败不影响 App 正常使用
                Log.w(TAG, "checkForUpdate: failed (non-fatal): ${t.message}")
            }
        }
    }

    /**
     * 判断是否应该抑制弹窗。
     * - 用户已忽略该版本 → 抑制
     * - 24小时内已弹过 → 抑制（避免每次启动都弹）
     */
    private fun shouldSuppressPrompt(context: Context, latestVersionCode: Int?): Boolean {
        if (latestVersionCode == null) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ignoredVersion = prefs.getInt(KEY_IGNORED_VERSION_CODE, -1)
        if (ignoredVersion == latestVersionCode) return true
        val lastPromptTs = prefs.getLong(KEY_LAST_PROMPT_TS, 0L)
        if (System.currentTimeMillis() - lastPromptTs < PROMPT_COOLDOWN_MS) return true
        return false
    }

    /**
     * 记录用户忽略了某版本 + 记录弹窗时间戳。
     */
    private fun recordPrompt(context: Context, latestVersionCode: Int?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_LAST_PROMPT_TS, System.currentTimeMillis())
            .apply()
        // ignored_version_code 只在用户点"稍后"时写入，见 recordIgnore()
    }

    private fun recordIgnore(context: Context, versionCode: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_IGNORED_VERSION_CODE, versionCode)
            .apply()
    }

    private fun showUpdateDialog(
        activity: Activity,
        data: com.example.yingshi.data.remote.dto.AppReleaseCheckDto,
    ) {
        recordPrompt(activity, data.latestVersionCode)

        val versionName = data.latestVersionName ?: "新版本"
        val description = data.updateDescription?.takeIf { it.isNotBlank() } ?: "发现新版本，建议立即更新。"
        val message = buildString {
            append("发现新版本 v").append(versionName).append("\n\n")
            append(description)
        }

        val builder = AlertDialog.Builder(activity)
            .setTitle("应用更新")
            .setMessage(message)
            .setCancelable(!data.forceUpdate)
            .setPositiveButton("立即更新") { dialog, _ ->
                dialog.dismiss()
                val downloadUrl = data.downloadUrl
                if (downloadUrl.isNullOrBlank()) {
                    Log.e(TAG, "showUpdateDialog: downloadUrl is null, cannot update")
                    AlertDialog.Builder(activity)
                        .setTitle("更新失败")
                        .setMessage("下载地址无效，请稍后重试。")
                        .setPositiveButton("确定", null)
                        .show()
                    return@setPositiveButton
                }
                AppUpdater.startDownload(
                    context = activity,
                    downloadUrl = downloadUrl,
                    versionName = versionName,
                    autoPromptInstall = true,
                )
            }

        if (!data.forceUpdate) {
            builder.setNegativeButton("稍后") { dialog, _ ->
                dialog.dismiss()
                data.latestVersionCode?.let { recordIgnore(activity, it) }
            }
        }

        builder.show()
    }
}
