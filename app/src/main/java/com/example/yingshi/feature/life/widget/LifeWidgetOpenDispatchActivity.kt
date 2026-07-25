package com.example.yingshi.feature.life.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.yingshi.MainActivity
import com.example.yingshi.app.AppNavigationRequests
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.feature.life.LifeMediaQuickViewerActivity

/**
 * FR-6 Round 2: Widget 点击照片打开查看态的透明分发 Activity。
 *
 * 职责：
 * 1. FR-5: 置顶该框（saveFrontSlot + renderAllFromStore）
 * 2. FR-6: 从 Store 读 snapshot 定位 media，启动 QuickViewer
 * 3. finish（透明无 UI，用户无感知）
 *
 * 与 LifePushDispatchActivity 的差异：
 * - Push 场景只知道 mediaId，需异步反查 slotKey（today API + 重试）
 * - Widget 场景已知 slotKey + mediaId，同步从 Store 读 snapshot 即可
 *
 * 设计约束：
 * - exported=false / excludeFromRecents / taskAffinity=.widget / 透明主题
 * - 同步完成 setFrontSlot + renderAllFromStore + 启动 QuickViewer + finish
 *
 * 复检修复:
 * - refreshAll → renderAllFromStore: 避免触发网络请求（置顶仅需刷新本地视图）
 * - 媒体未找到兜底: 启动 MainActivity 跳转生活控制台并 Toast 提示，避免静默退出无反馈
 */
class LifeWidgetOpenDispatchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthSessionManager.init(applicationContext)
        BackendDebugConfig.init(applicationContext)

        val slotKeyStr = intent.getStringExtra(EXTRA_SLOT_KEY).orEmpty()
        val mediaId = intent.getStringExtra(EXTRA_MEDIA_ID).orEmpty()
        Log.d(TAG, "Dispatching widget open: slotKey=$slotKeyStr, mediaId=$mediaId")

        if (slotKeyStr.isBlank() || mediaId.isBlank()) {
            Log.w(TAG, "Missing slotKey or mediaId, finishing.")
            finish()
            return
        }

        val slotKey = slotKeyOrNull(slotKeyStr)
        if (slotKey == null) {
            Log.w(TAG, "Unknown slotKey=$slotKeyStr, finishing.")
            finish()
            return
        }

        // FR-5: 置顶该框（持久化 + 同步刷新本地视图使 setElevation 生效）。
        // 复检修复: 使用 renderAllFromStore 而非 refreshAll，避免触发网络请求；
        // 点击置顶只需要本地视图重渲染，远程数据后续由定期刷新机制更新即可。
        LifeConsoleWidgetStore.saveFrontSlot(applicationContext, slotKey)
        LifeConsoleWidgetProvider.renderAllFromStore(applicationContext)

        // FR-6: 从 Store 读 snapshot 定位 media，启动 QuickViewer
        val snapshot = LifeConsoleWidgetStore.loadSnapshot(applicationContext)
        val media = findMediaById(snapshot, slotKey, mediaId)
        if (media != null) {
            val viewerIntent = LifeMediaQuickViewerActivity.widgetIntent(
                applicationContext,
                media,
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(viewerIntent)
        } else {
            // 复检修复: 媒体未找到时启动 MainActivity 兜底跳转生活控制台，并 Toast 提示，
            // 避免 Activity 静默退出导致用户点击照片无任何反馈。
            Log.w(TAG, "mediaId=$mediaId not found in slot=$slotKeyStr snapshot, fallback to MainActivity.")
            Toast.makeText(applicationContext, "照片数据已更新，正在打开生活页", Toast.LENGTH_SHORT).show()
            val fallbackIntent = Intent(applicationContext, MainActivity::class.java).apply {
                action = AppNavigationRequests.ACTION_OPEN_LIFE_CONSOLE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(fallbackIntent)
        }
        finish()
    }

    private fun slotKeyOrNull(value: String): LifeConsoleWidgetSlotKey? {
        return LifeConsoleWidgetSlotKey.entries.firstOrNull { it.storageKey == value }
    }

    private fun findMediaById(
        snapshot: com.example.yingshi.data.model.RemoteLifeConsoleToday?,
        slotKey: LifeConsoleWidgetSlotKey,
        mediaId: String,
    ): RemoteMedia? {
        snapshot ?: return null
        return snapshot.slot(slotKey).mediaItems.firstOrNull { it.mediaId == mediaId }
    }

    companion object {
        private const val TAG = "LifeWidgetOpenDispatch"
        internal const val EXTRA_SLOT_KEY = "life_widget_dispatch_slot_key"
        internal const val EXTRA_MEDIA_ID = "life_widget_dispatch_media_id"
        const val ACTION_DISPATCH = "com.example.yingshi.WIDGET_OPEN_DISPATCH"

        internal fun intent(context: Context, slotKey: LifeConsoleWidgetSlotKey, media: RemoteMedia): Intent {
            return Intent(context, LifeWidgetOpenDispatchActivity::class.java).apply {
                action = ACTION_DISPATCH
                putExtra(EXTRA_SLOT_KEY, slotKey.storageKey)
                putExtra(EXTRA_MEDIA_ID, media.mediaId)
            }
        }
    }
}
