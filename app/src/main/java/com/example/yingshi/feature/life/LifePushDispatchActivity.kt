package com.example.yingshi.feature.life

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.example.yingshi.data.model.RemoteLifeConsoleToday
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.life.widget.LifeConsoleWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Transparent dispatcher that resolves a push targetRoute into the latest life-console
 * media and opens [LifeMediaQuickViewerActivity]. Finishes immediately after handing off.
 *
 * 修复：服务端推送 data 现在携带 mediaId 字段，客户端优先按 mediaId 精准定位媒体。
 * 如果 mediaId 缺失（旧版服务端或非媒体操作），回退到按 category 匹配第一张可用媒体。
 */
class LifePushDispatchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthSessionManager.init(applicationContext)
        BackendDebugConfig.init(applicationContext)

        val route = intent.getStringExtra(EXTRA_TARGET_ROUTE).orEmpty()
        val category = intent.getStringExtra(EXTRA_CATEGORY).orEmpty()
        val mediaId = intent.getStringExtra(EXTRA_MEDIA_ID).orEmpty()
        Log.d(TAG, "Dispatching push: route=$route, category=$category, mediaId=$mediaId")

        if (route.isBlank()) {
            Log.w(TAG, "No targetRoute, falling back to main app.")
            finish()
            return
        }

        LifeConsoleWidgetProvider.refreshAll(applicationContext)

        dispatchScope.launch {
            // 重试机制：服务端 SSE 推送虽已延迟到事务提交后，但网络/数据库复制延迟
            // 仍可能导致首次查询查不到新媒体。重试 3 次，每次间隔 600ms。
            val snapshot = fetchTodaySnapshotWithRetry(mediaId)
            val (media, slotKey) = resolveMediaWithSlot(snapshot, route, category, mediaId)
            runOnUiThread {
                if (media != null) {
                    // 携带 slotKey 让 Viewer 能直接定位同 slot 的全部媒体,避免反查 slot 时找不到 mediaId
                    val viewerIntent = LifeMediaQuickViewerActivity.widgetIntent(applicationContext, media).apply {
                        slotKey?.let { putExtra(LifeMediaQuickViewerActivity.EXTRA_SLOT_KEY, it) }
                        putExtra(EXTRA_LAUNCHED_FROM_PUSH, true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(viewerIntent)
                } else {
                    Log.d(TAG, "No media resolved for route=$route, falling back to main app.")
                }
                finish()
            }
        }
    }

    /**
     * 带重试的 today snapshot 获取：
     * - 如果 mediaId 为空，只查一次
     * - 如果 mediaId 非空，重试 3 次，每次检查 mediaId 是否已在 snapshot 中
     *   （服务端事务提交后才能查到，重试确保跨网络/数据库延迟的最终一致性）
     */
    private suspend fun fetchTodaySnapshotWithRetry(targetMediaId: String?): RemoteLifeConsoleToday? {
        if (targetMediaId.isNullOrBlank()) {
            return fetchTodaySnapshot()
        }
        var attempt = 0
        while (attempt < 3) {
            val snapshot = fetchTodaySnapshot()
            if (snapshot != null && containsMediaId(snapshot, targetMediaId)) {
                Log.d(TAG, "fetchTodaySnapshotWithRetry: mediaId=$targetMediaId found on attempt=${attempt + 1}")
                return snapshot
            }
            attempt++
            Log.d(TAG, "fetchTodaySnapshotWithRetry: mediaId=$targetMediaId not found on attempt=$attempt, retrying after 600ms")
            delay(600)
        }
        Log.w(TAG, "fetchTodaySnapshotWithRetry: mediaId=$targetMediaId not found after 3 attempts, returning last snapshot")
        return fetchTodaySnapshot()
    }

    private fun containsMediaId(snapshot: RemoteLifeConsoleToday, mediaId: String): Boolean {
        val allMediaIds = listOf(
            snapshot.personPartner.mediaItems,
            snapshot.mealPartner.mediaItems,
            snapshot.personSelf.mediaItems,
            snapshot.mealSelf.mediaItems,
        ).flatten().map { it.mediaId }
        return allMediaIds.contains(mediaId)
    }

    private suspend fun fetchTodaySnapshot(): RemoteLifeConsoleToday? {
        return when (val result = RepositoryProvider.lifeConsoleRepository.getToday(date = todayDate())) {
            is ApiResult.Success -> result.data
            else -> null
        }
    }

    /**
     * 解析推送跳转目标,同时返回 slotKey 让 Viewer 能直接定位同 slot 的全部媒体.
     * 返回值: (media, slotKey?) — 找不到时 media 为 null
     */
    private fun resolveMediaWithSlot(
        snapshot: RemoteLifeConsoleToday?,
        route: String,
        category: String,
        mediaId: String,
    ): Pair<RemoteMedia?, String?> {
        if (snapshot == null) return null to null

        // 优先按 mediaId 精准匹配（服务端 addMedia/deleteMedia 推送携带了具体 mediaId）
        if (mediaId.isNotBlank()) {
            // 遍历 4 个 slot,找到 mediaId 所在的 slot,同时返回 slotKey
            val slots = listOf(
                LifeConsoleSlotKeys.PERSON_PARTNER to snapshot.personPartner.mediaItems,
                LifeConsoleSlotKeys.MEAL_PARTNER to snapshot.mealPartner.mediaItems,
                LifeConsoleSlotKeys.PERSON_SELF to snapshot.personSelf.mediaItems,
                LifeConsoleSlotKeys.MEAL_SELF to snapshot.mealSelf.mediaItems,
            )
            for ((slotKey, items) in slots) {
                val matched = items.find { it.mediaId == mediaId }
                if (matched != null) {
                    Log.d(TAG, "resolveMediaWithSlot: matched by mediaId=$mediaId slotKey=$slotKey")
                    return matched to slotKey
                }
            }
            Log.d(TAG, "resolveMediaWithSlot: mediaId=$mediaId not found in today snapshot, falling back to category match")
        }

        // 回退：按 category 匹配 partner 的第一张可用媒体,同时返回对应 slotKey
        return when (route) {
            "life:trace" -> {
                when {
                    // 按 category 精准定位 partner 的媒体（通知是 partner 触发）
                    category.startsWith("person_") -> firstAvailableMediaWithSlot(
                        LifeConsoleSlotKeys.PERSON_PARTNER to snapshot.personPartner.mediaItems,
                        LifeConsoleSlotKeys.PERSON_SELF to snapshot.personSelf.mediaItems,
                    )
                    category.startsWith("meal_") -> firstAvailableMediaWithSlot(
                        LifeConsoleSlotKeys.MEAL_PARTNER to snapshot.mealPartner.mediaItems,
                        LifeConsoleSlotKeys.MEAL_SELF to snapshot.mealSelf.mediaItems,
                    )
                    else -> firstAvailableMediaWithSlot(
                        LifeConsoleSlotKeys.PERSON_PARTNER to snapshot.personPartner.mediaItems,
                        LifeConsoleSlotKeys.MEAL_PARTNER to snapshot.mealPartner.mediaItems,
                        LifeConsoleSlotKeys.PERSON_SELF to snapshot.personSelf.mediaItems,
                        LifeConsoleSlotKeys.MEAL_SELF to snapshot.mealSelf.mediaItems,
                    )
                }
            }
            "life:bowel" -> {
                // 大便通知没有媒体，fallback 到任意可用媒体
                Log.d(TAG, "Bowel notification: no media to show, falling back to any available media")
                firstAvailableMediaWithSlot(
                    LifeConsoleSlotKeys.PERSON_SELF to snapshot.personSelf.mediaItems,
                    LifeConsoleSlotKeys.MEAL_SELF to snapshot.mealSelf.mediaItems,
                    LifeConsoleSlotKeys.PERSON_PARTNER to snapshot.personPartner.mediaItems,
                    LifeConsoleSlotKeys.MEAL_PARTNER to snapshot.mealPartner.mediaItems,
                )
            }
            else -> null to null
        }
    }

    private fun firstAvailableMediaWithSlot(
        vararg slots: Pair<String, List<RemoteMedia>>,
    ): Pair<RemoteMedia?, String?> {
        for ((slotKey, items) in slots) {
            val first = items.firstOrNull()
            if (first != null) return first to slotKey
        }
        return null to null
    }

    private fun todayDate(zoneId: String = LIFE_CONSOLE_ZONE_ID): String {
        return LocalDate.now(ZoneId.of(zoneId)).toString()
    }

    companion object {
        private const val TAG = "LifePushDispatch"
        const val EXTRA_TARGET_ROUTE = "life_push_target_route"
        const val EXTRA_CATEGORY = "life_push_category"
        const val EXTRA_MEDIA_ID = "life_push_media_id"
        const val EXTRA_LAUNCHED_FROM_PUSH = "life_push_launched"
        const val ACTION_DISPATCH = "com.example.yingshi.LIFE_PUSH_DISPATCH"

        private val dispatchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun intent(context: Context, route: String, category: String, mediaId: String? = null): Intent {
            return Intent(context, LifePushDispatchActivity::class.java).apply {
                action = ACTION_DISPATCH
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TARGET_ROUTE, route)
                putExtra(EXTRA_CATEGORY, category)
                mediaId?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_MEDIA_ID, it) }
            }
        }
    }
}
