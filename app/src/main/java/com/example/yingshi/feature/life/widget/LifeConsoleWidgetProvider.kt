package com.example.yingshi.feature.life.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.example.yingshi.data.model.RemoteLifeConsoleToday
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LifeConsoleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        LifeConsoleWidgetController.renderAllFromStore(context)
        LifeConsoleWidgetController.fetchAndUpdateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        LifeConsoleWidgetController.handleReceive(context, intent)
    }

    companion object {
        const val EXTRA_CATEGORY = "life_console_category"
        const val CATEGORY_PERSON = "PERSON"
        const val CATEGORY_MEAL = "MEAL"

        fun refreshAll(context: Context) {
            LifeConsoleWidgetController.refreshAll(context)
        }

        /**
         * FR-5 Round 2 复检修复: 仅刷新本地视图（renderAllFromStore），不触发网络请求。
         * 用于 DispatchActivity 点击置顶场景，避免不必要的远程拉取。
         */
        fun renderAllFromStore(context: Context) {
            LifeConsoleWidgetController.renderAllFromStore(context)
        }

        fun applySnapshot(context: Context, snapshot: RemoteLifeConsoleToday) {
            LifeConsoleWidgetController.applySnapshot(context, snapshot)
        }
    }
}

internal object LifeConsoleWidgetController {
    private const val EXTRA_SLOT_KEY = "life_console_slot_key"

    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun refreshAll(context: Context) {
        renderAllFromStore(context)
        fetchAndUpdateAll(context)
    }

    fun applySnapshot(context: Context, snapshot: RemoteLifeConsoleToday) {
        ConsoleWidgetBuilder.initRuntime(context)
        LifeConsoleWidgetStore.saveSnapshot(context, snapshot)
        LifeConsoleWidgetStore.saveStatus(context, ConsoleWidgetBuilder.updateStatus("已更新"))
        renderAllFromStore(context)
        widgetScope.launch {
            LifeConsoleWidgetStore.warmThumbnailCache(context, snapshot)
            renderAllFromStore(context)
        }
    }

    fun handleReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in WidgetActions.ALL) return

        ConsoleWidgetBuilder.initRuntime(context)
        val slotKey = intent.getStringExtra(EXTRA_SLOT_KEY)?.let(::slotKeyOrNull)
        when (action) {
            WidgetActions.ACTION_REFRESH -> fetchAndUpdateAll(context)
            WidgetActions.ACTION_BOWEL_ADD -> widgetScope.launch {
                val restoredSnapshot = LifeConsoleWidgetStore.updateBowelOptimistically(context, delta = 1)
                LifeConsoleWidgetStore.saveStatus(context, "已记录")
                renderAllFromStore(context)
                when (val result = RepositoryProvider.lifeConsoleRepository.addBowelEvent()) {
                    is ApiResult.Success -> {
                        LifeConsoleWidgetStore.loadSnapshot(context)?.let { snapshot ->
                            LifeConsoleWidgetStore.saveSnapshot(context, snapshot.copy(bowel = result.data.bowel))
                        }
                        LifeConsoleWidgetStore.saveStatus(context, ConsoleWidgetBuilder.updateStatus("已同步"))
                        renderAllFromStore(context)
                    }
                    is ApiResult.Error -> {
                        if (restoredSnapshot != null) {
                            LifeConsoleWidgetStore.restoreSnapshot(context, restoredSnapshot)
                        }
                        LifeConsoleWidgetStore.saveStatus(context, "记录失败")
                        renderAllFromStore(context)
                    }
                    ApiResult.Loading -> Unit
                }
            }
            WidgetActions.ACTION_BOWEL_REMOVE -> widgetScope.launch {
                val restoredSnapshot = LifeConsoleWidgetStore.updateBowelOptimistically(context, delta = -1)
                    ?: return@launch
                LifeConsoleWidgetStore.saveStatus(context, "已删除")
                renderAllFromStore(context)
                when (val result = RepositoryProvider.lifeConsoleRepository.deleteLatestBowelEvent()) {
                    is ApiResult.Success -> {
                        LifeConsoleWidgetStore.loadSnapshot(context)?.let { snapshot ->
                            LifeConsoleWidgetStore.saveSnapshot(context, snapshot.copy(bowel = result.data.bowel))
                        }
                        LifeConsoleWidgetStore.saveStatus(context, ConsoleWidgetBuilder.updateStatus("已同步"))
                        renderAllFromStore(context)
                    }
                    is ApiResult.Error -> {
                        LifeConsoleWidgetStore.restoreSnapshot(context, restoredSnapshot)
                        LifeConsoleWidgetStore.saveStatus(context, "删除失败")
                        renderAllFromStore(context)
                    }
                    ApiResult.Loading -> Unit
                }
            }
            WidgetActions.ACTION_SLOT_PREV -> {
                if (slotKey != null) {
                    // FR-5 AC-1: 任意操作置顶该框
                    LifeConsoleWidgetStore.saveFrontSlot(context, slotKey)
                    LifeConsoleWidgetStore.moveIndex(context, slotKey, -1)
                    renderAllFromStore(context)
                }
            }
            WidgetActions.ACTION_SLOT_NEXT -> {
                if (slotKey != null) {
                    // FR-5 AC-1: 任意操作置顶该框
                    LifeConsoleWidgetStore.saveFrontSlot(context, slotKey)
                    LifeConsoleWidgetStore.moveIndex(context, slotKey, 1)
                    renderAllFromStore(context)
                }
            }
            WidgetActions.ACTION_SLOT_DELETE -> {
                if (slotKey != null) {
                    // FR-5 AC-1: 任意操作置顶该框
                    LifeConsoleWidgetStore.saveFrontSlot(context, slotKey)
                    deleteCurrentMedia(context, slotKey)
                }
            }
            WidgetActions.ACTION_SLOT_TO_FRONT -> {
                // FR-5: 仅置顶该框（相框点击事件触发）
                if (slotKey != null) {
                    LifeConsoleWidgetStore.saveFrontSlot(context, slotKey)
                    renderAllFromStore(context)
                }
            }
        }
    }

    fun renderAllFromStore(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val snapshot = LifeConsoleWidgetStore.loadSnapshot(context)
        val status = LifeConsoleWidgetStore.status(context)
        updateConsoleWidgets(context, manager, status, snapshot)
        updatePeopleWidgets(context, manager, status, snapshot)
    }

    fun fetchAndUpdateAll(context: Context) {
        ConsoleWidgetBuilder.initRuntime(context)
        LifeConsoleWidgetStore.saveStatus(context, "同步中")
        renderAllFromStore(context)
        widgetScope.launch {
            when (val result = RepositoryProvider.lifeConsoleRepository.getToday(date = ConsoleWidgetBuilder.widgetTodayDate())) {
                is ApiResult.Success -> {
                    LifeConsoleWidgetStore.saveSnapshot(context, result.data)
                    LifeConsoleWidgetStore.saveStatus(context, ConsoleWidgetBuilder.updateStatus("更新"))
                    renderAllFromStore(context)
                    LifeConsoleWidgetStore.warmThumbnailCache(context, result.data)
                    renderAllFromStore(context)
                }
                is ApiResult.Error -> {
                    LifeConsoleWidgetStore.saveStatus(context, "同步失败")
                    renderAllFromStore(context)
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    private fun deleteCurrentMedia(context: Context, slotKey: LifeConsoleWidgetSlotKey) {
        if (!slotKey.editable) return
        val snapshot = LifeConsoleWidgetStore.loadSnapshot(context) ?: return
        val slot = snapshot.slot(slotKey)
        val currentMedia = ConsoleWidgetBuilder.currentMedia(context, slotKey, slot) ?: return
        val restoredSnapshot = LifeConsoleWidgetStore.removeMediaOptimistically(context, slotKey, currentMedia.mediaId)
            ?: return
        LifeConsoleWidgetStore.saveStatus(context, "删除中")
        renderAllFromStore(context)

        widgetScope.launch {
            when (RepositoryProvider.lifeConsoleRepository.deleteMedia(slotKey.category, currentMedia.mediaId)) {
                is ApiResult.Success -> {
                    LifeConsoleWidgetStore.saveStatus(context, ConsoleWidgetBuilder.updateStatus("已删除"))
                    fetchAndUpdateAll(context)
                }
                is ApiResult.Error -> {
                    LifeConsoleWidgetStore.restoreSnapshot(context, restoredSnapshot)
                    LifeConsoleWidgetStore.saveStatus(context, "删除失败")
                    renderAllFromStore(context)
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    private fun updateConsoleWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        status: String,
        snapshot: RemoteLifeConsoleToday?,
    ) {
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, LifeConsoleWidgetProvider::class.java))
        ids.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, ConsoleWidgetBuilder.build(context, status, snapshot))
        }
    }

    private fun updatePeopleWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        status: String,
        snapshot: RemoteLifeConsoleToday?,
    ) {
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, LifePeopleWidgetProvider::class.java))
        ids.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, PeopleWidgetBuilder.build(context, status, snapshot))
        }
    }

    private fun slotKeyOrNull(value: String): LifeConsoleWidgetSlotKey? {
        return LifeConsoleWidgetSlotKey.entries.firstOrNull { it.storageKey == value }
    }
}